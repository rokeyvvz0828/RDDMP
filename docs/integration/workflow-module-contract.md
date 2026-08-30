# 工作流模块接入规范

## 范围

平台采用单租户业务表 + Flowable 7.0.1 运行引擎。前端只提交业务流程模型，禁止直接操作 `ACT_*` 引擎表。首期支持开始、审批、抄送、条件网关、并行分支、并行汇聚和结束节点；服务任务、消息事件、复杂子流程暂不生成。

## 流程模型

新版 `definitionJson` 使用 `schemaVersion: 2`，核心结构如下：

```json
{
  "schemaVersion": 2,
  "variables": [{"name":"amount","type":"DECIMAL","required":true}],
  "formBindings": [],
  "nodes": [{"id":"approve","type":"APPROVAL","label":"部门审批","position":{"x":120,"y":200},"config":{}}],
  "edges": [{"id":"e1","source":"start","target":"approve","condition":"${amount > 100}"}]
}
```

审批人来源支持 `USER`、`ROLE`、`STARTER`、`ORG_OWNER`、`FORM_FIELD`、`EXPRESSION`。审批规则支持任一同意、全部同意和比例同意，多实例支持串行或并行。组织负责人必须维护在 `sys_org.leader_id`，没有有效负责人时按节点的 `emptyAssigneeAction` 返回明确错误或保持等待。

`schemaVersion: 1` 和旧 `steps` JSON 继续走兼容路径，不部署到 Flowable。

## 生命周期

1. 创建定义写入 `wf_definition` 和 `wf_version`。
2. 发布时编译为 BPMN 2.0 XML，部署到 Flowable，并保存 `deployment_id`、`bpmn_xml` 和节点映射。
3. 启动实例固定 `definition_id + version_no`，写入 Flowable 实例 ID、流程变量和业务单号。
4. Flowable 当前任务同步到 `wf_task`，审批动作同时写入 `wf_task_action` 和 `wf_audit_event`。
5. 实例、节点状态和审计时间线通过 `/api/workflows/instances`、`/api/workflows/instances/{id}/timeline` 查询。

业务模块应通过 `com.ccb.workflow.integration.WorkflowBusinessGateway` 按稳定流程编码或已发布定义 ID 启动，不直接写工作流表。业务上下文必须显式包含稳定的业务板块编码和名称、业务类型、业务主键、标题、流程轮次、站内详情路由和规范化数据 SHA-256 摘要；项目标识和名称仅保存展示快照。启动后平台固定本次定义版本。

板块编码必须以小写字母开头，只能包含小写字母、数字、下划线和连字符，最长 64 字符；板块名称最长 128 字符。板块、业务类型和来源是不同概念：配置管理版本申请固定使用 `moduleCode=release`、`moduleName=配置管理`、`businessType=release_application`，工作流通知来源由平台填写为 `审批中心`。新业务接入不得依赖平台按 `businessType` 猜测板块；兼容映射仅用于迁移前已运行且板块字段为空的旧流程，触发时会记录告警。

业务流程状态变化会写入不可变生命周期事件，类型包括 `STARTED`、`RETURNED`、`APPROVED`、`REJECTED`、`TERMINATED`。业务模块实现 `WorkflowLifecycleConsumer`，以 `subscriberKey + eventId` 保证幂等；消费失败由平台持久重试，耗尽后进入 `DEAD`，不得回滚已完成的流程状态。

平台内部电子签名当前暂停使用。流程设计器不再提供签名配置，任务上下文中的 `signature_required` 固定为 `false`，服务端忽略兼容请求中的 `signatureConfirmed` 且不新增签名记录。存量流程 JSON 中的 `config.signatureRequired`、公开接口字段、`wf_signature` 表和历史签名查询继续保留；历史证据不可修改或物理删除。

## 页面职责与审批接入

工作流模块只负责流程定义、配置、发布和实例监控，不承载业务审批页面，也不提供通用的人工“发起审批”入口。业务模块按流程编码启动流程，并在完整业务详情页中展示审批进度、审计记录和当前登录人的可用审批动作。页面必须先加载完整业务数据；无法取得完整业务详情时不得展示审批控件。

工作台和任务中心只聚合待办、已办并导航到 `action_path`，不得直接审批。`action_path` 必须是以单个 `/` 开头的站内路由，禁止协议相对地址、反斜杠、换行和无法安全解码的地址。历史 `/workflow/inbox`、`/workflow/done` 应跳转到任务中心，历史 `/workflow/review/:taskId` 仅用于解析并跳转业务详情，不再渲染通用审批页。

业务详情页直接打开时，通过 `GET /api/workflows/tasks/current-context?businessType=<type>&businessKey=<key>` 按当前租户、当前登录人和业务标识解析本人可办任务；没有本人待办时返回空数据并展示只读详情。任务中心等精确入口仍可通过 `GET /api/workflows/tasks/{id}/context` 获取指定任务上下文。两种响应的任务上下文字段包括：

- `task_id`、`instance_id`、`task_key`、`node_id`、`node_name`、`task_type`
- `business_key`、`business_type`、`business_title`、`business_round`
- `project_ref`、`project_name`、`action_path`
- `task_status`、`instance_status`、`allowed_actions`、`signature_required`、`actionable`

服务端必须按当前租户和当前登录人校验任务归属。非当前处理人返回 `403`；任务不存在、上下文不完整、路由不安全或任务状态已变化返回冲突错误并关闭审批控件。已办任务可以展示为只读，但 `allowed_actions` 必须为空且 `actionable=false`。

提交 `POST /api/workflows/tasks/{id}/decision` 时，前端只能从 `allowed_actions` 中选择动作，不传签署人身份。服务端在同一请求中重新校验处理人、任务状态和允许动作，避免页面打开后任务被其他操作处理造成重复审批。成功后业务详情页保持在当前页面并刷新业务状态、流程进度和审计记录。

## API

- `GET /api/workflows/definitions`
- `POST /api/workflows/definitions`
- `POST /api/workflows/definitions/{id}/publish`
- `POST /api/workflows/instances`
- `GET /api/workflows/instances`
- `GET /api/workflows/instances/{id}/timeline`
- `GET /api/workflows/inbox`
- `GET /api/workflows/done`
- `GET /api/workflows/tasks/{id}/context`
- `POST /api/workflows/tasks/{id}/decision`
- `POST /api/workflows/instances/{id}/terminate`，需要 `workflow:access:delete` 或 `system:admin`
- `GET /api/workflows/events/deliveries`，需要 `workflow:event:manage` 或 `system:admin`
- `POST /api/workflows/events/{eventId}/subscribers/{subscriberKey}/retry`

审批动作包括 `APPROVE`、`REJECT`、`RETURN`、`ADD_SIGN`、`CC`、`TRANSFER`、`DELEGATE`。所有业务查询和修改必须带 `tenant_id`，接口时间格式为 `yyyy-MM-dd HH:mm:ss`，页面日期格式为 `yyyy-MM-dd`。

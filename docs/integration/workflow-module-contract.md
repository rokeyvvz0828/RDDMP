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

## API

- `GET /api/workflows/definitions`
- `POST /api/workflows/definitions`
- `POST /api/workflows/definitions/{id}/publish`
- `POST /api/workflows/instances`
- `GET /api/workflows/instances`
- `GET /api/workflows/instances/{id}/timeline`
- `GET /api/workflows/inbox`
- `POST /api/workflows/tasks/{id}/decision`
- `POST /api/workflows/instances/{id}/terminate`，需要 `workflow:access:delete` 或 `system:admin`

审批动作包括 `APPROVE`、`REJECT`、`RETURN`、`ADD_SIGN`、`CC`、`TRANSFER`、`DELEGATE`。所有业务查询和修改必须带 `tenant_id`，接口时间格式为 `yyyy-MM-dd HH:mm:ss`，页面日期格式为 `yyyy-MM-dd`。
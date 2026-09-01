# CLB、DNS 与证书工单设计

适用需求：`REQ-20260823-051`；来源 `temp/implementation/issues/11-clb-dns-certificate-work-orders.md`
与 `temp/spec.md` User Stories 110-113。

## 1. 目标与边界

三类网络专项工单（CLB 开通/调整、DNS 新增/变更/注销、证书申请/续期/吊销）只登记
申请内容、办理过程和办理结果。平台不承担 CLB 实例/监听器/后端池/健康检查生命周期、
DNS 解析管理、证书生成/私钥保存/自动部署。实际动作在线下或外部系统完成，办理人员
通过平台工作流核验、退回、拒绝或批准，并登记结果与凭证。

模块落点：`business/architecture`（用户于 2026-08-23 确认），新增
`com.ccb.architecture.network` 子包；复用 REQ-048 的工单+工作流模式（状态机、轮次、
回执、历史、审计），不重复建设流程状态机。

## 2. 数据模型（V89）

全部表 `arch_` 前缀，归属 architecture 模块，MySQL 8.4 utf8mb4。

### arch_network_work_order（主表）

| 列 | 说明 |
| --- | --- |
| id / tenant_id | 主键；租户只从认证上下文注入 |
| kind | `CLB/DNS/CERT`（CHECK 约束） |
| action_type | 按 kind 受控：CLB `OPEN/ADJUST`；DNS `ADD/CHANGE/REMOVE`；CERT `APPLY/RENEW/REVOKE`（跨列 CHECK） |
| subject | 列表主标识（CLB 名称/域名/证书主题），服务端从载荷投影 |
| applicant_id / reason | 申请人、申请原因/变更说明 |
| status | `DRAFT/IN_REVIEW/RETURNED/COMPLETED/REJECTED/CANCELLED` |
| business_payload | kind 专属字段快照 JSON，服务端强类型 DTO 校验后写入 |
| attachment_ids | 申请材料附件 id 列表 JSON |
| result_status / result_description / result_attachment_ids / result_registered_by / result_registered_at | 办理结果（SUCCESS/FAILED）与凭证 |
| current_business_round / current_workflow_* / current_payload_digest / cancellation_requested / row_version | 工作流上下文与并发控制（同 REQ-048） |
| created_by / updated_by / created_at / updated_at | 审计与时间戳 |

索引：`(tenant_id,id)` 唯一；申请人、kind+status、工作流实例。约束：状态/动作/轮次/
行版本/取消标记/结果状态 CHECK。

### 附表

- `arch_network_work_order_history`：不可变业务事件（event_type、from/to status、
  business_round、summary、snapshot_json、diff_json、operator_id、occurred_at）。
- `arch_network_workflow_round`：每轮工作流绑定（定义/版本/实例/摘要/状态）。
- `arch_network_workflow_receipt`：`(tenant_id,event_id,subscriber_key)` 唯一回执，
  幂等消费。

不需要编号保留、值保留、排他锁、替换关系（无主记录发布动作）。

## 3. 状态机与规则

```
DRAFT ──submit──▶ IN_REVIEW ──APPROVED 事件──▶ COMPLETED
  ▲                  │  │
  │ RETURNED 事件 ◀──┘  └──REJECTED 事件──▶ REJECTED
  │                    └──cancel+TERMINATED 事件──▶ CANCELLED
  └────cancel（同步）──▶ CANCELLED
```

- 编辑/删除仅限本人且 `DRAFT/RETURNED`；提交固化工单快照与 SHA-256 摘要，启动新轮次
  并进入 `IN_REVIEW`；退回后重提递增轮次。
- `APPROVED` 事件：工单进入 `COMPLETED`（完成仅表示外部配置已办理并登记，不执行任何
  外部动作）；`RETURNED/REJECTED` 分别进入对应状态；`TERMINATED` 仅在已登记取消请求
  时确认 `CANCELLED`。
- 办理结果登记：`manage` 权限，状态 `IN_REVIEW` 或 `COMPLETED`；`rowVersion` 防并发
  覆盖；每次登记写历史事件（RESULT_REGISTERED），不改变工单状态。
- 摘要计算：`{kind, actionType, subject, businessPayload, attachmentIds}` 规范化 JSON
  的 SHA-256。

## 4. 工作流集成

- `moduleCode=architecture`、`moduleName=架构管理`、
  `businessType=architecture_network_work_order`、订阅键
  `architecture.network.work-order.lifecycle.v1`。
- 固定流程编码 `architecture.network.work-order`：单一 ROLE 审批节点
  （角色 113 `NETWORK_MANAGER`，ANY，空处理人 ERROR，动作 `APPROVE/RETURN/REJECT`），
  V90 预置草稿（定义 900000000000032、版本 900000000000033），经平台发布入口生成
  Flowable deployment 后方可提交；未发布时提交返回明确错误。
- 提交/取消通过 `WorkflowBusinessGateway`；生命周期事件按
  `subscriberKey + eventId` 幂等消费，回执/轮次/业务状态同一事务，失败可重试。
- 详情路由 `action_path=/architecture/network-work-orders/{id}`；流程变量
  `workOrderId/kind/actionType/applicantId`。

## 5. 权限与审计（V90）

- 菜单 808「网络专项工单」（父 800，路由 `/architecture/network-work-orders`）；
  权限 8081/8082/8083 = `architecture:network-work-order:view/apply/manage`；
  角色 113 `NETWORK_MANAGER` 拥有三者；用户 1（本地管理员）加入角色 113 并直接授权；
  存量角色兼容映射：持有 `architecture:view` → 8081、`architecture:apply` → 8082。
- 数据范围：`view/apply` 仅本人，`manage` 当前租户全部；HTTP DTO 不接受 `tenantId`。
- 写操作审计：`architecture.network-work-order.create/update/submit/cancel/result`
  成功与业务失败均记录 `sys_operation_log`（含 trace ID）。
- 附件实体授权：`NetworkAttachmentAccessPolicy`（businessType
  `architecture_network_work_order`）——读/预览/下载需可读该工单（租户内存在 +
  本人或 manage 权限），删除仅 `DRAFT/RETURNED` 且本人或 manage。
- 证书工单附件扩展名黑名单：`key/pem/pfx/p12/jks/keystore` → 400。

## 6. 前端

- 页面：`NetworkWorkOrderListPage`（kind 页签 + status 筛选 + 桌面表格/移动卡片）、
  `NetworkWorkOrderFormPage`（按 kind 渲染固定强类型字段 + 附件上传）、
  `NetworkWorkOrderDetailPage`（快照 + 办理结果 + 历史时间线 + 工作流审批区 +
  申请人操作条）。路由 `/architecture/network-work-orders[/new|:id/edit|:id]`。
- 复用：`UiPageHeader/UiDataTable/UiStatusTag/UiToolbar/UiEmptyState`、architecture
  页样式与移动列表模式、`api/attachments.ts`（上传/预览/下载）、`api/workflow.ts`
  （任务上下文/决策）、`stores/auth.ts` 权限判断。
- 新样式仅限附件列表/结果登记等 architecture 样式无法覆盖的部分，在 architecture.css
  内以 `.architecture-network-*` 前缀追加并说明。
- 状态覆盖：加载/空/失败/无权限/提交中/重复提交/只读详情；桌面与移动视口不溢出。

## 7. 测试接缝

- 领域：状态机与轮次、per-kind 校验与动作集、摘要、结果登记并发、私钥黑名单。
- MySQL（Testcontainers + 真实迁移）：V89 结构/约束、V90 种子与身份冲突失败关闭、
  租户隔离、行版本、回执幂等、轮次持久化、工作流集成（发布前草稿模型断言）。
- 公开 HTTP 与浏览器 UAT 按 `codex-task-scope.yaml` 的 manual 用例执行。

## 8. 未决问题与风险

- 合并顺序：V89-V90 与在途 049 的 V85-V86 必须 049→050 顺序入库（乱序触发 Flyway
  out-of-order）。
- 流程发布依赖平台入口，未发布前提交必须返回明确错误并写入审计。
- 私钥黑名单为扩展名级防线，不能替代上传环节的提示与契约约束。

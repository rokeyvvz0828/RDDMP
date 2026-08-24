# 架构子系统模块集成契约

适用需求：`REQ-20260812-021`；`REQ-20260822-048` 完成生命周期修订（主记录写入全部经由变更工单、主记录新增状态、三级权限、固定审批流程、引用检查 SPI 与操作审计）；`REQ-20260823-049` 新增部署单元（版本发布、停用/作废生命周期、Excel 初始化导入与技术架构师权限）。本契约冻结逻辑子系统、物理子系统、部署单元、受限选项 API 和变更工单的 HTTP 边界。V1 使用固定强类型表单，不提供表单 schema 或任意字段接口。

## 通用约定

- 资源根为 `/api/architecture`，必须认证并执行方法上的固定 `architecture:*` 权限。
- 租户只从服务端 `AuthUser.tenantId` 取得。请求、查询和响应均没有 `tenantId`/`tenant_id`。
- 响应统一为 `ApiResponse<T>{code,message,data,traceId,timestamp}`。
- 分页数据为 `PageResult<T>{records,total,page,size}`；`page` 从 1 开始，`size` 默认 20、最大 100。
- JSON 字段使用 camelCase。未在本契约列出的客户端字段不参与持久化。
- 400 表示格式或引用无效，401 表示未认证，403 表示缺少动作权限，404/code `40400` 表示当前租户资源不存在或选项上下文不支持，409 表示唯一、引用或并发冲突。

## 逻辑子系统

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/logical-subsystems` | `architecture:logical:list`，或 `architecture:view`/`apply`/`manage` 任一 |
| GET | `/logical-subsystems/{id}` | `architecture:logical:list`，或 `architecture:view`/`apply`/`manage` 任一 |
| POST | `/logical-subsystems` | 保留路由；统一返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`，无数据库写入 |
| PUT | `/logical-subsystems/{id}` | 保留路由；统一返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`，无数据库写入 |
| DELETE | `/logical-subsystems/{id}` | 保留路由；统一返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`，无数据库写入 |

列表查询只接受 `page,size,code,shortName,name,businessOrgId,status`。`status` 取 `ACTIVE|OFFLINE|VOIDED`。

创建和编辑请求字段固定为：

```json
{
  "code": "AP_201",
  "shortName": "员工渠道",
  "name": "员工渠道整合平台",
  "businessOrgId": 11,
  "deploymentPlatformCode": "P2",
  "systemTypeCode": "APPLICATION",
  "systemOwnershipCode": "CHANNEL",
  "contactUserId": 21,
  "description": null,
  "remark": null
}
```

列表记录和详情的字段固定为：`id,code,shortName,name,businessOrgId,deploymentPlatformCode,systemTypeCode,systemOwnershipCode,contactUserId,description,remark,numberSequence,status,sortNo,rowVersion,physicalSubsystems,createdBy,updatedBy,createdAt,updatedAt`。

- `numberSequence`：逻辑全局内部序号，`null` 仅表示存量未回填（预期不会出现）；展示编号由服务端格式化（`A%04d`）。
- `status`：`ACTIVE|OFFLINE|VOIDED`；`VOIDED` 为不可逆终态。
- `rowVersion`：乐观并发版本，草稿的 `sourceRowVersion` 必须等于该值。
- `physicalSubsystems`：已发布物理子系统摘要（`PhysicalSubsystemSummary[]`，仅逻辑详情页返回）。

## 物理子系统

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/physical-subsystems` | `architecture:physical:list`，或 `architecture:view`/`apply`/`manage` 任一 |
| GET | `/physical-subsystems/{id}` | `architecture:physical:list`，或 `architecture:view`/`apply`/`manage` 任一 |
| POST | `/physical-subsystems` | 保留路由；统一返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`，无数据库写入 |
| PUT | `/physical-subsystems/{id}` | 保留路由；统一返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`，无数据库写入 |
| DELETE | `/physical-subsystems/{id}` | 保留路由；统一返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`，无数据库写入 |

列表查询只接受 `page,size,code,shortName,name,businessGroupName,responsibleTeamOrgId,logicalSubsystemId,status`。

创建和编辑请求字段固定为：

```json
{
  "code": "WP_201",
  "shortName": "员工渠道物理",
  "name": "员工渠道物理平台",
  "logicalSubsystemId": 101,
  "businessGroupName": null,
  "responsibleTeamOrgId": 12,
  "runtimeCode": "24H",
  "systemLevelCode": "A_PLUS",
  "developmentFrameworkCode": "P2",
  "ownerUserId": null,
  "description": null,
  "remark": null
}
```

列表记录和详情的字段固定为：

`id,code,shortName,name,logicalSubsystemId,logicalSubsystemCode,logicalSubsystemName,businessGroupName,responsibleTeamOrgId,responsibleTeamDisplayName,responsibleTeamValid,runtimeCode,systemLevelCode,developmentFrameworkCode,ownerUserId,ownerDisplayName,numberSlot,englishName,status,rowVersion,logicalSubsystemNumberSequence,logicalSubsystemStatus,description,remark,createdBy,createdByDisplayName,updatedBy,createdAt,updatedAt`。

- `numberSlot`：物理槽位（`1..9,A..Z`），配合父逻辑 `numberSequence` 形成编号（`W%04d<slot>`）。
- `englishName`：非空时租户内永久唯一。
- `status`：`ACTIVE|OFFLINE|VOIDED`；`logicalSubsystemStatus` 为父逻辑发布状态。
- 普通修改不得改变 `logicalSubsystemId`；归属迁移必须使用 REPLACE 工单。

`responsibleTeamDisplayName` 在组织仍活动时取当前名称，否则取保存时的服务端快照；`responsibleTeamValid=false` 时编辑必须重新选择活动组织。`createdByDisplayName` 由服务端按 `createdBy` 投影当前租户用户显示名，历史用户不可读时可为 `null`。物理子系统不维护联系人或联系电话；请求不能提交联系人、团队名称快照、电话、状态或审计字段。

## 选项 API

路径中的资源上下文允许 `logical-subsystem`、`physical-subsystem` 和 `deployment-unit`。已知上下文检查自己的 `list`/`view` 权限或新三级权限（`view`/`apply`/`manage`），不以多项权限 OR 放行；未知或不支持的上下文返回 404/code `40400`。

| 方法与路径 | 查询 | 权限 | `data` |
| --- | --- | --- | --- |
| `GET /options/logical-subsystem/organizations` | `page,size,keyword?` | `architecture:logical:list` | `PageResult<OrganizationOption>` |
| `GET /options/physical-subsystem/organizations` | `page,size,keyword?` | `architecture:physical:list` | `PageResult<OrganizationOption>` |
| `GET /options/logical-subsystem/users` | `page,size,keyword?` | `architecture:logical:list` | `PageResult<UserOption>` |
| `GET /options/physical-subsystem/users` | `page,size,keyword?` | `architecture:physical:list` | `PageResult<UserOption>` |
| `GET /options/logical-subsystem/parameters/{categoryCode}` | 无 | `architecture:logical:list` | `ParameterOption[]` |
| `GET /options/physical-subsystem/parameters/{categoryCode}` | 无 | `architecture:physical:list` | `ParameterOption[]` |
| `GET /options/physical-subsystem/logical-subsystems` | `page,size,code?,name?` | `architecture:physical:list` | `PageResult<LogicalSubsystemOption>` |
| `GET /options/deployment-unit/physical-subsystems` | `page,size,code?,name?` | `architecture:deployment-unit:view`/`manage`，或 `view`/`apply`/`manage` 任一 | `PageResult<PhysicalSubsystemOption>` |

选项记录必须精确使用以下字段：

| DTO | 字段 | 可空性与说明 |
| --- | --- | --- |
| `OrganizationOption` | `id,name,parentId,pathLabel` | 根组织 `parentId=null`；只返回活动组织 |
| `UserOption` | `id,displayName,username,phone` | `phone` 显式允许 `null`；只返回活动用户 |
| `ParameterOption` | `code,label` | 不分页 |
| `LogicalSubsystemOption` | `id,code,name` | 只返回当前租户未删除记录 |
| `PhysicalSubsystemOption` | `id,code,name,status` | 只返回当前租户 ACTIVE 物理子系统（部署单元级联选择） |

逻辑上下文参数分类白名单为 `ARCH_DEPLOYMENT_PLATFORM`、`ARCH_SYSTEM_TYPE`、`ARCH_SYSTEM_OWNERSHIP`；物理上下文为 `ARCH_RUNTIME`、`ARCH_SYSTEM_LEVEL`、`ARCH_DEVELOPMENT_FRAMEWORK`。跨上下文分类返回 400。

选项响应不得包含密码散列、头像对象键、活动状态、删除标记、租户或平台内部管理字段。业务用户不需要 system 用户、组织或参数管理权限。

## 变更工单（REQ-20260822-048）

资源根：`/api/architecture/subsystem-change-applications`。所有主记录写入（新增、更新、下线、重新启用、作废、归属替换）必须先创建草稿、提交审批，批准后在同一真实事务中原子发布。审批人只能批准、退回或拒绝，不得修改已提交业务字段。

| 方法 | 路径 | 权限与语义 |
| --- | --- | --- |
| GET | `.../subsystem-change-applications` | `view`/`apply`/`manage`；查询 `status?,limit,offset`；`view`/`apply` 只返回本人，`manage` 返回当前租户全部；响应为 `SubsystemChangeApplicationSummary[]`（无 total，客户端以返回长度判断分页） |
| POST | `.../subsystem-change-applications` | `apply`/`manage`；创建草稿 `DRAFT` |
| GET | `.../subsystem-change-applications/{id}` | `view`/`apply`/`manage`；本人或管理范围，跨租户 404 |
| PUT | `.../subsystem-change-applications/{id}` | `apply`/`manage`；只是本人 `DRAFT/RETURNED`；其它人返回 403 或 409 |
| POST | `.../{id}/submit` | 分配/保留编号、取目标锁、启动新流程轮次；`body {rowVersion}` |
| POST | `.../{id}/cancel` | 草稿/退回同步取消；审批中先登记并调用工作流终止，收到 `TERMINATED` 事件后终态化；`body {rowVersion}` |
| POST | `.../suggestions` | 本地建议；`body {fieldValues}`；仅对空字段返回候选；当前生产接线为 Noop（恒空），不调用真实 AI |

工单状态机：`DRAFT → IN_REVIEW → APPROVED/REJECTED`，`IN_REVIEW → RETURNED → IN_REVIEW`（新 instance、businessRound+1），`DRAFT/RETURNED → CANCELLED`，`IN_REVIEW → CANCELLED`（终止事件确认后）。

工单类型与目标：

| 目标 | 动作 | 说明 |
| --- | --- | --- |
| LOGICAL | `CREATE` | 可选 `0..N` 物理草稿（级联，批准时同事务发布）；不支持 `REPLACE` |
| LOGICAL | `UPDATE/OFFLINE/REACTIVATE/VOID` | 单目标；`VOID` 要求无物理历史且引用校验通过 |
| PHYSICAL | `CREATE/UPDATE/OFFLINE/REACTIVATE/VOID/REPLACE` | `REPLACE` 必须指定与源物理当前归属不同的目标逻辑；批准时新建物理并下线旧物理，保存不可变替换关系 |

请求体字段（camelCase）：

- 创建：`{targetKind, actionType, targetId?, reason, logicalDraft?, physicalDrafts?[], physicalDraft?}`；`targetKind` 为 `LOGICAL|PHYSICAL`。
- `LogicalDraftInput`：`shortName,name,businessOrgId,contactUserId` 必填；`deploymentPlatformCode,systemTypeCode,systemOwnershipCode,description,remark,sortNo,sourceRowVersion` 可选（非 CREATE 的 `sourceRowVersion` 必须等于目标主记录的 `rowVersion`）。
- `PhysicalDraftInput`：`lineNo,shortName,name,responsibleTeamOrgId,responsibleTeamNameSnapshot` 必填；逻辑 CREATE 级联草稿的 `targetLogicalSubsystemId` 必须为 `null`，独立物理草稿必须指定已发布活动逻辑；非 CREATE 物理草稿的 `sourceRowVersion` 必须等于目标物理 `rowVersion`；非 REPLACE 物理草稿不得改变 `targetLogicalSubsystemId`。
- 更新：`{rowVersion, reason, logicalDraft?, physicalDrafts?[]}`；物理工单更新必须且只能包含一行物理草稿。
- 提交/取消：`{rowVersion}`。

详情响应 `data`：`{application, logicalDraft?, physicalDrafts[], history[]}`。`application` 含 `id,targetKind,actionType,targetId,applicantId,reason,status,currentBusinessRound,currentWorkflowDefinitionId,currentWorkflowVersionId,currentWorkflowInstanceId,currentPayloadDigest,cancellationRequested,rowVersion,createdBy,updatedBy,createdAt,updatedAt`。草稿含 `reservedNumberSequence`/`reservedNumberSlot`（提交前为 `null`，提交后保留）、`draftRevision`、`submittedSnapshotJson`；`history[]` 为不可变业务事件（`eventType,fromStatus,toStatus,businessRound,summary,snapshotJson,diffJson,operatorId,occurredAt`）。

错误码：400 字段/状态/父级/容量/引用错误；401 未认证；403 权限、归属或工作流任务处理人（管理不能编辑他人草稿为 403）；404 当前租户资源不存在；409 目标锁、值保留、行版本、唯一编号或名称、旧写接口、流程轮次/实例已结束（取消已结束实例返回 409 而非 500）；503 引用检查器不可判定（作废 fail-closed）。

## 工作流与审计

- 固定流程编码 `architecture.subsystem.change`，业务类型 `architecture_subsystem_change`，订阅键 `architecture.subsystem.change.lifecycle.v1`；审批节点为单一 ROLE（角色 110 `ARCHITECTURE_MANAGER`，ANY，空处理人 ERROR），只允许 `APPROVE/RETURN/REJECT`。
- 首次提交与每次退回重提均启动新实例并递增 `currentBusinessRound`；V84 预置草稿定义，必须经平台既有发布入口（`POST /api/workflows/definitions/900000000000030/publish`）生成 Flowable deployment 后才能提交。
- 生命周期事件按 `subscriberKey + eventId` 幂等消费并校验租户、业务键、实例、轮次与摘要；`APPROVED` 事件在同一业务事务重新校验编号、唯一性、引用、状态和行版本后原子发布，失败则保持未批准并交由平台重试（重试耗尽进入 DEAD，需运维处置；对已结束实例的取消返回 409）。
- 写操作审计：关键工单写（create/update/submit/cancel 成功与业务失败）写入 `sys_operation_log`（operation_code `architecture.subsystem-change.*`）；工作流任务动作走 `wf_audit_event`（`TASK_*`）。
- 部署单元写操作审计：`sys_operation_log` 的 operation_code 为 `architecture.deployment-unit.*`（create/update/deactivate/reactivate/void 成功与失败）与 `architecture.deployment-unit.import`（导入上传与确认成功与失败）。

## 部署单元（REQ-20260823-049）

资源根：`/api/architecture/deployment-units`。部署单元由技术架构师直接维护，不经过变更工单；创建即发布版本 1 并分配永久编号；已发布单元显示内容变更自动发布新版本；已发布后编号与物理归属不可变更。

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/deployment-units` | `architecture:deployment-unit:view`，或 `view`/`apply`/`manage` 任一 |
| GET | `/deployment-units/{id}` | 同上 |
| GET | `/deployment-units/{id}/versions` | 同上 |
| POST | `/deployment-units` | `architecture:deployment-unit:manage` |
| PUT | `/deployment-units/{id}` | `architecture:deployment-unit:manage` |
| POST | `/deployment-units/{id}/deactivate` | `architecture:deployment-unit:manage` |
| POST | `/deployment-units/{id}/reactivate` | `architecture:deployment-unit:manage` |
| POST | `/deployment-units/{id}/void` | `architecture:deployment-unit:manage` |

列表查询只接受 `page,size,code,shortName,name,physicalSubsystemId,kind,status`。`kind` 取 `APPLICATION|DATABASE|MQ`；`status` 取 `ACTIVE|INACTIVE|VOIDED`。

创建请求体：

```json
{
  "physicalSubsystemId": 501,
  "shortName": "ECIP-AP",
  "name": "电子渠道接入应用",
  "kind": "APPLICATION",
  "description": null,
  "remark": null
}
```

更新请求体与创建相同但必须携带 `rowVersion`（乐观锁）；`physicalSubsystemId` 忽略。创建响应与详情记录字段固定为：

`id,code,physicalSubsystemId,physicalSubsystemCode,physicalSubsystemName,physicalSubsystemStatus,shortName,name,kind,status,currentVersion,description,remark,createdBy,createdByDisplayName,updatedBy,updatedByDisplayName,createdAt,updatedAt,rowVersion`。

- `code`：永久编号 `D<物理编号><三位序号>`（如 `DW0001A001`），创建即分配，之后不可修改、不可复用；每物理子系统最多 999 个。
- `currentVersion`：当前版本号；每次更新 ACTIVE 单元自动 +1 并新增不可改写版本行。
- `status`：`ACTIVE|INACTIVE|VOIDED`；`INACTIVE` 阻止新的引用但保留历史，可重新启用；`VOIDED` 为终态，仅从未被引用（`com.ccb.architecture.integration.DeploymentUnitReferenceChecker` 全部 CLEAR）允许，检查异常按存在引用失败关闭（503）。
- `rowVersion`：更新必须等于服务端当前值，冲突返回 409。
- `kind` 可随版本变化；`physicalSubsystemId` 创建后不可变更。

版本历史 `GET /deployment-units/{id}/versions` 返回 `[{versionNo,shortName,name,kind,description,remark,publishedBy,publishedByDisplayName,publishedAt}]`，按 `versionNo` 升序；版本行无更新/删除接口。

### 初始化导入

资源根：`/api/architecture/deployment-unit-imports`。仅 `architecture:deployment-unit:manage` 可上传与确认；批次台账查询需要查看权限；错误报告导出需要维护权限。

| 方法 | 路径 | 语义 |
| --- | --- | --- |
| POST | `/deployment-unit-imports` | multipart 上传 `.xlsx`（≤10MB、≤5000 行），解析与校验后创建 PREVIEW 批次并返回预览 |
| GET | `/deployment-unit-imports` | 批次分页（`page,size`），`PageResult<DeploymentUnitImportBatch>` |
| GET | `/deployment-unit-imports/{id}` | 批次与行明细 |
| POST | `/deployment-unit-imports/{id}/confirm` | 确认写入；PREVIEW 批次只可确认一次 |
| GET | `/deployment-unit-imports/{id}/error-report` | 失败行 CSV（UTF-8 BOM），`Content-Disposition` 下载 |
| GET | `/deployment-unit-imports/template` | xlsx 模板下载 |

模板表头固定：`物理子系统编号,部署单元简称,部署单元名称,部署单元类型,描述,备注`；类型取 `应用|数据库|消息队列`（或 `APPLICATION|DATABASE|MQ`）。预览行状态 `VALID|INVALID`；确认后 `SUCCESS|FAILED|SKIPPED`（SKIPPED 为幂等重导时已存在的 ACTIVE 同名同物理行）。批次状态 `PREVIEW|SUCCESS|PARTIAL|FAILED`；确认时预期行级失败记录明细并继续，意外异常整批回滚并标记 FAILED。批次字段：`id,fileName,fileSize,totalRows,validRows,successRows,failedRows,skippedRows,status,errorMessage,createdBy,createdByDisplayName,createdAt,completedAt`；行明细：`itemId,lineNo,row{physicalCode,shortName,name,kindLabel,description,remark},rowStatus,errorMessage,note,unitId`。

## 明确不提供

- `/api/architecture/form-schemas/**` 或任何动态表单 schema。
- 绕过工单直接修改发布主记录的 HTTP 写接口（旧 POST/PUT/DELETE 保留路由但只返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`）。
- 客户端提供 tenant、团队名称快照、物理联系人、电话或审计字段的写入能力。
- 部署单元编号、物理归属或版本行的修改能力；已发布部署单元的显示内容只能通过发布新版本改变。
- 绕过引用守卫的部署单元作废；引用检查器不可判定时一律失败关闭。
- 直接访问 `com.ccb.system.internal.*` 或 system 私有数据表。
- 真实 AI、外部引用 provider 或业务模块之外的引用检查实现（`com.ccb.architecture.integration` SPI 预留，provider 为空视为无外部引用）。

## 架构规范（REQ-20260823-050）

资源根 `/api/architecture/standards`。

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/categories` | `architecture:standard:view` |
| GET | `` | 列表（`page,size,title,categoryCode,status`） |
| GET | `/{id}` | 详情 |
| GET | `/{id}/versions` | 发布版本快照 |
| GET | `/{id}/attachments` | 附件清单（业务类型 `architecture-standard`） |
| POST | `` | 创建草稿 |
| PUT | `/{id}` | 编辑（草稿或已发布；已下线拒绝） |
| POST | `/{id}/publish` | 发布/重新发布（追加不可变版本快照） |
| POST | `/{id}/offline` | 下线 |
| DELETE | `/{id}` | 仅从未发布的草稿 |
| POST | `/{id}/attachments` | 绑定附件（manage） |
| DELETE | `/{id}/attachments/{attachmentId}` | 移除附件（manage；OFFLINE 拒绝） |

- `status` 取 `DRAFT|PUBLISHED|OFFLINE`；类别为平台参数 `ARCH_STANDARD_CATEGORY` 的键。
- 每次发布版本号自增并写入不可变快照；PDF 等只作为附件格式。

## 架构决策（REQ-20260823-050）

资源根 `/api/architecture/decisions`。权限分级 `architecture:decision:view/propose/review/manage`。

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/options/types`、`/options/users` | view |
| GET | `` | 事项列表（`page,size,keyword,typeCode,status,firstHandlingOverdue`） |
| GET | `/{id}`、`/{id}/materials`、`/{id}/reviews`、`/{id}/reviews/{reviewId}/participants`、`/{id}/reviews/{reviewId}/action-items` | view |
| GET | `/conclusions`、`/conclusions/{conclusionId}/chain` | view |
| POST | `` | 提交事项（propose） |
| PUT | `/{id}` | 编辑标题/问题（propose 本人或 manage，仅 SUBMITTED/RETURNED_FOR_INFO） |
| POST | `/{id}/materials` | 补充材料（propose 本人/review/manage） |
| POST | `/{id}/type` | 确定类型（review/manage；发布前必填） |
| POST | `/{id}/first-handling` | 首次处理（review；ACCEPTED/REQUESTED_INFO/REVIEW_MODE_SET） |
| POST | `/{id}/resubmit` | 要求补充后重新提交（propose 本人或 manage） |
| POST | `/{id}/reviews`、`PUT /{id}/reviews/{reviewId}` | 记录/编辑评审（review） |
| POST | `/{id}/reviews/{reviewId}/action-items/{actionItemId}/complete` | 完成行动项（review；发布后仍可跟踪） |
| POST | `/{id}/publication/prepare` | 结论发布准备（manage；类型+含结论评审必填，登记替代/部分修订目标） |
| POST | `/{id}/publication/start` | 启动 `architecture.decision.review` 工作流（manage） |
| POST | `/{id}/attachments`、`DELETE /{id}/attachments/{attachmentId}` | 附件（业务类型 `architecture-decision`；发布后不可删除） |

- 事项编号 `AD-<年份>-<四位序号>` 租户内永久唯一、不可复用。
- 首次处理期限 = 受理时间 + 7 自然日，逾期为计算标识。
- 正式结论只由工作流 `APPROVED` 生命周期事件写入，已发布结论无修改/删除路径。
- 结论有效状态由替代关系推导：`EFFECTIVE`/`SUPERSEDED`/`PARTIALLY_SUPERSEDED`。
- 工作流业务类型 `architecture_decision_publish`，订阅标识 `architecture.decision.publish.lifecycle.v1`。

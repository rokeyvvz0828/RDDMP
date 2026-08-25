# 架构子系统模块集成契约

适用需求：`REQ-20260812-021`；`REQ-20260822-048` 完成生命周期修订（主记录写入全部经由变更工单、主记录新增状态、三级权限、固定审批流程、引用检查 SPI 与操作审计）；`REQ-20260823-049` 新增部署单元（版本发布、停用/作废生命周期、Excel 初始化导入与技术架构师权限）；`REQ-20260824-052` 新增具体环境与资源申请（申请态审批，不生成实际资源分配）。本契约冻结逻辑子系统、物理子系统、部署单元、具体环境、资源申请、受限选项 API 和变更工单的 HTTP 边界。V1 使用固定强类型表单，不提供表单 schema 或任意字段接口。

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
  "businessContinuityLevel": "B1",
  "collectedSystemLevel": "A",
  "deploymentPlatform": "architecture.deployment-platform.p8",
  "disasterRecoveryMode": "architecture.disaster-recovery.active-standby",
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
V95 后额外包含 `businessContinuityLevel,collectedSystemLevel,deploymentPlatform,disasterRecoveryMode`；资源申请按 V96 口径只带出 `businessGroupName,systemLevelCode,deploymentPlatform,disasterRecoveryMode`，不再使用业务连续性等级和项目组收集系统等级。

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
| `GET /resource-requests/options/deployment-units` | `physicalSubsystemId,limit?` | `architecture:resource-request:view/apply/manage`，或 `view`/`apply`/`manage` 任一 | `DeploymentUnitOption[]` |

选项记录必须精确使用以下字段：

| DTO | 字段 | 可空性与说明 |
| --- | --- | --- |
| `OrganizationOption` | `id,name,parentId,pathLabel` | 根组织 `parentId=null`；只返回活动组织 |
| `UserOption` | `id,displayName,username,phone` | `phone` 显式允许 `null`；只返回活动用户 |
| `ParameterOption` | `code,label` | 不分页 |
| `LogicalSubsystemOption` | `id,code,name` | 只返回当前租户未删除记录 |
| `PhysicalSubsystemOption` | `id,code,shortName,name,businessGroupName,businessContinuityLevel,collectedSystemLevel,deploymentPlatform,disasterRecoveryMode,systemLevelCode,status` | 只返回当前租户 ACTIVE 物理子系统；资源申请只消费 `businessGroupName,systemLevelCode,deploymentPlatform,disasterRecoveryMode` 作为物理子系统只读带出信息 |
| `DeploymentUnitOption` | `id,code,name,kind,physicalSubsystemId,relatedDeploymentUnitName,deploymentUnitType,description` | 只返回所选 ACTIVE 物理子系统下的 ACTIVE 部署单元（资源申请级联选择和部署单元信息带出） |

逻辑上下文参数分类白名单为 `ARCH_DEPLOYMENT_PLATFORM`、`ARCH_SYSTEM_TYPE`、`ARCH_SYSTEM_OWNERSHIP`；物理上下文为 `ARCH_RUNTIME`、`ARCH_SYSTEM_LEVEL`、`ARCH_DEVELOPMENT_FRAMEWORK`、`ARCH_DEPLOYMENT_PLATFORM`、`ARCH_DISASTER_RECOVERY_MODE`、`ARCH_SERVER_TYPE`、`ARCH_JDK_VERSION`、`ARCH_MIDDLEWARE`、`ARCH_OPERATING_SYSTEM`。跨上下文分类返回 400。

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


## 具体环境与资源申请（REQ-20260824-052）

具体环境资源根：`/api/architecture/environments`；环境类型通过 `GET /api/architecture/environment-types` 读取系统字典 `ARCH_ENVIRONMENT_TYPE` 的启用项。具体环境主数据由环境资源办理人员维护，不创建机器、IP、环境部署实例或实际资源分配；环境类型增删改停不在架构模块内维护。

| 方法 | 路径 | 权限与语义 |
| --- | --- | --- |
| GET | `/environment-types` | `architecture:environment:view/manage`，或 `architecture:view/manage`；返回启用字典项 `{code,name}` |
| GET | `/environments` | `architecture:environment:view/manage`，或 `architecture:view/manage`；查询 `typeCode?,status?,keyword?,limit,offset` |
| GET | `/environments/{id}` | 同上；返回环境详情与资源汇总 |
| POST | `/environments` | `architecture:environment:manage`，或 `architecture:manage`；创建 ACTIVE 具体环境 |
| PUT | `/environments/{id}` | 同上；`body {code,name,typeCode,description,remark,rowVersion}` |
| POST | `/environments/{id}/deactivate`、`/reactivate`、`/delete` | 同上；`body {rowVersion}`；删除要求未被资源申请引用 |

环境类型字段：`code,name`，来源于系统字典 `ARCH_ENVIRONMENT_TYPE` 启用参数项。具体环境字段：`id,code,name,typeCode,typeName,status,description,remark,rowVersion,createdBy,updatedBy,createdAt,updatedAt`。

环境详情 `resourceSummary` 区分申请态与实际态：申请态统计已批准资源申请明细汇总；实际态字段 `actualCpuCores,actualMemoryGb,actualStorageGb,actualNodeCount` 在本需求固定为 0，等待后续搭建任务接入实际资源台账。

资源申请资源根：`/api/architecture/resource-requests`。申请必须固定选择一个 ACTIVE 物理子系统和一个 ACTIVE 具体环境，可包含多条部署单元登记明细；部署单元必须 ACTIVE 且属于所选物理子系统。
申请联系人通过 `contactUserId` 选择租户内启用用户；来源任务号不再由资源申请维护。

| 方法 | 路径 | 权限与语义 |
| --- | --- | --- |
| GET | `/resource-requests` | `view/apply/manage`；查询 `status?,environmentId?,physicalSubsystemId?,limit,offset`；`view/apply` 只返回本人，`manage` 返回当前租户全部 |
| GET | `/resource-requests/{id}` | `view/apply/manage`；本人或管理范围，跨租户 404 |
| POST | `/resource-requests` | `apply/manage`；创建草稿 `DRAFT` |
| PUT | `/resource-requests/{id}` | `apply/manage`；仅本人 `DRAFT/RETURNED`；其它人 403/409 |
| POST | `/resource-requests/{id}/submit` | `apply/manage`；固化申请快照与 SHA-256 摘要并启动审批；`body {rowVersion}` |
| POST | `/resource-requests/{id}/cancel` | `apply/manage`；草稿/退回同步取消；审批中终止流程后由 `TERMINATED` 事件确认；`body {rowVersion}` |

创建/更新请求体：

```json
{
  "physicalSubsystemId": 501,
  "environmentId": 9001,
  "contactUserId": 10001,
  "requestType": "INITIAL",
  "reason": "新建开发环境资源",
  "items": [
    {
      "deploymentUnitId": 7001,
      "databaseStorageGb": 0,
      "fileStorageGb": 100,
      "networkZone": "开放区",
      "serverType": "architecture.server-type.container",
      "cpuCores": 2,
      "memoryGb": 4,
      "appWebGroupCount": 1,
      "plannedNodeCount": 2,
      "sidecarCpuCores": 0,
      "sidecarMemoryGb": 0,
      "hasSidecar": false,
      "databaseName": null,
      "databaseVersion": null,
      "jdkVersion": "architecture.jdk.jdk17",
      "middleware": "architecture.middleware.tomcat9",
      "operatingSystem": "architecture.os.rhel8-5",
      "extraCbsGb": 0,
      "localDiskGb": 0,
      "needsNft": false,
      "needsFserver": false,
      "needsJobexecutor": false,
      "remark": "应用资源登记"
    }
  ],
  "rowVersion": 0
}
```

明细请求体只接收申请人可填写的资源需求字段；物理子系统字段由服务端从物理子系统主数据带出并写入申请级快照，部署单元字段从部署单元主数据带出并写入明细快照。`DB` 部署单元仅接收数据库存储需求、数据库和数据库版本，`AP/WB/PL` 接收除 `DB` 专属字段外的资源、网络、技术栈和附加需求字段。服务器类型来源于 `ARCH_SERVER_TYPE`，默认 `architecture.server-type.container`；灾备模式和系统等级来源于物理子系统。JDK、中间件和产品化操作系统分别来源于 `ARCH_JDK_VERSION`、`ARCH_MIDDLEWARE`、`ARCH_OPERATING_SYSTEM`。容量、CPU、内存和存储类字段均为非负整数；`plannedNodeCount` 允许为 0，用于数据库存储类登记行。申请态汇总按 `cpuCores * plannedNodeCount + sidecarCpuCores`、`memoryGb * plannedNodeCount + sidecarMemoryGb` 和 `databaseStorageGb + fileStorageGb + extraCbsGb + localDiskGb` 计算。

`requestType=INITIAL|EXPANSION|SHRINK|ADJUSTMENT`；工单状态机：`DRAFT → IN_REVIEW → APPROVED/REJECTED`，`IN_REVIEW → RETURNED → IN_REVIEW`（新轮次），`DRAFT/RETURNED → CANCELLED`，`IN_REVIEW → CANCELLED`（终止事件确认）。批准只将资源申请置为 `APPROVED`，不生成机器、IP、环境部署实例、实际资源分配或搭建任务。

详情响应 `data`：`{request, items[], history[]}`。`request` 含 `id,requestNo,physicalSubsystemId,physicalSubsystemCode,physicalSubsystemShortName,physicalSubsystemName,physicalSubsystemBusinessGroupName,physicalSubsystemSystemLevelCode,physicalSubsystemDeploymentPlatform,physicalSubsystemDisasterRecoveryMode,environmentId,environmentCode,environmentName,environmentTypeName,applicantId,contactUserId,requestType,reason,status,currentBusinessRound,cancellationRequested,rowVersion,createdBy,updatedBy,createdAt,updatedAt`。`items[]` 含 `id,itemSeq,deploymentUnitId,deploymentUnitCode,deploymentUnitName,deploymentUnitKind,relatedDeploymentUnitName,deploymentUnitDescription,deploymentUnitType,databaseStorageGb,fileStorageGb,networkZone,serverType,cpuCores,memoryGb,appWebGroupCount,plannedNodeCount,totalCpuCores,totalMemoryGb,sidecarCpuCores,sidecarMemoryGb,sidecarMemoryRatio,hasSidecar,databaseName,databaseVersion,jdkVersion,middleware,operatingSystem,extraCbsGb,localDiskGb,needsNft,needsFserver,needsJobexecutor,remark`。`history[]` 为不可变业务事件。

工作流与审计：

- 固定流程编码 `architecture.resource-request`，业务类型 `architecture_resource_request`，订阅键 `architecture.resource-request.lifecycle.v1`；审批节点为单一 ROLE（角色 114 `ENVIRONMENT_RESOURCE_MANAGER`，ANY，空处理人 ERROR），只允许 `APPROVE/RETURN/REJECT`；V92 预置草稿定义（`900000000000050`），必须经平台既有发布入口发布后才能提交。
- 生命周期事件按 `subscriberKey + eventId` 幂等消费并校验租户、业务键、实例、轮次与摘要。
- 写操作审计：`architecture.environment.*`、`architecture.resource-request.create/update/submit/cancel` 写入 `sys_operation_log`；环境类型维护走系统字典自身审计；工作流任务动作走 `wf_audit_event`。

## 网络专项工单（REQ-20260823-051）

资源根：`/api/architecture/network-work-orders`。三类工单（CLB/DNS/证书）共享工单引擎与
固定审批流程，但各自持有独立字段契约；平台只登记申请、办理过程与办理结果，不执行任何
外部 CLB/DNS/证书动作。

| 方法 | 路径 | 权限与语义 |
| --- | --- | --- |
| GET | `.../network-work-orders` | `view`/`apply`/`manage`；查询 `kind?,status?,limit,offset`；`view`/`apply` 只返回本人，`manage` 返回当前租户全部；响应 `NetworkWorkOrderSummary[]` |
| GET | `.../network-work-orders/{id}` | `view`/`apply`/`manage`；本人或管理范围，跨租户 404 |
| POST | `.../network-work-orders` | `apply`/`manage`；创建草稿 `DRAFT` |
| PUT | `.../network-work-orders/{id}` | `apply`/`manage`；仅本人 `DRAFT/RETURNED`；其它人 403/409 |
| POST | `.../{id}/submit` | `apply`/`manage`；固化工单快照与 SHA-256 摘要并启动新流程轮次；`body {rowVersion}` |
| POST | `.../{id}/cancel` | `apply`/`manage`；草稿/退回同步取消；审批中先登记并调用工作流终止，收到 `TERMINATED` 事件后终态化；`body {rowVersion}` |
| POST | `.../{id}/handling-result` | `manage`；状态 `IN_REVIEW` 或 `COMPLETED` 时登记/更新办理结果与凭证附件；`body {rowVersion, resultStatus, resultDescription?, resultAttachmentIds[]}` |
| POST | `.../{id}/attachments/{attachmentId}/remove` | `apply`/`manage`；仅 `DRAFT/RETURNED` 且删除授权通过附件策略；`body {rowVersion}` |

状态机：`DRAFT → IN_REVIEW → COMPLETED/REJECTED`，`IN_REVIEW → RETURNED → IN_REVIEW`（新轮次），
`DRAFT/RETURNED → CANCELLED`（同步），`IN_REVIEW → CANCELLED`（`TERMINATED` 事件确认）。
批准只把工单推进到 `COMPLETED`（外部配置已办理并登记），不产生任何主记录发布。

工单类型与动作：

| kind | 动作 | 载荷契约（payload，服务端强类型校验） |
| --- | --- | --- |
| `CLB` | `OPEN/ADJUST` | `{clbName, purpose, description?}`；subject = clbName |
| `DNS` | `ADD/CHANGE/REMOVE` | `{domainName, purpose, description?}`；subject = 小写 domainName |
| `CERT` | `APPLY/RENEW/REVOKE` | `{certType: SSL\|EXTERNAL, subjectName, purpose, description?}`；subject = subjectName；附件扩展名黑名单 `key/pem/pfx/p12/jks/keystore` |

详情响应 `data`：`{workOrder, payload, attachmentIds[], resultAttachmentIds[], history[]}`；
`workOrder` 含 `id,kind,actionType,subject,applicantId,reason,status,resultStatus,
resultDescription,currentBusinessRound,cancellationRequested,rowVersion,createdBy,updatedBy,
createdAt,updatedAt`；`history[]` 为不可变业务事件。

工作流与附件：

- 固定流程编码 `architecture.network.work-order`，业务类型 `architecture_network_work_order`，
  订阅键 `architecture.network.work-order.lifecycle.v1`；审批节点为单一 ROLE（角色 113
  `NETWORK_MANAGER`，ANY，空处理人 ERROR），只允许 `APPROVE/RETURN/REJECT`；V90 预置草稿
  定义（`900000000000032`），必须经平台既有发布入口（`POST /api/workflows/definitions/900000000000032/publish`）
  生成 Flowable deployment 后才能提交。
- 生命周期事件按 `subscriberKey + eventId` 幂等消费并校验租户、业务键、实例、轮次与摘要。
- 附件业务类型 `architecture_network_work_order`，业务键为工单 id；授权策略
  `NetworkAttachmentAccessPolicy`（读/预览/下载需可读工单，删除仅草稿/退回且本人或管理）。
- 写操作审计：`architecture.network-work-order.create/update/submit/cancel/result/attachment-remove`
  写入 `sys_operation_log`；工作流任务动作走 `wf_audit_event`。

错误码：400 字段/状态/附件黑名单；401 未认证；403 权限/归属/删除授权；404/code `40400`
当前租户资源不存在；409 行版本、状态机、流程实例已结束。

## 明确不提供

- `/api/architecture/form-schemas/**` 或任何动态表单 schema。
- 绕过工单直接修改发布主记录的 HTTP 写接口（旧 POST/PUT/DELETE 保留路由但只返回 409 `ARCHITECTURE_WORK_ORDER_REQUIRED`）。
- 客户端提供 tenant、团队名称快照、物理联系人、电话或审计字段的写入能力。
- 部署单元编号、物理归属或版本行的修改能力；已发布部署单元的显示内容只能通过发布新版本改变。
- 资源申请批准后的真实资源分配、机器/IP、环境部署实例、网络连通、DNS/证书/CLB 实际配置或搭建任务生成；这些能力由后续需求另行定义。
- 绕过引用守卫的部署单元作废；引用检查器不可判定时一律失败关闭。
- 直接访问 `com.ccb.system.internal.*` 或 system 私有数据表。
- 真实 AI、外部引用 provider 或业务模块之外的引用检查实现（`com.ccb.architecture.integration` SPI 预留，provider 为空视为无外部引用）。

## 架构规范（REQ-20260823-051）

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

## 架构决策（REQ-20260823-051）

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

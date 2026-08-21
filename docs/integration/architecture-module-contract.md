# 架构子系统模块集成契约

适用需求：`REQ-20260812-021`。本契约冻结逻辑子系统、物理子系统和受限选项 API 的 HTTP 边界。V1 使用固定强类型表单，不提供表单 schema、启停用或任意字段接口。

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
| GET | `/logical-subsystems` | `architecture:logical:list` |
| GET | `/logical-subsystems/{id}` | `architecture:logical:list` |
| POST | `/logical-subsystems` | `architecture:logical:create` |
| PUT | `/logical-subsystems/{id}` | `architecture:logical:update` |
| DELETE | `/logical-subsystems/{id}` | `architecture:logical:delete` |

列表查询只接受 `page,size,code,shortName,name,businessOrgId`。

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

列表记录和详情的字段固定为：`id,code,shortName,name,businessOrgId,deploymentPlatformCode,systemTypeCode,systemOwnershipCode,contactUserId,description,remark,createdBy,updatedBy,createdAt,updatedAt`。

## 物理子系统

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/physical-subsystems` | `architecture:physical:list` |
| GET | `/physical-subsystems/{id}` | `architecture:physical:list` |
| POST | `/physical-subsystems` | `architecture:physical:create` |
| PUT | `/physical-subsystems/{id}` | `architecture:physical:update` |
| DELETE | `/physical-subsystems/{id}` | `architecture:physical:delete` |

列表查询只接受 `page,size,code,shortName,name,businessGroupName,responsibleTeamOrgId,logicalSubsystemId`。

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

`id,code,shortName,name,logicalSubsystemId,logicalSubsystemCode,logicalSubsystemName,businessGroupName,responsibleTeamOrgId,responsibleTeamDisplayName,responsibleTeamValid,runtimeCode,systemLevelCode,developmentFrameworkCode,ownerUserId,ownerDisplayName,description,remark,createdBy,createdByDisplayName,updatedBy,createdAt,updatedAt`。

`responsibleTeamDisplayName` 在组织仍活动时取当前名称，否则取保存时的服务端快照；`responsibleTeamValid=false` 时编辑必须重新选择活动组织。`createdByDisplayName` 由服务端按 `createdBy` 投影当前租户用户显示名，历史用户不可读时可为 `null`。物理子系统不维护联系人或联系电话；请求不能提交联系人、团队名称快照、电话、状态或审计字段。

## 选项 API

路径中的资源上下文只允许 `logical-subsystem` 和 `physical-subsystem`。已知上下文分别只检查自己的 `list` 权限，不以两项权限 OR 放行；未知或不支持的上下文返回 404/code `40400`。

| 方法与路径 | 查询 | 权限 | `data` |
| --- | --- | --- | --- |
| `GET /options/logical-subsystem/organizations` | `page,size,keyword?` | `architecture:logical:list` | `PageResult<OrganizationOption>` |
| `GET /options/physical-subsystem/organizations` | `page,size,keyword?` | `architecture:physical:list` | `PageResult<OrganizationOption>` |
| `GET /options/logical-subsystem/users` | `page,size,keyword?` | `architecture:logical:list` | `PageResult<UserOption>` |
| `GET /options/physical-subsystem/users` | `page,size,keyword?` | `architecture:physical:list` | `PageResult<UserOption>` |
| `GET /options/logical-subsystem/parameters/{categoryCode}` | 无 | `architecture:logical:list` | `ParameterOption[]` |
| `GET /options/physical-subsystem/parameters/{categoryCode}` | 无 | `architecture:physical:list` | `ParameterOption[]` |
| `GET /options/physical-subsystem/logical-subsystems` | `page,size,code?,name?` | `architecture:physical:list` | `PageResult<LogicalSubsystemOption>` |

选项记录必须精确使用以下字段：

| DTO | 字段 | 可空性与说明 |
| --- | --- | --- |
| `OrganizationOption` | `id,name,parentId,pathLabel` | 根组织 `parentId=null`；只返回活动组织 |
| `UserOption` | `id,displayName,username,phone` | `phone` 显式允许 `null`；只返回活动用户 |
| `ParameterOption` | `code,label` | 不分页 |
| `LogicalSubsystemOption` | `id,code,name` | 只返回当前租户未删除记录 |

逻辑上下文参数分类白名单为 `ARCH_DEPLOYMENT_PLATFORM`、`ARCH_SYSTEM_TYPE`、`ARCH_SYSTEM_OWNERSHIP`；物理上下文为 `ARCH_RUNTIME`、`ARCH_SYSTEM_LEVEL`、`ARCH_DEVELOPMENT_FRAMEWORK`。跨上下文分类返回 400。

选项响应不得包含密码散列、头像对象键、活动状态、删除标记、租户或平台内部管理字段。业务用户不需要 system 用户、组织或参数管理权限。

## 明确不提供

- `/api/architecture/form-schemas/**` 或任何动态表单 schema。
- 子系统启用、停用或 status 写接口。
- 客户端提供 tenant、团队名称快照、物理联系人、电话或审计字段的写入能力。
- 直接访问 `com.ccb.system.internal.*` 或 system 私有数据表。

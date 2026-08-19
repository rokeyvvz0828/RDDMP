# Project Context 公共契约

## 所有权与边界

`business/project` 是项目主数据、项目成员和项目审计的唯一数据 Owner。项目资产库及其他业务模块只能依赖 `com.ccb.project.api`、`com.ccb.project.model` 和项目 REST API，不得查询或联接 `pm_project`、`pm_project_member`、`pm_project_audit_event`。

`platform/system` 继续拥有 `sys_user`。`UserDirectory` 只返回同租户、未删除、启用用户的 `id`、`username`、`displayName`、`orgId`、`orgName`，不暴露密码、手机号、头像对象键或角色私有关系。project 不为 `sys_user` 建外键，也不读取该表。

## 服务端授权

有效操作始终是以下三项的交集：

1. 调用入口的 Spring Security 平台 RBAC；
2. `ProjectContextPort` 根据认证 `AuthUser` 解析的项目成员角色；
3. 项目状态允许的操作。

`ProjectContextPort` 不替代调用模块自身的 RBAC。浏览器提交的租户、成员角色和动作结论不可信。

| 角色 | VIEW | WRITE | MANAGE_MEMBERS | MANAGE_PROJECT |
| --- | --- | --- | --- | --- |
| OWNER | 允许 | 允许 | 允许 | 允许 |
| ADMIN | 允许 | 允许 | 允许 | 拒绝 |
| MEMBER | 允许 | 允许 | 拒绝 | 拒绝 |
| VIEWER | 允许 | 拒绝 | 拒绝 | 拒绝 |

`ARCHIVED` 项目对已有查看范围的成员只允许 `VIEW`。恢复项目保留全部成员关系，再按平台 RBAC 和原角色重新计算写范围。

## Java 契约

```java
public interface UserDirectory {
    PageResult<UserDirectoryUser> searchActive(long tenantId, String keyword, PageQuery pageQuery);
    Map<Long, UserDirectoryUser> requireActive(long tenantId, Set<Long> userIds);
}

public interface ProjectContextPort {
    ProjectSummary requireAccess(long projectId, AuthUser user, ProjectAction action);
    ProjectMembership membership(long projectId, AuthUser user);
    List<ProjectSummary> available(AuthUser user);
}
```

`ProjectRole` 固定为 `OWNER`、`ADMIN`、`MEMBER`、`VIEWER`；`ProjectStatus` 固定为 `ACTIVE`、`ARCHIVED`；`ProjectAction` 固定为 `VIEW`、`WRITE`、`MANAGE_MEMBERS`、`MANAGE_PROJECT`。

不可见项目的不存在、跨租户和非成员情形使用相同拒绝语义，不向调用方泄露项目是否存在。项目和成员写操作必须在项目事务内保留最小审计事件。

## REST 契约

所有入口使用认证用户中的租户和用户标识，不接受浏览器提交 `tenantId`、当前角色或允许动作。响应统一为 `ApiResponse<T>`，分页数据为 `{ records, total, page, size }`。

| 方法与路径 | 请求 | 响应 data | 平台权限 |
| --- | --- | --- | --- |
| `GET /api/projects` | `page`、`size`、可选 `keyword`、`status` | `PageResult<ProjectSummary>` | `project:list` |
| `GET /api/projects/available` | 无 | `ProjectSummary[]` | `project:list` |
| `GET /api/projects/{projectId}` | 路径 ID | `ProjectSummary` | `project:list` |
| `POST /api/projects` | `{ projectCode, projectName }` | `ProjectSummary` | `project:list:create` |
| `PUT /api/projects/{projectId}` | `{ projectName, version }` | `ProjectSummary` | `project:list:update` |
| `POST /api/projects/{projectId}/archive` | `{ version }` | `ProjectSummary` | `project:list:archive` |
| `POST /api/projects/{projectId}/restore` | `{ version }` | `ProjectSummary` | `project:list:archive` |
| `GET /api/projects/{projectId}/members` | 路径 ID | `ProjectMembership[]` | `project:list` |
| `GET /api/projects/member-candidates` | `page`、`size`、可选 `keyword` | `PageResult<UserDirectoryUser>` | `project:list:member` |
| `POST /api/projects/{projectId}/members` | `{ userId, role, version }` | `ProjectSummary` | `project:list:member` |
| `PATCH /api/projects/{projectId}/members/{userId}` | `{ role, version }` | `ProjectSummary` | `project:list:member` |
| `DELETE /api/projects/{projectId}/members/{userId}` | 查询参数 `version` | `ProjectSummary` | `project:list:member` |
| `POST /api/projects/{projectId}/owner-transfer` | `{ newOwnerUserId, version }` | `ProjectSummary` | `project:list:member` |

平台权限只允许请求进入业务层。更新项目和负责人转移还要求 `MANAGE_PROJECT`，成员写操作要求 `MANAGE_MEMBERS`；归档和恢复只允许当前 `OWNER`。`ARCHIVED` 项目的公开动作集合只含 `VIEW`，恢复入口在 project 内部再次确认负责人身份。

`projectCode` 最大 64 个字符，首字符为字母或数字，其余只允许字母、数字、点、下划线和短横线；`projectName` 最大 128 个字符。普通成员新增和角色变更只接受 `ADMIN`、`MEMBER`、`VIEWER`，`OWNER` 必须通过负责人转移入口设置。所有更新使用返回的 `version` 执行乐观锁检查。

## 字段映射

Java `ProjectSummary` 与 TypeScript `ProjectSummary` 使用同名 JSON 字段：`id`、`projectCode`、`projectName`、`status`、`ownerUserId`、`ownerDisplayName`、`currentRole`、`allowedActions`、`version`。前端不得补造更新时间或从按钮权限反推角色。

Java `ProjectMembership` 与 TypeScript `ProjectMembership` 使用同名字段：`projectId`、`userId`、`username`、`displayName`、`role`、`allowedActions`。候选用户映射为 `id`、`username`、`displayName`、`orgId`、`orgName`，不包含敏感身份字段。

`web/src/stores/project-context.ts` 只消费 `GET /api/projects/available`。本地选择键按当前 `tenantId` 和 `userId` 隔离；每次刷新都使用服务端返回集合重新校验，失效选择不能继续作为业务请求的项目范围。

## 错误语义

| HTTP / code | 语义 | 前端行为 |
| --- | --- | --- |
| `400 / 40000` | 字段无效、用户无效、非法角色、负责人被直接移除等 | 在当前表单或成员弹层持续显示服务端消息 |
| `401 / 40100` | 会话无效 | 由统一 HTTP 层刷新会话或返回登录页 |
| `403 / 40300` | 缺平台权限，或项目不存在、跨租户、非成员、角色/状态不允许 | 列表入口显示无权限；实体操作不披露项目是否存在 |
| `409 / 40900` | 编号/成员重复、版本冲突或状态已变化 | 保留操作上下文，提示刷新后重试，不覆盖服务端新状态 |
| `500 / 50000` | 未预期服务端错误 | 显示可重试失败状态，不把失败结果写入本地项目上下文 |

前端操作显隐只改善体验，不能替代以上服务端 RBAC、项目成员、租户和状态校验。

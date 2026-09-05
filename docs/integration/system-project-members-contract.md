# 项目成员引用契约

`platform/system` 通过 `com.ccb.system.capability.ProjectMemberReferenceQuery` 向业务模块提供只读的项目成员引用。

## 约束

- 调用方必须传入认证主体，租户从 `AuthUser.tenantId()` 派生。
- 查询同时限定 `projectId`、租户、成员关系未删除、成员状态有效和用户未删除。
- 返回值只包含成员关系编号、用户编号、显示名和用户名，不返回密码、角色、组织私有字段或对象存储键。
- 业务模块不得直接查询 `pm_project_member`、`sys_user` 或其他 system 私有表。

## Java 契约

```java
List<ProjectMemberReference> findActiveMembers(AuthUser actor, long projectId);
Optional<ProjectMemberReference> findActiveMember(AuthUser actor, long projectId, long projectMemberId);
```

该能力只读，不改变既有项目成员管理 API 和权限语义。

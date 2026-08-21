# 系统用户目录公开契约

`com.ccb.system.model.UserDirectoryPort` 是平台系统模块向业务模块提供的只读用户目录。调用方必须传入当前认证用户的 `tenantId`，实现只返回该租户内 `status = 1` 且未删除的用户。

返回字段限定为用户主键、账号、显示名、组织主键、组织名称和手机号。业务模块可保存用户主键并展示这些字段，但不得通过该契约修改用户、角色或组织，也不得直接访问 `sys_user`、`sys_org` 表。

- `listActive(tenantId, keyword, limit)`：按账号、显示名或手机号模糊查询；服务端将 limit 限制在 1–100。
- `findActive(tenantId, userId)`：校验并读取一个有效用户，不存在、已停用、已删除或跨租户时返回空。

该契约不承诺返回敏感认证字段、密码、角色或完整个人资料。

# 项目组织架构设计

## 数据模型

- `pm_project_org` 保存项目独立组织树，使用 `project_id`、`tenant_id` 隔离项目和租户；`parent_id=0` 表示根节点。
- `pm_project_member.org_id` 保存成员在当前项目中的所属项目机构，可为空以兼容既有成员。
- `pm_project_member_role` 继续保存角色与项目成员的多对多关系；角色接口返回关联成员，角色保存时校验成员属于当前项目。

## 接口契约

- `GET /api/project/{projectId}/organizations` 返回项目组织节点树所需的平铺节点；项目详情同时通过 `project_organizations` 返回该数据。
- `POST/PUT/DELETE /api/project/{projectId}/organizations[/{organizationId}]` 维护项目组织节点。
- 成员新增/编辑 payload 增加 `org_id`；成员返回 `org_id`、`org_name`。
- 角色新增/编辑 payload 增加 `member_ids`；角色返回 `members` 和 `member_count`。

## 页面交互

项目组织架构页签采用左侧项目组织树、右侧成员与角色维护区。左侧提供“全部机构”，点击机构过滤成员；角色区始终显示项目角色，关联人员使用当前项目成员多选。组织节点删除前由服务端校验下级节点和成员引用。

桌面端使用局部滚动容器；移动端组织树位于维护区上方并切换为单列布局。新增/编辑组织节点使用现有弹窗风格，成员和角色沿用现有对话框、加载、空数据、错误和权限状态。

## 权限与审计

复用 `project:member:*` 和 `project:role:*` 权限维护成员、机构归属和角色关联；新增组织树操作复用项目更新权限并要求项目负责人或超级管理员。组织新增、修改、删除和成员/角色保存均写入项目操作日志。

---
id: REQ-20260906-066
status: ready
owner: rokeyvvz0828
module: business/data-migration + platform/infrastructure
---

# 数据迁移目录菜单权限清理

## 业务目标
权限管理页面目前对二级目录“数迁资产内容管理”（菜单 720）和“基础资料管理”（菜单 740）显示“删除”选项。
平台设计规则要求 `type='directory'` 的目录菜单不提供动作选项，目录仅作为组织容器存在。
本需求仅清理数据迁移模块为目录菜单产生的冗余权限节点，不修改平台系统目录接口和权限管理页代码。

## 现状与根因
1. `V84__data_migration_component_enrichment.sql` 对菜单区间 700-744 统一生成 read/create/update/delete 权限节点，
   其中包含目录 720（`/data-migration/content`）和 740（`/data-migration/base`）；
2. 前置任务新增的 `V193__data_migration_remove_directory_write_permissions.sql` 只删除 7200-7203 / 7400-7403，
   遗漏了 `menu_id*10+4` 的删除节点（实际数据：7204、7404），因此权限管理页仍看到“删除”；
3. `V190` 对 `sys_menu_permission` 的斜杠清理只覆盖基础权限码，未覆盖 `:create/:update/:delete` 变体，
   导致 726-731 子菜单（参数/依赖/程序/专题/投产演练/问题）仍残留 `data-migration:content/…:create` 斜杠权限码。

## 范围

### 本次实施
- 新增 Flyway 追加迁移，幂等清理数据迁移目录菜单的权限节点（7204、7404 等）；
- 修复残留的斜杠动作权限码（`data-migration:content/…:create/update/delete`）；
- 关联需求文档与任务记录。

### 本次不实施
- 不修改平台系统目录接口（`SystemService.permissionCatalog`）；
- 不修改平台权限管理页（`RolePermissionView.vue`）；
- 不修改菜单管理页（`ModuleView.vue`）对目录的编辑/删除按钮；
- 不调整其他业务模块（需求/工作流/AI/投产）已存在的目录权限节点；
- 不修改已发布的历史迁移脚本。

## 验收标准
1. 数据库 `sys_menu_permission` 中 `/data-migration` 目录节点不再存在权限节点；
2. `sys_menu_permission` 不存在 `data-migration:content/` 斜杠权限码；
3. 本地 MySQL 执行新增迁移后，目录 720/740 的“删除”选项因权限节点移除而不复存在；
4. 平台 `SystemService.java` 与 `RolePermissionView.vue` 无任何改动。

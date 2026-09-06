# REQ-20260906-065: 数据迁移模块权限硬编码修复

## 状态
`ready`

## 目标
将数据迁移模块中所有菜单功能从硬编码权限检查改为完全使用"权限管理"（RBAC）控制权限，消除角色名称硬编码、路由守卫缺失、权限链不一致等问题。

## 背景
数据迁移模块存在 8 处权限硬编码问题，涉及前端路由、前端权限判断、后端权限服务、后端 Controller 注解和数据库菜单定义。这些问题导致：
- 前端路由守卫不拦截无权限访问
- 角色代码硬编码在前端和后端，新角色无法自动获得权限
- 权限链不一致，部分页面缺少 `data-migration:manage` 回退
- 数据库菜单 `permission_code` 格式不统一

## 验收标准

### 必须（Must）
1. 所有 data-migration 路由在 `meta` 中包含 `permission` 和 `menuPath`，路由守卫能拦截无权限访问
2. 后端 `DataMigrationPermissionService.isAdmin()` 不再硬编码角色名称，改用权限码查询
3. 前端 `MappingsPage.vue` 不再使用角色代码判断权限，改为权限码判断
4. 所有 Controller 的 `@PreAuthorize` 注解包含 `data-migration:manage`
5. 数据库菜单 `permission_code` 使用冒号格式（`data-migration:content:reports`）
6. 缺失的菜单（Dependencies/ValidationRules/Parameters）有对应的菜单记录和权限节点
7. 菜单权限正确授予管理员(200)和开发人员(201)角色

### 不应该（Should Not）
- 不修改已发布的 Flyway 迁移脚本
- 不改变现有业务逻辑
- 不引入新的依赖

## 影响范围
- **前端**：`web/src/router/index.ts`、`web/src/modules/data-migration/` 下多个文件
- **后端**：`server/src/modules/data-migration/` 下 Controller 和 Service
- **数据库**：新增 V190 迁移脚本
- **公共能力**：无变更

## 风险
- 数据库迁移执行后，如果权限节点 ID 与现有 ID 冲突，INSERT IGNORE 会静默跳过（幂等安全）
- 路由守卫新增 permission 检查，如果用户缺少对应权限码，会被重定向到 /dashboard

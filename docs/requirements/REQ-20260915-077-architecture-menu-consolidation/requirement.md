---
id: REQ-20260915-077
status: ready
owner: rokeyvvz0828
module: business/architecture
---

# 架构菜单收敛：变更工单入口内聚与部署单元初始化导入整合

## 业务目标

收敛架构资产管理菜单：把「架构子系统变更工单」收敛为物理子系统页内的入口，把「部署单元初始化导入」从独立菜单整合为部署单元页内的按钮 + 抽屉，减少一级菜单数量，同时不丢失既有能力与权限。

## 范围

### 本次实施

- 隐藏菜单「架构子系统变更工单」（`sys_menu.id = 803`）：
  - 隐藏后，物理子系统页头部新增「变更工单」按钮，点击打开抽屉，抽屉内嵌现有变更工单列表能力（状态筛选、分页、详情、草稿编辑、空态与无权限态）。
  - 现有列表路由 `/architecture/subsystem-change-applications` 与组件保留（工单详情「返回列表」、表单提交后跳转仍指向它），权限码 `architecture:view/apply/manage` 不变。
- 隐藏菜单「部署单元初始化导入」（`sys_menu.id = 805`），并把该功能整合进「部署单元」页：
  - 部署单元页头部新增「初始化导入」按钮，仅 `architecture:deployment-unit:manage` 可见；点击打开抽屉。
  - 抽屉内保留原页面全部能力：下载模板、拖拽上传 `.xlsx`、解析预览与确认写入、导入批次台账（分页）、批次明细、错误报告下载、加载/空/失败/无权限状态。
  - 导入成功后刷新部署单元列表。
  - 移除 `/architecture/deployment-unit-imports` 路由与独立页面组件。
- 菜单隐藏通过追加一条 Flyway 迁移实现：仅将 803/805 置 `visible = 0`，保留 `deleted`、权限码、`sys_menu_permission` 与 `sys_role_menu` 授权，不改历史迁移。
- 覆盖桌面与手机视口、明暗主题、加载/空/失败/无权限、无页面级横向溢出。

### 本次不实施

- 不删除或改写 `sys_menu` 记录，不删除权限码，不改角色授权；隐藏只影响侧边菜单可见性与 `/auth/routes` 返回。
- 不改变变更工单与部署单元导入的后端接口、DTO、状态机、审批流程、权限与数据范围。
- 不在抽屉内新增直写数据表或绕过审批的入口。
- 不改写已发布 Flyway 迁移，不重命名既有路由、Java 包或数据表。

## 现状与规则

- 菜单由 `sys_menu` 驱动，`AuthRepository.findRoutes` 仅返回 `status = 1 AND visible = 1 AND deleted = 0` 的菜单；权限解析同时使用 `sys_menu_permission`（不按 visible 过滤）与 `sys_menu.permission_code`（按 visible 过滤），因此置 `visible = 0` 不会回收权限。
- `architecture:view/apply/manage` 由 `sys_menu_permission` 8031/8032/8033 提供；`architecture:deployment-unit:manage` 由 8042 提供。隐藏 803/805 不影响这两个权限。
- 变更工单列表页 `SubsystemChangeApplicationListPage.vue` 含状态筛选、分页、桌面表格与移动卡片；被详情页「返回列表」与表单提交后跳转引用。
- 部署单元初始化导入页 `DeploymentUnitImportPage.vue` 自包含上传、批次台账与预览/明细两个内层抽屉；仅被路由引用。
- 前端路由为 `web/src/router/index.ts` 静态定义，菜单 `component_path` 不参与前端路由注册，隐藏菜单不影响既有路由。

## 接口与数据

- 本次不新增接口。抽屉继续调用既有 `listSubsystemChangeApplications` 与 `deployment-unit-imports` 系列接口。
- 数据 Owner：`business/architecture`。
- 数据库迁移：追加 `V20260915120000__hide_subsystem_change_and_import_menus.sql`，将租户 1 的 803/805 置 `visible = 0`，并用失败关闭守卫校验两行确已隐藏且权限码仍存在。存量数据兼容、可回退（改回 `visible = 1`）。

## 验收标准

1. 登录后「架构管理 → 架构资产管理」下不再出现「架构子系统变更工单」与「部署单元初始化导入」；物理子系统、部署单元、交付单元菜单保持可见。
2. 物理子系统页头部「变更工单」按钮打开抽屉，抽屉内可见工单列表、状态筛选、分页与详情/编辑入口；无查看权限时显示无权限提示。
3. 部署单元页头部「初始化导入」按钮（仅有 `architecture:deployment-unit:manage` 时显示）打开抽屉，抽屉内可下载模板、上传 xlsx、看到批次台账与明细；导入成功提示并刷新部署单元列表。
4. `/architecture/deployment-unit-imports` 路由与页面不再存在（直接访问回落 404/默认页），`/architecture/subsystem-change-applications` 仍可访问。
5. `architecture:view/apply/manage` 与 `architecture:deployment-unit:manage` 权限对既有角色不变；`node scripts/check-flyway-migrations.mjs` 与范围检查、前端构建通过。
6. 375x812、390x844、430x932 与桌面视口下抽屉无页面级横向溢出、无遮挡，明暗主题可读。

## 测试与发布

- 必须执行的测试：`mvn -pl :ccb-platform-infrastructure -am test`（如适用）、`node scripts/check-flyway-migrations.mjs`、`npm --prefix web run build`、`node scripts/check-codex-scope.mjs --scope ... --base dev-ivanh --head HEAD`、`git diff --check`。
- 浏览器验收：以 `admin` 账号核对菜单树、物理子系统变更工单抽屉、部署单元初始化导入抽屉；以仅具备部分权限的角色核对按钮显隐与无权限态。
- 上线验证：迁移执行后重新登录，核对 `/api/auth/routes` 树与权限集合。
- 回退或补偿：把 803/805 的 `visible` 改回 1（新增补偿迁移或手工运维脚本），并回退前端提交。
- 风险与人工复核人：菜单可见性影响所有架构角色，需架构模块 Owner 复核；隐藏菜单不应回收权限。

## 交付与审批

- 实施分支：`feat/REQ-20260915-077-architecture-menu-consolidation`（从 `dev-ivanh` 新建）。
- 用户决策：2026-09-15 确认「变更工单菜单隐藏 + 物理子系统页抽屉入口」（方案 A）与「初始化导入整合为部署单元页按钮 + 抽屉」。

## 已知问题

- 变更工单列表抽屉内的「详情」「编辑」会跳转到既有独立路由，返回时落到隐藏的列表页而非物理子系统页；本次按最小改动保留该行为。

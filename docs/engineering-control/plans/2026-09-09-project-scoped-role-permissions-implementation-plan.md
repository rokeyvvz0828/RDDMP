# 项目级角色权限实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：2
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-09-project-scoped-role-permissions-design.md`
- 状态：可移交

**目标：** 将项目业务权限迁移为按当前项目的项目角色授权，同时以系统设置“权限维护”集中管理权限目录，并保持系统管理权限全局有效。

**架构：** `sys_menu_permission` 继续作为权限目录，V201 增加项目角色权限关系并初始化存量项目。Security 根据 `X-Project-Id` 合并系统级权限与当前项目角色权限，System 负责目录和项目角色授权，前端项目切换器在提交选择前刷新授权上下文。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Spring Security、MySQL 8.4、Flyway、Vue 3、TypeScript、Pinia、Axios、Element Plus。

## 全局约束

- 保持现有 Java 包名、Maven artifact、JWT 格式、统一 API 响应与顶部项目切换入口。
- 系统管理权限仅由系统角色授予；项目角色不能授予系统管理权限。
- 项目业务权限必须由服务端按租户、用户、项目、成员和角色校验，前端显隐不替代后端授权。
- Flyway 只新增 `V201__project_scoped_role_permissions.sql`，不修改 V1-V200。
- 权限目录及项目角色权限写操作保留审计；不接触 `.env`、生产数据、凭据和外部系统。
- 只修改 REQ-20260909-074 范围文件中的 `writable_paths`。
- 实施前 `node scripts/check-development-entry.mjs --require-plugin` 必须通过；当前 `unknown error` 未解除时禁止进入产品代码执行。

---

## 文件职责地图

- `server/src/platform/infrastructure/src/main/resources/db/migration/V201__project_scoped_role_permissions.sql`（candidate-new）：创建项目角色权限关系、菜单调整和存量角色初始化。
- `server/src/platform/system/src/main/java/com/ccb/system/service/SystemService.java`（existing）：权限目录生命周期、引用保护和系统权限审计。
- `server/src/platform/system/src/main/java/com/ccb/system/web/SystemController.java`（existing）：权限目录 HTTP 契约。
- `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`（existing）：项目角色权限读写、项目作用域校验和项目业务动作授权。
- `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectController.java`（existing）：项目角色权限 HTTP 契约。
- `server/src/platform/security/src/main/java/com/ccb/security/repository/AuthRepository.java`（existing）：查询系统权限、项目角色权限并集和项目化路由。
- `server/src/platform/security/src/main/java/com/ccb/security/service/AuthService.java`（existing）：按可选项目上下文组装个人信息、权限和路由。
- `server/src/platform/security/src/main/java/com/ccb/security/web/JwtAuthenticationFilter.java`（existing）：从请求读取项目上下文并建立服务端 authorities。
- `server/src/platform/security/src/main/java/com/ccb/security/web/AuthController.java`（existing）：`/me` 与 `/routes` 接收项目上下文。
- `web/src/api/http.ts`、`web/src/stores/project-context.ts`、`web/src/stores/auth.ts`（existing）：持久化项目 ID、附加请求头、原子刷新授权上下文。
- `web/src/views/RolePermissionView.vue`（existing）：改造为系统权限维护页面。
- `web/src/views/ModuleView.vue`（existing）：移除系统角色列表遗留的角色权限配置入口。
- `web/src/views/ProjectView.vue`（existing）：项目角色权限数量与可全屏权限抽屉。
- `web/src/views/AppLayout.vue`、`web/src/router/index.ts`（existing）：项目切换和无权路由恢复、菜单名称调整。
- `web/src/api/system.ts`、`web/src/api/project.ts`、`web/src/types/system.ts`、`web/src/types/project.ts`（existing）：前后端类型与 API 契约。

## 任务依赖图与并行策略

`T1 -> T2 -> T3 -> T4 -> T5 -> T6`。全部串行：T2-T5 共享权限范围和有效权限语义，先稳定生产者契约再改消费者，避免迁移、路由和接口鉴权暂时分叉。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 系统权限维护 | T1、T2、T5 |
| R2 项目角色配置权限 | T1、T3、T5 |
| R3 权限随项目切换并由服务端执行 | T3、T4、T5、T6 |
| R4 存量权限初始化与负责人兜底 | T1、T3、T4、T6 |
| R5 隔离与审计 | T2、T3、T4、T6 |

### T1：建立项目角色权限数据关系和迁移基线

**需求映射：** R1、R2、R4

**前置任务：** 无

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V201__project_scoped_role_permissions.sql`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectRolePermissionMigrationTest.java`（candidate-new）

**接口：**

- 消费：`sys_menu`、`sys_menu_permission`、`pm_project_role`、`pm_project_member_role` 现有表。
- 产出：`pm_project_role_permission(tenant_id, project_id, role_id, permission_id, created_at)`；唯一键 `(tenant_id, project_id, role_id, permission_id)`。

- [ ] 步骤 1：新增迁移测试，断言复合唯一键、租户/项目索引、PM 全部项目业务权限、非 PM 仅 `action_code='read'`、系统菜单权限不进入项目角色。
- [ ] 步骤 2：运行 `mvn -q "-Dnet.bytebuddy.experimental=true" -pl :ccb-system -am -Dtest=ProjectRolePermissionMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期因 V201 或表不存在失败。
- [ ] 步骤 3：编写 V201，创建关系表；将系统菜单 102 的名称/路由调整为权限维护入口；按系统菜单树排除 `system:*` 权限并初始化项目角色。项目负责人兜底由代码实现，不写冗余角色关系。
- [ ] 步骤 4：复跑聚焦测试，预期 0 失败；执行 SQL 静态检查，确认未修改 V1-V200。
- [ ] 步骤 5：记录迁移行数、索引和分类规则到 `execution-T1.json`，建立提交检查点 `feat(auth): add project role permission schema`。

**验收、证据与回滚：** V201 可在空库和已含项目数据的测试库执行；重复关系不会产生；PM/非 PM 初始化符合规则。回滚应用时保留新表，使用追加补偿迁移或恢复旧读取路径。

**停止条件：** 发现已存在 V201、项目经理角色不以 `PM` 标识、权限无法按菜单树稳定区分系统与项目作用域。

**升级条件：** 必须新增或修改 V201 之外的历史迁移，或需要删除现有 `sys_role_permission` 数据。

### T2：交付系统权限目录维护 API

**需求映射：** R1、R5

**前置任务：** T1

**文件：**

- 修改：`server/src/platform/system/src/main/java/com/ccb/system/service/SystemService.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/web/SystemController.java`
- 新建测试：`server/src/platform/system/src/test/java/com/ccb/system/service/SystemPermissionCatalogTest.java`（candidate-new）

**接口：**

- 产出：`GET/POST /api/system/permissions`、`PUT /api/system/permissions/{id}`、`PUT /api/system/permissions/{id}/status`、`DELETE /api/system/permissions/{id}`。
- 契约：编码创建后不可修改；菜单必须属于当前租户；删除前同时检查 `sys_role_permission` 与 `pm_project_role_permission`；所有写操作审计。

- [ ] 步骤 1：编写服务测试，覆盖列表过滤、编码唯一、编码不可变、菜单归属、引用删除拒绝、启停与审计。
- [ ] 步骤 2：运行 `mvn -q "-Dnet.bytebuddy.experimental=true" -pl :ccb-system -am -Dtest=SystemPermissionCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认当前接口缺失导致失败。
- [ ] 步骤 3：在 SystemService 实现参数化 SQL 和集中校验，在 SystemController 暴露 DTO 兼容的 Map/List HTTP 适配；继续使用 `system:role:list` 的系统管理授权，不把项目权限用于此接口。
- [ ] 步骤 4：复跑测试并增加未授权用户 403、跨租户 ID 不可见断言。
- [ ] 步骤 5：记录 API 请求/响应和失败语义，建立提交检查点 `feat(system): add permission catalog maintenance`。

**验收、证据与回滚：** 聚焦测试全部通过，权限编码不可被更新，引用权限删除返回明确业务错误。回滚为恢复只读 `permissionCatalog` 和旧路由，不删除目录数据。

**停止条件：** 现有审计 API 无法表达权限目录变更，或菜单/权限 ID 在租户内不唯一。

**升级条件：** 需要修改 shared 错误模型或跨模块公共 API。

### T3：交付项目角色权限 API 与项目内动作授权

**需求映射：** R2、R3、R4、R5

**前置任务：** T2

**文件：**

- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectController.java`
- 新建测试：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectRolePermissionServiceTest.java`（candidate-new）

**接口：**

- 产出：`GET /api/project/{projectId}/roles/{roleId}/permissions` 与 `PUT /api/project/{projectId}/roles/{roleId}/permissions`，沿用当前项目 API 的单数 `/api/project` 根路径。
- 产出：`ProjectService.requireAction(projectId, resource, action, user)`，以负责人/超级管理员兜底，否则查询当前项目成员角色权限并集。

- [ ] 步骤 1：建立测试，覆盖角色归属、系统权限排除、多角色并集、空权限拒绝、PM/负责人兜底、跨项目与跨租户拒绝、事务替换和审计。
- [ ] 步骤 2：运行聚焦测试，确认当前 `requireAction` 仍读取 `sys_role_permission` 且新端点不存在。
- [ ] 步骤 3：实现角色权限读写；角色列表增加 `permission_count`；将项目实体操作的 `requireAction` 调用传入实际 `projectId`，项目创建等无当前项目动作继续由全局系统权限控制。
- [ ] 步骤 4：复跑 ProjectService 全部测试，检查不存在逐行权限查询和字符串拼接用户输入。
- [ ] 步骤 5：记录权限决策矩阵，建立提交检查点 `feat(project): manage project role permissions`。

**验收、证据与回滚：** 普通成员只能执行当前项目角色授予动作，负责人和 PM 可管理，跨项目 roleId 失败。回滚为恢复旧 `sys_role_permission` 查询并保留关系数据。

**停止条件：** 某个项目业务动作无法确定唯一 `projectId`，或现有业务调用依赖无项目上下文的全局写权限。

**升级条件：** 需要修改 `server/src/modules/**` 才能完成基础项目管理验收，或权限范围规则与设计冲突。

### T4：交付请求级项目上下文和动态权限/路由

**需求映射：** R3、R4、R5

**前置任务：** T3

**文件：**

- 修改：`server/src/platform/security/src/main/java/com/ccb/security/repository/AuthRepository.java`
- 修改：`server/src/platform/security/src/main/java/com/ccb/security/service/AuthService.java`
- 修改：`server/src/platform/security/src/main/java/com/ccb/security/web/JwtAuthenticationFilter.java`
- 修改：`server/src/platform/security/src/main/java/com/ccb/security/web/AuthController.java`
- 新建测试：`server/src/platform/security/src/test/java/com/ccb/security/repository/AuthRepositoryProjectPermissionTest.java`（candidate-new）
- 修改测试：`server/src/platform/security/src/test/java/com/ccb/security/service/AuthServiceProfileTest.java`

**接口：**

- 消费：可选 `X-Project-Id: positive long`。
- 产出：`AuthService.permissions(AuthUser, Long projectId)`、`me(AuthUser, Long projectId)`、`routes(AuthUser, Long projectId)`；无项目上下文只返回系统级权限/路由。

- [ ] 步骤 1：建立测试矩阵：无项目仅系统权限、项目 A/B 差异、多角色并集、负责人/PM、非成员 403、停用权限即时消失、父菜单自动保留。
- [ ] 步骤 2：运行 security 聚焦测试，确认当前签名和查询不支持项目上下文。
- [ ] 步骤 3：AuthController 和 JWT filter 解析同一请求头；AuthRepository 分离系统权限与项目权限查询并合并；路由按有效动作保留父菜单；非法项目上下文明确拒绝，不静默清空身份。
- [ ] 步骤 4：运行 `mvn -q "-Dnet.bytebuddy.experimental=true" -pl :ccb-security -am test`，预期 0 失败；运行 boot 聚焦测试验证过滤器装配。
- [ ] 步骤 5：记录 A/B 权限集合与 403 样本，建立提交检查点 `feat(security): resolve permissions by project context`。

**验收、证据与回滚：** `/auth/me`、`/auth/routes` 与过滤器 authorities 使用同一解析规则。回滚为忽略 `X-Project-Id` 并恢复全局权限查询。

**停止条件：** Security 无法在不产生 platform 反向依赖的情况下读取项目权限，或过滤器与 System 的鉴权无法共享一致语义。

**升级条件：** 需要调整 `governance/modules.yaml`、JWT claim 或 shared 公共包。

### T5：交付权限维护、项目角色授权和原子项目切换界面

**需求映射：** R1、R2、R3

**前置任务：** T4

**文件：**

- 修改：`web/src/api/http.ts`、`web/src/api/system.ts`、`web/src/api/project.ts`
- 修改：`web/src/stores/auth.ts`、`web/src/stores/project-context.ts`
- 修改：`web/src/types/system.ts`、`web/src/types/project.ts`
- 修改：`web/src/views/RolePermissionView.vue`、`web/src/views/ProjectView.vue`、`web/src/views/AppLayout.vue`
- 修改：`web/src/views/ModuleView.vue`（仅移除系统角色旧权限入口）
- 修改：`web/src/router/index.ts`、`web/src/styles.css`

**接口：**

- 产出：project-context 保存当前项目 `id/ref`；Axios 对已选择项目附加 `X-Project-Id`，登录/刷新和明确无项目请求除外。
- 产出：auth store 提供 `loadProjectAuthorization(projectId)`，成功后一次性替换 user/routes；失败不覆盖原状态。
- 消费：T2 权限目录 API、T3 项目角色权限 API、T4 项目化 `/auth/me` 与 `/auth/routes`。

- [ ] 步骤 1：记录当前切换基线，确认目前 `select()` 后直接 reload 且权限不刷新；建立类型检查失败点。
- [ ] 步骤 2：扩展 API 和类型；项目上下文持久化项目 ID；HTTP 拦截器附加请求头并确保 refresh 重试保留原 header。
- [ ] 步骤 3：将 RolePermissionView 改为权限维护列表/抽屉，覆盖加载、空、失败、搜索、提交中、引用删除失败和启停确认；路由标题改为“权限维护”；移除系统角色列表中的旧权限配置入口。
- [ ] 步骤 4：项目设置角色列表显示 `permission_count`，增加“配置权限”按钮与可全屏抽屉，复用现有权限树、菜单内全选和搜索。
- [ ] 步骤 5：AppLayout 切换项目时先加载目标项目授权，成功才提交项目选择；失败保留原项目。路由守卫发现当前页无权时跳转工作台并显示一次提示。
- [ ] 步骤 6：运行 `npm --prefix web run build`，预期 vue-tsc 与 Vite 均退出 0；在 1280x800 和 390x844 检查无横向溢出、抽屉全屏和操作可达。
- [ ] 步骤 7：记录截图/DOM 指标和失败恢复证据，建立提交检查点 `feat(web): configure permissions by project role`。

**验收、证据与回滚：** 系统权限维护与项目角色配置可完成，项目 A/B 切换后权限同步变化。回滚为恢复旧 RolePermissionView 和 reload 式切换，服务端兼容旧无头请求仅限系统页面。

**停止条件：** 项目上下文只保存 code 无法稳定解析 ID，或业务页面在不 reload 时不监听项目变化且无法有界修复。

**升级条件：** 需要批量修改 `web/src/modules/**`，或必须改变公共 UI 组件契约。

### T6：集成、迁移和真实权限链路收敛验收

**需求映射：** R3、R4、R5

**前置任务：** T5

**文件：**

- 新建/更新证据：`.ai-control/requirements/req-20260909-074-project-scoped-role-permissions/execution-T*.json`、`observation-T*.json`、`convergence.json`、`state.json`、`handoff.json`

**接口：** 消费 T1-T5 的最终迁移、API 与 UI；不新增产品接口。

- [ ] 步骤 1：执行 `mvn -q "-Dnet.bytebuddy.experimental=true" -pl :ccb-system,:ccb-security,:ccb-boot -am test`、`npm --prefix web run build`、`git diff --check` 和当前范围检查，预期全部通过；记录既有治理故障不得冒充本需求失败。
- [ ] 步骤 2：在本地测试库执行 Flyway，查询每项目 PM/非 PM 权限数量、孤儿关系和系统权限误分配，预期为 0 个孤儿及 0 个系统权限项目关联。
- [ ] 步骤 3：以管理员、项目负责人和普通成员调用 API；项目 A/B 权限不同，缺头/伪造头/跨项目 roleId 返回预期状态。
- [ ] 步骤 4：真实浏览器验收系统权限维护、项目角色授权、A/B 切换、刷新、无权跳转、保存失败恢复、重复点击、明暗主题、桌面与手机视口。
- [ ] 步骤 5：由独立观察者形成原子反馈；存在 P0/P1 或菜单/接口分叉则回到对应任务纠偏，不进入收敛。
- [ ] 步骤 6：门禁通过后写 convergence，将任务前缀状态转为 `converged`，建立最终提交检查点。

**验收、证据与回滚：** R1-R5 均有自动化、API 或浏览器证据；回退按设计恢复旧读取路径并保留 V201 数据。

**停止条件：** Flyway 数据不满足初始化规则、任何跨项目越权、动态路由与 API 不一致、开发入口门禁仍失败。

**升级条件：** 需要生产数据修复、强制迁移、修改历史 Flyway、降低权限验收标准或接受 P0/P1 残余风险。

## 集成检查

- T1 后：V201 结构和初始化规则可重复验证。
- T3 后：System 项目 API 不再从系统角色读取项目业务动作权限。
- T4 后：`/auth/me`、路由和过滤器 authorities 对同一项目产生一致权限集合。
- T5 后：前端项目切换在授权加载成功后才提交状态。
- T6：后端测试、前端构建、Flyway、本地 API、真实浏览器和范围审计共同通过。

## 控制模型种子

以下均为 `hypotheses-only`，由 control-engineering 建模阶段验证：

- 被控边界候选：权限目录、项目角色授权、项目上下文、Security authorities、动态路由和前端按钮。
- 状态变量候选：当前项目 ID、系统权限集合、项目权限集合、有效权限并集、动态路由树、角色权限版本。
- 传感器候选：聚焦测试、SQL 迁移查询、`/auth/me` 与 `/auth/routes` 响应、受保护 API 状态码、浏览器菜单/按钮状态。
- 执行器候选：V201 关系初始化、目录 CRUD、角色权限替换、请求头注入、auth store 原子替换、路由守卫。
- 扰动候选：旧客户端缺头、用户同时拥有多个角色、权限被停用、多标签页项目不同、迁移库已有自定义角色。
- 时延候选：项目切换的两次 auth 请求、权限保存后下一请求生效、浏览器 reload/route guard 时序。

## 风险与用户批准

高风险动作包括权限来源切换、追加 Flyway 数据初始化、Security 过滤器变更和系统菜单调整。禁止修改历史迁移、清理旧权限关系或降低服务端校验。用户确认当前计划后，交接包才能标记 approved 并导入控制工程账本。

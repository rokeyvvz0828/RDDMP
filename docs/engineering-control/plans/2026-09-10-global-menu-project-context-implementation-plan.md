# 全局菜单项目上下文实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-10-global-menu-project-context-design.md`
- 状态：可移交

## 目标与全局约束

使 `/projects` 和 `/system/**` 成为不初始化也不显示项目选择器的全局页面；项目管理菜单由任一可访问项目中的 `project:project:list` 决定。项目业务路由、`X-Project-Id`、服务端数据范围和实体授权不变。

- 仅修改 REQ-20260909-075 的 `writable_paths`。
- 不修改 JWT、公开 API、项目业务表、V202 或项目内数据和权限边界。
- 全局菜单权限查询必须按租户、有效项目和有效成员角色过滤，并在失败时关闭项目管理入口。
- 不清除 localStorage 中已有项目选择；用户返回项目业务路由时沿用既有初始化机制。

## 文件职责地图

| 路径 | 状态 | 职责与事实依据 |
| --- | --- | --- |
| `server/src/platform/security/src/main/java/com/ccb/security/repository/AuthRepository.java` | 现有 | `findPermissions` 已按项目查询有效项目角色权限；新增聚合查询复用相同租户、成员、PM、Owner 与超级管理员语义。 |
| `server/src/platform/security/src/main/java/com/ccb/security/service/AuthService.java` | 现有 | `routes(user, projectId)` 构建动态菜单，`hasRoutePermission` 目前以 `*:access` 命名空间匹配入口。 |
| `server/src/platform/security/src/test/java/com/ccb/security/service/AuthServiceProjectContextTest.java` | 现有 | 覆盖项目上下文动态路由；更新为全局项目管理正例、其他项目权限反例和无权限反例。 |
| `web/src/router/index.ts` | 现有 | 壳层子路由和全局守卫；当前守卫无差别调用 `projectContext.initialize()`。 |
| `web/src/views/AppLayout.vue` | 现有 | 包含桌面顶部、普通头部和移动抽屉三处项目选择器。 |
| `docs/requirements/REQ-20260909-075-menu-regression-fix/**` | 已修改 | 本需求的验收、范围和回退事实源。 |
| `.ai-control/requirements/req-20260909-075-menu-regression-fix/*.json` | 现有 | 任务执行、观测和收敛证据。 |

## 任务依赖图与并行策略

`T1 -> T2 -> T3 -> T4`。四项串行：T2 必须消费 T1 明确的无项目上下文动态菜单语义，T3 组合验收前两项，T4 修复认证完成时缺失项目上下文造成的菜单快照退化。不存在安全的并行写入组。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1：全局路由不初始化或展示项目上下文 | T2, T3 |
| R2：任一可访问项目的项目管理权限激活入口 | T1, T3 |
| R3：项目内授权保持项目范围 | T1, T2, T3 |
| R4：缺失本地项目 ID 时恢复可访问项目和项目菜单 | T4 |

### T1：无项目上下文的项目管理菜单授权

#### 需求映射与前置事实

**需求映射：** R2, R3
**前置任务：** 无
`AuthService.routes(user, null)` 当前只消费系统权限，且 `/projects` 的 `project:access` 会被任意 `project:*` 命名空间权限激活。需要将无项目上下文下的项目管理可见性收敛到任一可访问项目的 `project:project:list`。

#### 文件边界与接口

- 修改：`server/src/platform/security/src/main/java/com/ccb/security/repository/AuthRepository.java`
- 修改：`server/src/platform/security/src/main/java/com/ccb/security/service/AuthService.java`
- 测试：`server/src/platform/security/src/test/java/com/ccb/security/service/AuthServiceProjectContextTest.java`
- 消费：现有 `findPermissions(userId, tenantId, projectId)` 与 `RouteNode.permissionCode()`。
- 产出：`AuthRepository` 的租户隔离聚合项目管理权限查询；`AuthService.routes(user, null)` 可返回项目管理入口；`project:access` 仅由 `project:project:list` 激活。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：建立基准测试。**

  在 `AuthServiceProjectContextTest` 添加或替换用例，构造：任一项目存在 `project:project:list`、仅存在 `project:plan:list`、以及无项目权限。断言无项目上下文的路由树分别含 `/projects`、不含 `/projects`、不含 `/projects`。

- [ ] **步骤 2：运行基准检查。**

  运行：`mvn -pl :ccb-security -am -Dtest=AuthServiceProjectContextTest -Dsurefire.failIfNoSpecifiedTests=false test`
  预期：新增正例在现有实现下失败，说明动态路由仍依赖当前项目或规则过宽。
  证据：退出码与失败断言。

- [ ] **步骤 3：实施最小授权变更。**

  在 `AuthRepository` 新增只返回 `project:project:list` 的聚合权限查询，复用当前项目权限查询中的 tenant、未删除项目、Owner、PM、有效成员角色和 `SUPER_ADMIN` 条件。`AuthService.routes(user, null)` 合并该结果；`hasRoutePermission` 对 `project:access` 只接受 `project:project:list`，其余 `*:access` 保留命名空间行为。

- [ ] **步骤 4：运行局部与模块回归。**

  运行：`mvn -pl :ccb-security -am -Dtest=AuthServiceProjectContextTest -Dsurefire.failIfNoSpecifiedTests=false test`
  预期：全部测试通过，且没有变更 `GET /api/auth/routes` 响应结构。
  证据：退出码、测试数和三个路由断言。

- [ ] **步骤 5：建立提交检查点。**

  运行：`git diff --check -- server/src/platform/security/src/main/java/com/ccb/security/repository/AuthRepository.java server/src/platform/security/src/main/java/com/ccb/security/service/AuthService.java server/src/platform/security/src/test/java/com/ccb/security/service/AuthServiceProjectContextTest.java`
  预期：退出码 0。
  证据：范围内 diff 与检查结果；不创建提交，保留给用户的分支工作流。

#### 验收、证据与回滚

验收：无项目上下文且任一可访问项目有项目管理权限时动态路由含 `/projects`；仅其他项目权限或无权限时不含。
回滚：撤销 T1 的 `AuthRepository`、`AuthService` 和测试增量。
停止条件：实现需要改变 JWT、公开 API 或项目 API 的上下文校验。
升级条件：项目菜单目录实际没有 `project:access`/`project:project:list` 对应关系，或现有权限模型无法表达“任一可访问项目”。

### T2：全局路由的上下文与选择器呈现

#### 需求映射与前置事实

**需求映射：** R1, R3
**前置任务：** T1
路由守卫当前为所有已登录页面执行 `projectContext.initialize()`；`AppLayout` 在桌面顶部、普通头部和移动抽屉各渲染一处同样的选择器。

#### 文件边界与接口

- 修改：`web/src/router/index.ts`
- 修改：`web/src/views/AppLayout.vue`
- 消费：Vue Router `RouteMeta`、现有 `projectContext.initialize()`、`auth.fetchAuthorization(projectId?)` 与 `auth.applyAuthorization(...)`。
- 产出：`meta.projectContext = 'global'` 的 `/projects`（含详情）和 `/system/**` 路由；三处选择器的单一计算条件；全局路由用无项目参数刷新动态菜单。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：建立静态基准。**

  检查 `router.beforeEach` 与 `AppLayout` 中项目选择器：记录全局路由当前仍调用初始化且三处选择器无路由条件。
  运行：`rg -n "projectContext.initialize|project-context-select" web/src/router/index.ts web/src/views/AppLayout.vue`
  预期：找到无条件初始化和三处渲染点。
  证据：行号输出。

- [ ] **步骤 2：实施最小前端变更。**

  为 `/projects`、`/projects/:projectId` 及所有 `/system/**` 路由设置 `meta.projectContext: 'global'`。在守卫中，若该元数据存在则跳过 `projectContext.initialize()`，并用 `auth.fetchAuthorization()` 更新无项目授权快照；否则保留现有项目初始化与按项目刷新路径。`AppLayout` 新增一个路由元数据计算值，作为三处项目选择器的共同 `v-if` 条件。

- [ ] **步骤 3：运行前端构建。**

  运行：`npm --prefix web run build`
  预期：`vue-tsc` 与 Vite 生产构建成功。
  证据：退出码与构建摘要。

- [ ] **步骤 4：检查范围与格式。**

  运行：`git diff --check -- web/src/router/index.ts web/src/views/AppLayout.vue`
  预期：退出码 0。
  证据：范围内 diff 与检查结果。

#### 验收、证据与回滚

验收：全局路由不显示选择器；已选项目未被清除；项目业务路由仍显示选择器并走原有初始化。
回滚：撤销 T2 的路由元数据、守卫分支和 `AppLayout` 选择器条件。
停止条件：路由元数据不能覆盖系统通配路由，或需重构路由树才能表达范围。
升级条件：构建显示 RouteMeta 类型错误，或无项目授权快照导致系统菜单消失。

### T3：组合回归与桌面/移动验收

#### 需求映射与前置事实

**需求映射：** R1, R2, R3
**前置任务：** T1, T2
本任务消费 T1 的无项目动态菜单和 T2 的全局路由呈现，验证它们不会放宽项目 API 边界。

#### 文件边界与接口

- 修改：`.ai-control/requirements/req-20260909-075-menu-regression-fix/*.json`
- 消费：T1/T2 代码、`GET /api/auth/routes`、`X-Project-Id` 项目 API 契约。
- 产出：实际执行、观测和收敛证据；不新增产品接口。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：运行范围内自动化回归。**

  运行：`mvn -pl :ccb-security -am test`，随后运行 `npm --prefix web run build`。
  预期：两个命令均以 0 退出。
  证据：测试数、构建摘要与退出码。

- [ ] **步骤 2：验证授权 API。**

  在本地隔离环境中调用无 `X-Project-Id` 的 `GET /api/auth/routes`：项目管理权限正例含 `/projects`，其他项目权限与无权限反例不含；调用既有项目 API 时验证缺失或伪造 `X-Project-Id` 仍被拒绝。
  预期：全局菜单可达，项目 API 边界不变。
  证据：HTTP 状态、无敏感数据的路由断言和错误码。

- [ ] **步骤 3：浏览器验收。**

  在桌面 `1280x800` 与手机 `390x844` 中依次访问 `/projects`、一个 `/system/**` 页面和一个项目业务页面。检查前两者没有项目选择器、业务页面恢复显示、页面无横向溢出，并检查控制台无新增错误。
  预期：两视口满足 R1，操作和布局不遮挡。
  证据：路径、视口、菜单状态、控制台与 `document.documentElement.scrollWidth` 结果。

- [ ] **步骤 4：记录独立观测与收敛。**

  将实际命令、退出码、API 和浏览器证据写入当前任务前缀账本；运行 `git diff --check`。
  预期：可追溯证据且无空白检查结果。
  证据：账本文件、最终 diff 和残余风险。

#### 验收、证据与回滚

验收：R1-R3 都具有自动化或运行、浏览器和范围证据。
回滚：按 T1、T2 的文件增量回退；保留已发布的 V202 文本修复。
停止条件：任何项目 API 在缺少或伪造上下文时成功，或无权限用户获得 `/projects`。
升级条件：本地环境缺少无敏感配置的运行条件，导致 API/浏览器验收无法执行。

### T4：认证后的项目上下文恢复

#### 需求映射与前置事实

**需求映射：** R4
**前置任务：** T3
登录后固定请求无项目上下文授权，缺失 `ccb.current_project_id` 时只会保留全局菜单。现有 `project-context` store 已能从工作台选择可访问项目并持久化其 ID，因此认证 store 只需复用该选择结果，不得直接写入 localStorage 或改变项目 API 头契约。

#### 文件边界与接口

- 修改：`web/src/stores/auth.ts`
- 修改：当前需求、设计、计划和任务前缀账本证据。
- 消费：`useProjectContextStore().initialize()`、`currentId`、`fetchAuthorization(projectId)` 与 `applyAuthorization(...)`。
- 产出：缺失本地项目 ID 时，登录和会话恢复以全局授权为后备并在可访问项目存在时刷新项目级菜单。

#### 操作步骤、命令和预期信号

- [ ] 先加载无项目上下文授权，确保系统级菜单和身份数据可用。
- [ ] 仅在当前快照没有项目 ID 时初始化既有项目上下文；取得 ID 后刷新授权快照。
- [ ] 项目列表请求异常时保持无项目快照，不退出登录。
- [ ] 运行 `npm --prefix web run build` 和 `git diff --check`，并在本地运行服务中验证登录或刷新后的项目上下文恢复。

#### 验收、证据与回滚

验收：删除 `ccb.current_project_id` 后，具有可访问项目的用户重新登录或刷新时恢复有效项目 ID 和项目菜单；没有可访问项目或工作台请求失败时系统菜单仍可用。
回滚：撤销 `auth.ts` 中的授权恢复辅助逻辑；全局路由元数据、HTTP 头和项目 API 不变。
停止条件：需要修改项目工作台接口、项目存储键、JWT 或服务端授权。
升级条件：工作台初始化与认证 store 出现循环请求或破坏全局路由的选择器隐藏规则。

## 集成检查

- `git diff --check`
- `mvn -pl :ccb-security -am test`
- `npm --prefix web run build`
- 隔离 API 探针：无上下文动态菜单正反例，以及项目 API 缺失/伪造上下文反例。
- 浏览器：`1280x800`、`390x844`；`/projects`、`/system/**`、项目业务路由；检查选择器、溢出、控制台和返回后上下文。

## 控制模型种子

以下均为 `hypotheses-only`，待 `model-engineering-system` 验证：被控边界是动态菜单构建、路由守卫和应用壳层；状态变量候选为当前路由上下文类别、存储项目 ID、授权快照和可见选择器；传感器候选为 Security 测试、构建、API 路由树和真实浏览器；执行器候选为权限聚合查询、路由元数据和选择器条件；扰动候选为无当前项目、只有非项目管理权限、项目成员失效和路由刷新；时延候选为授权异步刷新与项目上下文初始化。

## 风险与用户批准

高风险动作仅限授权路由筛选与全局/项目上下文分离。计划不修改数据库、JWT、项目 API 或项目数据；需要扩大这些边界时停止并重新确认。

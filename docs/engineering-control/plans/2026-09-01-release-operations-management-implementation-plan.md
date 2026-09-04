# 投产管理 实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 在既有 release 模块中交付按项目隔离的投产演练、时序、回退时序、问题跟踪和投产组织五项能力。

**架构：** 新增 `release-operations` 目录菜单和 `/api/release/operations` 业务接口，数据全部以 `project_id + tenant_id` 隔离。演练计划、时序、问题和组织使用独立持久化模型；普通/回退时序共用 timeline/item 模型，通过类型字段隔离；投产组成员通过 `platform/system` 只读项目成员引用契约校验，不复制成员管理能力。

**技术栈：** Java 17、Spring Boot、JdbcTemplate、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Pinia、Vue Router。

## 全局约束

- 只修改 `REQ-20260901-057` 任务范围中的文件，不读取或修改 `.env`、密钥和生产数据。
- 保持 `com.ccb.release` 包名、`ccb-release` artifact 和现有配置管理路由/API 兼容。
- V123 只能追加；不得修改历史迁移，不直接修改其他模块负责的数据表。
- 所有接口执行认证、RBAC、租户和项目范围校验；所有写接口执行输入校验、实体校验、rowVersion 并记录操作审计。
- 前端复用 `useProjectContextStore`、现有 release 导航、Element Plus 和语义主题变量；不得用前端显隐替代后端权限。
- 桌面表格和横向时序图必须在局部容器滚动；手机端不得产生页面级横向溢出。

---

### T1：数据库模型与投产管理菜单权限

**需求映射：** R1, R2, R3, R4, R5, R6

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V123__release_operations_management.sql`
- 新建：`.ai-control/requirements/req-20260901-057-release-operations-management/execution-T1.json`
- 测试：`node scripts/check-flyway-migrations.mjs`

**接口：**
- 产出：七张 `rel_release_*` 表、索引、软删除/版本字段，以及 `release-operations` 父菜单和五个子菜单的幂等权限种子。
- 消费：T2 使用表结构和权限编码实现服务端契约。

- [ ] **步骤 1：建立迁移基线检查**

运行 `node scripts/check-flyway-migrations.mjs`，确认当前迁移版本和命名规则；记录退出码，不修改已有脚本。

- [ ] **步骤 2：实施 V123**

创建演练计划/轮次、时序/明细、问题、投产组/组成员表。每张表包含 `id`、`tenant_id`、项目标识、创建/更新时间、软删除字段；可编辑聚合包含 `row_version`。添加项目、类型、序号、状态和唯一性索引。插入父目录 `820`、子菜单 `821-825` 和 view/manage 权限，使用 `INSERT ... WHERE NOT EXISTS` 或 `INSERT IGNORE` 保证重复执行安全，并为已有角色补授父子菜单的最小兼容关系。

- [ ] **步骤 3：运行迁移检查**

运行 `node scripts/check-flyway-migrations.mjs`，预期 V123 唯一、版本连续规则和 SQL 结构检查通过；保存命令结果到 execution-T1。

**回滚：** 不删除 V123；回滚应用时停用新菜单权限，保留已写入表和数据。

**停止条件：** 发现 V123 与现有迁移版本冲突、菜单 ID 已被占用、表结构无法表达 rowVersion 或项目范围。

**升级条件：** 需要超出已批准的 `ProjectMemberReferenceQuery` 只读契约、修改项目成员管理行为或历史迁移才能满足成员校验时，暂停并请求模块 Owner 决定，不使用自由文本或直接读内部表替代。

### T2：投产管理后端聚合接口

**需求映射：** R2, R3, R4, R5, R6, R7

**前置任务：** T1

**文件：**
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/operations/model/ReleaseOperationsModels.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/operations/persistence/ReleaseOperationsStore.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/operations/service/ReleaseOperationsService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/operations/web/ReleaseOperationsController.java`
- 新建：`server/src/modules/release/src/test/java/com/ccb/release/operations/service/ReleaseOperationsServiceTest.java`
- 新建：`server/src/modules/release/src/test/java/com/ccb/release/operations/web/ReleaseOperationsControllerSecurityTest.java`

**接口：**
- 消费：T1 表和既有 `AuthUser`、`ApiResponse`、`PageResult`、项目访问和用户引用公开契约。
- 产出：`GET|PUT /api/release/operations/drill-plan`、轮次 CRUD、`GET|PUT /timelines/{type}`、时序明细 CRUD、问题分页 CRUD、组 CRUD、组成员增删和当前项目成员选项 API。

- [ ] **步骤 1：建立服务测试基线**

先增加服务测试用例并运行 `mvn -pl :ccb-release -am -Dtest=ReleaseOperationsServiceTest test -Dnet.bytebuddy.experimental=true`，预期新方法尚未存在而失败，记录失败信号。

- [ ] **步骤 2：实现模型、存储和服务**

用 record 请求/响应固定字段白名单。Service 对每个入口执行 tenant/project 访问检查，普通和回退时序用枚举/白名单隔离；轮次、明细、问题和组支持行版本冲突返回 409；删除使用软删除且明确不存在/越权错误；组成员写入前校验成员属于当前项目且有效，保存显示名快照。列表支持分页和关键词/优先级/状态筛选，时序明细按 `seq_no,id` 排序。

- [ ] **步骤 3：实现 Controller 和安全测试**

Controller 只负责参数适配和权限注解，Service 负责业务规则。覆盖查看、管理权限缺失返回 403，跨租户/跨项目实体返回拒绝，重复组成员/不存在成员/rowVersion 不一致返回业务错误。

- [ ] **步骤 4：运行后端局部回归**

运行 `mvn -pl :ccb-release -am test -Dnet.bytebuddy.experimental=true`，预期 release 模块测试全部通过；保存失败、通过数量和关键断言。

**回滚：** 按 operations 包文件边界回退应用代码，保留 V123 数据和迁移历史。

**停止条件：** 后端必须直接访问 `sys_*` 私有表、依赖未登记业务模块、或无法在服务端判断项目成员归属。

**升级条件：** 已批准的项目成员公开契约无法满足当前项目成员选项时，向 Owner 提交新的能力决策，不在本任务内扩大数据访问范围。

### T3：前端接口、路由和菜单入口

**需求映射：** R1, R6, R7

**前置任务：** T1, T2

**文件：**
- 修改：`web/src/api/release.ts`
- 修改：`web/src/router/index.ts`
- 新建：`web/src/modules/release/ReleaseOperationsManagement.vue`
- 新建：`web/src/modules/release/release-operations.css`
- 测试：`npm --prefix web run build`

**接口：**
- 消费：T2 的 `/release/operations` DTO、权限码、项目 ID 和业务错误语义。
- 产出：五个路由，统一页面入口，当前项目切换刷新事件和后续 T4 使用的 typed API 函数。

- [ ] **步骤 1：在 `release.ts` 固定 DTO 和请求函数**

新增演练计划/轮次、时序/明细、问题、投产组/成员类型和 list/create/update/delete 请求函数；所有请求只接受调用方传入的当前 `projectId`，不在 API 层缓存跨项目数据。

- [ ] **步骤 2：登记五个路由**

在现有根目录下新增 `release-operations` 目录及五个子路由，分别使用五个权限码和 `release-operations` 菜单路径；`/release-operations` 重定向到第一个可访问子路由。

- [ ] **步骤 3：建立统一页面壳**

页面壳复用现有 release 导航和 project store，按路由展示五个子视图；监听 `projectStore.currentRef`，清空表单/选择项/旧数据并重新加载。权限列表为空时显示无权限状态。为 T4 预留子组件或同文件视图边界，但不复制 project-context 状态。

- [ ] **步骤 4：运行前端构建**

运行 `npm --prefix web run build`，预期 TypeScript/Vite 构建通过；保存构建日志和既有 chunk warning（若有）。

**回滚：** 删除新路由/API/页面文件并保留既有 `release.ts` 函数，恢复到配置管理入口。

**停止条件：** 需要修改 `AppLayout.vue`、公共 UI 组件或项目上下文 store 才能实现切换时刷新，或出现路由菜单和权限不一致。

**升级条件：** 动态菜单接口要求额外的公共路由字段或菜单层级调整时，先提交前端公共能力变更评审。

### T4：五个业务页面和项目成员操作

**需求映射：** R2, R3, R4, R5, R6, R7

**前置任务：** T3

**文件：**
- 新建：`web/src/modules/release/components/ReleaseDrillPlanView.vue`
- 新建：`web/src/modules/release/components/ReleaseTimelineView.vue`
- 新建：`web/src/modules/release/components/ReleaseIssueTrackingView.vue`
- 新建：`web/src/modules/release/components/ReleaseOperationsOrganizationView.vue`
- 修改：`web/src/modules/release/ReleaseOperationsManagement.vue`
- 修改：`web/src/modules/release/release-operations.css`

**接口：**
- 消费：T2 typed API 和 T3 页面壳/项目刷新回调。
- 产出：五个页面的可操作表单、表格、时序图、筛选、确认和错误状态。

- [ ] **步骤 1：实现演练计划页面**

方案和环境搭建说明使用表单保存；轮次使用列表并通过抽屉/对话框增改删，删除前确认。保存中禁用按钮，切换项目关闭弹层并清空旧轮次。

- [ ] **步骤 2：实现普通/回退时序页面**

共用 `ReleaseTimelineView`，根据 `timelineType` 加载对应数据；明细表支持节点增改删和序号调整，横向节点图只在局部容器滚动，时间/责任人/状态显示清晰。删除节点保留确认和失败重试。

- [ ] **步骤 3：实现问题跟踪页面**

添加关键词、优先级、状态筛选；表格和移动端卡片显示编号、标题、状态和责任人；表单支持描述、分析、措施、跟踪记录和关闭时间，删除需确认。

- [ ] **步骤 4：实现投产组织页面**

无层级组列表支持增改删；选择组后展示成员，成员对话框从当前项目成员选项选择，支持添加/移除并处理重复和失效成员错误。不能输入任意 userId。

- [ ] **步骤 5：补齐全状态和响应式样式**

每页覆盖 loading、空、失败/重试、无权限、保存中、重复提交和只读/删除确认；桌面使用约束内容宽度，手机使用纵向布局和局部滚动，不产生页面级横向溢出；保留浅色/深色语义变量。

- [ ] **步骤 6：运行构建和静态检查**

运行 `npm --prefix web run build` 和 `git diff --check`，预期均通过。

**回滚：** 按组件和样式文件边界回退，不动既有配置管理视图。

**停止条件：** 页面出现跨项目旧数据残留、成员可选择其他项目用户、表单能绕过服务端权限、或手机视口产生页面级横向滚动。

**升级条件：** 需要新增公共组件或调整动态菜单加载机制时，停止并重新评估公共能力范围。

### T5：综合验证、运行态和交付证据

**需求映射：** R1-R8

**前置任务：** T4

**文件：**
- 新建：`.ai-control/requirements/req-20260901-057-release-operations-management/execution-T2.json`
- 新建：`.ai-control/requirements/req-20260901-057-release-operations-management/observation-T1.json`
- 新建：`.ai-control/requirements/req-20260901-057-release-operations-management/state.json`

**接口：**
- 消费：T1-T4 的 SQL、服务测试、前端构建和运行态输出。
- 产出：可重复的命令、浏览器路径、视口、结果、偏差和回退说明。

- [ ] **步骤 1：执行治理与差异检查**

运行 `node scripts/check-all-governance.mjs`、`node scripts/check-flyway-migrations.mjs`、`git diff --check`；分别记录通过或已有环境阻断，不把阻断写成通过。

- [ ] **步骤 2：执行全量后端和前端检查**

运行 `mvn test`、`npm --prefix web run build`，记录实际退出结果。

- [ ] **步骤 3：执行运行态 API 检查**

使用本地测试环境的 `.env` 配置启动后端和前端，不输出 `.env` 内容；检查健康接口、Flyway V123、五个路由静态资源和未授权接口拒绝。

- [ ] **步骤 4：执行浏览器验收**

使用已登录测试账号依次访问五个路由，在 `1280x800`、`390x844`、必要时 `375x812` 和 `430x932` 检查：新建/编辑/删除确认、时序明细、组成员、空/失败重试、项目切换后数据清空和重新加载、控制台错误、`document.documentElement.scrollWidth`。

- [ ] **步骤 5：记录观测和收敛状态**

将每个传感器的预期/实际、偏差、残余风险和回退方式写入当前任务账本；只有全部 must 需求有证据才允许标记 converged。

**回滚：** 运行态验证异常时停止新菜单入口，保留 V123 数据；应用代码按 T1-T4 边界回退。

**停止条件：** 构建失败、越权、项目串数、白屏、控制台持续错误、页面遮挡/溢出或迁移失败。

**升级条件：** 本地环境已有 Flyway failed 状态、缺少登录测试会话或治理检查存在历史账本阻断时，记录证据并请求继续/修复决定，不伪造运行态结论。

## 依赖和并行策略

T1 → T2 → T3 → T4 → T5 串行执行。数据库结构、后端 DTO、前端 API 和页面存在直接契约依赖，不进行并行写入。每个任务结束后先做局部验证，再进入下一任务。

## 控制种子（hypotheses-only）

- 被控边界：V123 投产管理表、`ReleaseOperationsService`、五个前端路由和项目切换刷新状态。
- 状态变量：当前 `projectId`、演练计划/轮次数量、时序类型及明细序号、问题状态、组成员集合、rowVersion、页面 loading/error/save 状态。
- 接口：顶部 `project-context`、release operations REST API、现有项目成员 HTTP 选项接口、Flyway、Maven、Vite、浏览器。
- 传感器：服务测试、安全测试、Flyway 检查、治理检查、Maven、Vite、健康接口、浏览器 DOM/控制台/视口测量。
- 执行器：V123、后端 Controller/Service/Store、菜单种子、typed API、Vue 页面和样式。
- 扰动：旧迁移失败状态、项目切换时未完成请求、并发 rowVersion 冲突、无成员/无数据、长文本和手机窄屏。
- 时延：项目切换触发接口刷新；旧请求晚返回不得覆盖新项目状态。
- 假设：现有项目成员公开接口可被页面和服务端安全复用；动态菜单会读取新迁移中的 parent/child 关系；本地测试数据库可应用 V123。

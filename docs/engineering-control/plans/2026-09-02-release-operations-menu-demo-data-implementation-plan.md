# 投产管理独立菜单与测试数据实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 保留投产管理父级目录，将五项功能作为独立子菜单展示，并为至少两个测试项目补齐五项投产管理测试数据。

**架构：** 沿用 `ReleaseOperationsManagement.vue` 作为公共项目上下文壳，删除页面内 Tabs/横向导航，根据当前路由只渲染一个既有业务组件。数据库仅追加 V124/V125/V126，使用固定测试项目编码、固定 ID 和条件写入，引用现有演示用户和项目成员；V125 补齐本地登录用户的项目实体授权，V126 修正固定演示问题记录的后端枚举值。

**技术栈：** Vue 3、TypeScript、Vue Router、Element Plus、Spring Boot 既有投产 API、MySQL 8.4、Flyway、Maven。

## 全局约束

- 只修改本任务 `codex-task-scope.yaml` 的 `writable_paths`；保留工作区上一轮未提交改动。
- 不修改已发布 V123/V124/V125，不改投产管理 API、权限编码、公共 UI、项目上下文 store 或平台私有实现。
- 不访问生产系统，不使用真实个人信息；V124/V125/V126 只使用本地演示项目、演示用户和本地测试登录用户。
- V124/V125 只追加并可重复执行；V126 只修正两个固定演示问题记录；不得按项目删除数据，不得覆盖用户手工新增记录。
- 所有页面仍必须保留 loading、error、empty、forbidden、saving 和项目切换旧请求保护。
- 前端复用既有投产组件、`release-operations.css` 和交付示范中心已验证的页面结构；不增加新的公共样式体系。

## 文件职责地图

- `web/src/modules/release/ReleaseOperationsManagement.vue`：投产管理公共项目解析、路由到单业务组件和公共错误/空状态；移除内部导航。
- `web/src/router/index.ts`：五条现有投产路由和父级入口的兼容性检查；只有发现路由 meta 或入口需要对齐时才修改。
- `server/src/platform/infrastructure/src/main/resources/db/migration/V124__release_operations_demo_data.sql`：两个虚构测试项目、演示项目成员和五项投产业务数据的幂等追加迁移。
- `server/src/platform/infrastructure/src/main/resources/db/migration/V125__release_operations_demo_admin_members.sql`：为本地登录用户补齐两个测试项目的实体授权成员关系；V124 已执行后不得修改，因此使用追加迁移。
- `server/src/platform/infrastructure/src/main/resources/db/migration/V126__release_operations_demo_issue_status.sql`：将 V124 固定演示问题记录的无效状态修正为后端合法枚举；V124/V125 已执行后不得修改，因此使用追加迁移。
- `.ai-control/requirements/req-20260902-058-release-operations-menu-demo-data/`：本任务阶段证据，不覆盖上一任务账本。

## 任务依赖图与并行策略

```text
T1 页面路由壳改造 -> T2 V124 测试数据 -> T3 集成验证与浏览器验收
```

任务串行执行。T1 与 T2 虽然写入面不重叠，但 T2 的运行态验收依赖 T1 页面入口，串行可以减少菜单缓存和数据库版本同时变化造成的诊断歧义。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 独立菜单与路由权限 | T1、T3 |
| R2 每项目演练数据 | T2、T3 |
| R3 普通/回退时序数据 | T2、T3 |
| R4 问题分析跟踪数据 | T2、T3 |
| R5 投产组织和成员数据 | T2、T3 |
| R6 项目隔离和切换 | T1、T2、T3 |
| R7 既有写入契约与回退 | T3 |
| R8 桌面/手机全状态与布局 | T1、T3 |

### T1：移除页面内 Tabs 并按独立路由展示业务页面

**需求映射：** R1、R6、R8

**前置任务：** 无；需先完成 baseline 复核和系统建模。

**文件：**
- 修改：`web/src/modules/release/ReleaseOperationsManagement.vue`
- 检查/必要时修改：`web/src/router/index.ts`
- 测试：使用现有前端构建和浏览器验收，不新增公共测试文件

**接口：**
- 消费：现有五条路由、菜单权限、`useProjectContextStore`、`getProjectWorkbench` 和五个业务组件的 `project-id` 属性。
- 产出：当前路由仅渲染一个对应业务组件；父级入口仍跳转第一个可访问子菜单。

- [ ] **步骤 1：执行前基线检查**

运行：`git status --short --branch`、`rg -n "release-operations-nav|visibleNavItems|activeKey|ReleaseDrillPlanView|ReleaseTimelineView" web/src/modules/release/ReleaseOperationsManagement.vue web/src/router/index.ts`

预期：确认导航逻辑集中在页面壳，五条路由均存在，上一轮用户改动保持不变。证据记录到 `execution-T1.json`。

- [ ] **步骤 2：实施最小页面壳变更**

删除页面壳的 `navItems`、`visibleNavItems`、`navigate` 和 `<nav>`；保留项目初始化、项目切换请求代次、公共错误/空状态。将 `activeKey` 改为基于 `route.path` 的只读路由映射，并通过单一 `v-if/v-else-if` 链渲染对应组件，确保同一时刻只有一个业务页面。

预期：页面中不再出现投产管理重复菜单；项目 key 仍随 `currentRef` 变化，旧项目编辑状态不会复用。

- [ ] **步骤 3：运行局部检查**

运行：`npm --prefix web run build`、`git diff --check`。

预期：构建退出码为 0、无空白错误；如路由文件无需修改，记录实际未修改并保持范围最小。

**验收、证据与回滚：** 静态检查页面壳没有 Tabs 标识；构建通过；回滚只恢复本任务对 `ReleaseOperationsManagement.vue`/路由的修改，不回退上一轮文件。

**停止条件：** 需要修改 AppLayout、项目上下文 store、公共 UI 或权限 API；路由权限与动态菜单不一致；旧项目请求仍可覆盖新项目状态。

**升级条件：** 页面壳无法在不复制五套项目解析逻辑的情况下承载独立路由，或发现菜单系统并非从 V123 读取五项子菜单。

### T2：追加两个项目的投产管理测试数据

**需求映射：** R2、R3、R4、R5、R6

**前置任务：** T1

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V124__release_operations_demo_data.sql`
- 测试：`node scripts/check-flyway-migrations.mjs` 和本地数据库迁移结果

**接口：**
- 消费：V51 的 `pm_project`/`pm_project_member` 表、V71 已建立的演示用户、V123 的七张投产管理表。
- 产出：测试项目编码 `REL-DEMO-ALPHA`、`REL-DEMO-BETA` 及每项目五项投产数据；数据只使用 `tenant_id=1` 和固定项目 ID。

- [ ] **步骤 1：建立迁移前结构基线**

运行：`Get-ChildItem server/src/platform/infrastructure/src/main/resources/db/migration -Filter 'V*.sql' | Sort-Object Name | Select-Object -Last 5`，并检查 V51、V71、V123 的列定义和当前 Flyway 版本。

预期：确认 V124 是当前版本链的下一个版本，确认测试用户 1001-1006 和投产表存在；若版本或字段不符，停止并回到建模/计划阶段。

- [ ] **步骤 2：实施固定 ID、条件插入的项目和成员种子**

写入两个项目（固定项目 ID、高位测试 ID、虚构名称和日期）；使用项目编码和主键双重 `NOT EXISTS` 防止覆盖冲突。为每个项目补入固定项目成员关系，只有对应演示用户存在时才插入，成员 ID 和用户 ID 使用固定映射。

预期：迁移不新增生产用户，不产生没有用户来源的投产组成员关系；重复运行不会重复项目或成员。

- [ ] **步骤 3：实施五项投产数据种子**

对每个项目条件写入一条演练计划、三条演练轮次、一条普通时序及四条明细、一条回退时序及三条明细、三条不同优先级/状态且包含分析/处理/跟踪字段的问题、两个投产组及至少两条有效项目成员关系。每行使用固定测试 ID 和 `NOT EXISTS`，不按项目清理已有记录。

预期：两个项目各有完整五项数据；普通与回退通过 `timeline_type` 隔离；组成员引用本项目固定成员；迁移可重复执行且不影响其他记录。

- [ ] **步骤 4：运行迁移检查和数据库验证**

运行：`node scripts/check-flyway-migrations.mjs`；在本地测试数据库执行 Flyway 后，用只读查询按项目统计七张投产表，并重复执行迁移/验证唯一记录数。

预期：V124 唯一且通过检查；两个项目每项数据数量达到设计值；第二次执行数量不增加；项目 A 查询结果不包含项目 B 的业务 ID。

**验收、证据与回滚：** 保存迁移检查退出码、版本号、两项目各表计数和重复执行前后计数。回滚不删除迁移或数据，应用层可关闭菜单；若必须撤销本地测试数据，仅允许按固定测试 ID 由数据库管理员在测试环境执行单独清理，不写入迁移。

**停止条件：** V124 不是下一个版本；固定 ID 与非本任务数据冲突；演示用户/项目成员缺失导致无法满足组成员有效性；需要修改 V123 或业务表结构。

**升级条件：** 现有数据库没有安全的测试项目/成员创建路径，或 Flyway 运行环境需要读取生产配置/真实数据。

### T3：集成回归、项目切换和响应式验收

**需求映射：** R1、R2、R3、R4、R5、R6、R7、R8

**前置任务：** T1、T2

**文件：**
- 检查：本任务所有已修改文件及现有投产组件
- 证据：`.ai-control/requirements/req-20260902-058-release-operations-menu-demo-data/execution-T3.json`、`observation-T3.json`、`convergence.json`

**接口：**
- 消费：动态菜单、五条路由、投产 API、顶部项目切换栏和 V124 测试项目。
- 产出：构建、测试、迁移、治理、范围和浏览器验收证据，以及未观测到的边界说明。

- [ ] **步骤 1：运行自动化回归**

运行：`mvn -pl :ccb-release -am test '-Dnet.bytebuddy.experimental=true'`、`npm --prefix web run build`、`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260902-058-release-operations-menu-demo-data/codex-task-scope.yaml --working-tree`、`node scripts/check-all-governance.mjs`、`git diff --check`。

预期：后端测试、前端构建、差异检查通过；治理或范围检查若被上一轮未提交改动/历史账本阻断，原样记录并区分本任务结果。

- [ ] **步骤 2：运行态检查五个独立菜单**

用本地测试账号打开投产管理父级目录，逐项进入五个子菜单，检查页面内没有重复 Tabs/横向导航、当前页面标题正确、接口请求带当前项目 ID、数据状态可见。

预期：五个子菜单独立可点击，五个页面分别显示 V124 测试数据，控制台没有本任务引入的错误。

- [ ] **步骤 3：验证项目切换和刷新**

在测试项目 A 的五个页面分别记录一条可识别数据，切换到项目 B 后重新检查五个页面，再刷新每个页面。

预期：项目 B 只显示 B 的固定数据，A 的业务 ID/名称不残留；刷新后数据仍存在；切换期间旧请求不会覆盖当前页面。

- [ ] **步骤 4：执行四视口验收**

在 `375x812`、`390x844`、`430x932`、`1280x800` 视口检查菜单、页面内容、加载/空/失败状态、弹层可达性、`document.documentElement.scrollWidth` 和控制台错误。

预期：页面级横向溢出为 false，五项菜单和主操作可达，数据列表/时间线/组织页面不发生遮挡。

**验收、证据与回滚：** 记录实际账号、路由、视口、接口状态、控制台计数和页面级滚动宽度。应用回退按任务边界恢复旧壳；保留 V124 迁移和数据，关闭菜单即可阻断新入口。

**停止条件：** 出现白屏、401/403 非预期、跨项目数据、页面横向溢出、重复 Tabs、控制台新增错误或自动化回归失败。

**升级条件：** 无法用本地测试账号验证项目切换，或需要改变权限/公共壳/历史迁移才能完成验收。

## 集成检查

- `node scripts/check-flyway-migrations.mjs`：V124 版本唯一、追加结构合规。
- `mvn -pl :ccb-release -am test '-Dnet.bytebuddy.experimental=true'`：既有投产后端回归通过。
- `npm --prefix web run build`：Vue/TypeScript/Vite 构建通过。
- `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260902-058-release-operations-menu-demo-data/codex-task-scope.yaml --working-tree`：本任务范围可识别，预先存在的改动单独记录。
- `node scripts/check-all-governance.mjs`、`git diff --check`：治理和差异检查结果如实记录。
- 浏览器：五个独立子菜单、两个项目切换、刷新、四视口、控制台和页面级滚动宽度。

## 控制模型种子

以下均为待 `$model-engineering-system` 验证的假设，不是已完成的运行结论：

- 被控边界：投产管理页面壳、五条路由、动态菜单和 V124 测试数据迁移；外部边界为项目上下文、权限和数据库运行环境。
- 状态候选：当前项目 ID、当前路由、页面 loading/error/data 状态、V124 固定数据是否存在、Flyway 版本。
- 传感器候选：路由/菜单 DOM、浏览器网络和控制台、项目切换后的业务 ID、数据库只读统计、Maven/Vite/Flyway/治理命令。
- 执行器候选：页面壳路由映射、追加 V124 条件插入、刷新/切换项目、构建和本地迁移验证。
- 扰动候选：动态菜单缓存、演示用户缺失、固定 ID 冲突、旧请求延迟、上一轮未提交改动和治理历史阻断。

## 风险与用户批准

- 高风险动作：追加数据库测试数据迁移；停止条件是版本/主键/成员引用不满足。
- 用户已批准：保留父级目录、五个独立子菜单；至少两个项目覆盖五项投产功能；按本设计实施。
- 本计划状态：待用户确认；确认后生成 approved handoff，导入控制账本并进入建模阶段。

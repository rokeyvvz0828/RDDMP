# 工作流仅保留全局模板与项目流程实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 安全退出用户可见的平台流程模型，把存量结构迁移为全局模板，并只为平台能力升级项目生成可配置的项目流程草稿。

**架构：** `V136` 以复制而非原地改写保护旧定义和实例引用；workflow 服务把 `PLATFORM` 限制为隐藏的运行兼容类型，管理面只接受 `TEMPLATE/PROJECT`。前端只呈现两个范围，并通过服务端返回的 `requires_configuration` 标识引导项目人员配置。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Flowable、MySQL 8.4、Flyway、Testcontainers、Vue 3、TypeScript、Element Plus、Pinia。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-02-workflow-project-template-only-design.md`
- 需求文档：`docs/requirements/REQ-20260902-062-workflow-project-template-only/requirement.md`
- 状态：可移交（用户于 2026-09-02 批准实施计划）

## 全局约束

- 只追加 `V136`，禁止修改 V134、V135 和 Flowable `ACT_*` 表。
- 不改写或删除旧 `PLATFORM` 定义、版本、实例和任务。
- 只为 `910000000003001 / RDDMP-PLATFORM / 平台能力升级项目` 生成项目草稿。
- 模板不得包含具体用户、角色主键或动态办理人配置，不得发布或启动。
- 项目草稿可保存人员占位，但发布和启动必须由后端最终校验。
- 项目同编码流程一旦发布，新实例优先使用项目定义；无项目发布版本时才兼容旧平台定义。
- 不改变业务模块流程编码、现有运行实例版本或项目权限所有权。

## 文件职责地图

- `candidate-new` `server/src/platform/infrastructure/src/main/resources/db/migration/V136__retire_platform_workflow_management.sql`：复制平台定义并清理办理人配置。
- `existing` `server/src/platform/workflow/pom.xml`：仅增加 MySQL 迁移测试依赖。
- `existing` `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`：管理范围、草稿占位、发布校验和列表待配置投影。
- `existing` `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java`：按编码和显式定义 ID 的项目优先选路及兼容回退。
- `existing` `server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`：创建请求默认范围与管理 API 输入边界。
- `candidate-new` `server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowPlatformRetirementMigrationMySqlTest.java`：V135 到 V136 的真实 MySQL 8.4 传感器。
- `candidate-new` `server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowPlatformRetirementTest.java`：隐藏平台管理面和项目占位生命周期测试。
- `existing` `server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowTemplateScopeTest.java`：模板和项目占位范围回归。
- `existing` `server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowBusinessIntegrationServiceTest.java`：项目优先、显式 ID 重选和回退测试。
- `existing` `web/src/api/workflow.ts`：定义列表增加 `requires_configuration` 类型。
- `existing` `web/src/components/workflow/WorkflowNodeInspector.vue`：项目占位提示及配置转换。
- `existing` `web/src/views/WorkflowView.vue`：双分区、移除项目标签和待配置交互。
- `existing` `docs/integration/workflow-module-contract.md`：记录用户可见范围和过渡期启动规则。

## 任务依赖图与并行策略

`T1 -> T2 -> T3` 串行。T2 依赖 T1 的迁移数据形态，T3 依赖 T2 的 `requires_configuration` 与错误契约；不存在已证明安全的并行写入组。

## 需求覆盖表

- R1：T2、T3
- R2、R3、R6、R7：T1
- R4：T2、T3
- R5：T2

### T1：V136 安全复制存量平台流程

**需求映射：** R2, R3, R6, R7

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V136__retire_platform_workflow_management.sql`
- 修改：`server/src/platform/workflow/pom.xml`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowPlatformRetirementMigrationMySqlTest.java`

**接口：**
- 消费：V135 后 `wf_definition(scope_type, project_id, code)`、`wf_version(definition_json)` 和项目 `910000000003001`。
- 产出：每条有效 `PLATFORM` 对应一个同编码 `TEMPLATE/DRAFT` 和一个属于 `910000000003001` 的 `PROJECT/DRAFT`；旧行主键和引用不变。

- [ ] **步骤 1：建立 V135 基准和失败迁移测试**

  新测试使用 `MySQLContainer("mysql:8.4")` 和 Flyway：先迁移到 V135，在 10 条迁移种子上补入一条运行期平台定义形成当前部署库的 11 条输入，记录旧版本和 `wf_instance(definition_id, version_no, status)` 快照；再迁移 V136，断言当前因脚本不存在而失败或目标版本不可达。

  运行：`mvn -pl :ccb-workflow -am -Dtest=WorkflowPlatformRetirementMigrationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：测试失败，原因包含 V136 不存在或迁移结果缺少模板/项目草稿。

  证据：测试退出码和第一个失败断言。

- [ ] **步骤 2：实现只追加的数据复制迁移**

  使用临时源表固定每个 `PLATFORM` 最新版本；使用 `JSON_TABLE` 展开节点并按序重建 `nodes`：`APPROVAL` 写入 `assigneeType=TEMPLATE_PLACEHOLDER`、空 `assigneeIds` 并移除 `assigneeVariable/fieldName/expression/organizationId`，`CC` 写入空 `userIds` 和 `templatePlaceholder=true`。模板和项目草稿均使用清理后的 JSON、`DRAFT/current_version=0`、空部署字段，使用预留的 `913600...` 确定性主键区间和 `NOT EXISTS` 防重。

  预期：旧定义、版本、实例没有 `UPDATE/DELETE`；只有目标项目产生 `PROJECT` 行。

  证据：V136 diff 和迁移 SQL 中的范围条件、主键区间、防重条件。

- [ ] **步骤 3：验证迁移数据和历史兼容**

  运行：`mvn -pl :ccb-workflow -am -Dtest=WorkflowPlatformRetirementMigrationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：11 个模板、11 个目标项目草稿；模板与项目草稿无具体办理人；其他项目为 0；旧平台定义/版本/实例快照完全一致；第二次 `flyway.migrate()` 无新增数据。

  证据：零失败测试输出和断言基数。

- [ ] **步骤 4：建立提交检查点**

  运行：`git diff --check && node scripts/check-flyway-migrations.mjs`

  预期：无空白错误，迁移序号和命名检查通过。

  证据：命令退出码 0。

**回滚：** 回退 Java 测试依赖和迁移调用代码时保留 V136 新增草稿；若必须清理，新增 V137 补偿迁移，仅删除无实例、无绑定且仍为草稿的 `913600...` 迁移行。

**停止条件：** 部署前目标库基数不是已确认的 11 条平台定义、目标项目不存在或不唯一、JSON 节点无法无损重建、确定性主键区间已被占用。

**升级条件：** 需要修改历史迁移、Flowable 表、旧实例引用或自动映射具体人员。

### T2：收紧管理范围并实现项目优先启动

**需求映射：** R1, R4, R5, R6

**前置任务：** T1

**文件：**
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowPlatformRetirementTest.java`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowTemplateScopeTest.java`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowBusinessIntegrationServiceTest.java`

**接口：**
- 消费：T1 生成的模板/项目草稿和现有 `WorkflowProjectAccessGateway`。
- 产出：管理 API 仅 `TEMPLATE/PROJECT`；列表字段 `requires_configuration:boolean`；项目草稿宽松保存、严格发布；启动选择 `PROJECT > legacy PLATFORM`。

- [ ] **步骤 1：建立管理隐藏、占位生命周期和选路失败测试**

  覆盖：列表默认不含平台；`scopeType=PLATFORM`、创建/读取/编辑平台返回业务错误；项目占位草稿可创建和更新但发布失败；无效项目成员/角色发布失败；按编码和显式平台定义 ID 在同编码项目流程已发布时均选择项目定义；项目流程未发布时回退平台；无可运行定义时错误包含项目名称与流程编码。

  运行：`mvn -pl :ccb-workflow -am -Dtest=WorkflowPlatformRetirementTest,WorkflowTemplateScopeTest,WorkflowBusinessIntegrationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：新增断言失败并准确指向现有三范围管理与显式 ID 不重选行为。

  证据：失败测试名和错误信息。

- [ ] **步骤 2：实现管理范围与两阶段人员校验**

  `WorkflowService` 将管理范围规范化限制为 `TEMPLATE/PROJECT`；无范围创建默认 `TEMPLATE`；定义管理读取和生命周期拒绝隐藏平台。创建/更新项目草稿允许 `TEMPLATE_PLACEHOLDER` 和抄送占位，发布前读取最新 `definition_json` 严格要求 `PROJECT_MEMBER/PROJECT_ROLE/STARTER` 与有效项目目标。定义列表通过最新草稿 JSON 投影 `requires_configuration`。

  预期：前端无法绕过后端创建或维护平台定义，未解析项目草稿仍可保存但绝不发布。

  证据：服务测试通过和错误码/文案断言。

- [ ] **步骤 3：实现所有新启动入口的项目优先**

  `WorkflowBusinessIntegrationService` 提取同编码运行定义选择器。`startByCode` 直接选择当前项目已发布定义；`startByDefinitionId` 若传入旧平台 ID，则基于其编码和业务项目重新选择同编码项目定义。无项目版本时允许旧平台兼容回退，`TEMPLATE` 永不参与；无匹配时返回包含项目名称和流程编码的错误。

  预期：同一项目、同一编码一旦发布 `PROJECT`，两个启动入口都不再创建旧平台实例。

  证据：两种入口的定义 ID 断言和回退断言。

- [ ] **步骤 4：运行局部和模块回归**

  运行：`mvn -pl :ccb-workflow -am test`

  预期：工作流及依赖模块测试零失败。

  证据：Surefire 汇总与退出码 0。

**回滚：** 回退三个 Java 文件，V136 新增草稿保留；旧平台运行能力未被数据迁移破坏。

**停止条件：** 现有业务存在无法提供项目上下文的新启动入口，或显式定义 ID 重选会破坏已确认的业务绑定语义。

**升级条件：** 需要修改业务模块 API、项目权限契约或已运行实例状态。

### T3：交付双范围页面和完整验收

**需求映射：** R1, R4

**前置任务：** T2

**文件：**
- 修改：`web/src/api/workflow.ts`
- 修改：`web/src/components/workflow/WorkflowNodeInspector.vue`
- 修改：`web/src/views/WorkflowView.vue`
- 修改：`docs/integration/workflow-module-contract.md`

**接口：**
- 消费：T2 的 `PROJECT/TEMPLATE` 管理范围和 `requires_configuration`。
- 产出：桌面/移动双分区页面、项目草稿待配置提示和可保存/不可发布交互。

- [ ] **步骤 1：建立前端构建基准并修改页面状态模型**

  运行：`npm --prefix web run build`

  预期：修改前构建通过；记录基准退出码。随后给 `WorkflowDefinition` 增加 `requires_configuration?: boolean`，去掉页面加载平台用户/角色的分支和无用 import。

  证据：基准构建结果和 TypeScript diff。

- [ ] **步骤 2：实现双分区和待配置编辑体验**

  工具栏只保留“本项目流程/全局模板”，移除旁边当前项目标签；新建弹框范围也只保留两项。项目草稿包含模板占位时，桌面状态列和移动卡片显示“待配置人员”；检查器提示并允许改为项目成员或项目角色，选择有效目标时清理占位标志；保存允许占位草稿，发布仍由 T2 返回明确错误。

  预期：页面代码不再出现用户可见“平台流程”，也不再请求平台级用户/角色选项。

  证据：`rg -n "平台流程" web/src/views/WorkflowView.vue web/src/components/workflow/WorkflowNodeInspector.vue` 无匹配，页面 diff。

- [ ] **步骤 3：更新契约并执行静态/全量检查**

  运行：`npm --prefix web run build`

  运行：`mvn test`

  运行：`node scripts/check-all-governance.mjs`

  运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260902-062-workflow-project-template-only/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`

  预期：四个命令退出码均为 0，差异仅在授权文件。

  证据：构建、测试、治理与范围检查摘要。

- [ ] **步骤 4：真实浏览器验收**

  在 `http://127.0.0.1:5173/workflow/definitions` 验证桌面和 `375x812`、`390x844`、`430x932`：仅两个分区；没有重复项目标签；迁移草稿显示待配置；占位草稿可保存但不能发布；配置成员/角色后可发布；无页面横向溢出、遮挡或控制台错误。

  预期：全部视口通过，关键截图与控制台检查可复现。

  证据：浏览器截图路径、视口和控制台结果。

**回滚：** 回退三个前端文件与契约文档；后端仍阻止平台管理和未配置发布。

**停止条件：** 后端未返回可判定的待配置状态、目标项目无可选成员/角色或移动端设计器无法完成配置。

**升级条件：** 需要修改公共 UI 组件、顶层项目选择器或业务模块页面。

## 集成检查

- T1 后：真实 MySQL V136 数据基数、JSON、目标项目和旧引用全部通过。
- T2 后：两个启动入口、管理 API、项目占位发布和工作流模块回归通过。
- T3 后：全量后端、前端构建、治理、范围及四个浏览器视口通过。
- 最终差异不得包含 V134/V135、Flowable `ACT_*`、业务模块实现或环境密钥。

## 控制模型种子

- 状态：`hypotheses-only`。
- 被控边界候选：工作流定义管理、V136 复制数据、项目流程发布和新实例定义选择；旧实例执行为兼容边界。
- 状态变量候选：定义范围、项目主键、定义状态、JSON 办理人类型、实例定义主键、项目已发布同编码定义是否存在。
- 接口候选：定义管理 REST API、`WorkflowDefinitionCatalog/WorkflowGateway`、`WorkflowProjectAccessGateway`、Flyway migration。
- 传感器候选：MySQL 迁移测试、JdbcTemplate 单元测试、Maven 回归、TypeScript 构建、浏览器视口与控制台。
- 执行器候选：V136 插入、管理范围校验、发布前严格校验、运行定义选择器、Vue 分区与状态标签。
- 扰动候选：旧平台定义发布状态差异、项目成员停用、已有同编码模板/项目草稿、显式平台定义绑定、并发发布。
- 时延候选：Flyway 首次启动、Flowable 部署、项目成员状态在保存与发布之间变化。
- 假设：V135 基准有 11 条有效平台定义且目标项目唯一；迁移测试或数据库查询与此不符时立即停止。

## 风险与用户批准

- 高风险动作：追加存量数据迁移、改变公共工作流管理 API、改变新实例定义选择。
- 兼容保证：不改旧定义和实例；无项目发布版本时保留平台回退。
- 当前状态：用户已批准本计划；交接包以 `high-assurance` 导入控制账本后进入需求基准门禁。

# 迁移过程依赖文件菜单优化实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将迁移过程依赖文件改造成迁移参数与使用方系统关系管理能力，取消文件动作并支持单条维护、批量导入、回收站和完整关联展示。

**架构：** 采用已确认的方案 A，新增 `DependencyService`、`DependencyController` 和 `DependencyRecycleBinSource`，让 DEPENDENCY 脱离通用附件资产链路。`dm_dependency` 追加 V199 空表门禁迁移，使用 `parameter_id + system_code` 保存关系并通过参数/系统投影提供展示名称；前端保留原路由但改为专属表格、移动卡片和导入抽屉。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Apache POI 5.4.1、MySQL 8.4/Flyway、Vue 3、TypeScript、Element Plus、Pinia、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-10-data-migration-dependency-design.md`
- 状态：待确认
- 设计审批：用户已于 2026-09-10 确认
- 计划审批：等待用户确认后进入控制闭环

## 全局约束

- 仅修改 `REQ-20260910-068` 任务范围中的 writable paths；保护工作区既有 REQ-067 未提交改动。
- 数据 Owner 为 `business/data-migration`；保持 Java 包名、模块 artifact、菜单路由和既有统一响应。
- `dm_dependency` 历史数据不处理；V199 必须先断言表为空，非空时在任何结构修改前失败关闭。
- Flyway 只追加，不修改 V162、V182、V190、V197、V198 或其他已发布脚本。
- 服务端执行认证、RBAC、租户/项目范围、记录 Owner/管理员校验和 `DEPENDENCY` 审计；前端显隐不代替后端校验。
- 不调用或修改生产系统，不使用真实个人信息、生产日志、附件、密钥或凭据。
- 不新增依赖关系导出，不改造系统/组件清单、系统管理/参数管理或公共 UI 组件。
- 页面必须复用 `UiToolbar`、`UiDataTable`、`UiFormDrawer`、`UiEmptyState`、语义主题变量和交付示范中心的列表/移动模式。

## 文件职责地图

- `server/src/platform/infrastructure/src/main/resources/db/migration/V199__data_migration_dependency_domain.sql`（candidate-new）：空表门禁、字段/索引、菜单 750 细粒度权限。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DependencyService.java`（candidate-new）：依赖查询、投影、CRUD、导入、审计、回收站。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/DependencyController.java`（candidate-new）：依赖 REST 路由和权限注解。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DependencyRecycleBinSource.java`（candidate-new）：统一回收站 DEPENDENCY 来源。
- `ContentFileAssetService.java`、`ContentAssetController.java`、`ContentAssetRecycleBinSource.java`：移除 DEPENDENCY 文件服务分支和旧上传/替换/下载路由，保留其他资产支持。
- `ParameterService.java`：删除和彻底删除前的依赖引用保护。
- `DashboardMetricDefinition.java`：DEPENDENCY 下钻改为参数/系统关联表达式。
- `web/src/api/data-migration.ts`、`DependenciesPage.vue`：类型、请求封装、桌面/移动页面、表单和导入状态。
- `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/**`：新增依赖域、迁移、权限、看板和回归测试。

## 任务依赖图与并行策略

```text
T1 数据库和权限契约
  -> T2 后端依赖领域与回收站
  -> T3 参数引用保护、通用链路剥离和看板适配
  -> T4 前端 API 与页面
  -> T5 集成验证与浏览器验收
```

所有任务串行。T2 依赖 T1 的列名、唯一键、权限节点和回收站类型；T3 依赖 T2 的服务接口；T4 依赖 T2/T3 的 REST 契约；T5 需要全部实现完成。串行还用于避免覆盖当前 REQ-067 在 `ParameterService.java`、`data-migration.ts` 等文件中的未提交修改。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 结构迁移和空表门禁 | T1 |
| R2 关联列表和详情展示 | T2、T4 |
| R3 单条维护和唯一关系 | T2、T3、T4 |
| R4 两列 Excel 导入和部分成功 | T2、T4 |
| R5 下线文件动作并剥离通用链路 | T3、T4 |
| R6 RBAC、项目范围、实体权限、审计 | T1、T2、T3 |
| R7 回收站和参数引用完整性 | T2、T3、T4 |
| R8 看板、全状态和响应式验收 | T3、T4、T5 |

### T1：数据库结构、权限节点与失败关闭迁移

**需求映射：** R1, R6

**前置任务：** 无

**已证实输入事实：** `dm_dependency` 当前由 V162 创建并含 `doc_code/doc_name/checksum_md5/component_id`；菜单 750 已由 V190 创建且只有查看权限；最新迁移版本为 V198；治理要求 Flyway 只追加。

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V199__data_migration_dependency_domain.sql`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DataMigrationDependencyMigrationMySqlTest.java`
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java`

**接口：**
- 产出数据库契约：`dm_dependency.parameter_id BIGINT NOT NULL`、`system_code` 为使用方编号、唯一键 `(tenant_id, project_id, parameter_id, system_code)`、查询索引、菜单 750 的 create/update/delete 节点。
- 消费方：T2 的 DependencyService、统一回收站和前端 API。

- [ ] **步骤 1：建立迁移和权限失败基线测试**

  增加 SQL 文本断言和 Testcontainers MySQL 场景：空表可迁移；预置一行时迁移过程抛出预期 SQLSTATE，且旧列仍存在。

- [ ] **步骤 2：运行基线测试确认缺口**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DataMigrationDependencyMigrationMySqlTest,DataMigrationModuleRegistrationTest test`

  预期：新增测试在 V199 不存在或旧结构下失败；记录退出码和首个缺失断言。

- [ ] **步骤 3：实施追加迁移**

  V199 先创建 `dm_v199_assert_dependency_empty` 并 `SIGNAL` 非空；空表时增加 `parameter_id`、删除依赖旧列/索引，建立关系唯一键和 `(tenant_id, project_id, system_code, deleted, updated_at)` 查询索引。补充菜单 750 的 `:create/:update/:delete` 权限节点及管理员/开发人员/超级管理员授权，使用 `INSERT IGNORE` 和存在性守卫保持幂等。

- [ ] **步骤 4：运行迁移与注册局部验证**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DataMigrationDependencyMigrationMySqlTest,DataMigrationModuleRegistrationTest test`

  预期：空表成功、非空失败关闭、列/索引/权限断言通过，退出码 0。

**回滚：** V199 尚未应用时删除新文件即可；已应用且无新关系时只能由 Owner 批准前向补偿迁移恢复旧列和索引，不修改 V199。

**停止条件：** 目标数据库非空、Flyway 版本冲突、迁移无法在结构修改前失败或需要修改历史脚本。

**升级条件：** 发现必须处理历史数据、权限 Owner 不认可节点授权、或平台基础设施要求改变既有列语义。

### T2：专属依赖后端、投影、导入和回收站来源

**需求映射：** R2, R3, R4, R6, R7

**前置任务：** T1

**已证实输入事实：** `ParameterService` 已实现项目范围、用户目录、参数选项和 Excel 导入工具；`ProjectComponentService.getSystemOptions` 返回当前项目启用系统；`ParameterRecycleBinSource` 是专属回收站范式。

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DependencyService.java`
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DependencyRecycleBinSource.java`
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/DependencyController.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DependencyServiceTest.java`

**接口：**
- 消费：T1 的字段、唯一键和权限契约；`dm_parameter`、`dm_component`、`arch_physical_subsystem`、`pm_project`、`sys_user` 只读投影。
- 产出：`GET /dependencies`、`GET /dependencies/{id}`、`GET /dependencies/options/parameters`、`POST /dependencies`、`PUT /dependencies/{id}`、`DELETE /dependencies`、`GET /dependencies/template`、`POST /dependencies/import`；`RecycleBinSource` 支持 `DEPENDENCY`。

- [ ] **步骤 1：建立服务和 Controller 契约测试**

  测试列表 SQL 必须包含参数/两侧系统投影与项目过滤；创建/更新必须传 `parameterId`、`consumerSystemCode`；模板表头必须正好为“参数英文名、使用方系统编号”；导入测试覆盖零命中、多义、停用系统、文件内重复、库内重复和部分成功。

- [ ] **步骤 2：运行基线测试确认缺口**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DependencyServiceTest test`

  预期：新类或端点不存在导致编译/测试失败，记录缺口。

- [ ] **步骤 3：实施服务、接口与审计**

  按项目/租户强制过滤；参数英文名在当前项目做大小写不敏感精确匹配，恰好一条才解析 ID；系统必须是当前项目启用组件。单条冲突返回 409；导入逐行部分成功，合法行独立事务写入并记录 `entity_type='DEPENDENCY'`。列表和详情返回约定字段及更新人名称。

- [ ] **步骤 4：接入回收站来源**

  实现 `countDeleted/listDeletedPage/detail/restore/purge`，恢复时重新校验参数和系统有效性及关系唯一键；彻底删除仅删除软删关系并写审计。

- [ ] **步骤 5：运行服务局部验证**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DependencyServiceTest test`

  预期：CRUD、投影、导入部分成功、审计、权限错误和回收站断言全部通过。

**回滚：** 删除新增服务/Controller/来源及其测试，恢复前一版本依赖服务注册；不回退 T1 迁移。

**停止条件：** 无法在不依赖业务模块私有实现的情况下投影参数/系统、导入无法逐行提交、或统一回收站契约要求旧附件字段。

**升级条件：** 参数英文名在项目内多义且产品要求自动选择、需要新增公共 API、或发现回收站恢复必须改变统一平台能力。

### T3：剥离文件资产链路、参数引用保护与看板适配

**需求映射：** R5, R6, R7, R8

**前置任务：** T2

**已证实输入事实：** `ContentFileAssetService.MANAGED_TYPES` 目前包含 `DEPENDENCY`；`ContentAssetController` 注册上传/替换/下载；`ContentAssetRecycleBinSource` 将 DEPENDENCY 路由至附件服务；`DashboardMetricDefinition.DEPENDENCY` 读取 `doc_code/doc_name`。

**文件：**
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ContentAssetController.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentAssetRecycleBinSource.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ParameterService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DashboardMetricDefinition.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java` 及相关回归测试

**接口：**
- 消费：T2 的 `DependencyService` 和 `DependencyRecycleBinSource`。
- 产出：通用文件 Controller 不再暴露 `/dependencies/upload`、`/{id}/upload`、`/{id}/download`；看板 DEPENDENCY 下钻返回参数/系统可读标识；参数删除前查询依赖引用。

- [ ] **步骤 1：补充旧入口消失和引用保护测试**

  断言 `MANAGED_TYPES` 不含 DEPENDENCY，Controller 无旧端点，通用回收站来源不支持 DEPENDENCY；参数活动删除有活动依赖时返回冲突，彻底删除有任意依赖时返回冲突；看板 SQL 不包含 `dm_dependency.doc_code/doc_name`。

- [ ] **步骤 2：运行回归基线**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DataMigrationModuleRegistrationTest,DashboardMetricDefinitionTest,DashboardMetricMySqlTest,ParameterServiceTest test`

  预期：新增断言在当前实现下失败，既有 REQ-067 参数英文名测试保持可见。

- [ ] **步骤 3：移除通用 DEPENDENCY 分支并调整看板**

  从通用文件服务、Controller 和回收站来源去除 DEPENDENCY；看板下钻改用安全的固定关联 SQL 或专属下钻实现；参数删除和回收站彻底删除加入 `dm_dependency` 引用检查，保留租户/项目过滤和审计。

- [ ] **步骤 4：运行后端回归**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DataMigrationModuleRegistrationTest,DashboardMetricDefinitionTest,DashboardMetricMySqlTest,ParameterServiceTest test`

  预期：旧端点消失、看板、参数引用保护和 REQ-067 既有测试通过，退出码 0。

**回滚：** 在未应用 V199 时可恢复旧通用分支；V199 应用后不得恢复旧读写 SQL，必须保留专属服务并修复前进。

**停止条件：** 仍有任何活动代码读取 `dm_dependency.doc_code/doc_name`，或删除参数会绕过租户/项目/审计检查。

**升级条件：** 看板公共契约必须保留旧字段且无法从新关系投影，或参数删除语义需要跨业务模块变更。

### T4：前端 API、专属页面和响应式交互

**需求映射：** R2, R3, R4, R5, R7, R8

**前置任务：** T2, T3

**已证实输入事实：** `ParametersPage.vue` 已提供专属页、参数选择和导入状态范式；`AssetListView.vue` 含文件上传入口，依赖页必须不再复用；交付示范中心已有表格、移动卡片、导入/错误/空状态组合。

**文件：**
- 修改：`web/src/api/data-migration.ts`
- 修改：`web/src/modules/data-migration/views/content/DependenciesPage.vue`
- 测试/静态契约：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java`（页面路径与文案断言如现有模式）

**接口：**
- 消费：T2 的 REST 字段、分页响应、导入结果和错误语义；系统选项公共端点。
- 产出：页面调用专属 API，表格字段和移动卡片字段与 R2 一致；新增/编辑抽屉提交 `parameterId/consumerSystemCode`；导入显示逐行结果。

- [ ] **步骤 1：建立前端契约基线**

  先在 API 类型和页面测试/静态断言中写入字段、旧上传入口不存在、模板文案和手机状态要求；运行构建确认当前页面缺少专属方法。

- [ ] **步骤 2：实施 API 类型与请求封装**

  增加 `DependencyRecord`、查询/表单/导入结果类型及 list/detail/options/create/update/delete/template/import 方法；不再让依赖页调用 `listDataMigrationAssetsPage`、`uploadDataMigrationAsset`、`downloadDataMigrationAsset`。

- [ ] **步骤 3：实施页面**

  复用现有 UI 组件和语义主题，桌面表格展示全部 8 个字段，移动端卡片按“参数、使用方、提供方、更新时间/更新人”重排；新增/编辑抽屉提供可搜索参数和系统选择；导入对话框只接受两列模板并保留部分失败结果；覆盖加载、空、失败、无权限、提交中和防重复。

- [ ] **步骤 4：运行前端构建**

  运行：`npm --prefix web run build`

  预期：TypeScript/Vite 构建退出码 0，无依赖页未使用导入或附件 API 的类型错误。

**回滚：** 未应用 V199 前可恢复旧页面；V199 应用后保留新页面契约，回退只允许修复前进。

**停止条件：** 页面只能通过整页横向滚动展示、参数选择无法说明多义、或依赖页仍调用附件 API。

**升级条件：** 现有 UI 组件无法满足导入逐行结果或移动端字段可读性，需新增公共组件/样式时先暂停并申请范围变更。

### T5：集成验证、治理检查与浏览器验收

**需求映射：** R1-R8

**前置任务：** T1, T2, T3, T4

**文件：**
- 修改：`.ai-control/requirements/req-20260910-068-dm-dependency/*.json`
- 测试证据：无新增产品文件；保存命令和结果到当前前缀账本

**接口：**
- 消费：T1-T4 全部实现、测试和前端构建产物。
- 产出：治理、后端、前端、运行和浏览器验收证据；发现偏差时回到 T2/T3/T4 对应执行任务。

- [ ] **步骤 1：执行静态和范围检查**

  运行：`git diff --check`；`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260910-068-data-migration-dependency/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`；`node scripts/check-all-governance.mjs`。

  预期：本任务新增文件无空白错误，范围检查只报告当前任务或已知基线文件，治理检查结果如实记录。

- [ ] **步骤 2：执行后端完整测试**

  运行：`mvn -pl :ccb-data-migration -am test`

  预期：数据迁移模块及依赖模块测试通过；失败时记录首个原子反馈，不以构建成功代替行为验证。

- [ ] **步骤 3：执行前端生产构建**

  运行：`npm --prefix web run build`

  预期：构建成功；若工作区 REQ-067 改动造成冲突，区分其基线影响并保护用户修改。

- [ ] **步骤 4：执行真实浏览器验收**

  启动本地依赖、后端和前端，访问 `/data-migration/content/dependencies`，使用只读和可写角色分别完成筛选、详情、新增、编辑、两列导入含失败行、删除、回收站恢复/彻底删除；在 `1280x800`、`375x812`、`390x844`、`430x932` 检查明暗主题、控制台错误、请求响应、遮挡和 `document.documentElement.scrollWidth <= innerWidth`。

  预期：所有必须路径可达，无页面级横向溢出、重复提交或旧文件动作；保存真实浏览器结果和限制。

- [ ] **步骤 5：形成收敛证据**

  由独立观测记录每个需求的预期/观察、P0-P3、残余风险；通过 `control_loop.py` 按阶段转移并生成 `convergence.json`，不得手工改 phase。

**回滚：** 按 T1-T4 任务边界回退；保留账本证据。V199 已应用时遵循前向修复原则。

**停止条件：** 任一 P0/P1、权限越权、迁移非空误删、页面白屏、关键路径不可达或范围越界。

**升级条件：** 治理基线故障无法隔离、MySQL/浏览器环境不可用、或需要用户重新决定历史数据/公共契约。

## 集成检查

1. `node -e "const fs=require('fs'); for (const f of process.argv.slice(1)) JSON.parse(fs.readFileSync(f,'utf8')); console.log('ledger JSON passed')" .ai-control/requirements/req-20260910-068-dm-dependency/*.json`
2. `mvn -pl :ccb-data-migration -am test`
3. `npm --prefix web run build`
4. `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260910-068-data-migration-dependency/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`
5. `git diff --check`
6. 真实浏览器四视口路径和页面级横向溢出检查

## 控制模型种子

以下内容仅为 `$model-engineering-system` 待验证的 hypotheses-only 输入：

- 被控边界候选：`dm_dependency` 表、data-migration 后端 REST、依赖页、统一回收站和 DEPENDENCY 看板下钻。
- 状态变量候选：迁移版本、依赖活动/删除状态、参数与系统有效性、关系唯一键、当前页面加载/提交状态。
- 接口候选：V199 Flyway、DependencyService/Controller、ParameterService 删除保护、前端 API、RecycleBinSource、DashboardMetricDefinition。
- 传感器候选：MySQL Testcontainers/迁移断言、JUnit/JdbcTemplate 行为测试、静态路由检查、Vue 构建、真实浏览器网络/DOM/控制台采样。
- 执行器候选：追加迁移、服务和 Controller 写入、前端页面/API 修改、测试和浏览器命令。
- 扰动候选：当前工作区 REQ-067 未提交改动、目标库非空、已有参数英文名多义、MySQL/浏览器环境不可用、旧客户端仍请求上传端点。
- 时延候选：Flyway 在应用启动前执行、导入逐行写入、前端异步列表/导入请求、回收站恢复前的重新校验。

## 风险与用户批准

- 高风险动作：删除 `dm_dependency.doc_code/doc_name`、移除旧上传下载接口、追加菜单权限和参数删除保护。
- 主要回退：V199 未执行前可回退代码；执行后仅允许经 Owner 批准的前向补偿或修复前进。
- 用户已批准设计，但尚未批准本实施计划；计划导入和任何产品代码修改必须等待计划确认。

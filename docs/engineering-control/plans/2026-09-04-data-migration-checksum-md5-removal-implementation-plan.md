# 数据迁移内容 checksum_md5 移除实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 从 data-migration 文件型内容模型和接口中完整移除 `checksum_md5`，关闭 MD5 查重，同时保持附件绑定、对象存储、权限、审计和按 `id` 替换文件能力。

**架构：** 变更边界限定在 business/data-migration、当前需求文档/账本和一条追加 Flyway 清理迁移。服务端不再计算、查询、接收或投影摘要；前端直接上传；数据库在 V173 后条件删除 7 张文件型内容表的摘要列及索引。`att_file`、platform/shared 和其他模块不变。

**技术栈：** Java 17、Spring Boot/JdbcTemplate、MySQL 8.4/Flyway、Vue 3/TypeScript、JUnit/Maven、Vite、Playwright。

## 全局约束

- 需求基准：`REQ-20260820-031`，设计：`docs/engineering-control/designs/2026-09-04-data-migration-checksum-md5-removal-design.md`。
- 目标模块为 `business/data-migration`；只修改当前 `codex-task-scope.yaml` 覆盖的文件。
- Flyway 只追加；不修改 V90、V160、V162、V163、V170 等历史迁移，不连接生产环境。
- 不修改 `att_file`、platform/shared Java 能力、其他业务模块和历史生产数据。
- 删除 `dm_plan`、`dm_mapping_doc`、`dm_dependency`、`dm_script`、`dm_topic`、`dm_release_drill`、`dm_report` 的 `checksum_md5` 和 `idx_*_md5`。
- 删除 `/content/check-md5`、`/reports/check-md5` 及所有 MD5 请求/响应契约；重复文件允许入库。
- `id`、`doc_code`、`active_doc_code`、`attachment_id`、项目/租户授权、附件生命周期和审计语义保持不变。

## 文件职责地图

- `server/src/platform/infrastructure/src/main/resources/db/migration/V174__data_migration_remove_checksum_md5.sql`（candidate-new）：V173 后幂等删除 7 张文件表的 MD5 索引和列。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentAssetTables.java`：文件表注册；移除 MD5 UNION SQL/参数生成。
- `ContentFileAssetService.java`、`PlanService.java`、`ReportService.java`：移除摘要参数、校验、写入和查询投影；保留附件与业务编号流程。
- `ContentAssetController.java`、`ReportController.java`：删除摘要参数和查重路由，保持认证/项目/实体授权。
- `web/src/api/data-migration.ts`、data-migration 页面：删除摘要类型、计算、查重请求、上传字段和展示。
- `database-schema-and-relations.md`、`requirement.md`、`design-report-material.md`、相关工程设计：同步数据模型、接口和验收口径。
- `ContentAssetMigrationMySqlTest.java`、`PlanDomainMigrationMySqlTest.java`、`ReportServiceTest.java` 及相关 Controller/Service 测试：验证列/索引删除、无摘要契约和附件回归。

## 任务依赖图与并行策略

严格串行：T1（范围授权与迁移）→ T2（后端契约）→ T3（前端与文档）→ T4（集成验证与证据）。所有任务共享文件表契约和 Flyway 版本，不能并行写入。

## 需求覆盖表

| 需求 | 覆盖任务 |
| --- | --- |
| R1 数据模型移除 | T1, T4 |
| R2 服务端契约移除 | T2, T4 |
| R3 前端契约移除 | T3, T4 |
| R4 附件能力保持 | T2, T4 |

### T1：追加迁移并锁定无摘要数据库模式

**需求映射：** R1

**前置任务：** 无

**文件：**
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml`，登记新迁移路径。
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V174__data_migration_remove_checksum_md5.sql`。
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ContentAssetMigrationMySqlTest.java`、`PlanDomainMigrationMySqlTest.java`，移除对最终摘要列的正向断言并增加 V174 后列/索引缺失断言。

**接口：**
- 消费：V162/V163 建表和回填、V170 MD5 索引调整、V173 之前的现有模式。
- 产出：执行到 V174 后 7 张文件表均无摘要列/索引，其他列和附件绑定存在。

- [ ] **步骤 1：建立迁移基准**

运行：`rg -n "checksum_md5|idx_.*_md5" server/src/platform/infrastructure/src/main/resources/db/migration/V162__data_migration_content_tables.sql server/src/platform/infrastructure/src/main/resources/db/migration/V163__data_migration_content_table_backfill.sql server/src/platform/infrastructure/src/main/resources/db/migration/V170__data_migration_md5_index_project_id.sql`

预期：只确认历史迁移确实创建/引用摘要列，不能修改这些脚本；记录命中和版本顺序。

- [ ] **步骤 2：登记写入边界**

在 `codex-task-scope.yaml` 的 `writable_paths` 增加 `V174__data_migration_remove_checksum_md5.sql`。

预期：当前 scope 检查将允许新迁移，未授权路径仍不变；证据保存 scope diff。

- [ ] **步骤 3：实现幂等清理迁移**

为 7 张表逐表按 `information_schema.statistics` 条件删除 `idx_<table>_md5`，再按 `information_schema.columns` 条件删除 `checksum_md5`；使用 MySQL 动态 SQL，确保列/索引已不存在时安全执行，迁移末尾不删除 `att_file` 内容。

预期：全新数据库和已执行到 V173 的数据库均可执行 V174；其他表结构不变。

- [ ] **步骤 4：增加隔离数据库断言**

运行目标 MySQL 迁移测试，断言 7 张表的 `information_schema.columns` 无 `checksum_md5`、`statistics` 无 `idx_*_md5`，并断言 `attachment_id`、`doc_code`、`active_doc_code` 仍存在。

**验收、证据与回滚：** 保存迁移退出码、列/索引查询结果和 `git diff --check`；代码回滚不撤销已执行数据库迁移，数据库恢复需独立补偿迁移。若历史脚本在 V174 后仍引用摘要列，停止并回到建模。

### T2：删除服务端摘要契约并保留附件流程

**需求映射：** R2, R4

**前置任务：** T1

**文件：**
- 修改：`ContentAssetTables.java`、`ContentFileAssetService.java`、`PlanService.java`、`ReportService.java`。
- 修改：`ContentAssetController.java`、`ReportController.java`。
- 测试：`ContentAssetMigrationMySqlTest.java`、`ReportServiceTest.java`、`DataMigrationModuleRegistrationTest.java`、相关 Controller 测试。

**接口：**
- 产出：文件创建/替换和报告上传/更新签名不含 `md5`/`checksumMd5`；删除两个 `check-md5` 路由。
- 保留：按 `id` 替换文件、`attachment_id` 绑定、项目/实体授权、审计、`doc_code` 和非摘要文件元数据。

- [ ] **步骤 1：建立后端契约基准**

运行：`rg -n "checksum_md5|check-md5|assertMd5Available|md5Hits|normalizeMd5|checksumMd5" server/src/modules/data-migration/src/main/java server/src/modules/data-migration/src/test/java`

预期：列出全部服务/Controller/测试调用方，作为删除清单；不修改平台附件代码。

- [ ] **步骤 2：移除文件服务摘要路径**

从 `ContentFileAssetService` 删除摘要参数、规范化、查重方法及 SQL 字段；创建和按 `id` 替换只写 `doc_code`、名称、组件和附件绑定。同步删除 `ContentAssetTables.md5UnionSql/md5UnionArgs`。

预期：文件内容创建/替换不再执行跨表摘要查询，重复内容不产生摘要冲突。

- [ ] **步骤 3：移除计划/报告摘要路径**

从 `PlanService`、`ReportService` 的写入、查询、批量上传、更新和响应投影中删除摘要；保留报告周期、日期、关键字和附件信息。

预期：SQL 不引用 `checksum_md5`，报告上传不再要求 `checksumMd5s` 与附件数量匹配。

- [ ] **步骤 4：收敛 Controller 路由**

删除 `@RequestParam md5/checksumMd5` 和 `/check-md5` 映射；保留认证、项目可达性、实体授权、附件校验和审计入口。

预期：旧摘要参数不会进入业务写入；查重路由不再注册，其他上传路由返回结构不变。

- [ ] **步骤 5：运行后端局部测试**

运行：`mvn -pl :ccb-data-migration -am -Dtest=ContentAssetMigrationMySqlTest,ReportServiceTest,DataMigrationModuleRegistrationTest test`

预期：服务测试通过，附件新增/替换和报告上传通过，查重路由不存在；失败时保留真实日志并停止扩大范围。

**验收、证据与回滚：** 保存 Maven 结果和静态搜索；应用代码可回退，数据库列不反向恢复。若发现外部消费者必须依赖摘要契约，升级用户而不是加兼容代理。

### T3：删除前端摘要契约并同步业务文档

**需求映射：** R1, R3

**前置任务：** T2

**文件：**
- 修改：`web/src/api/data-migration.ts`、`web/src/modules/data-migration/views/content/PlansPage.vue`、`ReportsPage.vue`、`RecycleBinPage.vue`、相关资产/结构化组件。
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/database-schema-and-relations.md`、`requirement.md`、`design-report-material.md`、已登记的相关工程设计文档。

**接口：**
- 消费：T2 的无摘要上传/替换、报告上传/更新接口。
- 产出：前端上传请求不含摘要字段；列表/回收站不展示摘要；文档以无摘要模型为唯一口径。

- [ ] **步骤 1：建立前端/文档基准**

运行：`rg -n "checksum_md5|check-md5|computeFileMd5|checksumMd5|checksumMd5s|md5" web/src/api/data-migration.ts web/src/modules/data-migration docs/requirements/REQ-20260820-031-data-migration-asset-library-v3`

预期：列出全部前端字段、计算和文档段落；历史迁移脚本不纳入前端契约清单。

- [ ] **步骤 2：收敛 API 类型和请求**

删除摘要字段、计算函数、查重 API、FormData 字段和批量摘要数组；上传/替换只提交项目、元数据和附件。

预期：TypeScript 类型不再暴露 MD5；同内容文件可直接提交。

- [ ] **步骤 3：收敛页面和展示**

移除计划/报告上传前摘要计算和提示、编辑摘要状态、回收站摘要列；保留文件名、大小、业务编号、附件操作和移动端布局。

预期：桌面和 `390x844` 无摘要控件、请求或溢出。

- [ ] **步骤 4：同步需求和关系文档**

删除 3.1 及各文件表的 `checksum_md5`/MD5 索引描述，移除 `check-md5` 和跨项目查重验收，补充“重复内容允许入库、att_file 保持不变、新增 V174 清理迁移”的口径；历史迁移只保留事实说明。

预期：当前需求文档、设计文档和实现契约一致，无未决占位符。

- [ ] **步骤 5：运行前端构建与静态检查**

运行：`npm --prefix web run build`；`rg -n "checksum_md5|check-md5|computeFileMd5|checksumMd5|checksumMd5s" web/src/api/data-migration.ts web/src/modules/data-migration`

预期：构建成功；业务前端/API无命中，文档命中仅允许明确的历史迁移事实或本设计的反例描述。

**验收、证据与回滚：** 保存构建、静态搜索和文档 diff；前端可随应用代码回退。若移动端上传/替换操作被破坏，停止并回到 T2/T3 设计调整。

### T4：集成回归、运行观测与交接证据

**需求映射：** R1, R2, R3, R4

**前置任务：** T3

**文件：**
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/execution-T37.json`、`observation-T37.json`。
- 只读观测：当前需求 scope、数据库迁移、data-migration 服务/页面和相关测试。

**接口：**
- 消费：T1-T3 的无摘要数据库、服务端和前端契约。
- 产出：独立可复验的执行/观测证据；不改写 T36 历史证据和 canonical converged 状态。

- [ ] **步骤 1：运行模块回归**

运行：`mvn -pl :ccb-data-migration -am test`。

预期：模块测试 0 失败；若迁移环境或既有测试失败，按真实原因记录。

- [ ] **步骤 2：运行全量构建与规则检查**

运行：`npm --prefix web run build`；`git diff --check`；`node scripts/check-all-governance.mjs`；当前 scope 检查命令。

预期：前端、差异检查通过；治理/范围若受无关工作树改动影响，明确归因，不修改无关文件。

- [ ] **步骤 3：隔离运行和 API 验收**

在本地 MySQL 8.4/MinIO/后端环境验证：创建两个相同内容文件、按 `id` 替换、报告单条/批量上传、回收站；检查无摘要冲突、附件可访问、权限/项目隔离和审计仍在。

预期：无 `check-md5` 调用、无 5xx、重复内容均可入库、替换保留 `doc_code` 和附件生命周期。

- [ ] **步骤 4：浏览器验收**

使用管理员和普通用户，在 `1280x800` 与 `390x844` 完成计划/报告上传、替换、回收站操作；检查控制台、网络、长文本和滚动宽度。

预期：无 MD5 控件/请求、无白屏/遮挡/横向溢出；临时数据和附件清理。

- [ ] **步骤 5：写入证据并收敛**

保存真实命令、退出码、测试数量、运行/浏览器结果和残余风险；只有所有 must 验收通过才将 T37 观测标记为收敛增量，不修改既有历史 `convergence.json`。

**验收、证据与回滚：** 形成 T37 执行/观测 JSON；应用回退无需数据库反向迁移，已执行 V174 的数据库恢复必须另立补偿迁移。

## 集成检查

- `jq empty` 校验设计、计划和 T37 证据 JSON。
- `git diff --check`。
- data-migration Maven 模块测试、前端构建、隔离 MySQL 迁移和本地 API/浏览器路径。
- 静态搜索确认业务代码/API/页面不再引用摘要契约；历史 Flyway 仅作为只读迁移事实。

## 控制模型种子

以下仅为 `$model-engineering-system` 验证的候选假设：

- 被控边界：data-migration 文件内容的数据库列、服务端上传/替换、报告上传、前端上传和附件关系。
- 状态变量候选：V174 模式、内容行字段、附件绑定、HTTP 参数、前端上传状态、测试/构建结果。
- 接口候选：文件创建/替换、报告上传/更新、附件绑定；`check-md5` 删除为负向接口状态。
- 传感器候选：information_schema、JUnit/Maven、静态搜索、Vite、运行 API、Playwright。
- 执行器候选：V174、服务/Controller/API/UI/测试修改。
- 扰动候选：共享工作树无关改动、旧客户端摘要参数、迁移链历史引用、重复对象存储增长。
- 时延候选：前后端同版本发布；迁移完成后模式可观测；对象清理异步。
- 假设：模块尚未投产；V174 可排在所有摘要引用迁移之后；att_file 不依赖 data-migration 摘要列。

## 风险与用户批准

- 主要风险是关闭查重后重复对象存储增长，以及旧客户端调用被删除接口；通过同版本发布、容量监控和后续独立需求处理。
- 设计已由用户确认“完整移除并关闭 MD5 查重”；本计划按该设计执行。
- 计划状态：待用户确认后进入 control-engineering baseline/modeling/planning/executing。

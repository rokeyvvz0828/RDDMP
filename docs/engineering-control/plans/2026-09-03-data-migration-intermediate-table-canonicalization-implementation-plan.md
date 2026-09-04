# 数据迁移中间表模型唯一化实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。计划基于已批准的 ADR-DM-20260903-01，测试节点先收敛应用，再由 V169 断言空表并清理旧表。

**目标：** 将中间表唯一收敛到 `dm_target_table(table_category='INTERMEDIATE')` + `dm_target_table_field`，停止旧表应用读写并安全清理空旧表。

**架构：** 应用先停止旧表读写，V169 在删除前断言测试节点旧表为空并清理；业务服务和前端统一复用 `TargetTableService`，最终只保留规范化主表。

**技术栈：** MySQL 8.4/Flyway、Spring JdbcTemplate、Vue 3/TypeScript、JUnit/Testcontainers。

## 全局约束

- 只追加 V169，不修改 V88/V162/V163 或任何已发布迁移。
- V169 只在旧表为空时删除 `dm_intermediate_table`，不修改 `pm_project`、`pm_project_member` 和 platform/system。
- 保留租户、项目可达性、RBAC、实体授权和审计；不引入动态表单元数据。
- 当前为测试节点，不迁移旧表存量；删除前必须执行空表断言。

---

### T1：建立旧表空值断言与清理迁移

**需求映射：** R1, R2, R4

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V169__data_migration_intermediate_table_canonicalization.sql`
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ContentAssetMigrationMySqlTest.java`

**接口：**
- 消费：V88 创建的 `dm_target_table`/`dm_target_table_field` 与 V162/V163 创建的 `dm_intermediate_table`。
- 产出：V169 迁移完成后，旧表不存在；中间表业务由目标表/字段表统一承载。

- [ ] 建立旧表为空和存在任意数据的 fixture。
- [ ] 在 `DROP TABLE` 前使用存储过程断言；失败必须 `SIGNAL`，不执行删除。
- [ ] 仅追加 V169，在删除前断言旧表为空，不复制存量数据。
- [ ] 运行 `mvn -pl :ccb-data-migration -am test`，记录迁移成功和阻断证据。

**回滚：** V169 失败时旧表保留；测试数据库通过重建回退，禁止反向 Flyway。

**停止条件：** 旧表存在任意数据，或无法证明清理发生在接口收敛之后。

**升级条件：** 需要修改历史迁移、平台表或迁移非空存量时升级给 Owner。

### T2：后端接口和回收站事实源收敛

**需求映射：** R1, R3, R5

**前置任务：** T1

**文件：**
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/StructuredAssetService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentAssetTables.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ExcelService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentAssetRecycleBinSource.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/TargetTableService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/StructuredAssetController.java`
- 修改/测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ContentRecycleBinRegistryTest.java`

**接口：**
- 消费：现有 `/target-tables`、`/{id}/fields` 及 `TargetTableService` 的 `INTERMEDIATE` 类别。
- 产出：结构化内容服务只接受 RULE/PARAMETER；中间表回收站、统计和关联检查从目标表/字段读取。

- [ ] 删除旧表名常量和 `INTERMEDIATE_TABLE` 业务分支；旧结构化端点明确拒绝。
- [ ] 为 `INTERMEDIATE` 目标表补齐回收站分页、详情、恢复、清理分发，保留项目和权限校验。
- [ ] 更新关系检查/统计查询，确保中间表只来自目标表类别。
- [ ] 运行模块单测、旧表 SQL 残留扫描和模块边界检查。

**回滚：** 恢复应用版本；V169 已执行时通过测试库重建恢复旧表。

**停止条件：** 目标表回收站操作无法复用既有权限/审计语义。

**升级条件：** 需要新增 platform 公开契约或修改其他业务模块时升级。

### T3：前端菜单和契约收敛

**需求映射：** R3, R5

**前置任务：** T2

**文件：**
- 删除：`web/src/modules/data-migration/views/base/IntermediateTablesPage.vue`
- 修改：`web/src/router/index.ts`
- 修改：`web/src/modules/data-migration/views/base/TargetTablesPage.vue`
- 修改：`web/src/modules/data-migration/components/StructuredListView.vue`
- 修改：`web/src/api/data-migration.ts`

**接口：**
- 消费：`TargetTablesPage` 的 `category='INTERMEDIATE'` 和 `table-fields-intermediate*` 权限码。
- 产出：中间表入口只加载规范化表/字段 API；前端不存在 `INTERMEDIATE_TABLE` 结构化资产编辑链路。

- [ ] 保留路由路径和菜单标题，确认组件直接指向 `TargetTablesPage` 并传入类别。
- [ ] 移除结构化列表、API 类型和回收站展示中的旧内容类型分支。
- [ ] 保留加载/空/失败/无权限/提交中状态，验证桌面与移动视口无溢出。
- [ ] 运行 `npm --prefix web run build`。

**回滚：** 回滚应用版本；不回滚数据库迁移。

**停止条件：** 现有菜单权限或移动端布局无法保持兼容。

**升级条件：** 需要修改公共 UI 或 router 公共契约时升级。

### T4：独立回归与收敛验收

**需求映射：** R1-R5

**前置任务：** T3

**文件：**
- 测试证据：`.ai-control/requirements/req-20260903-064-data-migration-intermediate-table-canonicalization/*.json`

- [ ] 执行 `mvn -pl :ccb-data-migration -am test`。
- [ ] 执行 `npm --prefix web run build`。
- [ ] 执行 `node scripts/check-all-governance.mjs`、Flyway 检查和 `git diff --check`。
- [ ] 执行模块范围扫描：生产代码不含 `dm_intermediate_table` SQL；V169 仅包含受控 DROP。
- [ ] 记录三次观测、残余风险和回退路径，满足高保证收敛门禁。

**回滚：** 回滚应用版本；数据库通过测试库重建恢复。

**停止条件：** 任一 must 需求无可复验通过证据。

**升级条件：** 出现数据丢失、跨租户访问或旧表新写入。

## 依赖与采样

任务串行执行：`T1 -> T2 -> T3 -> T4`。采样点为迁移 fixture、后端旧表扫描、前端构建和全量治理；不设置并行组，避免数据库与接口契约同时变更造成不可观测偏差。

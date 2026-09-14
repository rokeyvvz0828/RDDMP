# 2026-09-11 数据迁移文件资产通用链路下线实施计划（REQ-20260911-070）

## 任务

### T1 后端删除与精简
- 删除 `StructuredAssetService/StructuredAssetController/ExcelService/ContentAssetController`。
- 精简 `ContentFileAssetService`（保留 resolveAttachment/replaceMainFile/BUSINESS_TYPE，构造 3 参）。
- `ContentDocCodeGenerator` 收敛 6 前缀；`ContentRecycleBinService` 注释修正。
- 写边界：上述 6 个后端文件（含 4 删除）。

### T2 测试与治理断言
- `DataMigrationModuleRegistrationTest`：删除源码文件读取改为 `Files.notExists` 断言。
- `ContentFileAssetDocCodeGovernanceTest`：改为通用链路下线治理断言。
- `ContentDocCodeGeneratorTest`：前缀表 6 项 + 拒绝 DEPENDENCY/PARAMETER。
- `ReportServiceTest/TopicServiceTest`：`ContentFileAssetService` 构造调用同步。
- 写边界：`server/src/modules/data-migration/src/test/**`。

### T3 前端死代码
- 删除 `AssetListView.vue`/`StructuredListView.vue`；删除 `data-migration.ts` 通用函数与 `DataMigrationAsset`。
- 写边界：`web/src/api/data-migration.ts`、`web/src/modules/data-migration/components/*.vue`。

### T4 集成验证
- `mvn -pl :ccb-data-migration -am test` 全量；`npm run build`；`git diff --check`；启动后端并执行依赖页/回收站/看板冒烟。

## 依赖与门禁

- 依赖顺序：T1 -> T2 -> T3 -> T4。
- 每任务完成本地检查；观察阶段按 T1（后端编译+引用扫描）、T2（测试断言）、T3（前端构建）、T4（运行证据）采样。
- 回滚：全部为代码删除，git checkout 即可恢复；无数据库变更。

## 风险

- 并行 REQ-067/069 未提交改动：已核验其不引用删除类；删除后再次全仓引用扫描确认。
- 看板计数回归：`ContentAssetTables` 不动，DashboardMetricMySqlTest 复跑。

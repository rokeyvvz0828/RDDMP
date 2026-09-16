# 2026-09-11 数据迁移文件资产通用链路下线设计（REQ-20260911-070）

## 目标与边界

- 目标：删除已无调用方的通用文件/结构化资产链路（后端服务与控制器、通用 Excel 辅助、前端通用组件与 API），保留仍在复用的平台附件能力、看板计数登记与全部专属服务。
- 被控对象：`data-migration` 模块内的“通用资产链路”代码面 + 前端死代码。
- 不变量：`ContentAssetTables` 与看板/组件计数 UNION SQL 不动；`ContentAttachmentService/AttachmentStreamService/AttachmentGateway/DataMigrationAssetAttachmentAccessPolicy` 不动；全部专属 Service/Controller/RecycleBinSource 不动；无 Flyway 变更。

## 现场事实（已全仓核验）

| 组件 | 状态 |
| --- | --- |
| `ContentFileAssetService` 通用 CRUD/回收站方法（list/create/replace/delete/restore/purge/downloadAttachmentId/countDeleted/listDeletedPage/findDeletedDetail） | 无调用方（仅曾被已删除的 ContentAssetRecycleBinSource/ContentAssetController 使用） |
| `ContentFileAssetService.resolveAttachment/replaceMainFile/BUSINESS_TYPE` | 仍被 Plan/Topic/Mapping/Program/ReleaseDrill/Report 等专属服务使用，保留 |
| `StructuredAssetService.TYPES` | 空集；全仓无可达调用方（Parameter/Rule 已域化） |
| `StructuredAssetController` | 六个端点全部命中「Unsupported structured asset type」 |
| 通用 `ExcelService` | 仅被 StructuredAssetController 使用 |
| `ContentAssetController` | 空壳类，仅模块注册测试以读文件方式引用 |
| `ContentDocCodeGenerator` 的 DEPENDENCY/PARAMETER 前缀 | 无调用方（ParameterService 自生成英文名） |
| `ContentAssetTables` 的 DEPENDENCY 映射 | 在用：看板/组件计数经 ALL_TABLES + typeFor 使用 |
| 前端 `AssetListView/StructuredListView` 与通用资产 API | 两组件互引用且无任何页面引用；API 函数仅被两组件使用 |
| `ContentRecycleBinService` 类注释 | 仍声称通用来源覆盖六种文件型+三种结构化型，过期 |

## 方案（方案 A：一次性删除 + 断言收敛）

1. 删除文件：`StructuredAssetService.java`、`StructuredAssetController.java`、`ExcelService.java`、`ContentAssetController.java`（后端）；`AssetListView.vue`、`StructuredListView.vue`（前端）。
2. 精简 `ContentFileAssetService`：仅保留 `BUSINESS_TYPE`、`jdbc`、`attachmentGateway`、`attachments` 与 `resolveAttachment`/`replaceMainFile`；删除通用方法与 `MANAGED_TYPES/MAX_FILE_SIZE/docCodes/userDirectory/permissions` 成员，构造收敛为 (jdbc, attachmentGateway, attachments)。
3. `ContentDocCodeGenerator` 前缀收敛为 6 个在用类型。
4. `ContentRecycleBinService` 类注释改为“全部内容类型由专属来源认领”。
5. 测试同步：删除源码的文本断言改为 `Files.notExists` 断言；`ContentFileAssetDocCodeGovernanceTest` 改为“通用链路已下线”治理断言；`ContentDocCodeGeneratorTest` 前缀表同步；`ReportServiceTest/TopicServiceTest` 构造调用同步。
6. 前端删除 `data-migration.ts` 中 11 个通用函数/辅助与 `DataMigrationAsset` 类型（如需保留回收站类型则不删）。

## 验收

- 全仓无引用上述删除类；`npm run build` 通过；`mvn -pl :ccb-data-migration -am test` 全量通过；`git diff --check` 干净；浏览器依赖页/回收站冒烟通过。

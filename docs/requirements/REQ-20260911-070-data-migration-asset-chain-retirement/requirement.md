---
id: REQ-20260911-070
status: ready
owner: rokeyvvz0828
module: business/data-migration
---

# 彻底下线数据迁移文件资产通用链路

## 业务目标

REQ-20260910-068 已将「迁移过程依赖文件」逐项迁移为依赖关系管理域，各内容类型均已拥有专属 Service、Controller 与统一回收站来源。本需求彻底下线已无调用方的「通用文件/结构化资产链路」残留代码与前端组件，消除死代码与过期契约，保留仍在复用的平台附件能力与看板计数能力。

## 范围

### 本次实施（后端删除）

- 删除 `StructuredAssetService`（`TYPES` 已为空集，无任何可达调用方）、`StructuredAssetController`（六个 `/api/data-migration/structured/{type}` 端点全部因类型为空而不可达）、通用 `ExcelService`（仅被 `StructuredAssetController` 使用）与 `ContentAssetController`（空壳类）。
- 精简 `ContentFileAssetService`：删除通用 CRUD/回收站方法（`list/create/replace/delete/restore/purge/downloadAttachmentId/countDeleted/listDeletedPage/findDeletedDetail` 及私有辅助），仅保留 `resolveAttachment` 与 `replaceMainFile` 等仍在被专属服务复用的能力；同步删除 `MANAGED_TYPES`、`MAX_FILE_SIZE`、`docCodes`、`userDirectory`、`permissions` 等仅服务通用链路的成员。
- 精简 `ContentDocCodeGenerator`：删除 `DEPENDENCY`、`PARAMETER` 前缀条目（`PARAMETER` 已被 ParameterService 域化、无调用方）。
- 修正 `ContentRecycleBinService` 过期的类注释（不再描述已删除的通用来源）。

### 本次实施（前端删除）

- 删除无页面引用的 `AssetListView.vue`、`StructuredListView.vue`。
- 删除 `data-migration.ts` 中仅被上述组件使用的通用资产 API 与辅助（`listDataMigrationStructured/updateDataMigrationStructured/deleteDataMigrationStructured/exportDataMigrationStructured/inspectDataMigrationStructuredImport/uploadDataMigrationAsset/replaceDataMigrationAsset/deleteDataMigrationAssets/downloadDataMigrationAsset`、`structuredBase/fileSegment/buildDataMigrationAssetUpload`、`DataMigrationAsset`）。

### 本次不实施

- 不删除平台附件能力：`ContentAttachmentService`、`AttachmentStreamService`、`AttachmentGateway`、`DataMigrationAssetAttachmentAccessPolicy` 继续保留。
- 不修改 `ContentAssetTables`：`FILE_TABLES/ALL_TABLES` 与 `tableFor/typeFor` 仍被看板计数、项目组件计数和附件访问策略使用（`dm_dependency` 映射为看板 DEPENDENCY 指标服务，属在用能力）。
- 不改动任何专属服务与回收站来源（Plan/ReleaseDrill/Mapping/Program/Topic/Meeting/Report/Rule/Parameter/Dependency 及其 Controller、RecycleBinSource）。
- 不新增或修改 Flyway 迁移；不改变路由权限、菜单和审计语义。
- 不改动业务前端页面与统一回收站页（`RecycleBinPage` 继续通过专属来源工作）。

## 现状与规则

- 通用链路残留均为死代码：`ContentFileAssetService` 通用方法在删除 `ContentAssetRecycleBinSource` 后无调用方（全仓核验）；`StructuredAssetService.TYPES` 与 `ContentFileAssetService.MANAGED_TYPES` 均为空集；`StructuredListView/AssetListView` 无任何页面引用。
- 平台附件绑定（`dm_content_attachment`）由各专属服务经专用 Service 继续使用，删除通用链路不影响附件读写。
- 看板/组件计数使用 `ContentAssetTables.ALL_TABLES` 的 UNION SQL，`dm_dependency` 计数依赖该登记表，保持不动。
- 测试同步更新：`DataMigrationModuleRegistrationTest` 中读取已删除源码的断言改为对文件不存在的断言；`ContentFileAssetDocCodeGovernanceTest` 改为断言通用链路已下线；`ContentDocCodeGeneratorTest` 前缀表同步缩减；`ReportServiceTest/TopicServiceTest` 中 `ContentFileAssetService` 构造调用同步精简。

## 接口与数据

- 删除端点：`/api/data-migration/structured/{type}(/delete|/export|/import)`、`/api/data-migration/{type}/upload|{id}/upload|{id}/download|delete`（通用文件型）——均由各专属 Controller 承接对应业务。
- 无数据库变更、无权限变更、无菜单变更。

## 验收标准

- 全仓不存在对 `StructuredAssetService/StructuredAssetController/ExcelService(通用)/ContentAssetController` 的代码引用。
- `ContentFileAssetService` 不再包含 `MANAGED_TYPES` 与通用 CRUD/回收站方法；`resolveAttachment/replaceMainFile/BUSINESS_TYPE` 保留且可编译。
- `ContentDocCodeGenerator` 仅含 PLAN/MAPPING_DOC/SCRIPT/TOPIC/RELEASE_DRILL/REPORT 六个前缀。
- 前端 `npm run build` 通过；`AssetListView/StructuredListView` 与上述 API 函数不存在。
- `mvn -pl :ccb-data-migration -am test` 全量通过；看板计数、项目组件计数与附件访问策略测试不受影响。
- `git diff --check` 干净。

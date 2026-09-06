# 数据迁移“投产及演练”实施计划

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-05-data-migration-release-drill-design.md`
- 状态：待用户复核
- 控制前缀：`req-20260820-031-data-migration-asset-library-v3`

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将“数据迁移 / 数迁资产内容 / 投产及演练”从通用文件资产薄页升级为专属域功能，保存颗粒度、资料类型、所属轮次和单一源文件，支持组合筛选、查看、编辑、下载、逻辑删除与统一回收站。

**架构：** 后端新增 `ReleaseDrillService`/`ReleaseDrillController`/`ReleaseDrillRecycleBinSource`，并使用 `V184` 追加迁移在 `dm_release_drill` 增加业务字段和参数初始项；`RELEASE_DRILL` 从通用文件资产链路上摘除；前端重写 `ReleaseDrillsPage.vue` 并扩展 `data-migration.ts`。

**技术栈：** Spring Boot 3.4、MyBatis/Spring JDBC、MySQL 8 / Flyway、Vue 3 + TypeScript + Element Plus。

## 全局约束

- 只修改 `docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml` 中 `writable_paths` 覆盖的文件。
- Flyway 只追加，不修改已发布迁移；使用 `V185__data_migration_release_drill_domain.sql`。
- 颗粒度与资料类型通过 `DataMigrationCodeValueService` 和“系统管理/参数管理”读取；业务代码与前端不硬编码选项名称。
- 数据迁移模块不读取或依赖投产模块表/服务；“所属轮次”为纯手填文本。
- 涉及物理子系统只允许 `dm_component.enabled=1` 且属于当前项目；业务表只存 `(project_id, system_code)`。
- 源文件单个，单个文件不超过 50MB；编辑不传新文件时保留原文件。
- 所有写操作执行 RBAC、项目隔离、实体授权、附件校验并写 `dm_operation_log`。
- 保持 `com.ccb.*` 包名和 `/api` 前缀；不改 `platform/system` 参数实现，不改全局项目切换器和公共附件表结构。

## 文件职责地图

| 文件 | 状态 | 职责 | 证据来源 |
|---|---|---|---|
| `server/src/platform/infrastructure/src/main/resources/db/migration/V185__data_migration_release_drill_domain.sql` | 新建 | 加列、回填、索引、参数类别/项初始化 | V180/V183 模式 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DataMigrationCodeValueService.java` | 修改 | 注册 `DM_RELEASE_DRILL_GRANULARITY` 与 `DM_RELEASE_DRILL_TYPE` | V183 模式 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ReleaseDrillService.java` | 新建 | 专属 CRUD、筛选、校验、附件、审计、回收站 SPI | TopicService/PlanService |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ReleaseDrillController.java` | 新建 | `/api/data-migration/release-drills` 路由与权限 | TopicController/PlanController |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ReleaseDrillRecycleBinSource.java` | 新建 | 统一回收站认领 `RELEASE_DRILL` | TopicRecycleBinSource |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java` | 修改 | `MANAGED_TYPES` 移除 `RELEASE_DRILL` | 现状字段 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ContentAssetController.java` | 修改 | 移除 release-drills 路由映射 | 现状字段 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ReleaseDrillServiceTest.java` | 新建 | R1-R6 行为与校验测试 | TopicServiceTest |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ReleaseDrillRecycleBinSourceTest.java` | 新建 | R7 回收站来源测试 | TopicRecycleBinSource |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentAssetTables.java` | 不改（只读确认） | 保留 `RELEASE_DRILL -> dm_release_drill` 供看板识别 | 现状 |
| `web/src/api/data-migration.ts` | 修改 | ReleaseDrill 契约、参数类别常量 | Topic/Plan API |
| `web/src/modules/data-migration/views/content/ReleaseDrillsPage.vue` | 重写 | 列表、筛选、抽屉表单、上传、下载、删除 | PlansPage/TopicsPage |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java` | 修改 | 注册断言改为新控制器，通用控制器不再含 release-drills | 现状断言 |

## 任务依赖图与并行策略

```text
T1
 |
 T2
 | \
 T3  T4
 \  /
  T5
```

- T1 必须先完成（数据库与参数基础）。
- T2 完成后，T3 与 T4 串行/并行均可，但 T3 优先级高（避免同一类型双来源）。
- T5 作为整体集成检查和最终门禁。

## 需求覆盖表

| 需求 | 覆盖任务 |
|---|---|
| R1 元数据必填与规则 | T1, T2 |
| R2 参数管理码值 | T1, T2 |
| R3 物理子系统/轮次展示 | T2, T4 |
| R4 单文件附件 | T2, T4 |
| R5 列表筛选分页与前端状态 | T2, T4 |
| R6 权限/项目隔离/审计 | T2, T3 |
| R7 回收站与链路收敛 | T3 |
| R8 范围控制 | T1-T5 |

---

### T1：V184 迁移与参数码值注册

**需求映射：** R1, R2, R8

**前置任务：** 无

**输入事实：** 当前最新迁移为 `V183`；`dm_release_drill` 目前仅含通用 14 列；`DataMigrationCodeValueService` 已具备按前缀读取参数的能力；参数选项服务已存在。

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V185__data_migration_release_drill_domain.sql`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DataMigrationCodeValueService.java`
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/TestDataMigrationCodeValues.java`
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DataMigrationCodeValueServiceTest.java`

- [ ] **步骤 1：编写 `V184` 迁移**

使用 information_schema 条件式 `ADD COLUMN`，为 `dm_release_drill` 增加：
- `granularity varchar(16) NOT NULL DEFAULT 'PROJECT'`
- `material_type_code varchar(64) NOT NULL DEFAULT 'RELEASE_PLAN'`
- `drill_round varchar(128) NOT NULL DEFAULT ''`

回填存量行：`granularity='PROJECT'`、`material_type_code='RELEASE_PLAN'`、`drill_round=''`。

新增索引：
- `idx_dm_release_drill_granularity (tenant_id, project_id, granularity, deleted, updated_at)`
- `idx_dm_release_drill_type (tenant_id, project_id, material_type_code, deleted)`

幂等插入参数：
- `sys_dict_type`：`(5812,'DM_RELEASE_DRILL_GRANULARITY','投产及演练颗粒度')`、`(5813,'DM_RELEASE_DRILL_TYPE','投产及演练资料类型')`
- `sys_config`：`DM_RELEASE_DRILL_GRANULARITY.PROJECT/COMPONENT` 与 `DM_RELEASE_DRILL_TYPE.RELEASE_PLAN/EMERGENCY_PLAN/RELEASE_SUMMARY/BRIEFING_NOTICE/SCHEDULE_TIMELINE/CRITICAL_PATH/MILESTONE_REPORT_INSTRUCTION/DRILL_ENV_INFO`（ids 58145-58154），`INSERT IGNORE` 不覆盖管理员配置。

预期：SQL 可重复执行；`SHOW CREATE TABLE dm_release_drill` 包含新列和索引；参数类别数/项数与预期一致。

证据：迁移文件内容、迁移测试输出。

- [ ] **步骤 2：扩展 `DataMigrationCodeValueService`**

增加常量：
```java
public static final String DM_RELEASE_DRILL_GRANULARITY = "DM_RELEASE_DRILL_GRANULARITY";
public static final String DM_RELEASE_DRILL_TYPE = "DM_RELEASE_DRILL_TYPE";
```
并把两个类别加入 `ALLOWED_CATEGORIES`。

预期：`options()` 和 `requireActive()` 接受新类别。

- [ ] **步骤 3：扩展测试 fixture**

在 `TestDataMigrationCodeValues.java` 添加：
```java
put("DM_RELEASE_DRILL_GRANULARITY", "PROJECT", "项目级", "COMPONENT", "组件级");
put("DM_RELEASE_DRILL_TYPE", "RELEASE_PLAN", "投产方案", "EMERGENCY_PLAN", "应急方案", "RELEASE_SUMMARY", "投产总结", "BRIEFING_NOTICE", "宣讲通知", "SCHEDULE_TIMELINE", "调度时序", "CRITICAL_PATH", "关键路径", "MILESTONE_REPORT_INSTRUCTION", "里程碑汇报指令", "DRILL_ENV_INFO", "数迁环境信息");
```

在 `DataMigrationCodeValueServiceTest` 增加新类别读取与无效编码拒绝断言。

- [ ] **步骤 4：运行局部测试并保存证据**

运行：`mvn -pl :ccb-data-migration -am -Dtest=DataMigrationCodeValueServiceTest,ContentAssetMigrationMySqlTest test`

预期：通过，失败数 0；迁移测试确认 `dm_release_drill` 新列/索引和参数项存在。

**回滚：** 不执行/不发布 `V184`，回退 `DataMigrationCodeValueService` 与测试改动；应用回退不执行反向迁移。

**停止条件：** 迁移文件无法幂等、参数项目与类别数不符、模块边界外文件被引入。

**升级条件：** 需要新平台参数接口、修改 `sys_dict_type`/`sys_config` 全局约束、或调整已有发布迁移。

---

### T2：专用后端服务与控制器

**需求映射：** R1-R6

**前置任务：** T1

**输入事实：** `TopicService`/`PlanService` 提供完整 JdbcTemplate 服务模式；`DataMigrationCodeValueService` 支持新类别；`ContentDocCodeGenerator` 已支持 `RELEASE_DRILL` 前缀 `DRILL`。

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ReleaseDrillService.java`
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ReleaseDrillController.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ReleaseDrillServiceTest.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ReleaseDrillControllerSecurityTest.java`
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/TestDataMigrationCodeValues.java`（如 T1 已做则不改）

- [ ] **步骤 1：实现 `ReleaseDrillService`**

参考 `TopicService`/`PlanService`，实现：
- `list(projectId, granularity, materialTypeCode, keyword, page, size, user)`
- `detail(id, user)`
- `create(body, user)`：校验必填、参数启用项；组件级必填一个 `system_code` 并调用 `ensureComponent(...)`；项目级系统必须为空；单附件校验并调用 `ContentAttachmentService.replaceAll`；服务端生成 `doc_code`；写审计 `RELEASE_DRILL_CREATE`。
- `update(id, body, user)`：不改变 `project_id` 与 `doc_code`；如传新附件则替换主文件；写审计 `RELEASE_DRILL_UPDATE`。
- `delete(ids, user)`：逻辑删除，写删除人/时间与审计 `RELEASE_DRILL_DELETE`。
- `download(id, user)`：返回主文件平台下载路径。
- `getSystemOptions(projectId, user)`：返回当前项目 `dm_component.enabled=1` 的 `value=system_code`、`label=编号-名称`。
- `getTypeOptions`：读取 `DM_RELEASE_DRILL_TYPE` 启用项。
- 回收站 SPI 方法：`countRecycleBin`、`fetchRecycleBinPage`、`findRecycleBinDetail`、`restore`、`purge`。

SQL 投影：
```sql
SELECT a.doc_code AS asset_code, a.doc_name AS asset_name, a.granularity,
       a.material_type_code, a.drill_round, a.system_code, sys.name AS system_name
FROM dm_release_drill a
LEFT JOIN dm_component c ON c.tenant_id=a.tenant_id AND c.project_id=a.project_id AND c.system_code=a.system_code
LEFT JOIN arch_physical_subsystem sys ON sys.tenant_id=c.tenant_id AND sys.code=c.system_code AND sys.deleted=0
WHERE a.tenant_id=? AND a.project_id=? AND a.deleted=0
```

预期：服务代码编译通过，新增/编辑/删除/下载/回收站行为与设计一致。

- [ ] **步骤 2：实现 `ReleaseDrillController`**

```java
@RestController("dataMigrationReleaseDrillController")
@RequestMapping("/api/data-migration/release-drills")
@PreAuthorize("hasAnyAuthority('data-migration:content:release-drills','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
```
提供列表、系统选项、详情、新增、编辑、删除、下载端点；写操作再叠加 `:create/:update/:delete`；数字 `PathVariable` 使用 `\\d+`。

预期：路由与权限声明与 Topics/Plan 控制器一致。

- [ ] **步骤 3：编写服务测试**

`ReleaseDrillServiceTest` 覆盖：
- 缺少必填项/组件级缺系统/项目级传系统 → 400；
- 颗粒度与类型未知或停用 → 400；
- 单附件新增成功且 `doc_code` 前缀 `DRILL`；
- 编辑不传新文件保留原附件、传新文件替换主文件；
- 跨项目/越权限拒绝；审计写入 `RELEASE_DRILL_CREATE/UPDATE/DELETE`；
- 回收站 restore 冲突翻译 409。

`ReleaseDrillControllerSecurityTest` 覆盖路由权限声明。

- [ ] **步骤 4：运行局部测试**

运行：`mvn -pl :ccb-data-migration -am -Dtest=ReleaseDrillServiceTest,ReleaseDrillControllerSecurityTest test`

预期：用例全部通过，失败数 0。

**回滚：** 删除新增服务/控制器及测试；保留 T1 迁移；不回滚已发布迁移。

**停止条件：** 参数校验无法经公开选项接口、需要直接访问平台参数私有 SQL、单附件无法在一个事务内保持附件/业务记录一致。

**升级条件：** 附件主文件替换需要修改公共附件接口、系统选项需要调用交叉模块服务、或权限码需要新增菜单/RBAC 播种。

---

### T3：回收站来源与通用链路摘除

**需求映射：** R6, R7, R8

**前置任务：** T2

**输入事实：** 当前 `ContentFileAssetService.MANAGED_TYPES` 含 `RELEASE_DRILL`；`ContentAssetController` 含 release-drills 路由；`ContentAssetRecycleBinSource.supports()` 由 `MANAGED_TYPES` 推导；`DataMigrationModuleRegistrationTest` 当前断言 `/release-drills/delete` 在通用控制器。

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ReleaseDrillRecycleBinSource.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ReleaseDrillRecycleBinSourceTest.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ContentAssetController.java`
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java`

- [ ] **步骤 1：实现 `ReleaseDrillRecycleBinSource`**

参考 `TopicRecycleBinSource`，`supports() = Set.of("RELEASE_DRILL")`；count/detail/list/restore/purge 全部委托 `ReleaseDrillService`。

预期：编译通过，统一回收站按 `type=RELEASE_DRILL` 分发。

- [ ] **步骤 2：移除通用链路**

`ContentFileAssetService.MANAGED_TYPES` 改为 `Set.of("MAPPING_DOC","DEPENDENCY","SCRIPT")`。

`ContentAssetController.RESOURCE_TYPES` 移除 `release-drills -> RELEASE_DRILL`；`@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping` 中删除 release-drills 路径段。

`ContentAssetTables.java` 保持不变，确保看板和旧数据信封仍能识别 `RELEASE_DRILL -> dm_release_drill`。

预期：通用控制器不再处理 release-drills；新控制器继续提供同样资源端点。

- [ ] **步骤 3：更新注册与回收站测试**

`DataMigrationModuleRegistrationTest.contentResourceEndpointsRequireWriteOrManageAuthority` 中“`assertTrue(assets.contains("/release-drills/delete"))`”改为断言通用控制器不含 `/release-drills/delete`，且新增 `ReleaseDrillController` 含 `/api/data-migration/release-drills` 与 `data-migration:content:release-drills:create`。

`ReleaseDrillRecycleBinSourceTest` 断言 `supports()` 只有 `RELEASE_DRILL`、count/list/detail/restore/purge 委托调用覆盖。

- [ ] **步骤 4：运行回归**

运行：`mvn -pl :ccb-data-migration -am -Dtest=DataMigrationModuleRegistrationTest,ReleaseDrillRecycleBinSourceTest,ContentAssetMigrationMySqlTest test`

预期：通过；无重复认领；其他内容类型无回归。

**回滚：** 同步恢复通用映射与 `MANAGED_TYPES`，移除 `ReleaseDrillRecycleBinSource`；必须与 T2 一起回退避免来源缺失。

**停止条件：** 看板或历史回收站信封依赖通用 `RELEASE_DRILL` 字段且无法投影；注册测试出现非目标类型变化。

**升级条件：** 需修改统一回收站公共 SPI、平台菜单/RBAC 表或跨业务模块契约。

---

### T4：前端 API 与专属页面

**需求映射：** R3-R6

**前置任务：** T2（T4 可在 T3 完成后运行集成）

**输入事实：** `ReleaseDrillsPage.vue` 当前仅包装 `AssetListView`；`PlansPage/TopicsPage` 已验证项目上下文、抽屉、附件、筛选和移动端模式；`data-migration.ts` 已提供参数选项和附件 API。

**文件：**
- 修改：`web/src/api/data-migration.ts`
- 重写：`web/src/modules/data-migration/views/content/ReleaseDrillsPage.vue`

- [ ] **步骤 1：扩展 `data-migration.ts`**

新增 `DM_CODE_CATEGORIES` 成员：
```ts
releaseDrillGranularity: 'DM_RELEASE_DRILL_GRANULARITY',
releaseDrillType: 'DM_RELEASE_DRILL_TYPE'
```

新增：
- `ReleaseDrillRecord` 含 `asset_code/asset_name/material_type_code/drill_round/system_code/system_name/granularity`
- `ReleaseDrillQuery`、`ReleaseDrillFormData`
- `listReleaseDrills/getReleaseDrill/createReleaseDrill/updateReleaseDrill/deleteReleaseDrills/getReleaseDrillDownloadPath/getReleaseDrillSystemOptions`

预期：类型与前后端字段契约一致。

- [ ] **步骤 2：重写 `ReleaseDrillsPage.vue`**

页面结构复制 `PlansPage/TopicsPage` 成功模式：
- 顶部筛选：颗粒度、资料类型、资料名称关键字；
- 表格列：资料编号、资料名称、资料类型、颗粒度、所属轮次、涉及物理子系统、操作；
- 分页 20/50/100；
- 新增/编辑抽屉：颗粒度、资料类型、所属轮次（手填）、涉及物理子系统（组件级显示并必填，单选）、资料名称、单个文件上传；
- 资料名称为空时取文件名去扩展名；
- 下载、删除、详情沿用统一交互；
- 项目切换重置筛选、抽屉和分页并重查。

预期：`npm --prefix web run build` 通过；桌面/移动视口无明显页面级横向溢出。

- [ ] **步骤 3：运行前端构建**

运行：`npm --prefix web run build`

预期：构建成功，无 TypeScript 错误。

**回滚：** 回退 `data-migration.ts` 与 `ReleaseDrillsPage.vue`；路由保持不变。

**停止条件：** 后端新接口响应字段与前端声明不一致且无法在任务内修正；参数选项接口未返回新类别。

**升级条件：** 需要修改 `web/src/components/ui`、`web/src/stores`、`web/src/router` 或共享组件契约。

---

### T5：集成检查与收敛门禁

**需求映射：** R1-R8

**前置任务：** T1, T2, T3, T4

**文件：** 无新增代码修改；根据失败证据回补 T1-T4。

- [ ] **步骤 1：数据迁移模块全量测试**

运行：`mvn -pl :ccb-data-migration -am test`

预期：全部测试通过，失败数 0。

- [ ] **步骤 2：治理和范围检查**

运行：`node scripts/check-all-governance.mjs`

预期：通过；无越界、无模块依赖违规、无发布迁移修改。

- [ ] **步骤 3：前端构建与运行时验收准备**

运行：`npm --prefix web run build`

预期：构建成功。

按可用环境启动后端和前端，使用真实浏览器按以下路径验收：
- 打开“数据迁移 › 数迁资产内容 › 投产及演练”，检查列表、筛选、分页、新增、编辑、下载、删除、回收站恢复/彻底清理；
- 组件级新增必须选择系统，项目级不显示系统必填；
- 桌面与手机视口无页面级横向滚动、控制台无错误、无 ≥400 意外响应。

证据：命令退出码、测试报告、构建日志、浏览器截图/断言记录。

**回滚：** 应用回退到 T4 前提交；数据按逻辑删除/附件绑定可恢复；不执行反向迁移。

**停止条件：** 集成测试或浏览器验收出现未覆盖的负反馈且无法在方案内修复。

**升级条件：** 需要新增菜单/RBAC 种子、修改公共组件、跨模块依赖或重定需求基准。

---

## 集成检查

| 时机 | 命令 | 预期 |
|---|---|---|
| T1 后 | `mvn -pl :ccb-data-migration -am -Dtest=DataMigrationCodeValueServiceTest,ContentAssetMigrationMySqlTest test` | 迁移与码值测试通过 |
| T2 后 | `mvn -pl :ccb-data-migration -am -Dtest=ReleaseDrillServiceTest,ReleaseDrillControllerSecurityTest test` | 服务与控制器测试通过 |
| T3 后 | `mvn -pl :ccb-data-migration -am -Dtest=DataMigrationModuleRegistrationTest,ReleaseDrillRecycleBinSourceTest test` | 注册与回收站测试通过 |
| T4 后 | `npm --prefix web run build` | 前端构建通过 |
| T5 | `mvn -pl :ccb-data-migration -am test && node scripts/check-all-governance.mjs && npm --prefix web run build` | 全量门禁通过 |

## 控制模型种子

以下均为待建模验证的候选假设：

- 被控边界：`dm_release_drill` 元数据、附件、参数、系统、审计和回收站状态。
- 状态变量：活动/删除状态、颗粒度、资料类型、轮次、系统、附件当前主文件、参数启用态。
- 接口：`/api/data-migration/release-drills*`、`/api/data-migration/options/{category}`、附件上传接口、统一回收站接口。
- 传感器：迁移测试、服务测试、回收站注册测试、前端构建、浏览器验收。
- 执行器：`V184` 迁移、`ReleaseDrillService/Controller`、回收站来源、前端页面。
- 扰动：参数停用/新增、项目切换、数据迁移模块其他内容类型的回归风险。
- 假设：单文件编辑不传新文件保留原文件；`dm_release_drill` 无业务存量行；组件级等价于 `dm_component` 一个启用记录。

## 风险与用户批准

- 风险 1：`RELEASE_DRILL` 通用链路摘除影响看板或旧信封 → T3 使用注册与回收站回归测试验证。
- 风险 2：参数项目/类别初始化与已有管理员配置冲突 → `INSERT IGNORE` 且迁移测试断言。
- 风险 3：前端类型契约与后端字段不一致 → T4 与 T5 构建/API 验收。

请在复核本计划后明确是否按此计划进入开发；确认后我会将计划状态置为 `ready`、准备 `handoff`，并交给 `$control-engineering` 接管执行。

# 数据迁移“迁移程序”实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将“迁移程序”升级为独立程序包管理，支持参数管理程序类型、系统编号、程序包名称/说明、多文件上传与下载、编辑、删除和统一回收站。

**架构：** 后端新增 `ProgramService`、`ProgramController`、`ProgramRecycleBinSource`，将 `SCRIPT` 从通用 `ContentFileAssetService`/`ContentAssetController` 认领中摘除；数据库 `dm_script` 增列 `program_type`、`program_description` 并新增参数码值 `DM_PROGRAM_TYPE`；前端 `ProgramsPage.vue` 改为独立页面并新增多文件附件交互。

**技术栈：** Spring Boot 3 / JdbcTemplate、Java 17、MySQL 8、Flyway、TypeScript、Vue 3、Element Plus、Playwright。

## 全局约束

- 只修改 `codex-task-scope.yaml` 中授权的数据迁移模块、追加 Flyway、数据迁移前端 API/页面与同前缀文档、账本；不改 `~/.m2` 和公共附件表结构。
- Flyway 只追加当前最新版本之后的 `V185`，不修改任何已发布脚本。
- 程序类型只允许来自参数管理 `DM_PROGRAM_TYPE`；系统编号必须是当前项目 `enabled=1` 的 `dm_component`。
- 单个文件不超过 50MB；新增需要至少一个源文件。
- 逻辑字段 `doc_code` 服务端生成且不可修改；所有写操作绑定项目隔离、RBAC、实体授权与审计。
- 页面遵循 `design-h5.md`、`UiToolbar`、`UiDataTable`、`UiFormDrawer` 和统一状态组件。

---

### T1：数据库迁移与程序类型码值

**需求映射：** R2, R9

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V185__data_migration_program_domain.sql`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DataMigrationCodeValueService.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DataMigrationProgramMigrationMySqlTest.java`

**接口：**
- 消费：现有 `DataMigrationCodeValueService`、`sys_dict_type`、`sys_config`。
- 产出：`dm_script.program_type`、`dm_script.program_description` 列；`DM_PROGRAM_TYPE` 码值类别可在 `GET /api/data-migration/options/DM_PROGRAM_TYPE` 返回。

- [ ] **步骤 1：编写 V185 迁移**
  创建上述 SQL 文件，包含：
  - 幂等创建 `DICT` 类别 `DM_PROGRAM_TYPE`（名称“迁移程序类型”）；
  - `INSERT IGNORE` 两条配置：`DM_PROGRAM_TYPE.MIGRATE_OUT`→迁出程序、`DM_PROGRAM_TYPE.MIGRATE_IN`→迁入程序，使用不会与现有迁移冲突的 ID 区间（建议类别 ID `9010`、配置 ID `90101/90102`）；
  - 对 `dm_script` 执行两个受 `information_schema` 保护的追加列：`program_type VARCHAR(64) NOT NULL DEFAULT ''`、`program_description VARCHAR(2000) NULL`。

- [ ] **步骤 2：注册码值类别**
  在 `DataMigrationCodeValueService` 增加 `public static final String DM_PROGRAM_TYPE = "DM_PROGRAM_TYPE";` 并加入 `ALLOWED_CATEGORIES`。

- [ ] **步骤 3：编写 MySQL 迁移测试**
  新建 `DataMigrationProgramMigrationMySqlTest`：用 Testcontainers 启动 MySQL 8.4，Flyway clean + migrate，断言 `dm_script` 存在两列、`sys_dict_type` 存在 `DM_PROGRAM_TYPE`、`sys_config` 存在两个启用配置。

- [ ] **步骤 4：运行测试并记录证据**
  运行：`mvn -q -pl :ccb-data-migration -am -Dtest=DataMigrationProgramMigrationMySqlTest test`
  预期：BUILD SUCCESS、Testing count=1 且无失败。

- [ ] **步骤 5：建立提交检查点**
  若 Git 允许由任务逐步提交，则提交 `V185`、CodeValueService 与测试；当前工作区由主 Agent 控制时先写 execution 证据，不散落 commit。

**回滚：** 删除本次新增测试断言，不执行 `ALTER`；未发布迁移可移除文件。
**停止条件：** 迁移容器启动失败或 `DM_PROGRAM_TYPE` 读取失败。
**升级条件：** 发现 `dm_script` 存在真实业务行且无法填默认值时，升级用户确认是否补录。

---

### T2：ProgramService（列表、详情、创建、编辑、下载）

**需求映射：** R1, R3, R4, R5, R7

**前置任务：** T1

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ProgramService.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ProgramServiceTest.java`

**接口：**
- 消费：`JdbcTemplate`、`ContentAttachmentService`、`ContentDocCodeGenerator`、`DataMigrationCodeValueService`、`DataMigrationPermissionService`、`UserDirectoryPort`、`ProjectComponentService`、`AttachmentGateway`。
- 产出：`list/create/update/detail/downloadAttachmentId/bundleForDownload/listAttachments/countDeleted/fetchRecycleBinPage/findRecycleBinDetail/restore/purge` 方法。

- [ ] **步骤 1：定义 SELECT_COLUMNS 与 JOIN**
  以 `ReleaseDrillService` 为模板，`SELECT a.*` 物化 `system_name`：通过 `dm_component c` + `arch_physical_subsystem s` 左连接（历史只读也保留名称投影）；对外字段名 `asset_id`、`asset_code`、`asset_name`、`program_type`、`program_type_name`、`system_code`、`system_name`、`program_description`。

- [ ] **步骤 2：实现组合筛选列表**
  `list(projectId, programTypeCode, systemCode, keyword, page, size, user)`：恒定 `tenant_id`、`deleted=0`、`project_id` 过滤；程序类型筛选精确等值；系统编号筛选等值；关键字 `doc_name LIKE`；服务端分页 20/50/100。

- [ ] **步骤 3：实现创建**
  `create(body, user)`：必填 `projectId/programTypeCode/systemCode/docName`；`programTypeCode` 通过 `requireActive(DM_PROGRAM_TYPE,...)`；系统通过 `dm_component enabled=1` 校验；至少一个临时附件；生成 `doc_code`（`ContentDocCodeGenerator`，CONTENT_TYPE=`SCRIPT`）；插入 `dm_script`；调用 `ContentAttachmentService.replaceAll("SCRIPT", "DATA_MIGRATION_ASSET", id, projectId, files, user)`；写 `PROGRAM_CREATE` 审计。

- [ ] **步骤 4：实现编辑**
  `update(id, body, user)`：只允许修改 `program_type/system_code/doc_name/program_description` 与附件；不可改 `project_id/doc_code`；按提交的附件列表执行替换；权限通过 `permissions.requireWrite`；更新后写 `PROGRAM_UPDATE` 审计。

- [ ] **步骤 5：实现多附件下载**
  `download(id, user)` 返回活动附件 ID；`downloadBundleBytes` 使用 `java.util.zip.ZipOutputStream` 将附件流写入 ZIP，文件名冲突时追加序号；`listAttachments` 返回全部活动附件 `{attachmentId,fileName}`。

- [ ] **步骤 6：实现回收站四方法**
  `countDeleted/fetchRecycleBinPage/findRecycleBinDetail/restore/purge` 按 `ReleaseDrillService` 同款 SQL，恢复时附件批量解绑回活动；Purge 同时清理所有附件与记录；审计码为 `PROGRAM_RESTORE/PROGRAM_PURGE`。

- [ ] **步骤 7：编写单测**
  `ProgramServiceTest` 覆盖：必填校验、50MB 上限、至少一个文件、系统启用校验、项目隔离、编辑保留附件、回收站恢复/清理。

- [ ] **步骤 8：运行局部测试**
  运行：`mvn -q -pl :ccb-data-migration -am -Dtest=ProgramServiceTest test`
  预期：BUILD SUCCESS，全部断言通过。

**回滚：** 删除新增 Service 文件；尚未被 Controller 引用时不影响现有行为。
**停止条件：** 多附件绑定 SQL 无法同时满足新增/编辑/回收站原子性。
**升级条件：** 出现附件归属冲突或现有 `dm_content_attachment` 对 SCRIPT 多附件无支持证据时，升级主 Agent 与设计复核。

---

### T3：ProgramController、回收站来源与通用链路摘除

**需求映射：** R5, R6, R7, R9

**前置任务：** T2

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ProgramController.java`
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ProgramRecycleBinSource.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ContentAssetController.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/web/ProgramControllerSecurityTest.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ProgramRecycleBinSourceTest.java`

**接口：**
- 消费：`ProgramService`、`ProjectComponentService.getSystemOptions`、`ContentRecycleBinService`。
- 产出：`/api/data-migration/programs/**` 路由；`SCRIPT` 回收站专用认领。

- [ ] **步骤 1：新建 Controller**
  路由 `/api/data-migration/programs`；类级 `@PreAuthorize("hasAnyAuthority('data-migration:content:programs','data-migration:access','data-migration:write','data-migration:manage','system:admin')")`；接口：
  - `GET /`：分页列表
  - `GET /options/types`：程序类型
  - `GET /options/systems`：委托 `ProjectComponentService.getSystemOptions(projectId, user)`
  - `GET /{id}`：详情
  - `POST /`：创建（写权限）
  - `PUT /{id}`：编辑（写权限）
  - `DELETE /`：批量删除（写权限）
  - `GET /{id}/attachments`
  - `GET /{id}/download`
  - `GET /{id}/download-all`

- [ ] **步骤 2：新建 RecycleBinSource**
  `ProgramRecycleBinSource` 实现 `RecycleBinSource.supports()=Set.of("SCRIPT")`，五个方法全部委托 `ProgramService`，使用 `@Component` 注册。

- [ ] **步骤 3：从通用链路摘除 SCRIPT**
  `ContentFileAssetService.MANAGED_TYPES` 移除 `SCRIPT`；`ContentAssetController.RESOURCE_TYPES` 移除 `"programs"` 映射且删除 `/programs*` 端点的多余映射只保留其余类型。同步调整前端 API 中与迁移程序相关的旧通用函数（见 T4）。

- [ ] **步骤 4：编写安全和来源测试**
  `ProgramControllerSecurityTest` 验证匿名 401、只读用户可读、无写权限用户写操作 403、管理员可创建。`ProgramRecycleBinSourceTest` 验证 supports 仅 SCRIPT、恢复/清理委托正确、不与 `ContentAssetRecycleBinSource` 重复。

- [ ] **步骤 5：运行后端测试**
  运行：`mvn -q -pl :ccb-data-migration -am test`
  预期：BUILD SUCCESS，无失败测试。

**回滚：** 删除新 Controller/RecycleBinSource，并在通用链路恢复 SCRIPT。
**停止条件：** 回收站注册出现重复认领（ContentRecycleBinService 启动失败）。
**升级条件：** 发现其他功能仍直接依赖通用 SCRIPT 端点时，升级并纳入修改清单。

---

### T4：前端 API 与 ProgramsPage

**需求映射：** R1-R8

**前置任务：** T3

**文件：**
- 修改：`web/src/api/data-migration.ts`
- 重写：`web/src/modules/data-migration/views/content/ProgramsPage.vue`

**接口：**
- 消费：T2/T3 定义的 `programs` REST 契约、平台附件上传接口。
- 产出：页面路由复用原 `ProgramsPage.vue` 路由，无新增路由。

- [ ] **步骤 1：扩展 API**
  在 `data-migration.ts` 增加 `DM_CODE_CATEGORIES.programType='DM_PROGRAM_TYPE'`，新增：`listPrograms`、`getProgramOptions`、`getProgramSystemOptions`、`getProgram`、`createProgram`、`updateProgram`、`deletePrograms`、`getProgramAttachments`、`getProgramDownloadPath`、`getProgramDownloadAllPath`。

- [ ] **步骤 2：重写页面脚本**
  `ProgramsPage.vue` 沿用 `ReleaseDrillsPage.vue` 的结构：项目闸门、页头“迁移程序”、工具栏（程序类型下拉、系统编号下拉、名称模糊搜索）、`UiDataTable`、空态、分页；新增/编辑抽屉表单包含程序类型、系统编号、程序包名称、程序包说明、多文件上传。

- [ ] **步骤 3：实现系统选项本地筛选**
  `EL-SELECT` 加载 `getProgramSystemOptions(projectId)` 一次，`filterable` 前端过滤，显示“系统编号 - 系统名称”，不区分大小写。

- [ ] **步骤 4：实现多文件上传与下载**
  上传区域支持选择多个临时文件、展示待上传项和“移除”；编辑时支持保留/删除已有附件；操作列提供单个附件下载和“全部下载”按钮；下载全部调用 `download-all` 保存 ZIP。

- [ ] **步骤 5：实现删除与回收站入口**
  行内“删除”与多选“移入回收站”调用 `deletePrograms`；统一回收站继续使用 `SCRIPT` 类型页面，无需新页面。

- [ ] **步骤 6：运行前端构建**
  运行：`npm --prefix web run build`
  预期：构建成功且无 TypeScript/路由错误。

**回滚：** 恢复原通用 `AssetListView` 使用方式及 API。
**停止条件：** 多附件上传无法通过浏览器正常选择/删除。
**升级条件：** 浏览器测试发现下载全部 ZIP 中文文件名乱码等容器/编码问题时，升级到服务端处理。

---

### T5：回归、范围与浏览器验收

**需求映射：** R6, R8, R9, R10

**前置任务：** T1, T2, T3, T4

**文件：**
- 证据：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/execution-*`、`observation-*`

**接口：**
- 消费：T1-T4 形成的后端与前端契约。
- 产出：运行证据和收敛结论所需观测文件。

- [ ] **步骤 1：后端全量聚焦测试**
  运行：`mvn -q -pl :ccb-data-migration -am test`
  预期：BUILD SUCCESS，0 fail/0 error。

- [ ] **步骤 2：治理与范围检查**
  运行：`node scripts/check-all-governance.mjs`
  预期：通过，无越界文件和硬编码违规。

- [ ] **步骤 3：启动本地环境**
  使用 `./backend-dev.sh restart` 与前端 Vite，确认 `/actuator/health` 200、迁移程序页面可达。

- [ ] **步骤 4：Playwright 浏览器验收**
  新建并运行 `/tmp/program_acceptance_playwright.mjs`，桌面 `1280x800` 和移动 `375x812/390x844/430x932`：登录，进入 `/data-migration/content/programs`，验证程序类型两项、系统下拉、新增表单、多文件上传、编辑、删除、回收站恢复，记录接口状态与控制台错误。页面整体 `scrollWidth <= viewportWidth`。运行：`NODE_PATH=web/node_modules node /tmp/program_acceptance_playwright.mjs`。

- [ ] **步骤 5：写执行与观测证据**
  主 Agent 写 `execution-T*.json`；独立验证写 `observation-T*.json`；存在偏差时回到计划/执行，全部通过后写 `convergence.json` 并交付。

**回滚：** 应用层回退保留表和附件；未发布迁移可移除。
**停止条件：** 任何 4xx/5xx、控制台错误、横向溢出或权限越界未修复前不得声明完成。
**升级条件：** 浏览器进入页面时出现“尚未选择项目”或参数/系统选项缺失，升级运行证据与参数种子问题。

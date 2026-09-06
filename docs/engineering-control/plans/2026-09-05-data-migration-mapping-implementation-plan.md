# 数据迁移“迁移映射”优化实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将迁移映射收敛为独立、项目隔离且受权限保护的多附件业务域，满足参数化类型、系统选择、组合筛选、统一回收站和桌面/移动完整交互。

**架构：** `MappingService`、`MappingController` 和 `MappingRecycleBinSource` 拥有 `MAPPING_DOC` 的业务行为，继续消费现有附件、参数、项目权限和审计能力。前端由独立 `MappingsPage.vue` 通过 `web/src/api/data-migration.ts` 调用专属接口，不修改平台公共实现。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring Security、JdbcTemplate、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Vite。

## 状态与来源

- 计划修订：2
- 设计修订：2
- 设计文档：`docs/engineering-control/designs/2026-09-05-data-migration-mapping-design.md`
- 机器设计：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/data-migration-mapping-design.json`
- 状态：可移交
- 用户批准：2026-09-06 用户确认设计，并明确要求“制定实施计划并进入代码修改”。

## 全局约束

- 只修改 `REQ-20260820-031` 的 `codex-task-scope.yaml` 所授权 `writable_paths`；保护工作区现有未关联修改。
- 保持 `com.ccb.*` 包名、Maven artifact、路由根和模块边界，不修改 platform/shared 公共实现。
- Flyway 只追加；`V186` 当前未发布但已存在于工作区，本任务只按批准设计收紧该新增脚本，不修改任何更早迁移。
- 当前 `dm_mapping_doc` 无历史业务数据；迁移不得包含历史回填或兼容空默认值。
- 一条映射记录绑定多个附件；单附件上限 50MB；业务代码不保存对象键或凭据，不解压压缩包。
- 类型必须来自启用的 `DM_MAPPING_TYPE`；系统必须来自当前项目启用的 `dm_component`。
- 所有服务端访问执行认证、RBAC、租户、项目、实体和附件归属校验；成功写操作保留审计。
- 前端复用公共 UI、项目上下文和附件 API，覆盖全状态并在 `375x812`、`390x844`、`430x932` 与桌面视口验收。
- 不提交、不推送、不合并；不访问生产环境或生产数据。

## 文件职责地图

| 路径 | 状态 | 职责与边界 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V186__data_migration_mapping_domain.sql` | existing-untracked | `mapping_type`、查询索引和 `DM_MAPPING_TYPE` 初始项；不得处理历史数据 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/MappingService.java` | existing-untracked | 映射 CRUD、筛选、多附件、下载、授权、审计和回收站业务 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/MappingController.java` | existing-untracked | `/api/data-migration/mappings*` HTTP 与 RBAC 边界 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/MappingRecycleBinSource.java` | existing-untracked | `MAPPING_DOC` 唯一统一回收站来源 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java` | existing-modified | 通用文件服务；只核对并保留 `MAPPING_DOC` 已摘除事实，不重写其他未关联改动 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ContentAssetController.java` | existing-modified | 通用资源路由；只核对并保留 `mappings` 已摘除事实 |
| `web/src/api/data-migration.ts` | existing-modified | Mapping DTO、筛选、CRUD、附件和下载 API；避免改动同文件其他业务增量 |
| `web/src/modules/data-migration/views/content/MappingsPage.vue` | existing-modified | 独立列表、移动卡片、筛选、附件状态、下载、编辑、删除与响应式状态 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DataMigrationMappingMigrationMySqlTest.java` | existing-untracked | MySQL 8.4 `V186` 结构、码值、幂等和无回填验证 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/MappingServiceTest.java` | existing-untracked | 服务正常、异常、权限、附件、下载、审计和回收站测试 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/web/MappingControllerSecurityTest.java` | existing-untracked | 查询/下载和写操作方法级权限声明测试 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/MappingRecycleBinSourceTest.java` | existing-untracked | 回收站唯一认领和委托测试 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java` | existing-modified | 模块注册和回收站来源唯一性回归 |
| `docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/requirement.md` | existing-modified | 本增量需求事实源 |
| `docs/engineering-control/designs/2026-09-05-data-migration-mapping-design.md` | existing-untracked | 已批准设计修订 2 |
| `docs/engineering-control/plans/2026-09-05-data-migration-mapping-implementation-plan.md` | existing-untracked | 本计划 |
| `.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/data-migration-mapping-*.json` | candidate-new/existing | 本增量设计、交接、阶段、执行、观察和收敛证据 |

## 任务依赖图与并行策略

```text
T1 数据迁移契约
  -> T2 后端专属域
       -> T3 前端完整交互
            -> T4 集成与浏览器验收
```

全部任务串行。T2 消费 T1 的非空字段和码值，T3 消费 T2 的稳定 API，T4 对组合结果取证；工作区存在同需求交叠修改，不安排并行写入。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T1, T2, T3 |
| R2 | T1, T2, T3 |
| R3 | T2, T3 |
| R4 | T2, T3, T4 |
| R5 | T2, T3, T4 |
| R6 | T2, T3, T4 |
| R7 | T2, T4 |
| R8 | T2, T3, T4 |
| R9 | T3, T4 |
| R10 | T1, T4 |

### T1：数据库字段和参数码值满足无历史数据契约

#### 需求映射与前置事实

- 需求映射：R1、R2、R10。
- 前置事实：`V186`、迁移测试和参数码值服务已存在于未提交工作区；用户确认 `dm_mapping_doc` 无历史业务数据。

#### 文件边界与接口

- 修改：`server/src/platform/infrastructure/src/main/resources/db/migration/V186__data_migration_mapping_domain.sql`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DataMigrationMappingMigrationMySqlTest.java`
- 消费：现有 `dm_mapping_doc`、`sys_dict_type`、`sys_config` 表结构。
- 产出：非空 `mapping_type`、组合查询索引、启用的 `DM_MAPPING_TYPE.MIGRATE_OUT/MIGRATE_IN`。

- [ ] 步骤 1：建立无回填和非空字段迁移断言；执行指定 MySQL 测试，记录当前失败或基准信号。
- [ ] 步骤 2：删除 `mapping_type DEFAULT ''` 和任何历史 `UPDATE`，保留追加、幂等的字段/索引/参数初始化。
- [ ] 步骤 3：运行 `mvn -pl :ccb-data-migration -am -Dtest=DataMigrationMappingMigrationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期测试通过、0 失败，若 Docker 不可用则明确记录跳过而不能宣称通过。
- [ ] 步骤 4：检查 `rg -n "UPDATE dm_mapping_doc SET mapping_type|DEFAULT ''" V186__data_migration_mapping_domain.sql` 无命中，并保存证据。

#### 验收、证据与回滚

- 验收：字段非空且无兼容默认；两个参数项可读且重复执行不覆盖已有配置；没有历史回填 SQL。
- 证据：测试报告、迁移文件静态断言、`git diff --check`。
- 回滚：仅回退本任务对尚未发布 `V186` 和其测试的修改；不改动 V185 及更早脚本。

#### 停止和升级条件

- 停止：发现目标环境存在必须保留的 `dm_mapping_doc` 数据，或 `V186` 已在共享环境发布。
- 升级：字段非空必须依赖新的产品默认值，或参数表约束与当前设计不兼容。

### T2：专属后端满足多附件、权限、审计和回收站契约

#### 需求映射与前置事实

- 需求映射：R1、R2、R3、R4、R5、R6、R7、R8。
- 前置任务：T1。
- 前置事实：工作区已有 `MappingService`、Controller、回收站来源和初版测试；通用服务/路由已包含其他菜单的交叠修改，只允许补本需求缺口。

#### 文件边界与接口

- 修改：`MappingService.java`、`MappingController.java`、`MappingRecycleBinSource.java`、`ContentFileAssetService.java`、`ContentAssetController.java`。
- 测试：`MappingServiceTest.java`、`MappingControllerSecurityTest.java`、`MappingRecycleBinSourceTest.java`、`DataMigrationModuleRegistrationTest.java`。
- 消费：`DataMigrationCodeValueService.requireActive/options`、`DataMigrationPermissionService`、`ContentAttachmentService`、`AttachmentStreamService`、`ContentDocCodeGenerator`。
- 产出：`GET/POST/PUT/DELETE /api/data-migration/mappings*` 和统一回收站 `MAPPING_DOC` 唯一来源。

- [ ] 步骤 1：运行四组聚焦测试并记录基准；用静态检查确认通用服务和通用 Controller 不再认领 `MAPPING_DOC/mappings`。
- [ ] 步骤 2：增加文件名称 200 字符服务端校验、显式空附件集合拒绝、重复附件 ID 处理、指定附件归属和空 ZIP 失败行为测试。
- [ ] 步骤 3：实施最小后端修正，保持 Controller 只做 HTTP/RBAC，业务校验与事务留在 Service。
- [ ] 步骤 4：增加查询/下载权限声明和普通用户所有者边界断言；验证成功写操作分别记录 `MAPPING_CREATE/UPDATE/DELETE/RESTORE/PURGE`，失败不记录成功审计。
- [ ] 步骤 5：运行 `mvn -pl :ccb-data-migration -am -Dtest=MappingServiceTest,MappingRecycleBinSourceTest,MappingControllerSecurityTest,DataMigrationModuleRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期全部通过、0 失败。

#### 验收、证据与回滚

- 验收：API、50MB、多附件、组合筛选、权限、项目、实体、附件、审计、软删/恢复/清理均有可重复断言；`MAPPING_DOC` 只有专属来源。
- 证据：Surefire 用例数与退出码、源契约扫描、实际 diff。
- 回滚：回退 Mapping 专属新增文件及通用链路中仅针对 `MAPPING_DOC` 的摘除；不得覆盖同文件中程序、专题、投产演练等既有修改。

#### 停止和升级条件

- 停止：需要修改 platform/attachment、platform/system、shared 或安全公共实现；发现同文件用户改动与本设计冲突。
- 升级：现有附件端口无法保证绑定归属或彻底清理，或回收站 SPI 无法唯一认领类型。

### T3：独立页面满足响应式、多文件恢复和下载交互

#### 需求映射与前置事实

- 需求映射：R1、R2、R3、R4、R5、R6、R8、R9。
- 前置任务：T2。
- 前置事实：初版 `MappingsPage.vue` 已提供桌面表格和顺序上传，但缺少移动卡片、逐文件持久状态、部分失败重试、单附件下载入口和持久错误态。

#### 文件边界与接口

- 修改：`web/src/api/data-migration.ts`、`web/src/modules/data-migration/views/content/MappingsPage.vue`；仅在必要时修改 `web/src/modules/data-migration/data-migration.css` 的 Mapping 专属选择器。
- 消费：T2 Mapping API、`uploadAttachment`、`useProjectScope`、公共 `UiToolbar/UiDataTable/UiFormDrawer/UiPagination/UiEmptyState`。
- 产出：桌面表格、移动卡片、表单抽屉、逐文件状态、失败重试、附件下载选择和权限操作层级。

- [ ] 步骤 1：记录当前前端构建和源契约基准；为文件状态模型定义 `pending/uploading/uploaded/error`，保留已上传附件 ID。
- [ ] 步骤 2：重构选择与上传流程：前端预检 50MB，逐项上传，成功项持久保留，失败项显示错误并可单独重试，存在失败项时不提交业务记录，保存期间禁用重复操作。
- [ ] 步骤 3：增加附件下载入口：先读取附件列表，允许逐附件下载和全部 ZIP；所有入口按访问权限和加载状态工作。
- [ ] 步骤 4：实现桌面表格与移动卡片互斥呈现，卡片展示文件名称、编号、系统和类型；将编辑/删除收进明确的更多操作，删除保留对象与后果确认。
- [ ] 步骤 5：实现加载失败可重试、筛选无结果、项目切换清空旧数据/选项/抽屉/附件状态，以及类型/系统选项失败反馈；移除重复功能说明。
- [ ] 步骤 6：运行 `npm --prefix web run build`；预期 `vue-tsc` 与 Vite 构建成功、退出码 0。

#### 验收、证据与回滚

- 验收：多文件部分失败不会丢失成功项；可单独重试；单/全部下载可达；移动端不显示桌面表格且无页面级横向滚动；所有状态和权限入口可观察。
- 证据：前端构建输出、浏览器请求/DOM/控制台/溢出探针、实际 diff。
- 回滚：只回退 Mapping API 段落和 `MappingsPage.vue` 的本任务变更；不回退 `data-migration.ts` 同文件其他业务增量。

#### 停止和升级条件

- 停止：必须修改 `web/src/components/ui`、stores 或全局主题才能完成；附件 API 无法提供受认证单附件下载。
- 升级：浏览器上传 API 无法暴露逐文件成功/失败结果，或移动卡片要求改变已批准字段集合。

### T4：组合实现通过工程门禁和真实用户路径

#### 需求映射与前置事实

- 需求映射：R4、R5、R6、R7、R8、R9、R10。
- 前置任务：T3。
- 前置事实：本任务在已有脏工作树中实施，范围检查可能包含同一 REQ 的其他既有修改，必须区分本增量证据与外部扰动。

#### 文件边界与接口

- 修改：仅当前前缀 `data-migration-mapping-execution.json`、`data-migration-mapping-observation.json`、`data-migration-mapping-verification.json`、`data-migration-mapping-state.json` 和交接文件。
- 消费：T1-T3 的数据库、API、页面和测试结果。
- 产出：可重复的执行、观察、收敛证据和残余风险。

- [ ] 步骤 1：运行 `git diff --check`、聚焦 Mapping 测试和 `mvn -pl :ccb-data-migration -am test`；预期退出码 0。
- [ ] 步骤 2：运行 `mvn test` 与 `npm --prefix web run build`；预期退出码 0，非本需求既有失败必须单独记录归因。
- [ ] 步骤 3：运行 `node scripts/check-all-governance.mjs` 与 `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base origin/main --head HEAD --working-tree`；记录真实退出码和已知基线扰动。
- [ ] 步骤 4：在本地 MySQL、MinIO、后端和前端可用时，以管理员和普通人员验证列表、组合筛选、新增多附件、部分失败重试、编辑附件集合、单附件/ZIP 下载、删除、恢复和彻底清理；记录 HTTP 和控制台结果。
- [ ] 步骤 5：在 `1280x800`、`375x812`、`390x844`、`430x932` 检查表格/卡片互斥、抽屉高度、操作可达、长文本和 `document.documentElement.scrollWidth <= innerWidth`。
- [ ] 步骤 6：写执行和观察证据；所有 must 覆盖、任务验证、P0/P1 为零且至少两次采样后执行收敛门禁。

#### 验收、证据与回滚

- 验收：全部 must 有测试或浏览器信号；范围一致；没有未裁决阻塞反馈；无法执行的运行验证明确列为残余风险而非通过。
- 证据：命令、退出码、测试数、浏览器视口/角色/路由、请求结果、控制台和溢出数值。
- 回滚：应用变更按 T3、T2、T1 逆序回退；已执行 Flyway 不反向修改，结构撤销另立前向补偿任务。

#### 停止和升级条件

- 停止：数据库迁移失败、越权成功、附件跨项目访问、对象泄露、构建失败或页面级横向溢出。
- 升级：生产身份/数据/拓扑才可验证的项目，或全局治理失败需要修改本任务禁止路径。

## 集成检查

- T1 后：迁移测试与无回填静态检查。
- T2 后：Mapping 聚焦服务/权限/回收站测试。
- T3 后：前端构建和源契约检查。
- T4：完整 Maven、前端、治理、范围、运行和四视口浏览器验收。

## 控制模型种子

以下仅为 `hypotheses-only` 候选，必须由系统建模阶段验证：

- 被控边界候选：`dm_mapping_doc`、`dm_content_attachment(MAPPING_DOC)`、Mapping 后端、Mapping 前端、统一回收站来源。
- 状态变量候选：迁移版本、映射活动/删除状态、附件绑定集合、项目上下文、上传项状态、权限主体、页面异步状态。
- 接口候选：Mapping REST、附件上传/下载、参数选项、系统选项、回收站 SPI。
- 传感器候选：MySQL Testcontainers、JUnit 服务/安全测试、Maven/Vue 构建、治理/范围脚本、HTTP 探针、Playwright DOM/控制台/溢出探针。
- 执行器候选：收紧 `V186`、修正 Service/Controller、调整 Mapping API/Page、增加聚焦测试。
- 扰动候选：同一 REQ 的既有脏改动、Docker/MySQL/MinIO 不可用、端口冲突、公共附件上传时延、其他需求导致的全局治理失败。
- 时延候选：多个 50MB 文件顺序上传、ZIP 流式生成、完整 Maven 测试、真实浏览器启动。
- 假设候选：当前无历史映射数据；未绑定临时附件由平台生命周期清理；本地测试基础设施可用。

## 风险与用户批准

- 高风险动作：追加数据库迁移、多附件解绑/彻底清理、服务端权限和项目隔离。必须以 MySQL、权限、附件和回收站测试取证，并保留 Owner 专项复核要求。
- 用户已批准设计和进入代码修改；本计划不得扩展到平台公共实现、其他业务菜单、生产环境、提交、推送或发布。

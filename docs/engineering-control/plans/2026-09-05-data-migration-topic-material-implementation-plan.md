# 数据迁移专题材料优化实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将“数迁资产内容 / 专题材料”升级为支持颗粒度、参数管理类型、多系统、多附件、查看、编辑、下载、逻辑删除和统一回收站的专属域功能。

**架构：** 在 `business/data-migration` 内新增专题服务、控制器和回收站来源，`dm_topic` 追加专题字段并新增 `dm_topic_system` 关系表。专题类型通过 `platform/system` 已有 `SystemReferenceQuery` 读取，V180 只在迁移脚本中幂等初始化参数类别和参数项；公共附件、审计和全局项目切换器保持不变。通用 TOPIC 链路在同一模块内摘除，避免重复路由和回收站认领。

**技术栈：** Java 17、Spring Boot 3.4.4、MyBatis/JdbcTemplate、MySQL 8.4/Flyway、Vue 3、TypeScript、Element Plus、现有 `UiToolbar`/`UiDataTable`/`UiFormDrawer` 组件。

## 全局约束

- 需求基准：`REQ-20260820-031`，控制前缀：`req-20260820-031-data-migration-asset-library-v3`。
- 只修改当前 `codex-task-scope.yaml` 的 `writable_paths`；禁止修改生产系统、凭据、平台私有实现和其他需求账本。
- Flyway 只追加 `V181__data_migration_topic_domain.sql`，不修改 V1-V179。
- 专题类型只能来自 `SystemReferenceQuery.activeParameters`；禁止新增 `dm_topic_type` 或前端硬编码类型。
- 所有 SQL 绑定 `tenant_id` 和项目范围；项目归属由全局项目上下文和服务端项目可达性校验决定。
- 文件单个不超过 50MB；对象引用不返回前端；附件绑定必须经过公共附件能力。
- 逻辑删除、恢复、彻底清理和审计保持事务一致；普通用户只能操作本人记录，管理员可操作全部记录。
- 页面遵守 `design-h5.md`；桌面 1280x800，移动 375x812、390x844、430x932 验收，无页面级横向滚动。
- 当前 T31-r1 项目上下文规则继续生效：专题页面不新增项目选择器或项目字段；如需显示项目名称，停止并请求产品确认。

## 文件地图与所有权

| 领域 | 新建文件 | 修改文件 | 责任 |
|---|---|---|---|
| 数据库 | `server/src/platform/infrastructure/src/main/resources/db/migration/V181__data_migration_topic_domain.sql` | 无历史迁移修改 | 建字段、关系表、参数初始化 |
| 后端专题域 | `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/TopicService.java`、`TopicRecycleBinSource.java`、`web/TopicController.java` | `ContentAssetController.java`、`ContentFileAssetService.java`、`ContentAssetRecycleBinSource.java`、`ContentAssetTables.java`、看板/注册相关实现 | 专题 CRUD、选项、附件、回收站和通用链路摘除 |
| 后端测试 | `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/TopicServiceTest.java`、`TopicDomainMigrationMySqlTest.java`、`TopicRecycleBinSourceTest.java`、必要的注册测试修改 | 现有模块注册/治理测试 | 结构、权限、关系、状态和路由证据 |
| 前端契约 | 无新模块 | `web/src/api/data-migration.ts` | TOPIC 专属类型和请求函数 |
| 前端页面 | 无新目录 | `web/src/modules/data-migration/views/content/TopicsPage.vue` | 专题列表、详情、编辑和状态 |
| 控制证据 | `docs/engineering-control/plans/2026-09-05-data-migration-topic-material-implementation-plan.md`、当前前缀 `correction-topic-material-plan.json` | 当前前缀仅追加本任务证据 | 计划、交接和验收记录 |

## 任务依赖

```text
T1 迁移与参数初始化
  -> T2 后端 TopicService/Controller 契约
     -> T3 通用 TOPIC 链路摘除与回收站集成
        -> T4 前端 API 与 TopicsPage
           -> T5 集成、运行和浏览器验收
```

任务全部串行。T2 消费 T1 的表结构，T3 消费 T2 的服务和回收站契约，T4 消费 T2/T3 的 HTTP 契约，T5 消费全部实现；共享数据库、前端构建和运行端口不存在可安全并行的证明。

### T1：专题数据库与系统参数初始化

**需求映射：** R1, R2, R3

**前置任务：** 无

**输入事实：** V179 最终模型已有 `dm_topic`、`dm_content_attachment`、`dm_operation_log`；系统公开参数契约为 `SystemReferenceQuery.activeParameters(actor, categoryCode)`；用户已确认参数类别和 23 项初始化授权。

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V181__data_migration_topic_domain.sql`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/TopicDomainMigrationMySqlTest.java`
- 修改：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java`（仅增加 V180/参数/关系断言）

**接口：**
- 消费：V179 的 `dm_topic` 结构、系统参数表、`dm_component` 活动系统编号。
- 产出：`dm_topic.granularity/topic_type_code/topic_summary`、`dm_topic_system`、`DM_TOPIC_PROJECT_TYPE`、`DM_TOPIC_SYSTEM_TYPE`。

- [ ] **步骤 1：建立失败基线检查**

运行：`mvn -pl :ccb-data-migration -am -Dtest=TopicDomainMigrationMySqlTest test`

预期：测试类尚不存在或未覆盖 V180，命令以编译/测试失败结束；记录退出码作为基线，不修改历史迁移。

- [ ] **步骤 2：实施追加式迁移**

在 V180 中按以下顺序实施：

1. 检查 `dm_topic` 存量；增加 `granularity`、`topic_type_code`、`topic_summary`，对既有行使用 `PROJECT` 和兼容编码回填，不能回填时 `SIGNAL` 失败。
2. 创建 `dm_topic_system`，唯一键为 `(tenant_id, topic_id, system_code)`，索引为 `(tenant_id, project_id, system_code)`。
3. 幂等创建 `DM_TOPIC_PROJECT_TYPE`、`DM_TOPIC_SYSTEM_TYPE` 两个 `sys_dict_type` 类别。
4. 使用 `INSERT IGNORE` 或存在性判断插入 19 个项目级和 4 个系统级 `sys_config` 参数项，不能覆盖管理员已有名称、排序、状态或备注。
5. 为 `dm_topic` 增加项目/颗粒度/类型查询索引；不修改 `dm_content_attachment`、`dm_operation_log` 或平台 Java。

- [ ] **步骤 3：运行迁移测试**

运行：`mvn -pl :ccb-data-migration -am -Dtest=TopicDomainMigrationMySqlTest,DataMigrationModuleRegistrationTest test`

预期：V179→V180 首次执行成功；重复执行无重复列、表、类别或参数；关系唯一键、参数数量、历史回填和未授权表未变化断言通过。

证据：记录迁移版本、`SHOW CREATE TABLE dm_topic`、`SHOW CREATE TABLE dm_topic_system`、两个参数类别及 23 项计数、测试退出码。

**回滚：** 应用版本回退；不执行反向 Flyway。V180 新列/关系表和初始参数保留，删除它们必须另行审批补偿迁移。

**停止条件：** 存量专题无法安全回填、V180 触碰 V179 以前脚本、参数初始化覆盖既有管理员配置、关系表无法建立唯一约束。

**升级条件：** 需要修改 `platform/system` Java、公共附件/审计表结构，或发现参数管理不能表达两个颗粒度类别时，停止并请求模块 Owner 决策。

### T2：专题专属后端服务与 HTTP 契约

**需求映射：** R1, R2, R3, R5

**前置任务：** T1

**输入事实：** `MeetingService`/`PlanService` 已具备项目隔离、用户归属、多附件、系统关系、审计和分页模式；附件通过 `ContentAttachmentService`；参数通过 `SystemReferenceQuery`。

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/TopicService.java`
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/TopicController.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/TopicServiceTest.java`
- 新建：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/TopicRecycleBinSourceTest.java`（来源实现于 T3 注册前可先测试投影逻辑）

**接口：**
- 消费：T1 的 `dm_topic`/`dm_topic_system`；`SystemReferenceQuery`；`DataMigrationPermissionService`；`ContentAttachmentService`。
- 产出：
  - `GET /api/data-migration/topics?projectId&granularity&topicTypeCode&systemCodes&keyword&page&size`
  - `GET /api/data-migration/topics/{id}`
  - `POST /api/data-migration/topics`
  - `PUT /api/data-migration/topics/{id}`
  - `DELETE /api/data-migration/topics`（body 为 ID 数组）
  - `GET /api/data-migration/topics/options/types?granularity=PROJECT|SYSTEM`
  - `GET /api/data-migration/topics/options/systems?projectId`
  - `GET /api/data-migration/topics/{id}/attachments`
  - `GET /api/data-migration/topics/{id}/download`

- [ ] **步骤 1：建立服务层失败基线**

在 `TopicServiceTest.java` 固定以下断言后运行：

```text
缺少topicName/topicSummary/granularity/topicTypeCode/files -> BAD_REQUEST
SYSTEM无systemCodes或PROJECT带systemCodes -> BAD_REQUEST
停用/未知参数编码、跨项目systemCode -> BAD_REQUEST
非本人编辑/删除 -> FORBIDDEN
唯一冲突/状态变化 -> CONFLICT
```

运行：`mvn -pl :ccb-data-migration -am -Dtest=TopicServiceTest test`

预期：先因 `TopicService` 尚不存在失败；保存失败基线退出码。

- [ ] **步骤 2：实施 TopicService**

1. 列表 SQL 固定租户、项目、`deleted=0`，关键字只匹配 `doc_name` 和 `topic_summary`，按 `updated_at DESC,id DESC` 分页。
2. 类型校验按颗粒度调用 `SystemReferenceQuery.activeParameters`，只允许启用 `config_key`。
3. 系统校验查询当前项目活动 `dm_component`，去重后事务替换 `dm_topic_system`。
4. 新增生成 `doc_code`，保存专题元数据、附件集合和系统关系；编辑从库中读取项目归属，不接受客户端改变 `projectId/ownerId/tenantId`。
5. 删除、恢复、purge、下载和附件列表复用现有生命周期与项目授权，写入 `TOPIC_CREATE/UPDATE/DELETE/RESTORE/PURGE` 审计。
6. 主文件为 `sort_order=0`；新增和编辑至少保留一个活动附件；附件临时上传失败时不写专题关系。

- [ ] **步骤 3：实施 TopicController 和服务测试**

控制器使用显式 Bean 名 `dataMigrationTopicController`，类级权限沿用 `data-migration:content:topics|access|write|manage|system:admin`，写操作补充 `:create/:update/:delete` 回退权限。PathVariable 使用 `\\d+` 约束。

运行：`mvn -pl :ccb-data-migration -am -Dtest=TopicServiceTest,TopicRecycleBinSourceTest test`

预期：必填、参数联动、系统关系、多附件、权限、租户/项目隔离、逻辑删除、恢复冲突、purge 级联和审计断言通过。

证据：测试报告、接口路径清单、关键错误码和审计行断言。

**回滚：** 删除本任务新建服务/控制器及测试；保留 T1 追加迁移，应用回退不执行反向迁移。

**停止条件：** 无法通过公开参数契约读取类型、必须直接 SQL 访问平台私有表、附件绑定需要修改公共平台 API、或事务无法同时覆盖专题/关系/附件/审计。

**升级条件：** 需要新增平台公开接口、改变统一错误码、改变现有会议/方案附件语义，或发现系统编号来源不是 `dm_component` 活动清单。

### T3：摘除通用 TOPIC 链路并接入统一回收站

**需求映射：** R3, R5, R6

**前置任务：** T2

**输入事实：** 当前通用 TOPIC 注册在 `ContentAssetController`、`ContentFileAssetService.MANAGED_TYPES`、`ContentAssetRecycleBinSource` 和前端类型映射；统一回收站按 `RecycleBinSource` 唯一认领类型。

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/TopicRecycleBinSource.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ContentAssetController.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentAssetRecycleBinSource.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentAssetTables.java`
- 修改：相关 `DashboardService`/注册测试，仅在静态搜索确认存在 TOPIC 通用分支时修改

**接口：**
- 消费：T2 的 `TopicService`；统一 `RecycleBinSource` SPI。
- 产出：TOPIC 仅由 `TopicRecycleBinSource` 提供 `countDeleted/listDeletedPage/detail/restore/purge`；看板仍统计 TOPIC 类型。

- [ ] **步骤 1：建立重复认领基线**

运行：`rg -n 'TOPIC|topics|MANAGED_TYPES|RecycleBinSource' server/src/modules/data-migration/src/main/java`

预期：输出当前通用链路位置；记录每个位置的修改前职责，确认不触碰 PLAN/REPORT/MEETING/RULE/PARAMETER。

- [ ] **步骤 2：实施最小摘除与注册**

1. 从通用 Controller 的资源映射和 `MANAGED_TYPES` 移除 TOPIC。
2. 从通用回收站来源移除 TOPIC，新增 `TopicRecycleBinSource` 并注册 `supports() = {"TOPIC"}`。
3. `ContentAssetTables` 保留 TOPIC 类型标签，确保看板和历史回收站信封可识别。
4. 不删除 `dm_topic`、不改变公共附件关系、不改变统一回收站 HTTP 入口。

- [ ] **步骤 3：运行模块回归**

运行：`mvn -pl :ccb-data-migration -am -Dtest=DataMigrationModuleRegistrationTest,ContentRecycleBinRegistryTest,TopicRecycleBinSourceTest test`

预期：TOPIC 无通用来源重复认领；统一回收站恢复/purge 调用 `TopicService`；其他内容类型注册、看板类型统计和现有回收站测试无回归。

**回滚：** 同步恢复通用 TOPIC 映射和来源，移除 `TopicRecycleBinSource`；必须整体回退 T2/T3，避免同一类型无来源或双来源。

**停止条件：** 发现看板、历史数据迁移或其他类型依赖 TOPIC 的通用字段，且无法在专题服务内提供等价投影；或注册测试出现非 TOPIC 类型变化。

**升级条件：** 需要修改统一回收站公共 SPI、平台菜单/RBAC 表结构或跨业务模块服务契约。

### T4：前端专题页面与 API 契约

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T2、T3

**输入事实：** 当前 `TopicsPage.vue` 仅 10 余行包装 `AssetListView`；`MeetingsPage.vue` 和 `PlansPage.vue` 已验证详情、多附件、项目上下文、抽屉和状态模式；全局项目上下文通过 `useProjectScope` 提供。

**文件：**
- 修改：`web/src/api/data-migration.ts`
- 修改：`web/src/modules/data-migration/views/content/TopicsPage.vue`
- 修改：必要的 `web/src/modules/data-migration/data-migration.css`，仅在现有样式无法承载移动端多选/附件列表时追加语义变量样式
- 测试：`npm --prefix web run build`；浏览器验收记录写入 T4/T5 证据

**接口：**
- 消费：T2 的 `/topics*` JSON/分页/错误契约、公共附件上传下载、T3 的统一回收站类型 `TOPIC`。
- 产出：`TopicRecord`、`TopicQuery`、`TopicFormData`、类型和系统 options API，以及列表/详情/编辑交互。

- [ ] **步骤 1：建立前端失败基线**

运行：`npm --prefix web run build`

预期：现有构建结果作为基线；不因本任务修改其他模块页面或全局 store。

- [ ] **步骤 2：实施 API 类型和页面**

1. 删除 TOPIC 对通用 `listDataMigrationAssets/uploadDataMigrationAsset/replaceDataMigrationAsset` 的消费，新增专题专属函数和类型。
2. 页面接入 `useProjectScope`；项目未就绪时清空旧数据并阻断请求。
3. 筛选区提供颗粒度、参数管理专题类型、涉及系统多选、关键字；颗粒度切换清空不适用值并回到第一页。
4. 桌面表格展示名称、颗粒度、类型、简述、附件数量和操作；名称打开只读详情；移动端转换为卡片。
5. 新增/编辑抽屉支持完整元数据和多文件；系统级动态显示系统多选；上传期间禁用重复提交；删除使用明确确认文案。
6. 复用现有统一回收站入口，不增加专题局部回收站；下载只请求受控附件流，不展示对象引用。

- [ ] **步骤 3：运行类型检查与页面静态验收**

运行：`npm --prefix web run build`

预期：`vue-tsc` 和 Vite 构建成功；无 TOPIC 通用 API 残留；页面包含加载/空/失败/无权限/提交中/切换项目清空状态。

证据：构建退出码、`rg` 残留搜索、桌面和三个手机视口截图/操作记录。

**回滚：** 恢复 `TopicsPage.vue` 和 API TOPIC 映射；仅在 T2/T3 HTTP 契约同时回退时恢复通用薄页。

**停止条件：** 需要修改全局项目切换器、公共 UI 组件、平台参数页面，或移动端出现页面级横向滚动且无法通过专题局部布局修复。

**升级条件：** 产品要求专题页面展示项目名称、需要新增参数管理 UI、或接口需要改变公共附件 API。

### T5：集成、运行和浏览器验收

**需求映射：** R1, R2, R3, R4, R5, R6

**前置任务：** T1、T2、T3、T4

**输入事实：** 前序任务已产生 V180、专题 HTTP 契约、唯一 TOPIC 回收站来源和前端页面；全局治理存在其他需求目录的既有格式错误，不得借验收扩大修复范围。

**文件：**
- 修改：当前前缀下 `execution-*.json`、`observation-*.json`、`convergence.json`（仅记录真实证据；不修改其他需求账本）
- 测试：`server/src/modules/data-migration/src/test/**`、`npm --prefix web run build`

**接口：**
- 消费：T1-T4 全部产物。
- 产出：可复验的 Maven、Flyway、API、权限、浏览器和范围证据；若失败，形成原子反馈并回到对应任务。

- [ ] **步骤 1：局部和全量构建**

运行：

```bash
mvn -pl :ccb-data-migration -am test
npm --prefix web run build
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base HEAD --head HEAD --include-working-tree
```

预期：data-migration Maven 测试、前端构建和当前范围检查通过；全局治理若仍被 `REQ-20260904-061` 的既有格式错误阻断，只记录该外部失败，不修改范围外文件。

- [ ] **步骤 2：本地运行验证**

在开发测试环境启动 MySQL 8.4/MinIO/后端/前端，验证：

1. 未认证请求返回 401。
2. 普通用户列表/下载可用，编辑/删除他人记录返回 403。
3. 管理员新增项目级和系统级专题，系统级多选系统，绑定多个文件。
4. 编辑元数据和附件集合后列表/详情一致；下载主文件成功。
5. 逻辑删除进入统一回收站，管理员查看、恢复和 purge 后关系、附件、审计一致。
6. 参数停用、非法系统、重复系统、缺少必填项分别返回可识别 400/409。

- [ ] **步骤 3：真实浏览器验收**

使用管理员和普通用户，在桌面 1280x800、手机 375x812/390x844/430x932 完成列表筛选、详情、新增、多附件、编辑、下载、删除和回收站恢复；检查控制台无异常、刷新不丢项目上下文、无遮挡/溢出/重复提交。

- [ ] **步骤 4：独立观测与收敛判断**

由独立验证上下文复跑至少一组后端权限/迁移检查和一组浏览器路径，按 R1-R6 记录预期与观察差异。存在偏差时只修复对应任务边界；全部 must 通过后再写当前前缀 `convergence.json`，不得因单次构建通过直接宣布完成。

**回滚：** 应用版本整体回退；保留 V180 结构和参数，必要时在测试库重建。不得执行反向 Flyway 或清理管理员参数。

**停止条件：** 任一 P0/P1、越权、跨租户数据、附件孤儿、Flyway 失败、白屏、页面级横向滚动或统一回收站重复来源。

**升级条件：** 发现必须修改平台公共契约、生产环境、需求基准或当前 scope 未授权路径；等待 Owner/用户重新确认。

## 采样计划

| 采样点 | 触发 | 观察者 | 传感器 | 覆盖 |
|---|---|---|---|---|
| S0 | 计划批准前 | 主 Agent | 设计/范围/依赖审计 | R1-R6 |
| S1 | T1 完成 | 独立测试上下文 | MySQL8.4迁移、SHOW CREATE、参数计数 | R1-R3 |
| S2 | T2/T3 完成 | 独立后端测试上下文 | Maven 服务/注册/权限/回收站测试 | R1-R3,R5,R6 |
| S3 | T4 完成 | 独立前端上下文 | vue-tsc/Vite、残留搜索、视口检查 | R2-R4 |
| S4 | T5 完成 | 独立验证上下文 | API运行流、数据库断言、浏览器路径、控制台 | R1-R6 |

## 需求-任务-传感器追踪

| 需求 | 任务 | 传感器 |
|---|---|---|
| R1 | T1,T2,T4,T5 | V180结构、服务校验、页面表单、API/浏览器 |
| R2 | T1,T2,T4,T5 | 参数类别/23项计数、`SystemReferenceQuery`、options接口、动态筛选 |
| R3 | T1,T2,T3,T4,T5 | 关系唯一键、附件事务、回收站测试、多附件浏览器流 |
| R4 | T2,T4,T5 | 分页 API、前端构建、四种视口和操作路径 |
| R5 | T2,T3,T5 | 401/403/400/409、审计 SQL、普通/管理员浏览器 |
| R6 | T3,T4,T5 | TOPIC唯一来源注册、残留搜索、范围检查、回退演练 |

## 计划完成门禁

- 所有 R1-R6 均有任务、验收检查和传感器。
- T1-T5 依赖无环且串行原因明确。
- 参数管理是唯一类型来源；不新增 `dm_topic_type`。
- 高风险外部影响仅为已确认的 V180 参数表幂等初始化；其余平台表和代码只读复用。
- 用户批准本计划和高风险动作后，才生成/导入受控执行交接并进入 `$control-engineering`。

## 任务共同收尾与提交边界

每个 T1-T5 完成后，执行者必须先运行 `git diff --check` 和对应的局部验证命令，再在当前需求前缀下记录真实的 `execution-Tn` 证据；观察者随后记录对应 `observation-Tn`。本计划生成阶段不执行 Git commit、push、合并或发布。是否建立提交检查点、如何集成分支，待全部实现和验证完成后按 `finishing-a-development-branch` Skill 及用户授权处理。

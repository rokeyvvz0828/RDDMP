# 数据迁移内容编号治理实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将 9 张数据迁移内容表的 `doc_code` 统一改为服务端生成的独立业务编号，使 `id` 仅承担技术主键职责。

**架构：** 在 data-migration 模块内新增无状态 `ContentDocCodeGenerator`，按内容类型生成 `<TYPE_PREFIX>-<32 位小写无连字符 UUID>`。所有创建和 Excel 导入调用生成器，更新/文件替换按技术 `id` 定位并保留原编号；数据库列和活动唯一键不变。

**技术栈：** Java 17、Spring Boot/JdbcTemplate、MySQL 8.4、Vue 3/TypeScript、Element Plus、Apache POI、JUnit/Maven。

## 全局约束

- 目标模块为 `business/data-migration`；只修改当前需求 `writable_paths` 覆盖的文件。
- `id` 仅用于技术定位、数据库关联、审计和附件绑定；`doc_code` 是服务端生成且创建后不可编辑的业务编号。
- 9 张内容表类型为 `PLAN`、`MAPPING_DOC`、`DEPENDENCY`、`SCRIPT`、`TOPIC`、`RELEASE_DRILL`、`REPORT`、`RULE`、`PARAMETER`。
- UUID 使用小写 32 位十六进制且不含连字符；类型前缀与 UUID 之间保留一个 `-` 分隔符。
- 不新增 Flyway 迁移，不修改已发布迁移，不改平台/共享模块，不连接生产环境。
- POST 新增不接受 `assetCode`/`doc_code`；结构化 Excel 导入不把编号作为输入；导出可包含生成后的业务编号。
- 文件型内容的重新上传必须按记录 `id` 定位，保留原 `doc_code`、权限、MD5、附件生命周期和审计语义。
- 所有行为变更都要有可重复的后端测试、前端构建和范围/治理检查；不得覆盖工作树中的其他改动。

---

### T1：后端编号生成与内容写入契约

**需求映射：** R1, R2, R3, R4

**前置任务：** 无

**文件：**
- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentDocCodeGenerator.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/PlanService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ReportService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/StructuredAssetService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ExcelService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ContentAssetController.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/StructuredAssetController.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ContentDocCodeGeneratorTest.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ContentAssetMigrationMySqlTest.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ReportServiceTest.java`

**接口：**
- 产出：`ContentDocCodeGenerator.generate(String assetType): String`，只接受 9 个登记类型并返回类型前缀加 32 位小写 UUID。
- 修改产出：`ContentFileAssetService.create(type, projectId, componentId, attachmentId, checksumMd5, user)`；创建时生成编号。
- 修改产出：`ContentFileAssetService.replace(type, id, componentId, attachmentId, checksumMd5, user)`；按 `id` 替换文件并保号。
- 修改接口：文件型 POST 上传移除 `assetCode`；增加 `PUT /{resource}/{id}/upload`。
- 修改接口：结构化新增/更新和 Excel 导入不读取客户端 `assetCode`，更新只按 `id` 改名称、组件和结构化主体。

- [ ] **步骤 1：建立基准检查和失败断言**

运行：`rg -n -S 'PLAN-" \+ id|REPORT-" \+ id|assetCode|doc_code.*VALUES' server/src/modules/data-migration/src/main/java`

预期：确认现有 `PLAN-{id}`、`REPORT-{id}`、文件型按编号 upsert、结构化 `assetCode` 写入和 Excel 编号列位置；新增测试先断言生成器不存在或服务仍违反新契约。

证据：保存命令退出码、命中路径和测试失败断言。

- [ ] **步骤 2：实施集中生成器**

实现 `ContentDocCodeGenerator` 的类型白名单：`PLAN`、`MAPPING_DOC`、`DEPENDENCY`、`SCRIPT`、`TOPIC`、`RELEASE_DRILL`、`REPORT`、`RULE`、`PARAMETER`；使用 `UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT)`，按类型前缀拼接，不读取数据库、不接收 `id`。

- [ ] **步骤 3：改造服务端创建/更新/替换**

将 `PlanService` 和 `ReportService` 的编号生成替换为生成器；`ContentFileAssetService` 拆分新增与按 `id` 替换，取消按 `project_id + assetCode` upsert；`StructuredAssetService` 创建时生成编号、更新时不再读取或写入 `assetCode`；`ExcelService` 结构化导入删除编号输入列并逐行调用生成器。所有查询、回收站、导出仍投影 `doc_code AS asset_code`。

- [ ] **步骤 4：收敛 Controller 契约**

移除文件上传 `@RequestParam String assetCode`，新增 5 个文件资源的 `PUT /{resource}/{id}/upload` 映射；保留认证、项目可达性、实体授权、MD5 查重、附件关系和审计。结构化 Controller 继续接收 JSON/Excel，但编号字段不参与持久化。

- [ ] **步骤 5：运行后端局部验证**

运行：`mvn -pl :ccb-data-migration -am -Dtest=ContentDocCodeGeneratorTest,ContentAssetMigrationMySqlTest,ReportServiceTest test`

预期：9 种类型格式断言通过；创建不依赖 `id`；更新、文件替换和恢复保留 `doc_code`；活动唯一冲突仍返回冲突语义；Excel 行生成编号。

证据：保存 Maven 退出码、测试数量和失败数。

**回滚：** 仅回退 T1 修改的 data-migration Java 文件；不触碰数据库结构和其他工作树改动。

**停止条件：** 发现必须修改平台/共享模块、必须新增数据库状态，或文件替换无法在现有权限/附件契约内按 `id` 实现时停止并重新建模。

**升级条件：** 现有测试或接口消费者要求 `assetCode` 作为持久化输入，或唯一键冲突不能保持现有错误语义时升级主 Agent/用户。

---

### T2：前端 API、表单和文件替换交互

**需求映射：** R1, R3, R4, R5

**前置任务：** T1

**文件：**
- 修改：`web/src/api/data-migration.ts`
- 修改：`web/src/modules/data-migration/components/AssetListView.vue`
- 修改：`web/src/modules/data-migration/components/StructuredListView.vue`
- 修改：`web/src/modules/data-migration/views/content/PlansPage.vue`
- 修改：`web/src/modules/data-migration/views/content/ReportsPage.vue`
- 修改：`web/src/modules/data-migration/views/content/RecycleBinPage.vue`

**接口：**
- 消费：T1 的无编号 POST 创建和按 `id` PUT 文件替换。
- 产出：`uploadDataMigrationAsset(type, projectId, file, componentId?)` 不再发送 `assetCode`；`replaceDataMigrationAsset(type, id, file, componentId?)` 按 `id` 替换。
- 产出：结构化更新 body 不含 `assetCode`；列表/详情/回收站继续读取 `asset_code`。

- [ ] **步骤 1：建立前端基准检查**

运行：`rg -n -S 'assetCode|uploadAssetCode|<dt>ID|prop="id"' web/src/api/data-migration.ts web/src/modules/data-migration`

预期：命中编号输入、旧上传参数、结构化更新回传和技术 ID 展示位置。

- [ ] **步骤 2：更新 API 封装**

删除上传函数的 `assetCode` 参数和 FormData 字段；增加按 `id` 的替换函数；保留响应类型中的 `asset_code`，因为它是业务展示编号。

- [ ] **步骤 3：更新页面交互**

移除通用文件上传抽屉的编号输入；增加行级“替换文件”入口并按记录 `id` 调用 PUT；结构化编辑不再提交 `assetCode`；计划/汇报页面继续显示生成编号但移除技术 ID 展示；回收站只显示业务编号。

- [ ] **步骤 4：运行类型和构建验证**

运行：`npm --prefix web run build`

预期：TypeScript/Vite 构建成功，0 个错误；静态搜索不再出现用户填写 `assetCode` 的表单或上传参数。

证据：保存构建退出码、产物生成结果和静态搜索结果。

**回滚：** 回退 T2 前端/API 文件，保留 T1 后端变更前不得单独发布。

**停止条件：** 页面必须继续依赖按编号 upsert 才能完成既有替换流程，且无法在 `id` 路由下恢复时停止。

**升级条件：** 前端现有组件契约要求展示技术 ID，或移动端替换入口导致视口溢出/遮挡时升级。

---

### T3：需求、数据库和接口文档口径同步

**需求映射：** R1, R2, R3, R5

**前置任务：** T1, T2

**文件：**
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/database-schema-and-relations.md`
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/requirement.md`
- 修改：`docs/engineering-control/designs/2026-08-31-data-migration-content-table-split-design.md`
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/design-report-material.md`

**接口：**
- 消费：T1/T2 的最终编号格式、创建/更新/替换和 Excel 契约。
- 产出：数据库字段来源矩阵、需求不变量、接口说明和唯一事实源引用。

- [ ] **步骤 1：修订公共列说明**

在数据库关系说明第 3.1 节增加 `doc_code` 来源和禁止事项：历史迁移字段映射仅作为迁移事实，新建统一由服务端生成；`active_doc_code` 仅是生成列。

- [ ] **步骤 2：同步内容拆分和汇报材料设计**

删除或修正“`REPORT-{id}`”“时间戳随机编号”“assetCode 必填”等过时表述，统一引用 `ContentDocCodeGenerator` 规则；明确导入模板无编号输入、导出编号只读。

- [ ] **步骤 3：补充需求验收和回退**

在当前需求增量中登记 `id`/`doc_code` 不变量、按 `id` 文件替换、无数据库迁移和应用回退策略，不扩大到其他编号体系。

- [ ] **步骤 4：运行文档和范围检查**

运行：`node scripts/check-all-governance.mjs`；`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base origin/main --head HEAD --working-tree`

预期：治理和当前任务范围检查通过，文档中无未完成占位内容或与选定规则冲突的旧生成描述。

证据：保存两条命令的退出码和关键摘要。

**回滚：** 只回退 T3 文档修改，不修改已发布 Flyway 脚本。

**停止条件：** 发现文档修改超出当前 `writable_paths` 或要求改变已发布迁移时停止。

**升级条件：** 发现其他 ready 需求依赖旧编号输入契约时升级并拆分兼容任务。

---

### T4：集成回归、运行观测与交接证据

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T3

**文件：**
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/execution-T36.json`
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/observation-T36.json`
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/state.json`
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/convergence.json`（仅全部门禁通过后）

**接口：**
- 消费：T1-T3 的代码、API、文档和测试输出。
- 产出：可独立复验的执行、观测和收敛证据；不伪造独立性，明确同一上下文限制。

- [ ] **步骤 1：运行聚焦和全量后端测试**

运行：`mvn -pl :ccb-data-migration -am test`；`mvn test`

预期：数据迁移模块测试 0 失败；全量结果如有其他模块历史/环境失败，按模块归因记录，不冒充本需求通过。

- [ ] **步骤 2：运行前端和治理检查**

运行：`npm --prefix web run build`；`node scripts/check-all-governance.mjs`；当前 scope 检查命令。

预期：前端构建、治理和范围检查通过。

- [ ] **步骤 3：本地运行与浏览器验收**

在本地测试环境启动 MySQL/MinIO、后端和前端，验证管理员和普通用户在桌面及 `390x844` 视口完成：新增 9 类内容、查看生成编号、编辑保号、文件按 `id` 替换、Excel 导入生成编号、导出编号、回收站恢复。

预期：接口无 500、无越权、无重复新增、控制台无错误，技术 ID 不作为业务编号展示。

- [ ] **步骤 4：写入独立观测与收敛证据**

记录真实命令、退出码、测试数量、浏览器路径、角色、视口、接口结果、范围审计和剩余风险；只有全部 must 条件通过才创建/更新 `convergence.json` 并将 phase 置为 `converged`。

**回滚：** 应用回退到 T1 前版本；不执行数据库反向迁移。运行期测试数据使用本地受控清理。

**停止条件：** 任一 must 验收失败、出现跨租户/项目访问、编号可被客户端覆盖、文件替换丢附件或浏览器白屏时停止并回到 planning/correcting。

**升级条件：** 发现生产数据或已投产客户端事实、公共平台契约需要变更，或同一反馈连续三次无法在当前任务边界内闭合。

## 依赖与并行策略

任务严格串行：T1 固定后端编号和接口契约，T2 依赖 T1 更新前端，T3 依赖代码和页面最终口径，T4 在全部实现和文档完成后独立观测。不存在可证明安全的并行写入组，避免共同修改 `data-migration.ts`、内容服务和需求文档造成契约漂移。

## 控制种子（仅候选假设，待建模验证）

- 被控边界：data-migration 9 张内容表的新建、更新、文件替换、结构化 Excel 导入和编号展示。
- 状态变量：生成器版本、内容表 `doc_code`、`active_doc_code`、文件附件关系、请求契约、前端表单状态、测试/构建结果。
- 输入：认证用户、项目/组件、文件附件、结构化内容和页面操作；客户端编号字段视为不可信扰动。
- 输出：服务端生成编号、内容表行、附件关系、API 响应、页面展示和审计记录。
- 传感器：单元/服务/数据库测试、静态搜索、治理/范围脚本、Maven/Vite 构建、运行健康检查和浏览器验收。
- 执行器：生成器实现、服务/Controller/API/UI/Excel 修改和测试补充。
- 扰动：工作树已有未关联改动、UUID 碰撞、旧客户端请求、附件状态变化、测试环境缺失。
- 时延：前后端同版本发布；唯一键和审计结果在写事务提交后可观测。
- 假设：尚未投产且无生产历史兼容要求；数据库已有 `doc_code`/`active_doc_code` 约束足够承载新规则。

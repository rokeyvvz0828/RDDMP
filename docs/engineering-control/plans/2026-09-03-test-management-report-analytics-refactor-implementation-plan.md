# 测试报告与分析统计重构实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 以统一中文指标引擎完成八章节测试报告、26 张固定报表、16 张固定图表、自定义分析与质量阈值，并兼容 V1 历史数据。

**架构：** 在测试管理模块内引入唯一的指标目录与计算引擎；报告、固定统计和自定义分析均消费该引擎。通过 `V20260914233001` 追加 V2 语义与阈值数据，V1 数据经兼容适配读取，不修改已发布迁移。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MyBatis 既有设施、MySQL 8.4、Flyway、Apache POI、Vue 3、TypeScript、Element Plus、ECharts。

## 状态与来源

- 计划修订：3
- 手工上传报告增补：用户于 2026-09-14 确认，按测试方案的附件、版本确认和权限模式实现。
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-03-test-management-report-analytics-refactor-design.md`
- 状态：可移交

## 2026-09-03 执行偏差纠正任务包

**触发证据：** 用户在本地验收中反馈报告没有数据、统计分析仍显示英文，并要求重新对照说明书补齐遗漏。经代码复查，已实现的固定目录名称与原始 V1.1 目录不一致，固定项大量复用概览聚合，报告详情与导出缺少八章节的事实表和图形数据，自定义分析没有实际执行保存的维度/指标配置。

**本修订不改变已确认需求，只收紧实现和验收。** 以下任务替代原 T2、T3、T5、T6、T8 中的泛化实现；用户于 2026-09-03 指示“继续完成”，作为继续执行已确认范围的授权证据。

### C1：以原始目录替换占位的固定报表和图表

**需求映射：** R3, R4, R8
**文件：** `TestAnalyticsMetricCatalog.java`、`TestAnalyticsAdvancedService.java`、`TestAnalyticsServiceTest.java`
**产出：** 目录必须逐项等于 V1.1：

- RPT-001 测试执行进度汇总表，002 按周期各系统执行统计表，003 按轮次各系统执行统计表，004 按机构各周期执行统计表，005 按机构各轮次执行统计表，006 测试案例执行明细表，007 测试缺陷明细表，008 缺陷状态分布统计表，009 缺陷严重程度分布统计表，010 按系统缺陷密度统计表，011 范围覆盖分析表，012 测试人员工作量统计表，013 缺陷生命周期统计表，014 按案例类型执行统计表，015 按优先级执行统计表，016 核算相关功能测试统计表，017 缺陷处理效率统计表，018 测试轮次对比分析表，019 系统间测试质量对比表，020 测试日报表（按子系统），021 测试周报表（按子系统），022 测试月报表（按子系统），023 项目测试日报汇总表，024 机构测试质量汇总表，025 轮次内按日进度跟踪表，026 缺陷闭环统计表。
- CHT-001 案例执行状态分布，002 缺陷状态分布，003 缺陷严重程度分布，004 执行/案例成功/已执行成功率趋势，005 系统质量对比，006 范围执行覆盖率，007 系统×周期执行状态，008 缺陷严重程度×状态，009 测试人员工作量，010 缺陷提出/关闭趋势，011 案例类型分布，012 系统质量雷达图，013 日执行量与累计执行率，014 缺陷状态随时间，015 系统日执行强度热力图，016 轮次执行状态对比。

**实施与验证：** 统一以“每案例最新执行记录 + 有效案例”构建受参数绑定的事实查询；每个编号显式选择维度、指标、时间粒度和图形语义，不能把未实现编号降级为概览。为 42 项建立唯一中文名称、结果形状和可执行断言；执行 `mvn -pl :ccb-test-management -am test -Dtest=TestAnalyticsMetricCatalogTest,TestAnalyticsServiceTest`，并在本地样本逐项请求，结果为数据行或中文空态。

### C2：将八章节报告变为冻结的事实展示和正式导出

**需求映射：** R2, R3, R6, R7, R8
**文件：** `TestReportService.java`、`TestReportDocumentService.java`、`TestReportController.java`、`TestReportPage.vue`、`TestReportServiceTest.java`
**产出：** 新建只接受项目/责任团队组织/系统 × 全周期/轮次；历史专项报告仅可阅读和导出。每个新版本快照含概览、环境、范围策略、系统/轮次/日期执行表与趋势、缺陷状态/严重程度/明细、仅生效质量阈值、风险和结论八章所需事实。页面使用 KPI、趋势/分布图和局部滚动的中文明细，DOCX/PDF 使用同一份冻结快照。

**实施与验证：** 单测覆盖“执行中不计已执行、失效案例不作分母、轮次/周期影响缺陷和执行范围、停用阈值不展示、SPECIAL 禁止新建”。以种子报告导出 DOCX/PDF，检查八个中文章节标题和快照版本号。

### C3：落实可执行的自定义分析、导出和中文界面

**需求映射：** R1, R4, R5, R8
**文件：** `TestAnalyticsAdvancedService.java`、`TestAnalyticsController.java`、`TestAnalyticsWorkbookService.java`、`api.ts`、`TestAnalyticsPage.vue`、`TestAnalyticsServiceTest.java`
**产出：** 后端接受白名单维度、指标、筛选、行/列/度量及图形类型配置并实际返回数据；保存的个人/共享视图可恢复并运行配置。分析页面全中文，提供项目→机构→系统→范围→案例和生命周期→轮次→周期→日/周/月的受限下钻；XLSX/PDF/PNG 统一中文标题、筛选范围和权限审计。

**实施与验证：** 对非法字段、非法图形、跨项目/测试大类和无权限导出返回中文拒绝；对一个多维配置断言返回分组行和可视图形；Vite 构建通过，桌面及 375/390/430px 使用浏览器视口验证主路径无页面横向溢出。

### C4：使本地样本成为真实口径的验收数据

**需求映射：** R9, R2, R4
**文件：** `seed-report-analytics-mock.sql`、`TestAnalyticsServiceTest.java`、`TestReportServiceTest.java`
**产出：** 幂等脚本只写测试管理自有表，按每个可用项目写入至少 3 系统、2 责任团队、3 轮次、6 周期、60 案例、执行/缺陷/阈值及六种可展示报告；报告快照的指标来自与服务端一致的最新执行和有效案例语义，不伪造不能复算的值。

**实施与验证：** 连续执行两次脚本，分别以 SQL 和 API 核对案例/轮次/周期/报告组合计数及 RPT-001、RPT-002、RPT-008、CHT-004 的非空数据；不足主数据时事务回滚且零写入。

## 全局约束

- 仅修改 `docs/requirements/REQ-20260903-063-test-management-report-analytics-refactor/codex-task-scope.yaml` 的 `writable_paths`。
- 保持 `com.ccb.*`、现有路由和模块边界；不改动项目、组织、物理子系统、案例、执行或缺陷的主数据所有权。
- 机构维度读取物理子系统责任团队组织；测试大类由路由 `domain` 固定，不提供跨大类筛选。
- 有效案例排除无效案例；执行中独立展示且不计入已执行；所有用户可见文本和导出使用中文。
- `V20260914233001` 只追加，不修改 V142~V146；写操作必须经服务端权限、租户、项目和实体范围校验并记录审计。
- 不新增依赖。中文 PDF 能力先以现有能力做实际文件验证；若无法输出中文，则停止该任务并请求最小依赖或字体资源的范围审批。
- 手工报告仅接受 `.docx`、`.xlsx` 且最大 50MB；必须复用附件平台，上传的附件必须属于当前操作人。手工报告不生成统计快照、图表或质量结论。

### U1：手工上传报告与版本管理

**需求映射：** R10, R7, R8

**前置任务：** C2

**文件：**

- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/report/TestReportService.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestReportController.java`
- 修改：`web/src/modules/test-management/api.ts`
- 修改：`web/src/modules/test-management/report/TestReportPage.vue`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/report/TestReportServiceTest.java`

**接口：**

- 消费：附件平台的 `AttachmentGateway`、当前报告权限、项目/测试大类/报告范围、测试方案的 `.docx/.xlsx`、50MB、同名版本确认约定。
- 产出：`POST /api/test-management/reports/{domain}/upload` 和 `POST /{id}/versions/upload`；请求包含 `projectId`、`attachment_id`、`report_name`（新建时）、`version_note` 和当前范围，响应包含 `version_confirmation_required`、`next_version` 或新版本元数据。

- [ ] **步骤 1：确认兼容存储边界**

不新增或改写 Flyway 迁移。手工报告使用既有 `tm_test_report.source_type` 标记来源，并将附件、文件元数据和版本说明写入既有报告版本的 `snapshot_json`；这不会伪造统计快照。

- [ ] **步骤 2：实现服务端上传契约与校验**

在报告服务复用附件平台检查上传人、`.docx/.xlsx` 与 50MB；对项目、测试大类、项目/机构/系统范围和 RBAC 做服务端验证。同一范围的同名手工报告先返回版本确认，再追加不可变版本并写 `UPLOAD`/`UPLOAD_VERSION` 审计；手工版本不写虚构快照。

- [ ] **步骤 3：实现单页上传交互**

在报告页新增“上传报告”主操作、上传弹框和版本操作；字段、选择文件、提交中、失败恢复、同名确认、中文消息与测试方案一致。列表展示“系统生成/手工上传”来源；手工报告详情切换为文件版本列表和下载，不展示数据解读或图表。

- [ ] **步骤 4：运行局部与浏览器验证**

运行：`mvn -pl :ccb-test-management -DskipTests compile`、`vue-tsc -p web/tsconfig.json --noEmit`、`node scripts/check-flyway-migrations.mjs`。

预期：手工 `.docx/.xlsx` 上传、同名确认新增版本、非本人附件/超限/错误后缀拒绝、生成报告仍可用；本地浏览器可完成打开上传弹框、选择文件、提交、查看版本和下载路径。

**回滚：** 回退应用代码；已上传附件绑定记录和版本元数据保留，既有报告继续可读。

**停止条件：** 附件平台无法按当前用户读取已上传文件，或需要修改共享附件模块。

**升级条件：** 上传文件类型或大小规则与测试方案的既有规则冲突。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V20260914233001__test_management_report_analytics_refactor.sql` | candidate-new | V2 兼容列、阈值表与菜单权限追加。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/analytics/TestAnalyticsMetricCatalog.java` | candidate-new | 中文指标、维度、固定报表/图表目录和组合校验。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/analytics/TestAnalyticsService.java` | existing | 统一统计、钻取、配置、快照与审计编排。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/analytics/TestAnalyticsAdvancedService.java` | existing | 固定报表、图表与时间/组织聚合。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/report/TestReportService.java` | existing | 六种报告组合、八章节事实快照和质量结论。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestConfigurationService.java` | existing | 项目加测试大类阈值维护与审计。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestReportDocumentService.java` | existing | 中文 DOCX/PDF 报告输出。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestAnalyticsWorkbookService.java` | existing | 中文 XLSX 统计输出。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/*.java` | existing | 报告、统计和配置 HTTP/权限适配。 |
| `web/src/modules/test-management/api.ts` | existing | 本模块请求契约和中文导出命名。 |
| `web/src/modules/test-management/configuration/TestConfigurationPage.vue` | existing | 质量阈值配置页。 |
| `web/src/modules/test-management/report/TestReportPage.vue` | existing | 八章节报告、版本、中文导出与响应式体验。 |
| `web/src/modules/test-management/analytics/TestAnalyticsPage.vue` | existing | 固定目录、自定义设计、下钻、导出与响应式体验。 |
| `server/src/modules/test-management/scripts/seed-report-analytics-mock.sql` | candidate-new | 本地幂等报告/统计模拟数据。 |
| `server/src/modules/test-management/src/test/java/com/ccb/testmanagement/analytics/*.java` | candidate-new | 指标、目录、固定统计和权限测试。 |
| `server/src/modules/test-management/src/test/java/com/ccb/testmanagement/report/*.java` | candidate-new | 报告范围、快照与中文导出测试。 |

## 任务依赖图与并行策略

`T1 → T2 → T3 → T5 → T8 → T7`；`T1 → T4 → T6 → T8 → T7`；`T3 → T6`。

所有任务串行执行：它们共享迁移、统一指标契约和 `web/src/modules/test-management/api.ts`，不安排并行写入。

## 需求覆盖表

| 需求 | 覆盖任务 |
| --- | --- |
| R1 | T2, T3, T5, T6, T7 |
| R2 | T2, T5, T7 |
| R3 | T2, T3, T5, T6 |
| R4 | T3, T6, T7 |
| R5 | T3, T6, T7 |
| R6 | T1, T2, T4, T5, T7 |
| R7 | T1, T2, T3, T7 |
| R8 | T4, T5, T6, T7 |
| R9 | T8, T7 |

### T1：建立 V2 兼容数据结构与质量阈值配置

**需求映射：** R6, R7

**前置任务：** 无

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V20260914233001__test_management_report_analytics_refactor.sql`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestConfigurationService.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestConfigurationController.java`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/service/TestConfigurationServiceTest.java`

**接口：**

- 消费：当前 `domain`、项目上下文、配置菜单权限、`tm_test_report` 与 `tm_test_analytics_*` 历史表。
- 产出：按项目和测试大类读取/保存 `QualityThreshold` 配置；V2 兼容结构与审计语义。

- [ ] **步骤 1：建立迁移和阈值失败检查**

运行：`node scripts/check-flyway-migrations.mjs`

预期：当前基线通过；新增迁移前，阈值 API/服务测试缺少预期能力。

证据：退出码、缺失能力的断言。

- [ ] **步骤 2：追加最小迁移**

新增 `V20260914233001`：创建质量阈值表，固定指标代码、比较方向、达标/风险阈值、生效标记、审计字段和项目/大类索引；对报告/统计 V2 只增加兼容性字段，不改写历史行或 V142~V146。

- [ ] **步骤 3：实现服务端配置和审计**

在 `TestConfigurationService` 中实现项目/大类边界校验、固定目录校验、启停和双阈值校验；在 Controller 使用既有配置动作权限暴露读写接口。

- [ ] **步骤 4：执行局部验证**

运行：`mvn -pl :ccb-test-management -am test -Dtest=TestConfigurationServiceTest`

预期：阈值创建、更新、停用、越权/越项目拒绝和审计断言通过。

**回滚：** 回退本任务 Java 代码；保留本需求追加迁移和阈值数据。

**停止条件：** 需要修改已发布迁移或平台主数据表。

**升级条件：** 现有配置权限无法区分查看与维护，或迁移需新增未授权公共能力。

### T2：实现统一指标引擎和 V2 报告快照

**需求映射：** R1, R2, R3, R6, R7

**前置任务：** T1

**文件：**

- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/analytics/TestAnalyticsMetricCatalog.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/report/TestReportService.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestReportController.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestReportDocumentService.java`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/report/TestReportServiceTest.java`

**接口：**

- 消费：T1 阈值读取、责任团队组织字段、案例/执行/缺陷事实表。
- 产出：`MetricSnapshot`（中文指标、筛选上下文、状态分布、质量判定）和六种新报告组合。

- [ ] **步骤 1：编写状态与分母测试**

在报告服务测试中构造有效、无效、执行中、成功、失败、阻塞和未执行案例，断言执行中不增加已执行；断言项目/机构/系统、全周期/轮次的范围隔离。

- [ ] **步骤 2：实现指标目录和快照计算**

集中定义中文指标代码、分母、显示名和比较方向；报告服务只调用此目录计算最新状态、责任团队组织聚合、八章节数据和生效阈值结论。

- [ ] **步骤 3：兼容历史报告**

保留历史周期级/专项级对象的详情与导出适配；新建/重生成界面和服务端仅接受项目、机构、系统加全周期/轮次组合。

- [ ] **步骤 4：验证中文导出基础**

生成最小 DOCX/PDF，实际读取文件内容和打开结果验证中文标题、章节和指标没有乱码；若现有 PDF 实现无法渲染中文，停止并请求字体/依赖范围决策。

- [ ] **步骤 5：执行局部验证**

运行：`mvn -pl :ccb-test-management -am test -Dtest=TestReportServiceTest`

预期：指标、快照、历史兼容、权限与报告范围测试通过。

**回滚：** 回退报告和目录代码；本需求追加迁移保留且历史报告仍由兼容逻辑读取。

**停止条件：** 指标需要修改案例、执行或缺陷的主数据语义。

**升级条件：** 中文 PDF 需要未授权依赖、字体资源或公共导出平台改动。

### T3：实现固定目录、自定义分析与统计导出服务端契约

**需求映射：** R1, R3, R4, R5, R7

**前置任务：** T2

**文件：**

- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/analytics/TestAnalyticsMetricCatalog.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/analytics/TestAnalyticsService.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/analytics/TestAnalyticsAdvancedService.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestAnalyticsWorkbookService.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestAnalyticsController.java`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/analytics/TestAnalyticsServiceTest.java`

**接口：**

- 消费：T2 的 `MetricSnapshot` 与目录；现有统计配置/快照表。
- 产出：RPT/CHT 中文目录、受约束查询/钻取、个人/共享配置、快照和 XLSX/PDF/PNG 导出请求契约。

- [ ] **步骤 1：建立目录覆盖测试**

断言 RPT-001~RPT-026、CHT-001~CHT-016 的唯一编号、中文名称、允许维度、指标、图形和下钻实体完整；断言不存在跨测试大类筛选参数。

- [ ] **步骤 2：实现查询规划器**

让固定报表和自定义配置都经目录校验后调用统一指标聚合；禁止任意字段、任意 SQL、无效图表组合和跨项目/大类访问。

- [ ] **步骤 3：实现钻取、共享、快照与审计**

钻取继承全部筛选；共享仅限同项目同大类有查看权限成员；归档保存事实和筛选；写入、发布、删除与导出写审计。

- [ ] **步骤 4：完成中文导出契约**

XLSX 表头、PDF 内容和 PNG 元数据带中文标题、生成时间和筛选范围；每种格式拒绝无权限请求。

- [ ] **步骤 5：执行局部验证**

运行：`mvn -pl :ccb-test-management -am test -Dtest=TestAnalyticsServiceTest`

预期：目录、口径、下钻、共享、快照、导出与越权拒绝测试通过。

**回滚：** 回退统计服务代码；历史统计配置和快照保持可读。

**停止条件：** 自定义分析需要修改其他业务模块数据或执行未限制 SQL。

**升级条件：** 26/16 中任一项目依赖未登记维度或主数据字段。

### T4：交付质量阈值前端配置与中文全状态体验

**需求映射：** R6, R8

**前置任务：** T1

**文件：**

- 修改：`web/src/modules/test-management/api.ts`
- 修改：`web/src/modules/test-management/configuration/TestConfigurationPage.vue`
- 修改：`web/src/modules/test-management/configuration/test-configuration.css`

**接口：**

- 消费：T1 的阈值读取/保存接口和配置权限。
- 产出：中文阈值配置页，能展示指标方向、生效状态、达标/风险阈值、加载/空/失败/无权限/提交中状态。

- [ ] **步骤 1：为阈值 API 和页面状态建立类型边界**

在模块 `api.ts` 定义中文展示所需的 `QualityThreshold` 请求/响应类型，不改动公共 HTTP 基础设施。

- [ ] **步骤 2：实现配置 Tab 和表单校验**

复用 `UiDataTable` 与 `TestManagementFormDialog`，以开关表达是否生效；按指标方向限制两个阈值关系；提交中禁用重复保存。

- [ ] **步骤 3：实施移动端布局**

760px 以下表单单列、表格在明确容器滚动，长指标名换行，操作收敛为主操作和更多操作。

- [ ] **步骤 4：执行前端构建**

运行：`npm --prefix web run build`

预期：类型检查和 Vite 构建通过。

**回滚：** 回退配置页面和模块 API；阈值数据保留但不影响旧页面。

**停止条件：** 需要修改 `web/src/components/ui` 或全局主题。

**升级条件：** 现有配置菜单权限无法在服务端表达维护权限。

### T5：重构八章节报告前端与中文导出体验

**需求映射：** R1, R2, R3, R6, R8

**前置任务：** T2, T4

**文件：**

- 修改：`web/src/modules/test-management/api.ts`
- 修改：`web/src/modules/test-management/report/TestReportPage.vue`

**接口：**

- 消费：T2 的六种报告组合、八章节、质量快照和历史兼容接口。
- 产出：中文报告树、生成向导、正式报告阅读器、版本详情、章节专属补充弹框、导出和状态可视化。

- [ ] **步骤 1：替换报告范围和时间选择**

生成向导只提供项目/机构/系统加全周期/轮次；历史对象显示“历史报告”且保持查看/导出，不提供旧类型的新建。

- [ ] **步骤 2：实现八章节阅读器和冻结图表**

在封面区显示范围、版本、快照时间和总体质量结论；执行章节显示有效、无效、已执行、执行中、成功、失败、阻塞、未执行及三项比率，并按当前版本冻结状态渲染图表。范围、缺陷、质量和风险章节只消费快照中的对应冻结分布，质量章节仅显示生效指标及总体中文判定；缺失的历史分布明确降级，不使用实时数据补全。

- [ ] **步骤 3：实现章节专属补充弹框**

每一章的“编辑补充”打开带当前章节和版本上下文的独立富文本弹框；提交调用既有版本补充接口，防止重复提交，成功后直接刷新该章节的补充内容。阅读器内不保留全局底部编辑器。

- [ ] **步骤 4：实现全状态和响应式路径**

未选项目、空树、版本加载失败、无权限、生成中和导出失败均有中文可恢复反馈；手机端树置顶、内容纵向排列，明细表局部滚动。

- [ ] **步骤 5：执行前端构建与浏览器采样**

运行：`npm --prefix web run build`

预期：报告页面通过 TypeScript 与 Vite 构建；桌面及 375px、390px、430px 视口中阅读器、版本切换、图表和章节编辑无页面级横向溢出。

**回滚：** 回退报告页面和模块 API；服务端兼容层仍允许历史读取。

**停止条件：** 新页面需要修改公共组件或路由。

**升级条件：** 服务端快照无法同时表达八章节与历史版本。

### T6：重构统计目录、自定义分析与移动端页面

**需求映射：** R1, R3, R4, R5, R8

**前置任务：** T3, T4

**文件：**

- 修改：`web/src/modules/test-management/api.ts`
- 修改：`web/src/modules/test-management/analytics/TestAnalyticsPage.vue`

**接口：**

- 消费：T3 的 RPT/CHT 目录、受约束查询、钻取、共享、快照与导出接口。
- 产出：中文固定目录、自定义拖拽区、筛选、图表、明细、共享和导出体验。

- [ ] **步骤 1：实现 26/16 中文目录导航**

按固定目录分类展示报表和图表，路由已固定 domain，页面不渲染测试大类筛选器；无数据时保留目录和中文空态。

- [ ] **步骤 2：实现受约束自定义设计器**

用行、列、指标和筛选分区表达拖拽配置；保存前显示无效组合原因，加载个人/共享视图时恢复完整上下文。

- [ ] **步骤 3：实现下钻、导出和移动布局**

钻取以面包屑展示组织/时间路径并可返回；XLSX/PDF/PNG 操作保留权限与失败提示；手机端图表固定容器、标签精简，表格只局部横向滚动。

- [ ] **步骤 4：执行前端构建**

运行：`npm --prefix web run build`

预期：统计页面通过 TypeScript 与 Vite 构建。

**回滚：** 回退统计页面和模块 API；既有 V1 统计可通过服务端兼容响应读取。

**停止条件：** 自定义拖拽需要引入新前端依赖。

**升级条件：** ECharts 现有能力无法实现某固定图形且需更换公共图表组件。

### T8：准备足量、幂等的本地分析统计模拟数据

**需求映射：** R4, R5, R8, R9

**前置任务：** T5, T6

**文件：**

- 新建：`server/src/modules/test-management/scripts/seed-report-analytics-mock.sql`
- 新建：`docs/requirements/REQ-20260903-063-test-management-report-analytics-refactor/mock-data-report-analytics-guide.md`

**接口：**

- 消费：本地已有项目、至少 3 个物理子系统及至少 2 个责任团队组织；T1 的阈值结构。
- 产出：只写入测试管理自有表的虚构项目数据，含至少 60 个案例、执行状态、缺陷、轮次/周期、阈值、报告版本和统计快照。

- [ ] **步骤 1：建立主数据前置检查**

脚本先以临时上下文验证可用项目、3 个系统和 2 个责任团队组织；不满足时 `SIGNAL` 失败，事务开始前不写入业务表。

- [ ] **步骤 2：生成覆盖矩阵**

使用固定模拟 ID 和 `ON DUPLICATE KEY UPDATE` 生成 3 个轮次、每轮 2 个周期、60 个以上案例；每个系统包含有效/无效、未执行/执行中/成功/失败/阻塞案例，跨日期、多执行人、全缺陷状态和严重程度。

- [ ] **步骤 3：生成报告与统计验证样本**

写入本任务拥有的阈值、报告版本和统计快照；数据使用中文虚构名称与 `【模拟】` 标识，不写项目、组织或架构表。

- [ ] **步骤 4：验证幂等性和分布**

在隔离本地库连续执行两次，查询案例、执行、缺陷、报告、快照与阈值数量；第二次执行不增加记录，且各统计状态与组织/时间维度均有样本。

**回滚：** 仅清理由本脚本固定 ID 创建的测试管理模拟记录；不删除人工记录、主数据或 Flyway 数据。

**停止条件：** 本地缺少 3 个系统、2 个责任团队组织或需要写入其他模块主数据。

**升级条件：** 既有本地表结构无法容纳完整 V1.1 状态和历史样本。

### T7：集成验证、历史兼容与控制证据

**需求映射：** R1, R2, R4, R5, R6, R7, R8

**前置任务：** T8

**文件：**

- 修改：`docs/requirements/REQ-20260903-063-test-management-report-analytics-refactor/codex-task-scope.yaml`
- 修改：`.ai-control/requirements/req-20260903-063-test-management-report-analytics-refactor/execution-T*.json`
- 修改：`.ai-control/requirements/req-20260903-063-test-management-report-analytics-refactor/observation-T*.json`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/**`

**接口：**

- 消费：T1~T6 的最终数据库、服务和页面契约以及 T8 的本地模拟数据。
- 产出：可复核的测试、范围、浏览器、导出、迁移和收敛证据。

- [ ] **步骤 1：执行后端和迁移验证**

运行：`mvn -pl :ccb-test-management -am test`、`node scripts/check-flyway-migrations.mjs`

预期：模块测试和迁移检查通过。

- [ ] **步骤 2：执行治理、范围和构建检查**

运行：`node scripts/check-all-governance.mjs`、`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260903-063-test-management-report-analytics-refactor/codex-task-scope.yaml --base origin/main --head HEAD --working-tree`、`npm --prefix web run build`、`git diff --check`。

预期：全部命令退出码为 0。

- [ ] **步骤 3：真实浏览器验收**

以有权限与无权限账号分别验证报告生成、阈值启停、固定目录、自定义视图、钻取、历史读取和四种导出；在 1280×800、375×812、390×844、430×932 检查主操作、更多操作、弹层、主题、长文本及页面级横向溢出。

- [ ] **步骤 4：写入执行与观测证据**

仅把实际命令、退出码、截图/浏览器路径、偏差和风险写入本需求前缀账本；若发现偏差，返回对应任务而不标记收敛。

**回滚：** 回退应用代码；保留本需求追加迁移和业务数据，使用兼容版本读取。

**停止条件：** 迁移、权限、历史兼容或中文导出任一关键验收失败。

**升级条件：** 需要修改公共平台、共享组件或本任务范围外的路径。

## 集成检查

T7 完成后执行 Maven 模块测试、Flyway、治理、任务范围、前端构建、差异检查和浏览器验收；只有实际通过结果才能进入收敛验证。

## 控制模型种子

- 状态变量候选：V2 迁移状态、阈值生效状态、指标快照版本、报告版本、自定义配置共享状态、导出结果、模拟数据覆盖度。
- 传感器候选：模块测试、迁移检查、范围检查、前端构建、权限 API 探测、实际导出文件、浏览器视口检查、模拟数据分布查询。
- 执行器候选：本需求追加迁移、指标目录、服务端适配、前端领域页面、模拟数据脚本、回归测试。
- 扰动候选：历史 V1 脏字段、组织引用缺失、中文字体能力、本地主数据不足、现有数据量、用户并行修改。
- 以上均为 `hypotheses-only`，需由系统建模阶段验证。

## 风险与用户批准

高风险动作为追加数据库迁移、改动公开前端 API、服务端权限与审计、历史兼容和中文 PDF 输出。计划批准后才能导入闭环账本、进入基准与建模阶段；任何需要新增依赖、字体资源、公共组件或平台能力的情况均停止并升级范围。

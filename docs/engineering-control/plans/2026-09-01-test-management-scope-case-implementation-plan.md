# 测试管理：测试范围与测试案例实施计划

## 修订 2：当前执行包（模块 4）

| 执行包 | 写入边界 | 验证信号 |
| --- | --- | --- |
| R4-1 范围领域契约 | `scope/TestScopeService.java`、`TestScopeController.java` | 编译、模块测试、健康检查；目录/回收/排序/编码预览由服务端实体范围保护 |
| R4-2 XLSX 契约 | `ScopeCaseWorkbookService.java` | 模板/导入预检/提交接口的字段、上限和零写入规则；Flyway 连续性检查 |
| R4-3 紧凑工作台 | `api.ts`、`scope/TestScopePage.vue` | 生产构建；本地浏览器桌面与 375px 视口无控制台错误、无页面横向溢出 |

本轮不改 V140，也不触及案例、执行、缺陷、报告或平台公共组件。出现执行表接入、公共字典契约不兼容或需要跨模块表写入时停止并升级。

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-01-test-management-scope-case-design.md`
- 状态：可移交

## 目标与全局约束

交付按测试大类、项目和参测系统隔离的测试范围与测试案例能力：独立目录树、编号联动、导入导出、富文本附件、批量调整、服务端权限和审计。

- 仅追加 `V140__test_management_scope_case.sql`；不改 V137-V139、平台附件、预览、公共 UI、执行/缺陷/报告模块。
- 页面只消费已有全局项目上下文，不重复项目选择器；范围树和案例树相互独立、最多五层。
- 所有写端点验证认证、RBAC、租户/大类/项目/参测系统范围并记录审计。
- 执行模块尚未落地时，范围和案例状态安全返回“未执行”；不得生成伪执行数据。
- 使用现有 POI、AttachmentGateway、UiDataTable、UiFormDrawer、UiFilePreview 和公告板成熟富文本编辑器。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V140__test_management_scope_case.sql` | candidate-new | 范围/案例/目录/附件/审计表和权限种子 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/scope/TestScopeService.java` | candidate-new | 范围目录、范围、编号、状态、导入导出、审计 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/casework/TestCaseService.java` | candidate-new | 案例目录、案例、联动、批量、附件、审计 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/ScopeCaseWorkbookService.java` | candidate-new | 范围/案例模板、解析、预校验和导出 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/casework/TestCaseAttachmentPolicy.java` | candidate-new | 案例附件授权 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestScopeController.java` | candidate-new | 范围 API |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestCaseController.java` | candidate-new | 案例 API |
| `server/src/modules/test-management/src/test/java/com/ccb/testmanagement/scope/**` | candidate-new | 迁移、范围和工作簿测试 |
| `server/src/modules/test-management/src/test/java/com/ccb/testmanagement/casework/**` | candidate-new | 案例和附件策略测试 |
| `web/src/modules/test-management/api.ts` | existing | DTO 和 API 请求 |
| `web/src/modules/test-management/scope/TestScopePage.vue` | candidate-new | 范围页面 |
| `web/src/modules/test-management/casework/TestCasePage.vue` | candidate-new | 案例页面 |
| `web/src/modules/test-management/{scope,casework}/*.css` | candidate-new | 桌面/移动布局 |
| `web/src/router/index.ts` | existing | 四大类路由 |

## 任务依赖与策略

`T1 迁移与权限 → T2 范围后端 → T3 案例后端 → T4 范围前端 → T5 案例前端 → T6 集成验收`。

所有任务串行执行。范围是案例归属和编号的前置条件；前端共享 API、路由和运行环境，因此不并行写入。

## 需求覆盖

| 需求 | 覆盖任务 |
| --- | --- |
| SC1 | T1-T6 |
| SC2 | T1-T5 |
| SC3 | T2、T3、T6 |
| SC4 | T3、T5、T6 |
| SC5 | T1、T2、T3、T6 |

### T1：建立 V140 持久化边界和权限种子

**需求映射：** SC1、SC2、SC5；**前置任务：** 无。

**文件：** 新建 V140 迁移和 `scope/ScopeCaseSchemaTest.java`。

**接口：** 消费 `tm_test_participating_system`、`tm_test_dictionary`、`att_file`；产出范围/案例/目录/附件/审计表以及 `test-management:<domain>:scopes|cases` 权限。

1. 运行 `node scripts/check-flyway-migrations.mjs`，记录 V139 后的基准。
2. 先写 schema 断言：唯一编号、目录父引用、软删除字段、索引、权限种子。
3. 追加 V140：所有行带租户、大类、项目、系统和软删除；案例历史流水号不可复用。
4. 运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -pl :ccb-test-management -am test` 和迁移检查。

**验收：** V140 仅追加，迁移可应用，既有数据不变。
**回滚：** 回退应用代码，保留迁移数据。
**停止条件：** 迁移版本冲突或附件契约无法绑定。
**升级条件：** 必须修改 platform/attachment、公共 UI 或跨业务模块数据所有权。

### T2：实现范围、目录、导入导出和编号联动入口

**需求映射：** SC1、SC2、SC3、SC5；**前置任务：** T1。

**文件：** 新建 `scope/TestScopeService.java`、`web/TestScopeController.java`、`service/ScopeCaseWorkbookService.java`、范围服务与工作簿测试。

**接口：** 消费启用参测系统和四类范围字典；产出 `/api/test-management/scopes/{domain}` 的树、目录 CRUD、范围分页/详情/保存、置无效、删除、模板、预校验、提交和导出端点，以及供案例使用的原子编号同步契约。

1. 先写失败测试：非参测系统拒绝、目录五层、序号格式/唯一、零案例未执行、手工无效优先、目录删除移交、改号影响摘要。
2. 运行 `mvn -pl :ccb-test-management -am -Dtest=TestScopeServiceTest,ScopeCaseWorkbookServiceTest test`，保存失败基准。
3. 实施服务与 Controller；查询固定租户/域/项目过滤，保存校验启用字典，改号先返回影响预览再事务同步案例前缀，所有写入审计。
4. 实现 XLSX 模板、预校验、重复跳过/覆盖、自动建目录、导出；失败行禁止提交，覆盖仅更新非空字段。
5. 运行测试管理模块测试。

**验收：** 目录、范围、置无效、导入零写入和改号预览均有测试；认证边界可返回 401/403。
**回滚：** 回退 T2 Java 文件。
**停止条件：** 字典代码无法安全读取或编号迁移要求改缺陷编号。
**升级条件：** 需要配置模块新增公共契约。

### T3：实现案例、附件、批量调整和编号联动

**需求映射：** SC1、SC2、SC3、SC4、SC5；**前置任务：** T2。

**文件：** 新建 `casework/TestCaseService.java`、`casework/TestCaseAttachmentPolicy.java`、`web/TestCaseController.java` 和对应测试；修改 `ScopeCaseWorkbookService.java`。

**接口：** 消费 T2 范围契约、`AttachmentGateway` 与 `AttachmentAccessPolicy`；产出 `/api/test-management/cases/{domain}` 的树、案例 CRUD、目录移动、批量调整、核算核对、附件、导入导出 API。

1. 先写失败测试：范围不存在、编号不复用、换范围同步前缀、案例目录独立五层、执行引用删除阻断、附件跨租户拒绝、导入前缀不匹配零写入。
2. 实施案例服务：净化富文本，步骤/预期必填，附件绑定 `TEST_CASE`，核算独立更新，删除分支和批量预览/执行逐条审计。
3. 实施附件策略：案例未删除、同租户、拥有对应大类案例阅读权限才允许访问。
4. 实现案例模板、自动编号预览、范围存在验证、目录自动创建、纯文本富文本导入和全字段导出。
5. 运行 `mvn -pl :ccb-test-management -am test`。

**验收：** 编号、引用保护、附件授权和导入原子性有自动化证据。
**回滚：** 回退 T3 Java 文件；附件绑定和软删除数据保留。
**停止条件：** 需要案例直接访问平台附件表。
**升级条件：** 平台附件策略无法并存或执行模块要求不同状态契约。

### T4：实现范围前端紧凑工作台

**需求映射：** SC1、SC2、SC3、SC5；**前置任务：** T2。

**文件：** 修改 `api.ts`、`router/index.ts`；新建 `scope/TestScopePage.vue` 与 `scope/test-scope.css`。

**接口：** 消费 T2 API、全局项目 store、配置字典和 UiPageHeader/UiDataTable/UiFormDrawer/UiEmptyState；产出四大类范围路由。

1. 运行 `npm --prefix web run build` 记录基准。
2. 实现无项目空态、参测系统/范围目录树、筛选、服务端分页排序表格、移动卡片和紧凑图标操作。
3. 实现目录操作、范围抽屉、改号确认、无效/删除确认、模板下载、预校验导入、导出和按用户/域/项目键本地保存的列设置。
4. 复用交付示范中心通用表格的排序/列宽能力；移动端树上置并让列表卡片化。
5. 运行生产构建并在浏览器检查无项目、空树、创建、改号、导入失败、长文本路径。

**验收：** 无第二个项目选择器；范围覆盖数跳转案例带范围筛选；375px 无页面横滚。
**回滚：** 移除范围路由和页面。
**停止条件：** 必须修改公共 UI 才可实现所需表格能力。
**升级条件：** 需要改动全局项目上下文。

### T5：实现案例前端完整工作台

**需求映射：** SC1、SC2、SC3、SC4、SC5；**前置任务：** T3、T4。

**文件：** 修改 `api.ts`、`router/index.ts`；新建 `casework/TestCasePage.vue` 与 `casework/test-case.css`。

**接口：** 消费 T3 案例 API、T2 范围选择 API、统一附件 API、公告板成熟富文本编辑器和全局项目 store；产出四大类案例路由。

1. 实现独立案例目录树、筛选、选择行、服务端分页排序表格、移动卡片、列设置和范围筛选恢复。
2. 实现 640px 三段抽屉：基本信息、成熟富文本与附件、独立核算核对；范围切换刷新只读系统与编号联动确认。
3. 实现目录移动、删除/置无效分支、核算行内更新、批量调整五步向导、XLSX 导入导出和附件预览/下载图标。
4. 处理加载、失败、无权限、提交中、空结果和未保存关闭确认，不新增手写编辑器。
5. 运行构建与桌面/手机浏览器路径。

**验收：** 案例编号只读；附件走统一能力；移动端低频操作收进更多菜单。
**回滚：** 移除案例路由和页面。
**停止条件：** 成熟编辑器的图片上传/净化链路不能复用。
**升级条件：** 需要新增公共依赖或修改 UiFilePreview。

### T6：集成、运行与浏览器验收

**需求映射：** SC1、SC2、SC3、SC4、SC5；**前置任务：** T1-T5。

**文件：** 修改当前前缀 `.ai-control/requirements/req-20260831-057-test-management-configuration/*.json`，仅记录真实证据。

1. 运行 `mvn -pl :ccb-test-management -am test`、`npm --prefix web run build`、`node scripts/check-flyway-migrations.mjs`、`node scripts/check-all-governance.mjs`、`git diff --check`。
2. 在本地 MySQL 应用 V140；以管理员完成范围/案例 CRUD、改范围序号、改案例范围、上传附件、导入导出并读取审计。
3. 采样未认证、无权限、跨项目和附件访问边界；验证 401/403 与导入零写入。
4. 在 1280px、375x812、390x844、430x932 检查主流程、空/失败、长编号、弹层可达、浅深主题、控制台和页面级横向溢出。
5. 写入 execution、observation 与收敛证据；不把范围外根级 architecture 编译问题归因为本功能。

**验收：** SC1-SC5 均同时有自动化和运行/浏览器证据，范围审计无意外代码路径。
**回滚：** 回退本功能代码和路由，保留 V140 数据。
**停止条件：** V140 失败、跨租户泄露或浏览器阻断错误。
**升级条件：** 验收需要生产数据、外部系统或范围外代码。

## 集成检查

`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -pl :ccb-test-management -am test`；`npm --prefix web run build`；`node scripts/check-flyway-migrations.mjs`；`node scripts/check-all-governance.mjs`；`git diff --check`。

预期各检查通过；根级 `ccb-boot` 如仍受 architecture 既有错误阻断，只记录外部基线，不修改范围外代码。

## 控制模型种子

以下均为 `hypotheses-only`：被控边界为 V140、范围/案例服务/控制器/工作簿/附件策略、Vue 页面和本地基础设施；状态变量为编号唯一性、前缀一致性、目录深度、软删除/无效、导入事务、附件授权；传感器为测试、Flyway、API、审计查询、构建和浏览器；扰动为未提交改动、执行模块缺失、并发导入、MinIO/kkFileView 与根级 architecture 基线错误。

## 风险与用户批准

高风险动作是 V140、范围/案例编号批量联动、附件绑定和全量导入事务。计划以影响预览、二次校验、单事务和审计限幅。用户已确认“开始实施”，交接包可导入控制闭环。

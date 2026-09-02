# 测试方案实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：[测试方案工程设计](../designs/2026-09-01-test-management-plan-design.md)
- 状态：可移交；用户已确认开始实施。

**目标：** 在四个测试大类中提供可隔离、可版本化、可预览下载的测试方案管理。

**架构：** 测试管理模块拥有专项节点、逻辑方案、方案版本及审计；平台附件仍只负责文件与授权 URL。后端用方案/版本分离保证同名递增及整方案软删除，前端在全局项目上下文内渲染树、双 TAB 和紧凑通用列表。

**技术栈：** Spring Boot 3、JdbcTemplate、MySQL/Flyway、Vue 3、TypeScript、Element Plus、现有 `xlsx`、平台附件公开契约。

## 全局约束

- 仅修改 REQ-20260831-057 的 `writable_paths`；新增迁移只能是 V125，不能修改 V123/V124 或平台附件/公共 UI。
- 所有方案记录按 tenant、测试大类、项目与节点隔离；项目和物理子系统只读，系统节点仅取已启用参测系统。
- 文件只允许 `.docx/.xlsx`、最大 50MB；同名版本写入前必须由用户确认；历史版本不提供删除接口。
- 读权限使用现有 `test-management:<domain>:plans`，写操作由服务端 `create/update/delete` 子权限保护并写审计。
- 删除方案只软删除，以支持后续 30 天回收站恢复；本任务不创建回收站页面或修改平台附件文件。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V125__test_management_plan.sql` | candidate-new | 方案、版本、专项节点、审计表及细粒度权限种子 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/plan/TestPlanService.java` | candidate-new | 树、专项节点、方案与版本的租户隔离业务规则 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/plan/TestPlanAttachmentPolicy.java` | candidate-new | 方案版本附件统一访问校验 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestPlanController.java` | candidate-new | 受保护 REST 适配层 |
| `server/src/modules/test-management/src/test/java/com/ccb/testmanagement/plan/TestPlanServiceTest.java` | candidate-new | 隔离、版本、节点、删除和附件策略规则测试 |
| `web/src/modules/test-management/api.ts` | existing | 测试方案 TypeScript 类型与 HTTP 调用 |
| `web/src/modules/test-management/plan/TestPlanPage.vue` | candidate-new | 树、双 TAB、上传、版本历史与预览交互 |
| `web/src/modules/test-management/plan/test-plan.css` | candidate-new | 方案模块主题、桌面分栏与移动布局 |
| `web/src/router/index.ts` | existing | 将 plans 路由从占位列表替换为专属页面 |

## 任务依赖图与并行策略

`T1 迁移和领域服务 → T2 REST/权限与附件策略 → T3 页面和路由 → T4 集成验证`。任务共享迁移、API 契约与本地运行环境，全部串行。

## 需求覆盖表

| 需求 | 任务 | 传感器 |
| --- | --- | --- |
| TP1 树与隔离 | T1、T2、T3、T4 | 服务测试、API 冒烟、浏览器 |
| TP2 版本与软删除 | T1、T2、T3、T4 | 服务测试、API 冒烟、浏览器 |
| TP3 文件与预览 | T2、T3、T4 | 格式测试、前端构建、浏览器 |
| TP4 权限与审计 | T1、T2、T4 | 服务测试、未授权 API 探针 |

### T1：持久化模型与版本规则

**需求映射：** TP1、TP2、TP4

**前置任务：** 无

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V125__test_management_plan.sql`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/plan/TestPlanService.java`
- 新建：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/plan/TestPlanServiceTest.java`

**接口：**

- 消费：`tm_test_participating_system` 已启用参测系统、`pm_project` 只读项目、`AttachmentGateway` 公开绑定接口。
- 产出：方案树、专项节点 CRUD、节点方案列表、当前方案、版本历史、上传预检/确认、删除服务方法。

1. 建立迁移基线检查：运行 `node scripts/check-flyway-migrations.mjs`，记录新增 V125 前的结果。
2. 追加 V125：建立专项节点、逻辑方案、版本和审计表；创建 `(tenant,domain,project,node,name,deleted)` 查询索引，向四个 plans 菜单追加 create/update/delete 权限并只授予初始管理员。
3. 建立失败测试：断言未参测系统不能成为节点、同节点同名未经确认不写新版本、确认后版本从 V1 到 V2、存在方案的专项节点不能删除、删除后查询不可见。
4. 实现 `TestPlanService`：服务端验证 domain/project/node/附件/格式/大小/版本说明；所有写入携带认证用户、审计和事务；删除仅写删除状态与时间。
5. 运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -pl :ccb-test-management -am test -Dtest=TestPlanServiceTest`，预期测试通过；再运行 Flyway 检查。

**验收、证据与回滚：** 保存迁移与测试输出；可回退 T1 应用代码，已执行 V125 和软删除数据保留。

**停止条件：** 附件绑定必须修改平台私有表或系统节点无受支持的参测系统数据源。

**升级条件：** 现有菜单权限种子与已确认测试经理权限矩阵冲突。

### T2：受保护 API 与附件访问策略

**需求映射：** TP1、TP2、TP3、TP4

**前置任务：** T1

**文件：**

- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestPlanController.java`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/plan/TestPlanAttachmentPolicy.java`
- 修改：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/plan/TestPlanServiceTest.java`

**接口：**

- 消费：T1 `TestPlanService`、`AttachmentAccessPolicy` 和 `AttachmentOperation` 公共接口。
- 产出：`/api/test-management/plans/{domain}` 的树、列表、当前、专项节点、上传、历史版本、删除接口；业务类型 `TEST_PLAN_VERSION` 的附件访问策略。

1. 建立控制器/策略失败测试：未认证或仅阅读权限用户调用写端点得到拒绝；删除方案后同版本附件预览/下载被拒绝。
2. 按 HTTP 语义实现读取、专项节点 CRUD、上传、上传新版本、版本历史及整方案删除端点；查询统一返回 `ApiResponse`。
3. 为上传接口只接收平台已上传的附件 ID，服务端再次验证文件扩展名、大小、临时归属并以 `AttachmentGateway.bind` 绑定版本。
4. 实现访问策略：校验用户启用、租户、版本所属方案未删除；不直接操作 `att_file`。
5. 运行目标 Maven 测试与本地认证 API 冒烟；预期管理员读写成功、无权限写入 403、跨项目 ID 返回业务拒绝。

**验收、证据与回滚：** 保存 API 探针及测试输出；回退 T2 删除端点和策略注册，V125 数据保持。

**停止条件：** 预览/下载需要修改平台 attachment 公共接口。

**升级条件：** 平台策略注册发现重复业务类型或无法区分已删除方案。

### T3：方案树、双 TAB 与版本化页面

**需求映射：** TP1、TP2、TP3

**前置任务：** T2

**文件：**

- 修改：`web/src/modules/test-management/api.ts`
- 新建：`web/src/modules/test-management/plan/TestPlanPage.vue`
- 新建：`web/src/modules/test-management/plan/test-plan.css`
- 修改：`web/src/router/index.ts`

**接口：**

- 消费：T2 REST 端点，现有 `useProjectContextStore`、`UiPageHeader`、`UiDataTable`、`UiFilePreview`、附件上传/预览/下载 API、`xlsx`。
- 产出：`/test-management/:domain/plans` 的真实页面，取代会话级占位列表。

1. 在 `api.ts` 写入树、方案、版本、专项节点和上传 DTO；以精确 HTTP 方法绑定 T2 路由。
2. 实现桌面两栏页面：左树 260px，右侧交付示范中心式双 TAB；未选项目、加载、空节点、无权限和请求失败均有独立状态。
3. 实现方案管理表：复用 `UiDataTable`，更新时间倒序，支持排序、拖拽列宽、紧凑图标操作；按权限显示上传、上传版本、删除与专项维护。
4. 实现上传/版本/专项弹窗：文件默认名、节点锁定级别、版本说明、同名确认、提交中防重和未保存确认。
5. 实现预览：docx 调用 `UiFilePreview`；xlsx 用 `xlsx` 读取受控下载 URL 并渲染首 Sheet，多 Sheet 显示下载提示；解析失败保留下载按钮并显示错误。
6. 配置路由懒加载，运行 `npm --prefix web run build`；预期 TypeScript 与 Vite 均通过。

**验收、证据与回滚：** 保存构建输出和页面 diff；回退 T3 即恢复既有 `TestManagementList` 占位页面。

**停止条件：** 页面必须修改 `web/src/components/ui`、全局项目选择器或主题基础设施。

**升级条件：** 平台附件预览 URL 无法在 iframe 或浏览器下载中使用。

### T4：集成、权限与浏览器验收

**需求映射：** TP1、TP2、TP3、TP4

**前置任务：** T1、T2、T3

**文件：**

- 修改：`.ai-control/requirements/req-20260831-057-test-management-configuration/execution-TP*.json`
- 修改：`.ai-control/requirements/req-20260831-057-test-management-configuration/observation-TP*.json`

**接口：**

- 消费：完整 T1-T3 实现与本地 MySQL/MinIO/前后端运行环境。
- 产出：可复查的构建、测试、API 和浏览器证据。

1. 运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -pl :ccb-test-management -am test`、`npm --prefix web run build`、`node scripts/check-flyway-migrations.mjs`、`node scripts/check-all-governance.mjs` 和 `git diff --check`。
2. 以管理员在一个项目中验证树、专项创建、docx/xlsx 上传、同名 V2、当前方案、历史版本、下载和整方案删除。
3. 以无写权限用户验证读可用而写端点 403；验证跨大类/跨项目 ID 不能读取方案或附件。
4. 在桌面和 375px 手机视口验证无项目、空节点、树选择、双 TAB、长名称、上传弹窗和表格横向滚动边界；记录控制台和网络失败。
5. 将真实命令、退出码、页面路径、观察结论和剩余风险写入当前前缀账本。

**验收、证据与回滚：** 所有命令和浏览器路径有实际结果；发现越权、跨域读取、版本错误或页面级溢出时停止交付并回到观察/纠偏。

**停止条件：** 任一方案版本可被无权用户下载，或迁移/审计数据发生跨租户写入。

**升级条件：** 本地 MinIO/预览服务不可达而无法判定预览能力。

## 集成检查

按 T4 顺序执行后端测试、前端构建、Flyway、治理、差异、健康接口、管理员与未授权 API 以及桌面/手机浏览器路径。任何检查未运行或失败均如实记录，不标记完成。

## 控制模型种子

- 被控边界候选：测试方案表、方案服务/控制器、附件策略、测试管理方案页面与路由。
- 状态变量候选：domain、projectId、节点类型/ID、专项节点、方案、最新版本、附件绑定与逻辑删除状态。
- 传感器候选：JDK 17 服务测试、Flyway/治理检查、认证 API 探针、Vite 构建、真实浏览器桌面与手机验收。
- 执行器候选：V125、方案服务、访问策略、控制器、专属前端页面与 API DTO。
- 扰动候选：全局项目选择器状态、参测系统配置为空、MinIO/kkFileView 不可达、用户并行改动。

## 风险与用户批准

用户已经确认在当前分支实施本计划所描述的测试方案模块。高风险动作仅为追加 V125、权限种子和平台附件公开契约的消费；不修改平台实现。

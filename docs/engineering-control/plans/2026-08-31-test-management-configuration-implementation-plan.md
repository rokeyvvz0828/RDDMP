# 测试管理：管理配置实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 交付四大测试大类隔离的参测系统、系统角色、轮次周期和字典配置前后端能力。

**架构：** 在测试管理模块新增配置服务、控制器和 XLSX 工作簿服务，以 V137 创建配置表及菜单权限。前端在既有动态测试大类路由之前注册专属管理配置页面，复用项目 API、UI 组件和 HTTP 错误处理。项目、用户和物理子系统仅被读取；当前每项目候选为所有启用物理子系统。

**技术栈：** Java 17、Spring Boot、JdbcTemplate、MySQL 8.4/Flyway、Apache POI、Vue 3、TypeScript、Element Plus。

## 全局约束

- 只修改 `REQ-20260831-057` 范围文件；不访问生产环境或敏感数据。
- 全部写接口执行认证、RBAC、租户、测试大类、项目和参测系统范围校验，并写操作审计。
- V137 仅追加；不改 V80/V81、营业日表或平台主数据。
- 所有测试命令显式使用 JDK 17：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`。
- 通过管理配置页的项目选择器提供当前模块上下文；全局项目选择器留给后续平台主框架模块。

### T1：配置持久化、菜单与受保护 API

**需求映射：** R1、R2、R3、R5

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V137__test_management_configuration.sql`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/configuration/TestConfigurationService.java`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/configuration/TestConfigurationModels.java`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestConfigurationController.java`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/configuration/TestConfigurationServiceTest.java`

**接口：**
- 消费：认证 `AuthUser`、平台用户目录、项目只读 API 与物理子系统只读 HTTP 候选。
- 产出：`/api/test-management/configuration/{domain}/systems|roles|rounds|cycles|dictionaries` 的分页、详情与写入契约。

- [ ] 建立服务测试，覆盖大类/项目隔离、未参测系统角色拒绝、轮次日期校验、字典引用删除拒绝和未授权写入。
- [ ] 运行：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -pl :ccb-test-management -am test`；预期：现有测试通过，新增测试先失败。
- [ ] 追加 V137：创建配置表、唯一索引、初始本地字典、管理配置菜单及读写权限；菜单只授予初始管理员。
- [ ] 实现服务和控制器：以 `domain + tenant + project` 作为所有配置查询与写入范围，系统候选为启用物理子系统，写入调用现有 `SystemOperationAudit`。
- [ ] 重跑同一 Maven 命令；预期：所有测试通过。

**回滚：** 回退控制器、服务和菜单入口；V137 与数据保留。

**停止条件：** 物理子系统查询无法以当前认证身份读取，或现有项目 API 无法提供项目范围。

**升级条件：** 需要修改平台/架构公开契约或存量 V80/V81 时，先请求新增授权。

### T2：导入预校验与原子提交

**需求映射：** R4、R6

**前置任务：** T1

**文件：**
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/configuration/TestConfigurationWorkbookService.java`
- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestConfigurationController.java`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/configuration/TestConfigurationWorkbookServiceTest.java`

**接口：**
- 消费：T1 系统、角色与字典服务。
- 产出：系统/角色模板下载、预校验和确认导入接口；预校验结果含总数、成功、失败、重复、新建目录清单（本模块为空）与行错误。

- [ ] 建立工作簿测试：错误行、重复系统行覆盖、重复角色行合并、空字段保留，以及错误批次零写入。
- [ ] 运行 Maven 聚焦测试，确认新增测试失败。
- [ ] 使用既有 Apache POI 生成模板并实现解析、逐行验证和事务提交；不新增依赖。
- [ ] 重跑测试；预期：模板、预校验和原子性断言通过。

**回滚：** 回退工作簿服务和导入端点；已导入数据由管理界面修正或停用，不删除迁移。

**停止条件：** 导入格式无法表达多角色或平台用户查验需要未授权主数据写入。

**升级条件：** 需要上传真实用户信息、生产 XLSX 或外部存储时停止并请求数据边界确认。

### T3：管理配置前端页面与路由

**需求映射：** R1、R2、R3、R5、R6

**前置任务：** T1、T2

**文件：**
- 新建：`web/src/modules/test-management/configuration/TestConfigurationPage.vue`
- 新建：`web/src/modules/test-management/configuration/test-configuration.css`
- 修改：`web/src/modules/test-management/api.ts`
- 修改：`web/src/modules/test-management/catalog.ts`
- 修改：`web/src/modules/test-management/application-assembly/catalog.ts`
- 修改：`web/src/modules/test-management/user-testing/catalog.ts`
- 修改：`web/src/modules/test-management/non-functional/catalog.ts`
- 修改：`web/src/modules/test-management/security/catalog.ts`
- 修改：`web/src/router/index.ts`

**接口：**
- 消费：T1/T2 配置 API、`web/src/api/project.ts` 项目选项和既有 `UiPageHeader`、`UiToolbar`、`UiDataTable`、`UiFormDrawer`。
- 产出：四大类共用的管理配置路由和四个配置子视图。

- [ ] 先注册静态 `test-management/:domain/configuration` 路由，确保它位于通用 `:domain/:section` 路由之前；将各大类目录加入“管理配置”叶子菜单。
- [ ] 实现参测系统子页：项目选择、候选系统、确认停用、导入与加载/空/失败状态。
- [ ] 实现角色、轮次周期、字典子页：树/列表、抽屉或对话框、禁用原因、提交中状态和错误恢复。
- [ ] 增加响应式样式：窄屏纵向呈现树、筛选、表格卡片和全屏抽屉；不产生整页横向滚动。
- [ ] 运行：`npm --prefix web run build`；预期：类型检查和 Vite 构建成功。

**回滚：** 回退专属路由和页面，保留动态占位路由；后端配置数据保持。

**停止条件：** 页面需要修改公共 UI 组件或 AppLayout 才能工作。

**升级条件：** 需要全局项目选择器时，转入平台主框架需求，不能在本任务夹带修改。

### T4：集成验证、权限与浏览器验收

**需求映射：** R1—R6

**前置任务：** T1、T2、T3

**文件：**
- 修改：`.ai-control/requirements/req-20260831-057-test-management-configuration/execution-T1.json`
- 修改：`.ai-control/requirements/req-20260831-057-test-management-configuration/observation-T1.json`
- 修改：`.ai-control/requirements/req-20260831-057-test-management-configuration/convergence.json`

**接口：**
- 消费：完整配置页面和 API。
- 产出：可复现的自动化、API、桌面/移动端证据与残余风险。

- [ ] 运行后端、前端、Flyway、治理和差异检查，记录实际退出码。
- [ ] 使用管理员验证项目选择、系统开关、角色分配、轮次周期、字典和导入；使用无权限账号验证接口 403 与页面无入口。
- [ ] 在桌面和手机视口验证抽屉、表格卡片、错误、空态、长文本和重复点击。
- [ ] 将实际命令、结果、偏差与回退结论写入当前任务前缀账本。

**回滚：** 仅回退本需求代码和菜单入口；保留配置数据与 V137。

**停止条件：** 任一写接口越权、跨大类读取、导入部分写入或浏览器出现不可达操作。

**升级条件：** 发现架构关系或平台权限语义与已确认设计冲突时，返回设计确认。

## 任务依赖与风险

T1 → T2 → T3 → T4 串行，避免数据库/API/页面契约漂移。高风险动作为 V137 迁移、菜单权限和后续模块将依赖的配置数据；应用回退不删除迁移或配置行。

---

## 修订 2：跨模块本地模拟数据实施计划（待确认）

> 执行要求：使用 `$control-engineering` 逐任务实施；本计划只生成本地脱敏模拟数据，不改变任何已有产品接口、迁移或公共能力。

**目标：** 为“平台能力升级项目”生成可重复、可关联、能驱动全量测试管理页面与分析统计的本地脱敏数据。

**架构：** 新增一个显式执行的测试管理本地种子工具和脱敏模板。工具通过现有测试管理服务及附件公开能力写入数据和审计；它按 `【模拟】`/`DEMO` 标识查找已有行，仅重建模拟数据，禁止清空未标识记录。

**技术栈：** Java 17、Spring Boot、既有测试管理服务、JdbcTemplate 只读发现、平台附件上传能力、MySQL 本地库。

### 全局约束

- 只在本地运行，目标项目名必须精确匹配“平台能力升级”；未匹配时失败，不选择其他项目。
- 不写入真实个人信息、凭证或外部链接；人员字段只引用本地已有演示用户。
- 附件服务不可用时方案模块失败并停止后续报告生成，绝不写无效附件 ID。
- 应用组装覆盖全部系统；其他大类按二分之一、五分之一缩放并保留页面可用最小量。

### 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/mock/TestManagementLocalMockSeeder.java` | candidate-new | 显式本地种子入口、项目/系统发现、幂等清理与模块执行摘要。 |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/mock/TestManagementMockTemplates.java` | candidate-new | 脱敏公告 HTML、范围、案例、执行、缺陷、报告命名模板。 |
| `server/src/modules/test-management/src/test/java/com/ccb/testmanagement/mock/TestManagementLocalMockSeederTest.java` | candidate-new | 规模、关联、二次运行幂等和非模拟数据保护测试。 |
| `docs/engineering-control/designs/2026-08-31-test-management-configuration-design.md` | existing | 本次数据规则与边界。 |
| `.ai-control/requirements/req-20260831-057-test-management-configuration/mock-data-*.json` | candidate-new | 计划、执行和观测证据。 |

### 任务依赖图与并行策略

`MD-T1 本地发现与模拟边界 → MD-T2 配置/公告/方案 → MD-T3 范围/案例/执行/缺陷 → MD-T4 报告/统计 → MD-T5 重跑与浏览器观测`。任务串行：后续模块均消费前一模块的真实 ID，禁止并行写同一项目。

### MD-T1：本地发现、标识与前置检查

**需求映射：** MD1、MD3。
**前置任务：** 无。
**接口：** 消费 `pm_project`、当前物理子系统和本地演示用户只读信息；产出项目、系统、用户和附件服务前置检查结果。
**步骤：**

1. 先在本地只读查询中验证唯一项目、至少一个系统和可用演示用户；记录系统数作为各领域缩放基数。
2. 建立种子测试，断言找不到项目、无系统、无用户或附件服务不可用时零业务写入。
3. 实现 `DEMO` 标识选择与依赖逆序的模拟数据重建；限定删除条件为种子标识，禁止按项目全量删除。
4. 运行聚焦测试；预期：前置失败可判别、非模拟记录数不变。

**验收与证据：** 记录运行前项目/系统/非模拟记录计数及零写入失败路径。
**回滚：** 仅运行种子提供的模拟数据清理，不影响未标识数据。
**停止/升级条件：** 项目名称不唯一、没有系统或必须删除未标识记录时停止并请求用户决定。

### MD-T2：配置、公告、方案及真实示例附件

**需求映射：** MD1、MD2、MD3。
**前置任务：** MD-T1。
**接口：** 消费前置项目/系统/用户和附件上传结果；产出四大类配置、轮次周期、富文本公告、项目/系统方案及版本。
**步骤：**

1. 为应用组装启用全部系统，建立 2 个轮次和每轮 2–3 周期；缩放域建立对应最小可用轮次周期。
2. 写入应用组装 10 条（2 条置顶）及缩放域公告，使用既有安全富文本字段。
3. 上传脱敏本地示例附件，取得真实附件 ID 后建立项目级、半数系统级方案；首个系统写两个版本。
4. 断言方案版本数、公告置顶数、附件可下载元数据和重复运行后记录数稳定。

**回滚：** 清理 `【模拟】` 公告/方案及其关联模拟数据；不删除附件物理对象以外的非模拟文件。
**停止/升级条件：** 附件上传或访问失败时停止，修复本地存储后重试。

### MD-T3：范围、案例、执行与缺陷闭环

**需求映射：** MD1、MD2、MD3。
**前置任务：** MD-T2。
**接口：** 消费参测系统、轮次周期和方案上下文；产出全字段范围、案例、每周期 5–20 条执行记录、缺陷与执行关联。
**步骤：**

1. 按系统创建“模拟功能/联机/批处理”目录、5 条全字段范围和每范围 6 条带富文本步骤的案例；缩放域按比例下取并保留最小值。
2. 每周期选择 12 条（缩放域至少 5 条）案例导入执行，覆盖成功、失败、阻塞、执行中和未执行状态。
3. 创建 `max(10, 应用组装系统数)` 条缺陷，逐系统覆盖；为失败执行建立 1–2 条关联。
4. 断言范围→案例、失败执行→缺陷、缺陷→执行反向查询均非空且所属项目/系统一致。

**回滚：** 按缺陷关联、执行、案例、范围的逆序清理模拟行。
**停止/升级条件：** 任一服务端边界校验拒绝正常模拟关联，或循环清理会触及未标识数据时停止。

### MD-T4：报告生成与统计归档

**需求映射：** MD2。
**前置任务：** MD-T3。
**接口：** 消费真实范围/案例/执行/缺陷；产出项目级、系统级报告版本和每个完成轮次四类统计快照。
**步骤：**

1. 使用现有报告服务生成应用组装 1 条项目级和向上取整 50% 系统级报告；缩放域生成项目级及至少一个系统级报告。
2. 调用既有统计预置和轮次归档服务，保存范围覆盖、执行进度、缺陷分布、人员工作量快照。
3. 比对报告快照和实时统计关键计数与前一任务写入记录。

**回滚：** 删除带模拟标识的报告、版本、补充、轨迹和统计快照。
**停止/升级条件：** 报告/统计服务无法消费同一项目数据时停止并保留模块级摘要。

### MD-T5：重复运行、API 和浏览器观测

**需求映射：** MD1、MD2、MD3。
**前置任务：** MD-T4。
**接口：** 消费所有种子摘要；产出二次运行对比、API 计数、页面路径和残余风险记录。
**步骤：**

1. 记录首次运行后每个大类、模块、模拟标识和未标识记录计数。
2. 第二次运行种子，对比模拟计数稳定、未标识计数不变。
3. 使用本地 API 与浏览器只读检查应用组装公告、方案、范围、案例、执行、缺陷、报告、统计；确认至少一条失败案例展示关联缺陷。
4. 运行测试管理模块测试、前端构建和差异检查，记录实际结果。

**回滚：** 执行 MD-T1 的模拟清理；保留命令与计数证据。
**停止/升级条件：** 二次运行产生未标识数据变化、关键详情为空或浏览器/API 存在 5xx 时停止并纠偏。

### 集成检查与风险

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -pl :ccb-test-management -am test`
- `npm --prefix web run build`
- `git diff --check`
- 本地 API/浏览器只读验证和前后两次按标识计数对比。

主要风险为附件服务、演示用户和本地项目主数据缺失；均以阻断和可重试摘要处理，不写替代假数据。

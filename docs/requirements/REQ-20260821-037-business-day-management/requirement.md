---
id: REQ-20260821-037
status: ready
owner: rokeyvvz0828
module: business/test-management
---

# 营业日管理全功能迁移

## 业务目标

测试管理人员能够在 RDDMP 内维护测试环境、营业日日历安排和跑批需求，并通过月历快速查看各测试环境的营业日与跑批状态。功能参考 RADAR 现有营业日管理，但遵循 RDDMP 的模块、权限、用户、审计和界面规约。

## 范围

### 本次实施

- Sidebar 在“测试管理”下只保留一个二级“营业日管理”入口；日历概览、日历安排、跑批需求、测试环境管理改为页面顶部导航，并支持 URL 深链接。
- 测试环境支持分页查询、新增、编辑、启停、排序和受引用保护删除；环境编码变化时同步更新关联日历与跑批需求。
- 日历安排支持环境、日期、跑批标志和跑批类型筛选，新增、编辑、同环境同自然日覆盖、删除、模板下载、XLSX 批量导入和 XLSX 导出。
- 跑批需求支持关键词、月份/日期、环境、采纳状态筛选，新增、编辑、删除、采纳/不采纳和 XLSX 导出。
- 日历概览支持环境与月份切换、今天定位、42 格月历、日详情，以及可复制的当前视图 URL。
- 日历安排导入兼容 RADAR 的日期输入：Excel 日期单元格、Excel 日期序列、`YYYYMMDD`、`YYYY-M-D`、斜杠/点分隔日期及中文年月日；导入模板采用 RADAR 的 8 列表头和两类示例行，同时继续兼容已下载的 RDDMP 旧版模板表头。
- 日历概览取消摘要卡片和常驻右侧详情，按 RADAR 的“月份/今天 + 环境按钮/分享 + 七列月历”结构展示；环境行显示环境、营业日期、营业星期和跑批摘要，点击后以 RADAR 信息层级打开当日日历安排弹窗。
- “涉及系统”使用可创建标签的手工输入，后端统一去空、去重并限制数量和长度。
- “提出人”从 RDDMP 当前租户的有效用户管理数据中选择；通过公开只读用户目录契约读取，不直接访问系统用户表。
- 所有写操作记录业务审计，后端执行租户隔离、权限校验、字段校验和冲突保护。
- 覆盖加载、空、错误、无权限、提交中、删除确认、响应式列表/卡片等完整界面状态。

### 本次不实施

- 不迁移 RADAR 的动态输入项配置、应用列表和独立“环境信息”模块。
- 不接入外部系统目录；涉及系统暂不校验真实性。
- 不把跑批需求采纳自动同步为日历安排；采纳只记录评审结论。
- 不建设流程审批、消息通知、附件或生产环境数据迁移。

## 现状与规则

- 当前营业日管理是 V51 创建的二级目录，下面有四个 Sidebar 三级占位菜单，页面仅有会话内存 CRUD。
- 测试环境编码在租户内唯一；已停用环境可展示历史数据，但不能用于新增安排或需求。
- 同一环境、同一自然日最多一条日历安排；手工保存和导入均按该自然键覆盖。
- 删除环境前必须确认没有未删除的日历安排或跑批需求引用。
- 跑批需求自然日期接受 `YYYY-MM` 或 `YYYY-MM-DD`；采纳状态为待定、采纳、不采纳。
- 日期与时间按 Asia/Shanghai 业务语义显示；数据库审计时间使用服务器时间。

## 接口与数据

- API 根路径：`/api/test-management/business-days`。
- 数据 Owner：`business/test-management` 拥有 `tm_test_environment`、`tm_calendar_schedule`、`tm_batch_requirement`、`tm_business_day_audit`。
- 跨模块契约：`platform/system` 提供 `UserDirectoryPort` 和只读 `UserDirectoryItem`，只返回当前租户有效用户的必要展示字段。
- 数据库迁移：只追加 V52；将 V51 的四个营业日三级菜单停用并把 614 调整为叶子入口，同时创建业务表和操作权限。
- 导入导出：XLSX 仅处理业务字段，不导出内部租户、删除标记和审计主键；导入限制文件大小和行数。

## 验收标准

1. Sidebar 只显示一个“营业日管理”二级入口，进入后顶部显示四个导航项，刷新和复制 URL 后仍保持当前视图。
2. 测试环境、日历安排、跑批需求的新增、查询、编辑、删除/评审均持久化，刷新后数据不丢失，权限和租户隔离由后端强制执行。
3. 已停用环境不能用于新增；被引用环境不能删除；环境编码修改能同步关联记录；同环境同自然日保存时覆盖且给出明确反馈。
4. 提出人只能选择 RDDMP 当前租户的有效用户并展示姓名、组织、手机号；涉及系统可手工输入多个标签。
5. 月历固定为 42 格，可按环境和月份浏览并查看日详情；模板、日历导入/导出和需求导出均可用 XLSX 打开。
6. 桌面和 375x812、390x844、430x932 视口无页面级横向溢出；完整状态与键盘操作可达。
7. Maven 测试、前端构建、Flyway、治理/范围检查及真实浏览器业务闭环均有实际证据。
8. RADAR 模板可直接用于导入，日期兼容样例均规范化为 `YYYY-MM-DD`/`YYYYMMDD`；概览无三个摘要卡片，桌面月历和当日详情弹窗的信息顺序与 RADAR 一致。

## 测试与发布

- 必须执行：后端单元/集成测试、完整 Maven 测试、Vue 类型检查与生产构建、Flyway 和治理检查、任务范围检查、API 权限/冲突/导入导出测试、真实浏览器桌面与移动端验收。
- 上线验证：管理员重新登录，确认菜单结构；依次新建环境、日历安排、跑批需求，完成采纳、月历查看、导出和引用保护删除。
- 回退：回退需求提交；V52 已应用时通过后续补偿迁移停用营业日能力并恢复菜单，不修改 V52，不直接删除业务数据。
- 风险：公开用户目录契约、数据库迁移、权限和批量文件处理属于高风险变更，由 `rokeyvvz0828` 复核。

## 补充设计：RADAR 跑批字段联动规则

### 文档状态

- 修订：2
- 状态：已确认
- 用户确认依据：用户确认按 RADAR 复刻并明确“跑批类型选择翻数时，不校验后续字段必填”；随后要求两个业务列表拆分测试环境、自然日期、营业日期，并统一营业日桌面列表居中。

### 目标与成功信号

日历安排、跑批需求、手工接口和日历导入使用同一套跑批字段矩阵。选择“翻数”后可以不填写跑批时间、涉及系统和验证内容；其他跑批类型不能借助前端显隐或接口参数绕过必填规则。

### 字段矩阵

| 场景 | 跑批类型 | 跑批时间 | 涉及系统 | 验证内容 |
| --- | --- | --- | --- | --- |
| 日历安排关闭跑批 | 清空并忽略 | 清空并忽略 | 清空并忽略 | 清空并忽略 |
| 日历安排开启跑批 | 必填 | 按类型联动 | 按类型联动 | 按类型联动 |
| 跑批需求 | 必填；固定为跑批，不显示“是否跑批” | 按类型联动 | 按类型联动 | 按类型联动 |
| 类型为翻数 | 必填且值为“翻数” | 选填 | 选填 | 选填 |
| 类型为全量、增量或初始化 | 必填 | 必填 | 至少一项 | 必填 |

选填字段一旦填写，仍执行既有格式、长度、数量、去空和去重校验。用户从其他类型切换为“翻数”时保留已填写内容并清除必填错误；从“翻数”切回其他类型时不自动补默认值，提交时在对应字段旁提示缺失。

### 列表列结构与对齐

- 日历安排桌面列表将“测试环境 / 日期”拆为“测试环境”“自然日期”“营业日期”三个独立列；测试环境列可同时显示环境名称和环境编码，但不得再混入日期。
- 跑批需求桌面列表将“环境 / 需求日期”拆为“测试环境”“自然日期”“营业日期”三个独立列；提出人、跑批、系统、评审和操作继续保持独立列。
- 营业日管理中的测试环境、日历安排、跑批需求三个桌面表格，全部表头和单元格内容居中；状态标签、系统标签、环境标识、提出人信息和操作按钮组也在单元格内居中。
- 日历概览是月历而非数据列表，不改变其 RADAR 月历对齐方式。移动端继续使用现有业务卡片和信息层级；手机卡片没有表头，不强制全文居中，以免降低长内容可读性。
- 拆列和对齐只改变页面呈现，不改变 API、筛选、排序、导出字段、移动卡片或权限行为。

### 边界、状态与数据流

- 前端日历安排保留“是否跑批”；关闭时不提交过期跑批字段。
- 前端跑批需求移除“是否跑批”，提交始终表示跑批。
- 三个桌面列表使用模块局部的统一居中规则；日历安排和跑批需求显式声明三个日期相关列，避免内容重新组合。
- 后端是最终规则边界：跑批需求无论客户端传入何种 `has_batch` 都按跑批校验和保存；日历安排根据跑批标志及导入字段推断结果执行相同字段矩阵。
- Excel 导入复用日历安排保存校验：翻数行允许时间、系统、验证内容为空；其他跑批类型缺少任一必填字段时整批回滚并返回明确原因。
- 不修改数据库、权限、审计、用户目录、环境引用或导出字段契约。

### 方案比较与选择

- 选择：前后端和导入共用固定 RADAR 字段矩阵。规则可观察、不可绕过，且不引入新平台能力。
- 不选择仅修改前端：直接调用接口或导入仍会产生不一致数据。
- 不选择迁移 RADAR 动态输入项配置：RDDMP 已明确该能力下线，本次只需要稳定字段联动，迁移会扩大模块和数据库范围。

### 错误处理与恢复

- 非翻数缺少时间、系统或验证内容时，分别返回“请选择跑批时间”“请至少填写一个涉及系统”“请填写验证内容”。
- 已填写时间格式非法、系统名称超限或验证内容超长时，继续使用既有格式和长度错误。
- 前端保存失败时保留抽屉和已输入内容；切换类型只更新校验状态，不丢失用户输入。
- 回退时恢复本补充规则涉及的服务、表单和测试文件；无数据库补偿操作。

### 验证策略

1. 后端测试覆盖翻数三项均空可保存、翻数已填非法值仍拒绝、非翻数分别缺少时间/系统/验证内容时拒绝、跑批需求传 `has_batch=false` 仍按跑批校验。
2. 导入测试覆盖合法翻数空字段和非法非翻数缺字段的整批回滚边界。
3. 前端生产构建通过；真实浏览器分别验收日历安排和跑批需求的翻数/增量切换、字段错误清除与重新出现。
4. 桌面端确认日历安排和跑批需求均存在独立的测试环境、自然日期、营业日期表头；三个列表的表头、普通单元格和复合内容均居中。
5. 桌面及 375x812、390x844、430x932 视口中字段、错误信息和底部操作可达，页面无横向溢出；浅色和深色主题均可读，移动卡片信息层级不回归。

### 非目标、风险与回退原则

- 不迁移动态输入项配置，不新增跑批类型，不接入系统目录，不改变需求评审和日历同步规则。
- 主要风险是前端、手工接口和导入规则漂移；通过后端单一校验入口和对应回归测试限制。
- 本补充无数据库结构变化，回退仅涉及应用代码和当前需求设计记录。

## 实施计划：跑批字段联动与列表拆列居中

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

### 状态与来源

- 计划修订：1
- 设计修订：2
- 设计文档：本文件“补充设计：RADAR 跑批字段联动规则”
- 状态：可移交

### 目标与全局约束

**目标：** 日历安排、跑批需求、直接 API 和 Excel 导入统一执行 RADAR 跑批字段矩阵；两个业务列表拆分测试环境、自然日期、营业日期，三个营业日桌面列表统一居中。

**架构：** `BusinessDayService` 作为跑批字段的单一最终校验入口，前端两个表单只提供即时同构反馈。列表拆列和居中限定在测试管理营业日模块，不修改公共 `UiDataTable`、API 字段或数据库。

**技术栈：** Java 17、Spring Boot 3.4.4、JUnit 5/Mockito、Vue 3、TypeScript、Element Plus、现有 Apache POI 导入链路。

全局约束：

- 仅修改当前需求 `writable_paths`；RADAR 目录和公共 UI 组件保持只读。
- 不新增依赖、数据库迁移、跑批类型、公共接口或动态输入项配置。
- 保持租户隔离、RBAC、审计、环境有效性、用户目录和原子导入语义。
- 所有测试管理模块源文件继续保留文件头及关键逻辑注释。
- 桌面表格居中不得改变移动卡片信息层级，不得引入页面级横向滚动。

### 文件职责地图

| 文件 | 状态 | 单一职责 |
| --- | --- | --- |
| `server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/BusinessDayService.java` | existing | 统一规范化并校验日历安排和跑批需求的跑批字段，供手工与导入复用 |
| `server/src/modules/test-management/src/test/java/com/ccb/testmanagement/service/BusinessDayServiceTest.java` | existing | 覆盖翻数豁免、非翻数必填、需求固定跑批和导入复用边界 |
| `web/src/modules/test-management/business-day/CalendarScheduleList.vue` | existing | 日历安排表单联动、独立列和列表呈现 |
| `web/src/modules/test-management/business-day/BatchRequirementList.vue` | existing | 固定跑批需求表单联动、独立列和列表呈现 |
| `web/src/modules/test-management/business-day/TestEnvironmentList.vue` | existing | 测试环境桌面列表呈现回归范围 |
| `web/src/modules/test-management/business-day/business-day.css` | existing | 营业日桌面表格局部居中与移动端边界 |
| `.ai-control/requirements/req-20260821-037-business-day-management/execution-T5.json` | candidate-new | 后端规则实施证据 |
| `.ai-control/requirements/req-20260821-037-business-day-management/execution-T6.json` | candidate-new | 前端与集成实施证据 |
| `.ai-control/requirements/req-20260821-037-business-day-management/observation-T5.json` | candidate-new | 后端规则观测证据 |
| `.ai-control/requirements/req-20260821-037-business-day-management/observation-T6.json` | candidate-new | 表单、列表和浏览器观测证据 |

### 任务依赖与覆盖

- 串行执行：T5 后端规则 → T6 前端联动与列表呈现。前端以 T5 已固定的错误语义为契约，不并行猜测。
- T5 覆盖 R-BATCH-1 至 R-BATCH-5；T6 覆盖 R-BATCH-1 至 R-BATCH-4、R-LIST-1、R-LIST-2。
- 集成采样在 T5 后执行目标模块测试，在 T6 后执行全量 Maven、Vue、治理、范围和真实浏览器检查。

### T5：统一后端跑批字段矩阵

**需求映射：** R-BATCH-1、R-BATCH-2、R-BATCH-3、R-BATCH-4、R-BATCH-5
**前置任务：** 无

**文件：**

- 修改：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/BusinessDayService.java`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/service/BusinessDayServiceTest.java`
- 证据：`.ai-control/requirements/req-20260821-037-business-day-management/execution-T5.json`

**接口：**

- 消费：现有 `saveSchedule(Map<String,Object>, AuthUser)`、`updateSchedule`、`importSchedules`、`createRequirement`、`updateRequirement`。
- 产出：现有请求和响应字段不变；跑批需求强制 `has_batch=true`，翻数允许后三项为空，其他类型分别返回时间、系统、验证内容错误。

- [ ] 步骤 1：在 `BusinessDayServiceTest` 建立失败测试，覆盖翻数三项为空可保存、翻数非法非空时间仍拒绝、非翻数分别缺少时间/系统/验证内容拒绝、需求传 `has_batch=false` 仍按跑批处理，以及导入复用同一规则。
- [ ] 步骤 2：运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -pl :ccb-test-management -am -Dtest=BusinessDayServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期新增用例在现有实现上失败，证据记录失败断言而非环境错误。
- [ ] 步骤 3：在服务内建立最小的单一跑批字段规范化入口：无跑批清空四项；跑批类型必填；翻数使用可选时间/系统/验证内容；其他类型使用必填时间、至少一个系统和必填验证内容；非空值继续复用现有限长和格式校验。
- [ ] 步骤 4：让日历手工保存、需求保存和导入保存全部经过该入口；需求忽略客户端的 `has_batch=false` 并持久化为跑批。
- [ ] 步骤 5：重跑聚焦测试，预期全部通过且 0 个失败；随后运行 `git diff --check`，保存退出码和关键用例数。
- [ ] 步骤 6：写入 `execution-T5.json`，记录真实文件、命令、结果、扰动和未决项，不提交或覆盖用户无关改动。

**验收：** 五条跑批需求均有正常和负向传感器；导入没有独立旁路；数据库、权限和审计契约不变。
**回滚：** 仅回退 `BusinessDayService.java`、对应测试和 T5 证据；无数据库补偿。
**停止条件：** 需要修改 V52、公共错误模型、公共导入组件或 API 字段时停止并重新建模。
**升级条件：** 现有历史记录因数据库非空约束无法保存合法翻数空值，或手工与导入无法共用服务入口时升级主 Agent/用户。

### T6：前端联动、拆列和统一居中

**需求映射：** R-BATCH-1、R-BATCH-2、R-BATCH-3、R-BATCH-4、R-LIST-1、R-LIST-2
**前置任务：** T5

**文件：**

- 修改：`web/src/modules/test-management/business-day/CalendarScheduleList.vue`
- 修改：`web/src/modules/test-management/business-day/BatchRequirementList.vue`
- 修改：`web/src/modules/test-management/business-day/TestEnvironmentList.vue`
- 修改：`web/src/modules/test-management/business-day/business-day.css`
- 证据：`.ai-control/requirements/req-20260821-037-business-day-management/execution-T6.json`

**接口：**

- 消费：T5 固定的跑批字段矩阵和现有营业日 API。
- 产出：需求表单不再显示是否跑批；两个表格分别产出“测试环境、自然日期、营业日期”表头；三个桌面表格使用模块局部居中规则。

- [ ] 步骤 1：记录当前 Vue 构建和浏览器 DOM 基线：两个列表仍存在组合列、需求仍有是否跑批开关，保存为预期待纠正信号。
- [ ] 步骤 2：日历安排表单增加时间、系统和验证内容的类型联动校验；切换翻数时清除三项必填错误但保留值；关闭跑批时提交空跑批字段，编辑翻数空时间不再回填 `22:00`。
- [ ] 步骤 3：跑批需求移除是否跑批开关，提交显式 `has_batch=true`；应用相同类型联动，编辑翻数空时间保持为空。
- [ ] 步骤 4：把两张表的组合列拆为独立测试环境、自然日期、营业日期；环境列仅显示名称/编码。使用模块局部 CSS 或 Element Plus 列属性让三个桌面表格表头和全部单元格复合内容居中，不修改 `UiDataTable`。
- [ ] 步骤 5：运行 `npm --prefix web run build`；预期 `vue-tsc` 与 Vite 成功，仅允许既有 chunk 告警。运行 `git diff --check`，预期退出码 0。
- [ ] 步骤 6：重启受影响服务后使用真实浏览器验收 `?view=schedule`、`?view=requirements`、`?view=environments`：增量缺字段显示三个对应错误，翻数空后三项可提交；两个列表具有三个独立列；三个列表计算样式居中；控制台无 warning/error。
- [ ] 步骤 7：在 1280x800、375x812、390x844、430x932 及浅色/深色主题检查移动卡片、表单错误、底部操作、长文本和页面宽度；预期无页面级横向溢出，手机卡片保持原信息层级。
- [ ] 步骤 8：写入 `execution-T6.json`，记录真实构建、浏览器路径、视口和结果，不提交或覆盖用户无关改动。

**验收：** 表单联动与后端一致；列表拆列和居中可由 DOM/计算样式重复测量；移动卡片和主题无回归。
**回滚：** 回退四个前端文件和 T6 证据，T5 后端规则可独立保留。
**停止条件：** 实现需要修改公共 `UiDataTable`、应用壳层或整页横向滚动时停止并重新规划。
**升级条件：** 用户要求移动卡片也全文居中、列宽无法在桌面目标视口容纳或现有表格固定列产生遮挡时升级主 Agent/用户。

### 集成检查与控制模型种子

集成命令与预期：

1. `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn test`：全部 Maven 模块成功，0 个失败。
2. `npm --prefix web run build`：Vue 类型检查和 Vite 生产构建成功。
3. `node scripts/check-governance.mjs && node scripts/check-module-boundaries.mjs && node scripts/check-flyway-migrations.mjs`：专项治理、模块边界和 52 个迁移检查通过。
4. `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260821-037-business-day-management/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`：全部修改处于授权范围。
5. `git diff --check`：无空白错误。
6. 真实浏览器：三个桌面列表、两个表单、三个手机视口、浅色/深色主题、控制台和页面宽度均满足设计信号。

控制模型种子仅为待验证假设：被控边界是营业日两个写入流程和三个桌面列表；状态变量包括跑批类型、后三项必填状态、需求固定跑批、列结构和对齐；传感器候选为 JUnit、Vue 构建、真实 API、DOM/计算样式、视口宽度和控制台；执行器候选为服务字段规范化、表单规则、列模板和模块 CSS；扰动候选为历史空字段记录、Element Plus 表格内部样式和本机 JDK 默认版本；JDK 17 路径、现有 API 字段和移动卡片行为需要在闭环建模阶段复核。

### 风险与用户批准

- 高风险动作：无数据库、权限、公共能力或外部系统变更；主要风险是历史非翻数缺字段记录再次编辑时需要补齐，以及居中长文本的桌面可读性。
- 当前工作区已由任务范围指定为 `/Volumes/GuanMac/Code/RDDMP`，当前分支为 `feat/REQ-20260821-037-business-day-management`；不创建嵌套工作树，不清理已有未提交需求改动。
- 用户批准本计划后，机器交接包改为 `approved`，再导入独立的本补充控制状态文件并进入 baseline/modeling；未批准前不得修改产品代码。
- 计划批准依据：用户明确回复“确认计划”。

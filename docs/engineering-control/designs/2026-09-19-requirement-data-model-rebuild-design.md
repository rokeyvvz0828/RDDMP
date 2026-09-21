# 需求管理数据模型重构工程设计

## 文档状态

- 修订：1
- 状态：已确认
- 用户确认依据：用户 2026-09-19 确认"新建项目一个需求以物理子系统维度提出所以只有一个，存量是关联的会有多个""只做需求管理，不做开发管理，但要考虑给开发管理用""系统人员不保留，只留系统负责人，前端也去掉""新建项目只有需求全部评审完、形成基线才可以创建开发任务，存量要到软需阶段、立项完才可以创建开发任务"。

## 目标与成功信号

新建与存量两页共用一条需求主表，需求与物理子系统的关系用关联行表达（新建 1 行、存量 1..N 行），系统主数据统一取架构管理物理子系统，并把开发管理所需的数据口径固定下来。成功信号：新建差异只能选 1 个物理子系统；存量主责唯一、可多行；开发管理读取契约形状不变而数据来源切到新表。

## 使用者与场景

- 需求提出人/分析员/统筹：在两个页面按新模型维护需求与系统。
- 开发管理：按 (需求, 物理子系统) 读取来源，为每个系统生成一个开发任务。
- 架构管理：作为物理子系统主数据的唯一所有者。

## 必须需求与验收条件

- R1 统一主表：两页共用 `req_requirement`，`requirement_kind` 区分。验收：新建与存量数据同表不同 kind，页面仍按 kind 过滤。
- R2 系统关联行：`req_requirement_system`，新建恰好 1 行、存量 1..N 且 LEAD 唯一。反例：新建差异挂 2 个系统。
- R3 系统主数据来源：系统下拉来自架构物理子系统，需求不再自建系统清单。反例：存在 `req_system` 驱动的下拉。
- R4 开发来源时点：新建差异在"项目内无待评审/评审中差异且该差异已纳入基线"后为来源；存量在"到软需阶段且立项已完成"后为来源。
- R5 系统人员下线：系统行只有系统负责人；前端无系统人员控件。
- R6 对外契约兼容：`RequirementDevelopmentQuery` 形状不变，存量读取语义与改造前一致。
- R7 项目归属：`project_id` 为 `pm_project.id`。

## 不变量与约束

- 不改 `business/development` 与 `business/architecture` 代码；跨模块只通过 `com.ccb.requirement.integration` + `platform/boot` 适配器。
- Flyway 只追加；不做数据回填（测试数据，按新模型重建）。
- 所有查询带 `tenant_id`。

## 非目标

- 开发任务的生成、来源类型放开（`dev_task` 约束）与开发侧映射改造。
- 权限目录与数据范围重建（REQ-20260919-077，同批实施）。
- 交付件上传能力。

## 方案比较与选择

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| 主表 + 关联行（选择） | 公共字段上主表，系统关系用关联行，专有字段进详情表 | 选择：流转/收回/数据范围/开发来源只实现一次，行数天然等于开发任务数 |
| 保持两表 + 共用机制 | 不动模型，只抽公共实现 | 备选：字段与查询仍两套，开发来源需两处实现 |
| 合并宽表 | 所有字段一表 | 不选：列过多、约束难表达 |

## 架构边界与组件职责

- `req_requirement` / `req_requirement_system` / `req_difference_detail` / `req_legacy_detail` / `req_flow_log` / `req_legacy_deliverable`：需求侧数据模型。
- `RequirementSystemDirectory`（requirement 的 integration 包）：只读系统目录接口。
- `RequirementSystemDirectoryAdapter`（platform/boot）：委托架构 `PhysicalSystemDirectoryQuery`。
- `JdbcRequirementDevelopmentQuery`：契约不变，改读新表并实现 R4 的 active 规则。
- 需求页：新建差异单选系统；存量系统行含主责/改造/测试与负责人，无系统人员；页签重组。

## 接口、数据和状态流

- 新建差异：创建时写 1 行系统关联（LEAD）；`active` 需项目内无待评审/评审中差异且已纳入基线。
- 存量需求：创建不校验系统；离开"需求对接"阶段前必须 LEAD 1 行 + 协同 ≥1 行；`active` 需软需阶段且立项已完成。
- 开发读取：`search` 按项目 + 授权系统编码过滤；`find` 按需求 id 返回含 systems 的来源投影。

## 错误、降级与恢复

- 新建差异未选系统：400 且提示按物理子系统维度提出。
- 存量推进需求对接缺系统：冲突错误并列出缺失项。
- 系统编码在架构侧不存在或停用：候选不返回；已保存的历史快照保留展示。

## 验证策略

- 模块测试：系统行约束、active 规则、契约输出形状。
- 全量后端测试：确认 development 侧不受影响。
- 前端构建；浏览器验收由用户执行。

## 假设、未知项与决策记录

- 假设：架构物理子系统的编码可用于跨模块关联（`uk_arch_physical_code` 唯一）。
- 未知项：开发侧放开来源类型的时间点（不在本次范围）。
- D1 新建=1 个物理子系统；D2 系统人员下线；D3 开发来源时点按 R4；D4 不做数据回填。

## 风险与回退原则

- 风险：模型重构期间需求数据不可用（测试数据可接受）；契约实现改写若语义偏差会影响开发管理读取。
- 回退：整体回退代码与迁移，重建测试数据。

## 实施对齐说明（2026-09-19 实施记录）

- 迁移 `V20260919210000__requirement_data_model_rebuild.sql` 已按本节模型建表、清空并重建演示数据；`req_legacy_deliverable` 合并原 `req_workload`/`req_soft_doc`。
- 被替换的旧需求表分两条迁移删除：`V20260919210000` 删除 `req_workload`、`req_soft_doc`、`req_legacy_member`；`V20260919211000` 删除 `req_difference_flow_log`、`req_difference`、`req_legacy_requirement`、`req_legacy_system_item`、`req_coordination_item`、`req_business_group_member`、`req_system`（均已无读写方）。
- 同批清理：`mock/mock-data.json` 需求演示数据改由迁移重建、按新模型补回开发管理的来源演示数据；`MockDataInitializer` 允许清单（旧表出、新表入）、`MockDataInitializerTest` 内联数据集、`platform/boot` 开发管理集成测试的需求来源夹具。
- 迁移纪律事故与纠正（2026-09-19）：`V20260919210000` 在本地库执行后又被追加删表语句，导致 Flyway 校验和不一致、应用无法启动。纠正方式为把该迁移还原为已执行版本（Flyway 校验和 1884520140 已用 flyway-core 复算比对一致），新增删表动作改由 `V20260919211000` 承接；后续同一迁移一旦在任一环境执行即不再修改内容。
- 运行缺陷纠正（2026-09-19）：需求模块 SQL 字符串拼接漏分隔空白（Java 文本块缩进决定前导空白），导致存量列表 500；已补齐并改为显式分隔，模块内 121 条完整 SQL + 12 条动态拼接查询已在真实 MySQL 上 PREPARE 校验通过。
- 前端落位澄清（2026-09-19，Owner）：存量需求「需求对接 / 工作量评估 / 软需」不作为与「阶段信息」平级的页签，而是内嵌进「阶段信息」中对应阶段下——选到"需求对接"阶段显示系统与协同事项表、选到"工作量评估"阶段显示工作量表、选到"软需"阶段显示软需文档；抽屉页签收敛为「阶段信息 / 流转记录 / 版本历史」。创建存量需求时预置 `current_stage=PROPOSE`，使阶段时序条可用、可在保存前维护主责/协同系统行。
- 阶段状态取值澄清（2026-09-19，Owner）：阶段信息内的「需求状态及备注」固定在每个阶段内容最下方，且**需求状态候选值按当前查看的阶段**收敛（需求提出：需求提出/需求终止；需求对接：需求分析/需规编制/需规评审/需求终止；工作量评估：工作量评估/需求终止；立项：立项/需求终止；软需：软需编写/软需评审/需求终止；投产：**待投产/已投产/需求终止**，默认取第一个"待投产"）。查看历史阶段时该字段只读，回到当前阶段可修改。
- 需求对接前置校验澄清（2026-09-19，Owner）：离开「需求对接」阶段前**只要求维护 1 个主责系统**（主责唯一由保存时校验），协同系统（改造/测试）可选、不作硬拦截。此口径覆盖 requirement.md 验收标准中"缺主责或协同都拒绝"的旧表述，后端 `requireDockingSystemItems` 与前端提示已按新口径调整。
- 评审文件上传落地（2026-09-19，Owner 追加）：原"本次不实施：交付件与文档上传能力"中的**评审报告上传**已实施——新建差异评审、存量工作量评审与软需评审提交时必须先上传评审报告文件。
  - 文件托管走平台附件能力（`POST /api/attachments` 上传 → `AttachmentGateway.bind` 绑定到业务类型 `REQUIREMENT_REVIEW`，businessKey = 需求 ID），需求侧只落文件名称快照与附件 ID。
  - 新增只读依赖：`governance/modules.yaml` 的 `business/requirement.allowed_dependencies` 增加 `platform/attachment`，模块 pom 增加 `ccb-attachment`；模块内新增 `RequirementReviewAttachmentPolicy`（复用需求数据范围做附件访问判定）。
  - 存储：`req_difference_detail.review_report_attachment_id`、`req_legacy_deliverable.review_report_attachment_id`（迁移 `V20260919212000`）；评审完成时把附件 ID 写入 `req_review_record.report_preview_id`，评审记录弹窗可按附件 ID 生成预览链接打开文件。
  - 接口契约：`/differences/{id}/submit-review` 与 `/deliverables/{id}/submit-review` 以 `reportAttachmentId`（平台附件 ID）替代原来的 `reportDocName` 文本；缺失时后端返回 400「请先上传评审报告文件，再提交评审」。
- 审批界面归属与深链优先级（2026-09-19，Owner 询问后澄清）：**审批弹窗由平台框架定义**——需求页里的审批 UI 完全由 `getWorkflowTaskContext(taskId)`（business_title / allowed_actions）驱动、`decideWorkflowTask` 提交，审批人与节点来自工作流定义；需求模块只发起流程（`workflowGateway.startByDefinitionId` + `WorkflowBusinessContext`）并在终态回写状态，不做按人定制的审批表单。平台统一入口是待办中心 `/workbench/tasks?tab=pending`，`/dashboard?taskId=` 会先解析业务 `action_path` 再把 `taskId` 带回业务页面。
  - 缺陷修正：存量交付件评审的业务 `action_path` 为 `/requirements/legacy?legacyId=<id>`，当深链同时带 `taskId`（从待办/仪表盘进入）时，需求编辑抽屉覆盖了审批弹窗，导致"只看到需求编辑页、只能从我的待办审批"。现改为**审批优先**：带 `taskId` 时不再自动打开需求抽屉，`openApproval` 先收起抽屉，审批完成后若仍有 `legacyId` 再展示需求详情。
  - 回归与纠正（2026-09-19）：上一版把"收起需求抽屉"放在 `openApproval` 首行，而 `?taskId=` 的 watcher 是 `immediate: true`（在 setup 阶段就会调用它），此时文件后半部分声明的 `legacyFormVisible` 尚未初始化 → 抛 TDZ 异常被 async 吞掉，导致从待办进评审时弹窗不再出现（只打开需求列表）。现已把该赋值移到首个 `await` 之后（setup 同步阶段结束后才执行），并在代码注释中写明约束。
- 评审报告必传开关（2026-09-19，Owner 要求先关闭以便测试）：`ccb.requirement.review-report-required`，默认 `true`（上线口径＝必须上传）；`application-dev.yml` 置为 `false`（本地未启动 MinIO/附件能力，暂不拦截提交）。开关通过 `/requirements/enums` 下发前端，两端一致：关闭时表单不再标必填、不拦截提交，上传入口保留（上传后仍会校验并绑定，评审记录里可打开文件）；`local` 等其它环境仍按默认必传。

### 前端约束备注（避免再次回归）

`RequirementsView.vue` 里 `?taskId=`、`section`、`current_stage` 三个 watcher 都是 `immediate: true`，会在 setup 同步阶段执行。它们触发的逻辑**不得同步访问文件后半部分声明的状态**（如 `legacyFormVisible`、`legacyForm`、`legacyViewStage`），否则抛 TDZ 异常且被 async 吞掉，表现为"弹窗/抽屉不出现"。需要收尾动作时放到首个 `await` 之后，或改用 `onMounted` 触发。

### 审批弹窗信息与入口对齐（2026-09-19，Owner 反馈）

- 业务类型 / 业务单号**框架不做映射**：`business_type` 是业务模块发起流程时传入的类型码（需求侧为 `requirement_diff_review` / `requirement_deliverable_review`），`business_key` 是业务侧拼的单号（`req-deliverable:<TYPE>:<id>` / `req-diff:<id>`）。需求页审批弹窗现做友好展示：业务类型→「新建项目差异评审 / 存量交付件评审」，业务单号→「软需文档 #id / 工作量表 #id / 差异 #id」，原始码放在 `title` 悬浮提示里。
- 去掉了弹窗里的「允许操作」只读行（同意 / 不通过 / 加签 / 抄送），审批动作仍由底部按钮提供。
- 发起人：框架 `WorkflowService.taskContext` 原本未返回 `starter_name`，已在查询中补 `sys_user.display_name AS starter_name` 并返回（对所有模块生效）；前端为空时不再显示该行。
- 审批人可见业务信息：弹窗按业务单号回查并展示需求编号 / 名称 / 当前阶段 / 状态、评审对象（工作量表 / 软需文档 + 文档名 + 版本）、评审报告文件（可点击打开预览）；差异评审展示差异名称 / 编号 / 评审状态 / 报告文件。取不到或无权限时给一行提示，不阻断审批。
- 入口对齐：站内消息深链只带 `legacyId`，现在进入需求页时会先用框架 `/workflows/tasks/current-context` 查询"当前用户对该需求是否还有待审批任务"，命中则直接打开审批弹窗（与待办中心入口一致），无待办才打开需求详情。备选方案（未采用）：在平台通知桥 `WorkflowSystemNotificationBridge` 的待办通知 `action_path` 上追加 `taskId`。
- 评审文件预览（2026-09-19，Owner 要求"评审的人得看下文件再评审"）：平台已有预览能力（`platform/file-preview` 模块 + kkFileView 适配 + 附件 `GET /api/attachments/{id}/preview|download` + 前端统一 `UiFilePreview` 弹窗）。审批弹窗的评审报告行现提供「预览 / 下载」两个入口，并按格式分档：
  - PDF / 图片 / 文本（pdf、png、jpg、jpeg、gif、bmp、webp、svg、txt、md、csv）：直接用受控的预签名下载地址在 `UiFilePreview` 内嵌渲染，**不依赖 kkFileView**；
  - Office 等需服务端转换的格式：走 kkFileView 在线预览（`GET /api/attachments/{id}/preview`）；
  - 在线预览未部署时回退下载并明确提示（`ccb.file-preview.enabled=false` / `kk-base-url` 为空时即为此情形）。
  评审记录弹窗里的评审报告文档同样按此逻辑打开。若要在浏览器内直接翻页看 docx/xlsx，需部署 kkFileView 并配置 `ccb.file-preview.kk-base-url`（容器访问 MinIO 的地址另由 `MINIO_PREVIEW_ENDPOINT` 提供）。
- 系统目录适配器 `RequirementSystemDirectoryAdapter` 按架构库表只读查询"项目内有效物理子系统"：架构侧现有公开契约 `PhysicalSystemDirectoryQuery` 只提供"我负责的系统"，口径不匹配，故保留当前实现并记录为偏差；架构侧开放"按项目列系统"公开契约后应改为委托。
- 演示数据挂载在"已有有效架构物理子系统"的项目上（不足时用历史演示项目补齐）；需求台账 `req_project` 同时与该项目管理主键对齐。存量页面可见性仍由 `pm_project.creation_type` 决定。

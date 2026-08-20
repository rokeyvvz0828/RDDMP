# 配置管理正式业务模块工程设计

## 文档状态

- 需求编号：`REQ-20260814-021`
- 主题：`release-management-module`
- 修订：5
- 状态：已批准
- 用户确认依据：用户要求正式开发除项目上下文、物理子系统和交付单元选择源之外的全部配置管理功能，确认后端保存三个上游对象的快照，选择方案 A，并于 2026-08-15 明确确认第 5 版落盘设计。

## 1. 目标与成功信号

在 RDDMP 中交付正式 `ccb-release` 业务模块，让投产窗口、版本申请、审批状态、投产基线、生产版本和统计分析形成同一套持久化事实。前端只保留当前项目、物理子系统和交付单元的临时 Mock 选择源，提交后所有业务数据进入后端。

成功信号：

1. 页面刷新、重新登录和服务重启后，窗口、申请、审批关联、投产结果和统计数据保持一致。
2. 申请提交通过业务服务按固定流程编码启动真实流程，不存在前端模拟当前节点、审批轮次和日志。
3. 审批完成只产生制品准出候选；只有投产成功记录改变生产版本。
4. 投产基线、生产版本、统计指标和下钻明细来自同一数据库事实。
5. 验收数据通过正式 API 和工作流操作生成，不直接插入业务演示记录。

## 2. 方案比较与选择

### 2.1 已选：单一 `ccb-release` Maven 模块

模块内部按 `window`、`application`、`production`、`reporting`、`integration` 和 `web` 分包。窗口与申请冲突、流程回写、投产候选和生产版本需要一致事务与联合查询，集中所有权更适合当前模块化单体。

### 2.2 未选：拆成多个业务 Maven 模块

窗口、申请、投产和报表的数据强相关，拆分会引入跨模块事务、事件顺序和重复 DTO，当前收益不足。

### 2.3 未选：保留前端状态并增加包装接口

包装前端 Mock 无法解决刷新丢失、审批事实分裂、并发覆盖和报表不一致，不属于正式业务实现。

## 3. 范围与边界

### 3.1 模块内

- 投产窗口和变更审计。
- 版本申请、交付单元快照、需求、附件关联、冲突与状态机。
- 工作流启动、审批轮次关联和生命周期事件消费。
- 窗口投产候选、投产结果、结果变更审计和生产版本计算。
- 统计聚合、筛选和下钻。
- 五个正式前端视图与版本申请业务详情审批。

### 3.2 模块外

- 当前项目切换、项目成员和项目数据范围的正式实现。
- 物理子系统和交付单元主数据服务。
- 工作流引擎、任务授权、电子签名、通知和附件平台内部实现。
- 需求、测试缺陷、制品仓库和外部系统集成。

### 3.3 临时 Mock 边界

前端 `release-master-data.mock.ts` 只提供当前项目、物理子系统和交付单元选项。后端接收并保存以下快照：

- 项目 ID、编码、名称。
- 物理子系统 ID、编码、名称。
- 交付单元 ID、编码、名称、制品类型。

后端验证非空、长度、同申请一致性、交付单元唯一性和制品类型，但在上游模块接入前不宣称验证实体真实性或项目成员范围。正式端口接入后只替换选择和授权适配器，历史快照不回写。

## 4. 架构与组件

```text
ccb-release
├── window          窗口规则、查询、编辑和审计
├── application     申请、冲突、场景、状态与轮次
├── production      投产候选、结果、生产版本和回算
├── reporting       聚合指标、筛选与下钻
├── integration     工作流生命周期、附件和临时快照端口
└── web             Controller、请求 DTO、响应 DTO
```

### 4.1 依赖方向

- `ccb-release` 依赖 `ccb-common`、`ccb-infrastructure`、`ccb-security`、`ccb-workflow` 和 `ccb-attachment` 的公开包。
- `ccb-release` 不访问 `wf_*`、`att_*`、`sys_*` 私有表。
- `ccb-boot` 只负责装配 `ccb-release`。
- 前端通过 `web/src/api/release.ts` 访问业务 API，通过现有 `workflow.ts` 和 `attachments.ts` 使用平台公开 API。

### 4.2 关键组件

| 组件 | 职责 |
|---|---|
| `ReleaseWindowService` | 窗口 CRUD、重叠校验、状态和开关 |
| `ReleaseApplicationService` | 草稿、修改、提交、撤回、取消和查询 |
| `ReleaseScenarioPolicy` | 常规、紧急、应急及追加特征判定 |
| `ReleaseConflictService` | 同窗口交付单元历史检索和可选动作 |
| `ReleaseWorkflowAdapter` | 调用 `WorkflowBusinessGateway` 并保存轮次 |
| `ReleaseWorkflowLifecycleConsumer` | 幂等消费流程事件并更新申请状态 |
| `ReleaseProductionService` | 生成窗口候选、维护投产结果和回算生产版本 |
| `ReleaseReportingService` | 数据库聚合、筛选和下钻 |
| `ReleaseAuthorizationService` | 租户、RBAC、申请人和实体状态校验 |

## 5. 数据模型

### 5.1 业务表

| 表 | 责任 |
|---|---|
| `rel_release_window` | 窗口编码、项目快照、四个时间、常规开关和版本号 |
| `rel_window_change_log` | 窗口字段前后值、原因、操作人和时间 |
| `rel_release_application` | 申请单号、场景、状态、申请人、窗口及项目/子系统快照 |
| `rel_application_delivery` | 交付单元快照、制品类型、版本及历史生产版本快照 |
| `rel_application_requirement` | 需求编号明细 |
| `rel_application_attachment` | 平台附件 ID、类别和提交时名称快照 |
| `rel_application_round` | 审批轮次、流程编码、定义/版本、实例 ID 和数据摘要 |
| `rel_application_relation` | 追加、冲突来源、撤回重提和版本替代关系 |
| `rel_application_event` | 业务状态、操作、原因和结构化载荷 |
| `rel_production_entry` | 窗口、申请、交付单元、准出版本和投产结果 |
| `rel_production_result_log` | 投产结果变更前后值、原因、操作人和时间 |
| `rel_workflow_event_receipt` | 工作流事件幂等消费记录 |

### 5.2 核心唯一性

```text
窗口编码：tenant_id + window_code
窗口周期：服务层锁定同项目候选窗口后校验不重叠
申请单号：tenant_id + application_code
申请交付单元：tenant_id + application_id + delivery_unit_code
申请需求：tenant_id + application_id + requirement_no
审批轮次：tenant_id + application_id + round_no
工作流实例：tenant_id + workflow_instance_id
投产候选：tenant_id + window_id + physical_subsystem_code + delivery_unit_code
工作流回执：tenant_id + workflow_event_id + consumer_key
```

### 5.3 状态

申请状态：`DRAFT`、`IN_REVIEW`、`RETURNED`、`WITHDRAWN`、`CANCELLED`、`RELEASED`。

投产结果：`RELEASED`、`SUCCEEDED`、`FAILED`、`NOT_DEPLOYED`。

前端分别显示：草稿、审批中、已退回、已撤回、已取消、制品准出，以及制品准出、投产成功、投产失败、未投产。

## 6. 核心业务流

### 6.1 创建窗口

1. 前端携带当前项目快照和四个时间。
2. 服务端校验权限、时间顺序、投产起止同年和同项目周期不重叠。
3. 系统生成窗口编码并写入窗口。
4. 编辑时禁止改变项目和编码，要求原因并记录前后值。

### 6.2 保存与提交申请

1. 前端从临时 Mock 选择项目、物理子系统和交付单元。
2. 后端保存快照，校验至少一个交付单元、唯一性、制品类型和版本。
3. 后端根据应急标志、窗口时间、常规开关和历史申请计算版本类型、追加特征和流程编码。
4. 后端返回冲突详情；创建人明确冲突处理选择后才继续相应操作。
5. 提交时在同一业务事务中冻结申请摘要、创建轮次并调用 `WorkflowBusinessGateway.startByCode()`。
6. 流程启动成功后保存实例信息并将申请置为 `IN_REVIEW`；启动失败则事务回滚，申请保持原允许状态。

### 6.3 审批

1. 业务详情读取申请 API。
2. 详情根据当前轮次实例 ID 读取平台流程详情，根据当前登录人和业务标识读取可办任务。
3. 当前节点、处理人、意见、签名、任务状态和时间线只使用平台数据。
4. 审批决定由平台接口提交；`ReleaseWorkflowLifecycleConsumer` 幂等消费事件。
5. `APPROVED` 将申请置为 `RELEASED` 并生成或刷新投产候选；`RETURNED`/`REJECTED` 将申请置为 `RETURNED`；终止事件按业务操作来源落为撤回或取消前置状态。

### 6.4 投产候选与生产版本

1. 投产基线按窗口查询每个“物理子系统 + 交付单元”最新制品准出申请，排序以审批完成时间和申请 ID 为准，不比较版本字符串。
2. 首次进入窗口基线时幂等生成 `RELEASED` 候选；后续更晚准出版本替换尚未确认的候选来源并保留日志。
3. 投产人员把候选维护为成功、失败或未投产。失败和未投产原因必填，成功要求投产时间。
4. 生产版本查询从有效 `SUCCEEDED` 记录按投产时间、更新时间和 ID 选取最新一条。
5. 成功记录改为其他结果后，查询自动回到上一条有效成功记录，不维护容易失真的前端指针。

### 6.5 应急窗口归属

应急申请提交时不选择窗口。制品准出后按完成时间选择：

1. 完成时间落入计划投产开始至结束范围的窗口。
2. 若没有投产中窗口，选择计划投产开始晚于完成时间的最近窗口。
3. 若没有当前或未来窗口，流程提交前即阻止并提示维护承接窗口。

## 7. API

### 7.1 窗口

- `GET /api/release/windows`
- `GET /api/release/windows/{id}`
- `POST /api/release/windows`
- `PUT /api/release/windows/{id}`
- `PUT /api/release/windows/{id}/regular-enabled`

### 7.2 申请

- `GET /api/release/applications`
- `GET /api/release/applications/{applicationCode}`
- `POST /api/release/applications`
- `PUT /api/release/applications/{applicationCode}`
- `POST /api/release/applications/{applicationCode}/conflicts`
- `POST /api/release/applications/{applicationCode}/submit`
- `POST /api/release/applications/{applicationCode}/withdraw`
- `POST /api/release/applications/{applicationCode}/cancel`

创建和编辑响应返回服务端重算后的版本类型、追加特征、窗口可用性、最近生产版本和冲突摘要。提交请求携带 `rowVersion` 与冲突确认令牌，不能用布尔值绕过已经变化的历史事实。

### 7.3 投产与统计

- `GET /api/release/production-baselines?windowId={id}`
- `PUT /api/release/production-baselines/entries/{entryId}/result`
- `GET /api/release/production-versions`
- `GET /api/release/production-versions/{subsystemCode}/{deliveryUnitCode}/history`
- `GET /api/release/analytics/summary`
- `GET /api/release/analytics/drilldown`

### 7.4 平台协作

- 业务启动使用 Java 公共接口 `WorkflowBusinessGateway.startByCode()`。
- 审批详情和动作继续使用 `/api/workflows/tasks/*` 与实例详情接口。
- 附件先通过平台附件 API 上传，再由申请 API 校验归属并绑定。
- 生命周期通过 `WorkflowLifecycleConsumer` 回调，不由前端轮询修改业务状态。

## 8. 前端改造

- 新增 `web/src/api/release.ts`，集中正式业务 API 与 DTO。
- 删除 `repository.ts` 对业务状态的内存写入职责。
- `mock.ts` 拆为仅包含项目、物理子系统和交付单元选项的 `release-master-data.mock.ts`。
- 投产窗口、版本申请、投产基线、生产版本和统计视图全部改为服务端分页或聚合数据。
- 详情页使用申请响应中的当前轮次实例 ID加载真实流程，审批面板不再回写 Mock 申请状态。
- 页面覆盖加载、空、失败、无权限、并发失效、提交中和长文本状态。
- 桌面表格在手机端转换为业务卡片；必要的宽表只允许容器内局部滚动。

## 9. 权限与审计

建议权限：

- `release:window:view`、`release:window:create`、`release:window:update`
- `release:application:view`、`release:application:create`、`release:application:update`
- `release:application:submit`、`release:application:withdraw`、`release:application:cancel`
- `release:production:view`、`release:production:update`
- `release:analytics:view`

服务端从 `AuthUser` 获取租户和操作人，不接受客户端指定身份。申请编辑同时校验权限、申请人/管理权限、实体状态和 `rowVersion`。窗口修改、申请操作、流程事件、附件绑定和投产结果变化写入业务审计表。

当前项目模块未完成前，项目 ID 是租户内业务快照和筛选维度，不宣称完成项目成员权限校验。该限制必须在接口契约、测试和最终交付中披露。

## 10. 错误、恢复与幂等

| 条件 | 行为 | 恢复 |
|---|---|---|
| 流程编码未发布 | 提交事务回滚，申请保持原状态 | 发布流程后重新提交 |
| 工作流事件重复 | 回执唯一键命中，不重复更新 | 返回幂等成功 |
| 并发编辑 | 返回 `409` 和最新 `rowVersion` | 刷新后重试 |
| 冲突事实变化 | 冲突令牌失效并返回最新列表 | 重新选择处理动作 |
| 附件未上传完成或不属于当前用户/租户 | 拒绝绑定 | 修正附件后重试 |
| 投产结果更新失败 | 不写结果日志和业务状态 | 保留用户输入并重试 |
| 应急无承接窗口 | 阻止提交，不启动流程 | 创建窗口后重新提交 |

## 11. 性能、兼容与运维

- 列表使用服务端分页，统计按项目/窗口过滤，核心维度建立组合索引。
- 所有写接口使用短事务，外部平台调用采用同一应用内公共服务；工作流启动失败必须回滚业务轮次。
- Flyway 只新增 `V38__release_management.sql`，不修改 V35-V37。
- 不初始化窗口、申请、投产结果等业务演示数据；菜单和权限属于配置迁移。
- 正式验收使用隔离本地数据库，通过 API 生成测试数据，测试结束可删除隔离库，不修改原分支数据库历史。
- 回退时关闭菜单和模块装配，保留业务及审计表为只读证据。

## 12. 验证策略

### 12.1 后端

- 窗口日期、重叠、开关、不可变字段和审计测试。
- 申请字段、场景、冲突、状态、轮次、并发和权限测试。
- 五个流程编码的业务网关契约测试。
- 生命周期正常、重复、乱序和过期轮次测试。
- 投产候选、结果变更、生产版本回算和统计一致性测试。
- MySQL 8.4 Flyway、索引、唯一键和事务集成测试。

### 12.2 前端与浏览器

- 前端生产构建和类型检查。
- 通过 UI/API 创建窗口、创建申请、提交、审批、维护投产结果并查询生产版本。
- 验证刷新和重启后数据保持，审批页面左右节点、轮次和状态一致。
- 验证 1280x800、375x812、390x844、430x932，无页面级横向溢出。

### 12.3 真实数据验收

验收前通过工作流配置 API 创建并发布所需流程定义，再通过配置管理 API 创建窗口和申请。禁止直接 `INSERT` 配置管理业务数据，禁止以 `createReleasePrototypeState()` 作为验收证据。

## 13. 决策、假设和风险

### 13.1 已确认决策

- D1：采用单一 `ccb-release` Maven 模块，用户选择方案 A。
- D2：仅项目、物理子系统和交付单元选择源保留前端 Mock。
- D3：后端保存三个上游对象的编码和名称快照。
- D4：审批完成表示制品准出，投产结果独立维护。
- D5：流程细节由平台按固定流程编码配置，业务模块不硬编码节点名称。

### 13.2 假设

- 平台工作流、附件和安全公开能力保持当前契约可用。
- 项目管理和研发管理后续提供稳定查询/授权适配器。
- 当前项目为单租户内唯一前端工作上下文，正式项目权限接入前接受已披露限制。

### 13.3 非阻塞未知项

- 申请单号最终展示格式。首期使用 `RA-yyyyMMdd-流水号`，内部关系使用数值 ID。
- 投产窗口编码最终格式。首期使用 `WIN-yyyyMM-流水号`，不作为业务外键。

### 13.4 风险

- 项目权限尚未后端化：以租户、RBAC 和实体状态控制降低风险，最终交付明确披露。
- 工作流启动与业务事务耦合：使用同应用公共服务和事务测试保证失败不产生半提交申请。
- 投产候选被后续准出版本替换：只替换尚未确认候选，所有来源变化写日志。
- 前端原型类型与正式 DTO 差异较大：先固定 API DTO，再逐视图替换，禁止同时保留两套业务事实。

## 14. 回退原则

前端可关闭配置管理入口；后端可停止装配 `ccb-release`。新增迁移不回滚，数据库中的申请、流程实例关联、投产结果和审计记录保留。平台工作流、附件和安全模块不因业务模块回退而降级。

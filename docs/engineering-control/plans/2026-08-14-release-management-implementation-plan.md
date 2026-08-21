# 配置管理正式业务模块实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：5
- 需求：`REQ-20260814-021`
- 设计文档：`docs/engineering-control/designs/2026-08-14-release-management-module-design.md`
- 状态：已批准
- 目标分支：用户指定的本地 `rokey`

**目标：** 将配置管理原型升级为单一 `ccb-release` 正式业务模块，使窗口、申请、真实审批关联、投产基线、生产版本和统计分析共享持久化事实。

**架构：** `ccb-release` 内部按 `window`、`application`、`production`、`reporting`、`integration` 和 `web` 分区，业务表和状态归该模块所有。工作流、附件和安全只通过现有公开 Java/API 契约接入；前端仅保留项目、物理子系统和交付单元选择源 Mock，后端持久化提交快照。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Axios、ECharts。

## 全局约束

- 只写 `REQ-20260814-021` 任务范围授权路径，保护工作区现有未提交改动。
- 保持 `com.ccb.*` 包名、统一 `ApiResponse`、`BusinessException`、`TraceId` 和 `AuthUser`。
- `ccb-release` 只能依赖 `ccb-common`、`ccb-infrastructure`、`ccb-security`、`ccb-workflow`、`ccb-attachment` 的公开包。
- Flyway 仅新增 `V38__release_management.sql`；不修改 V1-V37，不向配置管理业务表写演示申请。
- 工作流是当前节点、任务、处理人、意见、签名和流程日志的唯一事实源；配置管理不得直接写平台私有表。
- 审批完成表示制品准出，不表示投产成功；生产版本只由有效的投产成功记录推导。
- 项目、物理子系统和交付单元选择源暂为前端 Mock，除此之外不得保留前端业务状态 Mock。
- 当前无法验证正式项目成员关系；后端必须执行租户、RBAC、申请人、实体状态和乐观锁校验，并在交付中披露项目数据范围限制。
- 不依赖已下线的输入项配置、`biz_form_*` 表或外部系统；不上传镜像和二进制制品。
- 桌面与 `375x812`、`390x844`、`430x932` 均需完成核心路径，不允许页面级横向溢出。

---

## 文件职责地图

| 路径 | 状态 | 单一职责 |
| --- | --- | --- |
| `server/src/modules/release/pom.xml` | candidate-new | `ccb-release` Maven 依赖与测试配置 |
| `server/src/modules/release/src/main/java/com/ccb/release/window/**` | candidate-new | 窗口持久化、规则、API 和审计 |
| `server/src/modules/release/src/main/java/com/ccb/release/application/**` | candidate-new | 申请快照、冲突、场景、状态与提交编排 |
| `server/src/modules/release/src/main/java/com/ccb/release/production/**` | candidate-new | 投产候选、结果审计和生产版本计算 |
| `server/src/modules/release/src/main/java/com/ccb/release/reporting/**` | candidate-new | 统计聚合与下钻 |
| `server/src/modules/release/src/main/java/com/ccb/release/integration/**` | candidate-new | 工作流生命周期和附件授权适配 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V38__release_management.sql` | candidate-new | 配置管理表、索引、菜单和权限的追加迁移 |
| `web/src/api/release.ts` | candidate-new | 正式配置管理 DTO 与 HTTP 调用 |
| `web/src/modules/release/release-master-data.mock.ts` | candidate-new | 仅提供项目、物理子系统、交付单元选择项 |
| `web/src/modules/release/**` | existing | 五个正式视图、详情审批和响应式交互 |
| `pom.xml`、`server/src/platform/boot/pom.xml` | existing | Maven 依赖管理、模块聚合和启动装配 |
| `governance/modules.yaml`、`docs/architecture/MODULES.md`、`.github/CODEOWNERS` | existing | 模块边界、公开包与所有权 |

## 任务依赖图与并行策略

```text
T1 模块与数据库基础
 ├─> T2 投产窗口后端
 └─> T3 版本申请核心后端（依赖 T2）
       └─> T4 投产基线与统计后端
             └─> T5 工作流与附件闭环
                   └─> T6 前端窗口与申请正式化
                         └─> T7 前端详情、基线和统计正式化
                               └─> T8 真实链路集成验收
```

所有任务串行执行。数据库、共享 DTO、同一前端类型文件和真实流程状态存在强依赖，当前没有可证明安全的并行写入组。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1-R2 | T1, T6, T7 |
| R3-R8 | T2, T3, T6 |
| R9-R12 | T5, T7 |
| R13-R17 | T4, T7 |
| R18-R19 | T1-T5 |
| R20-R21 | T8 |

### T1：建立 `ccb-release` 模块、数据库和治理基础

**需求映射：** R1, R19

**前置任务：** 无

**文件：**
- 新建：`server/src/modules/release/pom.xml`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/integration/package-info.java`
- 新建：`web/src/api/release.ts`
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V38__release_management.sql`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/ReleaseSchemaContractTest.java`
- 修改：`pom.xml`
- 修改：`server/src/platform/boot/pom.xml`
- 修改：`governance/modules.yaml`
- 修改：`docs/architecture/MODULES.md`
- 修改：`.github/CODEOWNERS`

**接口：**
- 消费：`ccb-common`、`ccb-infrastructure`、`ccb-security`、`ccb-workflow`、`ccb-attachment` 的现有 Maven artifact 和公开包。
- 产出：Maven artifact `ccb-release`；十二张 `rel_*` 业务表；`release:*` 菜单与操作权限；后续任务可编译的模块边界。

- [ ] **步骤 1：建立模块与迁移契约检查**
  - 在 `ReleaseSchemaContractTest` 断言迁移文件包含设计中的十二张表、关键唯一键和菜单权限，且不包含向 `rel_release_application`、`rel_production_entry` 插入演示业务行的语句。
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`
  - 预期：因 artifact 或迁移不存在而失败，失败点与 T1 目标一致。
- [ ] **步骤 2：追加模块和数据库结构**
  - 新建 `ccb-release` POM；在根 POM 的 dependency management、modules 和 boot 组合根中登记。
  - 创建公开 integration 包与前端 API 的最小所有权标记，使治理清单声明的路径在 T1 即可验证；实际接口行为留给 T5、T6。
  - `V38` 创建设计中的十二张表，统一包含租户、审计、业务唯一键、组合查询索引和适用的 `row_version`；仅初始化菜单和权限。
  - 在模块清单登记 `business/release`，公开包限定为 `com.ccb.release.integration`，Owner 为 `rokeyvvz0828`。
- [ ] **步骤 3：执行局部与治理检查**
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`
  - 运行：`node scripts/check-all-governance.mjs`
  - 预期：模块被 Maven 与治理脚本识别，V38 顺序合法，测试 0 失败。
- [ ] **步骤 4：记录检查点**
  - 保存实际 diff、两个命令退出码和迁移兼容说明到 T1 execution 证据；不在本任务自动提交或推送。

**验收：** `ccb-release` 可独立构建；V38 是唯一新增迁移；菜单、权限、Owner、依赖和模块文档一致；无业务演示数据。

**回滚：** 回退 Maven/治理/菜单装配代码；已执行的 V38 表保留为只读证据，不删除数据库表。

**停止条件：** V38 已被其他并行变更占用；现有 V35-V37 未落地；必须依赖未公开的平台包。

**升级条件：** 需要修改平台或 shared 公开契约，或现有脏改动与模块装配产生不可合并冲突。

### T2：交付真实投产窗口 API

**需求映射：** R4, R8, R18

**前置任务：** T1

**文件：**
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/window/model/ReleaseWindowCommand.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/window/model/ReleaseWindowItem.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/window/persistence/ReleaseWindowStore.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/window/service/ReleaseWindowService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/window/web/ReleaseWindowController.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/window/service/ReleaseWindowServiceTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/window/web/ReleaseWindowControllerTest.java`

**接口：**
- 消费：`AuthUser`、`PageQuery`、`PageResult`、`BusinessException`、`JdbcTemplate`。
- 产出：`GET/POST/PUT /api/release/windows`、`GET /api/release/windows/{id}`、`PUT /api/release/windows/{id}/regular-enabled`；响应包含动态状态、不可选原因和 `rowVersion`。

- [ ] **步骤 1：先写窗口规则和权限失败测试**
  - 覆盖四个时间严格有序、默认申报开始为当月 1 日 00:00、投产起止同年、同项目周期不重叠、项目/编码不可变、关闭/未开始/已进入投产期不可用于非应急申请、常规开关和过期 `rowVersion` 返回冲突。
  - 覆盖无 `release:window:*` 权限、跨租户详情和无修改原因被拒绝。
- [ ] **步骤 2：实现持久化和服务端规则**
  - `ReleaseWindowStore` 只访问 `rel_release_window` 与 `rel_window_change_log`。
  - `ReleaseWindowService` 生成 `WIN-yyyyMM-流水号`，从服务端当前时间计算状态，更新时保存字段前后值、原因、操作人和时间。
  - Controller 使用细分 `@PreAuthorize` 权限和统一响应。
- [ ] **步骤 3：运行窗口测试**
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am -Dtest=ReleaseWindowServiceTest,ReleaseWindowControllerTest test`
  - 预期：正常、边界、冲突和权限断言全部通过。
- [ ] **步骤 4：执行模块回归并记录证据**
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`
  - 预期：0 个失败；记录 SQL 范围和审计断言。

**验收：** 窗口刷新和服务重启后存在；不可修改项目；状态、可选性和开关由后端决定；更新有完整审计。

**回滚：** 移除窗口 Java 实现并关闭窗口菜单；保留 V38 表。

**停止条件：** 同项目窗口重叠定义无法按四个时间判定；数据库方言与 MySQL 8.4 不兼容。

**升级条件：** 需要正式项目成员接口才能完成当前规则，或权限码与现有角色模型冲突。

### T3：交付版本申请、快照、冲突和状态机 API

**需求映射：** R3, R5, R6, R7, R8, R18

**前置任务：** T2

**文件：**
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationCommand.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationDetail.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationPageItem.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseConflictResult.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseScenario.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/persistence/ReleaseApplicationStore.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseScenarioPolicy.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseConflictService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseApplicationController.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseScenarioPolicyTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/application/web/ReleaseApplicationControllerTest.java`

**接口：**
- 消费：T2 的窗口查询与可用性判断。
- 产出：申请列表、详情、创建、编辑、冲突检查、撤回、取消 API；`ReleaseScenario` 固定映射 `release.regular`、`release.regular.additional`、`release.regular.overdue`、`release.regular.overdue-additional`、`release.emergency`。

- [ ] **步骤 1：建立场景、字段和状态机失败测试**
  - 覆盖常规/紧急/应急日期边界、追加特征、一个物理子系统、至少一个且不重复的交付单元、版本仅禁止空格、非应急至少一个需求号、应急无需求号且应急说明必填。
  - 覆盖持久化 ID/编码/名称/制品类型快照、草稿编辑、审批中不可直接编辑、撤回回到可编辑、取消原因必填、整单状态和乐观锁。
- [ ] **步骤 2：实现申请存储和服务端派生**
  - 明细分别写入 application、delivery、requirement、attachment、relation 和 event 表，不使用逗号拼接结构化数据。
  - 服务端忽略客户端提交的权威版本类型和流程编码，自行重算并返回最近生产版本占位信息。
  - 冲突响应包含历史申请全部业务字段但排除附件，使用历史事实摘要生成确认令牌；事实变化后旧令牌返回 409。
- [ ] **步骤 3：实现状态动作和实体授权**
  - 研发人员仅修改本人且处于允许状态的申请；管理权限可按规则处理；所有查询按租户和项目快照过滤。
  - 撤回、取消和重新编辑写 `rel_application_event`，不物理删除历史数据。
- [ ] **步骤 4：运行聚焦测试和模块回归**
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am -Dtest=ReleaseScenarioPolicyTest,ReleaseApplicationServiceTest,ReleaseApplicationControllerTest test`
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`
  - 预期：场景、冲突、状态、并发和权限测试 0 失败。

**验收：** 申请业务事实全部落库；历史快照稳定；冲突可追溯且不覆盖旧申请；非法状态和并发覆盖被拒绝。

**回滚：** 关闭版本申请入口并回退申请 Java 代码；保留申请与事件表数据。

**停止条件：** 版本类型仍需客户端决定；一个交付单元出现多种制品类型；需求编号被要求立即接入未完成模块。

**升级条件：** 必须新增跨业务模块依赖，或产品规则无法确定某历史状态允许的冲突动作。

### T4：交付投产基线、生产版本和统计 API

**需求映射：** R13, R14, R15, R16, R17, R18

**前置任务：** T3

**文件：**
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/production/model/ProductionBaselineItem.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/production/model/ProductionResultCommand.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/production/model/ProductionVersionItem.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/production/persistence/ReleaseProductionStore.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/production/service/ReleaseProductionService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/production/web/ReleaseProductionController.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/reporting/model/ReleaseAnalyticsResult.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/reporting/service/ReleaseReportingService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/reporting/web/ReleaseReportingController.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/production/service/ReleaseProductionServiceTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/reporting/service/ReleaseReportingServiceTest.java`

**接口：**
- 消费：T2 窗口事实、T3 制品准出申请和交付单元快照。
- 产出：`GET /api/release/production-baselines`、`PUT .../entries/{entryId}/result`、生产版本/历史 API、统计 summary/drilldown API；供 T5 的审批通过事件调用 `refreshReleasedCandidates(applicationId)`。

- [ ] **步骤 1：先写候选和回算失败测试**
  - 覆盖同窗口同“子系统+交付单元”按审批完成时间和申请 ID 取最新准出，不按版本字符串排序。
  - 覆盖制品准出、投产成功、投产失败、未投产；失败和未投产原因必填，成功投产时间必填，任何变更保存前后值。
  - 覆盖成功改为失败后自动回退到更早的有效成功版本。
- [ ] **步骤 2：实现投产候选和生产版本查询**
  - 候选生成幂等；新准出只替换尚未确认的同维度候选来源，历史申请和结果日志不删除。
  - 生产版本由有效 `SUCCEEDED` 结果查询派生，不保存易失的当前指针。
  - 应急候选使用审批完成时的投产中窗口或最近未来窗口；提交前无承接窗口的校验由 T5 完成。
- [ ] **步骤 3：实现统计与下钻同源查询**
  - 汇总窗口、系统、交付单元、需求、申请、版本类型、制品准出和投产结果；筛选参数与下钻接口共享同一条件构造。
  - 服务端分页、限制聚合范围，并按租户和项目快照过滤。
- [ ] **步骤 4：运行聚焦与模块回归**
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am -Dtest=ReleaseProductionServiceTest,ReleaseReportingServiceTest test`
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`
  - 预期：候选替换、结果审计、历史回算和统计一致性测试 0 失败。

**验收：** 制品准出与投产结果分离；生产版本只反映最近有效投产成功；报表数字可由下钻明细复现。

**回滚：** 关闭投产和统计菜单并回退 Java 查询代码；保留候选与结果日志。

**停止条件：** 查询需要比较版本字符串才能决定最新；结果更新无法保留历史成功事实。

**升级条件：** 需要自动检测部署或外部制品库数据，超出本期边界。

### T5：闭合真实工作流、附件与申请生命周期

**需求映射：** R9, R10, R11, R12, R16, R18

**前置任务：** T4

**文件：**
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumer.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseAttachmentAccessPolicy.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseSubmissionServiceTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumerTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/integration/ReleaseAttachmentAccessPolicyTest.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseApplicationController.java`

**接口：**
- 消费：`WorkflowBusinessGateway.startByCode()`、`terminate()`、`progress()`；`WorkflowLifecycleConsumer`；`AttachmentGateway.bind/get/deleteBound`；T4 `refreshReleasedCandidates(applicationId)`。
- 产出：`POST /api/release/applications/{code}/submit`；`release_application` 生命周期消费者；附件访问策略；持久审批轮次和幂等回执。

- [ ] **步骤 1：建立五个流程编码与失败原子性测试**
  - 对五种场景断言固定流程编码、`WorkflowBusinessContext` 的 business key/title/round/project/action path/data digest。
  - 模拟未发布流程、附件无权、应急缺测试报告、无承接窗口和重复提交，断言申请不进入 `IN_REVIEW` 且不产生半轮次。
- [ ] **步骤 2：实现附件绑定和提交事务**
  - 提交前逐个读取并校验附件租户、上传人、状态和类别；应急至少一个 `TEST_REPORT`。
  - 成功启动真实流程后保存 definition ID/version、instance ID、round 和 digest；申请进入 `IN_REVIEW`。
  - 撤回/取消审批中申请通过 gateway 终止流程，再按业务原因落状态。
- [ ] **步骤 3：实现幂等生命周期消费**
  - `subscriberKey()` 固定且 `supports("release_application")`；以 event ID、active round 和 instance ID 三重校验。
  - `APPROVED` 更新为 `RELEASED` 并刷新 T4 候选；`RETURNED/REJECTED` 更新为 `RETURNED`；`TERMINATED` 只完成已发起的撤回或取消动作。
  - 重复、过期轮次和其他业务类型事件不得重复改变状态。
- [ ] **步骤 4：运行契约和回归测试**
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am -Dtest=ReleaseSubmissionServiceTest,ReleaseWorkflowLifecycleConsumerTest,ReleaseAttachmentAccessPolicyTest test`
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`
  - 预期：真实公共接口调用、失败回滚、幂等和过期事件测试 0 失败。

**验收：** 申请提交启动真实流程；审批事实不在业务模块复制；生命周期回写幂等；应急测试报告和附件访问由服务端校验。

**回滚：** 关闭提交权限和菜单；停止注册生命周期消费者；保留申请轮次、工作流实例和回执证据。

**停止条件：** `WorkflowBusinessGateway` 或 `AttachmentGateway` 不能满足已批准设计；跨模块事务出现可复现半提交。

**升级条件：** 需要修改平台公开接口、生命周期语义或电子签名实现。

### T6：正式化投产窗口与版本申请前端

**需求映射：** R2, R3, R4, R5, R6, R7, R8, R18

**前置任务：** T5

**文件：**
- 新建：`web/src/api/release.ts`
- 新建：`web/src/modules/release/release-master-data.mock.ts`
- 修改：`web/src/modules/release/types.ts`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/components/ReleaseWindowView.vue`
- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue`
- 修改：`web/src/modules/release/components/ReleaseApplicationDrawer.vue`
- 修改：`web/src/modules/release/components/ReleaseConflictDialog.vue`
- 修改：`web/src/router/index.ts`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**
- 消费：T2/T3/T5 正式 REST API、现有 `http.ts` 和 `attachments.ts`。
- 产出：服务端分页窗口与申请列表、真实草稿/提交/冲突操作、仅含三个选择源的 Mock 文件。

- [ ] **步骤 1：固定 TypeScript DTO 和请求层**
  - `release.ts` 覆盖设计第 7 节全部 API；写请求携带 `rowVersion`，错误保留 403/409 语义。
  - `types.ts` 删除 `ReleasePrototypeState` 和 `ReleaseDemoState` 业务依赖，区分服务端状态码和中文展示映射。
- [ ] **步骤 2：替换窗口和申请内存写入**
  - 页面首次进入、筛选、分页和操作后重新查询后端；不再直接修改数组。
  - 窗口选择显示完整四个时间、状态、可选标记和禁用原因；非应急版本类型只读显示服务端判定。
  - 申请表单从 selector Mock 取项目/子系统/交付单元，但保存 ID、编码、名称、制品类型快照；附件先上传平台再提交 attachment ID。
- [ ] **步骤 3：实现冲突和并发恢复体验**
  - 冲突弹框展示附件外全部历史内容，明确取消旧申请、修改旧申请、创建新申请的服务端允许动作。
  - 409 保留用户输入并要求刷新事实；提交按钮在请求中禁用，避免重复提交。
- [ ] **步骤 4：执行构建和静态检查**
  - 运行：`npm --prefix web run build`
  - 运行：`git diff --check -- web/src/api/release.ts web/src/modules/release web/src/router/index.ts`
  - 预期：Vue 类型检查和 Vite 构建通过，无空白、调试状态或原型重置入口。

**验收：** 窗口和申请刷新后保持；所有写操作走 API；三个选择源之外没有前端业务事实；失败、权限、并发和重复提交有明确状态。

**回滚：** 关闭正式菜单入口并回退前端 API 接线；后端数据保留。

**停止条件：** 后端 DTO 与计划接口不一致；现有原型文件存在用户未提交且不可安全合并的同区域修改。

**升级条件：** 需要修改公共 UI 组件、全局 store 或平台 HTTP 拦截器。

### T7：正式化详情审批、投产基线、生产版本与统计前端

**需求映射：** R2, R9, R10, R11, R12, R13, R14, R15, R16, R17, R21

**前置任务：** T6

**文件：**
- 修改：`web/src/modules/release/ReleaseApplicationDetailPage.vue`
- 修改：`web/src/modules/release/components/ReleaseApprovalPanel.vue`
- 修改：`web/src/modules/release/components/ReleaseBaselineView.vue`
- 修改：`web/src/modules/release/components/ReleaseCurrentProductionView.vue`
- 修改：`web/src/modules/release/components/ReleaseAnalyticsView.vue`
- 修改：`web/src/modules/release/components/ReleaseDetailDrawer.vue`
- 修改：`web/src/modules/release/components/ReleaseChart.vue`
- 修改：`web/src/modules/release/release-prototype.css`
- 删除：`web/src/modules/release/repository.ts`
- 删除：`web/src/modules/release/mock.ts`
- 删除或退役：`web/src/modules/release/ReleaseWorkflowReviewPage.vue`

**接口：**
- 消费：`release.ts` 正式业务接口、`workflow.ts` 当前任务/实例详情/审批动作、`attachments.ts` 预览下载。
- 产出：业务详情内审批、真实投产维护、生产版本和统计视图；任务中心只通过业务路由导航。

- [ ] **步骤 1：统一详情和审批事实**
  - 详情从 release API 获取业务数据和 active round instance ID，再从 workflow API 获取节点、任务、意见、签名和时间线。
  - `ReleaseApprovalPanel` 移除 `repository.recordWorkflowDecision`；审批成功后重新加载业务详情和工作流上下文。
  - 无当前任务时只读展示；有权限的当前处理人无论从待办还是申请列表进入，都能在详情内审批。
- [ ] **步骤 2：接入投产与报表 API**
  - 基线先选窗口，再查询候选并维护结果；投产失败/未投产及成功回改必须填写原因，成功时填写投产时间。
  - 生产版本和历史、统计汇总和下钻均从后端加载；图表与明细使用同一筛选条件。
- [ ] **步骤 3：彻底移除业务 Mock**
  - 删除内存 repository 和旧 `mock.ts`，确认 `rg "createReleasePrototypeState|useReleaseRepository|ReleasePrototypeState|ReleaseDemoState" web/src/modules/release` 无匹配。
  - 保留 `release-master-data.mock.ts`，其导出只包含项目、物理子系统和交付单元选择项。
- [ ] **步骤 4：完成响应式与全状态处理**
  - 桌面高密度表格在手机端改为卡片或受控局部滚动；审批主操作可达；弹层限制在视口内。
  - 覆盖加载、空、失败、403、409、只读、提交中、长单号和长意见。
- [ ] **步骤 5：构建和静态验收**
  - 运行：`npm --prefix web run build`
  - 运行：`rg "createReleasePrototypeState|useReleaseRepository|ReleasePrototypeState|ReleaseDemoState" web/src/modules/release`
  - 预期：构建通过；搜索退出码为 1 且无匹配文本。

**验收：** 详情左右审批信息来自同一真实流程；审批在业务详情完成；基线、生产版本和报表均使用后端事实；无业务 Mock 残留。

**回滚：** 关闭配置管理菜单；不恢复前端业务 Mock；保留后端和数据库事实供修复。

**停止条件：** 页面仍需模拟任务 ID、节点或审批结果；移动端需要整页横向滚动才能操作。

**升级条件：** 现有工作流 API 无法从业务键获取当前任务，或附件预览需要扩大平台接口。

### T8：通过真实 API 和工作流完成集成验收

**需求映射：** R1-R21

**前置任务：** T7

**文件：**
- 新建证据：`.ai-control/requirements/req-20260814-021-release-management/execution-T8.json`
- 新建证据：`.ai-control/requirements/req-20260814-021-release-management/observation-T8.json`
- 最终验收时新建：`.ai-control/requirements/req-20260814-021-release-management/convergence.json`
- 按阶段更新：`.ai-control/requirements/req-20260814-021-release-management/state.json`
- 按阶段更新：`.ai-control/requirements/req-20260814-021-release-management/handoff.json`

**接口：**
- 消费：T1-T7 全部 API、五个已发布流程编码、隔离本地数据库、浏览器。
- 产出：可重复的“窗口 -> 申请 -> 提交 -> 审批 -> 投产结果 -> 生产版本/统计”证据和收敛结论。

- [ ] **步骤 1：完成静态、模块和完整构建门禁**
  - 运行：`node scripts/check-all-governance.mjs`
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`
  - 运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn test`
  - 运行：`npm --prefix web run build`
  - 预期：全部退出码 0。
- [ ] **步骤 2：在隔离数据库启动正式服务**
  - 使用当前隔离验收数据库配置启动 `ccb-boot`，确认 Flyway 执行到 V38；不得连接或修改原始 `ccb_platform`。
  - 运行：`curl -s http://127.0.0.1:8080/actuator/health`
  - 预期：返回 `UP`，启动日志无 Flyway checksum 或表结构错误。
- [ ] **步骤 3：通过平台 API 创建并发布五个流程**
  - 创建并发布固定编码流程，配置测试审核、后续审核、允许动作和按流程需要的电子签名；不得直接插入工作流表。
  - 预期：五个编码均存在已发布版本，业务申请人不能选择流程定义。
- [ ] **步骤 4：只通过业务 API 建立验收数据**
  - 创建窗口、常规/紧急/应急申请，验证重复交付单元冲突、保存快照、附件绑定、提交和真实 workflow instance。
  - 使用具备任务权限的账号在业务详情审批，验证退回重提、重复事件幂等和制品准出状态。
  - 维护成功/失败/未投产，验证成功回改后的生产版本回算及统计汇总与下钻一致。
- [ ] **步骤 5：桌面和手机浏览器验收**
  - 路径：`/release`、`/release/applications/{code}`。
  - 视口：`1280x800`、`375x812`、`390x844`、`430x932`。
  - 检查：核心操作、刷新/返回、错误/无权限、长文本、重复点击、控制台、网络请求、页面级 `scrollWidth`、弹层和审批按钮可达性。
  - 预期：无业务 Mock 请求、无控制台错误、无页面级横向溢出，刷新后业务与流程事实一致。
- [ ] **步骤 6：独立观测并处理偏差**
  - 执行者写 execution；独立上下文或人工验证者写 observation。发现偏差回到对应 Tn，不直接标记完成。
  - 全部 R1-R21 有通过证据后才写 convergence 并将 phase 更新为 `converged`。

**验收：** 完整链路无需直接业务表插入；重启和刷新不丢数据；审批、基线、生产版本和报表一致；四个视口通过。

**回滚：** 停止本地服务、关闭配置管理菜单与模块装配；保留隔离库中的业务和审计证据，生产迁移不执行逆向删除。

**停止条件：** 任一构建失败、Flyway 历史异常、服务误连原始数据库、真实流程无法启动、权限越界、业务 Mock 残留或浏览器事实不一致。

**升级条件：** 需要修改已批准需求、平台公共契约、生产环境或外部系统；项目权限限制阻断正式验收结论。

## 集成检查

1. T1 后检查 Maven、V38、菜单和治理一致。
2. T3 后检查窗口与申请事务边界、快照和状态机。
3. T5 后检查工作流、附件和投产候选的跨模块公开契约。
4. T7 后检查前端不再包含业务 Mock，正式 API 构建通过。
5. T8 执行完整 Maven、Vue、治理、API、桌面和手机验收。

## 控制模型种子

以下仅为 `hypotheses-only` 候选，进入 `$model-engineering-system` 后必须重新验证：

- 被控边界候选：`ccb-release` 业务表、服务、API 和五个前端视图；平台工作流/附件/安全为外部已登记能力。
- 状态变量候选：窗口时间与开关、申请状态/轮次/rowVersion、工作流实例状态、投产结果、当前生产版本、统计聚合结果。
- 接口候选：Release REST API、`WorkflowBusinessGateway`、`WorkflowLifecycleConsumer`、`AttachmentGateway`、workflow/attachment 前端 API。
- 传感器候选：单元测试、治理检查、Flyway 版本、API 响应、数据库重启保持、工作流事件回执、浏览器网络/控制台/几何检查。
- 执行器候选：窗口和申请命令、工作流启动/终止/决定、生命周期消费、投产结果更新、前端刷新查询。
- 扰动候选：工作区既有改动、流程未发布、并发编辑、重复/乱序事件、附件上传失败、未来项目主数据契约变化。
- 时延候选：工作流生命周期投递重试、审批后页面刷新、附件上传与绑定、统计聚合查询。
- 假设：平台公开接口保持当前签名；推翻证据为编译或契约测试显示无法满足配置管理集成。

## 风险与用户批准

- 高风险动作：新增 Maven 模块和十二张业务表；接入真实工作流生命周期；维护可回算的投产结果；删除前端业务 Mock。
- 已知限制：正式项目成员和研发主数据模块未完成，首期只能保存快照并执行租户/RBAC/实体状态权限。
- 分支偏差：`rokey` 不符合仓库推荐的需求分支正则，但这是用户明确指定的开发分支；不自动改分支。
- 用户已于 2026-08-15 明确确认开发；`handoff_status` 更新为 `approved` 后导入工程控制闭环。

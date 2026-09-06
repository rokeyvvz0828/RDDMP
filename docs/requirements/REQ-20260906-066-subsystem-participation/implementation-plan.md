# 物理子系统参与人员与个人敏捷看板实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。先完成基准复核和系统建模，不得从本计划直接跳到执行。

**目标：** 系统参与资格、任务分工、执行和个人看板统一闭环。
**架构：** 架构模块持有系统人员关系，任务沿用现有人员字段和状态机；平台只通过公开能力复用。
**技术栈：** 现有Java/Maven、MySQL/Flyway、Vue/TypeScript/Element Plus，不新增依赖。

## 状态与来源
修订2；设计修订2；用户已确认。设计见同目录design.md，机器交接包见当前前缀handoff.json。
本轮scope仅允许文档。开始实施前必须修订scope，授权业务文件、精确追加迁移路径及测试文件；确认隔离分支/工作区和Owner专项复核。
未创建实施分支，未获公共能力审批，不启动数据库，不导入闭环，不生成执行通过证据。

## 全局约束
- 保留现有Java包和模块边界
- 本轮只写文档
- 数据库只追加迁移
- 前后端共同权限控制
- 中文文案和注释；不修改已发布Flyway脚本，不连接生产，不覆盖其他任务。
- 复用交付示范中心和公共UI；375×812、390×844、430×932及1280×800以上、明暗主题实测。
- 跨模块统一契约仅为设计后续接入，不在本实施范围修改其他业务模块。

## 文件职责地图
以下为候选实施边界，不代表当前scope可写；candidate-new文件尚不存在。
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/PhysicalSubsystemService.java` [existing]：系统参与关系与统一资格契约
- `web/src/modules/architecture/PhysicalSubsystemPage.vue` [existing]：系统参与关系与统一资格契约
- `web/src/modules/architecture/api.ts` [existing]：系统参与关系与统一资格契约
- `web/src/modules/architecture/types.ts` [existing]：系统参与关系与统一资格契约
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/SubsystemParticipationService.java` [candidate-new]：系统参与关系与统一资格契约
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/SubsystemParticipationStore.java` [candidate-new]：系统参与关系与统一资格契约
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/service/SubsystemParticipationServiceTest.java` [candidate-new]：系统参与关系与统一资格契约
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanGenerationService.java` [existing]：任务展开默认负责人、参与人及独立分派
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/persistence/PlanStore.java` [existing]：任务执行授权及个人/管理读取闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/web/PlanController.java` [existing]：替换当前搭建计划原有看板及分工交互
- `web/src/modules/architecture/PlanListPage.vue` [existing]：替换当前搭建计划原有看板及分工交互
- `web/src/modules/architecture/PlanDetailPage.vue` [existing]：替换当前搭建计划原有看板及分工交互
- `web/src/modules/architecture/planApi.ts` [existing]：替换当前搭建计划原有看板及分工交互
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PlanParticipationGenerationTest.java` [candidate-new]：任务展开默认负责人、参与人及独立分派
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanEngine.java` [existing]：任务执行授权及个人/管理读取闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanExecutionService.java` [existing]：任务执行授权及个人/管理读取闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanQueryService.java` [existing]：替换当前搭建计划原有看板及分工交互
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanBlockService.java` [existing]：任务执行授权及个人/管理读取闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanTimeService.java` [existing]：任务执行授权及个人/管理读取闭环
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PlanParticipationAuthorizationTest.java` [candidate-new]：任务执行授权及个人/管理读取闭环
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/web/PlanParticipationControllerTest.java` [candidate-new]：任务执行授权及个人/管理读取闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/service/EnvironmentResourceService.java` [existing]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/persistence/EnvironmentResourceStore.java` [existing]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanWorkOrderService.java` [existing]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanNotificationService.java` [existing]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkWorkOrderService.java` [existing]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessService.java` [existing]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitService.java` [existing]：资源申请及关联业务权限闭环
- `web/src/modules/architecture/ResourceRequestPage.vue` [existing]：资源申请及关联业务权限闭环
- `web/src/modules/architecture/InstanceListPage.vue` [existing]：资源申请及关联业务权限闭环
- `web/src/modules/architecture/DeploymentUnitPage.vue` [existing]：资源申请及关联业务权限闭环
- `web/src/modules/architecture/NetworkAccessPage.vue` [existing]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/environment/service/ResourceRequestParticipationTest.java` [candidate-new]：资源申请及关联业务权限闭环
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkParticipationTest.java` [candidate-new]：资源申请及关联业务权限闭环
- `web/src/modules/architecture/architecture.css` [existing]：替换当前搭建计划原有看板及分工交互
- `web/src/modules/architecture/components/PersonalTaskBoard.vue` [candidate-new]：替换当前搭建计划原有看板及分工交互
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PersonalTaskBoardTest.java` [candidate-new]：替换当前搭建计划原有看板及分工交互
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/ParticipationProjectIsolationMySqlTest.java` [candidate-new]：存量迁移、端到端权限回归与交付验收

数据库追加迁移版本不预占：T1在实施准入时盘点当前最大版本，确认具体新文件并获授权后再编码；未授权之前停止数据库实施。
相关模型、控制器、附件业务策略如果不在上述地图内，先在T1补精确文件及scope审批，禁止顺手扩写。

## 任务依赖与提交
T1 → T2 → T3 → T4 → T5 → T6。共用授权契约和计划文件，全部串行，不启动子Agent。
每个任务完成聚焦验证后建立独立提交；最终独立观测可以由另一个验收会话或人工复核，不能把执行者自测冒充独立验收。

## 需求覆盖
- R1：T1, T6
- R2：T2, T5, T6
- R3：T1, T2, T3, T6
- R4：T3, T5, T6
- R5：T5, T6
- R6：T4, T6
- R7：T3, T4, T6
- R8：T1, T2, T5, T6

## T1：系统参与关系与统一资格契约

**需求映射：** R1, R3, R8
**前置任务：** 无；先批准计划与实施scope
**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/PhysicalSubsystemService.java`
- 修改：`web/src/modules/architecture/PhysicalSubsystemPage.vue`
- 修改：`web/src/modules/architecture/api.ts`
- 修改：`web/src/modules/architecture/types.ts`
- 候选新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/SubsystemParticipationService.java`
- 候选新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/SubsystemParticipationStore.java`
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/SubsystemParticipationServiceTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/SubsystemParticipationServiceTest.java`

**接口消费：** 既有系统负责人字段、平台公开用户/项目能力
**接口产出：** 候选 ParticipationView(ownerUserId, explicitParticipantUserIds, effectiveParticipantUserIds, rowVersion)；候选 requireSystemParticipant(actor, projectId, subsystemId)；后续任务只调用统一资格服务

- [ ] T1-S1：按验收矩阵在列出的测试文件建立失败测试，先确认旧实现不满足目标；既有行为已通过者记录基准。
预期：同项目系统A/B成员严格隔离；有效集合去重且含负责人。；系统负责人退出或用户停用后实时失效；退出前阻塞未完责任移交；成员版本冲突不覆盖他人修改。
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T1.json`（实施时生成，本轮不创建通过记录）。

- [ ] T1-S2：运行聚焦测试，记录基准及失败原因，不将编译错误冒充业务失败。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=SubsystemParticipationServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：新需求断言在实现前失败或既有行为基准通过，退出码与原因可追踪
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T1.json`（实施时生成，本轮不创建通过记录）。

- [ ] T1-S3：冻结读取/更新人员API、任务分工DTO、管理权限码映射和精确追加迁移路径；再实现参与关系存储与系统详情人员维护。迁移路径未授权前不得写SQL。
预期：仅修改批准后的任务文件和迁移，不触碰其他任务改动
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T1.json`（实施时生成，本轮不创建通过记录）。

- [ ] T1-S4：重复聚焦测试并执行架构模块回归；前端任务同时生产构建。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=SubsystemParticipationServiceTest -Dsurefire.failIfNoSpecifiedTests=false; mvn -pl :ccb-architecture -am test; npm --prefix web run build
```
预期：分别记录每条命令退出码，均为0且无断言失败
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/observation-T1.json`（实施时生成，本轮不创建通过记录）。

- [ ] T1-S5：检查差异后只暂存当前任务明确文件，建立小步提交。实施分支获授权后执行，不能git add全仓。
```powershell
git diff --check
```
预期：无空白错误，无范围外修改；提交信息：feat: 系统参与关系与统一资格契约
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T1.json`（实施时生成，本轮不创建通过记录）。

**验收输入与断言：**
- 同项目系统A/B成员严格隔离；有效集合去重且含负责人。
- 系统负责人退出或用户停用后实时失效；退出前阻塞未完责任移交；成员版本冲突不覆盖他人修改。

**回滚：** 回退本任务独立提交，保留成员和审计数据；权限故障先暂停受影响写入口，不能恢复旧宽权限。
**停止条件：** 计划未批准、实际路径未纳入scope、迁移版本未授权或测试出现越权时停止推进。
**升级条件：** 需要改平台/公共能力/其他模块、变更管理角色语义或覆盖他人改动时提交Owner及用户复核。

## T2：任务展开默认负责人、参与人及独立分派

**需求映射：** R2, R3, R8
**前置任务：** T1
**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanGenerationService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/persistence/PlanStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/web/PlanController.java`
- 修改：`web/src/modules/architecture/PlanListPage.vue`
- 修改：`web/src/modules/architecture/PlanDetailPage.vue`
- 修改：`web/src/modules/architecture/planApi.ts`
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PlanParticipationGenerationTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PlanParticipationGenerationTest.java`

**接口消费：** T1 ParticipationView及requireSystemParticipant
**接口产出：** 生成预览：模板任务+目标类型+目标ID稳定键、ownerUserId、participantUserIds；生成提交和分派更新：校验有效系统人员；负责人原子加入任务参与人

- [ ] T2-S1：按验收矩阵在列出的测试文件建立失败测试，先确认旧实现不满足目标；既有行为已通过者记录基准。
预期：系统A负责人甲、成员乙丙展开后默认甲负责、甲乙丙参与；单任务改乙负责只影响该任务。；按部署单元取其所属系统；无负责人不能提交；新系统成员不进入旧任务；修改系统负责人不重写任务。；更换任务负责人后旧人保留参与；公共任务按项目资格校验，不依赖虚构系统。
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T2.json`（实施时生成，本轮不创建通过记录）。

- [ ] T2-S2：运行聚焦测试，记录基准及失败原因，不将编译错误冒充业务失败。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=PlanParticipationGenerationTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：新需求断言在实现前失败或既有行为基准通过，退出码与原因可追踪
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T2.json`（实施时生成，本轮不创建通过记录）。

- [ ] T2-S3：修改生成预览/提交及任务分派，采用任务级覆盖而非计划全局名单覆盖所有展开任务；创建提交重新校验预览以来的资格变化。
预期：仅修改批准后的任务文件和迁移，不触碰其他任务改动
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T2.json`（实施时生成，本轮不创建通过记录）。

- [ ] T2-S4：重复聚焦测试并执行架构模块回归；前端任务同时生产构建。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=PlanParticipationGenerationTest -Dsurefire.failIfNoSpecifiedTests=false; mvn -pl :ccb-architecture -am test; npm --prefix web run build
```
预期：分别记录每条命令退出码，均为0且无断言失败
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/observation-T2.json`（实施时生成，本轮不创建通过记录）。

- [ ] T2-S5：检查差异后只暂存当前任务明确文件，建立小步提交。实施分支获授权后执行，不能git add全仓。
```powershell
git diff --check
```
预期：无空白错误，无范围外修改；提交信息：feat: 任务展开默认负责人、参与人及独立分派
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T2.json`（实施时生成，本轮不创建通过记录）。

**验收输入与断言：**
- 系统A负责人甲、成员乙丙展开后默认甲负责、甲乙丙参与；单任务改乙负责只影响该任务。
- 按部署单元取其所属系统；无负责人不能提交；新系统成员不进入旧任务；修改系统负责人不重写任务。
- 更换任务负责人后旧人保留参与；公共任务按项目资格校验，不依赖虚构系统。

**回滚：** 回退本任务独立提交，保留成员和审计数据；权限故障先暂停受影响写入口，不能恢复旧宽权限。
**停止条件：** 计划未批准、实际路径未纳入scope、迁移版本未授权或测试出现越权时停止推进。
**升级条件：** 需要改平台/公共能力/其他模块、变更管理角色语义或覆盖他人改动时提交Owner及用户复核。

## T3：任务执行授权及个人/管理读取闭环

**需求映射：** R3, R4, R7
**前置任务：** T2
**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanEngine.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanExecutionService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanQueryService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanBlockService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanTimeService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/persistence/PlanStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/web/PlanController.java`
- 修改：`web/src/modules/architecture/PlanDetailPage.vue`
- 修改：`web/src/modules/architecture/planApi.ts`
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PlanParticipationAuthorizationTest.java`
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/web/PlanParticipationControllerTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PlanParticipationAuthorizationTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/web/PlanParticipationControllerTest.java`

**接口消费：** T1统一系统资格；T2任务分工
**接口产出：** 个人查询 view=mine 与有管理资格的 view=management；服务端对象 canView/canManage/canExecute；管理员不豁免requireTaskExecutor

- [ ] T3-S1：按验收矩阵在列出的测试文件建立失败测试，先确认旧实现不满足目标；既有行为已通过者记录基准。
预期：仅系统成员但非任务人员不能执行；管理者能查看授权计划全部任务但启动/勾选/重开返回禁止。；个人列表、详情、报告、时间视图、分页总数仅含合法本人任务；不可见前置任务不返回详情。；任务参与人被移除后，重放旧完成请求失败；取消/恢复仍保留原管理职责与审计。
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T3.json`（实施时生成，本轮不创建通过记录）。

- [ ] T3-S2：运行聚焦测试，记录基准及失败原因，不将编译错误冒充业务失败。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=PlanParticipationAuthorizationTest,PlanParticipationControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：新需求断言在实现前失败或既有行为基准通过，退出码与原因可追踪
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T3.json`（实施时生成，本轮不创建通过记录）。

- [ ] T3-S3：将查询范围下推SQL，拆分管理与执行判断，去掉管理员执行放行；所有检查项和任务写入口回溯任务、系统和项目校验，事务内防止撤销竞态。
预期：仅修改批准后的任务文件和迁移，不触碰其他任务改动
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T3.json`（实施时生成，本轮不创建通过记录）。

- [ ] T3-S4：重复聚焦测试并执行架构模块回归；前端任务同时生产构建。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=PlanParticipationAuthorizationTest,PlanParticipationControllerTest -Dsurefire.failIfNoSpecifiedTests=false; mvn -pl :ccb-architecture -am test; npm --prefix web run build
```
预期：分别记录每条命令退出码，均为0且无断言失败
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/observation-T3.json`（实施时生成，本轮不创建通过记录）。

- [ ] T3-S5：检查差异后只暂存当前任务明确文件，建立小步提交。实施分支获授权后执行，不能git add全仓。
```powershell
git diff --check
```
预期：无空白错误，无范围外修改；提交信息：feat: 任务执行授权及个人/管理读取闭环
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T3.json`（实施时生成，本轮不创建通过记录）。

**验收输入与断言：**
- 仅系统成员但非任务人员不能执行；管理者能查看授权计划全部任务但启动/勾选/重开返回禁止。
- 个人列表、详情、报告、时间视图、分页总数仅含合法本人任务；不可见前置任务不返回详情。
- 任务参与人被移除后，重放旧完成请求失败；取消/恢复仍保留原管理职责与审计。

**回滚：** 回退本任务独立提交，保留成员和审计数据；权限故障先暂停受影响写入口，不能恢复旧宽权限。
**停止条件：** 计划未批准、实际路径未纳入scope、迁移版本未授权或测试出现越权时停止推进。
**升级条件：** 需要改平台/公共能力/其他模块、变更管理角色语义或覆盖他人改动时提交Owner及用户复核。

## T4：资源申请及关联业务权限闭环

**需求映射：** R6, R7
**前置任务：** T3
**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/service/EnvironmentResourceService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/persistence/EnvironmentResourceStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanWorkOrderService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanNotificationService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkWorkOrderService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitService.java`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue`
- 修改：`web/src/modules/architecture/InstanceListPage.vue`
- 修改：`web/src/modules/architecture/DeploymentUnitPage.vue`
- 修改：`web/src/modules/architecture/NetworkAccessPage.vue`
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/environment/service/ResourceRequestParticipationTest.java`
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkParticipationTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/environment/service/ResourceRequestParticipationTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkParticipationTest.java`

**接口消费：** T1统一系统资格；T3任务可见与执行能力
**接口产出：** 资源申请目标筛选与逐项实体授权；通知及关联工单目标授权；流程办理不依赖申请人参与身份

- [ ] T4-S1：按验收矩阵在列出的测试文件建立失败测试，先确认旧实现不满足目标；既有行为已通过者记录基准。
预期：申请人参与A不参与B，混合A/B部署单元申请整体拒绝；创建、复制、编辑、提交均拒绝替换目标ID。；被移除人员的草稿不能提交；审批人/办理人未参与系统仍能履行已分配流程动作。；源系统参与人可以申请合法目录目标网络访问，不需要加入目标系统；非法目标和不可见工单关联拒绝。；资源、附件、通知跳转、搜索与导出按对象范围校验，既有平台能力不足时停止该接入而非修改平台绕过。
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T4.json`（实施时生成，本轮不创建通过记录）。

- [ ] T4-S2：运行聚焦测试，记录基准及失败原因，不将编译错误冒充业务失败。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=ResourceRequestParticipationTest,NetworkParticipationTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：新需求断言在实现前失败或既有行为基准通过，退出码与原因可追踪
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T4.json`（实施时生成，本轮不创建通过记录）。

- [ ] T4-S3：收紧业务选择源和写入服务，保留申请人规则及流程回调职责；逐一盘点实际附件、报告、通知入口，无该入口的对象记录不适用证据。
预期：仅修改批准后的任务文件和迁移，不触碰其他任务改动
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T4.json`（实施时生成，本轮不创建通过记录）。

- [ ] T4-S4：重复聚焦测试并执行架构模块回归；前端任务同时生产构建。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=ResourceRequestParticipationTest,NetworkParticipationTest -Dsurefire.failIfNoSpecifiedTests=false; mvn -pl :ccb-architecture -am test; npm --prefix web run build
```
预期：分别记录每条命令退出码，均为0且无断言失败
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/observation-T4.json`（实施时生成，本轮不创建通过记录）。

- [ ] T4-S5：检查差异后只暂存当前任务明确文件，建立小步提交。实施分支获授权后执行，不能git add全仓。
```powershell
git diff --check
```
预期：无空白错误，无范围外修改；提交信息：feat: 资源申请及关联业务权限闭环
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T4.json`（实施时生成，本轮不创建通过记录）。

**验收输入与断言：**
- 申请人参与A不参与B，混合A/B部署单元申请整体拒绝；创建、复制、编辑、提交均拒绝替换目标ID。
- 被移除人员的草稿不能提交；审批人/办理人未参与系统仍能履行已分配流程动作。
- 源系统参与人可以申请合法目录目标网络访问，不需要加入目标系统；非法目标和不可见工单关联拒绝。
- 资源、附件、通知跳转、搜索与导出按对象范围校验，既有平台能力不足时停止该接入而非修改平台绕过。

**回滚：** 回退本任务独立提交，保留成员和审计数据；权限故障先暂停受影响写入口，不能恢复旧宽权限。
**停止条件：** 计划未批准、实际路径未纳入scope、迁移版本未授权或测试出现越权时停止推进。
**升级条件：** 需要改平台/公共能力/其他模块、变更管理角色语义或覆盖他人改动时提交Owner及用户复核。

## T5：替换当前搭建计划原有看板及分工交互

**需求映射：** R2, R4, R5, R8
**前置任务：** T4
**文件：**
- 修改：`web/src/modules/architecture/PlanDetailPage.vue`
- 修改：`web/src/modules/architecture/planApi.ts`
- 修改：`web/src/modules/architecture/architecture.css`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanQueryService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/web/PlanController.java`
- 候选新建：`web/src/modules/architecture/components/PersonalTaskBoard.vue`
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PersonalTaskBoardTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/service/PersonalTaskBoardTest.java`

**接口消费：** T3个人/管理读取和对象能力；T2分工预览及更新接口
**接口产出：** 当前计划原有看板原位替换为四列敏捷看板，固定planId，不新增跨计划筛选；同一看板内授权管理者切换当前计划全部任务，手机状态页签和单列任务卡片

- [ ] T5-S1：按验收矩阵在列出的测试文件建立失败测试，先确认旧实现不满足目标；既有行为已通过者记录基准。
预期：NOT_STARTED/IN_PROGRESS/COMPLETED/BLOCKED与WAITING_PRECEDING准确分列，取消默认隐藏，计数与实际本人卡片一致。；看板默认仅展示当前计划本人相关任务；管理者可在同一看板切换当前计划全部任务，无执行资格不显示执行操作；其他计划任务不得混入。；切项目晚到响应不得覆盖新项目；375/390/430宽无整页横溢；明暗主题、提交失败和无权限有明确反馈。
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T5.json`（实施时生成，本轮不创建通过记录）。

- [ ] T5-S2：运行聚焦测试，记录基准及失败原因，不将编译错误冒充业务失败。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=PersonalTaskBoardTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：新需求断言在实现前失败或既有行为基准通过，退出码与原因可追踪
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T5.json`（实施时生成，本轮不创建通过记录）。

- [ ] T5-S3：在PlanDetailPage.vue现有看板页签原位替换内容，可抽取本模块组件；复用UiToolbar、卡片/抽屉和语义色，不新增看板路由或跨计划聚合，不新建状态机，不提供拖拽绕过检查项完成。
预期：仅修改批准后的任务文件和迁移，不触碰其他任务改动
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T5.json`（实施时生成，本轮不创建通过记录）。

- [ ] T5-S4：重复聚焦测试并执行架构模块回归；前端任务同时生产构建。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=PersonalTaskBoardTest -Dsurefire.failIfNoSpecifiedTests=false; mvn -pl :ccb-architecture -am test; npm --prefix web run build
```
预期：分别记录每条命令退出码，均为0且无断言失败
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/observation-T5.json`（实施时生成，本轮不创建通过记录）。

- [ ] T5-S5：检查差异后只暂存当前任务明确文件，建立小步提交。实施分支获授权后执行，不能git add全仓。
```powershell
git diff --check
```
预期：无空白错误，无范围外修改；提交信息：feat: 替换当前搭建计划原有看板及分工交互
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T5.json`（实施时生成，本轮不创建通过记录）。

**验收输入与断言：**
- NOT_STARTED/IN_PROGRESS/COMPLETED/BLOCKED与WAITING_PRECEDING准确分列，取消默认隐藏，计数与实际本人卡片一致。
- 看板默认仅展示当前计划本人相关任务；管理者可在同一看板切换当前计划全部任务，无执行资格不显示执行操作；其他计划任务不得混入。
- 切项目晚到响应不得覆盖新项目；375/390/430宽无整页横溢；明暗主题、提交失败和无权限有明确反馈。

**回滚：** 回退本任务独立提交，保留成员和审计数据；权限故障先暂停受影响写入口，不能恢复旧宽权限。
**停止条件：** 计划未批准、实际路径未纳入scope、迁移版本未授权或测试出现越权时停止推进。
**升级条件：** 需要改平台/公共能力/其他模块、变更管理角色语义或覆盖他人改动时提交Owner及用户复核。

## T6：存量迁移、端到端权限回归与交付验收

**需求映射：** R1, R2, R3, R4, R5, R6, R7, R8
**前置任务：** T5
**文件：**
- 候选新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/ParticipationProjectIsolationMySqlTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/plan/ParticipationProjectIsolationMySqlTest.java`

**接口消费：** T1-T5契约与迁移；原始系统负责人及既有任务人员
**接口产出：** 权限矩阵证据、迁移差异报告、浏览器验收和上线/安全回退清单

- [ ] T6-S1：按验收矩阵在列出的测试文件建立失败测试，先确认旧实现不满足目标；既有行为已通过者记录基准。
预期：新建数据库与升级数据库均满足关系唯一约束；旧任务名单未批量扩大；异常负责人被识别且不能执行。；两个项目、两系统、管理员、任务成员、仅系统成员、退出成员、审批/办理人全矩阵通过。；完整测试、生产构建和全部指定视口实测记录；未执行部分不写通过。
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T6.json`（实施时生成，本轮不创建通过记录）。

- [ ] T6-S2：运行聚焦测试，记录基准及失败原因，不将编译错误冒充业务失败。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=ParticipationProjectIsolationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：新需求断言在实现前失败或既有行为基准通过，退出码与原因可追踪
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T6.json`（实施时生成，本轮不创建通过记录）。

- [ ] T6-S3：用虚构数据重放权限矩阵和并发撤销场景，追加权限集成测试，执行全部验证并记录独立观测；问题回到对应任务修正后重测。
预期：仅修改批准后的任务文件和迁移，不触碰其他任务改动
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T6.json`（实施时生成，本轮不创建通过记录）。

- [ ] T6-S4：重复聚焦测试并执行架构模块回归；前端任务同时生产构建。
```powershell
mvn -pl :ccb-architecture -am test -Dtest=ParticipationProjectIsolationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false; mvn -pl :ccb-architecture -am test
```
预期：分别记录每条命令退出码，均为0且无断言失败
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/observation-T6.json`（实施时生成，本轮不创建通过记录）。

- [ ] T6-S5：检查差异后只暂存当前任务明确文件，建立小步提交。实施分支获授权后执行，不能git add全仓。
```powershell
git diff --check
```
预期：无空白错误，无范围外修改；提交信息：feat: 存量迁移、端到端权限回归与交付验收
证据：`.ai-control/requirements/req-20260906-066-subsystem-participation/execution-T6.json`（实施时生成，本轮不创建通过记录）。

**验收输入与断言：**
- 新建数据库与升级数据库均满足关系唯一约束；旧任务名单未批量扩大；异常负责人被识别且不能执行。
- 两个项目、两系统、管理员、任务成员、仅系统成员、退出成员、审批/办理人全矩阵通过。
- 完整测试、生产构建和全部指定视口实测记录；未执行部分不写通过。

**回滚：** 回退本任务独立提交，保留成员和审计数据；权限故障先暂停受影响写入口，不能恢复旧宽权限。
**停止条件：** 计划未批准、实际路径未纳入scope、迁移版本未授权或测试出现越权时停止推进。
**升级条件：** 需要改平台/公共能力/其他模块、变更管理角色语义或覆盖他人改动时提交Owner及用户复核。

## 集成和运行验收
- `mvn -pl :ccb-architecture -am test`：退出码0，分别保留输出；不能用构建代替运行验收
- `mvn test`：退出码0，分别保留输出；不能用构建代替运行验收
- `npm --prefix web run build`：退出码0，分别保留输出；不能用构建代替运行验收
- `node scripts/check-all-governance.mjs`：退出码0，分别保留输出；不能用构建代替运行验收
- `node scripts/check-flyway-migrations.mjs`：退出码0，分别保留输出；不能用构建代替运行验收
- `git diff --check`：退出码0，分别保留输出；不能用构建代替运行验收
- 范围检查使用scripts/check-codex-scope.mjs的--scope、--base、--head，在独立需求提交范围中检查；当前共享脏工作区不能用全仓差异冒充本任务范围。
- 本地虚构数据创建两个项目和两个系统；逐角色验证列表、详情、选项、完整报告、关联工单、资源申请、附件和通知跳转。
- 核心浏览器路径：系统维护人员 → 创建计划预览默认分工 → 修改单任务负责人/成员 → 成员进入当前搭建计划原有看板页签执行 → 管理者查看全貌且执行被拒 → 成员资源申请 → 撤销成员后旧请求被拒。
- 服务端API测试必须直接构造跨项目/跨系统ID、管理员执行、撤销后重放请求，不能只点隐藏按钮。
- 测试并发成员移除与检查项完成，确认不出现撤销成功后仍凭旧资格提交的动作。
- 浏览器记录角色、路由、视口、请求/响应、控制台、刷新、返回、重复提交、长文本、网络失败及未保存保护。

## 控制模型种子
handoff.control_seed仅为hypotheses-only；成员撤销、晚到响应和存量异常为扰动候选，需在闭环建模阶段验证。

## 高风险动作及用户批准
本计划修订2已获用户确认；后续scope中的实际文件、数据库追加迁移、权限改造及隔离实施工作区仍须通过实施准入。
Owner审批尚未取得；不得把用户同意产品规则等同于数据库/公共能力Owner审批。
本轮完成标准仅为需求、设计、计划、交接包自检；产品尚未实现、运行或验收。

## 当前实施状态（2026-09-06 更新）
用户已批准在当前目录独立需求分支开发，不使用 worktree。此前待授权及仅文档描述作为历史记录，不再代表当前准入状态。当前分支为 feat/REQ-20260906-066-subsystem-participation，正式控制阶段见当前前缀 control-state.json。仅 scope 精确路径可写；其余路径不得写入。

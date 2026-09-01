# 架构子系统全生命周期实施计划

> 执行要求：本计划经用户整体批准后，使用 `control-engineering` 导入并按任务包实施。设计已批准，当前计划状态不授权修改产品代码。

## 状态与来源

- 计划修订：2
- 设计修订：2
- 设计文档：`docs/engineering-control/designs/2026-08-22-architecture-subsystem-lifecycle-design.md`
- 需求文档：`docs/requirements/REQ-20260822-048-architecture-subsystem-lifecycle/requirement.md`
- 状态：可移交
- 分支：`feat/REQ-20260822-048-architecture-subsystem-lifecycle`
- 基线提交：`e5b57473beabc9d2a9c2c63bd32635b2f708d8a8`
- 修订 2：T1 编号与迁移约束统一为全系统逻辑序号、每逻辑物理槽位；业务行仍按租户隔离。

## 目标与全局约束

**唯一目标：** 在现有 `business/architecture` 内交付逻辑/物理子系统强类型工单、确定性编号、真实审批发布、状态与引用约束、响应式页面和兼容迁移，使所有发布写入只发生在当前工作流轮次批准事件中。

全局约束：

1. 只修改 scope 的 `writable_paths`；workflow/system/security/shared/release 和 `business/ai` 只读或禁止。
2. 不修改 V1—V81；合并前 V82—V84 或稳定 ID 冲突时停止并重新分配，不能覆盖他人迁移。
3. 未批准申请不得写主记录；审批人不得修改业务字段；管理人员可以自申请自审批但不能绕过流程。
4. 逻辑 CREATE 可带 `0..N` 个物理草稿，批准全成或全不成；其他变更不级联。
5. 编号首次提交分配；RETURNED 保留；未批准的 REJECTED/CANCELLED 释放；已发布永久占用。
6. 物理普通 UPDATE 不能改父级；归属迁移必须使用 REPLACE，原子创建新记录、下线旧记录并留关系。
7. 引用检查异常时拒绝作废；AI provider 始终 no-op，不发生真实 AI/网络调用。
8. 租户只来自 `AuthUser`；服务端校验 RBAC、本人/管理数据范围、实体、行版本、工作流任务和轮次。
9. 每个任务先建立失败传感器，再做限幅实现；局部通过后才进入下一任务。每个小步提交前运行局部测试与 `git diff --check`。
10. 本机 Testcontainers 验证只在 Maven 子进程设置 `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`、`DOCKER_API_VERSION=1.44` 和带引号的 docker-java 参数，不改 `~/.testcontainers.properties`。

## 已测基线

- 开发入口：通过。
- 前端构建：通过。
- MySQL 8.4 / Flyway 聚焦迁移：1/1 通过。
- architecture 完整测试：45/47 通过；唯一失败类 `PhysicalSubsystemConcurrencyMySqlTest` 错误固定 Flyway target V36，导致 V77 表不存在。T1 必须先修复并获得完整绿色基线。
- 隔离工作树在准入文档写入前无产品 diff；原 checkout 的用户未跟踪文件不在本计划工作树内。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V93__architecture_subsystem_lifecycle.sql` | candidate-new | 主表兼容扩展、申请/编号/锁/历史/替换/回执表及既有内部序号迁移 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V94__seed_architecture_subsystem_lifecycle.sql` | candidate-new | 菜单 803、三级权限、旧权限映射、角色 110 与 admin 本地绑定 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V95__seed_architecture_subsystem_workflow.sql` | candidate-new | 固定流程 `architecture.subsystem.change` 及 ROLE 审批节点 |
| `server/src/modules/architecture/pom.xml` | existing | 增加 `ccb-workflow` 公开契约依赖 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/**` | existing | 发布主记录增加状态、内部编号、行版本和关联摘要 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/repository/ArchitectureSubsystemRepository.java` | existing | 发布查询、审批事务内受控写入和主记录行锁 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/model/SubsystemChangeModels.java` | candidate-new | 工单命令、查询、状态、类型、快照和响应强类型模型 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/persistence/SubsystemChangeStore.java` | candidate-new | 申请、草稿、锁、值保留、历史、轮次和回执 SQL |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/persistence/SubsystemNumberStore.java` | candidate-new | 命名空间、回收池和活动编号 reservation 行锁 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/number/SubsystemNumberStrategy.java` | candidate-new | 编号策略接口 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/number/FixedPrefixIncrementalSubsystemNumberStrategy.java` | candidate-new | `A0001` 与 `W00011` 的固定前缀/递增实现 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/service/SubsystemChangeService.java` | candidate-new | 草稿、编辑、提交、取消、权限、目标锁和值保留 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/service/SubsystemPublicationService.java` | candidate-new | 批准事件重校验与原子发布、释放和历史 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/service/SubsystemReferenceGuard.java` | candidate-new | 内部和外部引用检查汇总及 fail-closed |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/integration/SubsystemReferenceChecker.java` | candidate-new | 公开引用检查 SPI 与中性结果类型 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/suggestion/SubsystemSuggestionProvider.java` | candidate-new | 建议 provider 内部接口 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/suggestion/DeterministicSubsystemSuggestionProvider.java` | candidate-new | 参数/规则本地建议 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/suggestion/NoopAiSubsystemSuggestionProvider.java` | candidate-new | 空 AI provider，无依赖、无调用 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/integration/ArchitectureWorkflowLifecycleConsumer.java` | candidate-new | 工作流事件幂等、轮次校验和发布 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/web/SubsystemChangeApplicationController.java` | candidate-new | 工单列表、草稿、详情、提交、取消和建议 REST |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/LogicalSubsystemController.java` | existing | GET 保持，写接口改为 409 工单提示 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/PhysicalSubsystemController.java` | existing | GET 保持，写接口改为 409 工单提示 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/ArchitectureExceptionAdvice.java` | existing | 新业务错误码与 400/409/503 映射 |
| `server/src/modules/architecture/src/test/**` | existing | 单元、MockMvc、MySQL、并发、工作流和兼容传感器 |
| `web/src/modules/architecture/api.ts` | existing | 主数据与工单 REST DTO 调用 |
| `web/src/modules/architecture/types.ts` | existing | 发布状态、工单、草稿、差异、历史和建议类型 |
| `web/src/modules/architecture/LogicalSubsystemPage.vue` | existing | 发布逻辑只读列表/详情和发起变更 |
| `web/src/modules/architecture/PhysicalSubsystemPage.vue` | existing | 发布物理只读列表/详情和发起变更 |
| `web/src/modules/architecture/SubsystemChangeApplicationListPage.vue` | candidate-new | 本人/全部工单响应式列表 |
| `web/src/modules/architecture/SubsystemChangeApplicationFormPage.vue` | candidate-new | 全页逻辑/物理工单表单和动态卡片 |
| `web/src/modules/architecture/SubsystemChangeApplicationDetailPage.vue` | candidate-new | 快照、差异、历史、工作流时间线和审批 |
| `web/src/modules/architecture/components/LogicalSubsystemFormDrawer.vue` | existing-delete | 移除主数据直接新增/编辑抽屉 |
| `web/src/modules/architecture/components/PhysicalSubsystemFormDrawer.vue` | existing-delete | 移除主数据直接新增/编辑抽屉 |
| `web/src/modules/architecture/components/SubsystemChangePhysicalCard.vue` | candidate-new | 动态物理子系统卡片 |
| `web/src/modules/architecture/components/SubsystemChangeTimeline.vue` | candidate-new | 业务历史与流程时间线 |
| `web/src/modules/architecture/architecture.css` | existing | 桌面/移动布局、滚动边界和状态样式 |
| `web/src/router/index.ts` | existing | 工单列表、新建和详情路由 |
| `governance/modules.yaml` | existing | workflow 依赖和 architecture 公开包 |
| `docs/architecture/MODULES.md` | existing | 模块依赖与公共 SPI 说明 |
| `docs/integration/architecture-module-contract.md` | existing | 新 REST、生命周期、权限、兼容和 SPI 契约 |

## 任务依赖图与并行策略

```text
T1 数据与编号基座
  -> T2 工单领域与受控发布
      -> T3 工作流、权限和目录闭环
          -> T4 响应式前端
              -> T5 真实集成与收敛验收
```

任务全部串行。T1—T3 共享数据库与 Java 契约，T4 消费稳定 API，T5 使用共享 MySQL、Boot 和浏览器运行资源；并行会放大迁移、DTO 或验收噪声。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T1、T2、T4、T5 |
| R2 | T2、T3、T4、T5 |
| R3 | T1、T2、T3、T4、T5 |
| R4 | T1、T2、T4、T5 |
| R5 | T1、T2、T3、T4、T5 |
| R6 | T1、T2、T3、T4、T5 |
| R7 | T2、T4、T5 |
| R8 | T2、T3、T4、T5 |
| R9 | T1、T3、T4、T5 |
| R10 | T4、T5 |
| R11 | T1、T2、T3、T5 |
| R12 | T1—T5 |

### T1：建立绿色迁移基线、生命周期数据模型和编号策略

#### 需求映射与前置事实

- 映射：R1、R3、R4、R5、R6、R9、R11、R12。
- 前置：计划已批准并导入；V82—V84、菜单 803、权限 8031—8033、角色 110、流程 ID 900000000000030/31 仍未占用。
- 已知基线：并发测试 target V36 早于 V77，必须先单独修复。

#### 文件边界与接口

修改：

- `server/src/modules/architecture/src/test/java/com/ccb/architecture/service/PhysicalSubsystemConcurrencyMySqlTest.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/LogicalSubsystem.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/PhysicalSubsystem.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/repository/ArchitectureSubsystemRepository.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureMigrationMySqlTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureCatalogMigrationMySqlTest.java`

新建：

- `server/src/platform/infrastructure/src/main/resources/db/migration/V93__architecture_subsystem_lifecycle.sql`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/model/SubsystemChangeModels.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/persistence/SubsystemChangeStore.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/persistence/SubsystemNumberStore.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/number/SubsystemNumberStrategy.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/number/FixedPrefixIncrementalSubsystemNumberStrategy.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/change/number/FixedPrefixIncrementalSubsystemNumberStrategyTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureSubsystemLifecycleMySqlTest.java`

产出接口：`SubsystemNumberStrategy.reserve/release`；业务 store 显式 tenant，编号 store 使用全系统逻辑 namespace 和每逻辑物理 namespace；发布记录状态/内部序号/行版本查询。

#### 操作步骤、命令和预期信号

1. 重扫迁移和稳定 ID：`rg --files server/src/platform/infrastructure/src/main/resources/db/migration` 与精确 ID 搜索。预期 V82—V84 和所有候选 ID 无匹配；有冲突立即停止。
2. 仅移除并发测试的错误 `.target(V36)`，让其迁移到当前最新版本；运行完整 architecture 基线。预期 47/47，若出现新失败先记录为基线反馈，不叠加产品代码。
3. 先写 V82 迁移测试：空库、V81 增量、既有 code 不变、全系统逻辑序号稳定且唯一、全系统逻辑 9999/每逻辑物理 35 超限失败、表/唯一键/default/nullability。
4. 追加 V82：扩展两个主表；创建申请、草稿、历史、目标锁、值保留、替换、轮次、回执、编号 namespace/recycled/reservation 表；回填内部序号/槽位。
5. 先写策略和并发失败测试，再实现固定前缀策略与 MySQL 行锁存储；覆盖 `1..9,A..Z`、最小回收值、RETURNED 保留、批准消费、容量和并发唯一。
6. 运行聚焦和模块测试：
   - `mvn -pl :ccb-architecture -am "-Dtest=ArchitectureSubsystemLifecycleMySqlTest,FixedPrefixIncrementalSubsystemNumberStrategyTest,PhysicalSubsystemConcurrencyMySqlTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
   - `mvn -pl :ccb-architecture -am test`
   - `node scripts/check-flyway-migrations.mjs`
   - `git diff --check`
7. 候选提交边界：`feat(architecture): establish lifecycle numbering storage`。

#### 验收、证据与回滚

- 验收：完整 architecture 基线绿色；V81→V82 和空库均通过；既有 code 不变；编号格式、容量、回收和并发可判定。
- 证据：T1 execution/observation、Surefire 报告、迁移 SQL 结构断言、编号并发结果、diff check。
- 回滚：回退 Java/测试提交；V82 一旦进入任何共享环境必须保留，后续以补偿迁移禁用入口，不 DROP 表或回改 code。
- 停止：V82 冲突；既有数据超出容量；迁移需改 V77/V78；并发测试不能稳定复现。
- 升级：编号范围、历史槽位占用或旧 code 兼容必须改变已批准规则。

### T2：交付强类型工单、引用/建议边界和受控发布服务

#### 需求映射与前置事实

- 映射：R1—R8、R11、R12。
- 前置：T1 数据模型与编号传感器通过；本任务不启动真实工作流，提交协调在 T3 接线。

#### 文件边界与接口

新建：

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/service/SubsystemChangeService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/service/SubsystemPublicationService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/service/SubsystemReferenceGuard.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/integration/SubsystemReferenceChecker.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/suggestion/SubsystemSuggestionProvider.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/suggestion/DeterministicSubsystemSuggestionProvider.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/suggestion/NoopAiSubsystemSuggestionProvider.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/web/SubsystemChangeApplicationController.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/change/service/SubsystemChangeServiceTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/change/service/SubsystemPublicationServiceTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/change/service/SubsystemReferenceGuardTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/change/web/SubsystemChangeApplicationControllerTest.java`

修改：T1 的 `SubsystemChangeModels` / `SubsystemChangeStore`、现有 logical/physical model、query、repository、service、controller、Advice 及其测试；同时在 `governance/modules.yaml` 和 `docs/architecture/MODULES.md` 登记 `com.ccb.architecture.integration` 公开 SPI。T3 仍负责追加 workflow 依赖声明。

产出 REST：工单分页/创建/详情/更新/取消/建议；提交服务预留 gateway 协作者；旧写接口 409。

#### 操作步骤、命令和预期信号

1. 先写状态机、权限矩阵、本人/全部范围、目标锁、值保留、逻辑 0..N 物理、物理 REPLACE、状态/引用、旧写 409 和 no-op AI 失败测试。
2. 扩展 T1 强类型模型/store 并实现 service；DRAFT 可编辑，RETURNED 保留锁/编号并可编辑，审批人无业务更新入口。
3. 实现 `SubsystemPublicationService`：在事务中重锁申请/目标，重验摘要、行版本、唯一值、编号、父状态和引用；按类型原子发布并追加历史。
4. 实现引用 SPI/guard：内部逻辑—物理检查常驻，外部 provider 空集合健康，异常对 VOID 返回不可判定并 fail-closed。
5. 实现本地建议和空 AI provider；断言无 `business/ai` import、HTTP client 或外部调用。
6. 修改现有主数据服务/Controller：GET 增加状态和关联摘要，写方法在权限校验后立即返回 `ARCHITECTURE_WORK_ORDER_REQUIRED`，不调用 repository 写方法。
7. 运行：
   - `mvn -pl :ccb-architecture -am "-Dtest=SubsystemChangeServiceTest,SubsystemPublicationServiceTest,SubsystemReferenceGuardTest,SubsystemChangeApplicationControllerTest,LogicalSubsystemControllerTest,PhysicalSubsystemControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
   - `mvn -pl :ccb-architecture -am test`
   - `rg -n "com\.ccb\.ai|server/src/modules/ai|WebClient|RestClient" server/src/modules/architecture`
   - `git diff --check`
8. 候选提交边界：`feat(architecture): add subsystem change domain`。

#### 验收、证据与回滚

- 验收：所有申请与发布规则在无 Web 的服务测试中可判定；逻辑级联和 REPLACE 失败零部分写入；旧写接口数据库无变化；无真实 AI。
- 证据：状态转换矩阵、MySQL 前后计数、权限/租户 MockMvc、引用异常、import/网络静态搜索。
- 回滚：回退 T2 Java/API；保留 V82 空表和兼容列。旧 GET 可继续工作。
- 停止：需要修改 shared/security/system；公开 SPI 需要业务模块反向依赖；强类型表不能表达已批准字段。
- 升级：用户要求审批人编辑发布值、直接改父级或引入真实 AI。

### T3：接入真实工作流并完成权限、目录和兼容迁移

#### 需求映射与前置事实

- 映射：R2、R3、R5、R6、R8、R9、R11、R12。
- 前置：T2 工单/发布服务稳定；workflow 公开接口签名已核验。

#### 文件边界与接口

新建：

- `server/src/platform/infrastructure/src/main/resources/db/migration/V94__seed_architecture_subsystem_lifecycle.sql`
- `server/src/platform/infrastructure/src/main/resources/db/migration/V95__seed_architecture_subsystem_workflow.sql`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/integration/ArchitectureWorkflowLifecycleConsumer.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/change/integration/ArchitectureWorkflowLifecycleConsumerTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/change/integration/ArchitectureWorkflowIntegrationMySqlTest.java`

修改：

- `server/src/modules/architecture/pom.xml`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/service/SubsystemChangeService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/persistence/SubsystemChangeStore.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/web/SubsystemChangeApplicationController.java`
- `governance/modules.yaml`
- `docs/architecture/MODULES.md`

消费：`WorkflowBusinessGateway.startByCode/terminate/progress`、`WorkflowLifecycleConsumer`。产出：business type、subscriber key、当前轮次持久化和幂等回执。

#### 操作步骤、命令和预期信号

1. 先写提交失败原子性与生命周期测试：流程未发布、空角色、启动失败、重复/乱序/旧摘要事件、退回重提、拒绝、取消、批准发布失败重试、自申请自审批。
2. POM/治理只增加 `platform/workflow` 和公开 `com.ccb.architecture.integration`；先运行 module-boundary 负/正信号。
3. V83 创建菜单 803、权限 8031—8033、角色 110，迁移旧权限并给 tenant 1 admin 绑定管理角色；发布 GET/选项兼容旧读取与新三级权限；角色 110 绑定既有 workflow 根 200/收件箱 202 以复用当前任务 API，但不绑定定义/监控/已办菜单。SQL 必须幂等且不删除旧权限记录。
4. V84 以 `DRAFT` 创建 definition 900000000000030/version 900000000000031：`ROLE` 110、ANY、空处理人 ERROR、只允许 APPROVE/RETURN/REJECT；不得在没有真实 Flowable deployment 时伪标 `PUBLISHED`，真实集成测试通过既有发布入口完成部署。
5. 提交服务在业务事务校验并建立轮次/锁/编号后调用 `startByCode`，保存返回 definition/version/instance；失败必须回滚全部业务准备。
6. consumer 固定 `architecture.subsystem.change.lifecycle.v1`；按 event、tenant、business key、instance、round、digest 校验。APPROVED 调 T2 发布，RETURNED 保留，REJECTED 释放，TERMINATED 仅确认取消请求。
7. 运行：
   - `mvn -pl :ccb-architecture -am "-Dtest=ArchitectureWorkflowLifecycleConsumerTest,ArchitectureWorkflowIntegrationMySqlTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
   - `mvn -pl :ccb-workflow -am test`
   - `mvn -pl :ccb-architecture -am test`
   - `node scripts/check-module-boundaries.mjs`
   - `node scripts/check-all-governance.mjs`
   - `node scripts/check-flyway-migrations.mjs`
   - `git diff --check`
8. 候选提交边界：`feat(architecture): integrate subsystem approval workflow`。

#### 验收、证据与回滚

- 验收：真实定义可发布/启动并解析 ROLE；管理用户可处理本人申请；批准后才发布；重复/旧事件无副作用；发布失败可重试。
- 证据：workflow definition JSON、instance/task/round/receipt 数据、生命周期测试和模块边界输出。
- 回滚：停止注册 consumer、关闭提交入口并回退 Java；V83/V84 和历史保留，后续补偿迁移撤权/隐藏菜单。
- 停止：公开 workflow 契约不足；需要改 workflow 内部；事件失败不能安全重试；候选 ID 冲突。
- 升级：需要职责分离、动态审批人或新增工作流动作。

### T4：交付主数据只读页和全页工单体验

#### 需求映射与前置事实

- 映射：R1—R10、R12。
- 前置：T3 REST/权限/工作流静态契约及两项 P1 可达性纠偏已复验；真实 Flowable 发布/start/task 仍由 T5 运行。先复核 delivery-showcase 页面和 `design-h5.md` 的响应式规范。

#### 文件边界与接口

新建：

- `web/src/modules/architecture/SubsystemChangeApplicationListPage.vue`
- `web/src/modules/architecture/SubsystemChangeApplicationFormPage.vue`
- `web/src/modules/architecture/SubsystemChangeApplicationDetailPage.vue`
- `web/src/modules/architecture/components/SubsystemChangePhysicalCard.vue`
- `web/src/modules/architecture/components/SubsystemChangeTimeline.vue`

修改：`api.ts`、`types.ts`、`utils.ts`、`architecture.css`、两个主数据 Page、`SubsystemDetailDrawer.vue`、`web/src/router/index.ts`。

删除：两个直接写主数据的 FormDrawer。

消费：T2/T3 REST、只读 `web/src/api/workflow.ts`、现有 UI 组件和 auth store。

#### 操作步骤、命令和预期信号

1. 固定 TypeScript DTO、状态/类型中文映射和请求 API；409 保留后端错误码/冲突申请，工作流决定继续走平台 API。
2. 主数据页移除新增/编辑/删除直写；按状态展示并提供有权限的工单入口；物理详情显示逻辑，逻辑详情分页显示物理。
3. 工单列表：view/apply 默认本人，manage 可切换全部；桌面表格、移动卡片；覆盖筛选、分页、加载/空/失败/403。
4. 全页表单：类型驱动字段；逻辑 CREATE 上方逻辑区、下方 0..N 物理卡片；草稿“待生成”，提交后展示保留编号；dirty 字段不被建议覆盖。
5. 详情：提交快照/当前发布差异、业务历史、工作流时间线；当前任务 + manage 时显示 APPROVE/RETURN/REJECT，不提供业务字段编辑。
6. 处理重复点击、提交中、409 保留输入、离开脏表单确认、长文本、弹层高度、粘性操作栏和明暗主题。
7. 运行：
   - `npm --prefix web run build`
   - `rg -n "LogicalSubsystemFormDrawer|PhysicalSubsystemFormDrawer" web/src/modules/architecture web/src/router/index.ts`
   - `rg -n "ai|suggest" web/src/modules/architecture/api.ts` 并人工确认没有真实 AI URL/请求。
   - `git diff --check`
8. 候选提交边界：`feat(architecture): add subsystem work order pages`。

#### 验收、证据与回滚

- 验收：构建通过；无主数据直写 UI；逻辑 0/1/N 物理卡片均可保存提交；审批区域不编辑业务字段；移动端无页面级横向滚动。
- 证据：构建日志、静态搜索、关键状态 DOM/截图和网络调用。
- 回滚：回退路由和页面到只读主数据；不恢复直写抽屉；后端数据保留。
- 停止：需要修改共享 UI/http/auth；后端 DTO 不稳定；移动端只能靠整页横滚完成核心操作。
- 升级：需要改变已批准全页布局、权限降级或建议采用规则。

### T5：真实 API/工作流/浏览器集成与收敛验收

#### 需求映射与前置事实

- 映射：R1—R12。
- 前置：T1—T4 局部观察无开放反馈；隔离数据库、Docker、Boot/Vite 端口不影响现有服务。

#### 文件边界与接口

修改：

- `docs/integration/architecture-module-contract.md`
- `docs/architecture/MODULES.md`
- 当前前缀 `.ai-control/requirements/req-20260822-048-architecture-subsystem-lifecycle/*.json`

不通过手工业务表写入制造验收结果；测试数据只走迁移种子和业务 API。

#### 操作步骤、命令和预期信号

1. 完整静态/构建门禁：development-entry、scope、module-boundaries、Flyway、all-governance、architecture/workflow 模块、`mvn test`、前端 build、diff check，全部退出码 0。
2. 在隔离 MySQL 空库启动 Boot，确认 Flyway 到 V84、health UP；不得连接原开发库或生产。
3. 通过业务 API 与真实工作流验证：逻辑单独 CREATE、逻辑 CREATE + 多物理、独立物理 CREATE、UPDATE、OFFLINE/REACTIVATE/VOID、REPLACE、退回重提、自审批、拒绝和取消。
4. 并发/失败验证：同目标双提交、逻辑 9999/物理 35 容量、编号回收最小值、重复/旧事件、发布中唯一冲突、引用 provider 异常；断言无部分主记录。
5. 权限/兼容：view/apply/manage/无权限/tenant 2；本人/全部；旧 GET；旧写 409 前后数据不变；审计 trace 可查。
6. 浏览器视口 `1280x800`、`375x812`、`390x844`、`430x932`，明暗主题完成列表、全页表单、动态卡片、详情审批、错误恢复；检查 console、network、scrollWidth、弹层和操作可达性。
7. 独立观察 execution 证据并原子化反馈；偏差回到对应 Tn，纠偏后复验同一传感器。所有 R1—R12 通过、开放反馈为 0 后写 convergence。
8. 候选提交边界：`docs(architecture): finalize lifecycle contract and evidence`；未经用户另行要求不推送、不创建 PR、不合并或发布。

#### 验收、证据与回滚

- 验收：真实 MySQL/Flyway、真实 workflow instance/task、真实 HTTP、重启保持和四视口浏览器全部闭环；无真实 AI 请求；治理通过。
- 证据：完整命令/退出码、脱敏 API/数据库/workflow/审计、截图、网络/控制台/几何、control convergence。
- 回滚：停止本地服务；按 T4→T3→T2→T1 逆序回退应用提交，保留迁移和历史；补偿迁移关闭入口。
- 停止：任何环境误连非隔离库、迁移 checksum 异常、真实流程无法启动、权限越界、主数据部分发布或浏览器事实与 API 不一致。
- 升级：需要生产访问、外部业务 provider、平台公共接口修改、真实 AI 或改变已批准需求。

## 集成检查

1. T1 后：完整 architecture 基线从 45/47 收敛到 47/47，V82 与编号并发通过。
2. T2 后：无 workflow 也可独立验证工单领域、发布事务、引用和 no-op AI；旧写无副作用。
3. T3 后：固定工作流、三级权限和生命周期事件闭环，module boundaries/Flyway 全绿。
4. T4 后：前端 build 通过，主数据只读、工单全页入口和审批入口一致。
5. T5：所有模块、真实运行、浏览器和治理一起采样后才能判定收敛。

## 控制模型种子

- 被控边界候选：architecture 主记录、申请、编号、锁、历史、引用 guard、workflow consumer、REST 和 Vue 页面。
- 状态变量候选：主记录状态/rowVersion；申请状态/round/digest；目标锁；编号 namespace/recycled/reservation；workflow instance/task/receipt；前端 dirty/submitting/conflict 状态。
- 接口候选：architecture REST、`WorkflowBusinessGateway`、`WorkflowLifecycleConsumer`、`SubsystemReferenceChecker`、workflow 前端 API。
- 传感器候选：JUnit/MockMvc、Testcontainers MySQL、Flyway schema/history、HTTP/DB/workflow 对账、操作审计、Vue build、浏览器网络/控制台/几何、治理脚本。
- 执行器候选：草稿命令、submit/cancel、workflow decision/lifecycle consume、编号 reserve/release/consume、原子 publish、页面重新查询。
- 扰动候选：并发提交、重复/乱序事件、流程无处理人、编号容量、引用 provider 故障、旧数据规模、用户工作树改动、Docker API 版本。
- 时延候选：工作流事件重试、批准后业务状态回写、页面刷新、MySQL 容器启动。
- 假设：workflow 当前公开契约可满足固定 ROLE 流程；推翻证据为编译/真实流程测试必须修改 workflow 内部。

## 风险与用户批准

- 风险等级：高。
- 高增益点：编号回收并发、V82 既有数据回填、批准事件原子发布、REPLACE 双记录变化、引用 fail-closed、旧写行为变化。
- 已知基线偏差：并发测试 target V36；T1 限幅修复并复验，不把它误报为新功能回归。
- 设计批准：用户已于 2026-08-22 批准全部关键产品决定。
- 计划批准：用户于 2026-08-22 明确回复“批准，使用子代理驱动开发”，批准本计划修订 1 和 handoff；执行阶段按用户要求使用子代理承担有界实现与独立观测，主线程保持唯一控制器。

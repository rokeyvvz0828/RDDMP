# 移除逻辑子系统模型实施计划

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-31-remove-logical-subsystem-design.md`
- 需求目录：`docs/requirements/REQ-20260831-001-remove-logical-subsystem/`
- 状态：可移交
- 用户批准依据：用户在 2026-08-31 选择方案 B，并回复“可以”确认落盘和实施。

## 目标与全局约束

目标是把架构模块从“逻辑子系统 + 物理子系统父子模型”收敛为“物理子系统单一主模型”：删除独立逻辑模型、逻辑表、逻辑页面和逻辑接口；物理子系统新增可选逻辑子系统文本、业务组件编号字典字段，并改为由申请人自行填写物理子系统编号。

全局约束：

- 仅修改 `REQ-20260831-001-remove-logical-subsystem/codex-task-scope.yaml` 的 `writable_paths` 覆盖文件。
- Flyway 只追加 `V147__remove_logical_subsystem_model.sql`，不修改历史迁移。
- 不触碰生产系统、真实数据、密钥、平台字典管理页面、动态表单元数据或其他业务模块私有表。
- 逻辑接口删除是有意破坏性变更；部署单元、资源申请、环境实例、网络访问和搭建计划必须继续以 ACTIVE 物理子系统作为引用根。
- 直接物理 CRUD 仍保持需要工单，不恢复绕过审批的主数据写入。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V147__remove_logical_subsystem_model.sql` | candidate-new | 迁移物理主表和工单草稿表，删除逻辑结构，种子业务组件字典分类。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/*Physical*` | existing | 承载物理主模型、查询、命令和选项 DTO。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/repository/ArchitectureSubsystemRepository.java` | existing | 物理主表分页、详情、唯一性和写入 SQL。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/PhysicalSubsystemService.java` | existing | 物理查询视图、权限、租户、字典校验和工单入口提示。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/**` | existing | 物理子系统变更工单、草稿、提交、发布和历史详情。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/*Logical*` | existing | 删除逻辑 HTTP 入口。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/LogicalSubsystemService.java` | existing | 删除逻辑服务。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/LogicalSubsystem*.java` | existing | 删除逻辑模型 DTO。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/ArchitectureOptionsService.java` | existing | 移除逻辑选项，增加 `ARCH_BUSINESS_COMPONENT` 读取。 |
| `web/src/modules/architecture/**` | existing | 移除逻辑页面/API/路由入口，改造物理列表、详情和工单表单。 |
| `web/src/router/index.ts` | existing | 删除逻辑子系统路由和菜单映射。 |
| `mock/mock-data.json` | existing | 删除逻辑表样例，补充物理逻辑文本和业务组件编号。 |
| `docs/integration/architecture-module-contract.md` | existing | 更新架构模块公开契约。 |
| `governance/modules.yaml`、`docs/architecture/MODULES.md` | existing | 如有公开包或表所有权变化，更新模块边界说明。 |
| `server/src/modules/architecture/src/test/**` | existing | 调整物理、选项、工单、迁移和下游聚焦测试。 |

## 任务依赖图与并行策略

任务串行执行：

```text
T1 数据迁移和数据契约 -> T2 后端物理主模型 -> T3 变更工单发布链路 -> T4 前端入口和表单 -> T5 Mock/文档/验证
```

不并行的原因：T1 改变数据库列和删除表，T2/T3/T4 都消费同一契约；T3 和 T4 同时修改工单字段语义；T5 依赖最终代码和迁移状态。

## 需求覆盖表

| 需求 | 覆盖任务 |
| --- | --- |
| R1 退役逻辑模型和入口 | T2、T3、T4、T5 |
| R2 追加迁移删除逻辑结构 | T1、T5 |
| R3 物理可选逻辑文本 | T1、T2、T3、T4、T5 |
| R4 业务组件编号字典 | T1、T2、T3、T4、T5 |
| R5 人工填写物理编号 | T1、T2、T3、T4、T5 |
| R6 下游继续引用物理子系统 | T2、T3、T5 |
| R7 权限、租户、审计和固定表单 | T2、T3、T5 |
| R8 前端桌面和移动端可用 | T4、T5 |

### T1 数据迁移和数据契约

#### 需求映射与前置事实

覆盖 R2、R3、R4、R5、R7。当前 `V77` 创建逻辑表和物理逻辑外键，`V93` 增加逻辑序号、物理槽位、逻辑/物理草稿和子系统编号保留表。

#### 文件边界与接口

允许写入 `V147__remove_logical_subsystem_model.sql`、迁移测试和 mock 数据。迁移必须先回填物理逻辑文本，再删除逻辑外键、逻辑草稿和编号保留结构。

#### 操作步骤、命令和预期信号

1. 新增 `logical_subsystem_name`、`business_component_code` 和物理草稿 `code` 字段。
2. 从旧逻辑主表回填物理主表和物理草稿逻辑文本。
3. 删除 `arch_logical_subsystem`、`arch_subsystem_logical_draft`、子系统编号保留表及逻辑/槽位列。
4. 种子 `ARCH_BUSINESS_COMPONENT` 系统参数分类和演示选项。
5. 运行迁移结构检查或聚焦 MySQL 测试。

#### 验收、证据与回滚

验收：迁移后逻辑表不存在；物理表无逻辑外键和 `number_slot`；物理表存在 `logical_subsystem_name`、`business_component_code`、人工 code 唯一约束。证据为 SQL 结构断言、测试输出和 diff。回滚依赖数据库备份或追加补偿迁移。

#### 停止和升级条件

发现架构模块外仍有数据库外键引用逻辑表时停止；发现生产发布必须保留逻辑历史表时升级用户和发布负责人。

### T2 后端物理主模型

#### 需求映射与前置事实

覆盖 R1、R3、R4、R5、R6、R7。物理子系统查询、详情和下游选项是架构主数据消费者的入口。

#### 文件边界与接口

允许写入物理模型、仓储、服务、控制器、选项服务和对应测试；禁止修改系统字典管理实现。

#### 操作步骤、命令和预期信号

1. 移除 `LogicalSubsystemController/Service/Model/Option/Lock/Query` 对外生产代码。
2. 改造 `PhysicalSubsystem`、`PhysicalSubsystemCommand`、`PhysicalSubsystemQuery` 和 `PhysicalSubsystemView` 字段。
3. 改造 `ArchitectureSubsystemRepository` 的物理 SQL，不再 join 或读取逻辑表。
4. 在物理服务中校验人工编号、可选逻辑文本和 `ARCH_BUSINESS_COMPONENT` 字典项。
5. 更新 `ArchitectureOptionsController/Service`，删除逻辑选项并增加业务组件选项。

#### 验收、证据与回滚

验收：后端无逻辑 HTTP mapping；物理列表/详情返回 `logicalSubsystemName` 和 `businessComponentCode`；无效业务组件返回 400；重复人工编号返回 409；下游物理选项仍可用。回滚为恢复本任务开始前 diff。

#### 停止和升级条件

如果保留旧逻辑接口成为兼容性硬要求，停止并回到设计；如果字典接口不支持所需禁用过滤，停止建模。

### T3 变更工单发布链路

#### 需求映射与前置事实

覆盖 R1、R3、R4、R5、R6、R7。历史工单表仍保留，但新流程只能提交物理子系统目标。

#### 文件边界与接口

允许写入 `server/src/modules/architecture/src/main/java/com/ccb/architecture/change/**` 和相关测试。

#### 操作步骤、命令和预期信号

1. 将 `PhysicalDraftInput/PhysicalDraft` 改为包含 `code`、`logicalSubsystemName`、`businessComponentCode`，移除 `targetLogicalSubsystemId` 和 `reservedNumberSlot`。
2. 禁止新建 LOGICAL 目标工单，详情读取不再访问逻辑草稿表。
3. 删除物理编号自动分配和逻辑父级锁定；发布时使用草稿人工编号并保持编号租户内永久唯一。
4. 调整 CREATE/UPDATE/REPLACE/RETIRE/VOID 的发布和历史快照。
5. 更新工单控制器测试和服务测试。

#### 验收、证据与回滚

验收：物理 CREATE 工单缺少 code 返回 400；人工 code 发布后成为物理主表 code；重复历史 code 返回 409；旧逻辑工单不能继续发布；取消/驳回不再释放编号池。证据为单元/集成测试输出和源码扫描。回滚为恢复本任务开始前 diff。

#### 停止和升级条件

如果历史 LOGICAL 工单详情必须完整重放旧草稿，需重新评估保留归档表；如果工作流事件必须兼容逻辑目标发布，停止并重规划。

### T4 前端入口和表单

#### 需求映射与前置事实

覆盖 R1、R3、R4、R5、R8。前端当前存在逻辑列表页、逻辑 API、物理页逻辑筛选和工单逻辑级联。

#### 文件边界与接口

允许写入 `web/src/modules/architecture/**` 和 `web/src/router/index.ts`；不修改共享 UI 和系统字典页面。

#### 操作步骤、命令和预期信号

1. 删除逻辑页面、逻辑 API 函数、逻辑菜单和逻辑路由。
2. 改造物理页面筛选、表格、移动卡片和详情字段。
3. 改造子系统变更新建/编辑/详情页面，目标仅保留 PHYSICAL；物理卡片增加编号、可选逻辑文本和业务组件编号选择器。
4. 保留加载、空、失败、无权限、提交中和脏表单状态。
5. 运行前端构建，必要时做桌面和手机视口检查。

#### 验收、证据与回滚

验收：逻辑路由不可达；物理页面无逻辑外键选择；业务组件选择器可加载；编号字段参与提交；`npm build` 通过。回滚为恢复本任务开始前 diff。

#### 停止和升级条件

如果前端仍有必须消费旧逻辑接口的页面，停止并回到 T2/T3；如果移动验收发现布局超出当前组件能力，升级用户确认是否追加 UI 工作。

### T5 Mock、文档和最终验证

#### 需求映射与前置事实

覆盖 R1 到 R8。最终交付必须更新演示数据、公开契约和控制账本，并如实区分自动化、运行和浏览器 UAT。

#### 文件边界与接口

允许写入 mock、架构契约文档、模块边界文档、测试和本需求 `.ai-control` 账本文件。

#### 操作步骤、命令和预期信号

1. 更新 `mock/mock-data.json` 和模块契约说明。
2. 扫描 `LogicalSubsystem`、`logical_subsystem_id`、`number_slot`、逻辑路由等残留，并裁决必要保留的历史兼容字段。
3. 运行后端聚焦测试、迁移测试和前端构建。
4. 运行 scope/diff 检查和 `git diff --check`。
5. 记录 execution、observation 和 convergence 产物。

#### 验收、证据与回滚

验收：需求覆盖表全部有证据；最终 P0/P1 为 0；无范围外修改；未执行的 UAT 明确披露。回滚为代码 revert；数据库回滚依赖备份或补偿迁移。

#### 停止和升级条件

如果全量迁移因 Testcontainers/Docker 不可用失败，要区分环境扰动和业务断言；如果 scope 检查发现范围外修改，停止并拆分或请求授权。

## 集成检查

- `mvn -pl server/src/modules/architecture -am test`
- `mvn -pl server/src/platform/infrastructure -am test`
- `npm --prefix web run build`
- `node scripts/check-ai-control-layout.mjs`
- `git diff --check`
- `node scripts/check-codex-scope.mjs --base origin/dev-ivanh --scope docs/requirements/REQ-20260831-001-remove-logical-subsystem/codex-task-scope.yaml`

## 控制模型种子

候选边界：架构模块后端、架构模块前端、Flyway 迁移、Mock 数据、架构契约文档、下游物理子系统选择器。候选传感器：源码扫描、SQL 结构断言、物理/工单服务测试、控制器测试、前端构建、scope/diff 检查、浏览器视口验收。候选执行器：追加迁移、Java DTO/服务/仓储改造、Vue 页面改造、测试更新和文档更新。

## 风险与用户批准

本计划包含删除逻辑表的破坏性迁移。用户已批准方案 B，但上线仍需要发布负责人确认数据库备份和预发演练。本地实施不连接生产系统。

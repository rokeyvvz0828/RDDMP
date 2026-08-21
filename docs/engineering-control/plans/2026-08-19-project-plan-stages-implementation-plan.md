# 项目计划阶段与分组重构 实施计划

> 执行要求：按当前任务范围受控实施；先完成迁移和后端契约，再完成前端阶段-月份时间轴和主计划子计划弹框，最后执行独立观测。

**目标：** 将项目计划重构为固定七阶段纵轴、月份横轴和主计划时间条，点击主计划弹框查看子计划，并保持当前计划字段、编号和权限兼容；移除项目级阶段字段。

**架构：** 复用 `PLAN_PHASE` 参数和 `pm_project_plan.phase`，为 `pm_project_plan_group` 增加阶段字段，并通过 V47 固定七阶段和删除 `pm_project.phase`。服务端负责历史兼容、层级校验和归组同步，前端负责阶段树和现有拖拽交互。

**技术栈：** Spring Boot 3.4、Java 17兼容代码、JdbcTemplate/MySQL 8/Flyway、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 只追加 V46/V47，不修改 V43-V46。
- 只修改当前 `codex-task-scope.yaml` 的 writable_paths。
- 保持 `com.ccb.*` 包名、现有项目权限和计划字段。
- 阶段由参数管理维护，前端不写死阶段值；计划表单不提供阶段编辑，主计划默认立项，子计划继承父计划。
- 所有写入带租户、项目、逻辑删除和现有权限校验。
- 不修改公共 `web/src/components/ui` 和 `server/src/shared`。

## 文件职责地图

- `V46__project_plan_stages.sql`：新增分组阶段字段、七阶段参数、历史兼容回填。
- `V47__project_plan_fixed_stages.sql`：固定七个计划阶段、停用其他阶段、删除项目级阶段字段。
- `ProjectService.java`：返回阶段显示名，创建/更新/移动计划和分组时校验并同步层级。
- `ProjectServiceTest.java`：验证阶段继承、不一致拒绝、分组阶段更新及后代同步。
- `project.ts`：扩展计划和分组类型字段（当前API函数无需新增）。
- `ProjectView.vue`：构建阶段→分组→计划树，分组表单选择阶段，保留主计划拖拽和子计划操作。
- `styles.css`：阶段行和分组行的层级/主题视觉。
- 当前任务 requirement/design/plan/handoff JSON：范围和控制证据。

## 任务依赖图与并行策略

```text
T1 V46迁移 -> T2 后端契约与测试 -> T3 前端时间轴交互 -> T4 集成验证
```

串行执行。T2依赖V46字段和参数，T3依赖T2返回契约，T4依赖全部实现。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 阶段参数 | T1, T2, T3 |
| R2 阶段内分组 | T1, T2, T3 |
| R3 阶段-月份时间轴与子计划弹框 | T2, T3 |
| R4 主计划拖拽同步后代 | T2, T3, T4 |
| R5 服务端边界校验 | T2, T4 |
| R6 历史兼容迁移 | T1, T4 |

## T1：追加阶段与分组兼容迁移

**需求映射：** R1、R2、R6

**文件：** 新建 `server/src/platform/infrastructure/src/main/resources/db/migration/V46__project_plan_stages.sql`，并追加 `V47__project_plan_fixed_stages.sql`。

**接口：** 为T2提供 `pm_project_plan_group.phase` 字段和七阶段参数数据。

- [x] 添加 `phase VARCHAR(128)`、中文注释和项目阶段索引。
- [x] 将 `PLAN_PHASE` 参数调整为七阶段键值，映射旧计划编码。
- [x] 按已有主计划阶段为历史分组回填阶段，无阶段使用 `PLAN_INITIATION`；同步历史分组内计划阶段。
- [ ] V47 固定七阶段、停用其他计划阶段参数、清理非法阶段并删除项目级 `pm_project.phase`。
- [x] 运行 `node scripts/check-flyway-migrations.mjs` 和 `git diff --check`，确认V43-V45未修改。

**回滚：** 应用回退保留V46字段和参数；不得执行DROP。

**停止条件：** 无法在不删除历史数据的情况下完成参数映射或迁移检查失败。

## T2：后端阶段和层级契约

**需求映射：** R1、R2、R4、R5。

**前置任务：** T1。

**文件：** 修改 `ProjectService.java`、`ProjectServiceTest.java`、`web/src/types/project.ts`。

**接口：** `plan_groups` 增加 `phase`、`phase_name`；计划归组继续接收 `group_id`，服务端同步主计划及所有后代的 `group_id` 和 `phase`。

- [x] 计划分组查询/返回增加阶段字段和名称。
- [x] 创建/更新分组要求有效阶段；更新分组阶段时同步其计划树阶段。
- [x] 新建子计划默认继承父计划阶段；编辑/创建时拒绝不一致阶段。
- [x] 移动主计划时锁定并更新主计划及全部后代；只允许根计划作为拖拽源。
- [x] 增加阶段、分组跨项目、父子阶段和移动后代的测试断言。
- [x] 运行 `mvn -pl :ccb-system -am test`，预期项目服务测试全部通过。

**回滚：** 恢复服务和类型改动，保留V46数据。

**停止条件：** 需要改公共模块、跨表绕过服务契约或无法保证事务内同步。

## T3：前端阶段-月份时间轴、子计划弹框和分组维护

**需求映射：** R1、R2、R3、R4。

**前置任务：** T2。

**文件：** 修改 `ProjectView.vue`、`web/src/types/project.ts`、`web/src/styles.css`。

**接口：** 消费项目详情计划/分组阶段字段和项目 options 阶段列表。

- [x] 将项目计划页改为阶段纵轴、月份横轴的主计划时间轴；计划字段继续沿用当前契约。
- [x] 点击主计划打开子计划弹框，保留子计划新增/编辑/删除和主计划编辑/删除操作。
- [x] 分组新建/编辑表单增加阶段选择，显示阶段名称；同名分组按阶段隔离。
- [x] 主计划整行拖拽目标限制为阶段下的分组行，分组行提示阶段和计划数量；子计划不允许拖拽。
- [x] 保留新增主计划、子计划、编辑、删除、加载、空和失败状态；子计划创建默认父计划阶段。
- [x] 使用语义主题变量完成阶段/分组层级视觉，桌面和375px保证局部表格滚动而非页面溢出。
- [x] 运行 `npm --prefix web run build`，预期vue-tsc和Vite通过。

**回滚：** 恢复当前计划表格层级实现，不删除后端字段。

**停止条件：** 公共组件契约需改变、现有页签刷新上下文丢失或出现页面级横向溢出。

## T4：集成验证和运行观测

**需求映射：** R1-R6。

**前置任务：** T1、T2、T3。

**文件：** 仅更新当前任务 `.ai-control/requirements/req-20260819-033-project-plan-stages/*.json`。

- [x] 运行 `mvn -pl :ccb-system -am test`、`npm --prefix web run build`、`node scripts/check-flyway-migrations.mjs`、`git diff --check`。
- [ ] 启动本地数据库、后端和前端，验证项目计划页阶段/分组/主子计划读取、创建分组和拖拽请求。
- [ ] 在桌面和375px视口检查阶段层级、表格局部滚动、加载/空/失败状态和主题显示。
- [x] 记录实际命令结果、浏览器限制和未决风险，不把构建结果写成浏览器验收。

**回滚：** 保留证据，按T1-T3边界回退应用代码。

**停止条件：** Flyway失败、跨项目访问、计划树白屏、拖拽只更新前端或关键操作无权限校验。

## 集成检查

```powershell
mvn -pl :ccb-system -am test
npm --prefix web run build
node scripts/check-flyway-migrations.mjs
node scripts/check-all-governance.mjs
git diff --check
```

## 控制模型种子

- 被控边界：项目计划阶段/分组数据、ProjectService计划契约、ProjectView计划树。
- 状态变量：`PLAN_PHASE`参数、分组phase、计划parent/group/phase、前端`planTree`、`draggingPlanId`。
- 传感器：迁移检查、后端单元测试、API响应、前端构建、浏览器DOM和拖拽结果。
- 执行器：V46迁移、服务端归组事务、前端时间轴计算和主计划弹框事件。
- 扰动：历史阶段编码、同名分组、跨项目ID、移动端宽度和用户快速重复拖拽。

## 风险与用户批准

- 高风险：历史数据兼容回填和分组阶段一致性；通过V46显式映射、服务端事务和回归测试控制。
- 用户已确认阶段由参数管理维护、分组归属阶段的设计；本计划按该确认实施。

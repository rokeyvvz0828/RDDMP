# 投产演练时序与抽屉交互优化实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施；本计划只覆盖已批准的前端交互变化。

**目标：** 将投产方案和演练步骤改为时序图展示与抽屉维护，并移除重复的当前项目标识。

**架构：** 复用现有 API、状态、权限和保存/删除函数，仅替换 Vue 模板和投产专用 CSS。时序图由已有 DTO 的步骤数组派生，按 `seqNo` 排序，不引入新数据结构。

**技术栈：** Vue 3、TypeScript、Element Plus、现有投产模块样式。

## 任务

### T1：方案时序图与抽屉维护

**需求映射：** R1, R2

**文件：** `web/src/modules/release/components/ReleaseDrillPlanView.vue`、`web/src/modules/release/release-operations.css`

**步骤：**

1. 将正向/回退步骤卡片替换为横向时序轨道，保留步骤字段和按序号排序。
2. 将步骤新增/编辑表单从 `el-dialog` 改为右侧 `el-drawer`，复用现有保存、错误和删除逻辑。
3. 增加时序轨道及抽屉的桌面/窄视口样式，控制页面级溢出。
4. 运行前端构建和浏览器方案页交互检查。

**回滚：** 回退该组件和样式文件。

### T2：演练步骤抽屉维护

**需求映射：** R3

**前置任务：** T1

**文件：** `web/src/modules/release/components/ReleaseDrillExecutionView.vue`、`web/src/modules/release/release-operations.css`

**步骤：**

1. 将当前轮次步骤改为横向时序展示，保持序号、状态、负责人和时间信息。
2. 将步骤新增/编辑表单改为右侧 `el-drawer`，复用现有演练步骤契约和保存逻辑。
3. 运行前端构建和浏览器新增/编辑/删除步骤检查。

**回滚：** 回退该组件和样式文件。

### T3：移除投产头部项目标识并完成集成验证

**需求映射：** R4, R5

**前置任务：** T1, T2

**文件：** `web/src/modules/release/ReleaseOperationsManagement.vue`、`web/src/modules/release/release-operations.css`

**步骤：**

1. 移除投产管理头部当前项目块及无用专属样式。
2. 运行 Maven、Vite、差异检查。
3. 浏览器验证五个投产路由、抽屉状态、页面尺寸和控制台日志。

**回滚：** 回退头部模板和对应样式。

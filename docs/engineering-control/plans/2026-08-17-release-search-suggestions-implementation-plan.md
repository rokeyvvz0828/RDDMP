# 配置管理候选式模糊检索实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将配置管理四个列表的自由文本搜索统一为必须选择真实候选的模糊检索下拉控件。

**架构：** 在配置管理模块内部新增 `ReleaseSearchSelect` 和统一候选类型。投产窗口、投产基线、生产版本使用页面已加载记录生成本地候选；版本申请由父页面复用现有分页接口提供项目范围远程候选。临时输入与稳定选择值分离，只有选择或清空才改变主列表。

**技术栈：** Vue 3.5、TypeScript 5.7、Element Plus 2.9、Axios、Vite 6。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-17-release-search-suggestions-design.md`
- 状态：可移交

## 全局约束

- 仅修改配置管理模块的投产窗口、版本申请、投产基线、生产版本四个列表搜索。
- 必须选择真实候选；临时输入不得改变主列表或提交任意查询值。
- 空输入获得焦点时展示前 20 条；输入使用不区分大小写的包含匹配。
- 版本申请候选复用 `GET /api/release/applications`，不新增后端接口、迁移或权限。
- 保留现有状态、制品类型、投产结果等独立筛选器及服务端分页行为。
- 项目和投产窗口范围继续由现有上下文控制；移动端不在范围内。
- 在当前本地 `rokey` 工作区实施，保护所有现有未提交修改，不执行格式化全仓、提交、推送或分支合并。
- 仓库当前没有前端单元测试运行器，不新增测试依赖；使用类型检查、生产构建和浏览器行为验证。

## 文件职责地图

| 路径 | 状态 | 职责 | 事实依据 |
| --- | --- | --- | --- |
| `web/src/modules/release/components/ReleaseSearchSelect.vue` | candidate-new | 配置管理内部候选选择控件；隔离临时输入和稳定选择 | 四个页面当前重复使用普通 `el-input` |
| `web/src/modules/release/types.ts` | existing | 增加 `ReleaseSearchOption` 公共候选契约 | 当前仅保存模块视图类型，适合作为模块内部类型边界 |
| `web/src/modules/release/release-prototype.css` | existing | 搜索选择器和候选主辅文本样式 | 当前配置管理专属样式集中在此文件 |
| `web/src/modules/release/components/ReleaseWindowView.vue` | existing | 投产窗口本地候选和精确记录选择 | 已接收当前项目 200 条窗口记录 |
| `web/src/modules/release/components/ReleaseBaselineView.vue` | existing | 当前投产窗口基线候选和精确明细选择 | 已加载当前窗口完整基线数组 |
| `web/src/modules/release/components/ReleaseCurrentProductionView.vue` | existing | 当前项目生产版本候选和精确明细选择 | 已加载当前项目生产版本数组 |
| `web/src/modules/release/components/ReleaseApplicationView.vue` | existing | 展示远程候选，选中申请单号后驱动主列表查询 | 当前普通输入通过防抖直接修改分页查询 |
| `web/src/modules/release/ReleaseManagementPrototype.vue` | existing | 提供项目范围远程申请候选、请求防抖和过期响应保护 | 已拥有项目上下文和 `listReleaseApplications` 调用 |

## 任务依赖图与并行策略

```text
T1 公共组件与契约
  -> T2 三个本地数据页面接入
  -> T3 版本申请远程候选及集成验收
```

三个任务串行执行。T2、T3 都消费 T1 的组件契约且会共同修改配置管理样式或交互，当前工作区又有未提交修改，不安排并行写入。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T1, T2, T3 |
| R2 | T1, T2, T3 |
| R3 | T1, T2, T3 |
| R4 | T2, T3 |
| R5 | T1, T3 |

### T1：公共候选选择组件与稳定选择契约

**需求映射：** R1, R2, R3, R5

**前置任务：** 无

**文件：**

- 新建：`web/src/modules/release/components/ReleaseSearchSelect.vue`
- 修改：`web/src/modules/release/types.ts`
- 修改：`web/src/modules/release/release-prototype.css`
- 测试：无独立测试文件；仓库未配置前端测试运行器

**接口：**

- 消费：Element Plus `el-select` 的 `filterable`、`remote-method`、`visible-change`、`clearable`、`loading` 能力。
- 产出：

```ts
export interface ReleaseSearchOption {
  value: string | number
  label: string
  description: string
  keywords: string
}
```

```ts
// ReleaseSearchSelect props/events
modelValue?: string | number
options: ReleaseSearchOption[]
placeholder: string
loading?: boolean
remote?: boolean
error?: string
emit('update:modelValue', value?: string | number)
emit('search', query: string)
```

- [ ] **步骤 1：记录当前失败基准**

运行：`rg -n "<el-input.*release-search-input" web/src/modules/release/components`

预期：命中四个普通搜索输入框，证明统一候选组件尚不存在。

证据：命中文件和行号。

- [ ] **步骤 2：定义候选类型和组件选择语义**

在 `types.ts` 增加 `ReleaseSearchOption`；新组件内部维护临时 `query`，本地模式按 `keywords.toLowerCase().includes(query.toLowerCase())` 过滤并 `slice(0, 20)`，远程模式只展示父级传入的最多 20 条候选。

预期：`modelValue` 只在 `change` 选择真实值或 `clear` 时发出；临时输入和失焦不发稳定值变化。

证据：组件事件绑定和可见候选计算。

- [ ] **步骤 3：实现空焦点、远程触发和失败状态**

空输入打开下拉时，本地模式展示前 20 条，远程模式发出 `search('')`。远程输入通过 `search` 事件交给调用方；组件显示 `loading`、`无匹配选项` 和独立失败文案。

预期：不提供 `allow-create`；不存在提交自由文本的路径。

证据：组件模板中无创建选项，稳定值来自 `el-option`。

- [ ] **步骤 4：补充模块专属样式并运行局部类型检查**

运行：`cd web && npx vue-tsc --noEmit`

预期：退出码 0，无 TypeScript 或 Vue 模板错误。

证据：命令输出和退出码。

- [ ] **步骤 5：建立差异检查点**

运行：`git diff -- web/src/modules/release/components/ReleaseSearchSelect.vue web/src/modules/release/types.ts web/src/modules/release/release-prototype.css`

预期：只包含公共组件、候选类型和相关样式；不提交 Git。

**验收、证据与回滚：**

- 空查询最多 20 条；本地包含匹配大小写不敏感。
- 临时输入不发 `update:modelValue`，选择和清空才发。
- 主辅文本截断且下拉宽度稳定。
- 回滚：删除新组件，撤销 `types.ts` 和专属 CSS 中本任务新增块。

**停止条件：** 当前 Element Plus 版本无法区分临时输入和选择值，或组件必须依赖未安装库。

**升级条件：** 需要新增平台公共组件、修改 Element Plus 版本或改变“必须选择”的用户规则。

### T2：投产窗口、投产基线、生产版本本地候选接入

**需求映射：** R1, R2, R3, R4

**前置任务：** T1

**文件：**

- 修改：`web/src/modules/release/components/ReleaseWindowView.vue`
- 修改：`web/src/modules/release/components/ReleaseBaselineView.vue`
- 修改：`web/src/modules/release/components/ReleaseCurrentProductionView.vue`

**接口：**

- 消费：T1 的 `ReleaseSearchSelect` 和 `ReleaseSearchOption`。
- 产出：三个页面的 `selectedSearchId` 稳定选择值和页面专属候选映射。

- [ ] **步骤 1：将投产窗口检索改为窗口 ID 选择**

由 `props.windows` 映射候选：`value=id`、`label=windowName`、`description=windowCode + statusLabel`、`keywords=windowCode + windowName`。`rows` 只在有选择时按 `id` 精确过滤，同时继续叠加状态筛选。

预期：输入临时文字不改变窗口表；选择后只显示目标窗口；清空恢复状态筛选下全部窗口。

- [ ] **步骤 2：将投产基线检索改为明细 ID 选择**

由 `entries` 映射候选，检索字段包含子系统编码/名称、交付单元编码/名称、制品版本和来源申请。选择按基线明细 `id` 精确过滤，并继续叠加投产结果筛选。

预期：切换 `windowId` 或重新加载后，如果选择 ID 不存在则自动清空。

- [ ] **步骤 3：将生产版本检索改为明细 ID 选择**

由 `entries` 映射候选，检索字段包含子系统、交付单元和制品版本。选择按生产版本明细 `id` 精确过滤，并继续叠加制品类型筛选。

预期：项目切换和加载后校验选择 ID，不把旧项目选择用于新项目。

- [ ] **步骤 4：运行类型检查并确认普通输入框减少**

运行：`cd web && npx vue-tsc --noEmit`

预期：退出码 0。

运行：`rg -n "<el-input.*release-search-input" web/src/modules/release/components`

预期：只剩版本申请搜索框尚未替换。

- [ ] **步骤 5：建立差异检查点**

运行：`git diff -- web/src/modules/release/components/ReleaseWindowView.vue web/src/modules/release/components/ReleaseBaselineView.vue web/src/modules/release/components/ReleaseCurrentProductionView.vue`

预期：只改变搜索状态、候选映射、过滤条件和组件模板。

**验收、证据与回滚：**

- 三页空焦点和模糊匹配均展示真实候选。
- 状态、投产结果、制品类型筛选继续与选择条件组合。
- 项目或窗口范围变化不会残留旧选择。
- 回滚：恢复三个页面原 `keyword` 和 `el-input` 本地包含过滤。

**停止条件：** 任一数据源实际分页或截断，无法满足当前范围完整候选。

**升级条件：** 必须新增后端候选接口才能覆盖当前项目或窗口数据。

### T3：版本申请远程候选、完整构建与浏览器验收

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T2

**文件：**

- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 复验：T1、T2 涉及的全部前端文件

**接口：**

- 消费：`listReleaseApplications({ page: 1, size: 20, projectId, keyword })`。
- 产出：

```ts
// ReleaseApplicationView additions
searchOptions: ReleaseSearchOption[]
searchLoading: boolean
searchError?: string
projectRef?: string
emit('search-options', query: string)
```

- [ ] **步骤 1：在父页面建立独立候选请求状态**

增加候选数组、加载、错误、300ms 定时器和递增请求序号。查询固定 `page=1,size=20,projectId=currentRef,keyword=query || undefined`，不修改 `applications`、主列表页码或状态筛选。

预期：空查询返回前 20 条；旧请求晚到时因序号不匹配被忽略；失败只更新候选错误。

- [ ] **步骤 2：映射申请候选并接入选择查询**

候选 `value=applicationCode`、`label=applicationCode`、`description=subsystemCode + subsystemName + status`、`keywords=applicationCode + subsystemCode + subsystemName`。页面选择后才发主列表 `query`，关键词使用唯一申请单号；清空后发无关键词查询。

预期：临时输入不会触发主列表加载；状态筛选和分页仍使用现有查询函数。

- [ ] **步骤 3：处理项目切换和组件销毁**

项目变化时清空已选申请、候选、错误和待执行定时器，并由现有项目刷新流程加载主列表。组件/父页面卸载时清理定时器，避免离开页面后写状态。

预期：不存在旧项目候选或延迟请求覆盖。

- [ ] **步骤 4：运行完整前端构建**

运行：`cd web && npm run build`

预期：`vue-tsc --noEmit` 和 Vite 均成功；允许现有包体积告警，不允许编译错误。

证据：构建退出码和关键摘要。

- [ ] **步骤 5：运行静态范围检查**

运行：`rg -n "<el-input.*release-search-input" web/src/modules/release/components`

预期：四个列表搜索框均已移除普通输入实现；表单、文本域和非列表输入不受影响。

运行：`git diff --check`

预期：无新增空白错误。

- [ ] **步骤 6：在浏览器验证四个页面**

地址：`http://127.0.0.1:5173/release`

逐页验证：空焦点展示候选；输入片段缩小候选；不选择直接失焦不改变行数；选择后只显示目标；清空恢复；切换项目/窗口清空失效选择；版本申请候选请求不改变主表直至选择。

预期：四页行为一致，无控制台错误、下拉遮挡、文字溢出或工具栏重叠。

- [ ] **步骤 7：检查最终差异和服务状态**

运行：`git status --short` 和按本计划文件路径执行 `git diff`。

预期：不覆盖用户其他修改；不提交或推送。

**验收、证据与回滚：**

- 四个页面全部满足 R1-R5，版本申请远程候选可包含主表当前页之外记录。
- 主列表请求只发生在选择、清空、状态或分页变化时。
- 前端构建和真实浏览器验证通过。
- 回滚：恢复版本申请原 `keyword` 防抖监听，删除候选父级状态，并按 T2、T1 回滚顺序恢复。

**停止条件：** 现有申请列表接口无法按项目和关键词返回候选，或真实环境返回数据不满足唯一申请单号假设。

**升级条件：** 需要后端专用联想接口、改变分页契约、加入新依赖或扩展到配置管理之外。

## 集成检查

- `cd web && npm run build`：类型检查和生产构建均成功。
- `git diff --check`：无新增空白问题。
- 浏览器四页面行为矩阵全部通过，固定列表格背景修复仍有效。
- `http://127.0.0.1:5173/release` 返回 200，现有后端接口保持可用。
- 只在计划列出的配置管理文件和需求文档中产生本任务修改。

## 控制模型种子

以下均为 `hypotheses-only`，进入 `$control-engineering` 后必须重新验证：

- 被控边界候选：四个列表搜索控件、候选映射、申请候选查询状态和现有列表查询接口。
- 状态变量候选：临时输入、稳定选择值、候选数组、候选加载/错误、申请候选请求序号、页面主列表查询条件。
- 接口候选：`ReleaseSearchSelect` props/events、`ReleaseSearchOption`、`listReleaseApplications`。
- 传感器候选：`vue-tsc`、Vite 构建、网络请求观察、DOM 快照、候选和表格行数、控制台错误。
- 执行器候选：Vue 状态更新、候选映射、组件替换、父级远程请求函数。
- 扰动候选：项目切换、窗口切换、快速连续输入、旧请求晚到、数据刷新删除已选记录、现有未提交工作区修改。
- 时延候选：约 300ms 输入防抖和网络响应时间。
- 假设：本地三个接口返回完整范围；Element Plus 选择器不会把临时输入提交为值；申请单号在租户内唯一稳定。

## 风险与用户批准

- 高风险动作：无数据库、依赖、权限、提交、推送或分支操作。
- 中等风险：申请候选与主列表共享接口，必须保持请求状态隔离并抑制过期响应。
- 用户需批准：当前计划的三个串行任务、只改前端且不新增后端接口、使用构建和浏览器验证替代新增测试框架。

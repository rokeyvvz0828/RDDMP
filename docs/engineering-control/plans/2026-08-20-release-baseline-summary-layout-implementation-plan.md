# 投产基线汇总区分层布局实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将投产基线汇总区改为“窗口信息栏 + 五项指标行”的稳定响应式布局。

**架构：** `ReleaseBaselineView.vue` 只负责用现有 `selectedWindow` 和 `summary` 输出两层语义结构；`release-prototype.css` 负责桌面五列、窄屏两列和手机单列的布局与分隔。数据加载、统计计算、权限和接口保持不变。

**技术栈：** Vue 3、TypeScript、Element Plus、CSS Grid、Vite。

## 全局约束

- 仅修改 `web/src/modules/release/components/ReleaseBaselineView.vue` 和 `web/src/modules/release/release-prototype.css`。
- 不新增依赖，不修改后端、API、状态模型和统计口径。
- 保留现有设计变量、6px 容器圆角和紧凑业务界面风格。
- 不覆盖 `rokey` 工作区内其他未提交修改。

---

## 文件职责地图

- `web/src/modules/release/components/ReleaseBaselineView.vue`：现有文件；将单层六项汇总结构拆为窗口信息层和五项指标层。
- `web/src/modules/release/release-prototype.css`：现有文件；替换汇总区五轨道规则，增加两层结构和断点边框规则。
- 自动化测试文件：项目当前没有组件测试运行器，本任务不新增测试依赖；使用生产构建、CSS/DOM 检查和浏览器视口测量作为传感器。

## 任务依赖图与并行策略

仅有 T1，串行执行。模板和 CSS 属于同一视觉契约，不能拆成并行任务。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T1 |
| R2 | T1 |
| R3 | T1 |
| R4 | T1 |

### T1：完成投产基线两层汇总布局

**需求映射：** R1, R2, R3, R4

**前置任务：** 无

**已证实事实：**

- 当前模板在 `ReleaseBaselineView.vue:220` 连续输出 6 个直属指标项。
- 当前 CSS 在 `release-prototype.css:219` 只定义 `1 + 4` 共 5 个网格轨道，因此第 6 项自动换行。
- `selectedWindow` 已提供 `windowCode`、`windowName` 和 `statusLabel`，`summary` 已提供全部五项统计，无需新增状态或接口。

**文件：**

- 修改：`web/src/modules/release/components/ReleaseBaselineView.vue:220`
- 修改：`web/src/modules/release/release-prototype.css:219`
- 验证：无新增测试文件；使用构建和浏览器视口检查。

**接口：**

- 消费：`selectedWindow: ComputedRef<ReleaseWindowDto | undefined>`；`summary: ComputedRef<{ systems: number; units: number; files: number; success: number; released: number }>`。
- 产出：`.release-ledger-summary` 下的 `.release-ledger-summary__window` 和 `.release-ledger-summary__metrics` 两层 DOM 契约。

- [ ] **步骤 1：记录当前失败基准**

  运行：`rg -n "release-ledger-summary|repeat\(4" web/src/modules/release/components/ReleaseBaselineView.vue web/src/modules/release/release-prototype.css`

  预期：模板存在 6 项，而桌面网格仅有 5 个轨道。

  证据：保留命中行和用户截图中的孤立“制品准出”第二行。

- [ ] **步骤 2：重组汇总区语义结构**

  在现有 `v-if="selectedWindow"` 容器中创建窗口信息层，展示窗口编码、名称和状态；创建指标层，按原顺序输出五项统计。继续复用现有值，不新增事件和计算属性。

  预期：DOM 明确分为一个窗口上下文区域和一个五指标区域。

  证据：作用域 diff 只包含汇总区模板结构。

- [ ] **步骤 3：实现桌面和响应式 CSS**

  将根容器改为两层布局；窗口信息层采用可收缩文本区与固定状态区；指标层在桌面使用 `repeat(5, minmax(0, 1fr))`。在 `760px` 断点改为两列，在 `430px` 断点改为单列，并同步调整行列分隔。

  预期：桌面没有第二行留白，窄屏按固定列数换行，长名称不挤压状态。

  证据：CSS diff 和浏览器布局测量。

- [ ] **步骤 4：运行局部与回归验证**

  运行：`npm run build`

  预期：`vue-tsc --noEmit` 和 Vite 构建退出码为 0。

  运行：`git diff --check -- web/src/modules/release/components/ReleaseBaselineView.vue web/src/modules/release/release-prototype.css`

  预期：无空白错误。

  浏览器检查：桌面视口确认五指标单行；约 `760px` 确认两列；约 `430px` 确认单列；测量 `scrollWidth <= clientWidth`。

  预期：无溢出、遮挡、孤立指标或边框错位。

- [ ] **步骤 5：建立工作区检查点**

  运行：`git status --short -- web/src/modules/release/components/ReleaseBaselineView.vue web/src/modules/release/release-prototype.css`

  预期：只报告计划内两个产品文件的既有工作区状态；不提交、不清理其他文件。

**验收检查：**

- 选中窗口时显示独立窗口信息层，包含编码、名称和状态。
- 桌面端五项指标等宽单行，无空白第二行。
- 窄屏两列、手机单列且无页面级横向溢出。
- 未选中窗口时汇总区仍不渲染。
- 构建通过，数据与交互逻辑无改动。

**回滚：** 仅恢复本任务在两个计划文件中的汇总区模板和 CSS 片段，不触碰其他工作区修改。

**停止条件：** 发现 `ReleaseWindowDto` 缺少已确认字段、现有断点与全局布局冲突导致必须修改第三个产品文件，或构建出现与本任务无关且无法隔离的失败时停止并重新建模。

**升级条件：** 用户要求改变指标内容、统计口径、交互行为或将该模式推广到其他页面时升级为新的需求设计。

## 集成检查

- T1 完成后运行 `npm run build`，必须成功。
- 检查前端 `http://127.0.0.1:5173/release/production-baseline` 可访问，后端 `/actuator/health` 为 `UP`。
- 对桌面、760px 和 430px 三档视口收集可见布局证据；若认证阻塞页面检查，明确记录为验证限制，不以猜测替代。

## 控制模型种子

- 候选被控边界：投产基线汇总区模板与局部 CSS。
- 候选状态变量：窗口信息层高度、指标网格列数、容器横向溢出、五项指标行数。
- 候选接口：`selectedWindow`、`summary` 和两层 CSS 类契约。
- 候选传感器：构建退出码、DOM 结构、浏览器截图、`scrollWidth/clientWidth`。
- 候选执行器：模板分组和 CSS Grid/断点规则。
- 候选扰动：长窗口名称、不同权限下的页面宽度、标签栏占用空间、未认证浏览器状态。
- 候选时延：Vite HMR 和浏览器重绘。
- 种子状态：仅为假设，须由建模阶段验证。

## 风险与用户批准

本任务无数据库、权限、接口或依赖变更。主要风险是长名称溢出和响应式边框错位，均通过局部 CSS 和三档视口检查控制。用户已批准方案 2 和实施方向；当前计划修订仍需落盘复核后才能导入闭环执行。

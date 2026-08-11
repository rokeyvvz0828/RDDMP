# 主题模式扇形切换实施计划

> 执行要求：按 `rddmp-delivery-engineer` 的受控修改和验证流程执行。

**目标：** 在现有 Vue 主题公共能力上增加独立的模式扇形切换和页面级扩散反馈。

**范围：** 只修改公共主题组件、应用布局、主题抽屉和语义样式；不修改后端、数据库、依赖或其他业务页。

### T1：实现模式组件并接入布局

**需求映射：** REQ-20260810-018 验收标准 1-4

**文件：**

- 新增 `web/src/components/ui/ThemeModeFan.vue`。
- 修改 `web/src/views/AppLayout.vue`，在两个头部操作区接入组件。
- 修改 `web/src/components/ui/ThemeSettingsDrawer.vue`，删除重复显示模式区。
- 修改 `web/src/styles.css`，增加扇形菜单、扩散层、减弱动画和移动端规则。

**步骤：**

1. 复用 `useThemeStore` 和 Element Plus 的 `Sunny`、`Moon`、`Monitor` 图标，建立可访问的中心按钮和三项选项。
2. 处理点击外部、`Escape`、模式选择、视口中心计算和扩散层清理。
3. 为顶部布局、侧栏布局和移动视口提供稳定尺寸与层级。
4. 构建并检查生成的模板类型和 CSS 选择器。

**验收：** `npm --prefix web run build` 通过；浏览器中三个模式可切换，扩散起点为图标中心，抽屉无重复模式控件。

**回退：** 仅回退 T1 变更，保留现有主题仓库和存储值。

**停止条件：** 出现认证回归、主题仓库数据结构变化、移动端横向溢出或构建错误时停止并记录。

### T2：独立观察与交付证据

**需求映射：** REQ-20260810-018 验收标准 1-5

**检查：** 运行治理检查、前端构建，并使用浏览器检查桌面/移动视口、模式切换、外部关闭、刷新持久化和减少动态效果分支。

**证据：** `.ai-control/requirements/req-20260810-018-theme-mode-fan/observation-T1.json` 和 `convergence.json`。

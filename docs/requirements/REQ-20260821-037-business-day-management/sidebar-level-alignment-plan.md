# 营业日管理 Sidebar 层级对齐实施计划

> 执行要求：在 REQ-20260821-037 既有范围内完成最小视觉纠偏，不改变菜单数据、路由、权限或公共菜单组件。

**目标：** 让“营业日管理”的图标和文字与“应用组装测试”等二级菜单严格对齐。

**架构：** 保留后端 `parent_id=600` 和单一叶子入口。由 `AppLayout.vue` 为测试管理根节点提供稳定作用域类，再由营业日模块样式仅覆盖该根节点的直接叶子项缩进。

**技术栈：** Vue 3、Element Plus、CSS、Vite。

## 全局约束

- 不修改 V51/V52、API、权限、顶部四视图和公共 `UiRouteMenuNode`。
- 不影响系统管理、工作流等其他菜单。
- 桌面侧栏和移动菜单使用同一规则。

### T7：营业日管理二级入口视觉对齐

**需求映射：** REQ-20260821-037 Sidebar 单一二级入口。

**前置任务：** T1-T6。

**文件：**

- 修改：`web/src/views/AppLayout.vue`
- 修改：`web/src/modules/test-management/business-day/business-day.css`
- 证据：`.ai-control/requirements/req-20260821-037-business-day-management/execution-T7.json`
- 证据：`.ai-control/requirements/req-20260821-037-business-day-management/observation-T7.json`

**接口：**

- 消费：后端 `/auth/routes` 返回的 `/test-management` 根节点和 614 叶子节点。
- 产出：`.app-menu-node--test-management` 作用域及直接叶子项 40px 缩进规则。

- [ ] 建立浏览器基线：记录同级菜单横坐标，当前营业日管理比应用组装测试右移约 10px。
- [ ] 实施最小作用域类与 CSS 覆盖。
- [ ] 运行 `npm --prefix web run build`，预期退出码 0。
- [ ] 运行 REQ-037 scope 与 `git diff --check`，预期退出码 0。
- [ ] 在桌面和移动菜单中测量图标、文字横坐标一致，并验证营业日页面仍可进入。

**回滚：** 回退 `AppLayout.vue` 的作用域类绑定和 `business-day.css` 的单条覆盖规则。

**停止条件：** 若修复必须修改公共菜单组件、全局菜单缩进或数据库结构，则停止并重新设计。

**升级条件：** 若 Element Plus 不透传根节点 class，或移动端与桌面端无法共享规则，则升级为公共菜单组件任务。

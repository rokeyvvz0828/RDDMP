# 项目概览甘特图实施计划

## T1：项目概览排期改造

- 修改 `web/src/views/ProjectView.vue`：移除日历状态、日历模板和日历跳转；新增主计划、子计划数据计算、日期轴辅助函数、自定义甘特图实例和销毁/重绘逻辑。
- 修改 `web/src/styles.css`：新增甘特图卡片、局部滚动容器、任务层级行、空状态和移动端规则；清理项目概览日历专用布局影响。
- 复用现有 ECharts、项目计划类型、状态标签语义颜色和交付示范中心 custom series 方案。

## 验证

- `npm --prefix web run build`
- `git diff --check`
- 浏览器进入 `/projects/{projectId}?tab=overview`，确认日历消失、月/周甘特图和主子层级；检查主题、空状态、刷新和页面级溢出。

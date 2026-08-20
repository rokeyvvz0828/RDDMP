# 项目概览信息与排期布局实施计划

## T1 页面结构

- 调整 ProjectView 概览区域的卡片顺序。
- 将 hero 中的整体计划进度与项目基本信息合并到同一信息卡片。

## T2 样式与验证

- 更新桌面和移动端网格规则，保证顺序、间距、溢出和主题可读性。
- 执行 `git diff --check`、`npm --prefix web run build` 和浏览器验收。

## 回退

仅恢复本任务对 `web/src/views/ProjectView.vue`、`web/src/styles.css` 的修改。

# 项目计划分组主题色阶设计

分组记录保存语义色阶 `brand/accent/success/warning/danger/muted`，不保存 `ocean/tech-blue` 等系统主题编码。服务端在读写时使用固定白名单，空值默认 `brand`。

前端分组选择器和计划表通过 CSS 变量 `--brand`、`--accent`、`--success`、`--warning`、`--danger`、`--muted` 渲染。主题 store 更新 `data-palette` 后，CSS 变量自动变化，分组的语义色阶保持不变而实际颜色随当前主题变化。

V42 将 V41 已产生的主题编码统一改为 `brand`，保证历史数据不再绑定到某个系统主题。V41 不修改，迁移只追加。

---
id: REQ-20260907-069
status: ready
owner: rokeyvvz0828
module: business/release
---

# 投产管理抽屉全屏

用户要求投产管理涉及抽屉的功能可放大至全屏，并已确认设计修订1。实现页面内全屏与还原：投产/回退时序、方案指令、演练环境、演练步骤、问题详情及新增编辑，共5个抽屉实例。

R1：所有5处支持全屏与原尺寸，浏览器工具栏不隐藏。
R2：不清空输入、不重建表单、不请求业务数据，父子状态独立；原权限、保存、关闭保护和Esc语义不变；关闭重开及项目切换恢复默认。
R3：桌面1280x800与手机375x812、390x844、430x932、明暗主题下无溢出遮挡，标题/页脚可达，图标有提示和可访问名称。

详细设计：docs/engineering-control/designs/2026-09-07-release-drawer-fullscreen-design.md。实施计划：docs/engineering-control/plans/2026-09-07-release-drawer-fullscreen-implementation-plan.md，修订1已获用户确认批准并实施。

只修改scope列出的4个业务组件、模块内候选帮助函数和头部组件、专属样式、当前需求测试及元数据。不改其他模块、公共组件、后端、数据库、API或路由；不读取.env，不重启或提交推送。保留已有改动。

局部测试、前端构建和差异检查通过；治理检查存在历史失败，真实浏览器矩阵尚未执行，详见acceptance.md。回退只撤销本次增量，不回退此前页面和测试数据。前缀req-20260907-069-release-drawer-fullscreen，当前phase为observing，等待真实浏览器验收，不声明converged。

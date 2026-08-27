# 版本申请审批链接与待办同步实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 为审批中的版本申请增加固定审批链接，确保只有当前审核人可操作，并让审批完成后的待办及时更新且适配 H5。

**架构：** 版本申请列表生成申请级前端路由并调用浏览器剪贴板；审批权限继续使用现有工作流任务上下文与服务端决定接口。审批成功后发布统一页面事件，任务中心和工作台监听事件及页面重新激活信号后刷新。

**技术栈：** Vue 3、TypeScript、Element Plus、Vue Router、现有工作流 API。

## 全局约束
- 不修改后端审批流转、数据库和 API 契约。
- 只有当前登录用户拥有可操作待办时才能审批。
- 不覆盖工作区已有未提交修改。
- 固定链接不携带任务 ID。

---

### T1：申请级审批链接

**需求映射：** R1, R2

**前置任务：** 无

**文件：**
- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`

**接口：**
- 消费：`ReleaseApplicationDto.applicationCode`、`ReleaseApplicationDto.status`
- 产出：`copy-approval-link` 视图事件与固定业务详情 URL

- [ ] 在列表中只为 `IN_REVIEW` 行显示带复制图标的“审批链接”操作。
- [ ] 在容器页使用 `router.resolve` 和 `window.location.origin` 生成 `/release/applications/{applicationCode}`。
- [ ] 优先使用 Clipboard API，失败时使用受控文本框回退复制，并显示结果提示。
- [ ] 运行 `npm --prefix web run build`，预期退出码 0。

**回滚：** 移除新增事件、按钮和复制函数。

**停止条件：** 固定业务详情路由无法在登录后恢复，或必须暴露任务 ID 才能定位审批。

**升级条件：** 发现现有服务端允许非当前审核人提交审批。

### T2：审批完成后的待办同步

**需求映射：** R2, R3

**前置任务：** T1

**文件：**
- 新建：`web/src/utils/workflow-task-events.ts`
- 修改：`web/src/modules/release/components/ReleaseApprovalPanel.vue`
- 修改：`web/src/views/TaskCenterView.vue`
- 修改：`web/src/views/DashboardView.vue`

**接口：**
- 消费：现有 `decideWorkflowTask` 成功结果与待办列表 API
- 产出：`workflow-task-changed` 浏览器事件及聚焦刷新生命周期

- [ ] 审批接口成功后发布任务变化事件，失败时不发布。
- [ ] 任务中心和工作台监听事件并刷新当前列表。
- [ ] 两个页面在窗口聚焦和文档恢复可见时刷新，并在卸载时移除监听。
- [ ] 保留服务端 `actionable`、任务归属和任务状态为审批按钮的最终条件。
- [ ] 运行 `npm --prefix web run build`，预期退出码 0。

**回滚：** 移除事件工具及三个页面中的发布和监听代码。

**停止条件：** 刷新形成重复请求循环，或审批成功事件在请求完成前发布。

**升级条件：** 用户要求跨设备两秒内主动更新，此时需另行设计 SSE、WebSocket 或轮询。

### T3：H5 布局与集成验收

**需求映射：** R1, R4

**前置任务：** T1, T2

**文件：**
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**
- 消费：现有审批详情 DOM 结构
- 产出：桌面与 `390x844` 稳定布局

- [ ] 调整窄屏头部、审批面板、意见输入和按钮触控尺寸，确保长文本换行。
- [ ] 运行 `npm --prefix web run build` 和 `git diff --check`，预期均退出码 0。
- [ ] 在 `390x844` 与桌面视口截图验证无页面级横向溢出、遮挡和不可点击按钮。

**回滚：** 回退本需求新增的局部媒体查询规则。

**停止条件：** 需要修改公共应用壳层或全局主题才能完成布局。

**升级条件：** 当前登录数据无法覆盖审批中任务，导致真实权限场景无法验收。

## 依赖与并行策略
T1、T2 共用前端业务契约，按顺序实施；T3 在结构稳定后执行。三个任务串行，避免在同一 Vue 页面和构建产物上产生冲突。

## 批准记录
用户于 2026-08-27 确认申请级固定链接方案，并补充只有流程到达审核人环节时才允许操作；计划状态为已批准。

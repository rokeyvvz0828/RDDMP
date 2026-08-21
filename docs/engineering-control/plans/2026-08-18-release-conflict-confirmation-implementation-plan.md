# 重复申请确认交互修复实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 修复“仍创建新申请”的操作层级、关闭行为和重复弹窗判断。

**架构：** 保留现有后端冲突令牌契约。弹窗组件将集合级确认移入 footer；页面组件在当前申请会话中保存已确认令牌，并仅在令牌变化时要求重新确认。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite。

## 状态与全局约束

- 计划修订：1；设计修订：1；状态：可移交。
- 用户已确认推荐方案并明确要求开发。
- 只修改两个授权 Vue 文件，保护 `rokey` 中所有其他未提交内容。
- 不修改 API、后端、数据库、权限或公共组件；不提交、不推送、不切换分支。

## 文件职责地图

| 路径 | 职责 |
| --- | --- |
| `web/src/modules/release/components/ReleaseConflictDialog.vue` | 冲突信息、卡片级操作和弹窗底部集合级确认 |
| `web/src/modules/release/ReleaseManagementPrototype.vue` | 待提交状态、冲突令牌确认和提交错误路由 |

## 任务依赖与需求覆盖

单任务 T1 串行完成，覆盖 R1-R4。

### T1：重复申请确认状态流

**需求映射：** R1、R2、R3、R4

**前置事实：** 提交接口已经接受 `conflictToken` 并在冲突事实不变时放行；当前 `CREATE_NEW` 处理接口只返回原冲突报告；前端当前把所有 `409` 都重新打开冲突弹窗。

**文件：**

- 修改：`web/src/modules/release/components/ReleaseConflictDialog.vue`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`

**步骤：**

1. 建立当前代码基准：确认主按钮在卡片内，`submitSavedApplication` 对任意 `409` 重开弹窗。
2. 将 `CREATE_NEW` 事件改为不依赖单条 conflict，并将按钮移到 footer 的“暂不处理”右侧。
3. 为待提交状态增加 `confirmedConflictToken`；点击确认后先关闭弹窗并直接提交。
4. 保存和再次提交时复用仍匹配的已确认令牌；令牌变化时重新提示。
5. 修改 `409` 分支，只在未确认或冲突令牌变化时重开弹窗，其他错误使用 `apiErrorMessage`。
6. 运行 `npm --prefix web run build`、`git diff --check`、治理与范围检查，并执行桌面浏览器真实路径验收。

**验收：** 底部操作层级正确；点击立即关闭；成功进入审批中；相同令牌不重弹；变化令牌重弹；取消/修改/暂不处理无回归。

**回滚：** 恢复两个 Vue 文件在 T1 前的局部差异。

**停止条件：** 需要修改后端冲突契约、数据库或公共组件；运行环境无法产生可验证的重复申请。

**升级条件：** 产品要求确认跨页面刷新持久化，或需要把 `409` 拆分为新的公共错误码。

## 控制模型种子

- 被控对象候选：重复申请弹窗与版本申请提交状态流。
- 状态候选：弹窗开关、pending application、confirmed token、server conflict token、submission result。
- 传感器候选：前端构建、浏览器弹窗状态、提交请求、申请状态、控制台。
- 执行器候选：footer 布局、令牌保存、提交错误路由。
- 扰动候选：并发修改历史申请、非冲突类 `409`、重复点击。

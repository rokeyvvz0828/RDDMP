# 审批中重复申请门禁实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。

**目标：** 在新申请落库前拦截审批中重复申请，并提供旧申请快捷撤回和撤回完成后继续原操作的完整交互。

**架构：** 后端新增创建/更新预检，并在创建、更新、提交中复用同一审批中门禁；前端新增未持久化操作状态和 2 秒轮询，冲突弹框负责卡片级撤销和底部继续门禁。

**技术栈：** Java 17、Spring Boot、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 不新增数据库迁移，不改变冲突匹配范围和追加申请判定。
- 仅审批中冲突阻止保存草稿和提交审批。
- 复用现有撤回接口及异步工作流终止结果。
- 不修改当前 `rokey` 分支以外内容，不提交、不推送、不清理。

### T1：后端预检和不可绕过门禁

**需求映射：** R1, R2, R5

**文件：**
- 修改：`ReleaseApplicationModels.java`
- 修改：`ReleaseApplicationService.java`
- 修改：`ReleaseSubmissionService.java`
- 修改：`ReleaseApplicationController.java`
- 修改：`ReleaseApplicationServiceTest.java`
- 修改：`ReleaseSubmissionServiceTest.java`

**步骤：**
1. 增加创建/更新冲突预览服务和控制器端点，复用现有 `prepare`、`conflictFacts` 和详情映射，不执行插入或更新。
2. 抽取审批中冲突判定，并在创建、更新、提交、CREATE_NEW 冲突处理处强制拒绝。
3. 调整审批中历史申请不再暴露 `CREATE_NEW` 允许动作。
4. 增加单元测试并运行 `mvn -pl server/src/modules/release -am test`。

**回滚：** 同时恢复端点、服务门禁和测试。

### T2：前端弹框状态机和撤回轮询

**需求映射：** R1, R3, R4, R5, R6

**前置任务：** T1

**文件：**
- 修改：`web/src/api/release.ts`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/components/ReleaseConflictDialog.vue`
- 修改：`web/src/modules/release/release-prototype.css`

**步骤：**
1. 接入创建/更新预检 API，暂存原始保存模式、payload、附件和编辑身份。
2. 阻塞时不调用创建/更新；取消时返回表单；继续时重新预检并恢复原操作。
3. 在卡片右下角增加同申请人审批中申请的撤销按钮，保留原因输入并调用现有撤回 API。
4. 弹框打开时每 2 秒刷新；任意审批中冲突存在时禁用继续并展示原因。
5. 关闭弹框或组件卸载时清理轮询；运行前端构建和浏览器验证。

**回滚：** 同时恢复 API 接入、暂存状态、弹框模板和局部 CSS。

## 集成检查

- `mvn -pl server/src/modules/release -am test`
- `npm --prefix web run build`
- `git diff --check`
- 浏览器验证审批中同用户/其他用户、撤回中、撤回完成、取消和继续两种模式。

## 风险与批准

标准模式串行执行 T1 -> T2。用户已明确确认设计并要求直接实施。

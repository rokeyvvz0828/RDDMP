# 配置管理项目维度隔离实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 让配置管理六个菜单严格按顶部当前项目展示和处理，并以服务端统一项目访问校验阻止跨项目读写。

**架构：** 在 `platform/system` 的既有公开包中新增只读 `ProjectAccessService`，`business/release` 通过正式 Maven 依赖复用该能力。列表接口显式接收必填项目编码，实体接口从存量记录反查项目；前端继续使用唯一项目 Store，并在项目切换和直接链接场景同步上下文、丢弃过期响应。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、JUnit 5、Mockito、Vue 3、TypeScript、Pinia、Vue Router、Element Plus。

## 全局约束

- 不修改数据库结构、Flyway 脚本、审批流程、菜单权限种子或业务状态机。
- 租户与用户身份只取自 `AuthUser`，不得信任请求体中的身份和项目名称。
- 前端顶部项目只负责交互上下文，后端负责最终授权。
- 不覆盖、提交或清理工作区现有未关联改动。
- 新公共能力只放在已声明公开包 `com.ccb.system.capability`，release 只新增对 `platform/system` 的正式依赖。
- 前端沿用现有配置管理结构和公共 UI，不新增全局样式体系。

---

## 文件职责地图

- `governance/modules.yaml`：声明 release 对 system 公共能力的依赖。
- `server/src/platform/system/.../capability/ProjectAccess.java` 与 `ProjectAccessService.java`：项目访问公开契约。
- `server/src/platform/system/.../internal/capability/JdbcProjectAccessService.java`：项目编码解析和访问授权的唯一实现。
- `server/src/modules/release/pom.xml`：引入 `ccb-system`。
- release 五个领域目录下的 Service/Controller：列表项目范围、实体项目反查和请求一致性校验。
- `web/src/api/release.ts`：将列表项目参数收紧为必填。
- `web/src/stores/project-context.ts`：复用并明确可访问项目选择结果。
- release 页面与四个独立视图：项目切换清理、过期响应保护和直接链接同步。

## 任务依赖图与并行策略

```text
T1 统一项目访问能力
  -> T2 后端配置管理六域接入
    -> T3 前端项目上下文与直接链接
      -> T4 集成与浏览器验收
```

四个任务串行。T1 的公共接口是 T2 的编译前提，T2 的错误契约是 T3 的交互前提；T4 必须在前后端稳定后执行。工作区已有未提交改动，因此每个任务使用文件清单和差异证据作为检查点，不自动创建 Git 提交。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 六菜单当前项目范围 | T2, T3, T4 |
| R2 统一项目访问门禁 | T1, T2, T4 |
| R3 实体所属项目校验 | T2, T4 |
| R4 直接链接项目同步 | T3, T4 |
| R5 权限与业务兼容 | T1, T2, T3, T4 |

### T1：统一项目访问公共能力

**需求映射：** R2, R5

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/capability/ProjectAccess.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/capability/ProjectAccessService.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/internal/capability/JdbcProjectAccessService.java`
- 新建：`server/src/platform/system/src/test/java/com/ccb/system/internal/capability/JdbcProjectAccessServiceTest.java`
- 修改：`governance/modules.yaml`
- 修改：`server/src/modules/release/pom.xml`

**接口：**
- 消费：`AuthUser.id()`、`AuthUser.tenantId()`、`pm_project`、`pm_project_member`、`sys_user_role/sys_role`
- 产出：`ProjectAccessService.requireAccessible(String projectRef, AuthUser user)` 与只读 `ProjectAccess` 结果

- [ ] **步骤 1：建立项目访问失败测试**

覆盖有效成员、超级管理员、非成员、空项目编码、项目不存在、已删除或停用项目。断言非成员为 `ErrorCode.FORBIDDEN`，无效项目为稳定中文业务错误。

- [ ] **步骤 2：运行当前检查并确认失败信号**

运行：`mvn -pl :ccb-system -am -Dtest=JdbcProjectAccessServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：因 `ProjectAccessService` 尚不存在而编译失败。

证据：保存退出码和首个编译错误。

- [ ] **步骤 3：实施最小公共能力和依赖声明**

公开包只放 `ProjectAccess` 与 `ProjectAccessService`；internal 实现使用参数化 SQL 按 `tenant_id + project_code` 查询有效项目。超级管理员沿用 `SUPER_ADMIN` 角色规则，普通用户校验有效成员。更新治理依赖和 release Maven 依赖，不修改现有 `ProjectService`。

- [ ] **步骤 4：运行局部与治理回归**

运行：`mvn -pl :ccb-system -am -Dtest=JdbcProjectAccessServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：退出码 0，项目访问场景全部通过。

运行：`node scripts/check-all-governance.mjs`

预期：退出码 0，模块依赖和公开包检查通过。

- [ ] **步骤 5：建立差异检查点**

运行：`git diff --check -- governance/modules.yaml server/src/platform/system server/src/modules/release/pom.xml`

预期：退出码 0；只记录本任务差异，不暂存现有未关联文件。

**回滚：** 同时移除新增服务和测试，并回退 governance 与 release POM 的 `platform/system` 依赖。

**停止条件：** 现有 `pm_project.project_code` 在同租户内不唯一，或项目有效状态语义无法由现有表确定。

**升级条件：** 需要修改既有 `ProjectService` 公开行为、项目成员模型或数据库索引。

### T2：后端六域项目隔离

**需求映射：** R1, R2, R3, R5

**前置任务：** T1

**文件：**
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseApplicationController.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/window/service/ReleaseWindowService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/window/web/ReleaseWindowController.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/production/service/ReleaseProductionService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/production/web/ReleaseProductionController.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/reporting/service/ReleaseAnalyticsService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/reporting/web/ReleaseAnalyticsController.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/workflow/service/ReleaseWorkflowBindingService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/workflow/web/ReleaseWorkflowBindingController.java`
- 测试：任务范围中对应六个 Service 测试文件

**接口：**
- 消费：T1 的 `requireAccessible`、现有 Store 的 `findById/findByCode` 和实体 `projectId`
- 产出：列表项目参数必填、所有面向用户的实体操作执行项目访问门禁

- [ ] **步骤 1：为跨项目和缺参场景补失败测试**

应用域覆盖列表、详情、冲突、提交、撤回、取消和附件；窗口域覆盖列表、详情和写操作；投产域覆盖基线、结果维护、生产版本和历史；统计域覆盖汇总/下钻；流程配置覆盖列表、更新、历史和解析。

- [ ] **步骤 2：运行 release 测试并记录当前失败**

运行：`mvn -pl :ccb-release -am test`

预期：新增授权断言失败或构造函数尚未接入公共服务。

证据：保存失败测试名和实际/期望差异。

- [ ] **步骤 3：逐域接入项目访问校验**

列表先规范化并校验显式项目编码；实体操作先按租户读取目标，再校验实体项目。创建/修改使用 `ProjectAccess` 返回的规范编码和名称，并校验窗口、申请、基线条目及流程绑定属于同一项目。

- [ ] **步骤 4：收紧 Controller 参数契约**

投产窗口、版本申请、生产版本、统计汇总和下钻的项目参数改为必填；投产基线继续从 `windowId` 反查项目，生产历史继续从 entry/source 反查项目。内部工作流生命周期回调不增加前端项目参数。

- [ ] **步骤 5：运行局部和相关回归**

运行：`mvn -pl :ccb-release -am test`

预期：退出码 0；正常、缺参、无权限、跨项目和既有状态流测试全部通过。

- [ ] **步骤 6：建立差异检查点**

运行：`git diff --check -- server/src/modules/release server/src/platform/system governance/modules.yaml`

预期：退出码 0，无任务范围外后端文件变化。

**回滚：** 按域回退 Service/Controller/测试；若回退全部业务接入，同时执行 T1 回滚，避免保留无消费者依赖。

**停止条件：** 任一 Store 无法从目标实体获得项目编码，或同一批量投产结果包含多个项目且当前模型无法原子拒绝。

**升级条件：** 需要修改 rel 表结构、工作流公开协议或附件平台授权模型。

### T3：前端项目上下文与直接链接同步

**需求映射：** R1, R4, R5

**前置任务：** T2

**文件：**
- 修改：`web/src/api/release.ts`
- 修改：`web/src/stores/project-context.ts`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/ReleaseApplicationDetailPage.vue`
- 修改：`web/src/modules/release/components/ReleaseBaselineView.vue`
- 修改：`web/src/modules/release/components/ReleaseCurrentProductionView.vue`
- 修改：`web/src/modules/release/components/ReleaseAnalyticsView.vue`
- 修改：`web/src/modules/release/components/ReleaseWorkflowBindingView.vue`

**接口：**
- 消费：后端收紧后的项目参数、`ReleaseApplicationDto.projectId`、项目 Store 的可访问列表
- 产出：当前项目必填 API、直接链接自动选择、各视图过期响应保护

- [ ] **步骤 1：收紧 API 类型并运行构建基线**

将投产窗口、版本申请、生产版本和统计的 `projectId` 改为必填；投产基线和实体历史仍由 ID 反查。运行 `npm --prefix web run build`，预期暴露所有遗漏调用点。

- [ ] **步骤 2：实现项目切换状态清理**

捕获每次加载开始时的 `projectRef`，赋值前与 Store 当前值比较；切换时清空列表、统计、选择项和错误，关闭详情/编辑/历史/冲突弹层，停止冲突轮询。

- [ ] **步骤 3：实现直接链接同步**

详情页先初始化 Store，再调用受保护申请详情；详情所属项目在可访问列表中时调用 `select`，随后加载轮次、附件、相关历史和工作流。403 或目标不在列表时显示无权访问并停止后续请求。

- [ ] **步骤 4：覆盖独立菜单组件竞态**

为投产基线、生产版本、统计分析和审批流程配置的加载、历史和保存回调增加项目捕获检查；项目切换时关闭局部弹层并清空旧数据。

- [ ] **步骤 5：运行前端构建和差异检查**

运行：`npm --prefix web run build`

预期：退出码 0，无 TypeScript 参数遗漏。

运行：`git diff --check -- web/src/api/release.ts web/src/stores/project-context.ts web/src/modules/release`

预期：退出码 0。

**回滚：** 回退 API 必填类型和 release 页面项目同步逻辑；不删除用户保存的项目选择。

**停止条件：** 详情接口在授权前不能返回项目且现有项目列表无法判断目标项目，导致必须新增未确认的公开预解析接口。

**升级条件：** 需要修改全局路由守卫、应用壳层或公共 HTTP 拦截器。

### T4：集成、越权与响应式验收

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T3

**文件：**
- 证据：`.ai-control/requirements/req-20260831-057-release-project-scope/execution-T*.json`
- 证据：`.ai-control/requirements/req-20260831-057-release-project-scope/observation-T*.json`

**接口：**
- 消费：T1-T3 的完整前后端行为
- 产出：可重复测试结果、浏览器证据和残余风险结论

- [ ] **步骤 1：运行后端、前端和治理回归**

运行：`mvn -pl :ccb-system,:ccb-release -am test`

运行：`npm --prefix web run build`

运行：`node scripts/check-all-governance.mjs`

运行：`git diff --check`

预期：四条命令退出码均为 0；若全仓既有失败与本任务无关，记录准确失败和基线对比，不宣称通过。

- [ ] **步骤 2：启动并验证真实用户流程**

使用本地前后端服务，准备两个可访问项目和一个无成员关系项目。桌面验证六菜单切换、直接链接自动切换、无权链接拒绝、跨项目参数 403 和快速连续切换。

- [ ] **步骤 3：执行 H5 验收**

在 `390x844` 验证详情无横向溢出、错误提示可读、返回与审批操作可达；项目自动切换不引发布局跳动或旧内容闪回。

- [ ] **步骤 4：记录执行与独立观测证据**

每个任务记录实际文件、命令、结果和扰动；观察结果逐项映射 R1-R5。仅当全部 must 需求有通过证据时进入收敛验收。

**回滚：** 验收失败时不发布；按失败所属任务回退或进入限幅纠正，不修改需求设定值。

**停止条件：** 缺少两个项目的可验证账号/数据，或本地环境无法区分 400、403 与服务不可用。

**升级条件：** 出现存量跨项目脏数据、公共权限回归或必须修改数据库才能修复。

## 集成检查

- 后端：`mvn -pl :ccb-system,:ccb-release -am test`
- 前端：`npm --prefix web run build`
- 治理：`node scripts/check-all-governance.mjs`
- 差异：`git diff --check`
- 浏览器：桌面与 `390x844` 的两个有权项目、一个无权项目和快速切换旅程。

## 控制模型种子

- 被控边界候选：项目访问公共能力、release 六域服务、release API 和页面项目状态。
- 状态变量候选：当前 `projectRef`、实体 `projectId`、请求代次、弹层状态、后端授权结果。
- 传感器候选：JUnit/Mockito 断言、Maven/Vite 构建、治理检查、HTTP 400/403、浏览器可见数据和控制台错误。
- 执行器候选：项目参数必填、`requireAccessible`、实体反查、Store `select`、视图清理和过期响应丢弃。
- 扰动候选：存量脏项目编码、慢网络、用户快速切换、工作区已有修改、本地测试数据不足。
- 时延候选：并行接口返回顺序、工作流状态刷新、项目列表初始化。
- 种子状态：仅为假设，进入 `$control-engineering` 后由建模阶段验证。

## 风险与用户批准

- 高风险点是公共平台能力与后端数据范围，不是视觉改动。
- 没有迁移或生产操作；回退面是 Java/Vue/治理契约。
- 用户于 2026-08-31 明确同意按推荐方案处理；本计划状态为可移交。

# 平台全局操作审计日志实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 为平台关键操作和敏感查询提供不泄露业务正文、支持项目数据范围和自动保留清理的统一审计能力。

**架构：** `platform/boot` 负责 HTTP 采集和策略判定，`platform/system` 负责请求语义上下文、持久化、查询权限和清理。V156 扩展存量表并增加菜单；Vue 页面复用公共列表、工具栏、状态标签和移动卡片模式。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring MVC/Security、JdbcTemplate、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Pinia。

## 全局约束

- 只追加 V156，不修改历史迁移。
- 只记录确认范围，不保存请求/响应正文和任何敏感值。
- 项目归属来自服务端可信参数或上下文，未知时为空。
- 操作日志最多一条/HTTP 请求；领域专用审计表保留。
- 审计失败不改变业务结果；查询权限必须在 SQL 层收敛。
- UI/API 只暴露 30 天，数据库保留 180 天。
- 实现仅修改 `codex-task-scope.yaml` 授权路径。

## 任务依赖与覆盖

串行执行 `T1 -> T2 -> T3`。R1-R4 由 T1 覆盖，R5-R6 由 T2 覆盖，R7 由 T3 覆盖，R8 由全量集成检查覆盖。

### T1：统一采集、去重和数据迁移

**需求映射：** R1, R2, R3, R4

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V156__platform_operation_audit.sql`
- 新建：`server/src/shared/common/src/main/java/com/ccb/common/audit/OperationAuditContext.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/internal/capability/JdbcSystemOperationAudit.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/service/SystemService.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/service/SystemNotificationService.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 新建：`server/src/platform/boot/src/main/java/com/ccb/boot/audit/OperationAuditPolicy.java`
- 新建：`server/src/platform/boot/src/main/java/com/ccb/boot/audit/OperationAuditInterceptor.java`
- 新建：`server/src/platform/boot/src/main/java/com/ccb/boot/audit/OperationAuditRequestBodyAdvice.java`
- 新建：`server/src/platform/boot/src/main/java/com/ccb/boot/audit/OperationAuditWebConfiguration.java`
- 测试：`server/src/platform/boot/src/test/java/com/ccb/boot/audit/OperationAuditPolicyTest.java`

**接口：**
- 消费：认证主体、MVC 路径变量、请求参数、响应状态、Trace ID、现有 `SystemOperationAudit`。
- 产出：每个纳入请求一条脱敏 `sys_operation_log`；非 HTTP 手工审计兼容落库。

- [ ] **步骤 1：建立策略和上下文失败测试**

  断言关键写入、敏感 GET、普通 GET、预览校验排除、更新字段名去敏和重复手工审计合并。

  运行：`mvn -pl :ccb-boot -am -Dtest=OperationAuditPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：新增类型缺失或断言失败。

- [ ] **步骤 2：追加 V156 并实现请求上下文**

  V156 添加可空字段、索引、审计菜单和超级管理员授权。上下文使用 ThreadLocal 且在 finally 清理，只接受字段名和受限长度元数据。

  运行：`node scripts/check-flyway-migrations.mjs && git diff --check`

  预期：迁移命名、顺序和空白检查通过。

- [ ] **步骤 3：实现拦截器、集中策略和去重适配**

  拦截器在请求完成后独立写入；原手工审计在活跃请求内只补充语义，非请求场景按旧行为落库。失败只保存 HTTP 状态或异常类型。

  运行：`mvn -pl :ccb-boot -am -Dtest=OperationAuditPolicyTest,JdbcSystemOperationAuditTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：零失败，单请求单日志和非 HTTP 兼容断言通过。

**回滚：** 回退 Java 采集和上下文；保留 V156 可空列，菜单可通过后续迁移停用。

**停止条件：** 请求上下文跨线程泄漏、必须缓存原始请求正文、同一请求仍产生重复通用日志。

**升级条件：** 需要修改业务模块私有 DTO、信任客户端项目头或替换领域专用审计。

### T2：查询权限、30 天视图和 180 天清理

**需求映射：** R5, R6

**前置任务：** T1

**文件：**
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/audit/OperationAuditService.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/audit/OperationAuditController.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/audit/OperationAuditRetentionJob.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/audit/OperationAuditRecord.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/audit/LoginAuditRecord.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/audit/AuditProjectOption.java`
- 修改：`server/src/platform/security/src/main/java/com/ccb/security/repository/AuthRepository.java`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/audit/OperationAuditServiceTest.java`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/audit/OperationAuditRetentionJobTest.java`

**接口：**
- 消费：T1 扩展日志表、项目 owner/PM 关系、认证主体。
- 产出：`GET /api/system/audit/operations|logins|projects` 和每日 180 天清理。

- [ ] **步骤 1：建立权限与时间边界失败测试**

  覆盖超级管理员、owner/PM、普通成员、跨项目、登录日志限制、默认/越界日期和清理汇总。

  运行：`mvn -pl :ccb-system -am -Dtest=OperationAuditServiceTest,OperationAuditRetentionJobTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：新增服务缺失或权限断言失败。

- [ ] **步骤 2：实现 SQL 级数据范围和查询 API**

  所有查询固定 tenant，项目管理员增加 `EXISTS(owner/PM)`，日期下限使用服务端当前时间减 30 天。登录日志接口只接受超级管理员。

  运行：同步骤 1。

  预期：零失败且 SQL 参数包含租户、日期和项目范围。

- [ ] **步骤 3：实现保留任务和动态菜单可见性**

  每日批量删除两表 180 天前数据，写一条系统汇总日志；认证路由仅向超级管理员或拥有 owner/PM 项目的用户返回审计入口。

  运行：`mvn -pl :ccb-system -am test`

  预期：system 模块零失败。

**回滚：** 回退 API、动态路由和任务；日志继续保留，后续恢复不丢数据。

**停止条件：** 查询必须先取全量再内存过滤、项目管理员可读取登录日志或跨项目数据。

**升级条件：** 需要新增项目管理员业务角色定义或更改现有项目成员模型。

### T3：审计日志页面与全量验收

**需求映射：** R7, R8

**前置任务：** T2

**文件：**
- 修改：`web/src/api/system.ts`
- 修改：`web/src/types/system.ts`
- 修改：`web/src/router/index.ts`
- 新建：`web/src/views/AuditLogView.vue`

**接口：**
- 消费：T2 三个查询接口和当前认证角色。
- 产出：桌面表格、移动卡片、角色化页签、筛选、分页和详情展示。

- [ ] **步骤 1：实现类型、API 和静态路由**

  路由 `/system/audit` 使用权限 `system:audit:list`；页面默认最近 30 天并只在超级管理员角色下显示登录页签。

  运行：`npm --prefix web run build`

  预期：TypeScript 与 Vite 构建通过。

- [ ] **步骤 2：实现桌面表格和移动卡片**

  复用 `UiPageHeader`、`UiToolbar`、`UiDataTable`、`UiStatusTag`。详情不显示任何正文值，只显示允许的审计元数据。

  运行：`npm --prefix web run build && git diff --check`

  预期：构建和空白检查通过。

- [ ] **步骤 3：全量与治理检查**

  运行：`mvn test`

  运行：`npm --prefix web run build`

  运行：`node scripts/check-all-governance.mjs`

  运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260904-064-platform-operation-audit/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`

  预期：全部退出码 0，差异仅在授权路径。

- [ ] **步骤 4：真实浏览器验收**

  在 `/system/audit` 使用超级管理员和项目管理员验证桌面、`375x812`、`390x844`、`430x932`；检查菜单、页签、筛选、分页、详情、无权限、无数据、控制台和页面宽度。

  预期：角色范围正确，操作可达，无横向溢出和控制台错误。

**回滚：** 回退前端四个文件，后端审计仍可继续采集和保留。

**停止条件：** 接口字段与页面类型不一致、普通用户可见菜单、移动端必须依赖整页横向滚动。

**升级条件：** 需要修改公共 UI 组件或增加日志编辑、导出能力。

## 控制模型种子

- 状态：`hypotheses-only`。
- 边界候选：认证 HTTP 请求、通用操作日志、登录日志、查询权限、保留任务和审计页面。
- 状态变量候选：是否纳入、操作类型、业务语义、项目归属、响应状态、保留时间和用户角色。
- 传感器候选：策略单元测试、JdbcTemplate SQL 测试、Flyway 检查、模块/全量 Maven、Vue 构建、治理范围和浏览器视口。
- 执行器候选：拦截器策略、请求上下文、V156、SQL 数据范围、定时删除、动态路由和 Vue 页面。
- 扰动候选：旧手工日志、异常处理吞并异常、代理 IP、缺失项目参数、删除用户/项目、跨午夜日期和大日志量。
- 假设：后端端口只经可信反向代理暴露；存量 `SystemOperationAudit` 调用在同一请求线程；反例出现时停止并调整模型。

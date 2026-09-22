# 投产演练复用架构环境管理实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：[2026-09-18-release-drill-architecture-environment-design.md](D:/ai_project/ccb_rd/docs/engineering-control/designs/2026-09-18-release-drill-architecture-environment-design.md)
- 状态：待用户确认

## 目标与全局约束

移除投产模块私有演练环境，以架构模块项目级 ACTIVE 环境作为投产演练的唯一数据源，并按用户确认不可逆地清理旧环境与依赖演练数据。

- 仅修改 `REQ-20260918-084` 的 `writable_paths`；不读 `.env`、不访问生产。
- 投产模块只消费架构 `com.ccb.architecture.integration` 公开契约，不查询 `arch_` 表。
- `environmentId` HTTP 字段保持不变，但存储/读取语义改为 `arch_environment.id`。
- 只追加 V217，不能改写 V153；V217 的数据删除必须经 MySQL 集成测试验证。
- 维持认证、租户、项目成员、RBAC、乐观锁和审计；旧环境 API 不保留兼容层。

## 文件职责地图

- `ReleaseMasterDataQuery.java`：架构向投产公开 ACTIVE 项目环境投影。
- `JdbcReleaseMasterDataQuery.java`：以 tenant/project/status 条件实现环境查询和精确解析。
- `ReleaseArchitectureDirectory.java`、Boot adapter：隔离投产模块与架构实现的消费端口。
- `EnvironmentResourceService/Store`：在架构环境删除前执行投产演练引用检查。
- `ReleaseOperationsService/Store/Models/Controller`：移除私有环境 CRUD，使用架构环境投影校验与回显演练轮次。
- `ReleaseDrillExecutionView.vue`：以架构环境选项维护演练轮次；`ReleaseDrillEnvironmentView.vue` 删除。
- `V217__release_drill_use_architecture_environment.sql`：清理旧数据、删除表、下线菜单权限。

## 任务依赖图与并行策略

`T1 架构公开查询与删除守卫 -> T2 投产服务和前端切换 -> T3 V217 清理迁移 -> T4 集成验证`。

所有任务串行。T2 依赖 T1 的稳定环境投影；T3 仅在新引用读写可用后执行；T4 观察完整组合。

## 需求覆盖表

| 需求 | 覆盖任务 |
| --- | --- |
| R1 私有能力移除 | T2, T3 |
| R2 架构环境选择 | T1, T2 |
| R3 旧数据清理 | T3 |
| R4 引用删除保护 | T1, T4 |
| R5 模块/授权边界 | T1, T2, T4 |

### T1：发布环境公开投影与架构删除引用守卫

**需求映射：** R2, R4, R5

**前置任务：** 无

**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/integration/ReleaseMasterDataQuery.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/JdbcReleaseMasterDataQuery.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseArchitectureDirectory.java`
- 修改：`server/src/platform/boot/src/main/java/com/ccb/boot/release/ReleaseArchitectureDirectoryAdapter.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/service/EnvironmentResourceService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/persistence/EnvironmentResourceStore.java`
- 修改：`governance/modules.yaml`、`docs/integration/architecture-module-contract.md`
- 测试：`JdbcReleaseMasterDataQueryTest.java`、环境服务/持久化聚焦测试、`ReleaseArchitectureDirectoryAdapterTest.java`

**接口：**
- 消费：现有 `ReleaseMasterDataQuery`、`arch_environment` 的 project/tenant/status 条件。
- 产出：`EnvironmentRef(id, code, name, typeName)` 的列表和精确 ACTIVE 解析；架构环境删除时可检查 `rel_release_drill_round.environment_id` 的活动引用。

- [ ] **步骤 1：增加失败测试**

在 `JdbcReleaseMasterDataQueryTest` 添加同项目 ACTIVE 环境返回、跨项目/停用环境精确解析为空的断言；在环境删除测试中添加演练引用时冲突断言。

运行：`mvn -pl :ccb-architecture -am -Dtest=JdbcReleaseMasterDataQueryTest,EnvironmentResourceServiceTest test`

预期：当前实现缺少环境投影和引用守卫，测试不能通过。

- [ ] **步骤 2：扩展公开契约并实现投影**

在 `ReleaseMasterDataQuery` 添加 `listActiveEnvironments(AuthUser,long)` 与 `resolveActiveEnvironment(AuthUser,long,long)`；实现 SQL 必须包含 `tenant_id = ? AND project_id = ? AND deleted = 0 AND status = 'ACTIVE'`。在 release 端口和 Boot adapter 中映射同名最小 DTO。

- [ ] **步骤 3：实现删除引用守卫**

在架构环境删除事务中检查当前租户、项目和未删除的 `rel_release_drill_round.environment_id`；存在引用时返回可展示的冲突信息，不执行删除。同步更新模块依赖声明与集成契约。

- [ ] **步骤 4：运行局部回归**

运行：`mvn -pl :ccb-architecture -am -Dtest=JdbcReleaseMasterDataQueryTest,EnvironmentResourceServiceTest test`

预期：0 失败；跨项目、停用和引用删除均被拒绝。

**回滚：** 回退本任务代码和契约，不改变数据。

**停止条件：** 架构删除守卫需要投产模块直接依赖架构私有包或产生循环 Maven 依赖。

**升级条件：** 已有架构环境删除语义无法在事务内检查投产引用。

### T2：投产演练切换架构环境并移除私有入口

**需求映射：** R1, R2, R5

**前置任务：** T1

**文件：**
- 修改：`ReleaseOperationsController.java`、`ReleaseOperationsService.java`、`ReleaseOperationsStore.java`、`ReleaseOperationsModels.java`
- 修改：`ReleaseOperationsControllerSecurityTest.java`、`ReleaseOperationsServiceTest.java`
- 修改：`web/src/api/release.ts`、`web/src/router/index.ts`
- 修改：`web/src/modules/release/ReleaseOperationsManagement.vue`、`web/src/modules/release/components/ReleaseDrillExecutionView.vue`
- 删除：`web/src/modules/release/components/ReleaseDrillEnvironmentView.vue`

**接口：**
- 消费：T1 的 `ReleaseArchitectureDirectory` 环境列表/解析。
- 产出：投产演练读取和写入使用架构环境 ID、名称、编码、类型；旧 `/release/operations/environments` 端点和类型不存在。

- [ ] **步骤 1：增加服务与控制器失败测试**

模拟目录返回空，验证 `saveReleaseDrill` 拒绝；模拟有效环境，验证写入其 ID；验证旧环境端点不再受控制器暴露。

运行：`mvn -pl :ccb-release -am -Dtest=ReleaseOperationsServiceTest,ReleaseOperationsControllerSecurityTest test`

预期：当前服务继续调用私有 environment store，测试不能通过。

- [ ] **步骤 2：移除私有环境模型与 CRUD**

删除 `DrillEnvironment`/请求 DTO、控制器 `/environments` 四个端点、服务和 store 私有环境方法。重写演练 SQL 为与 `arch_environment` 关联以回显 `code,name,type_code`；服务保存前经 T1 目录解析并用投影构造轮次。

- [ ] **步骤 3：切换前端路由与演练表单**

删除环境路由、routeMap 条目和组件导入；从 `api/release.ts` 去除私有环境 API，演练页面改用架构环境读取 API 或投产受控环境选项接口。无有效环境时显示提示、禁用新建；保留加载、错误、无权限与长名称样式。

- [ ] **步骤 4：运行发布与前端检查**

运行：`mvn -pl :ccb-release -am -Dtest=ReleaseOperationsServiceTest,ReleaseOperationsControllerSecurityTest test`

运行：`npm --prefix web run build`

预期：服务测试与前端生产构建通过，私有环境菜单/路由/类型无残留。

**回滚：** 在 V217 前可回退应用代码；迁移执行后不能重新启用已清空的旧私有环境。

**停止条件：** 前端只能通过直接导入架构模块私有 API 才能加载环境。

**升级条件：** 现有投产权限无法允许已授权成员读取架构环境，且需要改变角色模型。

### T3：V217 不可逆旧数据清理和菜单权限下线

**需求映射：** R1, R3

**前置任务：** T2

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V217__release_drill_use_architecture_environment.sql`
- 测试：新增或扩展 release/architecture MySQL 迁移集成测试

**接口：**
- 消费：T2 的架构环境 ID 语义。
- 产出：无 `rel_drill_environment` 表、无旧演练/步骤/关联问题，菜单 1006 和两项环境权限/角色授权逻辑下线。

- [ ] **步骤 1：建立迁移基准测试**

向隔离 MySQL 种子插入私有环境、轮次、步骤、演练关联问题和菜单授权，断言升级 V217 后当前尚会留下这些记录/表。

运行：`mvn -pl :ccb-release -am -Dtest=ReleaseDrillArchitectureEnvironmentMigrationTest test`

预期：迁移测试先失败，因为 V217 不存在。

- [ ] **步骤 2：追加清理迁移**

以外键/关联安全顺序删除 `rel_release_issue` 中关联旧轮次的行、`rel_release_drill_step`、`rel_release_drill_round`、`rel_drill_environment` 数据并 `DROP TABLE rel_drill_environment`；将 `sys_menu` 1006 和关联 `sys_menu_permission`、`sys_role_menu`、`sys_role_permission` 逻辑下线或删除授权。不得改写 V153。

- [ ] **步骤 3：验证迁移结果**

运行：`mvn -pl :ccb-release -am -Dtest=ReleaseDrillArchitectureEnvironmentMigrationTest test`

预期：旧数据计数为 0、私有表不存在、演练表保留 `environment_id`、菜单与权限不再有效。

**回滚：** 无应用级数据回退；恢复必须使用 V217 前经确认的备份。

**停止条件：** 发现未识别的外键或其他模块引用旧私有表。

**升级条件：** 清理范围超过用户确认的私有环境、演练轮次、步骤与关联问题。

### T4：跨模块集成、范围与用户路径观测

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T3

**文件：**
- 测试：T1-T3 测试及前端构建产物；当前任务 `.ai-control` 执行与观测记录。

**接口：**
- 消费：全部已实现接口和 V217。
- 产出：范围、构建、迁移、权限与浏览器路径证据。

- [ ] **步骤 1：运行集成检查**

运行：`mvn -pl :ccb-release -am test`

运行：`mvn -pl :ccb-architecture -am test`

运行：`npm --prefix web run build`

运行：`node scripts/check-all-governance.mjs`

运行：`git diff --check`

预期：全部通过；没有私有环境的 API/路由残留。

- [ ] **步骤 2：运行路径观测**

以项目成员身份在架构环境管理创建 ACTIVE 环境，进入投产演练创建轮次；停用/跨项目环境保存应失败；被引用环境删除应失败。检查桌面、375x812、390x844、430x932 无页面级横向溢出。

- [ ] **步骤 3：记录不可逆风险和提交检查点**

在当前任务账本写入实际命令、迁移清理结果、浏览器观察或工具限制。提交前只暂存任务 scope 文件。

**回滚：** 回退应用变更；若误执行 V217，只能从预迁移备份恢复删除数据。

**停止条件：** 任何测试显示越权、跨项目选择、遗留菜单或数据清理超范围。

**升级条件：** 浏览器或迁移验证发现需要改变架构环境 CRUD 的既有业务规则。

## 集成检查

在 T4 后执行：`mvn -pl :ccb-release -am test; mvn -pl :ccb-architecture -am test; npm --prefix web run build; node scripts/check-all-governance.mjs; git diff --check`。

## 控制模型种子

- 被控边界候选：架构环境公开投影、投产演练环境解析、环境删除守卫、V217 清理顺序。
- 状态候选：`arch_environment.status`、演练 `environment_id`、旧数据计数、动态菜单权限状态。
- 传感器候选：聚焦 Java 测试、MySQL 迁移测试、前端构建、菜单/浏览器检查。
- 执行器候选：限定 Java/TS/Vue、V217、集成契约和任务账本改动。
- 扰动候选：脏工作区、旧数据未知关联、浏览器自动化不可用、Flyway 执行时延。

## 风险与用户批准

V217 会永久删除旧私有环境、演练轮次、步骤和关联问题。用户已确认不保留兼容；仍须在执行前完成迁移测试，并在上线前确认可用备份。

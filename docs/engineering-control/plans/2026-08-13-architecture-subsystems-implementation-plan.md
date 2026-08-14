# 物理子系统与逻辑子系统实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：3
- 设计修订：4
- 设计文档：`docs/engineering-control/designs/2026-08-12-architecture-subsystems-design.md`
- 状态：已批准
- 批准证据：用户先批准进入开发，随后针对 T0 的两项阻塞明确要求“修复这两个问题”，授权版本重分配与开发入口兼容修复。
- 旧计划处理：修订 1 的动态 schema 和旧物理字段设计继续失效；修订 2 的业务范围保持不变，仅替换迁移版本与 T0 治理传感器。

**目标：** 在独立 `business/architecture` 模块内交付逻辑/物理子系统强类型 CRUD、平台受控引用、统一审计、Flyway/Mock 初始化和桌面/移动页面。

**架构：** 领域服务、表、API 和 Vue 页面归 architecture；platform/system 只新增无架构语义的用户/参数查询与操作审计契约，组织复用现有公开服务。固定表单不请求动态 schema。当前需求取得主干实际连续的 V35—V37；未落地的其他需求顺延到 V38—V40。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MySQL 8.4、Flyway、JUnit 5、Mockito、Testcontainers、Vue 3、TypeScript、Element Plus。

## 全局约束

- 只修改 `codex-task-scope.yaml` 的 `writable_paths`；其他任务账本、shared/security、历史迁移和存量表单元数据代码只读或禁止。
- `tenant_id` 运行期只来自 `AuthUser`，命令和页面不声明；新表非空且无默认值。
- 编码和提交前确认 `origin/main` 最高仍为 V34，V35—V37 未被实际文件占用；冲突时停止并重新分配，不覆盖迁移。
- platform/system 公共契约必须保持架构中立，公开包与内部实现分离；既有系统 API 行为保持不变。
- 固定表单不得新增 `biz_form_*`、form-schema、PublishedFormSchemaQuery、元数据缓存或任意动态字段渲染。
- V1 不新增启用、停用、status 请求字段或状态变更权限。
- Flyway 只追加；生产回退不 DROP 表、不删除业务记录和审计证据。
- 小步提交；每个任务提交前运行局部测试和 `git diff --check`，不得混入无关格式化。

## 编码前硬门禁

T0 必须同时取得以下证据，否则不得执行 T1：

1. 修复后的 `node scripts/check-development-entry.mjs --require-plugin` 退出码 0；优先验证 CLI JSON，兼容桌面 config/cache 的 enabled+manifest 双证据。
2. scope 的 `requirement.codex_allowed=true`，`public_capability_change.owner_approved=true`，且用户批准本计划修订 2。
3. 当前 Git 分支/工作树与 scope 声明一致，并保留现有未跟踪设计资产；使用 `$using-git-worktrees` 处理隔离。
4. `origin/main` 实际最高为 V34，V35—V37 没有文件占用；只存在未实施需求文档不视为已发布迁移。
5. 同步主干后菜单 ID 600—602、权限 ID 6011—6024、参数 ID 360001—360106 和迁移 V35—V37 均未被占用。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/modules/architecture/pom.xml` | candidate-new | ccb-architecture 依赖和测试运行时 |
| `server/src/modules/architecture/AGENTS.md` | candidate-new | 模块局部边界 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/**` | candidate-new | 领域命令、记录、查询和响应投影 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/repository/ArchitectureSubsystemRepository.java` | candidate-new | 仅访问两个 arch 表的租户 SQL |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/**` | candidate-new | 逻辑/物理用例、事务、校验和审计 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/**` | candidate-new | CRUD/options HTTP、权限和局部 404 |
| `server/src/modules/architecture/src/test/**` | candidate-new | 单元、MockMvc、MySQL 迁移与并发传感器 |
| `server/src/platform/system/src/main/java/com/ccb/system/capability/**` | candidate-new | 用户/参数查询和审计公开契约 |
| `server/src/platform/system/src/main/java/com/ccb/system/internal/capability/**` | candidate-new | 平台表私有 JdbcTemplate 实现 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V35__extend_operation_log_trace.sql` | candidate-new | 审计 trace 兼容扩展 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V36__create_architecture_subsystems.sql` | candidate-new | 两个业务表和约束 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V37__seed_architecture_subsystem_catalog.sql` | candidate-new | 菜单、权限、管理员授权和六类参数 |
| `server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java` | existing | local Mock 白名单和引用校验 |
| `mock/mock-data.json` | existing | 虚构架构演示数据 |
| `web/src/modules/architecture/**` | candidate-new | 固定强类型页面、组件、API、类型和样式 |
| `web/src/router/index.ts` | existing | 两条静态业务路由 |
| `pom.xml`、`server/src/platform/boot/pom.xml` | existing | reactor、依赖管理和 Boot 装配 |
| `governance/modules.yaml`、`docs/architecture/MODULES.md`、`.github/CODEOWNERS` | existing | 模块边界、公开包和所有权 |
| `docs/integration/architecture-module-contract.md` | candidate-new | 稳定 HTTP、平台 capability 和数据语义 |

## 任务依赖图与并行策略

```text
T0 -> T1 -> T2 -> T3 -> T4 -> T5 -> T6 -> T9 -> T10
                    |      |
                    |      +--------> T7 -> T8 --+
                    +-----------------------------+
```

- T4 与 T7 可在 T3 后并行：分别修改 architecture Java 和 V37 SQL，没有共享写入面；二者都完成后再进入 T8/T10。
- 其余任务串行。T5 会修改 T4 建立的仓储/服务边界；T6 消费 T4/T5 的稳定响应；T9 消费最终 HTTP DTO。
- MySQL 容器、Boot 端口和浏览器环境属于共享运行资源，即使文件无冲突也不并行运行集成套件。

## 需求覆盖

| 需求 | 任务 |
| --- | --- |
| R1 | T3、T4、T9、T10 |
| R2 | T3、T5、T9、T10 |
| R3 | T3、T4、T5、T10 |
| R4 | T2、T4、T5、T6、T10 |
| R5 | T3、T5、T10 |
| R6 | T2、T4、T5、T6、T7、T10 |
| R7 | T2、T4、T5、T6、T7、T9、T10 |
| R8 | T0、T7、T8、T10 |
| R9 | T9、T10 |
| R10 | T7、T9、T10 |
| R11 | T0、T1、T10 |
| R12 | T3、T4、T5、T8、T10 |
| R13 | T3、T5、T8、T9、T10 |

### T0：关闭开发入口、Owner、分支和迁移前置门禁

**需求映射：** R8、R11

**前置任务：** 无

**文件：** 无产品代码修改；只产生当前任务执行/观测证据。

**接口：**

- 消费：最新 `origin/main`、scope、插件状态、当前工作树。
- 产出：可判别的 execution-ready 门禁记录，包含每项命令、退出码和迁移/ID 清单。

- [ ] **步骤 1：重新同步并保护工作区。** 运行 `git status --short --branch`、`git fetch origin`、`git rev-list --left-right --count HEAD...origin/main`；如需切换隔离分支，使用 `$using-git-worktrees`，不覆盖当前未跟踪设计文件。
- [ ] **步骤 2：修复并验证开发入口。** 让脚本在不同 Codex CLI 版本和桌面端插件安装来源下使用可靠双证据；运行 `node scripts/check-development-entry.mjs --require-plugin`，预期退出码 0，并运行无插件伪环境负向检查防止误放行。
- [x] **步骤 3：验证 scope 解锁字段。** 读取 scope，确认 `codex_allowed=true`、`issue=REQ-20260812-021`、`owner_approved=true`。本步骤不运行依赖新模块登记的全量 scope 检查；T1 先登记 `business/architecture`，随后立即运行全量 scope 检查，失败则停止 T2。
- [ ] **步骤 4：验证迁移连续性和分配。** 运行 `git ls-tree -r --name-only origin/main server/src/platform/infrastructure/src/main/resources/db/migration`；必须确认最高为 V34、V35—V37 不存在，并核对其他未实施需求已顺延为 V38—V40。
- [ ] **步骤 5：重扫稳定 ID 和最新差异。** 对迁移、requirements 和 designs 搜索菜单/权限/参数 ID；任何占用都回到 modeling 重新分配，不临时换号。

**验收：** 五项硬门禁全部有退出码或文件清单证据；没有产品 diff。

**回滚：** 无产品改动；若创建隔离工作树失败，保留原工作树并按 Skill 安全清理候选目录。

**停止条件：** 任一硬门禁失败；目标主干已实际占用 V35—V37；存在用户工作覆盖风险。

**升级条件：** 需要修改 governance 脚本、其他需求迁移、shared/security 或重新分配公共 ID。

### T1：建立 architecture 模块装配与治理边界

**需求映射：** R11

**前置任务：** T0

**文件：**

- 新建：`server/src/modules/architecture/pom.xml`
- 新建：`server/src/modules/architecture/AGENTS.md`
- 修改：`pom.xml`
- 修改：`server/src/platform/boot/pom.xml`
- 修改：`governance/modules.yaml`
- 修改：`docs/architecture/MODULES.md`
- 修改：`.github/CODEOWNERS`

**接口：**

- 消费：现有 root parent、ccb-common/infrastructure/security/system artifact。
- 产出：`ccb-architecture` reactor 模块；允许依赖 common/infrastructure/security/system；公开 Java 包仍为空；Boot 和 frontend application 允许依赖 architecture；platform/system 公开 `com.ccb.system.capability`。

- [ ] **步骤 1：建立模块边界失败信号。** 先运行 `node scripts/check-module-boundaries.mjs` 和 scope 检查，记录当前 `Unknown assignment module: business/architecture`。
- [ ] **步骤 2：创建最小 POM 和模块 AGENTS。** POM 依赖 common、infrastructure、security、system、starter-test、Testcontainers JUnit/MySQL test scope；不增加业务反向依赖。AGENTS 明确只访问 arch 表、平台只走公开包、禁止 tenant/status/schema。
- [ ] **步骤 3：装配 root 与 Boot。** 在 dependencyManagement/modules 和 Boot dependencies 各增加一次 `ccb-architecture`，保持现有顺序和模块不变。
- [ ] **步骤 4：登记治理。** modules.yaml 新增 `business/architecture` 和精确后端/前端/契约路径；同步 Boot/frontend 允许依赖、system public package/contracts、MODULES.md 和 CODEOWNERS。
- [ ] **步骤 5：验证并提交。** 运行 `mvn -pl :ccb-architecture -am test -DskipTests`、模块边界、governance、全量 `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260812-021-architecture-subsystems/codex-task-scope.yaml --base origin/main --head HEAD --working-tree` 和 `git diff --check`；scope 未通过则停止 T2。提交 `chore(architecture): register subsystem module`。

**验收：** Maven 能解析新 artifact；治理脚本识别 business/architecture；platform/shared 没有反向依赖。

**回滚：** 回退 T1 提交，删除空模块目录和所有登记，不影响数据库。

**停止条件：** 需要重排现有模块、修改共享包或 scope 无法覆盖文件。

**升级条件：** modules.yaml 校验器不支持新模块类型或现有 Owner 与 scope 不一致。

### T2：交付用户/参数查询、统一审计和 V35

**需求映射：** R4、R6、R7

**前置任务：** T1

**文件：**

- 新建：`server/src/platform/system/src/main/java/com/ccb/system/capability/SystemReferenceQuery.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/capability/SystemUserReference.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/capability/SystemParameterReference.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/capability/SystemOperationAudit.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/capability/SystemOperationAuditCommand.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/internal/capability/JdbcSystemReferenceQuery.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/internal/capability/JdbcSystemOperationAudit.java`
- 新建：`server/src/platform/system/src/test/java/com/ccb/system/internal/capability/JdbcSystemReferenceQueryTest.java`
- 新建：`server/src/platform/system/src/test/java/com/ccb/system/internal/capability/JdbcSystemOperationAuditTest.java`
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V35__extend_operation_log_trace.sql`

**接口：**

```java
PageResult<SystemUserReference> searchActiveUsers(AuthUser actor, PageQuery page, String keyword);
Optional<SystemUserReference> findUser(AuthUser actor, long userId);
List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode);

void recordSuccess(SystemOperationAuditCommand command);
void recordFailure(SystemOperationAuditCommand command);
```

`SystemUserReference` 包含 `id/displayName/username/phone/active`；`SystemParameterReference` 包含 `code/label`。命令从 `AuthUser` 派生租户和操作者，不允许调用方单独传 tenant；failure 使用 `REQUIRES_NEW`，success 参与调用方事务。

- [ ] **步骤 1：先写失败测试。** 覆盖 tenant/deleted/status、关键字转义、分页、phone 映射、参数分类不存在/停用、输出字段；审计覆盖成功参与事务、失败独立事务、错误限长、trace null/非 null 和旧 INSERT 兼容。
- [ ] **步骤 2：运行失败信号。** `mvn -pl :ccb-system -am -Dtest=JdbcSystemReferenceQueryTest,JdbcSystemOperationAuditTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期类不存在或断言失败。
- [ ] **步骤 3：实现公开接口和私有 JdbcTemplate。** SQL 固定表名/列名，全部 tenant 限定；参数分类只把 categoryCode 当值绑定，不拼 SQL；不返回 password/avatar object key/last login。
- [ ] **步骤 4：追加 V35。** 只增加 `trace_id VARCHAR(64) NULL` 和 `(tenant_id,trace_id)` 索引；不改既有脚本，不改变现有列默认值。
- [ ] **步骤 5：局部与回归。** 运行聚焦测试、`mvn -pl :ccb-system -am test`、Flyway 检查和 diff check；提交 `feat(system): expose safe references and operation audit`。

**验收：** 平台公开类型无 architecture 字段；用户和参数安全查询按租户工作；审计事务语义及 trace 可复核；现有 system 测试不回归。

**回滚：** 先确保业务消费者未合入或已回退，再回退 Java 契约；V35 保留且可空列不影响旧应用。

**停止条件：** 主干已出现冲突的 V35；需要修改 SystemService/SystemController/OrganizationService 或共享异常；现有日志写入回归。

**升级条件：** 需要扩大公开包到 `com.ccb.system.service`，或 audit 事务无法在当前 Spring 边界表达。

### T3：建立 V36 数据模型、仓储与 MySQL 传感器

**需求映射：** R1、R2、R3、R5、R12、R13

**前置任务：** T2

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V36__create_architecture_subsystems.sql`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/LogicalSubsystem.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/PhysicalSubsystem.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/LogicalSubsystemCommand.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/PhysicalSubsystemCommand.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/LogicalSubsystemQuery.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/PhysicalSubsystemQuery.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/repository/ArchitectureSubsystemRepository.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureMigrationMySqlTest.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureSubsystemRepositoryTest.java`

**接口：** 仓储方法全部显式接收 `tenantId`；父锁方法可读取已删除父记录，普通详情默认只读活动记录。命令没有 tenant、phone、snapshot、status、deleted 或审计字段。

- [ ] **步骤 1：写 MySQL 迁移失败测试。** Testcontainers 从空库执行到 V36，并从已执行到 V35 的增量库到 V36；断言两表、tenant 无默认、永久唯一、组合父外键、字段可空性和索引。
- [ ] **步骤 2：实现 V36。** 物理使用 `business_group_name NULL`、`responsible_team_org_id NOT NULL`、`responsible_team_name_snapshot NOT NULL`、owner/contact NULL；逻辑字段保持修订 2。
- [ ] **步骤 3：写仓储失败测试。** 覆盖租户分页/筛选、软删除、永久唯一、父锁、活动引用计数、物理筛选和团队 validity 投影所需原始字段。
- [ ] **步骤 4：实现最小仓储。** 只访问 arch 表；SQL 列和排序白名单固定；文本 LIKE 正确转义；影响行数为 0 映射 not found。
- [ ] **步骤 5：验证并提交。** 运行 `mvn -pl :ccb-architecture -am -Dtest=ArchitectureMigrationMySqlTest,ArchitectureSubsystemRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`、Flyway 和 diff；提交 `feat(architecture): add subsystem persistence model`。

**验收：** 空库/增量到 V36；数据库无法跨租户建立父引用或复用软删除编号/名称；字段与修订 4 一致。

**回滚：** 回退 Java；V36 和表保留，不在已迁移库删除。

**停止条件：** V35—V36 不连续；MySQL 约束无法表达；测试需修改 scope 外 POM/基础设施测试框架。

**升级条件：** 需要改变永久唯一、物理字段必填性或父子关联语义。

### T4：交付逻辑子系统 CRUD、权限、租户和审计

**需求映射：** R1、R3、R4、R6、R7、R12

**前置任务：** T3

**文件：**

- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/LogicalSubsystemService.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/LogicalSubsystemController.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/ArchitectureNotFoundException.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/ArchitectureExceptionAdvice.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/LogicalSubsystemServiceTest.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/web/LogicalSubsystemControllerTest.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/repository/ArchitectureSubsystemRepository.java`

**接口：** 控制器五个 CRUD 路径分别用固定 `@PreAuthorize`；请求只绑定 `LogicalSubsystemCommand`；列表返回 typed `PageResult<LogicalSubsystem>`。

- [ ] **步骤 1：写 service/MockMvc 失败测试。** 覆盖四权限、401/403、tenant 注入和客户端 tenant 忽略、code 归一化/格式、组织/联系人/参数有效性、唯一冲突、软删除、删除引用冲突、成功/失败 audit。
- [ ] **步骤 2：实现 service。** 先规范化再调 OrganizationService/SystemReferenceQuery；事务内写记录和 success audit；失败回滚后调用 failure audit；不记录请求正文。
- [ ] **步骤 3：实现 controller 和局部 404。** `ArchitectureExceptionAdvice` 仅处理模块 not found，返回 HTTP 404/code 40400；其他异常交全局处理。
- [ ] **步骤 4：运行局部和平台回归。** `mvn -pl :ccb-architecture,:ccb-system -am test`；确认 400/401/403/404/409 和 JSON 字段准确。
- [ ] **步骤 5：提交。** `feat(architecture): add logical subsystem management`。

**验收：** 逻辑资源完整闭环；权限、租户、受控引用、永久唯一和审计均由服务端执行。

**回滚：** 回退 T4 提交；V36 数据保留。

**停止条件：** 需要前端或通用 SystemController 参与校验；失败审计覆盖原错误；组织树无法满足有效性。

**升级条件：** 逻辑字段设计或错误码必须改变。

### T5：交付物理字段、团队快照、父锁和 CRUD

**需求映射：** R2、R3、R4、R5、R6、R7、R12、R13

**前置任务：** T4

**文件：**

- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/PhysicalSubsystemService.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/PhysicalSubsystemController.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/PhysicalSubsystemServiceTest.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/PhysicalSubsystemConcurrencyMySqlTest.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/web/PhysicalSubsystemControllerTest.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/repository/ArchitectureSubsystemRepository.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/LogicalSubsystemService.java`

**接口：** `PhysicalSubsystemCommand` 固定包含 code、shortName、name、logicalSubsystemId、businessGroupName、responsibleTeamOrgId、runtimeCode、systemLevelCode、developmentFrameworkCode、ownerUserId?、contactUserId?、description、remark；不含 snapshot/phone/tenant/status。

- [ ] **步骤 1：写字段失败测试。** 事业群 blank→null/最长 100；负责团队缺失/跨租户/停用/删除失败；服务端忽略客户端同名快照；owner/contact 均可空且非空时有效；联系人电话实时投影。
- [ ] **步骤 2：实现团队快照与历史读取。** 保存时从当前有效组织取得 ID/名称；读取时当前组织有效用当前名，否则用 snapshot 并返回 `responsibleTeamValid=false`；失效原值编辑被拒绝，删除仍可执行。
- [ ] **步骤 3：写父锁并发失败测试。** 两个 `TransactionTemplate` 和 latch 固定“物理先锁”和“删除先锁且物理已初检”两种时序，断言 409 和最终无悬挂活动引用。
- [ ] **步骤 4：实现物理事务和逻辑删除保护。** 固定父先、物理后锁顺序；普通无效初检 400，并发状态变化 409；成功/失败审计同 T4。
- [ ] **步骤 5：运行局部与回归。** `mvn -pl :ccb-architecture -am -Dtest=PhysicalSubsystemServiceTest,PhysicalSubsystemConcurrencyMySqlTest,PhysicalSubsystemControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`，再运行 architecture 全测。
- [ ] **步骤 6：提交。** `feat(architecture): add physical subsystem management`。

**验收：** 修订 3 的物理字段、快照、失效编辑、人员可空和并发父子不变量全部可重复验证。

**回滚：** 回退 T5 提交；V36 数据和快照保留。

**停止条件：** 组织当前名/快照语义无法按设计实现；父锁测试不稳定；需弱化同租户/活动校验。

**升级条件：** 用户要求失效组织自动替换、允许原值保存或增加组织历史服务。

### T6：交付最小选项 API 和架构模块契约

**需求映射：** R4、R6、R7

**前置任务：** T5

**文件：**

- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/OrganizationOption.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/UserOption.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/ParameterOption.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/LogicalSubsystemOption.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/ArchitectureOptionsService.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/ArchitectureOptionsController.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/web/ArchitectureOptionsControllerTest.java`
- 新建：`docs/integration/architecture-module-contract.md`

**接口：** exact-key DTO 和资源上下文/参数分类白名单采用设计第 6.2 节；没有 form-schema 路径。

- [ ] **步骤 1：先写 MockMvc exact-key 测试。** 断言 organizations/users 分页层级、parentId/phone 显式 null、parameter 数组、logical option 三字段；响应不含 tenant/password/avatar/status/internal ID。
- [ ] **步骤 2：写权限和错误测试。** logical/physical 上下文分别只认对应 list 权限；未知 resource 40400；已知 resource 缺选项段、跨资源参数、未知选项 400；401/403 不被 Advice 改写。
- [ ] **步骤 3：实现 options。** 组织树压平为 pathLabel 并过滤 status；用户和参数只调 SystemReferenceQuery；物理逻辑选项只查活动逻辑记录。
- [ ] **步骤 4：写集成契约文档。** 固定路径、权限、请求/响应 DTO、字段可空性、错误、tenant 和审计语义；明确表单元数据已退役。
- [ ] **步骤 5：验证并提交。** 运行 architecture/system 测试、source scan、diff；提交 `feat(architecture): add safe subsystem options`。

**验收：** 业务查看权限可读取必要选项而无需 system 管理权限；响应字段最小且稳定；无 schema API。

**回滚：** 回退 T6 API/文档；CRUD 和数据保留。

**停止条件：** 选项必须泄露平台管理字段或要求修改 OrganizationService；资源上下文权限无法固定。

**升级条件：** 组织规模要求新分页公共契约，或用户选择器需要当前 scope 外索引。

### T7：初始化 V37 菜单、权限和六类参数

**需求映射：** R6、R7、R8、R10

**前置任务：** T3

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V37__seed_architecture_subsystem_catalog.sql`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureCatalogMigrationMySqlTest.java`

**接口：** 菜单、权限、管理员授权、参数 ID/代码/标签采用设计第 10 节；不插入 biz_form 表，不创建 status/enable 权限。

- [ ] **步骤 1：写迁移失败测试。** 从 V36 到 V37，断言菜单 3、角色菜单 3、权限 8、角色权限 8、参数分类 6、基础选项 6；重复校验目标状态无重复。
- [ ] **步骤 2：实现 V37。** 使用稳定 ID 和 `NOT EXISTS`；显式 tenant 1；保持 route/component/permission 合同；只授权超级管理员。
- [ ] **步骤 3：负向扫描。** 断言 SQL 不含 `biz_form_`、status action、enable/disable，且没有旧截图人员/业务记录。
- [ ] **步骤 4：验证并提交。** 运行迁移测试、Flyway、governance 和 diff；提交 `feat(architecture): seed subsystem catalog`。

**验收：** 空库无需手工菜单/权限/参数；目录与动作授权完整；没有表单元数据或启停用种子。

**回滚：** V37 保留；应用入口关闭用未来补偿迁移，不删除已发布脚本。

**停止条件：** ID 被占用、超级管理员稳定键变化或 V35—V37 不连续。

**升级条件：** 需要多租户目录复制或参数值超出批准清单。

### T8：交付受限 local Mock 数据

**需求映射：** R8、R12、R13

**前置任务：** T3、T7

**文件：**

- 修改：`server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java`
- 修改：`server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/mock/MockDataInitializerTest.java`
- 修改：`mock/mock-data.json`

**接口：** 继续使用统一 dataset；新增两张 arch 表白名单。每条行显式 tenant；物理记录包含可空 business_group_name、必填团队 ID/snapshot 和合法逻辑父 ID。

- [ ] **步骤 1：写失败测试。** 覆盖表白名单、显式 tenant 成功、缺失/零/不存在 tenant、无根组织、跨租户组织/用户/父引用、负责团队无效、重复运行幂等和事务回滚。
- [ ] **步骤 2：实现最小校验。** 仅 local+enabled 的现有入口生效；不为 arch 行补租户或快照；验证数据集提供的 snapshot 与组织名一致，防止虚构历史漂移。
- [ ] **步骤 3：添加虚构数据。** 至少两个逻辑和若干物理；使用现有虚构账号/组织，不复制旧截图系统、人员或电话；递增 datasetVersion 和 catalog。
- [ ] **步骤 4：验证并提交。** `mvn -pl :ccb-infrastructure -am test`、JSON parse、重复初始化和 diff；提交 `test(architecture): add local subsystem dataset`。

**验收：** local 演示关联完整且幂等；任何租户/引用错误整体失败；非 local 无执行通道。

**回滚：** 回退 Mock 代码和 JSON；已写本地数据可重建开发库，生产不受影响。

**停止条件：** 校验需要连接生产或使用真实个人信息；初始化器无法保证单事务。

**升级条件：** 需要修改 mock contract 或新增正式生产 seed 通道。

### T9：交付两个固定强类型 Vue 页面

**需求映射：** R1、R2、R7、R9、R10、R13

**前置任务：** T6

**文件：**

- 新建：`web/src/modules/architecture/types.ts`
- 新建：`web/src/modules/architecture/api.ts`
- 新建：`web/src/modules/architecture/errors.ts`
- 新建：`web/src/modules/architecture/components/OrganizationOptionSelect.vue`
- 新建：`web/src/modules/architecture/components/UserOptionSelect.vue`
- 新建：`web/src/modules/architecture/components/LogicalSubsystemOptionSelect.vue`
- 新建：`web/src/modules/architecture/components/LogicalSubsystemFormDrawer.vue`
- 新建：`web/src/modules/architecture/components/PhysicalSubsystemFormDrawer.vue`
- 新建：`web/src/modules/architecture/components/LogicalSubsystemDetailDrawer.vue`
- 新建：`web/src/modules/architecture/components/PhysicalSubsystemDetailDrawer.vue`
- 新建：`web/src/modules/architecture/logical-subsystems/index.vue`
- 新建：`web/src/modules/architecture/physical-subsystems/index.vue`
- 新建：`web/src/modules/architecture/architecture.css`
- 修改：`web/src/router/index.ts`

**接口：** `types.ts` 一一对应冻结的 CRUD/options DTO。`api.ts` 不提供 schema 方法，不提交 tenant/status/phone/snapshot。路由名分别为 `architecture-logical-subsystems`、`architecture-physical-subsystems`。

- [ ] **步骤 1：建立 TypeScript 失败信号。** 创建 types/api 和空页面 import，运行 `npm --prefix web run build`；预期缺失组件或类型失败。Windows PowerShell 若 npm.ps1 被策略阻止，只在本机经 `cmd.exe` 调用相同 npm 命令，不修改 CI 标准命令。
- [ ] **步骤 2：实现三个选择器。** 组织路径、用户 300ms 防抖/分页/电话、逻辑分页；使用 AbortController 或请求序号丢弃过期响应；编辑值可反显。
- [ ] **步骤 3：实现固定表单抽屉。** 使用明确 FormRules 和三分区；物理事业群 blank→undefined/null，团队必填，owner/contact 可空；失效团队显示错误并要求重选；脏关闭、提交锁和 409 保留输入。
- [ ] **步骤 4：实现列表/详情全状态。** 复用 UiPageHeader/Toolbar/DataTable/FormDrawer/EmptyState；桌面分页表格，<760px 业务卡片；查看主操作，编辑/删除按权限进入更多。
- [ ] **步骤 5：注册静态路由并扫描。** 构建后 `rg` 确认 architecture 源码无 `form-schema|biz_form|PublishedForm|tenant_id|tenantId` 请求字段和 enable/disable/status 动作。
- [ ] **步骤 6：提交。** `feat(architecture): add subsystem management pages`。

**验收：** 两页面可编译；固定字段、权限、全状态、负责团队失效、脏表单和移动布局符合设计；没有运行时 schema。

**回滚：** 回退 T9 提交，移除路由；后端和数据保留。

**停止条件：** 必须修改公共 UI/auth/http/package 才能实现；后端偏离冻结 DTO；出现页面级横向滚动且业务 CSS 无法解决。

**升级条件：** 需要动态字段、公共组件变更或新的移动端例外。

### T10：全量集成、真实 API、浏览器 UAT 和收敛

**需求映射：** R1—R13

**前置任务：** T2、T4、T5、T6、T7、T8、T9

**文件：**

- 修改：仅当前前缀 `execution-T*.json`、`observation-T*.json`、`convergence.json` 证据文件；发现实现偏差时回到对应任务 scope 修复。

**接口：** 消费全部实现、MySQL、Boot、管理员/只读/无权限/tenant2 上下文和真实浏览器；产出需求证据矩阵与收敛判断。

- [ ] **步骤 1：自动门禁。** 运行 architecture/system/infrastructure 模块测试、`mvn test`、`mvn --batch-mode -DskipTests package`、前端构建、development-entry、scope、governance、module-boundaries、Flyway 和 diff；每条记录退出码与测试数。
- [ ] **步骤 2：真实迁移。** 空库 V1→V37，以及已执行到 V34 的已有库→V37；复核表、约束、目录、参数、Mock 两次幂等和 audit trace。
- [ ] **步骤 3：运行真实 Boot。** 运行 package 后的 `ccb-boot` JAR，先 health，再登录和 API；不使用根聚合 `spring-boot:run` 作为可用性证明。
- [ ] **步骤 4：API/权限/租户矩阵。** 执行两资源 CRUD/options、400/401/403/40400/409、客户端 tenant/status/phone/snapshot、团队失效和父锁两时序；按 trace 查审计。
- [ ] **步骤 5：四视口浏览器 UAT。** 管理员/只读/无权限执行筛选、分页、详情、新增、编辑、删除、团队失效重选、脏关闭、409、明暗主题；保存截图、Network、Console 和 scrollWidth。
- [ ] **步骤 6：独立观测和纠偏。** high-assurance 至少三次采样，使用代码/测试、真实 API/SQL、浏览器三类异质传感器；P0/P1 归零，P2 逐项裁决。
- [ ] **步骤 7：收敛。** 写 convergence，运行 control gate；只有阶段、任务、反馈和采样门禁全部通过才转 converged。

**验收：** R1—R13 全部有可复现证据；现有 system/Boot/前端无回归；所有已执行和未执行验证如实区分。

**回滚：** 按 T9→T8→T7→T6→T5→T4→T3→T2→T1 逆序回退应用提交；保留 V35—V37、业务数据、快照和审计。

**停止条件：** 任一安全/租户/父子/迁移/Required Check P0/P1；环境不可用且无等价证据；需要生产访问。

**升级条件：** 回退要求删除生产数据、公共契约必须破坏兼容或外部治理基线无法在本任务范围关闭。

## 集成检查

| 采样点 | 命令/传感器 | 通过信号 |
| --- | --- | --- |
| T0 | development-entry、scope、git ls-tree | 所有硬门禁为绿，主干最高 V34 且 V35—V37 可用 |
| T1 | Maven parse、governance、module-boundaries | 新模块可识别、无反向依赖 |
| T2 | system 全测 + V35 | 公共契约/审计通过，既有 system 无回归 |
| T3 | Testcontainers Flyway/repository | V36 约束与租户 SQL 准确 |
| T4/T5 | architecture 全测 + 并发 MySQL | CRUD、团队、父锁和 audit 通过 |
| T6/T7 | MockMvc exact-key + V37 migration | 选项安全、目录参数计数准确、无 schema |
| T8 | infrastructure 全测 + Mock 两次 | local/tenant/引用/幂等通过 |
| T9 | npm build + browser smoke | 类型、路由、固定表单和响应式通过 |
| T10 | 全量自动 + 真实 API/SQL/browser | R1—R13、回归和治理全部收敛 |

## 控制模型种子

- 状态：`hypotheses-only`，不能替代 modeling 证据。
- 被控边界候选：architecture Maven/Vue、system 两个 capability、V35—V37、local Mock、静态路由。
- 状态变量候选：逻辑/物理活动与软删除、父锁、负责团队有效性、权限、tenant、audit trace、Flyway、页面异步状态。
- 接口候选：CRUD、options、OrganizationService、SystemReferenceQuery、SystemOperationAudit、组合父外键、固定表单 DTO。
- 传感器候选：JUnit/Mockito/MockMvc、Testcontainers MySQL、Flyway/information_schema、Maven/Vite、治理/scope、真实 HTTP/SQL、浏览器 Network/Console/截图/宽度。
- 执行器候选：scope 内 Java/SQL/Vue/Mock/装配/治理修改和小提交。
- 扰动候选：V35—V37 并行占用、CLI/桌面插件状态差异、Owner/scope/分支、Docker/Maven/npm、并行 main 变更。
- 时延候选：全 Maven/Testcontainers、组织后续失效、异步选择器和四视口 UAT。

## 风险与用户批准

高风险动作包括平台公开 Java 契约、现有审计表兼容迁移、两个新业务表、父子并发锁、Boot/Maven 装配和四视口真实 UAT。用户已批准计划修订 3、最小公共能力变更、开发入口兼容和未落地迁移版本重分配；`codex_allowed` 与 `owner_approved` 已解锁。仍须以修复后的入口命令和最新主干/ID 扫描关闭 T0。

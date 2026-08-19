# 正式项目上下文与项目成员权限实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 建立正式 `ccb-project` 业务模块、项目成员数据范围契约和可维护的项目上下文界面，为项目资产库提供可信 `projectId` 与成员授权边界。

**架构：** `ccb-project` 拥有项目、成员和项目审计数据，通过公开 `ProjectContextPort` 提供项目状态与角色范围。它通过 `platform/system` 新增的只读 `UserDirectory` 公开契约校验用户，不读取 `sys_user`；前端通过 Remote Provider 和 Pinia Store 消费正式项目 API，不再把 Mock 项目当作授权来源。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring Security Method Security、JdbcTemplate、MySQL 8.4、Flyway、Vue 3、TypeScript、Pinia、Vue Router、Element Plus。

## 状态与来源

- 计划修订：2
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-18-project-asset-library-design.md`
- 机器设计：`.ai-control/requirements/req-20260818-026-project-context/design.json`
- 需求：`docs/requirements/REQ-20260818-026-project-context/requirement.md`
- 状态：用户已批准计划修订 2；Owner 已批准模块、公共能力、任务范围和 V37，独立工作树已建立，可导入控制闭环
- 交接包：`.ai-control/requirements/req-20260818-026-project-context/handoff.json`

## 全局约束

- `REQ-20260818-026`、`codex-task-scope.yaml`、公共能力变更和独立工作区是产品代码修改的硬门禁。
- Owner 已确认 `REQ-20260814-022` 保留 V35/V36，本需求使用 `V37__project_context.sql`；执行前仍必须扫描当前分支，发现 V37 已占用即停止并重新编号。
- 新模块候选路径为 `server/src/modules/project`，Java 根包为 `com.ccb.project`，Maven artifact 为 `ccb-project`；最终名称必须与 `governance/modules.yaml` 的 Owner 审批一致。
- 只允许 `ccb-project` 依赖 `shared/common`、`platform/infrastructure`、`platform/security` 和 `platform/system` 的公开包；不得读取 `sys_user`、其他模块私有表或前端 Mock 项目。
- `platform/system` 只新增只读用户目录公共契约，不改变现有用户 CRUD、认证、角色或数据所有权。
- 有效权限始终为平台 RBAC、项目角色允许范围和项目状态允许操作的交集。`ProjectContextPort` 只判断项目角色/状态，调用方仍必须执行自己的平台 RBAC。
- 项目状态固定为 `ACTIVE`、`ARCHIVED`；角色固定为 `OWNER`、`ADMIN`、`MEMBER`、`VIEWER`。
- 项目编号租户内唯一；负责人唯一；项目不物理删除；项目和成员写操作写入项目域不可变审计。
- 不新增计划、里程碑、预算、风险、工时、资产或附件能力；不恢复已下线的业务表单元数据。
- Flyway 只追加；不修改 V1-V34。测试与 Mock 数据必须虚构，不读取生产数据或真实身份信息。
- 前端复用 `UiToolbar`、`UiDataTable`、`UiEmptyState`、`UiFormDrawer`、`UiUserIdentity` 和语义主题变量，不修改这些公共组件行为。
- 前端覆盖加载、空、筛选无结果、失败、无权限、归档只读、提交中、并发冲突和移动端操作可达状态。

## 实施前授权门禁

以下事项全部满足后才能把交接包改为 `approved` 并导入控制闭环：

1. Owner 已将 `REQ-20260818-026` 改为 `ready` 并批准 JSON 兼容的 `codex-task-scope.yaml`。
2. 任务范围授权本计划列出的候选文件、当前需求账本前缀、V37 迁移和必要公共契约路径。
3. `public_capability_change.owner_approved=true`，回归至少包含 system、security、project、boot、frontend 和 governance。
4. `governance/modules.yaml` 的目标条目、公开包、Owner 和允许依赖已由治理 Owner 批准。
5. Owner 已确认 V35/V36 保留给 `REQ-20260814-022`，V37 分配给本需求；若当前分支出现新迁移占用 V37，必须停止并重新规划。
6. 已建立 `feat/REQ-20260818-026-project-context` 独立分支和工作区；scope 在 T1 注册新模块前暂归属现有 `project-control`，并以 `target_module=business/project` 固定目标模块；T1 完成模块登记后必须把 `assignment.module` 切换为 `business/project` 并重新通过 scope 检查；当前 `sjqy` 工作区不直接承担产品实现。

## 文件职责地图

| 路径 | 状态 | 单一职责 | 事实来源 |
| --- | --- | --- | --- |
| `.gitignore` | existing | 忽略项目内隔离工作树目录 | `using-git-worktrees` 准入要求 |
| `pom.xml` | existing | Maven dependency management 和 reactor 模块列表 | 当前根 POM |
| `server/src/platform/boot/pom.xml` | existing | 组合根装配全部模块 | 当前 boot POM |
| `governance/modules.yaml` | existing | 模块 Owner、路径、公开包和依赖事实源 | 项目治理规则 |
| `docs/architecture/MODULES.md` | existing | 人类可读模块边界 | 项目治理规则 |
| `.github/CODEOWNERS` | existing | 路径审批责任 | GitHub 规约 |
| `server/src/platform/system/src/main/java/com/ccb/system/model/UserDirectory.java` | candidate-new | 跨模块只读用户查询契约 | 项目成员必须验证正式用户 |
| `server/src/platform/system/src/main/java/com/ccb/system/model/UserDirectoryUser.java` | candidate-new | 对外用户摘要白名单 DTO | 项目成员选择只需最小身份字段 |
| `server/src/platform/system/src/main/java/com/ccb/system/service/JdbcUserDirectory.java` | candidate-new | 在 system 数据所有权内实现只读用户查询 | 当前 system 使用 JdbcTemplate |
| `server/src/platform/system/src/test/java/com/ccb/system/service/JdbcUserDirectoryTest.java` | candidate-new | 用户目录租户、状态和分页传感器 | 公共契约回归要求 |
| `server/src/modules/project/pom.xml` | candidate-new | `ccb-project` 依赖声明 | 已确认模块设计 |
| `server/src/modules/project/src/main/java/com/ccb/project/model/ProjectRole.java` | candidate-new | 固定项目角色枚举 | 已确认角色决策 |
| `server/src/modules/project/src/main/java/com/ccb/project/model/ProjectStatus.java` | candidate-new | 项目状态枚举 | 已确认归档决策 |
| `server/src/modules/project/src/main/java/com/ccb/project/model/ProjectAction.java` | candidate-new | 项目角色/状态判断动作枚举 | 三层权限设计 |
| `server/src/modules/project/src/main/java/com/ccb/project/model/ProjectSummary.java` | candidate-new | 跨模块项目摘要 DTO | 公开项目上下文契约 |
| `server/src/modules/project/src/main/java/com/ccb/project/model/ProjectMembership.java` | candidate-new | 当前用户项目角色 DTO | 公开项目上下文契约 |
| `server/src/modules/project/src/main/java/com/ccb/project/api/ProjectContextPort.java` | candidate-new | 消费模块项目范围入口 | 已确认模块边界 |
| `server/src/modules/project/src/main/java/com/ccb/project/repository/ProjectRepository.java` | candidate-new | 项目、成员和审计持久化 | 项目域数据 Owner |
| `server/src/modules/project/src/main/java/com/ccb/project/service/ProjectContextService.java` | candidate-new | 实现公开项目范围判断 | 项目角色/状态规则 |
| `server/src/modules/project/src/main/java/com/ccb/project/service/ProjectService.java` | candidate-new | 项目与成员命令、查询和事务编排 | Controller/service 分层规则 |
| `server/src/modules/project/src/main/java/com/ccb/project/web/ProjectController.java` | candidate-new | `/api/projects` HTTP 适配和 RBAC | 统一 API 规则 |
| `server/src/modules/project/src/main/java/com/ccb/project/web/ProjectCommands.java` | candidate-new | 创建、更新、成员和负责人转移输入 DTO | 字段白名单和校验 |
| `server/src/modules/project/src/test/java/com/ccb/project/service/ProjectContextServiceTest.java` | candidate-new | 角色、归档和租户边界测试 | P2/P3 验收 |
| `server/src/modules/project/src/test/java/com/ccb/project/service/ProjectServiceTest.java` | candidate-new | 项目/成员业务规则和并发测试 | P1/P3 验收 |
| `server/src/modules/project/src/test/java/com/ccb/project/web/ProjectControllerSecurityTest.java` | candidate-new | `@PreAuthorize` 和 HTTP 字段契约测试 | 服务端 RBAC 验收 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V37__project_context.sql` | candidate-new | 项目、成员、审计、菜单和权限增量迁移 | Owner 于 2026-08-19 分配；执行前扫描占用 |
| `server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java` | existing | Mock 表/字段白名单 | Mock 数据契约 |
| `server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/mock/MockDataInitializerTest.java` | existing | 项目 Mock 白名单和幂等同步测试 | Mock 数据契约 |
| `mock/mock-data.json` | existing | 本地虚构项目、成员和权限数据 | 统一 Mock 数据事实源 |
| `web/src/types/project-context.ts` | candidate-new | Project Context、项目表单和成员类型 | REQ-20260814-022 预留接口与正式替换目标 |
| `web/src/api/projects.ts` | candidate-new | 项目 API 封装 | 前端 API 边界 |
| `web/src/stores/project-context.ts` | candidate-new | Remote Provider、可用项目和当前选择 | 已确认正式项目上下文 |
| `web/src/modules/project/ProjectManagementView.vue` | candidate-new | 项目列表、状态和主操作编排 | 最小项目目录 UI |
| `web/src/modules/project/components/ProjectFormDrawer.vue` | candidate-new | 创建/编辑项目表单 | 单一表单职责 |
| `web/src/modules/project/components/ProjectMemberDialog.vue` | candidate-new | 成员搜索、角色维护和负责人转移 | 成员管理职责 |
| `web/src/modules/project/components/ProjectContextSwitcher.vue` | candidate-new | AppLayout 正式项目切换控件 | 多业务共享项目上下文入口 |
| `web/src/modules/project/project.css` | candidate-new | 项目页面与切换器专项响应式样式 | 不污染共享 UI |
| `web/src/router/index.ts` | existing | `/projects` 静态组件映射 | 当前路由模式 |
| `web/src/views/AppLayout.vue` | existing | 装配 ProjectContextSwitcher 和标题回退 | 当前应用壳 |
| `docs/integration/project-context-contract.md` | candidate-new | 项目公共 Java/REST/前端契约 | 跨模块稳定边界 |

## 任务依赖图与并行策略

```text
T1 公共契约与模块骨架
  -> T2 项目持久化、业务 API 与权限
      -> T3 正式项目前端与 Remote Provider
          -> T4 全量集成与真实浏览器验收
```

所有任务串行。T2 依赖 T1 的类型和 Maven 边界，T3 依赖 T2 的 REST 契约，T4 依赖完整组合结果；没有可证明安全的并行写入面。

## 需求覆盖表

| 设计需求 | 任务 | 主要传感器 |
| --- | --- | --- |
| P1 最小项目目录与角色 | T2、T3、T4 | 服务测试、API、浏览器 |
| P2 稳定项目上下文契约 | T1、T2、T3、T4 | 编译边界、契约测试、Provider 运行 |
| P3 归档只读与恢复 | T2、T3、T4 | 状态测试、权限 API、浏览器 |

### T1：公共用户目录、项目契约与模块骨架

**需求映射：** P2

**前置任务：** 实施前授权门禁全部通过

**文件：**

- 新建：`server/src/platform/system/src/main/java/com/ccb/system/model/UserDirectory.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/model/UserDirectoryUser.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/service/JdbcUserDirectory.java`
- 新建：`server/src/platform/system/src/test/java/com/ccb/system/service/JdbcUserDirectoryTest.java`
- 新建：`server/src/modules/project/pom.xml`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/model/ProjectRole.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/model/ProjectStatus.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/model/ProjectAction.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/model/ProjectSummary.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/model/ProjectMembership.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/api/ProjectContextPort.java`
- 修改：`pom.xml`
- 修改：`server/src/platform/boot/pom.xml`
- 修改：`governance/modules.yaml`
- 修改：`docs/architecture/MODULES.md`
- 修改：`.github/CODEOWNERS`
- 新建：`docs/integration/project-context-contract.md`

**接口：**

- 消费：`com.ccb.common.api.PageQuery`、`PageResult`，`com.ccb.security.model.AuthUser`。
- 产出：

```java
public interface UserDirectory {
    PageResult<UserDirectoryUser> searchActive(long tenantId, String keyword, PageQuery pageQuery);
    Map<Long, UserDirectoryUser> requireActive(long tenantId, Set<Long> userIds);
}

public record UserDirectoryUser(long id, String username, String displayName,
                                long orgId, String orgName) {}

public interface ProjectContextPort {
    ProjectSummary requireAccess(long projectId, AuthUser user, ProjectAction action);
    ProjectMembership membership(long projectId, AuthUser user);
    List<ProjectSummary> available(AuthUser user);
}
```

- `ProjectAction` 固定为 `VIEW`、`WRITE`、`MANAGE_MEMBERS`、`MANAGE_PROJECT`；调用方必须在调用前完成自己的平台 RBAC。
- `governance/modules.yaml` 新增 `business/project`，公开包仅为 `com.ccb.project.api` 和 `com.ccb.project.model`，允许依赖 `shared/common`、`platform/infrastructure`、`platform/security`、`platform/system`。

- [ ] **步骤 1：建立用户目录失败测试**

在 `JdbcUserDirectoryTest` 使用 Mock `JdbcTemplate` 断言查询必须包含 `tenant_id`、`deleted = 0`、`status = 1`，并且 `requireActive` 在缺少任一用户时抛出业务异常。

运行：`mvn -pl :ccb-system -am -Dtest=JdbcUserDirectoryTest test`

预期：测试编译失败，错误包含 `UserDirectory` 或 `JdbcUserDirectory` 不存在。

证据：保存退出码和缺失类型错误。

- [ ] **步骤 2：实现只读用户目录契约**

在 system 公共 `model` 包定义接口/白名单 DTO，在 `service` 包实现租户内启用用户搜索和批量校验；不暴露密码、手机号、头像对象键或角色私有表。

运行：`mvn -pl :ccb-system -am -Dtest=JdbcUserDirectoryTest test`

预期：目标测试通过，0 个失败。

证据：Surefire 测试数、退出码和 SQL 参数断言。

- [ ] **步骤 3：建立项目模块骨架和公开类型**

创建 `ccb-project` POM、固定枚举、公开 DTO 和 `ProjectContextPort`。在根 POM 管理/注册 artifact，在 boot 中装配依赖，在模块清单、架构和 CODEOWNERS 登记路径与公开包。

运行：`mvn -pl :ccb-project -am -DskipTests compile`

预期：Maven reactor 包含 common、infrastructure、security、system、project，`BUILD SUCCESS`。

证据：reactor 顺序、编译退出码和 artifact 名称。

- [ ] **步骤 4：写入公开契约文档并检查边界**

`project-context-contract.md` 明确角色矩阵、归档语义、`ProjectContextPort` 只负责项目范围而不替代 RBAC，以及消费方禁止读取 `pm_*` 表。

运行：`node scripts/check-all-governance.mjs`

预期：模块边界检查包含 9 个 Maven 模块并通过；无 platform 反向依赖 business。

证据：治理输出和模块计数。

- [ ] **步骤 5：建立提交检查点**

```bash
git add pom.xml server/src/platform/boot/pom.xml server/src/platform/system server/src/modules/project governance/modules.yaml docs/architecture/MODULES.md docs/integration/project-context-contract.md .github/CODEOWNERS
git commit -m "feat(project): add project context contracts"
```

**验收检查：** 用户目录只返回同租户启用用户；project 模块只导入登记公开包；公共接口签名与契约文档一致；现有 system 测试无回归。

**证据：** `execution-T1.json` 记录实际文件、测试和提交；独立观察写 `observation-T1.json`。

**回滚：** 回退 T1 提交，删除未被消费者使用的新模块骨架和用户目录契约；不涉及数据库。

**停止条件：** Owner 不批准 `business/project` 模块名/公开包；platform/system Owner 不批准用户目录公共契约；模块边界检查要求 project 读取 system 私有包。

**升级条件：** 用户查询需要新增组织数据范围或敏感字段；Owner 要求把项目域放入 platform/system；其他进行中任务已创建同名 Project Context 类型。

### T2：项目持久化、业务 API、角色范围与审计

**需求映射：** P1、P2、P3

**前置任务：** T1

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V37__project_context.sql`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/repository/ProjectRepository.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/service/ProjectContextService.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/service/ProjectService.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/web/ProjectController.java`
- 新建：`server/src/modules/project/src/main/java/com/ccb/project/web/ProjectCommands.java`
- 新建：`server/src/modules/project/src/test/java/com/ccb/project/service/ProjectContextServiceTest.java`
- 新建：`server/src/modules/project/src/test/java/com/ccb/project/service/ProjectServiceTest.java`
- 新建：`server/src/modules/project/src/test/java/com/ccb/project/web/ProjectControllerSecurityTest.java`
- 修改：`server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java`
- 修改：`server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/mock/MockDataInitializerTest.java`
- 修改：`mock/mock-data.json`

**接口：**

- 消费：T1 的 `UserDirectory`、`ProjectContextPort` 类型、认证 `AuthUser`、`PageQuery/PageResult`。
- 产出 REST：

```text
GET    /api/projects?page=&size=&keyword=&status=
GET    /api/projects/available
GET    /api/projects/{projectId}
POST   /api/projects
PUT    /api/projects/{projectId}
POST   /api/projects/{projectId}/archive
POST   /api/projects/{projectId}/restore
GET    /api/projects/{projectId}/members
GET    /api/projects/member-candidates?page=&size=&keyword=
POST   /api/projects/{projectId}/members
PATCH  /api/projects/{projectId}/members/{userId}
DELETE /api/projects/{projectId}/members/{userId}
POST   /api/projects/{projectId}/owner-transfer
```

- 权限码固定为 `project:list`、`project:list:create`、`project:list:update`、`project:list:member`、`project:list:archive`。
- `pm_project` 保存 `id`、`tenant_id`、`project_code`、`project_name`、`status`、`owner_user_id`、`version` 和创建/更新审计字段。
- `pm_project_member` 保存项目、用户、角色和创建/更新审计字段；租户/项目/用户唯一。
- `pm_project_audit_event` 保存项目、操作人、动作、结果、trace ID、最小变更摘要和时间；无普通删除接口。
- 建模确认 V11 已占用菜单 ID 400；V37 当前候选使用菜单 ID 600，权限 ID 使用 6001-6005。T2 执行前必须重新扫描当前迁移和待集成占用，任一冲突均停止并重新规划。

- [ ] **步骤 1：建立领域失败测试**

在 `ProjectServiceTest` 和 `ProjectContextServiceTest` 至少建立以下断言：

```java
assertThrows(BusinessException.class,
        () -> service.create(duplicateCodeCommand, owner));
assertThrows(BusinessException.class,
        () -> context.requireAccess(12L, viewer, ProjectAction.WRITE));
assertThrows(BusinessException.class,
        () -> context.requireAccess(12L, member, ProjectAction.WRITE)); // project is ARCHIVED
assertEquals(ProjectRole.OWNER,
        context.membership(12L, owner).role());
```

同时测试唯一负责人、负责人不能被普通成员删除、ADMIN 可维护成员但不能转移负责人、乐观锁冲突和跨租户不可见。

运行：`mvn -pl :ccb-project -am test`

预期：因 service/repository/controller 不存在而失败。

证据：保存失败测试名和缺失类型/行为。

- [ ] **步骤 2：编写 V37 增量迁移**

创建三个 `pm_*` 表及租户/项目/成员/状态索引，增加项目管理菜单和五个权限，并授权管理员角色。不得为 `sys_user` 建跨模块外键；用户有效性通过 `UserDirectory` 校验。

运行：`node scripts/check-flyway-migrations.mjs`

预期：只检测到一个新增迁移；版本未与当前分支或已批准预留冲突。

证据：迁移文件状态、版本扫描和 Owner 分配记录。

- [ ] **步骤 3：实现 repository 和项目域审计**

`ProjectRepository` 只访问 `pm_project`、`pm_project_member`、`pm_project_audit_event`；所有 SQL 带 `tenant_id`。项目命令在同一事务内更新主表、成员不变量和审计事件。

运行：`mvn -pl :ccb-project -am -DskipTests compile`

预期：编译通过，不导入 system 私有实现包。

证据：编译输出和模块边界检查。

- [ ] **步骤 4：实现 ProjectContextService 角色矩阵**

角色上限固定为：OWNER 可执行全部项目动作；ADMIN 可 `VIEW/WRITE/MANAGE_MEMBERS`；MEMBER 可 `VIEW/WRITE`；VIEWER 仅 `VIEW`。`ARCHIVED` 只允许 `VIEW`。不可见项目统一拒绝，不区分不存在、跨租户和非成员。

运行：`mvn -pl :ccb-project -am -Dtest=ProjectContextServiceTest test`

预期：角色、归档、恢复和租户测试全部通过。

证据：测试数和关键拒绝断言。

- [ ] **步骤 5：实现 ProjectService 与 REST 字段白名单**

Controller 只做 Bean Validation、认证主体和 HTTP 适配；Service 负责项目编号规范化、用户批量校验、负责人/成员事务、乐观锁和审计。平台 RBAC 使用明确的 `@PreAuthorize`，不得只依赖页面按钮显隐。

运行：`mvn -pl :ccb-project -am test`

预期：service、context 和 controller security 测试通过，0 个失败。

证据：Surefire 报告、权限注解断言和 API DTO 序列化结果。

- [ ] **步骤 6：接入本地虚构 Mock 数据**

在 allowlist 登记 `pm_project` 和 `pm_project_member` 的准确列；`mock-data.json` 增加一个虚构项目，以 `mock.product` 为 OWNER、`mock.release` 为 ADMIN、`mock.engineer` 为 MEMBER、`mock.qa` 为 VIEWER，并为虚构角色登记菜单/权限关系。不得写真实个人信息或生产项目。

运行：`mvn -pl :ccb-infrastructure -am -Dtest=MockDataInitializerTest test`

预期：项目表白名单、未知列拒绝和幂等 upsert 测试通过。

证据：Mock 测试数和 datasetVersion 变化。

- [ ] **步骤 7：运行局部回归并建立提交检查点**

```bash
mvn -pl :ccb-project,:ccb-system,:ccb-infrastructure -am test
node scripts/check-all-governance.mjs
git add server/src/modules/project server/src/platform/infrastructure/src/main/resources/db/migration/V37__project_context.sql server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/mock/MockDataInitializerTest.java mock/mock-data.json
git commit -m "feat(project): add project context APIs"
```

预期：目标 reactor、治理和 Flyway 检查通过。

**验收检查：** 项目编号唯一；负责人唯一；角色矩阵和归档只读准确；用户通过公开目录验证；所有查询带租户；写操作有审计；API 返回统一响应。

**证据：** `execution-T2.json`、API 请求/响应摘要、迁移结果和 `observation-T2.json`。

**回滚：** 回退 T2 应用代码和菜单入口；保留已应用的 V37 表及审计数据，不执行生产 DROP。未发布环境可重建本地数据库验证迁移。

**停止条件：** V37 已被当前分支或任一待集成分支占用；需要读取 `sys_user` 私有表；无法在一个事务内保持 owner/member 不变量；现有权限表不能表达五个权限。

**升级条件：** 需要组织范围自动继承、项目物理删除、多个负责人、外部项目同步或公共审计平台变更。

### T3：正式项目管理界面与 Remote Project Context Provider

**需求映射：** P1、P2、P3

**前置任务：** T2

**文件：**

- 新建：`web/src/types/project-context.ts`
- 新建：`web/src/api/projects.ts`
- 新建：`web/src/stores/project-context.ts`
- 新建：`web/src/modules/project/ProjectManagementView.vue`
- 新建：`web/src/modules/project/components/ProjectFormDrawer.vue`
- 新建：`web/src/modules/project/components/ProjectMemberDialog.vue`
- 新建：`web/src/modules/project/components/ProjectContextSwitcher.vue`
- 新建：`web/src/modules/project/project.css`
- 修改：`web/src/router/index.ts`
- 修改：`web/src/views/AppLayout.vue`
- 修改：`governance/modules.yaml`
- 修改：`docs/integration/project-context-contract.md`

**接口：**

- 消费：T2 的项目 REST API、现有 `api/http.ts`、auth Store、共享 UI。
- 产出：

```ts
export interface ProjectContextItem {
  id: number
  code: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  role: 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'
}

export interface ProjectContextProvider {
  list(): Promise<ProjectContextItem[]>
}

export interface ProjectContextStore {
  availableProjects: ProjectContextItem[]
  currentProject: ProjectContextItem | null
  refresh(): Promise<void>
  select(projectId: number): void
  reset(): void
}
```

- Store 每次 refresh 都以服务端可用项目集合校验本地选择；本地持久值按当前租户/用户命名空间保存，不能扩大后端权限。

- [ ] **步骤 1：建立 TypeScript 基准失败信号**

先定义 `project-context.ts` 类型和 `projects.ts` 函数签名，并在 Store/页面引用尚未创建的 Remote Provider。

运行：`npm --prefix web run build`

预期：因缺少 Store 或组件实现而失败，错误指向准确模块路径。

证据：vue-tsc 缺失模块/成员错误。

- [ ] **步骤 2：实现 API 和 Remote Provider Store**

封装项目列表、available、详情、创建、更新、归档/恢复、成员候选和成员命令。Store 区分首次加载、刷新失败、无可用项目和当前项目失效；不能内置项目数组。

运行：`npm --prefix web run build`

预期：API/Store 类型通过，若页面尚未完成仅保留明确的组件缺失错误。

证据：TypeScript 输出和接口字段核对。

- [ ] **步骤 3：实现项目管理工作区**

桌面使用 `UiToolbar + UiDataTable + UiFormDrawer`，显示项目编号、名称、状态、负责人、当前用户角色和乐观锁版本；移动端改为项目卡片。主操作为新建项目，成员维护、归档/恢复按权限和状态显示，服务端错误持续可见。

成员对话框使用 `member-candidates` 受控选择用户和固定角色，不允许自由输入人员名称。负责人转移明确展示后果并二次确认。

运行：`npm --prefix web run build`

预期：vue-tsc 和 Vite 生产构建通过，0 个类型错误。

证据：构建退出码和产物摘要。

- [ ] **步骤 4：装配项目路由与顶级切换器**

在 router 增加 `/projects` 静态组件映射；AppLayout 装配 `ProjectContextSwitcher`，长名称截断并用 Tooltip 展示，加载/无项目/失败状态不改变导航高度。切换项目只更新 Store，业务页面后续把 `currentProject.id` 作为查询条件，服务端仍重新授权。

前端文件实际创建后，将 `web/src/api/projects.ts`、`web/src/types/project-context.ts` 和 `web/src/modules/project/**` 登记给 `business/project`；Store、router 和 AppLayout 继续由 `frontend/application` 拥有。同步集成契约的实际 REST 端点、请求字段、错误码和 TypeScript 字段映射。

运行：`npm --prefix web run build`

预期：路由和 AppLayout 构建通过；不修改 auth Token 或动态路由安全模型。

证据：构建结果和路由映射截图/DOM 记录。

- [ ] **步骤 5：建立提交检查点**

```bash
git add web/src/types/project-context.ts web/src/api/projects.ts web/src/stores/project-context.ts web/src/modules/project web/src/router/index.ts web/src/views/AppLayout.vue
git commit -m "feat(project): add project management workspace"
```

**验收检查：** 正式 API 是唯一项目数据源；项目切换在刷新后保持且会校验；桌面/移动均可维护项目和成员；归档状态只读；无权限和失败状态明确。

**证据：** `execution-T3.json`、前端构建、关键 DOM/网络记录和 `observation-T3.json`。

**回滚：** 回退 T3 前端提交，移除 `/projects` 静态路由和切换器；后端项目 API 保持可用且不影响现有页面。

**停止条件：** REQ-20260814-022 已在另一分支创建不兼容的 `project-context.ts`/Store；AppLayout 需要保存服务端项目权限到 Token；移动端只能通过横向压缩表格实现。

**升级条件：** 用户要求完整项目计划功能；项目切换需要修改所有现有业务页面；需要修改共享 UI 组件行为。

### T4：全量集成、运行验证与浏览器验收

**需求映射：** P1、P2、P3

**前置任务：** T3

**文件：**

- 修改：`docs/integration/project-context-contract.md`（仅同步实际批准的最终签名）
- 写入证据：`.ai-control/requirements/req-20260818-026-project-context/execution-T4.json`
- 写入证据：`.ai-control/requirements/req-20260818-026-project-context/observation-T4.json`
- 写入证据：`.ai-control/requirements/req-20260818-026-project-context/convergence.json`（仅全部门禁通过后）

**接口：**

- 消费：T1-T3 的最终 Maven、数据库、REST 和前端契约。
- 产出：可重复构建、运行、权限和浏览器证据；不新增产品能力。

- [ ] **步骤 1：运行聚焦和全量自动化检查**

```bash
mvn -pl :ccb-project,:ccb-system,:ccb-infrastructure -am test
mvn test
npm --prefix web run build
node scripts/check-all-governance.mjs
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260818-026-project-context/codex-task-scope.yaml --base origin/main --head HEAD --working-tree
git diff --check
```

预期：所有命令退出码为 0；治理报告 9 个 Maven 模块；范围检查没有 writable/read-only/forbidden 偏差。

证据：每条命令、退出码、测试数和关键摘要。

- [ ] **步骤 2：验证 MySQL 迁移和重复启动**

在用户提供的本地非生产配置下启动 MySQL 和后端；首次启动应用 V37，第二次启动报告 schema 已最新且不重复插入菜单/权限。

运行：`mvn -pl :ccb-boot -am spring-boot:run -Dspring-boot.run.profiles=local`

预期：`/actuator/health` 返回 HTTP 200；`pm_project`、`pm_project_member`、`pm_project_audit_event` 存在；重复启动无迁移错误。

证据：脱敏启动摘要、Flyway 版本、健康接口和表存在性；不记录口令或连接地址。

- [ ] **步骤 3：执行 API 权限矩阵**

使用虚构本地账号验证 OWNER、ADMIN、MEMBER、VIEWER、非成员和跨租户测试主体：

- OWNER 可更新、归档、恢复、维护成员和转移负责人。
- ADMIN 可维护成员但不能转移负责人或归档项目。
- MEMBER 可查看且 `ProjectContextPort.WRITE` 为真，但不能维护项目/成员。
- VIEWER 只能查看。
- 非成员、无平台权限和跨租户请求不返回项目数据。
- ARCHIVED 对所有写动作拒绝，恢复后权限重新生效。

预期：HTTP、统一业务码和数据库审计与矩阵一致。

证据：请求方法/路径、角色、HTTP 结果、业务码和审计行摘要。

- [ ] **步骤 4：执行真实浏览器桌面与移动验收**

角色/路由：项目负责人和只读成员访问 `/projects`；同时验证 AppLayout ProjectContextSwitcher。

视口：1440x900、375x812、390x844、430x932；浅色和深色主题。

操作：加载项目、筛选无结果、创建、编辑、成员新增/改角色/移除、负责人转移、归档/恢复、切换当前项目、刷新、返回；模拟 API 失败和无权限。

预期：无页面级横向溢出，弹层操作可达，长编号/名称不遮挡，危险操作后果明确，网络无越权请求，控制台无未处理错误。

证据：视口、角色、操作结果、关键网络响应、控制台和 `document.documentElement.scrollWidth` 检查。

- [ ] **步骤 5：独立观测并决定收敛**

独立验证者重新运行高风险权限、归档和项目切换路径，核对实际 diff、模块依赖、迁移追加性和需求覆盖。存在任何 P0/P1、安全越权、迁移冲突或移动端不可达时，写入负反馈并返回 planning/executing，不生成 converged 结论。

**验收检查：** P1-P3 自动化、运行和浏览器证据齐全；无开放负反馈；范围一致；回退路径可执行。

**证据：** T4 execution/observation、最终 convergence 和 handoff；明确独立观察者身份与环境。

**回滚：** 关闭项目菜单和前端入口，回退应用模块装配；保留已应用项目表、成员和审计。不得修改历史迁移或生产 DROP。

**停止条件：** 任一自动化检查失败；Flyway 版本冲突；跨租户/非成员可见；归档仍可写；浏览器溢出或关键操作不可达；验证环境包含生产数据。

**升级条件：** 需要生产容量决策、数据补偿、公共 UI 改动、认证 Token 变更或跨系统项目同步。

## 集成检查

| 采样点 | 命令/动作 | 预期信号 |
| --- | --- | --- |
| T1 后 | `mvn -pl :ccb-system,:ccb-project -am test` | 用户目录和项目契约编译/测试通过 |
| T2 后 | `mvn -pl :ccb-project,:ccb-infrastructure -am test` | 领域、权限、Mock 和迁移检查通过 |
| T3 后 | `npm --prefix web run build` | vue-tsc 与 Vite 通过 |
| T4 | `mvn test`、前端构建、治理、范围、Flyway、运行和浏览器 | P1-P3 全证据且无 P0/P1 |

## 控制模型种子

以下内容均为 `hypotheses-only`，由 `$model-engineering-system` 在导入后验证：

- 被控边界候选：`ccb-project`、system 用户目录公开契约、V37 项目 schema、Project Context Store 和 `/projects` 页面。
- 状态变量候选：模块编译状态、Flyway 项目 schema 状态、项目状态、成员角色、当前项目选择、页面异步状态。
- 接口候选：`UserDirectory`、`ProjectContextPort`、`/api/projects`、ProjectContextProvider。
- 传感器候选：Maven 测试、模块边界检查、Flyway 启动、API 权限矩阵、前端构建、浏览器 DOM/网络/控制台。
- 执行器候选：受控文件修改、迁移追加、Mock 数据更新、测试和本地启动命令。
- 扰动候选：V37 被新迁移占用、REQ-20260814-022 并行创建 Store、MySQL 不可用、动态菜单缓存、端口冲突。
- 时延候选：依赖构建、Flyway 启动、Mock 同步、浏览器项目切换刷新。
- 假设：Owner 对模块名、公开包、V37 分配和任务范围的批准在实施期间保持有效；若任一项失效，必须重新规划而不是执行。

## 风险与用户批准

- 高风险动作：新增业务模块和公开契约、修改 platform/system 公共能力、追加 Flyway、增加菜单/RBAC、修改 AppLayout 和全局项目上下文。
- 最大当前风险：`REQ-20260814-022` 仍计划创建 Mock Project Context Store；Owner 已裁定本需求正式 Store 优先，旧任务执行到冲突写入面前必须重新规划。
- 用户已批准计划修订 2，并以 Owner 身份批准模块、公共能力、任务范围和 V37；该批准不能替代 CODEOWNERS、Required Checks 或生产变更审批。
- 独立工作树已建立，交接包状态已由 `blocked` 改为 `approved`；使用 control-engineering `import-handoff` 以 high-assurance 模式导入后，结果必须保持 `baseline`，下一阶段为 `modeling`。

# 迁移系统与人员关系（组件清单关联人员）实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 在系统/组件清单中按项目维护“系统-当前项目成员”关系（一人一角色），列表可查看、人员浮层看详情、查看抽屉内管理、导出含关联人员，写操作经服务端权限校验并审计。

**架构：** 在既有 `ProjectComponentService`/`ProjectComponentController` 内扩展：新增 `dm_component_person` 关系表（Flyway V214 幂等迁移，同时种子参数类别 `DM_COMPONENT_PERSON_ROLE`）；列表/导出批量带出人员；新增成员选项与全量替换保存接口并写 `dm_operation_log`；前端复用 `UiUserIdentity` 与查看抽屉，不新增样式体系。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MySQL 8.4/Flyway、Testcontainers MySQL、Vue 3、TypeScript、Element Plus、Pinia、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-19-dm-component-person-design.md`
- 状态：待确认
- 设计审批：用户已于 2026-09-19 确认
- 计划审批：等待用户确认后进入控制闭环

## 全局约束

- 仅修改 `docs/requirements/REQ-20260919-076-dm-component-person/codex-task-scope.yaml` 的 writable paths；`web/src/components/ui`、`person-directory.ts`、`user-profile.ts`、`platform/system`、架构物理子系统只读复用。
- 不修改 `server/src/platform/system` 参数管理代码；`DM_COMPONENT_PERSON_ROLE` 类别只经由 V214 迁移种子，后续由系统管理/参数管理维护。
- 保持 Java 包名 `com.ccb.datamigration`、既有路由与统一 `ApiResponse`；控制器只做 HTTP 适配，业务在 service。
- 服务端执行认证、RBAC（写 `data-migration:manage`/`system:admin`）、项目数据范围（`permissions.requireProject`）、组件存在与成员归属/启用校验；审计写入 `dm_operation_log`。
- Flyway 只追加；`V214__data_migration_component_person.sql` 使用 `CREATE TABLE IF NOT EXISTS`/`INSERT IGNORE` 与 information_schema 判断，新库与存量库均可执行。
- 不调用或修改生产系统；不使用真实个人信息；本地验收数据仅用开发库虚构成员。
- 前端遵循 `design-h5.md`：桌面表格 + 移动卡片 + 抽屉管理，覆盖加载/空/失败/无权限/提交中状态，375/390/430/1280+ 视口验收。
- 不新增依赖、不批量格式化、不覆盖其他需求的未提交改动（保留工作区既有 `application.yml` 本地改动）。

## 文件职责地图

- `server/src/platform/infrastructure/src/main/resources/db/migration/V214__data_migration_component_person.sql`（candidate-new）：关系表 + 参数类别/选项种子，幂等。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ProjectComponentService.java`（existing）：列表/导出带 persons、成员选项、关系查询与全量保存、组件删除级联、审计。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ProjectComponentController.java`（existing）：新增 persons GET/PUT、member-options GET 路由与权限注解。
- `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/**`（candidate-new）：迁移/查询/写路径/权限聚焦测试。
- `web/src/api/data-migration.ts`（existing）：`DataMigrationComponent.persons` 类型与新增请求封装。
- `web/src/modules/data-migration/views/base/ComponentsPage.vue`（existing）：关联人员列（桌面/移动）、查看抽屉管理区块、导出列、全状态。

## 任务依赖图与并行策略

```text
T1 数据库与参数种子 -> T2 查询与成员选项 -> T3 关系写路径与级联 -> T4 前端 -> T5 集成验收
```

全部串行。T2 依赖 T1 的表/字典；T3 依赖 T2 的 member-options 契约并继续修改同一个 `ProjectComponentService.java`，串行避免写冲突；T4 依赖 T2/T3 的 REST 契约；T5 依赖全部实现。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 关系表与唯一约束 | T1 |
| R2 列表/导出返回 persons | T2 |
| R3 成员选项（项目成员） | T2 |
| R4 保存接口与校验/审计 | T3 |
| R5 列表关联人员列与浮层 | T4 |
| R6 查看抽屉管理/只读 | T4 |
| R7 导出“关联人员（角色）”列 | T2（后端导出列） |
| R8 角色字典 DM_COMPONENT_PERSON_ROLE | T1（种子）、T3（复用 options）、T4（下拉） |
| R9 组件删除级联清理 | T3 |
| R10 全状态与视口验收 | T4、T5 |

---

### T1：关系表迁移与角色字典种子

**需求映射：** R1, R8

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V214__data_migration_component_person.sql`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ComponentPersonMigrationMySqlTest.java`

**接口：**
- 产出：`dm_component_person` 表（主键 `(tenant_id, project_id, system_code, user_id)`，列 `person_role VARCHAR(64)`、`created_by BIGINT`、`created_at TIMESTAMP`、`updated_by BIGINT`、`updated_at TIMESTAMP`，均 NOT NULL，MySQL 8.4 utf8mb4）；`DM_COMPONENT_PERSON_ROLE` 字典类别（`sys_dict_type` id 5824）与 4 个选项（`sys_config` id 58240-58243：负责人/对接人/业务人员/实施人员）。

- [ ] **步骤 1：建立失败基准确认当前最大迁移版本**
  运行：`ls server/src/platform/infrastructure/src/main/resources/db/migration/ | sort -V | tail -3`
  预期：最大 legacy 版本为 `V213__normalize_delivery_unit_artifact_types.sql`，V214 未被占用
  证据：记录输出

- [ ] **步骤 2：编写幂等迁移**
  内容：`CREATE TABLE IF NOT EXISTS dm_component_person (...)`（含中文注释）；`INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted) VALUES (5824, 1, 'DM_COMPONENT_PERSON_ROLE', '组件人员职责', 1, 0)`；`INSERT IGNORE INTO sys_config` 插入 58240-58243 四个选项（`config_key` 形如 `DM_COMPONENT_PERSON_ROLE.OWNER`，`config_value` 中文名），仿照 V186 写法；执行前先 `SELECT COUNT(*)` 校验 id 5824/58240-58243 未被占用，被占用则停机上报。

- [ ] **步骤 3：迁移静态检查**
  运行：`node scripts/check-flyway-migrations.mjs`
  预期：`Flyway migration check passed`，无版本冲突
  证据：退出码 0 与输出

- [ ] **步骤 4：写迁移聚焦测试**
  新建 `ComponentPersonMigrationMySqlTest`（仿 `DashboardMetricMySqlTest` 的 Testcontainers 模式，Flyway 全量 clean+migrate）：断言 `dm_component_person` 存在且主键列集正确；插入两行不同 `user_id` 成功；插入同 `user_id` 重复行抛主键冲突；断言 `DM_COMPONENT_PERSON_ROLE` 类别与 4 个选项存在。

- [ ] **步骤 5：运行聚焦测试**
  运行：`mvn -pl :ccb-data-migration -am -Dtest=ComponentPersonMigrationMySqlTest test`
  预期：通过，0 个失败
  证据：保存退出码与测试摘要

- [ ] **步骤 6：建立提交检查点**
  运行：`git add server/src/platform/infrastructure/src/main/resources/db/migration/V214__data_migration_component_person.sql server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ComponentPersonMigrationMySqlTest.java && git commit -m "feat(data-migration): add component person relation table and role dictionary seed"`

**回滚：** 未提交前删除两个新文件；已提交则 `git revert` 该提交（迁移仅新增表与字典，无存量影响）。

**停止条件：** dict id 5824/58240-58243 与既有数据冲突且无法选用安全 id；全新库全量迁移在 V214 失败。

**升级条件：** 迁移在其他旧库执行出现非预期失败；字典选项文案需业务侧修订。

---

### T2：列表/导出 persons 与项目成员选项

**需求映射：** R2, R3, R7

**前置任务：** T1

**文件：**
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ProjectComponentService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ProjectComponentController.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ComponentPersonQueryMySqlTest.java`

**接口：**
- 消费：T1 的 `dm_component_person` 表与既有 `components/export`/`getSystemOptions` 结构。
- 产出：`components` 行内新增 `person_count` 与 `persons`（`user_id`、`display_name`、`person_role`）；`components/export` 新增“关联人员（角色）”列；`GET /api/data-migration/components/member-options?projectId=` 返回 `[{ user_id, display_name, username }]`。

- [ ] **步骤 1：建立基线**：运行 `mvn -pl :ccb-data-migration -am -DskipTests compile` 确认当前可编译。
- [ ] **步骤 2：实现列表批查**：`components` 主查询后按 `(project_id, system_code)` 集合批查 `dm_component_person JOIN sys_user`，按 `system_code` 分组映射到行内 `persons` 与 `person_count`；空关系给空数组与 0。
- [ ] **步骤 3：实现导出列**：`exportComponents` 结果行增加 `persons`，POI 追加“关联人员（角色）”列头与 `姓名（角色）` 逗号拼接内容，空关系写 `-`。
- [ ] **步骤 4：实现成员选项**：`getMemberOptions(projectId, user)`：`pm_project_member m JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.status = 1 AND u.deleted = 0 WHERE m.tenant_id = ? AND m.project_id = ? AND m.deleted = 0 ORDER BY u.display_name`；控制器 `GET /components/member-options`，读权限沿用 `data-migration:components` 集合。
- [ ] **步骤 5：写聚焦测试**：`ComponentPersonQueryMySqlTest`（Testcontainers）种子一个项目、两个 `dm_component`、三个项目成员（其一停用）：断言列表 persons/person_count 正确；导出列内容正确；member-options 排除非成员与停用成员；无权限调用 403。
- [ ] **步骤 6：回归验证**
  运行：`mvn -pl :ccb-data-migration -am -Dtest=ComponentPersonQueryMySqlTest test`
  预期：通过，0 个失败
  证据：保存退出码与测试摘要
- [ ] **步骤 7：建立提交检查点**
  运行：`git commit -am "feat(data-migration): include component persons and member options in queries"`

**回滚：** `git revert` 本任务提交；列表/导出退回原字段（新增字段不影响旧调用点）。

**停止条件：** 成员归属或停用过滤语义与既有 `pm_project_member` 数据不一致（需对照 `ProjectView` 成员口径）。

**升级条件：** 批量带出导致列表性能恶化（单页关系数异常放大）；既有组件查询被其他未登记调用点破坏。

---

### T3：关系写路径、级联删除与角色选项

**需求映射：** R4, R8, R9

**前置任务：** T2

**文件：**
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ProjectComponentService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ProjectComponentController.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ComponentPersonWriteMySqlTest.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/web/ComponentPersonControllerSecurityTest.java`

**接口：**
- 消费：T2 的 member-options 契约；既有 `deleteComponent`/`audit`/`permissions`。
- 产出：`GET /api/data-migration/components/persons?projectId=&systemCode=` 返回该组件人员（含角色）；`PUT /api/data-migration/components/persons` 全量替换（body `{ projectId, systemCode, persons: [{ userId, personRole }] }`），写权限 `data-migration:manage`/`system:admin`，审计 `COMPONENT_PERSON_SAVE`。

- [ ] **步骤 1：建立基线**：运行 `node scripts/check-flyway-migrations.mjs` 与 `git diff --check`，确认 T2 后工作区干净。
- [ ] **步骤 2：实现查询接口**：`getPersons(projectId, systemCode, user)`：`requireProject` + 组件存在校验，返回 `[{ user_id, display_name, person_role }]`。
- [ ] **步骤 3：实现保存**：`savePersons(projectId, systemCode, persons, user)`：`@Transactional`；校验组件存在、`personRole` 必须属于 `DM_COMPONENT_PERSON_ROLE` 字典、每个 `userId` 是当前项目启用成员（否则逐一指名拒绝）；全量删除后按序插入，含 audit 列；写 `dm_operation_log`（`operation_code='COMPONENT_PERSON_SAVE'`、`entity_type='COMPONENT'`、`entity_id=system_code`）。
- [ ] **步骤 4：级联删除**：`deleteComponent` 同一事务内先删 `dm_component_person` 再删 `dm_component`（若方法缺 `@Transactional` 则仅在该方法补充），保留既有 `COMPONENT_DELETE` 审计。
- [ ] **步骤 5：角色选项验证**：确认 `GET /api/data-migration/options/DM_COMPONENT_PERSON_ROLE` 返回迁移种子的 4 项（复用既有 `getDataMigrationParamOptions` 通路，不加新端点）。
- [ ] **步骤 6：写聚焦测试**：`ComponentPersonWriteMySqlTest`：全量替换生效；非成员/停用成员被拒绝；非法 `personRole` 被拒绝；保存后审计行存在；删除组件后关系消失。`ComponentPersonControllerSecurityTest`（仿 `ProgramControllerSecurityTest`）：无写权限 403，有写权限 200。
- [ ] **步骤 7：回归验证**
  运行：`mvn -pl :ccb-boot -am test`
  预期：全部通过，0 个失败
  证据：保存退出码与测试摘要
- [ ] **步骤 8：建立提交检查点**
  运行：`git commit -am "feat(data-migration): manage component person relations with validation and audit"`

**回滚：** `git revert` 本任务提交；关系接口下线，新表保留无副作用。

**停止条件：** `req-20260911-071-dm-governance` 等历史任务账本要求与本次审计/操作码命名冲突。

**升级条件：** 存在同 `(system, user)` 唯一键上的并发覆盖争议需要产品决策；既有 `deleteComponent` 调用点行为需要调整。

---

### T4：前端 API 与组件清单页面

**需求映射：** R5, R6, R7, R8, R10

**前置任务：** T3

**文件：**
- 修改：`web/src/api/data-migration.ts`
- 修改：`web/src/modules/data-migration/views/base/ComponentsPage.vue`

**接口：**
- 消费：T2/T3 的 REST 契约（列表 `persons`、`member-options`、`persons GET/PUT`）与既有 `getDataMigrationParamOptions('DM_COMPONENT_PERSON_ROLE')`。
- 产出：`DataMigrationComponent.persons?: ComponentPerson[]`；`getComponentPersons`、`getComponentMemberOptions`、`saveComponentPersons` 封装。

- [ ] **步骤 1：建立基线**：运行 `npm --prefix web run build` 前置当前可构建。
- [ ] **步骤 2：扩展 API 封装**：在 `web/src/api/data-migration.ts` 添加类型与函数（`GET /components/persons`、`GET /components/member-options`、`PUT /components/persons`），角色选项复用 `getDataMigrationParamOptions`。
- [ ] **步骤 3：列表列（桌面/移动）**：`UiDataTable` 新增“关联人员”列：每行渲染前 2 名成员 `UiUserIdentity`（`:user-id` + `:fallback-name`，`variant="compact"`、`show-name` 显示姓名）+ 角色 `el-tag`；超过 2 人显示 `+N` 并以 `el-popover`/展开列表查看全部；移动卡片同规则；无关系显示 `-`；浮层交互由 `UiUserIdentity` 自带（hover/点击人员详情）。
- [ ] **步骤 4：查看抽屉管理区块**：`viewOpen` 抽屉新增“关联人员”区：只读用户仅展示 `UiUserIdentity` 列表；`canManage` 用户可添加成员（`member-options` 下拉）、逐行删除、逐行选角色（`DM_COMPONENT_PERSON_ROLE` 下拉），保存调 `saveComponentPersons` 全量替换；覆盖加载/空/失败/无权限/提交中（按钮 loading 防重复提交）。
- [ ] **步骤 5：状态与移动端**：抽屉宽度沿用现有 `min(900px, 92vw)`；成员行在 375px 可换行不溢出；角色下拉选项缺失时提示“请先在参数管理配置”。
- [ ] **步骤 6：前端构建与自检**
  运行：`npm --prefix web run build`、`git diff --check`
  预期：构建成功、类型检查通过、无空白错误
  证据：保存构建输出
- [ ] **步骤 7：建立提交检查点**
  运行：`git commit -am "feat(data-migration): show and manage component persons in system/component inventory"`

**回滚：** `git revert` 本任务提交；页面退回无关联人员展示。

**停止条件：** `UiUserIdentity`/`person-directory` 在列表列形态下出现异常（先回归既有调用页面）。

**升级条件：** 角色多选/展示与“一人一角色”约束在交互上冲突需要产品决策；移动端抽屉溢出无法按期修完。

---

### T5：集成验证与浏览器验收

**需求映射：** R1-R10

**前置任务：** T4

**文件：**
- 记录：`.ai-control/requirements/req-20260919-076-dm-component-person/execution-T1.json`
- 记录：`.ai-control/requirements/req-20260919-076-dm-component-person/observation-T1.json`

**接口：**
- 消费：T1-T4 全部实现；本地 dev 环境（后端 8080、前端 5173、MySQL/MinIO 容器）。

- [ ] **步骤 1：治理与迁移检查**
  运行：`node scripts/check-all-governance.mjs`、`node scripts/check-flyway-migrations.mjs`、`git diff --check`
  预期：本 REQ-20260919-076 无告警；V214 通过；无空白错误
  证据：保存三份输出
- [ ] **步骤 2：后端全量测试**
  运行：`mvn -pl :ccb-boot -am test`
  预期：全部通过
  证据：保存测试摘要
- [ ] **步骤 3：前端构建**
  运行：`npm --prefix web run build`
  预期：构建成功
  证据：保存构建输出
- [ ] **步骤 4：本地数据准备**：在开发库为 `P2026-001` 的 1-2 个组件配置 2-3 名项目成员关系（通过页面或接口，仅虚构数据）。
- [ ] **步骤 5：浏览器验收**：真实浏览器以管理员与只读角色登录，分别在 `375x812`、`390x844`、`430x932`、`1280x800` 验收：列表列与浮层、抽屉内增删改角色并保存、只读无编辑控件、导出 Excel 列、深浅主题、加载/空/失败/无权限状态、`document.documentElement.scrollWidth` 不超视口。
  证据：记录视口、路径、操作、接口结果与控制台无错误
- [ ] **步骤 6：写观测与收敛证据**：按 rddmp-delivery-engineer 证据等级填写 `execution-T1.json` 与 `observation-T1.json`，再按 `$control-engineering` 门禁进入收敛。

**回滚：** 分任务提交逐个 `git revert`；数据库按“保留表停止功能”或早期环境重建处理。

**停止条件：** 任一试运行验证失败且无法在当轮修正。

**升级条件：** 浏览器验收暴露设计缺口（如角色管理交互、移动端层级）需回到设计修订。

---

## 集成检查

| 之后 | 命令 | 预期 |
| --- | --- | --- |
| T1 | `node scripts/check-flyway-migrations.mjs` | 通过 |
| T1 | `mvn -pl :ccb-data-migration -am -Dtest=ComponentPersonMigrationMySqlTest test` | 通过 |
| T3 | `mvn -pl :ccb-boot -am test` | 通过 |
| T4 | `npm --prefix web run build` | 构建成功 |
| T5 | `node scripts/check-all-governance.mjs` | 本需求无告警 |

## 控制模型种子

以下为 `hypotheses-only`，供 `$model-engineering-system` 验证：

- 被控边界：`business/data-migration` 组件清单的关系读写与展示链路；`dm_component_person` 为唯一关系事实源。
- 状态变量候选：每系统关系集合、角色字典版本、当前项目上下文（顶部切换器）。
- 接口候选：列表/导出 persons、member-options、persons GET/PUT、`DM_COMPONENT_PERSON_ROLE` options。
- 传感器候选：聚焦测试断言、治理检查、构建、浏览器视口检查。
- 执行器候选：`savePersons` 全量替换、`deleteComponent` 级联删除、前端抽屉保存按钮。
- 扰动候选：字典选项增删、成员启停/移除、组件启停/删除、并发编辑。
- 时延候选：人员档案批量缓存 TTL（10 分钟）导致的浮层延迟、批查随关系量增长。
- 假设：项目内关系总量小；`deleted=0`/`status=1` 成员语义一致；人员档案接口覆盖浮层所需字段。

## 风险与用户批准

- 数据库新表与写接口属高风险，需 Owner 复核；并发编辑最后提交覆盖先提交（已接受并在保存前提示）。
- 请用户复核本计划；批准后计划状态置为 `ready`、交接包 `handoff_status=approved`，再导入控制闭环实施。

# 项目计划分组实施计划

> 执行要求：按任务顺序实施；每项完成后运行局部验证，后端和前端接口契约一致后再做浏览器验收。

**目标：** 为项目计划增加可持久化的项目级分组，并支持主计划带子计划一起移动。

**架构：** 新增 `pm_project_plan_group` 和 `pm_project_plan.group_id`，复用项目计划权限与实体授权。后端提供分组 CRUD 和主计划移动接口，前端展示三级树并提供拖拽及移动端菜单。

**技术栈：** Spring Boot 3、JDK 17、JdbcTemplate、MySQL/Flyway、Vue 3、TypeScript、Element Plus。

## 全局约束

- 只修改本需求 `codex-task-scope.yaml` 的 `writable_paths`。
- 不修改已发布 Flyway 脚本；所有新表字段带中文注释。
- 不改变 `parent_id`、编号、计划删除和项目权限既有语义。
- 所有界面文案使用中文，接口错误使用中文。

## 文件职责地图

- 新建 V40：项目计划分组表、计划分组字段、索引和字段注释。
- 修改 `ProjectService/Controller`：分组查询、CRUD、后代批量移动和权限校验。
- 修改 `MockDataInitializer`：登记新表列，保证 mock 清理/初始化识别分组表。
- 修改 `project.ts/project.ts types`：分组和移动接口契约。
- 修改 `ProjectView.vue`：三级树、拖拽、移动端操作、新建/编辑/删除分组。
- 修改 `styles.css`：分组行、拖拽高亮和移动提示的主题响应式样式。

## 任务依赖图与并行策略

`T1 数据库` -> `T2 后端契约` -> `T3 前端交互` -> `T4 集成验收`。数据库、后端和前端不并行，避免接口字段漂移。

### T1：新增项目计划分组数据模型

**需求映射：** R1, R2, R3

**文件：** 新建 `server/src/platform/infrastructure/src/main/resources/db/migration/V40__project_plan_groups.sql`；修改 `server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java`。

**步骤：**

1. 追加 `pm_project_plan_group`，包含 `id/tenant_id/project_id/group_name/description/sort_no/created_at/updated_at/deleted` 和项目、租户索引。
2. 给 `pm_project_plan` 追加可空 `group_id`、分组索引和中文注释；不回填已有数据，空值即未分组。
3. 在 mock 初始化列集合中登记新表和新字段。
4. 执行 Flyway/迁移静态检查及 `git diff --check`。

**回滚：** 仅回滚尚未发布的 V40 和对应代码；已发布环境按数据库恢复流程处理，不修改历史迁移。

**停止/升级：** 发现 V40 版本已存在、表结构与实际数据库冲突或需要手工改生产库时停止并升级。

### T2：分组 REST 与事务性移动

**需求映射：** R1, R2, R5

**前置任务：** T1

**文件：** 修改 `ProjectController.java`、`ProjectService.java`；测试 `ProjectServiceTest.java`。

**接口产出：** `GET/POST/PUT/DELETE /api/project/{projectId}/plan-groups` 与 `PUT /api/project/{projectId}/plans/{planId}/group`；计划查询和单计划返回 `group_id/group_name`。

**步骤：**

1. 为分组查询和写操作复用 `plan` 资源权限与 `requireProjectAccess`。
2. 校验分组名称长度、同项目唯一性和目标项目归属。
3. 移动接口拒绝子计划，仅锁定并校验主计划；递归查找所有后代并在事务内更新 `group_id`。
4. 删除分组只软删分组并清空其计划的 `group_id`。
5. 增加测试覆盖跨项目、子计划、无权限和后代同步。
6. 运行 `mvn -pl :ccb-system -am test`。

**回滚：** 恢复 Java 文件和测试；V40 保持追加迁移不改写。

**停止/升级：** 若当前 JDBC 测试无法独立验证递归更新，补充可重复的集成测试或升级，不用未验证的前端状态替代后端事实。

### T3：计划分组前端交互

**需求映射：** R1, R3, R4

**前置任务：** T2

**文件：** 修改 `web/src/api/project.ts`、`web/src/types/project.ts`、`web/src/views/ProjectView.vue`、`web/src/styles.css`。

**步骤：**

1. 加载分组并把计划按 `group_id` 组织为“分组 -> 主计划 -> 子计划”，未分组使用固定中文节点。
2. 在计划工具栏增加“新建分组”和提示“可以拖动主计划进行分组”。
3. 桌面端让主计划行可拖动、分组行可接收并高亮，成功后调用移动接口并刷新；子计划不可拖动。
4. 增加主计划“移动到分组”操作和移动端菜单，保证无拖拽设备可用。
5. 支持分组重命名和删除确认，删除后刷新且计划仍显示在未分组。
6. 保持已有空、加载、失败、无权限状态并运行 `npm --prefix web run build`。

**回滚：** 恢复前端四个文件并重新构建。

**停止/升级：** 若拖拽导致树行无法识别或移动端出现横向溢出，保留移动菜单并先修复布局，不宣称拖拽已验收。

### T4：集成验收

**需求映射：** R1-R5

**前置任务：** T3

**步骤：**

1. 运行 `git diff --check`、后端测试和前端构建。
2. 使用本地数据库验证新建分组、移动主计划、刷新、删除分组和后代一致性。
3. 通过浏览器检查桌面拖拽、中文提示、移动端移动菜单和失败提示。

**完成信号：** 需求验收全部可重复，未出现跨项目写入、子计划拆组或删除计划。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T1, T2, T3, T4 |
| R2 | T1, T2, T4 |
| R3 | T1, T3, T4 |
| R4 | T3, T4 |
| R5 | T2, T4 |

## 风险与用户批准

用户已确认分组模型和拖拽提示。剩余风险为浏览器拖拽事件差异，移动端菜单作为等价降级入口；任何后端授权或数据库冲突都停止在对应任务并报告。

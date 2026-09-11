# 数据迁移「迁移参数」字段信息子表实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和
> 系统建模，不得直接跳到执行阶段。

**目标：** 为迁移参数增加字段信息子表，支持批量添加字段、字段维护入口，并让参数+字段可在同一 Excel 中批量导入。

**架构：** 采用用户确认的推荐设计：新增 `dm_parameter_field` 子表（V200 幂等迁移 + `DM_PARAMETER_FIELD_TYPE`
码值），扩展 `ParameterService`/`ParameterController`（字段 CRUD、创建携带字段、详情/列表字段数、级联删除、
十一列导入），前端 `ParametersPage.vue` 增加字段批量区、字段抽屉与导入优化；复用 `dm_target_table_field`
已验证范式，不引入新公共组件或样式。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Apache POI 5.4.1、MySQL 8.4/Flyway、Vue 3、
TypeScript、Element Plus、Pinia、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-10-data-migration-parameter-field-design.md`
- 状态：已确认
- 设计审批：用户 2026-09-10 采纳推荐设计并确认三个决策点
- 计划审批：随需求 REQ-20260910-069 进入控制闭环

## 全局约束

- 仅修改 `REQ-20260910-069` 任务范围中的 writable paths；保护工作区 REQ-067/068 未提交改动。
- 数据 Owner 为 `business/data-migration`；保持 Java 包名、模块 artifact、菜单路由与统一响应。
- Flyway 只追加 V200，不修改 V197/V198/V199 或其他已发布脚本；V200 幂等可重跑。
- 服务端执行认证、RBAC、租户/项目范围、记录 Owner/管理员校验与 `PARAMETER` 审计；前端显隐不代替后端校验。
- 不调用或修改生产系统，不使用真实个人信息、生产日志、附件、密钥或凭据。
- 页面复用 `UiToolbar`、`UiDataTable`、`UiFormDrawer`、`UiEmptyState` 和语义主题变量，遵守 design-h5.md
  移动端规则（375/390/430 视口无横向溢出）。

## 文件职责地图

- `server/src/platform/infrastructure/src/main/resources/db/migration/V200__data_migration_parameter_field.sql`
  （candidate-new）：字段子表、唯一键/索引、`DM_PARAMETER_FIELD_TYPE` 码值，幂等。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DataMigrationCodeValueService.java`：
  注册 `DM_PARAMETER_FIELD_TYPE` 类别。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ParameterService.java`：
  字段列表/批量新增/行编辑/删除/批量删除、创建携带字段、详情与列表字段数、导入扩展、级联删除。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ParameterController.java`：
  字段 REST 路由（沿用参数写权限码）。
- `web/src/api/data-migration.ts`：字段类型、请求封装、`DM_PARAMETER_FIELD_TYPE` 码值类别。
- `web/src/modules/data-migration/views/content/ParametersPage.vue`：新增字段批量区、字段抽屉、详情字段、
  导入文案/模板说明。
- `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/**`：字段服务、V200 迁移、权限回归测试。

## 任务依赖图与并行策略

```text
T1 数据库与码值契约（V200 + DataMigrationCodeValueService）
  -> T2 后端字段领域（ParameterService/ParameterController）
  -> T3 前端 API 与页面（data-migration.ts + ParametersPage.vue）
  -> T4 测试与集成验证（服务测试、V200 迁移测试、构建、治理检查）
```

全部任务串行：T2 依赖 T1 的表结构与码值；T3 依赖 T2 的 REST 契约；T4 依赖完整实现。串行同时避免覆盖
REQ-067/068 在 `ParameterService.java`、`ParametersPage.vue`、`data-migration.ts` 的未提交修改。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 字段子表与码值、同参数中英文名唯一 | T1、T2 |
| R2 批量添加字段与字段维护入口、详情展示 | T2、T3 |
| R3 十一列导入同时导入参数与字段 | T2、T3 |
| R4 参数删除/恢复/彻底删除与字段级联 | T2 |
| R5 桌面与移动端无横向溢出、全状态覆盖 | T3 |
| R6 权限沿用 RBAC、写操作后端校验并审计 | T2、T4 |

## 验证与停止条件

- 必须执行：聚焦测试（ParameterServiceTest、DataMigrationParameterFieldMigrationMySqlTest、
  DataMigrationModuleRegistrationTest）、`npm --prefix web run build`、治理检查、范围检查、`git diff --check`。
- 浏览器验收：真实浏览器桌面与 375/390/430 视口走通 新增带字段/字段抽屉/导入/删除/回收站。
- 停止条件：任务范围外文件被修改、既有 067/068 差异被覆盖、唯一约束失效、构建或治理检查失败。

## 回滚

未应用 V200 时回退本需求代码；已应用 V200 且无新数据时删除迁移、子表与码值并回退代码；存在新数据后修复前进。

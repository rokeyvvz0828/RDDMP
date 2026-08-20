# 项目管理增强实施计划

> 执行要求：按任务顺序实施，每个任务完成后运行对应局部验证。

## T1：数据库与参数契约

**文件：** `server/src/platform/infrastructure/src/main/resources/db/migration/V37__project_management_enhancement.sql`

- 追加项目阶段、计划阶段字段。
- 新增计划组织关联表及索引、中文注释。
- 初始化 `PROJECT_PHASE`、`PLAN_PHASE` 参数类别和值。

**验证：** SQL 静态检查、Flyway 启动迁移、`git diff --check`。

## T2：后端项目域

**文件：** `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`、`ProjectController.java`、对应测试。

- 新增项目选项接口。
- 项目创建初始化 `PM/项目负责人` 角色并绑定创建人。
- 增加阶段读取与启用值校验。
- 增加组织关系保存、查询与租户校验。
- 增加项目和计划日期范围校验。

**验证：** `mvn -pl :ccb-system -am test`。

## T3：前端项目工作台

**文件：** `web/src/api/project.ts`、`web/src/types/project.ts`、`web/src/views/ProjectView.vue`。

- 加载阶段和组织树选项。
- 项目表单阶段下拉与日期校验。
- 计划表单阶段、牵头方、配合方与日期校验。
- 列表显示阶段和组织名称。

**验证：** `npm --prefix web run build`。

## T4：集成检查

- 运行 `git diff --check`、后端测试和前端构建。
- 检查创建、编辑、日期错误和空组织选项状态。

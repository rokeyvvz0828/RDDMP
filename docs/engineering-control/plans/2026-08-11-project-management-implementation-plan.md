# 项目管理实施计划

> 执行要求：按任务顺序实施，每个任务完成后执行局部验证；不得将“构建通过”替代越权场景验证。

## T1：数据库与权限种子

**文件：** `server/src/platform/infrastructure/src/main/resources/db/migration/V35__project_management.sql`

创建四张 `pm_` 表、索引、唯一约束、中文表字段注释、项目菜单、四类资源操作权限和 `SUPER_ADMIN` 角色授权。只追加 Flyway 迁移，不修改历史脚本。

**验证：** SQL 静态检查、Flyway 启动验证、`git diff --check`。失败时停止后端实现并修正字段契约。

## T2：后端项目域接口与数据范围

**文件：** `server/src/platform/system/src/main/java/com/ccb/system/project/**` 及对应测试。

实现 DTO、Controller、Service 和查询映射。所有读写操作先校验 `project:*` 操作权限，再校验当前用户是否为超级管理员、项目负责人或有效成员；普通用户列表仅返回成员项目。实现项目、计划、成员、项目角色的增改删及详情统计。

**验证：** `mvn -pl :ccb-system -am test`；测试管理员全量、成员可见、非成员 403、越权 ID 403、跨租户空结果和删除级联逻辑。

## T3：前端工作台与详情页签

**文件：** `web/src/api/project.ts`、`web/src/types/project.ts`、`web/src/views/ProjectView.vue`、`web/src/router/index.ts`、必要的 `AppLayout.vue` 和主题样式。

复用通用工具栏、数据表格、状态标签、用户头像组件和权限按钮。工作台使用项目卡片；详情页使用四个页签，所有页签具备加载、空、错误、无权限和提交中状态。路由名称与菜单种子一致，支持返回工作台和刷新。

**验证：** `npm --prefix web run build`，桌面及窄视口检查卡片、页签、抽屉和表格无溢出。

## T4：集成与收敛

执行 `node scripts/check-all-governance.mjs`、`mvn test`、前端构建和接口冒烟；检查动态菜单只出现一个项目入口、普通用户不可见非成员项目、管理员可见全部项目。保留未执行的浏览器检查和外部环境限制作为残余风险，不虚报为通过。

# 项目计划编号与项目日历实施计划

## 状态与来源

- 需求：`docs/requirements/REQ-20260812-023-project-plan-calendar/requirement.md`
- 设计：`docs/engineering-control/designs/2026-08-12-project-plan-calendar-design.md`
- 状态：已获用户确认，进入实施

## 任务

### T1 数据库与编号服务契约

- 文件：新增 `V38__project_plan_number_and_calendar.sql`，修改项目服务和测试。
- 内容：增加项目规则、计划编号、序号计数器和唯一索引；服务端生成编号、校验规则、计算实际进度。
- 验证：Flyway 静态检查、项目服务测试、计划编号规则边界测试。

### T2 前端项目设置与进度

- 文件：`web/src/api/project.ts`、`web/src/types/project.ts`、`web/src/views/ProjectView.vue`、`web/src/styles.css`。
- 内容：设置接口、设置页签、计划编号列、卡片实际进度。
- 验证：TypeScript/Vite 生产构建。

### T3 项目概览日历

- 文件：`web/src/views/ProjectView.vue`、`web/src/styles.css`。
- 内容：按月渲染有日期计划，显示状态和进度，点击跳转计划页签。
- 验证：前端构建和运行页面检查。

### T4 集成验证

- 内容：重启后端应用迁移，检查健康接口、Flyway 版本、接口返回字段和前端资源。
- 验证：`git diff --check`、Maven 测试、前端构建、健康接口。

## 不变约束

- 不回退现有未提交改动。
- 不修改历史 Flyway 脚本。
- 不拆分项目菜单，不新增独立日历表。

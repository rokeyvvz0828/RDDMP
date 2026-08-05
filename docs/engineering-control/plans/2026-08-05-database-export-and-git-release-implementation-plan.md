# 数据库注释与 Git 发布实施计划

> 执行要求：在既有单租户平台和企业级工作流实现基础上，补齐数据库可读性、可复现导出和代码交付物，不改变已确认的业务行为。

**目标：** 为项目数据库表和字段提供稳定的中文注释，提交当前数据库结构导出与可重复导出脚本，并将完整代码安全推送到远程 `main` 分支。

**架构：** 保留现有 Spring Boot、MyBatis-Plus、Flyway、Vue 3 和 Flowable 模块边界。数据库注释通过新增 Flyway 迁移统一补齐；schema 导出作为文档快照，导出脚本作为后续更新入口。

**技术栈：** MySQL 8.4、Flyway、Maven、Node.js、Git。

## 全局约束

- 只增加注释、导出文档和说明性代码注释，不重写已完成的流程运行逻辑。
- 注释迁移必须可重复执行，不能删除业务数据或改变字段类型、索引和约束。
- 导出文件不包含密码、JWT 密钥、头像内容或业务数据，只包含数据库结构。
- Git 推送使用普通 `push`，不执行强制推送，不覆盖远程已有提交。

## 实施任务

### T1：数据库注释迁移与 schema 快照

**文件：**

- 新增 `ccb-infrastructure/src/main/resources/db/migration/V23__database_comments.sql`
- 新增 `docs/database/export-schema.ps1`
- 新增 `docs/database/ccb_platform_schema.sql`
- 新增 `docs/database/README.md`

**验收：**

- `sys_*`、`wf_*`、`ai_*` 和 Flowable 表都有表注释。
- 表字段均有非空注释；迁移脚本只修改注释。
- `mysqldump --no-data --no-tablespaces` 能重新生成结构快照。
- schema 文件不包含 `INSERT`、密码哈希、令牌或密钥值。

### T2：代码注释与仓库基础文件

**文件：**

- 在复杂的 Flowable 编排、审批人解析、审计和前端流程设计器处补充解释性注释。
- 新增 `.gitignore`，排除构建产物、运行日志、本地环境文件和 IDE 文件。
- 补充根目录 README 的启动、数据库和导出说明。

**验收：**

- 注释解释设计原因、边界和外部引擎交互，不添加空泛逐行注释。
- 项目构建和既有测试保持通过。

### T3：验证与发布

**命令：**

- `mvn -DskipTests package`
- `npm run build`（工作目录 `ccb-web`）
- Flyway 启动/迁移检查和 schema 文件敏感内容扫描
- `git add`、`git commit`、`git push origin main`

**停止条件：** 构建失败、迁移出现破坏性变更、远程 `main` 有未合并提交、或 GitHub 认证失败时停止推送并报告证据。

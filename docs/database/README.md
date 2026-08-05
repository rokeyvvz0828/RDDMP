# 数据库结构

`ccb_platform_schema.sql` 是当前单租户平台数据库的结构快照，包含平台业务表、工作流业务表、AI 配置表以及 Flowable 引擎表的表结构、索引和注释，不包含业务数据。

数据库表和平台业务字段的中文注释由 `V23__database_comments.sql` 维护。Flowable 引擎字段由 Flowable 官方 schema 管理，迁移中不改字段定义，仅补充表用途说明。

重新导出结构：

```powershell
$env:DB_PASSWORD = '<数据库密码>'
.\docs\database\export-schema.ps1
```

脚本默认读取 `ccb-platform-mysql` 容器中的 `ccb_platform` 数据库，并使用 `mysqldump --no-data --no-tablespaces --skip-triggers`，因此不会导出业务数据、触发器内容或表空间信息。

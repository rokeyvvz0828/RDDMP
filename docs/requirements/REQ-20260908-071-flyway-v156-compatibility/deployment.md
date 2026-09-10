# 部署与回退

标准链固定使用 `V156__platform_operation_audit.sql` 和 `V200__rename_release_drill_plan_menu.sql`。空库和已经执行标准 V156 的库直接执行 Flyway。

旧 licon 库先编译并运行 `NormalizeFlywayHistory` 的只读诊断；输出 `NORMALIZATION_DRY_RUN_OK` 后，再以 `--apply` 执行并启动标准 Flyway。应用模式会先创建且绝不覆盖 `flyway_schema_history_req071_backup`。工具只接受两个已知旧校验和；纯菜单 V156 会删除该历史行，让标准审计 V156 补跑；审计结构已经完整时只把 V156 历史归一为标准校验和；旧本地 V200 历史会删除，让幂等菜单 V200 重新登记。

工具拒绝未知校验和、重复成功记录和部分审计结构。拒绝时不得运行通用 `flyway repair`。回退只恢复操作前的 `flyway_schema_history` 备份；V200 只更新展示名称，无业务数据回退要求。

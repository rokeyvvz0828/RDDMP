## 需求与范围

- 需求编号：REQ-20260915-001
- 需求文档：`docs/requirements/REQ-20260915-001-default-project-migration/requirement.md`
- 任务范围：同目录 `codex-task-scope.yaml`
- 控制记录：`.ai-control/requirements/req-20260915-001-default-project-migration/`，observing
- 目标模块与 Owner：platform/infrastructure，rokeyvvz0828

## 变更

空库迁移到 V209 时，旧 SQL 已生成网络分区数据，却没有初始化其必需的 RDDMP-PLATFORM 项目，导致默认项目守卫失败。本补丁追加 `V206_20260915143000`，在 V207/V209 前初始化缺失项目、同租户 admin 的 PM 成员关系和标准阶段，已有项目保持原样，成功执行过 V209 的库不补写业务数据。

V207、V209 及其他已发布 SQL 保持原字节。新增九个 MySQL 8.4 集成场景，并给迁移文件名检查增加单文件例外。恢复流程见同目录 `recovery.md`。

## 风险

- 数据初始化涉及项目归属，只选择同租户唯一有效 admin；缺失 admin 或存在已删除默认项目时失败关闭，永久插入失败全部回滚。
- 已升级环境需审核 pending 迁移并使用 outOfOrder 补跑。V209 守卫失败的库必须先核对没有部分永久 DDL，再执行受限 repair 流程。
- 迁移时停止业务写入。ID 动态分配，不能并发创建项目。
- 本地初始化验证必须设置 `MOCK_DATA_ENABLED=false`。当前 mock 导入仍使用固定项目 ID，未在本任务中改造，不应覆盖正式项目。
- 没有连接或改动运营数据库，没有前端或 API 变更。

## 验证

最终命令和结果见同目录 `validation.md`。Java 使用 17，MySQL 使用独立 Testcontainers 容器。

已确认两个主线门禁问题，需要在合并前关闭：

1. `check-all-governance.mjs` 报历史控制记录的 topic、文件名和缺失验收材料问题；报错来自已有需求目录。
2. `DataMigrationModuleRegistrationTest` 有 4 个断言失败、4 个文件缺失错误。例如测试引用 V161 的 remove_compatibility_columns，而主线文件实际为 V162；该测试与本补丁基线一致。

本 PR 不应在上述门禁未处理时直接合入 main。

## 发布与回退

- 上线验证：无失败 Flyway 记录，前置补丁/V207/V209 成功，同租户默认项目唯一且归属正确。
- 未执行时可回退代码；执行后不能删除已被业务数据引用的项目，优先向前修复。恢复数据库必须使用升级前备份并确认后续写入影响。
- 需要项目及数据库 Owner 对初始化归属与恢复条件进行复核。

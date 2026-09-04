# 数据迁移中间表模型收敛架构决策

> ADR：ADR-DM-20260903-01
> 关联需求：REQ-20260903-064
> 状态：Accepted（用户已确认推荐方案）
> 日期：2026-09-03

## 1. 背景与问题

数据迁移模块目前存在两套“中间表结构”模型：

- 内容侧 `dm_intermediate_table`：由 V153/V154 建立，主数据放在 `structured_data` JSON 中，`StructuredAssetService` 和 `ExcelService` 仍直接读写。
- 基础资料侧 `dm_target_table(table_category='INTERMEDIATE')` + `dm_target_table_field`：由 V88 建立，提供表级与字段级 CRUD、导入导出、权限和关联校验。

两套模型导致菜单、接口、字段校验和关联查询的事实源不一致。旧表 JSON 与规范化字段表的字段集合不等价，不能通过重命名或直接删表解决。

## 2. 决策

唯一主模型确定为：

```text
dm_target_table(table_category = 'INTERMEDIATE')
        1 ─────── N
dm_target_table_field
```

`dm_intermediate_table` 仅作为待清理的旧实现：

1. 当前为测试节点，不考虑旧表存量迁移；迁移脚本只做空表断言。
2. 应用在同一发布版本内停止对旧表的读写；中间表菜单统一使用现有 `TargetTableService` 与 `/target-tables`/字段接口，参数固定为 `tableCategory=INTERMEDIATE`。
3. V160 在删除前断言旧表为空，断言通过后执行 `DROP TABLE dm_intermediate_table`；存在数据时 fail-closed。
4. V160 完成后数据库和应用只保留目标表/字段表，不再引入旧表兼容路径。

## 3. 方案比较

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| A. 规范化主模型、空表断言后清理（推荐） | 迁移到 `dm_target_table` + 字段表，应用立即收敛，V160 仅允许清理空旧表 | 测试节点无需搬迁数据，最终模型明确且无静默丢失 |
| B. 长期双写/双读 | 两套表长期同步，定时比对 | 兼容成本和漂移风险持续增加，无法消除双模型 |
| C. 未断言直接删除旧表 | 先删 `dm_intermediate_table`，不检查存量 | 可能造成数据丢失，禁止 |

## 4. 数据迁移契约

追加迁移使用 V160，并在删除旧表前执行断言：

- `dm_intermediate_table` 行数必须为 0；否则 `SIGNAL` 阻断迁移。
- 断言在 `DROP TABLE` 前执行，断言失败不删除任何数据。
- V160 不复制存量数据，不更新平台表，不连接生产库。

任何断言失败都必须在删除前终止；测试节点清空旧表后可重试。

## 5. 接口与菜单收敛

- 保留基础资料菜单 `/data-migration/base/intermediate-tables` 的用户入口，但页面实现统一为 `TargetTablesPage`，通过 `category=INTERMEDIATE` 调用目标表及字段 API。
- 移除 `StructuredAssetService`、`ContentAssetTables`、`ExcelService` 的 `INTERMEDIATE_TABLE` 分支；结构化内容接口仅保留 RULE/PARAMETER。
- `/structured/INTERMEDIATE_TABLE`、`/structured/INTERMEDIATE_TABLE/import|export` 不再作为中间表写入入口，返回明确的 `BAD_REQUEST`/废弃语义；不读取旧表。
- 回收站、看板、关联校验和导出使用 `dm_target_table`/`dm_target_table_field` 的 `INTERMEDIATE` 条件，不再把旧表计入内容资产统计。
- V160 完成后旧表不存在，不对业务用户暴露旧模型。

## 6. 发布、回退与风险

发布顺序：

1. 发布应用收敛版本，停止旧表读写。
2. 发布 V160，确认旧表为空后执行清理。

应用回退只能恢复代码版本；V160 为追加迁移，不提供反向 Flyway，测试库回退通过重建数据库完成。若断言失败，迁移停止且不删表。

主要风险是旧 JSON 字段缺失、编号冲突和字段重复；通过前置断言、幂等键和迁移测试 fail-closed 控制。

## 7. 验收

- 新迁移在旧表为空 fixture 下成功，在存在任意旧数据 fixture 下阻断。
- data-migration 后端不再出现 `dm_intermediate_table` SQL 或表名常量。
- 前端中间表菜单使用 `TargetTablesPage`，不存在 `StructuredListView` 的 `INTERMEDIATE_TABLE` 分支。
- 模块测试、全量治理、Flyway 检查和前端构建通过。

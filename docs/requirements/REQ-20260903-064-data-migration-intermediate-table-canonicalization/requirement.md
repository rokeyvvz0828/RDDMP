---
id: REQ-20260903-064
status: ready
owner: rokeyvvz0828
module: business/data-migration
source_issue: database-schema-and-relations.md P1
architecture_decision: ADR-DM-20260903-01
merged_into: REQ-20260820-031
---

# 数据迁移中间表模型唯一化

> 合并状态：2026-09-05 经用户确认，本需求已作为数据迁移资产库 V3 的模型治理增量并入 REQ-20260820-031。本文、原 scope 和独立控制账本只保留为历史证据；后续活动范围、验证和提交判断以 REQ-20260820-031 为准。

## 业务目标

消除数据迁移模块中内容侧 `dm_intermediate_table` 与基础资料侧
`dm_target_table(table_category='INTERMEDIATE')` + `dm_target_table_field` 的双模型，统一中间表结构的存储、接口、菜单和统计事实源。当前为测试节点，无需迁移旧表存量；通过空表断言保证清理不丢数据。

## 必须需求

- **R1 唯一主模型**：中间表业务唯一使用 `dm_target_table(table_category='INTERMEDIATE')` 与 `dm_target_table_field`；表信息和字段明细均通过目标表服务维护。
- **R2 旧表清理**：追加 Flyway 迁移在删除前断言 `dm_intermediate_table` 为空；断言通过后删除旧表，存在数据时 fail-closed，禁止静默丢失。
- **R3 接口收敛**：应用停止读写 `dm_intermediate_table`；基础资料中间表菜单、列表、字段编辑、导入导出、回收站、看板和关联校验全部按 `table_category='INTERMEDIATE'` 走目标表/字段接口。
- **R4 最终模型**：本需求完成后不保留 `dm_intermediate_table`；数据库和应用只使用目标表/字段表。
- **R5 租户与权限**：迁移和新接口继续执行租户、项目可达性、RBAC、实体授权和审计约束，不扩大 `platform/system` 或其他模块边界。

## 数据策略

当前为测试节点，不迁移 `dm_intermediate_table` 存量。V169 只允许在旧表为空时删除旧表；若发现任何数据，迁移失败并要求先人工处理测试数据。

## 非目标

- 本需求删除 `dm_intermediate_table`，但不得在未断言为空的情况下直接删除。
- 本需求不修改已发布 V88、V162、V163 或其他历史迁移。
- 本需求不修改 `pm_project`、`pm_project_member`、平台成员和项目权限模型。
- 本需求不新增动态表单元数据依赖，不改造无关内容资产模型。

## 不变量

- 旧表存在任意记录时，迁移在删除前失败并保留旧数据。
- 应用收敛后，业务查询结果不得来自旧表；旧表不得产生新写入。
- 所有表结构操作保留 `tenant_id` 与项目授权条件；字段不能跨表、跨项目挂接。
- Flyway 只追加；不得通过手工 SQL 绕过空表断言。

## 验收标准

1. V169 在旧表为空 fixture 上成功，在存在任意旧数据 fixture 上于删除前失败。
2. V169 成功后 `dm_intermediate_table` 不存在，目标主表/字段表和接口仍可用。
3. `server/src/modules/data-migration` 生产代码和前端中间表页面不再引用 `dm_intermediate_table`；结构化通用接口仅支持 RULE/PARAMETER。
4. 中间表菜单仍可访问，使用 `TargetTableService` 的 `INTERMEDIATE` 类别和字段表接口，权限码保持现有 `table-fields-intermediate*`。
5. `mvn -pl :ccb-data-migration -am test`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、Flyway 检查和差异检查通过。
6. 仓库无旧表重建或应用读写路径；测试库重建是唯一回退方式。

## 回滚

应用回滚只能恢复代码版本，不能恢复已删除旧表；测试库回退通过重建数据库完成，不执行反向 Flyway。

# 数据迁移「迁移参数」字段信息子表与批量导入优化设计

> 需求编号：REQ-20260910-069；状态：已确认（用户 2026-09-10 采纳推荐设计）；模块：business/data-migration。

## 1. 目标与范围

为「迁移参数」（菜单 752）增加字段信息子表，记录每个参数的字段英文名、字段中文名、字段类型、字段长度与
字段说明；同一参数内字段英文名、字段中文名均不允许重复。前端在参数新增流程支持批量添加字段，并提供独立的
字段维护入口；批量导入参数时可在同一 Excel 中同时导入参数及对应字段。

非目标：不改系统管理/参数管理页面、不改其他内容类型（方案/检核规则/映射/依赖关系）、不新增公共组件与样式、
不改参数导出（保持参数级八列）。

## 2. 用户流程

1. 用户在迁移参数列表点击「新增」→ 填写参数信息并批量添加 0..N 行字段（英文名/中文名/类型/长度/说明）→
   保存后参数与字段一并创建。
2. 用户点击列表行「字段」→ 字段抽屉展示该参数字段清单 → 可批量新增、行编辑、删除、批量删除。
3. 用户在「导入」抽屉下载十一列模板 → 每行一个字段、参数信息整组重复填写 → 上传后参数与字段逐组创建，
   失败行跳过并逐行报错。
4. 参数删除后字段随参数一起隐藏；回收站恢复时字段一并恢复；彻底删除时字段级联删除。

## 3. 数据模型

新增 `dm_parameter_field`（V200，幂等）：`id`、`tenant_id`、`parameter_id`（关联 dm_parameter）、
`field_name_en`(128)、`field_name_cn`(128)、`field_type`(64 码值)、`field_length`(INT 空)、
`field_description`(500 空)、`sort_no`(INT 默认 0)、`owner_id`、审计列、`deleted`。
唯一键：`uk_dm_parameter_field_en (tenant_id, parameter_id, field_name_en, deleted)` 与
`uk_dm_parameter_field_cn (tenant_id, parameter_id, field_name_cn, deleted)`（软删行不占用名称）。
查询索引：`idx_dm_parameter_field_parameter (tenant_id, parameter_id, deleted, sort_no)`。
码值：`DM_PARAMETER_FIELD_TYPE`（VARCHAR/NUMBER/DATE/DATETIME/TEXT/CLOB/BLOB），经
`DataMigrationCodeValueService` 维护并校验启用状态。

## 4. 后端接口

全部挂在 `/api/data-migration/parameters`，沿用现有参数查看/写权限码，不新增权限节点：

- `GET /{id}/fields`：字段列表（含类型码值 label）。
- `POST /{id}/fields`：批量新增（JSON 数组），逐条校验并在同一事务写入。
- `PUT /{id}/fields/{fieldId}`：单条编辑。
- `DELETE /{id}/fields/{fieldId}`：单条逻辑删除。
- `POST /{id}/fields/batch-delete`：批量逻辑删除（JSON 数组）。
- `POST /api/data-migration/parameters`：新增参数，body 可携带可选 `fields` 数组一并创建。
- `GET /{id}`（详情）：响应增加 `fields` 数组与 `field_count`。
- 列表：响应行增加 `field_count`。

写操作服务端执行租户/项目范围、记录 Owner/管理员校验与 `PARAMETER` 审计（`FIELD_CREATE/FIELD_UPDATE/
FIELD_DELETE`，`entity_id` 记参数 id）。

## 5. 校验规则

- 字段英文名：必填、无空格、仅字母数字下划线、≤128；同参数内不区分大小写唯一。
- 字段中文名：必填、无空格、≤128；同参数内不区分大小写唯一。
- 字段类型：必填，必须是启用中的 `DM_PARAMETER_FIELD_TYPE` 码值。
- 字段长度：可选，提供时必须为 1..2^31-1 的整数。
- 字段说明：可选，≤500。
- 同参数内中英文名重复：应用校验 + 数据库唯一键双层拒绝。

## 6. Excel 导入

模板列扩展为：参数类型、参数范围分类、系统编号、参数名称、参数英文名、参数说明、字段英文名、字段中文名、
字段类型、字段长度、字段说明。行语义：

- 以（系统编号 + 参数名称）作为参数组键，连续相同键的行归为一个参数组；组首行解析并创建参数，后续行累加字段。
- 字段列全空 → 纯参数行（兼容原六列文件）；整行全空 → 跳过。
- 组内字段中英文名唯一；与组首行参数信息不一致的非空列按该行报错。
- 行数上限 5000、文件上限 50MB 沿用现有规则；失败行跳过，返回总行/成功/失败与逐行错误。

## 7. 前端

- 新增参数抽屉：参数表单下方增加「字段信息」批量区，可添加/移除多行（英文名、中文名、类型下拉、长度、说明），
  保存时随 `fields` 提交。
- 列表：操作列新增「字段」入口；表格增加「字段数」列；移动卡片展示字段数并保留「字段」操作。
- 字段抽屉：字段表格（英文名/中文名/类型/长度/说明/操作）+「批量新增」动态行区 + 行编辑 + 批量删除，
  复用 `UiDataTable`/`UiFormDrawer`/`UiEmptyState` 与移动卡片无横向溢出约束。
- 详情弹层：增加字段清单区块。
- 导入弹层/模板：文案更新为十一列说明。

## 8. 验证策略

- 迁移：Testcontainers MySQL 8.4 跑 V200 两遍验证幂等、唯一键与码值。
- 服务：ParameterServiceTest 扩展字段唯一/批量/导入/级联场景。
- 前端：`npm --prefix web run build`；浏览器验收桌面与 375/390/430 视口。
- 治理：`check-all-governance.mjs` + 任务范围检查 + `git diff --check`。

## 9. 主要风险

- 工作区含 REQ-067/068 未提交改动：调用方文件（ParameterService、ParametersPage.vue、data-migration.ts）
  差异需保护，顺序开发、不覆盖他人改动。
- V200 唯一键含 `deleted` 保证软删释放名称；应用层仍做组内/库内重复预检，避免依赖唯一键兜底产生难懂报错。

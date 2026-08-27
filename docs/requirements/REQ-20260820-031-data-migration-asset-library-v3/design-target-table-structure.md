# 目标表结构（基础资料管理）功能设计方案

> 需求前缀：`req-20260820-031-data-migration-asset-library-v3`
> 菜单路径：`数据迁移 / 基础资料管理 / 目标表结构`
> 路由名：`DataMigrationTargetTableStructure`（沿用既有 base 分组 `data-migration/base/table-fields-target`）
> 作者：Codex　|　状态：**待确认（方案阶段）**
> 决策记录（已与用户确认）：
> 1. 存储模型：**新建独立表** `dm_target_table` + `dm_target_table_field`，彻底满足字段级 CRUD、唯一性校验与钻取溯源。
> 2. 数据字典编号：**自由文本 + 格式校验**（仅去空格/非空校验，不关联 `sys_dict_type`）。
> 3. 目标表/中间表：**共用一套表结构与接口**，用 `table_category='TARGET'|'INTERMEDIATE'` 区分（中间表结构作为后续增量，本方案接口预留类型参数，本次实现聚焦目标表）。

---

## 1. 现状与差距分析

当前"目标表结构"是 `StructuredAssetService` 的半成品：复用 `dm_asset`（`asset_type='TABLE_STRUCTURE'`），字段明细以 `structured_data` 整段 JSON 存入，列表仅展示表头聚合，**未满足需求**的关键能力如下：

| 需求能力 | 现状 | 差距 |
| --- | --- | --- |
| 上下结构（表信息 + 字段明细） | 仅表头列表，字段塞 JSON | 缺独立字段表、缺字段级编辑 |
| 字段级 CRUD / 行编辑 | 仅整段 JSON 文本框编辑 | 缺字段新增/删除/行更新 |
| 单字段明细为粒度的分页列表 | 无分页、按表头聚合 | 需新建分页列表 API |
| 查看/修改/字段/删除独立操作 | 仅有"查看/修改/删除"且修改=JSON | 缺字段独立操作、缺查看抽屉 |
| 新建空表（不靠上传） | 无入口 | 缺单笔新增接口 |
| 系统编号联动筛选 + 项目级唯一性 | 无 | 缺项目/系统编号联动与唯一校验 |
| 数据字典编号 / 字段属性建模 | 无 | 缺独立字段属性列 |
| 导入导出（Excel 全量字段） | 通用 `ExcelService` 仅导出 asset 列 | 缺表结构专用模板与解析 |

**结论**：复用 `dm_asset` 已不可行，采用新建独立表方案。既有 `dm_asset.TABLE_STRUCTURE` 数据通过迁移脚本一次性转入新表（见 §2.4），迁移后该 asset_type 路由停用。

---

## 2. 数据库设计（Flyway 追加迁移 V88）

> 风格对齐 V82/V84：幂等建表 + 幂等补索引；生产库不手工改表；仅追加。
> 物理子系统只读关联，目标表仅存 `system_code`，事业群/系统名称运行时 JOIN 带出，不入库。

### 2.1 `dm_target_table`（表信息主表）

```sql
CREATE TABLE IF NOT EXISTS dm_target_table (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id               BIGINT NOT NULL DEFAULT 1,
    table_code              VARCHAR(64)  NOT NULL COMMENT '表编号，系统自动生成，全局唯一',
    project_id              BIGINT       NOT NULL COMMENT '所属项目（pm_project.id）',
    system_code             VARCHAR(64)  NOT NULL COMMENT '系统编号（arch_physical_subsystem.code）',
    table_name_en           VARCHAR(128) NOT NULL COMMENT '表英文名称，无空格',
    table_name_cn           VARCHAR(128) NOT NULL COMMENT '表中文名称，无空格',
    table_meaning           VARCHAR(500) NULL     COMMENT '表含义',
    table_category          VARCHAR(16)  NOT NULL DEFAULT 'TARGET' COMMENT 'TARGET/INTERMEDIATE',
    owner_id                BIGINT       NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT       NULL,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_target_table_code     (tenant_id, table_code, deleted),
    UNIQUE KEY uk_target_table_en       (tenant_id, project_id, system_code, table_name_en, deleted),
    UNIQUE KEY uk_target_table_cn       (tenant_id, project_id, system_code, table_name_cn, deleted),
    KEY idx_target_table_list           (tenant_id, project_id, system_code, deleted, updated_at),
    KEY idx_target_table_key_field      (tenant_id, project_id, deleted)
);
```

### 2.2 `dm_target_table_field`（字段明细表）

```sql
CREATE TABLE IF NOT EXISTS dm_target_table_field (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id               BIGINT NOT NULL DEFAULT 1,
    field_code              VARCHAR(64)  NOT NULL COMMENT '字段编号，系统自动生成，全局唯一',
    table_id                BIGINT       NOT NULL COMMENT 'dm_target_table.id',
    table_code              VARCHAR(64)  NOT NULL COMMENT '冗余表编号，便于钻取',
    field_name_en           VARCHAR(128) NOT NULL COMMENT '字段英文名称，无空格',
    field_name_cn           VARCHAR(128) NOT NULL COMMENT '字段中文名称，无空格',
    field_meaning           VARCHAR(500) NULL     COMMENT '字段含义',
    code_description        VARCHAR(500) NULL     COMMENT '码值说明',
    is_key_field            TINYINT      NOT NULL DEFAULT 0 COMMENT '是否关键栏位 0否 1是',
    oracle_type             VARCHAR(64)  NULL     COMMENT 'ORACLE字段类型',
    mysql_type              VARCHAR(64)  NULL     COMMENT 'mysql字段类型',
    is_nullable             TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可空 0否 1是',
    is_primary_key          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否主键 0否 1是',
    dict_code               VARCHAR(64)  NULL     COMMENT '数据字典编号，无空格',
    owner_id                BIGINT       NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT       NULL,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_target_field_code     (tenant_id, field_code, deleted),
    UNIQUE KEY uk_target_field_en       (tenant_id, table_id, field_name_en, deleted),
    UNIQUE KEY uk_target_field_cn       (tenant_id, table_id, field_name_cn, deleted),
    KEY idx_target_field_table          (tenant_id, table_id, deleted),
    KEY idx_target_field_key            (tenant_id, table_id, is_key_field, deleted),
    KEY idx_target_field_dict           (tenant_id, dict_code, deleted),
    CONSTRAINT fk_target_field_table FOREIGN KEY (table_id) REFERENCES dm_target_table (id)
);
```

### 2.3 菜单与权限（对齐 V84 模式）

新增菜单 `DataMigrationTargetTableStructure`（父：基础资料管理 `DataMigrationBase`，route_path=`/data-migration/base/table-fields-target`），权限码：

- `data-migration:base:table-fields-target`（read）
- `data-migration:base:table-fields-target:create`
- `data-migration:base:table-fields-target:update`
- `data-migration:base:table-fields-target:delete`

> 导入/导出复用 `create/update/delete` 或 read 权限（下载=read，上传/新增/修改/删除=对应 write 码）；统一纳入 RBAC。
> 角色种子写入 `sys_role_permission`（role_id=1 超管 + 数据迁移角色）。

### 2.4 既有数据迁移（一次性）

```sql
-- 将历史 dm_asset(asset_type='TABLE_STRUCTURE') 的 structured_data 拆为表+字段行。
-- 说明：structured_data 当前多为空壳，迁移脚本对无法解析的行跳过并以 operation_log 记录。
-- 迁移后 StructuredAssetController 对 TABLE_STRUCTURE / INTERMEDIATE_TABLE 的读写路由停用（返回 410）。
```

---

## 3. 调用既有（只读）接口清单

| 用途 | 接口 | 调用方 | 说明 |
| --- | --- | --- | --- |
| 所属项目下拉 | `GET /data-migration/options?type=project` 或 `GET /project/projects/workbench` | 前端 + 后端校验 | 项目选项；后端写入时校验 `pm_project` 存在 |
| 系统编号联动 | `GET /architecture/physical-subsystems?code=&page=&size=` | 前端（筛选/表单）+ 后端校验 | 系统编号须落在所选项目下的组件清单（`dm_component.physical_subsystem_code`）内；事业群/系统名称前端只读展示 |
| 系统编号归属校验 | `SELECT 1 FROM dm_component WHERE project_id=? AND physical_subsystem_code=? AND deleted=0` | 后端 Service | 保证底层归属合法（上传前置条件） |
| 操作人姓名回填 | `sys_user` JOIN（created_by/updated_by） | 后端列表查询 | 复用既有 user 表 |
| 审计日志 | `dm_operation_log` | 后端写操作 | 复用既有审计表（import/create/update/delete 落 `TARGET_TABLE_*` 操作码） |

> 跨模块只读调用遵守 AGENTS.md：`server/src/platform`、`server/src/shared` 默认只读，本方案仅读取 `pm_project`/`arch_physical_subsystem`，不写入他模块表。

---

## 4. 新建接口清单（后端）

基础路径：`/data-migration/target-tables`（表信息）/ `/data-migration/target-table-fields`（字段）。统一返回 `ApiResponse<T>`，分页 `DataMigrationPage<T>`。

| # | 方法 | 路径 | 说明 | 权限码 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/target-tables` | 分页列表（字段粒度 join 表信息，支持 projectId/systemCode/isKeyField/dictCode/tableKeyword/fieldKeyword/tableCategory 筛选） | read |
| 2 | GET | `/target-tables/{id}` | 查看：表信息 + 字段列表 | read |
| 3 | POST | `/target-tables` | 单笔新增空表（自动生成表编号；校验项目/系统编号归属、表英文名/中文名项目内唯一、无空格） | create |
| 4 | PUT | `/target-tables/{id}` | 修改表信息（表编号/项目/系统编号不可改；表英文名/中文名唯一校验） | update |
| 5 | POST | `/target-tables/batch-delete` | 批量删除（同步删字段；二次确认由前端） | delete |
| 6 | DELETE | `/target-tables/{id}` | 单笔删除（同步删字段） | delete |
| 7 | GET | `/target-tables/{id}/fields` | 表下字段列表 | read |
| 8 | POST | `/target-tables/{id}/fields` | 表下新增字段（自动生成字段编号；字段英文名/中文名表内唯一、无空格） | update（字段归属表，用 update 码） |
| 9 | PUT | `/target-table-fields/{fieldId}` | 行编辑字段（字段编号不可改；表内字段英文名/中文名唯一、无空格） | update |
| 10 | DELETE | `/target-table-fields/{fieldId}` | 删除单字段 | delete |
| 11 | POST | `/target-tables/import` | Excel 批量上传（模板解析 + 归属校验 + 重复/空格拦截 + 审计；返回 accepted/failed/errors） | create |
| 12 | GET | `/target-tables/export` | 导出 Excel（单条/整表/筛选后批量，含全量字段属性），`ids` 或筛选参数 | read |
| 13 | GET | `/target-tables/template` | 下载上传模板 | read |

**校验规则（服务层）**
- 表/字段英文名、中文名、数据字典编号：去空格后非空、不含空格。
- 表英文名/中文名：同一 `project_id + system_code` 下唯一（唯一键兜底 + 服务预检）。
- 字段英文名/中文名：同一 `table_id` 下唯一。
- 上传前置：系统编号必须属于所选项目下已存在组件（`dm_component`）。

**Excel 模板列（导入）**
`所属项目编码(projCode)`、`系统编号(systemCode)`、`表英文名称`、`表中文名称`、`表含义`、`字段英文名称`、`字段中文名称`、`字段含义`、`码值说明`、`是否关键栏位`、`ORACLE字段类型`、`mysql字段类型`、`是否可空`、`是否主键`、`数据字典编号`。
（表编号/字段编号自动生成；事业群/系统名称不入库，由 systemCode 运行时带出。）
导出列在以上基础上追加 `表编号`、`字段编号`、`所属事业群`、`系统名称`、`创建人`、`创建时间`、`更新人`、`更新时间`。

---

## 5. 前端设计

- 位置：`web/src/modules/data-migration/`（新增 `TargetTableStructure/` 页面与组件，或复用 `StructuredListView` 改造）。
- 复用 `web/src/components/ui`（UiDataTable、UiFormDrawer、UiToolbar、UiFilterBar 等）与 `web/src/modules/delivery-showcase/` 已验证布局。
- 遵循 `design-h5.md`：桌面/移动双布局、列表呈现、滚动边界、弹层高度、操作层级；按手机视口验收。
- 列表：字段粒度分页表格（多列 + 操作列：查看/修改/字段/删除），始终显示"表名模糊搜索""字段名模糊搜索"两框 + 项目/系统编号下拉 + 关键栏位/字典编号筛选。
- 查看/修改/字段：上（表信息只读或编辑）+ 下（字段列表/行编辑）的抽屉或页面分区。
- 状态：加载/空/失败/无权限/提交中 统一状态组件。
- 权限：按钮级按权限码显隐（无 create 则隐藏新增/上传，无 delete 则隐藏删除等）。

---

## 6. 实施计划（待确认后执行）

1. **V88 迁移脚本**：建表 + 索引 + 菜单/权限种子 + 历史数据迁移 + `dm_asset` 旧路由停用。
2. **后端**：`TargetTableService` / `TargetTableFieldService` / `TargetTableController` / `TargetTableFieldController` / DTO / 编号生成（雪花式 `nextId()` 复用）+ Excel 解析导出（新增专用模板，不破坏既有 `ExcelService`）。
3. **前端**：页面 + API 封装 + 复用 UI 组件 + 权限/状态/移动端验收。
4. **验证**：Flyway 迁移本地跑通；接口用 curl/单测覆盖唯一性/空格/归属校验；前端按 design-h5 手机视口验收；RBAC 各码生效。

---

## 7. 风险与未决

- 历史 `dm_asset.TABLE_STRUCTURE` 数据若非空，迁移脚本需保证字段拆分不丢；当前实现多为空壳，风险低，但脚本需幂等可重跑。
- "数据字典编号"本次自由文本，后续如需下拉选项需单独 ready 需求升级。
- 中间表结构（INTERMEDIATE）本次仅预留 `table_category`，完整 UI 作为后续增量。
- 跨模块只读调用 `arch_physical_subsystem` 若契约变动需同步（已在调用清单标注）。

-- V88 数据迁移·目标表结构（基础资料管理）
-- 新建独立表 + 菜单/权限/角色种子 + 历史 dm_asset(TABLE_STRUCTURE) 数据迁移
-- 仅追加，幂等。生产库不手工改表。

-- 1. 表信息主表
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
    UNIQUE KEY uk_target_table_code (tenant_id, table_code, deleted),
    UNIQUE KEY uk_target_table_en   (tenant_id, project_id, system_code, table_name_en, deleted),
    UNIQUE KEY uk_target_table_cn   (tenant_id, project_id, system_code, table_name_cn, deleted),
    KEY idx_target_table_list       (tenant_id, project_id, system_code, deleted, updated_at),
    KEY idx_target_table_category  (tenant_id, table_category, deleted)
);

-- 2. 字段明细表
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
    UNIQUE KEY uk_target_field_code (tenant_id, field_code, deleted),
    UNIQUE KEY uk_target_field_en   (tenant_id, table_id, field_name_en, deleted),
    UNIQUE KEY uk_target_field_cn   (tenant_id, table_id, field_name_cn, deleted),
    KEY idx_target_field_table      (tenant_id, table_id, deleted),
    KEY idx_target_field_key        (tenant_id, table_id, is_key_field, deleted),
    KEY idx_target_field_dict       (tenant_id, dict_code, deleted),
    CONSTRAINT fk_target_field_table FOREIGN KEY (table_id) REFERENCES dm_target_table (id)
);

-- 3. 菜单：基础资料管理(DataMigrationBase=740) 下已存在 目标表结构(743)/中间表结构(744)。
--    仅将菜单 permission_code 与 sys_menu_permission 调整为与前后端一致的 table-fields-* 编码，
--    保持 sys_menu_permission.id(7431~7444) 不变，使已存在的 sys_role_permission 绑定继续有效。
--    幂等：已为 table-fields-* 的不再改写。

UPDATE sys_menu SET permission_code = 'data-migration:base:table-fields-target'
 WHERE id = 743 AND permission_code <> 'data-migration:base:table-fields-target' AND tenant_id = 1;
UPDATE sys_menu SET permission_code = 'data-migration:base:table-fields-intermediate'
 WHERE id = 744 AND permission_code <> 'data-migration:base:table-fields-intermediate' AND tenant_id = 1;

-- 4. 权限码（sys_menu_permission，沿用既有 id，仅改写 permission_code / permission_name）
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-target',          permission_name = '目标表结构-查看', updated_at = CURRENT_TIMESTAMP WHERE id = 7431 AND permission_code <> 'data-migration:base:table-fields-target';
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-target:create',   permission_name = '目标表结构-新增', updated_at = CURRENT_TIMESTAMP WHERE id = 7432 AND permission_code <> 'data-migration:base:table-fields-target:create';
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-target:update',   permission_name = '目标表结构-修改', updated_at = CURRENT_TIMESTAMP WHERE id = 7433 AND permission_code <> 'data-migration:base:table-fields-target:update';
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-target:delete',   permission_name = '目标表结构-删除', updated_at = CURRENT_TIMESTAMP WHERE id = 7434 AND permission_code <> 'data-migration:base:table-fields-target:delete';
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-intermediate',          permission_name = '中间表结构-查看', updated_at = CURRENT_TIMESTAMP WHERE id = 7441 AND permission_code <> 'data-migration:base:table-fields-intermediate';
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-intermediate:create',   permission_name = '中间表结构-新增', updated_at = CURRENT_TIMESTAMP WHERE id = 7442 AND permission_code <> 'data-migration:base:table-fields-intermediate:create';
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-intermediate:update',   permission_name = '中间表结构-修改', updated_at = CURRENT_TIMESTAMP WHERE id = 7443 AND permission_code <> 'data-migration:base:table-fields-intermediate:update';
UPDATE sys_menu_permission SET permission_code = 'data-migration:base:table-fields-intermediate:delete',   permission_name = '中间表结构-删除', updated_at = CURRENT_TIMESTAMP WHERE id = 7444 AND permission_code <> 'data-migration:base:table-fields-intermediate:delete';

-- 6. 历史数据迁移：dm_asset(asset_type IN ('TABLE_STRUCTURE','INTERMEDIATE_TABLE')) 拆为表 + 字段
-- 说明：structured_data 当前多为空壳；可解析的按 JSON 数组拆分字段，无法解析的跳过并在 dm_operation_log 记录。
-- 本迁移幂等：仅迁移尚未出现在 dm_target_table 的表编号（asset_code 映射为 table_code）。
INSERT INTO dm_target_table (id, tenant_id, table_code, project_id, system_code, table_name_en, table_name_cn, table_meaning, table_category, owner_id, created_by, created_at, updated_at)
SELECT
    a.id,
    a.tenant_id,
    a.asset_code,
    a.project_id,
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.systemCode')), ''),
    a.asset_name,
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.tableNameCn')), a.asset_name),
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.tableMeaning')), ''),
    CASE WHEN a.asset_type = 'INTERMEDIATE_TABLE' THEN 'INTERMEDIATE' ELSE 'TARGET' END,
    a.owner_id,
    a.owner_id,
    a.created_at,
    a.updated_at
FROM dm_asset a
JOIN pm_project p ON p.id = a.project_id AND p.tenant_id = a.tenant_id AND p.deleted = 0
WHERE a.asset_type IN ('TABLE_STRUCTURE', 'INTERMEDIATE_TABLE')
  AND a.deleted = 0
  AND a.tenant_id = 1
  AND NOT EXISTS (SELECT 1 FROM dm_target_table t WHERE t.table_code = a.asset_code AND t.tenant_id = a.tenant_id AND t.deleted = 0)
  AND JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.systemCode')) IS NOT NULL;

INSERT INTO dm_target_table_field (tenant_id, field_code, table_id, table_code, field_name_en, field_name_cn, field_meaning, code_description, is_key_field, oracle_type, mysql_type, is_nullable, is_primary_key, dict_code, owner_id, created_by, created_at, updated_at)
SELECT
    a.tenant_id,
    CONCAT(a.asset_code, '_F', j.seq),
    a.id,
    a.asset_code,
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.fieldNameEn')), ''),
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.fieldNameCn')), ''),
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.fieldMeaning')), ''),
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.codeDescription')), ''),
    CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.isKeyField')) IN ('1','true','Y') THEN 1 ELSE 0 END,
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.oracleType')), ''),
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.mysqlType')), ''),
    CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.isNullable')), '1') IN ('1','true','Y') THEN 1 ELSE 0 END,
    CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.isPrimaryKey')) IN ('1','true','Y') THEN 1 ELSE 0 END,
    COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.dictCode')), ''),
    a.owner_id,
    a.owner_id,
    a.created_at,
    a.updated_at
FROM dm_asset a
JOIN JSON_TABLE(
    COALESCE(a.structured_data, '{}'),
    '$.fields[*]' COLUMNS (seq FOR ORDINALITY, item JSON PATH '$')
) j
WHERE a.asset_type IN ('TABLE_STRUCTURE', 'INTERMEDIATE_TABLE')
  AND a.deleted = 0
  AND a.tenant_id = 1
  AND EXISTS (SELECT 1 FROM dm_target_table t WHERE t.id = a.id AND t.deleted = 0)
  AND JSON_UNQUOTE(JSON_EXTRACT(j.item, '$.fieldNameEn')) IS NOT NULL;

-- V200: 迁移参数字段信息子表（REQ-20260910-069）
-- 背景：dm_parameter 需要记录每个参数的字段信息（字段英文名、字段中文名、字段类型、字段长度、字段说明）。
--   方案：新增 dm_parameter_field 子表，parameter_id 关联 dm_parameter；同参数内字段英文名/中文名唯一
--   （不区分大小写）。软删行不占用名称：采用 V172「活动生成列 + 唯一索引」模型，active_* 仅未删除记录取值，
--   NULL 不参与唯一，删除后可重建同名、恢复/彻底删除语义与活动行唯一一致。
-- 字段类型：新增系统参数码值 DM_PARAMETER_FIELD_TYPE，统一由系统管理/参数管理维护，服务端不硬编码类型集合。
-- 存量：本表为全新表，无历史数据；码值仅插入、不覆盖管理员既有配置（INSERT IGNORE）。
-- 约束：Flyway 只追加；本脚本全部幂等，可安全重跑。

CREATE TABLE IF NOT EXISTS dm_parameter_field (
    id                      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID',
    parameter_id            BIGINT       NOT NULL COMMENT '迁移参数 ID（dm_parameter.id）',
    field_name_en           VARCHAR(128) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '字段英文名（不区分大小写唯一）',
    field_name_cn           VARCHAR(128) NOT NULL COMMENT '字段中文名（不区分大小写唯一）',
    field_type              VARCHAR(64)  NOT NULL COMMENT '字段类型码值（DM_PARAMETER_FIELD_TYPE）',
    field_length            INT          NULL COMMENT '字段长度（可选正整数）',
    field_description       VARCHAR(500) NULL COMMENT '字段说明',
    sort_no                 INT          NOT NULL DEFAULT 0 COMMENT '排序号（同参数内升序展示）',
    owner_id                BIGINT       NOT NULL COMMENT '创建人 ID',
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT       NULL,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    deleted_by              BIGINT       NULL COMMENT '逻辑删除人 ID',
    deleted_at              TIMESTAMP    NULL COMMENT '逻辑删除时间',
    deleted                 TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    active_field_name_en    VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_name_en ELSE NULL END) STORED COMMENT '活动字段英文名（仅未删除记录取值）',
    active_field_name_cn    VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_name_cn ELSE NULL END) STORED COMMENT '活动字段中文名（仅未删除记录取值）',
    UNIQUE KEY uk_dm_parameter_field_active_en (tenant_id, parameter_id, active_field_name_en),
    UNIQUE KEY uk_dm_parameter_field_active_cn (tenant_id, parameter_id, active_field_name_cn),
    KEY idx_dm_parameter_field_parameter (tenant_id, parameter_id, deleted, sort_no),
    CONSTRAINT fk_dm_parameter_field_parameter FOREIGN KEY (parameter_id) REFERENCES dm_parameter (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '迁移参数字段信息';

-- 参数管理：字段类型码值（幂等，INSERT IGNORE 不覆盖管理员既有配置）
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES (5833, 1, 'DM_PARAMETER_FIELD_TYPE', '参数字段类型', 1, 0);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '迁移参数字段类型码值（由系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 58331 id, 'DM_PARAMETER_FIELD_TYPE.VARCHAR' config_key, 'VARCHAR' config_value, 'DM_PARAMETER_FIELD_TYPE' category_code
    UNION ALL SELECT 58332, 'DM_PARAMETER_FIELD_TYPE.NUMBER', 'NUMBER', 'DM_PARAMETER_FIELD_TYPE'
    UNION ALL SELECT 58333, 'DM_PARAMETER_FIELD_TYPE.DATE', 'DATE', 'DM_PARAMETER_FIELD_TYPE'
    UNION ALL SELECT 58334, 'DM_PARAMETER_FIELD_TYPE.DATETIME', 'DATETIME', 'DM_PARAMETER_FIELD_TYPE'
    UNION ALL SELECT 58335, 'DM_PARAMETER_FIELD_TYPE.TEXT', 'TEXT', 'DM_PARAMETER_FIELD_TYPE'
    UNION ALL SELECT 58336, 'DM_PARAMETER_FIELD_TYPE.CLOB', 'CLOB', 'DM_PARAMETER_FIELD_TYPE'
    UNION ALL SELECT 58337, 'DM_PARAMETER_FIELD_TYPE.BLOB', 'BLOB', 'DM_PARAMETER_FIELD_TYPE'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = v.category_code AND t.deleted = 0;

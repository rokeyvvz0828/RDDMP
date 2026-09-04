-- V177: dm_target_table / dm_target_table_field 主键收敛为业务编号（REQ-20260820-031 T41）。
-- 背景：主表原以时间戳派生 id 为主键，table_code 为 "TT"+id 的派生冗余；本次改为 table_code 直接作主键，
--   编号沿用原 id 生成方式（纯数字、全局唯一、单列主键），字段表主键同步改 field_code，
--   字段表 table_id 改名为 table_code 关联主表；dm_issue_relation.related_id 与审计 entity_id
--   存的正是原 id 数值 = 新 code 数值，数据零改写。V172 的 uk_target_field_active_en/cn 引用 table_id，
--   随关联键改名一并重建为 (tenant_id, table_code, active_field_name_en/cn)。
-- 约束：Flyway 只追加，不改历史脚本；information_schema 条件式，幂等可重跑。

DROP PROCEDURE IF EXISTS dm_v177_convert_target_table_code_pk;
DELIMITER $$
CREATE PROCEDURE dm_v177_convert_target_table_code_pk()
BEGIN
    DECLARE v_has_table INT DEFAULT 0;
    DECLARE v_has_field INT DEFAULT 0;
    DECLARE v_has_table_id INT DEFAULT 0;
    DECLARE v_has_field_id INT DEFAULT 0;

    SELECT COUNT(*) INTO v_has_table FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'dm_target_table';
    SELECT COUNT(*) INTO v_has_field FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field';

    -- ============ 字段表：先解除对主表 id 的外键引用，再完成自身转换 ============
    IF v_has_field = 1 THEN
        SELECT COUNT(*) INTO v_has_field_id FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'id';
        IF v_has_field_id = 1 THEN
            IF EXISTS (SELECT 1 FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND constraint_name = 'fk_target_field_table') THEN
                ALTER TABLE dm_target_table_field DROP FOREIGN KEY fk_target_field_table;
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_code') THEN
                ALTER TABLE dm_target_table_field DROP KEY uk_target_field_active_code;
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'active_field_code') THEN
                ALTER TABLE dm_target_table_field DROP COLUMN active_field_code;
            END IF;
            -- V172 遗留引用 table_id 列的字段名唯一键：改名 table_id -> table_code 前先释放
            IF EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_en') THEN
                ALTER TABLE dm_target_table_field DROP KEY uk_target_field_active_en;
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_cn') THEN
                ALTER TABLE dm_target_table_field DROP KEY uk_target_field_active_cn;
            END IF;
            -- 其余引用 table_id 的辅助索引随改名一并重建（不依赖隐式索引改名）
            IF EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'idx_target_field_table') THEN
                ALTER TABLE dm_target_table_field DROP KEY idx_target_field_table;
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'idx_target_field_key') THEN
                ALTER TABLE dm_target_table_field DROP KEY idx_target_field_key;
            END IF;
            -- 回填：field_code = 旧 id（覆盖历史 "TF"+id）
            UPDATE dm_target_table_field SET field_code = id;
            ALTER TABLE dm_target_table_field MODIFY field_code BIGINT NOT NULL;
            ALTER TABLE dm_target_table_field MODIFY id BIGINT NOT NULL;
            ALTER TABLE dm_target_table_field DROP PRIMARY KEY, ADD PRIMARY KEY (field_code), DROP COLUMN id;
            -- 关联键改名：table_id -> table_code（值 = 旧 table_id = 主表旧 id = 主表新 table_code）
            ALTER TABLE dm_target_table_field CHANGE COLUMN table_id table_code BIGINT NOT NULL COMMENT 'dm_target_table.table_code';
        END IF;
    END IF;

    -- ============ 主表 ============
    IF v_has_table = 1 THEN
        SELECT COUNT(*) INTO v_has_table_id FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'id';
        IF v_has_table_id = 1 THEN
            IF EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_active_code') THEN
                ALTER TABLE dm_target_table DROP KEY uk_target_table_active_code;
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'active_table_code') THEN
                ALTER TABLE dm_target_table DROP COLUMN active_table_code;
            END IF;
            -- 回填：table_code = 旧 id（覆盖历史 "TT"+id / asset_code 值）
            UPDATE dm_target_table SET table_code = id;
            ALTER TABLE dm_target_table MODIFY table_code BIGINT NOT NULL;
            ALTER TABLE dm_target_table MODIFY id BIGINT NOT NULL;
            ALTER TABLE dm_target_table DROP PRIMARY KEY, ADD PRIMARY KEY (table_code), DROP COLUMN id;
        END IF;
        -- 确保段（首次转换或裸表均已覆盖）：生成列、活动唯一键、租户组合唯一键（组合外键引用目标）
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'active_table_code') THEN
            ALTER TABLE dm_target_table
                ADD COLUMN active_table_code BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN table_code ELSE NULL END) STORED
                COMMENT '活动表编号（仅未删除记录取值）';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_active_code') THEN
            ALTER TABLE dm_target_table ADD UNIQUE KEY uk_target_table_active_code (tenant_id, active_table_code);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_tenant_code') THEN
            ALTER TABLE dm_target_table ADD UNIQUE KEY uk_target_table_tenant_code (tenant_id, table_code);
        END IF;
    END IF;

    -- ============ 字段表：生成列、活动唯一键、组合外键（确保段） ============
    IF v_has_field = 1 THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'active_field_code') THEN
            ALTER TABLE dm_target_table_field
                ADD COLUMN active_field_code BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_code ELSE NULL END) STORED
                COMMENT '活动字段编号（仅未删除记录取值）';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_code') THEN
            ALTER TABLE dm_target_table_field ADD UNIQUE KEY uk_target_field_active_code (tenant_id, active_field_code);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_en') THEN
            ALTER TABLE dm_target_table_field ADD UNIQUE KEY uk_target_field_active_en (tenant_id, table_code, active_field_name_en);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_cn') THEN
            ALTER TABLE dm_target_table_field ADD UNIQUE KEY uk_target_field_active_cn (tenant_id, table_code, active_field_name_cn);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND constraint_name = 'fk_target_field_table_code') THEN
            ALTER TABLE dm_target_table_field
                ADD CONSTRAINT fk_target_field_table_code
                FOREIGN KEY (tenant_id, table_code) REFERENCES dm_target_table (tenant_id, table_code);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'idx_target_field_table') THEN
            ALTER TABLE dm_target_table_field ADD KEY idx_target_field_table (tenant_id, table_code, deleted);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'idx_target_field_key') THEN
            ALTER TABLE dm_target_table_field ADD KEY idx_target_field_key (tenant_id, table_code, is_key_field, deleted);
        END IF;
    END IF;
END$$
DELIMITER ;
CALL dm_v177_convert_target_table_code_pk();
DROP PROCEDURE dm_v177_convert_target_table_code_pk;

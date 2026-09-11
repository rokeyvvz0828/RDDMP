-- V198: 迁移参数增加必填参数英文名（REQ-20260906-067）
-- 规则：仅 ASCII 字母、数字和下划线；租户 + 项目 + 系统编号内不区分大小写唯一。
-- 存量：用户确认不处理已有数据。首次加列前必须为空，否则失败关闭且不回填。

DELIMITER $$
CREATE PROCEDURE dm_v198_add_parameter_english_name()
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    DECLARE row_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO column_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dm_parameter'
      AND column_name = 'parameter_name_en';

    IF column_exists = 0 THEN
        SELECT COUNT(*) INTO row_count FROM dm_parameter;
        IF row_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V198 failed: dm_parameter must be empty before adding parameter_name_en';
        END IF;

        ALTER TABLE dm_parameter
            ADD COLUMN parameter_name_en VARCHAR(255)
                CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL
                COMMENT '参数英文名（仅字母、数字、下划线）'
                AFTER parameter_name;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'dm_parameter'
          AND index_name = 'uk_dm_parameter_name_en'
    ) THEN
        ALTER TABLE dm_parameter
            ADD UNIQUE KEY uk_dm_parameter_name_en
                (tenant_id, project_id, system_code, parameter_name_en);
    END IF;
END$$
DELIMITER ;

CALL dm_v198_add_parameter_english_name();
DROP PROCEDURE dm_v198_add_parameter_english_name;

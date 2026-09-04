-- Remove unused data-migration file-content MD5 persistence and indexes.
-- Historical migrations remain immutable; this idempotent cleanup runs after V173.

DELIMITER $$

DROP PROCEDURE IF EXISTS dm_v174_drop_checksum_md5$$

CREATE PROCEDURE dm_v174_drop_checksum_md5(IN tbl_name VARCHAR(64), IN idx_name VARCHAR(64))
BEGIN
    DECLARE idx_exists INT DEFAULT 0;
    DECLARE col_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO idx_exists
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = tbl_name AND index_name = idx_name;
    IF idx_exists > 0 THEN
        SET @dm_v174_sql_text = CONCAT('ALTER TABLE `', tbl_name, '` DROP INDEX `', idx_name, '`');
        PREPARE dm_v174_idx_stmt FROM @dm_v174_sql_text;
        EXECUTE dm_v174_idx_stmt;
        DEALLOCATE PREPARE dm_v174_idx_stmt;
    END IF;

    SELECT COUNT(*) INTO col_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = tbl_name AND column_name = 'checksum_md5';
    IF col_exists > 0 THEN
        SET @dm_v174_sql_text = CONCAT('ALTER TABLE `', tbl_name, '` DROP COLUMN `checksum_md5`');
        PREPARE dm_v174_col_stmt FROM @dm_v174_sql_text;
        EXECUTE dm_v174_col_stmt;
        DEALLOCATE PREPARE dm_v174_col_stmt;
    END IF;
END$$

CALL dm_v174_drop_checksum_md5('dm_plan', 'idx_dm_plan_md5')$$
CALL dm_v174_drop_checksum_md5('dm_mapping_doc', 'idx_dm_mapping_doc_md5')$$
CALL dm_v174_drop_checksum_md5('dm_dependency', 'idx_dm_dependency_md5')$$
CALL dm_v174_drop_checksum_md5('dm_script', 'idx_dm_script_md5')$$
CALL dm_v174_drop_checksum_md5('dm_topic', 'idx_dm_topic_md5')$$
CALL dm_v174_drop_checksum_md5('dm_release_drill', 'idx_dm_release_drill_md5')$$
CALL dm_v174_drop_checksum_md5('dm_report', 'idx_dm_report_md5')$$
DROP PROCEDURE dm_v174_drop_checksum_md5$$

DELIMITER ;

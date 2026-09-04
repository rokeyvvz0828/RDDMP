-- V170: 文件型内容表 MD5 查重索引补齐项目维度。
-- REQ-20260820-031 9.2 P2「MD5 索引缺项目列」：
--   查重查询恒为 tenant_id + project_id + checksum_md5 + deleted（ContentAssetTables.md5UnionSql），
--   原 V162 索引 (tenant_id, checksum_md5, deleted) 缺 project_id，无法按项目先行裁剪；
--   且 V162 建的索引名各表独立，仅数据迁移模块使用，无其他查询依赖旧键。
-- 处理方式：同名索引原子替换为 (tenant_id, project_id, checksum_md5, deleted)，
--   单条 ALTER 完成 DROP+ADD 无中间缺失态；不修改任何已发布脚本。
-- 回退：同名重建旧结构 (tenant_id, checksum_md5, deleted)（见 database-schema-and-relations.md 9.2）。

DELIMITER $$
CREATE PROCEDURE dm_v170_replace_md5_project_index(IN tbl_name VARCHAR(64), IN idx_name VARCHAR(64))
BEGIN
    DECLARE old_index_count INT DEFAULT 0;
    SELECT COUNT(*) INTO old_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = tbl_name
       AND index_name = idx_name;
    IF old_index_count > 0 THEN
        SET @dm_v170_ddl = CONCAT(
            'ALTER TABLE ', tbl_name,
            ' DROP INDEX ', idx_name,
            ', ADD INDEX ', idx_name,
            ' (tenant_id, project_id, checksum_md5, deleted)');
        PREPARE dm_v170_stmt FROM @dm_v170_ddl;
        EXECUTE dm_v170_stmt;
        DEALLOCATE PREPARE dm_v170_stmt;
    END IF;
END$$
DELIMITER ;

CALL dm_v170_replace_md5_project_index('dm_plan', 'idx_dm_plan_md5');
CALL dm_v170_replace_md5_project_index('dm_mapping_doc', 'idx_dm_mapping_doc_md5');
CALL dm_v170_replace_md5_project_index('dm_dependency', 'idx_dm_dependency_md5');
CALL dm_v170_replace_md5_project_index('dm_script', 'idx_dm_script_md5');
CALL dm_v170_replace_md5_project_index('dm_topic', 'idx_dm_topic_md5');
CALL dm_v170_replace_md5_project_index('dm_release_drill', 'idx_dm_release_drill_md5');
CALL dm_v170_replace_md5_project_index('dm_report', 'idx_dm_report_md5');
DROP PROCEDURE dm_v170_replace_md5_project_index;

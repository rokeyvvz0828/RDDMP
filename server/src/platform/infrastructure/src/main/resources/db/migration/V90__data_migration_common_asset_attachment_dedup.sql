-- 数据迁移通用文件资产：补齐公共附件标识并为租户内有效文件增加 MD5 唯一约束。
-- 追加迁移，兼容已执行旧版 V89 的环境。
SET @dm_asset_attachment_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'attachment_id'
);
SET @dm_asset_attachment_id_sql = IF(
    @dm_asset_attachment_id_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN attachment_id BIGINT NULL COMMENT ''公共附件 ID；文件型资产只保存附件标识'' AFTER object_key',
    'SELECT 1'
);
PREPARE dm_asset_attachment_id_stmt FROM @dm_asset_attachment_id_sql;
EXECUTE dm_asset_attachment_id_stmt;
DEALLOCATE PREPARE dm_asset_attachment_id_stmt;

SET @dm_asset_md5_key_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND index_name = 'uk_dm_asset_checksum_active'
);
SET @dm_asset_md5_key_sql = IF(
    @dm_asset_md5_key_exists = 0,
    'ALTER TABLE dm_asset ADD UNIQUE KEY uk_dm_asset_checksum_active (tenant_id, checksum_md5, deleted)',
    'SELECT 1'
);
PREPARE dm_asset_md5_key_stmt FROM @dm_asset_md5_key_sql;
EXECUTE dm_asset_md5_key_stmt;
DEALLOCATE PREPARE dm_asset_md5_key_stmt;

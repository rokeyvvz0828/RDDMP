-- 数据迁移资产关联关系表
-- 支持双向关联，方便未来扩展（如在会议纪要中关联查找问题清单）
CREATE TABLE IF NOT EXISTS dm_asset_relation (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    source_asset_id BIGINT NOT NULL COMMENT '源资产ID',
    source_asset_type VARCHAR(32) NOT NULL COMMENT '源资产类型（ISSUE/MEETING等）',
    target_asset_id BIGINT NOT NULL COMMENT '目标资产ID',
    target_asset_type VARCHAR(32) NOT NULL COMMENT '目标资产类型（ISSUE/MEETING等）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    INDEX idx_asset_relation_source (tenant_id, source_asset_id, source_asset_type),
    INDEX idx_asset_relation_target (tenant_id, target_asset_id, target_asset_type),
    UNIQUE KEY uk_asset_relation (tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type)
) COMMENT '资产关联关系表（问题清单-会议纪要等）';

-- 迁移现有数据：从 dm_asset.structured_data.relatedMeetingMinutes 提取关联关系
INSERT IGNORE INTO dm_asset_relation (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_at, created_by)
SELECT 
    (@row_num := @row_num + 1) + UNIX_TIMESTAMP() * 1000 AS id,
    a.tenant_id,
    a.id AS source_asset_id,
    'ISSUE' AS source_asset_type,
    CAST(JSON_UNQUOTE(jt.meeting_id) AS UNSIGNED) AS target_asset_id,
    'MEETING' AS target_asset_type,
    a.created_at,
    a.created_by
FROM dm_asset a
CROSS JOIN (SELECT @row_num := 0) r
CROSS JOIN JSON_TABLE(
    a.structured_data,
    '$.relatedMeetingMinutes[*]' COLUMNS (meeting_id JSON PATH '$')
) jt
WHERE a.asset_type = 'ISSUE' 
  AND a.deleted = 0
  AND JSON_LENGTH(JSON_EXTRACT(a.structured_data, '$.relatedMeetingMinutes')) > 0;

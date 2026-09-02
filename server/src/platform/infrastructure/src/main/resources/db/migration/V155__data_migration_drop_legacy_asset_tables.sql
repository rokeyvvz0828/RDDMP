-- V101: 数迁资产内容一菜单一表 — 校验存量已全部落新表后物理删除旧表。
-- 需求 REQ-20260831-050；设计基线：
-- docs/engineering-control/designs/2026-08-31-data-migration-content-table-split-design.md
--
-- 原则：
-- 1) 必须与 V100 分发布版本执行：V100 复制搬迁后，应用（后端 T3 切换）停止向旧表写入，
--    隔一个发布窗口、经全量数据库备份后才执行本迁移，保留回退窗口。
-- 2) 删表前先做"无损"断言：旧表每一行都必须能在新表中按业务主键定位到对应记录
--    （V100 保留原 id，id 全局唯一）。任一行在新表缺失即 SIGNAL 失败，转人工核对，且不删任何表。
-- 3) 断言通过后才 DROP dm_asset / dm_asset_relation / dm_meeting_attachment。Flyway 只追加，
--    本迁移之后回退依赖 V101 前的数据库备份。

-- 一、无损断言过程（先于任何删表动作）--------------------------------------

DELIMITER $$
CREATE PROCEDURE dm_v101_assert_zero(IN actual_count BIGINT, IN failure_message VARCHAR(500))
BEGIN
    IF actual_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
    END IF;
END$$
DELIMITER ;

-- 1.1 dm_asset 每一行都必须已按原 id 复制到对应内容表；存在未复制行则拒绝删表。
SET @v101_unmigrated_assets = (
    SELECT COUNT(*) FROM dm_asset a
    WHERE a.id NOT IN (
        SELECT id FROM dm_plan
        UNION ALL SELECT id FROM dm_mapping_doc
        UNION ALL SELECT id FROM dm_dependency
        UNION ALL SELECT id FROM dm_script
        UNION ALL SELECT id FROM dm_topic
        UNION ALL SELECT id FROM dm_release_drill
        UNION ALL SELECT id FROM dm_report
        UNION ALL SELECT id FROM dm_rule
        UNION ALL SELECT id FROM dm_parameter
        UNION ALL SELECT id FROM dm_intermediate_table
    )
);
CALL dm_v101_assert_zero(@v101_unmigrated_assets,
    'V101 失败：dm_asset 存在未搬迁至内容表的行，禁止删表，请人工核对存量');

-- 1.2 文件型资产主附件绑定（attachment_id）必须已落 dm_content_attachment，保住 att_file 存量绑定。
SET @v101_unmigrated_asset_attachments = (
    SELECT COUNT(*) FROM dm_asset a
    WHERE a.attachment_id IS NOT NULL
      AND a.asset_type IN ('PLAN','MAPPING_DOC','DEPENDENCY','SCRIPT','TOPIC','RELEASE_DRILL','REPORT')
      AND NOT EXISTS (
          SELECT 1 FROM dm_content_attachment c
          WHERE c.tenant_id = a.tenant_id AND c.business_type = a.asset_type
            AND c.business_id = a.id AND c.attachment_id = a.attachment_id
      )
);
CALL dm_v101_assert_zero(@v101_unmigrated_asset_attachments,
    'V101 失败：dm_asset 存在未落入公共附件表的附件绑定，禁止删表，请人工核对存量');

-- 1.3 会议附件每一行都必须已按原 id 迁入 dm_content_attachment。
SET @v101_unmigrated_meeting_attachments = (
    SELECT COUNT(*) FROM dm_meeting_attachment m
    WHERE m.id NOT IN (SELECT id FROM dm_content_attachment)
);
CALL dm_v101_assert_zero(@v101_unmigrated_meeting_attachments,
    'V101 失败：dm_meeting_attachment 存在未迁入公共附件表的行，禁止删表，请人工核对存量');

-- 1.4 关系行都必须已按映射规则落 dm_issue_relation / dm_meeting_system（ISSUE→MEETING/TABLE/FIELD 原样、
--     MEETING→ISSUE 反转归一、MEETING→SYSTEM 独立落会议-系统表）。任一组合无对应新行即拒绝删表。
SET @v101_unmapped_relations = (
    SELECT COUNT(*) FROM dm_asset_relation r
    WHERE NOT (
        (r.source_asset_type = 'ISSUE' AND r.target_asset_type IN ('MEETING','TABLE','FIELD')
            AND EXISTS (SELECT 1 FROM dm_issue_relation x
                        WHERE x.tenant_id = r.tenant_id AND x.issue_id = r.source_asset_id
                          AND x.related_type = r.target_asset_type AND x.related_id = r.target_asset_id))
        OR (r.source_asset_type = 'MEETING' AND r.target_asset_type = 'ISSUE'
            AND EXISTS (SELECT 1 FROM dm_issue_relation x
                        WHERE x.tenant_id = r.tenant_id AND x.issue_id = r.target_asset_id
                          AND x.related_type = 'MEETING' AND x.related_id = r.source_asset_id))
        OR (r.source_asset_type = 'MEETING' AND r.target_asset_type = 'SYSTEM'
            AND EXISTS (SELECT 1 FROM dm_meeting_system s
                        WHERE s.tenant_id = r.tenant_id AND s.meeting_id = r.source_asset_id
                          AND s.subsystem_id = r.target_asset_id))
    )
);
CALL dm_v101_assert_zero(@v101_unmapped_relations,
    'V101 失败：dm_asset_relation 存在未落入公共关系表的行，禁止删表，请人工核对存量');

-- 二、断言通过后物理删除三张旧表（无外键依赖，删除顺序仅影响可读性）。
DROP TABLE dm_meeting_attachment;
DROP TABLE dm_asset_relation;
DROP TABLE dm_asset;

-- 三、收尾：删除断言过程。
DROP PROCEDURE dm_v101_assert_zero;

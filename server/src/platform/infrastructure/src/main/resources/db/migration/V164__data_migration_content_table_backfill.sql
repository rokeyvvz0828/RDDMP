-- V100: 数迁资产内容一菜单一表 — 存量数据复制搬迁（不删源表）。
-- 需求 REQ-20260831-050；设计基线：
-- docs/engineering-control/designs/2026-08-31-data-migration-content-table-split-design.md
--
-- 原则：
-- 1) 保留原 dm_asset.id（att_file.business_key=资产 id，id 保留才能维持存量附件绑定；
--    dm_operation_log.entity_id 历史审计同样依赖 id 不变）。
-- 2) 断言保护先于任何数据写入：未登记 asset_type、未映射关系组合、结构化资产空主体
--    均为 0，非 0 用 SIGNAL 使迁移失败，转人工决策；失败时未发生任何复制。
-- 3) 旧表删除在 V101，隔一个发布版本执行，保留回退窗口（回滚应用版本即可回退本迁移）。
-- 4) 本脚本按 Flyway 一次性执行设计；失败修复（repair）重跑前必须先人工核对目标表残留。

-- 一、断言保护过程（先于所有数据写入）--------------------------------------

DELIMITER $$
CREATE PROCEDURE dm_v100_assert_zero(IN actual_count BIGINT, IN failure_message VARCHAR(500))
BEGIN
    IF actual_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
    END IF;
END$$
DELIMITER ;

-- 1.1 dm_asset 只允许十个登记类型残留；TABLE_STRUCTURE/MEETING/TRANSFORM_DOC/CONFIG/
--     VALIDATION_DOC/OTHER/ISSUE 等历史类型必须已清零（ISSUE 已在 V93 搬离）。
SET @v100_leftover_asset_types = (
    SELECT COUNT(*) FROM dm_asset
    WHERE asset_type NOT IN ('PLAN','MAPPING_DOC','DEPENDENCY','SCRIPT','TOPIC',
                             'RELEASE_DRILL','REPORT','RULE','PARAMETER','INTERMEDIATE_TABLE')
);
CALL dm_v100_assert_zero(@v100_leftover_asset_types,
    'V100 失败：dm_asset 存在未登记 asset_type 的行，请人工评估后再迁移');

-- 1.2 结构化资产主体不允许为空（目标列 JSON NOT NULL）。
SET @v100_null_structured = (
    SELECT COUNT(*) FROM dm_asset
    WHERE asset_type IN ('RULE','PARAMETER','INTERMEDIATE_TABLE') AND structured_data IS NULL
);
CALL dm_v100_assert_zero(@v100_null_structured,
    'V100 失败：结构化资产存在空 structured_data 的行，请人工评估后再迁移');

-- 1.3 附件只允许挂在七类文件型资产上；其余类型带附件属于异常数据。
SET @v100_misplaced_attachment = (
    SELECT COUNT(*) FROM dm_asset
    WHERE attachment_id IS NOT NULL
      AND asset_type NOT IN ('PLAN','MAPPING_DOC','DEPENDENCY','SCRIPT','TOPIC','RELEASE_DRILL','REPORT')
);
CALL dm_v100_assert_zero(@v100_misplaced_attachment,
    'V100 失败：非文件型资产存在附件绑定，请人工评估后再迁移');

-- 1.4 dm_asset_relation 只允许四种已映射组合：
--     ISSUE→MEETING/TABLE/FIELD、MEETING→ISSUE、MEETING→SYSTEM。
SET @v100_unmapped_relations = (
    SELECT COUNT(*) FROM dm_asset_relation
    WHERE NOT (source_asset_type = 'ISSUE' AND target_asset_type IN ('MEETING','TABLE','FIELD'))
      AND NOT (source_asset_type = 'MEETING' AND target_asset_type IN ('ISSUE','SYSTEM'))
);
CALL dm_v100_assert_zero(@v100_unmapped_relations,
    'V100 失败：dm_asset_relation 存在未映射的关系组合，请人工评估后再迁移');

-- 二、内容表复制搬迁（保留原 id 与软删/审计字段）----------------------------

INSERT INTO dm_plan (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5,
                     owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, checksum_md5,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'PLAN';

INSERT INTO dm_mapping_doc (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5,
                            owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, checksum_md5,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'MAPPING_DOC';

INSERT INTO dm_dependency (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5,
                           owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, checksum_md5,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'DEPENDENCY';

INSERT INTO dm_script (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5,
                       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, checksum_md5,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'SCRIPT';

INSERT INTO dm_topic (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5,
                      owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, checksum_md5,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'TOPIC';

INSERT INTO dm_release_drill (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5,
                              owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, checksum_md5,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'RELEASE_DRILL';

INSERT INTO dm_report (id, tenant_id, project_id, component_id, doc_code, doc_name,
                       report_period, report_date, keywords, checksum_md5,
                       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name,
       report_period, report_date, keywords, checksum_md5,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'REPORT';

INSERT INTO dm_rule (id, tenant_id, project_id, component_id, doc_code, doc_name, structured_data,
                     owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, structured_data,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'RULE';

INSERT INTO dm_parameter (id, tenant_id, project_id, component_id, doc_code, doc_name, structured_data,
                          owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, structured_data,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'PARAMETER';

INSERT INTO dm_intermediate_table (id, tenant_id, project_id, component_id, doc_code, doc_name, structured_data,
                                   owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
SELECT id, tenant_id, project_id, component_id, asset_code, asset_name, structured_data,
       owner_id, deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at
FROM dm_asset WHERE asset_type = 'INTERMEDIATE_TABLE';

-- 三、公共附件关系表 --------------------------------------------------------
-- 先迁会议附件（保留原 id/排序/软删状态），再迁文件型资产主文件（sort_order=0）。
-- 显式 id 先行可保证后续自增 id 不与存量冲突。

INSERT INTO dm_content_attachment (id, tenant_id, business_type, business_id, attachment_id, file_name,
                                   sort_order, deleted, deleted_by, deleted_at, created_by, created_at)
SELECT id, tenant_id, 'MEETING', meeting_id, attachment_id, file_name,
       sort_order, deleted, deleted_by, deleted_at, created_by, created_at
FROM dm_meeting_attachment;

INSERT INTO dm_content_attachment (tenant_id, business_type, business_id, attachment_id, file_name,
                                   sort_order, deleted, deleted_by, deleted_at, created_by, created_at)
SELECT a.tenant_id, a.asset_type, a.id, a.attachment_id, COALESCE(af.file_name, a.asset_name),
       0, a.deleted, a.deleted_by, a.deleted_at, COALESCE(a.created_by, a.owner_id), a.created_at
FROM dm_asset a
LEFT JOIN att_file af ON af.id = a.attachment_id
WHERE a.attachment_id IS NOT NULL
  AND a.asset_type IN ('PLAN','MAPPING_DOC','DEPENDENCY','SCRIPT','TOPIC','RELEASE_DRILL','REPORT');

-- 四、公共问题关系表与会议-系统关系 ------------------------------------------
-- ISSUE→MEETING/TABLE/FIELD 原样迁移；MEETING→ISSUE 反转归一为同一行（INSERT IGNORE 去重）；
-- MEETING→SYSTEM 独立落会议-系统关联表。

INSERT INTO dm_issue_relation (tenant_id, issue_id, related_type, related_id, created_by, created_at)
SELECT tenant_id, source_asset_id, target_asset_type, target_asset_id, created_by, created_at
FROM dm_asset_relation
WHERE source_asset_type = 'ISSUE' AND target_asset_type IN ('MEETING','TABLE','FIELD');

INSERT IGNORE INTO dm_issue_relation (tenant_id, issue_id, related_type, related_id, created_by, created_at)
SELECT tenant_id, target_asset_id, 'MEETING', source_asset_id, created_by, created_at
FROM dm_asset_relation
WHERE source_asset_type = 'MEETING' AND target_asset_type = 'ISSUE';

INSERT INTO dm_meeting_system (tenant_id, meeting_id, subsystem_id, created_by, created_at)
SELECT tenant_id, source_asset_id, target_asset_id, created_by, created_at
FROM dm_asset_relation
WHERE source_asset_type = 'MEETING' AND target_asset_type = 'SYSTEM';

-- 五、收尾：删除断言过程（DDL 隐式提交上方数据写入）。
DROP PROCEDURE dm_v100_assert_zero;

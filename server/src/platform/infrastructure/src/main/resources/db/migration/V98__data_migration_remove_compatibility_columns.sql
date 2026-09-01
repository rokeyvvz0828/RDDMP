-- 数据迁移模型收敛：删除兼容冗余列和未接入的专题类型表。
--
-- 前置条件：V90 已将新上传文件统一绑定 att_file；应用已不再依赖 dm_asset.object_key
-- 作为下载或清理的事实来源。若存量仍存在 object_key 且没有 attachment_id，发布前必须
-- 先完成附件补偿迁移，否则删除该列会使旧对象无法通过业务接口定位。

-- V97 建立但尚未被任何菜单、接口或服务使用，删除孤立专题类型表。
DROP TABLE IF EXISTS dm_topic_type;

-- 会议项目名由 pm_project.project_name 投影，附件由 dm_meeting_attachment 维护。
ALTER TABLE dm_meeting
    DROP COLUMN project_name,
    DROP COLUMN attachment_id,
    DROP COLUMN file_name;

-- 问题系统名称由 system_code -> arch_physical_subsystem 投影。
ALTER TABLE dm_issue
    DROP COLUMN system_name;

-- 字段表编号由 table_id -> dm_target_table.table_code 投影。
ALTER TABLE dm_target_table_field
    DROP COLUMN table_code;

-- 新上传资产统一使用公共附件 ID；删除旧的 MinIO 对象键兼容字段。
ALTER TABLE dm_asset
    DROP COLUMN object_key,
    COMMENT = '数据迁移内容资产表（文件型资产、结构化规则和参数）';

-- V96 原脚本缺少活动附件唯一约束和租户前缀索引。先清理同一会议的重复活动行，
-- 再通过生成列实现“活动记录唯一、已删除记录可保留历史”的约束。
DELETE a
FROM dm_meeting_attachment a
JOIN dm_meeting_attachment b
  ON b.tenant_id = a.tenant_id
 AND b.meeting_id = a.meeting_id
 AND b.attachment_id = a.attachment_id
 AND b.deleted = 0
 AND a.deleted = 0
 AND b.id < a.id;

ALTER TABLE dm_meeting_attachment
    DROP INDEX idx_dm_meeting_att_meeting,
    DROP INDEX idx_dm_meeting_att_attachment,
    DROP INDEX idx_dm_meeting_att_tenant,
    ADD COLUMN active_attachment_key VARCHAR(256)
        GENERATED ALWAYS AS (
            CASE WHEN deleted = 0
                 THEN CONCAT(tenant_id, ':', meeting_id, ':', attachment_id)
                 ELSE NULL
            END
        ) STORED,
    ADD UNIQUE KEY uk_dm_meeting_att_active (active_attachment_key),
    ADD KEY idx_dm_meeting_att_meeting (tenant_id, meeting_id, deleted, sort_order),
    ADD KEY idx_dm_meeting_att_attachment (tenant_id, attachment_id, deleted),
    ADD KEY idx_dm_meeting_att_tenant (tenant_id, deleted);

-- V95 索引补齐租户前缀，匹配所有服务层查询的多租户过滤条件。
ALTER TABLE dm_meeting
    DROP INDEX idx_dm_meeting_project,
    DROP INDEX idx_dm_meeting_source,
    DROP INDEX idx_dm_meeting_granularity,
    DROP INDEX idx_dm_meeting_created,
    DROP INDEX idx_dm_meeting_deleted,
    ADD KEY idx_dm_meeting_project (tenant_id, project_id, deleted, updated_at),
    ADD KEY idx_dm_meeting_source (tenant_id, meeting_source, deleted),
    ADD KEY idx_dm_meeting_granularity (tenant_id, granularity, deleted),
    ADD KEY idx_dm_meeting_created (tenant_id, created_at),
    ADD KEY idx_dm_meeting_deleted (tenant_id, deleted, deleted_at);

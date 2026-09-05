-- REQ-20260904-063：将投产方案中的 P1/P2 时序与其指令拆分为两层。只追加，不修改历史迁移。

CREATE TABLE IF NOT EXISTS rel_release_plan_timeline (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    item_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/ROLLBACK',
    seq_no INT NOT NULL,
    timeline_name VARCHAR(128) NOT NULL,
    description VARCHAR(2000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_plan_timeline_seq (tenant_id, plan_id, item_type, seq_no, deleted),
    KEY idx_rel_plan_timeline_project (tenant_id, project_id, plan_id, item_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产方案时序';

INSERT IGNORE INTO rel_release_plan_timeline
    (id, tenant_id, project_id, plan_id, item_type, seq_no, timeline_name, description, created_by, updated_by)
SELECT MIN(i.id) + 400000000, i.tenant_id, i.project_id, i.plan_id, i.item_type, i.seq_no,
       CONCAT('P', i.seq_no, ' 时序'), NULL, MIN(i.created_by), MIN(i.updated_by)
FROM rel_release_plan_item i
GROUP BY i.tenant_id, i.project_id, i.plan_id, i.item_type, i.seq_no;

SET @timeline_id_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rel_release_plan_item'
      AND column_name = 'timeline_id'
);
SET @timeline_id_column_sql = IF(
    @timeline_id_column_exists = 0,
    'ALTER TABLE rel_release_plan_item ADD COLUMN timeline_id BIGINT NULL AFTER plan_id',
    'SELECT 1'
);
PREPARE add_timeline_id_column FROM @timeline_id_column_sql;
EXECUTE add_timeline_id_column;
DEALLOCATE PREPARE add_timeline_id_column;

SET @timeline_id_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rel_release_plan_item'
      AND index_name = 'idx_rel_plan_item_timeline'
);
SET @timeline_id_index_sql = IF(
    @timeline_id_index_exists = 0,
    'ALTER TABLE rel_release_plan_item ADD KEY idx_rel_plan_item_timeline (tenant_id, project_id, plan_id, item_type, timeline_id, deleted)',
    'SELECT 1'
);
PREPARE add_timeline_id_index FROM @timeline_id_index_sql;
EXECUTE add_timeline_id_index;
DEALLOCATE PREPARE add_timeline_id_index;

SET @legacy_seq_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rel_release_plan_item'
      AND index_name = 'uk_rel_plan_item_seq'
);
SET @legacy_seq_index_sql = IF(
    @legacy_seq_index_exists > 0,
    'ALTER TABLE rel_release_plan_item DROP INDEX uk_rel_plan_item_seq',
    'SELECT 1'
);
PREPARE drop_legacy_seq_index FROM @legacy_seq_index_sql;
EXECUTE drop_legacy_seq_index;
DEALLOCATE PREPARE drop_legacy_seq_index;

UPDATE rel_release_plan_item i
JOIN rel_release_plan_timeline t
  ON t.tenant_id = i.tenant_id
 AND t.project_id = i.project_id
 AND t.plan_id = i.plan_id
 AND t.item_type = i.item_type
 AND t.seq_no = i.seq_no
SET i.timeline_id = t.id,
    i.seq_no = CASE WHEN i.deleted = 0 THEN 1 ELSE i.seq_no END;

ALTER TABLE rel_release_plan_item
    MODIFY COLUMN timeline_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_rel_plan_item_timeline_seq (tenant_id, plan_id, item_type, timeline_id, seq_no, deleted);

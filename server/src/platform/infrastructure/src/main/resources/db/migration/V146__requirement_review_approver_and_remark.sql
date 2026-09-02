-- =============================================================================
-- V133：存量工作量/软需评审增强
-- -----------------------------------------------------------------------------
-- 1) req_workload / req_soft_doc：提交评审记录所选审批人与评审报告文档名称，
--    与新建项目差异「提交评审」流程一致。
-- 2) req_review_record：新增「评审备注」列（评审意见之外的可选备注）。
-- 追加式迁移，不改历史脚本；列新增按 information_schema 判断跳过。
-- =============================================================================

-- 1) req_workload 新增评审审批人/报告名称列
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_workload'
      AND COLUMN_NAME IN ('review_approver_ids', 'review_approver_names', 'review_report_name'));
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_workload
        ADD COLUMN review_approver_ids VARCHAR(255) NULL COMMENT ''评审审批人用户 ID（逗号分隔）'' AFTER review_record_id,
        ADD COLUMN review_approver_names VARCHAR(255) NULL COMMENT ''评审审批人姓名'' AFTER review_approver_ids,
        ADD COLUMN review_report_name VARCHAR(200) NULL COMMENT ''评审报告信息文档名称（提交评审时填写）'' AFTER review_approver_names',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) req_soft_doc 新增评审审批人/报告名称列
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_soft_doc'
      AND COLUMN_NAME IN ('review_approver_ids', 'review_approver_names', 'review_report_name'));
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_soft_doc
        ADD COLUMN review_approver_ids VARCHAR(255) NULL COMMENT ''评审审批人用户 ID（逗号分隔）'' AFTER review_record_id,
        ADD COLUMN review_approver_names VARCHAR(255) NULL COMMENT ''评审审批人姓名'' AFTER review_approver_ids,
        ADD COLUMN review_report_name VARCHAR(200) NULL COMMENT ''评审报告信息文档名称（提交评审时填写）'' AFTER review_approver_names',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) req_review_record 新增评审备注列
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_review_record'
      AND COLUMN_NAME = 'remark');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_review_record
        ADD COLUMN remark VARCHAR(500) NULL COMMENT ''评审备注'' AFTER comment',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

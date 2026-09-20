-- =============================================================================
-- REQ-20260919-078：评审必须上传文件（Owner 2026-09-19 追加要求）
-- -----------------------------------------------------------------------------
-- 新建项目差异评审、存量工作量评审与软需评审在提交时都必须带评审报告文件。
-- 文件由平台附件能力（POST /api/attachments 上传 → AttachmentGateway 绑定）托管，
-- 需求侧只保存附件业务引用与文件名称快照，便于评审记录回看与后续预览/下载。
--   1. 新建差异：req_difference_detail.review_report_attachment_id
--   2. 存量交付件：req_legacy_deliverable.review_report_attachment_id
-- （req_review_record 已有 report_doc_name / report_preview_id 两列，评审完成时写入同一附件引用）
-- 幂等：按 information_schema 判断列是否存在后再追加。
-- =============================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_difference_detail'
      AND COLUMN_NAME = 'review_report_attachment_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_difference_detail ADD COLUMN review_report_attachment_id BIGINT NULL COMMENT ''评审报告附件 ID（平台附件 att_file.id）'' AFTER review_report_name',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_deliverable'
      AND COLUMN_NAME = 'review_report_attachment_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_deliverable ADD COLUMN review_report_attachment_id BIGINT NULL COMMENT ''评审报告附件 ID（平台附件 att_file.id）'' AFTER review_report_name',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

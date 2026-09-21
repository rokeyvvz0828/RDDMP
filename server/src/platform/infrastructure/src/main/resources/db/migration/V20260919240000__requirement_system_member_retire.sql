-- =============================================================================
-- REQ-20260919-078：系统人员下线 + 需求流程定义范围修正
-- 1. 系统人员（req_legacy_system_member）原本用于需求模块自建的数据权限，
--    权限已统一由项目管理的三个项目角色控制，该表与相关接口一并下线；
-- 2. requirement.legacy.deliverable.review 种子定义此前写成 GLOBAL，
--    而工作流引擎只接受 PLATFORM / PROJECT，这里修正为 PLATFORM 以与既有
--    requirement.diff.review 保持一致（正式启用仍需在项目管理里发布项目流程）。
-- 幂等：可重复执行。
-- =============================================================================

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_system_member');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_legacy_system_member', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE wf_definition
SET scope_type = 'PLATFORM', project_id = NULL
WHERE tenant_id = 1 AND code = 'requirement.legacy.deliverable.review' AND scope_type = 'GLOBAL';

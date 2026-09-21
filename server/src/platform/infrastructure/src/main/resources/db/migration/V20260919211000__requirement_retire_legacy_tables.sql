-- =============================================================================
-- REQ-20260919-078 需求管理数据模型重构（续）：下线被替换的旧需求表
-- -----------------------------------------------------------------------------
-- V20260919210000 已建立统一需求模型（req_requirement / req_requirement_system /
-- req_difference_detail / req_legacy_detail / req_legacy_deliverable）并删除
-- req_workload / req_soft_doc / req_legacy_member；本迁移补删其余已无读写方的旧表：
--   req_difference_flow_log   → 统一流转日志 req_flow_log
--   req_difference            → 需求主表 + req_difference_detail
--   req_legacy_requirement    → 需求主表 + req_legacy_detail
--   req_legacy_system_item    → req_requirement_system
--   req_coordination_item     → req_requirement_system
--   req_business_group_member → 权限统一由项目管理角色控制
--   req_system                → 系统主数据统一取架构管理物理子系统
--
-- 同批清理（不涉及表结构）：mock/mock-data.json 需求演示数据改由迁移重建、
--   MockDataInitializer 允许清单与 MockDataInitializerTest 内联数据集、
--   platform/boot 开发管理集成测试的需求来源夹具。
--
-- 幂等：按 information_schema 存在性判断后删除，可重复执行。
-- =============================================================================

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_difference_flow_log');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_difference_flow_log', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_difference');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_difference', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_requirement');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_legacy_requirement', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_system_item');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_legacy_system_item', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_coordination_item');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_coordination_item', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_business_group_member');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_business_group_member', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_system');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_system', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

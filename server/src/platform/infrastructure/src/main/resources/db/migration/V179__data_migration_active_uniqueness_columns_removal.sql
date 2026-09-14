-- V178: 全模块下线 active_* 活动生成列，唯一键直接建在业务列上（方案3：不区分活动/软删，软删行同样占用唯一名额）。
-- 背景（REQ-20260820-031 T42，用户选定方案3）：V103/V157/V161/V162/V166/V168/V172/V177 用「CASE WHEN deleted = 0
--   THEN 业务列 ELSE NULL END」生成列 + uk_*_active_* 实现「软删行不参与唯一、删除后可同名重建」。
--   用户决策：开发阶段无历史数据，采用干净表结构 —— 删除所有 active_* 生成列，唯一键建在原始业务列上，
--   删除后必须先彻底删除（purge）才能重建同名/同编号记录；恢复语义不变（恢复只与活动行冲突）。
-- 影响表：dm_issue、dm_meeting、dm_plan（doc_code 键 + 维度键）、dm_component、dm_target_table、
--   dm_target_table_field、9 张内容表、dm_content_attachment（dm_meeting_attachment 已在 V164 删除，
--   dm_intermediate_table 已在 V169 删除，均不在最终模型）。编号类唯一（issue_code/meeting_code/doc_code/table_code
--   field_code）由发号/生成器保证唯一，朴素唯一键只做完整性兜底。
-- 约束：Flyway 只追加，不改历史脚本；全部 information_schema 条件式执行，幂等可重跑。

DROP PROCEDURE IF EXISTS dm_v178_remove_active_uniqueness_columns;
DELIMITER $$
CREATE PROCEDURE dm_v178_remove_active_uniqueness_columns()
BEGIN
    -- ============ 1. dm_issue：uk_dm_issue_active_code -> uk_dm_issue_code ============
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_issue') THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND index_name = 'uk_dm_issue_active_code') THEN
            ALTER TABLE dm_issue DROP INDEX uk_dm_issue_active_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND column_name = 'active_issue_code') THEN
            ALTER TABLE dm_issue DROP COLUMN active_issue_code;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND index_name = 'uk_dm_issue_code') THEN
            ALTER TABLE dm_issue ADD UNIQUE KEY uk_dm_issue_code (tenant_id, project_id, issue_code);
        END IF;
    END IF;

    -- ============ 2. dm_meeting：uk_dm_meeting_active_code -> uk_dm_meeting_code ============
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_meeting') THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_meeting' AND index_name = 'uk_dm_meeting_active_code') THEN
            ALTER TABLE dm_meeting DROP INDEX uk_dm_meeting_active_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_meeting' AND column_name = 'active_meeting_code') THEN
            ALTER TABLE dm_meeting DROP COLUMN active_meeting_code;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_meeting' AND index_name = 'uk_dm_meeting_code') THEN
            ALTER TABLE dm_meeting ADD UNIQUE KEY uk_dm_meeting_code (tenant_id, project_id, meeting_code);
        END IF;
    END IF;

    -- ============ 3. dm_plan：doc_code 键 + 维度键 -> 朴素列 ============
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_plan') THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND index_name = 'uk_dm_plan_active_code') THEN
            ALTER TABLE dm_plan DROP INDEX uk_dm_plan_active_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND index_name = 'uk_dm_plan_active_dimension') THEN
            ALTER TABLE dm_plan DROP INDEX uk_dm_plan_active_dimension;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND column_name = 'active_doc_code') THEN
            ALTER TABLE dm_plan DROP COLUMN active_doc_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND column_name = 'active_dimension_key') THEN
            ALTER TABLE dm_plan DROP COLUMN active_dimension_key;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND index_name = 'uk_dm_plan_code') THEN
            ALTER TABLE dm_plan ADD UNIQUE KEY uk_dm_plan_code (tenant_id, project_id, doc_code);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND index_name = 'uk_dm_plan_dimension') THEN
            ALTER TABLE dm_plan ADD UNIQUE KEY uk_dm_plan_dimension (tenant_id, project_id, granularity, plan_type, system_code);
        END IF;
    END IF;

    -- ============ 4. dm_component：uk_dm_component_active_subsystem -> uk_dm_component_subsystem ============
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_component') THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'uk_dm_component_active_subsystem') THEN
            ALTER TABLE dm_component DROP INDEX uk_dm_component_active_subsystem;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'active_physical_subsystem_code') THEN
            ALTER TABLE dm_component DROP COLUMN active_physical_subsystem_code;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'uk_dm_component_subsystem') THEN
            ALTER TABLE dm_component ADD UNIQUE KEY uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code);
        END IF;
    END IF;

    -- ============ 5. dm_target_table：active_* 四列与三个活动键 -> 朴素 en/cn 键 ============
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_target_table') THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_active_code') THEN
            ALTER TABLE dm_target_table DROP INDEX uk_target_table_active_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_active_en') THEN
            ALTER TABLE dm_target_table DROP INDEX uk_target_table_active_en;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_active_cn') THEN
            ALTER TABLE dm_target_table DROP INDEX uk_target_table_active_cn;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'active_table_code') THEN
            ALTER TABLE dm_target_table DROP COLUMN active_table_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'active_system_code') THEN
            ALTER TABLE dm_target_table DROP COLUMN active_system_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'active_table_name_en') THEN
            ALTER TABLE dm_target_table DROP COLUMN active_table_name_en;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'active_table_name_cn') THEN
            ALTER TABLE dm_target_table DROP COLUMN active_table_name_cn;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_en') THEN
            ALTER TABLE dm_target_table ADD UNIQUE KEY uk_target_table_en (tenant_id, project_id, system_code, table_name_en);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'uk_target_table_cn') THEN
            ALTER TABLE dm_target_table ADD UNIQUE KEY uk_target_table_cn (tenant_id, project_id, system_code, table_name_cn);
        END IF;
    END IF;

    -- ============ 6. dm_target_table_field：active_* 三列与三个活动键 -> 朴素 en/cn 键 ============
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field') THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_code') THEN
            ALTER TABLE dm_target_table_field DROP INDEX uk_target_field_active_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_en') THEN
            ALTER TABLE dm_target_table_field DROP INDEX uk_target_field_active_en;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_active_cn') THEN
            ALTER TABLE dm_target_table_field DROP INDEX uk_target_field_active_cn;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'active_field_code') THEN
            ALTER TABLE dm_target_table_field DROP COLUMN active_field_code;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'active_field_name_en') THEN
            ALTER TABLE dm_target_table_field DROP COLUMN active_field_name_en;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'active_field_name_cn') THEN
            ALTER TABLE dm_target_table_field DROP COLUMN active_field_name_cn;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_en') THEN
            ALTER TABLE dm_target_table_field ADD UNIQUE KEY uk_target_field_en (tenant_id, table_code, field_name_en);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'uk_target_field_cn') THEN
            ALTER TABLE dm_target_table_field ADD UNIQUE KEY uk_target_field_cn (tenant_id, table_code, field_name_cn);
        END IF;
    END IF;

    -- ============ 7. 9 张内容表（dm_plan 已在上方处理 doc_code）：active_doc_code -> uk_dm_*_code ============
    -- 表名与唯一键名一一对应；dm_plan 跳过（其 doc_code 键已在第 3 段处理）。
    BLOCK_CONTENT: BEGIN
        DECLARE v_table_name VARCHAR(64);
        DECLARE v_uk_name VARCHAR(64);
        DECLARE v_content_done INT DEFAULT 0;
        DECLARE content_cursor CURSOR FOR
            SELECT 'dm_mapping_doc','dm_mapping_doc' UNION ALL
            SELECT 'dm_dependency','dm_dependency' UNION ALL
            SELECT 'dm_script','dm_script' UNION ALL
            SELECT 'dm_topic','dm_topic' UNION ALL
            SELECT 'dm_release_drill','dm_release_drill' UNION ALL
            SELECT 'dm_report','dm_report' UNION ALL
            SELECT 'dm_rule','dm_rule' UNION ALL
            SELECT 'dm_parameter','dm_parameter';
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_content_done = 1;
        OPEN content_cursor;
        content_loop: LOOP
            FETCH content_cursor INTO v_table_name, v_uk_name;
            IF v_content_done = 1 THEN LEAVE content_loop; END IF;
            IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = v_table_name) THEN
                SET @drop_uk = CONCAT('ALTER TABLE `', v_table_name, '` DROP INDEX `uk_', v_uk_name, '_active_code`');
                IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = v_table_name AND index_name = CONCAT('uk_', v_uk_name, '_active_code')) THEN
                    PREPARE drop_uk_stmt FROM @drop_uk; EXECUTE drop_uk_stmt; DEALLOCATE PREPARE drop_uk_stmt;
                END IF;
                IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = v_table_name AND column_name = 'active_doc_code') THEN
                    SET @drop_col = CONCAT('ALTER TABLE `', v_table_name, '` DROP COLUMN active_doc_code');
                    PREPARE drop_col_stmt FROM @drop_col; EXECUTE drop_col_stmt; DEALLOCATE PREPARE drop_col_stmt;
                END IF;
                IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = v_table_name AND index_name = CONCAT('uk_', v_uk_name, '_code')) THEN
                    SET @add_uk = CONCAT('ALTER TABLE `', v_table_name, '` ADD UNIQUE KEY `uk_', v_uk_name, '_code` (tenant_id, project_id, doc_code)');
                    PREPARE add_uk_stmt FROM @add_uk; EXECUTE add_uk_stmt; DEALLOCATE PREPARE add_uk_stmt;
                END IF;
            END IF;
        END LOOP content_loop;
        CLOSE content_cursor;
    END BLOCK_CONTENT;

    -- ============ 8. dm_content_attachment：uk_dm_content_att_active -> uk_dm_content_att ============
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_content_attachment') THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_content_attachment' AND index_name = 'uk_dm_content_att_active') THEN
            ALTER TABLE dm_content_attachment DROP INDEX uk_dm_content_att_active;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_content_attachment' AND column_name = 'active_attachment_key') THEN
            ALTER TABLE dm_content_attachment DROP COLUMN active_attachment_key;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_content_attachment' AND index_name = 'uk_dm_content_att') THEN
            ALTER TABLE dm_content_attachment ADD UNIQUE KEY uk_dm_content_att (tenant_id, business_type, business_id, attachment_id);
        END IF;
    END IF;
END$$
DELIMITER ;
CALL dm_v178_remove_active_uniqueness_columns();
DROP PROCEDURE dm_v178_remove_active_uniqueness_columns;

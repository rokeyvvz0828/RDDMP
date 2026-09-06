-- V180: 专题材料域模型与系统参数初始化。
-- 仅追加、条件式执行；专题类型归属系统管理/参数管理，不新建业务字典表。

-- 1. dm_topic 专题元数据（兼容 V162/V178 存量行）。
SET @dm_topic_granularity_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND column_name = 'granularity'),
    'SELECT 1',
    'ALTER TABLE dm_topic ADD COLUMN granularity VARCHAR(16) NOT NULL DEFAULT ''PROJECT'' COMMENT ''专题颗粒度 PROJECT/SYSTEM'' AFTER project_id'
);
PREPARE dm_topic_granularity_stmt FROM @dm_topic_granularity_sql;
EXECUTE dm_topic_granularity_stmt;
DEALLOCATE PREPARE dm_topic_granularity_stmt;

SET @dm_topic_type_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND column_name = 'topic_type_code'),
    'SELECT 1',
    'ALTER TABLE dm_topic ADD COLUMN topic_type_code VARCHAR(64) NOT NULL DEFAULT ''LEGACY'' COMMENT ''专题类型参数编码（系统管理/参数管理）'' AFTER granularity'
);
PREPARE dm_topic_type_stmt FROM @dm_topic_type_sql;
EXECUTE dm_topic_type_stmt;
DEALLOCATE PREPARE dm_topic_type_stmt;

SET @dm_topic_summary_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND column_name = 'topic_summary'),
    'SELECT 1',
    'ALTER TABLE dm_topic ADD COLUMN topic_summary VARCHAR(2000) NOT NULL DEFAULT '''' COMMENT ''专题材料简述'' AFTER doc_name'
);
PREPARE dm_topic_summary_stmt FROM @dm_topic_summary_sql;
EXECUTE dm_topic_summary_stmt;
DEALLOCATE PREPARE dm_topic_summary_stmt;

-- 显式回填，确保重复执行和历史行均满足新字段不变量。
UPDATE dm_topic SET granularity = 'PROJECT' WHERE granularity IS NULL OR granularity = '';
UPDATE dm_topic SET topic_type_code = 'LEGACY' WHERE topic_type_code IS NULL OR topic_type_code = '';
UPDATE dm_topic SET topic_summary = '' WHERE topic_summary IS NULL;

-- 2. 一个专题可关联多个当前项目系统；软删专题保留关系，purge 由服务事务清理。
CREATE TABLE IF NOT EXISTS dm_topic_system (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    topic_id BIGINT NOT NULL COMMENT '专题材料ID（dm_topic.id）',
    project_id BIGINT NOT NULL COMMENT '项目ID（pm_project.id）',
    system_code VARCHAR(96) NOT NULL COMMENT '系统/组件编号（项目内有效）',
    created_by BIGINT NOT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_dm_topic_system (tenant_id, topic_id, system_code),
    KEY idx_dm_topic_system_project (tenant_id, project_id, system_code),
    KEY idx_dm_topic_system_topic (tenant_id, topic_id)
) COMMENT='专题材料涉及系统关系表';

SET @dm_topic_granularity_idx_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND index_name = 'idx_dm_topic_granularity'),
    'SELECT 1',
    'ALTER TABLE dm_topic ADD KEY idx_dm_topic_granularity (tenant_id, project_id, granularity, deleted, updated_at)'
);
PREPARE dm_topic_granularity_idx_stmt FROM @dm_topic_granularity_idx_sql;
EXECUTE dm_topic_granularity_idx_stmt;
DEALLOCATE PREPARE dm_topic_granularity_idx_stmt;

SET @dm_topic_type_idx_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND index_name = 'idx_dm_topic_type'),
    'SELECT 1',
    'ALTER TABLE dm_topic ADD KEY idx_dm_topic_type (tenant_id, project_id, topic_type_code, deleted)'
);
PREPARE dm_topic_type_idx_stmt FROM @dm_topic_type_idx_sql;
EXECUTE dm_topic_type_idx_stmt;
DEALLOCATE PREPARE dm_topic_type_idx_stmt;

-- 3. 参数管理类别：管理员后续可在“系统管理/参数管理”修改名称、排序和状态。
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES
    (5701, 1, 'DM_TOPIC_PROJECT_TYPE', '专题材料项目级类型', 1, 0),
    (5702, 1, 'DM_TOPIC_SYSTEM_TYPE', '专题材料系统级类型', 1, 0);

-- 4. 初始参数项。config_key 为稳定编码，config_value 为展示名称；INSERT IGNORE 不覆盖管理员既有配置。
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '专题材料类型（由系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 5710 id, 'NON_STRUCTURED_MIGRATION' config_key, '非结构化数据迁移专题' config_value, 'DM_TOPIC_PROJECT_TYPE' category_code UNION ALL
    SELECT 5711, 'MIGRATION_TEST_CASE', '数据迁移专项测试案例专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5712, 'ASSET_INVENTORY', '资产盘点专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5713, 'SPECIAL_BATCH', '特殊批专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5714, 'STATIC_CHECK_REPORT', '静态检核报表专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5715, 'BUSINESS_PARAMETER', '业务参数专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5716, 'DATA_LINEAGE', '数据线溯源专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5717, 'PERFORMANCE_OPTIMIZATION', '性能优化专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5718, 'CLEANUP_SUPPLEMENT', '清理补录专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5719, 'MIGRATION_SEQUENCE', '迁移时序专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5720, 'DATA_MASKING_RULE', '数据脱敏规则专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5721, 'ARCHIVE', '归档专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5722, 'PASSWORD_KEY', '密码秘钥专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5723, 'SMALL_MONTH_BATCH', '小月批专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5724, 'EARLY_MIGRATION', '提前迁移专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5725, 'SILENT_PERIOD', '静默期专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5726, 'VAT_MIGRATION', '增值税迁移专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5727, 'FREEZE_UNFREEZE', '冻结解冻专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5728, 'TOTAL_DETAIL_RECONCILIATION', '总分核对专题', 'DM_TOPIC_PROJECT_TYPE' UNION ALL
    SELECT 5730, 'INTERNAL_ACCOUNT_MIGRATION', '内部账迁移专题', 'DM_TOPIC_SYSTEM_TYPE' UNION ALL
    SELECT 5731, 'RETAIL_LOAN_REPAYMENT_PLAN', '个贷还款计划比对专题', 'DM_TOPIC_SYSTEM_TYPE' UNION ALL
    SELECT 5732, 'CORPORATE_LOAN_REPAYMENT_PLAN', '对公贷款还款计划比对专题', 'DM_TOPIC_SYSTEM_TYPE' UNION ALL
    SELECT 5733, 'DEPOSIT_TRANSACTION_FLOW', '存款交易流水迁移专题', 'DM_TOPIC_SYSTEM_TYPE'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = v.category_code AND t.deleted = 0;

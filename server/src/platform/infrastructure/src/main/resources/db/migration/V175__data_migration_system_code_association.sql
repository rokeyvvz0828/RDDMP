-- V175: 系统关联统一为 (project_id, system_code)，下线 V168/V171 追加的 arch_physical_subsystem.id 引用列。
-- 背景（REQ-20260820-031 T38/T39）：业务表不保存 arch_physical_subsystem.id —— 系统删除并重建后 id 变化，
--   存量关联会失效；统一以 (project_id, system_code) 关联当前项目 dm_component 活动清单，
--   system_code 即 dm_component.physical_subsystem_code（项目内唯一稳定业务键）。
-- 操作：dm_plan 删除 system_id、新增 system_code（项目级空串哨兵），重建 active_dimension_key 生成列与唯一键；
--   dm_issue / dm_target_table 删除 system_id 列与 id 引用索引；dm_component 删除 physical_subsystem_id 列与索引；
--   dm_meeting_system 删除 subsystem_id，改为 project_id + system_code（存量按 dm_meeting / arch_physical_subsystem 编号回填）。
-- 约束：Flyway 只追加，不改历史脚本；全部 information_schema 条件式执行，幂等可重跑。
-- 开发阶段（用户确认）：本地/开发库无需备份；回退以 system_code 数据为准，移除的 id 列如需还原走补偿迁移。

-- ============ 1. dm_plan：system_id -> system_code ============
SET @dm_plan_system_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND column_name = 'system_id'
);
SET @dm_plan_sql = IF(
    @dm_plan_system_id_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_plan
        DROP INDEX uk_dm_plan_active_dimension,
        DROP INDEX idx_dm_plan_system,
        DROP COLUMN active_dimension_key,
        DROP COLUMN system_id,
        ADD COLUMN system_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''关联系统(项目内dm_component活动记录编号)，项目级用空串哨兵'' AFTER plan_type,
        ADD COLUMN active_dimension_key VARCHAR(160)
            GENERATED ALWAYS AS (
                CASE WHEN deleted = 0
                     THEN CONCAT_WS('':'', tenant_id, project_id, granularity, plan_type, system_code)
                     ELSE NULL
                END
            ) STORED COMMENT ''活动维度唯一键(颗粒度+方案类型+关联系统编号，仅未删除取值)'' AFTER active_doc_code,
        ADD UNIQUE KEY uk_dm_plan_active_dimension (active_dimension_key),
        ADD KEY idx_dm_plan_system (tenant_id, project_id, system_code, deleted)'
);
PREPARE dm_plan_stmt FROM @dm_plan_sql;
EXECUTE dm_plan_stmt;
DEALLOCATE PREPARE dm_plan_stmt;

-- ============ 2. dm_issue：下线 system_id ============
SET @dm_issue_system_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND column_name = 'system_id'
);
SET @dm_issue_sql = IF(
    @dm_issue_system_id_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_issue
        DROP INDEX idx_dm_issue_system_id,
        DROP COLUMN system_id'
);
PREPARE dm_issue_stmt FROM @dm_issue_sql;
EXECUTE dm_issue_stmt;
DEALLOCATE PREPARE dm_issue_stmt;

-- ============ 3. dm_target_table：下线 system_id ============
SET @dm_target_table_system_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'system_id'
);
SET @dm_target_table_sql = IF(
    @dm_target_table_system_id_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_target_table
        DROP INDEX idx_target_table_system_id,
        DROP COLUMN system_id'
);
PREPARE dm_target_table_stmt FROM @dm_target_table_sql;
EXECUTE dm_target_table_stmt;
DEALLOCATE PREPARE dm_target_table_stmt;

-- ============ 4. dm_component：下线 physical_subsystem_id ============
SET @dm_component_subsystem_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'physical_subsystem_id'
);
SET @dm_component_sql = IF(
    @dm_component_subsystem_id_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_component
        DROP INDEX idx_dm_component_subsystem_id,
        DROP COLUMN physical_subsystem_id'
);
PREPARE dm_component_stmt FROM @dm_component_sql;
EXECUTE dm_component_stmt;
DEALLOCATE PREPARE dm_component_stmt;

-- ============ 5. dm_meeting_system：subsystem_id -> project_id + system_code ============
-- 表存在守卫：dm_meeting_system 自 V162 才存在，对低基线/无该表的库 5.1-5.4 整体跳过。
SET @dm_meeting_system_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'dm_meeting_system'
);
SET @dm_meeting_system_subsystem_id_exists = IF(
    @dm_meeting_system_table_exists = 0,
    0,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'dm_meeting_system' AND column_name = 'subsystem_id')
);

-- 5.1 追加 project_id / system_code（先于回填）
SET @dm_meeting_system_code_exists = IF(
    @dm_meeting_system_table_exists = 0,
    1,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'dm_meeting_system' AND column_name = 'system_code')
);
SET @dm_meeting_system_add_sql = IF(
    @dm_meeting_system_code_exists = 0,
    'ALTER TABLE dm_meeting_system
        ADD COLUMN project_id BIGINT NOT NULL DEFAULT 0 COMMENT ''所属项目(dm_meeting.project_id)'' AFTER meeting_id,
        ADD COLUMN system_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''项目内系统编号(dm_component活动记录)'' AFTER project_id',
    'SELECT 1'
);
PREPARE dm_meeting_system_add_stmt FROM @dm_meeting_system_add_sql;
EXECUTE dm_meeting_system_add_stmt;
DEALLOCATE PREPARE dm_meeting_system_add_stmt;

-- 5.2 回填 project_id：取自所属会议
SET @dm_meeting_system_backfill_project_sql = IF(
    @dm_meeting_system_table_exists = 0,
    'SELECT 1',
    'UPDATE dm_meeting_system ms
       JOIN dm_meeting m
         ON m.meeting_id = ms.meeting_id AND m.tenant_id = ms.tenant_id
       SET ms.project_id = m.project_id
     WHERE ms.project_id = 0'
);
PREPARE dm_meeting_system_backfill_project_stmt FROM @dm_meeting_system_backfill_project_sql;
EXECUTE dm_meeting_system_backfill_project_stmt;
DEALLOCATE PREPARE dm_meeting_system_backfill_project_stmt;

-- 5.3 回填 system_code：按旧 subsystem_id -> arch code（保留人工修正值，仅回填空串）
SET @dm_meeting_system_backfill_code_sql = IF(
    @dm_meeting_system_table_exists = 0 OR @dm_meeting_system_subsystem_id_exists = 0,
    'SELECT 1',
    'UPDATE dm_meeting_system ms
       JOIN arch_physical_subsystem s
         ON s.id = ms.subsystem_id AND s.tenant_id = ms.tenant_id
       SET ms.system_code = s.code
     WHERE ms.system_code = '''' AND s.deleted = 0'
);
PREPARE dm_meeting_system_backfill_code_stmt FROM @dm_meeting_system_backfill_code_sql;
EXECUTE dm_meeting_system_backfill_code_stmt;
DEALLOCATE PREPARE dm_meeting_system_backfill_code_stmt;

-- 5.4 下线 subsystem_id，重建约束与索引
SET @dm_meeting_system_rekey_sql = IF(
    @dm_meeting_system_table_exists = 0 OR @dm_meeting_system_subsystem_id_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_meeting_system
        DROP INDEX uk_dm_meeting_system,
        DROP INDEX idx_dm_meeting_system_subsystem,
        DROP COLUMN subsystem_id,
        ADD UNIQUE KEY uk_dm_meeting_system (tenant_id, meeting_id, system_code),
        ADD KEY idx_dm_meeting_system_project (tenant_id, project_id, system_code)'
);
PREPARE dm_meeting_system_rekey_stmt FROM @dm_meeting_system_rekey_sql;
EXECUTE dm_meeting_system_rekey_stmt;
DEALLOCATE PREPARE dm_meeting_system_rekey_stmt;

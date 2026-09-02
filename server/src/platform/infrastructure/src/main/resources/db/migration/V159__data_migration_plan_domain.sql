-- V159: 迁移方案域化改造（并入 REQ-20260820-031，对标会议纪要/汇报材料）
-- 背景：dm_plan 原为通用文件型内容表（仅 project_id/component_id/doc_code/doc_name/checksum_md5/owner_id），
--   不满足「迁移方案」新增的业务维度：资产颗粒度、迁移方案类型、关联系统、方案简介，
--   以及「(颗粒度 + 方案类型 + 关联系统) 仅一条」的活动域唯一约束。
-- 约束：Flyway 只追加，不改历史脚本；参照 V103/V94 生成列做法，唯一性仅约束未删除记录，允许软删-重建。
-- 存量：dm_plan 经确认为空表（开发环境 0 行），故只加列、加约束，不回填历史数据。

ALTER TABLE dm_plan
    ADD COLUMN granularity  VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '资产颗粒度 PROJECT=项目级/SYSTEM=系统级' AFTER component_id,
    ADD COLUMN plan_type    VARCHAR(16)  NOT NULL DEFAULT 'DATA'    COMMENT '迁移方案类型 BUSINESS=业务迁移方案/DATA=数据迁移方案' AFTER granularity,
    ADD COLUMN system_id    BIGINT       NOT NULL DEFAULT 0         COMMENT '关联系统(arch_physical_subsystem.id)，项目级用0哨兵' AFTER plan_type,
    ADD COLUMN plan_summary VARCHAR(1000) NULL                       COMMENT '方案简介' AFTER system_id;

-- 活动维度唯一键：项目级 system_id=0 哨兵规避 MySQL 唯一索引对 NULL 判不同；软删行取 NULL 不参与唯一。
ALTER TABLE dm_plan
    ADD COLUMN active_dimension_key VARCHAR(160)
        GENERATED ALWAYS AS (
            CASE WHEN deleted = 0
                 THEN CONCAT_WS(':', tenant_id, project_id, granularity, plan_type, system_id)
                 ELSE NULL
            END
        ) STORED COMMENT '活动维度唯一键(颗粒度+方案类型+关联系统，仅未删除取值)',
    ADD UNIQUE KEY uk_dm_plan_active_dimension (active_dimension_key);

-- 组合筛选与关联系统检索索引。
ALTER TABLE dm_plan
    ADD KEY idx_dm_plan_dimension (tenant_id, project_id, granularity, plan_type, deleted),
    ADD KEY idx_dm_plan_system (tenant_id, system_id, deleted);

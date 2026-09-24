-- =====================================================================
-- V163 批次4·风险管理 + 风险策略库（基线第 14 章）
-- 1) 5 张表：risk / risk_attachment / risk_strategy / risk_prevent_record / risk_strategy_lib
-- 2) 菜单 752「风险台账」挂 745 + 权限点 7521-7524
-- 3) 与问题域共用 reconcile 协议；6 态互斥；等级/概率单值约束
-- =====================================================================

-- 1) 风险台账主表（6 态互斥；等级/概率单值 CHECK）
CREATE TABLE IF NOT EXISTS risk (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    risk_code VARCHAR(64) NOT NULL COMMENT '风险唯一编码',
    risk_title VARCHAR(200) NOT NULL,
    risk_source VARCHAR(16) NOT NULL COMMENT 'MANUAL/ORDER_SYNC',
    project_id BIGINT NULL,
    business_group_id BIGINT NULL,
    component_id BIGINT NULL COMMENT '组件级风险展示（颗粒度继承 CK）',
    order_id BIGINT NULL,
    process_seq INT NULL,
    risk_level VARCHAR(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW（单一值）',
    probability VARCHAR(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW 发生概率（单一值）',
    impact_scope TEXT NOT NULL,
    risk_desc TEXT NOT NULL,
    potential_harm TEXT NULL,
    predicted_scene TEXT NULL,
    attachment_ids JSON NULL,
    reporter_id BIGINT NOT NULL,
    prevent_owner_id BIGINT NOT NULL COMMENT '防控责任人',
    reported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    prevented_at DATETIME(6) NULL,
    pre_prevent_measure TEXT NULL COMMENT '前置防控措施',
    response_strategy TEXT NULL COMMENT '标准化应对策略',
    degrade_plan TEXT NULL COMMENT '风险降级方案',
    emergency_plan TEXT NULL COMMENT '应急处置预案',
    prevent_priority VARCHAR(16) NULL COMMENT '防控优先级 HIGH/MEDIUM/LOW',
    dispose_deadline DATETIME(6) NULL,
    prevent_progress TEXT NULL,
    prevent_records JSON NULL,
    prevent_attachment_ids JSON NULL,
    risk_status VARCHAR(16) NOT NULL DEFAULT 'WAIT_PREVENT' COMMENT '6 态，见 3.6（AVOIDED/OCCURRED/CLOSED 互斥）',
    snapshot_version VARCHAR(64) NULL,
    change_log JSON NULL,
    synced_report_id BIGINT NULL COMMENT '反向关联 process_risk_report 上报单（reconcile 幂等锚点）',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_risk_granularity_component CHECK (
        (component_id IS NULL AND order_id IS NULL) OR (component_id IS NOT NULL)
    ),
    CONSTRAINT ck_risk_sync_report CHECK (
        (risk_source = 'ORDER_SYNC' AND synced_report_id IS NOT NULL) OR (risk_source <> 'ORDER_SYNC')
    ),
    CONSTRAINT ck_rse_origin_source CHECK (risk_source IN ('MANUAL', 'ORDER_SYNC')),
    CONSTRAINT ck_risk_level_single CHECK (risk_level IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_risk_probability_single CHECK (probability IN ('HIGH', 'MEDIUM', 'LOW')),
    UNIQUE KEY uk_risk_code (tenant_id, risk_code),
    KEY idx_risk_list (tenant_id, deleted, risk_status, reported_at),
    KEY idx_risk_sync (tenant_id, synced_report_id, deleted)
) COMMENT='风险台账（双渠道归集，等级/概率单值，6 态互斥）';

-- 2) 风险佐证附件占位
CREATE TABLE IF NOT EXISTS risk_attachment (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    risk_id BIGINT NOT NULL,
    attachment_ref VARCHAR(255) NOT NULL,
    file_type VARCHAR(64) NULL,
    file_size BIGINT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_risk_attachment (tenant_id, risk_id, deleted)
) COMMENT='风险佐证附件（占位）';

-- 3) 风险策略实例（管理员配置的策略快照，闭环后固化归档）
CREATE TABLE IF NOT EXISTS risk_strategy (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    risk_id BIGINT NOT NULL,
    strategy_code VARCHAR(64) NOT NULL,
    pre_prevent_measure TEXT NOT NULL,
    response_strategy TEXT NULL,
    degrade_plan TEXT NULL,
    emergency_plan TEXT NULL,
    create_by BIGINT NOT NULL,
    updated_by BIGINT NULL,
    change_log JSON NULL COMMENT '策略修改留痕',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_risk_strategy (tenant_id, risk_id, strategy_code),
    KEY idx_risk_strategy_risk (tenant_id, risk_id, deleted)
) COMMENT='风险应对策略实例（随风险闭环归档不可删改）';

-- 4) 防控落地执行记录（追加式，永久留存）
CREATE TABLE IF NOT EXISTS risk_prevent_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    risk_id BIGINT NOT NULL,
    record TEXT NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_risk_prevent_record (tenant_id, risk_id, deleted)
) COMMENT='风险防控落地执行记录（不可清空仅可补充）';

-- 5) 全局风险策略库（同类风险智能匹配与一键复用；普通用户只读）
CREATE TABLE IF NOT EXISTS risk_strategy_lib (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_title VARCHAR(200) NOT NULL,
    risk_title_pattern VARCHAR(255) NULL COMMENT '风险标题关键词模式（匹配）',
    match_keywords JSON NULL COMMENT '匹配关键词集',
    risk_level VARCHAR(16) NULL,
    probability VARCHAR(16) NULL,
    pre_prevent_measure TEXT NOT NULL,
    response_strategy TEXT NULL,
    degrade_plan TEXT NULL,
    emergency_plan TEXT NULL,
    use_count INT NOT NULL DEFAULT 0 COMMENT '一键复用次数',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INVALID（禁止删除，仅可下线）',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_strategy_lib_code (tenant_id, strategy_code),
    KEY idx_strategy_lib_active (tenant_id, status, deleted)
) COMMENT='全局风险策略库（策略沉淀与智能匹配复用）';

-- 6) 菜单 752「风险台账」挂 745 + 权限点
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (752, 1, 745, 'menu', '风险台账', 'DataMigrationLifecycleRisk', '/data-migration/lifecycle/risk', 'data-migration', 'data-migration-lifecycle:risk', 'warning', 70);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 752, 1), (200, 752, 1), (201, 752, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7521, 1, 752, 'read', 'data-migration-lifecycle:risk', '风险台账访问'),
    (7522, 1, 752, 'create', 'data-migration-lifecycle:risk:create', '风险新增/归集'),
    (7523, 1, 752, 'update', 'data-migration-lifecycle:risk:prevent', '防控推进/关闭'),
    (7524, 1, 752, 'audit', 'data-migration-lifecycle:risk:strategy', '策略库维护（仅管理员）');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 752 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 752 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 752 AND status = 1 AND action_code = 'read';

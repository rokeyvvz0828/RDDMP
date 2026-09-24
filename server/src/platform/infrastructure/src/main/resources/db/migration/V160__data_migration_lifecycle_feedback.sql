-- REQ-20260923-001 批次3·任务进度反馈（基线文档第 12 章）：
-- 1) 5 张表：process_feedback / work_log / feedback_attachment / process_issue_report / process_risk_report
--    （问题与风险为两张独立表，不共用状态机，铁律 #2）；
-- 2) 菜单 749「执行反馈」挂 745 + 权限点 7491-7494；
-- 3) synced_issue_id / synced_risk_id 预留被动同步字段（批次4 问题/风险台账消费，本期值为 NULL，降级路径可达）。
-- 仅追加，不修改已发布脚本；状态一律走独立枚举字段。

-- 1) 工序反馈实例（闭环后 is_locked=1 锁定编辑）
CREATE TABLE IF NOT EXISTS process_feedback (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    progress_desc TEXT NOT NULL COMMENT '工序执行进度/作业完成情况/实施进展描述（必填，支持多次迭代）',
    exit_content_filled TEXT NOT NULL COMMENT '准出标准内容填报（准出结果/执行结论/合规判定/落地说明）',
    deliverable_ids JSON NULL COMMENT '交付物附件 id（must_submit_deliverable=true 时必填）',
    work_log JSON NULL COMMENT '作业日志条目（内容/时间/操作人），追加式',
    attachment_ids JSON NULL COMMENT '通用附件 id（截图/报表/备案资料）',
    extra_remark TEXT NULL COMMENT '作业补充备注',
    filled_by BIGINT NOT NULL,
    filled_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_locked TINYINT NOT NULL DEFAULT 0 COMMENT '阶段锁定：工序闭环后置 true，禁止再编辑',
    snapshot_version VARCHAR(32) NOT NULL COMMENT '遵循的工序快照版本',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_process_feedback (tenant_id, order_id, process_seq, deleted),
    KEY idx_process_feedback_order (tenant_id, order_id, deleted)
) COMMENT='工序执行反馈（闭环判定唯一执行依据）';

-- 2) 作业日志（追加式留痕，不可清空）
CREATE TABLE IF NOT EXISTS work_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_work_log (tenant_id, order_id, process_seq, deleted)
) COMMENT='作业日志（仅追加，不可删除）';

-- 3) 附件占位（真实文件流批次4 接入 platform/attachment，本期以 file_ref 留痕）
CREATE TABLE IF NOT EXISTS feedback_attachment (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    file_ref VARCHAR(255) NOT NULL COMMENT '附件文件引用（ID 留痕）',
    file_name VARCHAR(255) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_feedback_attachment (tenant_id, order_id, process_seq, deleted)
) COMMENT='反馈附件占位（文件流后续接入）';

-- 4) 问题上报（独立台账；三项必填：标题/描述/发生场景；不可清空仅可补充）
CREATE TABLE IF NOT EXISTS process_issue_report (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    issue_title VARCHAR(200) NOT NULL,
    issue_desc TEXT NOT NULL,
    scene TEXT NOT NULL COMMENT '发生场景（必填）',
    impact_scope TEXT NULL,
    attachment_ids JSON NULL,
    reporter_id BIGINT NOT NULL,
    reported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    admin_advice TEXT NULL COMMENT '管理员整改建议/解决方案/处置意见（仅管理员可录入，固化归档）',
    synced_issue_id BIGINT NULL COMMENT '同步至问题管理模块台账 ID（批次4 消费，本期 NULL）',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_process_issue_report (tenant_id, order_id, process_seq, deleted),
    KEY idx_process_issue_report_global (tenant_id, deleted)
) COMMENT='工序问题上报（独立台账，不可删除）';

-- 5) 风险上报（独立台账；五项必填：标题/等级/描述/概率/影响范围；单工序多条）
CREATE TABLE IF NOT EXISTS process_risk_report (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    risk_title VARCHAR(200) NOT NULL,
    risk_level VARCHAR(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW（必填）',
    risk_desc TEXT NOT NULL,
    probability VARCHAR(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW 发生概率（必填）',
    impact_scope TEXT NOT NULL,
    attachment_ids JSON NULL,
    prepared_measure TEXT NULL COMMENT '整改预备措施',
    strategy_text TEXT NULL COMMENT '管理员标准风险应对策略（归档固化）',
    reporter_id BIGINT NOT NULL,
    reported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    synced_risk_id BIGINT NULL COMMENT '同步至风险管理模块台账 ID（批次4 消费，本期 NULL）',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_process_risk_report (tenant_id, order_id, process_seq, deleted),
    KEY idx_process_risk_report_global (tenant_id, deleted)
) COMMENT='工序风险上报（独立台账，多条留痕，不可删除）';

-- 6) 菜单 749「执行反馈」挂 745 + 权限点
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (749, 1, 745, 'menu', '执行反馈', 'DataMigrationLifecycleFeedback', '/data-migration/lifecycle/feedback', 'data-migration', 'data-migration-lifecycle:feedback', 'edit-pen', 40);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 749, 1), (200, 749, 1), (201, 749, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7491, 1, 749, 'read', 'data-migration-lifecycle:feedback', '执行反馈访问'),
    (7492, 1, 749, 'fill', 'data-migration-lifecycle:feedback:fill', '填写执行反馈'),
    (7493, 1, 749, 'update', 'data-migration-lifecycle:feedback:submit-audit', '提审'),
    (7494, 1, 749, 'create', 'data-migration-lifecycle:feedback:report', '问题/风险上报');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 749 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 749 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 749 AND status = 1 AND action_code = 'read';

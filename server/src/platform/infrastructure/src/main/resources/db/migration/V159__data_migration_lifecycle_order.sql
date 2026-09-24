-- REQ-20260923-001 批次3·任务流转引擎（基线文档第 11 章 + 18.2/18.3/18.4）：
-- 1) 8 张表：work_order / order_process_instance / order_status_log / order_transfer_log /
--    order_suspend_log / order_sla_log / order_restart_log / notification；
-- 2) 派生视图 v_order_deadline_live（18.3 口径与冗余字段一致性校验）；
-- 3) 菜单 748「工单流转」挂 745 + 权限点 7481-7484；
-- 4) 表级 CHECK 兜底：颗粒度-组件互斥 / 暂停态必须携带暂停前状态 / 终态闭环时间 / 工序交付物与准出闸门 /
--    未解锁工序不得持有解锁时间（11.4 铁律）。
-- 仅追加，不修改已发布脚本；状态一律走独立枚举字段，禁止 is_* 状态布尔拆分。

-- 1) 工单主表
CREATE TABLE IF NOT EXISTS work_order (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_code VARCHAR(64) NOT NULL COMMENT '工单编号 WO-######，系统自动生成',
    task_id BIGINT NOT NULL,
    granularity VARCHAR(16) NOT NULL,
    activity_type VARCHAR(16) NOT NULL,
    activity_id BIGINT NOT NULL,
    activity_name VARCHAR(160) NOT NULL,
    sub_activity_id BIGINT NULL COMMENT '本工单执行标准的来源普通活动（专题拆分必填）',
    topic_activity_ids JSON NULL,
    aggregate_edges JSON NULL,
    snapshot_version VARCHAR(32) NOT NULL COMMENT '工单级快照版本（与 activity_snapshot 1:1 语义）',
    snapshot_json JSON NOT NULL COMMENT '工单级固化执行配置快照（自描述，禁止被任务快照回填覆盖）',
    project_id BIGINT NOT NULL,
    business_group_id BIGINT NULL,
    component_id BIGINT NULL COMMENT '仅组件级工单必填（颗粒度-组件互斥 CHECK）',
    default_executor_id BIGINT NOT NULL,
    current_executor_id BIGINT NOT NULL,
    next_executor_id BIGINT NULL COMMENT '转交目标人（转交后需二次确认生效）',
    participant_ids JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    flow_start_at DATETIME(6) NULL COMMENT '流转启动时间',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    closed_at DATETIME(6) NULL COMMENT '闭环时间（终态必填，语义=最后一道闭环工序闭环时间）',
    order_status VARCHAR(16) NOT NULL DEFAULT 'WAIT_PRE' COMMENT '9 态（基线 3.2）',
    deadline_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '时效三档辅助标记（基线 3.8，独立维度不改主状态）',
    plan_finish_time DATETIME(6) NOT NULL,
    sla_exempt TINYINT NOT NULL DEFAULT 0,
    total_process_count INT NOT NULL DEFAULT 0,
    closed_process_count INT NOT NULL DEFAULT 0,
    flow_progress DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    suspend_at DATETIME(6) NULL,
    resume_at DATETIME(6) NULL,
    suspend_before_status VARCHAR(16) NULL COMMENT '暂停前状态（SUSPENDED 态必须非空）',
    suspend_hours DECIMAL(8,2) NOT NULL DEFAULT 0.00 COMMENT '暂停累计时长（小时），SLA 顺延依据',
    restart_log JSON NULL,
    block_reason VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PRECONDITION_OPEN/DELIVERABLE_MISSING/AUDIT_REJECTED',
    created_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_order_code (tenant_id, order_code, deleted),
    KEY idx_order_query (tenant_id, order_status, deadline_status, granularity, activity_type, project_id, deleted),
    KEY idx_order_executor (tenant_id, current_executor_id, deleted),
    KEY idx_order_task (tenant_id, task_id, deleted),
    CONSTRAINT ck_order_granularity_component CHECK (
        (granularity = 'PROJECT' AND component_id IS NULL)
        OR (granularity = 'COMPONENT' AND component_id IS NOT NULL)),
    CONSTRAINT ck_order_suspended_has_before CHECK (
        (order_status = 'SUSPENDED' AND suspend_before_status IS NOT NULL)
        OR order_status <> 'SUSPENDED'),
    CONSTRAINT ck_order_closed_at CHECK (
        (order_status IN ('CLOSED', 'ARCHIVED') AND closed_at IS NOT NULL)
        OR order_status NOT IN ('CLOSED', 'ARCHIVED'))
) COMMENT='数据迁移工单（工单级快照固化执行配置）';

-- 2) 工序实例表
CREATE TABLE IF NOT EXISTS order_process_instance (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    process_name VARCHAR(160) NOT NULL,
    process_status VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT '5 态（基线 3.3）',
    pre_depend_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/OPEN/CLOSED',
    exit_filled TINYINT NOT NULL DEFAULT 0,
    deliverable_submitted TINYINT NOT NULL DEFAULT 0,
    audit_status VARCHAR(16) NOT NULL DEFAULT 'WAIT_REVIEW' COMMENT '4 态（基线 3.7）',
    must_audit TINYINT NOT NULL DEFAULT 1 COMMENT '因子 A：取自工单固化快照，禁止回写',
    must_submit_deliverable TINYINT NOT NULL DEFAULT 1 COMMENT '因子 B：取自工单固化快照，禁止回写',
    reject_count INT NOT NULL DEFAULT 0,
    unlocked_at DATETIME(6) NULL COMMENT '工序解锁时间（LOCKED ⇒ 为空）',
    closed_at DATETIME(6) NULL COMMENT '工序闭环时间（CLOSED ⇒ 非空）',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_opi_order_seq (tenant_id, order_id, process_seq, deleted),
    KEY idx_opi_status (tenant_id, order_id, process_status, deleted),
    CONSTRAINT ck_opi_unlock CHECK (
        (process_status = 'LOCKED' AND unlocked_at IS NULL) OR process_status <> 'LOCKED'),
    CONSTRAINT ck_opi_closed CHECK (
        (process_status = 'CLOSED' AND closed_at IS NOT NULL) OR process_status <> 'CLOSED'),
    CONSTRAINT ck_opi_exit_gate CHECK (
        (process_status = 'CLOSED' AND exit_filled = 1)
        OR (process_status = 'CLOSED' AND must_submit_deliverable = 0)
        OR process_status <> 'CLOSED'),
    CONSTRAINT ck_opi_deliverable_gate CHECK (
        (process_status = 'CLOSED' AND must_submit_deliverable = 0)
        OR (process_status = 'CLOSED' AND deliverable_submitted = 1)
        OR process_status <> 'CLOSED')
) COMMENT='工单工序实例（流转依据=工单快照，非实时活动配置）';

-- 3) 状态留痕
CREATE TABLE IF NOT EXISTS order_status_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NULL,
    actor_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_order_status_log (tenant_id, order_id, deleted)
) COMMENT='工单状态变更留痕';

CREATE TABLE IF NOT EXISTS order_transfer_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    from_member_id BIGINT NOT NULL,
    to_member_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_order_transfer_log (tenant_id, order_id, deleted)
) COMMENT='工单转交留痕（原因必填）';

CREATE TABLE IF NOT EXISTS order_suspend_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    suspend_at DATETIME(6) NOT NULL,
    resume_at DATETIME(6) NULL,
    suspend_before_status VARCHAR(16) NULL,
    pre_suspend_processes JSON NULL COMMENT '暂停前工序状态快照（未闭环工序 status+unlocked_at，恢复唯一回退依据）',
    old_plan_finish_time DATETIME(6) NULL,
    new_plan_finish_time DATETIME(6) NULL,
    suspend_hours DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    actor_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_order_suspend_log (tenant_id, order_id, deleted)
) COMMENT='工单暂停/恢复留痕（SLA 顺延依据）';

CREATE TABLE IF NOT EXISTS order_sla_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    from_deadline VARCHAR(16) NULL,
    to_deadline VARCHAR(16) NULL,
    plan_finish_time DATETIME(6) NULL,
    reason VARCHAR(500) NULL,
    actor_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_order_sla_log (tenant_id, order_id, deleted)
) COMMENT='工单时效调整/豁免留痕（原因必填）';

CREATE TABLE IF NOT EXISTS order_restart_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    restart_point_seq INT NOT NULL COMMENT '恢复点工序序号（恢复点之后工序被重置）',
    reason VARCHAR(500) NOT NULL,
    actor_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_order_restart_log (tenant_id, order_id, deleted)
) COMMENT='工单异常重启留痕（恢复点+原因+操作人+时间）';

-- 4) 通知（SLA 预警/异常分支通知，幂等唯一键）
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    notify_type VARCHAR(32) NOT NULL COMMENT 'SLA_NEAR_OVERDUE/SLA_OVERDUE/ORDER_SUSPEND/ORDER_TRANSFER/ORDER_RESTART/ORDER_ADJUST_SLA',
    content VARCHAR(1000) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'system',
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_notification_idempotent (tenant_id, order_id, receiver_id, notify_type, deleted),
    KEY idx_notification_receiver (tenant_id, receiver_id, is_read, deleted)
) COMMENT='系统消息中心通知（SLA 预警幂等）';

-- 5) 任务-工单进度聚合视图（任务台账口径：总工单/已闭环工单/进度；依赖 V158 task 与本节 work_order）
CREATE OR REPLACE VIEW v_task_order_progress AS
SELECT t.id AS task_id, t.tenant_id,
       COUNT(o.id) AS order_total,
       SUM(CASE WHEN o.order_status IN ('CLOSED', 'ARCHIVED') THEN 1 ELSE 0 END) AS order_closed
FROM task t
LEFT JOIN work_order o ON o.task_id = t.id AND o.tenant_id = t.tenant_id AND o.deleted = 0
WHERE t.deleted = 0
GROUP BY t.id, t.tenant_id;

-- 6) 时效实时派生视图（18.3 口径：与 work_order.deadline_status 冗余字段必须一致）
CREATE OR REPLACE VIEW v_order_deadline_live AS
SELECT id AS order_id, tenant_id,
       CASE
           WHEN sla_exempt = 1 OR order_status IN ('CLOSED', 'ARCHIVED', 'CANCELLED') OR order_status = 'SUSPENDED' THEN 'NORMAL'
           WHEN plan_finish_time IS NULL OR plan_finish_time > DATE_ADD(NOW(), INTERVAL 24 HOUR) THEN 'NORMAL'
           WHEN plan_finish_time > NOW() THEN 'NEAR_OVERDUE'
           ELSE 'OVERDUE'
       END AS deadline_status_live,
       plan_finish_time
FROM work_order
WHERE deleted = 0;

-- 7) 菜单 748「工单流转」挂 745 + 权限点
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (748, 1, 745, 'menu', '工单流转', 'DataMigrationLifecycleFlow', '/data-migration/lifecycle/flow', 'data-migration', 'data-migration-lifecycle:order', 'guide', 30);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 748, 1), (200, 748, 1), (201, 748, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7481, 1, 748, 'read', 'data-migration-lifecycle:order', '工单台账访问'),
    (7482, 1, 748, 'update', 'data-migration-lifecycle:order:intervene', '工单异常干预（暂停/转交/重启/时效）'),
    (7483, 1, 748, 'create', 'data-migration-lifecycle:order:feedback', '工序执行反馈'),
    (7484, 1, 748, 'audit', 'data-migration-lifecycle:order:review', '审核数据查看');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 748 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 748 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 748 AND status = 1 AND action_code = 'read';

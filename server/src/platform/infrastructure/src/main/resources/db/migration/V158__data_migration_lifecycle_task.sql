-- REQ-20260923-001 批次3·任务发布（基线文档第 10 章）：
-- 1) 5 张表：task / task_snapshot / task_person_rel / topic_aggregate_edge
--    （任务台账聚合视图 v_task_order_progress 依赖 work_order，随 V159 订单表落地后创建）；
-- 2) 菜单 747「任务下发」挂 745 + 权限点 7471-7474；
-- 3) 快照固化契约：task_snapshot 承载「任务级快照」（专题为聚合包），工单级执行快照在 V159 work_order.snapshot_json，
--    两条路径互不覆盖（铁律 #15）。
-- 仅追加，不修改已发布脚本；状态一律走独立枚举字段，禁止 is_* 状态布尔拆分。组件任务按「单一组件单一工单」拆分。

-- 1) 任务主表
CREATE TABLE IF NOT EXISTS task (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    task_code VARCHAR(64) NOT NULL COMMENT '任务编号 TK-YYYYMMDD-###，系统自动生成',
    task_name VARCHAR(200) NOT NULL,
    granularity VARCHAR(16) NOT NULL COMMENT 'PROJECT/COMPONENT，由活动颗粒度强制继承',
    activity_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/TOPIC',
    activity_id BIGINT NOT NULL COMMENT '普通活动或专题活动 id',
    activity_name VARCHAR(160) NOT NULL,
    topic_activity_id BIGINT NULL COMMENT '任务来源为专题聚合活动时记录专题 id',
    project_id BIGINT NOT NULL COMMENT '所属项目 pm_project.id',
    business_group_id BIGINT NULL COMMENT '所属事业群',
    component_id BIGINT NULL COMMENT '组件级单组件任务填组件（多组件聚合任务为空，组件归属下沉到各工单）',
    default_executor_id BIGINT NOT NULL COMMENT '默认执行人（来自组件预设人员/下发人指定）',
    current_executor_id BIGINT NOT NULL COMMENT '当前执行人（可临时微调，仅本工单生效）',
    plan_finish_time DATETIME(6) NOT NULL COMMENT '计划完成时间（必填，不得早于当前）',
    task_status VARCHAR(16) NOT NULL DEFAULT 'WAIT_PRE' COMMENT '任务整体状态（9 态，基线 3.2），由工单聚合派生',
    current_process_seq INT NULL,
    finished_process_count INT NOT NULL DEFAULT 0,
    total_process_count INT NOT NULL DEFAULT 0,
    close_progress DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    snapshot_version VARCHAR(32) NOT NULL COMMENT '任务级快照版本',
    flow_constraint_desc VARCHAR(500) NULL COMMENT '流转约束说明（规则溯源）',
    creator_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_code (tenant_id, task_code, deleted),
    KEY idx_task_query (tenant_id, granularity, activity_type, task_status, project_id, deleted),
    KEY idx_task_activity (tenant_id, activity_id, deleted)
) COMMENT='数据迁移任务（下发唯一入口产物）';

-- 2) 任务快照（任务级：普通活动=活动快照同源；专题=聚合包；禁止与工单快照互相覆盖）
CREATE TABLE IF NOT EXISTS task_snapshot (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    task_id BIGINT NOT NULL,
    snapshot_version VARCHAR(32) NOT NULL,
    sub_activity_id BIGINT NULL COMMENT '专题下发时被展开的普通活动 id',
    topic_activity_ids JSON NULL COMMENT '专题聚合涉及的普通活动 id 列表',
    aggregate_edges JSON NULL COMMENT '专题跨活动流转依赖（活动间）',
    snapshot_json JSON NOT NULL COMMENT '自描述载荷（schemaVersion+entityType+entityId+frozenAt+topologyVersion）',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_snapshot_version (tenant_id, task_id, snapshot_version, deleted),
    KEY idx_task_snapshot_task (tenant_id, task_id, deleted)
) COMMENT='任务级快照（下发即冻结，禁止回填覆盖）';

-- 3) 任务执行人/参与人关系（成员唯一数据源：仅已激活成员）
CREATE TABLE IF NOT EXISTS task_person_rel (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    task_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    person_role VARCHAR(16) NOT NULL COMMENT 'EXECUTOR/PARTICIPANT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_person (tenant_id, task_id, member_id, person_role, deleted),
    KEY idx_task_person_member (tenant_id, member_id, deleted)
) COMMENT='任务执行人/参与人关系';

-- 3.5) 专题跨活动聚合依赖（仅专题生效；来源活动全部闭环后解锁目标活动工单，D-21）
CREATE TABLE IF NOT EXISTS topic_aggregate_edge (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    topic_activity_id BIGINT NOT NULL COMMENT '专题聚合活动 id',
    source_activity_id BIGINT NOT NULL COMMENT '跨活动依赖来源普通活动',
    target_activity_id BIGINT NOT NULL COMMENT '跨活动依赖目标普通活动',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_topic_edge (tenant_id, topic_activity_id, source_activity_id, target_activity_id, deleted),
    KEY idx_topic_edge_query (tenant_id, topic_activity_id, deleted)
) COMMENT='专题跨活动聚合依赖（任务下发时随快照固化到工单，流转依据为工单快照）';

-- 4) 菜单 747「任务下发」挂 745 + 权限点（read/create/update/delete）
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (747, 1, 745, 'menu', '任务下发', 'DataMigrationLifecycleTask', '/data-migration/lifecycle/task', 'data-migration', 'data-migration-lifecycle:task', 'promotion', 20);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 747, 1), (200, 747, 1), (201, 747, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7471, 1, 747, 'read', 'data-migration-lifecycle:task', '任务台账访问'),
    (7472, 1, 747, 'create', 'data-migration-lifecycle:task:create', '任务下发'),
    (7473, 1, 747, 'update', 'data-migration-lifecycle:task:update', '任务台账维护'),
    (7474, 1, 747, 'delete', 'data-migration-lifecycle:task:delete', '任务作废');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 747 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 747 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 747 AND status = 1 AND action_code = 'read';

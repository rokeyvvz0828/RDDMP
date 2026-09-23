-- REQ-20260923-001 批次2·活动管理（基线文档第 9 章）：
-- 1) 9 张表：lifecycle_stage / activity / activity_component_rel / activity_process /
--    activity_process_dep / activity_topology / activity_snapshot / activity_topic_rel / activity_template；
-- 2) 3 个契约视图：v_activity_available / v_process_option / v_activity_component_check；
-- 3) 菜单 746「活动管理」挂 745 目录 + 权限点（read/create/update/delete）；
-- 4) 生命周期阶段种子（普通活动必填、专题活动非必填；值域为本批次实现决策，账本已登记）。
-- 仅追加，不修改已发布脚本；幂等可重复执行。状态一律走独立枚举字段，禁止 is_closed/is_voided 等状态布尔拆分。

-- 1) 生命周期阶段字典
CREATE TABLE IF NOT EXISTS lifecycle_stage (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    stage_code VARCHAR(32) NOT NULL COMMENT '阶段编码',
    stage_name VARCHAR(64) NOT NULL COMMENT '阶段名称',
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lifecycle_stage_code (tenant_id, stage_code, deleted),
    KEY idx_lifecycle_stage_sort (tenant_id, sort_no, deleted)
) COMMENT='数据迁移生命周期阶段';

INSERT IGNORE INTO lifecycle_stage (id, tenant_id, stage_code, stage_name, sort_no)
VALUES
    (1, 1, 'PLAN', '规划分析', 10),
    (2, 1, 'DESIGN', '方案设计', 20),
    (3, 1, 'DEVELOP', '开发实现', 30),
    (4, 1, 'TEST', '测试验证', 40),
    (5, 1, 'REHEARSAL', '演练试运行', 50),
    (6, 1, 'RELEASE', '投产发布', 60),
    (7, 1, 'OPERATION', '运营维护', 70);

-- 2) 活动主表
CREATE TABLE IF NOT EXISTS activity (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    activity_code VARCHAR(64) NOT NULL COMMENT '活动编码 ACT-PRJ-###/ACT-CMP-###/ACT-TPC-###，全局唯一不可修改',
    activity_name VARCHAR(160) NOT NULL,
    activity_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/TOPIC，预留 APPROVAL/INTEGRATION',
    lifecycle_stage_id BIGINT NULL COMMENT '普通活动必填，专题活动非必填',
    granularity VARCHAR(16) NOT NULL COMMENT 'PROJECT/COMPONENT，创建后不可变更',
    activity_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/OBSOLETE（基线 3.4）',
    scene VARCHAR(500) NULL,
    goal VARCHAR(1000) NULL,
    overall_entry_cond TEXT NULL,
    overall_exit_desc TEXT NULL,
    overall_deliverables TEXT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_code (tenant_id, activity_code, deleted),
    KEY idx_activity_query (tenant_id, activity_type, granularity, activity_status, lifecycle_stage_id, deleted),
    KEY idx_activity_owner (tenant_id, created_by, deleted)
) COMMENT='数据迁移生命周期活动（任务标准模板）';

-- 3) 活动-组件绑定（组件级活动）
CREATE TABLE IF NOT EXISTS activity_component_rel (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    activity_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL COMMENT 'dm_component.id（deleted=0 可用，D2）',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_component (tenant_id, activity_id, component_id, deleted),
    KEY idx_activity_component_component (tenant_id, component_id, deleted)
) COMMENT='组件级活动绑定组件（多对多）';

-- 4) 工序
CREATE TABLE IF NOT EXISTS activity_process (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    activity_id BIGINT NOT NULL,
    seq INT NOT NULL COMMENT '工序序号，删除后重排 1..n 连续',
    process_name VARCHAR(160) NOT NULL COMMENT '同活动内唯一',
    owner_role_id BIGINT NULL COMMENT '工序负责人角色 sys_role.id（仅 ACTIVE）',
    is_required TINYINT NOT NULL DEFAULT 1 COMMENT '是否必选节点',
    entry_config JSON NULL COMMENT '准入配置：前置输入条件/依赖说明/所需资料清单',
    is_no_predecessor TINYINT NOT NULL DEFAULT 1 COMMENT '是否无前置任务：依赖连线派生值，非独立可编辑字段',
    exec_config JSON NULL COMMENT '执行配置：执行/参与角色、标准步骤、作业指南、注意事项',
    exit_content TEXT NOT NULL COMMENT '准出内容（准出三要素之一）',
    exit_deliverable_list JSON NULL COMMENT '准出交付物清单（准出三要素之一）',
    qualified_rule TEXT NOT NULL COMMENT '合格判定规则（准出三要素之一）',
    must_audit TINYINT NOT NULL DEFAULT 1 COMMENT '因子A：是否需审核',
    must_submit_deliverable TINYINT NOT NULL DEFAULT 1 COMMENT '因子B：是否需提交交付件',
    deliverable_template_id BIGINT NULL COMMENT '交付物标准模板（选填，仅作业参考，不参与流转判定）',
    config_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/READY 辅助标记（基线 3.9）',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_process_name (tenant_id, activity_id, process_name, deleted),
    KEY idx_activity_process_seq (tenant_id, activity_id, seq, deleted)
) COMMENT='活动工序';

-- 5) 工序依赖边（拓扑连线）
CREATE TABLE IF NOT EXISTS activity_process_dep (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    activity_id BIGINT NOT NULL,
    source_process_id BIGINT NOT NULL,
    target_process_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_process_dep (tenant_id, activity_id, source_process_id, target_process_id, deleted),
    KEY idx_activity_process_dep_target (tenant_id, activity_id, target_process_id, deleted)
) COMMENT='工序依赖（后置工序依赖前置工序闭环保锁）';

-- 6) 拓扑版本
CREATE TABLE IF NOT EXISTS activity_topology (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    activity_id BIGINT NOT NULL,
    topology_version VARCHAR(32) NOT NULL COMMENT '拓扑版本号，与 activity_snapshot 1:1',
    topology_json JSON NOT NULL COMMENT 'nodes+edges',
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/PUBLISHED',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_topology_version (tenant_id, activity_id, topology_version, deleted),
    KEY idx_activity_topology_activity (tenant_id, activity_id, deleted)
) COMMENT='活动拓扑版本';

-- 7) 活动流程快照（自描述载荷，铁律 #15）
CREATE TABLE IF NOT EXISTS activity_snapshot (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    activity_id BIGINT NOT NULL,
    topology_version VARCHAR(32) NOT NULL COMMENT '与 activity_topology.version 1:1 恒等',
    snapshot_json JSON NOT NULL COMMENT '自描述：schemaVersion+entityType+entityId+frozenAt+topologyVersion+工序定义+依赖边',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_snapshot_version (tenant_id, activity_id, topology_version, deleted),
    KEY idx_activity_snapshot_activity (tenant_id, activity_id, deleted)
) COMMENT='活动流程快照（任务下发取数基准，批次3消费）';

-- 8) 专题聚合关联
CREATE TABLE IF NOT EXISTS activity_topic_rel (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    topic_activity_id BIGINT NOT NULL COMMENT '专题聚合活动 id',
    member_activity_id BIGINT NOT NULL COMMENT '被聚合普通活动 id',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_topic_rel (tenant_id, topic_activity_id, member_activity_id, deleted),
    KEY idx_activity_topic_member (tenant_id, member_activity_id, deleted)
) COMMENT='专题聚合活动与普通活动关联';

-- 9) 活动模板导入导出包元数据
CREATE TABLE IF NOT EXISTS activity_template (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(160) NOT NULL,
    format VARCHAR(16) NOT NULL DEFAULT 'JSON',
    package_json JSON NOT NULL COMMENT '模板包内容（不含项目/组件/人员实例数据）',
    summary VARCHAR(1000) NULL COMMENT '模板包摘要（导入导出留痕）',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_template_code (tenant_id, template_code, deleted)
) COMMENT='活动模板导入导出包';

-- 10) 菜单 746「活动管理」挂 745 目录 + 权限点（read/create/update/delete）
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (746, 1, 745, 'menu', '活动管理', 'DataMigrationLifecycleActivity', '/data-migration/lifecycle/activity', 'data-migration', 'data-migration-lifecycle:activity', 'collection', 10);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 746, 1), (200, 746, 1), (201, 746, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7461, 1, 746, 'read', 'data-migration-lifecycle:activity', '活动管理访问'),
    (7462, 1, 746, 'create', 'data-migration-lifecycle:activity:create', '新增活动'),
    (7463, 1, 746, 'update', 'data-migration-lifecycle:activity:update', '维护活动与工序'),
    (7464, 1, 746, 'delete', 'data-migration-lifecycle:activity:delete', '作废活动');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 746 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 746 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 746 AND status = 1 AND action_code = 'read';

-- 11) 契约视图
-- v_activity_available：可下发/可聚合引用的启用活动
CREATE OR REPLACE VIEW v_activity_available AS
SELECT a.id, a.tenant_id, a.activity_code, a.activity_name, a.activity_type,
       a.lifecycle_stage_id, s.stage_code, s.stage_name, a.granularity, a.activity_status
FROM activity a
LEFT JOIN lifecycle_stage s ON s.id = a.lifecycle_stage_id AND s.tenant_id = a.tenant_id AND s.deleted = 0
WHERE a.activity_status = 'ACTIVE' AND a.deleted = 0;

-- v_process_option：工序下拉选项（工序序号/名称/负责人角色/配置状态）
CREATE OR REPLACE VIEW v_process_option AS
SELECT p.id, p.tenant_id, p.activity_id, p.seq, p.process_name, p.owner_role_id,
       p.is_required, p.config_status, p.must_audit, p.must_submit_deliverable
FROM activity_process p
WHERE p.deleted = 0;

-- v_activity_component_check：活动-组件一致性（组件级活动必绑组件、项目级活动禁绑组件校验依据）
CREATE OR REPLACE VIEW v_activity_component_check AS
SELECT a.id AS activity_id, a.tenant_id, a.granularity,
       COUNT(r.id) AS component_count
FROM activity a
LEFT JOIN activity_component_rel r ON r.activity_id = a.id AND r.tenant_id = a.tenant_id AND r.deleted = 0
WHERE a.deleted = 0
GROUP BY a.id, a.tenant_id, a.granularity;

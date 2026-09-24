-- =====================================================================
-- V162 批次4·问题管理 + 历史问题知识库（基线第 13 章）
-- 1) 7 张表：issue / issue_attachment / issue_rectify_record /
--            knowledge_entry / knowledge_tag / knowledge_tag_rel / knowledge_reuse_log
-- 2) 菜单 751「问题台账」挂 745 + 权限点 7511-7514
-- 3) 对账式同步 reconcile：归属类字段权威在上报单，整改类字段权威在台账
-- =====================================================================

-- 1) 问题台账主表（4 态枚举，禁止布尔拆分；闭环后禁改）
CREATE TABLE IF NOT EXISTS issue (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    issue_code VARCHAR(64) NOT NULL COMMENT '问题唯一编码',
    issue_title VARCHAR(200) NOT NULL,
    issue_source VARCHAR(16) NOT NULL COMMENT 'MANUAL/IMPORT/ORDER_SYNC',
    project_id BIGINT NULL,
    business_group_id BIGINT NULL,
    component_id BIGINT NULL COMMENT '组件级问题展示（颗粒度继承 CK）',
    order_id BIGINT NULL,
    process_seq INT NULL,
    reporter_id BIGINT NOT NULL,
    rectifier_id BIGINT NULL COMMENT '责任整改人',
    first_reported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    rectified_at DATETIME(6) NULL,
    issue_desc TEXT NOT NULL,
    scene TEXT NULL,
    impact_scope TEXT NULL,
    block_desc TEXT NULL,
    attachment_ids JSON NULL,
    admin_suggestion TEXT NULL COMMENT '管理员整改建议',
    solution TEXT NULL COMMENT '标准化解决方案',
    rectify_progress TEXT NULL,
    rectify_records JSON NULL,
    rectify_attachment_ids JSON NULL,
    issue_status VARCHAR(16) NOT NULL DEFAULT 'WAIT_RECTIFY' COMMENT '4 态：WAIT_RECTIFY/RECTIFYING/CLOSED/CANCELLED（见 3.5）',
    snapshot_version VARCHAR(64) NULL COMMENT '关联工单快照版本',
    change_log JSON NULL,
    synced_report_id BIGINT NULL COMMENT '反向关联 process_issue_report 上报单（reconcile 幂等锚点）',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_issue_granularity_component CHECK (
        (component_id IS NULL AND order_id IS NULL) OR (component_id IS NOT NULL)
    ),
    CONSTRAINT ck_issue_sync_report CHECK (
        (issue_source = 'ORDER_SYNC' AND synced_report_id IS NOT NULL) OR (issue_source <> 'ORDER_SYNC')
    ),
    UNIQUE KEY uk_issue_code (tenant_id, issue_code),
    KEY idx_issue_list (tenant_id, deleted, issue_status, first_reported_at),
    KEY idx_issue_sync (tenant_id, synced_report_id, deleted)
) COMMENT='问题台账（双渠道归集：手动/导入/工单同步）';

-- 2) 问题佐证附件占位
CREATE TABLE IF NOT EXISTS issue_attachment (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    issue_id BIGINT NOT NULL,
    attachment_ref VARCHAR(255) NOT NULL,
    file_type VARCHAR(64) NULL,
    file_size BIGINT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_issue_attachment (tenant_id, issue_id, deleted)
) COMMENT='问题佐证附件（占位）';

-- 3) 整改过程记录（追加式，永久留存）
CREATE TABLE IF NOT EXISTS issue_rectify_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    issue_id BIGINT NOT NULL,
    record TEXT NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_issue_rectify_record (tenant_id, issue_id, deleted)
) COMMENT='问题整改过程记录（不可清空仅可补充）';

-- 4) 知识条目（三元组：问题→解决方案→复用次数；ACTIVE/INVALID 禁删仅可失效）
CREATE TABLE IF NOT EXISTS knowledge_entry (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    knowledge_code VARCHAR(64) NOT NULL,
    source_issue_id BIGINT NULL COMMENT '来源问题（自动沉淀时必填）',
    problem_desc TEXT NOT NULL,
    solution TEXT NOT NULL,
    rectify_records JSON NULL,
    attachment_ids JSON NULL,
    tag_ids JSON NULL,
    reuse_count INT NOT NULL DEFAULT 0,
    adopt_result JSON NULL,
    knowledge_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INVALID（禁止物理删除）',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_knowledge_code (tenant_id, knowledge_code),
    KEY idx_knowledge_status (tenant_id, knowledge_status, deleted)
) COMMENT='历史问题知识条目（三元组建模，禁删仅可失效）';

-- 5) 多级标签（业务域/组件/工序环节/问题类型/根因归类，管理员维护）
CREATE TABLE IF NOT EXISTS knowledge_tag (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    tag_code VARCHAR(64) NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    tag_level VARCHAR(32) NOT NULL COMMENT 'BUSINESS_DOMAIN/COMPONENT/PROCESS_STAGE/ISSUE_TYPE/ROOT_CAUSE',
    parent_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_knowledge_tag (tenant_id, tag_code),
    KEY idx_knowledge_tag_level (tenant_id, tag_level, deleted)
) COMMENT='知识多级标签';

-- 6) 知识-标签关联
CREATE TABLE IF NOT EXISTS knowledge_tag_rel (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    knowledge_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_knowledge_tag_rel (tenant_id, knowledge_id, tag_id),
    KEY idx_knowledge_tag_rel_tag (tenant_id, tag_id)
) COMMENT='知识条目-标签关联';

-- 7) 知识复用留痕（引用关系与采纳结果，采纳率计算源）
CREATE TABLE IF NOT EXISTS knowledge_reuse_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    knowledge_id BIGINT NOT NULL,
    issue_id BIGINT NULL,
    action VARCHAR(16) NOT NULL COMMENT 'REFER/ADOPT',
    result VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_knowledge_reuse_log (tenant_id, knowledge_id, deleted)
) COMMENT='知识复用记录（复用次数回写源）';

-- 8) 菜单 751「问题台账」挂 745 + 权限点
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (751, 1, 745, 'menu', '问题台账', 'DataMigrationLifecycleIssue', '/data-migration/lifecycle/issue', 'data-migration', 'data-migration-lifecycle:issue', 'warning', 60);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 751, 1), (200, 751, 1), (201, 751, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7511, 1, 751, 'read', 'data-migration-lifecycle:issue', '问题台账访问'),
    (7512, 1, 751, 'create', 'data-migration-lifecycle:issue:create', '问题新增/批量导入'),
    (7513, 1, 751, 'update', 'data-migration-lifecycle:issue:rectify', '整改维护/闭环/作废'),
    (7514, 1, 751, 'audit', 'data-migration-lifecycle:issue:knowledge', '知识库维护（仅管理员）');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 751 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 751 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 751 AND status = 1 AND action_code = 'read';

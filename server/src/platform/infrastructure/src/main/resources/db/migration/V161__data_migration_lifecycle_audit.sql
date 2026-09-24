-- =====================================================================
-- V161 批次4·任务审核管理（基线第 15 章）
-- 1) 3 张表：audit_record / audit_batch_revoke / audit_attachment
-- 2) 菜单 750「任务审核」挂 745 + 权限点 7501-7504
-- 3) 审核留痕与工序状态迁移同一次提交（D-14 时序，T5 已修订）
-- =====================================================================

-- 1) 审核记录表（固化不可删改；REJECTED 强制打回整改要求）
CREATE TABLE IF NOT EXISTS audit_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    audit_round INT NOT NULL DEFAULT 1 COMMENT '审核轮次（复审次数+1；无默认裁决时每次提审递增）',
    audit_result VARCHAR(16) NOT NULL COMMENT 'PASSED/REJECTED（无默认值，必须手动选择）',
    audit_opinion TEXT NOT NULL COMMENT '审核意见（通过=合规验收结论；打回=问题定位与不合规点，必填）',
    rectify_requirement TEXT NULL COMMENT '打回整改要求（audit_result=REJECTED 时强制必填，CHECK 兜底）',
    audit_attachment_ids JSON NULL,
    special_remark TEXT NULL,
    auditor_id BIGINT NOT NULL,
    audited_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    audit_status VARCHAR(16) NOT NULL COMMENT 'WAIT_REVIEW/PASSED/REJECTED/RECHECK（见 3.7）',
    rectify_done VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/DONE 整改完成状态',
    post_unlock_status VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT 'LOCKED/UNLOCKED 后置工序解锁状态',
    snapshot_version VARCHAR(64) NOT NULL COMMENT '工单快照版本',
    batch_id BIGINT NULL COMMENT '批量打回批次号（批量场景留痕）',
    revoked_at DATETIME(6) NULL COMMENT '撤销窗口内整批撤销时间（固化留痕，不物理删除）',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_oar_reject_rectify CHECK (
        (audit_result = 'REJECTED' AND rectify_requirement IS NOT NULL AND rectify_requirement <> '') OR audit_result = 'PASSED'
    ),
    KEY idx_audit_record (tenant_id, order_id, process_seq, deleted),
    KEY idx_audit_record_by_auditor (tenant_id, auditor_id, audit_result, created_at, deleted)
) COMMENT='工序审核记录（永久固化，禁止删除/修改；撤销仅窗口内批量撤销并留痕）';

-- 2) 批量打回批次表（三约束：统一原因/二次确认/10 分钟撤销窗口；单批 ≤50 条）
CREATE TABLE IF NOT EXISTS audit_batch_revoke (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    order_ids JSON NOT NULL COMMENT '本批次打回工单清单（≤50 条）',
    unified_reason TEXT NOT NULL COMMENT '强制统一打回原因与整改要求',
    confirmed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '二次确认时间',
    confirmed_by BIGINT NOT NULL,
    revoke_deadline DATETIME(6) NOT NULL COMMENT '撤销窗口截止时间（提交后 10 分钟）',
    revoked_at DATETIME(6) NULL,
    revoked_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_audit_batch_revoke (tenant_id, deleted, revoked_at)
) COMMENT='批量打回批次（统一原因+二次确认+10分钟撤销窗口留痕）';

-- 3) 审核佐证附件占位（文件流后续批次接入 platform/attachment）
CREATE TABLE IF NOT EXISTS audit_attachment (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    audit_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    process_seq INT NOT NULL,
    attachment_ref VARCHAR(255) NOT NULL COMMENT '附件引用（ID 占位/文件流接入后替换）',
    file_type VARCHAR(64) NULL,
    file_size BIGINT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_audit_attachment (tenant_id, audit_id, deleted)
) COMMENT='审核佐证附件（选填，占位）';

-- 4) 菜单 750「任务审核」挂 745 + 权限点
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (750, 1, 745, 'menu', '任务审核', 'DataMigrationLifecycleAudit', '/data-migration/lifecycle/audit', 'data-migration', 'data-migration-lifecycle:audit', 'finished', 50);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 750, 1), (200, 750, 1), (201, 750, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7501, 1, 750, 'read', 'data-migration-lifecycle:audit', '审核台账访问'),
    (7502, 1, 750, 'audit', 'data-migration-lifecycle:audit:pass', '审核通过/打回'),
    (7503, 1, 750, 'create', 'data-migration-lifecycle:audit:batch', '批量审核/批量打回'),
    (7504, 1, 750, 'update', 'data-migration-lifecycle:audit:revoke', '批量打回撤销');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 750 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 750 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 750 AND status = 1 AND action_code = 'read';

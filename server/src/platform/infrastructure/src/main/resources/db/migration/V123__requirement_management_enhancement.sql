-- =============================================================================
-- V93：需求管理平台增强
-- 存量需求：系统子表（主责/协同）、整条需求流转、评审记录、工作量表、软需文档、
--          协同事项（改造/测试）、需求版本历史；需求头新增版本/工作量变更/当前流转人。
-- 新建差异：提交评审评审报告文档名称（上传能力后续开放）。
-- 仅追加，不修改历史迁移；管理员角色默认授予 requirement:pmo 权限。
-- 幂等说明：MySQL 8 的 DDL 不可回滚，若此前 V93 部分执行失败后重跑，
--           列/表已存在会导致重复定义；因此列新增按 information_schema 判断跳过，
--           建表使用 CREATE TABLE IF NOT EXISTS，权限写入自带存在性判断。
-- =============================================================================

-- 需求头新增字段（已存在则跳过）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_requirement'
      AND COLUMN_NAME IN ('version_no', 'workload_change', 'current_flow_user_id', 'current_flow_user_name'));
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_requirement
        ADD COLUMN version_no VARCHAR(16) NOT NULL DEFAULT ''1.0'' COMMENT ''需求版本号：1.0 原始版 / 2.0 变更版（可递增）'' AFTER not_project_developed,
        ADD COLUMN workload_change VARCHAR(1000) NULL COMMENT ''工作量需求变更记录：变更内容/原因/前后对比'' AFTER version_no,
        ADD COLUMN current_flow_user_id BIGINT NULL COMMENT ''当前流转处理人用户 ID'' AFTER workload_change,
        ADD COLUMN current_flow_user_name VARCHAR(64) NULL COMMENT ''当前流转处理人姓名'' AFTER current_flow_user_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 差异表新增评审报告文档名称字段（已存在则跳过）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_difference'
      AND COLUMN_NAME = 'review_report_name');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_difference
        ADD COLUMN review_report_name VARCHAR(200) NULL COMMENT ''评审报告信息文档名称（上传能力后续开放）'' AFTER review_comment',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS req_legacy_system_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID',
    system_role VARCHAR(16) NOT NULL COMMENT '系统角色：主责/协同',
    system_code VARCHAR(64) NULL COMMENT '物理子系统编号',
    system_name VARCHAR(200) NULL COMMENT '物理子系统名称',
    owner_user_id BIGINT NULL COMMENT '系统负责人用户 ID',
    owner_user_name VARCHAR(64) NULL COMMENT '系统负责人姓名',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_sys_item_req (tenant_id, requirement_id, deleted),
    KEY idx_sys_item_owner (tenant_id, owner_user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求-系统子表（一条需求对应主责/协同多行）';

CREATE TABLE IF NOT EXISTS req_flow_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID（按整条需求流转）',
    action VARCHAR(16) NOT NULL COMMENT 'SEND 流转/RETURN 回传/COMPLETE 完成',
    from_user_id BIGINT NULL COMMENT '操作人用户 ID',
    from_user_name VARCHAR(64) NULL COMMENT '操作人姓名',
    to_user_id BIGINT NULL COMMENT '流转目标用户 ID',
    to_user_name VARCHAR(64) NULL COMMENT '流转目标用户姓名',
    comment VARCHAR(500) NULL COMMENT '流转说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_flow_req (tenant_id, requirement_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求流转记录（按需求头单次流转一人）';

CREATE TABLE IF NOT EXISTS req_review_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    biz_type VARCHAR(32) NOT NULL COMMENT '业务类型：DIFFERENCE 差异评审/WORKLOAD 工作量表评审/SOFT 软需评审',
    biz_id BIGINT NOT NULL COMMENT '业务对象 ID',
    review_no VARCHAR(64) NULL COMMENT '评审编号',
    reviewer_id BIGINT NOT NULL COMMENT '评审人用户 ID',
    reviewer_name VARCHAR(64) NULL COMMENT '评审人姓名',
    review_time TIMESTAMP NULL COMMENT '评审时间',
    conclusion VARCHAR(16) NOT NULL COMMENT '评审结论：通过/退回',
    comment VARCHAR(1000) NULL COMMENT '评审意见',
    report_doc_name VARCHAR(200) NULL COMMENT '评审报告信息文档名称（上传能力后续开放）',
    report_preview_id VARCHAR(64) NULL COMMENT '评审报告文档预览引用（预留）',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_review_biz (tenant_id, biz_type, biz_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评审记录（含评审报告文档预留字段）';

CREATE TABLE IF NOT EXISTS req_workload (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID',
    system_item_id BIGINT NULL COMMENT '系统子表 ID',
    system_code VARCHAR(64) NULL COMMENT '物理子系统编号',
    doc_name VARCHAR(200) NULL COMMENT '工作量表文档名称（上传能力后续开放）',
    version_no VARCHAR(16) NOT NULL DEFAULT '1.0' COMMENT '版本号，变更替换时递增并保留历史',
    review_status VARCHAR(16) NOT NULL DEFAULT '待评审' COMMENT '待评审/评审中/已评审/已退回',
    review_record_id BIGINT NULL COMMENT '最近一次评审记录 ID',
    file_preview_id VARCHAR(64) NULL COMMENT '文件预览引用（预留）',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_workload_req (tenant_id, requirement_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求-工作量表（按系统独立记录，版本替换保留历史）';

CREATE TABLE IF NOT EXISTS req_soft_doc (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID',
    system_item_id BIGINT NULL COMMENT '系统子表 ID',
    system_code VARCHAR(64) NULL COMMENT '物理子系统编号',
    doc_name VARCHAR(200) NULL COMMENT '软需文档名称（上传能力后续开放）',
    version_no VARCHAR(16) NOT NULL DEFAULT '1.0' COMMENT '版本号，变更替换时递增并保留历史',
    review_status VARCHAR(16) NOT NULL DEFAULT '待评审' COMMENT '待评审/评审中/已评审/已退回',
    review_record_id BIGINT NULL COMMENT '最近一次评审记录 ID',
    file_preview_id VARCHAR(64) NULL COMMENT '文件预览引用（预留）',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_soft_req (tenant_id, requirement_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求-软需文档（按系统独立记录，版本替换保留历史）';

CREATE TABLE IF NOT EXISTS req_coordination_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID',
    system_item_id BIGINT NULL COMMENT '系统子表 ID',
    item_type VARCHAR(16) NOT NULL COMMENT '协同事项类型：改造/测试',
    system_code VARCHAR(64) NULL COMMENT '物理子系统编号',
    system_name VARCHAR(200) NULL COMMENT '物理子系统名称',
    owner_user_id BIGINT NULL COMMENT '负责人用户 ID',
    owner_user_name VARCHAR(64) NULL COMMENT '负责人姓名',
    start_date DATE NULL COMMENT '开始日期',
    end_date DATE NULL COMMENT '结束日期',
    status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '未开始/进行中/已完成',
    description VARCHAR(1000) NULL COMMENT '事项说明',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_coord_req (tenant_id, requirement_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求-协同事项（改造/测试）';

CREATE TABLE IF NOT EXISTS req_requirement_version (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID',
    version_no VARCHAR(16) NOT NULL COMMENT '版本号：1.0/2.0...',
    change_summary VARCHAR(1000) NULL COMMENT '变更说明',
    snapshot_json TEXT NULL COMMENT '需求头快照（JSON）',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_req_version (tenant_id, requirement_id, version_no),
    KEY idx_version_req (tenant_id, requirement_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求版本历史（1.0 原始版 / 2.0 变更版）';

-- 需求管理 PMO 权限：管理员角色（role_id=1）默认授予
INSERT INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 9601, 1, 700, 'pmo', 'requirement:pmo', '需求管理 PMO（全量查看/阶段推进/流转/标记完成）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE tenant_id = 1 AND permission_code = 'requirement:pmo');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, 9601, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE tenant_id = 1 AND role_id = 1 AND permission_id = 9601);

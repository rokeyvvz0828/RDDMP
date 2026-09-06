-- V99: 数迁资产内容一菜单一表与公共关系表（纯 DDL）。
-- 需求 REQ-20260831-050：内容管理 11 个二级菜单各落独立表，附件与问题关联收敛为公共关系表。
-- 本脚本只建表不搬数据；数据复制在 V100，删除旧表在 V101（分发布版本执行）。
-- 设计基线：docs/engineering-control/designs/2026-08-31-data-migration-content-table-split-design.md

-- 1. 迁移方案（原 dm_asset.asset_type='PLAN'）
CREATE TABLE dm_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '文档编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    checksum_md5 CHAR(32) NULL COMMENT '文件MD5校验值（查重）',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_plan_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_plan_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_plan_md5 (tenant_id, checksum_md5, deleted),
    KEY idx_dm_plan_owner (tenant_id, owner_id, deleted)
) COMMENT='迁移方案内容表';

-- 2. 迁移映射（原 dm_asset.asset_type='MAPPING_DOC'）
CREATE TABLE dm_mapping_doc (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '文档编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    checksum_md5 CHAR(32) NULL COMMENT '文件MD5校验值（查重）',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_mapping_doc_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_mapping_doc_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_mapping_doc_md5 (tenant_id, checksum_md5, deleted),
    KEY idx_dm_mapping_doc_owner (tenant_id, owner_id, deleted)
) COMMENT='迁移映射内容表';

-- 3. 迁移过程依赖文件（原 dm_asset.asset_type='DEPENDENCY'）
CREATE TABLE dm_dependency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '文档编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    checksum_md5 CHAR(32) NULL COMMENT '文件MD5校验值（查重）',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_dependency_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_dependency_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_dependency_md5 (tenant_id, checksum_md5, deleted),
    KEY idx_dm_dependency_owner (tenant_id, owner_id, deleted)
) COMMENT='迁移过程依赖文件内容表';

-- 4. 迁移程序（原 dm_asset.asset_type='SCRIPT'）
CREATE TABLE dm_script (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '文档编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    checksum_md5 CHAR(32) NULL COMMENT '文件MD5校验值（查重）',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_script_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_script_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_script_md5 (tenant_id, checksum_md5, deleted),
    KEY idx_dm_script_owner (tenant_id, owner_id, deleted)
) COMMENT='迁移程序内容表';

-- 5. 专题材料（原 dm_asset.asset_type='TOPIC'）
CREATE TABLE dm_topic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '文档编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    checksum_md5 CHAR(32) NULL COMMENT '文件MD5校验值（查重）',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_topic_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_topic_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_topic_md5 (tenant_id, checksum_md5, deleted),
    KEY idx_dm_topic_owner (tenant_id, owner_id, deleted)
) COMMENT='专题材料内容表';

-- 6. 投产及演练（原 dm_asset.asset_type='RELEASE_DRILL'）
CREATE TABLE dm_release_drill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '文档编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    checksum_md5 CHAR(32) NULL COMMENT '文件MD5校验值（查重）',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_release_drill_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_release_drill_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_release_drill_md5 (tenant_id, checksum_md5, deleted),
    KEY idx_dm_release_drill_owner (tenant_id, owner_id, deleted)
) COMMENT='投产及演练内容表';

-- 7. 汇报材料（原 dm_asset.asset_type='REPORT'，保留汇报专属维度）
CREATE TABLE dm_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '材料编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '材料名称',
    report_period VARCHAR(16) NULL COMMENT '汇报周期',
    report_date DATE NULL COMMENT '汇报日期',
    keywords VARCHAR(500) NULL COMMENT '关键字，逗号分隔',
    checksum_md5 CHAR(32) NULL COMMENT '文件MD5校验值（查重）',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_report_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_report_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_report_period (tenant_id, project_id, report_period, deleted),
    KEY idx_dm_report_md5 (tenant_id, checksum_md5, deleted),
    KEY idx_dm_report_owner (tenant_id, owner_id, deleted)
) COMMENT='汇报材料内容表';

-- 8. 迁移检核规则（原 dm_asset.asset_type='RULE'，主体为结构化 JSON）
CREATE TABLE dm_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '规则编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '规则名称',
    structured_data JSON NOT NULL COMMENT '规则主体结构化数据',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_rule_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_rule_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_rule_owner (tenant_id, owner_id, deleted)
) COMMENT='迁移检核规则内容表';

-- 9. 迁移参数（原 dm_asset.asset_type='PARAMETER'）
CREATE TABLE dm_parameter (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '参数编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '参数名称',
    structured_data JSON NOT NULL COMMENT '参数主体结构化数据',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_parameter_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_parameter_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_parameter_owner (tenant_id, owner_id, deleted)
) COMMENT='迁移参数内容表';

-- 10. 中间表结构化资产（原 dm_asset.asset_type='INTERMEDIATE_TABLE'，基础资料中间表结构菜单在用）
CREATE TABLE dm_intermediate_table (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    component_id BIGINT NULL COMMENT '所属组件',
    doc_code VARCHAR(96) NOT NULL COMMENT '中间表编号，项目内活动记录唯一',
    doc_name VARCHAR(255) NOT NULL COMMENT '中间表名称',
    structured_data JSON NOT NULL COMMENT '中间表结构化数据',
    owner_id BIGINT NOT NULL COMMENT '负责人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at TIMESTAMP NULL COMMENT '删除时间',
    created_by BIGINT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '最后编辑人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    active_doc_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED COMMENT '活动编号（仅未删除记录取值）',
    UNIQUE KEY uk_dm_intermediate_table_active_code (tenant_id, project_id, active_doc_code),
    KEY idx_dm_intermediate_table_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_intermediate_table_owner (tenant_id, owner_id, deleted)
) COMMENT='中间表结构化资产表';

-- 11. 公共附件关系表：所有内容菜单的附件统一登记（含文件型资产主文件与会议多附件）。
-- 主文件/首附件 sort_order=0；附件级回收站沿用软删三件套；活动唯一键对齐 V98 模式。
CREATE TABLE dm_content_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型 PLAN/MAPPING_DOC/DEPENDENCY/SCRIPT/TOPIC/RELEASE_DRILL/REPORT/MEETING',
    business_id BIGINT NOT NULL COMMENT '业务实体ID（内容表主键或会议主键）',
    attachment_id BIGINT NOT NULL COMMENT '关联 att_file.id',
    file_name VARCHAR(500) NOT NULL COMMENT '附件原始文件名',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号，主文件为0',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（附件回收站）0否 1是',
    deleted_by BIGINT NULL COMMENT '删除人',
    deleted_at DATETIME(6) NULL COMMENT '删除时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    active_attachment_key VARCHAR(256)
        GENERATED ALWAYS AS (
            CASE WHEN deleted = 0
                 THEN CONCAT(tenant_id, ':', business_type, ':', business_id, ':', attachment_id)
                 ELSE NULL
            END
        ) STORED COMMENT '活动附件唯一键（已删除记录为NULL）',
    UNIQUE KEY uk_dm_content_att_active (active_attachment_key),
    KEY idx_dm_content_att_business (tenant_id, business_type, business_id, deleted, sort_order),
    KEY idx_dm_content_att_attachment (tenant_id, attachment_id, deleted),
    KEY idx_dm_content_att_tenant (tenant_id, deleted)
) COMMENT='数迁内容公共附件关系表';

-- 12. 公共问题关系表：问题清单与会议/目标表/字段的关联（会议侧反向关联归一为同一行）。
-- 保持硬删全量重插语义，无软删。
CREATE TABLE dm_issue_relation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    issue_id BIGINT NOT NULL COMMENT '关联 dm_issue.id',
    related_type VARCHAR(32) NOT NULL COMMENT '关联对象类型 MEETING/TABLE/FIELD',
    related_id BIGINT NOT NULL COMMENT '关联对象ID（会议主键/目标表ID/目标字段ID）',
    created_by BIGINT NOT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_dm_issue_relation (tenant_id, issue_id, related_type, related_id),
    KEY idx_dm_issue_relation_target (tenant_id, related_type, related_id)
) COMMENT='数迁问题关联公共关系表';

-- 13. 会议-系统关联表：会议纪要关联物理子系统（原 dm_asset_relation MEETING->SYSTEM 行）。
CREATE TABLE dm_meeting_system (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    meeting_id BIGINT NOT NULL COMMENT '关联 dm_meeting.meeting_id',
    subsystem_id BIGINT NOT NULL COMMENT '关联 arch_physical_subsystem.id',
    created_by BIGINT NOT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_dm_meeting_system (tenant_id, meeting_id, subsystem_id),
    KEY idx_dm_meeting_system_subsystem (tenant_id, subsystem_id)
) COMMENT='会议纪要关联系统表';

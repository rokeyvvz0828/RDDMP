-- 项目风险管理：风险编号、问题上报、进展跟踪和升级解决信息。
ALTER TABLE pm_project
    ADD COLUMN risk_number_rule VARCHAR(128) NULL DEFAULT '{PROJECT_CODE}-R{SEQ:3}' COMMENT '风险编号规则，支持项目编号、序号和日期占位符' AFTER child_plan_number_rule,
    ADD COLUMN next_risk_sequence BIGINT NOT NULL DEFAULT 1 COMMENT '下一个风险编号序号' AFTER next_plan_sequence;

CREATE TABLE pm_project_risk (
    id BIGINT PRIMARY KEY COMMENT '项目风险主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    risk_code VARCHAR(128) NOT NULL COMMENT '风险编号，由项目风险编号规则服务端生成',
    occurred_date DATE NULL COMMENT '发生时间',
    project_phase VARCHAR(128) NULL COMMENT '项目阶段参数键',
    urgency VARCHAR(128) NULL COMMENT '紧急程度参数键',
    report_level VARCHAR(128) NULL COMMENT '上报问题级别参数键',
    current_status VARCHAR(128) NOT NULL DEFAULT 'OPEN' COMMENT '当前状态参数键',
    proposer_org_id BIGINT NULL COMMENT '提出组织主键',
    proposer_subsystem VARCHAR(128) NULL COMMENT '提出物理子系统',
    proposer_contact_name VARCHAR(128) NULL COMMENT '提出联系人',
    proposer_contact_phone VARCHAR(64) NULL COMMENT '提出联系方式',
    involved_org_id BIGINT NULL COMMENT '涉及组织主键',
    involved_subsystem VARCHAR(128) NULL COMMENT '涉及物理子系统',
    problem_description VARCHAR(2000) NULL COMMENT '问题描述',
    expected_resolution_date DATE NULL COMMENT '期望解决时间',
    suggested_solution TEXT NULL COMMENT '建议解决方案',
    current_handler_name VARCHAR(128) NULL COMMENT '当前处理人',
    current_handler_phone VARCHAR(64) NULL COMMENT '当前处理人联系方式',
    progress_description TEXT NULL COMMENT '进展描述',
    attention_level VARCHAR(128) NULL COMMENT '关注等级参数键',
    problem_nature VARCHAR(128) NULL COMMENT '问题性质',
    problem_domain VARCHAR(128) NULL COMMENT '问题领域',
    pmo_contact VARCHAR(256) NULL COMMENT 'PMO联系人及联系方式',
    escalation_level VARCHAR(128) NULL COMMENT '是否上升级别参数键',
    current_problem_level VARCHAR(128) NULL COMMENT '当前问题级别参数键',
    planned_resolution_date DATE NULL COMMENT '计划解决时间',
    actual_resolution_date DATE NULL COMMENT '实际解决时间',
    resolution_solution TEXT NULL COMMENT '问题解决方案',
    created_by BIGINT NOT NULL COMMENT '创建人用户主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    UNIQUE KEY uk_pm_project_risk_code (tenant_id, project_id, risk_code, deleted),
    KEY idx_pm_project_risk_project (tenant_id, project_id, deleted),
    KEY idx_pm_project_risk_status (tenant_id, project_id, current_status, deleted),
    KEY idx_pm_project_risk_occurred (tenant_id, project_id, occurred_date, deleted)
) COMMENT='项目风险问题表';

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES
    (4006, 1, 'RISK_URGENCY', '风险紧急程度', 1),
    (4007, 1, 'RISK_REPORT_LEVEL', '上报问题级别', 1),
    (4008, 1, 'RISK_STATUS', '风险当前状态', 1),
    (4009, 1, 'RISK_ATTENTION_LEVEL', '风险关注等级', 1),
    (4010, 1, 'RISK_ESCALATION_LEVEL', '风险升级级别', 1),
    (4011, 1, 'RISK_PROBLEM_LEVEL', '当前问题级别', 1);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4301, 1, id, 'HIGH', '高', 'string', '风险紧急程度' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_URGENCY' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4302, 1, id, 'MEDIUM', '中', 'string', '风险紧急程度' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_URGENCY' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4303, 1, id, 'LOW', '低', 'string', '风险紧急程度' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_URGENCY' AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4311, 1, id, 'L1', '一级', 'string', '上报问题级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_REPORT_LEVEL' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4312, 1, id, 'L2', '二级', 'string', '上报问题级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_REPORT_LEVEL' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4313, 1, id, 'L3', '三级', 'string', '上报问题级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_REPORT_LEVEL' AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4321, 1, id, 'OPEN', '待处理', 'string', '风险当前状态' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_STATUS' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4322, 1, id, 'PROCESSING', '处理中', 'string', '风险当前状态' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_STATUS' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4323, 1, id, 'RESOLVED', '已解决', 'string', '风险当前状态' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_STATUS' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4324, 1, id, 'CLOSED', '已关闭', 'string', '风险当前状态' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_STATUS' AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4331, 1, id, 'HIGH', '高关注', 'string', '风险关注等级' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_ATTENTION_LEVEL' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4332, 1, id, 'MEDIUM', '中关注', 'string', '风险关注等级' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_ATTENTION_LEVEL' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4333, 1, id, 'LOW', '低关注', 'string', '风险关注等级' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_ATTENTION_LEVEL' AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4341, 1, id, 'YES', '已升级', 'string', '风险升级级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_ESCALATION_LEVEL' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4342, 1, id, 'NO', '未升级', 'string', '风险升级级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_ESCALATION_LEVEL' AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4351, 1, id, 'P1', '重大', 'string', '当前问题级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_PROBLEM_LEVEL' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4352, 1, id, 'P2', '严重', 'string', '当前问题级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_PROBLEM_LEVEL' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4353, 1, id, 'P3', '一般', 'string', '当前问题级别' FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'RISK_PROBLEM_LEVEL' AND deleted = 0;

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no, visible)
SELECT 506, 1, 505, 'button', '项目风险权限', 'ProjectPermissionRisk', NULL, NULL, 'project:risk:list', 'warning', 5, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 506);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 506, 1 WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 506 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (5061, 1, 506, 'read', 'project:risk:list', '查看'),
    (5062, 1, 506, 'create', 'project:risk:list:create', '新增'),
    (5063, 1, 506, 'update', 'project:risk:list:update', '修改'),
    (5064, 1, 506, 'delete', 'project:risk:list:delete', '删除');
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 506;

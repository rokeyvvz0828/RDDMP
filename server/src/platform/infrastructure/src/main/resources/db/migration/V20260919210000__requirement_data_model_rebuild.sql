-- =============================================================================
-- REQ-20260919-078 需求管理数据模型重构（需求-物理子系统统一建模）
-- -----------------------------------------------------------------------------
-- 目标：新建项目差异与存量需求统一为「一条需求主表 + 需求-物理子系统关联行」，
--       系统主数据统一取架构管理物理子系统，为开发管理提供稳定的只读来源。
--
-- 模型：
--   req_requirement           需求主表（requirement_kind = NEW_PROJECT_DIFF / LEGACY）
--   req_requirement_system    需求 ↔ 架构物理子系统关联行（LEAD 主责 / CHANGE 改造 / TEST 测试）
--   req_difference_detail     新建项目差异专有字段
--   req_legacy_detail         存量需求专有字段
--   req_flow_log              两页统一流转日志（新增 requirement_kind / created_by）
--   req_legacy_deliverable    存量交付件（合并原 req_workload / req_soft_doc）
--
-- 口径：
--   * project_id 一律为项目管理主键 pm_project.id（V20260919230000 已完成历史口径归一）。
--   * 新建差异系统行恰好 1 行；存量需求 1..N 行且 LEAD 唯一。
--   * 用户确认当前均为测试数据：不做数据回填与兼容，先清空需求业务数据再重建演示数据。
--
-- 过期表：
--   req_workload / req_soft_doc / req_legacy_member 已无外部引用，本迁移删除。
--   其余被替换的旧表（req_difference、req_legacy_requirement、req_legacy_system_item、
--   req_coordination_item、req_system、req_business_group_member）仍被本任务写入范围之外的
--   mock/mock-data.json、MockDataInitializer 允许清单与 platform/boot 开发管理集成测试引用，
--   需与这些文件同批清理后再删除；本次保留为不再被需求服务读写的空壳
--   （见 docs/requirements/REQ-20260919-078-requirement-data-model-rebuild/codex-task-scope.yaml）。
--
-- 幂等：建表使用 CREATE TABLE IF NOT EXISTS；演示数据先清空再重建，可重复执行。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、新数据模型
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS req_requirement (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT NOT NULL COMMENT '归属项目：pm_project.id',
    requirement_kind VARCHAR(32) NOT NULL COMMENT '需求类型：NEW_PROJECT_DIFF 新建项目差异 / LEGACY 存量需求',
    requirement_no VARCHAR(64) NULL COMMENT '需求编号（新建差异：组件物理子系统编号+序号；存量：同业维普编号）',
    name VARCHAR(300) NOT NULL COMMENT '名称（新建差异：差异点名称；存量：需求名称）',
    summary VARCHAR(2000) NULL COMMENT '需求内容简述（存量）/ 差异描述（新建）',
    business_group VARCHAR(64) NULL COMMENT '业务组',
    review_status VARCHAR(32) NULL COMMENT '新建差异评审状态：待评审/评审中/已评审/已退回',
    current_stage VARCHAR(32) NULL COMMENT '存量需求当前阶段：PROPOSE/DOCKING/WORKLOAD/PROJECT/SOFT/LAUNCH',
    workflow_instance_id BIGINT NULL COMMENT '新建差异审批流程实例 ID',
    baseline_id BIGINT NULL COMMENT '新建差异归属基线 ID',
    version_no VARCHAR(16) NOT NULL DEFAULT '1.0' COMMENT '存量需求版本号：1.0 原始版 / 2.0 变更版',
    source VARCHAR(16) NOT NULL DEFAULT 'ONLINE' COMMENT '来源：ONLINE/IMPORT',
    import_batch_id BIGINT NULL COMMENT '导入批次',
    created_by BIGINT NULL,
    current_handler_user_id BIGINT NULL COMMENT '当前处理人用户 ID',
    current_handler_user_name VARCHAR(64) NULL COMMENT '当前处理人姓名',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_req_requirement_no (tenant_id, project_id, requirement_no),
    KEY idx_req_requirement_project (tenant_id, project_id, requirement_kind, deleted),
    KEY idx_req_requirement_handler (tenant_id, current_handler_user_id, deleted),
    KEY idx_req_requirement_creator (tenant_id, created_by, deleted),
    KEY idx_req_requirement_no (tenant_id, requirement_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求主表（新建项目差异与存量需求共用）';

CREATE TABLE IF NOT EXISTS req_requirement_system (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '需求 ID：req_requirement.id',
    physical_subsystem_id BIGINT NULL COMMENT '架构物理子系统主键 arch_physical_subsystem.id（历史快照可空）',
    subsystem_code VARCHAR(64) NULL COMMENT '物理子系统编码快照',
    subsystem_name VARCHAR(200) NULL COMMENT '物理子系统名称快照',
    system_role VARCHAR(16) NOT NULL COMMENT '系统角色：LEAD 主责 / CHANGE 改造 / TEST 测试',
    owner_user_id BIGINT NULL COMMENT '系统负责人用户 ID',
    owner_user_name VARCHAR(64) NULL COMMENT '系统负责人姓名',
    status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '事项状态：未开始/进行中/已完成',
    start_date DATE NULL COMMENT '开始日期',
    end_date DATE NULL COMMENT '结束日期',
    description VARCHAR(1000) NULL COMMENT '事项说明',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_req_system_requirement (tenant_id, requirement_id, deleted),
    KEY idx_req_system_subsystem (tenant_id, physical_subsystem_id, deleted),
    KEY idx_req_system_owner (tenant_id, owner_user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求-物理子系统关联行（含系统负责人，不含系统人员）';

CREATE TABLE IF NOT EXISTS req_difference_detail (
    requirement_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    seq_no INT NULL COMMENT '项目内差异序号（服务端自动生成）',
    business_conglomerate VARCHAR(64) NULL COMMENT '事业群',
    business_section VARCHAR(64) NULL COMMENT '业务板块',
    category VARCHAR(32) NULL COMMENT '分类',
    jinke_practice TEXT NULL COMMENT '我方做法',
    difference_type VARCHAR(64) NULL COMMENT '差异类型',
    monshang_practice TEXT NULL COMMENT '同业作法',
    difference_desc TEXT NULL COMMENT '差异描述',
    monshang_dept VARCHAR(128) NULL COMMENT '同业分析部门',
    monshang_analyst VARCHAR(64) NULL COMMENT '同业分析人',
    jinke_analyst VARCHAR(64) NULL COMMENT '我方分析人',
    adapt_mode VARCHAR(64) NULL COMMENT '适配方式',
    handle_status VARCHAR(64) NULL COMMENT '处理状态',
    coord_group VARCHAR(128) NULL COMMENT '协同组',
    solution TEXT NULL COMMENT '解决方案',
    is_special VARCHAR(4) NULL COMMENT '是否专题：是/否',
    decision_level VARCHAR(64) NULL COMMENT '上升决策层级',
    decision_conclusion TEXT NULL COMMENT '决策结论',
    monshang_confirm_dept VARCHAR(128) NULL COMMENT '同业确认部门',
    jinke_confirmer VARCHAR(64) NULL COMMENT '我方确认人',
    review_comment VARCHAR(1000) NULL COMMENT '评审意见/退回意见',
    review_report_name VARCHAR(200) NULL COMMENT '评审报告信息文档名称（上传能力后续开放）',
    reviewed_by BIGINT NULL COMMENT '评审人',
    reviewed_at TIMESTAMP NULL COMMENT '评审时间',
    dev_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '开发状态：未开始/开发中/已完成/已上线',
    test_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '测试状态：未开始/测试中/已通过',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_req_diff_detail_seq (tenant_id, seq_no),
    KEY idx_req_diff_detail_dev (tenant_id, dev_status, test_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新建项目差异专有字段';

CREATE TABLE IF NOT EXISTS req_legacy_detail (
    requirement_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    legacy_doc_name VARCHAR(200) NULL COMMENT '业需文档名称',
    propose_dept VARCHAR(128) NULL COMMENT '需求提出部门',
    proposer VARCHAR(128) NULL COMMENT '需求提出人及电话',
    monshang_ba VARCHAR(64) NULL COMMENT '同业 BA',
    monshang_architect VARCHAR(64) NULL COMMENT '同业架构',
    expected_launch_date DATE NULL COMMENT '业务期望上线时间',
    regulator VARCHAR(128) NULL COMMENT '外部监管单位',
    regulation_doc_no VARCHAR(200) NULL COMMENT '监管文件名称+文号',
    regulation_desc VARCHAR(2000) NULL COMMENT '监管文件内容描述',
    regulation_launch_date DATE NULL COMMENT '监管要求上线时间',
    requirement_received_date DATE NULL COMMENT '业需入手日',
    requirement_type VARCHAR(32) NULL COMMENT '需求类型：监管/业务/技术',
    regulation_category VARCHAR(32) NULL COMMENT '监管分类',
    sub_group VARCHAR(64) NULL COMMENT '分组',
    jinke_contact VARCHAR(128) NULL COMMENT '我方对接人及电话',
    need_jinke_arch_decision VARCHAR(4) NULL COMMENT '是否需要我方架构决策：是/否',
    jinke_architect VARCHAR(64) NULL COMMENT '我方架构人员',
    unified_managed VARCHAR(4) NULL COMMENT '是否纳入同业统一管理：是/否',
    ba_review_date DATE NULL COMMENT '业需评审完成日',
    workload_date DATE NULL COMMENT '工作量评估完成日',
    finance_project_date DATE NULL COMMENT '财务立项完成日（任务书）',
    soft_doc_name VARCHAR(200) NULL COMMENT '软需文档名称',
    owner_conglomerate VARCHAR(64) NULL COMMENT '主责事业群',
    owner_system VARCHAR(200) NULL COMMENT '主责物理子系统编号+名称（快照文本）',
    owner_contact VARCHAR(128) NULL COMMENT '主责项目组联系人及电话',
    involve_cooperation VARCHAR(4) NULL COMMENT '是否涉及引入组件协同：是/否',
    coord_conglomerate VARCHAR(64) NULL COMMENT '协同事业群',
    coord_system VARCHAR(200) NULL COMMENT '协同系统名称',
    soft_submit_date DATE NULL COMMENT '软需提交日',
    soft_review_date DATE NULL COMMENT '软需评审完成日',
    planned_launch_date DATE NULL COMMENT '计划上线时间',
    actual_launch_date DATE NULL COMMENT '实际上线时间',
    launch_mode VARCHAR(32) NULL COMMENT '上线形式：常规版本/紧急版本',
    requirement_status VARCHAR(32) NULL COMMENT '需求状态（按当前阶段受控，默认为空）',
    remark VARCHAR(1000) NULL COMMENT '备注',
    change_involved VARCHAR(4) NULL COMMENT '是否涉及需求变更：是/否',
    change_info VARCHAR(1000) NULL COMMENT '需求变更信息',
    change_review_conclusion VARCHAR(32) NULL COMMENT '变更评审结论',
    change_conclusion_status VARCHAR(32) NULL COMMENT '变更结论及状态',
    change_remark VARCHAR(1000) NULL COMMENT '需求变更备注',
    not_project_developed VARCHAR(4) NULL COMMENT '未立项已开发：是/否',
    workload_change VARCHAR(1000) NULL COMMENT '工作量需求变更记录',
    workload_person_months VARCHAR(16) NULL COMMENT '工作量（人月）',
    propose_stage_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '需求提出阶段状态',
    docking_stage_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '需求对接阶段状态',
    workload_stage_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '工作量评估阶段状态',
    project_stage_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '立项阶段状态',
    soft_stage_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '软需阶段状态',
    launch_stage_status VARCHAR(32) NOT NULL DEFAULT '未开始' COMMENT '投产阶段状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_req_legacy_detail_stage (tenant_id, propose_stage_status, docking_stage_status,
        workload_stage_status, project_stage_status, soft_stage_status, launch_stage_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求专有字段';

CREATE TABLE IF NOT EXISTS req_legacy_deliverable (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID：req_requirement.id',
    deliverable_type VARCHAR(16) NOT NULL COMMENT '交付件类型：WORKLOAD 工作量表 / SOFT 软需文档',
    system_item_id BIGINT NULL COMMENT '需求系统行 ID：req_requirement_system.id',
    system_code VARCHAR(64) NULL COMMENT '物理子系统编码',
    doc_name VARCHAR(200) NULL COMMENT '文档名称（上传能力后续开放）',
    version_no VARCHAR(16) NOT NULL DEFAULT '1.0' COMMENT '版本号，变更替换时递增并保留历史',
    review_status VARCHAR(16) NOT NULL DEFAULT '待评审' COMMENT '待评审/评审中/已评审/已退回',
    review_record_id BIGINT NULL COMMENT '最近一次评审记录 ID',
    workflow_instance_id BIGINT NULL COMMENT '审批流程实例 ID',
    file_preview_id VARCHAR(64) NULL COMMENT '文件预览引用（预留）',
    review_approver_ids VARCHAR(200) NULL COMMENT '评审审批人用户 ID（逗号分隔）',
    review_approver_names VARCHAR(200) NULL COMMENT '评审审批人姓名',
    review_report_name VARCHAR(200) NULL COMMENT '评审报告信息文档名称',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_req_deliverable (tenant_id, requirement_id, deliverable_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求交付件（合并原工作量表与软需文档）';

-- 统一流转日志：新增需求类型与操作人字段（幂等）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_flow_log' AND COLUMN_NAME = 'requirement_kind');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_flow_log ADD COLUMN requirement_kind VARCHAR(32) NULL COMMENT ''需求类型：NEW_PROJECT_DIFF / LEGACY'' AFTER tenant_id, ADD COLUMN created_by BIGINT NULL AFTER comment',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE req_flow_log SET requirement_kind = 'LEGACY' WHERE requirement_kind IS NULL;

-- -----------------------------------------------------------------------------
-- 二、清空需求业务数据（用户确认均为测试数据，不做回填）
-- -----------------------------------------------------------------------------
DELETE FROM req_baseline_item;
DELETE FROM req_baseline;
DELETE FROM req_change_log;
DELETE FROM req_stage_log;
DELETE FROM req_attachment;
DELETE FROM req_import_batch;
DELETE FROM req_review_record;
DELETE FROM req_requirement_version;
DELETE FROM req_flow_log;
DELETE FROM req_requirement_system;
DELETE FROM req_difference_detail;
DELETE FROM req_legacy_detail;
DELETE FROM req_legacy_deliverable;
DELETE FROM req_requirement;
DELETE FROM req_project_member;
DELETE FROM req_project;

-- -----------------------------------------------------------------------------
-- 三、重建演示数据
--   演示项目优先取「架构管理已有有效物理子系统」的项目，保证"涉及物理子系统"下拉可用；
--   不足时用需求历史演示项目补齐。系统行同时保存物理子系统主键与编码/名称快照。
-- -----------------------------------------------------------------------------
CREATE TEMPORARY TABLE tmp_req_demo_project (
    rn INT NOT NULL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    project_code VARCHAR(64) NULL,
    project_name VARCHAR(200) NULL,
    has_arch TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO tmp_req_demo_project (rn, project_id, project_code, project_name, has_arch)
SELECT ROW_NUMBER() OVER (ORDER BY src.priority, src.project_id),
       src.project_id, src.project_code, src.project_name, src.has_arch
FROM (
    SELECT p.id AS project_id, p.project_code, p.project_name, 0 AS priority, 1 AS has_arch
    FROM pm_project p
    WHERE p.tenant_id = 1 AND p.deleted = 0
      AND EXISTS (SELECT 1 FROM arch_physical_subsystem a
                  WHERE a.tenant_id = p.tenant_id AND a.project_id = p.id
                    AND a.deleted = 0 AND a.status = 'ACTIVE')
    UNION ALL
    SELECT p.id, p.project_code, p.project_name, 1 AS priority, 0 AS has_arch
    FROM pm_project p
    WHERE p.tenant_id = 1 AND p.deleted = 0
      AND p.project_code IN ('P2026-001', 'P2026-002', 'P2026-003')
) src
LIMIT 4;

-- 项目内架构物理子系统（按主键排序取前 3 个）
CREATE TEMPORARY TABLE tmp_req_demo_arch (
    project_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    subsystem_id BIGINT NOT NULL,
    subsystem_code VARCHAR(64) NULL,
    subsystem_name VARCHAR(200) NULL,
    owner_user_id BIGINT NULL,
    PRIMARY KEY (project_id, ordinal)
) ENGINE=InnoDB;

INSERT INTO tmp_req_demo_arch (project_id, ordinal, subsystem_id, subsystem_code, subsystem_name, owner_user_id)
SELECT ranked.project_id, ranked.ordinal, ranked.id, ranked.code, ranked.name, ranked.owner_user_id
FROM (
    SELECT a.project_id, a.id, a.code, a.name, a.owner_user_id,
           ROW_NUMBER() OVER (PARTITION BY a.project_id ORDER BY a.id) AS ordinal
    FROM arch_physical_subsystem a
    WHERE a.tenant_id = 1 AND a.deleted = 0 AND a.status = 'ACTIVE'
) ranked
WHERE ranked.ordinal <= 3;

-- 演示系统行：优先用架构物理子系统，缺失时用历史快照编码/名称兜底
CREATE TEMPORARY TABLE tmp_req_demo_subsystem (
    rn INT NOT NULL,
    ordinal INT NOT NULL,
    subsystem_id BIGINT NULL,
    subsystem_code VARCHAR(64) NULL,
    subsystem_name VARCHAR(200) NULL,
    owner_user_id BIGINT NULL,
    PRIMARY KEY (rn, ordinal)
) ENGINE=InnoDB;

INSERT INTO tmp_req_demo_subsystem (rn, ordinal, subsystem_id, subsystem_code, subsystem_name, owner_user_id)
SELECT d.rn, v.ordinal,
       a.subsystem_id,
       COALESCE(a.subsystem_code, CASE v.ordinal WHEN 1 THEN 'W01812' WHEN 2 THEN 'W0332C' ELSE 'WP106A' END),
       COALESCE(a.subsystem_name, CASE v.ordinal WHEN 1 THEN '存款-对公' WHEN 2 THEN '银联CUPS业务子系统' ELSE 'ATM自助渠道' END),
       COALESCE(a.owner_user_id, CASE v.ordinal WHEN 1 THEN 1003 WHEN 2 THEN 1006 ELSE 1008 END)
FROM tmp_req_demo_project d
CROSS JOIN (SELECT 1 AS ordinal UNION ALL SELECT 2 UNION ALL SELECT 3) v
LEFT JOIN tmp_req_demo_arch a ON a.project_id = d.project_id AND a.ordinal = v.ordinal;

-- 需求台账与项目管理主键对齐（id / 编码 / 名称 / 类型）
INSERT INTO req_project (id, tenant_id, project_code, project_name, project_type, start_time, status, description, created_by, deleted)
SELECT d.project_id, 1, d.project_code, d.project_name,
       COALESCE(p.creation_type, 'NEW'), p.planned_start_date, '进行中', p.description, 1, 0
FROM tmp_req_demo_project d
JOIN pm_project p ON p.id = d.project_id AND p.tenant_id = 1 AND p.deleted = 0;

-- 3.1 新建项目差异：每个演示项目 3 条（待评审 / 评审中 / 已评审纳入基线）
INSERT INTO req_requirement (id, tenant_id, project_id, requirement_kind, requirement_no, name, summary,
                             business_group, review_status, current_stage, baseline_id, version_no, source,
                             created_by, current_handler_user_id, current_handler_user_name, deleted)
SELECT 410000 + d.rn * 100 + v.slot, 1, d.project_id, 'NEW_PROJECT_DIFF',
       CONCAT('W01812-', LPAD(d.rn, 2, '0'), v.slot), v.name, v.summary, '零售一组',
       v.review_status, NULL, NULL, '1.0', 'ONLINE', 1002, v.handler_id, v.handler_name, 0
FROM tmp_req_demo_project d
CROSS JOIN (
    SELECT 1 AS slot, '线上受理影像采集改造' AS name, '将线下受理改为线上，覆盖影像采集与 OCR 识别' AS summary,
           '待评审' AS review_status, 1002 AS handler_id, '李 retail1 BA' AS handler_name
    UNION ALL SELECT 2, '风控规则前置改造', '风控规则从授信后挪到授信前，降低风险敞口',
           '评审中', 1003, '王 retail1 架构'
    UNION ALL SELECT 3, '贷款合同模板字段补全', '补齐 6 个监管必填字段并接入合同模板',
           '已评审', 1002, '李 retail1 BA'
) v;

INSERT INTO req_difference_detail (requirement_id, tenant_id, seq_no, business_conglomerate, business_section,
                                   category, jinke_practice, difference_type, monshang_practice, difference_desc,
                                   monshang_dept, monshang_analyst, jinke_analyst, adapt_mode, handle_status,
                                   coord_group, solution, is_special, decision_level, decision_conclusion,
                                   monshang_confirm_dept, jinke_confirmer, review_comment, review_report_name,
                                   reviewed_by, reviewed_at, dev_status, test_status)
SELECT 410000 + d.rn * 100 + v.slot, 1, v.slot, '零售事业群', '零售业务板块',
       v.category, v.jinke_practice, v.difference_type, v.monshang_practice, v.difference_desc,
       '零售业务部', '李 retail1 BA', '王 retail1 架构', v.adapt_mode, v.handle_status,
       '贷款组', v.solution, '否', '版块内', NULL,
       '零售部', '王 retail1 架构', v.review_comment, NULL,
       v.reviewed_by, v.reviewed_at, v.dev_status, v.test_status
FROM tmp_req_demo_project d
CROSS JOIN (
    SELECT 1 AS slot, '功能' AS category, '支持影像采集组件' AS jinke_practice, '我方有-同业无' AS difference_type,
           '同业当前为线下手工受理' AS monshang_practice, '将线下受理改为线上，覆盖影像采集与 OCR 识别' AS difference_desc,
           '按原型' AS adapt_mode, '双方已确认' AS handle_status, '按原型实现' AS solution,
           NULL AS review_comment, NULL AS reviewed_by, NULL AS reviewed_at, '未开始' AS dev_status, '未开始' AS test_status
    UNION ALL SELECT 2, '流程', '风控规则从授信后挪到授信前', '双方作法有差异',
           '同业在授信后做风控', '调整风控时机，降低风险敞口',
           '少量改造', '待决策', '改造风控决策流程',
           NULL, NULL, NULL, '开发中', '未开始'
    UNION ALL SELECT 3, '报表', '补齐 6 个监管必填字段', '同业有-我方无',
           '同业合同模板已含部分字段', '补充监管必填字段并接入合同模板',
           '参数配置', '双方已确认', '模板字段参数化',
           NULL, 1, '2026-08-15 10:00:00', '已完成', '已通过'
) v;

-- 新建差异系统行：恰好 1 行 LEAD（物理子系统维度提出）
INSERT INTO req_requirement_system (id, tenant_id, requirement_id, physical_subsystem_id, subsystem_code,
                                    subsystem_name, system_role, owner_user_id, owner_user_name, status,
                                    created_by, deleted)
SELECT 810000 + d.rn * 100 + v.slot, 1, 410000 + d.rn * 100 + v.slot,
       s.subsystem_id, s.subsystem_code, s.subsystem_name, 'LEAD',
       s.owner_user_id, NULL, '未开始', 1002, 0
FROM tmp_req_demo_project d
CROSS JOIN (SELECT 1 AS slot UNION ALL SELECT 2 UNION ALL SELECT 3) v
JOIN tmp_req_demo_subsystem s ON s.rn = d.rn AND s.ordinal = v.slot;

-- 3.2 项目基线（纳入已评审差异）
INSERT INTO req_baseline (id, tenant_id, project_id, baseline_no, baseline_name, status, difference_count,
                          remark, created_by, deleted)
SELECT 600000 + d.rn * 100, 1, d.project_id,
       CONCAT('BL-', d.project_code, '-', LPAD(d.rn, 2, '0')),
       CONCAT(d.project_name, ' 首版基线'), 'RELEASED', 1, '评审通过差异锁定', 1, 0
FROM tmp_req_demo_project d;

UPDATE req_requirement r
JOIN tmp_req_demo_project d ON d.project_id = r.project_id
SET r.baseline_id = 600000 + d.rn * 100
WHERE r.tenant_id = 1 AND r.requirement_kind = 'NEW_PROJECT_DIFF'
  AND r.id = 410000 + d.rn * 100 + 3;

INSERT INTO req_baseline_item (id, tenant_id, baseline_id, difference_id, snapshot_json, deleted)
SELECT 610000 + d.rn * 100, 1, 600000 + d.rn * 100, 410000 + d.rn * 100 + 3,
       JSON_OBJECT('name', '贷款合同模板字段补全', 'review_status', '已评审',
                   'requirement_no', CONCAT('W01812-', LPAD(d.rn, 2, '0'), 3)), 0
FROM tmp_req_demo_project d;

-- 3.3 存量需求：每个演示项目 3 条（需求提出 / 需求对接完成 / 软需且立项完成）
INSERT INTO req_requirement (id, tenant_id, project_id, requirement_kind, requirement_no, name, summary,
                             business_group, review_status, current_stage, baseline_id, version_no, source,
                             created_by, current_handler_user_id, current_handler_user_name, deleted)
SELECT 510000 + d.rn * 100 + v.slot, 1, d.project_id, 'LEGACY',
       CONCAT('JG-W0332C-', LPAD(d.rn, 2, '0'), v.slot), v.name, v.summary, '零售一组',
       NULL, v.current_stage, NULL, '1.0', 'ONLINE', 1002, v.handler_id, v.handler_name, 0
FROM tmp_req_demo_project d
CROSS JOIN (
    SELECT 1 AS slot, '零售贷款线上受理改造' AS name, '将线下受理改为线上，覆盖影像采集与 OCR 识别' AS summary,
           'PROPOSE' AS current_stage, 1002 AS handler_id, '李 retail1 BA' AS handler_name
    UNION ALL SELECT 2, '分期经营报表改造', '新增分期经营报表并接入监管报送口径',
           'SOFT', 1003, '王 retail1 架构'
    UNION ALL SELECT 3, '跨境汇款多路径改造', '增加多路径支持，提升汇款成功率',
           'DOCKING', 1002, '李 retail1 BA'
) v;

INSERT INTO req_legacy_detail (requirement_id, tenant_id, legacy_doc_name, propose_dept, proposer, monshang_ba,
                               monshang_architect, expected_launch_date, requirement_received_date, requirement_type,
                               sub_group, jinke_contact, need_jinke_arch_decision, jinke_architect, unified_managed,
                               ba_review_date, workload_date, finance_project_date, soft_doc_name,
                               owner_conglomerate, owner_system, owner_contact, involve_cooperation,
                               coord_conglomerate, coord_system, soft_submit_date, soft_review_date,
                               requirement_status, remark, change_involved, not_project_developed,
                                propose_stage_status, docking_stage_status, workload_stage_status,
                                project_stage_status, soft_stage_status, launch_stage_status)
SELECT 510000 + d.rn * 100 + v.slot, 1, v.legacy_doc_name, '零售业务部', '李 retail1 BA 13800001002',
       '李 retail1 BA', '王 retail1 架构', '2026-12-31', '2026-08-01', '业务',
       '贷款组', '王 retail1 架构 13800001003', '是', '王 retail1 架构', '是',
       v.ba_review_date, v.workload_date, v.finance_project_date, v.soft_doc_name,
       '零售事业群', v.owner_system, '赵 retail1 PM 13800001004', v.involve_cooperation,
       v.coord_conglomerate, v.coord_system, v.soft_submit_date, v.soft_review_date,
       v.requirement_status, '演示数据', '否', '否',
       v.propose_stage_status, v.docking_stage_status, v.workload_stage_status,
       v.project_stage_status, v.soft_stage_status, v.launch_stage_status
FROM tmp_req_demo_project d
CROSS JOIN (
    SELECT 1 AS slot, '业需-零售-2026-001' AS legacy_doc_name, NULL AS ba_review_date, NULL AS workload_date,
           NULL AS finance_project_date, NULL AS soft_doc_name, 'W01812 存款-对公' AS owner_system,
           '否' AS involve_cooperation, NULL AS coord_conglomerate, NULL AS coord_system,
           NULL AS soft_submit_date, NULL AS soft_review_date, '需求提出' AS requirement_status,
           '进行中' AS propose_stage_status, '未开始' AS docking_stage_status, '未开始' AS workload_stage_status,
           '未开始' AS project_stage_status, '未开始' AS soft_stage_status, '未开始' AS launch_stage_status
    UNION ALL SELECT 2, '业需-零售-2026-002', '2026-08-30', '2026-09-10', '2026-09-28',
           '软需-零售-2026-002', 'W01812 存款-对公', '是', '零售事业群', 'W0332C 银联CUPS业务子系统',
           '2026-10-10', '2026-10-25', '软需编写',
           '已完成', '已完成', '已完成', '已完成', '进行中', '未开始'
    UNION ALL SELECT 3, '业需-零售-2026-003', '2026-08-20', NULL, NULL,
           NULL, 'W01812 存款-对公', '是', '零售事业群', 'WP106A ATM自助渠道',
           NULL, NULL, '需求分析',
           '已完成', '已完成', '未开始', '未开始', '未开始', '未开始'
) v;

INSERT INTO req_requirement_system (id, tenant_id, requirement_id, physical_subsystem_id, subsystem_code,
                                    subsystem_name, system_role, owner_user_id, owner_user_name, status,
                                    start_date, end_date, description, remark, created_by, deleted)
SELECT 820000 + d.rn * 100 + v.slot * 10 + v.ordinal, 1, 510000 + d.rn * 100 + v.slot,
       s.subsystem_id, s.subsystem_code, s.subsystem_name, v.system_role, s.owner_user_id,
       CASE s.owner_user_id WHEN 1003 THEN '王 retail1 架构' WHEN 1006 THEN '孙 retail2 PM'
            WHEN 1008 THEN '吴 corp1 架构' ELSE NULL END,
       v.status, v.start_date, v.end_date, v.description, NULL, 1002, 0
FROM tmp_req_demo_project d
CROSS JOIN (
    SELECT 1 AS slot, 1 AS ordinal, 'LEAD' AS system_role, '进行中' AS status, NULL AS description,
           NULL AS start_date, NULL AS end_date
    UNION ALL SELECT 2, 1, 'LEAD', '进行中', '主责：软需编写与评审', '2026-10-01', '2026-10-25'
    UNION ALL SELECT 2, 2, 'CHANGE', '未开始', '协同：分期报表改造', '2026-10-01', '2026-10-20'
    UNION ALL SELECT 2, 3, 'TEST', '未开始', '协同：分期报表测试', '2026-10-20', '2026-11-05'
    UNION ALL SELECT 3, 1, 'LEAD', '进行中', '主责：需求对接', '2026-08-10', '2026-08-30'
    UNION ALL SELECT 3, 2, 'CHANGE', '进行中', '协同：汇款路径改造', '2026-08-10', '2026-09-10'
) v
JOIN tmp_req_demo_subsystem s ON s.rn = d.rn AND s.ordinal = v.ordinal;

-- 3.4 统一流转日志：提出人流转给分析员后收回（演示收回口径）
INSERT INTO req_flow_log (id, tenant_id, requirement_kind, requirement_id, action, from_user_id, from_user_name,
                          to_user_id, to_user_name, comment, created_by, deleted)
SELECT 830000 + d.rn * 100, 1, 'LEGACY', 510000 + d.rn * 100 + 3, 'SEND', 1002, '李 retail1 BA',
       1003, '王 retail1 架构', '流转需求分析员处理', 1002, 0
FROM tmp_req_demo_project d;

INSERT INTO req_flow_log (id, tenant_id, requirement_kind, requirement_id, action, from_user_id, from_user_name,
                          to_user_id, to_user_name, comment, created_by, deleted)
SELECT 830001 + d.rn * 100, 1, 'LEGACY', 510000 + d.rn * 100 + 3, 'WITHDRAW', 1003, '王 retail1 架构',
       1002, '李 retail1 BA', '提出人收回，重新流转', 1002, 0
FROM tmp_req_demo_project d;

INSERT INTO req_flow_log (id, tenant_id, requirement_kind, requirement_id, action, from_user_id, from_user_name,
                          to_user_id, to_user_name, comment, created_by, deleted)
SELECT 830002 + d.rn * 100, 1, 'NEW_PROJECT_DIFF', 410000 + d.rn * 100 + 1, 'SEND', 1002, '李 retail1 BA',
       1003, '王 retail1 架构', '流转补充差异描述', 1002, 0
FROM tmp_req_demo_project d;

-- 3.5 存量交付件：软需阶段需求的工作量表与软需文档
INSERT INTO req_legacy_deliverable (id, tenant_id, requirement_id, deliverable_type, system_item_id, system_code,
                                    doc_name, version_no, review_status, remark, created_by, deleted)
SELECT 840000 + d.rn * 100, 1, 510000 + d.rn * 100 + 2, 'WORKLOAD', NULL, s.subsystem_code,
       '工作量表-分期经营报表-V1.0', '1.0', '已评审', '演示数据', 1002, 0
FROM tmp_req_demo_project d
JOIN tmp_req_demo_subsystem s ON s.rn = d.rn AND s.ordinal = 1;

INSERT INTO req_legacy_deliverable (id, tenant_id, requirement_id, deliverable_type, system_item_id, system_code,
                                    doc_name, version_no, review_status, remark, created_by, deleted)
SELECT 840001 + d.rn * 100, 1, 510000 + d.rn * 100 + 2, 'SOFT', NULL, s.subsystem_code,
       '软需-分期经营报表-V1.0', '1.0', '待评审', '演示数据', 1002, 0
FROM tmp_req_demo_project d
JOIN tmp_req_demo_subsystem s ON s.rn = d.rn AND s.ordinal = 1;

-- 3.6 阶段流转记录
INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status,
                           operator_id, operator_name, comment, approval_result, workflow_instance_id, deleted)
SELECT 850000 + d.rn * 100, 1, 510000 + d.rn * 100 + 2, 'PROPOSE', 'DOCKING', '已完成', '已完成',
       1003, '王 retail1 架构', '需求对接完成，进入工作量评估', 'MANUAL', NULL, 0
FROM tmp_req_demo_project d;

INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status,
                           operator_id, operator_name, comment, approval_result, workflow_instance_id, deleted)
SELECT 850001 + d.rn * 100, 1, 510000 + d.rn * 100 + 2, 'PROJECT', 'SOFT', '已完成', '进行中',
       1003, '王 retail1 架构', '立项完成，进入软需阶段', 'MANUAL', NULL, 0
FROM tmp_req_demo_project d;

DROP TEMPORARY TABLE tmp_req_demo_subsystem;
DROP TEMPORARY TABLE tmp_req_demo_arch;
DROP TEMPORARY TABLE tmp_req_demo_project;

-- -----------------------------------------------------------------------------
-- 四、删除已无外部引用的过期表
-- -----------------------------------------------------------------------------
SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_workload');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_workload', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_soft_doc');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_soft_doc', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_member');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE req_legacy_member', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

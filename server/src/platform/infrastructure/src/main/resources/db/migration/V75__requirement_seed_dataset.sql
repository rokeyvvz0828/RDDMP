-- =============================================================================
-- V44：需求管理全量测试数据补充
-- -----------------------------------------------------------------------------
-- 补充 V39 遗漏的场景：
--   1. req_legacy_requirement.seq_no 回填（V42 新增字段）
--   2. req_stage_log 回填 approval_result + workflow_instance_id（V41 新增字段）
--   3. wf_instance / wf_task / wf_task_action 审批流实例数据（关联审批中场景）
--   4. req_difference 审批中记录关联真实 wf_instance
--   5. Excel 案例：ATM 跨行转账类交易规范性改造（需求编号 JG-W0332C-240507-001）
--   6. 补充 change_log 记录
-- =============================================================================

-- 一、存量需求回填 seq_no（V42 新增字段）
UPDATE req_legacy_requirement SET seq_no = 1 WHERE id = 5001 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 2 WHERE id = 5002 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 3 WHERE id = 5003 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 4 WHERE id = 5004 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 5 WHERE id = 5005 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 6 WHERE id = 5006 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 7 WHERE id = 5007 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 8 WHERE id = 5008 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 1 WHERE id = 5009 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 2 WHERE id = 5010 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 3 WHERE id = 5011 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 4 WHERE id = 5012 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 1 WHERE id = 5013 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 2 WHERE id = 5014 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 3 WHERE id = 5015 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 4 WHERE id = 5016 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 1 WHERE id = 5017 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 2 WHERE id = 5018 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 3 WHERE id = 5019 AND seq_no IS NULL;
UPDATE req_legacy_requirement SET seq_no = 4 WHERE id = 5020 AND seq_no IS NULL;

-- 二、Excel 案例：ATM 跨行转账类交易规范性改造（需求编号 JG-W0332C-240507-001）
-- 该案例完整对应 Excel 行数据，作为"需求提出阶段"的典型样本
INSERT INTO req_legacy_requirement
(id, tenant_id, seq_no, legacy_doc_name, requirement_no, requirement_name, content_summary, propose_dept, proposer, monshang_ba, monshang_architect, expected_launch_date, regulator, regulation_doc_no, regulation_desc, regulation_launch_date, requirement_received_date, requirement_type, regulation_category, business_group, sub_group, jinke_contact, need_jinke_arch_decision, jinke_architect, ba_review_date, workload_date, finance_project_date, soft_doc_name, owner_conglomerate, owner_system, owner_contact, involve_cooperation, coord_conglomerate, coord_system, soft_submit_date, soft_review_date, planned_launch_date, actual_launch_date, launch_mode, requirement_status, remark, change_involved, change_info, change_review_conclusion, change_conclusion_status, change_remark, not_project_developed, current_stage, propose_stage_status, docking_stage_status, workload_stage_status, project_stage_status, soft_stage_status, launch_stage_status, workflow_instance_id, source, import_batch_id, created_by, updated_by, deleted) VALUES
(5101, 1, 1,
 '【蒙商银行】业务需求说明书ATM跨行转账类类交易规范性改造项目-业务小组-2024-5-09',
 'JG-W0332C-240507-001',
 'ATM渠道跨行转账类交易上送完整对手方姓名',
 'ATM渠道跨行转账类交易上送完整对手方姓名',
 '运营管理部', '谢斌',
 '', '',
 '2024-06-30',
 '中国银联股份有限公司',
 '《关于开展ATM渠道跨行转账类交易规范性改造的函》',
 '2022年1月19日中国人民银行、银保监会、证监会发布的《金融机构客户尽职调查和客户身份资料及交易记录保存管理办法》要求金融机构和从事汇兑业务的机构为客户汇出资金时，应当登记汇款人的姓名或者名称、账号、住所和收款人的姓名或者名称、账号等信息。涉及范围包括通过银联网络实施的各渠道转账，包括ATM转账、多媒体自助终端转账、柜面转账、固话I型转账、固话II型转账、互联网（手机）转账等',
 '2024-06-30',
 '2024-05-22',
 '监管需求',
 '国家级监管',
 '渠道运营小组', '支付结算组',
 '朱琳',
 '否',
 NULL,
 '2024-06-11',
 '2024-06-11',
 '2024-06-27',
 '附件3：【蒙商银行】需求规格说明书-ATM跨行转账类类交易规范性改造项目-20240523-V0.3(发布稿)',
 '上海事业群',
 'W0332C+银联CUPS业务子系统',
 '瞿真',
 '是',
 '成都事业群',
 'WP106A+ATM自助渠道',
 '2024-05-23',
 '2024-06-11',
 '2024-06-27',
 '2024-06-27',
 NULL,
 '软需评审通过',
 '【0611】713前投产，纳入建设合同',
 '否', NULL, NULL, NULL, NULL,
 '否',
 'SOFT',
 '已完成', '已完成', '已完成', '已完成', '进行中', '未开始',
 NULL,
 'ONLINE', NULL, 1, NULL, 0);

-- 三、wf_instance 审批流实例数据（关联审批中场景）
-- 需求 5002：风控规则前置改造（PROPOSE 阶段审批中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70001, 1, 900000000000011, 1, 'req-legacy:5002:PROPOSE:START', 'RUNNING', 0, 1002,
 '{"approverIds":[1001]}', '2026-08-10 09:00:00');

-- 需求 5006：贷后预警7级改造（PROJECT 阶段审批中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70002, 1, 900000000000011, 1, 'req-legacy:5006:PROJECT:START', 'RUNNING', 0, 1002,
 '{"approverIds":[1001]}', '2026-08-12 10:00:00');

-- 需求 5010：信用卡分期24期产品上线（DOCKING 阶段审批中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70003, 1, 900000000000011, 1, 'req-legacy:5010:DOCKING:START', 'RUNNING', 0, 1005,
 '{"approverIds":[1001]}', '2026-08-14 14:00:00');

-- 需求 5012：信用卡分期2.0投产（LAUNCH 阶段审批中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70004, 1, 900000000000011, 1, 'req-legacy:5012:LAUNCH:START', 'RUNNING', 0, 1005,
 '{"approverIds":[1001]}', '2026-08-15 09:00:00');

-- 需求 5015：对公跨境报文ISO 20022升级（SOFT 阶段审批中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70005, 1, 900000000000011, 1, 'req-legacy:5015:SOFT:START', 'RUNNING', 0, 1007,
 '{"approverIds":[1001]}', '2026-08-16 10:00:00');

-- 需求 5020：手机银行5.0上线（LAUNCH 阶段审批中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70006, 1, 900000000000011, 1, 'req-legacy:5020:LAUNCH:START', 'RUNNING', 0, 1009,
 '{"approverIds":[1001]}', '2026-08-17 14:00:00');

-- 差异 4002：风控规则触发节点差异（评审中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70007, 1, 900000000000010, 1, 'req-diff:4002', 'RUNNING', 0, 1004,
 '{"approverIds":[1001]}', '2026-08-05 10:00:00');

-- 差异 4022：跨境反洗钱校验差异（评审中）
INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at) VALUES
(70008, 1, 900000000000010, 1, 'req-diff:4022', 'RUNNING', 0, 1009,
 '{"approverIds":[1001]}', '2026-08-13 11:00:00');

-- 四、wf_task 待办任务（关联以上审批实例）
INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, assignee_id, status, created_at) VALUES
(80001, 1, 70001, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-10 09:00:00'),
(80002, 1, 70002, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-12 10:00:00'),
(80003, 1, 70003, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-14 14:00:00'),
(80004, 1, 70004, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-15 09:00:00'),
(80005, 1, 70005, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-16 10:00:00'),
(80006, 1, 70006, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-17 14:00:00'),
(80007, 1, 70007, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-05 10:00:00'),
(80008, 1, 70008, 'approval-1', 'approval-1', 'APPROVAL', 1001, 'PENDING', '2026-08-13 11:00:00');

-- 五、wf_task_action 审批动作记录（已完成的审批动作）
INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, target_user_id, comment, created_at) VALUES
-- 已通过的审批
(90001, 1, 70001, 80001, 'APPROVE', 1001, NULL, '同意推进', '2026-08-10 10:00:00'),
(90002, 1, 70002, 80002, 'APPROVE', 1001, NULL, '同意立项', '2026-08-12 11:00:00'),
(90003, 1, 70007, 80007, 'APPROVE', 1001, NULL, '评审通过', '2026-08-05 11:00:00'),
-- 已驳回的审批
(90004, 1, 70008, 80008, 'REJECT',  1001, NULL, '差异描述需补充外币折算口径', '2026-08-13 12:00:00');

-- 六、存量需求回填 workflow_instance_id（关联 wf_instance）
UPDATE req_legacy_requirement SET workflow_instance_id = '70001' WHERE id = 5002;
UPDATE req_legacy_requirement SET workflow_instance_id = '70002' WHERE id = 5006;
UPDATE req_legacy_requirement SET workflow_instance_id = '70003' WHERE id = 5010;
UPDATE req_legacy_requirement SET workflow_instance_id = '70004' WHERE id = 5012;
UPDATE req_legacy_requirement SET workflow_instance_id = '70005' WHERE id = 5015;
UPDATE req_legacy_requirement SET workflow_instance_id = '70006' WHERE id = 5020;

-- 七、差异回填 workflow_instance_id
UPDATE req_difference SET workflow_instance_id = '70007' WHERE id = 4002;
UPDATE req_difference SET workflow_instance_id = '70008' WHERE id = 4022;

-- 八、req_stage_log 回填 approval_result + workflow_instance_id（V41 新增字段）
INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, approval_result, workflow_instance_id, deleted) VALUES
(7101, 1, 5002, NULL,      'PROPOSE',  NULL,      '审批中', 1002, '李 retail1 BA', '发起需求提出审批',        'PENDING', '70001', 0),
(7102, 1, 5006, 'WORKLOAD','PROJECT',  '已完成',  '审批中', 1002, '李 retail1 BA', '发起立项审批',            'PENDING', '70002', 0),
(7103, 1, 5010, 'PROPOSE', 'DOCKING',  '已完成',  '审批中', 1005, '钱 retail2 BA', '发起需求对接审批',        'PENDING', '70003', 0),
(7104, 1, 5012, 'SOFT',    'LAUNCH',   '已完成',  '审批中', 1005, '钱 retail2 BA', '发起投产审批',            'PENDING', '70004', 0),
(7105, 1, 5015, 'PROJECT', 'SOFT',     '已完成',  '审批中', 1007, '周 corp1 BA',   '发起软需审批',            'PENDING', '70005', 0),
(7106, 1, 5020, 'SOFT',    'LAUNCH',   '已完成',  '审批中', 1009, '郑 channel1 PM','发起投产审批',            'PENDING', '70006', 0),
-- 已通过的审批回写
(7107, 1, 5003, NULL,      'PROPOSE',  NULL,      '已完成', 1002, '李 retail1 BA', '审批通过，正式进入需求提出','APPROVED', NULL, 0),
(7108, 1, 5004, 'PROPOSE', 'DOCKING',  '已完成',  '已完成', 1002, '李 retail1 BA', '审批通过，进入需求对接',  'APPROVED', NULL, 0),
(7109, 1, 5008, 'SOFT',    'LAUNCH',   '已完成',  '已完成', 1002, '李 retail1 BA', '审批通过，完成上线',      'APPROVED', NULL, 0);

-- 九、补充 change_log 记录（关联审批结果）
INSERT INTO req_change_log (id, tenant_id, biz_type, biz_id, field_name, old_value, new_value, change_type, operator_id, operator_name, source, trace_id, deleted) VALUES
-- 需求 5002 提交审批
(8101, 1, 'LEGACY_REQUIREMENT', 5002, 'propose_stage_status', '进行中', '审批中', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5002-001', 0),
(8102, 1, 'LEGACY_REQUIREMENT', 5002, 'workflow_instance_id', NULL, '70001', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5002-002', 0),
-- 需求 5006 提交立项审批
(8103, 1, 'LEGACY_REQUIREMENT', 5006, 'project_stage_status', '进行中', '审批中', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5006-002', 0),
-- 差异 4002 提交评审
(8104, 1, 'NEW_PROJECT_DIFF',   4002, 'review_status', '待评审', '评审中', 'SUBMIT_REVIEW', 1004, '赵 retail1 PM', 'ONLINE', 'TRACE-4002-001', 0),
-- 差异 4004 退回
(8105, 1, 'NEW_PROJECT_DIFF',   4004, 'review_status', '评审中', '已退回', 'REVIEW_RETURN', 1001, '张统筹', 'WORKFLOW', 'TRACE-4004-003', 0),
-- 需求 5004 审批通过
(8106, 1, 'LEGACY_REQUIREMENT', 5004, 'requirement_status', '需求分析', '业需评审通过', 'STAGE_TRANSITION', 1, '审批系统', 'WORKFLOW', 'TRACE-5004-003', 0),
-- Excel 案例 5101 软需评审通过
(8107, 1, 'LEGACY_REQUIREMENT', 5101, 'soft_stage_status', '进行中', '已完成', 'STAGE_TRANSITION', 1, '审批系统', 'WORKFLOW', 'TRACE-5101-001', 0);

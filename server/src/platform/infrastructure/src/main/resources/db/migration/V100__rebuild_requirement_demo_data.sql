-- =============================================================================
-- V100：需求管理模块测试数据重建
-- 先清空需求模块全部业务数据，再按“不同项目 3/6/9 条”重建：
--   新建项目差异：3001=3 条 / 3002=6 条（已基线）/ 3003=9 条
--   存量项目需求：3001=3 条 / 3002=6 条 / 3003=9 条（project_id 归属项目）
-- 数据覆盖不同场景（评审状态、开发/测试状态、阶段推进、基线）。
-- 仅追加，不修改历史迁移；幂等前提：V100 在 Flyway 中只执行一次。
-- =============================================================================

-- 一、清空需求模块业务数据
DELETE FROM req_difference_flow_log;
DELETE FROM req_baseline_item;
DELETE FROM req_baseline;
DELETE FROM req_difference;
DELETE FROM req_legacy_system_member;
DELETE FROM req_legacy_system_item;
DELETE FROM req_legacy_member;
DELETE FROM req_flow_log;
DELETE FROM req_review_record;
DELETE FROM req_workload;
DELETE FROM req_soft_doc;
DELETE FROM req_coordination_item;
DELETE FROM req_requirement_version;
DELETE FROM req_stage_log;
DELETE FROM req_change_log;
DELETE FROM req_import_batch;
DELETE FROM req_attachment;
DELETE FROM req_project_member;
DELETE FROM req_business_group_member;
DELETE FROM req_legacy_requirement;
DELETE FROM req_project;
DELETE FROM req_system;

-- 二、系统清单主数据（10 个系统覆盖 4 个业务组）
INSERT INTO req_system (id, tenant_id, system_code, system_name, english_name, conglomerate, status, business_domain, introduction, created_by, deleted) VALUES
    (2001, 1, 'S-RETAIL-CORE',  '零售核心系统',   'RTL-CORE', '零售事业群', 'ACTIVE',   '零售存款/贷款',  '零售核心账务与产品引擎', 1, 0),
    (2002, 1, 'S-RETAIL-LOAN',  '零售贷款系统',   'RTL-LOAN', '零售事业群', 'ACTIVE',   '零售信贷',       '零售贷款全流程受理与放款', 1, 0),
    (2003, 1, 'S-CREDIT-CARD',  '信用卡系统',     'CC',       '零售事业群', 'ACTIVE',   '信用卡',         '信用卡账户与分期', 1, 0),
    (2004, 1, 'S-RETAIL-PAY',   '零售支付系统',   'RTL-PAY',  '零售事业群', 'ACTIVE',   '支付清算',       '零售支付路由与清算', 1, 0),
    (2005, 1, 'S-CORP-CORE',    '对公核心系统',   'CORP-CORE','对公事业群', 'ACTIVE',   '对公存款/贷款',  '对公核心账务', 1, 0),
    (2006, 1, 'S-CORP-LOAN',    '对公贷款系统',   'CORP-LOAN','对公事业群', 'ACTIVE',   '对公信贷',       '对公贷款额度与放款', 1, 0),
    (2007, 1, 'S-CHNL-NETBANK', '渠道网银系统',   'NETBANK',  '渠道事业群', 'ACTIVE',   '电子渠道',       '企业网银', 1, 0),
    (2008, 1, 'S-CHNL-MOBILE',  '手机银行系统',   'MOBILE',   '渠道事业群', 'ACTIVE',   '电子渠道',       '个人手机银行', 1, 0),
    (2009, 1, 'S-RISK-DEC',     '风控决策系统',   'RISK',     '零售事业群', 'INACTIVE', '风险控制',       '风控规则引擎（已停用）', 1, 0),
    (2010, 1, 'S-DATA-MID',     '数据中台',       'DATA-MID', '零售事业群', 'ACTIVE',   '数据',           '指标计算与数据服务', 1, 0);

-- 三、项目主数据（3 个项目）
INSERT INTO req_project (id, tenant_id, project_code, project_name, project_type, start_time, status, description, created_by, deleted) VALUES
    (3001, 1, 'P2026-001', '零售贷款 2.0 升级项目', '0~1 新建', '2026-08-01', 'ACTIVE',    '零售贷款流程线上化与风控规则升级', 1, 0),
    (3002, 1, 'P2026-002', '信用卡分期优化项目',     '0~1 新建', '2026-07-15', 'BASELINED', '信用卡分期方案灵活化与权益打通（已基线）', 1, 0),
    (3003, 1, 'P2026-003', '对公跨境支付项目',       '0~1 新建', '2026-08-10', 'ACTIVE',    '对公跨境收付汇与跨境清算', 1, 0);

INSERT INTO req_project_member (id, tenant_id, project_id, user_id, member_role, created_by, deleted) VALUES
    (52001, 1, 3001, 1004, 'LEADER', 1, 0),
    (52002, 1, 3001, 1002, 'MEMBER', 1, 0),
    (52003, 1, 3001, 1003, 'MEMBER', 1, 0),
    (52004, 1, 3002, 1006, 'LEADER', 1, 0),
    (52005, 1, 3002, 1005, 'MEMBER', 1, 0),
    (52006, 1, 3003, 1009, 'LEADER', 1, 0),
    (52007, 1, 3003, 1007, 'MEMBER', 1, 0),
    (52008, 1, 3003, 1008, 'MEMBER', 1, 0);

INSERT INTO req_business_group_member (id, tenant_id, business_group, user_id, created_by, deleted) VALUES
    (51001, 1, '零售一组', 1002, 1, 0),
    (51002, 1, '零售一组', 1003, 1, 0),
    (51003, 1, '零售一组', 1004, 1, 0),
    (51004, 1, '零售二组', 1005, 1, 0),
    (51005, 1, '零售二组', 1006, 1, 0),
    (51006, 1, '对公一组', 1007, 1, 0),
    (51007, 1, '对公一组', 1008, 1, 0),
    (51008, 1, '渠道一组', 1009, 1, 0);

-- 四、新建项目差异：3001=3 条 / 3002=6 条（已基线）/ 3003=9 条
INSERT INTO req_difference
    (id, tenant_id, project_id, seq_no, business_conglomerate, business_section, business_group, requirement_no, category, name, system_id, jinke_practice, difference_type, monshang_practice, difference_desc, monshang_dept, monshang_analyst, jinke_analyst, adapt_mode, handle_status, coord_group, solution, is_special, decision_level, decision_conclusion, monshang_confirm_dept, jinke_confirmer, review_status, review_comment, reviewed_by, reviewed_at, workflow_instance_id, dev_status, test_status, baseline_id, source, import_batch_id, created_by, updated_by, deleted, current_handler_user_id, current_handler_user_name) VALUES

    (4101, 1, 3001, 1, '零售事业群', '零售业务板块', '零售一组', 'W01812-101', '功能', '线上受理影像采集改造', 2002, '金科支持影像采集组件', '金科有-蒙商无', '蒙商当前为线下手工受理', '将线下受理改为线上，覆盖影像采集与 OCR 识别', '零售业务部', '李 retail1 BA', '王 retail1 架构', '按原型', '双方已确认', '贷款组', '按金科原型实现', '否', '版块内', '双方已确认', '零售部', '王 retail1 架构', '待评审', NULL, NULL, NULL, NULL, '未开始', '未开始', NULL, 'ONLINE', NULL, 1002, NULL, 0, 1002, '李 retail1 BA'),
    (4102, 1, 3001, 2, '零售事业群', '零售业务板块', '零售一组', 'W01812-102', '流程', '风控规则前置改造', 2002, '风控规则从授信后挪到授信前', '双方作法有差异', '蒙商在授信后做风控', '调整风控时机，降低风险敞口', '零售业务部', '李 retail1 BA', '王 retail1 架构', '少量改造', '待决策', '贷款组', '改造风控决策流程', '否', '版块内', NULL, '零售部', '王 retail1 架构', '评审中', NULL, NULL, NULL, NULL, '开发中', '未开始', NULL, 'ONLINE', NULL, 1002, NULL, 0, 1002, '李 retail1 BA'),
    (4103, 1, 3001, 3, '零售事业群', '零售业务板块', '零售一组', 'W01812-103', '报表', '贷款合同模板字段补全', 2002, '补齐 6 个监管必填字段', '蒙商有-金科无', '蒙商合同模板已含部分字段', '补充监管必填字段并接入合同模板', '零售业务部', '李 retail1 BA', '王 retail1 架构', '参数配置', '待行方确认', '贷款组', '模板字段参数化', '否', '版块内', NULL, '零售部', '王 retail1 架构', '已退回', '请补充监管字段对照表', 1, '2026-08-15 10:00:00', NULL, '未开始', '未开始', NULL, 'ONLINE', NULL, 1002, NULL, 0, 1002, '李 retail1 BA'),
    (4201, 1, 3002, 1, '零售事业群', '零售业务板块', '零售二组', 'W02234-201', '功能', '信用卡分期 App 渠道接入', 2003, '在 App 中接入分期申请入口', '金科有-蒙商无', '蒙商无分期入口', 'App 接入分期申请入口', '信用卡部', '钱 retail2 BA', NULL, '按原型', '双方已确认', '分期组', '按原型实现', '否', '版块内', '双方已确认', '零售部', '孙 retail2 PM', '已评审', NULL, 1, '2026-08-18 14:00:00', NULL, '已上线', '已通过', 6001, 'ONLINE', NULL, 1005, NULL, 0, 1005, '钱 retail2 BA'),
    (4202, 1, 3002, 2, '零售事业群', '零售业务板块', '零售二组', 'W02234-202', '产品', '信用卡分期 24 期产品上线', 2003, '增加 24 期分期产品', '金科有-蒙商手工', '蒙商手工配置分期', '24 期分期产品参数化上线', '信用卡部', '钱 retail2 BA', NULL, '参数配置', '双方已确认', '分期组', '产品参数配置', '否', '版块内', '双方已确认', '零售部', '孙 retail2 PM', '已评审', NULL, 1, '2026-08-19 10:30:00', NULL, '已上线', '已通过', 6001, 'ONLINE', NULL, 1005, NULL, 0, 1005, '钱 retail2 BA'),
    (4203, 1, 3002, 3, '零售事业群', '零售业务板块', '零售二组', 'W02234-203', '规则', '信用卡积分 1 倍规则对齐', 2003, '积分倍数从 2 倍改为 1 倍', '双方作法有差异', '蒙商当前 2 倍积分', '对齐金科 1 倍积分规则', '信用卡部', '钱 retail2 BA', NULL, '少量改造', '双方已确认', '积分组', '积分规则调整', '否', '版块内', '双方已确认', '零售部', '孙 retail2 PM', '已评审', NULL, 1, '2026-08-20 09:00:00', NULL, '已完成', '测试中', 6001, 'ONLINE', NULL, 1005, NULL, 0, 1005, '钱 retail2 BA'),
    (4204, 1, 3002, 4, '零售事业群', '零售业务板块', '零售二组', 'W02234-204', '批处理', '分期对账批量改造', 2003, '调整对账批量处理逻辑', '金科有-蒙商无', '蒙商无自动对账', '新增分期对账批量', '信用卡部', '钱 retail2 BA', NULL, '按原型', '双方已确认', '对账组', '按原型实现', '否', '版块内', '双方已确认', '零售部', '孙 retail2 PM', '已评审', NULL, 1, '2026-08-21 11:00:00', NULL, '已完成', '已通过', 6001, 'ONLINE', NULL, 1005, NULL, 0, 1005, '钱 retail2 BA'),
    (4205, 1, 3002, 5, '零售事业群', '零售业务板块', '零售二组', 'W02234-205', '菜单', '分期菜单结构调整', 2003, '菜单层级调整', '蒙商有-金科无', '蒙商菜单更细', '对齐金科菜单结构', '信用卡部', '钱 retail2 BA', NULL, '保留现状', '双方已确认', '渠道组', '菜单结构保留蒙商现状', '否', '版块内', '双方已确认', '零售部', '孙 retail2 PM', '已评审', NULL, 1, '2026-08-22 15:00:00', NULL, '已完成', '已通过', 6001, 'ONLINE', NULL, 1005, NULL, 0, 1005, '钱 retail2 BA'),
    (4206, 1, 3002, 6, '零售事业群', '零售业务板块', '零售二组', 'W02234-206', '报表', '分期经营报表改造', 2003, '新增分期经营报表', '金科有-蒙商手工', '蒙商手工出报表', '分期经营报表自动化', '信用卡部', '钱 retail2 BA', NULL, '少量改造', '双方已确认', '报表组', '报表口径对齐', '否', '版块内', '双方已确认', '零售部', '孙 retail2 PM', '已评审', NULL, 1, '2026-08-23 10:00:00', NULL, '已完成', '已通过', 6001, 'ONLINE', NULL, 1005, NULL, 0, 1005, '钱 retail2 BA'),
    (4301, 1, 3003, 1, '对公事业群', '对公业务板块', '对公一组', 'W0441C-301', '功能', '对公跨境汇款多路径改造', 2006, '增加多路径支持', '金科有-蒙商无', '蒙商仅单一路径', '多路径提升汇款成功率', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '按原型', '双方已确认', '跨境组', '按原型实现', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '待评审', NULL, NULL, NULL, NULL, '未开始', '未开始', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4302, 1, 3003, 2, '对公事业群', '对公业务板块', '对公一组', 'W0441C-302', '功能', '跨境反洗钱实时校验', 2006, '由批量校验改为实时校验', '金科有-蒙商手工', '蒙商批量校验', '实时反洗钱校验', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '少量改造', '待决策', '跨境组', '接入实时校验服务', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '待评审', NULL, NULL, NULL, NULL, '开发中', '未开始', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4303, 1, 3003, 3, '对公事业群', '对公业务板块', '对公一组', 'W0441C-303', '技术', '跨境报文 ISO 20022 升级', 2006, '从 MT103 升级为 ISO 20022', '双方作法有差异', '蒙商 MT103', '报文标准升级', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '按原型', '双方已确认', '报文组', '报文转换层改造', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '评审中', NULL, NULL, NULL, NULL, '开发中', '测试中', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4304, 1, 3003, 4, '对公事业群', '对公业务板块', '对公一组', 'W0441C-304', '功能', '跨境汇款手续费计算调整', 2006, '手续费计算规则调整', '蒙商有-金科无', '蒙商规则更细', '对齐金科手续费规则', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '参数配置', '待行方确认', '跨境组', '规则参数化', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '已退回', '请补充手续费计算样例', 1, '2026-08-25 16:00:00', NULL, '未开始', '未开始', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4305, 1, 3003, 5, '对公事业群', '对公业务板块', '对公一组', 'W0441C-305', '报表', '跨境结算报表升级', 2006, '跨境结算报表字段扩展', '金科有-蒙商无', '蒙商无对应报表', '扩展跨境结算报表', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '按原型', '双方已确认', '报表组', '按原型实现', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '评审中', NULL, NULL, NULL, NULL, '已完成', '测试中', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4306, 1, 3003, 6, '对公事业群', '对公业务板块', '对公一组', 'W0441C-306', '功能', '跨境汇款额度管理', 2006, '增加额度管控', '金科有-蒙商手工', '蒙商手工控制额度', '额度管理线上化', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '少量改造', '双方已确认', '跨境组', '额度管控改造', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '待评审', NULL, NULL, NULL, NULL, '未开始', '未开始', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4307, 1, 3003, 7, '对公事业群', '对公业务板块', '对公一组', 'W0441C-307', '流程', '跨境汇款审批流调整', 2006, '审批流层级调整', '双方作法有差异', '蒙商审批流更严', '对齐金科审批层级', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '少量改造', '双方已确认', '审批组', '审批流参数调整', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '已评审', NULL, 1, '2026-08-26 09:30:00', NULL, '已完成', '已通过', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4308, 1, 3003, 8, '对公事业群', '对公业务板块', '对公一组', 'W0441C-308', '批处理', '跨境清算批量改造', 2006, '清算批量处理逻辑调整', '金科有-蒙商无', '蒙商无自动清算', '跨境清算批量自动化', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '按原型', '双方已确认', '清算组', '按原型实现', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '已评审', NULL, 1, '2026-08-27 10:00:00', NULL, '已上线', '已通过', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA'),
    (4309, 1, 3003, 9, '对公事业群', '对公业务板块', '对公一组', 'W0441C-309', '菜单', '对公网银菜单调整', 2006, '企业网银菜单层级调整', '蒙商有-金科无', '蒙商菜单更细', '对齐金科网银菜单', '对公业务部', '周 corp1 BA', '吴 corp1 架构', '保留现状', '双方已确认', '渠道组', '保留蒙商现状', '否', '版块内', NULL, '对公部', '吴 corp1 架构', '待评审', NULL, NULL, NULL, NULL, '未开始', '未开始', NULL, 'ONLINE', NULL, 1007, NULL, 0, 1007, '周 corp1 BA');

-- 五、基线（项目 3002 的 6 条已评审差异）
INSERT INTO req_baseline (id, tenant_id, project_id, baseline_no, baseline_name, status, difference_count, remark, created_by, deleted) VALUES
    (6001, 1, 3002, 'BL-P2026-002-20260815-1', '信用卡分期优化项目首版基线', 'RELEASED', 6, '评审通过的全部差异锁定', 1, 0);

INSERT INTO req_baseline_item (id, tenant_id, baseline_id, difference_id, snapshot_json, deleted) VALUES
    (6101, 1, 6001, 4201, '{"name":"信用卡分期 App 渠道接入","review_status":"已评审","requirement_no":"W02234-201"}', 0),
    (6102, 1, 6001, 4202, '{"name":"信用卡分期 24 期产品上线","review_status":"已评审","requirement_no":"W02234-202"}', 0),
    (6103, 1, 6001, 4203, '{"name":"信用卡积分 1 倍规则对齐","review_status":"已评审","requirement_no":"W02234-203"}', 0),
    (6104, 1, 6001, 4204, '{"name":"分期对账批量改造","review_status":"已评审","requirement_no":"W02234-204"}', 0),
    (6105, 1, 6001, 4205, '{"name":"分期菜单结构调整","review_status":"已评审","requirement_no":"W02234-205"}', 0),
    (6106, 1, 6001, 4206, '{"name":"分期经营报表改造","review_status":"已评审","requirement_no":"W02234-206"}', 0);

-- 六、存量项目需求：3001=3 条 / 3002=6 条 / 3003=9 条（project_id 归属项目）
INSERT INTO req_legacy_requirement
    (id, tenant_id, project_id, legacy_doc_name, requirement_no, requirement_name, content_summary, propose_dept, proposer, monshang_ba, monshang_architect, expected_launch_date, regulator, regulation_doc_no, regulation_desc, regulation_launch_date, requirement_received_date, requirement_type, regulation_category, business_group, sub_group, jinke_contact, need_jinke_arch_decision, jinke_architect, unified_managed, ba_review_date, workload_date, finance_project_date, soft_doc_name, owner_conglomerate, owner_system, owner_contact, involve_cooperation, coord_conglomerate, coord_system, soft_submit_date, soft_review_date, planned_launch_date, actual_launch_date, launch_mode, requirement_status, remark, change_involved, change_info, change_review_conclusion, change_conclusion_status, change_remark, not_project_developed, version_no, workload_change, workload_person_months, current_flow_user_id, current_flow_user_name, current_stage, propose_stage_status, docking_stage_status, workload_stage_status, project_stage_status, soft_stage_status, launch_stage_status, source, import_batch_id, created_by, updated_by, deleted) VALUES
    -- 项目 3001：3 条
    (5001, 1, 3001, '业需-零售-2026-001', 'JG-W0332C-240507-001', '零售贷款线上受理改造', '将线下受理改为线上，覆盖影像采集与 OCR 识别', '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-12-31', NULL, NULL, NULL, NULL, '2026-08-01', '业务', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '是', '王 retail1 架构', '是', NULL, NULL, NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '新建需求', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'PROPOSE',  '进行中', '未开始', '未开始', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1002, NULL, 0),
    (5002, 1, 3001, '业需-零售-2026-002', 'JG-W0332C-240507-002', '风控规则前置改造', '风控规则从授信后挪到授信前，降低风险敞口', '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-11-30', NULL, NULL, NULL, NULL, '2026-08-05', '业务', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '是', '王 retail1 架构', '是', '2026-08-15', NULL, NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '是', '零售事业群', 'S-RISK-DEC 风控决策系统', NULL, NULL, NULL, NULL, NULL, '业需评审通过', '需求对接中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'DOCKING',  '已完成', '进行中', '未开始', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1002, NULL, 0),
    (5003, 1, 3001, '业需-零售-2026-003', 'JG-W0332C-240507-003', '贷款合同模板字段补全', '补齐 6 个监管必填字段并接入合同模板', '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-11-15', '银保监会', '个人贷款管理办法 2024 修订版', '补充 6 个监管字段', '2026-10-31', '2026-08-04', '监管', '国家级', '零售一组', '贷款组', '王 retail1 架构 13800001003', '否', NULL, '是', '2026-08-20', '2026-09-01', NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '工作量评估进行中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, '2.5 人月', NULL, NULL, 'WORKLOAD', '已完成', '已完成', '进行中', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1002, NULL, 0),
    -- 项目 3002：6 条
    (5101, 1, 3002, '业需-零售-2026-101', 'JG-W0332C-240508-101', '信用卡分期 App 渠道接入', '在 App 中接入分期申请入口', '信用卡部', '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL, '2026-12-31', NULL, NULL, NULL, NULL, '2026-08-15', '业务', NULL, '零售二组', '分期组', NULL, '否', NULL, '否', NULL, NULL, NULL, NULL, '零售事业群', 'S-CREDIT-CARD 信用卡系统', '孙 retail2 PM 13800001006', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '待启动', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'PROPOSE',  '进行中', '未开始', '未开始', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1005, NULL, 0),
    (5102, 1, 3002, '业需-零售-2026-102', 'JG-W0332C-240508-102', '信用卡分期 24 期产品上线', '增加 24 期分期产品', '信用卡部', '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL, '2026-11-30', NULL, NULL, NULL, NULL, '2026-08-18', '业务', NULL, '零售二组', '分期组', NULL, '否', NULL, '否', '2026-08-22', NULL, NULL, NULL, '零售事业群', 'S-CREDIT-CARD 信用卡系统', '孙 retail2 PM 13800001006', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '需求对接中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'DOCKING',  '已完成', '进行中', '未开始', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1005, NULL, 0),
    (5103, 1, 3002, '业需-零售-2026-103', 'JG-W0332C-240508-103', '信用卡积分 1 倍规则对齐', '积分倍数从 2 倍改为 1 倍', '信用卡部', '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL, '2026-12-15', NULL, NULL, NULL, NULL, '2026-08-20', '业务', NULL, '零售二组', '积分组', NULL, '否', NULL, '否', '2026-08-25', '2026-09-05', NULL, NULL, '零售事业群', 'S-CREDIT-CARD 信用卡系统', '孙 retail2 PM 13800001006', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '工作量评估进行中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, '1.0 人月', NULL, NULL, 'WORKLOAD', '已完成', '已完成', '进行中', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1005, NULL, 0),
    (5104, 1, 3002, '业需-零售-2026-104', 'JG-W0332C-240508-104', '分期对账批量改造', '调整对账批量处理逻辑', '信用卡部', '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL, '2026-12-20', NULL, NULL, NULL, NULL, '2026-08-22', '技术', NULL, '零售二组', '对账组', NULL, '否', NULL, '否', '2026-08-28', '2026-09-08', '2026-09-25', NULL, '零售事业群', 'S-CREDIT-CARD 信用卡系统', '孙 retail2 PM 13800001006', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '立项中', '立项审批中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'PROJECT',  '已完成', '已完成', '已完成', '进行中', '未开始', '未开始', 'ONLINE', NULL, 1005, NULL, 0),
    (5105, 1, 3002, '业需-零售-2026-105', 'JG-W0332C-240508-105', '分期经营报表改造', '新增分期经营报表', '信用卡部', '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL, '2027-01-15', NULL, NULL, NULL, NULL, '2026-08-25', '业务', NULL, '零售二组', '报表组', NULL, '否', NULL, '否', '2026-08-30', '2026-09-10', '2026-09-28', '软需-零售-2026-105', '零售事业群', 'S-CREDIT-CARD 信用卡系统', '孙 retail2 PM 13800001006', '否', NULL, NULL, '2026-10-10', '2026-10-25', NULL, NULL, NULL, '软需编制', '软需编制中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'SOFT',     '已完成', '已完成', '已完成', '已完成', '进行中', '未开始', 'ONLINE', NULL, 1005, NULL, 0),
    (5106, 1, 3002, '业需-零售-2026-106', 'JG-W0332C-240508-106', '信用卡分期 2.0 投产', '完成分期 2.0 全量上线', '信用卡部', '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL, '2027-03-31', NULL, NULL, NULL, NULL, '2026-08-28', '业务', NULL, '零售二组', '投产组', NULL, '否', NULL, '否', '2026-09-01', '2026-09-12', '2026-09-30', '软需-零售-2026-106', '零售事业群', 'S-CREDIT-CARD 信用卡系统', '孙 retail2 PM 13800001006', '否', NULL, NULL, '2026-10-15', '2026-11-01', '2027-03-20', '2027-03-31', '常规版本', '软需编制', '投产准备中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'LAUNCH',   '已完成', '已完成', '已完成', '已完成', '已完成', '进行中', 'ONLINE', NULL, 1005, NULL, 0),
    -- 项目 3003：9 条
    (5201, 1, 3003, '业需-对公-2026-201', 'JG-W0332C-240509-201', '对公跨境汇款多路径改造', '增加多路径支持，提升汇款成功率', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2026-12-31', NULL, NULL, NULL, NULL, '2026-08-12', '业务', NULL, '对公一组', '跨境组', '吴 corp1 架构 13800001008', '否', NULL, '是', NULL, NULL, NULL, NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '需求分析中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'PROPOSE',  '进行中', '未开始', '未开始', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1007, NULL, 0),
    (5202, 1, 3003, '业需-对公-2026-202', 'JG-W0332C-240509-202', '跨境反洗钱实时校验', '由批量校验改为实时校验', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2026-11-30', '人民银行', '反洗钱管理办法 2024', '改为实时校验', '2026-11-30', '2026-08-15', '监管', '国家级', '对公一组', '跨境组', '吴 corp1 架构 13800001008', '是', '吴 corp1 架构', '是', NULL, NULL, NULL, NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '需求分析中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'PROPOSE',  '已完成', '未开始', '未开始', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1007, NULL, 0),
    (5203, 1, 3003, '业需-对公-2026-203', 'JG-W0332C-240509-203', '跨境报文 ISO 20022 升级', '从 MT103 升级为 ISO 20022', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2026-12-15', NULL, NULL, NULL, NULL, '2026-08-18', '技术', NULL, '对公一组', '报文组', '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-08-22', NULL, NULL, NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '需求对接中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'DOCKING',  '已完成', '进行中', '未开始', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1007, NULL, 0),
    (5204, 1, 3003, '业需-对公-2026-204', 'JG-W0332C-240509-204', '跨境汇款手续费计算调整', '手续费计算规则调整', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2026-12-20', NULL, NULL, NULL, NULL, '2026-08-20', '业务', NULL, '对公一组', '跨境组', '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-08-25', NULL, NULL, NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '工作量评估进行中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, '3.0 人月', NULL, NULL, 'WORKLOAD', '已完成', '已完成', '进行中', '未开始', '未开始', '未开始', 'ONLINE', NULL, 1007, NULL, 0),
    (5205, 1, 3003, '业需-对公-2026-205', 'JG-W0332C-240509-205', '跨境结算报表升级', '跨境结算报表字段扩展', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2027-01-15', NULL, NULL, NULL, NULL, '2026-08-22', '业务', NULL, '对公一组', '报表组', '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-08-28', '2026-09-08', '2026-09-25', NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '立项审批中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'PROJECT',  '已完成', '已完成', '已完成', '进行中', '未开始', '未开始', 'ONLINE', NULL, 1007, NULL, 0),
    (5206, 1, 3003, '业需-对公-2026-206', 'JG-W0332C-240509-206', '跨境汇款额度管理', '增加额度管控', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2027-01-31', NULL, NULL, NULL, NULL, '2026-08-25', '业务', NULL, '对公一组', '跨境组', '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-08-30', '2026-09-10', '2026-09-28', NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '立项中', '软需编制中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'SOFT',     '已完成', '已完成', '已完成', '已完成', '进行中', '未开始', 'ONLINE', NULL, 1007, NULL, 0),
    (5207, 1, 3003, '业需-对公-2026-207', 'JG-W0332C-240509-207', '跨境汇款审批流调整', '审批流层级调整', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2027-02-15', NULL, NULL, NULL, NULL, '2026-08-28', '业务', NULL, '对公一组', '审批组', '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-09-01', '2026-09-12', '2026-09-30', '软需-对公-2026-207', '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, '2026-10-15', '2026-11-01', NULL, NULL, NULL, '软需编制', '软需编制中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'SOFT',     '已完成', '已完成', '已完成', '已完成', '进行中', '未开始', 'ONLINE', NULL, 1007, NULL, 0),
    (5208, 1, 3003, '业需-对公-2026-208', 'JG-W0332C-240509-208', '跨境清算批量改造', '清算批量处理逻辑调整', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2027-03-15', NULL, NULL, NULL, NULL, '2026-08-30', '技术', NULL, '对公一组', '清算组', '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-09-05', '2026-09-15', '2026-10-05', '软需-对公-2026-208', '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, '2026-10-20', '2026-11-05', NULL, NULL, NULL, '软需编制', '投产准备中', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'LAUNCH',   '已完成', '已完成', '已完成', '已完成', '已完成', '进行中', 'ONLINE', NULL, 1007, NULL, 0),
    (5209, 1, 3003, '业需-对公-2026-209', 'JG-W0332C-240509-209', '对公跨境支付 1.0 上线', '完成对公跨境支付全量上线', '对公业务部', '周 corp1 BA 13800001007', '周 corp1 BA', '吴 corp1 架构', '2027-03-31', NULL, NULL, NULL, NULL, '2026-09-01', '业务', NULL, '对公一组', '投产组', '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-09-08', '2026-09-18', '2026-10-08', '软需-对公-2026-209', '对公事业群', 'S-CORP-LOAN 对公贷款系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, '2026-10-22', '2026-11-08', '2027-03-20', '2027-03-31', '常规版本', '已投产', '已完成上线', '否', NULL, NULL, NULL, NULL, '否', '1.0', NULL, NULL, NULL, NULL, 'LAUNCH',   '已完成', '已完成', '已完成', '已完成', '已完成', '已完成', 'ONLINE', NULL, 1007, NULL, 0);

-- 七、存量需求系统子表（主责行，按项目对应系统）
INSERT INTO req_legacy_system_item (id, tenant_id, requirement_id, system_role, system_code, system_name, owner_user_id, owner_user_name, remark, created_by, deleted) VALUES
    (7101, 1, 5001, '主责', 'S-RETAIL-LOAN', '零售贷款系统', 1002, '李 retail1 BA', '演示主责系统', 1, 0),
    (7102, 1, 5002, '主责', 'S-RETAIL-LOAN', '零售贷款系统', 1002, '李 retail1 BA', '演示主责系统', 1, 0),
    (7103, 1, 5003, '主责', 'S-RETAIL-LOAN', '零售贷款系统', 1002, '李 retail1 BA', '演示主责系统', 1, 0),
    (7104, 1, 5101, '主责', 'S-CREDIT-CARD', '信用卡系统', 1005, '钱 retail2 BA', '演示主责系统', 1, 0),
    (7105, 1, 5102, '主责', 'S-CREDIT-CARD', '信用卡系统', 1005, '钱 retail2 BA', '演示主责系统', 1, 0),
    (7106, 1, 5103, '主责', 'S-CREDIT-CARD', '信用卡系统', 1005, '钱 retail2 BA', '演示主责系统', 1, 0),
    (7107, 1, 5104, '主责', 'S-CREDIT-CARD', '信用卡系统', 1005, '钱 retail2 BA', '演示主责系统', 1, 0),
    (7108, 1, 5105, '主责', 'S-CREDIT-CARD', '信用卡系统', 1005, '钱 retail2 BA', '演示主责系统', 1, 0),
    (7109, 1, 5106, '主责', 'S-CREDIT-CARD', '信用卡系统', 1005, '钱 retail2 BA', '演示主责系统', 1, 0),
    (7110, 1, 5201, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7111, 1, 5202, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7112, 1, 5203, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7113, 1, 5204, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7114, 1, 5205, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7115, 1, 5206, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7116, 1, 5207, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7117, 1, 5208, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0),
    (7118, 1, 5209, '主责', 'S-CORP-LOAN', '对公贷款系统', 1007, '周 corp1 BA', '演示主责系统', 1, 0);

-- 八、阶段流转与改动记录（少量留痕）
INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, approval_result, workflow_instance_id, deleted) VALUES
    (8001, 1, 5001, NULL,      'PROPOSE', NULL,      '进行中', 1002, '李 retail1 BA', '创建需求后自动进入需求提出', 'MANUAL', NULL, 0),
    (8002, 1, 5002, 'PROPOSE', 'DOCKING', '已完成',  '进行中', 1002, '李 retail1 BA', '业需评审通过，进入需求对接', 'MANUAL', NULL, 0),
    (8003, 1, 5003, 'DOCKING', 'WORKLOAD','已完成',  '进行中', 1002, '李 retail1 BA', '工作量评估开始', 'MANUAL', NULL, 0),
    (8004, 1, 5209, 'SOFT',    'LAUNCH',  '已完成',  '进行中', 1007, '周 corp1 BA',   '软需评审通过，进入投产', 'MANUAL', NULL, 0);

INSERT INTO req_change_log (id, tenant_id, biz_type, biz_id, field_name, old_value, new_value, change_type, operator_id, operator_name, source, trace_id, deleted) VALUES
    (8101, 1, 'NEW_PROJECT_DIFF',   4103, 'review_status', '待评审', '已退回', 'REVIEW_RETURN',     1, '管理员', 'ONLINE', 'TRACE-4103-001', 0),
    (8102, 1, 'NEW_PROJECT_DIFF',   4103, 'review_comment', NULL,    '请补充监管字段对照表', 'REVIEW_RETURN', 1, '管理员', 'ONLINE', 'TRACE-4103-002', 0),
    (8103, 1, 'LEGACY_REQUIREMENT', 5002, 'docking_stage_status', '未开始', '进行中', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5002-001', 0),
    (8104, 1, 'LEGACY_REQUIREMENT', 5209, 'launch_stage_status',  '未开始', '进行中', 'STAGE_TRANSITION', 1007, '周 corp1 BA',   'ONLINE', 'TRACE-5209-001', 0);

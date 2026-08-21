-- =============================================================================
-- 需求管理平台 测试/演示数据集（本地与联调环境使用）
-- -----------------------------------------------------------------------------
-- 设计目标：
--   1. 清空 tenant_id = 1 下所有 req_ 业务数据（保留 sys_user / sys_role / 菜单权限）
--   2. 重新插入覆盖全部状态的场景数据：
--      - 新建项目差异：待评审/评审中/已评审/已退回 × 开发中/已完成/已上线 × 测试中/已通过 × 已基线
--      - 存量需求阶段：PROPOSE/DOCKING/WORKLOAD/PROJECT/SOFT/LAUNCH × 未开始/审批中/进行中/已完成
--      - 联动 requirement_status（已基线项目、各阶段对应需求状态）
--   3. 补充测试用户与角色权限（含可登录密码 admin123），覆盖审批场景：
--      admin（已存在）+ 张统筹 + 各业务组 BA/架构师/PM
-- 注意：所有密码 hash 复用 admin 的 BCrypt 哈希（明文 admin123），仅限演示环境。
-- 幂等：脚本可重复执行，每次先 DELETE tenant_id=1 的 req_ 业务数据再重建。
-- =============================================================================

-- 一、清空需求管理业务数据（按外键依赖顺序，避免残留孤儿）
DELETE FROM req_baseline_item  WHERE tenant_id = 1;
DELETE FROM req_baseline       WHERE tenant_id = 1;
DELETE FROM req_change_log     WHERE tenant_id = 1;
DELETE FROM req_stage_log      WHERE tenant_id = 1;
DELETE FROM req_attachment     WHERE tenant_id = 1;
DELETE FROM req_difference     WHERE tenant_id = 1;
DELETE FROM req_legacy_requirement WHERE tenant_id = 1;
DELETE FROM req_project_member        WHERE tenant_id = 1;
DELETE FROM req_business_group_member WHERE tenant_id = 1;
DELETE FROM req_project        WHERE tenant_id = 1;
DELETE FROM req_system         WHERE tenant_id = 1;
DELETE FROM req_import_batch   WHERE tenant_id = 1;

-- 二、测试用户（保留 admin id=1，新增 10 个测试用户；密码同 admin = admin123）
-- 演示用 hash：$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.
INSERT INTO sys_user (id, tenant_id, username, password_hash, display_name, mobile_phone, org_id, status, deleted) VALUES
    (1001, 1, 'coordinator',  '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '张统筹',   '13800001001', 1, 1, 0),
    (1002, 1, 'retail1_ba',    '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '李 retail1 BA',  '13800001002', 1, 1, 0),
    (1003, 1, 'retail1_arch',  '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '王 retail1 架构', '13800001003', 1, 1, 0),
    (1004, 1, 'retail1_pm',    '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '赵 retail1 PM',  '13800001004', 1, 1, 0),
    (1005, 1, 'retail2_ba',    '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '钱 retail2 BA',  '13800001005', 1, 1, 0),
    (1006, 1, 'retail2_pm',    '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '孙 retail2 PM',  '13800001006', 1, 1, 0),
    (1007, 1, 'corp1_ba',      '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '周 corp1 BA',    '13800001007', 1, 1, 0),
    (1008, 1, 'corp1_arch',    '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '吴 corp1 架构',  '13800001008', 1, 1, 0),
    (1009, 1, 'channel1_pm',  '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '郑 channel1 PM', '13800001009', 1, 1, 0),
    (1010, 1, 'retail1_ba2',   '$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.', '王大锤',         '13800001010', 1, 1, 0)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), display_name = VALUES(display_name), mobile_phone = VALUES(mobile_phone), status = 1, deleted = 0;

-- 三、角色（需求统筹管理员 / 业务组成员）
INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, deleted) VALUES
    (100, 1, 'REQUIREMENT_COORDINATOR', '需求统筹管理员', 1, 0),
    (101, 1, 'REQUIREMENT_MEMBER',       '需求业务组成员', 1, 0)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), status = 1, deleted = 0;

-- 3.1 统筹角色挂全部需求管理权限（含 requirement:admin 豁免）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 100, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id BETWEEN 7001 AND 7105;

-- 3.2 业务组成员挂基础访问权限（不含 admin 豁免，数据范围由 req_business_group_member / req_project_member 控制）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 101, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND permission_code IN (
    'requirement:access', 'requirement:access:create', 'requirement:access:update', 'requirement:access:delete',
    'requirement:project:read', 'requirement:project:create', 'requirement:project:update', 'requirement:project:delete',
    'requirement:legacy:read',  'requirement:legacy:create',  'requirement:legacy:update',  'requirement:legacy:delete',
    'requirement:system:read',  'requirement:system:create',  'requirement:system:update',  'requirement:system:delete',
    'requirement:changelog:read');

-- 3.3 菜单可见性（让需求管理菜单对两个新角色都可见）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES
    (100, 700, 1), (100, 701, 1), (100, 702, 1), (100, 703, 1),
    (101, 700, 1), (101, 701, 1), (101, 702, 1), (101, 703, 1);

-- 3.4 用户角色绑定
DELETE FROM sys_user_role WHERE tenant_id = 1 AND user_id BETWEEN 1001 AND 1010;
INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES
    (1001, 100, 1),   -- 张统筹 → REQUIREMENT_COORDINATOR
    (1002, 101, 1), (1003, 101, 1), (1004, 101, 1), (1005, 101, 1),
    (1006, 101, 1), (1007, 101, 1), (1008, 101, 1), (1009, 101, 1), (1010, 101, 1);

-- 四、系统清单主数据（10 个系统覆盖 4 个业务组）
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

-- 五、业务组成员（存量需求数据范围）
INSERT INTO req_business_group_member (id, tenant_id, business_group, user_id, created_by, deleted) VALUES
    (51001, 1, '零售一组', 1002, 1, 0),
    (51002, 1, '零售一组', 1003, 1, 0),
    (51003, 1, '零售一组', 1004, 1, 0),
    (51004, 1, '零售一组', 1010, 1, 0),
    (51005, 1, '零售二组', 1005, 1, 0),
    (51006, 1, '零售二组', 1006, 1, 0),
    (51007, 1, '对公一组', 1007, 1, 0),
    (51008, 1, '对公一组', 1008, 1, 0),
    (51009, 1, '渠道一组', 1009, 1, 0);

-- 六、项目主数据 + 项目成员（新建项目数据范围）
INSERT INTO req_project (id, tenant_id, project_code, project_name, project_type, start_time, status, description, created_by, deleted) VALUES
    (3001, 1, 'P2026-001', '零售贷款 2.0 升级项目', 'NEW_PROJECT', '2026-08-01', 'ACTIVE',    '零售贷款流程线上化与风控规则升级', 1, 0),
    (3002, 1, 'P2026-002', '信用卡分期优化项目',     'NEW_PROJECT', '2026-07-15', 'BASELINED', '信用卡分期方案灵活化与权益打通', 1, 0),
    (3003, 1, 'P2026-003', '对公跨境支付项目',       'NEW_PROJECT', '2026-08-10', 'ACTIVE',    '对公跨境收付汇与跨境清算', 1, 0);

INSERT INTO req_project_member (id, tenant_id, project_id, user_id, member_role, created_by, deleted) VALUES
    (52001, 1, 3001, 1004, 'LEADER', 1, 0),
    (52002, 1, 3001, 1002, 'MEMBER', 1, 0),
    (52003, 1, 3001, 1003, 'MEMBER', 1, 0),
    (52004, 1, 3002, 1006, 'LEADER', 1, 0),
    (52005, 1, 3002, 1005, 'MEMBER', 1, 0),
    (52006, 1, 3003, 1009, 'LEADER', 1, 0),
    (52007, 1, 3003, 1007, 'MEMBER', 1, 0),
    (52008, 1, 3003, 1008, 'MEMBER', 1, 0);

-- =============================================================================
-- 七、新建项目差异清单（覆盖 review_status × dev_status × test_status 全矩阵）
-- 项目 3001（ACTIVE）：覆盖待评审/评审中/已评审/已退回 × 开发/测试/上线各态
-- 项目 3002（BASELINED）：10 条已评审差异，已纳入基线 6001
-- 项目 3003（ACTIVE）：覆盖待评审/评审中/已评审/已退回
-- =============================================================================
INSERT INTO req_difference
(id, tenant_id, project_id, seq_no, business_conglomerate, business_section, business_group, requirement_no, category, name, system_id, jinke_practice, difference_type, monshang_practice, difference_desc, monshang_dept, monshang_analyst, jinke_analyst, adapt_mode, handle_status, coord_group, solution, is_special, decision_level, decision_conclusion, monshang_confirm_dept, jinke_confirmer, review_status, review_comment, reviewed_by, reviewed_at, workflow_instance_id, dev_status, test_status, baseline_id, source, import_batch_id, created_by, updated_by, deleted) VALUES
-- 项目 3001：零售贷款 2.0
(4001, 1, 3001,  1, '零售事业群', '零售', '零售一组', 'RTL-LOAN-001', '功能', '贷款受理页面要素差异',     2002, '金科全线上受理',     '金科有-蒙商无',     '蒙商线下受理',         '金科支持纯线上申请，蒙商需补充线上要素', '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '按原型',     '双方已确认',   '零售一组',     '按金科原型实现，蒙商补齐影像采集', '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '待评审',   NULL,    NULL, NULL, NULL,             '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4002, 1, 3001,  2, '零售事业群', '零售', '零售一组', 'RTL-LOAN-002', '流程', '风控规则触发节点差异',     2002, '金科在授信前调用风控', '双方作法有差异',    '蒙商在授信后调用',     '风控前置可降低风险敞口，需评估改造',     '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '少量改造', '待架构确认',   '零售一组',     '蒙商风控前置，调整流程节点顺序', '是', '总体组', '评审通过', '蒙商零售部', '赵 retail1 PM', '评审中',   NULL,    NULL, NULL, 'WF-LEGACY-STUB-4002', '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4003, 1, 3001,  3, '零售事业群', '零售', '零售一组', 'RTL-LOAN-003', '功能', '贷款合同模板字段差异',     2002, '金科 18 个字段',      '双方作法有差异',    '蒙商 12 个字段',      '蒙商缺少 6 个监管必填字段，需补全',      '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '按原型',     '双方已确认',   '零售一组',     '补齐监管字段并接入合同模板',     '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '已评审',   NULL,    1,    '2026-08-05 10:00:00', NULL,             '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4004, 1, 3001,  4, '零售事业群', '零售', '零售一组', 'RTL-LOAN-004', '功能', '影像资料采集差异',         2002, '金科 OCR 自动识别',   '金科有-蒙商无',     '蒙商手工录入',         '引入 OCR 能力，提升录入效率',           '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '按原型',     '双方已确认',   '零售一组',     '复用金科 OCR 组件',              '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '已退回',   '差异描述需补充 OCR 准确率指标', 1, '2026-08-06 11:00:00', NULL, '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4005, 1, 3001,  5, '零售事业群', '零售', '零售一组', 'RTL-LOAN-005', '功能', '贷款产品参数化配置差异',   2002, '金科参数化',          '双方作法有差异',    '蒙商硬编码',           '改造成参数化配置，支持快速上线新产品', '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '参数配置',   '双方已确认',   '零售一组',     '抽取产品参数入配置中心',        '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '已评审',   NULL,    1,    '2026-08-04 09:00:00', NULL,             '开发中',     '未开始',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4006, 1, 3001,  6, '零售事业群', '零售', '零售一组', 'RTL-LOAN-006', '流程', '贷后预警机制差异',         2002, '金科 7 级预警',       '双方作法有差异',    '蒙商 5 级预警',       '增加 2 级预警，覆盖监管要求',           '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '少量改造',   '待行方确认',   '零售一组',     '补齐 2 级预警并联动短信',        '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '已评审',   NULL,    1,    '2026-08-03 14:00:00', NULL,             '已完成',     '未开始',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4007, 1, 3001,  7, '零售事业群', '零售', '零售一组', 'RTL-LOAN-007', '报表', '贷后报表口径差异',         2002, '金科按月汇总',       '双方作法有差异',    '蒙商按日汇总',         '对齐金科月报口径',                       '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '按原型',     '双方已确认',   '零售一组',     '调整报表维度为月度',            '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '已评审',   NULL,    1,    '2026-08-02 16:00:00', NULL,             '已上线',     '已通过',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4008, 1, 3001,  8, '零售事业群', '零售', '零售一组', 'RTL-LOAN-008', '功能', '提前还款手续费差异',       2002, '金科 1% 手续费',     '双方作法有差异',    '蒙商 0.5% 手续费',    '对齐手续费规则，评估客户影响',         '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '参数配置',   '待决策',       '零售一组',     '上报领导小组决策',              '是', '领导小组', '评审通过', '蒙商零售部', '赵 retail1 PM', '已评审',   NULL,    1,    '2026-08-01 10:00:00', NULL,             '未开始',     '测试中',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4009, 1, 3001,  9, '零售事业群', '零售', '零售一组', 'RTL-LOAN-009', '岗位', '客户经理岗位权限差异',     2002, '金科 4 级权限',      '双方作法有差异',    '蒙商 3 级权限',       '增加 1 级权限层级，覆盖总行客户经理', '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '按原型',     '双方已确认',   '零售一组',     '调整岗位权限模型',              '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '已评审',   NULL,    1,    '2026-07-30 09:00:00', NULL,             '已完成',     '已通过',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
(4010, 1, 3001, 10, '零售事业群', '零售', '零售一组', 'RTL-LOAN-010', '菜单', '贷款菜单层级差异',         2002, '金科 3 级菜单',      '双方作法有差异',    '蒙商 2 级菜单',       '调整菜单层级，对齐金科交互',           '蒙商零售部', '李 retail1 BA', '王 retail1 架构', '按原型',     '双方已确认',   '零售一组',     '调整菜单结构',                  '否', '版块内', '评审通过', '蒙商零售部', '赵 retail1 PM', '已评审',   NULL,    1,    '2026-07-29 14:00:00', NULL,             '已完成',     '已通过',  NULL,     'ONLINE', NULL,    1004, NULL, 0),
-- 项目 3002：信用卡分期（已基线）
(4011, 1, 3002,  1, '零售事业群', '信用卡', '零售二组', 'CC-INST-001', '功能', '分期手续费率差异',         2003, '金科 0.6%/期',       '双方作法有差异',    '蒙商 0.7%/期',        '对齐费率规则，调整手续费模型',         '蒙商零售部', '钱 retail2 BA', NULL,             '参数配置',   '双方已确认',   '零售二组',     '调整费率参数',                  '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 10:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4012, 1, 3002,  2, '零售事业群', '信用卡', '零售二组', 'CC-INST-002', '功能', '分期提前结清违约金差异',   2003, '金科 2% 违约金',     '双方作法有差异',    '蒙商 3% 违约金',      '对齐违约金规则',                        '蒙商零售部', '钱 retail2 BA', NULL,             '参数配置',   '双方已确认',   '零售二组',     '调整违约金参数',                '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 11:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4013, 1, 3002,  3, '零售事业群', '信用卡', '零售二组', 'CC-INST-003', '流程', '分期申请渠道差异',         2003, '金科支持 App 申请',  '金科有-蒙商无',     '蒙商仅柜面申请',      '增加 App 渠道，提升客户体验',          '蒙商零售部', '钱 retail2 BA', NULL,             '按原型',     '双方已确认',   '零售二组',     '复用手机银行分期模块',          '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 12:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4014, 1, 3002,  4, '零售事业群', '信用卡', '零售二组', 'CC-INST-004', '产品', '分期产品规则差异',         2003, '金科 6 期/12 期/24 期','双方作法有差异',   '蒙商 6 期/12 期',     '增加 24 期产品，覆盖长周期需求',       '蒙商零售部', '钱 retail2 BA', NULL,             '按原型',     '双方已确认',   '零售二组',     '新增 24 期分期产品',            '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 13:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4015, 1, 3002,  5, '零售事业群', '信用卡', '零售二组', 'CC-INST-005', '批处理', '分期日终批处理差异',     2003, '金科 23:00 跑批',    '双方作法有差异',    '蒙商 00:30 跑批',     '调整跑批时点，对齐金科账务时点',       '蒙商零售部', '钱 retail2 BA', NULL,             '少量改造',   '双方已确认',   '零售二组',     '调整批处理调度时间',            '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 14:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4016, 1, 3002,  6, '零售事业群', '信用卡', '零售二组', 'CC-INST-006', '核算', '分期账务科目差异',         2003, '金科 6 个科目',      '双方作法有差异',    '蒙商 4 个科目',       '补齐 2 个科目，对齐核算口径',           '蒙商零售部', '钱 retail2 BA', NULL,             '少量改造',   '双方已确认',   '零售二组',     '补充科目映射',                  '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 15:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4017, 1, 3002,  7, '零售事业群', '信用卡', '零售二组', 'CC-INST-007', '报表', '分期报表口径差异',         2003, '金科按月汇总',       '双方作法有差异',    '蒙商按日汇总',         '对齐月报口径',                          '蒙商零售部', '钱 retail2 BA', NULL,             '按原型',     '双方已确认',   '零售二组',     '调整报表维度',                  '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 16:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4018, 1, 3002,  8, '零售事业群', '信用卡', '零售二组', 'CC-INST-008', '岗位', '分期审核岗位差异',         2003, '金科 4 级权限',      '双方作法有差异',    '蒙商 3 级权限',       '增加 1 级审核岗位',                     '蒙商零售部', '钱 retail2 BA', NULL,             '按原型',     '双方已确认',   '零售二组',     '调整岗位权限',                  '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 17:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4019, 1, 3002,  9, '零售事业群', '信用卡', '零售二组', 'CC-INST-009', '菜单', '分期菜单层级差异',         2003, '金科 3 级菜单',      '双方作法有差异',    '蒙商 2 级菜单',       '调整菜单层级',                          '蒙商零售部', '钱 retail2 BA', NULL,             '按原型',     '双方已确认',   '零售二组',     '调整菜单结构',                  '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 18:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
(4020, 1, 3002, 10, '零售事业群', '信用卡', '零售二组', 'CC-INST-010', '其他', '分期权益积分差异',         2003, '金科 1 倍积分',      '双方作法有差异',    '蒙商 2 倍积分',       '对齐积分规则，避免客户投诉',           '蒙商零售部', '钱 retail2 BA', NULL,             '参数配置',   '双方已确认',   '零售二组',     '调整积分倍数',                  '否', '版块内', '评审通过', '蒙商零售部', '孙 retail2 PM', '已评审',   NULL,    1,    '2026-07-20 19:00:00', NULL,             '未开始',     '未开始',  6001,     'ONLINE', NULL,    1006, NULL, 0),
-- 项目 3003：对公跨境支付
(4021, 1, 3003,  1, '对公事业群', '跨境', '对公一组', 'CORP-XB-001', '功能', '跨境汇款路径差异',         2006, '金科支持多路径',     '双方作法有差异',    '蒙商单一路径',        '增加多路径支持，提升汇款成功率',       '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '按原型',     '双方已确认',   '对公一组',     '接入多路径选择',                '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '待评审',   NULL,    NULL, NULL, NULL,             '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4022, 1, 3003,  2, '对公事业群', '跨境', '对公一组', 'CORP-XB-002', '流程', '跨境反洗钱校验差异',       2006, '金科实时校验',       '双方作法有差异',    '蒙商批量校验',        '改为实时校验，降低合规风险',           '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '少量改造',   '待架构确认',   '对公一组',     '改为实时校验',                  '是', '总体组', '评审通过', '蒙商对公部', '郑 channel1 PM', '评审中',   NULL,    NULL, NULL, 'WF-LEGACY-STUB-4022', '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4023, 1, 3003,  3, '对公事业群', '跨境', '对公一组', 'CORP-XB-003', '功能', '跨境手续费计算差异',       2006, '金科按笔阶梯',       '双方作法有差异',    '蒙商按金额比例',      '对齐阶梯计费规则',                      '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '参数配置',   '双方已确认',   '对公一组',     '改为阶梯计费',                  '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '已评审',   NULL,    1,    '2026-08-12 10:00:00', NULL,             '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4024, 1, 3003,  4, '对公事业群', '跨境', '对公一组', 'CORP-XB-004', '报表', '跨境报表口径差异',         2006, '金科按月',           '双方作法有差异',    '蒙商按日',             '对齐月报口径',                          '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '按原型',     '双方已确认',   '对公一组',     '调整报表维度',                  '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '已退回',   '差异描述需补充外币折算口径', 1, '2026-08-13 11:00:00', NULL, '未开始',     '未开始',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4025, 1, 3003,  5, '对公事业群', '跨境', '对公一组', 'CORP-XB-005', '批处理', '跨境日终清算差异',       2006, '金科 T+0',           '双方作法有差异',    '蒙商 T+1',             '评估 T+0 改造可行性',                  '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '少量改造',   '待决策',       '对公一组',     '上报领导小组决策',              '是', '领导小组', '评审通过', '蒙商对公部', '郑 channel1 PM', '已评审',   NULL,    1,    '2026-08-12 14:00:00', NULL,             '开发中',     '未开始',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4026, 1, 3003,  6, '对公事业群', '跨境', '对公一组', 'CORP-XB-006', '功能', '跨境报文格式差异',         2006, '金科 ISO 20022',     '双方作法有差异',    '蒙商 MT103',           '升级为 ISO 20022 报文',                 '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '按原型',     '双方已确认',   '对公一组',     '升级报文格式',                  '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '已评审',   NULL,    1,    '2026-08-12 15:00:00', NULL,             '已完成',     '测试中',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4027, 1, 3003,  7, '对公事业群', '跨境', '对公一组', 'CORP-XB-007', '岗位', '跨境复核岗位差异',         2006, '金科双岗复核',       '双方作法有差异',    '蒙商单岗复核',        '改为双岗复核，提升合规性',             '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '按原型',     '双方已确认',   '对公一组',     '调整为双岗复核',                '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '已评审',   NULL,    1,    '2026-08-12 16:00:00', NULL,             '已完成',     '已通过',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4028, 1, 3003,  8, '对公事业群', '跨境', '对公一组', 'CORP-XB-008', '菜单', '跨境菜单结构差异',         2006, '金科 3 级菜单',      '双方作法有差异',    '蒙商 2 级菜单',       '调整菜单层级',                          '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '按原型',     '双方已确认',   '对公一组',     '调整菜单结构',                  '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '已评审',   NULL,    1,    '2026-08-12 17:00:00', NULL,             '已完成',     '已通过',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4029, 1, 3003,  9, '对公事业群', '跨境', '对公一组', 'CORP-XB-009', '核算', '跨境账务科目差异',         2006, '金科 8 个科目',      '双方作法有差异',    '蒙商 6 个科目',       '补齐 2 个科目',                         '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '少量改造',   '双方已确认',   '对公一组',     '补充科目映射',                  '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '已评审',   NULL,    1,    '2026-08-12 18:00:00', NULL,             '已上线',     '已通过',  NULL,     'ONLINE', NULL,    1009, NULL, 0),
(4030, 1, 3003, 10, '对公事业群', '跨境', '对公一组', 'CORP-XB-010', '其他', '跨境权益积分差异',         2006, '金科 2 倍积分',      '双方作法有差异',    '蒙商 1 倍积分',       '对齐积分倍数',                          '蒙商对公部', '周 corp1 BA', '吴 corp1 架构', '参数配置',   '双方已确认',   '对公一组',     '调整积分倍数',                  '否', '版块内', '评审通过', '蒙商对公部', '郑 channel1 PM', '已评审',   NULL,    1,    '2026-08-12 19:00:00', NULL,             '已完成',     '已通过',  NULL,     'ONLINE', NULL,    1009, NULL, 0);

-- 八、基线版本（项目 3002 已基线，纳入 10 条差异）
INSERT INTO req_baseline (id, tenant_id, project_id, baseline_no, baseline_name, status, difference_count, remark, created_by, deleted) VALUES
    (6001, 1, 3002, 'BL-P2026-002-20260815-1', '信用卡分期优化项目首版基线', 'RELEASED', 10, '评审通过的全部差异锁定', 1, 0);

INSERT INTO req_baseline_item (id, tenant_id, baseline_id, difference_id, snapshot_json, deleted) VALUES
    (61001, 1, 6001, 4011, '{"name":"分期手续费率差异","review_status":"已评审"}', 0),
    (61002, 1, 6001, 4012, '{"name":"分期提前结清违约金差异","review_status":"已评审"}', 0),
    (61003, 1, 6001, 4013, '{"name":"分期申请渠道差异","review_status":"已评审"}', 0),
    (61004, 1, 6001, 4014, '{"name":"分期产品规则差异","review_status":"已评审"}', 0),
    (61005, 1, 6001, 4015, '{"name":"分期日终批处理差异","review_status":"已评审"}', 0),
    (61006, 1, 6001, 4016, '{"name":"分期账务科目差异","review_status":"已评审"}', 0),
    (61007, 1, 6001, 4017, '{"name":"分期报表口径差异","review_status":"已评审"}', 0),
    (61008, 1, 6001, 4018, '{"name":"分期审核岗位差异","review_status":"已评审"}', 0),
    (61009, 1, 6001, 4019, '{"name":"分期菜单层级差异","review_status":"已评审"}', 0),
    (61010, 1, 6001, 4020, '{"name":"分期权益积分差异","review_status":"已评审"}', 0);

-- =============================================================================
-- 九、存量项目需求（覆盖 6 阶段 × 4 状态 × 4 业务组，含审批中场景）
-- 阶段：PROPOSE/DOCKING/WORKLOAD/PROJECT/SOFT/LAUNCH
-- 阶段状态：未开始/审批中/进行中/已完成
-- requirement_status 联动口径：PROPOSE→需求分析 / DOCKING/WORKLOAD→业需评审通过
--                              / PROJECT→立项中 / SOFT→软需编制 / LAUNCH→已投产
-- =============================================================================
INSERT INTO req_legacy_requirement
(id, tenant_id, legacy_doc_name, requirement_no, requirement_name, content_summary, propose_dept, proposer, monshang_ba, monshang_architect, expected_launch_date, regulator, regulation_doc_no, regulation_desc, regulation_launch_date, requirement_received_date, requirement_type, regulation_category, business_group, sub_group, jinke_contact, need_jinke_arch_decision, jinke_architect, unified_managed, ba_review_date, workload_date, finance_project_date, soft_doc_name, owner_conglomerate, owner_system, owner_contact, involve_cooperation, coord_conglomerate, coord_system, soft_submit_date, soft_review_date, planned_launch_date, actual_launch_date, launch_mode, requirement_status, remark, change_involved, change_info, change_review_conclusion, change_conclusion_status, change_remark, not_project_developed, current_stage, propose_stage_status, docking_stage_status, workload_stage_status, project_stage_status, soft_stage_status, launch_stage_status, workflow_instance_id, source, import_batch_id, created_by, updated_by, deleted) VALUES
-- 零售一组：8 条覆盖完整阶段链
(5001, 1, '业需-零售-2026-001', 'JG-W0332C-240507-001', '零售贷款线上受理改造',         '将线下受理改为线上，覆盖影像采集与 OCR 识别', '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-12-31', NULL, NULL, NULL, NULL, '2026-08-01', '业务', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '是', '王 retail1 架构', '是', NULL, NULL, NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '新建需求', '否', NULL, NULL, NULL, NULL, '否', 'PROPOSE',  '未开始', '未开始', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1002, NULL, 0),
(5002, 1, '业需-零售-2026-002', 'JG-W0332C-240507-002', '风控规则前置改造',             '风控规则从授信后挪到授信前，降低风险敞口',     '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-11-30', NULL, NULL, NULL, NULL, '2026-08-05', '业务', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '是', '王 retail1 架构', '是', NULL, NULL, NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '是', '零售事业群', 'S-RISK-DEC 风控决策系统', NULL, NULL, NULL, NULL, NULL, '需求分析', '风控前置审批中', '否', NULL, NULL, NULL, NULL, '否', 'PROPOSE',  '审批中', '未开始', '未开始', '未开始', '未开始', '未开始', 'WF-LEGACY-STUB-5002',    'ONLINE', NULL, 1002, NULL, 0),
(5003, 1, '业需-零售-2026-003', 'JG-W0332C-240507-003', '贷款合同模板字段补全',         '补齐 6 个监管必填字段并接入合同模板',          '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-11-15', '银保监会', '个人贷款管理办法 2024 修订版', '补充 6 个监管字段', '2026-10-31', '2026-08-04', '监管', '国家级', '零售一组', '贷款组', '王 retail1 架构 13800001003', '否', NULL, '是', NULL, NULL, NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '业需推进中', '否', NULL, NULL, NULL, NULL, '否', 'PROPOSE',  '进行中', '未开始', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1002, NULL, 0),
(5004, 1, '业需-零售-2026-004', 'JG-W0332C-240507-004', '影像资料 OCR 识别引入',        '引入金科 OCR 组件，提升影像录入效率',          '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-11-30', NULL, NULL, NULL, NULL, '2026-08-08', '技术', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '是', '王 retail1 架构', '是', '2026-08-15', NULL, NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '是', '零售事业群', 'S-DATA-MID 数据中台', NULL, NULL, NULL, NULL, NULL, '业需评审通过', 'OCR 接入完成', '否', NULL, NULL, NULL, NULL, '否', 'DOCKING',  '未开始', '进行中', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1002, NULL, 0),
(5005, 1, '业需-零售-2026-005', 'JG-W0332C-240507-005', '贷款产品参数化改造',           '抽取产品参数入配置中心，支持快速上线新产品',   '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-12-15', NULL, NULL, NULL, NULL, '2026-08-10', '业务', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '否', NULL, '是', '2026-08-20', '2026-09-01', NULL, NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '工作量评估完成', '否', NULL, NULL, NULL, NULL, '否', 'WORKLOAD', '未开始', '未开始', '已完成', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1002, NULL, 0),
(5006, 1, '业需-零售-2026-006', 'JG-W0332C-240507-006', '贷后预警 7 级改造',            '增加 2 级预警，覆盖监管要求并联动短信',       '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2026-12-31', '银保监会', '个人贷款风险预警指引', '补齐 2 级预警', '2026-12-31', '2026-08-12', '监管', '国家级', '零售一组', '贷款组', '王 retail1 架构 13800001003', '否', NULL, '是', '2026-08-22', '2026-09-05', '2026-09-20', NULL, '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '立项审批中', '否', NULL, NULL, NULL, NULL, '否', 'PROJECT',  '未开始', '未开始', '已完成', '审批中', '未开始', '未开始', 'WF-LEGACY-STUB-5006',    'ONLINE', NULL, 1002, NULL, 0),
(5007, 1, '业需-零售-2026-007', 'JG-W0332C-240507-007', '软需文档补齐',               '完成软需文档编制与评审',                       '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2027-01-15', NULL, NULL, NULL, NULL, '2026-08-15', '业务', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '否', NULL, '是', '2026-08-25', '2026-09-08', '2026-09-25', '软需-零售-2026-007', '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, '2026-10-10', '2026-10-25', NULL, NULL, NULL, '软需编制', '软需评审通过', '否', NULL, NULL, NULL, NULL, '否', 'SOFT',     '未开始', '未开始', '已完成', '已完成', '已完成', '未开始', NULL,                    'ONLINE', NULL, 1002, NULL, 0),
(5008, 1, '业需-零售-2026-008', 'JG-W0332C-240507-008', '零售贷款 2.0 上线',          '完成 UAT 与生产上线，按常规版本投产',          '零售业务部', '李 retail1 BA 13800001002', '李 retail1 BA', '王 retail1 架构', '2027-02-28', NULL, NULL, NULL, NULL, '2026-08-20', '业务', NULL, '零售一组', '贷款组', '王 retail1 架构 13800001003', '否', NULL, '是', '2026-08-28', '2026-09-10', '2026-09-28', '软需-零售-2026-008', '零售事业群', 'S-RETAIL-LOAN 零售贷款系统', '赵 retail1 PM 13800001004', '否', NULL, NULL, '2026-10-15', '2026-11-01', '2027-02-20', '2027-02-28', '常规版本', '已投产', '已完成上线', '否', NULL, NULL, NULL, NULL, '否', 'LAUNCH',   '未开始', '未开始', '已完成', '已完成', '已完成', '已完成', NULL,                    'ONLINE', NULL, 1002, NULL, 0),
-- 零售二组：4 条覆盖关键场景
(5009, 1, '业需-零售-2026-101', 'JG-W0332C-240508-101', '信用卡分期 App 渠道接入',     '在 App 中接入分期申请入口',                   '信用卡部',   '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL,             '2026-12-31', NULL, NULL, NULL, NULL, '2026-08-15', '业务', NULL, '零售二组', NULL,   NULL,                       '否', NULL, '否', NULL, NULL, NULL, NULL, '零售事业群', 'S-CREDIT-CARD 信用卡系统',  '孙 retail2 PM 13800001006', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '待启动', '否', NULL, NULL, NULL, NULL, '否', 'PROPOSE',  '未开始', '未开始', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1005, NULL, 0),
(5010, 1, '业需-零售-2026-102', 'JG-W0332C-240508-102', '信用卡分期 24 期产品上线',    '增加 24 期分期产品',                           '信用卡部',   '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL,             '2026-11-30', NULL, NULL, NULL, NULL, '2026-08-18', '业务', NULL, '零售二组', NULL,   NULL,                       '否', NULL, '否', '2026-08-22', NULL, NULL, NULL, '零售事业群', 'S-CREDIT-CARD 信用卡系统',  '孙 retail2 PM 13800001006', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '需求对接审批中', '否', NULL, NULL, NULL, NULL, '否', 'DOCKING',  '未开始', '审批中', '未开始', '未开始', '未开始', '未开始', 'WF-LEGACY-STUB-5010',    'ONLINE', NULL, 1005, NULL, 0),
(5011, 1, '业需-零售-2026-103', 'JG-W0332C-240508-103', '信用卡积分 1 倍规则对齐',     '积分倍数从 2 倍改为 1 倍，对齐金科',           '信用卡部',   '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL,             '2026-12-15', NULL, NULL, NULL, NULL, '2026-08-20', '业务', NULL, '零售二组', NULL,   NULL,                       '否', NULL, '否', '2026-08-25', '2026-09-05', '2026-09-20', NULL, '零售事业群', 'S-CREDIT-CARD 信用卡系统',  '孙 retail2 PM 13800001006', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '已完成立项', '否', NULL, NULL, NULL, NULL, '否', 'PROJECT',  '未开始', '未开始', '已完成', '已完成', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1005, NULL, 0),
(5012, 1, '业需-零售-2026-104', 'JG-W0332C-240508-104', '信用卡分期 2.0 投产',         '完成分期 2.0 全量上线',                       '信用卡部',   '钱 retail2 BA 13800001005', '钱 retail2 BA', NULL,             '2027-03-31', NULL, NULL, NULL, NULL, '2026-08-22', '业务', NULL, '零售二组', NULL,   NULL,                       '否', NULL, '否', '2026-08-28', '2026-09-08', '2026-09-25', '软需-零售-2026-104', '零售事业群', 'S-CREDIT-CARD 信用卡系统',  '孙 retail2 PM 13800001006', '否', NULL, NULL, '2026-10-15', '2026-11-01', '2027-03-20', NULL, NULL, '软需编制', '待投产审批', '否', NULL, NULL, NULL, NULL, '否', 'LAUNCH',   '未开始', '未开始', '已完成', '已完成', '已完成', '审批中', 'WF-LEGACY-STUB-5012',    'ONLINE', NULL, 1005, NULL, 0),
-- 对公一组：4 条
(5013, 1, '业需-对公-2026-201', 'JG-W0332C-240509-201', '对公跨境汇款多路径改造',     '增加多路径支持，提升汇款成功率',               '对公业务部', '周 corp1 BA 13800001007',   '周 corp1 BA', '吴 corp1 架构', '2026-12-31', NULL, NULL, NULL, NULL, '2026-08-12', '业务', NULL, '对公一组', NULL,   '吴 corp1 架构 13800001008', '否', NULL, '是', NULL, NULL, NULL, NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统',  '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '需求分析中', '否', NULL, NULL, NULL, NULL, '否', 'PROPOSE',  '进行中', '未开始', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1007, NULL, 0),
(5014, 1, '业需-对公-2026-202', 'JG-W0332C-240509-202', '对公跨境反洗钱实时校验',     '由批量校验改为实时校验',                       '对公业务部', '周 corp1 BA 13800001007',   '周 corp1 BA', '吴 corp1 架构', '2026-11-30', '人民银行', '反洗钱管理办法 2024', '改为实时校验', '2026-11-30', '2026-08-15', '监管', '国家级', '对公一组', NULL,   '吴 corp1 架构 13800001008', '是', '吴 corp1 架构', '是', NULL, NULL, NULL, NULL, '对公事业群', 'S-CORP-LOAN 对公贷款系统',  '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '工作量待启动', '否', NULL, NULL, NULL, NULL, '否', 'WORKLOAD', '未开始', '未开始', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1007, NULL, 0),
(5015, 1, '业需-对公-2026-203', 'JG-W0332C-240509-203', '对公跨境报文 ISO 20022 升级', '从 MT103 升级为 ISO 20022',                  '对公业务部', '周 corp1 BA 13800001007',   '周 corp1 BA', '吴 corp1 架构', '2026-12-15', NULL, NULL, NULL, NULL, '2026-08-18', '技术', NULL, '对公一组', NULL,   '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-08-22', '2026-09-01', '2026-09-20', '软需-对公-2026-203', '对公事业群', 'S-CORP-LOAN 对公贷款系统',  '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '软需编制', '软需审批中', '否', NULL, NULL, NULL, NULL, '否', 'SOFT',     '未开始', '未开始', '已完成', '已完成', '审批中', '未开始', 'WF-LEGACY-STUB-5015',    'ONLINE', NULL, 1007, NULL, 0),
(5016, 1, '业需-对公-2026-204', 'JG-W0332C-240509-204', '对公跨境支付 1.0 上线',      '对公跨境支付全量上线',                       '对公业务部', '周 corp1 BA 13800001007',   '周 corp1 BA', '吴 corp1 架构', '2027-03-31', NULL, NULL, NULL, NULL, '2026-08-20', '业务', NULL, '对公一组', NULL,   '吴 corp1 架构 13800001008', '否', NULL, '是', '2026-08-25', '2026-09-05', '2026-09-25', '软需-对公-2026-204', '对公事业群', 'S-CORP-LOAN 对公贷款系统',  '郑 channel1 PM 13800001009', '否', NULL, NULL, '2026-10-15', '2026-11-01', '2027-03-20', '2027-03-31', '常规版本', '已投产', '已完成上线', '否', NULL, NULL, NULL, NULL, '否', 'LAUNCH',   '未开始', '未开始', '已完成', '已完成', '已完成', '已完成', NULL,                    'ONLINE', NULL, 1007, NULL, 0),
-- 渠道一组：4 条
(5017, 1, '业需-渠道-2026-301', 'JG-W0332C-240510-301', '手机银行分期入口接入',       '在手机银行接入分期申请入口',                   '渠道部',     '郑 channel1 PM 13800001009','郑 channel1 PM', NULL,           '2026-12-31', NULL, NULL, NULL, NULL, '2026-08-15', '业务', NULL, '渠道一组', NULL,   NULL,                       '否', NULL, '否', NULL, NULL, NULL, NULL, '渠道事业群', 'S-CHNL-MOBILE 手机银行系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '需求分析', '待启动', '否', NULL, NULL, NULL, NULL, '否', 'PROPOSE',  '未开始', '未开始', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1009, NULL, 0),
(5018, 1, '业需-渠道-2026-302', 'JG-W0332C-240510-302', '网银跨境汇款入口接入',       '在企业网银接入跨境汇款入口',                   '渠道部',     '郑 channel1 PM 13800001009','郑 channel1 PM', NULL,           '2026-11-30', NULL, NULL, NULL, NULL, '2026-08-18', '业务', NULL, '渠道一组', NULL,   NULL,                       '否', NULL, '否', '2026-08-22', NULL, NULL, NULL, '渠道事业群', 'S-CHNL-NETBANK 渠道网银系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '需求对接中', '否', NULL, NULL, NULL, NULL, '否', 'DOCKING',  '未开始', '进行中', '未开始', '未开始', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1009, NULL, 0),
(5019, 1, '业需-渠道-2026-303', 'JG-W0332C-240510-303', '手机银行菜单结构调整',       '调整菜单层级，对齐金科交互',                   '渠道部',     '郑 channel1 PM 13800001009','郑 channel1 PM', NULL,           '2026-12-15', NULL, NULL, NULL, NULL, '2026-08-20', '业务', NULL, '渠道一组', NULL,   NULL,                       '否', NULL, '否', '2026-08-25', '2026-09-05', '2026-09-20', NULL, '渠道事业群', 'S-CHNL-MOBILE 手机银行系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '业需评审通过', '已完成立项', '否', NULL, NULL, NULL, NULL, '否', 'PROJECT',  '未开始', '未开始', '已完成', '已完成', '未开始', '未开始', NULL,                    'ONLINE', NULL, 1009, NULL, 0),
(5020, 1, '业需-渠道-2026-304', 'JG-W0332C-240510-304', '手机银行 5.0 上线',          '完成手机银行 5.0 全量上线',                   '渠道部',     '郑 channel1 PM 13800001009','郑 channel1 PM', NULL,           '2027-03-31', NULL, NULL, NULL, NULL, '2026-08-22', '业务', NULL, '渠道一组', NULL,   NULL,                       '否', NULL, '否', '2026-08-28', '2026-09-08', '2026-09-25', '软需-渠道-2026-304', '渠道事业群', 'S-CHNL-MOBILE 手机银行系统', '郑 channel1 PM 13800001009', '否', NULL, NULL, '2026-10-15', '2026-11-01', '2027-03-20', NULL, NULL, '软需编制', '待投产审批', '否', NULL, NULL, NULL, NULL, '否', 'LAUNCH',   '未开始', '未开始', '已完成', '已完成', '已完成', '审批中', 'WF-LEGACY-STUB-5020',    'ONLINE', NULL, 1009, NULL, 0);

-- 十、阶段流转记录（关键节点留痕）
INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, deleted) VALUES
    (7001, 1, 5003, NULL,      'PROPOSE',  NULL,      '进行中', 1002, '李 retail1 BA', '业需入手，启动需求提出', 0),
    (7002, 1, 5004, 'PROPOSE', 'DOCKING',  '已完成',  '进行中', 1002, '李 retail1 BA', '业需评审通过，进入需求对接', 0),
    (7003, 1, 5005, 'DOCKING', 'WORKLOAD', '已完成',  '已完成', 1002, '李 retail1 BA', '工作量评估完成', 0),
    (7004, 1, 5006, 'WORKLOAD','PROJECT',  '已完成',  '审批中', 1002, '李 retail1 BA', '发起立项审批', 0),
    (7005, 1, 5007, 'PROJECT', 'SOFT',     '已完成',  '已完成', 1002, '李 retail1 BA', '软需编制完成', 0),
    (7006, 1, 5008, 'SOFT',    'LAUNCH',   '已完成',  '已完成', 1002, '李 retail1 BA', '完成上线', 0),
    (7007, 1, 5011, 'DOCKING', 'WORKLOAD', '已完成',  '已完成', 1005, '钱 retail2 BA', '工作量评估完成', 0),
    (7008, 1, 5011, 'WORKLOAD','PROJECT',  '已完成',  '已完成', 1005, '钱 retail2 BA', '立项完成', 0),
    (7009, 1, 5013, NULL,       'PROPOSE',  NULL,      '进行中', 1007, '周 corp1 BA',   '启动需求提出', 0),
    (7010, 1, 5016, 'SOFT',    'LAUNCH',   '已完成',  '已完成', 1007, '周 corp1 BA',   '完成上线', 0),
    (7011, 1, 5018, 'PROPOSE', 'DOCKING',  '已完成',  '进行中', 1009, '郑 channel1 PM','业需评审通过，进入对接', 0),
    (7012, 1, 5019, 'DOCKING', 'WORKLOAD', '已完成',  '已完成', 1009, '郑 channel1 PM','工作量评估完成', 0),
    (7013, 1, 5019, 'WORKLOAD','PROJECT',  '已完成',  '已完成', 1009, '郑 channel1 PM','立项完成', 0);

-- 十一、统一改动记录（关键节点留痕，关联阶段状态变更与需求状态联动）
INSERT INTO req_change_log (id, tenant_id, biz_type, biz_id, field_name, old_value, new_value, change_type, operator_id, operator_name, source, trace_id, deleted) VALUES
    (8001, 1, 'LEGACY_REQUIREMENT', 5003, 'propose_stage_status', '未开始', '进行中', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5003-001', 0),
    (8002, 1, 'LEGACY_REQUIREMENT', 5004, 'docking_stage_status', '未开始', '进行中', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5004-001', 0),
    (8003, 1, 'LEGACY_REQUIREMENT', 5004, 'requirement_status',  '需求分析', '业需评审通过', 'STAGE_TRANSITION', 1, '审批系统', 'WORKFLOW', 'TRACE-5004-002', 0),
    (8004, 1, 'LEGACY_REQUIREMENT', 5005, 'workload_stage_status','未开始','已完成','STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5005-001', 0),
    (8005, 1, 'LEGACY_REQUIREMENT', 5006, 'project_stage_status', '未开始', '审批中', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5006-001', 0),
    (8006, 1, 'LEGACY_REQUIREMENT', 5008, 'launch_stage_status',  '未开始', '已完成', 'STAGE_TRANSITION', 1002, '李 retail1 BA', 'ONLINE', 'TRACE-5008-001', 0),
    (8007, 1, 'LEGACY_REQUIREMENT', 5008, 'requirement_status',  '软需编制', '已投产', 'STAGE_TRANSITION', 1, '审批系统', 'WORKFLOW', 'TRACE-5008-002', 0),
    (8008, 1, 'NEW_PROJECT_DIFF',   4003, 'review_status',        '待评审', '已评审', 'REVIEW_PASS',       1,    '管理员',     'ONLINE', 'TRACE-4003-001', 0),
    (8009, 1, 'NEW_PROJECT_DIFF',   4004, 'review_status',        '待评审', '已退回', 'REVIEW_RETURN',     1,    '管理员',     'ONLINE', 'TRACE-4004-001', 0),
    (8010, 1, 'NEW_PROJECT_DIFF',   4004, 'review_comment',       NULL,    '差异描述需补充 OCR 准确率指标', 'REVIEW_RETURN', 1, '管理员', 'ONLINE', 'TRACE-4004-002', 0),
    (8011, 1, 'BASELINE',           6001, 'baseline_no',         NULL,    'BL-P2026-002-20260815-1',     'BASELINE', 1,    '管理员',     'ONLINE', 'TRACE-6001-001', 0);

-- 十二、导入批次（演示批量导入能力，关联项目 3001 的 10 条差异）
INSERT INTO req_import_batch (id, tenant_id, biz_type, project_id, file_name, template_type, total_rows, success_rows, error_rows, errors_json, status, operator_id, operator_name, deleted) VALUES
    (9001, 1, 'DIFF', 3001, '零售贷款 2.0 差异清单.xlsx', 'DIFF_TEMPLATE_V1', 10, 10, 0, '[]', 'IMPORTED', 1004, '赵 retail1 PM', 0);

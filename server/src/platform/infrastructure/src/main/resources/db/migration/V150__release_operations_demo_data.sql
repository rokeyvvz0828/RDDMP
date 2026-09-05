-- REQ-20260902-058：投产管理本地测试数据。
-- 只为固定测试编码和固定测试 ID 补齐缺失记录，不删除或覆盖用户业务数据。
-- 依赖 V51 的项目/成员表、V71 的演示用户和 V149 的投产管理表。

-- 一、两个虚构测试项目。项目编码和主键均为本地测试预留值。
INSERT INTO pm_project
    (id, tenant_id, project_code, project_name, description, status, owner_id,
     planned_start_date, planned_end_date, created_by, deleted)
SELECT 940001, 1, 'REL-DEMO-ALPHA', '投产演练示范项目 A',
       '投产管理菜单验收用虚构项目 A', 'RUNNING', 1,
       '2026-09-01', '2026-12-31', 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM pm_project
    WHERE tenant_id = 1 AND deleted = 0
      AND (id = 940001 OR project_code = 'REL-DEMO-ALPHA')
);

INSERT INTO pm_project
    (id, tenant_id, project_code, project_name, description, status, owner_id,
     planned_start_date, planned_end_date, created_by, deleted)
SELECT 940002, 1, 'REL-DEMO-BETA', '投产演练示范项目 B',
       '投产管理菜单验收用虚构项目 B', 'PLANNING', 1,
       '2026-10-01', '2027-01-31', 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM pm_project
    WHERE tenant_id = 1 AND deleted = 0
      AND (id = 940002 OR project_code = 'REL-DEMO-BETA')
);

-- 二、项目成员。只有既有演示用户存在时才建立成员关系。
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941001, 1, 940001, 1001, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1001 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941001 OR (m.tenant_id = 1 AND m.project_id = 940001 AND m.user_id = 1001 AND m.deleted = 0));
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941002, 1, 940001, 1002, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1002 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941002 OR (m.tenant_id = 1 AND m.project_id = 940001 AND m.user_id = 1002 AND m.deleted = 0));
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941003, 1, 940001, 1003, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1003 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941003 OR (m.tenant_id = 1 AND m.project_id = 940001 AND m.user_id = 1003 AND m.deleted = 0));
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941004, 1, 940001, 1004, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1004 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941004 OR (m.tenant_id = 1 AND m.project_id = 940001 AND m.user_id = 1004 AND m.deleted = 0));
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941005, 1, 940002, 1005, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1005 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941005 OR (m.tenant_id = 1 AND m.project_id = 940002 AND m.user_id = 1005 AND m.deleted = 0));
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941006, 1, 940002, 1006, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1006 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941006 OR (m.tenant_id = 1 AND m.project_id = 940002 AND m.user_id = 1006 AND m.deleted = 0));
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941007, 1, 940002, 1007, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1007 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941007 OR (m.tenant_id = 1 AND m.project_id = 940002 AND m.user_id = 1007 AND m.deleted = 0));
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941008, 1, 940002, 1008, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1008 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941008 OR (m.tenant_id = 1 AND m.project_id = 940002 AND m.user_id = 1008 AND m.deleted = 0));

-- 三、投产演练计划、环境说明和三轮演练。
INSERT INTO rel_release_drill_plan
    (id, tenant_id, project_id, scenario_content, environment_content, created_by, updated_by)
SELECT 942001, 1, 940001, 'A 项目核心交易投产演练方案：验证发布、校验、回退和业务确认。',
       'A 项目环境：预生产双节点、模拟账务库、脱敏测试数据和监控告警。', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 942001);
INSERT INTO rel_release_drill_plan
    (id, tenant_id, project_id, scenario_content, environment_content, created_by, updated_by)
SELECT 943001, 1, 940002, 'B 项目渠道升级投产演练方案：验证分批发布、数据校验和应急回退。',
       'B 项目环境：预生产集群、模拟渠道端、脱敏客户数据和统一日志平台。', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 943001);

INSERT INTO rel_release_drill_round
    (id, tenant_id, project_id, drill_plan_id, round_no, round_name, planned_at, status, result_content, created_by, updated_by)
SELECT 942011, 1, 940001, 942001, 1, '第一轮：发布前检查', '2026-09-12 09:00:00', 'COMPLETED', '检查项全部通过，发现一项监控阈值需调整。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 942001 AND d.project_id = 940001 AND d.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_round r WHERE r.id = 942011);
INSERT INTO rel_release_drill_round
    (id, tenant_id, project_id, drill_plan_id, round_no, round_name, planned_at, status, result_content, created_by, updated_by)
SELECT 942012, 1, 940001, 942001, 2, '第二轮：业务验证', '2026-09-19 14:00:00', 'RUNNING', '正在验证核心交易和批量任务。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 942001 AND d.project_id = 940001 AND d.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_round r WHERE r.id = 942012);
INSERT INTO rel_release_drill_round
    (id, tenant_id, project_id, drill_plan_id, round_no, round_name, planned_at, status, result_content, created_by, updated_by)
SELECT 942013, 1, 940001, 942001, 3, '第三轮：回退演练', '2026-09-26 20:00:00', 'PLANNED', NULL, 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 942001 AND d.project_id = 940001 AND d.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_round r WHERE r.id = 942013);
INSERT INTO rel_release_drill_round
    (id, tenant_id, project_id, drill_plan_id, round_no, round_name, planned_at, status, result_content, created_by, updated_by)
SELECT 943011, 1, 940002, 943001, 1, '第一轮：发布前检查', '2026-10-10 09:00:00', 'COMPLETED', '发布前检查通过。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 943001 AND d.project_id = 940002 AND d.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_round r WHERE r.id = 943011);
INSERT INTO rel_release_drill_round
    (id, tenant_id, project_id, drill_plan_id, round_no, round_name, planned_at, status, result_content, created_by, updated_by)
SELECT 943012, 1, 940002, 943001, 2, '第二轮：渠道回归', '2026-10-17 14:00:00', 'PLANNED', NULL, 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 943001 AND d.project_id = 940002 AND d.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_round r WHERE r.id = 943012);
INSERT INTO rel_release_drill_round
    (id, tenant_id, project_id, drill_plan_id, round_no, round_name, planned_at, status, result_content, created_by, updated_by)
SELECT 943013, 1, 940002, 943001, 3, '第三轮：回退演练', '2026-10-24 20:00:00', 'PLANNED', NULL, 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_drill_plan d WHERE d.id = 943001 AND d.project_id = 940002 AND d.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_round r WHERE r.id = 943013);

-- 四、普通时序和回退时序明细。
INSERT INTO rel_release_timeline
    (id, tenant_id, project_id, timeline_type, timeline_name, description, created_by, updated_by)
SELECT 942101, 1, 940001, 'NORMAL', 'A 项目投产标准时序', '按发布、验证、观察、确认顺序执行。', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942101);
INSERT INTO rel_release_timeline
    (id, tenant_id, project_id, timeline_type, timeline_name, description, created_by, updated_by)
SELECT 942201, 1, 940001, 'ROLLBACK', 'A 项目投产回退时序', '按停止流量、恢复版本、校验数据、业务确认顺序执行。', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942201);
INSERT INTO rel_release_timeline
    (id, tenant_id, project_id, timeline_type, timeline_name, description, created_by, updated_by)
SELECT 943101, 1, 940002, 'NORMAL', 'B 项目投产标准时序', '覆盖渠道切换和批量任务观察。', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943101);
INSERT INTO rel_release_timeline
    (id, tenant_id, project_id, timeline_type, timeline_name, description, created_by, updated_by)
SELECT 943201, 1, 940002, 'ROLLBACK', 'B 项目投产回退时序', '覆盖渠道恢复、数据核对和业务确认。', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943201);

INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 942111, 1, 940001, 942101, 1, '冻结变更并完成发布前检查', '2026-09-28 20:00:00', '2026-09-28 20:30:00', 1001, '张统筹', 'COMPLETED', '核对发布单、窗口和监控基线。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942101 AND t.project_id = 940001 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 942111);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 942112, 1, 940001, 942101, 2, '部署应用版本', '2026-09-28 20:30:00', '2026-09-28 21:15:00', 1002, '李 retail1 BA', 'COMPLETED', '完成双节点滚动部署。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942101 AND t.project_id = 940001 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 942112);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 942113, 1, 940001, 942101, 3, '执行核心交易验证', '2026-09-28 21:15:00', '2026-09-28 22:00:00', 1003, '王 retail1 架构', 'RUNNING', '验证登录、查询、交易和批量任务。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942101 AND t.project_id = 940001 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 942113);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 942114, 1, 940001, 942101, 4, '业务确认并开放流量', '2026-09-28 22:00:00', '2026-09-28 22:30:00', 1004, '赵 retail1 PM', 'PENDING', '观察告警稳定后由业务负责人确认。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942101 AND t.project_id = 940001 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 942114);

INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 942211, 1, 940001, 942201, 1, '停止新流量并保留现场', '2026-09-28 22:30:00', '2026-09-28 22:45:00', 1001, '张统筹', 'PENDING', '保留日志、监控和问题现场。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942201 AND t.project_id = 940001 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 942211);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 942212, 1, 940001, 942201, 2, '恢复上一稳定版本', '2026-09-28 22:45:00', '2026-09-28 23:30:00', 1002, '李 retail1 BA', 'PENDING', '按回退包恢复应用和配置。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942201 AND t.project_id = 940001 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 942212);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 942213, 1, 940001, 942201, 3, '核对数据并完成回退确认', '2026-09-28 23:30:00', '2026-09-29 00:00:00', 1003, '王 retail1 架构', 'PENDING', '核对关键数据后由业务确认。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 942201 AND t.project_id = 940001 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 942213);

INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 943111, 1, 940002, 943101, 1, '渠道发布前检查', '2026-10-26 20:00:00', '2026-10-26 20:30:00', 1005, '钱 retail2 BA', 'PENDING', '确认渠道变更和监控基线。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943101 AND t.project_id = 940002 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 943111);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 943112, 1, 940002, 943101, 2, '部署渠道服务', '2026-10-26 20:30:00', '2026-10-26 21:15:00', 1006, '孙 retail2 PM', 'PENDING', '完成渠道服务滚动部署。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943101 AND t.project_id = 940002 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 943112);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 943113, 1, 940002, 943101, 3, '回归主要渠道功能', '2026-10-26 21:15:00', '2026-10-26 22:00:00', 1007, '周 corp1 BA', 'PENDING', '验证渠道登录、查询和交易链路。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943101 AND t.project_id = 940002 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 943113);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 943114, 1, 940002, 943101, 4, '业务确认并开放渠道', '2026-10-26 22:00:00', '2026-10-26 22:30:00', 1008, '吴 corp1 架构', 'PENDING', '监控稳定后开放全部渠道。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943101 AND t.project_id = 940002 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 943114);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 943211, 1, 940002, 943201, 1, '停止渠道流量', '2026-10-26 22:30:00', '2026-10-26 22:45:00', 1005, '钱 retail2 BA', 'PENDING', '保留故障现场并停止新增流量。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943201 AND t.project_id = 940002 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 943211);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 943212, 1, 940002, 943201, 2, '恢复渠道稳定版本', '2026-10-26 22:45:00', '2026-10-26 23:30:00', 1006, '孙 retail2 PM', 'PENDING', '回退渠道服务和配置。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943201 AND t.project_id = 940002 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 943212);
INSERT INTO rel_release_timeline_item
    (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT 943213, 1, 940002, 943201, 3, '核对渠道数据并确认恢复', '2026-10-26 23:30:00', '2026-10-27 00:00:00', 1007, '周 corp1 BA', 'PENDING', '核对关键数据并完成业务确认。', 1, 1
WHERE EXISTS (SELECT 1 FROM rel_release_timeline t WHERE t.id = 943201 AND t.project_id = 940002 AND t.deleted = 0) AND NOT EXISTS (SELECT 1 FROM rel_release_timeline_item i WHERE i.id = 943213);

-- 五、问题分析和跟踪数据，覆盖不同优先级与状态。
INSERT INTO rel_release_issue
    (id, tenant_id, project_id, issue_no, issue_title, priority, issue_status, discovered_at, owner_id, owner_name, issue_description, analysis_content, action_content, follow_up_content, created_by, updated_by)
SELECT 942301, 1, 940001, 'REL-A-001', '批量任务告警阈值偏低', 'HIGH', 'IN_PROGRESS', '2026-09-12 10:20:00', 1003, '王 retail1 架构', '首轮演练中批量任务出现误告警。', '当前阈值沿用旧版本，未按新批量窗口调整。', '调整监控阈值并补充回归场景。', '待第二轮演练确认告警收敛。', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_issue i WHERE i.id = 942301);
INSERT INTO rel_release_issue
    (id, tenant_id, project_id, issue_no, issue_title, priority, issue_status, discovered_at, owner_id, owner_name, issue_description, analysis_content, action_content, follow_up_content, created_by, updated_by)
SELECT 942302, 1, 940001, 'REL-A-002', '业务确认单缺少回退联系人', 'MEDIUM', 'OPEN', '2026-09-19 15:10:00', 1004, '赵 retail1 PM', '演练记录中的回退联系人字段未填写。', '项目角色配置中未指定回退联系人。', '补充项目联系人并更新演练模板。', '在回退演练前检查联系人清单。', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_issue i WHERE i.id = 942302);
INSERT INTO rel_release_issue
    (id, tenant_id, project_id, issue_no, issue_title, priority, issue_status, discovered_at, owner_id, owner_name, issue_description, analysis_content, action_content, follow_up_content, closed_at, created_by, updated_by)
SELECT 942303, 1, 940001, 'REL-A-003', '测试数据校验已完成', 'LOW', 'CLOSED', '2026-09-05 11:00:00', 1002, '李 retail1 BA', '发布前测试数据校验发现一项历史数据差异。', '差异来自已知脱敏规则，不影响业务结果。', '完成数据说明并关闭问题。', '已在发布检查清单中补充说明。', '2026-09-06 16:30:00', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_issue i WHERE i.id = 942303);
INSERT INTO rel_release_issue
    (id, tenant_id, project_id, issue_no, issue_title, priority, issue_status, discovered_at, owner_id, owner_name, issue_description, analysis_content, action_content, follow_up_content, created_by, updated_by)
SELECT 943301, 1, 940002, 'REL-B-001', '渠道回归脚本执行超时', 'HIGH', 'OPEN', '2026-10-10 10:15:00', 1007, '周 corp1 BA', '渠道回归脚本在高峰模拟流量下执行超时。', '脚本未按批次拆分请求，等待时间超过基线。', '拆分脚本批次并增加执行监控。', '下一轮渠道回归前复测。', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_issue i WHERE i.id = 943301);
INSERT INTO rel_release_issue
    (id, tenant_id, project_id, issue_no, issue_title, priority, issue_status, discovered_at, owner_id, owner_name, issue_description, analysis_content, action_content, follow_up_content, created_by, updated_by)
SELECT 943302, 1, 940002, 'REL-B-002', '监控看板缺少渠道维度', 'MEDIUM', 'IN_PROGRESS', '2026-10-17 15:20:00', 1008, '吴 corp1 架构', '投产观察看板无法按渠道区分请求量。', '现有看板只配置系统维度，缺少渠道标签。', '补充渠道维度并验证告警规则。', '等待监控配置发布后复核。', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_issue i WHERE i.id = 943302);
INSERT INTO rel_release_issue
    (id, tenant_id, project_id, issue_no, issue_title, priority, issue_status, discovered_at, owner_id, owner_name, issue_description, analysis_content, action_content, follow_up_content, closed_at, created_by, updated_by)
SELECT 943303, 1, 940002, 'REL-B-003', '预生产登录验证通过', 'LOW', 'CLOSED', '2026-10-03 09:30:00', 1006, '孙 retail2 PM', '预生产登录链路完成验证。', '账号、权限和认证链路均符合预期。', '记录验证结果并关闭问题。', '已纳入正式投产检查单。', '2026-10-03 12:00:00', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_issue i WHERE i.id = 943303);

-- 六、投产组及组内项目成员。
INSERT INTO rel_release_group
    (id, tenant_id, project_id, group_name, description, created_by, updated_by)
SELECT 942401, 1, 940001, 'A 项目投产指挥组', '负责投产窗口协调、状态播报和最终确认。', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 942401);
INSERT INTO rel_release_group
    (id, tenant_id, project_id, group_name, description, created_by, updated_by)
SELECT 942402, 1, 940001, 'A 项目技术保障组', '负责部署、监控、数据校验和回退执行。', 1, 1
FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 942402);
INSERT INTO rel_release_group
    (id, tenant_id, project_id, group_name, description, created_by, updated_by)
SELECT 943401, 1, 940002, 'B 项目投产指挥组', '负责渠道投产协调、业务通知和决策确认。', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 943401);
INSERT INTO rel_release_group
    (id, tenant_id, project_id, group_name, description, created_by, updated_by)
SELECT 943402, 1, 940002, 'B 项目技术保障组', '负责渠道服务、监控和数据恢复。', 1, 1
FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 943402);

INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 942411, 1, 940001, 942401, 941001, 1001, '张统筹', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941001 AND m.project_id = 940001 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 942401 AND g.project_id = 940001 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 942411);
INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 942412, 1, 940001, 942401, 941004, 1004, '赵 retail1 PM', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941004 AND m.project_id = 940001 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 942401 AND g.project_id = 940001 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 942412);
INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 942421, 1, 940001, 942402, 941002, 1002, '李 retail1 BA', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941002 AND m.project_id = 940001 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 942402 AND g.project_id = 940001 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 942421);
INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 942422, 1, 940001, 942402, 941003, 1003, '王 retail1 架构', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941003 AND m.project_id = 940001 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 942402 AND g.project_id = 940001 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 942422);
INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 943411, 1, 940002, 943401, 941005, 1005, '钱 retail2 BA', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941005 AND m.project_id = 940002 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 943401 AND g.project_id = 940002 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 943411);
INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 943412, 1, 940002, 943401, 941006, 1006, '孙 retail2 PM', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941006 AND m.project_id = 940002 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 943401 AND g.project_id = 940002 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 943412);
INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 943421, 1, 940002, 943402, 941007, 1007, '周 corp1 BA', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941007 AND m.project_id = 940002 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 943402 AND g.project_id = 940002 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 943421);
INSERT INTO rel_release_group_member
    (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by)
SELECT 943422, 1, 940002, 943402, 941008, 1008, '吴 corp1 架构', 1
WHERE EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941008 AND m.project_id = 940002 AND m.status = 1 AND m.deleted = 0)
  AND EXISTS (SELECT 1 FROM rel_release_group g WHERE g.id = 943402 AND g.project_id = 940002 AND g.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM rel_release_group_member gm WHERE gm.id = 943422);

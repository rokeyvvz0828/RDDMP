-- REQ-20260923-001 批次5·数据看板（基线文档第 16 章，T10）：
-- 1) 六大维度聚合视图 dws_*（16.3 数据层表清单：统计口径 100% 取自各模块原生数据，
--    无自定义统计逻辑；服务层按当前用户权责做前置权限过滤后消费）；
-- 2) 菜单 753「数据看板」挂 745 目录 + 权限点 7531（只读，看板无任何编辑入口）；
-- 仅追加，不修改已发布脚本；幂等可重复执行（CREATE OR REPLACE VIEW / INSERT IGNORE）。

-- 1) 维度 1：全生命周期阶段-活动-工单层级统计（仅普通活动，专题独立不归属阶段）
CREATE OR REPLACE VIEW dws_stage_activity_order AS
SELECT COALESCE(o.tenant_id, a.tenant_id) AS tenant_id,
       s.id AS stage_id, s.stage_code, s.stage_name, s.sort_no,
       a.id AS activity_id, a.activity_name, a.granularity,
       COUNT(o.id) AS order_total,
       SUM(CASE WHEN o.order_status = 'WAIT_PRE' THEN 1 ELSE 0 END) AS wait_pre_cnt,
       SUM(CASE WHEN o.order_status = 'WAIT_ACCEPT' THEN 1 ELSE 0 END) AS wait_accept_cnt,
       SUM(CASE WHEN o.order_status = 'EXECUTING' THEN 1 ELSE 0 END) AS executing_cnt,
       SUM(CASE WHEN o.order_status = 'SUSPENDED' THEN 1 ELSE 0 END) AS suspended_cnt,
       SUM(CASE WHEN o.order_status = 'REVIEWING' THEN 1 ELSE 0 END) AS reviewing_cnt,
       SUM(CASE WHEN o.order_status = 'REVIEW_REJECTED' THEN 1 ELSE 0 END) AS review_rejected_cnt,
       SUM(CASE WHEN o.order_status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_cnt,
       SUM(CASE WHEN o.order_status = 'ARCHIVED' THEN 1 ELSE 0 END) AS archived_cnt,
       SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_cnt
FROM lifecycle_stage s
JOIN activity a ON a.lifecycle_stage_id = s.id AND a.tenant_id = s.tenant_id
     AND a.deleted = 0 AND a.activity_type = 'NORMAL'
LEFT JOIN work_order o ON o.activity_id = a.id AND o.tenant_id = a.tenant_id AND o.deleted = 0
WHERE s.deleted = 0
GROUP BY COALESCE(o.tenant_id, a.tenant_id), s.id, s.stage_code, s.stage_name, s.sort_no,
         a.id, a.activity_name, a.granularity;

-- 2) 维度 2：活动进展状态（活动类型标签区分普通/专题，两态独立统计）
CREATE OR REPLACE VIEW dws_activity_status AS
SELECT a.tenant_id, a.activity_type, a.granularity,
       COUNT(*) AS total,
       SUM(CASE WHEN a.activity_status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_cnt,
       SUM(CASE WHEN a.activity_status = 'INACTIVE' THEN 1 ELSE 0 END) AS inactive_cnt,
       SUM(CASE WHEN a.activity_status = 'OBSOLETE' THEN 1 ELSE 0 END) AS obsolete_cnt,
       SUM(CASE WHEN a.activity_type = 'NORMAL' AND EXISTS (
               SELECT 1 FROM activity_topic_rel r
               WHERE r.member_activity_id = a.id AND r.tenant_id = a.tenant_id AND r.deleted = 0)
           THEN 1 ELSE 0 END) AS linked_topic_cnt,
       SUM(CASE WHEN a.activity_type = 'NORMAL' AND NOT EXISTS (
               SELECT 1 FROM activity_topic_rel r
               WHERE r.member_activity_id = a.id AND r.tenant_id = a.tenant_id AND r.deleted = 0)
           THEN 1 ELSE 0 END) AS unlinked_topic_cnt
FROM activity a
WHERE a.deleted = 0
GROUP BY a.tenant_id, a.activity_type, a.granularity;

-- 3) 维度 3：专题进度 + 专题运行状态（专题工单归属：经 task.topic_activity_id 关联拆分工单，
--    与下发链路一致；专题独立统计，不归属生命周期阶段）
CREATE OR REPLACE VIEW dws_topic_progress AS
SELECT t.tenant_id, t.id AS topic_id, t.activity_name AS topic_name, t.activity_status AS topic_status,
       COUNT(o.id) AS order_total,
       SUM(CASE WHEN o.order_status = 'WAIT_PRE' THEN 1 ELSE 0 END) AS wait_pre_cnt,
       SUM(CASE WHEN o.order_status = 'WAIT_ACCEPT' THEN 1 ELSE 0 END) AS wait_accept_cnt,
       SUM(CASE WHEN o.order_status = 'EXECUTING' THEN 1 ELSE 0 END) AS executing_cnt,
       SUM(CASE WHEN o.order_status = 'SUSPENDED' THEN 1 ELSE 0 END) AS suspended_cnt,
       SUM(CASE WHEN o.order_status = 'REVIEWING' THEN 1 ELSE 0 END) AS reviewing_cnt,
       SUM(CASE WHEN o.order_status = 'REVIEW_REJECTED' THEN 1 ELSE 0 END) AS review_rejected_cnt,
       SUM(CASE WHEN o.order_status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_cnt,
       SUM(CASE WHEN o.order_status = 'ARCHIVED' THEN 1 ELSE 0 END) AS archived_cnt,
       SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_cnt
FROM activity t
LEFT JOIN task tk ON tk.topic_activity_id = t.id AND tk.tenant_id = t.tenant_id AND tk.deleted = 0
LEFT JOIN work_order o ON o.task_id = tk.id AND o.tenant_id = tk.tenant_id AND o.deleted = 0
WHERE t.activity_type = 'TOPIC' AND t.deleted = 0
GROUP BY t.tenant_id, t.id, t.activity_name, t.activity_status;

-- 4) 维度 4：问题状态分项（4 态，与问题台账同源）
CREATE OR REPLACE VIEW dws_issue_status AS
SELECT tenant_id,
       COUNT(*) AS total,
       SUM(CASE WHEN issue_status = 'WAIT_RECTIFY' THEN 1 ELSE 0 END) AS wait_rectify_cnt,
       SUM(CASE WHEN issue_status = 'RECTIFYING' THEN 1 ELSE 0 END) AS rectifying_cnt,
       SUM(CASE WHEN issue_status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_cnt,
       SUM(CASE WHEN issue_status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_cnt
FROM issue
WHERE deleted = 0
GROUP BY tenant_id;

-- 5) 维度 5：风险状态分项 + 等级分层（6 态 + 单一等级值）
CREATE OR REPLACE VIEW dws_risk_status AS
SELECT tenant_id,
       COUNT(*) AS total,
       SUM(CASE WHEN risk_status = 'WAIT_PREVENT' THEN 1 ELSE 0 END) AS wait_prevent_cnt,
       SUM(CASE WHEN risk_status = 'PREVENTING' THEN 1 ELSE 0 END) AS preventing_cnt,
       SUM(CASE WHEN risk_status = 'AVOIDED' THEN 1 ELSE 0 END) AS avoided_cnt,
       SUM(CASE WHEN risk_status = 'OCCURRED' THEN 1 ELSE 0 END) AS occurred_cnt,
       SUM(CASE WHEN risk_status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_cnt,
       SUM(CASE WHEN risk_status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_cnt,
       SUM(CASE WHEN risk_level = 'HIGH' THEN 1 ELSE 0 END) AS level_high_cnt,
       SUM(CASE WHEN risk_level = 'MEDIUM' THEN 1 ELSE 0 END) AS level_mid_cnt,
       SUM(CASE WHEN risk_level = 'LOW' THEN 1 ELSE 0 END) AS level_low_cnt
FROM risk
WHERE deleted = 0
GROUP BY tenant_id;

-- 6) 维度 6：工单进度状态 + 时效分桶（时效为叠加维度，仅「在办工单」参与；
--    直接消费 v_order_deadline_live 实时视图，绝不自行重算三档）
CREATE OR REPLACE VIEW dws_order_status AS
SELECT o.tenant_id, o.granularity,
       COUNT(*) AS order_total,
       SUM(CASE WHEN o.order_status = 'WAIT_PRE' THEN 1 ELSE 0 END) AS wait_pre_cnt,
       SUM(CASE WHEN o.order_status = 'WAIT_ACCEPT' THEN 1 ELSE 0 END) AS wait_accept_cnt,
       SUM(CASE WHEN o.order_status = 'EXECUTING' THEN 1 ELSE 0 END) AS executing_cnt,
       SUM(CASE WHEN o.order_status = 'SUSPENDED' THEN 1 ELSE 0 END) AS suspended_cnt,
       SUM(CASE WHEN o.order_status = 'REVIEWING' THEN 1 ELSE 0 END) AS reviewing_cnt,
       SUM(CASE WHEN o.order_status = 'REVIEW_REJECTED' THEN 1 ELSE 0 END) AS review_rejected_cnt,
       SUM(CASE WHEN o.order_status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_cnt,
       SUM(CASE WHEN o.order_status = 'ARCHIVED' THEN 1 ELSE 0 END) AS archived_cnt,
       SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_cnt,
       SUM(CASE WHEN o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED')
                AND v.deadline_status_live = 'NORMAL' THEN 1 ELSE 0 END) AS deadline_normal_cnt,
       SUM(CASE WHEN o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED')
                AND v.deadline_status_live = 'NEAR_OVERDUE' THEN 1 ELSE 0 END) AS deadline_near_cnt,
       SUM(CASE WHEN o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED')
                AND v.deadline_status_live = 'OVERDUE' THEN 1 ELSE 0 END) AS deadline_overdue_cnt
FROM work_order o
JOIN v_order_deadline_live v ON v.order_id = o.id AND v.tenant_id = o.tenant_id
WHERE o.deleted = 0
GROUP BY o.tenant_id, o.granularity;

-- 7) 菜单 753「数据看板」挂 745 + 权限点（只读，看板无手动编辑入口）
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (753, 1, 745, 'menu', '数据看板', 'DataMigrationLifecycleDashboard', '/data-migration/lifecycle/dashboard', 'data-migration', 'data-migration-lifecycle:dashboard', 'data-analysis', 10);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 753, 1), (200, 753, 1), (201, 753, 1);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7531, 1, 753, 'read', 'data-migration-lifecycle:dashboard', '数据看板访问（只读）');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 753 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 753 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 753 AND status = 1;

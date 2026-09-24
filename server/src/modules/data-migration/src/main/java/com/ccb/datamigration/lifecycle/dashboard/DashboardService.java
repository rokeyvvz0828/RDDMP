package com.ccb.datamigration.lifecycle.dashboard;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.dashboard.model.ActivityOrderView;
import com.ccb.datamigration.lifecycle.dashboard.model.ActivityStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.GranularityOrderStatus;
import com.ccb.datamigration.lifecycle.dashboard.model.IssueStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.OrderStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.RiskStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.StageActivityOrderView;
import com.ccb.datamigration.lifecycle.dashboard.model.TopicProgressOverview;
import com.ccb.datamigration.lifecycle.dashboard.model.TopicProgressView;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 数据看板聚合服务（基线第 16 章，T10）：
 * 六大维度统计口径 100% 取自各业务模块原生数据（与台账同源同条件）；
 * 所有统计前置权限过滤（铁律 #8：先过滤后统计，杜绝全局数据泄露）；
 * 时效分桶为叠加维度，仅「在办工单」参与，直接消费 v_order_deadline_live 实时视图并逐单对账
 * （不一致抛 DASHBOARD_DEADLINE_VIEW_MISMATCH，绝不自行重算三档）。
 */
@Service
public class DashboardService {
    /** 9 态（基线 3.2），顺序即展示顺序。 */
    private static final String[] ORDER_STATES = {"WAIT_PRE", "WAIT_ACCEPT", "EXECUTING", "SUSPENDED",
            "REVIEWING", "REVIEW_REJECTED", "CLOSED", "ARCHIVED", "CANCELLED"};

    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;

    public DashboardService(JdbcTemplate jdbc, LifecyclePermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    /** 维度 1：全生命周期阶段-活动-工单层级统计（仅普通活动；专题独立不归属阶段）。 */
    public List<StageActivityOrderView> stageActivity(AuthUser user) {
        assertDeadlineViewConsistent(user);
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(user.tenantId());
        StringBuilder where = new StringBuilder("s.tenant_id = ? AND s.deleted = 0");
        appendActivityScope(user, where, whereArgs, "a", false);
        List<Object> joinArgs = new ArrayList<>();
        String orderScope = orderScopeSql(user, joinArgs);
        List<Object> args = new ArrayList<>(joinArgs);
        args.addAll(whereArgs);
        String sql = "SELECT s.id AS stage_id, s.stage_code, s.stage_name, a.id AS activity_id, "
                + "a.activity_name, a.granularity, "
                + "COUNT(o.id) AS order_total, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_PRE' THEN 1 ELSE 0 END), 0) AS wait_pre_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_ACCEPT' THEN 1 ELSE 0 END), 0) AS wait_accept_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'EXECUTING' THEN 1 ELSE 0 END), 0) AS executing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEWING' THEN 1 ELSE 0 END), 0) AS reviewing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEW_REJECTED' THEN 1 ELSE 0 END), 0) AS review_rejected_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'ARCHIVED' THEN 1 ELSE 0 END), 0) AS archived_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_cnt "
                + "FROM lifecycle_stage s "
                + "JOIN activity a ON a.lifecycle_stage_id = s.id AND a.tenant_id = s.tenant_id "
                + "AND a.deleted = 0 AND a.activity_type = 'NORMAL' "
                + "LEFT JOIN work_order o ON o.activity_id = a.id AND o.tenant_id = a.tenant_id "
                + "AND o.deleted = 0" + orderScope + " "
                + "WHERE " + where + " "
                + "GROUP BY s.id, s.stage_code, s.stage_name, a.id, a.activity_name, a.granularity "
                + "ORDER BY s.sort_no, a.id";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        List<StageActivityOrderView> stages = new ArrayList<>();
        Map<Long, List<ActivityOrderView>> byStage = new LinkedHashMap<>();
        Map<Long, long[]> stageTotals = new LinkedHashMap<>();
        Map<Long, String> stageMeta = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long stageId = longOf(row, "stage_id");
            long orderTotal = longOf(row, "order_total");
            long[] c = states(row);
            assertNineStateSum(orderTotal, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]);
            byStage.computeIfAbsent(stageId, k -> new ArrayList<>())
                    .add(new ActivityOrderView(longOf(row, "activity_id"), stringOf(row, "activity_name"),
                            stringOf(row, "granularity"), orderTotal, c[0], c[1], c[2], c[3], c[4], c[5],
                            c[6], c[7], c[8], rate(c[6] + c[7], orderTotal)));
            stageMeta.putIfAbsent(stageId, stringOf(row, "stage_code") + "\u0000" + stringOf(row, "stage_name"));
            long[] t = stageTotals.computeIfAbsent(stageId, k -> new long[10]);
            for (int i = 0; i < 9; i++) {
                t[i] += c[i];
            }
            t[9] += orderTotal;
        }
        for (Map.Entry<Long, List<ActivityOrderView>> entry : byStage.entrySet()) {
            long[] t = stageTotals.get(entry.getKey());
            String[] meta = stageMeta.get(entry.getKey()).split("\u0000");
            assertNineStateSum(t[9], t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7], t[8]);
            stages.add(new StageActivityOrderView(entry.getKey(), meta[0], meta[1], t[9], t[0], t[1], t[2],
                    t[3], t[4], t[5], t[6], t[7], t[8], rate(t[6] + t[7], t[9]), entry.getValue()));
        }
        return stages;
    }

    /** 维度 2：活动进展状态（活动类型标签区分普通/专题，颗粒度分列）。 */
    public ActivityStatusView activityStatus(AuthUser user) {
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("a.tenant_id = ? AND a.deleted = 0");
        appendActivityScope(user, where, args, "a", false);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.activity_type, a.granularity, COUNT(*) AS total, "
                        + "SUM(CASE WHEN a.activity_status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_cnt, "
                        + "SUM(CASE WHEN a.activity_status = 'INACTIVE' THEN 1 ELSE 0 END) AS inactive_cnt, "
                        + "SUM(CASE WHEN a.activity_status = 'OBSOLETE' THEN 1 ELSE 0 END) AS obsolete_cnt, "
                        + "SUM(CASE WHEN a.activity_type = 'NORMAL' AND EXISTS (SELECT 1 FROM activity_topic_rel r "
                        + "WHERE r.member_activity_id = a.id AND r.tenant_id = a.tenant_id AND r.deleted = 0) "
                        + "THEN 1 ELSE 0 END) AS linked_topic_cnt, "
                        + "SUM(CASE WHEN a.activity_type = 'NORMAL' AND NOT EXISTS (SELECT 1 FROM activity_topic_rel r "
                        + "WHERE r.member_activity_id = a.id AND r.tenant_id = a.tenant_id AND r.deleted = 0) "
                        + "THEN 1 ELSE 0 END) AS unlinked_topic_cnt "
                        + "FROM activity a WHERE " + where + " GROUP BY a.activity_type, a.granularity",
                args.toArray());
        long total = 0;
        long active = 0;
        long inactive = 0;
        long obsolete = 0;
        long linked = 0;
        long unlinked = 0;
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> byGranularity = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long t = longOf(row, "total");
            total += t;
            active += longOf(row, "active_cnt");
            inactive += longOf(row, "inactive_cnt");
            obsolete += longOf(row, "obsolete_cnt");
            linked += longOf(row, "linked_topic_cnt");
            unlinked += longOf(row, "unlinked_topic_cnt");
            byType.merge(stringOf(row, "activity_type"), t, Long::sum);
            byGranularity.merge(stringOf(row, "granularity"), t, Long::sum);
        }
        return new ActivityStatusView(total, active, inactive, obsolete, linked, unlinked, byType, byGranularity);
    }

    /** 维度 3：专题进度 + 专题运行状态（专题独立统计，不归属生命周期阶段）。 */
    public TopicProgressOverview topicProgress(AuthUser user) {
        assertDeadlineViewConsistent(user);
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(user.tenantId());
        StringBuilder where = new StringBuilder("t.tenant_id = ? AND t.activity_type = 'TOPIC' AND t.deleted = 0");
        appendActivityScope(user, where, whereArgs, "t", true);
        List<Object> joinArgs = new ArrayList<>();
        String orderScope = orderScopeSql(user, joinArgs);
        List<Object> args = new ArrayList<>(joinArgs);
        args.addAll(whereArgs);
        String sql = "SELECT t.id AS topic_id, t.activity_name, t.activity_status, "
                + "COUNT(o.id) AS order_total, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_PRE' THEN 1 ELSE 0 END), 0) AS wait_pre_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_ACCEPT' THEN 1 ELSE 0 END), 0) AS wait_accept_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'EXECUTING' THEN 1 ELSE 0 END), 0) AS executing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEWING' THEN 1 ELSE 0 END), 0) AS reviewing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEW_REJECTED' THEN 1 ELSE 0 END), 0) AS review_rejected_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'ARCHIVED' THEN 1 ELSE 0 END), 0) AS archived_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_cnt "
                + "FROM activity t "
                + "LEFT JOIN task tk ON tk.topic_activity_id = t.id AND tk.tenant_id = t.tenant_id AND tk.deleted = 0 "
                + "LEFT JOIN work_order o ON o.task_id = tk.id AND o.tenant_id = tk.tenant_id AND o.deleted = 0"
                + orderScope + " "
                + "WHERE " + where + " GROUP BY t.id, t.activity_name, t.activity_status ORDER BY t.id";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        List<TopicProgressView> topics = new ArrayList<>();
        long topicTotal = 0;
        long activeCnt = 0;
        long inactiveCnt = 0;
        long obsoleteCnt = 0;
        long taskedCnt = 0;
        long untaskedCnt = 0;
        long orderTotal = 0;
        for (Map<String, Object> row : rows) {
            long t = longOf(row, "order_total");
            long[] c = states(row);
            assertNineStateSum(t, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]);
            String status = stringOf(row, "activity_status");
            topicTotal++;
            if ("ACTIVE".equals(status)) {
                activeCnt++;
            } else if ("INACTIVE".equals(status)) {
                inactiveCnt++;
            } else {
                obsoleteCnt++;
            }
            if (t > 0) {
                taskedCnt++;
            } else {
                untaskedCnt++;
            }
            orderTotal += t;
            topics.add(new TopicProgressView(longOf(row, "topic_id"), stringOf(row, "activity_name"), status,
                    t, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8],
                    rate(c[6] + c[7], t), rate(c[1] + c[2] + c[4], t), rate(c[3] + c[5], t)));
        }
        return new TopicProgressOverview(topicTotal, activeCnt, inactiveCnt, obsoleteCnt, taskedCnt,
                untaskedCnt, orderTotal, topics);
    }

    /** 维度 4：问题状态分项（4 态，与问题台账同源同过滤条件）。 */
    public IssueStatusView issueStatus(AuthUser user) {
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("i.tenant_id = ? AND i.deleted = 0");
        if (!permissions.isAdmin(user)) {
            where.append(" AND (i.reporter_id = ? OR i.rectifier_id = ?)");
            args.add(user.id());
            args.add(user.id());
        }
        Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN i.issue_status = 'WAIT_RECTIFY' THEN 1 ELSE 0 END), 0) AS wait_rectify_cnt, "
                + "COALESCE(SUM(CASE WHEN i.issue_status = 'RECTIFYING' THEN 1 ELSE 0 END), 0) AS rectifying_cnt, "
                + "COALESCE(SUM(CASE WHEN i.issue_status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_cnt, "
                + "COALESCE(SUM(CASE WHEN i.issue_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_cnt "
                + "FROM issue i WHERE " + where, args.toArray());
        return new IssueStatusView(longOf(row, "total"), longOf(row, "wait_rectify_cnt"),
                longOf(row, "rectifying_cnt"), longOf(row, "closed_cnt"), longOf(row, "cancelled_cnt"));
    }

    /** 维度 5：风险状态分项（6 态）+ 等级分层（与风险台账同源同过滤条件）。 */
    public RiskStatusView riskStatus(AuthUser user) {
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("r.tenant_id = ? AND r.deleted = 0");
        if (!permissions.isAdmin(user)) {
            where.append(" AND (r.reporter_id = ? OR r.prevent_owner_id = ?)");
            args.add(user.id());
            args.add(user.id());
        }
        Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN r.risk_status = 'WAIT_PREVENT' THEN 1 ELSE 0 END), 0) AS wait_prevent_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_status = 'PREVENTING' THEN 1 ELSE 0 END), 0) AS preventing_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_status = 'AVOIDED' THEN 1 ELSE 0 END), 0) AS avoided_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_status = 'OCCURRED' THEN 1 ELSE 0 END), 0) AS occurred_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_level = 'HIGH' THEN 1 ELSE 0 END), 0) AS level_high_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_level = 'MEDIUM' THEN 1 ELSE 0 END), 0) AS level_mid_cnt, "
                + "COALESCE(SUM(CASE WHEN r.risk_level = 'LOW' THEN 1 ELSE 0 END), 0) AS level_low_cnt "
                + "FROM risk r WHERE " + where, args.toArray());
        return new RiskStatusView(longOf(row, "total"), longOf(row, "wait_prevent_cnt"),
                longOf(row, "preventing_cnt"), longOf(row, "avoided_cnt"), longOf(row, "occurred_cnt"),
                longOf(row, "closed_cnt"), longOf(row, "cancelled_cnt"), longOf(row, "level_high_cnt"),
                longOf(row, "level_mid_cnt"), longOf(row, "level_low_cnt"));
    }

    /** 维度 6：工单进度状态（9 态分项之和=总数）+ 时效分桶（叠加维度，仅「在办工单」参与）。 */
    public OrderStatusView orderStatus(AuthUser user) {
        assertDeadlineViewConsistent(user);
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("o.tenant_id = ? AND o.deleted = 0");
        appendOrderScope(user, where, args);
        Map<String, Object> overall = jdbc.queryForMap("SELECT COUNT(*) AS order_total, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_PRE' THEN 1 ELSE 0 END), 0) AS wait_pre_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_ACCEPT' THEN 1 ELSE 0 END), 0) AS wait_accept_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'EXECUTING' THEN 1 ELSE 0 END), 0) AS executing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEWING' THEN 1 ELSE 0 END), 0) AS reviewing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEW_REJECTED' THEN 1 ELSE 0 END), 0) AS review_rejected_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'ARCHIVED' THEN 1 ELSE 0 END), 0) AS archived_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_cnt "
                + "FROM work_order o WHERE " + where, args.toArray());
        long[] c = states(overall);
        long orderTotal = longOf(overall, "order_total");
        assertNineStateSum(orderTotal, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]);
        long[] deadline = deadlineBuckets(user);
        long[] normal = rateParts(c);
        List<GranularityOrderStatus> byGranularity = granularityRows(user);
        return new OrderStatusView(orderTotal, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8],
                rate(c[6] + c[7], orderTotal), rate(normal[0], normal[1]), rate(c[5], orderTotal),
                rate(c[7], orderTotal), deadline[0], deadline[1], deadline[2], byGranularity);
    }

    /** 9 态分项之和 = 总数（状态唯一统计不变量，铁律 #2）。 */
    static void assertNineStateSum(long total, long waitPre, long waitAccept, long executing, long suspended,
                                   long reviewing, long reviewRejected, long closed, long archived,
                                   long cancelled) {
        long sum = waitPre + waitAccept + executing + suspended + reviewing + reviewRejected
                + closed + archived + cancelled;
        if (sum != total) {
            throw new IllegalStateException("看板 9 态分项之和与总数不一致：sum=" + sum + ", total=" + total);
        }
    }

    /** 时效对账（16.2.3.6/16.7）：实时时效视图与工单冗余字段逐单一致，不一致即抛错拒绝出数。 */
    private void assertDeadlineViewConsistent(AuthUser user) {
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("o.tenant_id = ? AND o.deleted = 0");
        appendOrderScope(user, where, args);
        Integer mismatch = jdbc.queryForObject("SELECT COUNT(*) FROM work_order o "
                + "JOIN v_order_deadline_live v ON v.order_id = o.id AND v.tenant_id = o.tenant_id "
                + "WHERE " + where + " AND o.deadline_status <> v.deadline_status_live",
                Integer.class, args.toArray());
        if (mismatch != null && mismatch > 0) {
            throw new BusinessException(LifecycleErrorCode.DASHBOARD_DEADLINE_VIEW_MISMATCH,
                    "看板实时时效视图与工单冗余字段不一致，拒绝出数（mismatch=" + mismatch + "）");
        }
    }

    private long[] deadlineBuckets(AuthUser user) {
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("o.tenant_id = ? AND o.deleted = 0 "
                + "AND o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED')");
        appendOrderScope(user, where, args);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT v.deadline_status_live, COUNT(*) AS cnt "
                + "FROM work_order o JOIN v_order_deadline_live v ON v.order_id = o.id AND v.tenant_id = o.tenant_id "
                + "WHERE " + where + " GROUP BY v.deadline_status_live", args.toArray());
        long normal = 0;
        long near = 0;
        long overdue = 0;
        for (Map<String, Object> row : rows) {
            String bucket = stringOf(row, "deadline_status_live");
            long cnt = longOf(row, "cnt");
            if ("NORMAL".equals(bucket)) {
                normal += cnt;
            } else if ("NEAR_OVERDUE".equals(bucket)) {
                near += cnt;
            } else if ("OVERDUE".equals(bucket)) {
                overdue += cnt;
            }
        }
        return new long[]{normal, near, overdue};
    }

    private List<GranularityOrderStatus> granularityRows(AuthUser user) {
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("o.tenant_id = ? AND o.deleted = 0");
        appendOrderScope(user, where, args);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.granularity, COUNT(*) AS order_total, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_PRE' THEN 1 ELSE 0 END), 0) AS wait_pre_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'WAIT_ACCEPT' THEN 1 ELSE 0 END), 0) AS wait_accept_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'EXECUTING' THEN 1 ELSE 0 END), 0) AS executing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEWING' THEN 1 ELSE 0 END), 0) AS reviewing_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'REVIEW_REJECTED' THEN 1 ELSE 0 END), 0) AS review_rejected_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'ARCHIVED' THEN 1 ELSE 0 END), 0) AS archived_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED') "
                + "AND v.deadline_status_live = 'NORMAL' THEN 1 ELSE 0 END), 0) AS deadline_normal_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED') "
                + "AND v.deadline_status_live = 'NEAR_OVERDUE' THEN 1 ELSE 0 END), 0) AS deadline_near_cnt, "
                + "COALESCE(SUM(CASE WHEN o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED') "
                + "AND v.deadline_status_live = 'OVERDUE' THEN 1 ELSE 0 END), 0) AS deadline_overdue_cnt "
                + "FROM work_order o JOIN v_order_deadline_live v ON v.order_id = o.id AND v.tenant_id = o.tenant_id "
                + "WHERE " + where + " GROUP BY o.granularity ORDER BY o.granularity", args.toArray());
        List<GranularityOrderStatus> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long[] c = states(row);
            long total = longOf(row, "order_total");
            assertNineStateSum(total, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]);
            list.add(new GranularityOrderStatus(stringOf(row, "granularity"), total, c[0], c[1], c[2], c[3],
                    c[4], c[5], c[6], c[7], c[8], longOf(row, "deadline_normal_cnt"),
                    longOf(row, "deadline_near_cnt"), longOf(row, "deadline_overdue_cnt")));
        }
        return list;
    }

    /** 非管理员：仅统计本人负责/参与/管辖（与工单台账 appendDataScope 同源同口径）的工单。 */
    private void appendOrderScope(AuthUser user, StringBuilder where, List<Object> args) {
        if (permissions.isAdmin(user)) {
            return;
        }
        List<Long> ownedComponents = jdbc.queryForList("SELECT id FROM dm_component WHERE tenant_id = ? "
                + "AND owner_id = ? AND deleted = 0", Long.class, user.tenantId(), user.id());
        where.append(" AND (o.current_executor_id = ? OR JSON_CONTAINS(COALESCE(o.participant_ids, JSON_ARRAY()), ?, '$')");
        args.add(user.id());
        args.add(String.valueOf(user.id()));
        if (!ownedComponents.isEmpty()) {
            where.append(" OR o.component_id IN (" + String.join(",",
                    java.util.Collections.nCopies(ownedComponents.size(), "?")) + ")");
            args.addAll(ownedComponents);
        }
        where.append(")");
    }

    private String orderScopeSql(AuthUser user, List<Object> args) {
        if (permissions.isAdmin(user)) {
            return "";
        }
        StringBuilder where = new StringBuilder();
        appendOrderScope(user, where, args);
        return " AND " + where;
    }

    /** 非管理员：活动仅统计本人创建或含本人权责工单的活动（杜绝全局数据泄露）。
     *  普通活动：工单按 o.activity_id 归属；专题：经 task.topic_activity_id 归属拆分工单。 */
    private void appendActivityScope(AuthUser user, StringBuilder where, List<Object> args, String alias,
                                     boolean topicMode) {
        if (permissions.isAdmin(user)) {
            return;
        }
        where.append(" AND (").append(alias).append(".created_by = ? OR EXISTS (");
        if (topicMode) {
            where.append("SELECT 1 FROM task tk2 JOIN work_order o2 ON o2.task_id = tk2.id "
                    + "AND o2.tenant_id = tk2.tenant_id AND o2.deleted = 0 "
                    + "WHERE tk2.topic_activity_id = ").append(alias).append(".id AND tk2.tenant_id = ? "
                    + "AND tk2.deleted = 0 AND (");
        } else {
            where.append("SELECT 1 FROM work_order o2 WHERE o2.activity_id = ").append(alias)
                    .append(".id AND o2.tenant_id = ? AND o2.deleted = 0 AND (");
        }
        where.append("o2.current_executor_id = ? OR JSON_CONTAINS(COALESCE(o2.participant_ids, JSON_ARRAY()), ?, '$')");
        args.add(user.id());
        args.add(user.tenantId());
        args.add(user.id());
        args.add(String.valueOf(user.id()));
        List<Long> ownedComponents = jdbc.queryForList("SELECT id FROM dm_component WHERE tenant_id = ? "
                + "AND owner_id = ? AND deleted = 0", Long.class, user.tenantId(), user.id());
        if (!ownedComponents.isEmpty()) {
            where.append(" OR o2.component_id IN (" + String.join(",",
                    java.util.Collections.nCopies(ownedComponents.size(), "?")) + ")");
            args.addAll(ownedComponents);
        }
        where.append("))");
        if (topicMode) {
            where.append(")");
        }
        where.append(")");
    }

    private static long[] states(Map<String, Object> row) {
        long[] c = new long[9];
        for (int i = 0; i < ORDER_STATES.length; i++) {
            c[i] = longOf(row, stateColumn(ORDER_STATES[i]));
        }
        return c;
    }

    private static String stateColumn(String state) {
        return switch (state) {
            case "WAIT_PRE" -> "wait_pre_cnt";
            case "WAIT_ACCEPT" -> "wait_accept_cnt";
            case "EXECUTING" -> "executing_cnt";
            case "SUSPENDED" -> "suspended_cnt";
            case "REVIEWING" -> "reviewing_cnt";
            case "REVIEW_REJECTED" -> "review_rejected_cnt";
            case "CLOSED" -> "closed_cnt";
            case "ARCHIVED" -> "archived_cnt";
            case "CANCELLED" -> "cancelled_cnt";
            default -> throw new IllegalArgumentException("未知状态: " + state);
        };
    }

    /** 流转正常率分子/分母：排除打回/暂停/作废的在办与终态正常单据。 */
    private static long[] rateParts(long[] c) {
        long normal = c[0] + c[1] + c[2] + c[4] + c[6] + c[7];
        long total = c[0] + c[1] + c[2] + c[3] + c[4] + c[5] + c[6] + c[7] + c[8];
        return new long[]{normal, total};
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round(10000.0 * numerator / denominator) / 100.0;
    }

    private static long longOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }

    private static String stringOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}

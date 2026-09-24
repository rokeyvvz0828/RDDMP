package com.ccb.datamigration.lifecycle.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.dashboard.model.OrderStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.StageActivityOrderView;
import com.ccb.datamigration.lifecycle.dashboard.model.TopicProgressOverview;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/** 数据看板服务聚焦测试（T10）：权限前置过滤 / 9 态之和=总数 / 时效对账闸门 / 专题独立归属。 */
class DashboardServiceTest {
    private static final AuthUser USER = new AuthUser(9, 1, "ljy", "hash", "李佳一", 11, true, "org", null);

    private StubJdbc jdbc;
    private LifecyclePermissionService permissions;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbc();
        permissions = Mockito.mock(LifecyclePermissionService.class);
        service = new DashboardService(jdbc, permissions);
    }

    @Test
    void orderStatusAdminRunsWithoutPermissionPredicate() {
        when(permissions.isAdmin(USER)).thenReturn(true);
        OrderStatusView view = service.orderStatus(USER);
        assertThat(view.orderTotal()).isEqualTo(8);
        assertThat(view.waitPreCnt() + view.waitAcceptCnt() + view.executingCnt() + view.suspendedCnt()
                + view.reviewingCnt() + view.reviewRejectedCnt() + view.closedCnt() + view.archivedCnt()
                + view.cancelledCnt()).isEqualTo(8);
        assertThat(view.deadlineOverdueCnt()).isEqualTo(1);
        // 管理员全量：聚合 SQL 不含执行人过滤
        assertThat(jdbc.lastOrderSql()).doesNotContain("current_executor_id");
    }

    @Test
    void orderStatusNonAdminAppliesDataScopePredicate() {
        when(permissions.isAdmin(USER)).thenReturn(false);
        service.orderStatus(USER);
        // 前置权限过滤（铁律 #8）：非管理员聚合 SQL 必须带执行人/参与人范围
        assertThat(jdbc.lastOrderSql()).contains("current_executor_id = ?");
        assertThat(jdbc.lastOrderSql()).contains("JSON_CONTAINS(COALESCE(o.participant_ids");
    }

    @Test
    void deadlineViewMismatchThrowsDashboardCode() {
        when(permissions.isAdmin(USER)).thenReturn(true);
        jdbc.deadlineMismatch = 1;
        assertThatThrownBy(() -> service.orderStatus(USER))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code())
                        .isEqualTo(LifecycleErrorCode.DASHBOARD_DEADLINE_VIEW_MISMATCH));
    }

    @Test
    void nineStateSumInvariantRejectsInconsistentRow() {
        assertThatThrownBy(() -> DashboardService.assertNineStateSum(10, 1, 1, 1, 1, 1, 1, 1, 1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("9 态分项之和与总数不一致");
        DashboardService.assertNineStateSum(9, 1, 1, 1, 1, 1, 1, 1, 1, 1);
    }

    @Test
    void stageActivityOnlyNormalActivitiesGroupedByStage() {
        when(permissions.isAdmin(USER)).thenReturn(true);
        jdbc.stageRows = List.of(stageRow(1, "PLAN", "规划分析", 101, "表结构梳理", "PROJECT"));
        List<StageActivityOrderView> stages = service.stageActivity(USER);
        assertThat(stages).hasSize(1);
        assertThat(stages.get(0).orderTotal()).isEqualTo(3);
        // 专题不归属生命周期阶段：SQL 仅统计普通活动
        assertThat(jdbc.lastSql).contains("a.activity_type = 'NORMAL'");
        assertThat(stages.get(0).activities().get(0).activityName()).isEqualTo("表结构梳理");
    }

    @Test
    void topicProgressAttributedViaTaskTopicActivityId() {
        when(permissions.isAdmin(USER)).thenReturn(true);
        jdbc.topicRows = List.of(topicRow(201, "核心系统迁移专题", "ACTIVE", 4));
        TopicProgressOverview overview = service.topicProgress(USER);
        assertThat(jdbc.lastSql).contains("LEFT JOIN task tk ON tk.topic_activity_id = t.id");
        assertThat(overview.topicTotal()).isEqualTo(1);
        assertThat(overview.taskedCnt()).isEqualTo(1);
        assertThat(overview.orderTotal()).isEqualTo(4);
        assertThat(overview.topics().get(0).closeRate()).isEqualTo(50.0);
    }

    private Map<String, Object> stageRow(long stageId, String stageCode, String stageName, long activityId,
                                         String activityName, String granularity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage_id", stageId);
        row.put("stage_code", stageCode);
        row.put("stage_name", stageName);
        row.put("activity_id", activityId);
        row.put("activity_name", activityName);
        row.put("granularity", granularity);
        row.put("order_total", 3L);
        row.put("wait_pre_cnt", 1L);
        row.put("wait_accept_cnt", 0L);
        row.put("executing_cnt", 1L);
        row.put("suspended_cnt", 0L);
        row.put("reviewing_cnt", 0L);
        row.put("review_rejected_cnt", 0L);
        row.put("closed_cnt", 1L);
        row.put("archived_cnt", 0L);
        row.put("cancelled_cnt", 0L);
        return row;
    }

    private Map<String, Object> topicRow(long topicId, String name, String status, long orderTotal) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("topic_id", topicId);
        row.put("activity_name", name);
        row.put("activity_status", status);
        row.put("order_total", orderTotal);
        row.put("wait_pre_cnt", 0L);
        row.put("wait_accept_cnt", 0L);
        row.put("executing_cnt", 2L);
        row.put("suspended_cnt", 0L);
        row.put("reviewing_cnt", 0L);
        row.put("review_rejected_cnt", 0L);
        row.put("closed_cnt", 2L);
        row.put("archived_cnt", 0L);
        row.put("cancelled_cnt", 0L);
        return row;
    }

    private final class StubJdbc extends JdbcTemplate {
        private int deadlineMismatch = 0;
        private List<Map<String, Object>> stageRows = new ArrayList<>();
        private List<Map<String, Object>> topicRows = new ArrayList<>();
        private String lastSql = "";
        private String orderSql = "";

        private String lastOrderSql() {
            return orderSql;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            lastSql = sql;
            if (sql.contains("v_order_deadline_live") && sql.contains("o.deadline_status <> v.deadline_status_live")) {
                return requiredType.cast(Integer.valueOf(deadlineMismatch));
            }
            throw new AssertionError("Unexpected queryForObject: " + sql);
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("SELECT id FROM dm_component")) {
                return List.of();
            }
            throw new AssertionError("Unexpected typed queryForList: " + sql);
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            lastSql = sql;
            if (sql.contains("FROM work_order o")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("order_total", 8L);
                row.put("wait_pre_cnt", 1L);
                row.put("wait_accept_cnt", 1L);
                row.put("executing_cnt", 1L);
                row.put("suspended_cnt", 0L);
                row.put("reviewing_cnt", 1L);
                row.put("review_rejected_cnt", 1L);
                row.put("closed_cnt", 1L);
                row.put("archived_cnt", 1L);
                row.put("cancelled_cnt", 1L);
                return row;
            }
            if (sql.contains("FROM issue i")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("total", 2L);
                row.put("wait_rectify_cnt", 1L);
                row.put("rectifying_cnt", 1L);
                row.put("closed_cnt", 0L);
                row.put("cancelled_cnt", 0L);
                return row;
            }
            if (sql.contains("FROM risk r")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("total", 3L);
                row.put("wait_prevent_cnt", 1L);
                row.put("preventing_cnt", 1L);
                row.put("avoided_cnt", 0L);
                row.put("occurred_cnt", 0L);
                row.put("closed_cnt", 1L);
                row.put("cancelled_cnt", 0L);
                row.put("level_high_cnt", 1L);
                row.put("level_mid_cnt", 1L);
                row.put("level_low_cnt", 1L);
                return row;
            }
            throw new AssertionError("Unexpected queryForMap: " + sql);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            lastSql = sql;
            if (sql.contains("SELECT id FROM dm_component")) {
                return List.of();
            }
            if (sql.contains("GROUP BY v.deadline_status_live")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("deadline_status_live", "OVERDUE");
                row.put("cnt", 1L);
                return List.of(row);
            }
            if (sql.contains("GROUP BY o.granularity")) {
                orderSql = sql;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("granularity", "PROJECT");
                row.put("order_total", 8L);
                row.put("wait_pre_cnt", 1L);
                row.put("wait_accept_cnt", 1L);
                row.put("executing_cnt", 1L);
                row.put("suspended_cnt", 0L);
                row.put("reviewing_cnt", 1L);
                row.put("review_rejected_cnt", 1L);
                row.put("closed_cnt", 1L);
                row.put("archived_cnt", 1L);
                row.put("cancelled_cnt", 1L);
                row.put("deadline_normal_cnt", 0L);
                row.put("deadline_near_cnt", 0L);
                row.put("deadline_overdue_cnt", 1L);
                return List.of(row);
            }
            if (sql.contains("GROUP BY s.id, s.stage_code, s.stage_name, a.id")) {
                orderSql = sql;
                return stageRows;
            }
            if (sql.contains("LEFT JOIN task tk ON tk.topic_activity_id = t.id")) {
                orderSql = sql;
                return topicRows;
            }
            if (sql.contains("GROUP BY a.activity_type, a.granularity")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("activity_type", "NORMAL");
                row.put("granularity", "PROJECT");
                row.put("total", 1L);
                row.put("active_cnt", 1L);
                row.put("inactive_cnt", 0L);
                row.put("obsolete_cnt", 0L);
                row.put("linked_topic_cnt", 0L);
                row.put("unlinked_topic_cnt", 1L);
                return List.of(row);
            }
            throw new AssertionError("Unexpected queryForList: " + sql);
        }
    }
}

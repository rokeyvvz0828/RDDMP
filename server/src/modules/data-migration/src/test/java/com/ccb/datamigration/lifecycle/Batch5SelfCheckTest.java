package com.ccb.datamigration.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.ccb.datamigration.lifecycle.error.LifecycleErrorCatalog;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 批次5 自检（T10 数据看板）：V164 迁移结构 / 错误码 113 条 1:1 / 前端契约 / 下钻路由。 */
class Batch5SelfCheckTest {

    @Test
    void dashboardMigrationRegistersDwsViewsMenuPermissions() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V164__data_migration_lifecycle_dashboard.sql");
        assertThat(Files.exists(migration)).as("V164 迁移缺失").isTrue();
        String sql = Files.readString(migration);
        // 六大维度聚合视图（16.3 数据层表清单）
        for (String view : new String[]{"dws_stage_activity_order", "dws_activity_status", "dws_topic_progress",
                "dws_issue_status", "dws_risk_status", "dws_order_status"}) {
            assertThat(sql).contains("CREATE OR REPLACE VIEW " + view);
        }
        // 维度 1：仅普通活动归属阶段（专题独立不归属）
        assertThat(sql).contains("a.activity_type = 'NORMAL'");
        // 维度 3：专题经 task.topic_activity_id 归属拆分工单
        assertThat(sql).contains("LEFT JOIN task tk ON tk.topic_activity_id = t.id");
        // 维度 6：直接消费实时时效视图 + 仅「在办工单」参与（9 态 − 终态）
        assertThat(sql).contains("JOIN v_order_deadline_live v");
        assertThat(sql).contains("o.order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED')");
        // 菜单 753 挂 745 + 只读权限点 7531
        assertThat(sql).contains("(753, 1, 745");
        assertThat(sql).contains("(7531, 1, 753, 'read', 'data-migration-lifecycle:dashboard'");
    }

    @Test
    void dashboardErrorCodeRegisteredOneToOne() {
        assertThat(LifecycleErrorCode.DASHBOARD_DEADLINE_VIEW_MISMATCH).isEqualTo(45801);
        assertThat(LifecycleErrorCatalog.message(LifecycleErrorCode.DASHBOARD_DEADLINE_VIEW_MISMATCH))
                .isNotBlank().contains("不一致");
        // 批次1-4 112 条 + 批次5 看板 1 条
        assertThat(LifecycleErrorCatalog.all()).hasSize(113);
    }

    @Test
    void frontendContractsRegistered() throws Exception {
        Path types = Path.of("../../../../web/src/types/data-migration-lifecycle.ts");
        assertThat(Files.exists(types)).as("前端类型契约缺失").isTrue();
        String ts = Files.readString(types);
        for (String view : new String[]{"LifecycleStageActivityOrderView", "LifecycleActivityStatusView",
                "LifecycleTopicProgressOverview", "LifecycleTopicProgressView", "LifecycleOrderStatusView",
                "LifecycleIssueStatusView", "LifecycleRiskStatusView", "LifecycleGranularityOrderStatus"}) {
            assertThat(ts).contains(view);
        }
        assertThat(ts).contains("45801");
        assertThat(ts).contains("看板实时时效视图与工单冗余字段不一致");

        Path api = Path.of("../../../../web/src/api/data-migration-lifecycle.ts");
        String apiTs = Files.readString(api);
        for (String fn : new String[]{"getDashboardStageActivity", "getDashboardActivityStatus",
                "getDashboardTopicProgress", "getDashboardIssueStatus", "getDashboardRiskStatus",
                "getDashboardOrderStatus"}) {
            assertThat(apiTs).contains(fn);
        }

        Path router = Path.of("../../../../web/src/router/index.ts");
        String routerTs = Files.readString(router);
        assertThat(routerTs).contains("lifecycle/dashboard");
        assertThat(routerTs).contains("data-migration-lifecycle-dashboard");
    }

    @Test
    void deadlineNormalizationAlignedWithLiveView() throws Exception {
        Path sm = Path.of("src/main/java/com/ccb/datamigration/lifecycle/order/OrderStateMachineService.java");
        String java = Files.readString(sm);
        // 终态/暂停/豁免的冗余时效字段与 v_order_deadline_live 强制 NORMAL 口径一致（时效对账前提）
        assertThat(java).contains("order_status = 'SUSPENDED', deadline_status = 'NORMAL'");
        assertThat(java).contains("order_status = 'ARCHIVED', deadline_status = 'NORMAL'");
        assertThat(java).contains("order_status = 'CANCELLED', deadline_status = 'NORMAL'");
        assertThat(java).contains("derived.equals(\"CLOSED\") ? \"deadline_status = 'NORMAL', \" : \"\"");
    }
}

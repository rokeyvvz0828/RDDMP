package com.ccb.datamigration.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.datamigration.lifecycle.enums.LifecycleReachabilityRegistry;
import com.ccb.datamigration.lifecycle.enums.LifecycleReachabilityRegistry.Evidence;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCatalog;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 批次3 自检（T4 任务发布 + T5 流转引擎 + T6 反馈）：迁移结构 / 错误码 71 条 1:1 / 可达性翻转 / 铁律约束。 */
class Batch3SelfCheckTest {

    @Test
    void taskMigrationRegistersTablesViewMenuPermissions() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V158__data_migration_lifecycle_task.sql");
        assertTrue(Files.exists(migration), "V158 迁移缺失");
        String sql = Files.readString(migration);
        for (String table : new String[]{"task", "task_snapshot", "task_person_rel", "topic_aggregate_edge"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "V158 缺少表 " + table);
        }
        assertTrue(sql.contains("(747, 1, 745"));
        for (String permissionId : new String[]{"(7471,", "(7472,", "(7473,", "(7474,"}) {
            assertTrue(sql.contains(permissionId), "V158 缺少权限点 " + permissionId);
        }
        // 任务-工单快照互不覆盖（铁律 #15）：工单级执行快照在 V159，任务级快照仅聚合包
        assertTrue(sql.contains("snapshot_json JSON NOT NULL"));
        assertTrue(sql.contains("topic_activity_ids JSON NULL"));
        assertTrue(sql.contains("aggregate_edges JSON NULL"));
    }

    @Test
    void orderMigrationRegistersTablesViewMenuChecks() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V159__data_migration_lifecycle_order.sql");
        assertTrue(Files.exists(migration), "V159 迁移缺失");
        String sql = Files.readString(migration);
        for (String table : new String[]{"work_order", "order_process_instance", "order_status_log", "order_transfer_log",
                "order_suspend_log", "order_sla_log", "order_restart_log", "notification"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "V159 缺少表 " + table);
        }
        // V159 同时承载任务-工单聚合视图（依赖 work_order，随订单表落地）与时效实时视图
        assertTrue(sql.contains("CREATE OR REPLACE VIEW v_task_order_progress"));
        assertTrue(sql.contains("CREATE OR REPLACE VIEW v_order_deadline_live"));
        assertTrue(sql.contains("(748, 1, 745"));
        for (String permissionId : new String[]{"(7481,", "(7482,", "(7483,", "(7484,"}) {
            assertTrue(sql.contains(permissionId), "V159 缺少权限点 " + permissionId);
        }
        // 表级 CHECK 兜底（11.4）：暂停态必须携带暂停前状态 / 终态带闭环时间 / LOCKED 无解锁时间
        assertTrue(sql.contains("ck_order_suspended_has_before"));
        assertTrue(sql.contains("ck_order_closed_at"));
        assertTrue(sql.contains("ck_opi_unlock"));
        assertTrue(sql.contains("ck_opi_closed"));
        assertTrue(sql.contains("ck_opi_exit_gate"));
        assertTrue(sql.contains("ck_opi_deliverable_gate"));
        // 时效三档独立视图（18.3）
        assertTrue(sql.contains("sla_exempt = 1"));
        assertTrue(sql.contains("INTERVAL 24 HOUR"));
    }

    @Test
    void feedbackMigrationRegistersTablesMenuPermissions() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V160__data_migration_lifecycle_feedback.sql");
        assertTrue(Files.exists(migration), "V160 迁移缺失");
        String sql = Files.readString(migration);
        for (String table : new String[]{"process_feedback", "work_log", "feedback_attachment", "process_issue_report",
                "process_risk_report"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "V160 缺少表 " + table);
        }
        assertTrue(sql.contains("(749, 1, 745"));
        for (String permissionId : new String[]{"(7491,", "(7492,", "(7493,", "(7494,"}) {
            assertTrue(sql.contains(permissionId), "V160 缺少权限点 " + permissionId);
        }
        // 问题/风险独立台账，预留被动同步字段（批次4 消费）
        assertTrue(sql.contains("process_issue_report"));
        assertTrue(sql.contains("process_risk_report"));
        assertTrue(sql.contains("synced_issue_id"));
        assertTrue(sql.contains("synced_risk_id"));
    }

    @Test
    void batch3ErrorCodesAreDocumentedOneToOneAtTotal71() {
        assertThat(LifecycleErrorCatalog.all()).hasSize(113); // 批次3 71 + 批次4 41 + 批次5 1;
        // 任务发布域 11 条（45201-45211）
        int[] taskCodes = {LifecycleErrorCode.TASK_ACTIVITY_NOT_ACTIVE, LifecycleErrorCode.TASK_PROJECT_COMPONENT_FORBIDDEN,
                LifecycleErrorCode.TASK_COMPONENT_REQUIRED, LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE,
                LifecycleErrorCode.TASK_COMPONENT_DISABLED, LifecycleErrorCode.TASK_PLAN_FINISH_TIME_REQUIRED,
                LifecycleErrorCode.TASK_PLAN_FINISH_TIME_IN_PAST, LifecycleErrorCode.TASK_ACTIVITY_SNAPSHOT_MISSING,
                LifecycleErrorCode.TASK_EXECUTOR_INACTIVE, LifecycleErrorCode.TASK_NOT_FOUND,
                LifecycleErrorCode.TASK_TERMINAL_READONLY};
        // 流转引擎域 15 条（45301-45315）
        int[] orderCodes = {LifecycleErrorCode.ORDER_NOT_FOUND, LifecycleErrorCode.ORDER_STATUS_IMMUTABLE,
                LifecycleErrorCode.ORDER_NO_ACTIVE_FLOW, LifecycleErrorCode.ORDER_SUSPEND_BEFORE_MISSING,
                LifecycleErrorCode.ORDER_TRANSFER_REASON_REQUIRED, LifecycleErrorCode.ORDER_TRANSFER_SAME_MEMBER,
                LifecycleErrorCode.ORDER_RESTART_NOT_ALLOWED, LifecycleErrorCode.ORDER_SLA_REASON_REQUIRED,
                LifecycleErrorCode.ORDER_ARCHIVE_BLOCKED, LifecycleErrorCode.ORDER_READONLY_STATE,
                LifecycleErrorCode.ORDER_PROCESS_NOT_FOUND, LifecycleErrorCode.ORDER_PRECONDITION_OPEN,
                LifecycleErrorCode.ORDER_DELIVERABLE_MISSING, LifecycleErrorCode.ORDER_AUDIT_REJECTED,
                LifecycleErrorCode.ORDER_SUBMIT_ALREADY_REVIEWING};
        // 反馈域 12 条（45401-45412）
        int[] feedbackCodes = {LifecycleErrorCode.FEEDBACK_READONLY_STATE, LifecycleErrorCode.FEEDBACK_NO_AUDIT_STAGE,
                LifecycleErrorCode.FEEDBACK_DUPLICATE_SUBMIT, LifecycleErrorCode.FEEDBACK_PROCESS_LOCKED,
                LifecycleErrorCode.FEEDBACK_CLOSED_LOCKED, LifecycleErrorCode.FEEDBACK_EXECUTOR_ONLY,
                LifecycleErrorCode.FEEDBACK_PROGRESS_REQUIRED, LifecycleErrorCode.FEEDBACK_EXIT_REQUIRED,
                LifecycleErrorCode.FEEDBACK_DELIVERABLE_REQUIRED, LifecycleErrorCode.FEEDBACK_ISSUE_FIELDS_REQUIRED,
                LifecycleErrorCode.FEEDBACK_RISK_FIELDS_REQUIRED, LifecycleErrorCode.FEEDBACK_ADMIN_FORBIDDEN};
        for (int code : taskCodes) {
            assertThat(LifecycleErrorCatalog.message(code)).as("任务域错误码 %d 缺文案", code).isNotBlank();
        }
        for (int code : orderCodes) {
            assertThat(LifecycleErrorCatalog.message(code)).as("流转域错误码 %d 缺文案", code).isNotBlank();
        }
        for (int code : feedbackCodes) {
            assertThat(LifecycleErrorCatalog.message(code)).as("反馈域错误码 %d 缺文案", code).isNotBlank();
        }
    }

    @Test
    void batch3StatusesAreFlippedFromNotYetAuthorized() {
        String orderCategory = "工单整体状态（9 态，3.2）";
        String processCategory = "工序节点状态（5 态，3.3）";
        String auditCategory = "审核状态（4 态，3.7）";
        String timingCategory = "时效标记（3 档，3.8）";
        for (String code : new String[]{"WAIT_PRE", "WAIT_ACCEPT", "EXECUTING", "SUSPENDED", "REVIEWING",
                "REVIEW_REJECTED", "CLOSED", "ARCHIVED", "CANCELLED"}) {
            assertThat(LifecycleReachabilityRegistry.evidenceOf(orderCategory, code))
                    .as("工单态 %s 应已可达", code).isNotEqualTo(Evidence.NOT_YET_AUTHORIZED);
        }
        for (String code : new String[]{"LOCKED", "EXECUTING", "REVIEWING", "REJECTED", "CLOSED"}) {
            assertThat(LifecycleReachabilityRegistry.evidenceOf(processCategory, code))
                    .as("工序态 %s 应已可达", code).isNotEqualTo(Evidence.NOT_YET_AUTHORIZED);
        }
        for (String code : new String[]{"WAIT_REVIEW", "PASSED", "REJECTED", "RECHECK"}) {
            assertThat(LifecycleReachabilityRegistry.evidenceOf(auditCategory, code))
                    .as("审核态 %s 应已可达", code).isNotEqualTo(Evidence.NOT_YET_AUTHORIZED);
        }
        for (String code : new String[]{"NORMAL", "NEAR_OVERDUE", "OVERDUE"}) {
            assertThat(LifecycleReachabilityRegistry.evidenceOf(timingCategory, code))
                    .as("时效档 %s 应已可达", code).isNotEqualTo(Evidence.NOT_YET_AUTHORIZED);
        }
        // 批次1/2 状态仍为未授权（问题/风险/活动/工序配置由批次2/4 登记）
        assertThat(LifecycleReachabilityRegistry.evidenceOf("活动状态（3 态，3.4）", "ACTIVE"))
                .isEqualTo(Evidence.NOT_YET_AUTHORIZED);
    }

    @Test
    void batch3MigrationsAreFreeOfBooleanStatusColumns() throws Exception {
        Path dir = Path.of("../../platform/infrastructure/src/main/resources/db/migration");
        for (String file : new String[]{"V158__data_migration_lifecycle_task.sql",
                "V159__data_migration_lifecycle_order.sql", "V160__data_migration_lifecycle_feedback.sql"}) {
            String sql = Files.readString(dir.resolve(file)).toLowerCase();
            for (String column : new String[]{"`is_closed`", "`is_voided`", "`is_rejected`", "`is_passed`",
                    "`is_finished`", "`is_completed`", "`is_active`", "`is_enabled`", "`is_archived`",
                    "`is_disabled`", "`is_obsolete`"}) {
                assertThat(sql).as("%s 含状态布尔字段 %s（铁律 #2）", file, column).doesNotContain(column);
            }
        }
    }
}

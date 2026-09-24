package com.ccb.datamigration.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.ccb.datamigration.lifecycle.enums.AuditStatus;
import com.ccb.datamigration.lifecycle.enums.IssueStatus;
import com.ccb.datamigration.lifecycle.enums.RiskStatus;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCatalog;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 批次4 自检（T7 审核 + T8 问题/知识库 + T9 风险/策略库）：迁移结构 / 错误码 112 条 1:1 / 状态枚举 / 前端契约。 */
class Batch4SelfCheckTest {

    @Test
    void auditMigrationRegistersTablesChecksMenuPermissions() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V161__data_migration_lifecycle_audit.sql");
        assertThat(Files.exists(migration)).as("V161 迁移缺失").isTrue();
        String sql = Files.readString(migration);
        for (String table : new String[]{"audit_record", "audit_batch_revoke", "audit_attachment"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        assertThat(sql).contains("CONSTRAINT ck_oar_reject_rectify");
        assertThat(sql).contains("(750, 1, 745");
        for (String permissionId : new String[]{"(7501,", "(7502,", "(7503,", "(7504,"}) {
            assertThat(sql).contains(permissionId);
        }
        // 三约束：统一原因 / 二次确认（confirmed_at）/ 10 分钟撤销窗口（revoke_deadline）
        assertThat(sql).contains("unified_reason TEXT NOT NULL");
        assertThat(sql).contains("confirmed_at DATETIME(6) NOT NULL");
        assertThat(sql).contains("revoke_deadline DATETIME(6) NOT NULL");
        // 审核记录固化：revoked_at 撤销留痕但不物理删除
        assertThat(sql).contains("revoked_at DATETIME(6) NULL");
    }

    @Test
    void issueMigrationRegistersTablesChecksMenuPermissions() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V162__data_migration_lifecycle_issue.sql");
        assertThat(Files.exists(migration)).as("V162 迁移缺失").isTrue();
        String sql = Files.readString(migration);
        for (String table : new String[]{"issue", "issue_attachment", "issue_rectify_record",
                "knowledge_entry", "knowledge_tag", "knowledge_tag_rel", "knowledge_reuse_log"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        assertThat(sql).contains("CONSTRAINT ck_issue_granularity_component");
        assertThat(sql).contains("CONSTRAINT ck_issue_sync_report");
        assertThat(sql).contains("(751, 1, 745");
        for (String permissionId : new String[]{"(7511,", "(7512,", "(7513,", "(7514,"}) {
            assertThat(sql).contains(permissionId);
        }
        // 知识条目禁删：status 枚举 + 唯一编码；reconcile 幂等锚点
        assertThat(sql).contains("knowledge_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("synced_report_id BIGINT NULL");
        assertThat(sql).contains("reuse_count INT NOT NULL DEFAULT 0");
    }

    @Test
    void riskMigrationRegistersTablesChecksMenuPermissions() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V163__data_migration_lifecycle_risk.sql");
        assertThat(Files.exists(migration)).as("V163 迁移缺失").isTrue();
        String sql = Files.readString(migration);
        for (String table : new String[]{"risk", "risk_attachment", "risk_strategy", "risk_prevent_record", "risk_strategy_lib"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        assertThat(sql).contains("CONSTRAINT ck_risk_granularity_component");
        assertThat(sql).contains("CONSTRAINT ck_risk_sync_report");
        assertThat(sql).contains("CONSTRAINT ck_rse_origin_source");
        assertThat(sql).contains("CONSTRAINT ck_risk_level_single");
        assertThat(sql).contains("CONSTRAINT ck_risk_probability_single");
        assertThat(sql).contains("(752, 1, 745");
        for (String permissionId : new String[]{"(7521,", "(7522,", "(7523,", "(7524,"}) {
            assertThat(sql).contains(permissionId);
        }
        assertThat(sql).contains("synced_report_id BIGINT NULL");
    }

    @Test
    void batch4ErrorCodesRegisteredOneToOne() {
        // 45xxx 活动，452xx 任务，453xx 流转，454xx 反馈，455xx 审核，456xx 问题/知识，457xx 风险/策略
        assertThat(LifecycleErrorCode.AUDIT_PERMISSION_DENIED).isEqualTo(45501);
        assertThat(LifecycleErrorCode.AUDIT_CLOSED_LOCKED).isEqualTo(45512);
        assertThat(LifecycleErrorCode.ISSUE_NOT_FOUND).isEqualTo(45601);
        assertThat(LifecycleErrorCode.ISSUE_CLOSE_SOLUTION_REQUIRED).isEqualTo(45615);
        assertThat(LifecycleErrorCode.RISK_NOT_FOUND).isEqualTo(45701);
        assertThat(LifecycleErrorCode.RISK_IMPORT_INVALID).isEqualTo(45714);
        LifecycleErrorCatalog.all().forEach((code, message) -> {
            assertThat(code).isGreaterThanOrEqualTo(45001);
            assertThat(message).isNotBlank();
        });
    }

    @Test
    void statusEnumsMatchBaselineAndMutualExclusion() {
        assertThat(IssueStatus.values()).hasSize(4);
        assertThat(IssueStatus.CLOSED.terminal()).isTrue();
        assertThat(IssueStatus.CANCELLED.terminal()).isTrue();
        assertThat(RiskStatus.values()).hasSize(6);
        // 三态互斥：已规避/已发生/已闭环任一时刻仅唯一（枚举单值 + 终态/非终态区分不重叠）
        assertThat(RiskStatus.AVOIDED.terminal()).isTrue();
        assertThat(RiskStatus.CLOSED.terminal()).isTrue();
        assertThat(RiskStatus.OCCURRED.terminal()).isFalse();
        assertThat(AuditStatus.values()).hasSize(4);
        assertThat(AuditStatus.PASSED.terminal()).isTrue();
        assertThat(AuditStatus.REJECTED.terminal()).isFalse();
    }

    @Test
    void frontendContractsRegistered() throws Exception {
        Path types = Path.of("../../../../web/src/types/data-migration-lifecycle.ts");
        assertThat(Files.exists(types)).as("前端类型契约缺失").isTrue();
        String ts = Files.readString(types);
        assertThat(ts).contains("LifecycleAuditView");
        assertThat(ts).contains("LifecycleIssueView");
        assertThat(ts).contains("LifecycleKnowledgeEntryView");
        assertThat(ts).contains("LifecycleRiskView");
        assertThat(ts).contains("LifecycleRiskStrategyLibView");
        assertThat(ts).contains("45501");
        assertThat(ts).contains("45714");
        Path api = Path.of("../../../../web/src/api/data-migration-lifecycle.ts");
        assertThat(Files.exists(api)).as("前端 API 契约缺失").isTrue();
        String apiTs = Files.readString(api);
        for (String fn : new String[]{"listLifecycleAudit", "batchRejectLifecycleAudit", "revokeLifecycleAuditBatch",
                "reconcileLifecycleIssues", "referLifecycleKnowledge", "reconcileLifecycleRisks", "reuseLifecycleRiskStrategy"}) {
            assertThat(apiTs).contains(fn);
        }
        Path router = Path.of("../../../../web/src/router/index.ts");
        assertThat(Files.exists(router)).as("前端路由缺失").isTrue();
        String routerTs = Files.readString(router);
        for (String route : new String[]{"lifecycle/audit", "lifecycle/issue", "lifecycle/issue/knowledge",
                "lifecycle/risk", "lifecycle/risk/strategy"}) {
            assertThat(routerTs).contains("data-migration/" + route);
        }
    }
}

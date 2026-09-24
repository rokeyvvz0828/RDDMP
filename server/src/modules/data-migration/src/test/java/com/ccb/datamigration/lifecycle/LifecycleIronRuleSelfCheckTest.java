package com.ccb.datamigration.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.enums.ActivityStatus;
import com.ccb.datamigration.lifecycle.enums.AuditStatus;
import com.ccb.datamigration.lifecycle.enums.IssueStatus;
import com.ccb.datamigration.lifecycle.enums.LifecycleReachabilityRegistry;
import com.ccb.datamigration.lifecycle.enums.LifecycleStatusCatalog;
import com.ccb.datamigration.lifecycle.enums.LifecycleValidationPhase;
import com.ccb.datamigration.lifecycle.enums.ProcessConfigStatus;
import com.ccb.datamigration.lifecycle.enums.ProcessNodeStatus;
import com.ccb.datamigration.lifecycle.enums.RiskStatus;
import com.ccb.datamigration.lifecycle.enums.TimelinessMark;
import com.ccb.datamigration.lifecycle.enums.WorkOrderStatus;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCatalog;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.snapshot.LifecycleSnapshotEnvelope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LifecycleIronRuleSelfCheckTest {
    private static List<String> entryCodes(List<LifecycleStatusCatalog.Category> categories, int index) {
        return categories.get(index).entries().stream().map(LifecycleStatusCatalog.StatusEntry::code).toList();
    }

    @Test
    void statusCatalogMatchesChapter3Baseline() {
        List<LifecycleStatusCatalog.Category> categories = LifecycleStatusCatalog.all();
        assertThat(categories).hasSize(8);
        assertThat(entryCodes(categories, 0)).containsExactly("WAIT_PRE", "WAIT_ACCEPT", "EXECUTING", "SUSPENDED",
                "REVIEWING", "REVIEW_REJECTED", "CLOSED", "ARCHIVED", "CANCELLED");
        assertThat(entryCodes(categories, 1)).containsExactly("LOCKED", "EXECUTING", "REVIEWING", "REJECTED", "CLOSED");
        assertThat(entryCodes(categories, 2)).containsExactly("ACTIVE", "INACTIVE", "OBSOLETE");
        assertThat(entryCodes(categories, 3)).containsExactly("WAIT_RECTIFY", "RECTIFYING", "CLOSED", "CANCELLED");
        assertThat(entryCodes(categories, 4)).containsExactly("WAIT_PREVENT", "PREVENTING", "AVOIDED", "OCCURRED", "CLOSED", "CANCELLED");
        assertThat(entryCodes(categories, 5)).containsExactly("WAIT_REVIEW", "PASSED", "REJECTED", "RECHECK");
        assertThat(entryCodes(categories, 6)).containsExactly("NORMAL", "NEAR_OVERDUE", "OVERDUE");
        assertThat(entryCodes(categories, 7)).containsExactly("DRAFT", "READY");
    }

    @Test
    void enumDisplayNamesAreOneToOneWithinEachCategory() {
        for (LifecycleStatusCatalog.Category category : LifecycleStatusCatalog.all()) {
            Set<String> codes = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (LifecycleStatusCatalog.StatusEntry entry : category.entries()) {
                assertThat(codes.add(entry.code())).as("类别内枚举值重复: %s", entry.code()).isTrue();
                assertThat(names.add(entry.displayName())).as("类别内文案重复: %s", entry.displayName()).isTrue();
            }
        }
    }

    @Test
    void primaryStatusTerminalFlagsMatchChapter3() {
        assertThat(WorkOrderStatus.ARCHIVED.terminal()).isTrue();
        assertThat(WorkOrderStatus.CANCELLED.terminal()).isTrue();
        assertThat(WorkOrderStatus.CLOSED.terminal()).isFalse();
        assertThat(ProcessNodeStatus.CLOSED.terminal()).isTrue();
        assertThat(ActivityStatus.OBSOLETE.terminal()).isTrue();
        assertThat(ActivityStatus.ACTIVE.terminal()).isFalse();
        assertThat(IssueStatus.CLOSED.terminal()).isTrue();
        assertThat(IssueStatus.CANCELLED.terminal()).isTrue();
        assertThat(RiskStatus.AVOIDED.terminal()).isTrue();
        assertThat(RiskStatus.OCCURRED.terminal()).isFalse();
        assertThat(RiskStatus.CLOSED.terminal()).isTrue();
        assertThat(AuditStatus.PASSED.terminal()).isTrue();
        assertThat(AuditStatus.REJECTED.terminal()).isFalse();
    }

    @Test
    void auxiliaryMarksAreNotBusinessStates() {
        for (TimelinessMark mark : TimelinessMark.values()) {
            assertThat(mark.terminal()).isFalse();
        }
        for (ProcessConfigStatus status : ProcessConfigStatus.values()) {
            assertThat(status.terminal()).isFalse();
        }
    }

    @Test
    void errorCatalogIsOneToOneAndDocumented() {
        var messages = LifecycleErrorCatalog.all();
        assertThat(messages).hasSize(71);
        Set<Integer> codes = new HashSet<>();
        Set<String> texts = new HashSet<>();
        messages.forEach((code, message) -> {
            assertThat(codes.add(code)).as("错误码重复: %d", code).isTrue();
            assertThat(texts.add(message)).as("错误码文案重复: %s", message).isTrue();
            assertThat(LifecycleErrorCatalog.message(code)).isEqualTo(message);
        });
    }

    @Test
    void errorCodesDoNotCollideWithSharedErrorCode() {
        Set<Integer> shared = Set.of(ErrorCode.BAD_REQUEST, ErrorCode.UNAUTHORIZED, ErrorCode.FORBIDDEN,
                ErrorCode.CONFLICT, ErrorCode.INTERNAL_ERROR);
        LifecycleErrorCatalog.all().keySet().forEach(code -> {
            assertThat(code).isGreaterThanOrEqualTo(45001);
            assertThat(shared.contains(code)).isFalse();
        });
    }

    @Test
    void validationOrderIsExistenceThenQuantityThenState() {
        LifecycleValidationPhase[] phases = LifecycleValidationPhase.values();
        assertThat(phases).containsExactly(LifecycleValidationPhase.EXISTENCE,
                LifecycleValidationPhase.QUANTITY, LifecycleValidationPhase.STATE);
    }

    @Test
    void snapshotEnvelopeIsSelfDescribingWithVersion() {
        LocalDateTime frozenAt = LocalDateTime.of(2026, 9, 23, 10, 0);
        LifecycleSnapshotEnvelope envelope = LifecycleSnapshotEnvelope.freeze("ACTIVITY", 42L, frozenAt);
        assertThat(envelope.schemaVersion()).isEqualTo(LifecycleSnapshotEnvelope.SCHEMA_VERSION);
        assertThat(envelope.entityType()).isEqualTo("ACTIVITY");
        assertThat(envelope.entityId()).isEqualTo(42L);
        assertThat(envelope.frozenAt()).isEqualTo(frozenAt);
    }

    @Test
    void everyCatalogStatusIsRegisteredForReachability() {
        for (LifecycleStatusCatalog.Category category : LifecycleStatusCatalog.all()) {
            for (LifecycleStatusCatalog.StatusEntry entry : category.entries()) {
                assertThat(LifecycleReachabilityRegistry.isRegistered(category.name(), entry.code()))
                        .as("死枚举未登记: %s/%s", category.name(), entry.code()).isTrue();
                assertThat(LifecycleReachabilityRegistry.reasonOf(category.name(), entry.code()))
                        .as("死枚举未写明原因: %s/%s", category.name(), entry.code()).isNotBlank();
            }
        }
    }

    @Test
    void lifecycleMigrationsAreFreeOfBooleanStatusColumns() throws Exception {
        Path dir = Path.of("../../platform/infrastructure/src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir));
        List<String> forbidden = List.of("is_closed", "is_voided", "is_rejected", "is_passed",
                "is_finished", "is_completed", "is_active", "is_enabled", "is_archived", "is_disabled", "is_obsolete");
        List<Path> lifecycleMigrations;
        try (var stream = Files.list(dir)) {
            lifecycleMigrations = stream
                    .filter(path -> path.getFileName().toString().matches("V1\\d+__data_migration_lifecycle.*\\.sql"))
                    .sorted()
                    .toList();
        }
        assertThat(lifecycleMigrations).isNotEmpty();
        for (Path migration : lifecycleMigrations) {
            String sql = Files.readString(migration).toLowerCase();
            String fileName = migration.getFileName().toString();
            for (String column : forbidden) {
                assertThat(sql.contains("`" + column + "`"))
                        .as("%s 含状态布尔字段 %s（铁律 #2）", fileName, column).isFalse();
            }
        }
    }
}

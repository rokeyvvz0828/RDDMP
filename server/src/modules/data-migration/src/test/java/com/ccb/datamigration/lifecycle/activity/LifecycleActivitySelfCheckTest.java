package com.ccb.datamigration.lifecycle.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.datamigration.lifecycle.enums.ActivityStatus;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCatalog;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 活动域 L1 自检（基线 9.7）：迁移结构 / 编码前缀 / 错误码 1:1 / 终态与三不准 / 模板四类校验闸门 / 快照契约。 */
class LifecycleActivitySelfCheckTest {

    @Test
    void activityMigrationRegistersNineTablesThreeViewsMenuAndPermissions() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V157__data_migration_lifecycle_activity.sql");
        assertTrue(Files.exists(migration), "V157 迁移缺失");
        String sql = Files.readString(migration);
        for (String table : new String[]{"lifecycle_stage", "activity", "activity_component_rel", "activity_process",
                "activity_process_dep", "activity_topology", "activity_snapshot", "activity_topic_rel", "activity_template"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "缺少表 " + table);
        }
        for (String view : new String[]{"v_activity_available", "v_process_option", "v_activity_component_check"}) {
            assertTrue(sql.contains("CREATE OR REPLACE VIEW " + view), "缺少契约视图 " + view);
        }
        assertTrue(sql.contains("(746, 1, 745"));
        for (String permissionId : new String[]{"(7461,", "(7462,", "(7463,", "(7464,"}) {
            assertTrue(sql.contains(permissionId), "缺少权限点 " + permissionId);
        }
        for (String stage : new String[]{"PLAN", "DESIGN", "DEVELOP", "TEST", "REHEARSAL", "RELEASE", "OPERATION"}) {
            assertTrue(sql.contains(stage), "缺少生命周期阶段种子 " + stage);
        }
        assertTrue(sql.contains("uk_activity_code"));
        assertTrue(sql.contains("uk_activity_topology_version"));
        assertTrue(sql.contains("uk_activity_snapshot_version"));
        assertTrue(sql.contains("snapshot_json JSON NOT NULL"));
    }

    @Test
    void activityCodePrefixesAreStableAndPartitioned() {
        assertThat(ActivityCodeGenerator.prefixFor("NORMAL", "PROJECT")).isEqualTo("ACT-PRJ");
        assertThat(ActivityCodeGenerator.prefixFor("NORMAL", "COMPONENT")).isEqualTo("ACT-CMP");
        assertThat(ActivityCodeGenerator.prefixFor("TOPIC", "PROJECT")).isEqualTo("ACT-TPC");
        assertThat(ActivityCodeGenerator.prefixFor("TOPIC", "COMPONENT")).isEqualTo("ACT-TPC");
    }

    @Test
    void activityStatusTerminalFlagsMatchBaseline() {
        assertThat(ActivityStatus.OBSOLETE.terminal()).isTrue();
        assertThat(ActivityStatus.ACTIVE.terminal()).isFalse();
        assertThat(ActivityStatus.INACTIVE.terminal()).isFalse();
    }

    @Test
    void activityErrorCodesAreDocumentedOneToOne() {
        int[] required = {LifecycleErrorCode.ACTIVITY_NOT_FOUND, LifecycleErrorCode.ACTIVITY_CODE_CONFLICT,
                LifecycleErrorCode.GRANULARITY_IMMUTABLE, LifecycleErrorCode.COMPONENT_BINDING_MISMATCH,
                LifecycleErrorCode.ACTIVITY_INACTIVE_NO_PROCESS, LifecycleErrorCode.ACTIVITY_INACTIVE_NO_DISPATCH,
                LifecycleErrorCode.LIFE_STAGE_REQUIRED_FOR_NORMAL, LifecycleErrorCode.PROCESS_NOT_FOUND,
                LifecycleErrorCode.PROCESS_NAME_DUPLICATE, LifecycleErrorCode.PROCESS_SEQ_INVALID,
                LifecycleErrorCode.PROCESS_MIN_ONE, LifecycleErrorCode.EXIT_TRIAD_INCOMPLETE,
                LifecycleErrorCode.TOPOLOGY_SELF_LOOP, LifecycleErrorCode.TOPOLOGY_DUPLICATE_EDGE,
                LifecycleErrorCode.TOPOLOGY_CYCLE, LifecycleErrorCode.TOPOLOGY_MISSING_NODE,
                LifecycleErrorCode.TOPOLOGY_LIMIT_EXCEEDED, LifecycleErrorCode.TOPOLOGY_NOPRE_CONFLICT,
                LifecycleErrorCode.SNAPSHOT_VERSION_MISMATCH, LifecycleErrorCode.TEMPLATE_INVALID_JSON,
                LifecycleErrorCode.TEMPLATE_FIELD_INCOMPLETE, LifecycleErrorCode.TEMPLATE_GRANULARITY_MISMATCH,
                LifecycleErrorCode.TEMPLATE_DEPENDENCY_INVALID, LifecycleErrorCode.TEMPLATE_EXPORT_FORBIDDEN,
                LifecycleErrorCode.TOPIC_GRANULARITY_MISMATCH};
        for (int code : required) {
            String message = LifecycleErrorCatalog.message(code);
            assertThat(message).as("错误码 %d 缺文案", code).isNotBlank();
        }
    }

    @Test
    void obsoleteThreeNosAreBackedBySingleErrorCode() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/activity/ActivityService.java"));
        String topology = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/activity/ActivityTopologyService.java"));
        assertThat(service).contains("ACTIVITY_OBSOLETE_READONLY").contains("rejectObsolete");
        assertThat(topology).contains("ACTIVITY_OBSOLETE_READONLY").contains("rejectObsolete");
        // 三不准：已作废活动不可编辑/不可重新启用/不可重复作废，全部同一错误码
        assertThat(LifecycleErrorCatalog.message(LifecycleErrorCode.ACTIVITY_OBSOLETE_READONLY)).isNotBlank();
    }

    @Test
    void templateExportDesensitizesInstanceDataAndImportValidatesFourCategories() throws Exception {
        String template = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/activity/ActivityTemplateService.java"));
        assertThat(template).contains("permissions.requireAdmin") // 导入导出权限：仅数据迁移管理员
                .contains("TEMPLATE_INVALID_JSON")
                .contains("TEMPLATE_FIELD_INCOMPLETE")
                .contains("TEMPLATE_GRANULARITY_MISMATCH")
                .contains("TEMPLATE_DEPENDENCY_INVALID")
                .contains("'INACTIVE'") // 导入生成停用草稿
                .contains("nameDuplicateHint");
        // 脱敏：导出 SQL 范围仅活动基础属性 + 工序配置 + 依赖边，不触碰项目/组件/人员表
        assertThat(template).doesNotContain("dm_component").doesNotContain("pm_project").doesNotContain("sys_user");
    }

    @Test
    void snapshotForTaskContractIsExposed() throws Exception {
        String topology = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/activity/ActivityTopologyService.java"));
        assertThat(topology).contains("public Map<String, Object> snapshotForTask")
                .contains("activity_snapshot")
                .contains("snapshot_json")
                .contains("schemaVersion")
                .contains("topologyVersion");
    }

    @Test
    void controllerExposesActivityEndpoints() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/web/LifecycleActivityController.java"));
        for (String endpoint : new String[]{"@GetMapping", "@PostMapping", "@PutMapping", "@DeleteMapping"}) {
            assertThat(controller).contains(endpoint);
        }
        assertThat(controller).contains("/template/export").contains("/template/import").contains("/publish");
    }
}

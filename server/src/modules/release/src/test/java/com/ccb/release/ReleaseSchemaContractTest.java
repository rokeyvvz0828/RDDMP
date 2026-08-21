package com.ccb.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseSchemaContractTest {
    private static final String MIGRATION = "db/migration/V38__release_management.sql";
    private static final String REVISION_MIGRATION = "db/migration/V39__release_application_revision_history.sql";
    private static final String WORKFLOW_BINDING_MIGRATION = "db/migration/V43__release_workflow_scene_binding.sql";
    private static final String MENU_PERMISSION_MIGRATION = "db/migration/V45__release_menu_level_permissions.sql";
    private static final String FILE_MEDIA_MIGRATION = "db/migration/V46__release_file_media.sql";

    @Test
    void migrationOwnsRequiredReleaseTablesAndDoesNotSeedBusinessRows() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "V38 release migration must be available on the test classpath");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List.of(
                    "rel_release_window",
                    "rel_window_change_log",
                    "rel_release_application",
                    "rel_application_delivery",
                    "rel_application_requirement",
                    "rel_application_attachment",
                    "rel_application_round",
                    "rel_application_relation",
                    "rel_application_event",
                    "rel_production_entry",
                    "rel_production_result_log",
                    "rel_workflow_event_receipt"
            ).forEach(table -> assertTrue(sql.contains("CREATE TABLE " + table), table));

            assertTrue(sql.contains("uk_rel_window_code"));
            assertTrue(sql.contains("uk_rel_application_code"));
            assertTrue(sql.contains("uk_rel_workflow_receipt"));
            assertTrue(sql.contains("release:application:submit"));
            assertFalse(sql.matches("(?is).*INSERT\\s+INTO\\s+rel_(release_window|release_application|production_entry).*"));
        }
    }

    @Test
    void revisionMigrationPreservesEditableChildHistory() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(REVISION_MIGRATION)) {
            assertNotNull(input, "V39 revision migration must be available on the test classpath");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List.of("rel_application_delivery", "rel_application_requirement", "rel_application_attachment")
                    .forEach(table -> assertTrue(sql.contains("ALTER TABLE " + table), table));
            assertTrue(sql.contains("application_revision BIGINT NOT NULL DEFAULT 0"));
            assertTrue(sql.contains("active TINYINT(1) NOT NULL DEFAULT 1"));
            assertFalse(sql.matches("(?is).*DELETE\\s+FROM\\s+rel_application_.*"));
        }
    }

    @Test
    void workflowBindingMigrationOwnsAuditedProjectSceneBindings() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(WORKFLOW_BINDING_MIGRATION)) {
            assertNotNull(input, "V43 workflow binding migration must be available on the test classpath");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE TABLE rel_workflow_binding"));
            assertTrue(sql.contains("CREATE TABLE rel_workflow_binding_history"));
            assertTrue(sql.contains("uk_rel_workflow_binding_scene"));
            assertTrue(sql.contains("row_version BIGINT NOT NULL DEFAULT 0"));
            assertTrue(sql.contains("release:workflow-config:view"));
            assertTrue(sql.contains("release:workflow-config:update"));
            assertTrue(sql.contains("MODIFY workflow_code VARCHAR(96) NULL"));
        }
    }

    @Test
    void menuPermissionMigrationSplitsSixReleaseMenusAndPreservesExistingGrants() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MENU_PERMISSION_MIGRATION)) {
            assertNotNull(input, "V45 release menu permission migration must be available on the test classpath");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List.of(
                    "/release/windows",
                    "/release/applications",
                    "/release/production-baseline",
                    "/release/production-versions",
                    "/release/analytics",
                    "/release/workflow-bindings",
                    "release:baseline:view",
                    "release:baseline:update",
                    "release:production-version:view"
            ).forEach(value -> assertTrue(sql.contains(value), value));
            assertTrue(sql.contains("SELECT role_id, 6016, tenant_id"));
            assertTrue(sql.contains("p.menu_id BETWEEN 610 AND 615"));
            assertTrue(sql.contains("SET status = 0 WHERE tenant_id = 1 AND id = 6001"));
            assertTrue(sql.contains("LEFT JOIN sys_role_menu child_rm"));
            assertFalse(sql.contains("NOT EXISTS (\n      SELECT 1\n      FROM sys_role_menu child"));
            assertFalse(sql.matches("(?is).*UPDATE\\s+rel_.*"));
        }
    }

    @Test
    void fileMediaMigrationIntroducesStableItemIdentityWithoutDeletingBusinessRows() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(FILE_MEDIA_MIGRATION)) {
            assertNotNull(input, "V46 file-media migration must be available on the test classpath");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            List.of("rel_application_delivery", "rel_application_relation", "rel_production_entry")
                    .forEach(table -> assertTrue(sql.contains("ALTER TABLE " + table), table));
            List.of("item_type", "file_path", "item_key")
                    .forEach(column -> assertTrue(sql.contains(column), column));
            assertTrue(sql.contains("MODIFY COLUMN artifact_version VARCHAR(128) NULL"));
            assertTrue(sql.contains("CONCAT('UNIT:', delivery_unit_code)"));
            assertTrue(sql.contains("uk_rel_application_delivery (tenant_id, application_id, item_key, application_revision)"));
            assertTrue(sql.contains("idx_rel_delivery_conflict (tenant_id, item_key, active, application_id)"));
            assertTrue(sql.contains("uk_rel_application_relation (tenant_id, application_id, related_application_id, item_key, relation_type)"));
            assertTrue(sql.contains("uk_rel_production_source (tenant_id, window_id, application_id, item_key)"));
            assertFalse(sql.matches("(?is).*DELETE\\s+FROM\\s+rel_.*"));
        }
    }
}

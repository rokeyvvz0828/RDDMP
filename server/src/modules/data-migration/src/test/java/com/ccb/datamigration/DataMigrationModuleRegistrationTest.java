package com.ccb.datamigration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataMigrationModuleRegistrationTest {
    @Test
    void moduleRegistrationIsBackedByV84() {
        assertTrue(Files.exists(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V84__data_migration_component_enrichment.sql")));
    }

    @Test
    void targetTableMigrationAndServiceUseCurrentProjectContract() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V88__data_migration_target_table_structure.sql");
        Path service = Path.of("src/main/java/com/ccb/datamigration/service/TargetTableService.java");
        assertTrue(Files.exists(migration));
        String migrationSql = Files.readString(migration);
        String source = Files.readString(service);
        assertTrue(migrationSql.contains("pm_project"));
        assertTrue(source.contains("pm_project"));
        assertTrue(!source.contains("dm_project"));
        assertTrue(source.contains("ensureTableUnique(user.tenantId()"));
        assertTrue(source.contains("normalizedTableNameEn"));
        assertTrue(source.contains("if (createTableSafely"));
        assertTrue(source.contains("validateImportFields(fields)"));
    }

    @Test
    void categoryPermissionsAreEnforcedInServiceLayer() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/TargetTableService.java"));
        assertTrue(source.contains("requireCategoryPermission(user, cat, \"create\")"));
        assertTrue(source.contains("requireCategoryPermission(user, cat, \"update\")"));
        assertTrue(source.contains("requireCategoryPermission(user, cat, \"delete\")"));
        assertTrue(Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/DataMigrationPermissionService.java"))
                .contains("table-fields-"));
    }

    @Test
    void migrationRestoresRuntimeTablesAndImportPreservesAuthorizationErrors() throws Exception {
        String migration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V84__data_migration_component_enrichment.sql"));
        String controller = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/TargetTableController.java"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS dm_asset"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS dm_operation_log"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS dm_dashboard_snapshot"));
        assertTrue(controller.contains("catch (IOException ex)"));
        assertTrue(!controller.contains("catch (Exception ex)"));
    }

    @Test
    void assetWriteEndpointsRequireWriteOrManageAuthority() throws Exception {
        String asset = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/AssetController.java"));
        String structured = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/StructuredAssetController.java"));
        assertTrue(asset.contains("/assets/{type}/upload") && asset.contains("data-migration:write"));
        assertTrue(asset.contains("/assets/delete") && asset.contains("data-migration:write"));
        assertTrue(asset.contains("/recycle-bin/restore") && asset.contains("data-migration:manage"));
        assertTrue(structured.contains("/structured/{type}/import") && structured.contains("data-migration:write"));
        assertTrue(structured.contains("/structured/{type}/delete") && structured.contains("data-migration:write"));
    }
}

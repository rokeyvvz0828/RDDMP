package com.ccb.datamigration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataMigrationModuleRegistrationTest {
    @Test
    void moduleRegistrationIsBackedByV35() {
        assertTrue(Files.exists(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V35__data_migration_asset_library_v3.sql")));
    }
}

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import java.util.zip.CRC32;

public final class NormalizeFlywayHistory {
    private static final int LEGACY_MENU_V156 = 1191321843;
    private static final int LEGACY_LOCAL_V200 = 91870892;
    private static final Set<String> AUDIT_COLUMNS = Set.of(
        "operator_name", "module_code", "module_name", "operation_type", "target_type", "target_id",
        "project_id", "project_name", "http_status", "duration_ms", "user_agent", "changed_fields"
    );

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 2 && "--apply".equals(args[0]);
        Path migrations = Path.of(args[apply ? 1 : 0]);
        int canonicalV156 = checksum(migrations.resolve("V156__platform_operation_audit.sql"));
        int canonicalV200 = checksum(migrations.resolve("V200__rename_release_drill_plan_menu.sql"));
        var env = System.getenv();
        try (Connection connection = DriverManager.getConnection(
                required(env, "DB_URL"), required(env, "DB_USERNAME"), required(env, "DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            if (apply) backupHistory(connection);
            normalizeV156(connection, canonicalV156, apply);
            normalizeV200(connection, canonicalV200, apply);
            if (apply) connection.commit(); else connection.rollback();
            System.out.println(apply ? "NORMALIZATION_APPLIED" : "NORMALIZATION_DRY_RUN_OK");
        }
    }

    private static void backupHistory(Connection connection) throws Exception {
        if (count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history_req071_backup'") != 0) {
            throw new IllegalStateException("REQ071 history backup already exists; refusing overwrite");
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE flyway_schema_history_req071_backup LIKE flyway_schema_history");
            statement.executeUpdate("INSERT INTO flyway_schema_history_req071_backup SELECT * FROM flyway_schema_history");
        }
        if (count(connection, "SELECT COUNT(*) FROM flyway_schema_history_req071_backup") != count(connection, "SELECT COUNT(*) FROM flyway_schema_history")) {
            throw new IllegalStateException("REQ071 history backup row count mismatch");
        }
    }

    private static void normalizeV156(Connection connection, int canonicalChecksum, boolean apply) throws Exception {
        Migration row = migration(connection, "156");
        if (row == null || row.checksum == canonicalChecksum) return;
        if (row.checksum != LEGACY_MENU_V156 || !row.script.contains("rename_release_drill_plan_menu")) {
            throw new IllegalStateException("Unknown V156 history; refusing normalization");
        }
        int columns = count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_operation_log' AND column_name IN (" + quoted(AUDIT_COLUMNS) + ")");
        int indexes = count(connection, "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='sys_operation_log' AND index_name IN ('idx_sys_operation_log_project_created','idx_sys_operation_log_type_created')");
        if (columns == 0 && indexes == 0) {
            execute(connection, "DELETE FROM flyway_schema_history WHERE installed_rank=?", row.rank, apply);
            return;
        }
        if (columns != AUDIT_COLUMNS.size() || indexes != 2) {
            throw new IllegalStateException("Partial audit schema detected; refusing normalization");
        }
        execute(connection, "UPDATE flyway_schema_history SET description='platform operation audit', script='V156__platform_operation_audit.sql', checksum=? WHERE installed_rank=?", canonicalChecksum, row.rank, apply);
    }

    private static void normalizeV200(Connection connection, int canonicalChecksum, boolean apply) throws Exception {
        Migration row = migration(connection, "200");
        if (row == null || row.checksum == canonicalChecksum) return;
        if (row.checksum != LEGACY_LOCAL_V200 || !row.script.contains("local_preserved_schema_compatibility")) {
            throw new IllegalStateException("Unknown V200 history; refusing normalization");
        }
        execute(connection, "DELETE FROM flyway_schema_history WHERE installed_rank=?", row.rank, apply);
    }

    private static Migration migration(Connection connection, String version) throws Exception {
        try (var statement = connection.prepareStatement("SELECT installed_rank, script, checksum FROM flyway_schema_history WHERE version=? AND success=1")) {
            statement.setString(1, version);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return null;
                Migration migration = new Migration(result.getInt(1), result.getString(2), result.getInt(3));
                if (result.next()) throw new IllegalStateException("Multiple successful migrations found for V" + version);
                return migration;
            }
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void execute(Connection connection, String sql, Object first, boolean apply) throws Exception {
        if (!apply) return;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, first);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("History normalization affected an unexpected row count");
        }
    }

    private static void execute(Connection connection, String sql, Object first, Object second, boolean apply) throws Exception {
        if (!apply) return;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, first);
            statement.setObject(2, second);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("History normalization affected an unexpected row count");
        }
    }

    private static int checksum(Path file) throws Exception {
        CRC32 crc = new CRC32();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == '\ufeff') line = line.substring(1);
                crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        return (int) crc.getValue();
    }

    private static String quoted(Set<String> values) {
        return values.stream().sorted().map(value -> "'" + value + "'").reduce((a, b) -> a + "," + b).orElseThrow();
    }

    private static String required(java.util.Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private record Migration(int rank, String script, int checksum) {}
}

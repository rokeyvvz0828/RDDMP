import java.util.Map;
import org.flywaydb.core.Flyway;

public class LocalMigration {
    public static void main(String[] args) {
        try {
            var env = System.getenv();
            var flyway = Flyway.configure()
                .dataSource(env.get("DB_URL"), env.get("DB_USERNAME"), env.get("DB_PASSWORD"))
                .locations("filesystem:" + args[1])
                .outOfOrder(true)
                .placeholders(Map.of("bootstrap_admin_password_hash", env.get("BOOTSTRAP_ADMIN_PASSWORD_HASH")))
                .load();
            if ("migrate".equals(args[0])) {
                var result = flyway.migrate();
                System.out.println("MIGRATIONS_EXECUTED=" + result.migrationsExecuted);
            } else {
                var result = flyway.validateWithResult();
                System.out.println("VALIDATION_SUCCESS=" + result.validationSuccessful);
                for (var invalid : result.invalidMigrations) {
                    System.out.println("INVALID_VERSION=" + invalid.version + ";CODE=" + invalid.errorDetails.errorCode);
                }
                if (!result.validationSuccessful) System.exit(2);
            }
        } catch (Exception error) {
            System.out.println("MIGRATION_FAILED=" + error.getClass().getSimpleName());
            System.exit(1);
        }
    }
}

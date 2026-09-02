package com.ccb.architecture.change.number;

import com.ccb.architecture.change.model.SubsystemNumberReleaseReason;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;
import com.ccb.architecture.change.persistence.JdbcSubsystemNumberReservationStore;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JdbcSubsystemNumberReservationStoreMySqlTest {
    private static final String DATABASE = "architecture_number_store";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    private FixedPrefixIncrementalSubsystemNumberStrategy strategy;
    private JdbcSubsystemNumberReservationStore store;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("93"))
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void clearStatics() {
        transactions = null;
        jdbc = null;
        dataSource = null;
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM arch_subsystem_number_reservation");
        jdbc.update("DELETE FROM arch_subsystem_number_recycled");
        jdbc.update("DELETE FROM arch_subsystem_change_application");
        jdbc.update("DELETE FROM arch_physical_subsystem");
        jdbc.update("DELETE FROM arch_logical_subsystem");
        jdbc.update("DELETE FROM arch_subsystem_number_namespace");
        jdbc.update("INSERT INTO arch_subsystem_number_namespace (allocation_scope, namespace_code, next_ordinal) VALUES (0, 'LOGICAL', 1)");
        store = new JdbcSubsystemNumberReservationStore(jdbc);
        strategy = new FixedPrefixIncrementalSubsystemNumberStrategy(store);
    }

    @Test
    void rejectsReserveReleaseAndConsumeWithoutActualTransaction() {
        insertApplication(101, 1);
        SubsystemNumberRequest request = SubsystemNumberRequest.logical(1, 101);
        SubsystemNumberReservation reservation = SubsystemNumberReservation.unformatted(request, 1);

        assertThatThrownBy(() -> strategy.reserve(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("真实数据库事务");
        assertThatThrownBy(() -> store.release(reservation, SubsystemNumberReleaseReason.REJECTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("真实数据库事务");
        assertThatThrownBy(() -> store.consume(reservation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("真实数据库事务");
        assertThatThrownBy(() -> store.isPublishedCodeOccupied("A0001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("真实数据库事务");
    }

    @Test
    void keepsApplicationLineIdempotentAndAllocatesGloballyAcrossTenants() throws Exception {
        insertApplication(201, 1);
        SubsystemNumberReservation first = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 201)));
        SubsystemNumberReservation repeated = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 201)));
        assertThat(repeated).isEqualTo(first);
        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_number_reservation WHERE application_id = 201")).isEqualTo(1);

        resetData();
        insertApplication(202, 1);
        insertApplication(203, 2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> tenantOne = executor.submit(() -> {
                await(start);
                return inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 202)).code());
            });
            Future<String> tenantTwo = executor.submit(() -> {
                await(start);
                return inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(2, 203)).code());
            });
            start.countDown();
            assertThat(Set.of(tenantOne.get(20, TimeUnit.SECONDS), tenantTwo.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("A0001", "A0002");
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_number_reservation")).isEqualTo(2);
        assertThat(integer("SELECT next_ordinal FROM arch_subsystem_number_namespace WHERE namespace_code = 'LOGICAL'"))
                .isEqualTo(3);
    }

    @Test
    void reusesSmallestReleasedOrdinalKeepsReturnedAndNeverRecyclesConsumed() {
        for (long applicationId = 301; applicationId <= 306; applicationId++) {
            insertApplication(applicationId, 1);
        }
        SubsystemNumberReservation one = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 301)));
        SubsystemNumberReservation two = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 302)));
        inTransaction(() -> strategy.release(two, SubsystemNumberReleaseReason.CANCELLED));
        inTransaction(() -> strategy.release(one, SubsystemNumberReleaseReason.REJECTED));

        SubsystemNumberReservation reusedOne = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 303)));
        SubsystemNumberReservation returned = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 303)));
        SubsystemNumberReservation reusedTwo = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 304)));
        assertThat(reusedOne.code()).isEqualTo("A0001");
        assertThat(returned).isEqualTo(reusedOne);
        assertThat(reusedTwo.code()).isEqualTo("A0002");

        inTransaction(() -> strategy.consume(reusedOne));
        inTransaction(() -> strategy.consume(reusedOne));
        SubsystemNumberReservation next = inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 305)));
        assertThat(next.code()).isEqualTo("A0003");
        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_number_recycled")).isZero();
    }

    @Test
    void skipsPublishedCodeAcrossTenantsIncludingDeletedHistory() {
        jdbc.update("""
                INSERT INTO arch_logical_subsystem
                    (id, tenant_id, code, short_name, name, business_org_id, contact_user_id,
                     deleted, created_by, updated_by)
                VALUES (401, 2, 'A0001', '历史逻辑', '历史逻辑', 1, 1, 1, 1, 1)
                """);
        insertApplication(402, 1);

        SubsystemNumberReservation reservation = inTransaction(
                () -> strategy.reserve(SubsystemNumberRequest.logical(1, 402)));

        assertThat(reservation.code()).isEqualTo("A0002");
        assertThat(integer("SELECT next_ordinal FROM arch_subsystem_number_namespace WHERE namespace_code = 'LOGICAL'"))
                .isEqualTo(3);
    }

    @Test
    void formatsPhysicalBoundaryAndFailsCapacityWithoutPartialReservation() {
        for (long applicationId = 501; applicationId <= 504; applicationId++) {
            insertApplication(applicationId, 1);
        }
        jdbc.update("INSERT INTO arch_subsystem_number_namespace (allocation_scope, namespace_code, next_ordinal) VALUES (0, 'PHYSICAL:1', 9)");

        assertThat(inTransaction(() -> strategy.reserve(SubsystemNumberRequest.physical(1, 501, 1, 1))).code())
                .isEqualTo("W00019");
        assertThat(inTransaction(() -> strategy.reserve(SubsystemNumberRequest.physical(1, 502, 1, 1))).code())
                .isEqualTo("W0001A");
        jdbc.update("UPDATE arch_subsystem_number_namespace SET next_ordinal = 35 WHERE namespace_code = 'PHYSICAL:1'");
        assertThat(inTransaction(() -> strategy.reserve(SubsystemNumberRequest.physical(1, 503, 1, 1))).code())
                .isEqualTo("W0001Z");

        assertThatThrownBy(() -> inTransaction(
                () -> strategy.reserve(SubsystemNumberRequest.physical(1, 504, 1, 1))))
                .isInstanceOf(SubsystemNumberCapacityExceededException.class);
        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_number_reservation WHERE application_id = 504")).isZero();
        assertThat(integer("SELECT next_ordinal FROM arch_subsystem_number_namespace WHERE namespace_code = 'PHYSICAL:1'"))
                .isEqualTo(36);
    }

    @Test
    void transactionRollbackRestoresRecycledCandidateAndReservationState() {
        insertApplication(601, 1);
        insertApplication(602, 1);
        insertApplication(603, 1);
        SubsystemNumberReservation original = inTransaction(
                () -> strategy.reserve(SubsystemNumberRequest.logical(1, 601)));
        inTransaction(() -> strategy.release(original, SubsystemNumberReleaseReason.CANCELLED));

        transactions.execute(status -> {
            SubsystemNumberReservation transientReservation = strategy.reserve(SubsystemNumberRequest.logical(1, 602));
            assertThat(transientReservation.code()).isEqualTo("A0001");
            status.setRollbackOnly();
            return null;
        });

        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_number_reservation WHERE application_id = 602")).isZero();
        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_number_recycled WHERE ordinal = 1")).isEqualTo(1);
        assertThat(inTransaction(() -> strategy.reserve(SubsystemNumberRequest.logical(1, 603))).code())
                .isEqualTo("A0001");
    }

    private void insertApplication(long applicationId, long tenantId) {
        jdbc.update("""
                        INSERT INTO arch_subsystem_change_application
                            (id, tenant_id, target_kind, action_type, applicant_id, created_by, updated_by)
                        VALUES (?, ?, 'LOGICAL', 'CREATE', ?, ?, ?)
                        """, applicationId, tenantId, applicationId, applicationId, applicationId);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private void inTransaction(Runnable work) {
        transactions.executeWithoutResult(status -> work.run());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private int integer(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发编号测试未能同时启动");
        }
    }

    private static String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) {
                return candidate.toString();
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 Flyway 迁移目录");
    }
}

package com.ccb.architecture.service;

import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnit;
import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitQuery;
import com.ccb.architecture.model.DeliveryUnitModels.DeploymentUnitRef;
import com.ccb.architecture.persistence.DeliveryUnitNumberCapacityExceededException;
import com.ccb.architecture.persistence.DeliveryUnitStore;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 交付单元数据层 MySQL 集成测试：真实 MySQL 8.4 + 完整 Flyway 迁移。
 * 覆盖编号确定性、同物理子系统名称唯一、无方向关联、数据库层同物理子系统不变量、
 * 软删除与关联清理、分页筛选与项目隔离。
 */
@Testcontainers
class DeliveryUnitMySqlTest {
    private static final String DATABASE = "delivery_unit_store";
    private static final long TENANT_ID = 1L;
    private static final ProjectAccess PROJECT = new ProjectAccess(70L, "PROJECT-A", "项目 A");
    private static final ProjectAccess PROJECT_B = new ProjectAccess(71L, "PROJECT-B", "项目 B");
    private static final long PHYSICAL_ID = 501L;
    private static final long PHYSICAL_B_ID = 502L;
    private static final long DEPLOYMENT_UNIT_ID = 9501L;
    private static final long DEPLOYMENT_UNIT_SECOND_ID = 9502L;
    private static final long DEPLOYMENT_UNIT_OTHER_PHYSICAL_ID = 9503L;
    private static final long DEPLOYMENT_UNIT_OTHER_PROJECT_ID = 9504L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbc;
    private static DeliveryUnitStore store;
    private static TransactionTemplate transactions;
    private static final AtomicLong identifiers = new AtomicLong(700_000L);
    private static final AtomicInteger sequence = new AtomicInteger();

    private final AuthUser actor = new AuthUser(88L, TENANT_ID, "tech", "-", "技术架构师", 1L, true);

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("157"))
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("157"))
                .cleanDisabled(false)
                .load()
                .migrate();
        prepareDefaultProject();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .cleanDisabled(false)
                .load()
                .migrate();

        store = new DeliveryUnitStore(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static void prepareDefaultProject() {
        jdbc.update("DELETE FROM pm_project WHERE tenant_id = 1 AND "
                + "(project_code = 'RDDMP-PLATFORM' OR id = 990001)");
        jdbc.update("INSERT INTO pm_project (id, tenant_id, project_code, project_name, status, owner_id, "
                        + "created_by, deleted) VALUES (?, ?, 'RDDMP-PLATFORM', '交付单元数据层测试项目', "
                        + "'RUNNING', 1, 1, 0)", 990001L, 1L);
    }

    @BeforeEach
    void seedArchitecture() {
        jdbc.update("DELETE FROM arch_delivery_unit_deployment_unit");
        jdbc.update("DELETE FROM arch_delivery_unit");
        jdbc.update("DELETE FROM arch_delivery_unit_number_seq");
        jdbc.update("DELETE FROM arch_deployment_unit_relation_history");
        jdbc.update("DELETE FROM arch_deployment_unit_relation");
        jdbc.update("DELETE FROM arch_deployment_unit_version");
        jdbc.update("DELETE FROM arch_deployment_unit");
        jdbc.update("DELETE FROM arch_deployment_unit_number_seq");
        jdbc.update("DELETE FROM arch_physical_subsystem WHERE tenant_id = ?", TENANT_ID);
        insertPhysical(PHYSICAL_ID, PROJECT.id(), "W0001A", "渠道接入系统");
        insertPhysical(PHYSICAL_B_ID, PROJECT.id(), "W0002B", "支付通道系统");
        insertPhysical(503L, PROJECT_B.id(), "W0003C", "其他项目系统");
        insertDeploymentUnit(DEPLOYMENT_UNIT_ID, PROJECT.id(), PHYSICAL_ID, "DW0001A001", "PORTAL_AP");
        insertDeploymentUnit(DEPLOYMENT_UNIT_SECOND_ID, PROJECT.id(), PHYSICAL_ID, "DW0001A002", "AUTH_AP");
        insertDeploymentUnit(DEPLOYMENT_UNIT_OTHER_PHYSICAL_ID, PROJECT.id(), PHYSICAL_B_ID, "DW0002B001", "PAY_AP");
        insertDeploymentUnit(DEPLOYMENT_UNIT_OTHER_PROJECT_ID, PROJECT_B.id(), 503L, "DW0003C001", "EXTERNAL_AP");
    }

    @AfterAll
    static void clearStatics() {
        jdbc = null;
        store = null;
        transactions = null;
    }

    @Test
    void allocateNumberIsDeterministicAndNeverReusesOrdinals() {
        String first = store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A");
        String second = store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A");
        String otherPhysical = store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_B_ID, "W0002B");

        assertThat(first).isEqualTo("DUW0001A001");
        assertThat(second).isEqualTo("DUW0001A002");
        assertThat(otherPhysical).isEqualTo("DUW0002B001");
    }

    @Test
    void concurrentAllocationsProduceDistinctNumbers() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> codes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        List<String> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int index = 0; index < threads; index++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    codes.add(transactions.execute(status ->
                            store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A")));
                } catch (Exception exception) {
                    failures.add(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).as("并发编号失败明细").isEmpty();
        assertThat(codes).hasSize(threads).doesNotHaveDuplicates();
    }

    @Test
    void capacityExhaustionIsRejectedWithoutReusingOrdinals() {
        jdbc.update("INSERT INTO arch_delivery_unit_number_seq "
                        + "(tenant_id, project_id, physical_subsystem_id, next_ordinal) VALUES (?, ?, ?, 1000)",
                TENANT_ID, PROJECT.id(), PHYSICAL_ID);

        assertThatThrownBy(() -> store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A"))
                .isInstanceOf(DeliveryUnitNumberCapacityExceededException.class);
    }

    @Test
    void nameIsUniqueInsidePhysicalSubsystemAndReusableAcrossSubsystems() {
        store.insertUnit(nextId(), TENANT_ID, PROJECT.id(), "DUW0001A001", PHYSICAL_ID, "统一认证交付包",
                null, null, null, actor.id());

        assertThatThrownBy(() -> store.insertUnit(nextId(), TENANT_ID, PROJECT.id(), "DUW0001A002", PHYSICAL_ID,
                "统一认证交付包", null, null, null, actor.id()))
                .isInstanceOf(DuplicateKeyException.class);

        store.insertUnit(nextId(), TENANT_ID, PROJECT.id(), "DUW0002B001", PHYSICAL_B_ID, "统一认证交付包",
                null, null, null, actor.id());
        assertThat(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_B_ID, "统一认证交付包", null)).isTrue();
        assertThat(store.unitNameExists(TENANT_ID, PROJECT.id(), 503L, "统一认证交付包", null)).isFalse();
    }

    @Test
    void relationRowsCannotCrossPhysicalSubsystemEvenBypassingService() {
        long deliveryUnitId = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "统一认证交付包");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO arch_delivery_unit_deployment_unit "
                        + "(tenant_id, project_id, physical_subsystem_id, delivery_unit_id, deployment_unit_id, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                TENANT_ID, PROJECT.id(), PHYSICAL_ID, deliveryUnitId, DEPLOYMENT_UNIT_OTHER_PHYSICAL_ID, actor.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void relationsAreUndirectedAndVisibleFromBothSides() {
        long deliveryUnitId = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "统一认证交付包");
        store.replaceDeploymentUnits(TENANT_ID, PROJECT.id(), PHYSICAL_ID, deliveryUnitId,
                Set.of(DEPLOYMENT_UNIT_ID, DEPLOYMENT_UNIT_SECOND_ID), actor.id());

        List<DeploymentUnitRef> related = store.findRelatedDeploymentUnits(TENANT_ID, PROJECT.id(), deliveryUnitId);
        assertThat(related).extracting(DeploymentUnitRef::code)
                .containsExactlyInAnyOrder("DW0001A001", "DW0001A002");

        List<DeliveryUnit> reverse = store.findRelatedDeliveryUnits(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID);
        assertThat(reverse).extracting(DeliveryUnit::id).containsExactly(deliveryUnitId);
        assertThat(store.hasDeliveryUnitRelation(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID)).isTrue();
        assertThat(store.hasDeliveryUnitRelation(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_OTHER_PHYSICAL_ID)).isFalse();

        // 覆盖式更新只保留目标集合
        store.replaceDeploymentUnits(TENANT_ID, PROJECT.id(), PHYSICAL_ID, deliveryUnitId,
                Set.of(DEPLOYMENT_UNIT_SECOND_ID), actor.id());
        assertThat(store.findRelatedDeploymentUnits(TENANT_ID, PROJECT.id(), deliveryUnitId))
                .extracting(DeploymentUnitRef::id).containsExactly(DEPLOYMENT_UNIT_SECOND_ID);
        assertThat(store.hasDeliveryUnitRelation(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID)).isFalse();
    }

    @Test
    void softDeleteClearsRelationsAndHidesRecordWithoutTouchingDeploymentUnit() {
        long deliveryUnitId = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "统一认证交付包");
        store.replaceDeploymentUnits(TENANT_ID, PROJECT.id(), PHYSICAL_ID, deliveryUnitId,
                Set.of(DEPLOYMENT_UNIT_ID), actor.id());

        store.softDelete(TENANT_ID, PROJECT.id(), deliveryUnitId, actor.id());

        assertThat(store.findUnit(TENANT_ID, PROJECT.id(), deliveryUnitId)).isEmpty();
        assertThat(store.hasDeliveryUnitRelation(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID)).isFalse();
        assertThat(store.findRelatedDeliveryUnits(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID)).isEmpty();
        Integer deploymentUnits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ? AND id = ?",
                Integer.class, TENANT_ID, DEPLOYMENT_UNIT_ID);
        assertThat(deploymentUnits).isEqualTo(1);
    }

    @Test
    void pageUnitsFiltersAndIsolatesProjects() {
        insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "统一认证交付包");
        insertDeliveryUnit(PROJECT.id(), PHYSICAL_B_ID, "支付交付包");
        insertDeliveryUnit(PROJECT_B.id(), 503L, "外部交付包");

        PageResult<DeliveryUnit> all = store.pageUnits(TENANT_ID, PROJECT.id(), new PageQuery(1, 20),
                DeliveryUnitQuery.empty());
        assertThat(all.total()).isEqualTo(2);

        PageResult<DeliveryUnit> byPhysical = store.pageUnits(TENANT_ID, PROJECT.id(), new PageQuery(1, 20),
                new DeliveryUnitQuery(null, PHYSICAL_B_ID, null, null));
        assertThat(byPhysical.records()).extracting(DeliveryUnit::name).containsExactly("支付交付包");

        PageResult<DeliveryUnit> byName = store.pageUnits(TENANT_ID, PROJECT.id(), new PageQuery(1, 20),
                new DeliveryUnitQuery("认证交付", null, null, null));
        assertThat(byName.records()).extracting(DeliveryUnit::name).containsExactly("统一认证交付包");

        PageResult<DeliveryUnit> inactive = store.pageUnits(TENANT_ID, PROJECT.id(), new PageQuery(1, 20),
                new DeliveryUnitQuery(null, null, "INACTIVE", null));
        assertThat(inactive.total()).isZero();
    }

    @Test
    void updateContentRejectsStaleRowVersionAndStatusTransitionIsGuarded() {
        long deliveryUnitId = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "统一认证交付包");
        DeliveryUnit created = store.findUnit(TENANT_ID, PROJECT.id(), deliveryUnitId).orElseThrow();

        int updated = store.updateUnitContent(TENANT_ID, PROJECT.id(), deliveryUnitId, created.rowVersion(),
                "统一认证交付包 V2", null, "描述", "备注", actor.id());
        assertThat(updated).isEqualTo(1);

        int stale = store.updateUnitContent(TENANT_ID, PROJECT.id(), deliveryUnitId, created.rowVersion(),
                "统一认证交付包 V3", null, null, null, actor.id());
        assertThat(stale).isZero();

        assertThat(store.updateUnitStatus(TENANT_ID, PROJECT.id(), deliveryUnitId, "ACTIVE", "INACTIVE",
                actor.id())).isEqualTo(1);
        assertThat(store.updateUnitStatus(TENANT_ID, PROJECT.id(), deliveryUnitId, "ACTIVE", "INACTIVE",
                actor.id())).isZero();
    }

    @Test
    void deploymentUnitVoidGuardRejectsWhileDeliveryUnitRelationExists() {
        long deliveryUnitId = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "统一认证交付包");
        DeploymentUnitReferenceGuard guard = new DeploymentUnitReferenceGuard(
                List.of(new DeliveryUnitDeploymentUnitReferenceChecker(store)));

        // 没有交付单元关联时允许作废
        guard.requireClear(new com.ccb.architecture.integration.DeploymentUnitReferenceCheckRequest(
                TENANT_ID, DEPLOYMENT_UNIT_ID));

        store.replaceDeploymentUnits(TENANT_ID, PROJECT.id(), PHYSICAL_ID, deliveryUnitId,
                Set.of(DEPLOYMENT_UNIT_ID), actor.id());

        assertThatThrownBy(() -> guard.requireClear(
                new com.ccb.architecture.integration.DeploymentUnitReferenceCheckRequest(
                        TENANT_ID, DEPLOYMENT_UNIT_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("交付单元关联");

        // 交付单元软删除后恢复可作废
        store.softDelete(TENANT_ID, PROJECT.id(), deliveryUnitId, actor.id());
        guard.requireClear(new com.ccb.architecture.integration.DeploymentUnitReferenceCheckRequest(
                TENANT_ID, DEPLOYMENT_UNIT_ID));
    }

    @Test
    void relationsEditedFromDeploymentSideAreSymmetricAndDoNotPublishVersions() {
        long deliveryUnitId = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "统一认证交付包");
        int versionsBefore = countRows("arch_deployment_unit_version", "unit_id = " + DEPLOYMENT_UNIT_ID);

        store.replaceDeploymentUnitsFromDeploymentSide(TENANT_ID, PROJECT.id(), PHYSICAL_ID, DEPLOYMENT_UNIT_ID,
                Set.of(deliveryUnitId), actor.id());

        assertThat(store.findRelatedDeliveryUnits(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID))
                .extracting(DeliveryUnit::id).containsExactly(deliveryUnitId);
        assertThat(store.findRelatedDeploymentUnits(TENANT_ID, PROJECT.id(), deliveryUnitId))
                .extracting(DeploymentUnitRef::id).containsExactly(DEPLOYMENT_UNIT_ID);
        assertThat(store.hasDeliveryUnitRelation(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID)).isTrue();

        // 方案 A：部署单元侧的关联编辑不发布新版本，也不写关系变更历史
        assertThat(countRows("arch_deployment_unit_version", "unit_id = " + DEPLOYMENT_UNIT_ID))
                .isEqualTo(versionsBefore);
        assertThat(countRows("arch_deployment_unit_relation_history", null)).isZero();

        // 从部署单元侧清空后，交付单元侧也立即看不到
        store.replaceDeploymentUnitsFromDeploymentSide(TENANT_ID, PROJECT.id(), PHYSICAL_ID, DEPLOYMENT_UNIT_ID,
                Set.of(), actor.id());
        assertThat(store.findRelatedDeliveryUnits(TENANT_ID, PROJECT.id(), DEPLOYMENT_UNIT_ID)).isEmpty();
        assertThat(store.findRelatedDeploymentUnits(TENANT_ID, PROJECT.id(), deliveryUnitId)).isEmpty();
    }

    @Test
    void deliveryUnitCandidatesAreLimitedToActiveUnitsOfSamePhysicalSubsystem() {
        long active = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "启用交付包");
        long inactive = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "停用交付包");
        store.updateUnitStatus(TENANT_ID, PROJECT.id(), inactive, "ACTIVE", "INACTIVE", actor.id());
        long otherPhysical = insertDeliveryUnit(PROJECT.id(), PHYSICAL_B_ID, "其他子系统交付包");
        long deleted = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "已删除交付包");
        store.softDelete(TENANT_ID, PROJECT.id(), deleted, actor.id());

        PageResult<DeliveryUnit> page = store.searchActiveOptions(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "交付包",
                new PageQuery(1, 20));

        assertThat(page.records()).extracting(DeliveryUnit::id).containsExactly(active);
        assertThat(page.records()).extracting(DeliveryUnit::id).doesNotContain(inactive, otherPhysical, deleted);

        PageResult<DeliveryUnit> byKeyword = store.searchActiveOptions(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "启用",
                new PageQuery(1, 20));
        assertThat(byKeyword.records()).extracting(DeliveryUnit::id).containsExactly(active);
        assertThat(store.findDeliveryUnitsByIds(TENANT_ID, PROJECT.id(), List.of(active, otherPhysical)))
                .extracting(DeliveryUnit::id).containsExactlyInAnyOrder(active, otherPhysical);
        assertThat(store.findDeliveryUnitsByIds(TENANT_ID, PROJECT.id(), List.of(deleted))).isEmpty();
    }

    private static int countRows(String table, String where) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + (where == null ? "" : " WHERE " + where), Integer.class);
        return count == null ? 0 : count;
    }

    private long insertDeliveryUnit(long projectId, long physicalSubsystemId, String name) {
        return insertDeliveryUnit(projectId, physicalSubsystemId, name, null);
    }

    private long insertDeliveryUnit(long projectId, long physicalSubsystemId, String name, String artifactTypeCode) {
        long id = nextId();
        String code = "DU" + physicalSubsystemId + "00" + sequence.incrementAndGet();
        store.insertUnit(id, TENANT_ID, projectId, code, physicalSubsystemId, name, artifactTypeCode, null, null,
                actor.id());
        return id;
    }

    @Test
    void artifactTypeDictionaryIsSeededAndArtifactTypeIsOptionalPersistedAndFilterable() {
        Integer dictionaryCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_type "
                + "WHERE tenant_id = 1 AND dict_code = 'ARCH_ARTIFACT_TYPE' AND status = 1 AND deleted = 0",
                Integer.class);
        assertThat(dictionaryCount).isEqualTo(1);
        List<String> keys = jdbc.queryForList("SELECT config.config_key FROM sys_config config "
                + "JOIN sys_dict_type dict ON dict.id = config.category_id AND dict.tenant_id = config.tenant_id "
                + "WHERE config.tenant_id = 1 AND dict.dict_code = 'ARCH_ARTIFACT_TYPE' "
                + "AND config.status = 1 AND config.deleted = 0 ORDER BY config.id", String.class);
        assertThat(keys).containsExactly("architecture.artifact-type.container",
                "architecture.artifact-type.archive", "architecture.artifact-type.script");

        long withType = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "容器交付包",
                "architecture.artifact-type.container");
        long withoutType = insertDeliveryUnit(PROJECT.id(), PHYSICAL_ID, "无类型交付包");
        assertThat(store.findUnit(TENANT_ID, PROJECT.id(), withType).orElseThrow().artifactTypeCode())
                .isEqualTo("architecture.artifact-type.container");
        assertThat(store.findUnit(TENANT_ID, PROJECT.id(), withoutType).orElseThrow().artifactTypeCode()).isNull();

        PageResult<DeliveryUnit> filtered = store.pageUnits(TENANT_ID, PROJECT.id(), new PageQuery(1, 20),
                new DeliveryUnitQuery(null, null, null, "architecture.artifact-type.container"));
        assertThat(filtered.records()).extracting(DeliveryUnit::id).containsExactly(withType);

        DeliveryUnit created = store.findUnit(TENANT_ID, PROJECT.id(), withType).orElseThrow();
        assertThat(store.updateUnitContent(TENANT_ID, PROJECT.id(), withType, created.rowVersion(), "容器交付包",
                "architecture.artifact-type.script", null, null, actor.id())).isEqualTo(1);
        assertThat(store.findUnit(TENANT_ID, PROJECT.id(), withType).orElseThrow().artifactTypeCode())
                .isEqualTo("architecture.artifact-type.script");
    }

    private static long nextId() {
        return identifiers.incrementAndGet();
    }

    private static void insertPhysical(long id, long projectId, String code, String name) {
        jdbc.update("INSERT INTO arch_physical_subsystem "
                        + "(id, tenant_id, project_id, code, short_name, name, logical_subsystem_name,"
                        + " responsible_team_org_id, responsible_team_name_snapshot, status, row_version,"
                        + " created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, 1, '测试团队', 'ACTIVE', 0, 1, 1)",
                id, TENANT_ID, projectId, code, name, name);
    }

    private static void insertDeploymentUnit(long id, long projectId, long physicalSubsystemId, String code,
                                             String name) {
        jdbc.update("INSERT INTO arch_deployment_unit "
                        + "(id, tenant_id, project_id, code, physical_subsystem_id, name, kind, status,"
                        + " current_version, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'APPLICATION', 'ACTIVE', 1, 1, 1)",
                id, TENANT_ID, projectId, code, physicalSubsystemId, name);
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
        throw new IllegalStateException("未找到 Flyway 迁移目录");
    }
}

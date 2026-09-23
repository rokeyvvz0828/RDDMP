package com.ccb.datamigration.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.service.DataMigrationPermissionService;
import com.ccb.security.model.AuthUser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class LifecycleBaseModuleTest {
    private static final AuthUser USER = new AuthUser(9, 1, "ljy", "hash", "李佳一", 11, true, "org", null);

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private DataMigrationPermissionService dmPermissions;

    @Test
    void baseMigrationRegistersLifecycleMenusPermissionsAndContractViews() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V156__data_migration_lifecycle_base.sql");
        assertTrue(Files.exists(migration));
        String sql = Files.readString(migration);
        assertTrue(sql.contains("data-migration-lifecycle:access"));
        assertTrue(sql.contains("data-migration-lifecycle:manage"));
        assertTrue(sql.contains("data-migration-lifecycle:review"));
        assertTrue(sql.contains("data-migration-lifecycle:dashboard"));
        assertTrue(sql.contains("INSERT IGNORE INTO sys_menu"));
        assertTrue(sql.contains("INSERT IGNORE INTO sys_role_menu"));
        assertTrue(sql.contains("INSERT IGNORE INTO sys_role_permission"));
        assertTrue(sql.contains("v_data_migration_lifecycle_component_option"));
        assertTrue(sql.contains("v_data_migration_lifecycle_role_option"));
        assertTrue(sql.contains("c.deleted = 0"));
        assertTrue(sql.contains("status = 1 AND deleted = 0"));
    }

    @Test
    void memberAndComponentSelectorsReuseExistingActiveDataSource() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/LifecycleDataSourceService.java"));
        String migration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V156__data_migration_lifecycle_base.sql"));
        assertTrue(service.contains("referenceQuery.searchActiveUsers"));
        assertTrue(service.contains("v_data_migration_lifecycle_component_option"));
        assertTrue(migration.contains("JOIN pm_project"));
        assertTrue(migration.contains("owner_id"));
    }

    @Test
    void fourDataScopesAreEnforcedServerSide() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/LifecyclePermissionService.java"));
        assertTrue(service.contains("resolveScope"));
        assertTrue(service.contains("LifecycleDataScope"));
        assertTrue(service.contains("case ADMIN"));
        assertTrue(service.contains("case REVIEWER"));
        assertTrue(service.contains("case OWNER"));
        assertTrue(service.contains("case EXECUTOR"));
        assertTrue(service.contains("ErrorCode.FORBIDDEN"));
        assertTrue(service.contains("dm_component"));
        assertTrue(service.contains("pm_project"));
        assertTrue(service.contains("owner_id = ?"));
        assertTrue(service.contains("deleted = 0"));
    }

    @Test
    void auditSkeletonReusesDmOperationLog() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/LifecycleAuditService.java"));
        assertTrue(service.contains("dm_operation_log"));
        assertTrue(service.contains("actor_id"));
        assertTrue(service.contains("operation_code"));
        assertTrue(service.contains("opCode"));
    }

    @Test
    void optionsEndpointsRequireLifecycleAccessAuthority() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/ccb/datamigration/lifecycle/web/LifecycleOptionsController.java"));
        assertTrue(controller.contains("data-migration-lifecycle:access"));
        assertTrue(controller.contains("/options/members"));
        assertTrue(controller.contains("/options/components"));
        assertTrue(controller.contains("/options/roles"));
        assertTrue(controller.contains("ApiResponse"));
        assertTrue(controller.contains("TraceId.getOrCreate()"));
    }

    @Test
    void adminScopeCoversAllDataScopes() {
        when(dmPermissions.isAdmin(USER)).thenReturn(true);
        LifecycleDataScopeSet scope = new LifecyclePermissionService(jdbc, dmPermissions)
                .resolveScope(USER, null, null, null);
        assertThat(scope.admin()).isTrue();
        assertThat(scope.reviewer()).isTrue();
        assertThat(scope.executor()).isTrue();
        assertThat(scope.owner()).isFalse();
    }

    @Test
    void nonAdminWithoutPermissionsIsRejectedWithForbidden() {
        LifecyclePermissionService service = new LifecyclePermissionService(jdbc, dmPermissions);
        assertThatThrownBy(() -> service.requireAdmin(USER))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.requireDataScope(USER, LifecycleDataScope.REVIEWER, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(service.isReviewer(USER)).isFalse();
    }

    @Test
    void componentOwnerPassesOwnerScopeButNotAdmin() {
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM dm_component"), eq(Integer.class), any(), any(), any())).thenReturn(1);
        LifecyclePermissionService service = new LifecyclePermissionService(jdbc, dmPermissions);
        assertThat(service.isOwnerOfComponent(USER, 42)).isTrue();
        service.requireDataScope(USER, LifecycleDataScope.OWNER, 42L, null, null);
        assertThat(service.isAdmin(USER)).isFalse();
        assertThatThrownBy(() -> service.requireDataScope(USER, LifecycleDataScope.ADMIN, 42L, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void executorScopeRequiresAssigneeIdentity() {
        LifecyclePermissionService service = new LifecyclePermissionService(jdbc, dmPermissions);
        service.requireDataScope(USER, LifecycleDataScope.EXECUTOR, null, null, USER.id());
        assertThatThrownBy(() -> service.requireDataScope(USER, LifecycleDataScope.EXECUTOR, null, null, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}

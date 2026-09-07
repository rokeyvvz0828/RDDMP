package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T32 项目隔离守卫行为测试（T32-r1 后口径）：projectId 必填、维护类操作恒取库中归属、
 * 项目可达性完全委托 platform/system 契约（未知项目 400、非成员 403），
 * 并且守卫自身不得再查询 {@code pm_project} / {@code pm_project_member}。
 */
class DataMigrationPermissionServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);
    private static final AuthUser ADMIN_USER = new AuthUser(9L, 1L, "rokey", "", "迁移管理员", 11L, true);
    private static final long PROJECT = 10L;

    /** 只服务 RBAC 查询的替身：记录 SQL，便于断言项目/成员表未被本模块读取。 */
    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final boolean admin;
        final List<String> queried = new ArrayList<>();

        private StubJdbcTemplate(boolean admin) {
            this.admin = admin;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queried.add(sql);
            if (sql.contains("FROM sys_user_role")) return (T) Integer.valueOf(admin ? 1 : 0);
            if (sql.contains("FROM sys_menu_permission")) return (T) Integer.valueOf(admin ? 1 : 0);
            throw new AssertionError("Unexpected query: " + sql);
        }
    }

    private static DataMigrationPermissionService service(StubJdbcTemplate jdbc, StubProjectAccess access) {
        return new DataMigrationPermissionService(jdbc, access);
    }

    private static BusinessException assertFailure(DataMigrationPermissionService service, Long projectId) {
        return assertThrows(BusinessException.class, () -> service.requireProject(projectId, USER));
    }

    @Test
    void missingOrNonPositiveProjectIsRejectedBeforeAnyDelegation() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(false);
        StubProjectAccess access = StubProjectAccess.allow();
        DataMigrationPermissionService service = service(jdbc, access);

        assertEquals(ErrorCode.BAD_REQUEST, assertFailure(service, null).code());
        assertEquals(ErrorCode.BAD_REQUEST, assertFailure(service, 0L).code());
        assertEquals(ErrorCode.BAD_REQUEST, assertFailure(service, -3L).code());
        // 必填判定必须先于任何数据库访问与平台调用，避免把“未选项目”退化成全项目扫描。
        assertTrue(jdbc.queried.isEmpty(), "requireProject must not query before validating projectId");
        assertTrue(access.checkedProjectIds().isEmpty(), "invalid projectId must not reach the platform contract");
    }

    @Test
    void unknownProjectIsRejectedByPlatformContract() {
        StubProjectAccess access = StubProjectAccess.withDecision(StubProjectAccess.Decision.PROJECT_NOT_FOUND);
        DataMigrationPermissionService service = service(new StubJdbcTemplate(true), access);

        assertEquals(ErrorCode.BAD_REQUEST, assertFailure(service, PROJECT).code());
        assertEquals(List.of(PROJECT), access.checkedProjectIds(), "项目可达性必须由平台契约判定");
    }

    @Test
    void nonMemberIsForbiddenByPlatformContract() {
        StubProjectAccess access = StubProjectAccess.withDecision(StubProjectAccess.Decision.NOT_MEMBER);
        DataMigrationPermissionService service = service(new StubJdbcTemplate(true), access);

        BusinessException error = assertFailure(service, PROJECT);
        assertEquals(ErrorCode.FORBIDDEN, error.code());
        assertEquals(List.of(PROJECT), access.checkedProjectIds());
    }

    @Test
    void activeMemberPassesAndReturnsBindableProjectWithoutProjectSql() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(false);
        StubProjectAccess access = StubProjectAccess.allow();
        DataMigrationPermissionService service = service(jdbc, access);

        assertEquals(PROJECT, service.requireProject(PROJECT, USER));
        assertEquals(List.of(PROJECT), access.checkedProjectIds());
        assertTrue(jdbc.queried.stream().noneMatch(sql -> sql.contains("pm_project")),
            "成员/项目口径属平台契约，本模块守卫不得再查询 pm_project 或 pm_project_member");
    }

    @Test
    void moduleAdminRoleDoesNotBypassProjectScope() {
        StubProjectAccess access = StubProjectAccess.withDecision(StubProjectAccess.Decision.NOT_MEMBER);
        DataMigrationPermissionService service = service(new StubJdbcTemplate(true), access);

        // ADMIN/DATA_MIGRATION_ADMIN 只豁免功能权限；数据范围豁免由平台侧判定，本模块不得自行放行。
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.requireAccessible(PROJECT, ADMIN_USER));
        assertEquals(ErrorCode.FORBIDDEN, error.code());
    }

    @Test
    void accessibleProjectIdsArePassedThroughFromPlatform() {
        StubProjectAccess access = StubProjectAccess.withAccessibleProjects(3001L, 3002L);
        DataMigrationPermissionService service = service(new StubJdbcTemplate(false), access);

        assertEquals(List.of(3001L, 3002L), service.accessibleProjectIds(USER));
    }

    @Test
    void maintenanceAlwaysUsesStoredOwnership() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(false);
        StubProjectAccess access = StubProjectAccess.allow();
        DataMigrationPermissionService service = service(jdbc, access);

        assertEquals(PROJECT, service.requireStoredProject(Long.valueOf(PROJECT), USER));
        // 库中归属缺失或类型异常时按 400 拒绝，绝不回退到前端传入的项目。
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
            () -> service.requireStoredProject(null, USER)).code());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
            () -> service.requireStoredProject("10", USER)).code());
        assertEquals(List.of(PROJECT), access.checkedProjectIds());
    }

    @Test
    void writePermissionStillUsesModuleRoleAndOwnerRules() {
        StubJdbcTemplate adminJdbc = new StubJdbcTemplate(true);
        DataMigrationPermissionService adminService = service(adminJdbc, StubProjectAccess.allow());
        adminService.requireWrite(ADMIN_USER, 999L);

        StubProjectAccess access = StubProjectAccess.allow();
        DataMigrationPermissionService memberService = service(new StubJdbcTemplate(false), access);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
            () -> memberService.requireWrite(USER, 999L)).code());
        memberService.requireWrite(USER, USER.id());
    }
}

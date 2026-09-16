package com.ccb.system.project;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberRemovalGuard;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectWorkflowMemberGuardTest {
    @Test
    void blocksDeactivationBeforeMemberStatusIsChanged() {
        GuardJdbcTemplate jdbc = new GuardJdbcTemplate();
        ProjectMemberRemovalGuard guard = (tenantId, projectId, userId) -> {
            throw new BusinessException(com.ccb.common.exception.ErrorCode.CONFLICT, "仍有待办");
        };
        ProjectService service = new ProjectService(jdbc, null);
        service.setMemberRemovalGuards(java.util.List.of(guard));
        AuthUser admin = new AuthUser(1L, 1L, "admin", "", "Admin", 1L, true);

        assertThrows(BusinessException.class, () -> service.updateMember(9001L, 3001L, Map.of("status", 0), admin));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.updateCount);
    }

    @Test
    void 所有守卫都在成员停用前执行且任一拒绝即停止写入() {
        GuardJdbcTemplate jdbc = new GuardJdbcTemplate();
        java.util.List<String> checked = new java.util.ArrayList<>();
        ProjectService service = new ProjectService(jdbc, null);
        service.setMemberRemovalGuards(java.util.List.of(
                (tenant, project, user) -> checked.add("审批"),
                (tenant, project, user) -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1L, tenant);
                    org.junit.jupiter.api.Assertions.assertEquals(9001L, project);
                    org.junit.jupiter.api.Assertions.assertEquals(7L, user);
                    checked.add("搭建任务");
                    throw new BusinessException(com.ccb.common.exception.ErrorCode.CONFLICT, "先移交任务");
                }));
        AuthUser admin = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);
        assertThrows(BusinessException.class, () -> service.updateMember(9001L, 3001L, Map.of("status", 0), admin));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("审批", "搭建任务"), checked);
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.updateCount);
    }

    @Test
    void 删除成员同样执行退出守卫() {
        GuardJdbcTemplate jdbc = new GuardJdbcTemplate();
        ProjectService service = new ProjectService(jdbc, null);
        java.util.concurrent.atomic.AtomicBoolean checked = new java.util.concurrent.atomic.AtomicBoolean();
        service.setMemberRemovalGuards(java.util.List.of((tenant, project, user) -> {
            org.junit.jupiter.api.Assertions.assertTrue(jdbc.memberLocked);
            checked.set(true);
            throw new BusinessException(com.ccb.common.exception.ErrorCode.CONFLICT, "先移交阻塞");
        }));
        AuthUser admin = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);
        assertThrows(BusinessException.class, () -> service.deleteMember(9001L, 3001L, admin));
        org.junit.jupiter.api.Assertions.assertTrue(checked.get());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.updateCount);
    }

    @Test
    void Spring同时装配审批与架构守卫不冲突() {
        GuardJdbcTemplate jdbc = new GuardJdbcTemplate();
        java.util.List<String> checked = new java.util.ArrayList<>();
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean("workflowGuard", ProjectMemberRemovalGuard.class,
                    () -> (tenant, project, user) -> checked.add("审批"));
            context.registerBean("architectureGuard", ProjectMemberRemovalGuard.class,
                    () -> (tenant, project, user) -> checked.add("架构"));
            context.registerBean(ProjectService.class, () -> new ProjectService(jdbc, null));
            context.refresh();
            AuthUser admin = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);
            context.getBean(ProjectService.class).deleteMember(9001L, 3001L, admin);
            org.junit.jupiter.api.Assertions.assertEquals(2, checked.size());
            org.junit.jupiter.api.Assertions.assertTrue(checked.containsAll(java.util.List.of("审批", "架构")));
            org.junit.jupiter.api.Assertions.assertTrue(jdbc.updateCount > 0);
        }
    }

    private static final class GuardJdbcTemplate extends JdbcTemplate {
        private int updateCount;
        private boolean memberLocked;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.startsWith("SELECT user_id FROM pm_project_member")) {
                org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FOR UPDATE"), "退出前应先锁定成员再检查责任");
                memberLocked = true;
            }
            if (requiredType == Long.class) return (T) Long.valueOf(sql.contains("SELECT owner_id") ? 99L : 7L);
            return (T) Integer.valueOf(1);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            return 1;
        }
    }
}

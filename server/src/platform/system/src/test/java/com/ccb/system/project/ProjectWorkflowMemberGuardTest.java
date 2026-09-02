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
        service.setMemberRemovalGuard(guard);
        AuthUser admin = new AuthUser(1L, 1L, "admin", "", "Admin", 1L, true);

        assertThrows(BusinessException.class, () -> service.updateMember(9001L, 3001L, Map.of("status", 0), admin));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.updateCount);
    }

    private static final class GuardJdbcTemplate extends JdbcTemplate {
        private int updateCount;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Long.class) return (T) Long.valueOf(7L);
            return (T) Integer.valueOf(1);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            return 1;
        }
    }
}

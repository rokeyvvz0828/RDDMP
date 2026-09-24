package com.ccb.datamigration.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.service.DataMigrationPermissionService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/** 审核权责刚性（基线 15.2.1，T7）：管理员不得代审；仅审核角色可审；无权限拒绝。 */
class LifecyclePermissionServiceTest {
    private static final AuthUser USER = new AuthUser(9, 1, "ljy", "hash", "李佳一", 11, true, "org", null);

    private JdbcTemplate jdbc;
    private DataMigrationPermissionService dmPermissions;
    private LifecyclePermissionService service;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        dmPermissions = Mockito.mock(DataMigrationPermissionService.class);
        service = new LifecyclePermissionService(jdbc, dmPermissions);
    }

    @Test
    void adminCannotPerformReviewAction() {
        when(dmPermissions.isAdmin(USER)).thenReturn(true);
        assertThatThrownBy(() -> service.requireReviewAction(USER))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.AUDIT_PERMISSION_DENIED));
    }

    @Test
    void reviewerWithAuditPermissionCanPerformReviewAction() {
        when(dmPermissions.isAdmin(USER)).thenReturn(false);
        // isAdmin 查询 manage 权限点返回 0；requireReviewAction 查询 audit:pass 权限点返回 1
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenAnswer(invocation -> {
            Object[] all = invocation.getArguments();
            String code = String.valueOf(all[all.length - 1]);
            return code.contains(":audit:pass") ? 1 : 0;
        });
        assertThatCode(() -> service.requireReviewAction(USER)).doesNotThrowAnyException();
    }

    @Test
    void userWithoutAuditPermissionIsRejected() {
        when(dmPermissions.isAdmin(USER)).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.requireReviewAction(USER))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.AUDIT_PERMISSION_DENIED));
    }
}

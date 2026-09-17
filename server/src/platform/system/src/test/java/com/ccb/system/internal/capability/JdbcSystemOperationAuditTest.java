package com.ccb.system.internal.capability;

import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAuditCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JdbcSystemOperationAuditTest {
    @Mock
    private SystemOperationAuditRepository repository;

    private JdbcSystemOperationAudit audit;
    private final AuthUser actor = new AuthUser(7L, 9L, "tester", "", "测试用户", 1L, true);

    @BeforeEach
    void setUp() {
        audit = new JdbcSystemOperationAudit(repository);
    }

    @Test
    void recordsSuccessWithTenantOperatorAndTrace() {
        audit.recordSuccess(new SystemOperationAuditCommand(
                actor, "architecture:logical:create", "POST", "/api/architecture/logical-subsystems/12", null, "trace-001"));

        verify(repository).insert(any(Long.class), eq(9L), eq(7L), eq("architecture:logical:create"), eq("POST"), eq("/api/architecture/logical-subsystems/12"), eq(1), eq(null), eq("trace-001"));
    }

    @Test
    void recordsFailureInNewTransactionAndLimitsErrorSummary() throws Exception {
        audit.recordFailure(new SystemOperationAuditCommand(
                actor, "architecture:physical:update", "PUT", "/api/architecture/physical-subsystems/22", "错".repeat(400), " "));

        verify(repository).insert(any(Long.class), eq(9L), eq(7L), eq("architecture:physical:update"), eq("PUT"), eq("/api/architecture/physical-subsystems/22"), eq(0), eq("Business operation failed"), eq(null));

        Method success = JdbcSystemOperationAudit.class.getMethod("recordSuccess", SystemOperationAuditCommand.class);
        Method failure = JdbcSystemOperationAudit.class.getMethod("recordFailure", SystemOperationAuditCommand.class);
        assertEquals(Propagation.REQUIRED, success.getAnnotation(Transactional.class).propagation());
        assertEquals(Propagation.REQUIRES_NEW, failure.getAnnotation(Transactional.class).propagation());
    }

    @Test
    void commandRejectsBlankOperationCodeAndDoesNotAcceptTenantSeparately() {
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new SystemOperationAuditCommand(actor, " ", "POST", "/api/test", null, null));
        assertTrue(exception.getMessage().contains("operationCode"));
        assertEquals(6, SystemOperationAuditCommand.class.getRecordComponents().length);
    }
}

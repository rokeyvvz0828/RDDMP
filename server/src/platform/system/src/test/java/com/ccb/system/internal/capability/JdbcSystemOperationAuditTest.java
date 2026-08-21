package com.ccb.system.internal.capability;

import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAuditCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JdbcSystemOperationAuditTest {
    @Mock
    private JdbcTemplate jdbc;

    private JdbcSystemOperationAudit audit;
    private final AuthUser actor = new AuthUser(7L, 9L, "tester", "", "测试用户", 1L, true);

    @BeforeEach
    void setUp() {
        audit = new JdbcSystemOperationAudit(jdbc);
    }

    @Test
    void recordsSuccessWithTenantOperatorAndTrace() {
        audit.recordSuccess(new SystemOperationAuditCommand(
                actor, "architecture:logical:create", "POST", "/api/architecture/logical-subsystems/12", null, "trace-001"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("trace_id"), args.capture());
        assertEquals(9L, args.getValue()[1]);
        assertEquals(7L, args.getValue()[2]);
        assertEquals("architecture:logical:create", args.getValue()[3]);
        assertEquals("POST", args.getValue()[4]);
        assertEquals("/api/architecture/logical-subsystems/12", args.getValue()[5]);
        assertEquals(1, args.getValue()[6]);
        assertNull(args.getValue()[7]);
        assertEquals("trace-001", args.getValue()[8]);
    }

    @Test
    void recordsFailureInNewTransactionAndLimitsErrorSummary() throws Exception {
        audit.recordFailure(new SystemOperationAuditCommand(
                actor, "architecture:physical:update", "PUT", "/api/architecture/physical-subsystems/22", "错".repeat(400), " "));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("trace_id"), args.capture());
        assertEquals(0, args.getValue()[6]);
        assertEquals(255, ((String) args.getValue()[7]).length());
        assertNull(args.getValue()[8]);

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

package com.ccb.system.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.notification.NotificationStreamTicket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSseServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "tester", "", "测试用户", 1L, true);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void ticketIsSingleUseAndBoundToIssuedUser() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        NotificationSseService service = new NotificationSseService(
                Clock.fixed(Instant.parse("2026-09-09T06:00:00Z"), ZoneOffset.UTC),
                new SecureRandom(),
                ignored -> emitter);

        NotificationStreamTicket ticket = service.issueTicket(USER);
        service.connect(ticket.ticket());

        assertEquals(1, service.activeConnectionCount(1L, 7L));
        assertEquals(0, service.activeConnectionCount(1L, 8L));
        BusinessException reused = assertThrows(BusinessException.class, () -> service.connect(ticket.ticket()));
        assertEquals(ErrorCode.UNAUTHORIZED, reused.code());
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void expiredTicketIsRejectedAndConsumed() {
        Clock clock = mock(Clock.class);
        Instant issuedAt = Instant.parse("2026-09-09T06:00:00Z");
        when(clock.instant()).thenReturn(issuedAt, issuedAt.plusSeconds(31));
        NotificationSseService service = new NotificationSseService(clock, new SecureRandom());

        NotificationStreamTicket ticket = service.issueTicket(USER);
        BusinessException expired = assertThrows(BusinessException.class, () -> service.connect(ticket.ticket()));

        assertEquals(ErrorCode.UNAUTHORIZED, expired.code());
        assertEquals(0, service.pendingTicketCount());
    }

    @Test
    void notificationIsSentOnlyAfterCommit() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        NotificationSseService service = new NotificationSseService(
                Clock.fixed(Instant.parse("2026-09-09T06:00:00Z"), ZoneOffset.UTC),
                new SecureRandom(),
                ignored -> emitter);
        service.connect(service.issueTicket(USER).ticket());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.notifyUsersAfterCommit(1L, List.of(7L));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void notificationIsScopedByTenantAndUser() throws Exception {
        SseEmitter matching = mock(SseEmitter.class);
        SseEmitter otherTenant = mock(SseEmitter.class);
        NotificationSseService service = new NotificationSseService(
                Clock.fixed(Instant.parse("2026-09-09T06:00:00Z"), ZoneOffset.UTC),
                new SecureRandom(),
                timeout -> serviceEmitter(serviceFactoryIndex++, matching, otherTenant));
        AuthUser sameUserOtherTenant = new AuthUser(7L, 2L, "other", "", "其他租户用户", 1L, true);
        service.connect(service.issueTicket(USER).ticket());
        service.connect(service.issueTicket(sameUserOtherTenant).ticket());

        service.notifyUsersAfterCommit(1L, List.of(7L));

        verify(matching, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherTenant, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void rolledBackTransactionDoesNotSendNotification() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        NotificationSseService service = new NotificationSseService(
                Clock.fixed(Instant.parse("2026-09-09T06:00:00Z"), ZoneOffset.UTC),
                new SecureRandom(),
                ignored -> emitter);
        service.connect(service.issueTicket(USER).ticket());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.notifyUsersAfterCommit(1L, List.of(7L));
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    private int serviceFactoryIndex;

    private SseEmitter serviceEmitter(int index, SseEmitter first, SseEmitter second) {
        return index == 0 ? first : second;
    }
}

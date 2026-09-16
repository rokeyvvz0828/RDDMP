package com.ccb.system.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.notification.NotificationStreamTicket;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongFunction;

@Service
public class NotificationSseService {
    private static final Logger log = LoggerFactory.getLogger(NotificationSseService.class);
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();
    private static final int TICKET_BYTES = 32;

    private final Clock clock;
    private final SecureRandom secureRandom;
    private final LongFunction<SseEmitter> emitterFactory;
    private final ConcurrentMap<String, PendingTicket> tickets = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConnectionKey, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    public NotificationSseService() {
        this(Clock.systemUTC(), new SecureRandom(), SseEmitter::new);
    }

    NotificationSseService(Clock clock, SecureRandom secureRandom) {
        this(clock, secureRandom, SseEmitter::new);
    }

    NotificationSseService(Clock clock, SecureRandom secureRandom, LongFunction<SseEmitter> emitterFactory) {
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.emitterFactory = emitterFactory;
    }

    public NotificationStreamTicket issueTicket(AuthUser user) {
        Instant expiresAt = clock.instant().plus(TICKET_TTL);
        PendingTicket pending = new PendingTicket(new ConnectionKey(user.tenantId(), user.id()), expiresAt);
        String ticket;
        do {
            byte[] bytes = new byte[TICKET_BYTES];
            secureRandom.nextBytes(bytes);
            ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (tickets.putIfAbsent(ticket, pending) != null);
        return new NotificationStreamTicket(ticket, expiresAt);
    }

    public SseEmitter connect(String ticket) {
        PendingTicket pending = ticket == null || ticket.isBlank() ? null : tickets.remove(ticket);
        if (pending == null || !pending.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "通知连接凭证无效或已过期");
        }

        SseEmitter emitter = emitterFactory.apply(STREAM_TIMEOUT_MS);
        ConnectionKey key = pending.key();
        connections.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> {
            remove(key, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(key, emitter));

        if (!send(key, emitter, SseEmitter.event()
                .name("connected")
                .reconnectTime(1_000)
                .data("ready"))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "通知连接建立失败");
        }
        return emitter;
    }

    public void notifyUsersAfterCommit(long tenantId, Collection<Long> userIds) {
        Set<Long> recipients = userIds == null ? Set.of() : new HashSet<>(userIds);
        recipients.removeIf(userId -> userId == null || userId <= 0);
        if (recipients.isEmpty()) return;

        Runnable notify = () -> recipients.forEach(userId -> broadcast(
                new ConnectionKey(tenantId, userId),
                SseEmitter.event().name("notification").data("refresh")));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notify.run();
                }
            });
        } else if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("Notification change has an active transaction without synchronization; skip early SSE broadcast");
        } else {
            notify.run();
        }
    }

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        connections.forEach((key, emitters) -> Set.copyOf(emitters).forEach(emitter ->
                send(key, emitter, SseEmitter.event().name("heartbeat").data(now.toString()))));
    }

    int activeConnectionCount(long tenantId, long userId) {
        Set<SseEmitter> emitters = connections.get(new ConnectionKey(tenantId, userId));
        return emitters == null ? 0 : emitters.size();
    }

    int pendingTicketCount() {
        return tickets.size();
    }

    private void broadcast(ConnectionKey key, SseEmitter.SseEventBuilder event) {
        Set<SseEmitter> emitters = connections.get(key);
        if (emitters == null) return;
        Set.copyOf(emitters).forEach(emitter -> send(key, emitter, event));
    }

    private boolean send(ConnectionKey key, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException exception) {
            remove(key, emitter);
            return false;
        }
    }

    private void remove(ConnectionKey key, SseEmitter emitter) {
        Set<SseEmitter> emitters = connections.get(key);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) connections.remove(key, emitters);
    }

    @PreDestroy
    void closeAll() {
        connections.values().forEach(emitters -> Set.copyOf(emitters).forEach(SseEmitter::complete));
        connections.clear();
        tickets.clear();
    }

    private record ConnectionKey(long tenantId, long userId) {
    }

    private record PendingTicket(ConnectionKey key, Instant expiresAt) {
    }
}

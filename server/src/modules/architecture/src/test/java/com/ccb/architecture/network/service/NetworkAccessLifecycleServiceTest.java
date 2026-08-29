package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkAccessModels.AccessProtocol;
import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointKind;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessActionType;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessRelation;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationCloseType;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ValidityType;
import com.ccb.architecture.network.persistence.NetworkAccessStore;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkAccessLifecycleServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 12, 31, 23, 59);

    @Mock
    private NetworkAccessStore store;

    private final AtomicLong ids = new AtomicLong(900_000L);
    private NetworkAccessService service;

    @BeforeEach
    void setUp() {
        service = new NetworkAccessService(store, new ObjectMapper(), ids::incrementAndGet,
                Clock.fixed(Instant.parse("2026-08-28T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 直接关闭关系被拒绝() {
        assertThatThrownBy(() -> service.closeRelation(ACTOR, 100L,
                new NetworkAccessService.CloseRelationCommand("直接关闭", 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须通过关闭申请办理");
    }

    @Test
    void 批准修改申请会关闭旧关系并创建替代关系() {
        NetworkAccessApplication application = application(NetworkAccessActionType.MODIFY, ApplicationStatus.IN_REVIEW);
        NetworkAccessApplication approved = application(NetworkAccessActionType.MODIFY, ApplicationStatus.APPROVED);
        NetworkAccessRelation target = relation(100L, RelationStatus.ACTIVE);
        ArgumentCaptor<NetworkAccessRelation> replacement = ArgumentCaptor.forClass(NetworkAccessRelation.class);

        when(store.lockApplication(7L, 900L)).thenReturn(Optional.of(application));
        when(store.updateApplicationStatus(7L, 900L, ApplicationStatus.IN_REVIEW, 3L,
                ApplicationStatus.APPROVED, ACTOR.id())).thenReturn(true);
        when(store.lockRelation(7L, 100L)).thenReturn(Optional.of(target));
        when(store.closeRelationByApplication(eq(7L), eq(100L), eq(900001L), eq(900L),
                eq(RelationCloseType.SUPERSEDED), any(), eq(ACTOR.id()), any())).thenReturn(true);
        when(store.findApplication(7L, 900L)).thenReturn(Optional.of(approved));

        NetworkAccessApplication result = service.approveApplication(ACTOR, 900L, 3L);

        assertThat(result.status()).isEqualTo(ApplicationStatus.APPROVED);
        verify(store).insertRelation(replacement.capture());
        assertThat(replacement.getValue().replacesRelationId()).isEqualTo(100L);
        assertThat(replacement.getValue().status()).isEqualTo(RelationStatus.ACTIVE);
    }

    private NetworkAccessApplication application(NetworkAccessActionType actionType, ApplicationStatus status) {
        return new NetworkAccessApplication(900L, 7L, "NAA900", ACTOR.id(), actionType, 100L,
                EndpointKind.MANAGED, null, null, null, null,
                "[{\"id\":11,\"machineName\":\"src\",\"ipAddress\":\"10.1.1.10\",\"networkZoneId\":800}]",
                EndpointKind.MANAGED, null, null, null, null,
                "[{\"id\":21,\"machineName\":\"dst\",\"ipAddress\":\"10.2.1.20\",\"networkZoneId\":801}]",
                AccessProtocol.TCP, "443", "修改访问关系", "审批替代关系", START, END,
                ValidityType.LIMITED, status, 1, null, null, null, null, false,
                3L, ACTOR.id(), ACTOR.id(), START, START);
    }

    private NetworkAccessRelation relation(long id, RelationStatus status) {
        return new NetworkAccessRelation(id, 7L, "NAR" + id, 800L,
                EndpointKind.MANAGED,
                "[{\"id\":11,\"machineName\":\"src\",\"ipAddress\":\"10.1.1.10\",\"networkZoneId\":800}]",
                EndpointKind.MANAGED,
                "[{\"id\":21,\"machineName\":\"dst\",\"ipAddress\":\"10.2.1.20\",\"networkZoneId\":801}]",
                AccessProtocol.TCP, "443", "历史关系", null, START, END, status,
                null, null, null, 0L, ACTOR.id(), ACTOR.id(), START, START);
    }
}

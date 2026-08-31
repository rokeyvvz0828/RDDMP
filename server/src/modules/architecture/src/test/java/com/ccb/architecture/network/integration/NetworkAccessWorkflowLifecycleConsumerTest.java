package com.ccb.architecture.network.integration;

import com.ccb.architecture.network.model.NetworkAccessModels.AccessProtocol;
import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointKind;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessActionType;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.ValidityType;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRoundStatus;
import com.ccb.architecture.network.persistence.NetworkAccessStore;
import com.ccb.architecture.network.service.NetworkAccessApplicationSubmissionService;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkAccessWorkflowLifecycleConsumerTest {
    private static final long TENANT_ID = 7L;
    private static final long APPLICATION_ID = 910041L;
    private static final long ROUND_ID = 910042L;
    private static final long INSTANCE_ID = 890041L;
    private static final long DEFINITION_ID = 900000000000104L;
    private static final long VERSION_ID = 900000000000105L;
    private static final String DIGEST = "d".repeat(64);
    private static final String SUBSCRIBER_KEY = "architecture.network-access.application.lifecycle.v1";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 28, 11, 0);

    @Mock
    private NetworkAccessStore store;
    @Mock
    private NetworkAccessService access;

    private final AtomicLong ids = new AtomicLong(910050L);
    private NetworkAccessWorkflowLifecycleConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NetworkAccessWorkflowLifecycleConsumer(store, access, ids::incrementAndGet);
    }

    @Test
    void 订阅键与业务类型正确() {
        assertThat(consumer.subscriberKey()).isEqualTo(SUBSCRIBER_KEY);
        assertThat(consumer.supports(NetworkAccessApplicationSubmissionService.BUSINESS_TYPE)).isTrue();
        assertThat(consumer.supports("other")).isFalse();
    }

    @Test
    void 批准事件推进申请批准并完成轮次与回执() {
        NetworkAccessApplication application = reviewApplication(false);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.beginReceipt(any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(TENANT_ID, INSTANCE_ID)).thenReturn(Optional.of(startedRound()));
        when(store.isLatestWorkflowRound(TENANT_ID, APPLICATION_ID, 1)).thenReturn(true);
        when(store.completeStartedWorkflowRound(eq(TENANT_ID), eq(APPLICATION_ID), eq(1),
                eq(WorkflowRoundStatus.APPROVED), eq(OCCURRED_AT))).thenReturn(true);
        when(store.completeReceipt(eq(TENANT_ID), eq("event-approved"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStatus.PROCESSED),
                anyString())).thenReturn(true);

        consumer.consume(event(WorkflowLifecycleEventType.APPROVED));

        verify(access).applyApprovalInCurrentTransaction(TENANT_ID, APPLICATION_ID, 3L, 101L);
        verify(store).completeStartedWorkflowRound(eq(TENANT_ID), eq(APPLICATION_ID), eq(1),
                eq(WorkflowRoundStatus.APPROVED), eq(OCCURRED_AT));
    }

    @Test
    void 退回事件应用退回结论() {
        NetworkAccessApplication application = reviewApplication(false);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.beginReceipt(any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(TENANT_ID, INSTANCE_ID)).thenReturn(Optional.of(startedRound()));
        when(store.isLatestWorkflowRound(TENANT_ID, APPLICATION_ID, 1)).thenReturn(true);
        when(store.completeStartedWorkflowRound(eq(TENANT_ID), eq(APPLICATION_ID), eq(1),
                eq(WorkflowRoundStatus.RETURNED), eq(OCCURRED_AT))).thenReturn(true);
        when(store.completeReceipt(eq(TENANT_ID), eq("event-returned"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStatus.PROCESSED),
                anyString())).thenReturn(true);

        consumer.consume(event(WorkflowLifecycleEventType.RETURNED));

        verify(access).applyReviewOutcomeInCurrentTransaction(TENANT_ID, APPLICATION_ID, 3L, 101L,
                ApplicationStatus.RETURNED);
    }

    @Test
    void 终止事件确认取消申请() {
        NetworkAccessApplication application = reviewApplication(true);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.beginReceipt(any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(TENANT_ID, INSTANCE_ID)).thenReturn(Optional.of(startedRound()));
        when(store.isLatestWorkflowRound(TENANT_ID, APPLICATION_ID, 1)).thenReturn(true);
        when(store.completeStartedWorkflowRound(eq(TENANT_ID), eq(APPLICATION_ID), eq(1),
                eq(WorkflowRoundStatus.TERMINATED), eq(OCCURRED_AT))).thenReturn(true);
        when(store.completeReceipt(eq(TENANT_ID), eq("event-terminated"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStatus.PROCESSED),
                anyString())).thenReturn(true);

        consumer.consume(event(WorkflowLifecycleEventType.TERMINATED));

        verify(access).applyCancellationConfirmationInCurrentTransaction(TENANT_ID, APPLICATION_ID, 3L, 101L);
    }

    @Test
    void 重复事件幂等跳过() {
        NetworkAccessApplication application = reviewApplication(false);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.beginReceipt(any())).thenReturn(false);

        consumer.consume(event(WorkflowLifecycleEventType.APPROVED));

        verify(store, never()).lockWorkflowRoundByInstance(anyLong(), anyLong());
        verify(access, never()).applyApprovalInCurrentTransaction(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void 事件不匹配当前轮次时忽略() {
        NetworkAccessApplication application = reviewApplication(false);
        WorkflowRound staleRound = new WorkflowRound(ROUND_ID, TENANT_ID, APPLICATION_ID, 2,
                DEFINITION_ID, VERSION_ID, INSTANCE_ID, "e".repeat(64), WorkflowRoundStatus.STARTED,
                OCCURRED_AT, null, OCCURRED_AT, OCCURRED_AT);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.beginReceipt(any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(TENANT_ID, INSTANCE_ID)).thenReturn(Optional.of(staleRound));
        when(store.completeReceipt(eq(TENANT_ID), eq("event-started"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStatus.IGNORED),
                anyString())).thenReturn(true);

        consumer.consume(event(WorkflowLifecycleEventType.STARTED));

        verify(store).completeReceipt(eq(TENANT_ID), eq("event-started"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStatus.IGNORED),
                anyString());
    }

    @Test
    void 无效事件失败关闭() {
        WorkflowLifecycleEvent invalid = new WorkflowLifecycleEvent(
                "", TENANT_ID, INSTANCE_ID, WorkflowLifecycleEventType.APPROVED,
                new WorkflowBusinessContext("architecture", "架构管理",
                        NetworkAccessApplicationSubmissionService.BUSINESS_TYPE, String.valueOf(APPLICATION_ID),
                        "网络访问申请 " + APPLICATION_ID, 1, null, null,
                        "/architecture/network-access?applicationId=" + APPLICATION_ID, "short"),
                101L,
                OCCURRED_AT);
        assertThatThrownBy(() -> consumer.consume(invalid))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    private NetworkAccessApplication reviewApplication(boolean cancellationRequested) {
        return new NetworkAccessApplication(
                APPLICATION_ID, TENANT_ID, "NAA" + APPLICATION_ID, 9L, NetworkAccessActionType.OPEN, null,
                EndpointKind.MANAGED, 1L, 2L, 3L, null, "[]",
                EndpointKind.EXTERNAL, null, null, null, 4L, "[]",
                AccessProtocol.TCP, "443", "访问用途", null,
                OCCURRED_AT, OCCURRED_AT.plusDays(7), ValidityType.LIMITED,
                ApplicationStatus.IN_REVIEW, 1, DEFINITION_ID, VERSION_ID, INSTANCE_ID,
                DIGEST, cancellationRequested, 3L, 9L, 9L, OCCURRED_AT, OCCURRED_AT);
    }

    private WorkflowRound startedRound() {
        return new WorkflowRound(ROUND_ID, TENANT_ID, APPLICATION_ID, 1,
                DEFINITION_ID, VERSION_ID, INSTANCE_ID, DIGEST, WorkflowRoundStatus.STARTED,
                OCCURRED_AT, null, OCCURRED_AT, OCCURRED_AT);
    }

    private WorkflowLifecycleEvent event(WorkflowLifecycleEventType type) {
        return new WorkflowLifecycleEvent(
                "event-" + type.name().toLowerCase(),
                TENANT_ID,
                INSTANCE_ID,
                type,
                new WorkflowBusinessContext("architecture", "架构管理",
                        NetworkAccessApplicationSubmissionService.BUSINESS_TYPE, String.valueOf(APPLICATION_ID),
                        "网络访问申请 " + APPLICATION_ID, 1, null, null,
                        "/architecture/network-access?applicationId=" + APPLICATION_ID, DIGEST),
                101L,
                OCCURRED_AT);
    }
}

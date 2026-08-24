package com.ccb.architecture.network.integration;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRoundStatus;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
import com.ccb.architecture.network.service.NetworkWorkOrderService;
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
class NetworkWorkflowLifecycleConsumerTest {
    private static final long TENANT_ID = 7L;
    private static final long WORK_ORDER_ID = 900041L;
    private static final long ROUND_ID = 900042L;
    private static final long INSTANCE_ID = 880041L;
    private static final String DIGEST = "b".repeat(64);
    private static final String SUBSCRIBER_KEY = "architecture.network.work-order.lifecycle.v1";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 23, 11, 0);

    @Mock
    private NetworkWorkOrderStore store;
    @Mock
    private NetworkWorkOrderService changes;

    private final AtomicLong ids = new AtomicLong(900051L);
    private NetworkWorkflowLifecycleConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NetworkWorkflowLifecycleConsumer(store, changes, ids::incrementAndGet);
    }

    private WorkOrder reviewOrder() {
        return new WorkOrder(WORK_ORDER_ID, TENANT_ID, Kind.CERT, ActionType.APPLY, "demo.example.test",
                9L, "原因", WorkOrderStatus.IN_REVIEW, "{}", "[]", null, null, "[]", null, null,
                1, 900000000000032L, 900000000000033L, INSTANCE_ID, DIGEST, false, 3,
                9L, 9L, OCCURRED_AT, OCCURRED_AT);
    }

    private WorkflowRound startedRound() {
        return new WorkflowRound(ROUND_ID, TENANT_ID, WORK_ORDER_ID, 1,
                900000000000032L, 900000000000033L, INSTANCE_ID, DIGEST, WorkflowRoundStatus.STARTED,
                OCCURRED_AT, null, OCCURRED_AT, OCCURRED_AT);
    }

    private WorkflowLifecycleEvent event(WorkflowLifecycleEventType type) {
        return new WorkflowLifecycleEvent(
                "event-" + type.name().toLowerCase(),
                TENANT_ID,
                INSTANCE_ID,
                type,
                new WorkflowBusinessContext("architecture", "架构管理",
                        "architecture_network_work_order", String.valueOf(WORK_ORDER_ID),
                        "网络专项工单 " + WORK_ORDER_ID, 1, null, null,
                        "/architecture/network-work-orders/" + WORK_ORDER_ID, DIGEST),
                101L,
                OCCURRED_AT);
    }

    @Test
    void 订阅键与业务类型正确() {
        assertThat(consumer.subscriberKey()).isEqualTo(SUBSCRIBER_KEY);
        assertThat(consumer.supports("architecture_network_work_order")).isTrue();
        assertThat(consumer.supports("other")).isFalse();
    }

    @Test
    void 批准事件推进工单完成并完成轮次与回执() {
        WorkOrder order = reviewOrder();
        when(store.lockWorkOrder(TENANT_ID, WORK_ORDER_ID)).thenReturn(Optional.of(order));
        when(store.beginReceipt(any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(TENANT_ID, INSTANCE_ID)).thenReturn(Optional.of(startedRound()));
        when(store.isLatestWorkflowRound(TENANT_ID, WORK_ORDER_ID, 1)).thenReturn(true);
        when(store.completeStartedWorkflowRound(eq(TENANT_ID), eq(WORK_ORDER_ID), eq(1),
                eq(WorkflowRoundStatus.APPROVED), eq(OCCURRED_AT))).thenReturn(true);
        when(store.completeReceipt(eq(TENANT_ID), eq("event-approved"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus.PROCESSED),
                anyString())).thenReturn(true);

        consumer.consume(event(WorkflowLifecycleEventType.APPROVED));

        verify(changes).applyCompletionInCurrentTransaction(TENANT_ID, WORK_ORDER_ID, 3L, 101L);
        verify(store).completeStartedWorkflowRound(eq(TENANT_ID), eq(WORK_ORDER_ID), eq(1),
                eq(WorkflowRoundStatus.APPROVED), eq(OCCURRED_AT));
    }

    @Test
    void 退回事件应用退回结论() {
        WorkOrder order = reviewOrder();
        when(store.lockWorkOrder(TENANT_ID, WORK_ORDER_ID)).thenReturn(Optional.of(order));
        when(store.beginReceipt(any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(TENANT_ID, INSTANCE_ID)).thenReturn(Optional.of(startedRound()));
        when(store.isLatestWorkflowRound(TENANT_ID, WORK_ORDER_ID, 1)).thenReturn(true);
        when(store.completeStartedWorkflowRound(eq(TENANT_ID), eq(WORK_ORDER_ID), eq(1),
                eq(WorkflowRoundStatus.RETURNED), eq(OCCURRED_AT))).thenReturn(true);
        when(store.completeReceipt(eq(TENANT_ID), eq("event-returned"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus.PROCESSED),
                anyString())).thenReturn(true);

        consumer.consume(event(WorkflowLifecycleEventType.RETURNED));

        verify(changes).applyReviewOutcomeInCurrentTransaction(TENANT_ID, WORK_ORDER_ID, 3L, 101L,
                WorkOrderStatus.RETURNED);
    }

    @Test
    void 重复事件幂等跳过() {
        WorkOrder order = reviewOrder();
        when(store.lockWorkOrder(TENANT_ID, WORK_ORDER_ID)).thenReturn(Optional.of(order));
        when(store.beginReceipt(any())).thenReturn(false);

        consumer.consume(event(WorkflowLifecycleEventType.APPROVED));

        verify(store, never()).lockWorkflowRoundByInstance(anyLong(), anyLong());
        verify(changes, never()).applyCompletionInCurrentTransaction(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void 事件不匹配当前轮次时忽略() {
        WorkOrder order = reviewOrder();
        WorkflowRound staleRound = new WorkflowRound(ROUND_ID, TENANT_ID, WORK_ORDER_ID, 2,
                900000000000032L, 900000000000033L, INSTANCE_ID, "c".repeat(64), WorkflowRoundStatus.STARTED,
                OCCURRED_AT, null, OCCURRED_AT, OCCURRED_AT);
        when(store.lockWorkOrder(TENANT_ID, WORK_ORDER_ID)).thenReturn(Optional.of(order));
        when(store.beginReceipt(any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(TENANT_ID, INSTANCE_ID)).thenReturn(Optional.of(staleRound));
        when(store.completeReceipt(eq(TENANT_ID), eq("event-started"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus.IGNORED),
                anyString())).thenReturn(true);

        consumer.consume(event(WorkflowLifecycleEventType.STARTED));

        verify(store).completeReceipt(eq(TENANT_ID), eq("event-started"), eq(SUBSCRIBER_KEY),
                eq(com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus.IGNORED),
                anyString());
    }

    @Test
    void 无效事件失败关闭() {
        WorkflowLifecycleEvent invalid = new WorkflowLifecycleEvent(
                "", TENANT_ID, INSTANCE_ID, WorkflowLifecycleEventType.APPROVED,
                new WorkflowBusinessContext("architecture", "架构管理",
                        "architecture_network_work_order", String.valueOf(WORK_ORDER_ID),
                        "网络专项工单 " + WORK_ORDER_ID, 1, null, null,
                        "/architecture/network-work-orders/" + WORK_ORDER_ID, "short"),
                101L,
                OCCURRED_AT);
        assertThatThrownBy(() -> consumer.consume(invalid))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}

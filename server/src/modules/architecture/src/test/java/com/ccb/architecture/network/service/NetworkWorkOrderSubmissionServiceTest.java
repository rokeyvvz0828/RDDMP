package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRoundStatus;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
import com.ccb.architecture.network.service.NetworkWorkOrderService.AccessScope;
import com.ccb.architecture.network.service.NetworkWorkOrderService.WorkOrderDetail;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowProgress;
import com.ccb.workflow.integration.WorkflowStartCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import com.ccb.workflow.integration.WorkflowTerminateCommand;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkWorkOrderSubmissionServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);
    private static final long WORK_ORDER_ID = 900021L;
    private static final long INSTANCE_ID = 880021L;
    private static final String DIGEST = "a".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 10, 0);

    @Mock
    private NetworkWorkOrderService changes;
    @Mock
    private NetworkWorkOrderStore store;
    @Mock
    private WorkflowBusinessGateway workflowGateway;

    private final AtomicLong ids = new AtomicLong(900031L);
    private NetworkWorkOrderSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new NetworkWorkOrderSubmissionService(changes, store, workflowGateway,
                ids::incrementAndGet,
                Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC));
    }

    private WorkOrder reviewOrder() {
        return new WorkOrder(WORK_ORDER_ID, 7L, Kind.DNS, ActionType.ADD, "demo.example.test",
                ACTOR.id(), "原因", WorkOrderStatus.IN_REVIEW, "{}", "[]", null, null, "[]",
                null, null, 0, 900000000000032L, 900000000000033L, INSTANCE_ID, DIGEST,
                false, 3, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }

    @Test
    void 提交启动工作流并绑定轮次() {
        WorkOrder prepared = reviewOrder();
        when(store.lockWorkOrder(7L, WORK_ORDER_ID)).thenReturn(Optional.of(prepared));
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), eq(ACTOR))).thenReturn(
                new WorkflowStartResult(INSTANCE_ID, 900000000000032L, 1, "RUNNING",
                        new WorkflowBusinessContext("architecture", "架构管理",
                                "architecture_network_work_order", String.valueOf(WORK_ORDER_ID),
                                "网络专项工单 " + WORK_ORDER_ID, 1, null, null,
                                "/architecture/network-work-orders/" + WORK_ORDER_ID, DIGEST)));
        when(store.bindWorkflowRoundStarted(eq(7L), eq(WORK_ORDER_ID), eq(1), eq(900000000000032L),
                eq(1L), eq(INSTANCE_ID), eq(DIGEST), any())).thenReturn(true);
        when(store.compareAndSetWorkflowContext(eq(7L), eq(WORK_ORDER_ID), eq(0), eq(3L),
                eq(1), eq(900000000000032L), eq(1L), eq(INSTANCE_ID),
                eq(DIGEST), eq(ACTOR.id()))).thenReturn(true);
        when(changes.detail(ACTOR, AccessScope.OWN, WORK_ORDER_ID)).thenReturn(
                new WorkOrderDetail(prepared, List.of()));

        org.mockito.Mockito.doAnswer(invocation -> {
                    java.util.function.Consumer<NetworkWorkOrderService.SubmissionPreparation> starter =
                            invocation.getArgument(3);
                    starter.accept(new NetworkWorkOrderService.SubmissionPreparation(WORK_ORDER_ID, 1, DIGEST));
                    return null;
                }).when(changes).coordinateSubmission(eq(ACTOR), eq(WORK_ORDER_ID), eq(3L), any());

        service.submit(ACTOR, WORK_ORDER_ID, 3L);

        ArgumentCaptor<WorkflowStartCommand> startCaptor = ArgumentCaptor.forClass(WorkflowStartCommand.class);
        verify(workflowGateway).startByCode(startCaptor.capture(), eq(ACTOR));
        assertThat(startCaptor.getValue().definitionCode()).isEqualTo("architecture.network.work-order");
        assertThat(startCaptor.getValue().context().businessType()).isEqualTo("architecture_network_work_order");
        assertThat(startCaptor.getValue().context().businessKey()).isEqualTo(String.valueOf(WORK_ORDER_ID));
        assertThat(startCaptor.getValue().context().businessRound()).isEqualTo(1);
        assertThat(startCaptor.getValue().context().actionPath())
                .isEqualTo("/architecture/network-work-orders/" + WORK_ORDER_ID);
        verify(store).insertPendingWorkflowRound(any(WorkflowRound.class));
        verify(store).bindWorkflowRoundStarted(eq(7L), eq(WORK_ORDER_ID), eq(1),
                eq(900000000000032L), eq(1L), eq(INSTANCE_ID), eq(DIGEST), any());
        verify(store).compareAndSetWorkflowContext(eq(7L), eq(WORK_ORDER_ID), eq(0), eq(3L),
                eq(1), eq(900000000000032L), eq(1L), eq(INSTANCE_ID), eq(DIGEST),
                eq(ACTOR.id()));
    }

    @Test
    void 提交结果上下文不一致时返回409() {
        WorkOrder prepared = reviewOrder();
        when(store.lockWorkOrder(7L, WORK_ORDER_ID)).thenReturn(Optional.of(prepared));
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), eq(ACTOR))).thenReturn(
                new WorkflowStartResult(INSTANCE_ID, 900000000000032L, 1, "RUNNING",
                        new WorkflowBusinessContext("architecture", "架构管理",
                                "architecture_network_work_order", String.valueOf(WORK_ORDER_ID),
                                "网络专项工单 " + WORK_ORDER_ID, 2, null, null,
                                "/architecture/network-work-orders/" + WORK_ORDER_ID, DIGEST)));
        org.mockito.Mockito.doAnswer(invocation -> {
                    java.util.function.Consumer<NetworkWorkOrderService.SubmissionPreparation> starter =
                            invocation.getArgument(3);
                    starter.accept(new NetworkWorkOrderService.SubmissionPreparation(WORK_ORDER_ID, 1, DIGEST));
                    return null;
                }).when(changes).coordinateSubmission(eq(ACTOR), eq(WORK_ORDER_ID), eq(3L), any());

        assertThatThrownBy(() -> service.submit(ACTOR, WORK_ORDER_ID, 3L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void 草稿取消走同步路径() {
        WorkOrder draft = new WorkOrder(WORK_ORDER_ID, 7L, Kind.CLB, ActionType.OPEN, "CLB-A",
                ACTOR.id(), "原因", WorkOrderStatus.DRAFT, "{}", "[]", null, null, "[]", null, null,
                0, null, null, null, null, false, 1, ACTOR.id(), ACTOR.id(), TIME, TIME);
        when(changes.detail(ACTOR, AccessScope.OWN, WORK_ORDER_ID)).thenReturn(
                new WorkOrderDetail(draft, List.of()));
        when(changes.cancel(ACTOR, AccessScope.OWN, WORK_ORDER_ID, 1L)).thenReturn(
                new WorkOrderDetail(draft, List.of()));

        service.cancel(ACTOR, WORK_ORDER_ID, 1L);

        verify(changes).cancel(ACTOR, AccessScope.OWN, WORK_ORDER_ID, 1L);
    }

    @Test
    void 审批中取消先登记再终止流程() {
        WorkOrder review = reviewOrder();
        when(changes.detail(ACTOR, AccessScope.OWN, WORK_ORDER_ID)).thenReturn(
                new WorkOrderDetail(review, List.of()));
        when(workflowGateway.progress(INSTANCE_ID, ACTOR)).thenReturn(
                new WorkflowProgress(INSTANCE_ID, 900000000000032L, 1, "RUNNING", null, TIME));
        org.mockito.Mockito.doAnswer(invocation -> {
                    java.util.function.Consumer<NetworkWorkOrderService.CancellationPreparation> terminator =
                            invocation.getArgument(3);
                    terminator.accept(new NetworkWorkOrderService.CancellationPreparation(
                            WORK_ORDER_ID, INSTANCE_ID, 1));
                    return null;
                }).when(changes).coordinateCancellation(eq(ACTOR), eq(WORK_ORDER_ID), eq(3L), any());

        service.cancel(ACTOR, WORK_ORDER_ID, 3L);

        ArgumentCaptor<WorkflowTerminateCommand> terminateCaptor =
                ArgumentCaptor.forClass(WorkflowTerminateCommand.class);
        verify(workflowGateway).terminate(terminateCaptor.capture(), eq(ACTOR));
        assertThat(terminateCaptor.getValue().instanceId()).isEqualTo(INSTANCE_ID);
        assertThat(terminateCaptor.getValue().businessKey()).isEqualTo(String.valueOf(WORK_ORDER_ID));
        assertThat(terminateCaptor.getValue().businessRound()).isEqualTo(1);
    }

    @Test
    void 审批流程已结束时取消返回409() {
        WorkOrder review = reviewOrder();
        when(changes.detail(ACTOR, AccessScope.OWN, WORK_ORDER_ID)).thenReturn(
                new WorkOrderDetail(review, List.of()));
        when(workflowGateway.progress(INSTANCE_ID, ACTOR)).thenReturn(
                new WorkflowProgress(INSTANCE_ID, 900000000000032L, 1, "ENDED", null, TIME));
        org.mockito.Mockito.doAnswer(invocation -> {
                    java.util.function.Consumer<NetworkWorkOrderService.CancellationPreparation> terminator =
                            invocation.getArgument(3);
                    terminator.accept(new NetworkWorkOrderService.CancellationPreparation(
                            WORK_ORDER_ID, INSTANCE_ID, 1));
                    return null;
                }).when(changes).coordinateCancellation(eq(ACTOR), eq(WORK_ORDER_ID), eq(3L), any());

        assertThatThrownBy(() -> service.cancel(ACTOR, WORK_ORDER_ID, 3L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}

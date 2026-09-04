package com.ccb.architecture.change.integration;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStart;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRound;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRoundStatus;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.change.service.ArchitectureSubsystemSubmissionService;
import com.ccb.architecture.change.service.SubsystemChangeService;
import com.ccb.architecture.change.service.SubsystemChangeService.ReviewOutcome;
import com.ccb.architecture.change.service.SubsystemPublicationService;
import com.ccb.architecture.change.service.SubsystemPublicationService.ApprovalCommand;
import com.ccb.architecture.change.service.SubsystemPublicationService.ApprovalResult;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureWorkflowLifecycleConsumerTest {
    private static final String DIGEST = "a".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 10, 30);

    @Mock
    private SubsystemChangeStore store;
    @Mock
    private SubsystemChangeService changes;
    @Mock
    private SubsystemPublicationService publication;

    private ArchitectureWorkflowLifecycleConsumer consumer;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(900L);
        consumer = new ArchitectureWorkflowLifecycleConsumer(store, changes, publication, ids::getAndIncrement);
    }

    @Test
    void 暴露稳定订阅标识且只支持架构子系统工单() {
        assertThat(consumer.subscriberKey())
                .isEqualTo("architecture.subsystem.change.lifecycle.v1");
        assertThat(consumer.supports(ArchitectureSubsystemSubmissionService.BUSINESS_TYPE)).isTrue();
        assertThat(consumer.supports("release_application")).isFalse();
    }

    @Test
    void 重复事件回执不再执行任何业务动作() {
        WorkflowLifecycleEvent event = event("event-1", WorkflowLifecycleEventType.APPROVED);
        when(store.lockApplication(7L, 101L)).thenReturn(Optional.of(application(false)));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(false);

        consumer.consume(event);

        verify(store, never()).lockWorkflowRoundByInstance(anyLong(), anyLong());
        verify(publication, never()).approve(any(), any());
    }

    @Test
    void 旧摘要事件记录为忽略且不改变工单() {
        WorkflowLifecycleEvent event = event("event-2", WorkflowLifecycleEventType.APPROVED);
        WorkflowRound stale = round("b".repeat(64));
        when(store.lockApplication(7L, 101L)).thenReturn(Optional.of(application(false)));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(7L, 90L)).thenReturn(Optional.of(stale));
        when(store.completeReceipt(7L, "event-2", consumer.subscriberKey(),
                WorkflowReceiptStatus.IGNORED, "事件不匹配当前实例、轮次或摘要")).thenReturn(true);

        consumer.consume(event);

        verify(publication, never()).approve(any(), any());
        verify(store, never()).completeStartedWorkflowRound(anyLong(), anyLong(), any(Integer.class), any(), any());
    }

    @Test
    void 批准事件先原子发布再完成轮次和回执() {
        WorkflowLifecycleEvent event = event("event-3", WorkflowLifecycleEventType.APPROVED);
        ChangeApplication application = application(false);
        WorkflowRound round = round(DIGEST);
        stubActive(event, application, round);
        when(publication.approve(any(ApprovalCommand.class), any()))
                .thenReturn(new ApprovalResult(101L, List.of(201L)));
        when(store.completeStartedWorkflowRound(7L, 101L, 2, WorkflowRoundStatus.APPROVED, TIME))
                .thenReturn(true);
        when(store.completeReceipt(7L, "event-3", consumer.subscriberKey(),
                WorkflowReceiptStatus.PROCESSED, "已批准并原子发布架构子系统变更")).thenReturn(true);

        consumer.consume(event);

        ArgumentCaptor<ApprovalCommand> command = ArgumentCaptor.forClass(ApprovalCommand.class);
        verify(publication).approve(command.capture(), any());
        assertThat(command.getValue().applicationId()).isEqualTo(101L);
        assertThat(command.getValue().expectedBusinessRound()).isEqualTo(2);
        assertThat(command.getValue().expectedApplicationRowVersion()).isEqualTo(6L);
        assertThat(command.getValue().expectedWorkflowInstanceId()).isEqualTo(90L);
        verify(store).completeStartedWorkflowRound(7L, 101L, 2, WorkflowRoundStatus.APPROVED, TIME);
    }

    @Test
    void 批准发布失败时不完成轮次或成功回执以允许平台重试() {
        WorkflowLifecycleEvent event = event("event-publish-failed", WorkflowLifecycleEventType.APPROVED);
        stubActive(event, application(false), round(DIGEST));
        when(publication.approve(any(ApprovalCommand.class), any()))
                .thenThrow(new IllegalStateException("唯一性重验失败"));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("唯一性重验失败");

        verify(store, never()).completeStartedWorkflowRound(anyLong(), anyLong(), any(Integer.class),
                any(), any());
        verify(store, never()).completeReceipt(anyLong(), any(), any(), any(), any());
    }

    @Test
    void 退回和拒绝分别调用保留与释放语义并完成对应轮次() {
        WorkflowLifecycleEvent returned = event("event-4", WorkflowLifecycleEventType.RETURNED);
        stubActive(returned, application(false), round(DIGEST));
        when(store.completeStartedWorkflowRound(7L, 101L, 2, WorkflowRoundStatus.RETURNED, TIME))
                .thenReturn(true);
        when(store.completeReceipt(7L, "event-4", consumer.subscriberKey(),
                WorkflowReceiptStatus.PROCESSED, "已退回申请人修改")).thenReturn(true);

        consumer.consume(returned);

        verify(changes).applyReviewOutcomeInCurrentTransaction(7L, 101L, 6L, 88L, ReviewOutcome.RETURNED);

        WorkflowLifecycleEvent rejected = event("event-5", WorkflowLifecycleEventType.REJECTED);
        stubActive(rejected, application(false), round(DIGEST));
        when(store.completeStartedWorkflowRound(7L, 101L, 2, WorkflowRoundStatus.REJECTED, TIME))
                .thenReturn(true);
        when(store.completeReceipt(7L, "event-5", consumer.subscriberKey(),
                WorkflowReceiptStatus.PROCESSED, "已拒绝并释放未发布资源")).thenReturn(true);

        consumer.consume(rejected);

        verify(changes).applyReviewOutcomeInCurrentTransaction(7L, 101L, 6L, 88L, ReviewOutcome.REJECTED);
    }

    @Test
    void 终止事件只有匹配已登记取消请求才确认取消() {
        WorkflowLifecycleEvent ignored = event("event-6", WorkflowLifecycleEventType.TERMINATED);
        stubActive(ignored, application(false), round(DIGEST));
        when(store.completeReceipt(7L, "event-6", consumer.subscriberKey(),
                WorkflowReceiptStatus.IGNORED, "TERMINATED 事件没有匹配的取消请求")).thenReturn(true);

        consumer.consume(ignored);

        verify(changes, never()).applyCancellationConfirmationInCurrentTransaction(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong());

        WorkflowLifecycleEvent confirmed = event("event-7", WorkflowLifecycleEventType.TERMINATED);
        ChangeApplication cancelling = application(true);
        stubActive(confirmed, cancelling, round(DIGEST));
        when(store.completeStartedWorkflowRound(7L, 101L, 2, WorkflowRoundStatus.TERMINATED, TIME))
                .thenReturn(true);
        when(store.completeReceipt(7L, "event-7", consumer.subscriberKey(),
                WorkflowReceiptStatus.PROCESSED, "已确认工作流终止并取消工单")).thenReturn(true);

        consumer.consume(confirmed);

        verify(changes).applyCancellationConfirmationInCurrentTransaction(7L, 101L, 6L, 90L, 88L);
    }

    private void stubActive(WorkflowLifecycleEvent event, ChangeApplication application, WorkflowRound round) {
        when(store.lockApplication(7L, 101L)).thenReturn(Optional.of(application));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(7L, 90L)).thenReturn(Optional.of(round));
        when(store.isLatestWorkflowRound(7L, 101L, 2)).thenReturn(true);
    }

    private WorkflowLifecycleEvent event(String eventId, WorkflowLifecycleEventType type) {
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "architecture", "架构管理", "architecture_subsystem_change", "101",
                "架构子系统变更申请 101", 2, null, null,
                "/architecture/subsystem-change-applications/101", DIGEST);
        return new WorkflowLifecycleEvent(eventId, 7L, 90L, type, context, 88L, TIME);
    }

    private ChangeApplication application(boolean cancellationRequested) {
        return new ChangeApplication(101L, 7L, TargetKind.PHYSICAL, ActionType.CREATE, null, 9L,
                "新建渠道系统", ApplicationStatus.IN_REVIEW, 2, 80L, 1L, 90L, DIGEST,
                cancellationRequested, 6L, 9L, 9L, TIME.minusHours(1), TIME.minusMinutes(1));
    }

    private WorkflowRound round(String digest) {
        return new WorkflowRound(700L, 7L, 101L, 2, 80L, 1L, 90L, digest,
                WorkflowRoundStatus.STARTED, TIME.minusMinutes(5), null,
                TIME.minusMinutes(6), TIME.minusMinutes(5));
    }
}

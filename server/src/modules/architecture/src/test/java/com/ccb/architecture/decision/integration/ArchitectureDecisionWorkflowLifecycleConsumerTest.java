package com.ccb.architecture.decision.integration;

import com.ccb.architecture.decision.model.DecisionModels.Conclusion;
import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.MatterStatus;
import com.ccb.architecture.decision.model.DecisionModels.PublicationIntent;
import com.ccb.architecture.decision.model.DecisionModels.ReviewRecord;
import com.ccb.architecture.decision.model.DecisionModels.ReviewMethod;
import com.ccb.architecture.decision.model.DecisionModels.SupersessionTarget;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStart;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStatus;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRound;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRoundStatus;
import com.ccb.architecture.decision.persistence.DecisionStore;
import com.ccb.architecture.decision.service.ArchitectureDecisionService;
import com.ccb.common.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureDecisionWorkflowLifecycleConsumerTest {
    private static final String DIGEST = "b".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 10, 30);

    @Mock
    private DecisionStore store;

    private ArchitectureDecisionWorkflowLifecycleConsumer consumer;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(900L);
        consumer = new ArchitectureDecisionWorkflowLifecycleConsumer(store, ids::getAndIncrement);
    }

    @Test
    void 暴露稳定订阅标识且只支持决策发布业务() {
        assertThat(consumer.subscriberKey()).isEqualTo(ArchitectureDecisionService.SUBSCRIBER_KEY);
        assertThat(consumer.supports(ArchitectureDecisionService.BUSINESS_TYPE)).isTrue();
        assertThat(consumer.supports("release_application")).isFalse();
    }

    @Test
    void 重复事件回执不再执行任何业务动作() {
        WorkflowLifecycleEvent event = event("event-1", WorkflowLifecycleEventType.APPROVED);
        when(store.lockMatter(7L, 101L)).thenReturn(Optional.of(matter(MatterStatus.IN_REVIEW, 1)));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(false);

        consumer.consume(event);

        verify(store, never()).lockWorkflowRoundByInstance(anyLong(), anyLong());
        verify(store, never()).insertConclusion(any());
    }

    @Test
    void 无效事件或摘要不匹配时拒绝并忽略() {
        WorkflowLifecycleEvent event = event("event-2", WorkflowLifecycleEventType.APPROVED);
        when(store.lockMatter(7L, 101L)).thenReturn(Optional.of(matter(MatterStatus.IN_REVIEW, 1)));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
        when(store.completeReceipt(anyLong(), any(), any(), any(), any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(7L, 500L))
                .thenReturn(Optional.of(round(1, "x".repeat(64), WorkflowRoundStatus.STARTED)));

        consumer.consume(event);

        verify(store).completeReceipt(7L, "event-2", ArchitectureDecisionService.SUBSCRIBER_KEY,
                WorkflowReceiptStatus.IGNORED, "事件不匹配当前实例、轮次或摘要");
        verify(store, never()).insertConclusion(any());
    }

    @Test
    void 批准事件原子发布结论替代关系并完成事项() {
        WorkflowLifecycleEvent event = event("event-3", WorkflowLifecycleEventType.APPROVED);
        when(store.lockMatter(7L, 101L)).thenReturn(Optional.of(matter(MatterStatus.IN_REVIEW, 1)));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
        when(store.completeReceipt(anyLong(), any(), any(), any(), any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(7L, 500L))
                .thenReturn(Optional.of(round(1, DIGEST, WorkflowRoundStatus.STARTED)));
        when(store.isLatestWorkflowRound(7L, 101L, 1)).thenReturn(true);
        when(store.findPublicationIntent(7L, 101L)).thenReturn(Optional.of(intent()));
        when(store.findReview(7L, 101L, 201L)).thenReturn(Optional.of(review()));
        when(store.findConclusion(7L, 101L)).thenReturn(Optional.empty());
        when(store.findConclusionById(7L, 7001L)).thenReturn(Optional.of(conclusion(7001L)));
        when(store.completeReceipt(anyLong(), any(), any(), any(), any())).thenReturn(true);

        consumer.consume(event);

        ArgumentCaptor<Conclusion> conclusionCaptor = ArgumentCaptor.forClass(Conclusion.class);
        verify(store).insertConclusion(conclusionCaptor.capture());
        assertThat(conclusionCaptor.getValue().matterId()).isEqualTo(101L);
        assertThat(conclusionCaptor.getValue().content()).isEqualTo("结论：采用新中间件");
        assertThat(conclusionCaptor.getValue().reviewId()).isEqualTo(201L);
        verify(store).insertSupersession(any());
        verify(store).markMatterPublished(7L, 101L, 3L);
        verify(store).completeStartedWorkflowRound(7L, 101L, 1, WorkflowRoundStatus.APPROVED, TIME);
        verify(store).completeReceipt(7L, "event-3", ArchitectureDecisionService.SUBSCRIBER_KEY,
                WorkflowReceiptStatus.PROCESSED, "已发布正式决策结论并完成事项");
    }

    @Test
    void 评审缺少正式结论时发布失败并回滚回执() {
        WorkflowLifecycleEvent event = event("event-4", WorkflowLifecycleEventType.APPROVED);
        when(store.lockMatter(7L, 101L)).thenReturn(Optional.of(matter(MatterStatus.IN_REVIEW, 1)));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(7L, 500L))
                .thenReturn(Optional.of(round(1, DIGEST, WorkflowRoundStatus.STARTED)));
        when(store.isLatestWorkflowRound(7L, 101L, 1)).thenReturn(true);
        when(store.findPublicationIntent(7L, 101L)).thenReturn(Optional.of(intent()));
        when(store.findReview(7L, 101L, 201L)).thenReturn(Optional.of(reviewWithoutConclusion()));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少正式结论");
        verify(store, never()).insertConclusion(any());
        verify(store, never()).completeReceipt(anyLong(), any(), any(), any(), any());
    }

    @Test
    void 退回事件保持评审中并保留发布准备() {
        WorkflowLifecycleEvent event = event("event-5", WorkflowLifecycleEventType.RETURNED);
        when(store.lockMatter(7L, 101L)).thenReturn(Optional.of(matter(MatterStatus.IN_REVIEW, 1)));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
        when(store.completeReceipt(anyLong(), any(), any(), any(), any())).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(7L, 500L))
                .thenReturn(Optional.of(round(1, DIGEST, WorkflowRoundStatus.STARTED)));
        when(store.isLatestWorkflowRound(7L, 101L, 1)).thenReturn(true);

        consumer.consume(event);

        verify(store).completeStartedWorkflowRound(7L, 101L, 1, WorkflowRoundStatus.RETURNED, TIME);
        verify(store, never()).insertConclusion(any());
        verify(store, never()).markMatterPublished(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 拒绝和终止事件只结束轮次不发布结论() {
        for (WorkflowLifecycleEventType type : List.of(WorkflowLifecycleEventType.REJECTED,
                WorkflowLifecycleEventType.TERMINATED)) {
            WorkflowLifecycleEvent event = event("event-" + type, type);
            when(store.lockMatter(7L, 101L)).thenReturn(Optional.of(matter(MatterStatus.IN_REVIEW, 1)));
            when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
            when(store.lockWorkflowRoundByInstance(7L, 500L))
                    .thenReturn(Optional.of(round(1, DIGEST, WorkflowRoundStatus.STARTED)));
            when(store.isLatestWorkflowRound(7L, 101L, 1)).thenReturn(true);
            when(store.completeReceipt(anyLong(), any(), any(), any(), any())).thenReturn(true);

            consumer.consume(event);

            verify(store).completeStartedWorkflowRound(7L, 101L, 1,
                    type == WorkflowLifecycleEventType.TERMINATED
                            ? WorkflowRoundStatus.TERMINATED : WorkflowRoundStatus.REJECTED, TIME);
            verify(store, never()).insertConclusion(any());
        }
    }

    private WorkflowLifecycleEvent event(String eventId, WorkflowLifecycleEventType type) {
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "architecture", "架构管理", ArchitectureDecisionService.BUSINESS_TYPE,
                "101", "架构决策事项 AD-2026-0001", 1, null, null,
                "/architecture/decisions/101", DIGEST);
        return new WorkflowLifecycleEvent(eventId, 7L, 500L, type, context, 11L, TIME);
    }

    private DecisionMatter matter(MatterStatus status, int round) {
        return new DecisionMatter(101L, 7L, "AD-2026-0001", "中间件选型", "问题", "TECHNOLOGY_SELECTION",
                status, TIME, TIME.toLocalDate().plusDays(7), null, null, null, null, null, null,
                1L, "提出人", 1L, "提出人", null, null, round, 90L, 91L, 500L, DIGEST, 3L,
                1L, "提出人", TIME, TIME);
    }

    private WorkflowRound round(int roundNo, String digest, WorkflowRoundStatus status) {
        return new WorkflowRound(600L, 7L, 101L, roundNo, 90L, 91L, 500L, digest, status,
                TIME, null, TIME, TIME);
    }

    private PublicationIntent intent() {
        return new PublicationIntent(101L, 7L, 201L,
                List.of(new SupersessionTarget(7001L,
                        com.ccb.architecture.decision.model.DecisionModels.SupersessionKind.SUPERSEDE)),
                DIGEST, 11L, "管理人员", TIME);
    }

    private ReviewRecord review() {
        return new ReviewRecord(201L, 7L, 101L, 1, ReviewMethod.MEETING, TIME, "材料",
                "意见", "结论：采用新中间件", "理由充分", 11L, "架构组成员", TIME, TIME);
    }

    private ReviewRecord reviewWithoutConclusion() {
        return new ReviewRecord(201L, 7L, 101L, 1, ReviewMethod.MEETING, TIME, "材料",
                "意见", null, null, 11L, "架构组成员", TIME, TIME);
    }

    private Conclusion conclusion(long id) {
        return new Conclusion(id, 7L, 6003L, 202L, "结论：旧中间件", "理由", TIME, 11L, "管理人员", TIME);
    }
}

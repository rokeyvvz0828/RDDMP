package com.ccb.architecture.decision.service;

import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.FirstHandlingOutcome;
import com.ccb.architecture.decision.model.DecisionModels.MatterStatus;
import com.ccb.architecture.decision.model.DecisionModels.ReviewMethod;
import com.ccb.architecture.decision.persistence.DecisionStore;
import com.ccb.architecture.decision.service.ArchitectureDecisionService.AccessLevel;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowStartCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
class ArchitectureDecisionServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 23, 10, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-23T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final AuthUser PROPOSER = new AuthUser(1L, 1L, "proposer", "", "提出人", 0L, true);
    private static final AuthUser REVIEWER = new AuthUser(2L, 1L, "reviewer", "", "架构组成员", 0L, true);
    private static final AuthUser MANAGER = new AuthUser(3L, 1L, "manager", "", "管理人员", 0L, true);

    @Mock
    private DecisionStore store;
    @Mock
    private SystemReferenceQuery referenceQuery;
    @Mock
    private WorkflowBusinessGateway workflowGateway;
    @Mock
    private AttachmentPort attachmentPort;
    @Mock
    private AttachmentGateway attachmentGateway;

    private ArchitectureDecisionService service;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(1_000L);
        service = new ArchitectureDecisionService(store, referenceQuery, workflowGateway,
                attachmentPort, attachmentGateway, ids::getAndIncrement, CLOCK);
    }

    @Test
    void 创建事项生成编号并计算七日首次处理期限() {
        when(store.allocateMatterOrdinal(1L, 2026)).thenReturn(1);
        when(store.findMatter(1L, 1000L)).thenReturn(Optional.of(matter(1000L, "AD-2026-0001", MatterStatus.SUBMITTED)));

        DecisionMatter created = service.create(PROPOSER, new com.ccb.architecture.decision.model.DecisionModels.MatterCommand("中间件选型", "问题"));

        assertThat(created.matterNo()).isEqualTo("AD-2026-0001");
        assertThat(created.receivedAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(created.firstHandlingDeadline()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(created.status()).isEqualTo(MatterStatus.SUBMITTED);
    }

    @Test
    void 编号按年份递增且跨年重新起号() {
        when(store.allocateMatterOrdinal(1L, 2026)).thenReturn(42);
        when(store.findMatter(1L, 1000L)).thenReturn(Optional.of(matter(1000L, "AD-2026-0042", MatterStatus.SUBMITTED)));

        DecisionMatter created = service.create(PROPOSER, new com.ccb.architecture.decision.model.DecisionModels.MatterCommand("标题", "问题"));

        assertThat(created.matterNo()).isEqualTo("AD-2026-0042");
        verify(store).allocateMatterOrdinal(1L, 2026);
    }

    @Test
    void 非提出人不能编辑他人事项() {
        when(store.findMatter(1L, 101L)).thenReturn(Optional.of(matter(101L, "AD-2026-0001", MatterStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.update(REVIEWER, AccessLevel.REVIEW, 101L, 1L,
                new com.ccb.architecture.decision.model.DecisionModels.MatterCommand("新标题", "新问题")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有事项提出人可以编辑本人事项");
    }

    @Test
    void 已处理事项不能再次办理首次处理() {
        when(store.findMatter(1L, 101L)).thenReturn(Optional.of(matter(101L, "AD-2026-0001", MatterStatus.IN_REVIEW)));

        assertThatThrownBy(() -> service.firstHandling(REVIEWER, AccessLevel.REVIEW, 101L, 1L,
                FirstHandlingOutcome.ACCEPTED, null, ReviewMethod.ASYNC))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有待首次处理的事项可以办理首次处理");
    }

    @Test
    void 受理或确定评审方式必须指定评审方式() {
        when(store.findMatter(1L, 101L)).thenReturn(Optional.of(matter(101L, "AD-2026-0001", MatterStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.firstHandling(REVIEWER, AccessLevel.REVIEW, 101L, 1L,
                FirstHandlingOutcome.ACCEPTED, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须指定异步或会议评审方式");
    }

    @Test
    void 要求补充信息后重新提交重置期限() {
        when(store.findMatter(1L, 101L)).thenReturn(
                Optional.of(matter(101L, "AD-2026-0001", MatterStatus.RETURNED_FOR_INFO)),
                Optional.of(matter(101L, "AD-2026-0001", MatterStatus.SUBMITTED)));

        DecisionMatter resubmitted = service.resubmit(PROPOSER, AccessLevel.PROPOSE, 101L, 1L);

        assertThat(resubmitted.status()).isEqualTo(MatterStatus.SUBMITTED);
        assertThat(resubmitted.firstHandlingDeadline()).isEqualTo(LocalDate.of(2026, 8, 30));
        verify(store).resubmit(anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void 发布准备要求类型与含结论的评审记录() {
        when(store.findMatter(1L, 101L)).thenReturn(Optional.of(matter(101L, "AD-2026-0001", MatterStatus.IN_REVIEW)));

        assertThatThrownBy(() -> service.preparePublication(MANAGER, 101L, 1L, 201L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须确定事项类型");
    }

    @Test
    void 发布准备校验替代目标必须已发布() {
        DecisionMatter untyped = matter(101L, "AD-2026-0001", MatterStatus.IN_REVIEW);
        untyped = new DecisionMatter(untyped.id(), untyped.tenantId(), untyped.matterNo(), untyped.title(),
                untyped.problem(), "TECHNOLOGY_SELECTION", untyped.status(), untyped.receivedAt(),
                untyped.firstHandlingDeadline(), untyped.firstHandlingOutcome(), untyped.firstHandlingComment(),
                untyped.firstHandledAt(), untyped.firstHandlerId(), untyped.firstHandlerName(), untyped.reviewMode(),
                untyped.proposerId(), untyped.proposerName(), untyped.submitterId(), untyped.submitterName(),
                untyped.publicationPreparedAt(), untyped.publicationPreparedBy(), untyped.currentBusinessRound(),
                untyped.currentWorkflowDefinitionId(), untyped.currentWorkflowVersionId(),
                untyped.currentWorkflowInstanceId(), untyped.currentPayloadDigest(), untyped.rowVersion(),
                untyped.createdBy(), untyped.createdByName(), untyped.createdAt(), untyped.updatedAt());
        when(store.findMatter(1L, 101L)).thenReturn(Optional.of(untyped));
        when(store.findReview(1L, 101L, 201L)).thenReturn(Optional.of(review()));
        when(store.findConclusionById(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preparePublication(MANAGER, 101L, 1L, 201L,
                List.of(new com.ccb.architecture.decision.model.DecisionModels.SupersessionTarget(999L,
                        com.ccb.architecture.decision.model.DecisionModels.SupersessionKind.SUPERSEDE))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("替代目标必须是已发布结论");
    }

    @Test
    void 启动发布校验工作流返回与准备一致并写入轮次() {
        DecisionMatter typed = typedMatter(101L, MatterStatus.IN_REVIEW, 0);
        when(store.findMatter(1L, 101L)).thenReturn(Optional.of(typed),
                Optional.of(typedMatter(101L, MatterStatus.IN_REVIEW, 1)));
        when(store.findPublicationIntent(1L, 101L)).thenReturn(Optional.of(intent()));
        when(store.bindWorkflowRoundStarted(anyLong(), anyLong(), anyInt(), anyLong(), anyInt(), anyLong(),
                any(), any())).thenReturn(true);
        when(store.compareAndSetMatterWorkflowContext(anyLong(), anyLong(), anyInt(), anyLong(), anyInt(),
                anyLong(), anyInt(), anyLong(), any(), anyLong())).thenReturn(true);
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), any())).thenReturn(startResult());

        DecisionMatter started = service.startPublication(MANAGER, 101L, 0L);

        assertThat(started.currentBusinessRound()).isEqualTo(1);
        ArgumentCaptor<WorkflowStartCommand> captor = ArgumentCaptor.forClass(WorkflowStartCommand.class);
        verify(workflowGateway).startByCode(captor.capture(), any());
        assertThat(captor.getValue().definitionCode()).isEqualTo("architecture.decision.review");
        assertThat(captor.getValue().context().businessKey()).isEqualTo("101");
        assertThat(captor.getValue().context().businessRound()).isEqualTo(1);
        assertThat(captor.getValue().context().dataDigest()).isEqualTo("d".repeat(64));
    }

    @Test
    void 未准备发布意图不能启动() {
        when(store.findMatter(1L, 101L)).thenReturn(Optional.of(typedMatter(101L, MatterStatus.IN_REVIEW, 0)));
        when(store.findPublicationIntent(1L, 101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startPublication(MANAGER, 101L, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先完成结论发布准备");
        verify(workflowGateway, never()).startByCode(any(), any());
    }

    @Test
    void 结论不存在时链查询返回404语义() {
        when(store.findConclusionById(1L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.conclusionChain(PROPOSER, 42L))
                .isInstanceOf(ArchitectureNotFoundException.class);
    }

    @Test
    void 类型选项来自平台参数() {
        when(referenceQuery.activeParameters(PROPOSER, "ARCH_MATTER_TYPE"))
                .thenReturn(List.of(new SystemParameterReference("TECHNOLOGY_SELECTION", "技术选型")));

        assertThat(service.types(PROPOSER)).hasSize(1);
        assertThat(service.types(PROPOSER).get(0).code()).isEqualTo("TECHNOLOGY_SELECTION");
    }

    private DecisionMatter matter(long id, String matterNo, MatterStatus status) {
        return new DecisionMatter(id, 1L, matterNo, "中间件选型", "问题", null, status,
                NOW, NOW.toLocalDate().plusDays(7), null, null, null, null, null, null,
                1L, "提出人", 1L, "提出人", null, null, 0, null, null, null, null, 1L,
                1L, "提出人", NOW, NOW);
    }

    private DecisionMatter typedMatter(long id, MatterStatus status, int round) {
        return new DecisionMatter(id, 1L, "AD-2026-0001", "中间件选型", "问题", "TECHNOLOGY_SELECTION",
                status, NOW, NOW.toLocalDate().plusDays(7), null, null, null, null, null, null,
                1L, "提出人", 1L, "提出人", null, null, round, null, null, null, null, 1L,
                1L, "提出人", NOW, NOW);
    }

    private com.ccb.architecture.decision.model.DecisionModels.ReviewRecord review() {
        return new com.ccb.architecture.decision.model.DecisionModels.ReviewRecord(
                201L, 1L, 101L, 1, ReviewMethod.MEETING, NOW, "材料", "意见",
                "结论：采用新中间件", "理由充分", 2L, "架构组成员", NOW, NOW);
    }

    private com.ccb.architecture.decision.model.DecisionModels.PublicationIntent intent() {
        return new com.ccb.architecture.decision.model.DecisionModels.PublicationIntent(
                101L, 1L, 201L, List.of(), "d".repeat(64), 3L, "管理人员", NOW);
    }

    private WorkflowStartResult startResult() {
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "architecture", "架构管理", "architecture_decision_publish",
                "101", "架构决策事项 AD-2026-0001", 1, null, null,
                "/architecture/decisions/101", "d".repeat(64));
        return new WorkflowStartResult(500L, 90L, 1, "RUNNING", context);
    }
}

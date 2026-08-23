package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.change.service.SubsystemChangeService.AccessScope;
import com.ccb.architecture.change.service.SubsystemChangeService.ApplicationDetail;
import com.ccb.architecture.change.service.SubsystemChangeService.CancellationCoordinator;
import com.ccb.architecture.change.service.SubsystemChangeService.CancellationPreparation;
import com.ccb.architecture.change.service.SubsystemChangeService.SubmissionCoordinator;
import com.ccb.architecture.change.service.SubsystemChangeService.SubmissionPreparation;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureSubsystemSubmissionServiceTest {
    private static final AuthUser ACTOR =
            new AuthUser(9L, 7L, "architect", "hash", "架构师", 11L, true);
    private static final String DIGEST = "a".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 10, 0);

    @Mock
    private SubsystemChangeService changes;
    @Mock
    private SubsystemChangeStore store;
    @Mock
    private WorkflowBusinessGateway workflowGateway;

    private ArchitectureSubsystemSubmissionService service;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(700L);
        service = new ArchitectureSubsystemSubmissionService(changes, store, workflowGateway,
                ids::getAndIncrement, Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 提交在协调事务中启动固定流程并绑定返回上下文() {
        ChangeApplication prepared = application(ApplicationStatus.IN_REVIEW, 0, null, false, 1L);
        ChangeApplication bound = application(ApplicationStatus.IN_REVIEW, 1, 90L, false, 2L);
        SubmissionPreparation preparation =
                new SubmissionPreparation(101L, 1, "{}", DIGEST, List.of());
        stubSubmission(preparation);
        when(store.lockApplication(7L, 101L)).thenReturn(Optional.of(prepared));
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), eq(ACTOR)))
                .thenAnswer(invocation -> {
                    WorkflowStartCommand command = invocation.getArgument(0);
                    return new WorkflowStartResult(90L, 80L, 1, "RUNNING", command.context());
                });
        when(store.bindWorkflowRoundStarted(7L, 101L, 1, 80L, 1L, 90L, DIGEST, TIME))
                .thenReturn(true);
        when(store.compareAndSetApplicationWorkflowContext(
                7L, 101L, 0, 1L, 1, 80L, 1L, 90L, DIGEST, ACTOR.id()))
                .thenReturn(true);
        ApplicationDetail expected = detail(bound);
        when(changes.detail(ACTOR, AccessScope.OWN, 101L)).thenReturn(expected);

        ApplicationDetail result = service.submit(ACTOR, 101L, 0L);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<WorkflowStartCommand> command = ArgumentCaptor.forClass(WorkflowStartCommand.class);
        verify(workflowGateway).startByCode(command.capture(), eq(ACTOR));
        assertThat(command.getValue().definitionCode())
                .isEqualTo(ArchitectureSubsystemSubmissionService.WORKFLOW_DEFINITION_CODE);
        assertThat(command.getValue().context().businessType())
                .isEqualTo(ArchitectureSubsystemSubmissionService.BUSINESS_TYPE);
        assertThat(command.getValue().context().businessKey()).isEqualTo("101");
        assertThat(command.getValue().context().businessRound()).isEqualTo(1);
        assertThat(command.getValue().context().dataDigest()).isEqualTo(DIGEST);
        verify(store).insertPendingWorkflowRound(any());
    }

    @Test
    void 平台返回不匹配上下文时拒绝绑定并向外抛出冲突() {
        ChangeApplication prepared = application(ApplicationStatus.IN_REVIEW, 0, null, false, 1L);
        SubmissionPreparation preparation =
                new SubmissionPreparation(101L, 1, "{}", DIGEST, List.of());
        stubSubmission(preparation);
        when(store.lockApplication(7L, 101L)).thenReturn(Optional.of(prepared));
        WorkflowBusinessContext wrong = new WorkflowBusinessContext(
                "architecture", "架构管理", "architecture_subsystem_change", "999",
                "错误申请", 1, null, null, "/architecture/wrong", DIGEST);
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), eq(ACTOR)))
                .thenReturn(new WorkflowStartResult(90L, 80L, 1, "RUNNING", wrong));

        assertThatThrownBy(() -> service.submit(ACTOR, 101L, 0L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(store, never()).bindWorkflowRoundStarted(any(Long.class), any(Long.class), any(Integer.class),
                any(Long.class), any(Long.class), any(Long.class), any(), any());
        verify(store, never()).compareAndSetApplicationWorkflowContext(any(Long.class), any(Long.class),
                any(Integer.class), any(Long.class), any(Integer.class), any(Long.class), any(Long.class),
                any(Long.class), any(), any(Long.class));
    }

    @Test
    void 流程启动失败时不得绑定轮次或工单上下文() {
        ChangeApplication prepared = application(ApplicationStatus.IN_REVIEW, 0, null, false, 1L);
        SubmissionPreparation preparation =
                new SubmissionPreparation(101L, 1, "{}", DIGEST, List.of());
        stubSubmission(preparation);
        when(store.lockApplication(7L, 101L)).thenReturn(Optional.of(prepared));
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), eq(ACTOR)))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "流程未发布"));

        assertThatThrownBy(() -> service.submit(ACTOR, 101L, 0L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(store).insertPendingWorkflowRound(any());
        verify(store, never()).bindWorkflowRoundStarted(any(Long.class), any(Long.class), any(Integer.class),
                any(Long.class), any(Long.class), any(Long.class), any(), any());
        verify(store, never()).compareAndSetApplicationWorkflowContext(any(Long.class), any(Long.class),
                any(Integer.class), any(Long.class), any(Integer.class), any(Long.class), any(Long.class),
                any(Long.class), any(), any(Long.class));
    }

    @Test
    void 草稿取消继续使用同步工单取消且不调用工作流() {
        ApplicationDetail current = detail(application(ApplicationStatus.DRAFT, 0, null, false, 2L));
        ApplicationDetail cancelled = detail(application(ApplicationStatus.CANCELLED, 0, null, false, 3L));
        when(changes.detail(ACTOR, AccessScope.OWN, 101L)).thenReturn(current);
        when(changes.cancel(ACTOR, AccessScope.OWN, 101L, 2L)).thenReturn(cancelled);

        assertThat(service.cancel(ACTOR, 101L, 2L)).isSameAs(cancelled);

        verify(changes, never()).coordinateCancellation(any(), any(Long.class), any(Long.class), any());
        verify(workflowGateway, never()).terminate(any(), any());
    }

    @Test
    void 审批中取消登记请求并终止当前轮次后等待事件确认() {
        ApplicationDetail current = detail(application(ApplicationStatus.IN_REVIEW, 2, 90L, false, 6L));
        ApplicationDetail requested = detail(application(ApplicationStatus.IN_REVIEW, 2, 90L, true, 7L));
        when(changes.detail(ACTOR, AccessScope.OWN, 101L)).thenReturn(current, requested);
        CancellationPreparation preparation = new CancellationPreparation(101L, 2, 90L, DIGEST);
        doAnswer(invocation -> {
            CancellationCoordinator coordinator = invocation.getArgument(3);
            coordinator.terminate(preparation);
            return preparation;
        }).when(changes).coordinateCancellation(eq(ACTOR), eq(101L), eq(6L),
                any(CancellationCoordinator.class));

        ApplicationDetail result = service.cancel(ACTOR, 101L, 6L);

        assertThat(result).isSameAs(requested);
        ArgumentCaptor<com.ccb.workflow.integration.WorkflowTerminateCommand> command =
                ArgumentCaptor.forClass(com.ccb.workflow.integration.WorkflowTerminateCommand.class);
        verify(workflowGateway).terminate(command.capture(), eq(ACTOR));
        assertThat(command.getValue().instanceId()).isEqualTo(90L);
        assertThat(command.getValue().businessKey()).isEqualTo("101");
        assertThat(command.getValue().businessRound()).isEqualTo(2);
        verify(changes, never()).cancel(any(), any(), any(Long.class), any(Long.class));
    }

    private void stubSubmission(SubmissionPreparation preparation) {
        doAnswer(invocation -> {
            SubmissionCoordinator coordinator = invocation.getArgument(4);
            coordinator.start(preparation);
            return preparation;
        }).when(changes).coordinateSubmission(eq(ACTOR), eq(AccessScope.OWN), eq(101L), eq(0L),
                any(SubmissionCoordinator.class));
    }

    private ApplicationDetail detail(ChangeApplication application) {
        return new ApplicationDetail(application, null, List.of(), List.of());
    }

    private ChangeApplication application(ApplicationStatus status, int round, Long instanceId,
                                          boolean cancellationRequested, long rowVersion) {
        return new ChangeApplication(101L, 7L, TargetKind.LOGICAL, ActionType.CREATE, null, ACTOR.id(),
                "新建渠道系统", status, round, instanceId == null ? null : 80L,
                instanceId == null ? null : 1L, instanceId,
                instanceId == null ? null : DIGEST, cancellationRequested, rowVersion,
                ACTOR.id(), ACTOR.id(), TIME, TIME);
    }
}

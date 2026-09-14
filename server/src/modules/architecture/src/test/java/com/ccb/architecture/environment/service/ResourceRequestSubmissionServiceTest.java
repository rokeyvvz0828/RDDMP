package com.ccb.architecture.environment.service;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore;
import com.ccb.architecture.environment.service.EnvironmentResourceService.ResourceRequestDetail;
import com.ccb.architecture.environment.service.EnvironmentResourceService.SubmissionPreparation;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceRequestSubmissionServiceTest {
    private static final AuthUser ACTOR =
            new AuthUser(9L, 7L, "architect", "hash", "架构师", 11L, true);
    private static final ProjectAccess PROJECT = new ProjectAccess(70L, "PROJECT-A", "项目 A");
    private static final String DIGEST = "a".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 5, 10, 0);

    @Mock
    private EnvironmentResourceService changes;
    @Mock
    private EnvironmentResourceStore store;
    @Mock
    private WorkflowBusinessGateway workflowGateway;

    private ResourceRequestSubmissionService service;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(700L);
        service = new ResourceRequestSubmissionService(changes, store, workflowGateway,
                ids::getAndIncrement, Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 提交工作流携带可信项目上下文并按项目绑定轮次() {
        ResourceRequest prepared = request(RequestStatus.IN_REVIEW, 0, null, 1L);
        SubmissionPreparation preparation = new SubmissionPreparation(101L, 1, DIGEST);
        stubSubmission(preparation);
        when(store.lockRequest(7L, PROJECT.id(), 101L)).thenReturn(Optional.of(prepared));
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), eq(ACTOR)))
                .thenAnswer(invocation -> {
                    WorkflowStartCommand command = invocation.getArgument(0);
                    return new WorkflowStartResult(90L, 80L, 1, "RUNNING", command.context());
                });
        when(store.bindWorkflowRoundStarted(7L, PROJECT.id(), 101L, 1, 80L, 1L, 90L, DIGEST, TIME))
                .thenReturn(true);
        when(store.compareAndSetWorkflowContext(
                7L, PROJECT.id(), 101L, 0, 1L, 1, 80L, 1L, 90L, DIGEST, ACTOR.id()))
                .thenReturn(true);
        ResourceRequestDetail expected = new ResourceRequestDetail(
                request(RequestStatus.IN_REVIEW, 1, 90L, 2L), List.of(), List.of());
        when(changes.detailRequest(ACTOR, PROJECT, EnvironmentResourceService.AccessScope.OWN, 101L))
                .thenReturn(expected);

        assertThat(service.submit(ACTOR, PROJECT, 101L, 0L)).isSameAs(expected);

        ArgumentCaptor<WorkflowStartCommand> command = ArgumentCaptor.forClass(WorkflowStartCommand.class);
        verify(workflowGateway).startByCode(command.capture(), eq(ACTOR));
        assertThat(command.getValue().context().projectRef()).isEqualTo(PROJECT.projectRef());
        assertThat(command.getValue().context().projectName()).isEqualTo(PROJECT.projectName());
        verify(store).insertPendingWorkflowRound(argThat(round -> round.projectId() == PROJECT.id()));
    }

    @Test
    void 平台返回伪造项目上下文时拒绝绑定资源申请() {
        SubmissionPreparation preparation = new SubmissionPreparation(101L, 1, DIGEST);
        stubSubmission(preparation);
        when(store.lockRequest(7L, PROJECT.id(), 101L))
                .thenReturn(Optional.of(request(RequestStatus.IN_REVIEW, 0, null, 1L)));
        WorkflowBusinessContext wrong = new WorkflowBusinessContext(
                "architecture", "架构管理", ResourceRequestSubmissionService.BUSINESS_TYPE, "101",
                "资源申请 RR101", 1, "PROJECT-B", "项目 B",
                "/architecture/resource-requests/101", DIGEST);
        when(workflowGateway.startByCode(any(WorkflowStartCommand.class), eq(ACTOR)))
                .thenReturn(new WorkflowStartResult(90L, 80L, 1, "RUNNING", wrong));

        assertThatThrownBy(() -> service.submit(ACTOR, PROJECT, 101L, 0L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(store, never()).bindWorkflowRoundStarted(anyLong(), anyLong(), anyLong(), anyInt(),
                anyLong(), anyLong(), anyLong(), anyString(), any(LocalDateTime.class));
        verify(store, never()).compareAndSetWorkflowContext(anyLong(), anyLong(), anyLong(), anyInt(),
                anyLong(), anyInt(), anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    }

    @SuppressWarnings("unchecked")
    private void stubSubmission(SubmissionPreparation preparation) {
        doAnswer(invocation -> {
            Consumer<SubmissionPreparation> starter = invocation.getArgument(4);
            starter.accept(preparation);
            return null;
        }).when(changes).coordinateSubmission(eq(ACTOR), eq(PROJECT), eq(101L), eq(0L), any(Consumer.class));
    }

    private ResourceRequest request(RequestStatus status, int round, Long instanceId, long rowVersion) {
        return new ResourceRequest(101L, 7L, PROJECT.id(), "RR101", 100L, "W0001A", "EACP",
                "电子渠道接入", "上海", "architecture.system-level.a-plus", "P8",
                "architecture.disaster-recovery.active-standby", 200L, "DEV-A", "开发环境 A",
                "architecture.environment-type.dev", ACTOR.id(), ACTOR.id(), RequestType.INITIAL,
                "新环境资源", status, round, instanceId == null ? null : 80L,
                instanceId == null ? null : 1L, instanceId, instanceId == null ? null : DIGEST,
                false, rowVersion, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }
}

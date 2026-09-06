package com.ccb.architecture.environment.integration;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceiptStart;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceiptStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRound;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRoundStatus;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore;
import com.ccb.architecture.environment.service.EnvironmentResourceService;
import com.ccb.architecture.environment.service.ResourceRequestSubmissionService;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceRequestWorkflowLifecycleConsumerTest {
    private static final String DIGEST = "a".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 5, 10, 30);
    private static final ProjectAccess PROJECT = new ProjectAccess(70L, "PROJECT-A", "项目 A");

    @Mock
    private EnvironmentResourceStore store;
    @Mock
    private EnvironmentResourceService changes;
    @Mock
    private ProjectAccessService projectAccessService;

    private ResourceRequestWorkflowLifecycleConsumer consumer;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(900L);
        consumer = new ResourceRequestWorkflowLifecycleConsumer(
                store, changes, projectAccessService, ids::getAndIncrement);
    }

    @Test
    void 工作流事件项目名称与可信项目不一致时拒绝且不读取申请() {
        WorkflowLifecycleEvent event = event("event-project-conflict", WorkflowLifecycleEventType.APPROVED,
                PROJECT.projectRef(), "伪造项目名称");
        when(projectAccessService.requireAccessible(eq(PROJECT.projectRef()), any())).thenReturn(PROJECT);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(store, never()).lockRequest(anyLong(), anyLong(), anyLong());
        verify(changes, never()).applyApprovalInCurrentTransaction(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void 批准事件只按可信项目更新申请轮次和回执() {
        WorkflowLifecycleEvent event = event("event-approved", WorkflowLifecycleEventType.APPROVED,
                PROJECT.projectRef(), PROJECT.projectName());
        ResourceRequest request = request();
        WorkflowRound round = new WorkflowRound(700L, 7L, PROJECT.id(), 101L, 2,
                80L, 1L, 90L, DIGEST, WorkflowRoundStatus.STARTED,
                TIME.minusMinutes(5), null, TIME.minusMinutes(6), TIME.minusMinutes(5));
        when(projectAccessService.requireAccessible(eq(PROJECT.projectRef()), any())).thenReturn(PROJECT);
        when(store.lockRequest(7L, PROJECT.id(), 101L)).thenReturn(Optional.of(request));
        when(store.beginReceipt(any(WorkflowReceiptStart.class))).thenReturn(true);
        when(store.lockWorkflowRoundByInstance(7L, PROJECT.id(), 90L)).thenReturn(Optional.of(round));
        when(store.isLatestWorkflowRound(7L, PROJECT.id(), 101L, 2)).thenReturn(true);
        when(store.completeStartedWorkflowRound(
                7L, PROJECT.id(), 101L, 2, WorkflowRoundStatus.APPROVED, TIME)).thenReturn(true);
        when(store.completeReceipt(7L, PROJECT.id(), "event-approved", consumer.subscriberKey(),
                WorkflowReceiptStatus.PROCESSED, "已批准资源申请，实际资源分配待后续搭建任务接入"))
                .thenReturn(true);

        consumer.consume(event);

        verify(changes).applyApprovalInCurrentTransaction(7L, PROJECT.id(), 101L, 6L, 88L);
        verify(store).completeStartedWorkflowRound(
                7L, PROJECT.id(), 101L, 2, WorkflowRoundStatus.APPROVED, TIME);
        verify(projectAccessService).requireAccessible(eq(PROJECT.projectRef()), any());
    }

    private WorkflowLifecycleEvent event(String eventId, WorkflowLifecycleEventType type,
                                         String projectRef, String projectName) {
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "architecture", "架构管理", ResourceRequestSubmissionService.BUSINESS_TYPE, "101",
                "资源申请 RR101", 2, projectRef, projectName,
                "/architecture/resource-requests/101", DIGEST);
        return new WorkflowLifecycleEvent(eventId, 7L, 90L, type, context, 88L, TIME);
    }

    private ResourceRequest request() {
        return new ResourceRequest(101L, 7L, PROJECT.id(), "RR101", 100L, "W0001A", "EACP",
                "电子渠道接入", "上海", "architecture.system-level.a-plus", "P8",
                "architecture.disaster-recovery.active-standby", 200L, "DEV-A", "开发环境 A",
                "architecture.environment-type.dev", 9L, 9L, RequestType.INITIAL, "新环境资源",
                RequestStatus.IN_REVIEW, 2, 80L, 1L, 90L, DIGEST,
                false, 6L, 9L, 9L, TIME.minusHours(1), TIME.minusMinutes(1));
    }
}

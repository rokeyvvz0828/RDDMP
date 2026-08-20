package com.ccb.release.integration;

import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.integration.ReleaseWorkflowStore.RoundSnapshot;
import com.ccb.release.production.service.ReleaseProductionService;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseWorkflowLifecycleConsumerTest {
    private ReleaseApplicationStore applications;
    private ReleaseWorkflowStore workflowStore;
    private ReleaseProductionService productionService;
    private ReleaseWorkflowLifecycleConsumer consumer;

    @BeforeEach
    void setUp() {
        applications = mock(ReleaseApplicationStore.class);
        workflowStore = mock(ReleaseWorkflowStore.class);
        productionService = mock(ReleaseProductionService.class);
        consumer = new ReleaseWorkflowLifecycleConsumer(applications, workflowStore, productionService);
    }

    @Test
    void exposesStableSubscriberIdentityForReleaseApplicationsOnly() {
        assertEquals("release.application.lifecycle.v1", consumer.subscriberKey());
        assertTrue(consumer.supports("release_application"));
    }

    @Test
    void duplicateEventReceiptIsANoOp() {
        WorkflowLifecycleEvent event = event("event-1", WorkflowLifecycleEventType.APPROVED, false);
        Application application = application(false, Status.IN_REVIEW);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(application));
        when(workflowStore.beginReceipt(event, 10L, ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY)).thenReturn(false);

        consumer.consume(event);

        verify(workflowStore, never()).findRoundByInstanceForUpdate(anyLong(), anyLong());
        verify(productionService, never()).refreshReleasedCandidates(anyLong(), any());
    }

    @Test
    void staleRoundIsRecordedAsIgnoredWithoutChangingApplication() {
        WorkflowLifecycleEvent event = event("event-2", WorkflowLifecycleEventType.APPROVED, false);
        Application application = application(false, Status.IN_REVIEW);
        RoundSnapshot round = round("IN_REVIEW", false);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(application));
        when(workflowStore.beginReceipt(event, 10L, ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY)).thenReturn(true);
        when(workflowStore.findRoundByInstanceForUpdate(1L, 90L)).thenReturn(Optional.of(round));
        when(workflowStore.isLatestRound(1L, 10L, 2)).thenReturn(false);

        consumer.consume(event);

        verify(workflowStore).completeReceipt(1L, "event-2", ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY,
                "IGNORED");
        verify(workflowStore, never()).markApproved(any(), anyLong(), any(), anyLong());
        verify(productionService, never()).refreshReleasedCandidates(anyLong(), any());
    }

    @Test
    void emergencyApprovalAssignsReceivingWindowAndRefreshesCandidateOnce() {
        WorkflowLifecycleEvent event = event("event-3", WorkflowLifecycleEventType.APPROVED, true);
        Application application = application(true, Status.IN_REVIEW);
        RoundSnapshot round = round("IN_REVIEW", true);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(application));
        when(workflowStore.beginReceipt(event, 10L, ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY)).thenReturn(true);
        when(workflowStore.findRoundByInstanceForUpdate(1L, 90L)).thenReturn(Optional.of(round));
        when(workflowStore.isLatestRound(1L, 10L, 2)).thenReturn(true);
        when(workflowStore.findReceivingWindow(1L, "P-001", event.occurredAt())).thenReturn(Optional.of(25L));
        when(workflowStore.markApproved(application, 25L, event.occurredAt(), 9L)).thenReturn(true);
        when(workflowStore.completeRound(1L, 30L, "IN_REVIEW", "APPROVED", event.occurredAt())).thenReturn(true);

        consumer.consume(event);

        verify(productionService).refreshReleasedCandidates(eq(10L), any());
        verify(workflowStore).completeReceipt(1L, "event-3", ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY,
                "PROCESSED");
        verify(applications).appendEvent(anyLong(), eq(1L), eq(10L), eq("WORKFLOW_APPROVED"),
                eq(Status.IN_REVIEW), eq(Status.RELEASED), eq(null), any(), eq(9L), eq("工作流审批"));
    }

    @Test
    void returnedEventMovesOnlyTheActiveReviewRoundBackToEditableState() {
        WorkflowLifecycleEvent event = event("event-4", WorkflowLifecycleEventType.RETURNED, false);
        Application application = application(false, Status.IN_REVIEW);
        RoundSnapshot round = round("IN_REVIEW", false);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(application));
        when(workflowStore.beginReceipt(event, 10L, ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY)).thenReturn(true);
        when(workflowStore.findRoundByInstanceForUpdate(1L, 90L)).thenReturn(Optional.of(round));
        when(workflowStore.isLatestRound(1L, 10L, 2)).thenReturn(true);
        when(workflowStore.markReturned(application, 9L)).thenReturn(true);
        when(workflowStore.completeRound(1L, 30L, "IN_REVIEW", "RETURNED", event.occurredAt())).thenReturn(true);

        consumer.consume(event);

        verify(workflowStore).markReturned(application, 9L);
        verify(productionService, never()).refreshReleasedCandidates(anyLong(), any());
        verify(workflowStore).completeReceipt(1L, "event-4", ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY,
                "PROCESSED");
    }

    @Test
    void terminatedWithdrawRequestBecomesWithdrawn() {
        WorkflowLifecycleEvent event = event("event-5", WorkflowLifecycleEventType.TERMINATED, false);
        Application application = application(false, Status.IN_REVIEW);
        RoundSnapshot round = round("WITHDRAW_REQUESTED", false);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(application));
        when(workflowStore.beginReceipt(event, 10L, ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY)).thenReturn(true);
        when(workflowStore.findRoundByInstanceForUpdate(1L, 90L)).thenReturn(Optional.of(round));
        when(workflowStore.isLatestRound(1L, 10L, 2)).thenReturn(true);
        when(workflowStore.markWithdrawn(application, 9L)).thenReturn(true);
        when(workflowStore.completeRound(1L, 30L, "WITHDRAW_REQUESTED", "TERMINATED", event.occurredAt()))
                .thenReturn(true);

        consumer.consume(event);

        verify(workflowStore).markWithdrawn(application, 9L);
        verify(applications).appendEvent(anyLong(), eq(1L), eq(10L), eq("WORKFLOW_TERMINATED"),
                eq(Status.IN_REVIEW), eq(Status.WITHDRAWN), eq(null), any(), eq(9L), eq("工作流审批"));
    }

    @Test
    void terminatedCancelRequestBecomesPermanentlyCancelled() {
        WorkflowLifecycleEvent event = event("event-6", WorkflowLifecycleEventType.TERMINATED, false);
        Application application = application(false, Status.IN_REVIEW);
        RoundSnapshot round = round("CANCEL_REQUESTED", false);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(application));
        when(workflowStore.beginReceipt(event, 10L, ReleaseWorkflowLifecycleConsumer.SUBSCRIBER_KEY)).thenReturn(true);
        when(workflowStore.findRoundByInstanceForUpdate(1L, 90L)).thenReturn(Optional.of(round));
        when(workflowStore.isLatestRound(1L, 10L, 2)).thenReturn(true);
        when(workflowStore.markCancelled(application, 9L)).thenReturn(true);
        when(workflowStore.completeRound(1L, 30L, "CANCEL_REQUESTED", "TERMINATED", event.occurredAt()))
                .thenReturn(true);

        consumer.consume(event);

        verify(workflowStore).markCancelled(application, 9L);
        verify(workflowStore, never()).markWithdrawn(any(), anyLong());
        verify(applications).appendEvent(anyLong(), eq(1L), eq(10L), eq("WORKFLOW_TERMINATED"),
                eq(Status.IN_REVIEW), eq(Status.CANCELLED), eq(null), any(), eq(9L), eq("工作流审批"));
    }

    private WorkflowLifecycleEvent event(String id, WorkflowLifecycleEventType type, boolean emergency) {
        String digest = emergency ? "b".repeat(64) : "a".repeat(64);
        WorkflowBusinessContext context = new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-001",
                "版本申请 SQ-001", 2, "P-001", "项目", "/release/applications/SQ-001", digest);
        return new WorkflowLifecycleEvent(id, 1L, 90L, type, context, 9L,
                LocalDateTime.of(2026, 8, 15, 14, 30));
    }

    private RoundSnapshot round(String status, boolean emergency) {
        return new RoundSnapshot(30L, 1L, 10L, 2, emergency ? "release.emergency" : "release.regular",
                80L, 3, 90L, status, emergency ? "b".repeat(64) : "a".repeat(64),
                LocalDateTime.of(2026, 8, 15, 9, 0), null);
    }

    private Application application(boolean emergency, Status status) {
        return new Application(10L, 1L, "SQ-001", "P-001", "P001", "项目", emergency,
                emergency ? null : 20L, null, "SYS-1", "SYS1", "用户中心",
                emergency ? VersionType.EMERGENCY : VersionType.REGULAR, Characteristic.STANDARD,
                emergency ? "release.emergency" : "release.regular", status, 7L, "研发人员", "研发部",
                emergency ? "应急说明" : null, null, "版本说明", null, 3L, 7L, 7L,
                LocalDateTime.of(2026, 8, 14, 9, 0), LocalDateTime.of(2026, 8, 14, 9, 0),
                List.of(new DeliverySnapshot(40L, "DU-1", "UNIT-A", "用户服务", ArtifactType.IMAGE, "v1")),
                emergency ? List.of() : List.of("REQ-001"));
    }
}

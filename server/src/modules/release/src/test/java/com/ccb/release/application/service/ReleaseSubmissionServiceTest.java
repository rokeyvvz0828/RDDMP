package com.ccb.release.application.service;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictReport;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryItemType;
import com.ccb.release.application.model.ReleaseApplicationModels.StateActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.application.service.ReleaseSubmissionService.AttachmentInput;
import com.ccb.release.application.service.ReleaseSubmissionService.SubmitRequest;
import com.ccb.release.integration.ReleaseWorkflowStore;
import com.ccb.release.integration.ReleaseWorkflowStore.AttachmentSnapshot;
import com.ccb.release.integration.ReleaseWorkflowStore.RoundSnapshot;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.ResolvedBinding;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
import com.ccb.release.workflow.service.ReleaseWorkflowBindingService;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowStartDefinitionCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseSubmissionServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true, "研发部", null);
    private ReleaseApplicationStore applications;
    private ReleaseWindowStore windows;
    private ReleaseScenarioPolicy scenarios;
    private ReleaseApplicationService applicationService;
    private ReleaseWorkflowStore workflowStore;
    private ReleaseWorkflowBindingService workflowBindings;
    private WorkflowBusinessGateway workflowGateway;
    private AttachmentGateway attachmentGateway;
    private ReleaseSubmissionService service;

    @BeforeEach
    void setUp() {
        applications = mock(ReleaseApplicationStore.class);
        windows = mock(ReleaseWindowStore.class);
        scenarios = mock(ReleaseScenarioPolicy.class);
        applicationService = mock(ReleaseApplicationService.class);
        workflowStore = mock(ReleaseWorkflowStore.class);
        workflowBindings = mock(ReleaseWorkflowBindingService.class);
        workflowGateway = mock(WorkflowBusinessGateway.class);
        attachmentGateway = mock(AttachmentGateway.class);
        service = new ReleaseSubmissionService(applications, windows, scenarios, applicationService, workflowStore,
                workflowBindings, workflowGateway, attachmentGateway, new ObjectMapper().findAndRegisterModules());

        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(application(false, Status.DRAFT)));
        when(applicationService.conflicts("SQ-001", USER)).thenReturn(ConflictReport.empty());
        when(windows.findById(20L, 1L)).thenReturn(Optional.of(window()));
        when(scenarios.nonEmergency(any(), eq(false))).thenReturn(
                new ReleaseScenarioPolicy.Scenario(VersionType.REGULAR, Characteristic.STANDARD,
                        Scene.REGULAR, true, null));
        when(workflowBindings.resolve("P-001", Scene.REGULAR, USER)).thenReturn(
                new ResolvedBinding(Scene.REGULAR, 80L, "release.regular", "常规版本审批", 4));
        when(workflowStore.findActiveAttachments(1L, 10L)).thenReturn(List.of());
        when(workflowStore.nextRoundNo(1L, 10L)).thenReturn(1);
        when(workflowStore.insertStartingRound(any(), eq(1), eq("release.regular"), any())).thenReturn(30L);
    }

    @Test
    void missingPublishedWorkflowDoesNotAdvanceBusinessStatus() {
        when(workflowBindings.resolve("P-001", Scene.REGULAR, USER))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "常规版本未配置审批流程"));

        assertCode(ErrorCode.CONFLICT, () -> service.submit("SQ-001", new SubmitRequest(3, null, List.of()), USER, false));

        verify(workflowStore, never()).completeWorkflowStart(anyLong(), anyLong(), any());
        verify(workflowStore, never()).transitionApplicationToReview(any(), anyLong(), any(), any(), any(), anyLong());
        verify(workflowStore, never()).insertStartingRound(any(), anyInt(), any(), any());
    }

    @Test
    void inReviewConflictCannotBeBypassedWithMatchingToken() {
        ConflictReport conflicts = mock(ConflictReport.class);
        when(conflicts.hasInReview()).thenReturn(true);
        when(conflicts.conflictToken()).thenReturn("matching-token");
        when(applicationService.conflicts("SQ-001", USER)).thenReturn(conflicts);

        assertCode(ErrorCode.CONFLICT, () -> service.submit("SQ-001",
                new SubmitRequest(3, "matching-token", List.of()), USER, false));

        verify(workflowBindings, never()).resolve(any(), any(), any());
        verify(workflowStore, never()).insertStartingRound(any(), anyInt(), any(), any());
        verify(workflowGateway, never()).startByDefinitionId(any(), any());
    }

    @Test
    void successfulSubmitStoresRoundDigestAndWorkflowIdentity() {
        when(workflowStore.completeWorkflowStart(eq(30L), eq(1L), any())).thenReturn(true);
        when(workflowStore.transitionApplicationToReview(any(), eq(3L), eq(VersionType.REGULAR),
                eq(Characteristic.STANDARD), eq("release.regular"), eq(7L))).thenReturn(true);
        when(workflowGateway.startByDefinitionId(any(), eq(USER))).thenAnswer(invocation -> {
            WorkflowStartDefinitionCommand command = invocation.getArgument(0);
            return new WorkflowStartResult(90L, 80L, 4, "RUNNING", command.context());
        });

        var result = service.submit("SQ-001", new SubmitRequest(3, null, List.of()), USER, false);

        assertEquals("IN_REVIEW", result.status());
        assertEquals(90L, result.workflowInstanceId());
        assertEquals(4L, result.rowVersion());
        assertEquals(64, result.dataDigest().length());
        ArgumentCaptor<WorkflowStartDefinitionCommand> command = ArgumentCaptor.forClass(WorkflowStartDefinitionCommand.class);
        verify(workflowGateway).startByDefinitionId(command.capture(), eq(USER));
        assertEquals(80L, command.getValue().definitionId());
        assertEquals("release", command.getValue().context().moduleCode());
        assertEquals("配置管理", command.getValue().context().moduleName());
        assertEquals("release_application", command.getValue().context().businessType());
        assertEquals("/release/applications/SQ-001", command.getValue().context().actionPath());
        assertEquals(result.dataDigest(), command.getValue().context().dataDigest());
        verify(applications).appendEvent(anyLong(), eq(1L), eq(10L), eq("SUBMITTED"), eq(Status.DRAFT),
                eq(Status.IN_REVIEW), eq(null), any(), eq(7L), eq("研发人员"));
    }

    @Test
    void fileOnlyApplicationCanBeSubmittedWithoutInventingAVersion() {
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(fileOnlyApplication()));
        when(workflowStore.completeWorkflowStart(eq(30L), eq(1L), any())).thenReturn(true);
        when(workflowStore.transitionApplicationToReview(any(), eq(3L), eq(VersionType.REGULAR),
                eq(Characteristic.STANDARD), eq("release.regular"), eq(7L))).thenReturn(true);
        when(workflowGateway.startByDefinitionId(any(), eq(USER))).thenAnswer(invocation -> {
            WorkflowStartDefinitionCommand command = invocation.getArgument(0);
            return new WorkflowStartResult(90L, 80L, 4, "RUNNING", command.context());
        });

        var result = service.submit("SQ-001", new SubmitRequest(3, null, List.of()), USER, false);

        assertEquals("IN_REVIEW", result.status());
        assertEquals(64, result.dataDigest().length());
    }

    @Test
    void rejectsAttachmentThatIsNotAccessibleToCurrentUploader() {
        when(attachmentGateway.get(51L, USER)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问附件"));

        assertCode(ErrorCode.FORBIDDEN, () -> service.submit("SQ-001",
                new SubmitRequest(3, null, List.of(new AttachmentInput(51L, "SUPPORTING"))), USER, false));

        verify(workflowGateway, never()).startByDefinitionId(any(), any());
    }

    @Test
    void emergencyRequiresReceivingWindowAndPersistedTestReport() {
        Application emergency = application(true, Status.DRAFT);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(emergency));
        when(scenarios.emergency(false)).thenReturn(new ReleaseScenarioPolicy.Scenario(VersionType.EMERGENCY,
                Characteristic.STANDARD, Scene.EMERGENCY, true, null));
        when(workflowBindings.resolve("P-001", Scene.EMERGENCY, USER)).thenReturn(
                new ResolvedBinding(Scene.EMERGENCY, 81L, "release.emergency", "应急版本审批", 2));

        when(workflowStore.findReceivingWindow(eq(1L), eq("P-001"), any())).thenReturn(Optional.empty());
        assertCode(ErrorCode.CONFLICT, () -> service.submit("SQ-001", new SubmitRequest(3, null, List.of()), USER, false));
        verify(workflowGateway, never()).startByDefinitionId(any(), any());

        when(workflowStore.findReceivingWindow(eq(1L), eq("P-001"), any())).thenReturn(Optional.of(20L));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.submit("SQ-001", new SubmitRequest(3, null, List.of()), USER, false));
        verify(workflowGateway, never()).startByDefinitionId(any(), any());
    }

    @Test
    void emergencyBindsTestReportBeforeStartingWorkflow() {
        Application emergency = application(true, Status.DRAFT);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(emergency));
        when(scenarios.emergency(false)).thenReturn(new ReleaseScenarioPolicy.Scenario(VersionType.EMERGENCY,
                Characteristic.STANDARD, Scene.EMERGENCY, true, null));
        when(workflowBindings.resolve("P-001", Scene.EMERGENCY, USER)).thenReturn(
                new ResolvedBinding(Scene.EMERGENCY, 81L, "release.emergency", "应急版本审批", 2));
        when(workflowStore.findReceivingWindow(eq(1L), eq("P-001"), any())).thenReturn(Optional.of(20L));
        when(attachmentGateway.get(51L, USER)).thenReturn(new AttachmentItem(51L, "测试报告.pdf", "application/pdf",
                100, "pdf", "TEMP", null, null, null, 7L, LocalDateTime.now()));
        when(workflowStore.findActiveAttachments(1L, 10L)).thenReturn(List.of(),
                List.of(new AttachmentSnapshot(51L, "TEST_REPORT", "测试报告.pdf", 3)));
        when(workflowStore.insertStartingRound(any(), eq(1), eq("release.emergency"), any())).thenReturn(30L);
        when(workflowStore.completeWorkflowStart(eq(30L), eq(1L), any())).thenReturn(true);
        when(workflowStore.transitionApplicationToReview(any(), eq(3L), eq(VersionType.EMERGENCY),
                eq(Characteristic.STANDARD), eq("release.emergency"), eq(7L))).thenReturn(true);
        when(workflowGateway.startByDefinitionId(any(), eq(USER))).thenAnswer(invocation -> {
            WorkflowStartDefinitionCommand command = invocation.getArgument(0);
            return new WorkflowStartResult(90L, 81L, 2, "RUNNING", command.context());
        });

        var result = service.submit("SQ-001",
                new SubmitRequest(3, null, List.of(new AttachmentInput(51L, "TEST_REPORT"))), USER, false);

        assertEquals("IN_REVIEW", result.status());
        verify(attachmentGateway).bind(any(), eq(USER));
        verify(workflowStore).insertAttachment(1L, 10L, 51L, "TEST_REPORT", "测试报告.pdf", 3L);
    }

    @Test
    void withdrawTerminatesTheActiveWorkflowWithoutDirectlyChangingApplicationStatus() {
        Application reviewing = application(false, Status.IN_REVIEW);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(reviewing));
        when(workflowStore.findLatestRoundForUpdate(1L, 10L)).thenReturn(Optional.of(
                new RoundSnapshot(30L, 1L, 10L, 2, "release.regular", 80L, 4, 90L,
                        "IN_REVIEW", "a".repeat(64), LocalDateTime.now(), null)));
        when(workflowStore.markWithdrawalRequested(1L, 30L)).thenReturn(true);

        var result = service.withdraw("SQ-001", new StateActionRequest(3, "版本内容需调整"), USER, false);

        assertEquals("WITHDRAW_REQUESTED", result.operationStatus());
        verify(workflowGateway).terminate(any(), eq(USER));
        verify(workflowStore, never()).markWithdrawn(any(), anyLong());
    }

    @Test
    void conflictCancelTerminatesTheActiveWorkflowWithPermanentCancelIntent() {
        Application reviewing = application(false, Status.IN_REVIEW);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(reviewing));
        when(workflowStore.findLatestRoundForUpdate(1L, 10L)).thenReturn(Optional.of(
                new RoundSnapshot(30L, 1L, 10L, 2, "release.regular", 80L, 4, 90L,
                        "IN_REVIEW", "a".repeat(64), LocalDateTime.now(), null)));
        when(workflowStore.markCancelRequested(1L, 30L)).thenReturn(true);

        var result = service.conflictCancel("SQ-001", new StateActionRequest(3, "改由新申请继续"), USER, false);

        assertEquals("IN_REVIEW", result.status());
        assertEquals("CANCEL_REQUESTED", result.operationStatus());
        verify(workflowGateway).terminate(any(), eq(USER));
        verify(workflowStore, never()).markCancelled(any(), anyLong());
        verify(applications).appendEvent(anyLong(), eq(1L), eq(10L), eq("CANCEL_REQUESTED"),
                eq(Status.IN_REVIEW), eq(Status.IN_REVIEW), eq("改由新申请继续"), any(), eq(7L), eq("研发人员"));
    }

    @Test
    void conflictCancelRejectsInvalidOwnerStateVersionAndReason() {
        Application reviewing = application(false, Status.IN_REVIEW);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(reviewing));
        AuthUser other = new AuthUser(9L, 1L, "other", "", "其他人", 11L, true, "研发部", null);

        assertCode(ErrorCode.FORBIDDEN, () -> service.conflictCancel("SQ-001",
                new StateActionRequest(3, "取消"), other, false));
        assertCode(ErrorCode.CONFLICT, () -> service.conflictCancel("SQ-001",
                new StateActionRequest(2, "取消"), USER, false));

        when(applications.findByCodeForUpdate("SQ-001", 1L))
                .thenReturn(Optional.of(application(false, Status.DRAFT)));
        assertCode(ErrorCode.CONFLICT, () -> service.conflictCancel("SQ-001",
                new StateActionRequest(3, "取消"), USER, false));

        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(reviewing));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.conflictCancel("SQ-001",
                new StateActionRequest(3, " "), USER, false));
    }

    @Test
    void conflictCancelDoesNotRecordRequestEventWhenWorkflowTerminationFails() {
        Application reviewing = application(false, Status.IN_REVIEW);
        when(applications.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(reviewing));
        when(workflowStore.findLatestRoundForUpdate(1L, 10L)).thenReturn(Optional.of(
                new RoundSnapshot(30L, 1L, 10L, 2, "release.regular", 80L, 4, 90L,
                        "IN_REVIEW", "a".repeat(64), LocalDateTime.now(), null)));
        when(workflowStore.markCancelRequested(1L, 30L)).thenReturn(true);
        doThrow(new IllegalStateException("workflow unavailable"))
                .when(workflowGateway).terminate(any(), eq(USER));

        assertThrows(IllegalStateException.class, () -> service.conflictCancel("SQ-001",
                new StateActionRequest(3, "改由新申请继续"), USER, false));

        verify(applications, never()).appendEvent(anyLong(), anyLong(), anyLong(), eq("CANCEL_REQUESTED"),
                any(), any(), any(), any(), anyLong(), any());
        verify(workflowStore, never()).markCancelled(any(), anyLong());
    }

    @Test
    void cancelledApplicationRejectsSubmissionAndAttachmentDeletion() {
        when(applications.findByCodeForUpdate("SQ-001", 1L))
                .thenReturn(Optional.of(application(false, Status.CANCELLED)));

        assertCode(ErrorCode.CONFLICT, () -> service.submit("SQ-001",
                new SubmitRequest(3, null, List.of()), USER, false));
        assertCode(ErrorCode.CONFLICT, () -> service.deleteAttachment("SQ-001", 51L,
                new StateActionRequest(3, "删除"), USER, false));

        verify(workflowGateway, never()).startByDefinitionId(any(), any());
        verify(attachmentGateway, never()).deleteBound(anyLong(), any(), any(), any());
    }

    private Application application(boolean emergency, Status status) {
        return new Application(10L, 1L, "SQ-001", "P-001", "P001", "项目", emergency,
                emergency ? null : 20L, null, "SYS-1", "SYS1", "用户中心",
                emergency ? VersionType.EMERGENCY : VersionType.REGULAR, Characteristic.STANDARD,
                emergency ? "release.emergency" : "release.regular", status, 7L, "研发人员", "研发部",
                emergency ? "P1故障修复及测试说明" : null, null, "版本说明", null, 3L, 7L, 7L,
                LocalDateTime.of(2026, 8, 14, 9, 0), LocalDateTime.of(2026, 8, 14, 9, 0),
                List.of(new DeliverySnapshot(40L, "DU-1", "UNIT-A", "用户服务", ArtifactType.IMAGE, "v1")),
                emergency ? List.of() : List.of("REQ-001"));
    }

    private Application fileOnlyApplication() {
        String path = "/deploy/packages/app.zip";
        return new Application(10L, 1L, "SQ-001", "P-001", "P001", "项目", false,
                20L, null, "SYS-1", "SYS1", "用户中心", VersionType.REGULAR, Characteristic.STANDARD,
                "release.regular", Status.DRAFT, 7L, "研发人员", "研发部", null, null, "版本说明", null,
                3L, 7L, 7L, LocalDateTime.of(2026, 8, 14, 9, 0), LocalDateTime.of(2026, 8, 14, 9, 0),
                List.of(new DeliverySnapshot(41L, "FILE", "FILE", "文件介质", ArtifactType.FILE, null,
                        DeliveryItemType.FILE_MEDIA, path, ReleaseApplicationService.fileItemKey(path))),
                List.of("REQ-001"));
    }

    private ReleaseWindow window() {
        return new ReleaseWindow(20L, 1L, "WIN-202608-001", "八月窗口", "P-001", "P001", "项目",
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 20, 0, 0),
                LocalDateTime.of(2026, 8, 25, 0, 0), LocalDateTime.of(2026, 8, 26, 0, 0), true, null,
                0, 7, 7, LocalDateTime.now(), LocalDateTime.now());
    }

    private void assertCode(int code, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(code, error.code());
        assertNotNull(error.getMessage());
        assertTrue(!error.getMessage().isBlank());
    }
}

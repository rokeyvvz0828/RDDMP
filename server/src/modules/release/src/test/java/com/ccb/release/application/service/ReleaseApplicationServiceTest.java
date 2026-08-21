package com.ccb.release.application.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.CreateRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryInput;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryItemType;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.FileMediaInput;
import com.ccb.release.application.model.ReleaseApplicationModels.StateActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.UpdateRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseApplicationServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true, "研发部", null);
    private ReleaseApplicationStore store;
    private ReleaseWindowStore windows;
    private ReleaseApplicationService service;

    @BeforeEach
    void setUp() {
        store = mock(ReleaseApplicationStore.class);
        windows = mock(ReleaseWindowStore.class);
        var clock = Clock.fixed(Instant.parse("2026-08-15T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ReleaseApplicationService(store, windows, new ReleaseScenarioPolicy(clock),
                new ObjectMapper().findAndRegisterModules());
        when(windows.findById(20L, 1L)).thenReturn(Optional.of(window()));
        when(store.nextMonthlySequence(eq(1L), any())).thenReturn(1);
    }

    @Test
    void persistsNormalizedSnapshotsAndDerivesUrgentAdditionalScenario() {
        Application released = application("SQ-OLD", Status.RELEASED, "v1", 3, 8L);
        when(store.findConflictIds(eq(1L), eq(20L), eq(List.of("UNIT:UNIT-A")), any())).thenReturn(List.of(80L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(released));
        when(store.findByCode(any(), eq(1L))).thenReturn(Optional.empty());

        var response = service.create(nonEmergency("v2"), USER);

        ArgumentCaptor<Application> inserted = ArgumentCaptor.forClass(Application.class);
        verify(store).insert(inserted.capture());
        assertEquals(VersionType.URGENT, inserted.getValue().versionType());
        assertEquals(Characteristic.ADDITIONAL, inserted.getValue().characteristic());
        assertEquals(null, inserted.getValue().workflowCode());
        assertEquals("P-001", inserted.getValue().projectId());
        assertEquals(ArtifactType.IMAGE, inserted.getValue().deliveries().get(0).artifactType());
        assertEquals(List.of("REQ-001"), inserted.getValue().requirementCodes());
        assertTrue(response.conflicts().hasConflicts());
        assertNotNull(response.conflicts().conflictToken());
        assertEquals("SQ-OLD", response.conflicts().applications().get(0).application().applicationCode());
        assertEquals("v1", response.conflicts().applications().get(0).versionChanges().get(0).previousVersion());
        verify(store).insertRelation(anyLong(), eq(1L), anyLong(), eq(80L), eq("UNIT-A"),
                eq(DeliveryItemType.DELIVERY_UNIT), eq("UNIT:UNIT-A"), eq(null), eq("ADDITIONAL"),
                eq("v1"), eq("v2"), any(), eq(7L));
    }

    @Test
    void previewsInReviewConflictWithoutPersistenceOrCreateNewAction() {
        Application reviewing = application("SQ-OLD", Status.IN_REVIEW, "v1", 4, 7L);
        when(store.findConflictIds(eq(1L), eq(20L), eq(List.of("UNIT:UNIT-A")), any()))
                .thenReturn(List.of(80L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(reviewing));

        var report = service.preview(nonEmergency("v2"), USER);

        assertTrue(report.hasInReview());
        assertEquals("SQ-OLD", report.applications().get(0).application().applicationCode());
        assertFalse(report.applications().get(0).allowedActions().contains("CREATE_NEW"));
        verify(store, never()).nextMonthlySequence(anyLong(), any());
        verify(store, never()).insert(any());
        verify(store, never()).update(any(), anyLong());
    }

    @Test
    void createRejectsInReviewConflictBeforeAllocatingSequence() {
        Application reviewing = application("SQ-OLD", Status.IN_REVIEW, "v1", 4, 7L);
        when(store.findConflictIds(eq(1L), eq(20L), eq(List.of("UNIT:UNIT-A")), any()))
                .thenReturn(List.of(80L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(reviewing));

        assertCode(ErrorCode.CONFLICT, () -> service.create(nonEmergency("v2"), USER));

        verify(store, never()).nextMonthlySequence(anyLong(), any());
        verify(store, never()).insert(any());
    }

    @Test
    void updateRejectsInReviewConflictWithoutPersistence() {
        Application current = application("SQ-001", Status.DRAFT, "v2", 3, 7L);
        Application reviewing = application("SQ-OLD", Status.IN_REVIEW, "v1", 4, 7L);
        when(store.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(current));
        when(store.findConflictIds(1L, 20L, List.of("UNIT:UNIT-A"), 10L)).thenReturn(List.of(80L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(reviewing));

        assertCode(ErrorCode.CONFLICT, () -> service.update("SQ-001", update("v3", 3), USER, false));

        verify(store, never()).update(any(), anyLong());
    }

    @Test
    void updatePreviewReturnsInReviewConflictWithoutPersistence() {
        Application current = application("SQ-001", Status.DRAFT, "v2", 3, 7L);
        Application reviewing = application("SQ-OLD", Status.IN_REVIEW, "v1", 4, 7L);
        when(store.findByCode("SQ-001", 1L)).thenReturn(Optional.of(current));
        when(store.findConflictIds(1L, 20L, List.of("UNIT:UNIT-A"), 10L)).thenReturn(List.of(80L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(reviewing));

        var report = service.preview("SQ-001", update("v3", 3), USER, false);

        assertTrue(report.hasInReview());
        assertFalse(report.applications().get(0).allowedActions().contains("CREATE_NEW"));
        verify(store, never()).insert(any());
        verify(store, never()).update(any(), anyLong());
    }

    @Test
    void emergencyRequiresDescriptionAndIgnoresHistoricalConflicts() {
        CreateRequest invalid = new CreateRequest(true, null, "P-001", "P001", "项目", "SYS-1", "SYS1", "系统",
                deliveries("v1"), List.of(), null, null, null);
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(invalid, USER));

        CreateRequest valid = new CreateRequest(true, null, "P-001", "P001", "项目", "SYS-1", "SYS1", "系统",
                deliveries("v1"), List.of(), "P0故障应急修复", null, null);
        when(store.findByCode(any(), eq(1L))).thenReturn(Optional.empty());
        var response = service.create(valid, USER);

        assertEquals("EMERGENCY", response.versionType());
        assertEquals(null, response.workflowCode());
        assertFalse(response.conflicts().hasConflicts());
        verify(store, never()).findConflictIds(anyLong(), anyLong(), any(), any());
    }

    @Test
    void rejectsDuplicateUnitsWhitespaceVersionAndMissingRequirements() {
        CreateRequest duplicate = new CreateRequest(false, 20L, "P-001", "P001", "项目", "SYS-1", "SYS1", "系统",
                List.of(deliveries("v1").get(0), deliveries("v2").get(0)), List.of("REQ-1"), null, "紧急", null);
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(duplicate, USER));

        CreateRequest whitespace = new CreateRequest(false, 20L, "P-001", "P001", "项目", "SYS-1", "SYS1", "系统",
                deliveries("v 1"), List.of("REQ-1"), null, "紧急", null);
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(whitespace, USER));

        CreateRequest missingRequirement = new CreateRequest(false, 20L, "P-001", "P001", "项目", "SYS-1", "SYS1", "系统",
                deliveries("v1"), List.of(), null, "紧急", null);
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(missingRequirement, USER));
    }

    @Test
    void acceptsFileOnlyAndMixedApplicationsWithServerOwnedMetadata() {
        when(store.findByCode(any(), eq(1L))).thenReturn(Optional.empty());
        CreateRequest fileOnly = fileRequest(List.of(new FileMediaInput("  /deploy/packages/app.zip  ")));

        var response = service.create(fileOnly, USER);

        ArgumentCaptor<Application> inserted = ArgumentCaptor.forClass(Application.class);
        verify(store).insert(inserted.capture());
        DeliverySnapshot file = inserted.getValue().deliveries().get(0);
        assertEquals(DeliveryItemType.FILE_MEDIA, file.itemType());
        assertEquals("FILE", file.deliveryUnitId());
        assertEquals("FILE", file.deliveryUnitCode());
        assertEquals("文件介质", file.deliveryUnitName());
        assertEquals(ArtifactType.FILE, file.artifactType());
        assertEquals(null, file.artifactVersion());
        assertEquals("/deploy/packages/app.zip", file.filePath());
        assertEquals(ReleaseApplicationService.fileItemKey("/deploy/packages/app.zip"), file.itemKey());
        assertTrue(response.deliveries().isEmpty());
        assertEquals(List.of("/deploy/packages/app.zip"), response.fileMedia().stream().map(item -> item.filePath()).toList());

        CreateRequest mixed = new CreateRequest(false, 20L, "P-001", "P001", "项目", "SYS-1", "SYS1", "用户中心",
                deliveries("v1"), List.of(new FileMediaInput("/deploy/config.yml")), List.of("REQ-001"),
                null, "超过申报截止时间", "版本说明");
        service.create(mixed, USER);
        verify(store, org.mockito.Mockito.times(2)).insert(any());
    }

    @Test
    void rejectsMissingDuplicateOversizedAndControlCharacterFilePaths() {
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(fileRequest(List.of()), USER));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(fileRequest(
                List.of(new FileMediaInput(" /same/path "), new FileMediaInput("/same/path"))), USER));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(fileRequest(
                List.of(new FileMediaInput("x".repeat(1025)))), USER));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(fileRequest(
                List.of(new FileMediaInput("/deploy/app\n.zip"))), USER));
    }

    @Test
    void sameReleasedFilePathIsAdditionalAndDifferentPathsRemainIndependent() {
        String path = "/deploy/packages/app.zip";
        String key = ReleaseApplicationService.fileItemKey(path);
        Application released = fileApplication("SQ-OLD", Status.RELEASED, path, 3L);
        when(store.findConflictIds(eq(1L), eq(20L), eq(List.of(key)), any())).thenReturn(List.of(80L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(released));
        when(store.findByCode(any(), eq(1L))).thenReturn(Optional.empty());

        var response = service.create(fileRequest(List.of(new FileMediaInput(path))), USER);

        assertEquals("ADDITIONAL", response.characteristic());
        assertTrue(response.conflicts().hasConflicts());
        assertEquals(path, response.conflicts().applications().get(0).versionChanges().get(0).currentVersion());
        verify(store).insertRelation(anyLong(), eq(1L), anyLong(), eq(80L), eq("FILE"),
                eq(DeliveryItemType.FILE_MEDIA), eq(key), eq(path), eq("ADDITIONAL"), eq(null), eq(null), any(), eq(7L));

        service.create(fileRequest(List.of(new FileMediaInput("/deploy/packages/other.zip"))), USER);
        verify(store, org.mockito.Mockito.atLeastOnce()).findConflictIds(eq(1L), eq(20L), eq(List.of(
                ReleaseApplicationService.fileItemKey("/deploy/packages/other.zip"))), any());
    }

    @Test
    void enforcesOwnerEditableStateAndOptimisticVersion() {
        Application draft = application("SQ-001", Status.DRAFT, "v1", 3, 7L);
        when(store.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(draft));

        UpdateRequest stale = update("v2", 2);
        assertCode(ErrorCode.CONFLICT, () -> service.update("SQ-001", stale, USER, false));

        AuthUser other = new AuthUser(9L, 1L, "other", "", "其他人", 1L, true);
        assertCode(ErrorCode.FORBIDDEN, () -> service.update("SQ-001", update("v2", 3), other, false));

        Application reviewing = application("SQ-001", Status.IN_REVIEW, "v1", 3, 7L);
        when(store.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(reviewing));
        assertCode(ErrorCode.CONFLICT, () -> service.update("SQ-001", update("v2", 3), USER, false));

        Application cancelled = application("SQ-001", Status.CANCELLED, "v1", 3, 7L);
        when(store.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(cancelled));
        assertCode(ErrorCode.CONFLICT, () -> service.update("SQ-001", update("v2", 3), USER, false));
    }

    @Test
    void cancelRequiresReasonAndPersistsStateEvent() {
        Application draft = application("SQ-001", Status.DRAFT, "v1", 3, 7L);
        when(store.findByCodeForUpdate("SQ-001", 1L)).thenReturn(Optional.of(draft));
        when(store.transition(10L, 1L, Status.DRAFT, Status.CANCELLED, 3L, 7L)).thenReturn(true);
        when(store.findByCode("SQ-001", 1L)).thenReturn(Optional.empty());

        var response = service.cancel("SQ-001", new StateActionRequest(3, "不再投产"), USER, false);

        assertEquals("CANCELLED", response.status());
        verify(store).appendEvent(anyLong(), eq(1L), eq(10L), eq("CANCELLED"), eq(Status.DRAFT),
                eq(Status.CANCELLED), eq("不再投产"), eq(null), eq(7L), eq("研发人员"));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.cancel("SQ-001", new StateActionRequest(3, " "), USER, false));
    }

    @Test
    void conflictTokenRejectsChangedFactsAndEditOldReturnsNavigationTarget() {
        Application current = application("SQ-NEW", Status.DRAFT, "v2", 1, 7L);
        Application old = application("SQ-OLD", Status.DRAFT, "v1", 4, 7L);
        when(store.findByCodeForUpdate("SQ-NEW", 1L)).thenReturn(Optional.of(current));
        when(store.findByCodeForUpdate("SQ-OLD", 1L)).thenReturn(Optional.of(old));
        when(store.findByCode("SQ-NEW", 1L)).thenReturn(Optional.of(current));
        when(store.findConflictIds(1L, 20L, List.of("UNIT:UNIT-A"), 10L)).thenReturn(List.of(80L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(old));

        assertCode(ErrorCode.CONFLICT, () -> service.resolveConflict("SQ-NEW",
                new ConflictActionRequest("EDIT_OLD", "SQ-OLD", 4L, "stale", null), USER, false));

        var report = service.conflicts("SQ-NEW", USER);
        var result = service.resolveConflict("SQ-NEW",
                new ConflictActionRequest("EDIT_OLD", "SQ-OLD", 4L, report.conflictToken(), null), USER, false);
        assertEquals("SQ-OLD", result.navigateApplicationCode());
    }

    @Test
    void rejectsCrossTenantApplicationAccess() {
        when(store.findByCode("SQ-OTHER", 1L)).thenReturn(Optional.empty());
        when(store.findTenantId("SQ-OTHER")).thenReturn(OptionalLong.of(2L));

        assertCode(ErrorCode.FORBIDDEN, () -> service.detail("SQ-OTHER", USER));
    }

    @Test
    void standardApplicationHasNoRelatedHistoryLookup() {
        Application standard = application("SQ-001", Status.DRAFT, "v2", 3, 7L);
        when(store.findByCode("SQ-001", 1L)).thenReturn(Optional.of(standard));

        assertTrue(service.relatedHistory("SQ-001", USER).isEmpty());

        verify(store, never()).findRelatedApplicationIds(anyLong(), anyLong());
    }

    @Test
    void relatedHistoryAggregatesLiveApplicationsFiltersStaleChangesAndSortsNewestReleaseFirst() {
        LocalDateTime created = LocalDateTime.of(2026, 8, 12, 9, 0);
        Application current = relatedApplication(10L, "SQ-NEW", Characteristic.ADDITIONAL, Status.IN_REVIEW,
                "v3", null, LocalDateTime.of(2026, 8, 20, 9, 0));
        Application older = relatedApplication(80L, "SQ-OLD-1", Characteristic.STANDARD, Status.CANCELLED,
                "v1", LocalDateTime.of(2026, 8, 14, 9, 0), created);
        Application newer = relatedApplication(81L, "SQ-OLD-2", Characteristic.STANDARD, Status.RELEASED,
                "v2", LocalDateTime.of(2026, 8, 16, 9, 0), created.plusDays(1));
        Application stale = new Application(82L, 1L, "SQ-STALE", "P-001", "P001", "项目", false,
                20L, null, "SYS-1", "SYS1", "用户中心", VersionType.URGENT, Characteristic.STANDARD,
                "release.regular.overdue", Status.RELEASED, 9L, "无关人员", "研发部", null, "紧急原因", "无关说明",
                LocalDateTime.of(2026, 8, 18, 9, 0), 1L, 9L, 9L, created, created,
                List.of(new DeliverySnapshot(102L, "DU-X", "UNIT-X", "无关单元", ArtifactType.IMAGE, "v1")),
                List.of("REQ-X"));
        when(store.findByCode("SQ-NEW", 1L)).thenReturn(Optional.of(current));
        when(store.findRelatedApplicationIds(1L, 10L)).thenReturn(List.of(80L, 81L, 80L, 82L));
        when(store.findById(80L, 1L)).thenReturn(Optional.of(older));
        when(store.findById(81L, 1L)).thenReturn(Optional.of(newer));
        when(store.findById(82L, 1L)).thenReturn(Optional.of(stale));

        var result = service.relatedHistory("SQ-NEW", USER);

        assertEquals(List.of("SQ-OLD-2", "SQ-OLD-1"), result.stream().map(item -> item.applicationCode()).toList());
        assertEquals("RELEASED", result.get(0).status());
        assertEquals("CANCELLED", result.get(1).status());
        assertEquals("v2", result.get(0).versionChanges().get(0).previousVersion());
        assertEquals("v3", result.get(0).versionChanges().get(0).currentVersion());
        assertEquals("其他研发人员", result.get(0).requesterName());
        assertEquals(List.of("REQ-001"), result.get(0).requirementCodes());
        assertEquals("说明-SQ-OLD-2", result.get(0).description());
        verify(store, org.mockito.Mockito.times(1)).findById(80L, 1L);
    }

    private CreateRequest nonEmergency(String version) {
        return new CreateRequest(false, 20L, "P-001", "P001", "项目", "SYS-1", "SYS1", "用户中心",
                deliveries(version), List.of("REQ-001", "REQ-001"), null, "超过申报截止时间", "版本说明");
    }

    private UpdateRequest update(String version, long rowVersion) {
        return new UpdateRequest(rowVersion, false, 20L, "P-001", "P001", "项目", "SYS-1", "SYS1", "用户中心",
                deliveries(version), List.of("REQ-001"), null, "超过申报截止时间", "版本说明");
    }

    private CreateRequest fileRequest(List<FileMediaInput> fileMedia) {
        return new CreateRequest(false, 20L, "P-001", "P001", "项目", "SYS-1", "SYS1", "用户中心",
                List.of(), fileMedia, List.of("REQ-001"), null, "超过申报截止时间", "版本说明");
    }

    private List<DeliveryInput> deliveries(String version) {
        return List.of(new DeliveryInput("DU-1", "UNIT-A", "用户服务", "IMAGE", version));
    }

    private Application application(String code, Status status, String version, long rowVersion, long requesterId) {
        return new Application(code.equals("SQ-OLD") ? 80L : 10L, 1L, code, "P-001", "P001", "项目", false,
                20L, null, "SYS-1", "SYS1", "用户中心", VersionType.URGENT, Characteristic.STANDARD,
                "release.regular.overdue", status, requesterId, "研发人员", "研发部", null, "紧急原因", "说明",
                status == Status.RELEASED ? LocalDateTime.of(2026, 8, 14, 9, 0) : null, rowVersion, requesterId, requesterId,
                LocalDateTime.of(2026, 8, 12, 9, 0), LocalDateTime.of(2026, 8, 12, 9, 0),
                List.of(new DeliverySnapshot(100L, "DU-1", "UNIT-A", "用户服务", ArtifactType.IMAGE, version)),
                List.of("REQ-001"));
    }

    private Application fileApplication(String code, Status status, String path, long rowVersion) {
        return new Application(code.equals("SQ-OLD") ? 80L : 10L, 1L, code, "P-001", "P001", "项目", false,
                20L, null, "SYS-1", "SYS1", "用户中心", VersionType.URGENT, Characteristic.STANDARD,
                "release.regular.overdue", status, 8L, "其他研发人员", "研发部", null, "紧急原因", "说明",
                status == Status.RELEASED ? LocalDateTime.of(2026, 8, 14, 9, 0) : null, rowVersion, 8L, 8L,
                LocalDateTime.of(2026, 8, 12, 9, 0), LocalDateTime.of(2026, 8, 12, 9, 0),
                List.of(new DeliverySnapshot(100L, "FILE", "FILE", "文件介质", ArtifactType.FILE, null,
                        DeliveryItemType.FILE_MEDIA, path, ReleaseApplicationService.fileItemKey(path))), List.of("REQ-001"));
    }

    private Application relatedApplication(long id, String code, Characteristic characteristic, Status status,
                                           String version, LocalDateTime approvedAt, LocalDateTime createdAt) {
        return new Application(id, 1L, code, "P-001", "P001", "项目", false, 20L, null,
                "SYS-1", "SYS1", "用户中心", VersionType.URGENT, characteristic,
                "release.regular.overdue", status, 8L, "其他研发人员", "研发部", null, "紧急原因",
                "说明-" + code, approvedAt, 3L, 8L, 8L, createdAt, createdAt,
                List.of(new DeliverySnapshot(100L + id, "DU-1", "UNIT-A", "用户服务", ArtifactType.IMAGE, version)),
                List.of("REQ-001"));
    }

    private ReleaseWindow window() {
        return new ReleaseWindow(20L, 1L, "WIN-202608-001", "八月窗口", "P-001", "P001", "项目",
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 10, 0, 0),
                LocalDateTime.of(2026, 8, 20, 0, 0), LocalDateTime.of(2026, 8, 21, 0, 0), true, null, 0,
                7, 7, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    private void assertCode(int code, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(code, exception.code());
    }
}

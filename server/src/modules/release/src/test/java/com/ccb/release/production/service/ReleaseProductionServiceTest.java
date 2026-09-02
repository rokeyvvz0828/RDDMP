package com.ccb.release.production.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryItemType;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.production.model.ProductionModels.BatchEntryRequest;
import com.ccb.release.production.model.ProductionModels.BatchUpdateResultRequest;
import com.ccb.release.production.model.ProductionModels.Entry;
import com.ccb.release.production.model.ProductionModels.Result;
import com.ccb.release.production.model.ProductionModels.UpdateResultRequest;
import com.ccb.release.production.persistence.ReleaseProductionStore;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseProductionServiceTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-18T02:00:00Z"), BUSINESS_ZONE);
    private static final AuthUser USER = new AuthUser(7L, 1L, "operator", "", "投产人员", 1L, true);
    private ReleaseProductionStore store;
    private ReleaseApplicationStore applications;
    private ReleaseWindowStore windows;
    private ProjectAccessService projectAccessService;
    private ReleaseProductionService service;

    @BeforeEach
    void setUp() {
        store = mock(ReleaseProductionStore.class);
        applications = mock(ReleaseApplicationStore.class);
        windows = mock(ReleaseWindowStore.class);
        projectAccessService = mock(ProjectAccessService.class);
        when(windows.findById(100L, 1L)).thenReturn(Optional.of(window(time(17))));
        when(projectAccessService.requireAccessible(any(), eq(USER)))
                .thenAnswer(invocation -> new ProjectAccess(1L, invocation.getArgument(0), "项目"));
        when(applications.findById(anyLong(), eq(1L))).thenAnswer(invocation ->
                Optional.of(application(invocation.getArgument(0), "v1", time(14))));
        when(store.findById(anyLong(), eq(1L))).thenAnswer(invocation ->
                Optional.of(entry(invocation.getArgument(0), invocation.getArgument(0), "v1", time(14), Result.RELEASED, 1)));
        service = new ReleaseProductionService(store, applications, windows, projectAccessService, CLOCK);
    }

    @Test
    void refreshSelectsLaterApprovalWithoutComparingVersionText() {
        Application application = application(20L, "v2", time(15));
        Entry old = entry(30L, 10L, "v99", time(14), Result.RELEASED, 0);
        when(applications.findById(20L, 1L)).thenReturn(Optional.of(application));
        when(store.findBySource(1L, 100L, 20L, "UNIT:UNIT1")).thenReturn(Optional.empty());
        when(store.findActiveForUpdate(1L, 100L, "SYS1", "UNIT:UNIT1")).thenReturn(Optional.of(old));

        service.refreshReleasedCandidates(20L, USER);

        verify(store).deactivate(30L, 1L, 7L);
        ArgumentCaptor<Entry> inserted = ArgumentCaptor.forClass(Entry.class);
        verify(store).insert(inserted.capture(), eq(7L));
        assertEquals("v2", inserted.getValue().artifactVersion());
        assertEquals(Result.RELEASED, inserted.getValue().productionResult());
    }

    @Test
    void refreshIsIdempotentForExistingSource() {
        Application application = application(20L, "v2", time(15));
        Entry source = entry(31L, 20L, "v2", time(15), Result.RELEASED, 0);
        when(applications.findById(20L, 1L)).thenReturn(Optional.of(application));
        when(store.findBySource(1L, 100L, 20L, "UNIT:UNIT1")).thenReturn(Optional.of(source));
        when(store.findActiveForUpdate(1L, 100L, "SYS1", "UNIT:UNIT1")).thenReturn(Optional.of(source));

        service.refreshReleasedCandidates(20L, USER);

        verify(store, never()).insert(any(), anyLong());
        verify(store, never()).deactivate(anyLong(), anyLong(), anyLong());
    }

    @Test
    void replayingOlderSourceDoesNotReplaceNewerActiveCandidate() {
        Application oldApplication = application(20L, "v1", time(14));
        Entry oldSource = entry(31L, 20L, "v1", time(14), Result.RELEASED, 0);
        Entry newer = entry(32L, 21L, "v2", time(15), Result.RELEASED, 0);
        when(applications.findById(20L, 1L)).thenReturn(Optional.of(oldApplication));
        when(store.findBySource(1L, 100L, 20L, "UNIT:UNIT1")).thenReturn(Optional.of(oldSource));
        when(store.findActiveForUpdate(1L, 100L, "SYS1", "UNIT:UNIT1")).thenReturn(Optional.of(newer));

        service.refreshReleasedCandidates(20L, USER);

        verify(store, never()).activate(anyLong(), anyLong(), anyLong());
        verify(store, never()).deactivate(anyLong(), anyLong(), anyLong());
    }

    @Test
    void validatesResultSemanticsAndAppendsAudit() {
        Entry before = entry(30L, 10L, "v1", time(14), Result.RELEASED, 2);
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(before));
        when(store.updateResult(eq(30L), eq(1L), eq(2L), eq(Result.SUCCEEDED), any(), eq(null), eq(7L))).thenReturn(true);

        assertCode(ErrorCode.BAD_REQUEST, () -> service.updateResult(30L,
                new UpdateResultRequest("SUCCEEDED", null, null, "上线", 2), USER));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.updateResult(30L,
                new UpdateResultRequest("FAILED", null, null, "失败", 2), USER));

        var result = service.updateResult(30L,
                new UpdateResultRequest("SUCCEEDED", time(20), null, "确认投产", 2), USER);
        assertEquals(Result.SUCCEEDED, result.productionResult());
        verify(store).appendResultLog(anyLong(), eq(1L), eq(before), eq(Result.SUCCEEDED), eq(time(20)),
                eq("确认投产"), eq(7L), eq("投产人员"));
    }

    @Test
    void rejectsStaleResultUpdate() {
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(entry(30L, 10L, "v1", time(14), Result.RELEASED, 3)));
        assertCode(ErrorCode.CONFLICT, () -> service.updateResult(30L,
                new UpdateResultRequest("NOT_DEPLOYED", null, "取消投产", "状态变化", 2), USER));
    }

    @Test
    void rejectsResultThatWasAlreadyMaintained() {
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(entry(30L, 10L, "v1", time(14), Result.SUCCEEDED, 3)));

        assertCode(ErrorCode.CONFLICT, () -> service.updateResult(30L,
                new UpdateResultRequest("FAILED", null, "失败", "重新维护", 3), USER));
        verify(store, never()).updateResult(anyLong(), anyLong(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void rejectsResultMaintenanceBeforeWindowEndsWithoutWriting() {
        Entry before = entry(30L, 10L, "v1", time(14), Result.RELEASED, 2);
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(before));
        when(windows.findById(100L, 1L)).thenReturn(Optional.of(window(time(19))));

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateResult(30L,
                new UpdateResultRequest("NOT_DEPLOYED", null, "尚未投产", "提前维护", 2), USER));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertEquals("投产窗口尚未结束，结束时间为 2026-08-19 10:00，不能提前维护投产结果", error.getMessage());
        verify(store, never()).updateResult(anyLong(), anyLong(), anyLong(), any(), any(), any(), anyLong());
        verify(store, never()).appendResultLog(anyLong(), anyLong(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void allowsResultMaintenanceAtExactWindowEnd() {
        Entry before = entry(30L, 10L, "v1", time(14), Result.RELEASED, 2);
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(before));
        when(windows.findById(100L, 1L)).thenReturn(Optional.of(window(LocalDateTime.now(CLOCK))));
        when(store.updateResult(eq(30L), eq(1L), eq(2L), eq(Result.NOT_DEPLOYED), eq(null),
                eq("窗口结束后未投产"), eq(7L))).thenReturn(true);

        Entry result = service.updateResult(30L,
                new UpdateResultRequest("NOT_DEPLOYED", null, "窗口结束后未投产", "确认结果", 2), USER);

        assertEquals(Result.NOT_DEPLOYED, result.productionResult());
        verify(store).appendResultLog(anyLong(), eq(1L), eq(before), eq(Result.NOT_DEPLOYED), eq(null),
                eq("确认结果"), eq(7L), eq("投产人员"));
    }

    @Test
    void rejectsBatchMaintenanceBeforeWindowEndsWithoutWriting() {
        Entry first = entry(30L, 10L, "v1", time(14), Result.RELEASED, 2);
        Entry second = entry(31L, 11L, "v2", time(15), Result.RELEASED, 4);
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(first));
        when(store.findByIdForUpdate(31L, 1L)).thenReturn(Optional.of(second));
        when(windows.findById(100L, 1L)).thenReturn(Optional.of(window(time(19))));

        assertCode(ErrorCode.CONFLICT, () -> service.updateResults(new BatchUpdateResultRequest(
                List.of(new BatchEntryRequest(30L, 2L), new BatchEntryRequest(31L, 4L)),
                "NOT_DEPLOYED", null, "尚未投产", "提前批量维护"), USER));

        verify(store, never()).updateResult(anyLong(), anyLong(), anyLong(), any(), any(), any(), anyLong());
        verify(store, never()).appendResultLog(anyLong(), anyLong(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void batchUpdatesReleasedEntriesWithOneSharedResult() {
        Entry first = entry(30L, 10L, "v1", time(14), Result.RELEASED, 2);
        Entry second = entry(31L, 11L, "v2", time(15), Result.RELEASED, 4);
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(first));
        when(store.findByIdForUpdate(31L, 1L)).thenReturn(Optional.of(second));
        when(store.updateResult(eq(30L), eq(1L), eq(2L), eq(Result.NOT_DEPLOYED), eq(null), eq("窗口取消"), eq(7L))).thenReturn(true);
        when(store.updateResult(eq(31L), eq(1L), eq(4L), eq(Result.NOT_DEPLOYED), eq(null), eq("窗口取消"), eq(7L))).thenReturn(true);

        List<Entry> results = service.updateResults(new BatchUpdateResultRequest(
                List.of(new BatchEntryRequest(30L, 2L), new BatchEntryRequest(31L, 4L)),
                "NOT_DEPLOYED", null, "窗口取消", "批量确认未投产"), USER);

        assertEquals(2, results.size());
        verify(store).appendResultLog(anyLong(), eq(1L), eq(first), eq(Result.NOT_DEPLOYED), eq(null),
                eq("批量确认未投产"), eq(7L), eq("投产人员"));
        verify(store).appendResultLog(anyLong(), eq(1L), eq(second), eq(Result.NOT_DEPLOYED), eq(null),
                eq("批量确认未投产"), eq(7L), eq("投产人员"));
    }

    @Test
    void createsIndependentBaselineRowsForMultipleFilePaths() {
        Application application = fileApplication(20L, time(15));
        when(applications.findById(20L, 1L)).thenReturn(Optional.of(application));

        service.refreshReleasedCandidates(20L, USER);

        ArgumentCaptor<Entry> inserted = ArgumentCaptor.forClass(Entry.class);
        verify(store, org.mockito.Mockito.times(2)).insert(inserted.capture(), eq(7L));
        assertEquals(2, inserted.getAllValues().stream().map(Entry::itemKey).distinct().count());
        assertEquals(List.of("/deploy/a.zip", "/deploy/b.zip"),
                inserted.getAllValues().stream().map(Entry::filePath).sorted().toList());
        org.junit.jupiter.api.Assertions.assertTrue(
                inserted.getAllValues().stream().allMatch(value -> value.artifactVersion() == null));
    }

    @Test
    void fileHistoryUsesAnchorEntryItemKey() {
        Entry anchor = fileEntry(50L, "/deploy/a.zip");
        when(store.findById(50L, 1L)).thenReturn(Optional.of(anchor));
        when(store.findHistoryByItemKey(1L, "SYS1", anchor.itemKey())).thenReturn(List.of(anchor));

        List<Entry> history = service.historyByEntry(50L, USER);

        assertEquals(List.of(anchor), history);
    }

    @Test
    void filtersProductionHistoryToRequestedProject() {
        Entry own = entry(30L, 10L, "v1", time(14), Result.SUCCEEDED, 1);
        Entry other = entry(31L, 11L, "v2", time(15), Result.SUCCEEDED, 1);
        Application otherApplication = mock(Application.class);
        when(otherApplication.projectId()).thenReturn("P2");
        when(store.findHistory(1L, "SYS1", "UNIT1")).thenReturn(List.of(other, own));
        when(applications.findById(10L, 1L)).thenReturn(Optional.of(application(10L, "v1", time(14))));
        when(applications.findById(11L, 1L)).thenReturn(Optional.of(otherApplication));

        List<Entry> history = service.history("P1", "SYS1", "UNIT1", USER);

        assertEquals(List.of(own), history);
    }

    @Test
    void deniedProjectAccessStopsResultUpdateBeforeWriting() {
        Entry before = entry(30L, 10L, "v1", time(14), Result.RELEASED, 2);
        when(store.findByIdForUpdate(30L, 1L)).thenReturn(Optional.of(before));
        when(projectAccessService.requireAccessible("P1", USER))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限"));

        assertCode(ErrorCode.FORBIDDEN, () -> service.updateResult(30L,
                new UpdateResultRequest("NOT_DEPLOYED", null, "取消", "确认", 2), USER));

        verify(store, never()).updateResult(anyLong(), anyLong(), anyLong(), any(), any(), any(), anyLong());
    }

    private Application application(long id, String version, LocalDateTime approvedAt) {
        return new Application(id, 1L, "SQ-020", "P1", "P001", "项目", false, 100L, null, "S1", "SYS1", "系统",
                VersionType.REGULAR, Characteristic.STANDARD, "release.regular", Status.RELEASED, 8L, "研发", "研发部",
                null, null, null, approvedAt, 1L, 8L, 8L, time(10), approvedAt,
                List.of(new DeliverySnapshot(40L, "D1", "UNIT1", "单元", ArtifactType.IMAGE, version)), List.of("REQ1"));
    }

    private Application fileApplication(long id, LocalDateTime approvedAt) {
        List<DeliverySnapshot> files = List.of("/deploy/a.zip", "/deploy/b.zip").stream()
                .map(path -> new DeliverySnapshot(40L + path.length(), "FILE", "FILE", "文件介质", ArtifactType.FILE,
                        null, DeliveryItemType.FILE_MEDIA, path, fileKey(path))).toList();
        return new Application(id, 1L, "SQ-020", "P1", "P001", "项目", false, 100L, null, "S1", "SYS1", "系统",
                VersionType.REGULAR, Characteristic.STANDARD, "release.regular", Status.RELEASED, 8L, "研发", "研发部",
                null, null, null, approvedAt, 1L, 8L, 8L, time(10), approvedAt, files, List.of("REQ1"));
    }

    private Entry fileEntry(long id, String path) {
        return new Entry(id, 1L, 100L, 20L, "SQ-020", time(15), "S1", "SYS1", "系统", "FILE", "FILE",
                "文件介质", "FILE", null, "FILE_MEDIA", path, fileKey(path), "REGULAR", "STANDARD",
                Result.SUCCEEDED, time(20), null, true, 1L, time(10), time(20));
    }

    private String fileKey(String path) {
        try {
            return "FILE:" + java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Entry entry(long id, long applicationId, String version, LocalDateTime approvedAt, Result result, long rowVersion) {
        return new Entry(id, 1L, 100L, applicationId, "SQ-OLD", approvedAt, "S1", "SYS1", "系统", "D1", "UNIT1",
                "单元", "IMAGE", version, "REGULAR", "STANDARD", result, null, null, true, rowVersion, time(10), time(10));
    }
    private ReleaseWindow window(LocalDateTime productionEnd) {
        return new ReleaseWindow(100L, 1L, "WIN-001", "投产窗口", "P1", "P001", "项目",
                time(1), time(10), time(17), productionEnd, true, null, 0, 7L, 7L, time(1), time(1));
    }
    private LocalDateTime time(int day) { return LocalDateTime.of(2026, 8, day, 10, 0); }
    private void assertCode(int code, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run); assertEquals(code, error.code());
    }
}

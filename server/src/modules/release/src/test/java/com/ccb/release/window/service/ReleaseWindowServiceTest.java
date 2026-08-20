package com.ccb.release.window.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.window.model.ChangeRegularEnabledRequest;
import com.ccb.release.window.model.CreateReleaseWindowRequest;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.model.ReleaseWindowStatus;
import com.ccb.release.window.model.UpdateReleaseWindowRequest;
import com.ccb.release.window.model.WindowFieldChange;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseWindowServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);

    private ReleaseWindowStore store;
    private ReleaseWindowService service;

    @BeforeEach
    void setUp() {
        store = mock(ReleaseWindowStore.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T04:00:00Z"), ZONE);
        service = new ReleaseWindowService(store, clock);
    }

    @Test
    void createsPersistentWindowWithGeneratedCodeAndTenantSnapshot() {
        CreateReleaseWindowRequest request = createRequest();
        when(store.nextMonthlySequence(1L, "WIN-202608-")).thenReturn(2);
        when(store.findById(anyLong(), eq(1L))).thenReturn(Optional.empty());

        var result = service.create(request, USER);

        assertEquals("WIN-202608-002", result.windowCode());
        assertEquals("P-001", result.projectId());
        assertEquals("URGENT", result.status());
        assertTrue(result.regularApplicationSelectable());
        verify(store).lockProjectWindows(1L, "P-001");
        verify(store).hasOverlap(1L, "P-001", request.declarationStart(), request.productionEnd(), null);
        verify(store).insert(any(ReleaseWindow.class));
    }

    @Test
    void rejectsInvalidMinuteOrderAndOverlapBeforeInsert() {
        CreateReleaseWindowRequest invalid = new CreateReleaseWindowRequest("八月窗口", "P-001", "P001", "项目",
                time(10, 0), time(9, 0), time(20, 0), time(21, 0), true, null);
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(invalid, USER));

        CreateReleaseWindowRequest seconds = new CreateReleaseWindowRequest("八月窗口", "P-001", "P001", "项目",
                time(1, 0).withSecond(1), time(10, 0), time(20, 0), time(21, 0), true, null);
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(seconds, USER));

        when(store.hasOverlap(eq(1L), eq("P-001"), any(LocalDateTime.class), any(LocalDateTime.class), eq(null)))
                .thenReturn(true);
        assertCode(ErrorCode.CONFLICT, () -> service.create(createRequest(), USER));
        verify(store, never()).insert(any());
    }

    @Test
    void rejectsImmutableProjectAndStaleVersion() {
        ReleaseWindow current = window(true, 3);
        when(store.findById(10L, 1L)).thenReturn(Optional.of(current));
        when(store.findByIdForUpdate(10L, 1L)).thenReturn(Optional.of(current));

        UpdateReleaseWindowRequest movedProject = updateRequest("P-002", 3L, "调整时间");
        assertCode(ErrorCode.CONFLICT, () -> service.update(10L, movedProject, USER));

        UpdateReleaseWindowRequest stale = updateRequest("P-001", 2L, "调整时间");
        assertCode(ErrorCode.CONFLICT, () -> service.update(10L, stale, USER));
        verify(store, never()).update(any(), anyLong());
    }

    @Test
    void appendsFieldLevelAuditAndRejectsConcurrentUpdate() {
        ReleaseWindow current = window(true, 3);
        when(store.findById(10L, 1L)).thenReturn(Optional.of(current), Optional.empty(), Optional.of(current));
        when(store.findByIdForUpdate(10L, 1L)).thenReturn(Optional.of(current));
        when(store.update(any(), eq(3L))).thenReturn(true);

        var response = service.update(10L, updateRequest("P-001", 3L, "窗口延期"), USER);

        assertEquals(4, response.rowVersion());
        ArgumentCaptor<List<WindowFieldChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(store).appendChanges(eq(1L), eq(10L), changes.capture(), eq("窗口延期"), eq(7L), anyLong());
        assertTrue(changes.getValue().stream().anyMatch(change -> change.fieldName().equals("window_name")));
        assertTrue(changes.getValue().stream().anyMatch(change -> change.fieldName().equals("production_end")));
        verify(store).lockProjectWindows(1L, "P-001");

        when(store.update(any(), eq(3L))).thenReturn(false);
        assertCode(ErrorCode.CONFLICT, () -> service.update(10L, updateRequest("P-001", 3L, "再次延期"), USER));
    }

    @Test
    void switchChangeRequiresReasonVersionAndWritesAudit() {
        ReleaseWindow current = window(true, 3);
        when(store.findByIdForUpdate(10L, 1L)).thenReturn(Optional.of(current));
        when(store.update(any(), eq(3L))).thenReturn(true);
        when(store.findById(10L, 1L)).thenReturn(Optional.empty());

        var response = service.changeRegularEnabled(10L,
                new ChangeRegularEnabledRequest(false, 3L, "暂停常规申报"), USER);

        assertFalse(response.regularEnabled());
        assertFalse(response.regularApplicationSelectable());
        verify(store).appendChanges(eq(1L), eq(10L), any(), eq("暂停常规申报"), eq(7L), anyLong());
        assertCode(ErrorCode.BAD_REQUEST, () -> service.changeRegularEnabled(10L,
                new ChangeRegularEnabledRequest(true, 3L, " "), USER));
    }

    @Test
    void rejectsCrossTenantEntityAccess() {
        when(store.findById(10L, 1L)).thenReturn(Optional.empty());
        when(store.findTenantId(10L)).thenReturn(OptionalLong.of(2L));

        assertCode(ErrorCode.FORBIDDEN, () -> service.detail(10L, USER));
    }

    @Test
    void derivesAllWindowStatusesAtBoundaries() {
        ReleaseWindow window = window(true, 3);
        assertEquals(ReleaseWindowStatus.UPCOMING, service.status(window, time(1, 0).minusMinutes(1)));
        assertEquals(ReleaseWindowStatus.DECLARATION_OPEN, service.status(window, time(10, 0)));
        assertEquals(ReleaseWindowStatus.URGENT, service.status(window, time(15, 0)));
        assertEquals(ReleaseWindowStatus.IN_PRODUCTION, service.status(window, time(20, 0)));
        assertEquals(ReleaseWindowStatus.CLOSED, service.status(window, time(21, 0).plusMinutes(1)));
    }

    private CreateReleaseWindowRequest createRequest() {
        return new CreateReleaseWindowRequest("八月投产窗口", "P-001", "P001", "统一研发交付平台升级项目",
                time(1, 0), time(10, 0), time(20, 0), time(21, 0), true, "月度窗口");
    }

    private UpdateReleaseWindowRequest updateRequest(String projectId, Long version, String reason) {
        return new UpdateReleaseWindowRequest("WIN-202608-001", "八月窗口（调整）", projectId, "P001",
                "统一研发交付平台升级项目", time(1, 0), time(10, 0), time(20, 0), time(22, 0),
                true, "调整后的窗口", version, reason);
    }

    private ReleaseWindow window(boolean enabled, long version) {
        return new ReleaseWindow(10L, 1L, "WIN-202608-001", "八月投产窗口", "P-001", "P001",
                "统一研发交付平台升级项目", time(1, 0), time(10, 0), time(20, 0), time(21, 0),
                enabled, "月度窗口", version, 7L, 7L, time(1, 0), time(1, 0));
    }

    private LocalDateTime time(int day, int hour) {
        return LocalDateTime.of(2026, 8, day, hour, 0);
    }

    private void assertCode(int code, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(code, exception.code());
    }
}

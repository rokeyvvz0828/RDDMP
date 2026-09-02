package com.ccb.release.reporting.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.release.reporting.model.ReleaseAnalyticsModels.Summary;
import com.ccb.release.reporting.persistence.ReleaseAnalyticsStore;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseAnalyticsServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "viewer", "", "查看人", 1L, true);

    @Test
    void summaryAndDrilldownUseSameTenantProjectAndWindowFilters() {
        ReleaseAnalyticsStore store = mock(ReleaseAnalyticsStore.class);
        ReleaseWindowStore windows = mock(ReleaseWindowStore.class);
        ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
        when(projectAccessService.requireAccessible("P1", USER)).thenReturn(new ProjectAccess(1L, "P1", "项目一"));
        ReleaseWindow window = mock(ReleaseWindow.class);
        when(window.projectId()).thenReturn("P1");
        when(windows.findById(100L, 1L)).thenReturn(java.util.Optional.of(window));
        ReleaseAnalyticsService service = new ReleaseAnalyticsService(store, windows, projectAccessService);
        Summary summary = new Summary(1, 2, 3, 4, 5, Map.of("REGULAR", 2L), Map.of("SUCCEEDED", 1L));
        when(store.summary(1L, "P1", 100L)).thenReturn(summary);
        when(store.drilldown(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("P1"),
                org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.eq("productionResult"),
                org.mockito.ArgumentMatchers.eq("SUCCEEDED"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        assertEquals(summary, service.summary("P1", 100L, USER));
        service.drilldown(1, 20, "P1", 100L, "productionResult", "SUCCEEDED", USER);

        verify(store).summary(1L, "P1", 100L);
        verify(store).drilldown(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("P1"),
                org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.eq("productionResult"),
                org.mockito.ArgumentMatchers.eq("SUCCEEDED"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnsupportedDrilldownDimension() {
        ReleaseAnalyticsService service = new ReleaseAnalyticsService(mock(ReleaseAnalyticsStore.class),
                mock(ReleaseWindowStore.class), mock(ProjectAccessService.class));
        assertThrows(BusinessException.class,
                () -> service.drilldown(1, 20, null, null, "unknown", "x", USER));
    }

    @Test
    void rejectsWindowFromAnotherProjectBeforeQueryingAnalytics() {
        ReleaseAnalyticsStore store = mock(ReleaseAnalyticsStore.class);
        ReleaseWindowStore windows = mock(ReleaseWindowStore.class);
        ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
        when(projectAccessService.requireAccessible("P1", USER)).thenReturn(new ProjectAccess(1L, "P1", "项目一"));
        ReleaseWindow window = mock(ReleaseWindow.class);
        when(window.projectId()).thenReturn("P2");
        when(windows.findById(100L, 1L)).thenReturn(java.util.Optional.of(window));
        ReleaseAnalyticsService service = new ReleaseAnalyticsService(store, windows, projectAccessService);

        assertThrows(BusinessException.class, () -> service.summary("P1", 100L, USER));

        verify(store, never()).summary(1L, "P1", 100L);
    }
}

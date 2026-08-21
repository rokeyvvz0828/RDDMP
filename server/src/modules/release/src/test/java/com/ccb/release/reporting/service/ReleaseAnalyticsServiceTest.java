package com.ccb.release.reporting.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.release.reporting.model.ReleaseAnalyticsModels.Summary;
import com.ccb.release.reporting.persistence.ReleaseAnalyticsStore;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseAnalyticsServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "viewer", "", "查看人", 1L, true);

    @Test
    void summaryAndDrilldownUseSameTenantProjectAndWindowFilters() {
        ReleaseAnalyticsStore store = mock(ReleaseAnalyticsStore.class);
        ReleaseAnalyticsService service = new ReleaseAnalyticsService(store);
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
        ReleaseAnalyticsService service = new ReleaseAnalyticsService(mock(ReleaseAnalyticsStore.class));
        assertThrows(BusinessException.class,
                () -> service.drilldown(1, 20, null, null, "unknown", "x", USER));
    }
}

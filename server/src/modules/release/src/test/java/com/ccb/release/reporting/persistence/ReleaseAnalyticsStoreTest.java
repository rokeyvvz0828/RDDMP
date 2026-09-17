package com.ccb.release.reporting.persistence;

import com.ccb.common.api.PageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseAnalyticsStoreTest {
    @Test
    void drilldownPassesDimensionAndPagedFilterToMapper() {
        ReleaseAnalyticsMapper mapper = mock(ReleaseAnalyticsMapper.class);
        when(mapper.drilldown(any())).thenReturn(List.of()); when(mapper.drilldownCount(any())).thenReturn(0L);
        new ReleaseAnalyticsStore(mapper).drilldown(1L, "PRJ-1", 10L, "productionResult", "SUCCEEDED", new PageQuery(1, 20));
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(mapper).drilldown(params.capture());
        assertEquals("productionResult", params.getValue().get("dimension")); assertEquals("SUCCEEDED", params.getValue().get("value")); assertEquals(20L, params.getValue().get("size"));
    }

    @Test
    void summaryDelegatesSeparateUnitAndFileMediaMetrics() {
        ReleaseAnalyticsMapper mapper = mock(ReleaseAnalyticsMapper.class);
        when(mapper.windows(any())).thenReturn(0L); when(mapper.applications(any())).thenReturn(0L); when(mapper.subsystems(any())).thenReturn(0L); when(mapper.units(any())).thenReturn(2L); when(mapper.fileMedia(any())).thenReturn(3L); when(mapper.requirements(any())).thenReturn(0L); when(mapper.versionTypes(any())).thenReturn(List.of()); when(mapper.results(any())).thenReturn(List.of());
        new ReleaseAnalyticsStore(mapper).summary(1L, "PRJ-1", 10L);
        verify(mapper).units(any()); verify(mapper).fileMedia(any());
    }
}

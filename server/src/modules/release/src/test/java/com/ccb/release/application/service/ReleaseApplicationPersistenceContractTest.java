package com.ccb.release.application.service;

import com.ccb.common.api.PageQuery;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationMapper;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseApplicationPersistenceContractTest {
    @Test
    void pageForwardsKeywordAndWindowFiltersToMapper() {
        ReleaseApplicationMapper mapper = mock(ReleaseApplicationMapper.class);
        when(mapper.count(any())).thenReturn(0L); when(mapper.page(any())).thenReturn(List.of());
        new ReleaseApplicationStore(mapper).findPage(1L, "P1", 20L, "AUTH", null, false, 7L, new PageQuery(1, 20));
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(mapper).count(params.capture());
        assertEquals("P1", params.getValue().get("projectId")); assertEquals(20L, params.getValue().get("windowId")); assertEquals("AUTH", params.getValue().get("keyword"));
    }

    @Test
    void updateRetiresPriorChildrenAndInsertsReplacementSnapshots() {
        ReleaseApplicationMapper mapper = mock(ReleaseApplicationMapper.class);
        when(mapper.update(any())).thenReturn(1);
        new ReleaseApplicationStore(mapper).update(application(), 2L);
        verify(mapper).deactivateDeliveries(Map.of("tenantId", 1L, "applicationId", 10L));
        verify(mapper).deactivateRequirements(Map.of("tenantId", 1L, "applicationId", 10L));
        verify(mapper).delivery(any()); verify(mapper).requirement(any());
    }

    @Test
    void failedVersionCheckDoesNotModifyChildren() {
        ReleaseApplicationMapper mapper = mock(ReleaseApplicationMapper.class);
        when(mapper.update(any())).thenReturn(0);
        assertFalse(new ReleaseApplicationStore(mapper).update(application(), 2L));
        verify(mapper, org.mockito.Mockito.never()).deactivateDeliveries(any());
    }

    @Test
    void conflictAndAdditionalRelationKeepStableItemKey() {
        ReleaseApplicationMapper mapper = mock(ReleaseApplicationMapper.class);
        ReleaseApplicationStore store = new ReleaseApplicationStore(mapper);
        store.findConflictIds(1L, 20L, List.of("UNIT:UNIT1"), null);
        store.insertRelation(40L, 1L, 10L, 11L, "UNIT1", "ADDITIONAL", "v1", "v2", "追加", 7L);
        ArgumentCaptor<Map<String, Object>> conflict = ArgumentCaptor.forClass(Map.class);
        verify(mapper).conflicts(conflict.capture()); assertEquals(List.of("UNIT:UNIT1"), conflict.getValue().get("itemKeys"));
        ArgumentCaptor<Map<String, Object>> relation = ArgumentCaptor.forClass(Map.class);
        verify(mapper).relation(relation.capture()); assertEquals("UNIT:UNIT1", relation.getValue().get("itemKey")); assertEquals("DELIVERY_UNIT", relation.getValue().get("itemType"));
    }

    @Test
    void relatedHistoryLookupIsTenantScoped() {
        ReleaseApplicationMapper mapper = mock(ReleaseApplicationMapper.class);
        new ReleaseApplicationStore(mapper).findRelatedApplicationIds(1L, 10L);
        verify(mapper).relatedIds(Map.of("tenantId", 1L, "applicationId", 10L));
    }

    private Application application() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 15, 12, 0);
        return new Application(10L, 1L, "SQ-001", "P1", "P001", "项目", false, 20L, null, "S1", "SYS1", "系统", VersionType.URGENT, Characteristic.STANDARD, "release.regular.overdue", Status.DRAFT, 7L, "研发人员", "研发部", null, "紧急原因", "说明", null, 3L, 7L, 7L, now, now, List.of(new DeliverySnapshot(30L, "D1", "UNIT1", "交付单元", ArtifactType.IMAGE, "v2")), List.of("REQ-1"));
    }
}

package com.ccb.release.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Binding;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.UpdateBindingRequest;
import com.ccb.release.workflow.persistence.ReleaseWorkflowBindingStore;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.workflow.integration.WorkflowDefinitionCatalog;
import com.ccb.workflow.integration.WorkflowDefinitionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

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

class ReleaseWorkflowBindingServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "admin", "", "管理员", 1L, true);
    private ReleaseWorkflowBindingStore store;
    private WorkflowDefinitionCatalog catalog;
    private ProjectAccessService projectAccessService;
    private ReleaseWorkflowBindingService service;

    @BeforeEach
    void setUp() {
        store = mock(ReleaseWorkflowBindingStore.class);
        catalog = mock(WorkflowDefinitionCatalog.class);
        projectAccessService = mock(ProjectAccessService.class);
        when(projectAccessService.requireAccessible(any(), eq(USER))).thenAnswer(invocation ->
                new ProjectAccess(1L, invocation.getArgument(0), "项目一"));
        service = new ReleaseWorkflowBindingService(store, catalog, projectAccessService);
    }

    @Test
    void alwaysReturnsFiveScenesAndMarksUnconfiguredRows() {
        when(store.findProject(1L, "P-001")).thenReturn(List.of());
        when(catalog.publishedDefinitions(USER)).thenReturn(List.of());

        var rows = service.list("P-001", USER);

        assertEquals(5, rows.size());
        assertEquals(List.of("REGULAR", "REGULAR_ADDITIONAL", "URGENT", "URGENT_ADDITIONAL", "EMERGENCY"),
                rows.stream().map(row -> row.sceneCode()).toList());
        assertTrue(rows.stream().noneMatch(row -> row.configured() || row.valid()));
    }

    @Test
    void createsAuditedBindingFromPublishedDefinition() {
        WorkflowDefinitionSummary definition = new WorkflowDefinitionSummary(88L, "release-regular", "常规版本审批", 3);
        when(catalog.requirePublished(88L, USER)).thenReturn(definition);
        when(catalog.publishedDefinitions(USER)).thenReturn(List.of(definition));
        when(store.find(1L, "P-001", Scene.REGULAR, true)).thenReturn(Optional.empty());
        when(store.find(1L, "P-001", Scene.REGULAR, false)).thenReturn(Optional.empty());

        var result = service.update(Scene.REGULAR,
                new UpdateBindingRequest("P-001", "项目一", 88L, 0L, "启用常规版本审批"), USER);

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(store).insert(binding.capture());
        verify(store).appendHistory(anyLong(), eq(null), any(Binding.class), eq("启用常规版本审批"), eq(7L), eq("管理员"));
        assertEquals(88L, binding.getValue().workflowDefinitionId());
        assertTrue(result.valid());
    }

    @Test
    void rejectsStaleUpdateAndInvalidResolution() {
        Binding current = new Binding(10L, 1L, "P-001", "项目一", Scene.REGULAR, 88L,
                "release-regular", "常规版本审批", 3, 4, 7, 7, null, null);
        when(catalog.requirePublished(88L, USER)).thenReturn(new WorkflowDefinitionSummary(88L, "release-regular", "常规版本审批", 3));
        when(store.find(1L, "P-001", Scene.REGULAR, true)).thenReturn(Optional.of(current));

        BusinessException stale = assertThrows(BusinessException.class, () -> service.update(Scene.REGULAR,
                new UpdateBindingRequest("P-001", "项目一", 88L, 3L, "改绑"), USER));
        assertEquals(ErrorCode.CONFLICT, stale.code());
        verify(store, never()).update(any(), anyLong());

        when(store.find(1L, "P-001", Scene.REGULAR, false)).thenReturn(Optional.of(current));
        when(catalog.requirePublished(88L, USER)).thenThrow(new BusinessException(ErrorCode.CONFLICT, "未发布"));
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.resolve("P-001", Scene.REGULAR, USER));
        assertFalse(invalid.getMessage().isBlank());
        assertEquals(ErrorCode.CONFLICT, invalid.code());
    }

    @Test
    void rejectsForgedProjectNameBeforeWritingBinding() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.update(Scene.REGULAR,
                new UpdateBindingRequest("P-001", "伪造项目名", null, 0L, "解绑"), USER));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        verify(store, never()).insert(any());
        verify(store, never()).update(any(), anyLong());
    }
}

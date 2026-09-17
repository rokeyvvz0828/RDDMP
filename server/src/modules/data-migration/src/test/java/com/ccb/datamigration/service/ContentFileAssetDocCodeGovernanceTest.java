package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.security.model.AuthUser;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentFileAssetDocCodeGovernanceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);

    @Test
    void postCreatesGeneratedCodeAndPutReplacementPreservesIt() {
        ContentFileAssetRepository repository = mock(ContentFileAssetRepository.class);
        Map<String, Object> row = new LinkedHashMap<>();
        doAnswer(invocation -> {
            Map<String, Object> values = invocation.getArgument(1);
            row.put("id", values.get("id")); row.put("project_id", values.get("projectId"));
            row.put("system_code", values.get("systemCode")); row.put("asset_code", values.get("docCode"));
            row.put("asset_name", values.get("docName")); row.put("owner_id", values.get("ownerId"));
            return null;
        }).when(repository).insert(anyString(), anyMap());
        doAnswer(invocation -> { Map<String, Object> values = invocation.getArgument(1); row.put("system_code", values.get("systemCode")); row.put("asset_name", values.get("docName")); return 1; }).when(repository).update(anyString(), anyMap());
        when(repository.active(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> row.isEmpty() ? null : new LinkedHashMap<>(row));
        AttachmentGateway gateway = mock(AttachmentGateway.class);
        when(gateway.get(anyLong(), any(AuthUser.class))).thenAnswer(invocation -> temporary(invocation.getArgument(0)));
        DataMigrationPermissionService permissions = mock(DataMigrationPermissionService.class);
        when(permissions.requireStoredProject(any(), any(AuthUser.class))).thenReturn(10L);
        ContentFileAssetService service = new ContentFileAssetService(
                repository, gateway, mock(ContentAttachmentService.class), permissions, null,
                new ContentDocCodeGenerator());

        Map<String, Object> created = service.create(
                "DEPENDENCY", 10L, null, 101L, USER);
        String code = String.valueOf(created.get("asset_code"));
        assertTrue(code.matches("DEP-[0-9a-f]{32}"));

        Map<String, Object> replaced = service.replace(
                "DEPENDENCY", ((Number) created.get("id")).longValue(), null,
                102L, USER);

        assertEquals(code, replaced.get("asset_code"));
        assertEquals("replacement-102.pdf", replaced.get("asset_name"));
    }

    private static AttachmentItem temporary(long id) {
        return new AttachmentItem(id, "replacement-" + id + ".pdf", "application/pdf", 8L,
                "pdf", "TEMP", null, null, null, USER.id(), LocalDateTime.of(2026, 9, 4, 9, 0));
    }

}

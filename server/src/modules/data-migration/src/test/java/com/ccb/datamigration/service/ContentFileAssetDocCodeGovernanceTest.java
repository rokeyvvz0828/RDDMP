package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.springframework.jdbc.core.JdbcTemplate;

class ContentFileAssetDocCodeGovernanceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);

    @Test
    void postCreatesGeneratedCodeAndPutReplacementPreservesIt() {
        RecordingJdbc jdbc = new RecordingJdbc();
        AttachmentGateway gateway = mock(AttachmentGateway.class);
        when(gateway.get(anyLong(), any(AuthUser.class))).thenAnswer(invocation -> temporary(invocation.getArgument(0)));
        DataMigrationPermissionService permissions = mock(DataMigrationPermissionService.class);
        when(permissions.requireStoredProject(any(), any(AuthUser.class))).thenReturn(10L);
        ContentFileAssetService service = new ContentFileAssetService(
                jdbc, gateway, mock(ContentAttachmentService.class), permissions, null,
                new ContentDocCodeGenerator());

        Map<String, Object> created = service.create(
                "MAPPING_DOC", 10L, null, 101L, USER);
        String code = String.valueOf(created.get("asset_code"));
        assertTrue(code.matches("MAP-[0-9a-f]{32}"));

        Map<String, Object> replaced = service.replace(
                "MAPPING_DOC", ((Number) created.get("id")).longValue(), null,
                102L, USER);

        assertEquals(code, replaced.get("asset_code"));
        assertEquals("replacement-102.pdf", replaced.get("asset_name"));
    }

    private static AttachmentItem temporary(long id) {
        return new AttachmentItem(id, "replacement-" + id + ".pdf", "application/pdf", 8L,
                "pdf", "TEMP", null, null, null, USER.id(), LocalDateTime.of(2026, 9, 4, 9, 0));
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        private final Map<String, Object> row = new LinkedHashMap<>();

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.startsWith("SELECT project_id, component_id, owner_id FROM dm_mapping_doc")) {
                return row.isEmpty() ? List.of() : List.of(row);
            }
            return List.of();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return List.of();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return new LinkedHashMap<>(row);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dm_mapping_doc")) {
                row.put("id", args[0]);
                row.put("project_id", args[2]);
                row.put("component_id", args[3]);
                row.put("asset_code", args[4]);
                row.put("asset_name", args[5]);
                row.put("owner_id", args[6]);
            } else if (sql.startsWith("UPDATE dm_mapping_doc SET component_id")) {
                row.put("component_id", args[0]);
                row.put("asset_name", args[1]);
            }
            return 1;
        }
    }
}

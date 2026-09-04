package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class StructuredDocCodeGovernanceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);

    @Test
    void structuredCreateIgnoresClientCodeAndUpdatePreservesStoredCode() {
        RecordingJdbc jdbc = new RecordingJdbc();
        DataMigrationPermissionService permissions = permissions();
        StructuredAssetService service = new StructuredAssetService(
                jdbc, new ObjectMapper(), permissions, new ContentDocCodeGenerator());

        service.save("RULE", Map.of(
                "projectId", 10L,
                "assetCode", "CLIENT-CODE",
                "assetName", "规则一",
                "structuredData", Map.of("rule", "x")), USER);

        assertTrue(String.valueOf(jdbc.lastInsertArgs[4]).matches("RULE-[0-9a-f]{32}"));
        assertFalse(Arrays.asList(jdbc.lastInsertArgs).contains("CLIENT-CODE"));

        service.update("RULE", 50L, Map.of(
                "assetCode", "CLIENT-UPDATE",
                "assetName", "规则二",
                "structuredData", Map.of("rule", "y")), USER);

        assertFalse(jdbc.lastUpdateSql.contains("doc_code"));
        assertFalse(Arrays.asList(jdbc.lastUpdateArgs).contains("CLIENT-UPDATE"));
    }

    @Test
    void excelImportUsesInputWithoutCodeColumnAndGeneratesCode() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        ExcelService service = new ExcelService(jdbc, permissions(), new ContentDocCodeGenerator());
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("data-migration");
            Row header = sheet.createRow(0);
            String[] columns = {"asset_name", "project_id", "component_id", "asset_type", "structured_data"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("参数一");
            row.createCell(1).setCellValue("10");
            row.createCell(2).setCellValue("");
            row.createCell(3).setCellValue("PARAMETER");
            row.createCell(4).setCellValue("{\"value\":1}");
            workbook.write(out);
            workbookBytes = out.toByteArray();
        }

        Map<String, Object> result = service.importAssets("PARAMETER", 10L,
                new MockMultipartFile("file", "parameters.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes), USER);

        assertEquals(1, result.get("accepted"));
        assertTrue(String.valueOf(jdbc.lastInsertArgs[4]).matches("PARAM-[0-9a-f]{32}"));
        assertEquals("参数一", jdbc.lastInsertArgs[5]);
    }

    @Test
    void structuredDeleteChecksRelationsWithArgumentsForBothManagedTables() {
        RecordingJdbc jdbc = new RecordingJdbc();
        StructuredAssetService service = new StructuredAssetService(
                jdbc, new ObjectMapper(), permissions(), new ContentDocCodeGenerator());

        service.delete(List.of(50L), "RULE", USER);

        assertNotNull(jdbc.lastRelationArgs);
        assertEquals(12, jdbc.lastRelationArgs.length);
        assertTrue(jdbc.lastUpdateSql.contains("SET deleted = 1"));
        assertFalse(jdbc.lastUpdateSql.contains("doc_code"));
    }

    private static DataMigrationPermissionService permissions() {
        DataMigrationPermissionService permissions = mock(DataMigrationPermissionService.class);
        when(permissions.requireAccessible(anyLong(), any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissions.requireStoredProject(any(), any(AuthUser.class))).thenReturn(10L);
        when(permissions.requireProject(any(), any(AuthUser.class))).thenReturn(10L);
        return permissions;
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        private Object[] lastInsertArgs;
        private String lastUpdateSql = "";
        private Object[] lastUpdateArgs = new Object[0];
        private Object[] lastRelationArgs;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("WHERE id = ?") && sql.contains("deleted = ?")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", 50L);
                row.put("project_id", 10L);
                row.put("component_id", null);
                row.put("asset_type", "RULE");
                row.put("asset_code", "RULE-existing");
                row.put("asset_name", "规则一");
                row.put("structured_data", "{}");
                row.put("owner_id", USER.id());
                return List.of(row);
            }
            return new ArrayList<>();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return Map.of("id", 50L, "asset_code", String.valueOf(lastInsertArgs[4]));
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("JSON_EXTRACT")) lastRelationArgs = args;
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dm_rule") || sql.startsWith("INSERT INTO dm_parameter")) {
                lastInsertArgs = args;
            } else if (sql.startsWith("UPDATE dm_rule")) {
                lastUpdateSql = sql;
                lastUpdateArgs = args;
            }
            return 1;
        }
    }
}

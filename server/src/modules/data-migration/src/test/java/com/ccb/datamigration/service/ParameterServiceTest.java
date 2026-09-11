package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** 迁移参数专属服务行为测试：必填校验、范围内中英文名称唯一、码值校验、Excel、搜索与回收站审计。 */
class ParameterServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);
    private static final AuthUser OTHER = new AuthUser(88L, 1L, "other", "", "他人", 11L, true);
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 11L, true);
    private static final long PROJECT = 10L;

    private StubJdbcTemplate jdbc;
    private DataMigrationPermissionService permissions;
    private ParameterService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplate();
        permissions = mock(DataMigrationPermissionService.class);
        service = new ParameterService(jdbc, permissions, null, codeValues());

        when(permissions.requireAccessible(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(0, Long.class));
        when(permissions.requireProject(any(), any())).thenAnswer(invocation -> {
            Long pid = invocation.getArgument(0, Long.class);
            if (pid == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "必须选择项目");
            return pid;
        });
        when(permissions.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
    }

    @Test
    void createRejectsMissingRequiredFields() {
        assertRejected(() -> service.create(without("projectId"), USER), "projectId 不能为空");
        assertRejected(() -> service.create(without("parameterType"), USER), "参数类型不能为空");
        assertRejected(() -> service.create(without("parameterScope"), USER), "参数范围分类不能为空");
        assertRejected(() -> service.create(without("systemCode"), USER), "关联系统不能为空");
        assertRejected(() -> service.create(without("parameterName"), USER), "参数名称不能为空");
        assertRejected(() -> service.create(without("parameterNameEn"), USER), "参数英文名不能为空");
    }

    @Test
    void createRejectsInvalidEnglishName() {
        Map<String, Object> invalidCharacters = body("额度参数");
        invalidCharacters.put("parameterNameEn", "LIMIT-AMOUNT");
        assertRejected(() -> service.create(invalidCharacters, USER), "只能包含字母、数字和下划线");

        Map<String, Object> tooLong = body("额度参数");
        tooLong.put("parameterNameEn", "A".repeat(256));
        assertRejected(() -> service.create(tooLong, USER), "不能超过 255 字符");
    }

    @Test
    void createRejectsDuplicateNameAndUnknownCodes() {
        jdbc.existingName = "额度参数";
        BusinessException conflict = assertThrows(BusinessException.class, () -> service.create(body("额度参数"), USER));
        assertEquals(ErrorCode.CONFLICT, conflict.code());
        assertTrue(conflict.getMessage().contains("已存在"));

        jdbc.existingName = null;
        Map<String, Object> unknown = body("新参数");
        unknown.put("parameterType", "UNKNOWN");
        assertRejected(() -> service.create(unknown, USER), "无效或已停用");
    }

    @Test
    void createRejectsCaseInsensitiveDuplicateEnglishName() {
        jdbc.existingEnglishName = "abc_01";

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.create(body("额度参数"), USER));

        assertEquals(ErrorCode.CONFLICT, conflict.code());
        assertTrue(conflict.getMessage().contains("参数英文名"));
    }

    @Test
    void createInsertsDomainColumnsAndAudits() {
        jdbc.existingName = null;
        Map<String, Object> created = service.create(body("额度参数"), USER);
        assertEquals("额度参数", created.get("parameter_name"));
        assertEquals("ABC_01", created.get("parameter_name_en"));
        assertTrue(jdbc.audits.contains("PARAMETER_CREATE"));
        assertFalse(jdbc.audits.contains("STRUCTURED_CREATE"));
    }

    @Test
    void deleteRejectsNonOwnerAndAudits() {
        DataMigrationPermissionService rejecting = mock(DataMigrationPermissionService.class);
        when(rejecting.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该参数操作权限")).when(rejecting).requireWrite(any(), anyLong());
        ParameterService rejectingService = new ParameterService(jdbc, rejecting, null, codeValues());

        jdbc.putParameter(50L, PROJECT, OTHER.id());
        assertThrows(BusinessException.class, () -> rejectingService.delete(List.of(50L), USER));
        assertFalse(jdbc.audits.contains("PARAMETER_DELETE"));

        jdbc.putParameter(51L, PROJECT, USER.id());
        service.delete(List.of(51L), USER);
        assertTrue(jdbc.audits.contains("PARAMETER_DELETE"));
    }

    @Test
    void deleteRejectedWhenParameterHasActiveDependency() {
        jdbc.putParameter(51L, PROJECT, USER.id());
        jdbc.dependencyCount = 1;

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(List.of(51L), USER));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertTrue(error.getMessage().contains("活动依赖关系"));
        assertFalse(jdbc.audits.contains("PARAMETER_DELETE"));
    }

    @Test
    void purgeRejectedWhenParameterHasAnyDependencyRecord() {
        jdbc.putParameter(63L, PROJECT, USER.id(), "SYS_C");
        jdbc.markDeleted(63L);
        jdbc.dependencyCount = 1;

        BusinessException error = assertThrows(BusinessException.class, () -> service.purge(List.of(63L), ADMIN));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertTrue(error.getMessage().contains("不能彻底删除"));
        assertFalse(jdbc.audits.contains("PARAMETER_PURGE"));
    }

    @Test
    void deleteAndPurgeProceedWithoutDependencyReferences() {
        jdbc.putParameter(51L, PROJECT, USER.id());
        service.delete(List.of(51L), USER);
        assertTrue(jdbc.audits.contains("PARAMETER_DELETE"));

        jdbc.putParameter(64L, PROJECT, USER.id());
        jdbc.markDeleted(64L);
        service.purge(List.of(64L), ADMIN);
        assertTrue(jdbc.audits.contains("PARAMETER_PURGE"));
    }

    @Test
    void recycleBinRestoreConflictAndPurgeAudit() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "需要管理员")).when(permissions).requireAdmin(USER);
        assertThrows(BusinessException.class, () -> service.restore(List.of(50L), USER));

        jdbc.putParameter(61L, PROJECT, USER.id());
        jdbc.putParameter(62L, PROJECT, USER.id());
        jdbc.parameters.get(62L).put("parameter_name", "参数-61");
        jdbc.markDeleted(61L);
        // 62 为活动行且同项目同系统同名 → 恢复 61 冲突
        assertThrows(BusinessException.class, () -> service.restore(List.of(61L), ADMIN));

        jdbc.putParameter(63L, PROJECT, USER.id(), "SYS_C");
        jdbc.markDeleted(63L);
        service.purge(List.of(63L), ADMIN);
        assertTrue(jdbc.audits.contains("PARAMETER_PURGE"));
    }

    @Test
    void templateAndImportParseAndSkipInvalidRows() throws Exception {
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(service.downloadTemplate()))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertEquals(11, header.getLastCellNum());
            assertEquals("参数英文名", header.getCell(4).getStringCellValue());
            assertEquals("字段英文名", header.getCell(6).getStringCellValue());
            assertEquals("字段类型", header.getCell(8).getStringCellValue());
        }

        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移参数");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("参数类型");
            header.createCell(1).setCellValue("参数范围分类");
            header.createCell(2).setCellValue("系统编号");
            header.createCell(3).setCellValue("参数名称");
            header.createCell(4).setCellValue("参数英文名");
            header.createCell(5).setCellValue("参数说明");
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("业务参数");
            valid.createCell(1).setCellValue("公共参数");
            valid.createCell(2).setCellValue("SYS_A");
            valid.createCell(3).setCellValue("批量参数A");
            valid.createCell(4).setCellValue("BATCH_A");
            valid.createCell(5).setCellValue("说明A");
            Row invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("不存在的类型");
            invalid.createCell(1).setCellValue("公共参数");
            invalid.createCell(2).setCellValue("SYS_A");
            invalid.createCell(3).setCellValue("批量参数B");
            invalid.createCell(4).setCellValue("BATCH_B");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        Map<String, Object> result = service.importParameters(PROJECT, new MockMultipartFile(
                "file", "parameters.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), USER);
        assertEquals(2, result.get("rows"));
        assertEquals(1, result.get("accepted"));
        assertEquals(1, result.get("failed"));
    }

    @Test
    void importRejectsCaseInsensitiveDuplicateEnglishNameWithinFile() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移参数");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 6; i++) header.createCell(i).setCellValue("H" + i);
            addImportRow(sheet.createRow(1), "批量参数A", "ABC_01");
            addImportRow(sheet.createRow(2), "批量参数B", "abc_01");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        Map<String, Object> result = service.importParameters(PROJECT, new MockMultipartFile(
                "file", "parameters.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), USER);

        assertEquals(2, result.get("rows"));
        assertEquals(1, result.get("accepted"));
        assertEquals(1, result.get("failed"));
        assertTrue(String.valueOf(result.get("errors")).contains("参数英文名重复"));
    }

    @Test
    void importCreatesParameterGroupsWithFields() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移参数");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 11; i++) header.createCell(i).setCellValue("H" + i);
            addFullImportRow(sheet.createRow(1), "批量参数A", "BATCH_A", "amount", "金额", "NUMBER", "15", "金额字段");
            addFieldOnlyRow(sheet.createRow(2), "batch_date", "日期", "DATE", null, "日期字段");
            addFullImportRow(sheet.createRow(3), "批量参数B", "BATCH_B", "client_no", "客户编号", "VARCHAR", "32", null);
            workbook.write(out);
            bytes = out.toByteArray();
        }

        Map<String, Object> result = service.importParameters(PROJECT, new MockMultipartFile(
                "file", "parameters.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), USER);
        assertEquals(3, result.get("rows"));
        assertEquals(2, result.get("accepted"));
        assertEquals(0, result.get("failed"));
        assertTrue(jdbc.fieldsByParameter.values().stream().anyMatch(fields -> fields.size() == 2),
                "应存在含 2 个字段的参数组：" + jdbc.fieldsByParameter);
        assertTrue(jdbc.fieldsByParameter.values().stream().anyMatch(fields -> fields.size() == 1),
                "应存在含 1 个字段的参数组：" + jdbc.fieldsByParameter);
    }

    @Test
    void importRejectsDuplicateFieldNamesWithinGroup() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移参数");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 11; i++) header.createCell(i).setCellValue("H" + i);
            addFullImportRow(sheet.createRow(1), "批量参数A", "BATCH_A", "amount", "金额", "NUMBER", "15", null);
            addFieldOnlyRow(sheet.createRow(2), "AMOUNT", "金额", "NUMBER", "15", null);
            workbook.write(out);
            bytes = out.toByteArray();
        }

        Map<String, Object> result = service.importParameters(PROJECT, new MockMultipartFile(
                "file", "parameters.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), USER);
        assertEquals(0, result.get("accepted"));
        assertEquals(1, result.get("failed"));
        assertTrue(String.valueOf(result.get("errors")).contains("字段英文名在参数内重复"));
    }

    @Test
    void createWithFieldsInsertsFieldsAndAudits() {
        Map<String, Object> created = service.create(withFields(body("额度参数")), USER);
        assertEquals(2, ((List<?>) created.get("fields")).size());
        long createAudits = jdbc.audits.stream().filter("FIELD_CREATE"::equals).count();
        assertEquals(2, createAudits);
    }

    @Test
    void createRejectsDuplicateFieldNamesInPayload() {
        Map<String, Object> body = withFields(body("额度参数"));
        List<Map<String, Object>> fields = castFields(body.get("fields"));
        fields.get(1).put("fieldNameEn", "amount");
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(body, USER));
        assertEquals(ErrorCode.CONFLICT, error.code());
        assertTrue(error.getMessage().contains("字段英文名在参数内重复"));
    }

    @Test
    void batchAddFieldsRejectsDuplicateExistingNameAndInserts() {
        jdbc.putParameter(70L, PROJECT, USER.id());
        jdbc.putField(700L, 70L, "amount", "金额", "NUMBER", 15);

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.batchAddFields(70L, List.of(fieldBody("AMOUNT", "金额大写", "NUMBER", 15)), USER));
        assertEquals(ErrorCode.CONFLICT, duplicate.code());
        assertTrue(duplicate.getMessage().contains("字段英文名在参数内已存在"));

        List<Map<String, Object>> fields = service.batchAddFields(70L,
                List.of(fieldBody("client_no", "客户编号", "VARCHAR", 32), fieldBody("batch_date", "日期", "DATE", null)), USER);
        assertEquals(3, fields.size());
        assertTrue(jdbc.audits.contains("FIELD_CREATE"));
    }

    @Test
    void updateFieldRejectsDuplicateAndUpdatesRow() {
        jdbc.putParameter(70L, PROJECT, USER.id());
        jdbc.putField(700L, 70L, "amount", "金额", "NUMBER", 15);
        jdbc.putField(701L, 70L, "client_no", "客户编号", "VARCHAR", 32);

        Map<String, Object> duplicateBody = new LinkedHashMap<>();
        duplicateBody.put("fieldNameEn", "CLIENT_NO");
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.updateField(70L, 700L, duplicateBody, USER));
        assertEquals(ErrorCode.CONFLICT, duplicate.code());
        assertTrue(duplicate.getMessage().contains("字段英文名在参数内已存在"));

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("fieldLength", 18);
        updateBody.put("fieldDescription", "额度字段");
        Map<String, Object> updated = service.updateField(70L, 700L, updateBody, USER);
        assertEquals("amount", updated.get("field_name_en"));
        assertEquals(18, updated.get("field_length"));
        assertTrue(jdbc.audits.contains("FIELD_UPDATE"));
    }

    @Test
    void deleteFieldsAndParameterCascadeMatches() {
        jdbc.putParameter(70L, PROJECT, USER.id());
        jdbc.putField(700L, 70L, "amount", "金额", "NUMBER", 15);
        jdbc.putField(701L, 70L, "client_no", "客户编号", "VARCHAR", 32);

        service.deleteFields(70L, List.of(700L), USER);
        assertEquals(1, service.listFields(70L, USER).size());
        assertTrue(jdbc.audits.contains("FIELD_DELETE"));

        service.delete(List.of(70L), USER);
        assertTrue(jdbc.fieldsByParameter.get(70L).stream().allMatch(field -> Integer.valueOf(1).equals(field.get("deleted"))));
    }

    @Test
    void keywordSearchIncludesEnglishNameForActiveAndRecycleRows() {
        service.list(PROJECT, null, null, null, "ABC", 1, 20, USER);
        service.fetchRecycleBinPage(PROJECT, "ABC", 20, ADMIN);

        assertTrue(jdbc.sqls.stream().filter(sql -> sql.contains("parameter_name_en LIKE")).count() >= 2);
    }

    @Test
    void exportIncludesEnglishNameAfterChineseName() throws Exception {
        jdbc.putParameter(50L, PROJECT, USER.id());

        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(
                service.export(PROJECT, null, null, null, null, USER)))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("参数英文名", sheet.getRow(0).getCell(6).getStringCellValue());
            assertEquals("PARAM_50", sheet.getRow(1).getCell(6).getStringCellValue());
        }
    }

    // ============ 辅助 ============

    private Map<String, Object> without(String key) {
        Map<String, Object> copy = body("额度参数");
        copy.remove(key);
        return copy;
    }

    private Map<String, Object> body(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", PROJECT);
        body.put("parameterType", "BUSINESS");
        body.put("parameterScope", "PUBLIC");
        body.put("systemCode", "SYS_A");
        body.put("parameterName", name);
        body.put("parameterNameEn", "ABC_01");
        body.put("parameterDescription", "说明");
        return body;
    }

    private void addImportRow(Row row, String parameterName, String parameterNameEn) {
        row.createCell(0).setCellValue("业务参数");
        row.createCell(1).setCellValue("公共参数");
        row.createCell(2).setCellValue("SYS_A");
        row.createCell(3).setCellValue(parameterName);
        row.createCell(4).setCellValue(parameterNameEn);
        row.createCell(5).setCellValue("说明");
        for (int i = 6; i <= 10; i++) row.createCell(i).setCellValue("");
    }

    private void addFullImportRow(Row row, String parameterName, String parameterNameEn,
                                  String fieldNameEn, String fieldNameCn, String fieldType, String length, String fieldDesc) {
        addImportRow(row, parameterName, parameterNameEn);
        row.createCell(6).setCellValue(fieldNameEn);
        row.createCell(7).setCellValue(fieldNameCn);
        row.createCell(8).setCellValue(fieldType);
        if (length != null) row.createCell(9).setCellValue(length);
        if (fieldDesc != null) row.createCell(10).setCellValue(fieldDesc);
    }

    private void addFieldOnlyRow(Row row, String fieldNameEn, String fieldNameCn, String fieldType, String length, String fieldDesc) {
        for (int i = 0; i <= 10; i++) row.createCell(i).setCellValue("");
        row.createCell(6).setCellValue(fieldNameEn);
        row.createCell(7).setCellValue(fieldNameCn);
        row.createCell(8).setCellValue(fieldType);
        if (length != null) row.createCell(9).setCellValue(length);
        if (fieldDesc != null) row.createCell(10).setCellValue(fieldDesc);
    }

    private Map<String, Object> withFields(Map<String, Object> body) {
        body.put("fields", List.of(
                fieldBody("amount", "金额", "NUMBER", 15),
                fieldBody("client_no", "客户编号", "VARCHAR", 32)));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castFields(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> fieldBody(String en, String cn, String type, Integer length) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldNameEn", en);
        field.put("fieldNameCn", cn);
        field.put("fieldType", type);
        field.put("fieldLength", length);
        field.put("fieldDescription", null);
        return field;
    }

    private DataMigrationCodeValueService codeValues() {
        SystemReferenceQuery refs = mock(SystemReferenceQuery.class);
        when(refs.activeParameters(any(), eq("DM_PARAMETER_TYPE")))
                .thenReturn(List.of(
                        new SystemParameterReference("DM_PARAMETER_TYPE.BUSINESS", "业务参数"),
                        new SystemParameterReference("DM_PARAMETER_TYPE.TECHNICAL", "技术参数")));
        when(refs.activeParameters(any(), eq("DM_PARAMETER_SCOPE")))
                .thenReturn(List.of(
                        new SystemParameterReference("DM_PARAMETER_SCOPE.PUBLIC", "公共参数"),
                        new SystemParameterReference("DM_PARAMETER_SCOPE.OWN", "自有参数")));
        when(refs.activeParameters(any(), eq("DM_PARAMETER_FIELD_TYPE")))
                .thenReturn(List.of(
                        new SystemParameterReference("DM_PARAMETER_FIELD_TYPE.VARCHAR", "VARCHAR"),
                        new SystemParameterReference("DM_PARAMETER_FIELD_TYPE.NUMBER", "NUMBER"),
                        new SystemParameterReference("DM_PARAMETER_FIELD_TYPE.DATE", "DATE")));
        return new DataMigrationCodeValueService(refs);
    }

    private void assertRejected(Runnable action, String messageContains) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains(messageContains), error.getMessage());
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<Long, Map<String, Object>> parameters = new LinkedHashMap<>();
        private final Map<Long, List<Map<String, Object>>> fieldsByParameter = new LinkedHashMap<>();
        private final List<String> audits = new ArrayList<>();
        private final List<String> sqls = new ArrayList<>();
        private String existingName;
        private String existingEnglishName;
        private int dependencyCount;

        private void putParameter(long id, long projectId, long ownerId) {
            putParameter(id, projectId, ownerId, "SYS_A");
        }

        private void putParameter(long id, long projectId, long ownerId, String systemCode) {
            Map<String, Object> row = baseRow();
            row.put("id", id);
            row.put("project_id", projectId);
            row.put("system_code", systemCode);
            row.put("parameter_name", "参数-" + id);
            row.put("parameter_name_en", "PARAM_" + id);
            row.put("owner_id", ownerId);
            row.put("deleted", 0);
            parameters.put(id, row);
        }

        private Map<String, Object> baseRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenant_id", 1L);
            row.put("parameter_type", "BUSINESS");
            row.put("parameter_scope", "PUBLIC");
            row.put("parameter_description", null);
            return row;
        }

        private void markDeleted(long id) {
            Map<String, Object> row = parameters.get(id);
            if (row != null) row.put("deleted", 1);
        }

        private void putField(long fieldId, long parameterId, String fieldNameEn, String fieldNameCn,
                              String fieldType, Integer fieldLength) {
            List<Map<String, Object>> fields = fieldsByParameter.computeIfAbsent(parameterId, key -> new ArrayList<>());
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", fieldId);
            field.put("parameter_id", parameterId);
            field.put("field_name_en", fieldNameEn);
            field.put("field_name_cn", fieldNameCn);
            field.put("field_type", fieldType);
            field.put("field_length", fieldLength);
            field.put("field_description", null);
            field.put("sort_no", fields.size() + 1);
            field.put("deleted", 0);
            fields.add(field);
        }

        @Override
        public int update(String sql, Object... args) {
            sqls.add(sql);
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                audits.add(String.valueOf(args[3]));
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_parameter_field")) {
                long id = ((Number) args[0]).longValue();
                long parameterId = ((Number) args[2]).longValue();
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("id", id);
                field.put("parameter_id", parameterId);
                field.put("field_name_en", args[3]);
                field.put("field_name_cn", args[4]);
                field.put("field_type", args[5]);
                field.put("field_length", args[6]);
                field.put("field_description", args[7]);
                field.put("sort_no", args[8]);
                field.put("owner_id", args[9]);
                field.put("deleted", 0);
                fieldsByParameter.computeIfAbsent(parameterId, key -> new ArrayList<>()).add(field);
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_parameter")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = baseRow();
                row.put("id", id);
                row.put("project_id", ((Number) args[2]).longValue());
                row.put("system_code", args[3]);
                row.put("parameter_type", args[4]);
                row.put("parameter_scope", args[5]);
                row.put("parameter_name", args[6]);
                row.put("parameter_name_en", args[7]);
                row.put("parameter_description", args[8]);
                row.put("owner_id", args[9]);
                row.put("deleted", 0);
                parameters.put(id, row);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_parameter SET deleted = 1")) {
                long id = ((Number) args[2]).longValue();
                Map<String, Object> row = parameters.get(id);
                if (row == null) return 0;
                row.put("deleted", 1);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_parameter SET deleted = 0")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = parameters.get(id);
                if (row == null) return 0;
                row.put("deleted", 0);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_parameter SET parameter_type")) {
                long id = ((Number) args[6]).longValue();
                Map<String, Object> row = parameters.get(id);
                if (row == null) return 0;
                row.put("parameter_type", args[0]);
                row.put("parameter_scope", args[1]);
                row.put("system_code", args[2]);
                row.put("parameter_name", args[3]);
                row.put("parameter_name_en", args[4]);
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_parameter")) {
                long id = ((Number) args[0]).longValue();
                parameters.remove(id);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_parameter_field SET field_name_en")) {
                long id = ((Number) args[6]).longValue();
                Map<String, Object> field = activeFieldById(id);
                if (field == null) return 0;
                field.put("field_name_en", args[0]);
                field.put("field_name_cn", args[1]);
                field.put("field_type", args[2]);
                field.put("field_length", args[3]);
                field.put("field_description", args[4]);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_parameter_field SET deleted = 1")) {
                int changed = 0;
                if (sql.contains("WHERE id IN")) {
                    long tenantId = ((Number) args[args.length - 2]).longValue();
                    long parameterId = ((Number) args[args.length - 1]).longValue();
                    for (int i = 2; i < args.length - 2; i++) {
                        Map<String, Object> field = activeFieldById(((Number) args[i]).longValue());
                        if (field != null && number(field.get("parameter_id")) == parameterId) {
                            field.put("deleted", 1);
                            changed++;
                        }
                    }
                    return changed;
                }
                // 参数级联软删：args = (deleted_by, updated_by, parameter_id, tenant_id)
                long parameterId = ((Number) args[2]).longValue();
                for (Map<String, Object> field : fieldsByParameter.getOrDefault(parameterId, List.of())) {
                    if (Integer.valueOf(0).equals(field.get("deleted"))) {
                        field.put("deleted", 1);
                        changed++;
                    }
                }
                return changed;
            }
            if (sql.startsWith("UPDATE dm_parameter_field SET deleted = 0")) {
                long parameterId = ((Number) args[1]).longValue();
                for (Map<String, Object> field : fieldsByParameter.getOrDefault(parameterId, List.of())) {
                    if (Integer.valueOf(1).equals(field.get("deleted"))) field.put("deleted", 0);
                }
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_parameter_field")) {
                long parameterId = ((Number) args[0]).longValue();
                fieldsByParameter.remove(parameterId);
                return 1;
            }
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            sqls.add(sql);
            if (sql.contains("SELECT COUNT(*) FROM dm_component")) return List.of();
            if (sql.contains("FROM dm_parameter a")) return new ArrayList<>(parameters.values());
            if (sql.contains("FROM dm_parameter WHERE") && sql.contains("deleted = 1")) {
                long id = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).longValue() : -1L;
                Map<String, Object> row = parameters.get(id);
                if (row == null || !Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                return new ArrayList<>(List.of(row));
            }
            if (sql.contains("SELECT id, tenant_id, project_id, system_code") && sql.contains("deleted = 0")) {
                long id = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).longValue() : -1L;
                Map<String, Object> row = parameters.get(id);
                if (row == null) return List.of();
                return new ArrayList<>(List.of(row));
            }
            if (sql.contains("SELECT id, project_id, owner_id FROM dm_parameter")) {
                long id = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).longValue() : -1L;
                Map<String, Object> row = parameters.get(id);
                if (row == null || Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                Map<String, Object> slim = new LinkedHashMap<>();
                slim.put("id", row.get("id"));
                slim.put("project_id", row.get("project_id"));
                slim.put("owner_id", row.get("owner_id"));
                return new ArrayList<>(List.of(slim));
            }
            if (sql.contains("FROM dm_parameter_field f")) {
                long parameterId = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).longValue() : -1L;
                if (sql.contains("f.id = ?") && args.length > 2) {
                    Map<String, Object> field = activeFieldById(((Number) args[0]).longValue());
                    if (field == null) return List.of();
                    if (args.length > 3 && number(field.get("parameter_id")) != number(args[2])) return List.of();
                    return new ArrayList<>(List.of(field));
                }
                if (sql.contains("f.id = ?")) {
                    Map<String, Object> field = activeFieldById(((Number) args[0]).longValue());
                    return field == null ? List.of() : new ArrayList<>(List.of(field));
                }
                return new ArrayList<>(fieldsByParameter.getOrDefault(parameterId, List.of())
                        .stream().filter(field -> Integer.valueOf(0).equals(field.get("deleted"))).toList());
            }
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            sqls.add(sql);
            if (sql.contains("SELECT project_id FROM dm_parameter") && sql.contains("deleted = 1")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = parameters.get(id);
                if (row == null || !Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                return (List<T>) new ArrayList<>(List.of(row.get("project_id")));
            }
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            sqls.add(sql);
            if (sql.contains("SELECT COUNT(*) FROM dm_parameter_field")) {
                long parameterId = number(args[1]);
                String name = String.valueOf(args[2]);
                Long excludeId = args.length > 3 && args[3] instanceof Number ? ((Number) args[3]).longValue() : null;
                boolean englishQuery = sql.contains("field_name_en = ?");
                int count = 0;
                for (Map<String, Object> field : fieldsByParameter.getOrDefault(parameterId, List.of())) {
                    if (!Integer.valueOf(0).equals(field.get("deleted"))) continue;
                    String stored = String.valueOf(field.get(englishQuery ? "field_name_en" : "field_name_cn"));
                    if (!stored.equalsIgnoreCase(name)) continue;
                    if (excludeId != null && excludeId.longValue() == number(field.get("id"))) continue;
                    count++;
                }
                return (T) Integer.valueOf(count);
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_parameter")) {
                if (!sql.contains("parameter_name = ?") && !sql.contains("parameter_name_en = ?")) {
                    return (T) Long.valueOf(parameters.size());
                }
                Integer count = 0;
                long projectId = number(args[1]);
                String systemCode = String.valueOf(args[2]);
                String name = String.valueOf(args[3]);
                Long excludeId = args.length > 4 && args[4] instanceof Number ? ((Number) args[4]).longValue() : null;
                boolean englishNameQuery = sql.contains("parameter_name_en = ?");
                if ((!englishNameQuery && existingName != null && name.equals(existingName))
                        || (englishNameQuery && existingEnglishName != null && name.equalsIgnoreCase(existingEnglishName))) {
                    count++;
                }
                for (Map<String, Object> row : parameters.values()) {
                    if (projectId != number(row.get("project_id"))) continue;
                    if (!systemCode.equals(String.valueOf(row.get("system_code")))) continue;
                    String storedName = String.valueOf(row.get(englishNameQuery ? "parameter_name_en" : "parameter_name"));
                    if (englishNameQuery ? !name.equalsIgnoreCase(storedName) : !name.equals(storedName)) continue;
                    long id = number(row.get("id"));
                    if (excludeId != null && excludeId.longValue() == id) continue;
                    count++;
                }
                return (T) count;
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_component")) {
                return (T) Integer.valueOf(1);
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_dependency")) {
                return (T) Integer.valueOf(dependencyCount);
            }
            if (sql.contains("COALESCE(MAX(sort_no), 0) FROM dm_parameter_field")) {
                long parameterId = ((Number) args[1]).longValue();
                return (T) Integer.valueOf(fieldsByParameter.getOrDefault(parameterId, List.of())
                        .stream().map(field -> number(field.get("sort_no"))).max(Long::compareTo)
                        .map(Long::intValue).orElse(0));
            }
            return (T) Integer.valueOf(0);
        }

        private Map<String, Object> activeFieldById(long fieldId) {
            for (List<Map<String, Object>> fields : fieldsByParameter.values()) {
                for (Map<String, Object> field : fields) {
                    if (number(field.get("id")) == fieldId) return field;
                }
            }
            return null;
        }

        private long number(Object value) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
        }
    }
}

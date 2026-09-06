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

/** 迁移参数专属服务行为测试：必填校验、租户+项目+系统 内参数名称唯一、码值校验、模板导入逐行校验、回收站审计。 */
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
    void createInsertsDomainColumnsAndAudits() {
        jdbc.existingName = null;
        Map<String, Object> created = service.create(body("额度参数"), USER);
        assertEquals("额度参数", created.get("parameter_name"));
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
        assertTrue(service.downloadTemplate().length > 0);

        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移参数");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("参数类型");
            header.createCell(1).setCellValue("参数范围分类");
            header.createCell(2).setCellValue("系统编号");
            header.createCell(3).setCellValue("参数名称");
            header.createCell(4).setCellValue("参数说明");
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("业务参数");
            valid.createCell(1).setCellValue("公共参数");
            valid.createCell(2).setCellValue("SYS_A");
            valid.createCell(3).setCellValue("批量参数A");
            valid.createCell(4).setCellValue("说明A");
            Row invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("不存在的类型");
            invalid.createCell(1).setCellValue("公共参数");
            invalid.createCell(2).setCellValue("SYS_A");
            invalid.createCell(3).setCellValue("批量参数B");
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
        body.put("parameterDescription", "说明");
        return body;
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
        return new DataMigrationCodeValueService(refs);
    }

    private void assertRejected(Runnable action, String messageContains) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains(messageContains), error.getMessage());
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<Long, Map<String, Object>> parameters = new LinkedHashMap<>();
        private final List<String> audits = new ArrayList<>();
        private String existingName;

        private void putParameter(long id, long projectId, long ownerId) {
            putParameter(id, projectId, ownerId, "SYS_A");
        }

        private void putParameter(long id, long projectId, long ownerId, String systemCode) {
            Map<String, Object> row = baseRow();
            row.put("id", id);
            row.put("project_id", projectId);
            row.put("system_code", systemCode);
            row.put("parameter_name", "参数-" + id);
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

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                audits.add(String.valueOf(args[3]));
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
                row.put("parameter_description", args[7]);
                row.put("owner_id", args[8]);
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
                long id = ((Number) args[5]).longValue();
                Map<String, Object> row = parameters.get(id);
                if (row == null) return 0;
                row.put("parameter_type", args[0]);
                row.put("parameter_scope", args[1]);
                row.put("system_code", args[2]);
                row.put("parameter_name", args[3]);
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_parameter")) {
                long id = ((Number) args[0]).longValue();
                parameters.remove(id);
                return 1;
            }
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
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
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
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
            if (sql.contains("SELECT COUNT(*) FROM dm_parameter")) {
                Integer count = 0;
                long projectId = number(args[1]);
                String systemCode = String.valueOf(args[2]);
                String name = String.valueOf(args[3]);
                Long excludeId = args.length > 4 && args[4] instanceof Number ? ((Number) args[4]).longValue() : null;
                if (existingName != null && name.equals(existingName)) {
                    count++;
                }
                for (Map<String, Object> row : parameters.values()) {
                    if (projectId != number(row.get("project_id"))) continue;
                    if (!systemCode.equals(String.valueOf(row.get("system_code")))) continue;
                    if (!name.equals(String.valueOf(row.get("parameter_name")))) continue;
                    long id = number(row.get("id"));
                    if (excludeId != null && excludeId.longValue() == id) continue;
                    count++;
                }
                return (T) count;
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_component")) {
                return (T) Integer.valueOf(1);
            }
            return (T) Integer.valueOf(0);
        }

        private long number(Object value) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
        }
    }
}

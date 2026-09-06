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

/** 迁移检核规则专属服务行为测试：必填校验、规则编码全局唯一、系统归属、单条编辑、删除权限与回收站审计。 */
class RuleServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);
    private static final AuthUser OTHER = new AuthUser(88L, 1L, "other", "", "他人", 11L, true);
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 11L, true);
    private static final long PROJECT = 10L;

    private StubJdbcTemplate jdbc;
    private DataMigrationPermissionService permissions;
    private RuleService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplate();
        permissions = mock(DataMigrationPermissionService.class);
        service = new RuleService(jdbc, permissions, null, codeValues());

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
        assertRejected(() -> service.create(without("checkTargetType"), USER), "检核目标类型不能为空");
        assertRejected(() -> service.create(without("ruleCategory"), USER), "检核规则大类不能为空");
        assertRejected(() -> service.create(without("systemCode"), USER), "关联系统不能为空");
        assertRejected(() -> service.create(without("ruleCode"), USER), "规则编码不能为空");
    }

    @Test
    void createRejectsDuplicateRuleCodeAndUnknownCodes() {
        jdbc.existingRuleCode = "RULE-001";
        BusinessException conflict = assertThrows(BusinessException.class, () -> service.create(body("RULE-001"), USER));
        assertEquals(ErrorCode.CONFLICT, conflict.code());
        assertTrue(conflict.getMessage().contains("规则编码已存在"));

        jdbc.existingRuleCode = null;
        Map<String, Object> unknown = body("RULE-002");
        unknown.put("checkTargetType", "UNKNOWN");
        assertRejected(() -> service.create(unknown, USER), "无效或已停用");
    }

    @Test
    void deleteRejectsNonOwnerAndAudits() {
        DataMigrationPermissionService rejecting = mock(DataMigrationPermissionService.class);
        when(rejecting.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该规则操作权限")).when(rejecting).requireWrite(any(), anyLong());
        RuleService rejectingService = new RuleService(jdbc, rejecting, null, codeValues());

        jdbc.putRule(50L, PROJECT, OTHER.id());
        assertThrows(BusinessException.class, () -> rejectingService.delete(List.of(50L), USER));
        assertFalse(jdbc.audits.contains("RULE_DELETE"));

        jdbc.putRule(51L, PROJECT, USER.id());
        service.delete(List.of(51L), USER);
        assertTrue(jdbc.audits.contains("RULE_DELETE"));
    }

    @Test
    void recycleBinRestoreConflictAndPurgeAudit() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "需要管理员")).when(permissions).requireAdmin(USER);
        assertThrows(BusinessException.class, () -> service.restore(List.of(50L), USER));

        jdbc.putRule(61L, PROJECT, USER.id());
        jdbc.markDeleted(61L);
        jdbc.restoreUpdateCount = 0;
        assertThrows(BusinessException.class, () -> service.restore(List.of(61L), ADMIN));

        jdbc.putRule(62L, PROJECT, USER.id());
        jdbc.markDeleted(62L);
        service.purge(List.of(62L), ADMIN);
        assertTrue(jdbc.audits.contains("RULE_PURGE"));
    }

    @Test
    void templateDownloadAndImportReadDimensionsPerRowAndSkipInvalidRows() throws Exception {
        assertTrue(service.downloadTemplate().length > 0);

        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        assertRejected(() -> service.importRules(PROJECT, empty, USER), "Excel 文件不能为空");

        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移检核规则");
            Row header = sheet.createRow(0);
            String[] columns = {"检核目标类型", "检核规则大类", "系统编号", "表英文名", "表中文名", "字段英文名称", "字段中文名称", "规则编码", "规则编码说明", "检核规则说明"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("迁出检核规则");
            valid.createCell(1).setCellValue("基础检核");
            valid.createCell(2).setCellValue("SYS_A");
            valid.createCell(3).setCellValue("ACCOUNT");
            valid.createCell(4).setCellValue("账户表");
            valid.createCell(5).setCellValue("BALANCE");
            valid.createCell(6).setCellValue("余额");
            valid.createCell(7).setCellValue("RULE-IMP-001");
            valid.createCell(8).setCellValue("编码说明");
            valid.createCell(9).setCellValue("检核说明");
            Row invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("不存在的类型");
            invalid.createCell(1).setCellValue("基础检核");
            invalid.createCell(2).setCellValue("SYS_A");
            invalid.createCell(7).setCellValue("RULE-IMP-002");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        Map<String, Object> result = service.importRules(PROJECT, new MockMultipartFile(
                "file", "rules.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), USER);
        assertEquals(2, result.get("rows"));
        assertEquals(1, result.get("accepted"));
        assertEquals(1, result.get("failed"));
    }

    // ============ 辅助 ============

    private Map<String, Object> without(String key) {
        Map<String, Object> copy = body("RULE-001");
        copy.remove(key);
        return copy;
    }

    private Map<String, Object> body(String ruleCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", PROJECT);
        body.put("checkTargetType", "MIGRATE_OUT_CHECK");
        body.put("ruleCategory", "BASIC_CHECK");
        body.put("systemCode", "SYS_A");
        body.put("ruleCode", ruleCode);
        body.put("tableNameEn", "ACCOUNT");
        body.put("tableNameCn", "账户表");
        return body;
    }

    private DataMigrationCodeValueService codeValues() {
        SystemReferenceQuery refs = mock(SystemReferenceQuery.class);
        when(refs.activeParameters(any(), eq("DM_RULE_TARGET_TYPE")))
                .thenReturn(List.of(new SystemParameterReference("DM_RULE_TARGET_TYPE.MIGRATE_OUT_CHECK", "迁出检核规则")));
        when(refs.activeParameters(any(), eq("DM_RULE_CATEGORY")))
                .thenReturn(List.of(new SystemParameterReference("DM_RULE_CATEGORY.BASIC_CHECK", "基础检核")));
        return new DataMigrationCodeValueService(refs);
    }

    private void assertRejected(Runnable action, String messageContains) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains(messageContains), error.getMessage());
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<Long, Map<String, Object>> rules = new LinkedHashMap<>();
        private final List<String> audits = new ArrayList<>();
        private String existingRuleCode;
        private int restoreUpdateCount = 1;

        private void putRule(long id, long projectId, long ownerId) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("project_id", projectId);
            row.put("system_code", "SYS_A");
            row.put("check_target_type", "MIGRATE_OUT_CHECK");
            row.put("rule_category", "BASIC_CHECK");
            row.put("rule_code", "RULE-" + id);
            row.put("rule_code_desc", null);
            row.put("rule_description", null);
            row.put("table_name_en", "ACCOUNT");
            row.put("table_name_cn", "账户表");
            row.put("field_name_en", "");
            row.put("field_name_cn", "");
            row.put("owner_id", ownerId);
            row.put("deleted", 0);
            rules.put(id, row);
        }

        private void markDeleted(long id) {
            Map<String, Object> row = rules.get(id);
            if (row != null) row.put("deleted", 1);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                audits.add(String.valueOf(args[3]));
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_rule")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("project_id", ((Number) args[2]).longValue());
                row.put("system_code", args[3]);
                row.put("check_target_type", args[4]);
                row.put("rule_category", args[5]);
                row.put("rule_code", args[6]);
                row.put("rule_code_desc", args[7]);
                row.put("rule_description", args[8]);
                row.put("table_name_en", args[9]);
                row.put("table_name_cn", args[10]);
                row.put("field_name_en", args[11]);
                row.put("field_name_cn", args[12]);
                row.put("owner_id", args[13]);
                row.put("deleted", 0);
                rules.put(id, row);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_rule SET deleted = 1")) {
                long id = ((Number) args[2]).longValue();
                Map<String, Object> row = rules.get(id);
                if (row == null) return 0;
                row.put("deleted", 1);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_rule SET deleted = 0")) {
                if (restoreUpdateCount == 0) return 0;
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = rules.get(id);
                if (row == null) return 0;
                row.put("deleted", 0);
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_rule")) {
                long id = ((Number) args[0]).longValue();
                rules.remove(id);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_rule SET check_target_type")) {
                long id = ((Number) args[10]).longValue();
                Map<String, Object> row = rules.get(id);
                if (row == null) return 0;
                row.put("check_target_type", args[0]);
                row.put("rule_category", args[1]);
                row.put("system_code", args[2]);
                row.put("rule_code", args[3]);
                return 1;
            }
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("SELECT project_id FROM dm_rule") && sql.contains("deleted = 1")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = rules.get(id);
                if (row == null || !Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                return (List<T>) new ArrayList<>(List.of(row.get("project_id")));
            }
            return List.of();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM dm_rule a")) {
                return new ArrayList<>(rules.values());
            }
            if (sql.contains("FROM dm_rule WHERE")) {
                long id = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).longValue() : 1L;
                Map<String, Object> row = rules.get(id);
                return row == null || Integer.valueOf(1).equals(row.get("deleted")) ? List.of() : List.of(new LinkedHashMap<>(row));
            }
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("SELECT COUNT(*) FROM dm_rule")) {
                if (requiredType == Long.class) {
                    long count = rules.values().stream().filter(r -> Integer.valueOf(1).equals(r.get("deleted"))).count();
                    return requiredType.cast(count);
                }
                String code = args.length > 1 ? String.valueOf(args[1]) : null;
                return requiredType.cast(code != null && code.equals(existingRuleCode) ? 1 : 0);
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_component")) {
                return requiredType.cast(1);
            }
            return requiredType.cast(0);
        }
    }
}

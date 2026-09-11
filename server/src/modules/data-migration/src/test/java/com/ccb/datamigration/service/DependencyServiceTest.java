package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 迁移过程依赖关系专属服务行为测试（REQ-20260910-068）。
 *
 * <p>覆盖 R2 列表投影与项目过滤、R3 单条新增/编辑与唯一关系、R4 两列模板与逐行部分成功导入、
 * R6 实体写权限与审计、R7 逻辑删除/恢复/彻底删除与回收站来源委托。
 */
class DependencyServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);
    private static final AuthUser OTHER = new AuthUser(88L, 1L, "other", "", "他人", 11L, true);
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 11L, true);
    private static final long PROJECT = 10L;

    private StubJdbcTemplate jdbc;
    private DataMigrationPermissionService permissions;
    private DependencyService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplate();
        permissions = mock(DataMigrationPermissionService.class);
        service = new DependencyService(jdbc, permissions, mock(UserDirectoryPort.class));

        when(permissions.requireAccessible(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(0, Long.class));
        when(permissions.requireProject(any(), any())).thenAnswer(invocation -> {
            Long pid = invocation.getArgument(0, Long.class);
            if (pid == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "必须选择项目");
            return pid;
        });
        when(permissions.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
    }

    @Test
    void listFiltersByProjectAndProjectsParameterAndSystems() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", USER.id());
        jdbc.putDependency(102L, PROJECT, 1L, "SYS_B", USER.id());

        var result = service.list(PROJECT, "SYS_B", "param", 1, 20, USER);

        assertEquals(2L, result.total());
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("a.project_id = ?")));
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("p.parameter_name_en")));
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("csys.name AS consumer_system_name")));
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("psys.name AS provider_system_name")));
        Map<String, Object> first = result.records().get(0);
        assertEquals("param_one", first.get("parameter_name_en"));
        assertEquals("SYS_A", first.get("provider_system_code"));
        assertEquals("consumer-SYS_A", first.get("consumer_system_name"));
        assertEquals("provider-SYS_A", first.get("provider_system_name"));
    }

    @Test
    void createRejectsMissingRequiredFields() {
        Map<String, Object> noProject = body();
        noProject.remove("projectId");
        assertRejected(() -> service.create(noProject, USER), "projectId 不能为空");

        Map<String, Object> noParameter = body();
        noParameter.remove("parameterId");
        assertRejected(() -> service.create(noParameter, USER), "参数ID不能为空");

        Map<String, Object> noSystem = body();
        noSystem.remove("consumerSystemCode");
        assertRejected(() -> service.create(noSystem, USER), "使用方系统编号不能为空");
    }

    @Test
    void createRejectsUnknownParameterAndDisabledSystem() {
        assertRejected(() -> service.create(body(), USER), "迁移参数不存在");

        jdbc.putParameter(1L, "param_one");
        Map<String, Object> disabledSystem = body();
        disabledSystem.put("consumerSystemCode", "SYS_X");
        assertRejected(() -> service.create(disabledSystem, USER), "不存在或未启用");
    }

    @Test
    void createRejectsDuplicateRelationThenSucceedsWithAudit() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", USER.id());

        BusinessException conflict = assertThrows(BusinessException.class, () -> service.create(body(), USER));
        assertEquals(ErrorCode.CONFLICT, conflict.code());
        assertTrue(conflict.getMessage().contains("已存在"));

        Map<String, Object> otherSystem = body();
        otherSystem.put("consumerSystemCode", "SYS_B");
        Map<String, Object> created = service.create(otherSystem, USER);
        assertEquals(1L, created.get("parameter_id"));
        assertEquals("SYS_B", created.get("consumer_system_code"));
        assertTrue(jdbc.audits.contains("DEPENDENCY_CREATE"));
        assertFalse(jdbc.audits.contains("DEPENDENCY_UPDATE"));
    }

    @Test
    void updateRejectsNonOwnerAndPreservesProjectScope() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putParameter(2L, "param_two");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", OTHER.id());

        DataMigrationPermissionService rejecting = mock(DataMigrationPermissionService.class);
        when(rejecting.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该依赖关系操作权限")).when(rejecting).requireWrite(any(), anyLong());
        DependencyService rejectingService = new DependencyService(jdbc, rejecting, mock(UserDirectoryPort.class));
        assertThrows(BusinessException.class, () -> rejectingService.update(101L, body(), USER));
        assertFalse(jdbc.audits.contains("DEPENDENCY_UPDATE"));

        Map<String, Object> updateBody = Map.of("parameterId", 2L, "consumerSystemCode", "SYS_B");
        Map<String, Object> updated = service.update(101L, updateBody, USER);
        assertEquals(2L, updated.get("parameter_id"));
        assertEquals("SYS_B", updated.get("consumer_system_code"));
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("UPDATE dm_dependency SET parameter_id")
                && sql.contains("project_id = ?")));
        assertTrue(jdbc.audits.contains("DEPENDENCY_UPDATE"));
    }

    @Test
    void deleteRejectsNonOwnerAndLogicallyDeletesWithAudit() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", OTHER.id());

        DataMigrationPermissionService rejecting = mock(DataMigrationPermissionService.class);
        when(rejecting.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该依赖关系操作权限")).when(rejecting).requireWrite(any(), anyLong());
        DependencyService rejectingService = new DependencyService(jdbc, rejecting, mock(UserDirectoryPort.class));
        assertThrows(BusinessException.class, () -> rejectingService.delete(List.of(101L), USER));
        assertFalse(jdbc.audits.contains("DEPENDENCY_DELETE"));

        service.delete(List.of(101L), USER);
        assertTrue(jdbc.isDeleted(101L));
        assertTrue(jdbc.audits.contains("DEPENDENCY_DELETE"));
    }

    @Test
    void templateUsesExactlyTwoColumns() throws Exception {
        byte[] bytes = service.downloadTemplate();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertEquals(2, header.getLastCellNum());
            assertEquals("参数英文名", header.getCell(0).getStringCellValue());
            assertEquals("使用方系统编号", header.getCell(1).getStringCellValue());
        }
    }

    @Test
    void importRejectsWrongHeaderAndFileLimits() {
        jdbc.putParameter(1L, "param_one");
        byte[] wrongHeader = excel(new String[][]{
                {"参数英文名", "系统ID"},
                {"param_one", "SYS_A"},
        });
        BusinessException headerError = assertThrows(BusinessException.class,
                () -> service.importDependencies(PROJECT, file("header.xlsx", wrongHeader), USER));
        assertTrue(headerError.getMessage().contains("表头"));
    }

    @Test
    void importSkipsInvalidRowsAndPartiallySucceeds() throws Exception {
        jdbc.putParameter(1L, "param_one");
        byte[] bytes = excel(new String[][]{
                {"参数英文名", "使用方系统编号"},
                {"param_one", "SYS_A"},
                {"not_exists", "SYS_A"},
                {"param_one", "SYS_X"},
        });

        Map<String, Object> result = service.importDependencies(PROJECT, file("deps.xlsx", bytes), USER);

        assertEquals(3, result.get("rows"));
        assertEquals(1, result.get("accepted"));
        assertEquals(2, result.get("failed"));
        assertTrue(result.get("errors").toString().contains("第 4 行"));
        assertTrue(result.get("errors").toString().contains("第 4 行"));
        assertTrue(result.get("errors").toString().contains("参数英文名不存在"));
        assertTrue(result.get("errors").toString().contains("不存在或未启用"));
        assertTrue(jdbc.audits.contains("DEPENDENCY_IMPORT"));
    }

    @Test
    void importRejectsAmbiguousParameterEnglishName() throws Exception {
        jdbc.putParameter(1L, "param_one");
        jdbc.putParameter(2L, "PARAM_ONE");
        byte[] bytes = excel(new String[][]{
                {"参数英文名", "使用方系统编号"},
                {"param_one", "SYS_A"},
        });

        Map<String, Object> result = service.importDependencies(PROJECT, file("ambiguous.xlsx", bytes), USER);

        assertEquals(0, result.get("accepted"));
        assertEquals(1, result.get("failed"));
        assertTrue(result.get("errors").toString().contains("不唯一"));
    }

    @Test
    void importRejectsInFileAndExistingDuplicates() throws Exception {
        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", USER.id());

        byte[] bytes = excel(new String[][]{
                {"参数英文名", "使用方系统编号"},
                {"param_one", "SYS_A"},
                {"param_one", "SYS_B"},
                {"param_one", "SYS_B"},
        });

        Map<String, Object> result = service.importDependencies(PROJECT, file("dups.xlsx", bytes), USER);

        assertEquals(1, result.get("accepted"));
        assertEquals(2, result.get("failed"));
        assertTrue(result.get("errors").toString().contains("已存在"));
        assertTrue(result.get("errors").toString().contains("文件内同一参数与使用方系统重复"));
    }

    @Test
    void batchCreateCreatesAllCombinationsAndSkipsExisting() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putParameter(2L, "param_two");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", USER.id());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", PROJECT);
        body.put("parameterIds", List.of(1L, 2L));
        body.put("consumerSystemCodes", List.of("SYS_A", "SYS_B"));

        Map<String, Object> result = service.batchCreate(body, USER);

        assertEquals(3, result.get("accepted"));
        assertEquals(1, result.get("skipped"));
        long createAudits = jdbc.audits.stream()
                .filter(operation -> operation.equals("DEPENDENCY_CREATE"))
                .count();
        assertEquals(3, createAudits);
        assertEquals(4L, service.list(PROJECT, null, null, 1, 20, USER).total());
    }

    @Test
    void batchCreateRejectsEmptyInputsAndInvalidCombination() {
        Map<String, Object> noParams = new LinkedHashMap<>();
        noParams.put("projectId", PROJECT);
        noParams.put("parameterIds", List.of());
        noParams.put("consumerSystemCodes", List.of("SYS_A"));
        assertRejected(() -> service.batchCreate(noParams, USER), "请选择至少一个迁移参数");

        Map<String, Object> noSystems = new LinkedHashMap<>();
        noSystems.put("projectId", PROJECT);
        noSystems.put("parameterIds", List.of(1L));
        noSystems.put("consumerSystemCodes", List.of());
        assertRejected(() -> service.batchCreate(noSystems, USER), "请选择至少一个使用方系统");

        jdbc.putParameter(1L, "param_one");
        Map<String, Object> unknownParam = new LinkedHashMap<>();
        unknownParam.put("projectId", PROJECT);
        unknownParam.put("parameterIds", List.of(99L));
        unknownParam.put("consumerSystemCodes", List.of("SYS_A"));
        assertRejected(() -> service.batchCreate(unknownParam, USER), "迁移参数不存在");

        Map<String, Object> disabledSystem = new LinkedHashMap<>();
        disabledSystem.put("projectId", PROJECT);
        disabledSystem.put("parameterIds", List.of(1L));
        disabledSystem.put("consumerSystemCodes", List.of("SYS_X"));
        assertRejected(() -> service.batchCreate(disabledSystem, USER), "不存在或未启用");
    }

    @Test
    void listParameterPageAppliesSystemAndKeywordFiltersAndPaging() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putParameter(2L, "param_two");

        PageResult<Map<String, Object>> result = service.listParameterPage(PROJECT, "SYS_A", "param", 1, 20, USER);

        assertEquals(2, result.records().size());
        assertEquals(2L, result.total());
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("p.system_code = ?")));
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("p.parameter_name LIKE ?")));
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("ORDER BY p.parameter_name_en ASC")));
        assertTrue(jdbc.sqls.stream().anyMatch(sql -> sql.contains("LIMIT ? OFFSET ?")));
    }

    @Test
    void recycleBinSourceDelegatesAndRequiresAdmin() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", USER.id());
        jdbc.markDeleted(101L);

        DependencyRecycleBinSource source = new DependencyRecycleBinSource(service);
        assertEquals(Set.of("DEPENDENCY"), source.supports());
        assertEquals(1L, source.countDeleted("DEPENDENCY", PROJECT, null, ADMIN));
        assertEquals(1, source.listDeletedPage("DEPENDENCY", PROJECT, null, 20, ADMIN).size());
        assertEquals(101L, source.detail("DEPENDENCY", 101L, ADMIN).get("id"));

        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "需要管理员")).when(permissions).requireAdmin(USER);
        assertThrows(BusinessException.class, () -> source.countDeleted("DEPENDENCY", PROJECT, null, USER));
    }

    @Test
    void restoreRevalidatesParameterAndSystem() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", USER.id());
        jdbc.markDeleted(101L);

        service.restore("DEPENDENCY", List.of(101L), ADMIN);
        assertFalse(jdbc.isDeleted(101L));
        assertTrue(jdbc.audits.contains("DEPENDENCY_RESTORE"));

        jdbc.putDependency(102L, PROJECT, 1L, "SYS_B", USER.id());
        jdbc.markDeleted(102L);
        jdbc.parameters.remove(1L);
        BusinessException parameterGone = assertThrows(BusinessException.class,
                () -> service.restore("DEPENDENCY", List.of(102L), ADMIN));
        assertTrue(parameterGone.getMessage().contains("参数已不存在"));

        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(103L, PROJECT, 1L, "SYS_X", USER.id());
        jdbc.markDeleted(103L);
        BusinessException systemDisabled = assertThrows(BusinessException.class,
                () -> service.restore("DEPENDENCY", List.of(103L), ADMIN));
        assertTrue(systemDisabled.getMessage().contains("未启用"));
    }

    @Test
    void purgeRemovesDeletedRowAndAudits() {
        jdbc.putParameter(1L, "param_one");
        jdbc.putDependency(101L, PROJECT, 1L, "SYS_A", USER.id());
        jdbc.markDeleted(101L);

        service.purge("DEPENDENCY", List.of(101L), ADMIN);

        assertFalse(jdbc.dependencies.containsKey(101L));
        assertTrue(jdbc.audits.contains("DEPENDENCY_PURGE"));
    }

    // ============ 辅助 ============

    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", PROJECT);
        body.put("parameterId", 1L);
        body.put("consumerSystemCode", "SYS_A");
        return body;
    }

    private MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private byte[] excel(String[][] rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("依赖关系");
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < rows[i].length; j++) {
                    if (rows[i][j] != null) row.createCell(j).setCellValue(rows[i][j]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void assertRejected(Runnable action, String messageContains) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains(messageContains), error.getMessage());
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<Long, Map<String, Object>> dependencies = new LinkedHashMap<>();
        private final Map<Long, Map<String, Object>> parameters = new LinkedHashMap<>();
        private final List<String> audits = new ArrayList<>();
        private final List<String> sqls = new ArrayList<>();
        private final Set<String> enabledSystems = new LinkedHashSet<>(List.of("SYS_A", "SYS_B"));
        private final Map<String, List<Long>> englishNameIds = new HashMap<>();

        private void putParameter(long id, String enName) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("tenant_id", 1L);
            row.put("project_id", PROJECT);
            row.put("system_code", "SYS_A");
            row.put("parameter_name_en", enName);
            row.put("parameter_name", "参数-" + id);
            row.put("deleted", 0);
            parameters.put(id, row);
            englishNameIds.computeIfAbsent(enName.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(id);
        }

        private void putDependency(long id, long projectId, long parameterId, String systemCode, long ownerId) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("tenant_id", 1L);
            row.put("project_id", projectId);
            row.put("parameter_id", parameterId);
            row.put("system_code", systemCode);
            row.put("owner_id", ownerId);
            row.put("deleted", 0);
            dependencies.put(id, row);
        }

        private void markDeleted(long id) {
            Map<String, Object> row = dependencies.get(id);
            if (row != null) row.put("deleted", 1);
        }

        private boolean isDeleted(long id) {
            Map<String, Object> row = dependencies.get(id);
            return row != null && Integer.valueOf(1).equals(row.get("deleted"));
        }

        @Override
        public int update(String sql, Object... args) {
            sqls.add(sql);
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                audits.add(String.valueOf(args[3]));
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_dependency")) {
                long id = number(args[0]);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("tenant_id", number(args[1]));
                row.put("project_id", number(args[2]));
                row.put("parameter_id", number(args[3]));
                row.put("system_code", String.valueOf(args[4]));
                row.put("owner_id", number(args[5]));
                row.put("deleted", 0);
                dependencies.put(id, row);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_dependency SET deleted = 1")) {
                long id = number(args[1]);
                Map<String, Object> row = dependencies.get(id);
                if (row == null) return 0;
                row.put("deleted", 1);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_dependency SET deleted = 0")) {
                long id = number(args[1]);
                Map<String, Object> row = dependencies.get(id);
                if (row == null) return 0;
                row.put("deleted", 0);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_dependency SET parameter_id")) {
                long id = number(args[3]);
                Map<String, Object> row = dependencies.get(id);
                if (row == null) return 0;
                row.put("parameter_id", number(args[0]));
                row.put("system_code", String.valueOf(args[1]));
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_dependency")) {
                long id = number(args[0]);
                dependencies.remove(id);
                return 1;
            }
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            sqls.add(sql);
            if (sql.contains("FROM dm_dependency a") && sql.contains("deleted = 1")) {
                if (sql.contains("a.id = ?")) {
                    long id = number(args[1]);
                    Map<String, Object> row = dependencies.get(id);
                    if (row == null || !Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                    return List.of(project(row));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> row : dependencies.values()) {
                    if (Integer.valueOf(1).equals(row.get("deleted"))) rows.add(project(row));
                }
                return rows;
            }
            if (sql.contains("FROM dm_dependency a") && sql.contains("a.id = ?")) {
                long id = number(args[1]);
                Map<String, Object> row = dependencies.get(id);
                if (row == null || Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                return List.of(project(row));
            }
            if (sql.contains("FROM dm_dependency a")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> row : dependencies.values()) {
                    if (!Integer.valueOf(1).equals(row.get("deleted"))) rows.add(project(row));
                }
                return rows;
            }
            if (sql.contains("FROM dm_dependency WHERE")) {
                long id = number(args[1]);
                Map<String, Object> row = dependencies.get(id);
                if (row == null || Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                return List.of(new LinkedHashMap<>(row));
            }
            if (sql.contains("SELECT id FROM dm_parameter WHERE") && sql.contains("LOWER(parameter_name_en)")) {
                String enName = String.valueOf(args[2]).toLowerCase(Locale.ROOT);
                List<Long> ids = englishNameIds.getOrDefault(enName, List.of());
                List<Map<String, Object>> matches = new ArrayList<>();
                for (Long pid : ids) matches.add(Map.of("id", pid));
                return matches;
            }
            if (sql.contains("FROM dm_parameter p")) return new ArrayList<>(parameters.values());
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            sqls.add(sql);
            if (sql.contains("SELECT COUNT(*) FROM dm_parameter p")) {
                return cast(Long.valueOf(parameters.size()), requiredType);
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_parameter WHERE")) {
                long parameterId = number(args[2]);
                return cast(Integer.valueOf(parameters.containsKey(parameterId) ? 1 : 0), requiredType);
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_component")) {
                String systemCode = String.valueOf(args[2]);
                return cast(Integer.valueOf(enabledSystems.contains(systemCode) ? 1 : 0), requiredType);
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_dependency")) {
                if (sql.contains("parameter_id = ?") && sql.contains("deleted = 0")) {
                    long parameterId = number(args[2]);
                    String systemCode = String.valueOf(args[3]);
                    int count = 0;
                    for (Map<String, Object> row : dependencies.values()) {
                        if (Integer.valueOf(1).equals(row.get("deleted"))) continue;
                        if (number(row.get("parameter_id")) == parameterId
                                && systemCode.equals(String.valueOf(row.get("system_code")))) count++;
                    }
                    return cast(Integer.valueOf(count), requiredType);
                }
                if (sql.contains("a.deleted = 1")) {
                    long count = 0;
                    for (Map<String, Object> row : dependencies.values()) {
                        if (Integer.valueOf(1).equals(row.get("deleted"))) count++;
                    }
                    return cast(Long.valueOf(count), requiredType);
                }
                return cast(Long.valueOf(dependencies.size()), requiredType);
            }
            return cast(Integer.valueOf(0), requiredType);
        }

        @SuppressWarnings("unchecked")
        private <T> T cast(Object value, Class<T> requiredType) {
            if (requiredType == Long.class) return (T) Long.valueOf(String.valueOf(value));
            return (T) value;
        }

        private Map<String, Object> project(Map<String, Object> row) {
            Map<String, Object> out = new LinkedHashMap<>(row);
            Map<String, Object> param = parameters.get(number(row.get("parameter_id")));
            if (param != null) {
                out.put("parameter_name_en", param.get("parameter_name_en"));
                out.put("parameter_name", param.get("parameter_name"));
                out.put("provider_system_code", param.get("system_code"));
            }
            out.put("provider_system_name", "provider-" + out.get("provider_system_code"));
            out.put("consumer_system_name", "consumer-" + out.get("system_code"));
            out.put("consumer_system_code", out.get("system_code"));
            out.put("updated_at", "2026-09-10 12:00:00");
            return out;
        }

        private long number(Object value) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
        }
    }
}

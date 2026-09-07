package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 迁移映射专属服务行为测试：必填校验、参数管理映射类型、系统编号项目校验、
 * 多文件绑定与编辑重设、审计、逻辑删除及回收站管理。
 */
class MappingServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);
    private static final AuthUser OTHER = new AuthUser(88L, 1L, "other", "", "他人", 11L, true);
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 11L, true);
    private static final long PROJECT = 10L;

    private StubJdbcTemplate jdbc;
    private ContentAttachmentService attachments;
    private ContentFileAssetService fileAssets;
    private DataMigrationPermissionService permissions;
    private MappingService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplate();
        attachments = mock(ContentAttachmentService.class);
        fileAssets = mock(ContentFileAssetService.class);
        permissions = mock(DataMigrationPermissionService.class);
        service = new MappingService(jdbc, attachments, fileAssets, permissions, null,
                new ContentDocCodeGenerator(), codeValues());

        when(permissions.requireAccessible(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(0, Long.class));
        when(permissions.requireProject(any(), any())).thenAnswer(invocation -> {
            Long pid = invocation.getArgument(0, Long.class);
            if (pid == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "必须选择项目");
            return pid;
        });
        when(permissions.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        when(fileAssets.resolveAttachment(101L, USER)).thenReturn(item(101L, "a.sql"));
        when(fileAssets.resolveAttachment(102L, USER)).thenReturn(item(102L, "b.sh"));
        when(attachments.list(any(), anyLong(), anyLong())).thenReturn(attachments());
    }

    @Test
    void createRejectsMissingRequiredFields() {
        assertRejected(() -> service.create(without("projectId"), USER), "projectId 不能为空");
        assertRejected(() -> service.create(without("mappingType"), USER), "映射类型不能为空");
        assertRejected(() -> service.create(without("systemCode"), USER), "系统编号不能为空");
        assertRejected(() -> service.create(without("fileName"), USER), "文件名称不能为空");
        assertRejected(() -> service.create(without("files"), USER), "请上传源文件");
    }

    @Test
    void createAndUpdateRejectFileNameLongerThan200Characters() {
        Map<String, Object> boundaryBody = body();
        boundaryBody.put("fileName", "文".repeat(200));
        service.create(boundaryBody, USER);
        assertTrue(jdbc.audits.contains("MAPPING_CREATE"));
        jdbc.audits.clear();

        Map<String, Object> createBody = body();
        createBody.put("fileName", "a".repeat(201));
        assertRejected(() -> service.create(createBody, USER), "不能超过 200 个字符");

        jdbc.putMapping(51L, PROJECT, USER.id(), false);
        Map<String, Object> patch = singleMappingPatch();
        patch.put("fileName", "中".repeat(201));
        assertRejected(() -> service.update(51L, patch, USER), "不能超过 200 个字符");
        assertFalse(jdbc.audits.contains("MAPPING_UPDATE"));
    }

    @Test
    void createAcceptsMultipleFilesAndAudits() {
        Map<String, Object> body = body();
        body.put("files", List.of(
                Map.of("attachmentId", 101L, "fileName", "a.sql"),
                Map.of("attachmentId", 102L, "fileName", "b.sh")));
        Map<String, Object> created = service.create(body, USER);
        assertEquals("MIGRATE_OUT", created.get("mapping_type"));
        assertEquals("SYS_A", created.get("system_code"));
        assertTrue(((String) created.get("asset_code")).startsWith("MAP-"));
        assertTrue(jdbc.audits.contains("MAPPING_CREATE"));

        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachments).replaceAll(any(), any(), anyLong(), anyLong(), captor.capture(), any());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void createRejectsFileOver50mb() {
        when(fileAssets.resolveAttachment(103L, USER)).thenReturn(new com.ccb.attachment.integration.AttachmentItem(
                103L, "big.zip", "application/zip", 50L * 1024 * 1024 + 1, "zip", "TEMP", null, null, null,
                USER.id(), LocalDateTime.now()));
        Map<String, Object> body = body();
        body.put("files", List.of(Map.of("attachmentId", 103L, "fileName", "big.zip")));
        assertRejected(() -> service.create(body, USER), "超过 50 MB");
    }

    @Test
    void rejectsUnknownOrInactiveTypeAndForeignSystem() {
        Map<String, Object> unknownType = body();
        unknownType.put("mappingType", "UNKNOWN");
        assertRejected(() -> service.create(unknownType, USER), "值无效或已停用");

        String original = jdbc.foreignSystem;
        jdbc.foreignSystem = "SYS_B";
        BusinessException systemError = assertThrows(BusinessException.class, () -> service.create(body(), ADMIN));
        assertTrue(systemError.getMessage().contains("系统编号不存在或不属于当前项目"));
        jdbc.foreignSystem = original;
    }

    @Test
    void updateRejectsNonOwnerAndUpdatesMetadata() {
        DataMigrationPermissionService rejecting = mock(DataMigrationPermissionService.class);
        when(rejecting.requireAccessible(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(0, Long.class));
        when(rejecting.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该映射文件操作权限")).when(rejecting).requireWrite(any(), anyLong());
        MappingService rejectingService = new MappingService(jdbc, attachments, fileAssets, rejecting, null,
                new ContentDocCodeGenerator(), codeValues());
        jdbc.putMapping(50L, PROJECT, OTHER.id(), false);
        assertThrows(BusinessException.class, () -> rejectingService.update(50L, singleMappingPatch(), USER));

        jdbc.putMapping(51L, PROJECT, USER.id(), false);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("mappingType", "MIGRATE_IN");
        patch.put("systemCode", "SYS_A");
        patch.put("fileName", "迁入映射文件");
        Map<String, Object> updated = service.update(51L, patch, USER);
        assertEquals("MIGRATE_IN", updated.get("mapping_type"));
        assertEquals("迁入映射文件", updated.get("asset_name"));
        assertTrue(jdbc.audits.contains("MAPPING_UPDATE"));
    }

    @Test
    void updateWithExplicitFilesReplacesAttachmentSet() {
        jdbc.putMapping(51L, PROJECT, USER.id(), false);
        Map<String, Object> patch = singleMappingPatch();
        patch.put("files", List.of(Map.of("attachmentId", 101L, "fileName", "a.sql")));
        service.update(51L, patch, USER);
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachments).replaceAll(any(), any(), anyLong(), anyLong(), captor.capture(), any());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void rejectsEmptyOrDuplicateAttachmentSet() {
        jdbc.putMapping(51L, PROJECT, USER.id(), false);
        Map<String, Object> emptyPatch = singleMappingPatch();
        emptyPatch.put("files", List.of());
        assertRejected(() -> service.update(51L, emptyPatch, USER), "至少保留一个源文件");
        verify(attachments, org.mockito.Mockito.never()).replaceAll(any(), any(), anyLong(), anyLong(), any(), any());
        assertFalse(jdbc.audits.contains("MAPPING_UPDATE"));

        Map<String, Object> duplicateCreate = body();
        duplicateCreate.put("files", List.of(
                Map.of("attachmentId", 101L, "fileName", "a.sql"),
                Map.of("attachmentId", 101L, "fileName", "a.sql")));
        assertRejected(() -> service.create(duplicateCreate, USER), "附件不能重复选择");
        assertFalse(jdbc.audits.contains("MAPPING_CREATE"));
    }

    @Test
    void deleteRejectsNonOwnerAndLogsDelete() {
        DataMigrationPermissionService rejecting = mock(DataMigrationPermissionService.class);
        when(rejecting.requireAccessible(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(0, Long.class));
        when(rejecting.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该映射文件操作权限")).when(rejecting).requireWrite(any(), anyLong());
        MappingService rejectingService = new MappingService(jdbc, attachments, fileAssets, rejecting, null,
                new ContentDocCodeGenerator(), codeValues());

        jdbc.putMapping(50L, PROJECT, OTHER.id(), false);
        assertThrows(BusinessException.class, () -> rejectingService.delete(List.of(50L), USER));
        assertFalse(jdbc.audits.contains("MAPPING_DELETE"));

        jdbc.putMapping(51L, PROJECT, USER.id(), false);
        service.delete(List.of(51L), USER);
        assertTrue(jdbc.audits.contains("MAPPING_DELETE"));
    }

    @Test
    void listRequiresProjectAndValidatesMappingType() {
        BusinessException projectError = assertThrows(BusinessException.class,
                () -> service.list(null, null, null, null, 1, 20, USER));
        assertTrue(projectError.getMessage().contains("项目"));

        BusinessException typeError = assertThrows(BusinessException.class,
                () -> service.list(PROJECT, "UNKNOWN", null, "关键字", 1, 20, USER));
        assertTrue(typeError.getMessage().contains("值无效或已停用"), typeError.getMessage());
    }

    @Test
    void downloadReturnsFirstAttachmentOrRequestedOne() {
        jdbc.putMapping(50L, PROJECT, USER.id(), false);
        assertEquals("/api/attachments/101/download", service.download(50L, null, USER));
        assertEquals("/api/attachments/102/download", service.download(50L, 102L, USER));
        BusinessException error = assertThrows(BusinessException.class, () -> service.download(50L, 999L, USER));
        assertTrue(error.getMessage().contains("附件不存在或不属于该迁移映射"));
    }

    @Test
    void recycleBinOperationsAreAdminOnlyAndAudit() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "需要管理员")).when(permissions).requireAdmin(USER);
        assertThrows(BusinessException.class, () -> service.restore(List.of(50L), USER));
        assertThrows(BusinessException.class, () -> service.purge(List.of(50L), USER));

        jdbc.putMapping(50L, PROJECT, USER.id(), true);
        jdbc.putMapping(51L, PROJECT, USER.id(), true);
        service.restore(List.of(50L), ADMIN);
        assertTrue(jdbc.audits.contains("MAPPING_RESTORE"));
        service.purge(List.of(51L), ADMIN);
        assertTrue(jdbc.audits.contains("MAPPING_PURGE"));
        verify(attachments).unbindAndRemoveAll(any(), any(), anyLong(), any());
    }

    @Test
    void purgeDoesNotAuditWhenRowChangedConcurrently() {
        jdbc.putMapping(52L, PROJECT, USER.id(), true);
        jdbc.purgeUpdateCount = 0;
        BusinessException error = assertThrows(BusinessException.class, () -> service.purge(List.of(52L), ADMIN));
        assertEquals(ErrorCode.CONFLICT, error.code());
        assertFalse(jdbc.audits.contains("MAPPING_PURGE"));
        verify(attachments, org.mockito.Mockito.never()).unbindAndRemoveAll(any(), any(), anyLong(), any());
    }

    @Test
    void typeOptionsComeFromSupportedSources() {
        List<Map<String, Object>> typeOptions = service.getTypeOptions(USER);
        assertEquals(List.of("MIGRATE_OUT", "MIGRATE_IN"), typeOptions.stream().map(o -> o.get("value")).toList());
    }

    // ============ 辅助 ============

    private Map<String, Object> without(String key) {
        Map<String, Object> copy = body();
        copy.remove(key);
        return copy;
    }

    private Map<String, Object> singleMappingPatch() {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("mappingType", "MIGRATE_IN");
        patch.put("systemCode", "SYS_A");
        patch.put("fileName", "迁入映射文件");
        return patch;
    }

    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", PROJECT);
        body.put("mappingType", "MIGRATE_OUT");
        body.put("systemCode", "SYS_A");
        body.put("fileName", "迁出映射文件");
        body.put("files", List.of(Map.of("attachmentId", 101L, "fileName", "a.sql")));
        return body;
    }

    private DataMigrationCodeValueService codeValues() {
        SystemReferenceQuery refs = mock(SystemReferenceQuery.class);
        when(refs.activeParameters(any(), any())).thenReturn(List.of(
                new SystemParameterReference("MIGRATE_OUT", "迁出程序"),
                new SystemParameterReference("MIGRATE_IN", "迁入程序")));
        return new DataMigrationCodeValueService(refs);
    }

    private com.ccb.attachment.integration.AttachmentItem item(long id, String name) {
        return new com.ccb.attachment.integration.AttachmentItem(id, name, "application/octet-stream", 8L, "sql",
                "TEMP", null, null, null, USER.id(), LocalDateTime.now());
    }

    private List<Map<String, Object>> attachments() {
        return List.of(row(101L, "a.sql"), row(102L, "b.sh"));
    }

    private Map<String, Object> row(long attachmentId, String fileName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attachmentId);
        row.put("attachment_id", attachmentId);
        row.put("file_name", fileName);
        row.put("sort_order", 0);
        row.put("created_by", USER.id());
        row.put("created_at", LocalDateTime.now());
        return row;
    }

    private void assertRejected(Runnable action, String messageContains) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains(messageContains), error.getMessage());
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<Long, Map<String, Object>> mappings = new LinkedHashMap<>();
        private final List<String> audits = new ArrayList<>();
        private int restoreUpdateCount = 1;
        private int purgeUpdateCount = 1;
        private String foreignSystem = "SYS_A";

        private void putMapping(long id, long projectId, long ownerId, boolean deleted) {
            Map<String, Object> row = prepareRow(id, projectId, ownerId);
            row.put("deleted", deleted ? 1 : 0);
            mappings.put(id, row);
        }

        private Map<String, Object> prepareRow(long id, long projectId, long ownerId) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("project_id", projectId);
            row.put("project_name", "示例项目");
            row.put("mapping_type", "MIGRATE_OUT");
            row.put("system_code", foreignSystem);
            row.put("doc_code", "MAP-" + (1000 + id));
            row.put("asset_code", "MAP-" + (1000 + id));
            row.put("doc_name", "迁出映射文件");
            row.put("asset_name", "迁出映射文件");
            row.put("system_short_name", null);
            row.put("system_name", "系统A");
            row.put("owner_id", ownerId);
            row.put("created_by", ownerId);
            row.put("updated_by", ownerId);
            row.put("created_at", LocalDateTime.now());
            row.put("updated_at", LocalDateTime.now());
            row.put("deleted_by", null);
            row.put("deleted_at", null);
            row.put("deleted", 0);
            return row;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("SELECT COUNT(*) FROM dm_component")) {
                String system = args.length >= 3 ? String.valueOf(args[2]) : foreignSystem;
                return (T) Integer.valueOf(system.equals(foreignSystem) ? 1 : 0);
            }
            if (sql.contains("COUNT(*)")) {
                if (requiredType == Long.class) return (T) Long.valueOf(0L);
                return (T) Integer.valueOf(0);
            }
            if (requiredType == Integer.class) return (T) Integer.valueOf(1);
            if (requiredType == Long.class) return (T) Long.valueOf(0L);
            return (T) Boolean.TRUE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("SELECT project_id FROM dm_mapping") && sql.contains("deleted = 1")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = mappings.get(id);
                if (row == null || !Integer.valueOf(1).equals(row.get("deleted"))) return List.of();
                return (List<T>) new ArrayList<>(List.of(row.get("project_id")));
            }
            if (sql.contains("SELECT m.attachment_id")) {
                return (List<T>) new ArrayList<>(List.of(101L, 102L));
            }
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM dm_component c") && sql.contains("AS value")) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("value", "SYS_A");
                option.put("label", "SYS_A - 系统A");
                return List.of(option);
            }
            if (sql.contains("FROM dm_mapping_doc a") || sql.contains("FROM dm_mapping_doc WHERE")) {
                long id = args.length > 1 && args[args.length - 1] instanceof Number
                        ? ((Number) args[args.length - 1]).longValue()
                        : (args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).longValue() : 50L);
                if (sql.contains("ORDER BY")) return List.of();
                Map<String, Object> row = mappings.getOrDefault(id, prepareRow(id, PROJECT, USER.id()));
                List<Map<String, Object>> result = new ArrayList<>();
                if (Integer.valueOf(0).equals(row.get("deleted"))) result.add(new LinkedHashMap<>(row));
                return result;
            }
            if (sql.contains("dm_mapping") && sql.contains("deleted = 1")) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> row : mappings.values()) {
                    if (Integer.valueOf(1).equals(row.get("deleted"))) result.add(new LinkedHashMap<>(row));
                }
                return result;
            }
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dm_mapping")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = prepareRow(id, ((Number) args[2]).longValue(), ((Number) args[7]).longValue());
                row.put("system_code", args[3]);
                row.put("doc_code", args[4]);
                row.put("doc_name", args[5]);
                row.put("mapping_type", args[6]);
                mappings.put(id, row);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_mapping SET deleted = 1")) {
                long id = ((Number) args[1]).longValue();
                Map<String, Object> row = mappings.get(id);
                if (row == null) return 0;
                row.put("deleted", 1);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_mapping SET deleted = 0")) {
                if (restoreUpdateCount == 0) return 0;
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = mappings.get(id);
                if (row == null) return 0;
                row.put("deleted", 0);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_mapping_doc SET doc_name")) {
                long id = ((Number) args[4]).longValue();
                Map<String, Object> row = mappings.get(id);
                if (row == null) return 0;
                row.put("doc_name", args[0]);
                row.put("asset_name", args[0]);
                row.put("mapping_type", args[1]);
                row.put("system_code", args[2]);
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_mapping")) {
                if (purgeUpdateCount == 0) return 0;
                long id = ((Number) args[0]).longValue();
                mappings.remove(id);
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                audits.add(String.valueOf(args[3]));
                return 1;
            }
            return 1;
        }
    }
}

package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 投产及演练专属服务行为测试（对标 TopicService/PlanService 桩模式）：必填校验、颗粒度与资料类型参数联动、
 * 组件级单选/项目级禁选系统、单文件绑定与编辑替换、权限审计、逻辑删除及回收站冲突。
 */
class ReleaseDrillServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);
    private static final AuthUser OTHER = new AuthUser(88L, 1L, "other", "", "他人", 11L, true);
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 11L, true);
    private static final long PROJECT = 10L;

    private StubReleaseDrillMapper jdbc;
    private ContentAttachmentService attachments;
    private ContentFileAssetService fileAssets;
    private DataMigrationPermissionService permissions;
    private ReleaseDrillService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubReleaseDrillMapper();
        attachments = mock(ContentAttachmentService.class);
        fileAssets = mock(ContentFileAssetService.class);
        permissions = mock(DataMigrationPermissionService.class);
        service = new ReleaseDrillService(new ReleaseDrillRepository(jdbc), attachments, fileAssets, permissions, null,
                new ContentDocCodeGenerator(), codeValues());

        when(permissions.requireAccessible(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(0, Long.class));
        when(permissions.requireProject(any(), any())).thenAnswer(invocation -> {
            Long pid = invocation.getArgument(0, Long.class);
            if (pid == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "必须选择项目");
            return pid;
        });
        when(permissions.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        when(fileAssets.resolveAttachment(anyLong(), any())).thenReturn(new com.ccb.attachment.integration.AttachmentItem(
                101L, "release.pdf", "application/pdf", 8L, "pdf", "TEMP", null, null, null, USER.id(), LocalDateTime.now()));
    }

    @Test
    void createRejectsMissingRequiredFields() {
        assertRejected(() -> service.create(without("projectId"), USER), "projectId 不能为空");
        assertRejected(() -> service.create(without("granularity"), USER), "投产及演练颗粒度不能为空");
        assertRejected(() -> service.create(without("materialTypeCode"), USER), "资料类型不能为空");
        assertRejected(() -> service.create(without("materialName"), USER), "资料名称不能为空");
        assertRejected(() -> service.create(without("files"), USER), "请上传源文件");
    }

    @Test
    void componentGranularityRequiresSystemCodeAndAuditsCreate() {
        Map<String, Object> body = body();
        body.put("granularity", "COMPONENT");
        body.put("materialTypeCode", "RELEASE_PLAN");
        assertRejected(() -> service.create(body, USER), "组件级资料必须选择涉及物理子系统");

        body.put("systemCode", "SYS_A");
        Map<String, Object> created = service.create(body, USER);
        assertEquals("COMPONENT", created.get("granularity"));
        assertEquals("SYS_A", created.get("system_code"));
        assertTrue(((String) created.get("asset_code")).startsWith("DRILL-"));
        assertTrue(jdbc.audits.contains("RELEASE_DRILL_CREATE"));
    }

    @Test
    void projectLevelRejectsSystemCodeAndUnknownTypeIsRejected() {
        Map<String, Object> withSystem = body();
        withSystem.put("systemCode", "SYS_A");
        assertRejected(() -> service.create(withSystem, USER), "项目级资料不能选择涉及物理子系统");

        Map<String, Object> unknownType = body();
        unknownType.put("materialTypeCode", "UNKNOWN_TYPE");
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(unknownType, USER));
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains("无效或已停用"), error.getMessage());
    }

    @Test
    void updateKeepsAttachmentWhenNoFileAndReplacesWhenProvided() {
        jdbc.putDrill(50L, PROJECT, USER.id(), false);
        service.update(50L, Map.of("materialName", "新名称", "drillRound", "2026-09"), USER);
        verify(attachments, org.mockito.Mockito.never()).replaceAll(anyString(), anyString(), anyLong(), anyLong(), any(), any());

        when(fileAssets.resolveAttachment(201L, USER)).thenReturn(new com.ccb.attachment.integration.AttachmentItem(
                201L, "v2.pdf", "application/pdf", 8L, "pdf", "TEMP", null, null, null, USER.id(), LocalDateTime.now()));
        Map<String, Object> update = body();
        update.put("attachmentId", 201L);
        service.update(50L, update, USER);
        verify(attachments).replaceAll(anyString(), anyString(), anyLong(), anyLong(), any(), any());
        assertTrue(jdbc.audits.contains("RELEASE_DRILL_UPDATE"));
    }

    @Test
    void deleteRejectsNonOwnerAndAudits() {
        DataMigrationPermissionService rejecting = mock(DataMigrationPermissionService.class);
        when(rejecting.requireAccessible(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(0, Long.class));
        when(rejecting.requireStoredProject(any(), any())).thenAnswer(invocation -> ((Number) invocation.getArgument(0)).longValue());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无该资料操作权限")).when(rejecting).requireWrite(any(), anyLong());
        ReleaseDrillService rejectingService = new ReleaseDrillService(new ReleaseDrillRepository(jdbc), attachments, fileAssets, rejecting, null,
                new ContentDocCodeGenerator(), codeValues());

        jdbc.putDrill(50L, PROJECT, OTHER.id(), false);
        assertThrows(BusinessException.class, () -> rejectingService.delete(List.of(50L), USER));
        assertFalse(jdbc.audits.contains("RELEASE_DRILL_DELETE"));

        jdbc.putDrill(51L, PROJECT, USER.id(), false);
        service.delete(List.of(51L), USER);
        assertTrue(jdbc.audits.contains("RELEASE_DRILL_DELETE"));
    }

    @Test
    void listRequiresProjectAndValidatesType() {
        BusinessException projectError = assertThrows(BusinessException.class,
                () -> service.list(null, null, null, null, null, 1, 20, USER));
        assertTrue(projectError.getMessage().contains("项目"));

        BusinessException typeError = assertThrows(BusinessException.class,
                () -> service.list(PROJECT, "PROJECT", "UNKNOWN", null, "关键字", 1, 20, USER));
        assertTrue(typeError.getMessage().contains("无效或已停用"), typeError.getMessage());
    }

    @Test
    void restoreConflictWhenStateChangedConcurrently() {
        jdbc.putDrill(50L, PROJECT, USER.id(), true);
        jdbc.restoreUpdateCount = 0;
        BusinessException error = assertThrows(BusinessException.class, () -> service.restore(List.of(50L), ADMIN));
        assertEquals(ErrorCode.CONFLICT, error.code());
        assertFalse(jdbc.audits.contains("RELEASE_DRILL_RESTORE"));
    }

    @Test
    void recycleBinOperationsAreAdminOnlyAndAudit() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "需要管理员")).when(permissions).requireAdmin(USER);
        assertThrows(BusinessException.class, () -> service.restore(List.of(50L), USER));
        assertThrows(BusinessException.class, () -> service.purge(List.of(50L), USER));

        jdbc.putDrill(50L, PROJECT, USER.id(), true);
        jdbc.putDrill(51L, PROJECT, USER.id(), true);
        service.restore(List.of(50L), ADMIN);
        assertTrue(jdbc.audits.contains("RELEASE_DRILL_RESTORE"));
        service.purge(List.of(51L), ADMIN);
        assertTrue(jdbc.audits.contains("RELEASE_DRILL_PURGE"));
    }

    @Test
    void typeOptionsComeFromActiveParameters() {
        List<Map<String, Object>> options = service.getTypeOptions(USER);
        assertEquals(List.of("RELEASE_PLAN"), options.stream().map(o -> o.get("value")).toList());
    }

    // ============ 辅助 ============

    private Map<String, Object> without(String key) {
        Map<String, Object> copy = body();
        copy.remove(key);
        return copy;
    }

    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", PROJECT);
        body.put("granularity", "PROJECT");
        body.put("materialTypeCode", "RELEASE_PLAN");
        body.put("materialName", "投产方案");
        body.put("drillRound", "2026-09-05");
        body.put("files", List.of(Map.of("attachmentId", 101L, "fileName", "release.pdf")));
        return body;
    }

    private DataMigrationCodeValueService codeValues() {
        SystemReferenceQuery refs = mock(SystemReferenceQuery.class);
        when(refs.activeParameters(any(), eq("DM_RELEASE_DRILL_GRANULARITY")))
                .thenReturn(List.of(new SystemParameterReference("PROJECT", "项目级"),
                        new SystemParameterReference("COMPONENT", "组件级")));
        when(refs.activeParameters(any(), eq("DM_RELEASE_DRILL_TYPE")))
                .thenReturn(List.of(new SystemParameterReference("RELEASE_PLAN", "投产方案")));
        return new DataMigrationCodeValueService(refs);
    }

    private Map<String, Object> row() {
        return jdbc.prepareRow(50L, PROJECT, USER.id());
    }

    private void assertRejected(Runnable action, String messageContains) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains(messageContains), error.getMessage());
    }

    private static final class StubReleaseDrillMapper implements ReleaseDrillMapper {
        private final Map<Long, Map<String, Object>> drills = new LinkedHashMap<>();
        private final List<String> audits = new ArrayList<>();
        private int restoreUpdateCount = 1;

        private void putDrill(long id, long projectId, long ownerId, boolean deleted) {
            Map<String, Object> row = prepareRow(id, projectId, ownerId);
            row.put("deleted", deleted ? 1 : 0);
            drills.put(id, row);
        }

        private Map<String, Object> prepareRow(long id, long projectId, long ownerId) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("project_id", projectId);
            row.put("project_name", "示例项目");
            row.put("granularity", "PROJECT");
            row.put("material_type_code", "RELEASE_PLAN");
            row.put("doc_code", "DRILL-" + (1000 + id));
            row.put("asset_code", "DRILL-" + (1000 + id));
            row.put("doc_name", "投产方案");
            row.put("asset_name", "投产方案");
            row.put("drill_round", "2026-09");
            row.put("system_code", "");
            row.put("system_short_name", null);
            row.put("system_name", null);
            row.put("attachment_id", 101L);
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

        @Override public Long count(long tenantId, long projectId, String granularity, String materialTypeCode, String systemCode, String keyword) { return 0L; }
        @Override public List<Map<String, Object>> page(long tenantId, long projectId, String granularity, String materialTypeCode, String systemCode, String keyword, int limit, long offset) { return List.of(); }
        @Override public List<Map<String, Object>> find(long tenantId, long id) { return active(id); }
        @Override public int insert(Map<String, Object> p) {
            long id = ((Number) p.get("id")).longValue();
            Map<String, Object> row = prepareRow(id, ((Number) p.get("projectId")).longValue(), ((Number) p.get("ownerId")).longValue());
            row.put("granularity", p.get("granularity")); row.put("material_type_code", p.get("materialTypeCode"));
            row.put("doc_code", p.get("docCode")); row.put("asset_code", p.get("docCode"));
            row.put("doc_name", p.get("docName")); row.put("asset_name", p.get("docName"));
            row.put("drill_round", p.get("drillRound")); row.put("system_code", p.get("systemCode")); drills.put(id, row); return 1;
        }
        @Override public int update(Map<String, Object> p) {
            Map<String, Object> row = drills.get(((Number) p.get("id")).longValue()); if (row == null || Integer.valueOf(1).equals(row.get("deleted"))) return 0;
            row.put("doc_name", p.get("docName")); row.put("asset_name", p.get("docName")); row.put("granularity", p.get("granularity"));
            row.put("material_type_code", p.get("materialTypeCode")); row.put("drill_round", p.get("drillRound")); row.put("system_code", p.get("systemCode")); return 1;
        }
        @Override public int softDelete(long tenantId, long id, long deletedBy) { Map<String, Object> row = drills.get(id); if (row == null || Integer.valueOf(1).equals(row.get("deleted"))) return 0; row.put("deleted", 1); row.put("deleted_by", deletedBy); return 1; }
        @Override public List<Long> mainAttachmentIds(long tenantId, long id) { return List.of(101L); }
        @Override public Long recycleCount(long tenantId, long projectId, String keyword) { return drills.values().stream().filter(row -> Integer.valueOf(1).equals(row.get("deleted"))).count(); }
        @Override public List<Map<String, Object>> recyclePage(long tenantId, long projectId, String keyword, int limit) { return drills.values().stream().filter(row -> Integer.valueOf(1).equals(row.get("deleted"))).<Map<String, Object>>map(LinkedHashMap::new).toList(); }
        @Override public List<Map<String, Object>> findDeleted(long tenantId, long id) { Map<String, Object> row = drills.get(id); return row != null && Integer.valueOf(1).equals(row.get("deleted")) ? List.of(new LinkedHashMap<>(row)) : List.of(); }
        @Override public int restore(long tenantId, long id) { if (restoreUpdateCount == 0) return 0; Map<String, Object> row = drills.get(id); if (row == null || !Integer.valueOf(1).equals(row.get("deleted"))) return 0; row.put("deleted", 0); return 1; }
        @Override public int purge(long tenantId, long id) { return drills.remove(id) == null ? 0 : 1; }
        @Override public Integer enabledComponentCount(long tenantId, long projectId, String systemCode) { return 1; }
        @Override public List<Map<String, Object>> findRaw(long tenantId, long id) { return active(id); }
        @Override public List<Long> deletedProjectIds(long tenantId, long id) { Map<String, Object> row = drills.get(id); return row != null && Integer.valueOf(1).equals(row.get("deleted")) ? List.of(((Number) row.get("project_id")).longValue()) : List.of(); }
        @Override public int insertAudit(long tenantId, long actorId, long projectId, String operation, long entityId) { audits.add(operation); return 1; }

        private List<Map<String, Object>> active(long id) {
            Map<String, Object> row = drills.getOrDefault(id, prepareRow(id, PROJECT, USER.id()));
            return Integer.valueOf(0).equals(row.get("deleted")) ? List.of(new LinkedHashMap<>(row)) : List.of();
        }
    }
}

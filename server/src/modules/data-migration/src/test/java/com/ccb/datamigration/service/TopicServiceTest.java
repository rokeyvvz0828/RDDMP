package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import org.junit.jupiter.api.Test;
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

/**
 * 专题材料专属服务行为测试（对标 PlanService/ReportService 桩模式）：必填校验、颗粒度与参数类型联动、
 * 系统级/项目级系统关系、多附件、租户/项目/实体授权、逻辑删除与恢复状态冲突、审计与 doc_code 生成。
 */
class TopicServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);
    private static final AuthUser OTHER_USER = new AuthUser(88L, 1L, "other", "", "他人", 11L, true);
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 11L, true);
    private static final long PROJECT = 10L;

    @Test
    void createRejectsMissingRequiredFields() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        StubAttachmentGateway attachments = new StubAttachmentGateway(USER);
        TopicService service = service(jdbc, attachments, params());

        assertRejected(() -> service.create(without("topicName"), USER), "专题名称不能为空");
        assertRejected(() -> service.create(without("topicSummary"), USER), "专题简述不能为空");
        assertRejected(() -> service.create(without("granularity"), USER), "专题颗粒度不能为空");
        assertRejected(() -> service.create(without("topicTypeCode"), USER), "专题类型不能为空");
        assertRejected(() -> service.create(without("files"), USER), "至少上传一个源文件");
    }

    @Test
    void systemGranularityRequiresSystemCodesAndProjectLevelRejectsThem() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        TopicService service = service(jdbc, new StubAttachmentGateway(USER), params());
        Map<String, Object> systemBody = body();
        systemBody.put("granularity", "SYSTEM");
        systemBody.put("topicTypeCode", "INTERNAL_ACCOUNT_MIGRATION");

        assertRejected(() -> service.create(systemBody, USER), "必须至少选择一个涉及系统");

        systemBody.put("systemCodes", List.of("SYS_A"));
        Map<String, Object> created = service.create(systemBody, USER);
        assertEquals("SYSTEM", created.get("granularity"));
        assertTrue(jdbc.systemRelations.get(created.get("id")).contains("SYS_A"));
        assertTrue(jdbc.audits.contains("TOPIC_CREATE"));

        Map<String, Object> projectWithSystems = body();
        projectWithSystems.put("systemCodes", List.of("SYS_A"));
        assertRejected(() -> service.create(projectWithSystems, USER), "项目级专题不能关联系统");
    }

    @Test
    void unknownOrDisabledTypeCodeIsRejected() {
        TopicService service = service(new StubJdbcTemplate(), new StubAttachmentGateway(USER), params());

        Map<String, Object> unknown = body();
        unknown.put("topicTypeCode", "UNKNOWN_TYPE");
        assertRejected(() -> service.create(unknown, USER), "专题类型无效或已停用");

        // 系统级使用项目级类型：类别不匹配拒绝。
        Map<String, Object> crossCategory = body();
        crossCategory.put("granularity", "SYSTEM");
        crossCategory.put("topicTypeCode", "ASSET_INVENTORY");
        crossCategory.put("systemCodes", List.of("SYS_A"));
        assertRejected(() -> service.create(crossCategory, USER), "专题类型无效或已停用");
    }

    @Test
    void systemCodeNotBelongingToCurrentProjectIsRejected() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        // 当前项目仅存在 SYS_A；SYS_X 属于其他项目。
        jdbc.dmComponentProjects.put("SYS_A", 10L);
        jdbc.dmComponentProjects.put("SYS_X", 99L);
        TopicService service = service(jdbc, new StubAttachmentGateway(USER), params());

        Map<String, Object> body = body();
        body.put("granularity", "SYSTEM");
        body.put("topicTypeCode", "INTERNAL_ACCOUNT_MIGRATION");
        body.put("systemCodes", List.of("SYS_X"));
        assertRejected(() -> service.create(body, USER), "涉及系统不存在或不属于当前项目");

        body.put("systemCodes", List.of("SYS_A"));
        service.create(body, USER);
    }

    @Test
    void createGeneratesTopicDocCodeAndBindsAttachments() {
        List<String> events = new ArrayList<>(List.of());
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        StubAttachmentGateway attachments = new StubAttachmentGateway(USER);
        TopicService service = service(jdbc, attachments, params());

        Map<String, Object> created = service.create(body(), USER);
        long id = ((Number) created.get("id")).longValue();
        assertTrue(created.get("asset_code").toString().matches("TOPIC-[0-9a-f]{32}"));
        assertEquals(List.of(101L), attachments.boundIds);
        assertTrue(jdbc.audits.contains("TOPIC_CREATE"));
        assertTrue(jdbc.systemRelations.getOrDefault(id, List.of()).isEmpty());
    }

    @Test
    void updateRejectsNonOwnerAndPreservesProjectOwnership() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.putTopic(50L, PROJECT, USER.id(), false);
        TopicService service = service(jdbc, new StubAttachmentGateway(USER), params());

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("topicName", "改名");
        BusinessException denied = assertThrows(BusinessException.class, () -> service.update(50L, update, OTHER_USER));
        assertEquals(ErrorCode.FORBIDDEN, denied.code());

        Map<String, Object> updated = service.update(50L, update, USER);
        assertEquals("改名", updated.get("asset_name"));
        assertEquals(PROJECT, ((Number) updated.get("project_id")).longValue());
        assertTrue(jdbc.audits.contains("TOPIC_UPDATE"));
    }

    @Test
    void updateSystemTopicToProjectClearsSystemRelations() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.putTopic(60L, PROJECT, USER.id(), false);
        jdbc.topics.get(60L).put("granularity", "SYSTEM");
        jdbc.topics.get(60L).put("topic_type_code", "INTERNAL_ACCOUNT_MIGRATION");
        jdbc.systemRelations.put(60L, new ArrayList<>(List.of("SYS_A")));
        TopicService service = service(jdbc, new StubAttachmentGateway(USER), params());

        Map<String, Object> update = body();
        update.put("granularity", "PROJECT");
        update.put("topicTypeCode", "ASSET_INVENTORY");
        update.put("systemCodes", List.of());

        Map<String, Object> updated = service.update(60L, update, USER);
        assertEquals("PROJECT", updated.get("granularity"));
        assertTrue(jdbc.systemRelations.getOrDefault(60L, List.of()).isEmpty());
    }

    @Test
    void deleteRejectsNonOwnerAndAuditsOnce() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.putTopic(50L, PROJECT, OTHER_USER.id(), false);
        TopicService service = service(jdbc, new StubAttachmentGateway(USER), params());

        assertThrows(BusinessException.class, () -> service.delete(List.of(50L), USER));
        assertFalse(jdbc.audits.contains("TOPIC_DELETE"));

        jdbc.putTopic(51L, PROJECT, USER.id(), false);
        service.delete(List.of(51L), USER);
        assertTrue(jdbc.audits.contains("TOPIC_DELETE"));
    }

    @Test
    void restoreConflictWhenStateChangedConcurrently() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.putTopic(50L, PROJECT, USER.id(), true);
        jdbc.restoreUpdateCount = 0;
        TopicService service = service(jdbc, new StubAttachmentGateway(ADMIN), params());

        BusinessException error = assertThrows(BusinessException.class, () -> service.restore(List.of(50L), ADMIN));
        assertEquals(ErrorCode.CONFLICT, error.code());
        assertFalse(jdbc.audits.contains("TOPIC_RESTORE"));
    }

    @Test
    void restoreAndPurgeAreAdminOnlyAndCleanRelations() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.putTopic(50L, PROJECT, USER.id(), true);
        jdbc.systemRelations.put(50L, new ArrayList<>(List.of("SYS_A")));
        TopicService service = service(jdbc, new StubAttachmentGateway(ADMIN), params());

        BusinessException denied = assertThrows(BusinessException.class, () -> service.restore(List.of(50L), USER));
        assertEquals(ErrorCode.FORBIDDEN, denied.code());

        service.restore(List.of(50L), ADMIN);
        assertTrue(jdbc.audits.contains("TOPIC_RESTORE"));

        jdbc.putTopic(51L, PROJECT, USER.id(), true);
        jdbc.systemRelations.put(51L, new ArrayList<>(List.of("SYS_B")));
        service.purge(List.of(51L), ADMIN);
        assertTrue(jdbc.audits.contains("TOPIC_PURGE"));
        assertTrue(jdbc.systemRelations.getOrDefault(51L, List.of()).isEmpty());
    }

    @Test
    void typeOptionsComeFromActiveParametersByGranularity() {
        TopicService service = service(new StubJdbcTemplate(), new StubAttachmentGateway(USER), params());

        List<Map<String, Object>> projectTypes = service.getTypeOptions("PROJECT", USER);
        assertEquals(List.of("ASSET_INVENTORY"), projectTypes.stream().map(o -> o.get("value")).toList());

        List<Map<String, Object>> systemTypes = service.getTypeOptions("SYSTEM", USER);
        assertEquals(List.of("INTERNAL_ACCOUNT_MIGRATION"), systemTypes.stream().map(o -> o.get("value")).toList());
    }

    @Test
    void listRequiresProjectAndAppliesTypeValidation() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        TopicService service = service(jdbc, new StubAttachmentGateway(USER), params());

        assertThrows(BusinessException.class, () -> service.list(null, null, null, null, null, 1, 20, USER));
        assertThrows(BusinessException.class, () -> service.list(PROJECT, "PROJECT", "UNKNOWN", null, null, 1, 20, USER));
        var result = service.list(PROJECT, "PROJECT", "ASSET_INVENTORY", "SYS_A", "关键字", 1, 20, USER);
        assertEquals(0, result.records().size());
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
        body.put("topicTypeCode", "ASSET_INVENTORY");
        body.put("topicName", "资产盘点专题材料");
        body.put("topicSummary", "本年度资产盘点专题材料");
        body.put("files", List.of(Map.of("attachmentId", 101L, "fileName", "inventory.pdf")));
        return body;
    }

    private void assertRejected(Runnable action, String messageContains) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains(messageContains), error.getMessage());
    }

    private SystemReferenceQuery params() {
        return new SystemReferenceQuery() {
            @Override
            public com.ccb.common.api.PageResult<com.ccb.system.capability.SystemUserReference> searchActiveUsers(
                    AuthUser actor, com.ccb.common.api.PageQuery page, String keyword) {
                return new com.ccb.common.api.PageResult<>(List.of(), 0L, 1, 20);
            }

            @Override
            public java.util.Optional<com.ccb.system.capability.SystemUserReference> findUser(AuthUser actor, long userId, boolean activeOnly) {
                return java.util.Optional.empty();
            }

            @Override
            public List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode) {
                if ("DM_TOPIC_GRANULARITY".equals(categoryCode)) {
                    return List.of(new SystemParameterReference("PROJECT", "项目级"), new SystemParameterReference("SYSTEM", "系统级"));
                }
                if ("DM_TOPIC_PROJECT_TYPE".equals(categoryCode)) {
                    return List.of(new SystemParameterReference("ASSET_INVENTORY", "资产盘点专题"));
                }
                if ("DM_TOPIC_SYSTEM_TYPE".equals(categoryCode)) {
                    return List.of(new SystemParameterReference("INTERNAL_ACCOUNT_MIGRATION", "内部账迁移专题"));
                }
                return List.of();
            }
        };
    }

    private TopicService service(StubJdbcTemplate jdbc, AttachmentGateway attachments, SystemReferenceQuery params) {
        DataMigrationPermissionService permissions = new DataMigrationPermissionService(jdbc, StubProjectAccess.allow());
        ContentAttachmentService contentAttachments = new ContentAttachmentService(jdbc, attachments);
        ContentFileAssetService fileAssets = new ContentFileAssetService(jdbc, attachments, contentAttachments, permissions);
        return new TopicService(jdbc, contentAttachments, fileAssets, permissions, null,
                new ContentDocCodeGenerator(), params, new DataMigrationCodeValueService(params));
    }

    private static AttachmentItem attachment(long id, String name) {
        return new AttachmentItem(id, name, "application/pdf", 8L, "pdf", "TEMP",
                null, null, null, 7L, LocalDateTime.of(2026, 8, 24, 9, 0));
    }

    private static final class StubAttachmentGateway implements AttachmentGateway {
        private final List<Long> boundIds = new ArrayList<>();
        private final AuthUser uploader;

        private StubAttachmentGateway(AuthUser uploader) {
            this.uploader = uploader;
        }

        @Override
        public void bind(AttachmentBindingCommand command, AuthUser operator) {
            boundIds.add(command.attachmentId());
        }

        @Override
        public AttachmentItem get(long attachmentId, AuthUser operator) {
            return attachment(attachmentId, "file-" + attachmentId + ".pdf");
        }

        @Override
        public void deleteBound(long attachmentId, String businessType, String businessKey, AuthUser operator) {
            boundIds.remove(attachmentId);
        }
    }

    /** 模拟 dm_topic / dm_topic_system / dm_content_attachment 的最小 JDBC 桩。 */
    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<Long, Map<String, Object>> topics = new LinkedHashMap<>();
        private final Map<Long, List<String>> systemRelations = new LinkedHashMap<>();
        private final Map<String, Long> dmComponentProjects = new LinkedHashMap<>(Map.of("SYS_A", 10L, "SYS_B", 10L));
        private final Map<Long, List<Long>> attachmentBindings = new LinkedHashMap<>();
        private final List<String> audits = new ArrayList<>();
        private int restoreUpdateCount = 1;
        private long nextId = 1000L;

        private void putTopic(long id, long projectId, long ownerId, boolean deleted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("project_id", projectId);
            row.put("project_name", "示例项目");
            row.put("granularity", "PROJECT");
            row.put("topic_type_code", "ASSET_INVENTORY");
            row.put("doc_code", "TOPIC-" + id);
            row.put("doc_name", "资产盘点专题材料");
            row.put("topic_summary", "简述");
            row.put("attachment_id", null);
            row.put("system_names", null);
            row.put("system_codes", null);
            row.put("attachment_count", 0L);
            row.put("owner_id", ownerId);
            row.put("created_by", ownerId);
            row.put("updated_by", ownerId);
            row.put("deleted", deleted ? 1 : 0);
            topics.put(id, row);
            systemRelations.computeIfAbsent(id, k -> new ArrayList<>());
        }

        private Map<String, Object> rowFor(Map<String, Object> base) {
            Map<String, Object> out = new LinkedHashMap<>(base);
            if (out.containsKey("doc_code") && !out.containsKey("asset_code")) out.put("asset_code", out.get("doc_code"));
            if (out.containsKey("doc_name") && !out.containsKey("asset_name")) out.put("asset_name", out.get("doc_name"));
            return out;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("FROM sys_user_role")) {
                long userId = ((Number) args[0]).longValue();
                return (T) Integer.valueOf(userId == 1L ? 1 : 0);
            }
            if (sql.contains("SELECT COUNT(*) FROM dm_component")) {
                long projectId = ((Number) args[1]).longValue();
                String systemCode = String.valueOf(args[2]);
                return (T) Integer.valueOf(dmComponentProjects.getOrDefault(systemCode, -1L) == projectId ? 1 : 0);
            }
            if (sql.contains("COUNT(*)")) return (T) Long.valueOf(topics.values().stream().filter(r -> ((Number) r.get("deleted")).intValue() == 0).count());
            return (T) Integer.valueOf(1);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("SELECT project_id FROM dm_topic") && sql.contains("deleted = 1")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = topics.get(id);
                if (row == null || ((Number) row.get("deleted")).intValue() != 1) return List.of();
                return (List<T>) new ArrayList<>(List.of(row.get("project_id")));
            }
            if (sql.contains("SELECT system_code FROM dm_topic_system")) {
                long topicId = ((Number) args[1]).longValue();
                return (List<T>) new ArrayList<>(systemRelations.getOrDefault(topicId, List.of()));
            }
            if (sql.contains("SELECT attachment_id FROM dm_content_attachment m JOIN dm_topic a")) {
                long topicId = ((Number) args[1]).longValue();
                List<Long> bound = attachmentBindings.getOrDefault(topicId, List.of());
                return (List<T>) new ArrayList<>(bound.isEmpty() ? List.of(101L) : bound);
            }
            if (sql.contains("SELECT attachment_id FROM dm_content_attachment") && sql.contains("deleted = 0")) {
                return (List<T>) new ArrayList<>(List.of());
            }
            if (sql.contains("SELECT attachment_id FROM dm_content_attachment") && sql.contains("deleted = 1")) {
                return (List<T>) new ArrayList<>(List.of());
            }
            if (sql.contains("SELECT attachment_id FROM dm_content_attachment")) {
                return (List<T>) new ArrayList<>(List.of(101L));
            }
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, tenant_id, project_id, granularity, topic_type_code, doc_code, doc_name, topic_summary, owner_id ")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = topics.get(id);
                if (row == null || ((Number) row.get("deleted")).intValue() == 1) return List.of();
                return List.of(rowFor(row));
            }
            if (sql.contains("FROM dm_topic a") && sql.contains("a.id = ?") && sql.contains("deleted = 0")) {
                long id = ((Number) args[1]).longValue();
                Map<String, Object> row = topics.get(id);
                if (row == null || ((Number) row.get("deleted")).intValue() == 1) return List.of();
                return List.of(rowFor(row));
            }
            if (sql.contains("FROM dm_topic a") && !sql.contains("AND a.id = ?")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> row : topics.values()) {
                    if (((Number) row.get("deleted")).intValue() == 0) rows.add(rowFor(row));
                }
                return rows;
            }
            if (sql.contains("FROM dm_topic a") && sql.contains("deleted = 1")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> row : topics.values()) {
                    if (((Number) row.get("deleted")).intValue() == 1) rows.add(rowFor(row));
                }
                return rows;
            }
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dm_topic ")) {
                long id = ((Number) args[0]).longValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("project_id", ((Number) args[2]).longValue());
                row.put("project_name", "示例项目");
                row.put("granularity", args[5]);
                row.put("topic_type_code", args[6]);
                row.put("doc_code", args[3]);
                row.put("doc_name", args[4]);
                row.put("topic_summary", args[7]);
                row.put("attachment_id", null);
                row.put("system_names", null);
                row.put("system_codes", null);
                row.put("attachment_count", 0L);
                row.put("owner_id", ((Number) args[8]).longValue());
                row.put("created_by", ((Number) args[9]).longValue());
                row.put("updated_by", ((Number) args[10]).longValue());
                row.put("deleted", 0);
                topics.put(id, row);
                systemRelations.computeIfAbsent(id, k -> new ArrayList<>());
                return 1;
            }
            if (sql.startsWith("UPDATE dm_topic SET doc_name ")) {
                long id = ((Number) args[5]).longValue();
                Map<String, Object> row = topics.get(id);
                if (row == null) return 0;
                row.put("doc_name", args[0]);
                row.put("granularity", args[1]);
                row.put("topic_type_code", args[2]);
                row.put("topic_summary", args[3]);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_topic SET deleted = 1")) {
                long id = ((Number) args[1]).longValue();
                Map<String, Object> row = topics.get(id);
                if (row == null) return 0;
                row.put("deleted", 1);
                return 1;
            }
            if (sql.startsWith("UPDATE dm_topic SET deleted = 0")) {
                long id = ((Number) args[1]).longValue();
                if (restoreUpdateCount == 0) return 0;
                Map<String, Object> row = topics.get(id);
                if (row == null) return 0;
                row.put("deleted", 0);
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_topic_system")) {
                long topicId = ((Number) args[1]).longValue();
                systemRelations.computeIfAbsent(topicId, k -> new ArrayList<>()).clear();
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_topic_system ")) {
                long topicId = ((Number) args[2]).longValue();
                systemRelations.computeIfAbsent(topicId, k -> new ArrayList<>()).add(String.valueOf(args[4]));
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_topic")) return 1;
            if (sql.startsWith("INSERT INTO dm_content_attachment")) return 1;
            if (sql.startsWith("UPDATE dm_content_attachment")) return 1;
            if (sql.startsWith("DELETE FROM dm_content_attachment")) return 1;
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                audits.add(String.valueOf(args[3]));
                return 1;
            }
            throw new AssertionError("Unexpected update: " + sql);
        }
    }
}

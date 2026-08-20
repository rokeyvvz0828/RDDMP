package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowDefinitionReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionLifecycleTest {
    private static final AuthUser ADMIN = new AuthUser(7L, 1L, "admin", "", "管理员", 1L, true);

    @Test
    void deletesDraftButRejectsPublishedDefinition() {
        LifecycleJdbcTemplate draftJdbc = new LifecycleJdbcTemplate("DRAFT");
        service(draftJdbc).deleteDefinition(31L, ADMIN);
        assertEquals(true, draftJdbc.deleted);
        assertEquals("DEFINITION_DELETED", draftJdbc.events.get(0).eventType());

        LifecycleJdbcTemplate publishedJdbc = new LifecycleJdbcTemplate("PUBLISHED");
        BusinessException error = assertThrows(BusinessException.class,
                () -> service(publishedJdbc).deleteDefinition(31L, ADMIN));
        assertEquals(ErrorCode.CONFLICT, error.code());
        assertEquals(false, publishedJdbc.deleted);
    }

    @Test
    void rejectsDeleteWhenPublishedHistoryOrInstanceHistoryExists() {
        LifecycleJdbcTemplate publishedHistory = new LifecycleJdbcTemplate("DRAFT", 1, 0);
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> service(publishedHistory).deleteDefinition(31L, ADMIN)).code());

        LifecycleJdbcTemplate instanceHistory = new LifecycleJdbcTemplate("DRAFT", 0, 1);
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> service(instanceHistory).deleteDefinition(31L, ADMIN)).code());
    }

    @Test
    void activeBusinessReferenceBlocksDeleteAndArchive() {
        WorkflowService draft = service(new LifecycleJdbcTemplate("DRAFT"));
        draft.setDefinitionReferenceProviders(List.of((tenantId, definitionId) -> List.of(
                new WorkflowDefinitionReference("release", "release_scene", "REGULAR", "常规版本"))));
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> draft.deleteDefinition(31L, ADMIN)).code());

        WorkflowService published = service(new LifecycleJdbcTemplate("PUBLISHED"));
        published.setDefinitionReferenceProviders(List.of((tenantId, definitionId) -> List.of(
                new WorkflowDefinitionReference("release", "release_scene", "REGULAR", "常规版本"))));
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> published.archiveDefinition(31L, "停用", ADMIN)).code());
    }

    @Test
    void archivesAndRestoresWithoutChangingBoundVersionOrDeployment() {
        LifecycleJdbcTemplate jdbc = new LifecycleJdbcTemplate("PUBLISHED");
        WorkflowService service = service(jdbc);

        service.archiveDefinition(31L, "停用旧流程", ADMIN);
        assertEquals("ARCHIVED", jdbc.status);
        assertEquals(4, jdbc.currentVersion);
        assertEquals("deployment-4", jdbc.deploymentId);
        assertEquals(new Event("DEFINITION_ARCHIVED", 4, "停用旧流程"), jdbc.events.get(0));

        service.restoreDefinition(31L, "恢复使用", ADMIN);
        assertEquals("PUBLISHED", jdbc.status);
        assertEquals(4, jdbc.currentVersion);
        assertEquals("deployment-4", jdbc.deploymentId);
        assertEquals(new Event("DEFINITION_RESTORED", 4, "恢复使用"), jdbc.events.get(1));
    }

    @Test
    void validatesLifecycleStateReasonAndTenant() {
        WorkflowService draft = service(new LifecycleJdbcTemplate("DRAFT"));
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> draft.archiveDefinition(31L, "归档", ADMIN)).code());

        WorkflowService published = service(new LifecycleJdbcTemplate("PUBLISHED"));
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> published.restoreDefinition(31L, "恢复", ADMIN)).code());
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> published.archiveDefinition(31L, "  ", ADMIN)).code());

        AuthUser anotherTenant = new AuthUser(7L, 2L, "admin", "", "管理员", 1L, true);
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> published.definitionVersions(31L, anotherTenant)).code());
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> published.publish(31L, ADMIN)).code());
    }

    @Test
    void returnsVersionSnapshotsAndDefinitionOnlyEvents() {
        LifecycleJdbcTemplate jdbc = new LifecycleJdbcTemplate("ARCHIVED");
        WorkflowService service = service(jdbc);

        List<Map<String, Object>> versions = service.definitionVersions(31L, ADMIN);
        Map<String, Object> version = service.definitionVersion(31L, 4, ADMIN);
        List<Map<String, Object>> events = service.definitionEvents(31L, ADMIN);

        assertEquals(List.of(4, 3), versions.stream().map(row -> row.get("version_no")).toList());
        assertEquals("{\"schemaVersion\":2}", version.get("definition_json"));
        assertEquals("DEFINITION_ARCHIVED", events.get(0).get("event_type"));
        assertEquals(true, jdbc.lastEventQuery.contains("a.instance_id IS NULL"));
    }

    private WorkflowService service(LifecycleJdbcTemplate jdbc) {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowService service = new WorkflowService(jdbc, mapper, null, null, null);
        service.setDefinitionAuditService(new WorkflowAuditService(jdbc, mapper) {
            @Override
            public void record(AuthUser operator, String eventType, Long definitionId, Integer versionNo,
                               Long instanceId, Long taskId, String reason, Map<String, Object> payload) {
                jdbc.events.add(new Event(eventType, versionNo, reason));
            }
        });
        return service;
    }

    private record Event(String eventType, int versionNo, String reason) {}

    private static final class LifecycleJdbcTemplate extends JdbcTemplate {
        private String status;
        private final int currentVersion = 4;
        private final String deploymentId = "deployment-4";
        private boolean deleted;
        private final List<Event> events = new ArrayList<>();
        private String lastEventQuery = "";
        private final int publishedVersionCount;
        private final int instanceCount;

        private LifecycleJdbcTemplate(String status) {
            this(status, 0, 0);
        }

        private LifecycleJdbcTemplate(String status, int publishedVersionCount, int instanceCount) {
            this.status = status;
            this.publishedVersionCount = publishedVersionCount;
            this.instanceCount = instanceCount;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            Integer value = sql.contains("FROM wf_version") ? publishedVersionCount : instanceCount;
            return requiredType.cast(value);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            long tenantId = args.length > 1 && args[1] instanceof Number number ? number.longValue() : 1L;
            if (tenantId != 1L || deleted) return List.of();
            if (sql.startsWith("SELECT id, code, name, status")) return List.of(definition());
            if (sql.startsWith("SELECT version_no, status, model_schema_version, created_at, CAST")) {
                if (((Number) args[2]).intValue() != 4) return List.of();
                Map<String, Object> row = version(4, "PUBLISHED");
                row.put("definition_json", "{\"schemaVersion\":2}");
                return List.of(row);
            }
            if (sql.startsWith("SELECT version_no, status, model_schema_version, created_at")) {
                return List.of(version(4, "PUBLISHED"), version(3, "PUBLISHED"));
            }
            if (sql.contains("FROM wf_audit_event")) {
                lastEventQuery = sql;
                return List.of(Map.of(
                        "id", 99L,
                        "event_type", "DEFINITION_ARCHIVED",
                        "version_no", 4,
                        "operator_id", 7L,
                        "operator_name", "管理员",
                        "reason", "停用旧流程",
                        "payload_json", "{}",
                        "created_at", LocalDateTime.of(2026, 8, 17, 10, 0)
                ));
            }
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE wf_definition SET deleted = 1")) {
                if (!"DRAFT".equals(status) || deleted) return 0;
                deleted = true;
                return 1;
            }
            if (sql.startsWith("UPDATE wf_definition SET status = 'ARCHIVED'")) {
                if (!"PUBLISHED".equals(status) || deleted) return 0;
                status = "ARCHIVED";
                return 1;
            }
            if (sql.startsWith("UPDATE wf_definition SET status = 'PUBLISHED'")) {
                if (!"ARCHIVED".equals(status) || deleted) return 0;
                status = "PUBLISHED";
                return 1;
            }
            return 1;
        }

        private Map<String, Object> definition() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 31L);
            row.put("code", "release_review");
            row.put("name", "版本审批");
            row.put("status", status);
            row.put("current_version", currentVersion);
            row.put("model_schema_version", 2);
            row.put("deployment_id", deploymentId);
            return row;
        }

        private Map<String, Object> version(int versionNo, String versionStatus) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version_no", versionNo);
            row.put("status", versionStatus);
            row.put("model_schema_version", 2);
            row.put("created_at", LocalDateTime.of(2026, 8, versionNo, 10, 0));
            return row;
        }
    }
}

package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssueServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "Developer", 11L, true);

    @Test
    void missingRelationKeysPreserveExistingRelations() {
        StubIssueMapper mapper = new StubIssueMapper();
        IssueService service = service(mapper);

        service.update(50L, minimalBody(), USER);

        assertEquals(0, mapper.relationDeletes);
        assertEquals(0, mapper.relationInserts);
    }

    @Test
    void explicitEmptyArraysClearAllRelationTypes() {
        StubIssueMapper mapper = new StubIssueMapper();
        IssueService service = service(mapper);
        Map<String, Object> body = minimalBody();
        body.put("relatedMeetingMinutes", List.of());
        body.put("relatedTables", List.of());
        body.put("relatedFields", List.of());

        service.update(50L, body, USER);

        assertEquals(3, mapper.relationDeletes);
        assertEquals(0, mapper.relationInserts);
    }

    @Test
    void explicitIdsReplaceOnlyThePresentRelationType() {
        StubIssueMapper mapper = new StubIssueMapper();
        IssueService service = service(mapper);
        Map<String, Object> body = minimalBody();
        body.put("relatedTables", List.of(31L));

        service.update(50L, body, USER);

        assertEquals(1, mapper.relationDeletes);
        assertEquals(1, mapper.relationInserts);
    }

    @Test
    void databaseUniquenessRaceBecomesBusinessConflict() {
        StubIssueMapper mapper = new StubIssueMapper();
        mapper.failIssueUpdateWithDuplicate = true;
        IssueService service = service(mapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(50L, minimalBody(), USER));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertEquals(0, mapper.auditWrites);
    }

    @Test
    void restorePrevalidatesWholeBatchBeforeWriting() {
        StubIssueMapper mapper = new StubIssueMapper();
        mapper.rows.get(50L).put("deleted", 1);
        mapper.putIssue(51L, "ISSUE-51", true);
        mapper.conflictingCode = "ISSUE-51";
        IssueService service = service(mapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.restore(List.of(50L, 51L), USER));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertEquals(0, mapper.restoreWrites);
        assertEquals(0, mapper.auditWrites);
    }

    @Test
    void activeIssueCannotBePurgedOrLoseRelations() {
        StubIssueMapper mapper = new StubIssueMapper();
        IssueService service = service(mapper);

        assertThrows(BusinessException.class, () -> service.purge(List.of(50L), USER));

        assertEquals(0, mapper.relationDeletes);
        assertEquals(0, mapper.purgeWrites);
        assertEquals(0, mapper.auditWrites);
    }

    @Test
    void deletedIssuePurgeDeletesRelationsThenAudits() {
        StubIssueMapper mapper = new StubIssueMapper();
        mapper.putIssue(51L, "ISSUE-51", true);
        IssueService service = service(mapper);

        service.purge(List.of(51L), USER);

        assertEquals(1, mapper.relationDeletes);
        assertEquals(1, mapper.purgeWrites);
        assertEquals(1, mapper.auditWrites);
    }

    @Test
    void relationFromAnotherProjectIsRejectedBeforeRelationMutation() {
        StubIssueMapper mapper = new StubIssueMapper();
        mapper.invalidRelationTarget = true;
        IssueService service = service(mapper);
        Map<String, Object> body = minimalBody();
        body.put("relatedTables", List.of(31L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(50L, body, USER));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertEquals(0, mapper.relationDeletes);
        assertEquals(0, mapper.relationInserts);
        assertEquals(0, mapper.auditWrites);
    }

    private IssueService service(StubIssueMapper mapper) {
        StubJdbcTemplate permissionJdbc = new StubJdbcTemplate();
        return new IssueService(new IssueRepository(mapper), new DataMigrationPermissionService(DataMigrationPermissionTestSupport.repository(permissionJdbc), StubProjectAccess.allow()), TestDataMigrationCodeValues.service());
    }

    private Map<String, Object> minimalBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", 10L);
        body.put("issueCode", "ISSUE-50");
        body.put("issueName", "Issue 50");
        return body;
    }

    private static final class StubIssueMapper implements IssueMapper {
        private final Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        private int relationDeletes;
        private int relationInserts;
        private int restoreWrites;
        private int purgeWrites;
        private int auditWrites;
        private boolean failIssueUpdateWithDuplicate;
        private boolean invalidRelationTarget;
        private String conflictingCode;

        private StubIssueMapper() {
            putIssue(50L, "ISSUE-50", false);
        }

        private void putIssue(long id, String code, boolean deleted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("project_id", 10L);
            row.put("asset_code", code);
            row.put("asset_name", "Issue " + id);
            row.put("owner_id", USER.id());
            row.put("deleted", deleted ? 1 : 0);
            rows.put(id, row);
        }

        @Override public Long count(long tenantId, long projectId, boolean deleted, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword) { return 0L; }
        @Override public List<Map<String, Object>> page(long tenantId, long projectId, boolean deleted, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, int limit, long offset) { return List.of(); }
        @Override public List<Map<String, Object>> exportRows(long tenantId, long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword) { return List.of(); }
        @Override public List<Map<String, Object>> find(long tenantId, long id, boolean deleted) { Map<String, Object> row = rows.get(id); return row != null && ((Number) row.get("deleted")).intValue() == (deleted ? 1 : 0) ? List.of(new LinkedHashMap<>(row)) : List.of(); }
        @Override public List<Long> relationIds(long tenantId, long issueId, String type) { return List.of(); }
        @Override public int insert(long id, long tenantId, long projectId, String issueCode, String issueName, String granularity, String systemCode, String issueSource, String defectType, String issueDescription, String solution, String meetingConclusion, String processingSteps, String businessScenario, String handler, String responsibleParty, String keywords, String frequency, long ownerId, long createdBy, long updatedBy) { return 1; }
        @Override public int update(long id, long tenantId, String issueCode, String issueName, String granularity, String systemCode, String issueSource, String defectType, String issueDescription, String solution, String meetingConclusion, String processingSteps, String businessScenario, String handler, String responsibleParty, String keywords, String frequency, long updatedBy) { if (failIssueUpdateWithDuplicate) throw new DuplicateKeyException("duplicate active issue code"); return 1; }
        @Override public int softDelete(long tenantId, long id, long deletedBy) { return 1; }
        @Override public int restore(long tenantId, long id, long updatedBy) { restoreWrites++; return 1; }
        @Override public int deleteRelations(long tenantId, long issueId, String type) { relationDeletes++; return 1; }
        @Override public int insertRelation(long tenantId, long issueId, String type, long relatedId, long createdBy) { relationInserts++; return 1; }
        @Override public int deleteAllRelations(long tenantId, long issueId) { relationDeletes++; return 1; }
        @Override public int purge(long tenantId, long id) { purgeWrites++; return 1; }
        @Override public int purgeAllRelations(long tenantId, long projectId) { return 1; }
        @Override public int purgeAll(long tenantId, long projectId) { return 1; }
        @Override public List<Map<String, Object>> systemName(long tenantId, String systemCode) { return List.of(); }
        @Override public List<Map<String, Object>> meetingOptions(long tenantId, long projectId) { return List.of(); }
        @Override public List<Map<String, Object>> targetTableOptions(long tenantId, long projectId) { return List.of(); }
        @Override public List<Long> targetTableProjects(long tenantId, long tableCode) { return List.of(); }
        @Override public List<Map<String, Object>> targetFieldOptions(long tenantId, long tableCode) { return List.of(); }
        @Override public Integer relationTargetCount(long tenantId, long projectId, long id, String type) { return invalidRelationTarget ? 0 : 1; }
        @Override public Integer invalidFieldRelationCount(long tenantId, long issueId) { return 0; }
        @Override public Integer issueCodeCount(long tenantId, long projectId, String issueCode, Long currentId) { return issueCode.equals(conflictingCode) ? 1 : 0; }
        @Override public Integer enabledComponentCount(long tenantId, long projectId, String systemCode) { return 1; }
        @Override public int insertAudit(long tenantId, long actorId, long projectId, String operation, long entityId) { auditWrites++; return 1; }
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return (T) Integer.valueOf(sql.contains("FROM sys_user_role") || sql.contains("FROM pm_project") ? 1 : 0);
        }
    }
}

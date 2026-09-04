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
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        IssueService service = service(jdbc);

        service.update(50L, minimalBody(), USER);

        assertEquals(0, jdbc.relationDeletes);
        assertEquals(0, jdbc.relationInserts);
    }

    @Test
    void explicitEmptyArraysClearAllRelationTypes() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        IssueService service = service(jdbc);
        Map<String, Object> body = minimalBody();
        body.put("relatedMeetingMinutes", List.of());
        body.put("relatedTables", List.of());
        body.put("relatedFields", List.of());

        service.update(50L, body, USER);

        assertEquals(3, jdbc.relationDeletes);
        assertEquals(0, jdbc.relationInserts);
    }

    @Test
    void explicitIdsReplaceOnlyThePresentRelationType() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        IssueService service = service(jdbc);
        Map<String, Object> body = minimalBody();
        body.put("relatedTables", List.of(31L));

        service.update(50L, body, USER);

        assertEquals(1, jdbc.relationDeletes);
        assertEquals(1, jdbc.relationInserts);
    }

    @Test
    void databaseUniquenessRaceBecomesBusinessConflict() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.failIssueUpdateWithDuplicate = true;
        IssueService service = service(jdbc);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(50L, minimalBody(), USER));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertEquals(0, jdbc.auditWrites);
    }

    @Test
    void restorePrevalidatesWholeBatchBeforeWriting() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.rows.get(50L).put("deleted", 1);
        jdbc.putIssue(51L, "ISSUE-51", true);
        jdbc.conflictingCode = "ISSUE-51";
        IssueService service = service(jdbc);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.restore(List.of(50L, 51L), USER));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertEquals(0, jdbc.restoreWrites);
        assertEquals(0, jdbc.auditWrites);
    }

    @Test
    void activeIssueCannotBePurgedOrLoseRelations() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        IssueService service = service(jdbc);

        assertThrows(BusinessException.class, () -> service.purge(List.of(50L), USER));

        assertEquals(0, jdbc.relationDeletes);
        assertEquals(0, jdbc.purgeWrites);
        assertEquals(0, jdbc.auditWrites);
    }

    @Test
    void deletedIssuePurgeDeletesRelationsThenAudits() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.putIssue(51L, "ISSUE-51", true);
        IssueService service = service(jdbc);

        service.purge(List.of(51L), USER);

        assertEquals(1, jdbc.relationDeletes);
        assertEquals(1, jdbc.purgeWrites);
        assertEquals(1, jdbc.auditWrites);
    }

    @Test
    void relationFromAnotherProjectIsRejectedBeforeRelationMutation() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.invalidRelationTarget = true;
        IssueService service = service(jdbc);
        Map<String, Object> body = minimalBody();
        body.put("relatedTables", List.of(31L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(50L, body, USER));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertEquals(0, jdbc.relationDeletes);
        assertEquals(0, jdbc.relationInserts);
        assertEquals(0, jdbc.auditWrites);
    }

    private IssueService service(StubJdbcTemplate jdbc) {
        return new IssueService(jdbc, new DataMigrationPermissionService(jdbc, StubProjectAccess.allow()));
    }

    private Map<String, Object> minimalBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", 10L);
        body.put("issueCode", "ISSUE-50");
        body.put("issueName", "Issue 50");
        return body;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        private int relationDeletes;
        private int relationInserts;
        private int restoreWrites;
        private int purgeWrites;
        private int auditWrites;
        private boolean failIssueUpdateWithDuplicate;
        private boolean invalidRelationTarget;
        private String conflictingCode;

        private StubJdbcTemplate() {
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

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            int count = 0;
            if (sql.contains("FROM sys_user_role")) count = 1;
            else if (sql.contains("FROM pm_project")) count = 1;
            else if (sql.contains("FROM dm_issue_relation")) count = 0;
            else if (sql.contains("FROM dm_target_table_field")) count = invalidRelationTarget ? 0 : 1;
            else if (sql.contains("FROM dm_target_table")) count = invalidRelationTarget ? 0 : 1;
            else if (sql.contains("FROM dm_meeting")) count = invalidRelationTarget ? 0 : 1;
            else if (sql.contains("FROM dm_issue")) {
                String code = String.valueOf(args[2]);
                count = code.equals(conflictingCode) ? 1 : 0;
            }
            return (T) Integer.valueOf(count);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (!sql.contains("FROM dm_issue i")) return List.of();
            long id = ((Number) args[1]).longValue();
            Map<String, Object> row = rows.get(id);
            if (row == null) return List.of();
            boolean expectedDeleted = sql.contains("i.deleted = 1");
            boolean actualDeleted = ((Number) row.get("deleted")).intValue() == 1;
            return expectedDeleted == actualDeleted ? List.of(new LinkedHashMap<>(row)) : List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return (List<T>) new ArrayList<Long>();
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE dm_issue SET issue_code")) {
                // T32 决策 D2：维护语句不得再携带 project_id，归属恒取库中记录。
                if (sql.contains("project_id")) throw new AssertionError("Issue update must not mutate project_id: " + sql);
                if (failIssueUpdateWithDuplicate) throw new DuplicateKeyException("duplicate active issue code");
                return 1;
            }
            if (sql.startsWith("UPDATE dm_issue SET deleted = 0")) {
                restoreWrites++;
                return 1;
            }
            if (sql.startsWith("UPDATE dm_issue SET deleted = 1")) return 1;
            if (sql.startsWith("DELETE FROM dm_issue_relation")) {
                relationDeletes++;
                return 1;
            }
            if (sql.startsWith("DELETE FROM dm_issue")) {
                purgeWrites++;
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_issue_relation")) {
                relationInserts++;
                return 1;
            }
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                auditWrites++;
                return 1;
            }
            throw new AssertionError("Unexpected update: " + sql);
        }
    }
}

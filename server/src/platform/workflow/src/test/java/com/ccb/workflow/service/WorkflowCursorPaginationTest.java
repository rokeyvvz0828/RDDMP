package com.ccb.workflow.service;

import org.junit.jupiter.api.Test;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.ccb.workflow.integration.WorkflowProjectMember;
import com.ccb.workflow.integration.WorkflowProjectRole;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowCursorPaginationTest {
    @Test
    void roundTripsTimestampAndIdWithoutBusinessData() {
        WorkflowCursorCodec codec = new WorkflowCursorCodec();
        String value = codec.encode(Timestamp.from(Instant.ofEpochMilli(1234)), 88L);
        WorkflowCursorCodec.Position position = codec.decode(value);
        assertEquals(1234L, position.createdAt().toInstant().toEpochMilli());
        assertEquals(88L, position.id());
    }

    @Test
    void rejectsMalformedCursor() {
        assertThrows(RuntimeException.class, () -> new WorkflowCursorCodec().decode("not-a-cursor"));
    }

    @Test
    void inboxSeekUsesTimestampIdPredicateAndFetchesOneExtraRow() {
        SeekInboxRepository repository = new SeekInboxRepository();
        WorkflowNodeLabelResolver labels = new WorkflowNodeLabelResolver(null, new ObjectMapper()) {
            @Override public List<Map<String, Object>> decorateTasks(List<Map<String, Object>> rows, long tenantId) { return rows; }
        };
        WorkflowService service = new WorkflowService(new ObjectMapper(), null, null, labels);
        service.setInboxRepository(repository);
        AuthUser user = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);

        var first = service.inboxSeek(null, 1, user);
        var second = service.inboxSeek(first.nextCursor(), 1, user);

        assertEquals(1, first.records().size());
        assertEquals(true, first.hasMore());
        assertEquals(2, repository.sizes.get(0));
        assertEquals(2, repository.sizes.get(1));
        assertEquals(false, repository.params.get(0).containsKey("cursorCreatedAt"));
        assertEquals(true, repository.params.get(1).containsKey("cursorCreatedAt"));
        assertEquals(101L, second.records().get(0).get("id"));
    }

    @Test
    void instancesSeekUsesTimestampIdPredicateAndProjectScope() {
        SeekRepository repository = new SeekRepository();
        WorkflowNodeLabelResolver labels = new WorkflowNodeLabelResolver(null, new ObjectMapper()) {
            @Override public String labelsForInstance(long instanceId, long tenantId, String nodeIds) { return nodeIds; }
        };
        WorkflowMonitorService service = new WorkflowMonitorService(repository, null, null, labels);
        service.setProjectAccess(new WorkflowProjectAccessGateway() {
            @Override public ProjectScope requireAccessible(String projectRef, AuthUser actor) { return new ProjectScope(11L, projectRef, "项目"); }
            @Override public void requireAccessible(long projectId, AuthUser actor) { }
            @Override public void requireManageable(long projectId, AuthUser actor) { }
            @Override public List<Long> accessibleProjectIds(AuthUser actor) { return List.of(11L); }
            @Override public List<WorkflowProjectMember> members(long projectId, AuthUser actor) { return List.of(); }
            @Override public List<WorkflowProjectRole> roles(long projectId, AuthUser actor) { return List.of(); }
            @Override public void requireMembers(long projectId, java.util.Collection<Long> userIds, AuthUser actor) { }
            @Override public List<WorkflowProjectMember> membersForRoles(long projectId, java.util.Collection<Long> roleIds, AuthUser actor) { return List.of(); }
        });
        AuthUser user = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);

        var first = service.instancesSeek(null, 1, null, null, null, null, null, null, null, user);
        service.instancesSeek(first.nextCursor(), 1, null, null, null, null, null, null, null, user);

        assertEquals(2, repository.sizes.get(0));
        assertEquals("accessible", repository.params.get(0).get("projectMode"));
        assertEquals(List.of(11L), repository.params.get(0).get("projectIds"));
        assertEquals(2, repository.sizes.get(1));
        assertTrue(repository.params.get(1).containsKey("cursorCreatedAt"));
    }

    private static final class SeekInboxRepository extends WorkflowInboxRepository {
        private final List<Map<String, Object>> params = new ArrayList<>();
        private final List<Integer> limits = new ArrayList<>();
        private final List<Integer> sizes = limits;
        private SeekInboxRepository() { super(null); }
        @Override public List<Map<String, Object>> seek(Map<String, Object> query) {
            params.add(new LinkedHashMap<>(query));
            limits.add(((Number) query.get("size")).intValue());
            Timestamp at = Timestamp.from(Instant.ofEpochMilli(1000));
            if (query.containsKey("cursorCreatedAt")) return List.of(row(101L, at));
            return List.of(row(102L, at), row(101L, at));
        }
        private Map<String, Object> row(long id, Timestamp at) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id); row.put("instance_id", 9L); row.put("created_at", at); row.put("status", "PENDING");
            return row;
        }
    }

    private static final class SeekRepository extends WorkflowMonitorRepository {
        private final List<Map<String,Object>> params = new ArrayList<>();
        private final List<Integer> sizes = new ArrayList<>();
        private SeekRepository() { super(null); }
        @Override public List<Map<String, Object>> instancesSeek(Map<String,Object> query) {
            params.add(query);
            sizes.add(((Number) query.get("size")).intValue());
            Timestamp at = Timestamp.from(Instant.ofEpochMilli(1000));
            if (query.containsKey("cursorCreatedAt")) return List.of(row(101L, at));
            return List.of(row(102L, at), row(101L, at));
        }
        private Map<String, Object> row(long id, Timestamp at) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id); row.put("instance_id", 9L); row.put("created_at", at); row.put("status", "PENDING");
            return row;
        }
    }
}

package com.ccb.requirement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.requirement.integration.RequirementDevelopmentQuery;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JdbcRequirementDevelopmentQueryTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "fixture", "", "虚构负责人", 1, true);

    @Test
    void mergesLeadChangeAndTestRolesWithoutTreatingMixedSystemAsTestOnly() {
        Fixture jdbc = new Fixture();
        jdbc.links.add(link("SYS-A", "LEAD", 9L));
        jdbc.links.add(link("SYS-A", "TEST", 10L));
        jdbc.links.add(link("SYS-A", "CHANGE", 9L));
        jdbc.links.add(link("SYS-B", "TEST", null));
        var source = new JdbcRequirementDevelopmentQuery(jdbc).find(ACTOR, "PROJECT-A", 42).orElseThrow();
        assertEquals(2, source.systems().size());
        assertEquals(Set.of(RequirementDevelopmentQuery.Role.LEAD, RequirementDevelopmentQuery.Role.CHANGE,
                RequirementDevelopmentQuery.Role.TEST), source.systems().get(0).roles());
        assertEquals(Set.of(RequirementDevelopmentQuery.Role.TEST), source.systems().get(1).roles());
        assertNull(source.systems().get(0).suggestedOwnerId());
    }

    @Test
    void revisionIgnoresAssociationOrderButChangesWhenRoleOwnerOrContentChanges() {
        Fixture jdbc = new Fixture();
        jdbc.links.add(link("SYS-B", "TEST", 10L));
        jdbc.links.add(link("SYS-A", "LEAD", 9L));
        var query = new JdbcRequirementDevelopmentQuery(jdbc);
        String first = query.find(ACTOR, "PROJECT-A", 42).orElseThrow().revision();
        java.util.Collections.reverse(jdbc.links);
        assertEquals(first, query.find(ACTOR, "PROJECT-A", 42).orElseThrow().revision());
        jdbc.links.set(0, link("SYS-A", "CHANGE", 9L));
        String changedRole = query.find(ACTOR, "PROJECT-A", 42).orElseThrow().revision();
        assertNotEquals(first, changedRole);
        jdbc.header.put("content_summary", "修订内容");
        assertNotEquals(changedRole, query.find(ACTOR, "PROJECT-A", 42).orElseThrow().revision());
        String changedText = query.find(ACTOR, "PROJECT-A", 42).orElseThrow().revision();
        jdbc.links.set(0, link("SYS-A", "CHANGE", 11L));
        assertNotEquals(changedText, query.find(ACTOR, "PROJECT-A", 42).orElseThrow().revision());
    }

    @Test
    void emptyAuthorizedSystemSetDoesNotQueryOrReturnTenantWideSources() {
        Fixture jdbc = new Fixture();
        var result = new JdbcRequirementDevelopmentQuery(jdbc).search(ACTOR,
                new RequirementDevelopmentQuery.Query("PROJECT-A", Set.of(), null, new PageQuery(1, 20)));
        assertEquals(0, result.total());
        assertTrue(result.records().isEmpty());
        assertEquals(0, jdbc.calls);
    }

    @Test
    void emittedQueriesBindTenantProjectAndAuthorizedSystemsBeforePagination() {
        Fixture jdbc = new Fixture();
        jdbc.links.add(link("SYS-A", "LEAD", 9L));
        var result = new JdbcRequirementDevelopmentQuery(jdbc).search(ACTOR,
                new RequirementDevelopmentQuery.Query("PROJECT-A", Set.of("SYS-A"), "abc", new PageQuery(2, 5)));
        assertEquals(1, result.total());
        assertEquals(42, result.records().get(0).id());
        assertTrue(jdbc.lastHeaderSql.contains("tenant_id = ?"));
        assertTrue(jdbc.lastHeaderSql.contains("project_id = ?"));
        assertTrue(jdbc.lastHeaderSql.contains("deleted = 0"));
        assertTrue(jdbc.lastHeaderSql.contains("需求终止"));
        assertTrue(jdbc.lastHeaderSql.contains("req_coordination_item"));
        assertEquals(7L, jdbc.lastHeaderArgs[0]);
        assertEquals(3100L, jdbc.lastHeaderArgs[1]);
        assertTrue(List.of(jdbc.lastHeaderArgs).contains("SYS-A"));
        assertEquals(5L, jdbc.lastHeaderArgs[jdbc.lastHeaderArgs.length - 1]);
    }

    @Test
    void findUsesProjectAndTenantAndExcludesInactiveSource() {
        Fixture jdbc = new Fixture();
        jdbc.absent = true;
        assertTrue(new JdbcRequirementDevelopmentQuery(jdbc).find(ACTOR, "PROJECT-A", 42).isEmpty());
        assertArrayEquals(new Object[]{7L, 3100L, 42L}, jdbc.lastHeaderArgs);
        assertTrue(jdbc.lastHeaderSql.contains("需求终止"));
    }

    @Test
    void unknownRoleAndMissingSystemCodeFailClosed() {
        Fixture jdbc = new Fixture();
        jdbc.links.add(link("SYS-A", "UNKNOWN", 9L));
        assertThrows(BusinessException.class, () -> new JdbcRequirementDevelopmentQuery(jdbc).find(ACTOR, "PROJECT-A", 42));
        jdbc.links.clear();
        jdbc.links.add(link("", "TEST", 9L));
        assertThrows(BusinessException.class, () -> new JdbcRequirementDevelopmentQuery(jdbc).find(ACTOR, "PROJECT-A", 42));
    }

    @Test
    void missingOrAmbiguousProjectMappingDoesNotFallBackToAnotherProject() {
        Fixture jdbc = new Fixture();
        var query = new JdbcRequirementDevelopmentQuery(jdbc);
        jdbc.projectRows = List.of();
        assertThrows(BusinessException.class, () -> query.find(ACTOR, "PROJECT-A", 42));
        jdbc.projectRows = List.of(Map.of("id", 3100L), Map.of("id", 3200L));
        assertThrows(BusinessException.class, () -> query.find(ACTOR, "PROJECT-A", 42));
        assertNull(jdbc.lastHeaderSql);
    }

    private static Map<String, Object> link(String code, String role, Long owner) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("requirement_id", 42L);
        row.put("system_code", code);
        row.put("role", role);
        row.put("owner_user_id", owner);
        return row;
    }

    private static class Fixture extends JdbcTemplate {
        final Map<String, Object> header = new LinkedHashMap<>(Map.of("id", 42L, "project_id", 3100L,
                "requirement_no", "REQ-FIXTURE", "requirement_name", "虚构需求", "content_summary", "内容"));
        List<Map<String, Object>> projectRows = List.of(Map.of("id", 3100L));
        final List<Map<String, Object>> links = new ArrayList<>();
        int calls;
        boolean absent;
        String lastHeaderSql;
        Object[] lastHeaderArgs;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            calls++;
            if (sql.contains("FROM req_project")) {
                assertArrayEquals(new Object[]{7L, "PROJECT-A"}, args);
                assertTrue(sql.contains("deleted = 0"));
                return projectRows;
            }
            if (sql.contains("SELECT r.id")) {
                lastHeaderSql = sql;
                lastHeaderArgs = args;
                return absent ? List.of() : List.of(header);
            }
            assertTrue(sql.contains("req_legacy_system_item"));
            assertTrue(sql.contains("req_coordination_item"));
            assertTrue(sql.contains("tenant_id = ?"));
            assertEquals(7L, args[0]);
            return List.copyOf(links);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            calls++;
            assertTrue(sql.contains("project_id = ?"));
            assertTrue(sql.contains("req_coordination_item"));
            assertEquals(7L, args[0]);
            assertEquals(3100L, args[1]);
            return type.cast(1L);
        }
    }
}

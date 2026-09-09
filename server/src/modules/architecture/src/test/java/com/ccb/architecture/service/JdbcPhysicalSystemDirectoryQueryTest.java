package com.ccb.architecture.service;

import com.ccb.common.api.PageQuery;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcPhysicalSystemDirectoryQueryTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "fixture", "", "虚构负责人", 1, true);

    @AfterEach
    void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void manageableQueryRestrictsOwnerAndTenantBeforeCountingAndPaging() {
        Fixture jdbc = new Fixture();
        var page = new JdbcPhysicalSystemDirectoryQuery(jdbc).searchManageable(ACTOR, 31, new PageQuery(1, 20), "系统");
        assertEquals(1, page.total());
        assertEquals(9L, page.records().get(0).ownerId());
        assertTrue(jdbc.query.contains("owner_user_id = ?"));
        assertTrue(jdbc.query.contains("tenant_id = ?"));
        assertTrue(jdbc.query.contains("project_id = ?"), "系统选项必须限定当前项目");
        assertTrue(jdbc.query.contains("status = 'ACTIVE'"));
        assertEquals(7L, jdbc.args[0]);
        assertEquals(31L, jdbc.args[1]);
        assertEquals(9L, jdbc.args[2]);
    }

    @Test
    void explicitAdministratorCanSearchTenantSystemsButCannotChangeTenant() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null,
                List.of(new SimpleGrantedAuthority("development:admin"))));
        Fixture jdbc = new Fixture();
        new JdbcPhysicalSystemDirectoryQuery(jdbc).searchManageable(ACTOR, 31, new PageQuery(1, 20), null);
        assertFalse(jdbc.query.contains("owner_user_id = ?"));
        assertEquals(7L, jdbc.args[0]);
        assertEquals(31L, jdbc.args[1]);
        assertTrue(jdbc.query.contains("project_id = ?"));
    }

    @Test
    void differentPrincipalCannotLendAdministratorAuthorityToActor() {
        AuthUser other = new AuthUser(10, 8, "other", "", "另一个虚构用户", 1, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other, null,
                List.of(new SimpleGrantedAuthority("development:admin"))));
        Fixture jdbc = new Fixture();
        new JdbcPhysicalSystemDirectoryQuery(jdbc).searchManageable(ACTOR, 31, new PageQuery(1, 20), null);
        assertTrue(jdbc.query.contains("owner_user_id = ?"));
    }

    @Test
    void findReadsCurrentOwnerAndVersionWithoutCachingOldAuthorization() {
        Fixture jdbc = new Fixture();
        var query = new JdbcPhysicalSystemDirectoryQuery(jdbc);
        assertEquals(9L, query.find(ACTOR, 31, 42).orElseThrow().ownerId());
        jdbc.owner = 11;
        jdbc.version = 3;
        var changed = query.find(ACTOR, 31, 42).orElseThrow();
        assertEquals(11L, changed.ownerId());
        assertEquals(3, changed.rowVersion());
        assertArrayEquals(new Object[]{7L, 31L, 42L}, jdbc.args);
        assertTrue(jdbc.query.contains("project_id = ?"));
    }

    private static class Fixture extends JdbcTemplate {
        String query;
        Object[] args;
        long owner = 9;
        long version = 2;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... parameters) {
            query = sql;
            args = parameters;
            return List.of(Map.of("id", 42L, "code", "SYS-A", "name", "虚构系统", "owner_user_id", owner,
                    "status", "ACTIVE", "row_version", version));
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... parameters) {
            assertTrue(sql.contains("tenant_id = ?"));
            assertTrue(sql.contains("project_id = ?"));
            return type.cast(1L);
        }
    }
}

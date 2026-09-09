package com.ccb.system.capability;

import com.ccb.security.model.AuthUser;
import com.ccb.system.internal.capability.JdbcProjectMemberReferenceQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectMemberReferenceQueryTest {
    @Test
    void queriesOnlyActiveMembersInActorTenantAndProject() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(1L), eq(9001L)))
                .thenReturn(List.of(new ProjectMemberReference(7001L, 42L, "项目成员", "member")));

        List<ProjectMemberReference> result = new JdbcProjectMemberReferenceQuery(jdbc)
                .findActiveMembers(new AuthUser(7L, 1L, "operator", "", "操作员", 1L, true), 9001L);

        assertEquals(1, result.size());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class), eq(1L), eq(9001L));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("m.status = 1"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("m.deleted = 0"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("u.deleted = 0"));
    }
    @Test
    void 只读查询不持资格锁而写事务采用当前共享锁读() {
        for (boolean readOnly : List.of(true, false)) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);
            try {
                new JdbcProjectMemberReferenceQuery(jdbc).findActiveMembers(
                        new AuthUser(7L,1L,"operator","","操作员",1L,true),9001L);
                ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
                verify(jdbc).query(sql.capture(),any(org.springframework.jdbc.core.RowMapper.class),eq(1L),eq(9001L));
                assertEquals(!readOnly,sql.getValue().contains("FOR SHARE"));
            } finally {
                org.springframework.transaction.support.TransactionSynchronizationManager.clear();
            }
        }
    }
}

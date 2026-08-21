package com.ccb.architecture.repository;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.model.PhysicalSubsystem;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.common.api.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureSubsystemRepositoryTest {
    @Mock
    private JdbcTemplate jdbc;

    private ArchitectureSubsystemRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ArchitectureSubsystemRepository(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesLogicalRecordsWithinTenantAndEscapesFilters() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.pageLogical(9L, new PageQuery(2, 10), new LogicalSubsystemQuery("A%_\\B", "简称", "名称", 21L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND deleted = 0"));
        assertTrue(sql.getValue().contains("ESCAPE '\\\\'"));
        assertEquals(9L, args.getValue()[0]);
        assertEquals("%A\\%\\_\\\\B%", args.getValue()[1]);
        assertEquals(21L, args.getValue()[4]);
        assertEquals(10L, args.getValue()[5]);
        assertEquals(10L, args.getValue()[6]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void locksLogicalParentEvenWhenDeletedAndCountsOnlyActiveChildren() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);

        assertTrue(repository.lockLogical(9L, 12L).isEmpty());
        assertEquals(3L, repository.countActivePhysicalByLogical(9L, 12L));

        ArgumentCaptor<String> lockSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(lockSql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(lockSql.getValue().contains("FOR UPDATE"));
        assertTrue(lockSql.getValue().contains("SELECT id, deleted"));
        assertFalse(lockSql.getValue().contains("deleted = 0"));
    }

    @Test
    void softDeletesOnlyActiveTenantRow() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals(1, repository.softDeleteLogical(9L, 12L, 7L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND id = ? AND deleted = 0"));
        assertEquals(List.of(7L, 9L, 12L), List.of(args.getValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesPhysicalRecordsWithFixedParentTeamAndTextFilters() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.pagePhysical(9L, new PageQuery(1, 20),
                new PhysicalSubsystemQuery("P1", "物理", "系统", "事业群", 31L, 12L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND deleted = 0"));
        assertTrue(sql.getValue().contains("responsible_team_org_id = ?"));
        assertTrue(sql.getValue().contains("logical_subsystem_id = ?"));
        assertEquals(9L, args.getValue()[0]);
        assertEquals(31L, args.getValue()[5]);
        assertEquals(12L, args.getValue()[6]);
    }
}

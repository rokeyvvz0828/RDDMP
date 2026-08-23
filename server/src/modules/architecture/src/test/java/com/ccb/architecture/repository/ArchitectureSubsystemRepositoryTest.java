package com.ccb.architecture.repository;

import com.ccb.architecture.model.LogicalSubsystemQuery;
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
    void 逻辑分页按租户状态筛选并投影V82字段() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.pageLogical(9L, new PageQuery(2, 10),
                new LogicalSubsystemQuery("A%_\\B", "简称", "名称", 21L, "OFFLINE"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND deleted = 0"));
        assertTrue(sql.getValue().contains("status = ?"));
        assertTrue(sql.getValue().contains("number_sequence, status, sort_no, row_version"));
        assertTrue(sql.getValue().contains("ORDER BY sort_no ASC, id DESC"));
        assertTrue(sql.getValue().contains("ESCAPE '\\\\'"));
        assertEquals(List.of(9L, "%A\\%\\_\\\\B%", "%简称%", "%名称%", 21L, "OFFLINE", 10L, 10L),
                List.of(args.getValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 物理分页按租户状态筛选并投影V82字段() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.pagePhysical(9L, new PageQuery(1, 20),
                new PhysicalSubsystemQuery("W", "物理", "系统", "事业群", 31L, 12L, "VOIDED"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND deleted = 0"));
        assertTrue(sql.getValue().contains("logical_subsystem_id = ?"));
        assertTrue(sql.getValue().contains("status = ?"));
        assertTrue(sql.getValue().contains("number_slot, english_name, status, row_version"));
        assertEquals(List.of(9L, "%W%", "%物理%", "%系统%", "%事业群%", 31L, 12L, "VOIDED", 20L, 0L),
                List.of(args.getValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 逻辑详情的物理摘要排除软删除历史并保持租户隔离() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        assertTrue(repository.findPhysicalByLogical(9L, 12L).isEmpty());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND logical_subsystem_id = ? AND deleted = 0"));
        assertTrue(sql.getValue().contains("ORDER BY number_slot ASC, id ASC"));
        assertEquals(List.of(9L, 12L), List.of(args.getValue()));
    }

    @Test
    void 活动物理计数仅统计ACTIVE发布事实() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);

        assertEquals(3L, repository.countActivePhysicalByLogical(9L, 12L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getValue().contains("deleted = 0 AND status = 'ACTIVE'"));
    }

    @Test
    void 物理历史计数包含软删除和非活动记录但仍按租户隔离() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(4L);

        assertEquals(4L, repository.countPhysicalHistoryByLogical(9L, 12L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND logical_subsystem_id = ?"));
        assertFalse(sql.getValue().contains("deleted = 0"));
        assertFalse(sql.getValue().contains("status ="));
        assertEquals(List.of(9L, 12L), List.of(args.getValue()));
    }

    @Test
    void 软删除仍限定当前租户和未删除记录() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals(1, repository.softDeleteLogical(9L, 12L, 7L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND id = ? AND deleted = 0"));
        assertFalse(sql.getValue().contains("status = 'VOIDED'"));
        assertEquals(List.of(7L, 9L, 12L), List.of(args.getValue()));
    }
}

package com.ccb.architecture.repository;

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
    void 物理分页按租户状态逻辑名称和业务组件筛选并投影新字段() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.pagePhysical(9L, new PageQuery(1, 20),
                new PhysicalSubsystemQuery("W", "物理", "系统", "逻辑域",
                        "architecture.business-component.employee-portal", "事业群", 31L, "VOIDED"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND deleted = 0"));
        assertTrue(sql.getValue().contains("logical_subsystem_name LIKE ?"));
        assertTrue(sql.getValue().contains("business_component_code = ?"));
        assertTrue(sql.getValue().contains("status = ?"));
        assertTrue(sql.getValue().contains("logical_subsystem_name, business_component_code"));
        assertFalse(sql.getValue().contains("logical_subsystem_id"));
        assertEquals(List.of(9L, "%W%", "%物理%", "%系统%", "%逻辑域%",
                        "architecture.business-component.employee-portal", "%事业群%", 31L, "VOIDED", 20L, 0L),
                List.of(args.getValue()));
    }

    @Test
    void 唯一性检查可排除当前物理记录且包含软删除历史() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);

        assertTrue(repository.physicalCodeExists(9L, "PHY_DEMO", 12L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), args.capture());
        assertTrue(sql.getValue().contains("arch_physical_subsystem"));
        assertTrue(sql.getValue().contains("code = ?"));
        assertTrue(sql.getValue().contains("id <> ?"));
        assertFalse(sql.getValue().contains("deleted = 0"));
        assertEquals(List.of(9L, "PHY_DEMO", 12L), List.of(args.getValue()));
    }

    @Test
    void 软删除物理仍限定当前租户和未删除记录() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals(1, repository.softDeletePhysical(9L, 12L, 7L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND id = ? AND deleted = 0"));
        assertEquals(List.of(7L, 9L, 12L), List.of(args.getValue()));
    }
}

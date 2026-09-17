package com.ccb.architecture.repository;

import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.common.api.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureSubsystemRepositoryTest {
    @Mock
    private ArchitectureSubsystemMapper mapper;

    private ArchitectureSubsystemRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ArchitectureSubsystemRepository(mapper);
    }

    @Test
    void 物理分页按租户状态逻辑名称和业务组件筛选并投影新字段() {
        when(mapper.countPhysical(any())).thenReturn(0L);
        when(mapper.physicals(any())).thenReturn(List.of());

        repository.pagePhysical(9L, new PageQuery(1, 20),
                new PhysicalSubsystemQuery("W", "物理", "系统", "逻辑域",
                        "architecture.business-component.employee-portal", "事业群", 31L, "VOIDED"));

        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(mapper).physicals(parameters.capture());
        assertEquals(9L, parameters.getValue().get("tenantId"));
        assertEquals("%逻辑域%", parameters.getValue().get("logicalSubsystemName"));
        assertEquals("architecture.business-component.employee-portal", parameters.getValue().get("businessComponentCode"));
        assertEquals("VOIDED", parameters.getValue().get("status"));
    }

    @Test
    void 唯一性检查可排除当前物理记录且包含软删除历史() {
        when(mapper.countByCode(any())).thenReturn(1L);

        assertTrue(repository.physicalCodeExists(9L, "PHY_DEMO", 12L));

        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(mapper).countByCode(parameters.capture());
        assertEquals(Map.of("tenantId", 9L, "value", "PHY_DEMO", "excludeId", 12L), parameters.getValue());
    }

    @Test
    void 软删除物理仍限定当前租户和未删除记录() {
        when(mapper.softDeletePhysical(any())).thenReturn(1);

        assertEquals(1, repository.softDeletePhysical(9L, 12L, 7L));

        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(mapper).softDeletePhysical(parameters.capture());
        assertEquals(Map.of("actorId", 7L, "tenantId", 9L, "id", 12L), parameters.getValue());
    }
}

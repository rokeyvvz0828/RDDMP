package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 迁移映射统一回收站来源测试：只认领 MAPPING_DOC，恢复/清理委托专属服务。 */
class MappingRecycleBinSourceTest {
    private static final AuthUser USER = new AuthUser(1L, 1L, "admin", "", "管理员", 11L, true);

    @Test
    void supportsOnlyMappingDocType() {
        assertEquals(Set.of("MAPPING_DOC"), new MappingRecycleBinSource(null).supports());
    }

    @Test
    void delegatesRestoreAndPurgeToMappingService() {
        MappingService service = mock(MappingService.class);
        MappingRecycleBinSource source = new MappingRecycleBinSource(service);
        source.restore("MAPPING_DOC", List.of(60L, 61L), USER);
        source.purge("MAPPING_DOC", List.of(62L), USER);
        verify(service).restore(List.of(60L, 61L), USER);
        verify(service).purge(List.of(62L), USER);
    }
}

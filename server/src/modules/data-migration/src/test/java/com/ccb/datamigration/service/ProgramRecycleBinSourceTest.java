package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 迁移程序统一回收站来源验证（REQ-20260820-031 增量）：只认领 SCRIPT，
 * 不与其他通用来源重复认领；恢复/彻底删除全部委托 {@link ProgramService} 管理规则。
 */
class ProgramRecycleBinSourceTest {

    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);

    @Test
    void supportsOnlyScriptType() {
        assertEquals(Set.of("SCRIPT"), new ProgramRecycleBinSource(null).supports());
    }

    @Test
    void delegatesRestoreAndPurgeToProgramService() {
        ProgramService service = mock(ProgramService.class);
        ProgramRecycleBinSource source = new ProgramRecycleBinSource(service);
        source.restore("SCRIPT", List.of(50L, 51L), USER);
        source.purge("SCRIPT", List.of(52L), USER);
        verify(service).restore(List.of(50L, 51L), USER);
        verify(service).purge(List.of(52L), USER);
    }
}

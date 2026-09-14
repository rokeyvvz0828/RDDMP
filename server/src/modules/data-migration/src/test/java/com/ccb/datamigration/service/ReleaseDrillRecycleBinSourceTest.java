package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 投产及演练统一回收站来源验证（REQ-20260820-031 增量）：只认领 RELEASE_DRILL，
 * 不与其他通用来源重复认领；恢复/彻底删除全部委托 {@link ReleaseDrillService} 管理规则。
 */
class ReleaseDrillRecycleBinSourceTest {

    @Test
    void supportsOnlyReleaseDrillType() {
        assertEquals(Set.of("RELEASE_DRILL"), new ReleaseDrillRecycleBinSource(null).supports());
    }
}

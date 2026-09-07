package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** 迁移检核规则统一回收站来源验证：只认领 RULE，避免与通用结构化来源重复认领。 */
class RuleRecycleBinSourceTest {

    @Test
    void supportsOnlyRuleType() {
        assertEquals(Set.of("RULE"), new RuleRecycleBinSource(null).supports());
    }
}

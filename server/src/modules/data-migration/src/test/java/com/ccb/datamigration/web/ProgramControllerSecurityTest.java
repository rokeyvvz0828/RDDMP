package com.ccb.datamigration.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移程序 Controller 权限声明测试：类级访问权限 + 写操作专属权限码。
 */
class ProgramControllerSecurityTest {

    @Test
    void protectsEveryRouteWithDataMigrationPermissions() {
        PreAuthorize root = ProgramController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("data-migration:content:programs"));
        assertTrue(root.value().contains("system:admin"));

        Map<String, String> expected = Map.of(
                "create", "data-migration:content:programs:create",
                "update", "data-migration:content:programs:update",
                "delete", "data-migration:content:programs:delete"
        );
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            var method = Arrays.stream(ProgramController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst().orElseThrow();
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertTrue(annotation.value().contains(entry.getValue()), entry.getKey());
            assertTrue(annotation.value().contains("system:admin"), entry.getKey());
        }
        assertEquals(3, expected.size());
    }
}

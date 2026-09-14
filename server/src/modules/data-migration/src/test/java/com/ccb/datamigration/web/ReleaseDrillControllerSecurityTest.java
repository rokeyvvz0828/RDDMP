package com.ccb.datamigration.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 投产及演练 Controller 权限声明测试：类级访问权限 + 写操作专属权限码。
 */
class ReleaseDrillControllerSecurityTest {

    @Test
    void protectsEveryRouteWithDataMigrationPermissions() {
        PreAuthorize root = ReleaseDrillController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("data-migration:content:release-drills"));
        assertTrue(root.value().contains("system:admin"));

        Map<String, String> expected = Map.of(
                "create", "data-migration:content:release-drills:create",
                "update", "data-migration:content:release-drills:update",
                "delete", "data-migration:content:release-drills:delete"
        );
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            var method = Arrays.stream(ReleaseDrillController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst().orElseThrow();
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertTrue(annotation.value().contains(entry.getValue()), entry.getKey());
            assertTrue(annotation.value().contains("system:admin"), entry.getKey());
        }
        assertEquals(3, expected.size());
    }
}

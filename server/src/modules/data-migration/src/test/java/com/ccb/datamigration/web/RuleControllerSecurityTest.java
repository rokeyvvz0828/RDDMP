package com.ccb.datamigration.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 迁移检核规则 Controller 权限声明测试：类级查看权限 + 写操作专属权限码。 */
class RuleControllerSecurityTest {

    @Test
    void protectsEveryRouteWithDataMigrationPermissions() {
        PreAuthorize root = RuleController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("data-migration:content:validation-rules"));
        assertTrue(root.value().contains("system:admin"));
        assertTrue(root.value().contains("data-migration:access"));
        assertTrue(root.value().contains("data-migration:manage"));
        assertTrue(root.value().contains("data-migration:write"));

        Map<String, String> expected = Map.of(
                "create", "data-migration:content:validation-rules:create",
                "importRules", "data-migration:content:validation-rules:create",
                "update", "data-migration:content:validation-rules:update",
                "delete", "data-migration:content:validation-rules:delete"
        );
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            var method = Arrays.stream(RuleController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst().orElseThrow();
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertTrue(annotation.value().contains(entry.getValue()), entry.getKey());
            assertTrue(annotation.value().contains("system:admin"), entry.getKey());
            assertTrue(annotation.value().contains("data-migration:manage"), entry.getKey());
            assertTrue(annotation.value().contains("data-migration:write"), entry.getKey());
        }
        assertEquals(4, expected.size());

        for (String methodName : List.of("list", "template", "export", "detail")) {
            var method = Arrays.stream(RuleController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            assertNull(method.getAnnotation(PreAuthorize.class), methodName + " 应使用类级查看权限");
        }
    }
}

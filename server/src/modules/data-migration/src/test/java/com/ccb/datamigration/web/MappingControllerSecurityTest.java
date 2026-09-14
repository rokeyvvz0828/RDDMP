package com.ccb.datamigration.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 迁移映射 Controller 权限声明测试：类级访问权限 + 写操作专属权限码。 */
class MappingControllerSecurityTest {

    @Test
    void protectsEveryRouteWithDataMigrationPermissions() {
        PreAuthorize root = MappingController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("data-migration:content:mappings"));
        assertTrue(root.value().contains("system:admin"));
        assertTrue(root.value().contains("data-migration:access"));
        assertTrue(root.value().contains("data-migration:manage"));
        assertTrue(root.value().contains("data-migration:write"));

        Map<String, String> expected = Map.of(
                "create", "data-migration:content:mappings:create",
                "update", "data-migration:content:mappings:update",
                "delete", "data-migration:content:mappings:delete"
        );
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            var method = Arrays.stream(MappingController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst().orElseThrow();
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertTrue(annotation.value().contains(entry.getValue()), entry.getKey());
            assertTrue(annotation.value().contains("system:admin"), entry.getKey());
            assertTrue(annotation.value().contains("data-migration:manage"), entry.getKey());
            assertTrue(annotation.value().contains("data-migration:write"), entry.getKey());
        }
        assertEquals(3, expected.size());

        for (String methodName : List.of("list", "typeOptions", "detail", "download", "downloadAll", "attachments")) {
            var method = Arrays.stream(MappingController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            assertNull(method.getAnnotation(PreAuthorize.class), methodName + " 应使用类级查看权限");
        }
    }
}

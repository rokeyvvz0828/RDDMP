package com.ccb.release.production.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseProductionControllerSecurityTest {
    @Test
    void separatesBaselineAndProductionVersionPermissions() {
        PreAuthorize root = ReleaseProductionController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("release:access"));

        Map<String, String> expected = Map.of(
                "baseline", "release:baseline:view",
                "updateResult", "release:baseline:update",
                "updateResults", "release:baseline:update",
                "currentVersions", "release:production-version:view",
                "history", "release:production-version:view",
                "historyByEntry", "release:production-version:view"
        );
        expected.forEach((methodName, permission) -> {
            var method = java.util.Arrays.stream(ReleaseProductionController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            String expression = method.getAnnotation(PreAuthorize.class).value();
            assertTrue(expression.contains(permission), methodName);
            assertTrue(expression.contains("system:admin"), methodName);
        });
        var currentVersions = java.util.Arrays.stream(ReleaseProductionController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("currentVersions"))
                .findFirst().orElseThrow();
        assertTrue(currentVersions.getAnnotation(PreAuthorize.class).value().contains("release:application:view"));
        assertEquals(6, expected.size());
    }
}

package com.ccb.release.application.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseApplicationControllerSecurityTest {
    @Test
    void protectsEveryApplicationRouteWithBusinessPermissions() {
        assertTrue(ReleaseApplicationController.class.getAnnotation(PreAuthorize.class).value().contains("release:access"));
        Map<String, String> expected = Map.ofEntries(
                Map.entry("list", "release:application:view"),
                Map.entry("detail", "release:application:view"),
                Map.entry("relatedHistory", "release:application:view"),
                Map.entry("create", "release:application:create"),
                Map.entry("update", "release:application:update"),
                Map.entry("previewCreate", "release:application:create"),
                Map.entry("previewUpdate", "release:application:update"),
                Map.entry("conflicts", "release:application:view"),
                Map.entry("resolveConflict", "release:application:update"),
                Map.entry("submit", "release:application:submit"),
                Map.entry("withdraw", "release:application:withdraw"),
                Map.entry("conflictCancel", "release:application:withdraw"),
                Map.entry("cancel", "release:application:cancel"),
                Map.entry("currentRound", "release:application:view"),
                Map.entry("attachments", "release:application:view"),
                Map.entry("deleteAttachment", "release:application:update")
        );
        expected.forEach((name, permission) -> {
            var method = java.util.Arrays.stream(ReleaseApplicationController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            String expression = method.getAnnotation(PreAuthorize.class).value();
            assertTrue(expression.contains(permission), name);
            assertTrue(expression.contains("system:admin"), name);
        });
    }
}

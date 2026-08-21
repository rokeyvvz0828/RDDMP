package com.ccb.release.window.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseWindowControllerSecurityTest {
    @Test
    void protectsEveryRouteWithReleasePermissions() {
        PreAuthorize root = ReleaseWindowController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("release:access"));

        Map<String, String> expected = Map.of(
                "detail", "release:window:view",
                "create", "release:window:create",
                "update", "release:window:update",
                "changeRegularEnabled", "release:window:update"
        );
        expected.forEach((methodName, permission) -> {
            var method = java.util.Arrays.stream(ReleaseWindowController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertTrue(annotation.value().contains(permission), methodName);
            assertTrue(annotation.value().contains("system:admin"), methodName);
        });
        var listMethod = java.util.Arrays.stream(ReleaseWindowController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("list"))
                .findFirst().orElseThrow();
        String listPermissions = listMethod.getAnnotation(PreAuthorize.class).value();
        for (String permission : new String[]{"release:window:view", "release:application:view",
                "release:application:create", "release:baseline:view", "release:analytics:view", "system:admin"}) {
            assertTrue(listPermissions.contains(permission), permission);
        }
        assertEquals(4, expected.size());
    }
}

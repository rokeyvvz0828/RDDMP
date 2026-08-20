package com.ccb.release.workflow.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseWorkflowBindingControllerSecurityTest {
    @Test
    void protectsReadAndUpdateRoutesWithDedicatedPermissions() {
        assertTrue(ReleaseWorkflowBindingController.class.getAnnotation(PreAuthorize.class).value().contains("release:access"));
        Map<String, String> expected = Map.of(
                "list", "release:workflow-config:view",
                "publishedDefinitions", "release:workflow-config:view",
                "history", "release:workflow-config:view",
                "update", "release:workflow-config:update"
        );
        expected.forEach((name, permission) -> {
            var method = java.util.Arrays.stream(ReleaseWorkflowBindingController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            String expression = method.getAnnotation(PreAuthorize.class).value();
            assertTrue(expression.contains(permission), name);
            assertTrue(expression.contains("system:admin"), name);
        });
    }
}

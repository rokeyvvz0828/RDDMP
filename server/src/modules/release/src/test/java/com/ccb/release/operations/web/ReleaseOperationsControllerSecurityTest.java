package com.ccb.release.operations.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseOperationsControllerSecurityTest {
    @Test
    void protectsAllOperationsByPageViewOrManagePermission() {
        PreAuthorize root = ReleaseOperationsController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("release-operations:access"));
        Map<String, String> expected = Map.of(
                "drillPlan", "release-operations:drill:view",
                "saveDrillPlan", "release-operations:drill:manage",
                "timeline", "release-operations:timeline:view",
                "saveTimeline", "release-operations:timeline:manage",
                "issues", "release-operations:issue:view",
                "createIssue", "release-operations:issue:manage",
                "groups", "release-operations:organization:view",
                "createGroup", "release-operations:organization:manage",
                "memberOptions", "release-operations:organization:view"
        );
        expected.forEach((method, permission) -> {
            var target = java.util.Arrays.stream(ReleaseOperationsController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow();
            assertTrue(target.getAnnotation(PreAuthorize.class).value().contains(permission), method);
            assertTrue(target.getAnnotation(PreAuthorize.class).value().contains("system:admin"), method);
        });
    }
}

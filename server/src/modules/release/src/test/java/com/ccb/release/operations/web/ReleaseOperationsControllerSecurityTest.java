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
        Map<String, String> expected = Map.ofEntries(
                Map.entry("releasePlans", "release-operations:plan:view"),
                Map.entry("createReleasePlan", "release-operations:plan:manage"),
                Map.entry("createPlanTimeline", "release-operations:plan:manage"),
                Map.entry("updatePlanTimeline", "release-operations:plan:manage"),
                Map.entry("deletePlanTimeline", "release-operations:plan:manage"),
                Map.entry("createPlanItem", "release-operations:plan:manage"),
                Map.entry("updatePlanItem", "release-operations:plan:manage"),
                Map.entry("deletePlanItem", "release-operations:plan:manage"),
                Map.entry("drillEnvironments", "release-operations:environment:view"),
                Map.entry("createDrillEnvironment", "release-operations:environment:manage"),
                Map.entry("releaseDrills", "release-operations:drill:view"),
                Map.entry("createReleaseDrill", "release-operations:drill:manage"),
                Map.entry("createDrillStep", "release-operations:drill:manage"),
                Map.entry("drillPlan", "release-operations:drill:view"),
                Map.entry("saveDrillPlan", "release-operations:drill:manage"),
                Map.entry("timeline", "release-operations:timeline:view"),
                Map.entry("saveTimeline", "release-operations:timeline:manage"),
                Map.entry("issues", "release-operations:issue:view"),
                Map.entry("createIssue", "release-operations:issue:manage"),
                Map.entry("groups", "release-operations:organization:view"),
                Map.entry("createGroup", "release-operations:organization:manage"),
                Map.entry("memberOptions", "release-operations:organization:view")
        );
        expected.forEach((method, permission) -> {
            var target = java.util.Arrays.stream(ReleaseOperationsController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow();
            assertTrue(target.getAnnotation(PreAuthorize.class).value().contains(permission), method);
            assertTrue(target.getAnnotation(PreAuthorize.class).value().contains("system:admin"), method);
        });
    }
}

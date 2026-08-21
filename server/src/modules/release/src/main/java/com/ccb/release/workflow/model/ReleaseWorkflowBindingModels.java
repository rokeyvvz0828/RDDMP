package com.ccb.release.workflow.model;

import java.time.LocalDateTime;
import java.util.Arrays;

public final class ReleaseWorkflowBindingModels {
    private ReleaseWorkflowBindingModels() {}

    public enum Scene {
        REGULAR("常规版本"), REGULAR_ADDITIONAL("常规追加版本"), URGENT("紧急版本"),
        URGENT_ADDITIONAL("紧急追加版本"), EMERGENCY("应急版本");

        private final String label;
        Scene(String label) { this.label = label; }
        public String label() { return label; }
        public static Scene parse(String value) {
            return Arrays.stream(values()).filter(scene -> scene.name().equalsIgnoreCase(value == null ? "" : value.trim()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("未知审批场景"));
        }
    }

    public record Binding(long id, long tenantId, String projectRef, String projectName, Scene scene,
                          Long workflowDefinitionId, String workflowCode, String workflowName, Integer workflowVersion,
                          long rowVersion, long createdBy, long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record BindingView(String sceneCode, String sceneName, String projectRef, String projectName,
                              Long workflowDefinitionId, String workflowCode, String workflowName,
                              Integer workflowVersion, boolean configured, boolean valid, String invalidReason,
                              long rowVersion, LocalDateTime updatedAt) {}
    public record PublishedDefinitionView(long definitionId, String workflowCode, String workflowName,
                                          int workflowVersion) {}
    public record UpdateBindingRequest(String projectRef, String projectName, Long workflowDefinitionId,
                                       Long rowVersion, String reason) {}
    public record BindingHistoryView(long id, String sceneCode, Long beforeDefinitionId, String beforeWorkflowCode,
                                     String beforeWorkflowName, Integer beforeWorkflowVersion,
                                     Long afterDefinitionId, String afterWorkflowCode, String afterWorkflowName,
                                     Integer afterWorkflowVersion, String reason, long operatorId,
                                     String operatorName, LocalDateTime occurredAt) {}
    public record ResolvedBinding(Scene scene, long workflowDefinitionId, String workflowCode,
                                  String workflowName, int workflowVersion) {}
}

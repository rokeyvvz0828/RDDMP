package com.ccb.release.integration;

import com.ccb.release.workflow.persistence.ReleaseWorkflowBindingStore;
import com.ccb.workflow.integration.WorkflowDefinitionReference;
import com.ccb.workflow.integration.WorkflowDefinitionReferenceProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReleaseWorkflowDefinitionReferenceProvider implements WorkflowDefinitionReferenceProvider {
    private final ReleaseWorkflowBindingStore store;
    public ReleaseWorkflowDefinitionReferenceProvider(ReleaseWorkflowBindingStore store) { this.store = store; }

    @Override
    public List<WorkflowDefinitionReference> activeReferences(long tenantId, long definitionId) {
        return store.references(tenantId, definitionId).stream().map(binding -> new WorkflowDefinitionReference(
                "release", "release_workflow_scene", binding.projectRef() + ":" + binding.scene().name(),
                binding.projectName() + " / " + binding.scene().label())).toList();
    }
}

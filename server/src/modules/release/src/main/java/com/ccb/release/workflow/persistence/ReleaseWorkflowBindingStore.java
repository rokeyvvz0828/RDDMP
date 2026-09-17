package com.ccb.release.workflow.persistence;

import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Binding;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.BindingHistoryView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReleaseWorkflowBindingStore {
    private final ReleaseWorkflowBindingMapper mapper;

    public ReleaseWorkflowBindingStore(ReleaseWorkflowBindingMapper mapper) { this.mapper = mapper; }
    public List<Binding> findProject(long tenantId, String projectRef) { return mapper.findProject(p("tenantId", tenantId, "projectRef", projectRef)); }
    public Optional<Binding> find(long tenantId, String projectRef, Scene scene, boolean forUpdate) { return Optional.ofNullable(forUpdate ? mapper.findForUpdate(p("tenantId", tenantId, "projectRef", projectRef, "scene", scene.name())) : mapper.find(p("tenantId", tenantId, "projectRef", projectRef, "scene", scene.name()))); }
    public void insert(Binding binding) { mapper.insert(bindingParams(binding, "rowVersion", binding.rowVersion(), "createdBy", binding.createdBy(), "updatedBy", binding.updatedBy())); }
    public boolean update(Binding binding, long expectedVersion) { return mapper.update(bindingParams(binding, "expectedVersion", expectedVersion, "updatedBy", binding.updatedBy())) == 1; }
    public void appendHistory(long historyId, Binding before, Binding after, String reason, long operatorId, String operatorName) { mapper.appendHistory(p("historyId", historyId, "tenantId", after.tenantId(), "bindingId", after.id(), "projectRef", after.projectRef(), "projectName", after.projectName(), "scene", after.scene().name(), "beforeDefinitionId", before == null ? null : before.workflowDefinitionId(), "beforeWorkflowCode", before == null ? null : before.workflowCode(), "beforeWorkflowName", before == null ? null : before.workflowName(), "beforeWorkflowVersion", before == null ? null : before.workflowVersion(), "afterDefinitionId", after.workflowDefinitionId(), "afterWorkflowCode", after.workflowCode(), "afterWorkflowName", after.workflowName(), "afterWorkflowVersion", after.workflowVersion(), "reason", reason, "operatorId", operatorId, "operatorName", operatorName)); }
    public List<BindingHistoryView> history(long tenantId, String projectRef, Scene scene) { return mapper.history(p("tenantId", tenantId, "projectRef", projectRef, "scene", scene.name())); }
    public List<Binding> references(long tenantId, long definitionId) { return mapper.references(p("tenantId", tenantId, "definitionId", definitionId)); }

    private static Map<String, Object> bindingParams(Binding binding, Object... extra) { Map<String, Object> values = p("id", binding.id(), "tenantId", binding.tenantId(), "projectRef", binding.projectRef(), "projectName", binding.projectName(), "scene", binding.scene().name(), "workflowDefinitionId", binding.workflowDefinitionId(), "workflowCode", binding.workflowCode(), "workflowName", binding.workflowName(), "workflowVersion", binding.workflowVersion()); for (int i = 0; i < extra.length; i += 2) values.put((String) extra[i], extra[i + 1]); return values; }
    private static Map<String, Object> p(Object... values) { Map<String, Object> result = new HashMap<>(); for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); return result; }
}

package com.ccb.release.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Binding;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.BindingHistoryView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.BindingView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.PublishedDefinitionView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.ResolvedBinding;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.UpdateBindingRequest;
import com.ccb.release.workflow.persistence.ReleaseWorkflowBindingStore;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowDefinitionCatalog;
import com.ccb.workflow.integration.WorkflowDefinitionSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReleaseWorkflowBindingService {
    private final ReleaseWorkflowBindingStore store;
    private final WorkflowDefinitionCatalog catalog;

    public ReleaseWorkflowBindingService(ReleaseWorkflowBindingStore store, WorkflowDefinitionCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    public List<BindingView> list(String projectRef, AuthUser user) {
        String project = required(projectRef, "项目标识", 64);
        Map<Scene, Binding> bindings = store.findProject(user.tenantId(), project).stream()
                .collect(Collectors.toMap(Binding::scene, Function.identity()));
        Map<Long, WorkflowDefinitionSummary> published = publishedMap(user);
        return Arrays.stream(Scene.values()).map(scene -> view(project, scene, bindings.get(scene), published)).toList();
    }

    public List<PublishedDefinitionView> publishedDefinitions(AuthUser user) {
        return catalog.publishedDefinitions(user).stream().map(value -> new PublishedDefinitionView(
                value.definitionId(), value.code(), value.name(), value.versionNo())).toList();
    }

    @Transactional
    public BindingView update(Scene scene, UpdateBindingRequest request, AuthUser user) {
        if (request == null) throw badRequest("审批流程配置不能为空");
        String projectRef = required(request.projectRef(), "项目标识", 64);
        String projectName = required(request.projectName(), "项目名称", 128);
        String reason = required(request.reason(), "修改原因", 500);
        if (request.rowVersion() == null || request.rowVersion() < 0) throw badRequest("rowVersion 不能为空");

        WorkflowDefinitionSummary definition = request.workflowDefinitionId() == null ? null
                : catalog.requirePublished(request.workflowDefinitionId(), user);
        Binding current = store.find(user.tenantId(), projectRef, scene, true).orElse(null);
        if (current == null && request.rowVersion() != 0) throw conflict("审批流程配置已变化，请刷新后重试");
        if (current != null && current.rowVersion() != request.rowVersion()) throw conflict("审批流程配置已被其他人修改，请刷新后重试");

        Binding after = new Binding(current == null ? nextId() : current.id(), user.tenantId(), projectRef, projectName, scene,
                definition == null ? null : definition.definitionId(), definition == null ? null : definition.code(),
                definition == null ? null : definition.name(), definition == null ? null : definition.versionNo(),
                current == null ? 0 : current.rowVersion() + 1, current == null ? user.id() : current.createdBy(),
                user.id(), current == null ? null : current.createdAt(), null);
        if (sameBinding(current, after)) return view(projectRef, scene, current, publishedMap(user));
        if (current == null) store.insert(after);
        else if (!store.update(after, current.rowVersion())) throw conflict("审批流程配置已被其他人修改，请刷新后重试");
        store.appendHistory(nextId(), current, after, reason, user.id(), user.displayName());
        Binding saved = store.find(user.tenantId(), projectRef, scene, false).orElse(after);
        return view(projectRef, scene, saved, publishedMap(user));
    }

    public List<BindingHistoryView> history(String projectRef, Scene scene, AuthUser user) {
        return store.history(user.tenantId(), required(projectRef, "项目标识", 64), scene);
    }

    public ResolvedBinding resolve(String projectRef, Scene scene, AuthUser user) {
        String project = required(projectRef, "项目标识", 64);
        Binding binding = store.find(user.tenantId(), project, scene, false)
                .orElseThrow(() -> conflict(scene.label() + "未配置审批流程"));
        if (binding.workflowDefinitionId() == null) throw conflict(scene.label() + "未配置审批流程");
        WorkflowDefinitionSummary definition;
        try {
            definition = catalog.requirePublished(binding.workflowDefinitionId(), user);
        } catch (BusinessException error) {
            throw conflict(scene.label() + "绑定的审批流程已失效，请联系配置管理员");
        }
        return new ResolvedBinding(scene, definition.definitionId(), definition.code(), definition.name(), definition.versionNo());
    }

    private BindingView view(String projectRef, Scene scene, Binding binding, Map<Long, WorkflowDefinitionSummary> published) {
        if (binding == null) return new BindingView(scene.name(), scene.label(), projectRef, null, null, null, null, null,
                false, false, "未配置审批流程", 0, null);
        boolean configured = binding.workflowDefinitionId() != null;
        boolean valid = configured && published.containsKey(binding.workflowDefinitionId());
        String invalidReason = !configured ? "未配置审批流程" : valid ? null : "绑定流程已取消发布、归档或不存在";
        return new BindingView(scene.name(), scene.label(), projectRef, binding.projectName(), binding.workflowDefinitionId(),
                binding.workflowCode(), binding.workflowName(), binding.workflowVersion(), configured, valid,
                invalidReason, binding.rowVersion(), binding.updatedAt());
    }

    private Map<Long, WorkflowDefinitionSummary> publishedMap(AuthUser user) {
        return catalog.publishedDefinitions(user).stream()
                .collect(Collectors.toMap(WorkflowDefinitionSummary::definitionId, Function.identity()));
    }

    private boolean sameBinding(Binding before, Binding after) {
        return before != null && Objects.equals(before.workflowDefinitionId(), after.workflowDefinitionId())
                && Objects.equals(before.projectName(), after.projectName());
    }
    private String required(String value, String label, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw badRequest(label + "不能为空");
        if (normalized.length() > max) throw badRequest(label + "长度不能超过 " + max);
        return normalized;
    }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

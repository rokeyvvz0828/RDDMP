package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowDefinitionCatalog;
import com.ccb.workflow.integration.WorkflowDefinitionSummary;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import com.ccb.workflow.integration.WorkflowProgress;
import com.ccb.workflow.integration.WorkflowStartCommand;
import com.ccb.workflow.integration.WorkflowStartDefinitionCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import com.ccb.workflow.integration.WorkflowTerminateCommand;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class WorkflowBusinessIntegrationService implements WorkflowBusinessGateway, WorkflowDefinitionCatalog {
    private static final Pattern DIGEST = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final WorkflowBusinessIntegrationRepository repository;
    private final WorkflowService workflowService;
    private final WorkflowLifecycleEventService lifecycleEvents;
    private WorkflowProjectAccessGateway projectAccess;

    public WorkflowBusinessIntegrationService(WorkflowBusinessIntegrationRepository repository, WorkflowService workflowService,
                                              WorkflowLifecycleEventService lifecycleEvents) {
        this.repository = repository;
        this.workflowService = workflowService;
        this.lifecycleEvents = lifecycleEvents;
    }

    @Autowired(required = false)
    void setProjectAccess(WorkflowProjectAccessGateway projectAccess) {
        this.projectAccess = projectAccess;
    }

    @Override
    @Transactional
    public WorkflowStartResult startByCode(WorkflowStartCommand command, AuthUser operator) {
        if (command == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程启动参数不能为空");
        String definitionCode = requireText(command.definitionCode(), "流程编码", 64);
        ResolvedProjectContext resolved = resolveProject(validate(command.context()), operator);
        Map<String, Object> definition = resolvePublishedByCode(definitionCode, resolved, operator);
        long definitionId = ((Number) definition.get("id")).longValue();
        return startResolved(definitionId, resolved.context(), command.variables(), operator);
    }

    private Map<String, Object> resolvePublishedByCode(String definitionCode, ResolvedProjectContext resolved,
                                                       AuthUser operator) {
        List<Map<String, Object>> definitions = repository.publishedByCode(operator.tenantId(), definitionCode, resolved.projectId());
        if (definitions.size() != 1) {
            String target = resolved.projectId() == null ? "当前业务" : "项目【" + resolved.context().projectName() + "】";
            throw new BusinessException(ErrorCode.CONFLICT, target + "未配置已发布流程【" + definitionCode + "】");
        }
        return definitions.get(0);
    }

    @Override
    @Transactional
    public WorkflowStartResult startByDefinitionId(WorkflowStartDefinitionCommand command, AuthUser operator) {
        if (command == null || command.definitionId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "流程定义不能为空");
        }
        ResolvedProjectContext resolved = resolveProject(validate(command.context()), operator);
        WorkflowDefinitionSummary requested = requirePublished(command.definitionId(), operator);
        Map<String, Object> definition = resolvePublishedByCode(requested.code(), resolved, operator);
        return startResolved(((Number) definition.get("id")).longValue(), resolved.context(), command.variables(), operator);
    }

    @Override
    public List<WorkflowDefinitionSummary> publishedDefinitions(AuthUser operator) {
        java.util.Set<Long> projectIds = projectAccess == null ? java.util.Set.of()
                : new java.util.HashSet<>(projectAccess.accessibleProjectIds(operator));
        return repository.publishedDefinitions(operator.tenantId()).stream()
                .filter(row -> "PLATFORM".equals(String.valueOf(row.get("scope_type")))
                        || row.get("project_id") instanceof Number number && projectIds.contains(number.longValue()))
                .map(this::definitionSummary).toList();
    }

    @Override
    public WorkflowDefinitionSummary requirePublished(long definitionId, AuthUser operator) {
        if (definitionId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程定义不能为空");
        List<Map<String, Object>> definitions = repository.publishedById(operator.tenantId(), definitionId);
        if (definitions.size() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "流程定义未发布、未部署或不存在");
        }
        Map<String, Object> scoped = definitions.get(0);
        if ("PROJECT".equals(String.valueOf(scoped.get("scope_type")))) {
            if (!(scoped.get("project_id") instanceof Number number) || projectAccess == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "项目流程缺少有效项目归属");
            }
            projectAccess.requireAccessible(number.longValue(), operator);
        }
        return definitionSummary(scoped);
    }

    private WorkflowDefinitionSummary definitionSummary(Map<String, Object> row) {
        return new WorkflowDefinitionSummary(((Number) row.get("id")).longValue(), String.valueOf(row.get("code")),
                String.valueOf(row.get("name")), ((Number) row.get("current_version")).intValue());
    }

    private WorkflowStartResult startResolved(long definitionId, WorkflowBusinessContext context,
                                               Map<String, Object> variables, AuthUser operator) {
        Map<String, Object> started = workflowService.start(definitionId, context.businessKey(),
                variables == null ? Map.of() : variables, context, operator);
        long instanceId = ((Number) started.get("id")).longValue();
        lifecycleEvents.emit(instanceId, WorkflowLifecycleEventType.STARTED, operator);
        return new WorkflowStartResult(instanceId, definitionId, ((Number) started.get("version_no")).intValue(),
                String.valueOf(started.get("status")), normalized(context));
    }

    @Override
    @Transactional
    public void terminate(WorkflowTerminateCommand command, AuthUser operator) {
        if (command == null || command.instanceId() <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不能为空");
        String businessType = requireText(command.businessType(), "业务类型", 64);
        String businessKey = requireText(command.businessKey(), "业务单号", 128);
        long count = repository.countMatchingInstance(operator.tenantId(), command.instanceId(), businessType, businessKey, command.businessRound());
        if (count != 1) throw new BusinessException(ErrorCode.CONFLICT, "流程实例与业务上下文不匹配");
        workflowService.terminate(command.instanceId(), requireText(command.reason(), "终止原因", 500), operator);
    }

    @Override
    public WorkflowProgress progress(long instanceId, AuthUser operator) {
        workflowService.requireInstanceAccessible(instanceId, operator);
        List<Map<String, Object>> rows = repository.instanceProgress(operator.tenantId(), instanceId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在");
        Map<String, Object> row = rows.get(0);
        if (row.get("business_type") == null) throw new BusinessException(ErrorCode.CONFLICT, "存量流程实例没有业务接入上下文");
        WorkflowBusinessContext context = new WorkflowBusinessContext(value(row.get("business_module_code")), value(row.get("business_module_name")), String.valueOf(row.get("business_type")), String.valueOf(row.get("business_key")),
                String.valueOf(row.get("business_title")), ((Number) row.get("business_round")).intValue(), value(row.get("project_ref")),
                value(row.get("project_name")), String.valueOf(row.get("action_path")), String.valueOf(row.get("data_digest")));
        Object created = row.get("created_at");
        LocalDateTime createdAt = created instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : null;
        return new WorkflowProgress(instanceId, ((Number) row.get("definition_id")).longValue(), ((Number) row.get("version_no")).intValue(),
                String.valueOf(row.get("status")), context, createdAt);
    }

    private WorkflowBusinessContext validate(WorkflowBusinessContext context) {
        if (context == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "业务上下文不能为空");
        String moduleCode = requireText(context.moduleCode(), "业务板块编码", 64);
        if (!moduleCode.matches("[a-z][a-z0-9_-]{0,63}")) throw new BusinessException(ErrorCode.BAD_REQUEST, "业务板块编码格式不正确");
        String moduleName = requireText(context.moduleName(), "业务板块名称", 128);
        String businessType = requireText(context.businessType(), "业务类型", 64);
        String businessKey = requireText(context.businessKey(), "业务单号", 128);
        String businessTitle = requireText(context.businessTitle(), "业务标题", 200);
        if (context.businessRound() <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "业务轮次必须大于0");
        String actionPath = requireText(context.actionPath(), "业务详情路由", 512);
        if (!actionPath.startsWith("/") || actionPath.startsWith("//") || actionPath.contains("\\") || actionPath.contains("\n") || actionPath.contains("\r") || actionPath.contains("://")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务详情路由必须是站内绝对路径");
        }
        String digest = requireText(context.dataDigest(), "业务数据摘要", 64);
        if (!DIGEST.matcher(digest).matches()) throw new BusinessException(ErrorCode.BAD_REQUEST, "业务数据摘要必须是SHA-256十六进制字符串");
        String projectRef = optional(context.projectRef(), "项目标识", 64);
        String projectName = optional(context.projectName(), "项目名称", 128);
        return new WorkflowBusinessContext(moduleCode, moduleName, businessType, businessKey, businessTitle, context.businessRound(), projectRef, projectName, actionPath, digest.toLowerCase());
    }

    private WorkflowBusinessContext normalized(WorkflowBusinessContext context) {
        return validate(context);
    }

    private ResolvedProjectContext resolveProject(WorkflowBusinessContext context, AuthUser operator) {
        if (context.projectRef() == null || context.projectRef().isBlank()) {
            return new ResolvedProjectContext(null, context);
        }
        if (projectAccess == null) throw new BusinessException(ErrorCode.CONFLICT, "项目工作流能力尚未就绪");
        WorkflowProjectAccessGateway.ProjectScope project = projectAccess.requireAccessible(context.projectRef(), operator);
        WorkflowBusinessContext normalized = new WorkflowBusinessContext(context.moduleCode(), context.moduleName(),
                context.businessType(), context.businessKey(), context.businessTitle(), context.businessRound(),
                project.ref(), project.name(), context.actionPath(), context.dataDigest());
        return new ResolvedProjectContext(project.id(), normalized);
    }

    private record ResolvedProjectContext(Long projectId, WorkflowBusinessContext context) {
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能为空");
        if (normalized.length() > maxLength) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "长度不能超过" + maxLength);
        return normalized;
    }

    private String optional(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, label, maxLength);
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.workflow.model.WorkflowNodeModel;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.ccb.workflow.integration.WorkflowProjectMember;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowAssigneeResolver {
    private final JdbcTemplate jdbc;
    private WorkflowProjectAccessGateway projectAccess;

    public WorkflowAssigneeResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Autowired(required = false)
    void setProjectAccess(WorkflowProjectAccessGateway projectAccess) {
        this.projectAccess = projectAccess;
    }

    public ProcessVariables prepareProcessVariables(WorkflowDefinitionValidator.WorkflowGraph graph,
                                                    long tenantId, long starterId, Map<String, Object> input) {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        if (input != null) variables.putAll(input);
        variables.put("starterId", starterId);
        for (WorkflowDefinitionValidator.WorkflowNode node : graph.nodes()) {
            if (!"APPROVAL".equals(node.type())) continue;
            JsonNode config = node.config();
            String type = config.path("assigneeType").asText("").toUpperCase(Locale.ROOT);
            if ("ORG_OWNER".equals(type)) {
                variables.put("orgOwnerUserId_" + node.id(), resolveOrgOwner(starterId, tenantId).id());
            } else if ("FORM_FIELD".equals(type)) {
                requireUserId(config.path("fieldName").asText(""), variables, node.id(), "表单字段");
            } else if ("EXPRESSION".equals(type)) {
                requireUserId(config.path("expression").asText(""), variables, node.id(), "表达式");
            }
        }
        return new ProcessVariables(variables);
    }

    public List<ResolvedAssignee> resolveNode(WorkflowNodeModel node, long tenantId, long starterId,
                                              Map<String, Object> variables) {
        return resolveNode(node, tenantId, starterId, null, null, variables);
    }

    public List<ResolvedAssignee> resolveNode(WorkflowNodeModel node, long tenantId, long starterId,
                                              Long projectId, AuthUser actor, Map<String, Object> variables) {
        String type = node.config().path("assigneeType").asText("").toUpperCase(Locale.ROOT);
        List<Long> ids = switch (type) {
            case "USER" -> ids(node.config().path("assigneeIds"));
            case "ROLE" -> usersForRoles(ids(node.config().path("assigneeIds")), tenantId);
            case "PROJECT_MEMBER" -> projectMembers(projectId, ids(node.config().path("assigneeIds")), actor);
            case "PROJECT_ROLE" -> projectRoleMembers(projectId, ids(node.config().path("assigneeIds")), actor);
            case "STARTER" -> List.of(starterId);
            case "ORG_OWNER" -> List.of(resolveOrgOwner(starterId, tenantId).id());
            case "FORM_FIELD" -> List.of(resolveVariableUserId(node.config().path("fieldName").asText(""), variables, node.id(), "表单字段"));
            case "EXPRESSION" -> List.of(resolveVariableUserId(node.config().path("expression").asText(""), variables, node.id(), "表达式"));
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的审批人类型: " + type);
        };
        List<ResolvedAssignee> result = activeUsers(ids, tenantId);
        if (result.isEmpty()) {
            String action = node.config().path("emptyAssigneeAction").asText("ERROR").toUpperCase(Locale.ROOT);
            if ("WAIT".equals(action)) return List.of();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批节点“" + node.label() + "”没有可用审批人");
        }
        if (Set.of("USER", "PROJECT_MEMBER").contains(type) && result.size() != new LinkedHashSet<>(ids).size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批节点“" + node.label() + "”包含不存在或已停用的用户");
        }
        return result;
    }

    private List<Long> projectMembers(Long projectId, List<Long> ids, AuthUser actor) {
        WorkflowProjectAccessGateway gateway = requireProjectGateway(projectId, actor);
        gateway.requireMembers(projectId, ids, actor);
        return ids;
    }

    private List<Long> projectRoleMembers(Long projectId, List<Long> roleIds, AuthUser actor) {
        return requireProjectGateway(projectId, actor).membersForRoles(projectId, roleIds, actor).stream()
                .map(WorkflowProjectMember::userId).toList();
    }

    private WorkflowProjectAccessGateway requireProjectGateway(Long projectId, AuthUser actor) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目流程实例缺少项目上下文");
        }
        if (actor == null || projectAccess == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目工作流人员目录尚未就绪");
        }
        projectAccess.requireAccessible(projectId, actor);
        return projectAccess;
    }

    private List<Long> usersForRoles(List<Long> roleIds, long tenantId) {
        if (roleIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(roleIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(roleIds);
        return jdbc.queryForList("SELECT DISTINCT u.id FROM sys_user u " +
                        "JOIN sys_user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id " +
                        "JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id " +
                        "WHERE u.tenant_id = ? AND u.deleted = 0 AND u.status = 1 AND r.deleted = 0 AND r.status = 1 " +
                        "AND ur.role_id IN (" + placeholders + ") ORDER BY u.id", Long.class, args.toArray());
    }

    private List<ResolvedAssignee> activeUsers(List<Long> ids, long tenantId) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        return jdbc.query("SELECT id, display_name FROM sys_user WHERE tenant_id = ? AND deleted = 0 AND status = 1 AND id IN (" + placeholders + ") ORDER BY id",
                (rs, rowNum) -> new ResolvedAssignee(rs.getLong("id"), rs.getString("display_name")), args.toArray());
    }

    private OrganizationOwner resolveOrgOwner(long starterId, long tenantId) {
        List<OrganizationOwner> owners = jdbc.query("SELECT o.leader_id, u.display_name FROM sys_user starter " +
                        "JOIN sys_org o ON o.id = starter.org_id AND o.tenant_id = starter.tenant_id AND o.deleted = 0 " +
                        "JOIN sys_user u ON u.id = o.leader_id AND u.tenant_id = o.tenant_id AND u.deleted = 0 AND u.status = 1 " +
                        "WHERE starter.id = ? AND starter.tenant_id = ? AND starter.deleted = 0",
                (rs, rowNum) -> new OrganizationOwner(rs.getLong("leader_id"), rs.getString("display_name")), starterId, tenantId);
        if (owners.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "发起人所属组织未配置有效负责人");
        return owners.get(0);
    }

    private void requireUserId(String expression, Map<String, Object> variables, String nodeId, String source) {
        resolveVariableUserId(expression, variables, nodeId, source);
    }

    private long resolveVariableUserId(String expression, Map<String, Object> variables, String nodeId, String source) {
        String name = expression == null ? "" : expression.trim();
        if (name.startsWith("${") && name.endsWith("}")) name = name.substring(2, name.length() - 1).trim();
        Object value = variables.get(name);
        long userId = numericId(value);
        if (userId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "审批节点“" + nodeId + "”的" + source + "未提供有效用户");
        return userId;
    }

    private long numericId(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) { return 0; }
    }

    private List<Long> ids(JsonNode node) {
        if (!node.isArray()) return List.of();
        Set<Long> result = new LinkedHashSet<>();
        for (JsonNode item : node) if (item.canConvertToLong() && item.asLong() > 0) result.add(item.asLong());
        return List.copyOf(result);
    }

    public record ResolvedAssignee(long id, String name) {}
    public record ProcessVariables(Map<String, Object> values) {}
    private record OrganizationOwner(long id, String name) {}
}

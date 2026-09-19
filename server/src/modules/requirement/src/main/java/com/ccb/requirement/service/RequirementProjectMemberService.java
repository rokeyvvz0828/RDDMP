package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.system.capability.ProjectMemberReference;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 需求管理流转/审批人员目录。
 * <p>唯一来源为「项目管理 → 项目组织架构」的有效成员（pm_project_member），
 * 通过 platform/system 公开只读契约读取，需求模块不直接查询项目成员表。
 */
@Service
public class RequirementProjectMemberService {
    private static final String NOT_MEMBER_MESSAGE =
            "所选人员不是当前项目组织架构的有效成员，请先在项目管理中添加项目成员";

    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccess;
    private final ProjectMemberReferenceQuery memberReference;

    public RequirementProjectMemberService(JdbcTemplate jdbc, ProjectAccessService projectAccess,
                                           ProjectMemberReferenceQuery memberReference) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
        this.memberReference = memberReference;
    }

    /** 按项目编码解析当前用户可访问的项目；编码缺失或项目不存在时给出明确提示。 */
    public ProjectAccess requireProject(String projectRef, AuthUser user) {
        if (projectRef == null || projectRef.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未确定当前项目，无法读取项目组织架构人员");
        }
        return projectAccess.requireAccessible(projectRef, user);
    }

    /** 当前项目组织架构的有效成员选项，字段与既有前端人员下拉一致。 */
    public List<Map<String, Object>> activeMembers(String projectRef, String keyword, AuthUser user) {
        ProjectAccess project = requireProject(projectRef, user);
        String filter = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectMemberReference member : memberReference.findActiveMembers(user, project.id())) {
            String username = member.username() == null ? "" : member.username();
            String displayName = member.displayName() == null ? "" : member.displayName();
            if (!filter.isEmpty()
                    && !username.toLowerCase(Locale.ROOT).contains(filter)
                    && !displayName.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", member.userId());
            row.put("username", member.username());
            row.put("display_name", member.displayName());
            result.add(row);
        }
        return result;
    }

    /** 强校验：被选人员必须全部是当前项目组织架构的有效成员。 */
    public void requireActiveMembers(String projectRef, Collection<Long> userIds, AuthUser user) {
        Set<Long> selected = positiveIds(userIds);
        if (selected.isEmpty()) {
            return;
        }
        ProjectAccess project = requireProject(projectRef, user);
        Set<Long> allowed = new LinkedHashSet<>();
        for (ProjectMemberReference member : memberReference.findActiveMembers(user, project.id())) {
            allowed.add(member.userId());
        }
        for (Long userId : selected) {
            if (!allowed.contains(userId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, NOT_MEMBER_MESSAGE);
            }
        }
    }

    /**
     * 单人流转/指派校验：确认目标用户是当前项目组织架构的有效成员，并返回其展示名。
     * 非成员、已停用或已删除用户一律拒绝，避免把记录流转给项目之外的人。
     */
    public String requireMemberName(String projectRef, long userId, AuthUser user) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择流转处理人");
        }
        ProjectAccess project = requireProject(projectRef, user);
        for (ProjectMemberReference member : memberReference.findActiveMembers(user, project.id())) {
            if (member.userId() == userId) {
                return member.displayName() == null ? member.username() : member.displayName();
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, NOT_MEMBER_MESSAGE);
    }

    /** 需求项目主键（req_project.id）对应的项目编码。 */
    public String projectCodeOfRequirementProject(long requirementProjectId, AuthUser user) {
        if (requirementProjectId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该记录未关联项目，无法确定项目组织架构人员");
        }
        // 需求数据统一挂项目管理主键（pm_project.id）
        List<String> platformCodes = jdbc.queryForList(
                "SELECT project_code FROM pm_project WHERE tenant_id = ? AND id = ? AND deleted = 0",
                String.class, user.tenantId(), requirementProjectId);
        if (!platformCodes.isEmpty() && platformCodes.get(0) != null && !platformCodes.get(0).isBlank()) {
            return platformCodes.get(0);
        }
        // 兼容历史台账（req_project.id）口径
        List<String> codes = jdbc.queryForList(
                "SELECT project_code FROM req_project WHERE tenant_id = ? AND id = ? AND deleted = 0",
                String.class, user.tenantId(), requirementProjectId);
        if (!codes.isEmpty() && codes.get(0) != null && !codes.get(0).isBlank()) {
            return codes.get(0);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "该记录关联的项目不存在，无法确定项目组织架构人员");
    }

    /** 新建项目差异所属项目的编码与名称（project_id 为项目管理主键）。 */
    public Map<String, Object> requireRequirementProjectContext(long requirementProjectId, AuthUser user) {
        return requireProjectContext(requirementProjectId, user);
    }

    /** 按项目管理主键解析项目编码与名称；台账口径仅作历史兜底。 */
    public Map<String, Object> requireProjectContext(long projectId, AuthUser user) {
        String code = projectCodeOfRequirementProject(projectId, user);
        String name = projectName(projectId, user, "pm_project");
        if (name.isBlank()) {
            name = projectName(projectId, user, "req_project");
        }
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("project_code", code);
        project.put("project_name", name);
        return project;
    }

    private String projectName(long projectId, AuthUser user, String table) {
        List<String> names = jdbc.queryForList(
                "SELECT COALESCE(project_name, '') FROM " + table
                        + " WHERE tenant_id = ? AND id = ? AND deleted = 0",
                String.class, user.tenantId(), projectId);
        return names.isEmpty() || names.get(0) == null ? "" : names.get(0);
    }

    /** 存量需求所属项目的编码与名称（需求数据统一挂项目管理主键 pm_project.id）。 */
    public Map<String, Object> requireLegacyProjectContext(long requirementId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT project_id FROM req_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), requirementId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        Object projectId = rows.get(0).get("project_id");
        if (projectId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该存量需求未关联项目，无法确定项目组织架构人员");
        }
        return requireProjectContext(((Number) projectId).longValue(), user);
    }

    private Set<Long> positiveIds(Collection<Long> userIds) {
        Set<Long> result = new LinkedHashSet<>();
        if (userIds == null) {
            return result;
        }
        for (Long id : userIds) {
            if (id != null && id > 0) {
                result.add(id);
            }
        }
        return result;
    }
}

package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementValues;

/** 新建项目主数据与项目组成员数据范围。 */
@Service
public class RequirementProjectService {
    private static final List<String> PROJECT_FIELDS = List.of(
            "project_code", "project_name", "project_type", "start_time", "status", "description");

    private final RequirementProjectRepository repository;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;

    public RequirementProjectService(RequirementProjectRepository repository, RequirementChangeLogService changeLog,
                                     RequirementSecurityService security) {
        this.repository = repository;
        this.changeLog = changeLog;
        this.security = security;
    }

    public List<Map<String, Object>> list(String keyword, AuthUser user) {
        return repository.list(user.tenantId(), keyword);
    }

    public Map<String, Object> get(long id, AuthUser user) {
        security.requireProjectVisible(user, id);
        Map<String, Object> row = repository.find(user.tenantId(), id);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在");
        }
        return row;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        String code = RequirementValues.requireText(body, "project_code", "项目编码不能为空");
        RequirementValues.requireText(body, "project_name", "项目名称不能为空");
        if (repository.duplicateProjectCode(user.tenantId(), code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目编码已存在：" + code);
        }
        RequirementValues.requireOption("projectTypes", RequirementValues.text(body, "project_type"));
        RequirementValues.requireOption("projectStatuses", RequirementValues.text(body, "status"));
        long id = RequirementIds.next();
        Map<String, Object> values = normalized(body);
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.putIfAbsent("project_type", "0~1 新建");
        values.putIfAbsent("status", "进行中");
        values.put("created_by", user.id());
        values.put("deleted", 0);
        repository.insertProject(values);
        changeLog.recordCreate("PROJECT", id, values, user, "ONLINE");
        return get(id, user);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = get(id, user);
        RequirementValues.requireOption("projectTypes", RequirementValues.text(body, "project_type"));
        RequirementValues.requireOption("projectStatuses", RequirementValues.text(body, "status"));
        Map<String, Object> changes = normalized(body);
        changes.remove("project_code");
        if (changes.isEmpty()) {
            return before;
        }
        changes.put("updated_by", user.id());
        changes.put("id", id);
        changes.put("tenant_id", user.tenantId());
        repository.updateProject(changes);
        Map<String, Object> after = get(id, user);
        changeLog.recordFields("PROJECT", id, "UPDATE", before, after, user, "ONLINE");
        return after;
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = get(id, user);
        if (repository.hasDifferences(user.tenantId(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目下存在差异数据，不能删除");
        }
        repository.deleteProject(user.tenantId(), id, user.id());
        changeLog.record("PROJECT", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    public List<Map<String, Object>> members(long projectId, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        return repository.members(user.tenantId(), projectId);
    }

    @Transactional
    public Map<String, Object> addMember(long projectId, Map<String, Object> body, AuthUser user) {
        if (!security.isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅统筹/管理员可维护项目成员");
        }
        get(projectId, user);
        long userId = RequirementValues.intOf(body.get("userId"), 0);
        String role = RequirementValues.text(body, "memberRole");
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员用户不能为空");
        }
        if (!repository.userExists(user.tenantId(), userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员用户不存在");
        }
        if (repository.memberExists(user.tenantId(), projectId, userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户已是项目成员");
        }
        long id = RequirementIds.next();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("project_id", projectId);
        values.put("user_id", userId);
        values.put("member_role", role == null || role.isBlank() ? "MEMBER" : role);
        values.put("created_by", user.id());
        values.put("deleted", 0);
        repository.insertMember(values);
        return repository.findMember(user.tenantId(), id);
    }

    @Transactional
    public void removeMember(long memberId, AuthUser user) {
        if (!security.isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅统筹/管理员可维护项目成员");
        }
        repository.deleteMember(user.tenantId(), memberId);
    }

    private Map<String, Object> normalized(Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : PROJECT_FIELDS) {
            Object value = body.get(field);
            if (value == null) {
                continue;
            }
            if ("start_time".equals(field)) {
                values.put(field, RequirementValues.date(value));
            } else {
                values.put(field, value);
            }
        }
        return values;
    }
}

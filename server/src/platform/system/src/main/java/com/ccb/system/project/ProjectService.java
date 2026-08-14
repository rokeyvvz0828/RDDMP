package com.ccb.system.project;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** 项目域服务：权限和项目可见范围都在服务端执行。 */
@Service
public class ProjectService {
    private static final Set<String> PROJECT_STATUSES = Set.of("PLANNING", "RUNNING", "COMPLETED", "SUSPENDED");
    private static final Set<String> PLAN_STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "BLOCKED");
    private static final Set<String> PLAN_PARTY_TYPES = Set.of("LEAD", "COOPERATING");
    private static final Set<String> PLAN_GROUP_COLOR_TOKENS = Set.of("brand", "accent", "success", "warning", "danger", "muted");
    private static final String DEFAULT_PLAN_GROUP_COLOR_TOKEN = "brand";
    private static final String DEFAULT_PLAN_NUMBER_RULE = "{PROJECT_CODE}-P{SEQ:3}";
    private static final String DEFAULT_CHILD_PLAN_NUMBER_RULE = "{PARENT_CODE}-S{SEQ:3}";
    private static final Pattern PLAN_RULE_TOKEN = Pattern.compile("\\{([A-Z_]+)(?::(\\d+))?}");

    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;

    public ProjectService(JdbcTemplate jdbc, MinioStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public List<Map<String, Object>> workbench(AuthUser user) {
        requireAction("project", "read", user);
        String scope = projectScope(user);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, project_code, project_name, description, status, phase, plan_number_rule, child_plan_number_rule, next_plan_sequence, owner_id, planned_start_date, planned_end_date, actual_end_date, created_at, updated_at FROM pm_project WHERE tenant_id = ? AND deleted = 0 AND " + scope + " ORDER BY updated_at DESC, id DESC", user.tenantId());
        rows.forEach(row -> decorateProject(row, user.tenantId()));
        return rows;
    }

    public Map<String, Object> detail(long projectId, AuthUser user) {
        requireAction("project", "read", user);
        requireProjectAccess(projectId, user, false);
        Map<String, Object> project = project(projectId, user.tenantId());
        decorateProject(project, user.tenantId());
        project.put("plans", plans(projectId, user));
        project.put("plan_groups", planGroups(projectId, user));
        project.put("members", members(projectId, user));
        project.put("roles", roles(projectId, user));
        return project;
    }

    @Transactional
    public Map<String, Object> updateSettings(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("project", "update", user);
        requireProjectAccess(projectId, user, true);
        String rule = optional(input, "plan_number_rule", DEFAULT_PLAN_NUMBER_RULE);
        validatePlanNumberRule(rule);
        String childRule = optional(input, "child_plan_number_rule", DEFAULT_CHILD_PLAN_NUMBER_RULE);
        validateChildPlanNumberRule(childRule);
        jdbc.update("UPDATE pm_project SET plan_number_rule = ?, child_plan_number_rule = ? WHERE id = ? AND tenant_id = ? AND deleted = 0", rule, childRule, projectId, user.tenantId());
        audit(user, "project:settings:update", projectId);
        return detail(projectId, user);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> input, AuthUser user) {
        requireAction("project", "create", user);
        String code = required(input, "project_code", "项目编号", 64);
        String name = required(input, "project_name", "项目名称", 128);
        long ownerId = longValue(input.get("owner_id"), user.id());
        validateUser(ownerId, user.tenantId());
        String status = optional(input, "status", "PLANNING");
        validateStatus(status, PROJECT_STATUSES, "项目状态");
        Date projectStart = date(input.get("planned_start_date"));
        Date projectEnd = date(input.get("planned_end_date"));
        validateDateRange(projectStart, projectEnd, "项目计划");
        Date actualEnd = date(input.get("actual_end_date"));
        validateDateRange(projectStart, actualEnd, "项目实际");
        String phase = optional(input, "phase", null);
        validatePhase(phase, "PROJECT_PHASE", user.tenantId(), false);
        long id = nextId();
        jdbc.update("INSERT INTO pm_project (id, tenant_id, project_code, project_name, description, status, phase, owner_id, planned_start_date, planned_end_date, actual_end_date, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), code, name, optional(input, "description", null), status, phase, ownerId, projectStart, projectEnd, actualEnd, user.id());
        long roleId = nextId();
        jdbc.update("INSERT INTO pm_project_role (id, tenant_id, project_id, role_code, role_name, description) VALUES (?, ?, ?, 'PM', '项目负责人', '项目创建时自动初始化的项目负责人角色')", roleId, user.tenantId(), id);
        addMember(id, user.id(), user.tenantId(), List.of(roleId), nextId());
        if (ownerId != user.id()) addMember(id, ownerId, user.tenantId(), List.of(), nextId());
        audit(user, "project:create", id);
        return detail(id, user);
    }

    @Transactional
    public Map<String, Object> update(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("project", "update", user);
        requireProjectAccess(projectId, user, true);
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (input.containsKey("project_code")) { assignments.add("project_code = ?"); args.add(required(input, "project_code", "项目编号", 64)); }
        if (input.containsKey("project_name")) { assignments.add("project_name = ?"); args.add(required(input, "project_name", "项目名称", 128)); }
        if (input.containsKey("description")) { assignments.add("description = ?"); args.add(optional(input, "description", null)); }
        if (input.containsKey("status")) { String value = optional(input, "status", "PLANNING"); validateStatus(value, PROJECT_STATUSES, "项目状态"); assignments.add("status = ?"); args.add(value); }
        if (input.containsKey("phase")) { String value = optional(input, "phase", null); validatePhase(value, "PROJECT_PHASE", user.tenantId(), false); assignments.add("phase = ?"); args.add(value); }
        if (input.containsKey("owner_id")) { long ownerId = longValue(input.get("owner_id"), 0); validateUser(ownerId, user.tenantId()); assignments.add("owner_id = ?"); args.add(ownerId); }
        Map<String, Object> current = project(projectId, user.tenantId());
        Date nextStart = input.containsKey("planned_start_date") ? date(input.get("planned_start_date")) : dateValue(current.get("planned_start_date"));
        Date nextEnd = input.containsKey("planned_end_date") ? date(input.get("planned_end_date")) : dateValue(current.get("planned_end_date"));
        Date nextActualEnd = input.containsKey("actual_end_date") ? date(input.get("actual_end_date")) : dateValue(current.get("actual_end_date"));
        validateDateRange(nextStart, nextEnd, "项目计划");
        validateDateRange(nextStart, nextActualEnd, "项目实际");
        if (input.containsKey("planned_start_date")) { assignments.add("planned_start_date = ?"); args.add(nextStart); }
        if (input.containsKey("planned_end_date")) { assignments.add("planned_end_date = ?"); args.add(nextEnd); }
        if (input.containsKey("actual_end_date")) { assignments.add("actual_end_date = ?"); args.add(nextActualEnd); }
        if (assignments.isEmpty()) throw badRequest("没有可修改的项目字段");
        args.add(projectId); args.add(user.tenantId());
        jdbc.update("UPDATE pm_project SET " + String.join(", ", assignments) + " WHERE id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        audit(user, "project:update", projectId);
        return detail(projectId, user);
    }

    @Transactional
    public void delete(long projectId, AuthUser user) {
        requireAction("project", "delete", user);
        requireProjectAccess(projectId, user, true);
        int changed = jdbc.update("UPDATE pm_project SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        if (changed == 0) throw badRequest("项目不存在或已删除");
        jdbc.update("DELETE FROM pm_project_plan_org WHERE tenant_id = ? AND plan_id IN (SELECT id FROM pm_project_plan WHERE project_id = ? AND tenant_id = ?)", user.tenantId(), projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_plan SET group_id = NULL, deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_plan_group SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("DELETE FROM pm_project_member_role WHERE tenant_id = ? AND member_id IN (SELECT id FROM pm_project_member WHERE project_id = ? AND tenant_id = ?)", user.tenantId(), projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_member SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_role SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        audit(user, "project:delete", projectId);
    }

    public List<Map<String, Object>> plans(long projectId, AuthUser user) {
        requireAction("plan", "read", user);
        requireProjectAccess(projectId, user, false);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.id, p.project_id, p.group_id, g.group_name, p.parent_id, p.plan_name, p.plan_code, p.description, p.owner_id, u.display_name AS owner_name, p.planned_start_date, p.planned_end_date, p.progress, p.status, p.phase, p.sort_no, p.created_at, p.updated_at FROM pm_project_plan p LEFT JOIN pm_project_plan_group g ON g.id = p.group_id AND g.project_id = p.project_id AND g.tenant_id = p.tenant_id AND g.deleted = 0 LEFT JOIN sys_user u ON u.id = p.owner_id AND u.tenant_id = p.tenant_id AND u.deleted = 0 WHERE p.project_id = ? AND p.tenant_id = ? AND p.deleted = 0 ORDER BY COALESCE(p.group_id, 0), p.parent_id, p.sort_no, p.id", projectId, user.tenantId());
        rows.forEach(row -> decoratePlanOrganizations(row, user.tenantId()));
        return rows;
    }

    public List<Map<String, Object>> planGroups(long projectId, AuthUser user) {
        requireAction("plan", "read", user);
        requireProjectAccess(projectId, user, false);
        return jdbc.queryForList("SELECT id, project_id, group_name, CASE WHEN color_key IN ('brand', 'accent', 'success', 'warning', 'danger', 'muted') THEN color_key ELSE 'brand' END AS color_key, description, sort_no, created_at, updated_at FROM pm_project_plan_group WHERE project_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY sort_no, id", projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createPlanGroup(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "create", user);
        requireProjectAccess(projectId, user, false);
        String name = required(input, "group_name", "分组名称", 128);
        ensureGroupNameAvailable(projectId, name, 0, user.tenantId());
        long id = nextId();
        String colorKey = planGroupPaletteKey(input.get("color_key"));
        jdbc.update("INSERT INTO pm_project_plan_group (id, tenant_id, project_id, group_name, color_key, description, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, name, colorKey, optional(input, "description", null), (int) optionalLong(input.get("sort_no"), 0));
        audit(user, "project:plan-group:create", id);
        return planGroup(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updatePlanGroup(long projectId, long groupId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "update", user);
        requireProjectAccess(projectId, user, false);
        ensureGroup(projectId, groupId, user.tenantId());
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (input.containsKey("group_name")) {
            String name = required(input, "group_name", "分组名称", 128);
            ensureGroupNameAvailable(projectId, name, groupId, user.tenantId());
            assignments.add("group_name = ?");
            args.add(name);
        }
        if (input.containsKey("description")) { assignments.add("description = ?"); args.add(optional(input, "description", null)); }
        if (input.containsKey("color_key")) { assignments.add("color_key = ?"); args.add(planGroupPaletteKey(input.get("color_key"))); }
        if (input.containsKey("sort_no")) { assignments.add("sort_no = ?"); args.add((int) optionalLong(input.get("sort_no"), 0)); }
        if (assignments.isEmpty()) throw badRequest("没有可修改的分组字段");
        args.add(groupId); args.add(projectId); args.add(user.tenantId());
        jdbc.update("UPDATE pm_project_plan_group SET " + String.join(", ", assignments) + " WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        audit(user, "project:plan-group:update", groupId);
        return planGroup(groupId, projectId, user.tenantId());
    }

    @Transactional
    public void deletePlanGroup(long projectId, long groupId, AuthUser user) {
        requireAction("plan", "delete", user);
        requireProjectAccess(projectId, user, false);
        ensureGroup(projectId, groupId, user.tenantId());
        jdbc.update("UPDATE pm_project_plan SET group_id = NULL WHERE group_id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", groupId, projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_plan_group SET deleted = 1 WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", groupId, projectId, user.tenantId());
        audit(user, "project:plan-group:delete", groupId);
    }

    @Transactional
    public void movePlanToGroup(long projectId, long planId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "update", user);
        requireProjectAccess(projectId, user, false);
        Map<String, Object> source = planForUpdate(planId, projectId, user.tenantId());
        long parentId = optionalLong(source.get("parent_id"), 0);
        if (parentId != 0) throw badRequest("只有主计划可以进行分组");
        Long groupId = nullableLong(input.get("group_id"));
        if (groupId != null && groupId > 0) ensureGroup(projectId, groupId, user.tenantId());
        List<Long> descendants = descendantPlanIds(projectId, planId, user.tenantId());
        descendants.add(0, planId);
        String placeholders = String.join(", ", descendants.stream().map(item -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(groupId);
        args.addAll(descendants);
        args.add(projectId); args.add(user.tenantId());
        jdbc.update("UPDATE pm_project_plan SET group_id = ? WHERE id IN (" + placeholders + ") AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        audit(user, "project:plan-group:move", planId);
    }

    @Transactional
    public Map<String, Object> createPlan(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "create", user); requireProjectAccess(projectId, user, false);
        String name = required(input, "plan_name", "计划名称", 128);
        long ownerId = optionalLong(input.get("owner_id"), 0); if (ownerId != 0) validateUser(ownerId, user.tenantId());
        long parentId = optionalLong(input.get("parent_id"), 0); validatePlanParent(projectId, parentId, user.tenantId());
        double progress = progress(input.get("progress")); String status = optional(input, "status", "NOT_STARTED"); validateStatus(status, PLAN_STATUSES, "计划状态");
        String phase = optional(input, "phase", null); validatePhase(phase, "PLAN_PHASE", user.tenantId(), false);
        Date planStart = date(input.get("planned_start_date")); Date planEnd = date(input.get("planned_end_date"));
        validateDateRange(planStart, planEnd, "计划"); validateWithinParent(projectId, parentId, planStart, planEnd, user.tenantId());
        Map<String, Object> projectRow = projectForUpdate(projectId, user.tenantId());
        String projectCode = String.valueOf(projectRow.get("project_code"));
        String mainRule = projectRow.get("plan_number_rule") == null ? DEFAULT_PLAN_NUMBER_RULE : String.valueOf(projectRow.get("plan_number_rule"));
        validatePlanNumberRule(mainRule);
        long mainSequence = 0;
        long childSequence = 0;
        String planCode;
        if (parentId == 0) {
            mainSequence = optionalLong(projectRow.get("next_plan_sequence"), 1);
            planCode = renderPlanCode(mainRule, projectCode, mainSequence, LocalDate.now());
        } else {
            Map<String, Object> parentRow = parentPlanForUpdate(parentId, projectId, user.tenantId());
            String parentCode = parentRow.get("plan_code") == null ? "" : String.valueOf(parentRow.get("plan_code"));
            if (parentCode.isBlank()) {
                long parentSequence = optionalLong(projectRow.get("next_plan_sequence"), 1);
                parentCode = renderPlanCode(mainRule, projectCode, parentSequence, LocalDate.now());
                jdbc.update("UPDATE pm_project_plan SET plan_code = ? WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", parentCode, parentId, projectId, user.tenantId());
                jdbc.update("UPDATE pm_project SET next_plan_sequence = ? WHERE id = ? AND tenant_id = ? AND deleted = 0", parentSequence + 1, projectId, user.tenantId());
            }
            String childRule = nonBlankOrDefault(projectRow.get("child_plan_number_rule"), DEFAULT_CHILD_PLAN_NUMBER_RULE);
            validateChildPlanNumberRule(childRule);
            childSequence = optionalLong(parentRow.get("next_child_plan_sequence"), 1);
            planCode = renderChildPlanCode(childRule, parentCode, childSequence, LocalDate.now());
        }
        long id = nextId();
        jdbc.update("INSERT INTO pm_project_plan (id, tenant_id, project_id, parent_id, plan_name, plan_code, description, owner_id, planned_start_date, planned_end_date, progress, status, phase, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, parentId, name, planCode, optional(input, "description", null), ownerId == 0 ? null : ownerId, planStart, planEnd, progress, status, phase, (int) optionalLong(input.get("sort_no"), 0));
        if (parentId == 0) {
            jdbc.update("UPDATE pm_project SET next_plan_sequence = ? WHERE id = ? AND tenant_id = ? AND deleted = 0", mainSequence + 1, projectId, user.tenantId());
        } else {
            jdbc.update("UPDATE pm_project_plan SET next_child_plan_sequence = ? WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", childSequence + 1, parentId, projectId, user.tenantId());
        }
        savePlanOrganizations(id, input, user.tenantId());
        audit(user, "project:plan:create", id); return plan(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updatePlan(long projectId, long planId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "update", user); requireProjectAccess(projectId, user, false); ensurePlan(projectId, planId, user.tenantId());
        List<String> assignments = new ArrayList<>(); List<Object> args = new ArrayList<>();
        if (input.containsKey("plan_name")) { assignments.add("plan_name = ?"); args.add(required(input, "plan_name", "计划名称", 128)); }
        if (input.containsKey("description")) { assignments.add("description = ?"); args.add(optional(input, "description", null)); }
        if (input.containsKey("owner_id")) { long id = optionalLong(input.get("owner_id"), 0); if (id != 0) validateUser(id, user.tenantId()); assignments.add("owner_id = ?"); args.add(id == 0 ? null : id); }
        if (input.containsKey("parent_id")) { long parent = optionalLong(input.get("parent_id"), 0); if (parent == planId) throw badRequest("计划不能选择自己作为父计划"); validatePlanParent(projectId, parent, user.tenantId()); assignments.add("parent_id = ?"); args.add(parent); }
        if (input.containsKey("phase")) { String phase = optional(input, "phase", null); validatePhase(phase, "PLAN_PHASE", user.tenantId(), false); assignments.add("phase = ?"); args.add(phase); }
        Map<String, Object> current = plan(planId, projectId, user.tenantId());
        Date nextStart = input.containsKey("planned_start_date") ? date(input.get("planned_start_date")) : dateValue(current.get("planned_start_date"));
        Date nextEnd = input.containsKey("planned_end_date") ? date(input.get("planned_end_date")) : dateValue(current.get("planned_end_date"));
        long nextParent = input.containsKey("parent_id") ? optionalLong(input.get("parent_id"), 0) : optionalLong(current.get("parent_id"), 0);
        validateDateRange(nextStart, nextEnd, "计划"); validateWithinParent(projectId, nextParent, nextStart, nextEnd, user.tenantId());
        if (input.containsKey("planned_start_date")) { assignments.add("planned_start_date = ?"); args.add(nextStart); }
        if (input.containsKey("planned_end_date")) { assignments.add("planned_end_date = ?"); args.add(nextEnd); }
        if (input.containsKey("progress")) { assignments.add("progress = ?"); args.add(progress(input.get("progress"))); }
        if (input.containsKey("status")) { String status = optional(input, "status", "NOT_STARTED"); validateStatus(status, PLAN_STATUSES, "计划状态"); assignments.add("status = ?"); args.add(status); }
        if (input.containsKey("sort_no")) { assignments.add("sort_no = ?"); args.add((int) optionalLong(input.get("sort_no"), 0)); }
        boolean organizationChanged = input.containsKey("lead_org_id") || input.containsKey("cooperating_org_ids");
        if (assignments.isEmpty() && !organizationChanged) throw badRequest("没有可修改的计划字段");
        if (!assignments.isEmpty()) {
            args.add(planId); args.add(projectId); args.add(user.tenantId());
            jdbc.update("UPDATE pm_project_plan SET " + String.join(", ", assignments) + " WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        }
        if (organizationChanged) savePlanOrganizations(planId, input, user.tenantId());
        audit(user, "project:plan:update", planId); return plan(planId, projectId, user.tenantId());
    }

    @Transactional
    public void deletePlan(long projectId, long planId, AuthUser user) {
        requireAction("plan", "delete", user); requireProjectAccess(projectId, user, false); ensurePlan(projectId, planId, user.tenantId());
        jdbc.update("DELETE FROM pm_project_plan_org WHERE tenant_id = ? AND plan_id IN (SELECT id FROM pm_project_plan WHERE id = ? OR parent_id = ? AND project_id = ? AND tenant_id = ?)", user.tenantId(), planId, planId, projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_plan SET deleted = 1 WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", planId, projectId, user.tenantId());
        deletePlanTree(projectId, planId, user.tenantId());
        audit(user, "project:plan:delete", planId);
    }

    public List<Map<String, Object>> members(long projectId, AuthUser user) {
        requireAction("member", "read", user); requireProjectAccess(projectId, user, false);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT m.id, m.project_id, m.user_id, u.username, u.display_name, u.avatar_object_key, m.status, m.joined_at FROM pm_project_member m JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.deleted = 0 WHERE m.project_id = ? AND m.tenant_id = ? AND m.deleted = 0 ORDER BY m.joined_at, m.id", projectId, user.tenantId());
        for (Map<String, Object> row : rows) { row.put("avatar_url", storage.presignedUrl((String) row.remove("avatar_object_key"))); row.put("roles", jdbc.queryForList("SELECT r.id, r.role_code, r.role_name FROM pm_project_member_role mr JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id AND r.deleted = 0 WHERE mr.member_id = ? AND mr.tenant_id = ? ORDER BY r.id", row.get("id"), user.tenantId())); }
        return rows;
    }

    @Transactional
    public Map<String, Object> createMember(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("member", "create", user); requireProjectAccess(projectId, user, true);
        long userId = longValue(input.get("user_id"), 0); validateUser(userId, user.tenantId());
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE project_id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, projectId, userId, user.tenantId());
        if (exists != null && exists > 0) throw badRequest("该用户已经是项目成员");
        long memberId = nextId(); addMember(projectId, userId, user.tenantId(), ids(input.get("role_ids")), memberId);
        audit(user, "project:member:create", memberId); return member(memberId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateMember(long projectId, long memberId, Map<String, Object> input, AuthUser user) {
        requireAction("member", "update", user); requireProjectAccess(projectId, user, true); ensureMember(projectId, memberId, user.tenantId());
        if (input.containsKey("status")) jdbc.update("UPDATE pm_project_member SET status = ? WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", optionalLong(input.get("status"), 1), memberId, projectId, user.tenantId());
        if (input.containsKey("role_ids")) saveMemberRoles(memberId, projectId, ids(input.get("role_ids")), user.tenantId());
        audit(user, "project:member:update", memberId); return member(memberId, user.tenantId());
    }

    @Transactional
    public void deleteMember(long projectId, long memberId, AuthUser user) {
        requireAction("member", "delete", user); requireProjectAccess(projectId, user, true); ensureMember(projectId, memberId, user.tenantId());
        Long memberUserId = jdbc.queryForObject("SELECT user_id FROM pm_project_member WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Long.class, memberId, projectId, user.tenantId());
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Long.class, projectId, user.tenantId());
        if (memberUserId != null && memberUserId.equals(ownerId)) throw badRequest("项目负责人不能移出项目");
        jdbc.update("UPDATE pm_project_member SET deleted = 1 WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", memberId, projectId, user.tenantId());
        audit(user, "project:member:delete", memberId);
    }

    public List<Map<String, Object>> roles(long projectId, AuthUser user) {
        requireAction("role", "read", user); requireProjectAccess(projectId, user, false);
        return jdbc.queryForList("SELECT r.id, r.project_id, r.role_code, r.role_name, r.description, r.created_at, (SELECT COUNT(*) FROM pm_project_member_role mr WHERE mr.role_id = r.id AND mr.tenant_id = r.tenant_id) AS member_count FROM pm_project_role r WHERE r.project_id = ? AND r.tenant_id = ? AND r.deleted = 0 ORDER BY r.id", projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createRole(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("role", "create", user); requireProjectAccess(projectId, user, true);
        String code = required(input, "role_code", "角色编码", 64); String name = required(input, "role_name", "角色名称", 128);
        long id = nextId(); jdbc.update("INSERT INTO pm_project_role (id, tenant_id, project_id, role_code, role_name, description) VALUES (?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, code, name, optional(input, "description", null)); audit(user, "project:role:create", id); return role(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateRole(long projectId, long roleId, Map<String, Object> input, AuthUser user) {
        requireAction("role", "update", user); requireProjectAccess(projectId, user, true); ensureRole(projectId, roleId, user.tenantId());
        List<String> assignments = new ArrayList<>(); List<Object> args = new ArrayList<>();
        if (input.containsKey("role_code")) { assignments.add("role_code = ?"); args.add(required(input, "role_code", "角色编码", 64)); }
        if (input.containsKey("role_name")) { assignments.add("role_name = ?"); args.add(required(input, "role_name", "角色名称", 128)); }
        if (input.containsKey("description")) { assignments.add("description = ?"); args.add(optional(input, "description", null)); }
        if (assignments.isEmpty()) throw badRequest("没有可修改的角色字段"); args.add(roleId); args.add(projectId); args.add(user.tenantId()); jdbc.update("UPDATE pm_project_role SET " + String.join(", ", assignments) + " WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray()); audit(user, "project:role:update", roleId); return role(roleId, projectId, user.tenantId());
    }

    @Transactional
    public void deleteRole(long projectId, long roleId, AuthUser user) {
        requireAction("role", "delete", user); requireProjectAccess(projectId, user, true); ensureRole(projectId, roleId, user.tenantId());
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member_role WHERE role_id = ? AND tenant_id = ?", Integer.class, roleId, user.tenantId()); if (used != null && used > 0) throw badRequest("该角色仍被项目成员使用，不能删除");
        jdbc.update("UPDATE pm_project_role SET deleted = 1 WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", roleId, projectId, user.tenantId()); audit(user, "project:role:delete", roleId);
    }

    public List<Map<String, Object>> userOptions(String keyword, AuthUser user) {
        requireAction("member", "read", user); String filter = keyword == null || keyword.isBlank() ? "" : " AND (u.username LIKE ? OR u.display_name LIKE ?)"; List<Object> args = new ArrayList<>(List.of(user.tenantId())); if (!filter.isBlank()) { String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); }
        args.add(100); return jdbc.queryForList("SELECT u.id, u.username, u.display_name, u.org_id FROM sys_user u WHERE u.tenant_id = ? AND u.status = 1 AND u.deleted = 0" + filter + " ORDER BY u.display_name, u.id LIMIT ?", args.toArray());
    }

     private Map<String, Object> project(long id, long tenantId) { try { return jdbc.queryForMap("SELECT id, project_code, project_name, description, status, phase, plan_number_rule, child_plan_number_rule, next_plan_sequence, owner_id, planned_start_date, planned_end_date, actual_end_date, created_at, updated_at FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("项目不存在"); } }
     private Map<String, Object> projectForUpdate(long id, long tenantId) { try { return jdbc.queryForMap("SELECT id, project_code, plan_number_rule, child_plan_number_rule, next_plan_sequence FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", id, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("项目不存在"); } }
     private Map<String, Object> parentPlanForUpdate(long planId, long projectId, long tenantId) { try { return jdbc.queryForMap("SELECT id, plan_code, next_child_plan_sequence FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", planId, projectId, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("父计划不存在"); } }
    private Map<String, Object> plan(long id, long projectId, long tenantId) { Map<String, Object> row = jdbc.queryForMap("SELECT p.id, p.project_id, p.group_id, g.group_name, p.parent_id, p.plan_name, p.plan_code, p.description, p.owner_id, u.display_name AS owner_name, p.planned_start_date, p.planned_end_date, p.progress, p.status, p.phase, p.sort_no, p.created_at, p.updated_at FROM pm_project_plan p LEFT JOIN pm_project_plan_group g ON g.id = p.group_id AND g.project_id = p.project_id AND g.tenant_id = p.tenant_id AND g.deleted = 0 LEFT JOIN sys_user u ON u.id = p.owner_id AND u.tenant_id = p.tenant_id WHERE p.id = ? AND p.project_id = ? AND p.tenant_id = ? AND p.deleted = 0", id, projectId, tenantId); decoratePlanOrganizations(row, tenantId); return row; }
    private Map<String, Object> planForUpdate(long id, long projectId, long tenantId) { try { return jdbc.queryForMap("SELECT id, parent_id, group_id FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", id, projectId, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("计划不存在"); } }
     private Map<String, Object> planGroup(long id, long projectId, long tenantId) { try { return jdbc.queryForMap("SELECT id, project_id, group_name, CASE WHEN color_key IN ('brand', 'accent', 'success', 'warning', 'danger', 'muted') THEN color_key ELSE 'brand' END AS color_key, description, sort_no, created_at, updated_at FROM pm_project_plan_group WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", id, projectId, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("计划分组不存在"); } }
    private void ensureGroup(long projectId, long groupId, long tenantId) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan_group WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, groupId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("计划分组不存在"); }
    private void ensureGroupNameAvailable(long projectId, String groupName, long excludedId, long tenantId) { Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan_group WHERE project_id = ? AND tenant_id = ? AND group_name = ? AND id <> ? AND deleted = 0", Integer.class, projectId, tenantId, groupName, excludedId); if (duplicate != null && duplicate > 0) throw badRequest("该项目下已存在同名分组"); }
     private String planGroupPaletteKey(Object value) { String key = value == null || String.valueOf(value).isBlank() ? DEFAULT_PLAN_GROUP_COLOR_TOKEN : String.valueOf(value).trim(); if (!PLAN_GROUP_COLOR_TOKENS.contains(key)) throw badRequest("分组色阶无效"); return key; }
    private List<Long> descendantPlanIds(long projectId, long planId, long tenantId) { List<Long> pending = new ArrayList<>(List.of(planId)); List<Long> descendants = new ArrayList<>(); Set<Long> visited = new HashSet<>(); while (!pending.isEmpty()) { long parent = pending.remove(0); if (!visited.add(parent)) continue; List<Long> children = jdbc.queryForList("SELECT id FROM pm_project_plan WHERE parent_id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", Long.class, parent, projectId, tenantId); descendants.addAll(children); pending.addAll(children); } return descendants; }
    private Map<String, Object> member(long id, long tenantId) { Map<String, Object> row = jdbc.queryForMap("SELECT m.id, m.project_id, m.user_id, u.username, u.display_name, m.status, m.joined_at FROM pm_project_member m JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id WHERE m.id = ? AND m.tenant_id = ? AND m.deleted = 0", id, tenantId); row.put("roles", jdbc.queryForList("SELECT r.id, r.role_code, r.role_name FROM pm_project_member_role mr JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id AND r.deleted = 0 WHERE mr.member_id = ? AND mr.tenant_id = ?", id, tenantId)); return row; }
    private Map<String, Object> role(long id, long projectId, long tenantId) { return jdbc.queryForMap("SELECT r.id, r.project_id, r.role_code, r.role_name, r.description, r.created_at FROM pm_project_role r WHERE r.id = ? AND r.project_id = ? AND r.tenant_id = ? AND r.deleted = 0", id, projectId, tenantId); }

    private void decorateProject(Map<String, Object> row, long tenantId) { Long id = ((Number) row.get("id")).longValue(); row.put("owner_name", jdbc.query("SELECT display_name FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0", rs -> rs.next() ? rs.getString(1) : null, row.get("owner_id"), tenantId)); row.put("phase_name", parameterLabel("PROJECT_PHASE", "项目阶段", row.get("phase"), tenantId)); row.put("member_count", jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE project_id = ? AND tenant_id = ? AND status = 1 AND deleted = 0", Integer.class, id, tenantId)); row.put("plan_count", jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, tenantId)); row.put("completed_plan_count", jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND status = 'COMPLETED' AND deleted = 0", Integer.class, id, tenantId)); Double progress = jdbc.queryForObject("SELECT AVG(progress) FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND deleted = 0", Double.class, id, tenantId); row.put("plan_progress", progress == null ? 0 : Math.round(progress * 100.0) / 100.0); }

    public Map<String, Object> options(AuthUser user) {
        requireAction("project", "read", user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project_phases", phaseOptions("PROJECT_PHASE", "项目阶段", user.tenantId()));
        result.put("plan_phases", phaseOptions("PLAN_PHASE", "计划阶段", user.tenantId()));
        result.put("organizations", jdbc.queryForList("SELECT id, parent_id, org_name, status FROM sys_org WHERE tenant_id = ? AND status = 1 AND deleted = 0 ORDER BY sort_no, id", user.tenantId()));
        return result;
    }

    private String projectScope(AuthUser user) { return isSuperAdmin(user) ? "1 = 1" : "EXISTS (SELECT 1 FROM pm_project_member pm WHERE pm.project_id = pm_project.id AND pm.tenant_id = pm_project.tenant_id AND pm.user_id = " + user.id() + " AND pm.status = 1 AND pm.deleted = 0)"; }
    private void requireProjectAccess(long projectId, AuthUser user, boolean ownerOnly) { Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, projectId, user.tenantId()); if (exists == null || exists == 0) throw badRequest("项目不存在"); if (isSuperAdmin(user)) return; String sql = ownerOnly ? "SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND owner_id = ? AND deleted = 0" : "SELECT COUNT(*) FROM pm_project_member WHERE project_id = ? AND tenant_id = ? AND user_id = ? AND status = 1 AND deleted = 0"; Integer allowed = jdbc.queryForObject(sql, Integer.class, projectId, user.tenantId(), user.id()); if (allowed == null || allowed == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有该项目的操作权限"); }
    private void requireAction(String resource, String action, AuthUser user) { if (isSuperAdmin(user)) return; String base = switch (resource) { case "project" -> "project:project:list"; case "plan" -> "project:plan:list"; case "member" -> "project:member:list"; case "role" -> "project:role:list"; default -> throw badRequest("项目资源无效"); }; String permission = "read".equals(action) ? base : base + ":" + action; Integer allowed = jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND p.tenant_id = ? AND p.permission_code = ? AND p.action_code = ? AND p.status = 1 AND r.status = 1", Integer.class, user.id(), user.tenantId(), permission, action); if (allowed == null || allowed == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有项目" + actionLabel(action) + "权限"); }
    private boolean isSuperAdmin(AuthUser user) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND ur.tenant_id = ? AND r.role_code = 'SUPER_ADMIN' AND r.status = 1 AND r.deleted = 0", Integer.class, user.id(), user.tenantId()); return count != null && count > 0; }

    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds) { addMember(projectId, userId, tenantId, roleIds, nextId()); }
    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds, long memberId) { jdbc.update("INSERT INTO pm_project_member (id, tenant_id, project_id, user_id) VALUES (?, ?, ?, ?)", memberId, tenantId, projectId, userId); saveMemberRoles(memberId, projectId, roleIds, tenantId); }
    private void saveMemberRoles(long memberId, long projectId, List<Long> roleIds, long tenantId) { Set<Long> ids = new HashSet<>(roleIds); jdbc.update("DELETE FROM pm_project_member_role WHERE member_id = ? AND tenant_id = ?", memberId, tenantId); for (Long roleId : ids) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_role WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, roleId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("项目角色不存在"); jdbc.update("INSERT INTO pm_project_member_role (tenant_id, member_id, role_id) VALUES (?, ?, ?)", tenantId, memberId, roleId); } }
    private void validatePlanParent(long projectId, long parentId, long tenantId) { if (parentId == 0) return; Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, parentId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("父计划不存在"); }
    private void validateWithinParent(long projectId, long parentId, Date start, Date end, long tenantId) { if (parentId == 0) return; Map<String, Object> parent = jdbc.queryForMap("SELECT planned_start_date, planned_end_date FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", parentId, projectId, tenantId); Date parentStart = dateValue(parent.get("planned_start_date")); Date parentEnd = dateValue(parent.get("planned_end_date")); if (start != null && parentStart != null && start.before(parentStart)) throw badRequest("子计划开始日期不能早于父计划"); if (end != null && parentEnd != null && end.after(parentEnd)) throw badRequest("子计划结束日期不能晚于父计划"); }
    private void validateDateRange(Date start, Date end, String label) { if (start != null && end != null && end.before(start)) throw badRequest(label + "结束日期必须大于等于开始日期"); }
     private void validatePlanNumberRule(String rule) { validatePlanNumberRule(rule, Set.of("PROJECT_CODE", "SEQ", "YYYY", "MM", "DD"), "计划编号规则"); }
     private void validateChildPlanNumberRule(String rule) { validatePlanNumberRule(rule, Set.of("PARENT_CODE", "SEQ", "YYYY", "MM", "DD"), "子计划编号规则"); }
     private void validatePlanNumberRule(String rule, Set<String> allowedTokens, String label) { if (rule == null || rule.isBlank() || rule.length() > 128) throw badRequest(label + "不能为空且不能超过128个字符"); Matcher matcher = PLAN_RULE_TOKEN.matcher(rule); int end = 0; boolean sequence = false; while (matcher.find()) { if (matcher.start() != end && rule.substring(end, matcher.start()).contains("{")) throw badRequest(label + "包含无效占位符"); String token = matcher.group(1); if (!allowedTokens.contains(token)) throw badRequest(label + "包含无效占位符"); if ("SEQ".equals(token)) sequence = true; end = matcher.end(); } if (end != rule.length() || !sequence) throw badRequest(label + "必须包含{SEQ}或{SEQ:n}"); }
     private String renderPlanCode(String rule, String projectCode, long sequence, LocalDate date) { Matcher matcher = PLAN_RULE_TOKEN.matcher(rule); StringBuffer result = new StringBuffer(); while (matcher.find()) { String token = matcher.group(1); int width = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)); String value = switch (token) { case "PROJECT_CODE" -> projectCode; case "SEQ" -> width > 0 ? String.format("%0" + width + "d", sequence) : String.valueOf(sequence); case "YYYY" -> date.format(DateTimeFormatter.ofPattern("yyyy")); case "MM" -> date.format(DateTimeFormatter.ofPattern("MM")); case "DD" -> date.format(DateTimeFormatter.ofPattern("dd")); default -> throw badRequest("计划编号规则包含无效占位符"); }; matcher.appendReplacement(result, Matcher.quoteReplacement(value)); } matcher.appendTail(result); String rendered = result.toString(); if (rendered.isBlank() || rendered.length() > 128) throw badRequest("生成的计划编号无效或超过128个字符"); return rendered; }
     private String renderChildPlanCode(String rule, String parentCode, long sequence, LocalDate date) { Matcher matcher = PLAN_RULE_TOKEN.matcher(rule); StringBuffer result = new StringBuffer(); while (matcher.find()) { String token = matcher.group(1); int width = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)); String value = switch (token) { case "PARENT_CODE" -> parentCode; case "SEQ" -> width > 0 ? String.format("%0" + width + "d", sequence) : String.valueOf(sequence); case "YYYY" -> date.format(DateTimeFormatter.ofPattern("yyyy")); case "MM" -> date.format(DateTimeFormatter.ofPattern("MM")); case "DD" -> date.format(DateTimeFormatter.ofPattern("dd")); default -> throw badRequest("子计划编号规则包含无效占位符"); }; matcher.appendReplacement(result, Matcher.quoteReplacement(value)); } matcher.appendTail(result); String rendered = result.toString(); if (rendered.isBlank() || rendered.length() > 128) throw badRequest("生成的子计划编号无效或超过128个字符"); return rendered; }
    private List<Map<String, Object>> phaseOptions(String categoryCode, String categoryName, long tenantId) { return jdbc.queryForList("SELECT c.config_key AS value, COALESCE(NULLIF(TRIM(c.config_value), ''), c.config_key) AS label FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id WHERE c.tenant_id = ? AND (LOWER(t.dict_code) = LOWER(?) OR t.dict_name = ?) AND t.status = 1 AND t.deleted = 0 AND c.status = 1 AND c.deleted = 0 ORDER BY c.id", tenantId, categoryCode, categoryName); }
    private void validatePhase(String value, String categoryCode, long tenantId, boolean required) { if (value == null || value.isBlank()) { if (required) throw badRequest("阶段不能为空"); return; } Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id WHERE c.tenant_id = ? AND (LOWER(t.dict_code) = LOWER(?) OR t.dict_name = ?) AND t.status = 1 AND t.deleted = 0 AND c.config_key = ? AND c.status = 1 AND c.deleted = 0", Integer.class, tenantId, categoryCode, phaseCategoryName(categoryCode), value); if (valid == null || valid == 0) throw badRequest("阶段参数无效"); }
    private String parameterLabel(String categoryCode, String categoryName, Object value, long tenantId) { if (value == null) return null; return jdbc.query("SELECT COALESCE(NULLIF(TRIM(c.config_value), ''), c.config_key) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id WHERE c.tenant_id = ? AND (LOWER(t.dict_code) = LOWER(?) OR t.dict_name = ?) AND t.status = 1 AND t.deleted = 0 AND c.config_key = ? AND c.status = 1 AND c.deleted = 0", rs -> rs.next() ? rs.getString(1) : null, tenantId, categoryCode, categoryName, value); }
    private String phaseCategoryName(String categoryCode) { return "PLAN_PHASE".equals(categoryCode) ? "计划阶段" : "项目阶段"; }
    private Date dateValue(Object value) { if (value == null) return null; if (value instanceof Date date) return date; return date(value); }
    private void savePlanOrganizations(long planId, Map<String, Object> input, long tenantId) { jdbc.update("DELETE FROM pm_project_plan_org WHERE plan_id = ? AND tenant_id = ?", planId, tenantId); long leadId = optionalLong(input.get("lead_org_id"), 0); if (leadId > 0) { validateOrganization(leadId, tenantId); jdbc.update("INSERT INTO pm_project_plan_org (plan_id, org_id, party_type, tenant_id) VALUES (?, ?, 'LEAD', ?)", planId, leadId, tenantId); } for (Long orgId : ids(input.get("cooperating_org_ids"))) { validateOrganization(orgId, tenantId); jdbc.update("INSERT INTO pm_project_plan_org (plan_id, org_id, party_type, tenant_id) VALUES (?, ?, 'COOPERATING', ?)", planId, orgId, tenantId); } }
    private void decoratePlanOrganizations(Map<String, Object> row, long tenantId) { long planId = ((Number) row.get("id")).longValue(); List<Map<String, Object>> parties = jdbc.queryForList("SELECT po.org_id, po.party_type, o.org_name FROM pm_project_plan_org po JOIN sys_org o ON o.id = po.org_id AND o.tenant_id = po.tenant_id AND o.deleted = 0 WHERE po.plan_id = ? AND po.tenant_id = ? ORDER BY po.party_type, po.org_id", planId, tenantId); row.put("lead_org_id", parties.stream().filter(item -> "LEAD".equals(item.get("party_type"))).map(item -> item.get("org_id")).findFirst().orElse(null)); row.put("lead_org_name", parties.stream().filter(item -> "LEAD".equals(item.get("party_type"))).map(item -> item.get("org_name")).findFirst().orElse(null)); row.put("cooperating_org_ids", parties.stream().filter(item -> "COOPERATING".equals(item.get("party_type"))).map(item -> item.get("org_id")).toList()); row.put("cooperating_org_names", parties.stream().filter(item -> "COOPERATING".equals(item.get("party_type"))).map(item -> item.get("org_name")).toList()); row.put("phase_name", parameterLabel("PLAN_PHASE", "计划阶段", row.get("phase"), tenantId)); }
    private void validateOrganization(long orgId, long tenantId) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM sys_org WHERE id = ? AND tenant_id = ? AND status = 1 AND deleted = 0", Integer.class, orgId, tenantId); if (valid == null || valid == 0) throw badRequest("组织不存在或已停用"); }
    private void ensurePlan(long projectId, long planId, long tenantId) { validatePlanParent(projectId, planId, tenantId); }
    private void deletePlanTree(long projectId, long planId, long tenantId) {
        List<Long> pending = new ArrayList<>(List.of(planId));
        Set<Long> all = new HashSet<>();
        while (!pending.isEmpty()) {
            long parent = pending.remove(0);
            if (!all.add(parent)) continue;
            List<Long> children = jdbc.queryForList("SELECT id FROM pm_project_plan WHERE parent_id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Long.class, parent, projectId, tenantId);
            pending.addAll(children);
        }
        for (Long id : all) {
            jdbc.update("DELETE FROM pm_project_plan_org WHERE plan_id = ? AND tenant_id = ?", id, tenantId);
            jdbc.update("UPDATE pm_project_plan SET deleted = 1 WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", id, projectId, tenantId);
        }
    }
    private void ensureMember(long projectId, long memberId, long tenantId) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, memberId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("项目成员不存在"); }
    private void ensureRole(long projectId, long roleId, long tenantId) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_role WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, roleId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("项目角色不存在"); }
    private void validateUser(long userId, long tenantId) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ? AND tenant_id = ? AND status = 1 AND deleted = 0", Integer.class, userId, tenantId); if (valid == null || valid == 0) throw badRequest("用户不存在或已停用"); }
    private String required(Map<String, Object> input, String key, String label, int max) { String value = optional(input, key, null); if (value == null) throw badRequest(label + "不能为空"); if (value.length() > max) throw badRequest(label + "不能超过" + max + "个字符"); return value; }
    private String optional(Map<String, Object> input, String key, String fallback) { Object value = input.get(key); if (value == null || String.valueOf(value).isBlank()) return fallback; return String.valueOf(value).trim(); }
    private String nonBlankOrDefault(Object value, String fallback) { return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim(); }
    private long longValue(Object value, long fallback) { return value == null || String.valueOf(value).isBlank() ? fallback : optionalLong(value, fallback); }
    private long optionalLong(Object value, long fallback) { try { return value == null || String.valueOf(value).isBlank() ? fallback : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException exception) { throw badRequest("数字格式无效"); } }
    private Long nullableLong(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; long parsed = optionalLong(value, 0); return parsed <= 0 ? null : parsed; }
    private double progress(Object value) { double result; try { result = value == null || String.valueOf(value).isBlank() ? 0 : Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException exception) { throw badRequest("计划进度格式无效"); } if (result < 0 || result > 100) throw badRequest("计划进度必须在0到100之间"); return result; }
    private Date date(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; try { return Date.valueOf(LocalDate.parse(String.valueOf(value))); } catch (RuntimeException exception) { throw badRequest("日期格式必须为yyyy-MM-dd"); } }
    private List<Long> ids(Object value) { if (!(value instanceof Collection<?> values)) return List.of(); return values.stream().map(item -> optionalLong(item, 0)).filter(item -> item > 0).distinct().toList(); }
    private void validateStatus(String value, Set<String> allowed, String label) { if (!allowed.contains(value)) throw badRequest(label + "无效"); }
    private String actionLabel(String action) { return switch (action) { case "read" -> "查看"; case "create" -> "新增"; case "delete" -> "删除"; default -> "编辑"; }; }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private void audit(AuthUser user, String operation, long targetId) { jdbc.update("INSERT INTO sys_operation_log (id, tenant_id, operator_id, operation_code, request_method, request_path, success) VALUES (?, ?, ?, ?, 'PROJECT', ?, 1)", nextId(), user.tenantId(), user.id(), operation, String.valueOf(targetId)); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

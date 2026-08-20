package com.ccb.system.project;

import com.ccb.attachment.model.AttachmentItem;
import com.ccb.attachment.model.AttachmentLink;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private static final String DEFAULT_RISK_NUMBER_RULE = "{PROJECT_CODE}-R{SEQ:3}";
    private static final Pattern PLAN_RULE_TOKEN = Pattern.compile("\\{([A-Z_]+)(?::(\\d+))?}");

    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;
    private final AttachmentPort attachmentPort;

    public ProjectService(JdbcTemplate jdbc, MinioStorageService storage) {
        this(jdbc, storage, null);
    }

    @Autowired
    public ProjectService(JdbcTemplate jdbc, MinioStorageService storage, AttachmentPort attachmentPort) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.attachmentPort = attachmentPort;
    }

    public List<Map<String, Object>> workbench(AuthUser user) {
        requireAction("project", "read", user);
        String scope = projectScope(user);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, project_code, project_name, description, status, plan_number_rule, child_plan_number_rule, next_plan_sequence, owner_id, planned_start_date, planned_end_date, actual_end_date, created_at, updated_at FROM pm_project WHERE tenant_id = ? AND deleted = 0 AND " + scope + " ORDER BY updated_at DESC, id DESC", user.tenantId());
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
        project.put("risks", risks(projectId, user));
        project.put("members", members(projectId, user));
        project.put("roles", roles(projectId, user));
        project.put("project_organizations", organizations(projectId, user));
        return project;
    }

    public PageResult<AttachmentItem> attachments(long projectId, long page, long size, String keyword, AuthUser user) {
        requireAction("project", "read", user);
        requireProjectAccess(projectId, user, false);
        PageResult<AttachmentItem> result = attachmentService().list("PROJECT", projectId, user.tenantId(), new PageQuery(page, size), keyword);
        List<AttachmentItem> records = result.records().stream()
                .map(item -> withUploaderName(item, user.tenantId()))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    @Transactional
    public AttachmentItem uploadAttachment(long projectId, MultipartFile file, AuthUser user) {
        requireAction("project", "update", user);
        requireProjectAccess(projectId, user, false);
        AttachmentItem item = attachmentService().uploadAndBind("PROJECT", projectId, file, user.tenantId(), user.id());
        audit(user, "project:attachment:upload", item.id());
        return withUploaderName(item, user.tenantId());
    }

    public AttachmentLink previewAttachment(long projectId, long attachmentId, AuthUser user) {
        requireAction("project", "read", user);
        requireProjectAccess(projectId, user, false);
        return attachmentService().preview(attachmentId, "PROJECT", projectId, user.tenantId());
    }

    public AttachmentLink downloadAttachment(long projectId, long attachmentId, AuthUser user) {
        requireAction("project", "read", user);
        requireProjectAccess(projectId, user, false);
        return attachmentService().download(attachmentId, "PROJECT", projectId, user.tenantId());
    }

    @Transactional
    public void deleteAttachment(long projectId, long attachmentId, AuthUser user) {
        requireAction("project", "update", user);
        requireProjectAccess(projectId, user, false);
        attachmentService().delete(attachmentId, "PROJECT", projectId, user.tenantId());
        audit(user, "project:attachment:delete", attachmentId);
    }

    @Transactional
    public Map<String, Object> updateSettings(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("project", "update", user);
        requireProjectAccess(projectId, user, true);
        String rule = optional(input, "plan_number_rule", DEFAULT_PLAN_NUMBER_RULE);
        validatePlanNumberRule(rule);
        String childRule = optional(input, "child_plan_number_rule", DEFAULT_CHILD_PLAN_NUMBER_RULE);
        validateChildPlanNumberRule(childRule);
        String riskRule = optional(input, "risk_number_rule", DEFAULT_RISK_NUMBER_RULE);
        validateRiskNumberRule(riskRule);
        jdbc.update("UPDATE pm_project SET plan_number_rule = ?, child_plan_number_rule = ?, risk_number_rule = ? WHERE id = ? AND tenant_id = ? AND deleted = 0", rule, childRule, riskRule, projectId, user.tenantId());
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
        long id = nextId();
        jdbc.update("INSERT INTO pm_project (id, tenant_id, project_code, project_name, description, status, owner_id, planned_start_date, planned_end_date, actual_end_date, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), code, name, optional(input, "description", null), status, ownerId, projectStart, projectEnd, actualEnd, user.id());
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
        if (attachmentPort != null) {
            PageResult<AttachmentItem> attachmentPage;
            do {
                attachmentPage = attachmentService().list("PROJECT", projectId, user.tenantId(), new PageQuery(1, 100), null);
                attachmentPage.records().forEach(item -> attachmentService().delete(item.id(), "PROJECT", projectId, user.tenantId()));
            } while (!attachmentPage.records().isEmpty());
        }
        int changed = jdbc.update("UPDATE pm_project SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        if (changed == 0) throw badRequest("项目不存在或已删除");
        jdbc.update("DELETE FROM pm_project_plan_org WHERE tenant_id = ? AND plan_id IN (SELECT id FROM pm_project_plan WHERE project_id = ? AND tenant_id = ?)", user.tenantId(), projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_plan SET group_id = NULL, deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_plan_group SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_risk SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("DELETE FROM pm_project_member_role WHERE tenant_id = ? AND member_id IN (SELECT id FROM pm_project_member WHERE project_id = ? AND tenant_id = ?)", user.tenantId(), projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_member SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_role SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
        jdbc.update("UPDATE pm_project_org SET deleted = 1 WHERE project_id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId());
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
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, project_id, phase, group_name, CASE WHEN color_key IN ('brand', 'accent', 'success', 'warning', 'danger', 'muted') THEN color_key ELSE 'brand' END AS color_key, description, sort_no, created_at, updated_at FROM pm_project_plan_group WHERE project_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY phase, sort_no, id", projectId, user.tenantId());
        rows.forEach(row -> {
            row.put("stage_plan_code", row.get("group_name"));
            row.put("phase_name", parameterLabel("PLAN_PHASE", "计划阶段", row.get("phase"), user.tenantId()));
        });
        return rows;
    }

    @Transactional
    public Map<String, Object> createPlanGroup(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "create", user);
        requireProjectAccess(projectId, user, false);
        String phase = input.containsKey("phase") ? optional(input, "phase", null) : null;
        validatePhase(phase, "PLAN_PHASE", user.tenantId(), true);
        // 锁定项目行，避免并发创建时生成相同的阶段计划编号。
        projectForUpdate(projectId, user.tenantId());
        String name = nextStagePlanCode(projectId, phase, user.tenantId());
        long id = nextId();
        String colorKey = planGroupPaletteKey(input.get("color_key"));
        jdbc.update("INSERT INTO pm_project_plan_group (id, tenant_id, project_id, phase, group_name, color_key, description, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, phase, name, colorKey, optional(input, "description", null), (int) optionalLong(input.get("sort_no"), 0));
        audit(user, "project:plan-group:create", id);
        return planGroup(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updatePlanGroup(long projectId, long groupId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "update", user);
        requireProjectAccess(projectId, user, false);
        ensureGroup(projectId, groupId, user.tenantId());
        Map<String, Object> currentGroup = planGroup(groupId, projectId, user.tenantId());
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String nextPhase = optional(currentGroup, "phase", null);
        if (input.containsKey("group_name")) {
            String name = required(input, "group_name", "阶段计划编号", 128);
            ensureGroupNameAvailable(projectId, nextPhase, name, groupId, user.tenantId());
            assignments.add("group_name = ?");
            args.add(name);
        }
        if (input.containsKey("phase")) {
            nextPhase = optional(input, "phase", null);
            validatePhase(nextPhase, "PLAN_PHASE", user.tenantId(), true);
            if (!nextPhase.equals(optional(currentGroup, "phase", null)) && !input.containsKey("group_name")) {
                projectForUpdate(projectId, user.tenantId());
                assignments.add("group_name = ?");
                args.add(nextStagePlanCode(projectId, nextPhase, user.tenantId()));
            } else {
                ensureGroupNameAvailable(projectId, nextPhase, optional(currentGroup, "group_name", ""), groupId, user.tenantId());
            }
            assignments.add("phase = ?");
            args.add(nextPhase);
        }
        if (input.containsKey("description")) { assignments.add("description = ?"); args.add(optional(input, "description", null)); }
        if (input.containsKey("color_key")) { assignments.add("color_key = ?"); args.add(planGroupPaletteKey(input.get("color_key"))); }
        if (input.containsKey("sort_no")) { assignments.add("sort_no = ?"); args.add((int) optionalLong(input.get("sort_no"), 0)); }
        if (assignments.isEmpty()) throw badRequest("没有可修改的阶段计划字段");
        args.add(groupId); args.add(projectId); args.add(user.tenantId());
        jdbc.update("UPDATE pm_project_plan_group SET " + String.join(", ", assignments) + " WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        if (!String.valueOf(nextPhase).equals(String.valueOf(optional(currentGroup, "phase", null)))) syncGroupPlansPhase(projectId, groupId, nextPhase, user.tenantId());
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
        String targetPhase = null;
        if (groupId != null && groupId > 0) {
            Map<String, Object> target = planGroup(groupId, projectId, user.tenantId());
            targetPhase = optional(target, "phase", null);
        }
        List<Long> descendants = descendantPlanIds(projectId, planId, user.tenantId());
        descendants.add(0, planId);
        String placeholders = String.join(", ", descendants.stream().map(item -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(groupId);
        if (targetPhase != null) args.add(targetPhase);
        args.addAll(descendants);
        args.add(projectId); args.add(user.tenantId());
        jdbc.update("UPDATE pm_project_plan SET group_id = ?" + (targetPhase == null ? "" : ", phase = ?") + " WHERE id IN (" + placeholders + ") AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        audit(user, "project:plan-group:move", planId);
    }

    @Transactional
    public Map<String, Object> createPlan(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("plan", "create", user); requireProjectAccess(projectId, user, false);
        String name = required(input, "plan_name", "计划名称", 128);
        long ownerId = optionalLong(input.get("owner_id"), 0); if (ownerId != 0) validateUser(ownerId, user.tenantId());
        long parentId = optionalLong(input.get("parent_id"), 0); validatePlanParent(projectId, parentId, user.tenantId());
        double progress = progress(input.get("progress")); String status = optional(input, "status", "NOT_STARTED"); validateStatus(status, PLAN_STATUSES, "计划状态");
        String phase = optional(input, "phase", null);
        Long requestedGroupId = nullableLong(input.get("group_id"));
        Date planStart = date(input.get("planned_start_date")); Date planEnd = date(input.get("planned_end_date"));
        if (parentId == 0 && (planStart == null || planEnd == null)) throw badRequest("主计划开始和结束日期不能为空");
        validateDateRange(planStart, planEnd, "计划"); validateWithinParent(projectId, parentId, planStart, planEnd, user.tenantId());
        Map<String, Object> parentRow = null;
        Map<String, Object> projectRow = projectForUpdate(projectId, user.tenantId());
        if (parentId != 0) {
            parentRow = parentPlanForUpdate(parentId, projectId, user.tenantId());
            String parentPhase = optional(parentRow, "phase", null);
            if (phase == null) phase = parentPhase;
            if (parentPhase != null && !parentPhase.equals(phase)) throw badRequest("子计划阶段必须与主计划一致");
            Long parentGroupId = nullableLong(parentRow.get("group_id"));
            if (requestedGroupId != null && !requestedGroupId.equals(parentGroupId)) throw badRequest("子计划阶段计划必须与主计划一致");
            requestedGroupId = parentGroupId;
        } else if (phase == null) {
            phase = requestedGroupId == null ? "PLAN_INITIATION" : optional(planGroup(requestedGroupId, projectId, user.tenantId()), "phase", null);
        } else if (requestedGroupId != null) {
            String groupPhase = optional(planGroup(requestedGroupId, projectId, user.tenantId()), "phase", null);
            if (!phase.equals(groupPhase)) throw badRequest("主计划阶段必须与阶段计划一致");
        }
        validatePhase(phase, "PLAN_PHASE", user.tenantId(), false);
        validateMainPlanSequence(projectId, requestedGroupId, 0, parentId, planStart, planEnd, user.tenantId());
        String projectCode = String.valueOf(projectRow.get("project_code"));
        String mainRule = projectRow.get("plan_number_rule") == null ? DEFAULT_PLAN_NUMBER_RULE : String.valueOf(projectRow.get("plan_number_rule"));
        validatePlanNumberRule(mainRule);
        long mainSequence = 0;
        long childSequence = 0;
        String planCode;
        if (parentId == 0) {
            mainSequence = optionalLong(projectRow.get("next_plan_sequence"), 1);
            mainSequence = nextAvailableMainPlanSequence(mainRule, projectCode, mainSequence, projectId, user.tenantId());
            planCode = renderPlanCode(mainRule, projectCode, mainSequence, LocalDate.now());
        } else {
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
        jdbc.update("INSERT INTO pm_project_plan (id, tenant_id, project_id, group_id, parent_id, plan_name, plan_code, description, owner_id, planned_start_date, planned_end_date, progress, status, phase, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, requestedGroupId, parentId, name, planCode, optional(input, "description", null), ownerId == 0 ? null : ownerId, planStart, planEnd, progress, status, phase, (int) optionalLong(input.get("sort_no"), 0));
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
        Map<String, Object> current = plan(planId, projectId, user.tenantId());
        Date nextStart = input.containsKey("planned_start_date") ? date(input.get("planned_start_date")) : dateValue(current.get("planned_start_date"));
        Date nextEnd = input.containsKey("planned_end_date") ? date(input.get("planned_end_date")) : dateValue(current.get("planned_end_date"));
        long nextParent = input.containsKey("parent_id") ? optionalLong(input.get("parent_id"), 0) : optionalLong(current.get("parent_id"), 0);
        if (nextParent == 0 && (nextStart == null || nextEnd == null)) throw badRequest("主计划开始和结束日期不能为空");
        validateDateRange(nextStart, nextEnd, "计划"); validateWithinParent(projectId, nextParent, nextStart, nextEnd, user.tenantId());
        validateMainPlanSequence(projectId, nullableLong(current.get("group_id")), planId, nextParent, nextStart, nextEnd, user.tenantId());
        String nextPhase = input.containsKey("phase") ? optional(input, "phase", null) : optional(current, "phase", null);
        if (nextParent != 0) {
            Map<String, Object> parent = parentPlanForUpdate(nextParent, projectId, user.tenantId());
            String parentPhase = optional(parent, "phase", null);
            if (nextPhase == null) nextPhase = parentPhase;
            if (parentPhase != null && !parentPhase.equals(nextPhase)) throw badRequest("子计划阶段必须与主计划一致");
        }
        validatePhase(nextPhase, "PLAN_PHASE", user.tenantId(), false);
        if (input.containsKey("phase") || !String.valueOf(optional(current, "phase", "")).equals(String.valueOf(nextPhase))) { assignments.add("phase = ?"); args.add(nextPhase); }
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

    public List<Map<String, Object>> risks(long projectId, AuthUser user) {
        requireAction("project", "read", user); requireProjectAccess(projectId, user, false);
        return jdbc.queryForList("SELECT r.id, r.project_id, r.risk_code, r.occurred_date, r.project_phase, r.urgency, r.report_level, r.current_status, r.proposer_org_id, po.org_name AS proposer_org_name, r.proposer_subsystem, r.proposer_contact_name, r.proposer_contact_phone, r.involved_org_id, io.org_name AS involved_org_name, r.involved_subsystem, r.problem_description, r.expected_resolution_date, r.suggested_solution, r.current_handler_name, r.current_handler_phone, r.progress_description, r.attention_level, r.problem_nature, r.problem_domain, r.pmo_contact, r.escalation_level, r.current_problem_level, r.planned_resolution_date, r.actual_resolution_date, r.resolution_solution, r.created_by, r.created_at, r.updated_at FROM pm_project_risk r LEFT JOIN sys_org po ON po.id = r.proposer_org_id AND po.tenant_id = r.tenant_id AND po.deleted = 0 LEFT JOIN sys_org io ON io.id = r.involved_org_id AND io.tenant_id = r.tenant_id AND io.deleted = 0 WHERE r.project_id = ? AND r.tenant_id = ? AND r.deleted = 0 ORDER BY COALESCE(r.occurred_date, '9999-12-31') DESC, r.id DESC", projectId, user.tenantId()).stream().peek(row -> decorateRisk(row, user.tenantId())).toList();
    }

    @Transactional
    public Map<String, Object> createRisk(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("risk", "create", user); requireProjectAccess(projectId, user, true);
        validateRiskInput(input, user.tenantId());
        Map<String, Object> projectRow = projectForUpdate(projectId, user.tenantId());
        String rule = nonBlankOrDefault(projectRow.get("risk_number_rule"), DEFAULT_RISK_NUMBER_RULE);
        validateRiskNumberRule(rule);
        long sequence = optionalLong(projectRow.get("next_risk_sequence"), 1);
        String riskCode = renderRiskCode(rule, String.valueOf(projectRow.get("project_code")), sequence, LocalDate.now());
        long id = nextId();
        insertRisk(id, projectId, riskCode, input, user);
        jdbc.update("UPDATE pm_project SET next_risk_sequence = ? WHERE id = ? AND tenant_id = ? AND deleted = 0", sequence + 1, projectId, user.tenantId());
        audit(user, "project:risk:create", id);
        return risk(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateRisk(long projectId, long riskId, Map<String, Object> input, AuthUser user) {
        requireAction("risk", "update", user); requireProjectAccess(projectId, user, true); ensureRisk(projectId, riskId, user.tenantId());
        validateRiskInput(input, user.tenantId());
        List<String> assignments = new ArrayList<>(); List<Object> args = new ArrayList<>();
        addRiskAssignments(input, assignments, args);
        if (assignments.isEmpty()) throw badRequest("没有可修改的项目风险字段");
        args.add(riskId); args.add(projectId); args.add(user.tenantId());
        jdbc.update("UPDATE pm_project_risk SET " + String.join(", ", assignments) + " WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        audit(user, "project:risk:update", riskId);
        return risk(riskId, projectId, user.tenantId());
    }

    @Transactional
    public void deleteRisk(long projectId, long riskId, AuthUser user) {
        requireAction("risk", "delete", user); requireProjectAccess(projectId, user, true); ensureRisk(projectId, riskId, user.tenantId());
        jdbc.update("UPDATE pm_project_risk SET deleted = 1 WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", riskId, projectId, user.tenantId());
        audit(user, "project:risk:delete", riskId);
    }

    /** 评论沿用项目成员可见范围，但不授予任何风险字段修改权限。 */
    public List<Map<String, Object>> riskComments(long projectId, long riskId, AuthUser user) {
        requireAction("project", "read", user); requireProjectAccess(projectId, user, false); ensureRisk(projectId, riskId, user.tenantId());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.id, c.project_id, c.risk_id, c.user_id, u.username, u.display_name, u.avatar_object_key, o.org_name, c.comment_text, c.created_at, c.updated_at FROM pm_project_risk_comment c JOIN sys_user u ON u.id = c.user_id AND u.tenant_id = c.tenant_id AND u.deleted = 0 LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0 WHERE c.project_id = ? AND c.risk_id = ? AND c.tenant_id = ? AND c.deleted = 0 ORDER BY c.created_at DESC, c.id DESC", projectId, riskId, user.tenantId());
        rows.forEach(row -> row.put("avatar_url", storage.presignedUrl((String) row.remove("avatar_object_key"))));
        return rows;
    }

    @Transactional
    public Map<String, Object> createRiskComment(long projectId, long riskId, Map<String, Object> input, AuthUser user) {
        requireAction("project", "read", user); requireProjectAccess(projectId, user, false); ensureRisk(projectId, riskId, user.tenantId());
        String comment = required(input, "comment_text", "评论内容", 2000);
        long id = nextId();
        jdbc.update("INSERT INTO pm_project_risk_comment (id, tenant_id, project_id, risk_id, user_id, comment_text) VALUES (?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, riskId, user.id(), comment);
        jdbc.update("UPDATE pm_project_risk SET progress_description = ? WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", comment, riskId, projectId, user.tenantId());
        audit(user, "project:risk:comment", id);
        return riskComment(id, projectId, riskId, user.tenantId());
    }

    private void insertRisk(long id, long projectId, String riskCode, Map<String, Object> input, AuthUser user) {
        jdbc.update("INSERT INTO pm_project_risk (id, tenant_id, project_id, risk_code, occurred_date, project_phase, urgency, report_level, current_status, proposer_org_id, proposer_subsystem, proposer_contact_name, proposer_contact_phone, involved_org_id, involved_subsystem, problem_description, expected_resolution_date, suggested_solution, current_handler_name, current_handler_phone, progress_description, attention_level, problem_nature, problem_domain, pmo_contact, escalation_level, current_problem_level, planned_resolution_date, actual_resolution_date, resolution_solution, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), projectId, riskCode, date(input.get("occurred_date")), optional(input, "project_phase", null), optional(input, "urgency", null), optional(input, "report_level", null), optional(input, "current_status", "OPEN"), nullableLong(input.get("proposer_org_id")), optional(input, "proposer_subsystem", null), optional(input, "proposer_contact_name", null), optional(input, "proposer_contact_phone", null), nullableLong(input.get("involved_org_id")), optional(input, "involved_subsystem", null), optional(input, "problem_description", null), date(input.get("expected_resolution_date")), optional(input, "suggested_solution", null), optional(input, "current_handler_name", null), optional(input, "current_handler_phone", null), optional(input, "progress_description", null), optional(input, "attention_level", null), optional(input, "problem_nature", null), optional(input, "problem_domain", null), optional(input, "pmo_contact", null), optional(input, "escalation_level", null), optional(input, "current_problem_level", null), date(input.get("planned_resolution_date")), date(input.get("actual_resolution_date")), optional(input, "resolution_solution", null), user.id());
    }

    private void addRiskAssignments(Map<String, Object> input, List<String> assignments, List<Object> args) {
        if (input.containsKey("occurred_date")) { assignments.add("occurred_date = ?"); args.add(date(input.get("occurred_date"))); }
        if (input.containsKey("project_phase")) { assignments.add("project_phase = ?"); args.add(optional(input, "project_phase", null)); }
        if (input.containsKey("urgency")) { assignments.add("urgency = ?"); args.add(optional(input, "urgency", null)); }
        if (input.containsKey("report_level")) { assignments.add("report_level = ?"); args.add(optional(input, "report_level", null)); }
        if (input.containsKey("current_status")) { assignments.add("current_status = ?"); args.add(required(input, "current_status", "当前状态", 128)); }
        if (input.containsKey("proposer_org_id")) { assignments.add("proposer_org_id = ?"); args.add(nullableLong(input.get("proposer_org_id"))); }
        if (input.containsKey("proposer_subsystem")) { assignments.add("proposer_subsystem = ?"); args.add(optional(input, "proposer_subsystem", null)); }
        if (input.containsKey("proposer_contact_name")) { assignments.add("proposer_contact_name = ?"); args.add(optional(input, "proposer_contact_name", null)); }
        if (input.containsKey("proposer_contact_phone")) { assignments.add("proposer_contact_phone = ?"); args.add(optional(input, "proposer_contact_phone", null)); }
        if (input.containsKey("involved_org_id")) { assignments.add("involved_org_id = ?"); args.add(nullableLong(input.get("involved_org_id"))); }
        if (input.containsKey("involved_subsystem")) { assignments.add("involved_subsystem = ?"); args.add(optional(input, "involved_subsystem", null)); }
        if (input.containsKey("problem_description")) { assignments.add("problem_description = ?"); args.add(optional(input, "problem_description", null)); }
        if (input.containsKey("expected_resolution_date")) { assignments.add("expected_resolution_date = ?"); args.add(date(input.get("expected_resolution_date"))); }
        if (input.containsKey("suggested_solution")) { assignments.add("suggested_solution = ?"); args.add(optional(input, "suggested_solution", null)); }
        if (input.containsKey("current_handler_name")) { assignments.add("current_handler_name = ?"); args.add(optional(input, "current_handler_name", null)); }
        if (input.containsKey("current_handler_phone")) { assignments.add("current_handler_phone = ?"); args.add(optional(input, "current_handler_phone", null)); }
        if (input.containsKey("progress_description")) { assignments.add("progress_description = ?"); args.add(optional(input, "progress_description", null)); }
        if (input.containsKey("attention_level")) { assignments.add("attention_level = ?"); args.add(optional(input, "attention_level", null)); }
        if (input.containsKey("problem_nature")) { assignments.add("problem_nature = ?"); args.add(optional(input, "problem_nature", null)); }
        if (input.containsKey("problem_domain")) { assignments.add("problem_domain = ?"); args.add(optional(input, "problem_domain", null)); }
        if (input.containsKey("pmo_contact")) { assignments.add("pmo_contact = ?"); args.add(optional(input, "pmo_contact", null)); }
        if (input.containsKey("escalation_level")) { assignments.add("escalation_level = ?"); args.add(optional(input, "escalation_level", null)); }
        if (input.containsKey("current_problem_level")) { assignments.add("current_problem_level = ?"); args.add(optional(input, "current_problem_level", null)); }
        if (input.containsKey("planned_resolution_date")) { assignments.add("planned_resolution_date = ?"); args.add(date(input.get("planned_resolution_date"))); }
        if (input.containsKey("actual_resolution_date")) { assignments.add("actual_resolution_date = ?"); args.add(date(input.get("actual_resolution_date"))); }
        if (input.containsKey("resolution_solution")) { assignments.add("resolution_solution = ?"); args.add(optional(input, "resolution_solution", null)); }
    }

    private Map<String, Object> risk(long riskId, long projectId, long tenantId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT r.id, r.project_id, r.risk_code, r.occurred_date, r.project_phase, r.urgency, r.report_level, r.current_status, r.proposer_org_id, po.org_name AS proposer_org_name, r.proposer_subsystem, r.proposer_contact_name, r.proposer_contact_phone, r.involved_org_id, io.org_name AS involved_org_name, r.involved_subsystem, r.problem_description, r.expected_resolution_date, r.suggested_solution, r.current_handler_name, r.current_handler_phone, r.progress_description, r.attention_level, r.problem_nature, r.problem_domain, r.pmo_contact, r.escalation_level, r.current_problem_level, r.planned_resolution_date, r.actual_resolution_date, r.resolution_solution, r.created_by, r.created_at, r.updated_at FROM pm_project_risk r LEFT JOIN sys_org po ON po.id = r.proposer_org_id AND po.tenant_id = r.tenant_id AND po.deleted = 0 LEFT JOIN sys_org io ON io.id = r.involved_org_id AND io.tenant_id = r.tenant_id AND io.deleted = 0 WHERE r.id = ? AND r.project_id = ? AND r.tenant_id = ? AND r.deleted = 0", riskId, projectId, tenantId);
            decorateRisk(row, tenantId);
            return row;
        } catch (EmptyResultDataAccessException exception) {
            throw badRequest("项目风险不存在");
        }
    }

    private Map<String, Object> riskComment(long commentId, long projectId, long riskId, long tenantId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT c.id, c.project_id, c.risk_id, c.user_id, u.username, u.display_name, u.avatar_object_key, o.org_name, c.comment_text, c.created_at, c.updated_at FROM pm_project_risk_comment c JOIN sys_user u ON u.id = c.user_id AND u.tenant_id = c.tenant_id AND u.deleted = 0 LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0 WHERE c.id = ? AND c.project_id = ? AND c.risk_id = ? AND c.tenant_id = ? AND c.deleted = 0", commentId, projectId, riskId, tenantId);
            row.put("avatar_url", storage.presignedUrl((String) row.remove("avatar_object_key")));
            return row;
        } catch (EmptyResultDataAccessException exception) {
            throw badRequest("项目风险评论不存在");
        }
    }

    private void validateRiskInput(Map<String, Object> input, long tenantId) {
        validateRiskText(input, "project_phase", "项目阶段", 128);
        validateRiskText(input, "urgency", "紧急程度", 128);
        validateRiskText(input, "report_level", "上报问题级别", 128);
        validateRiskText(input, "current_status", "当前状态", 128);
        validateRiskText(input, "attention_level", "关注等级", 128);
        validateRiskText(input, "escalation_level", "是否升级", 128);
        validateRiskText(input, "current_problem_level", "当前问题级别", 128);
        validateRiskLength(input, "proposer_subsystem", "提出物理子系统", 128);
        validateRiskLength(input, "proposer_contact_name", "提出联系人", 128);
        validateRiskLength(input, "proposer_contact_phone", "提出联系方式", 64);
        validateRiskLength(input, "involved_subsystem", "涉及物理子系统", 128);
        validateRiskLength(input, "problem_description", "问题描述", 2000);
        validateRiskLength(input, "current_handler_name", "当前处理人", 128);
        validateRiskLength(input, "current_handler_phone", "当前处理人联系方式", 64);
        validateRiskLength(input, "problem_nature", "问题性质", 128);
        validateRiskLength(input, "problem_domain", "问题领域", 128);
        validateRiskLength(input, "pmo_contact", "PMO联系人", 256);
        validateRiskDate(input, "occurred_date", "发生时间");
        validateRiskDate(input, "expected_resolution_date", "期望解决时间");
        validateRiskDate(input, "planned_resolution_date", "计划解决时间");
        validateRiskDate(input, "actual_resolution_date", "实际解决时间");
        if (input.containsKey("project_phase")) validateParameter(input.get("project_phase"), "PROJECT_PHASE", "项目阶段", tenantId);
        if (input.containsKey("urgency")) validateParameter(input.get("urgency"), "RISK_URGENCY", "紧急程度", tenantId);
        if (input.containsKey("report_level")) validateParameter(input.get("report_level"), "RISK_REPORT_LEVEL", "上报问题级别", tenantId);
        validateParameter(input.containsKey("current_status") ? input.get("current_status") : "OPEN", "RISK_STATUS", "当前状态", tenantId);
        if (input.containsKey("attention_level")) validateParameter(input.get("attention_level"), "RISK_ATTENTION_LEVEL", "关注等级", tenantId);
        if (input.containsKey("escalation_level")) validateParameter(input.get("escalation_level"), "RISK_ESCALATION_LEVEL", "是否升级", tenantId);
        if (input.containsKey("current_problem_level")) validateParameter(input.get("current_problem_level"), "RISK_PROBLEM_LEVEL", "当前问题级别", tenantId);
        validateRiskOrganization(input, "proposer_org_id", tenantId);
        validateRiskOrganization(input, "involved_org_id", tenantId);
    }

    private void validateRiskText(Map<String, Object> input, String key, String label, int max) {
        if (input.containsKey(key) && (optional(input, key, null) == null || optional(input, key, null).isBlank())) throw badRequest(label + "不能为空");
        validateRiskLength(input, key, label, max);
    }

    private void validateRiskLength(Map<String, Object> input, String key, String label, int max) {
        String value = optional(input, key, null);
        if (value != null && value.length() > max) throw badRequest(label + "不能超过" + max + "个字符");
    }

    private void validateRiskDate(Map<String, Object> input, String key, String label) {
        if (input.containsKey(key)) date(input.get(key));
    }

    private void validateRiskOrganization(Map<String, Object> input, String key, long tenantId) {
        if (input.containsKey(key)) {
            Long id = nullableLong(input.get(key));
            if (id != null) validateOrganization(id, tenantId);
        }
    }

    private void validateParameter(Object value, String categoryCode, String categoryName, long tenantId) {
        String key = optionalValue(value);
        if (key == null) throw badRequest(categoryName + "不能为空");
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id WHERE c.tenant_id = ? AND (LOWER(t.dict_code) = LOWER(?) OR t.dict_name = ?) AND t.status = 1 AND t.deleted = 0 AND c.config_key = ? AND c.status = 1 AND c.deleted = 0", Integer.class, tenantId, categoryCode, categoryName, key);
        if (valid == null || valid == 0) throw badRequest(categoryName + "参数无效");
    }

    private String optionalValue(Object value) { return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim(); }

    private void ensureRisk(long projectId, long riskId, long tenantId) {
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_risk WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, riskId, projectId, tenantId);
        if (valid == null || valid == 0) throw badRequest("项目风险不存在");
    }

    private void decorateRisk(Map<String, Object> row, long tenantId) {
        row.put("project_phase_name", parameterLabelOrValue("PROJECT_PHASE", "项目阶段", row.get("project_phase"), tenantId));
        row.put("urgency_name", parameterLabelOrValue("RISK_URGENCY", "风险紧急程度", row.get("urgency"), tenantId));
        row.put("report_level_name", parameterLabelOrValue("RISK_REPORT_LEVEL", "上报问题级别", row.get("report_level"), tenantId));
        row.put("current_status_name", parameterLabelOrValue("RISK_STATUS", "风险当前状态", row.get("current_status"), tenantId));
        row.put("attention_level_name", parameterLabelOrValue("RISK_ATTENTION_LEVEL", "风险关注等级", row.get("attention_level"), tenantId));
        row.put("escalation_level_name", parameterLabelOrValue("RISK_ESCALATION_LEVEL", "风险升级级别", row.get("escalation_level"), tenantId));
        row.put("current_problem_level_name", parameterLabelOrValue("RISK_PROBLEM_LEVEL", "当前问题级别", row.get("current_problem_level"), tenantId));
    }

    private String parameterLabelOrValue(String categoryCode, String categoryName, Object value, long tenantId) {
        String label = parameterLabel(categoryCode, categoryName, value, tenantId);
        return label == null ? optionalValue(value) : label;
    }

    private void validateRiskNumberRule(String rule) { validatePlanNumberRule(rule, Set.of("PROJECT_CODE", "SEQ", "YYYY", "MM", "DD"), "风险编号规则"); }

    private String renderRiskCode(String rule, String projectCode, long sequence, LocalDate date) {
        Matcher matcher = PLAN_RULE_TOKEN.matcher(rule);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            int width = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            String value = switch (token) {
                case "PROJECT_CODE" -> projectCode;
                case "SEQ" -> width > 0 ? String.format("%0" + width + "d", sequence) : String.valueOf(sequence);
                case "YYYY" -> date.format(DateTimeFormatter.ofPattern("yyyy"));
                case "MM" -> date.format(DateTimeFormatter.ofPattern("MM"));
                case "DD" -> date.format(DateTimeFormatter.ofPattern("dd"));
                default -> throw badRequest("风险编号规则包含无效占位符");
            };
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        String rendered = result.toString();
        if (rendered.isBlank() || rendered.length() > 128) throw badRequest("生成的风险编号无效或超过128个字符");
        return rendered;
    }

    public List<Map<String, Object>> members(long projectId, AuthUser user) {
        requireAction("member", "read", user); requireProjectAccess(projectId, user, false);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT m.id, m.project_id, m.user_id, m.org_id, po.org_name, u.username, u.display_name, u.avatar_object_key, m.status, m.joined_at FROM pm_project_member m JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.deleted = 0 LEFT JOIN pm_project_org po ON po.id = m.org_id AND po.project_id = m.project_id AND po.tenant_id = m.tenant_id AND po.deleted = 0 WHERE m.project_id = ? AND m.tenant_id = ? AND m.deleted = 0 ORDER BY m.joined_at, m.id", projectId, user.tenantId());
        for (Map<String, Object> row : rows) { row.put("avatar_url", storage.presignedUrl((String) row.remove("avatar_object_key"))); row.put("roles", jdbc.queryForList("SELECT r.id, r.role_code, r.role_name FROM pm_project_member_role mr JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id AND r.deleted = 0 WHERE mr.member_id = ? AND mr.tenant_id = ? ORDER BY r.id", row.get("id"), user.tenantId())); }
        return rows;
    }

    public List<Map<String, Object>> organizations(long projectId, AuthUser user) {
        requireAction("member", "read", user); requireProjectAccess(projectId, user, false);
        return jdbc.queryForList("SELECT id, project_id, parent_id, org_code, org_name, sort_no, status, created_at, updated_at FROM pm_project_org WHERE project_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY parent_id, sort_no, id", projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createOrganization(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("member", "create", user); requireProjectAccess(projectId, user, true);
        String code = required(input, "org_code", "项目组织编码", 64);
        String name = required(input, "org_name", "项目组织名称", 128);
        long parentId = optionalLong(input.get("parent_id"), 0);
        ensureProjectOrganizationParent(projectId, parentId, 0, user.tenantId());
        ensureProjectOrganizationCodeAvailable(projectId, code, 0, user.tenantId());
        long id = nextId();
        jdbc.update("INSERT INTO pm_project_org (id, tenant_id, project_id, parent_id, org_code, org_name, sort_no, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, parentId, code, name, (int) optionalLong(input.get("sort_no"), 0), optionalLong(input.get("status"), 1));
        audit(user, "project:organization:create", id);
        return projectOrganization(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateOrganization(long projectId, long organizationId, Map<String, Object> input, AuthUser user) {
        requireAction("member", "update", user); requireProjectAccess(projectId, user, true); ensureProjectOrganization(projectId, organizationId, user.tenantId());
        List<String> assignments = new ArrayList<>(); List<Object> args = new ArrayList<>();
        if (input.containsKey("org_code")) { String code = required(input, "org_code", "项目组织编码", 64); ensureProjectOrganizationCodeAvailable(projectId, code, organizationId, user.tenantId()); assignments.add("org_code = ?"); args.add(code); }
        if (input.containsKey("org_name")) { assignments.add("org_name = ?"); args.add(required(input, "org_name", "项目组织名称", 128)); }
        if (input.containsKey("parent_id")) { long parentId = optionalLong(input.get("parent_id"), 0); ensureProjectOrganizationParent(projectId, parentId, organizationId, user.tenantId()); assignments.add("parent_id = ?"); args.add(parentId); }
        if (input.containsKey("sort_no")) { assignments.add("sort_no = ?"); args.add((int) optionalLong(input.get("sort_no"), 0)); }
        if (input.containsKey("status")) { assignments.add("status = ?"); args.add(optionalLong(input.get("status"), 1)); }
        if (assignments.isEmpty()) throw badRequest("没有可修改的项目组织字段");
        args.add(organizationId); args.add(projectId); args.add(user.tenantId());
        jdbc.update("UPDATE pm_project_org SET " + String.join(", ", assignments) + " WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        audit(user, "project:organization:update", organizationId);
        return projectOrganization(organizationId, projectId, user.tenantId());
    }

    @Transactional
    public void deleteOrganization(long projectId, long organizationId, AuthUser user) {
        requireAction("member", "delete", user); requireProjectAccess(projectId, user, true); ensureProjectOrganization(projectId, organizationId, user.tenantId());
        Integer children = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_org WHERE parent_id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, organizationId, projectId, user.tenantId());
        if (children != null && children > 0) throw badRequest("该项目组织仍有下级节点，不能删除");
        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE org_id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, organizationId, projectId, user.tenantId());
        if (members != null && members > 0) throw badRequest("该项目组织仍挂接项目成员，不能删除");
        jdbc.update("UPDATE pm_project_org SET deleted = 1 WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", organizationId, projectId, user.tenantId());
        audit(user, "project:organization:delete", organizationId);
    }

    @Transactional
    public Map<String, Object> createMember(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("member", "create", user); requireProjectAccess(projectId, user, true);
        long userId = longValue(input.get("user_id"), 0); validateUser(userId, user.tenantId());
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE project_id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, projectId, userId, user.tenantId());
        if (exists != null && exists > 0) throw badRequest("该用户已经是项目成员");
        Long orgId = nullableLong(input.get("org_id")); validateProjectOrganization(orgId, projectId, user.tenantId());
        long memberId = nextId(); addMember(projectId, userId, user.tenantId(), ids(input.get("role_ids")), orgId, memberId);
        audit(user, "project:member:create", memberId); return member(memberId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateMember(long projectId, long memberId, Map<String, Object> input, AuthUser user) {
        requireAction("member", "update", user); requireProjectAccess(projectId, user, true); ensureMember(projectId, memberId, user.tenantId());
        if (input.containsKey("status")) jdbc.update("UPDATE pm_project_member SET status = ? WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", optionalLong(input.get("status"), 1), memberId, projectId, user.tenantId());
        if (input.containsKey("org_id")) { Long orgId = nullableLong(input.get("org_id")); validateProjectOrganization(orgId, projectId, user.tenantId()); jdbc.update("UPDATE pm_project_member SET org_id = ? WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", orgId, memberId, projectId, user.tenantId()); }
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
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.id, r.project_id, r.role_code, r.role_name, r.description, r.created_at, (SELECT COUNT(*) FROM pm_project_member_role mr JOIN pm_project_member m ON m.id = mr.member_id AND m.tenant_id = mr.tenant_id AND m.deleted = 0 WHERE mr.role_id = r.id AND mr.tenant_id = r.tenant_id) AS member_count FROM pm_project_role r WHERE r.project_id = ? AND r.tenant_id = ? AND r.deleted = 0 ORDER BY r.id", projectId, user.tenantId());
        rows.forEach(row -> decorateRoleMembers(row, projectId, user.tenantId()));
        return rows;
    }

    @Transactional
    public Map<String, Object> createRole(long projectId, Map<String, Object> input, AuthUser user) {
        requireAction("role", "create", user); requireProjectAccess(projectId, user, true);
        String code = required(input, "role_code", "角色编码", 64); String name = required(input, "role_name", "角色名称", 128);
        long id = nextId(); jdbc.update("INSERT INTO pm_project_role (id, tenant_id, project_id, role_code, role_name, description) VALUES (?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, code, name, optional(input, "description", null)); saveRoleMembers(id, projectId, ids(input.get("member_ids")), user.tenantId()); audit(user, "project:role:create", id); return role(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateRole(long projectId, long roleId, Map<String, Object> input, AuthUser user) {
        requireAction("role", "update", user); requireProjectAccess(projectId, user, true); ensureRole(projectId, roleId, user.tenantId());
        List<String> assignments = new ArrayList<>(); List<Object> args = new ArrayList<>();
        if (input.containsKey("role_code")) { assignments.add("role_code = ?"); args.add(required(input, "role_code", "角色编码", 64)); }
        if (input.containsKey("role_name")) { assignments.add("role_name = ?"); args.add(required(input, "role_name", "角色名称", 128)); }
        if (input.containsKey("description")) { assignments.add("description = ?"); args.add(optional(input, "description", null)); }
        if (assignments.isEmpty() && !input.containsKey("member_ids")) throw badRequest("没有可修改的角色字段");
        if (!assignments.isEmpty()) { args.add(roleId); args.add(projectId); args.add(user.tenantId()); jdbc.update("UPDATE pm_project_role SET " + String.join(", ", assignments) + " WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", args.toArray()); }
        if (input.containsKey("member_ids")) saveRoleMembers(roleId, projectId, ids(input.get("member_ids")), user.tenantId());
        audit(user, "project:role:update", roleId); return role(roleId, projectId, user.tenantId());
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

     private Map<String, Object> project(long id, long tenantId) { try { return jdbc.queryForMap("SELECT id, project_code, project_name, description, status, plan_number_rule, child_plan_number_rule, risk_number_rule, next_plan_sequence, next_risk_sequence, owner_id, planned_start_date, planned_end_date, actual_end_date, created_at, updated_at FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("项目不存在"); } }
     private Map<String, Object> projectForUpdate(long id, long tenantId) { try { return jdbc.queryForMap("SELECT id, project_code, plan_number_rule, child_plan_number_rule, risk_number_rule, next_plan_sequence, next_risk_sequence FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", id, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("项目不存在"); } }
    private Map<String, Object> parentPlanForUpdate(long planId, long projectId, long tenantId) { try { return jdbc.queryForMap("SELECT id, group_id, plan_code, phase, next_child_plan_sequence FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", planId, projectId, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("父计划不存在"); } }
    private Map<String, Object> plan(long id, long projectId, long tenantId) { Map<String, Object> row = jdbc.queryForMap("SELECT p.id, p.project_id, p.group_id, g.group_name, p.parent_id, p.plan_name, p.plan_code, p.description, p.owner_id, u.display_name AS owner_name, p.planned_start_date, p.planned_end_date, p.progress, p.status, p.phase, p.sort_no, p.created_at, p.updated_at FROM pm_project_plan p LEFT JOIN pm_project_plan_group g ON g.id = p.group_id AND g.project_id = p.project_id AND g.tenant_id = p.tenant_id AND g.deleted = 0 LEFT JOIN sys_user u ON u.id = p.owner_id AND u.tenant_id = p.tenant_id WHERE p.id = ? AND p.project_id = ? AND p.tenant_id = ? AND p.deleted = 0", id, projectId, tenantId); row.put("stage_plan_code", row.get("group_name")); decoratePlanOrganizations(row, tenantId); return row; }
    private Map<String, Object> planForUpdate(long id, long projectId, long tenantId) { try { return jdbc.queryForMap("SELECT id, parent_id, group_id FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", id, projectId, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("计划不存在"); } }
    private Map<String, Object> planGroup(long id, long projectId, long tenantId) { try { Map<String, Object> row = jdbc.queryForMap("SELECT id, project_id, phase, group_name, CASE WHEN color_key IN ('brand', 'accent', 'success', 'warning', 'danger', 'muted') THEN color_key ELSE 'brand' END AS color_key, description, sort_no, created_at, updated_at FROM pm_project_plan_group WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", id, projectId, tenantId); row.put("stage_plan_code", row.get("group_name")); row.put("phase_name", parameterLabel("PLAN_PHASE", "计划阶段", row.get("phase"), tenantId)); return row; } catch (EmptyResultDataAccessException exception) { throw badRequest("阶段计划不存在"); } }
    private void ensureGroup(long projectId, long groupId, long tenantId) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan_group WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, groupId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("阶段计划不存在"); }
    private void ensureGroupNameAvailable(long projectId, String phase, String groupName, long excludedId, long tenantId) { Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan_group WHERE project_id = ? AND tenant_id = ? AND phase = ? AND group_name = ? AND id <> ? AND deleted = 0", Integer.class, projectId, tenantId, phase, groupName, excludedId); if (duplicate != null && duplicate > 0) throw badRequest("该阶段下已存在重复阶段计划编号"); }
    private String nextStagePlanCode(long projectId, String phase, long tenantId) {
        List<Map<String, Object>> stages = phaseOptions("PLAN_PHASE", "计划阶段", tenantId);
        int stageSequence = 0;
        for (int index = 0; index < stages.size(); index++) {
            if (phase.equals(String.valueOf(stages.get(index).get("value")))) {
                stageSequence = index + 1;
                break;
            }
        }
        if (stageSequence == 0) throw badRequest("阶段参数无效");
        Long currentSequence = jdbc.queryForObject("SELECT COALESCE(MAX(CASE WHEN group_name REGEXP '^[0-9]+-[0-9]+$' THEN CAST(SUBSTRING_INDEX(group_name, '-', -1) AS UNSIGNED) ELSE 0 END), 0) FROM pm_project_plan_group WHERE project_id = ? AND tenant_id = ? AND phase = ?", Long.class, projectId, tenantId, phase);
        return stageSequence + "-" + ((currentSequence == null ? 0 : currentSequence) + 1);
    }
    private long nextAvailableMainPlanSequence(String rule, String projectCode, long sequence, long projectId, long tenantId) {
        long candidate = Math.max(sequence, 1);
        while (true) {
            String planCode = renderPlanCode(rule, projectCode, candidate, LocalDate.now());
            Long used = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND plan_code = ?", Long.class, projectId, tenantId, planCode);
            if (used == null || used == 0) return candidate;
            candidate++;
        }
    }
     private String planGroupPaletteKey(Object value) { String key = value == null || String.valueOf(value).isBlank() ? DEFAULT_PLAN_GROUP_COLOR_TOKEN : String.valueOf(value).trim(); if (!PLAN_GROUP_COLOR_TOKENS.contains(key)) throw badRequest("阶段计划色阶无效"); return key; }
    private List<Long> descendantPlanIds(long projectId, long planId, long tenantId) { List<Long> pending = new ArrayList<>(List.of(planId)); List<Long> descendants = new ArrayList<>(); Set<Long> visited = new HashSet<>(); while (!pending.isEmpty()) { long parent = pending.remove(0); if (!visited.add(parent)) continue; List<Long> children = jdbc.queryForList("SELECT id FROM pm_project_plan WHERE parent_id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", Long.class, parent, projectId, tenantId); descendants.addAll(children); pending.addAll(children); } return descendants; }
    private void syncGroupPlansPhase(long projectId, long groupId, String phase, long tenantId) { jdbc.update("UPDATE pm_project_plan SET phase = ? WHERE group_id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", phase, groupId, projectId, tenantId); }
     private Map<String, Object> member(long id, long tenantId) { Map<String, Object> row = jdbc.queryForMap("SELECT m.id, m.project_id, m.user_id, m.org_id, po.org_name, u.username, u.display_name, m.status, m.joined_at FROM pm_project_member m JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id LEFT JOIN pm_project_org po ON po.id = m.org_id AND po.project_id = m.project_id AND po.tenant_id = m.tenant_id AND po.deleted = 0 WHERE m.id = ? AND m.tenant_id = ? AND m.deleted = 0", id, tenantId); row.put("roles", jdbc.queryForList("SELECT r.id, r.role_code, r.role_name FROM pm_project_member_role mr JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id AND r.deleted = 0 WHERE mr.member_id = ? AND mr.tenant_id = ?", id, tenantId)); return row; }
    private Map<String, Object> role(long id, long projectId, long tenantId) { Map<String, Object> row = jdbc.queryForMap("SELECT r.id, r.project_id, r.role_code, r.role_name, r.description, r.created_at FROM pm_project_role r WHERE r.id = ? AND r.project_id = ? AND r.tenant_id = ? AND r.deleted = 0", id, projectId, tenantId); decorateRoleMembers(row, projectId, tenantId); return row; }

    private void decorateRoleMembers(Map<String, Object> role, long projectId, long tenantId) {
        List<Map<String, Object>> members = jdbc.queryForList("SELECT m.id, m.user_id, u.username, u.display_name, u.avatar_object_key FROM pm_project_member_role mr JOIN pm_project_member m ON m.id = mr.member_id AND m.project_id = ? AND m.tenant_id = mr.tenant_id AND m.deleted = 0 JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.deleted = 0 WHERE mr.role_id = ? AND mr.tenant_id = ? ORDER BY u.display_name, m.id", projectId, role.get("id"), tenantId);
        members.forEach(member -> member.put("avatar_url", storage.presignedUrl((String) member.remove("avatar_object_key"))));
        role.put("members", members);
    }

    private void decorateProject(Map<String, Object> row, long tenantId) { Long id = ((Number) row.get("id")).longValue(); row.put("owner_name", jdbc.query("SELECT display_name FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0", rs -> rs.next() ? rs.getString(1) : null, row.get("owner_id"), tenantId)); row.put("member_count", jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE project_id = ? AND tenant_id = ? AND status = 1 AND deleted = 0", Integer.class, id, tenantId)); row.put("plan_count", jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, tenantId)); row.put("completed_plan_count", jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND status = 'COMPLETED' AND deleted = 0", Integer.class, id, tenantId)); Double progress = jdbc.queryForObject("SELECT AVG(progress) FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND deleted = 0", Double.class, id, tenantId); row.put("plan_progress", progress == null ? 0 : Math.round(progress * 100.0) / 100.0); }

    private AttachmentPort attachmentService() {
        if (attachmentPort == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件服务未装配");
        return attachmentPort;
    }

    private AttachmentItem withUploaderName(AttachmentItem item, long tenantId) {
        String name = jdbc.query("SELECT display_name FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0",
                rs -> rs.next() ? rs.getString(1) : null, item.uploaderId(), tenantId);
        return new AttachmentItem(item.id(), item.fileName(), item.contentType(), item.size(), item.uploaderId(), name, item.createdAt());
    }

    public Map<String, Object> options(AuthUser user) {
        requireAction("project", "read", user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project_phases", phaseOptions("PROJECT_PHASE", "项目阶段", user.tenantId()));
        result.put("plan_phases", phaseOptions("PLAN_PHASE", "计划阶段", user.tenantId()));
        result.put("risk_urgencies", phaseOptions("RISK_URGENCY", "风险紧急程度", user.tenantId()));
        result.put("risk_report_levels", phaseOptions("RISK_REPORT_LEVEL", "上报问题级别", user.tenantId()));
        result.put("risk_statuses", phaseOptions("RISK_STATUS", "风险当前状态", user.tenantId()));
        result.put("risk_attention_levels", phaseOptions("RISK_ATTENTION_LEVEL", "风险关注等级", user.tenantId()));
        result.put("risk_escalation_levels", phaseOptions("RISK_ESCALATION_LEVEL", "风险升级级别", user.tenantId()));
        result.put("risk_problem_levels", phaseOptions("RISK_PROBLEM_LEVEL", "当前问题级别", user.tenantId()));
        result.put("organizations", jdbc.queryForList("SELECT id, parent_id, org_name, status FROM sys_org WHERE tenant_id = ? AND status = 1 AND deleted = 0 ORDER BY sort_no, id", user.tenantId()));
        return result;
    }

    private String projectScope(AuthUser user) { return isSuperAdmin(user) ? "1 = 1" : "EXISTS (SELECT 1 FROM pm_project_member pm WHERE pm.project_id = pm_project.id AND pm.tenant_id = pm_project.tenant_id AND pm.user_id = " + user.id() + " AND pm.status = 1 AND pm.deleted = 0)"; }
    private void requireProjectAccess(long projectId, AuthUser user, boolean ownerOnly) { Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, projectId, user.tenantId()); if (exists == null || exists == 0) throw badRequest("项目不存在"); if (isSuperAdmin(user)) return; String sql = ownerOnly ? "SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND owner_id = ? AND deleted = 0" : "SELECT COUNT(*) FROM pm_project_member WHERE project_id = ? AND tenant_id = ? AND user_id = ? AND status = 1 AND deleted = 0"; Integer allowed = jdbc.queryForObject(sql, Integer.class, projectId, user.tenantId(), user.id()); if (allowed == null || allowed == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有该项目的操作权限"); }
     private void requireAction(String resource, String action, AuthUser user) { if (isSuperAdmin(user)) return; String base = switch (resource) { case "project" -> "project:project:list"; case "plan" -> "project:plan:list"; case "risk" -> "project:risk:list"; case "member" -> "project:member:list"; case "role" -> "project:role:list"; default -> throw badRequest("项目资源无效"); }; String permission = "read".equals(action) ? base : base + ":" + action; Integer allowed = jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND p.tenant_id = ? AND p.permission_code = ? AND p.action_code = ? AND p.status = 1 AND r.status = 1", Integer.class, user.id(), user.tenantId(), permission, action); if (allowed == null || allowed == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有" + resourceLabel(resource) + actionLabel(action) + "权限"); }
    private boolean isSuperAdmin(AuthUser user) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND ur.tenant_id = ? AND r.role_code = 'SUPER_ADMIN' AND r.status = 1 AND r.deleted = 0", Integer.class, user.id(), user.tenantId()); return count != null && count > 0; }

    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds) { addMember(projectId, userId, tenantId, roleIds, null, nextId()); }
    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds, long memberId) { addMember(projectId, userId, tenantId, roleIds, null, memberId); }
    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds, Long orgId, long memberId) { jdbc.update("INSERT INTO pm_project_member (id, tenant_id, project_id, user_id, org_id) VALUES (?, ?, ?, ?, ?)", memberId, tenantId, projectId, userId, orgId); saveMemberRoles(memberId, projectId, roleIds, tenantId); }
    private void saveMemberRoles(long memberId, long projectId, List<Long> roleIds, long tenantId) { Set<Long> ids = new HashSet<>(roleIds); jdbc.update("DELETE FROM pm_project_member_role WHERE member_id = ? AND tenant_id = ?", memberId, tenantId); for (Long roleId : ids) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_role WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, roleId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("项目角色不存在"); jdbc.update("INSERT INTO pm_project_member_role (tenant_id, member_id, role_id) VALUES (?, ?, ?)", tenantId, memberId, roleId); } }
    private void saveRoleMembers(long roleId, long projectId, List<Long> memberIds, long tenantId) { Set<Long> ids = new HashSet<>(memberIds); jdbc.update("DELETE FROM pm_project_member_role WHERE role_id = ? AND tenant_id = ?", roleId, tenantId); for (Long memberId : ids) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE id = ? AND project_id = ? AND tenant_id = ? AND status = 1 AND deleted = 0", Integer.class, memberId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("角色关联的人员必须是当前项目有效成员"); jdbc.update("INSERT INTO pm_project_member_role (tenant_id, member_id, role_id) VALUES (?, ?, ?)", tenantId, memberId, roleId); } }
    private void validatePlanParent(long projectId, long parentId, long tenantId) { if (parentId == 0) return; Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, parentId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("父计划不存在"); }
    private void validateWithinParent(long projectId, long parentId, Date start, Date end, long tenantId) { if (parentId == 0) return; Map<String, Object> parent = jdbc.queryForMap("SELECT planned_start_date, planned_end_date FROM pm_project_plan WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", parentId, projectId, tenantId); Date parentStart = dateValue(parent.get("planned_start_date")); Date parentEnd = dateValue(parent.get("planned_end_date")); if (start != null && parentStart != null && start.before(parentStart)) throw badRequest("子计划开始日期不能早于父计划"); if (end != null && parentEnd != null && end.after(parentEnd)) throw badRequest("子计划结束日期不能晚于父计划"); }
    private void validateMainPlanSequence(long projectId, Long groupId, long currentPlanId, long candidateParentId, Date candidateStart, Date candidateEnd, long tenantId) {
        if (groupId == null || groupId <= 0 || candidateParentId != 0) return;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, parent_id, planned_start_date, planned_end_date FROM pm_project_plan WHERE project_id = ? AND tenant_id = ? AND group_id = ? AND deleted = 0 ORDER BY created_at ASC, id ASC", projectId, tenantId, groupId);
        List<Date[]> sequence = new ArrayList<>();
        boolean candidateFound = false;
        for (Map<String, Object> row : rows) {
            long rowId = optionalLong(row.get("id"), 0);
            if (currentPlanId > 0 && rowId == currentPlanId) {
                candidateFound = true;
                sequence.add(new Date[]{candidateStart, candidateEnd});
            } else if (optionalLong(row.get("parent_id"), 0) == 0) {
                sequence.add(new Date[]{dateValue(row.get("planned_start_date")), dateValue(row.get("planned_end_date"))});
            }
        }
        if (currentPlanId == 0 || !candidateFound) sequence.add(new Date[]{candidateStart, candidateEnd});
        for (int index = 1; index < sequence.size(); index++) {
            Date previousEnd = sequence.get(index - 1)[1];
            Date currentStart = sequence.get(index)[0];
            if (previousEnd == null || currentStart == null || !currentStart.after(previousEnd)) {
                throw badRequest("同组内后建主计划开始日期必须大于前一个主计划结束日期");
            }
        }
    }
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
    private Map<String, Object> projectOrganization(long id, long projectId, long tenantId) { try { return jdbc.queryForMap("SELECT id, project_id, parent_id, org_code, org_name, sort_no, status, created_at, updated_at FROM pm_project_org WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", id, projectId, tenantId); } catch (EmptyResultDataAccessException exception) { throw badRequest("项目组织不存在"); } }
    private void ensureProjectOrganization(long projectId, long organizationId, long tenantId) { Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_org WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, organizationId, projectId, tenantId); if (valid == null || valid == 0) throw badRequest("项目组织不存在"); }
    private void ensureProjectOrganizationCodeAvailable(long projectId, String code, long excludedId, long tenantId) { Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_org WHERE project_id = ? AND tenant_id = ? AND org_code = ? AND id <> ? AND deleted = 0", Integer.class, projectId, tenantId, code, excludedId); if (duplicate != null && duplicate > 0) throw badRequest("项目组织编码已存在"); }
    private void ensureProjectOrganizationParent(long projectId, long parentId, long excludedId, long tenantId) { if (parentId <= 0) return; if (parentId == excludedId) throw badRequest("上级项目组织不能选择自己"); ensureProjectOrganization(projectId, parentId, tenantId); long current = parentId; while (current > 0) { if (current == excludedId) throw badRequest("不能将项目组织移动到自己的下级节点"); Long next = jdbc.queryForObject("SELECT parent_id FROM pm_project_org WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Long.class, current, projectId, tenantId); current = next == null ? 0 : next; } }
    private void validateProjectOrganization(Long orgId, long projectId, long tenantId) { if (orgId != null) ensureProjectOrganization(projectId, orgId, tenantId); }
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
    private String resourceLabel(String resource) { return switch (resource) { case "risk" -> "项目风险"; case "plan" -> "项目计划"; case "member" -> "项目成员"; case "role" -> "项目角色"; default -> "项目"; }; }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private void audit(AuthUser user, String operation, long targetId) { jdbc.update("INSERT INTO sys_operation_log (id, tenant_id, operator_id, operation_code, request_method, request_path, success) VALUES (?, ?, ?, ?, 'PROJECT', ?, 1)", nextId(), user.tenantId(), user.id(), operation, String.valueOf(targetId)); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

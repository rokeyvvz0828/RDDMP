package com.ccb.system.project;

import com.ccb.attachment.model.AttachmentItem;
import com.ccb.attachment.model.AttachmentCategory;
import com.ccb.attachment.model.AttachmentLink;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberRemovalGuard;
import com.ccb.common.audit.OperationAuditContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    private static final Set<String> PROJECT_CREATION_TYPES = Set.of("NEW", "CONTINUATION");
    private static final Set<String> PROJECT_STATUSES = Set.of("PLANNING", "RUNNING", "COMPLETED", "SUSPENDED");
    private static final Set<String> PLAN_STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "BLOCKED");
    private static final Set<String> PLAN_PARTY_TYPES = Set.of("LEAD", "COOPERATING");
    private static final Set<String> PLAN_GROUP_COLOR_TOKENS = Set.of("brand", "accent", "success", "warning", "danger", "muted");
    private static final String DEFAULT_PLAN_GROUP_COLOR_TOKEN = "brand";
    private static final String DEFAULT_PLAN_NUMBER_RULE = "{PROJECT_CODE}-P{SEQ:3}";
    private static final String DEFAULT_CHILD_PLAN_NUMBER_RULE = "{PARENT_CODE}-S{SEQ:3}";
    private static final String DEFAULT_RISK_NUMBER_RULE = "{PROJECT_CODE}-R{SEQ:3}";
    private static final List<String[]> DEFAULT_PROJECT_STAGES = List.of(
            new String[]{"PLAN_INITIATION", "立项"},
            new String[]{"PLAN_REQUIREMENT", "需求"},
            new String[]{"PLAN_DESIGN_DEVELOPMENT", "设计开发"},
            new String[]{"PLAN_DATA_MIGRATION", "数据迁移"},
            new String[]{"PLAN_TEST_ACCEPTANCE", "测试与验收"},
            new String[]{"PLAN_TRAINING_PRODUCTION_REHEARSAL", "培训及投产演练"},
            new String[]{"PLAN_PRODUCTION_LAUNCH", "投产上线"});
    private static final Pattern PLAN_RULE_TOKEN = Pattern.compile("\\{([A-Z_]+)(?::(\\d+))?}");

    private final MinioStorageService storage;
    private final AttachmentPort attachmentPort;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectRepository projectRepository;
    private MeterRegistry meterRegistry;
    private ProjectMemberRemovalGuard memberRemovalGuard = (tenantId, projectId, userId) -> { };

    /** Kept only for source-compatible legacy tests; production wiring uses the Repository constructor. */
    @Deprecated(forRemoval = true)
    public ProjectService(Object legacyPersistence, MinioStorageService storage) {
        this(storage, (AttachmentPort) null, (ProjectMemberMapper) null, (ProjectRepository) null);
    }

    @Deprecated(forRemoval = true)
    public ProjectService(Object legacyPersistence, MinioStorageService storage, AttachmentPort attachmentPort) {
        this(storage, attachmentPort, null, null);
    }

    @Deprecated(forRemoval = true)
    public ProjectService(Object legacyPersistence, MinioStorageService storage, AttachmentPort attachmentPort,
                          ProjectMemberMapper projectMemberMapper) {
        this(storage, attachmentPort, projectMemberMapper, null);
    }

    @Autowired
    public ProjectService(MinioStorageService storage, AttachmentPort attachmentPort,
                          ProjectMemberMapper projectMemberMapper, ProjectRepository projectRepository) {
        this.storage = storage;
        this.attachmentPort = attachmentPort;
        this.projectMemberMapper = projectMemberMapper;
        this.projectRepository = projectRepository;
    }

    @Autowired(required = false)
    void setMemberRemovalGuard(ProjectMemberRemovalGuard memberRemovalGuard) {
        if (memberRemovalGuard != null) this.memberRemovalGuard = memberRemovalGuard;
    }

    @Autowired(required = false)
    void setMeterRegistry(MeterRegistry meterRegistry) { this.meterRegistry = meterRegistry; }

    public List<Map<String, Object>> workbench(AuthUser user) {
        List<Map<String, Object>> rows = projectRepository.workbench(user.tenantId(), isSuperAdmin(user) ? null : user.id());
        decorateProjectsBatch(rows, user.tenantId());
        return rows;
    }

    public Map<String, Object> detail(long projectId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user);
        requireProjectAccess(projectId, user, false);
        Map<String, Object> project = project(projectId, user.tenantId());
        decorateProject(project, user.tenantId());
        project.put("plan_stages", projectStages(projectId, user.tenantId()));
        project.put("plans", plans(projectId, user));
        project.put("plan_groups", planGroups(projectId, user));
        project.put("risks", risks(projectId, user));
        project.put("members", members(projectId, user));
        project.put("roles", roles(projectId, user));
        project.put("project_organizations", organizations(projectId, user));
        return project;
    }

    public PageResult<AttachmentItem> attachments(long projectId, long page, long size, String keyword,
                                                  Long categoryId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user);
        requireProjectAccess(projectId, user, false);
        PageResult<AttachmentItem> result = attachmentService().list("PROJECT", projectId, user.tenantId(),
                new PageQuery(page, size), keyword, categoryId);
        List<AttachmentItem> records = result.records().stream()
                .map(item -> withUploaderName(item, user.tenantId()))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    public List<AttachmentCategory> attachmentCategories(long projectId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user);
        requireProjectAccess(projectId, user, false);
        return attachmentService().listCategories("PROJECT", projectId, user.tenantId());
    }

    @Transactional
    public AttachmentCategory createAttachmentCategory(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, false);
        String name = required(input == null ? Map.of() : input, "name", "分类名称", 128);
        AttachmentCategory category = attachmentService().createCategory("PROJECT", projectId, name,
                user.tenantId(), user.id());
        audit(user, "project:attachment-category:create", category.id());
        return category;
    }

    @Transactional
    public AttachmentItem uploadAttachment(long projectId, MultipartFile file, Long categoryId, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, false);
        AttachmentItem item = attachmentService().uploadAndBind("PROJECT", projectId, file, categoryId,
                user.tenantId(), user.id());
        audit(user, "project:attachment:upload", item.id());
        return withUploaderName(item, user.tenantId());
    }

    @Transactional
    public AttachmentItem updateAttachmentCategory(long projectId, long attachmentId, Long categoryId, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, false);
        AttachmentItem item = attachmentService().updateCategory(attachmentId, "PROJECT", projectId, categoryId,
                user.tenantId());
        audit(user, "project:attachment-category:update", attachmentId);
        return withUploaderName(item, user.tenantId());
    }

    public AttachmentLink previewAttachment(long projectId, long attachmentId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user);
        requireProjectAccess(projectId, user, false);
        return attachmentService().preview(attachmentId, "PROJECT", projectId, user.tenantId());
    }

    public AttachmentLink downloadAttachment(long projectId, long attachmentId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user);
        requireProjectAccess(projectId, user, false);
        return attachmentService().download(attachmentId, "PROJECT", projectId, user.tenantId());
    }

    @Transactional
    public void deleteAttachment(long projectId, long attachmentId, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, false);
        attachmentService().delete(attachmentId, "PROJECT", projectId, user.tenantId());
        audit(user, "project:attachment:delete", attachmentId);
    }

    @Transactional
    public Map<String, Object> updateSettings(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, true);
        String rule = optional(input, "plan_number_rule", DEFAULT_PLAN_NUMBER_RULE);
        validatePlanNumberRule(rule);
        String childRule = optional(input, "child_plan_number_rule", DEFAULT_CHILD_PLAN_NUMBER_RULE);
        validateChildPlanNumberRule(childRule);
        String riskRule = optional(input, "risk_number_rule", DEFAULT_RISK_NUMBER_RULE);
        validateRiskNumberRule(riskRule);
        projectRepository.updateProjectSettings(projectId, user.tenantId(), rule, childRule, riskRule);
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
        String creationType = input.containsKey("creation_type")
                ? required(input, "creation_type", "创建类型", 16) : "NEW";
        validateStatus(creationType, PROJECT_CREATION_TYPES, "创建类型");
        Date projectStart = date(input.get("planned_start_date"));
        Date projectEnd = date(input.get("planned_end_date"));
        validateDateRange(projectStart, projectEnd, "项目计划");
        Date actualEnd = date(input.get("actual_end_date"));
        validateDateRange(projectStart, actualEnd, "项目实际");
        long id = nextId();
        Map<String, Object> projectValues = new LinkedHashMap<>();
        projectValues.put("id", id); projectValues.put("tenantId", user.tenantId()); projectValues.put("projectCode", code); projectValues.put("projectName", name); projectValues.put("description", optional(input, "description", null)); projectValues.put("status", status); projectValues.put("creationType", creationType); projectValues.put("ownerId", ownerId); projectValues.put("plannedStartDate", projectStart); projectValues.put("plannedEndDate", projectEnd); projectValues.put("actualEndDate", actualEnd); projectValues.put("createdBy", user.id());
        projectRepository.createProject(projectValues);
        initializeDefaultStages(id, user.tenantId());
        long roleId = nextId();
        projectRepository.createProjectManagerRole(roleId, user.tenantId(), id);
        addMember(id, user.id(), user.tenantId(), List.of(roleId), nextId());
        if (ownerId != user.id()) addMember(id, ownerId, user.tenantId(), List.of(), nextId());
        audit(user, "project:create", id);
        return detail(id, user);
    }

    @Transactional
    public Map<String, Object> update(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, true);
        Map<String, Object> values = new LinkedHashMap<>(); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        if (input.containsKey("project_code")) values.put("projectCode", required(input, "project_code", "项目编号", 64));
        if (input.containsKey("project_name")) values.put("projectName", required(input, "project_name", "项目名称", 128));
        if (input.containsKey("creation_type")) {
            String value = required(input, "creation_type", "创建类型", 16);
            validateStatus(value, PROJECT_CREATION_TYPES, "创建类型");
            values.put("creationType", value);
        }
        if (input.containsKey("description")) values.put("description", optional(input, "description", null));
        if (input.containsKey("status")) { String value = optional(input, "status", "PLANNING"); validateStatus(value, PROJECT_STATUSES, "项目状态"); values.put("status", value); }
        if (input.containsKey("owner_id")) { long ownerId = longValue(input.get("owner_id"), 0); validateUser(ownerId, user.tenantId()); values.put("ownerId", ownerId); }
        Map<String, Object> current = project(projectId, user.tenantId());
        Date nextStart = input.containsKey("planned_start_date") ? date(input.get("planned_start_date")) : dateValue(current.get("planned_start_date"));
        Date nextEnd = input.containsKey("planned_end_date") ? date(input.get("planned_end_date")) : dateValue(current.get("planned_end_date"));
        Date nextActualEnd = input.containsKey("actual_end_date") ? date(input.get("actual_end_date")) : dateValue(current.get("actual_end_date"));
        validateDateRange(nextStart, nextEnd, "项目计划");
        validateDateRange(nextStart, nextActualEnd, "项目实际");
        if (input.containsKey("planned_start_date")) values.put("plannedStartDate", nextStart);
        if (input.containsKey("planned_end_date")) values.put("plannedEndDate", nextEnd);
        if (input.containsKey("actual_end_date")) values.put("actualEndDate", nextActualEnd);
        if (values.size() == 2) throw badRequest("没有可修改的项目字段");
        projectRepository.updateProject(values);
        audit(user, "project:update", projectId);
        return detail(projectId, user);
    }

    @Transactional
    public void delete(long projectId, AuthUser user) {
        requireProjectAction(projectId, "project", "delete", user);
        requireProjectAccess(projectId, user, true);
        if (attachmentPort != null) {
            attachmentService().enqueueBusinessDeletion("PROJECT", projectId, user.tenantId());
        }
        int changed = projectRepository.deleteProject(projectId, user.tenantId());
        if (changed == 0) throw badRequest("项目不存在或已删除");
        projectRepository.deleteProjectDependents(projectId, user.tenantId());
        audit(user, "project:delete", projectId);
    }

    public List<Map<String, Object>> stages(long projectId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user);
        requireProjectAccess(projectId, user, false);
        return projectStages(projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createStage(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, true);
        String name = required(input == null ? Map.of() : input, "stage_name", "阶段名称", 128);
        projectForUpdate(projectId, user.tenantId());
        Integer nextSort = projectRepository.nextStageSort(projectId, user.tenantId());
        int sortNo = (int) optionalLong(input == null ? null : input.get("sort_no"), nextSort == null ? 0 : nextSort);
        long id = nextId();
        String code = "PROJECT_STAGE_" + id;
        projectRepository.createProjectStage(stageValues(id, user.tenantId(), projectId, code, name, sortNo));
        audit(user, "project:stage:create", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("project_id", projectId); result.put("stage_code", code); result.put("phase", code);
        result.put("stage_name", name); result.put("phase_name", name); result.put("sort_no", sortNo); result.put("status", 1);
        result.put("has_master_plans", 0); result.put("locked", false); result.put("locked_reason", null);
        return result;
    }

    @Transactional
    public Map<String, Object> updateStage(long projectId, long stageId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "project", "update", user);
        requireProjectAccess(projectId, user, true);
        Map<String, Object> current = projectStageForUpdate(projectId, stageId, user.tenantId());
        ensureStageHasNoMasterPlans(projectId, stageId, user.tenantId());
        Map<String, Object> values = new LinkedHashMap<>(); values.put("stageId", stageId); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        if (input.containsKey("stage_name")) values.put("stageName", required(input, "stage_name", "阶段名称", 128));
        if (input.containsKey("sort_no")) values.put("sortNo", (int) optionalLong(input.get("sort_no"), 0));
        if (values.size() == 3) throw badRequest("没有可修改的项目阶段字段");
        projectRepository.updateProjectStage(values);
        audit(user, "project:stage:update", stageId);
        return projectStage(stageId, projectId, user.tenantId());
    }

    @Transactional
    public void deleteStage(long projectId, long stageId, AuthUser user) {
        requireProjectAction(projectId, "project", "delete", user);
        requireProjectAccess(projectId, user, true);
        projectStageForUpdate(projectId, stageId, user.tenantId());
        ensureStageHasNoMasterPlans(projectId, stageId, user.tenantId());
        int changed = projectRepository.deleteProjectStage(stageId, projectId, user.tenantId());
        if (changed == 0) throw badRequest("项目阶段不存在或已删除");
        audit(user, "project:stage:delete", stageId);
    }

    public List<Map<String, Object>> plans(long projectId, AuthUser user) {
        requireProjectAction(projectId, "plan", "read", user);
        requireProjectAccess(projectId, user, false);
        List<Map<String, Object>> rows = projectRepository.plans(projectId, user.tenantId());
        rows.forEach(row -> decoratePlanOrganizations(row, user.tenantId()));
        return rows;
    }

    public List<Map<String, Object>> planGroups(long projectId, AuthUser user) {
        requireProjectAction(projectId, "plan", "read", user);
        requireProjectAccess(projectId, user, false);
        List<Map<String, Object>> rows = projectRepository.planGroups(projectId, user.tenantId());
        rows.forEach(row -> {
            row.put("stage_plan_code", row.get("group_name"));
            row.put("phase_name", row.get("phase_name") == null ? row.get("phase") : row.get("phase_name"));
        });
        return rows;
    }

    @Transactional
    public Map<String, Object> createPlanGroup(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "plan", "create", user);
        requireProjectAccess(projectId, user, false);
        String phase = input.containsKey("phase") ? optional(input, "phase", null) : null;
        validateProjectStage(projectId, phase, user.tenantId(), true);
        // 锁定项目行，避免并发创建时生成相同的阶段计划编号。
        projectForUpdate(projectId, user.tenantId());
        String name = nextStagePlanCode(projectId, phase, user.tenantId());
        long id = nextId();
        String colorKey = planGroupPaletteKey(input.get("color_key"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id); values.put("tenantId", user.tenantId()); values.put("projectId", projectId); values.put("phase", phase);
        values.put("groupName", name); values.put("colorKey", colorKey); values.put("description", optional(input, "description", null)); values.put("sortNo", (int) optionalLong(input.get("sort_no"), 0));
        projectRepository.createPlanGroup(values);
        audit(user, "project:plan-group:create", id);
        return planGroup(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updatePlanGroup(long projectId, long groupId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "plan", "update", user);
        requireProjectAccess(projectId, user, false);
        ensureGroup(projectId, groupId, user.tenantId());
        Map<String, Object> currentGroup = planGroup(groupId, projectId, user.tenantId());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("groupId", groupId); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        String nextPhase = optional(currentGroup, "phase", null);
        if (input.containsKey("group_name")) {
            String name = required(input, "group_name", "阶段计划编号", 128);
            ensureGroupNameAvailable(projectId, nextPhase, name, groupId, user.tenantId());
            values.put("groupName", name);
        }
        if (input.containsKey("phase")) {
            nextPhase = optional(input, "phase", null);
            validateProjectStage(projectId, nextPhase, user.tenantId(), true);
            if (!nextPhase.equals(optional(currentGroup, "phase", null)) && !input.containsKey("group_name")) {
                projectForUpdate(projectId, user.tenantId());
                values.put("groupName", nextStagePlanCode(projectId, nextPhase, user.tenantId()));
            } else {
                ensureGroupNameAvailable(projectId, nextPhase, optional(currentGroup, "group_name", ""), groupId, user.tenantId());
            }
            values.put("phase", nextPhase);
        }
        if (input.containsKey("description")) values.put("description", optional(input, "description", null));
        if (input.containsKey("color_key")) values.put("colorKey", planGroupPaletteKey(input.get("color_key")));
        if (input.containsKey("sort_no")) values.put("sortNo", (int) optionalLong(input.get("sort_no"), 0));
        if (values.size() == 3) throw badRequest("没有可修改的阶段计划字段");
        projectRepository.updatePlanGroup(values);
        if (!String.valueOf(nextPhase).equals(String.valueOf(optional(currentGroup, "phase", null)))) syncGroupPlansPhase(projectId, groupId, nextPhase, user.tenantId());
        audit(user, "project:plan-group:update", groupId);
        return planGroup(groupId, projectId, user.tenantId());
    }

    @Transactional
    public void deletePlanGroup(long projectId, long groupId, AuthUser user) {
        requireProjectAction(projectId, "plan", "delete", user);
        requireProjectAccess(projectId, user, false);
        ensureGroup(projectId, groupId, user.tenantId());
        projectRepository.clearPlanGroup(groupId, projectId, user.tenantId());
        projectRepository.deletePlanGroup(groupId, projectId, user.tenantId());
        audit(user, "project:plan-group:delete", groupId);
    }

    @Transactional
    public void movePlanToGroup(long projectId, long planId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "plan", "update", user);
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
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("groupId", groupId); values.put("phase", targetPhase); values.put("planIds", descendants); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        projectRepository.movePlansToGroup(values);
        audit(user, "project:plan-group:move", planId);
    }

    @Transactional
    public Map<String, Object> createPlan(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "plan", "create", user); requireProjectAccess(projectId, user, false);
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
            phase = requestedGroupId == null ? defaultProjectStageCode(projectId, user.tenantId()) : optional(planGroup(requestedGroupId, projectId, user.tenantId()), "phase", null);
        } else if (requestedGroupId != null) {
            String groupPhase = optional(planGroup(requestedGroupId, projectId, user.tenantId()), "phase", null);
            if (!phase.equals(groupPhase)) throw badRequest("主计划阶段必须与阶段计划一致");
        }
        validateProjectStage(projectId, phase, user.tenantId(), false);
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
                projectRepository.updatePlanCode(parentCode, parentId, projectId, user.tenantId());
                projectRepository.updateProjectPlanSequence(parentSequence + 1, projectId, user.tenantId());
            }
            String childRule = nonBlankOrDefault(projectRow.get("child_plan_number_rule"), DEFAULT_CHILD_PLAN_NUMBER_RULE);
            validateChildPlanNumberRule(childRule);
            childSequence = optionalLong(parentRow.get("next_child_plan_sequence"), 1);
            planCode = renderChildPlanCode(childRule, parentCode, childSequence, LocalDate.now());
        }
        long id = nextId();
        Map<String, Object> planValues = new LinkedHashMap<>();
        planValues.put("id", id); planValues.put("tenantId", user.tenantId()); planValues.put("projectId", projectId); planValues.put("groupId", requestedGroupId); planValues.put("parentId", parentId); planValues.put("planName", name); planValues.put("planCode", planCode); planValues.put("description", optional(input, "description", null)); planValues.put("ownerId", ownerId == 0 ? null : ownerId); planValues.put("plannedStartDate", planStart); planValues.put("plannedEndDate", planEnd); planValues.put("progress", progress); planValues.put("status", status); planValues.put("phase", phase); planValues.put("sortNo", (int) optionalLong(input.get("sort_no"), 0));
        projectRepository.createPlan(planValues);
        if (parentId == 0) {
            projectRepository.updateProjectPlanSequence(mainSequence + 1, projectId, user.tenantId());
        } else {
            projectRepository.updateChildPlanSequence(childSequence + 1, parentId, projectId, user.tenantId());
        }
        savePlanOrganizations(id, input, user.tenantId());
        audit(user, "project:plan:create", id); return plan(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updatePlan(long projectId, long planId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "plan", "update", user); requireProjectAccess(projectId, user, false); ensurePlan(projectId, planId, user.tenantId());
        Map<String, Object> values = new LinkedHashMap<>(); values.put("planId", planId); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        if (input.containsKey("plan_name")) values.put("planName", required(input, "plan_name", "计划名称", 128));
        if (input.containsKey("description")) values.put("description", optional(input, "description", null));
        if (input.containsKey("owner_id")) { long id = optionalLong(input.get("owner_id"), 0); if (id != 0) validateUser(id, user.tenantId()); values.put("ownerId", id == 0 ? null : id); }
        if (input.containsKey("parent_id")) { long parent = optionalLong(input.get("parent_id"), 0); if (parent == planId) throw badRequest("计划不能选择自己作为父计划"); validatePlanParent(projectId, parent, user.tenantId()); values.put("parentId", parent); }
        Map<String, Object> current = plan(planId, projectId, user.tenantId());
        Date nextStart = input.containsKey("planned_start_date") ? date(input.get("planned_start_date")) : dateValue(current.get("planned_start_date"));
        Date nextEnd = input.containsKey("planned_end_date") ? date(input.get("planned_end_date")) : dateValue(current.get("planned_end_date"));
        long nextParent = input.containsKey("parent_id") ? optionalLong(input.get("parent_id"), 0) : optionalLong(current.get("parent_id"), 0);
        if (nextParent == 0 && (nextStart == null || nextEnd == null)) throw badRequest("主计划开始和结束日期不能为空");
        validateDateRange(nextStart, nextEnd, "计划"); validateWithinParent(projectId, nextParent, nextStart, nextEnd, user.tenantId());
        validateMainPlanSequence(projectId, nullableLong(current.get("group_id")), planId, nextParent, nextStart, nextEnd, user.tenantId());
        String nextPhase = input.containsKey("phase") ? optional(input, "phase", null) : optional(current, "phase", null);
        Long nextGroupId = input.containsKey("group_id") ? nullableLong(input.get("group_id")) : nullableLong(current.get("group_id"));
        if (nextParent != 0) {
            Map<String, Object> parent = parentPlanForUpdate(nextParent, projectId, user.tenantId());
            String parentPhase = optional(parent, "phase", null);
            if (nextPhase == null) nextPhase = parentPhase;
            if (parentPhase != null && !parentPhase.equals(nextPhase)) throw badRequest("子计划阶段必须与主计划一致");
        } else if (nextGroupId != null && nextGroupId > 0) {
            String groupPhase = optional(planGroup(nextGroupId, projectId, user.tenantId()), "phase", null);
            if (nextPhase == null) nextPhase = groupPhase;
            if (groupPhase != null && !groupPhase.equals(nextPhase)) throw badRequest("主计划阶段必须与阶段计划一致");
        }
        validateProjectStage(projectId, nextPhase, user.tenantId(), false);
        if (input.containsKey("phase") || !String.valueOf(optional(current, "phase", "")).equals(String.valueOf(nextPhase))) values.put("phase", nextPhase);
        if (input.containsKey("planned_start_date")) values.put("plannedStartDate", nextStart);
        if (input.containsKey("planned_end_date")) values.put("plannedEndDate", nextEnd);
        if (input.containsKey("progress")) values.put("progress", progress(input.get("progress")));
        if (input.containsKey("status")) { String status = optional(input, "status", "NOT_STARTED"); validateStatus(status, PLAN_STATUSES, "计划状态"); values.put("status", status); }
        if (input.containsKey("sort_no")) values.put("sortNo", (int) optionalLong(input.get("sort_no"), 0));
        boolean organizationChanged = input.containsKey("lead_org_id") || input.containsKey("cooperating_org_ids");
        if (values.size() == 3 && !organizationChanged) throw badRequest("没有可修改的计划字段");
        if (values.size() > 3) projectRepository.updatePlan(values);
        if (organizationChanged) savePlanOrganizations(planId, input, user.tenantId());
        audit(user, "project:plan:update", planId); return plan(planId, projectId, user.tenantId());
    }

    @Transactional
    public void deletePlan(long projectId, long planId, AuthUser user) {
        requireProjectAction(projectId, "plan", "delete", user); requireProjectAccess(projectId, user, false); ensurePlan(projectId, planId, user.tenantId());
        projectRepository.deletePlanOrganizationsForRoot(planId, projectId, user.tenantId());
        projectRepository.deletePlan(planId, projectId, user.tenantId());
        deletePlanTree(projectId, planId, user.tenantId());
        audit(user, "project:plan:delete", planId);
    }

    public List<Map<String, Object>> risks(long projectId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user); requireProjectAccess(projectId, user, false);
        return projectRepository.risks(projectId, user.tenantId()).stream().peek(row -> decorateRisk(row, user.tenantId())).toList();
    }

    @Transactional
    public Map<String, Object> createRisk(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "risk", "create", user); requireProjectAccess(projectId, user, true);
        validateRiskInput(input, user.tenantId());
        Map<String, Object> projectRow = projectForUpdate(projectId, user.tenantId());
        String rule = nonBlankOrDefault(projectRow.get("risk_number_rule"), DEFAULT_RISK_NUMBER_RULE);
        validateRiskNumberRule(rule);
        long sequence = optionalLong(projectRow.get("next_risk_sequence"), 1);
        String riskCode = renderRiskCode(rule, String.valueOf(projectRow.get("project_code")), sequence, LocalDate.now());
        long id = nextId();
        insertRisk(id, projectId, riskCode, input, user);
        projectRepository.updateProjectRiskSequence(sequence + 1, projectId, user.tenantId());
        audit(user, "project:risk:create", id);
        return risk(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateRisk(long projectId, long riskId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "risk", "update", user); requireProjectAccess(projectId, user, true); ensureRisk(projectId, riskId, user.tenantId());
        validateRiskInput(input, user.tenantId());
        Map<String, Object> values = riskValues(input);
        if (values.isEmpty()) throw badRequest("没有可修改的项目风险字段");
        values.put("riskId", riskId); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        projectRepository.updateRisk(values);
        audit(user, "project:risk:update", riskId);
        return risk(riskId, projectId, user.tenantId());
    }

    @Transactional
    public void deleteRisk(long projectId, long riskId, AuthUser user) {
        requireProjectAction(projectId, "risk", "delete", user); requireProjectAccess(projectId, user, true); ensureRisk(projectId, riskId, user.tenantId());
        projectRepository.deleteRisk(riskId, projectId, user.tenantId());
        audit(user, "project:risk:delete", riskId);
    }

    /** 评论沿用项目成员可见范围，但不授予任何风险字段修改权限。 */
    public List<Map<String, Object>> riskComments(long projectId, long riskId, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user); requireProjectAccess(projectId, user, false); ensureRisk(projectId, riskId, user.tenantId());
        List<Map<String, Object>> rows = projectRepository.riskComments(projectId, riskId, user.tenantId());
        rows.forEach(row -> row.put("avatar_url", storage.presignedUrl((String) row.remove("avatar_object_key"))));
        return rows;
    }

    @Transactional
    public Map<String, Object> createRiskComment(long projectId, long riskId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "project", "read", user); requireProjectAccess(projectId, user, false); ensureRisk(projectId, riskId, user.tenantId());
        String comment = required(input, "comment_text", "评论内容", 2000);
        long id = nextId();
        Map<String, Object> values = new LinkedHashMap<>(); values.put("id", id); values.put("tenantId", user.tenantId()); values.put("projectId", projectId); values.put("riskId", riskId); values.put("userId", user.id()); values.put("comment", comment);
        projectRepository.createRiskComment(values);
        projectRepository.updateRiskProgress(comment, riskId, projectId, user.tenantId());
        audit(user, "project:risk:comment", id);
        return riskComment(id, projectId, riskId, user.tenantId());
    }

    private void insertRisk(long id, long projectId, String riskCode, Map<String, Object> input, AuthUser user) {
        Map<String, Object> values = riskValues(input);
        values.put("id", id); values.put("tenantId", user.tenantId()); values.put("projectId", projectId); values.put("riskCode", riskCode); values.put("currentStatus", optional(input, "current_status", "OPEN")); values.put("createdBy", user.id());
        projectRepository.createRisk(values);
    }

    private Map<String, Object> riskValues(Map<String, Object> input) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (input.containsKey("occurred_date")) values.put("occurredDate", date(input.get("occurred_date")));
        if (input.containsKey("project_phase")) values.put("projectPhase", optional(input, "project_phase", null));
        if (input.containsKey("urgency")) values.put("urgency", optional(input, "urgency", null));
        if (input.containsKey("report_level")) values.put("reportLevel", optional(input, "report_level", null));
        if (input.containsKey("current_status")) values.put("currentStatus", required(input, "current_status", "当前状态", 128));
        if (input.containsKey("proposer_org_id")) values.put("proposerOrgId", nullableLong(input.get("proposer_org_id")));
        if (input.containsKey("proposer_subsystem")) values.put("proposerSubsystem", optional(input, "proposer_subsystem", null));
        if (input.containsKey("proposer_contact_name")) values.put("proposerContactName", optional(input, "proposer_contact_name", null));
        if (input.containsKey("proposer_contact_phone")) values.put("proposerContactPhone", optional(input, "proposer_contact_phone", null));
        if (input.containsKey("involved_org_id")) values.put("involvedOrgId", nullableLong(input.get("involved_org_id")));
        if (input.containsKey("involved_subsystem")) values.put("involvedSubsystem", optional(input, "involved_subsystem", null));
        if (input.containsKey("problem_description")) values.put("problemDescription", optional(input, "problem_description", null));
        if (input.containsKey("expected_resolution_date")) values.put("expectedResolutionDate", date(input.get("expected_resolution_date")));
        if (input.containsKey("suggested_solution")) values.put("suggestedSolution", optional(input, "suggested_solution", null));
        if (input.containsKey("current_handler_name")) values.put("currentHandlerName", optional(input, "current_handler_name", null));
        if (input.containsKey("current_handler_phone")) values.put("currentHandlerPhone", optional(input, "current_handler_phone", null));
        if (input.containsKey("progress_description")) values.put("progressDescription", optional(input, "progress_description", null));
        if (input.containsKey("attention_level")) values.put("attentionLevel", optional(input, "attention_level", null));
        if (input.containsKey("problem_nature")) values.put("problemNature", optional(input, "problem_nature", null));
        if (input.containsKey("problem_domain")) values.put("problemDomain", optional(input, "problem_domain", null));
        if (input.containsKey("pmo_contact")) values.put("pmoContact", optional(input, "pmo_contact", null));
        if (input.containsKey("escalation_level")) values.put("escalationLevel", optional(input, "escalation_level", null));
        if (input.containsKey("current_problem_level")) values.put("currentProblemLevel", optional(input, "current_problem_level", null));
        if (input.containsKey("planned_resolution_date")) values.put("plannedResolutionDate", date(input.get("planned_resolution_date")));
        if (input.containsKey("actual_resolution_date")) values.put("actualResolutionDate", date(input.get("actual_resolution_date")));
        if (input.containsKey("resolution_solution")) values.put("resolutionSolution", optional(input, "resolution_solution", null));
        return values;
    }

    private Map<String, Object> risk(long riskId, long projectId, long tenantId) {
        Map<String, Object> row = projectRepository.risk(riskId, projectId, tenantId);
        if (row == null) throw badRequest("项目风险不存在");
        decorateRisk(row, tenantId);
        return row;
    }

    private Map<String, Object> riskComment(long commentId, long projectId, long riskId, long tenantId) {
        Map<String, Object> row = projectRepository.riskComment(commentId, projectId, riskId, tenantId);
        if (row == null) throw badRequest("项目风险评论不存在");
        row.put("avatar_url", storage.presignedUrl((String) row.remove("avatar_object_key")));
        return row;
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
        if (projectRepository.configValueCount(tenantId, categoryCode, categoryName, key) == 0) throw badRequest(categoryName + "参数无效");
    }

    private String optionalValue(Object value) { return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim(); }

    private void ensureRisk(long projectId, long riskId, long tenantId) {
        if (projectRepository.riskCount(riskId, projectId, tenantId) == 0) throw badRequest("项目风险不存在");
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
        long startedAt = System.nanoTime();
        try {
            requireProjectAction(projectId, "member", "read", user); requireProjectAccess(projectId, user, false);
            if (projectMemberMapper == null) throw new IllegalStateException("ProjectMemberMapper is unavailable");
            List<Map<String, Object>> rows = projectMemberMapper.selectMembers(projectId, user.tenantId()).stream()
                .map(LinkedHashMap::new).map(row -> (Map<String, Object>) row).toList();
            if (rows.isEmpty()) { recordMembersQuery("success", startedAt); return rows; }
            List<Long> memberIds = rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
            Map<Long, List<Map<String, Object>>> rolesByMemberId = new HashMap<>();
            for (Map<String, Object> rawRole : projectMemberMapper.selectRolesByMemberIds(memberIds, user.tenantId())) {
                Map<String, Object> role = new LinkedHashMap<>(rawRole);
                long memberId = ((Number) role.remove("member_id")).longValue();
                rolesByMemberId.computeIfAbsent(memberId, ignored -> new ArrayList<>()).add(role);
            }
            for (Map<String, Object> row : rows) {
                long memberId = ((Number) row.get("id")).longValue();
                row.put("avatar_url", storage.presignedUrl((String) row.remove("avatar_object_key")));
                row.put("roles", rolesByMemberId.getOrDefault(memberId, List.of()));
            }
            recordMembersQuery("success", startedAt);
            return rows;
        } catch (RuntimeException exception) {
            recordMembersQuery("error", startedAt);
            throw exception;
        }
    }

    private void recordMembersQuery(String outcome, long startedAt) {
        if (meterRegistry == null) return;
        Timer.builder("ccb.project.members.query").tags("operation", "members", "scope_type", "PROJECT", "outcome", outcome)
                .register(meterRegistry).record(System.nanoTime() - startedAt, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public List<Map<String, Object>> organizations(long projectId, AuthUser user) {
        requireProjectAction(projectId, "member", "read", user); requireProjectAccess(projectId, user, false);
        return projectRepository.organizations(projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createOrganization(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "member", "create", user); requireProjectAccess(projectId, user, true);
        String code = required(input, "org_code", "项目组织编码", 64);
        String name = required(input, "org_name", "项目组织名称", 128);
        long parentId = optionalLong(input.get("parent_id"), 0);
        ensureProjectOrganizationParent(projectId, parentId, 0, user.tenantId());
        ensureProjectOrganizationCodeAvailable(projectId, code, 0, user.tenantId());
        long id = nextId();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id); values.put("tenantId", user.tenantId()); values.put("projectId", projectId);
        values.put("parent_id", parentId); values.put("org_code", code); values.put("org_name", name);
        values.put("sort_no", (int) optionalLong(input.get("sort_no"), 0)); values.put("status", optionalLong(input.get("status"), 1));
        projectRepository.createOrganization(values);
        audit(user, "project:organization:create", id);
        return projectOrganization(id, projectId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateOrganization(long projectId, long organizationId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "member", "update", user); requireProjectAccess(projectId, user, true); ensureProjectOrganization(projectId, organizationId, user.tenantId());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", organizationId); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        if (input.containsKey("org_code")) { String code = required(input, "org_code", "项目组织编码", 64); ensureProjectOrganizationCodeAvailable(projectId, code, organizationId, user.tenantId()); values.put("org_code", code); }
        if (input.containsKey("org_name")) values.put("org_name", required(input, "org_name", "项目组织名称", 128));
        if (input.containsKey("parent_id")) { long parentId = optionalLong(input.get("parent_id"), 0); ensureProjectOrganizationParent(projectId, parentId, organizationId, user.tenantId()); values.put("parent_id", parentId); }
        if (input.containsKey("sort_no")) values.put("sort_no", (int) optionalLong(input.get("sort_no"), 0));
        if (input.containsKey("status")) values.put("status", optionalLong(input.get("status"), 1));
        if (values.size() == 3) throw badRequest("没有可修改的项目组织字段");
        projectRepository.updateOrganization(values);
        audit(user, "project:organization:update", organizationId);
        return projectOrganization(organizationId, projectId, user.tenantId());
    }

    @Transactional
    public void deleteOrganization(long projectId, long organizationId, AuthUser user) {
        requireProjectAction(projectId, "member", "delete", user); requireProjectAccess(projectId, user, true); ensureProjectOrganization(projectId, organizationId, user.tenantId());
        if (projectRepository.organizationChildren(organizationId, projectId, user.tenantId()) > 0) throw badRequest("该项目组织仍有下级节点，不能删除");
        if (projectRepository.organizationMembers(organizationId, projectId, user.tenantId()) > 0) throw badRequest("该项目组织仍挂接项目成员，不能删除");
        projectRepository.deleteOrganization(organizationId, projectId, user.tenantId());
        audit(user, "project:organization:delete", organizationId);
    }

    @Transactional
    public Map<String, Object> createMember(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "member", "create", user); requireProjectAccess(projectId, user, true);
        long userId = longValue(input.get("user_id"), 0); validateUser(userId, user.tenantId());
        if (projectRepository.memberCountByUser(projectId, userId, user.tenantId()) > 0) throw badRequest("该用户已经是项目成员");
        Long orgId = nullableLong(input.get("org_id")); validateProjectOrganization(orgId, projectId, user.tenantId());
        long memberId = nextId(); addMember(projectId, userId, user.tenantId(), ids(input.get("role_ids")), orgId, memberId);
        audit(user, "project:member:create", memberId); return member(memberId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateMember(long projectId, long memberId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "member", "update", user); requireProjectAccess(projectId, user, true); ensureMember(projectId, memberId, user.tenantId());
        if (input.containsKey("status")) {
            long status = optionalLong(input.get("status"), 1);
            if (status == 0) memberRemovalGuard.requireNoPendingTasks(user.tenantId(), projectId, memberUserId(memberId, projectId, user.tenantId()));
            projectRepository.updateMemberStatus(memberId, projectId, user.tenantId(), status);
        }
        if (input.containsKey("org_id")) { Long orgId = nullableLong(input.get("org_id")); validateProjectOrganization(orgId, projectId, user.tenantId()); projectRepository.updateMemberOrganization(memberId, projectId, user.tenantId(), orgId); }
        if (input.containsKey("role_ids")) saveMemberRoles(memberId, projectId, ids(input.get("role_ids")), user.tenantId());
        audit(user, "project:member:update", memberId); return member(memberId, user.tenantId());
    }

    @Transactional
    public void deleteMember(long projectId, long memberId, AuthUser user) {
        requireProjectAction(projectId, "member", "delete", user); requireProjectAccess(projectId, user, true); ensureMember(projectId, memberId, user.tenantId());
        Long memberUserId = projectRepository.memberUserId(memberId, projectId, user.tenantId());
        Long ownerId = projectRepository.projectOwnerId(projectId, user.tenantId());
        if (memberUserId != null && memberUserId.equals(ownerId)) throw badRequest("项目负责人不能移出项目");
        if (memberUserId != null) memberRemovalGuard.requireNoPendingTasks(user.tenantId(), projectId, memberUserId);
        projectRepository.deleteMember(memberId, projectId, user.tenantId());
        audit(user, "project:member:delete", memberId);
    }

    private long memberUserId(long memberId, long projectId, long tenantId) {
        Long userId = projectRepository.memberUserId(memberId, projectId, tenantId);
        if (userId == null || userId <= 0) throw badRequest("项目成员不存在");
        return userId;
    }

    public List<Map<String, Object>> roles(long projectId, AuthUser user) {
        requireProjectAction(projectId, "role", "read", user); requireProjectAccess(projectId, user, false);
        List<Map<String, Object>> rows = projectRepository.roles(projectId, user.tenantId());
        rows.forEach(row -> decorateRoleMembers(row, projectId, user.tenantId()));
        return rows;
    }

    @Transactional
    public Map<String, Object> createRole(long projectId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "role", "create", user); requireProjectAccess(projectId, user, true);
        String code = required(input, "role_code", "角色编码", 64); String name = required(input, "role_name", "角色名称", 128);
        long id = nextId(); Map<String, Object> values = new LinkedHashMap<>(); values.put("id", id); values.put("tenantId", user.tenantId()); values.put("projectId", projectId); values.put("role_code", code); values.put("role_name", name); values.put("description", optional(input, "description", null)); projectRepository.createRole(values); saveRoleMembers(id, projectId, ids(input.get("member_ids")), user.tenantId()); audit(user, "project:role:create", id); return role(id, projectId, user.tenantId());
    }

    public Map<String, Object> rolePermissions(long projectId, long roleId, AuthUser user) {
        requireProjectAction(projectId, "role", "read", user);
        requireProjectAccess(projectId, user, false);
        ensureRole(projectId, roleId, user.tenantId());
        List<Map<String, Object>> menus = projectRepository.projectPermissionMenus(user.tenantId());
        for (Map<String, Object> menu : menus) {
            menu.put("actions", projectRepository.projectMenuActions(user.tenantId(), ((Number) menu.get("id")).longValue()));
        }
        List<Long> permissionIds = projectRepository.projectRolePermissionIds(user.tenantId(), projectId, roleId);
        return Map.of("menus", menus, "permissionIds", permissionIds);
    }

    @Transactional
    public void saveRolePermissions(long projectId, long roleId, List<?> permissionIds, AuthUser user) {
        requireProjectAction(projectId, "role", "update", user);
        requireProjectAccess(projectId, user, true);
        ensureRole(projectId, roleId, user.tenantId());
        Set<Long> ids = new HashSet<>();
        if (permissionIds != null) {
            for (Object value : permissionIds) ids.add(requiredLong(value, "权限编号"));
        }
        if (!ids.isEmpty()) {
            if (projectRepository.assignablePermissionCount(user.tenantId(), new ArrayList<>(ids)) != ids.size()) throw badRequest("权限不存在、已停用或不允许分配给项目角色");
        }
        projectRepository.deleteProjectRolePermissions(user.tenantId(), projectId, roleId);
        for (Long permissionId : ids) {
            projectRepository.addProjectRolePermission(user.tenantId(), projectId, roleId, permissionId);
        }
        audit(user, "project:role:permissions", roleId);
    }

    @Transactional
    public Map<String, Object> updateRole(long projectId, long roleId, Map<String, Object> input, AuthUser user) {
        requireProjectAction(projectId, "role", "update", user); requireProjectAccess(projectId, user, true); ensureRole(projectId, roleId, user.tenantId());
        Map<String, Object> values = new LinkedHashMap<>(); values.put("id", roleId); values.put("projectId", projectId); values.put("tenantId", user.tenantId());
        if (input.containsKey("role_code")) values.put("role_code", required(input, "role_code", "角色编码", 64));
        if (input.containsKey("role_name")) values.put("role_name", required(input, "role_name", "角色名称", 128));
        if (input.containsKey("description")) values.put("description", optional(input, "description", null));
        if (values.size() == 3 && !input.containsKey("member_ids")) throw badRequest("没有可修改的角色字段");
        if (values.size() > 3) projectRepository.updateRole(values);
        if (input.containsKey("member_ids")) saveRoleMembers(roleId, projectId, ids(input.get("member_ids")), user.tenantId());
        audit(user, "project:role:update", roleId); return role(roleId, projectId, user.tenantId());
    }

    @Transactional
    public void deleteRole(long projectId, long roleId, AuthUser user) {
        requireProjectAction(projectId, "role", "delete", user); requireProjectAccess(projectId, user, true); ensureRole(projectId, roleId, user.tenantId());
        if (projectRepository.roleMemberCount(roleId, user.tenantId()) > 0) throw badRequest("该角色仍被项目成员使用，不能删除");
        projectRepository.deleteProjectRolePermissions(user.tenantId(), projectId, roleId);
        projectRepository.deleteRole(roleId, projectId, user.tenantId()); audit(user, "project:role:delete", roleId);
    }

    public List<Map<String, Object>> userOptions(String keyword, AuthUser user) {
        requireAction("member", "read", user); return projectRepository.userOptions(user.tenantId(), keyword == null ? null : keyword.trim());
    }

     private Map<String, Object> project(long id, long tenantId) { Map<String, Object> row = projectRepository.project(id, tenantId); if (row == null) throw badRequest("项目不存在"); return row; }
     private Map<String, Object> projectForUpdate(long id, long tenantId) { Map<String, Object> row = projectRepository.projectForUpdate(id, tenantId); if (row == null) throw badRequest("项目不存在"); return row; }
    private List<Map<String, Object>> projectStages(long projectId, long tenantId) {
        ensureDefaultStages(projectId, tenantId);
        return projectRepository.projectStages(projectId, tenantId).stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            boolean locked = optionalLong(copy.get("has_master_plans"), 0) > 0;
            copy.put("locked", locked);
            copy.put("locked_reason", locked ? "该阶段已有主计划，不能编辑或删除" : null);
            return copy;
        }).toList();
    }
    private Map<String, Object> projectStage(long stageId, long projectId, long tenantId) {
        List<Map<String, Object>> rows = projectStages(projectId, tenantId).stream().filter(row -> optionalLong(row.get("id"), 0) == stageId).toList();
        if (rows.isEmpty()) throw badRequest("项目阶段不存在");
        return rows.get(0);
    }
    private Map<String, Object> projectStageForUpdate(long projectId, long stageId, long tenantId) {
        Map<String, Object> stage = projectRepository.projectStageForUpdate(stageId, projectId, tenantId);
        if (stage == null) throw badRequest("项目阶段不存在");
        return stage;
    }
    private void ensureStageHasNoMasterPlans(long projectId, long stageId, long tenantId) {
        if (projectRepository.stageMasterPlanCount(stageId, projectId, tenantId) > 0) throw badRequest("该阶段已有主计划，不能编辑或删除");
    }
    private void initializeDefaultStages(long projectId, long tenantId) {
        for (int index = 0; index < DEFAULT_PROJECT_STAGES.size(); index++) {
            String[] stage = DEFAULT_PROJECT_STAGES.get(index);
            projectRepository.createProjectStage(stageValues(nextId(), tenantId, projectId, stage[0], stage[1], index));
        }
    }
    private void ensureDefaultStages(long projectId, long tenantId) {
        for (int index = 0; index < DEFAULT_PROJECT_STAGES.size(); index++) {
            String[] stage = DEFAULT_PROJECT_STAGES.get(index);
            projectRepository.createProjectStageIfMissing(stageValues(nextId(), tenantId, projectId, stage[0], stage[1], index));
        }
    }
    private String defaultProjectStageCode(long projectId, long tenantId) {
        List<Map<String, Object>> stages = projectStages(projectId, tenantId);
        // 兼容 V83 尚未应用的旧实例；后续的阶段有效性校验仍会拒绝无效编码。
        if (stages.isEmpty()) return "PLAN_INITIATION";
        return String.valueOf(stages.get(0).get("stage_code"));
    }
    private void validateProjectStage(long projectId, String value, long tenantId, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw badRequest("阶段不能为空");
            return;
        }
        if (projectRepository.activeStageCount(projectId, tenantId, value) == 0) throw badRequest("项目阶段无效或已删除");
    }
    private Map<String, Object> parentPlanForUpdate(long planId, long projectId, long tenantId) { Map<String, Object> row = projectRepository.parentPlanForUpdate(planId, projectId, tenantId); if (row == null) throw badRequest("父计划不存在"); return row; }
     private Map<String, Object> plan(long id, long projectId, long tenantId) { Map<String, Object> row = projectRepository.plan(id, projectId, tenantId); if (row == null) throw badRequest("计划不存在"); row.put("stage_plan_code", row.get("group_name")); decoratePlanOrganizations(row, tenantId); return row; }
    private Map<String, Object> planForUpdate(long id, long projectId, long tenantId) { Map<String, Object> row = projectRepository.planForUpdate(id, projectId, tenantId); if (row == null) throw badRequest("计划不存在"); return row; }
    private Map<String, Object> planGroup(long id, long projectId, long tenantId) { Map<String, Object> row = projectRepository.planGroup(id, projectId, tenantId); if (row == null) throw badRequest("阶段计划不存在"); row.put("stage_plan_code", row.get("group_name")); row.put("phase_name", row.get("phase_name") == null ? row.get("phase") : row.get("phase_name")); return row; }
    private void ensureGroup(long projectId, long groupId, long tenantId) { if (projectRepository.planGroupCount(groupId, projectId, tenantId) == 0) throw badRequest("阶段计划不存在"); }
    private void ensureGroupNameAvailable(long projectId, String phase, String groupName, long excludedId, long tenantId) { if (projectRepository.planGroupNameCount(projectId, tenantId, phase, groupName, excludedId) > 0) throw badRequest("该阶段下已存在重复阶段计划编号"); }
    private String nextStagePlanCode(long projectId, String phase, long tenantId) {
        List<Map<String, Object>> stages = projectStages(projectId, tenantId);
        int stageSequence = 0;
        for (int index = 0; index < stages.size(); index++) {
            if (phase.equals(String.valueOf(stages.get(index).get("stage_code")))) {
                stageSequence = index + 1;
                break;
            }
        }
        if (stageSequence == 0) throw badRequest("阶段参数无效");
        long currentSequence = projectRepository.maxPlanGroupSequence(projectId, tenantId, phase);
        return stageSequence + "-" + (currentSequence + 1);
    }
    private long nextAvailableMainPlanSequence(String rule, String projectCode, long sequence, long projectId, long tenantId) {
        long candidate = Math.max(sequence, 1);
        while (true) {
            String planCode = renderPlanCode(rule, projectCode, candidate, LocalDate.now());
            if (projectRepository.planCodeCount(projectId, tenantId, planCode) == 0) return candidate;
            candidate++;
        }
    }
     private String planGroupPaletteKey(Object value) { String key = value == null || String.valueOf(value).isBlank() ? DEFAULT_PLAN_GROUP_COLOR_TOKEN : String.valueOf(value).trim(); if (!PLAN_GROUP_COLOR_TOKENS.contains(key)) throw badRequest("阶段计划色阶无效"); return key; }
    private List<Long> descendantPlanIds(long projectId, long planId, long tenantId) { List<Long> pending = new ArrayList<>(List.of(planId)); List<Long> descendants = new ArrayList<>(); Set<Long> visited = new HashSet<>(); while (!pending.isEmpty()) { long parent = pending.remove(0); if (!visited.add(parent)) continue; List<Long> children = projectRepository.childPlanIdsForUpdate(parent, projectId, tenantId); descendants.addAll(children); pending.addAll(children); } return descendants; }
    private void syncGroupPlansPhase(long projectId, long groupId, String phase, long tenantId) { projectRepository.updateGroupPlansPhase(phase, groupId, projectId, tenantId); }
     private Map<String, Object> member(long id, long tenantId) { Map<String, Object> row = projectRepository.member(id, tenantId); if (row == null) throw badRequest("项目成员不存在"); row.put("roles", projectRepository.memberRoles(id, tenantId)); return row; }
    private Map<String, Object> role(long id, long projectId, long tenantId) { Map<String, Object> row = projectRepository.role(id, projectId, tenantId); if (row == null) throw badRequest("项目角色不存在"); decorateRoleMembers(row, projectId, tenantId); return row; }

    private void decorateRoleMembers(Map<String, Object> role, long projectId, long tenantId) {
        List<Map<String, Object>> members = projectRepository.roleMembers(((Number) role.get("id")).longValue(), projectId, tenantId);
        members.forEach(member -> member.put("avatar_url", storage.presignedUrl((String) member.remove("avatar_object_key"))));
        role.put("members", members);
    }

    private void decorateProject(Map<String, Object> row, long tenantId) { Long id = ((Number) row.get("id")).longValue(); row.put("owner_name", row.get("owner_id") instanceof Number ownerId ? projectRepository.userDisplayName(ownerId.longValue(), tenantId) : null); Map<String, Object> statistic = projectRepository.projectStatistics(tenantId, List.of(id)).stream().findFirst().orElse(null); row.put("member_count", numberValue(statistic, "member_count")); row.put("plan_count", numberValue(statistic, "plan_count")); row.put("completed_plan_count", numberValue(statistic, "completed_plan_count")); Double progress = statistic == null || statistic.get("plan_progress") == null ? null : ((Number) statistic.get("plan_progress")).doubleValue(); row.put("plan_progress", progress == null ? 0 : Math.round(progress * 100.0) / 100.0); }

    private void decorateProjectsBatch(List<Map<String, Object>> rows, long tenantId) {
        if (rows.isEmpty()) return;
        List<Long> projectIds = rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        List<Long> ownerIds = rows.stream().map(row -> row.get("owner_id")).filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue()).distinct().toList();
        Map<Long, String> ownerNames = new HashMap<>();
        if (!ownerIds.isEmpty()) projectRepository.userDisplayNames(tenantId, ownerIds).forEach(owner -> ownerNames.put(((Number) owner.get("id")).longValue(), (String) owner.get("display_name")));
        Map<Long, Map<String, Object>> statistics = new HashMap<>();
        projectRepository.projectStatistics(tenantId, projectIds).forEach(statistic -> statistics.put(((Number) statistic.get("id")).longValue(), statistic));
        rows.forEach(row -> {
            Object ownerId = row.get("owner_id");
            row.put("owner_name", ownerId instanceof Number number ? ownerNames.get(number.longValue()) : null);
            Map<String, Object> statistic = statistics.get(((Number) row.get("id")).longValue());
            row.put("member_count", numberValue(statistic, "member_count"));
            row.put("plan_count", numberValue(statistic, "plan_count"));
            row.put("completed_plan_count", numberValue(statistic, "completed_plan_count"));
            Double progress = statistic == null || statistic.get("plan_progress") == null ? null : ((Number) statistic.get("plan_progress")).doubleValue();
            row.put("plan_progress", progress == null ? 0 : Math.round(progress * 100.0) / 100.0);
        });
    }
    private Map<String, Object> stageValues(long id, long tenantId, long projectId, String code, String name, int sortNo) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id); values.put("tenantId", tenantId); values.put("projectId", projectId);
        values.put("stageCode", code); values.put("stageName", name); values.put("sortNo", sortNo);
        return values;
    }

    private int numberValue(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) return 0;
        return ((Number) row.get(key)).intValue();
    }

    private AttachmentPort attachmentService() {
        if (attachmentPort == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件服务未装配");
        return attachmentPort;
    }

    private AttachmentItem withUploaderName(AttachmentItem item, long tenantId) {
        String name = projectRepository.userDisplayName(item.uploaderId(), tenantId);
        return new AttachmentItem(item.id(), item.fileName(), item.contentType(), item.size(), item.uploaderId(), name,
                item.createdAt(), item.categoryId(), item.categoryName());
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
        result.put("organizations", projectRepository.organizationOptions(user.tenantId()));
        return result;
    }

    private void requireProjectAccess(long projectId, AuthUser user, boolean ownerOnly) { if (projectRepository.projectCount(projectId, user.tenantId()) == 0) throw badRequest("项目不存在"); if (isSuperAdmin(user)) return; long allowed = ownerOnly ? projectRepository.projectOwnerAccessCount(projectId, user.tenantId(), user.id()) : projectRepository.projectMemberAccessCount(projectId, user.tenantId(), user.id()); if (allowed == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有该项目的操作权限"); }
    private void requireProjectAction(long projectId, String resource, String action, AuthUser user) {
        if (isSuperAdmin(user)) return;
        String base = switch (resource) { case "project" -> "project:project:list"; case "plan" -> "project:plan:list"; case "risk" -> "project:risk:list"; case "member" -> "project:member:list"; case "role" -> "project:role:list"; default -> throw badRequest("项目资源无效"); };
        String permission = "read".equals(action) ? base : base + ":" + action;
        if (projectRepository.projectActionCount(projectId, user.tenantId(), user.id(), permission, action) == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有" + resourceLabel(resource) + actionLabel(action) + "权限");
    }
     private void requireAction(String resource, String action, AuthUser user) { if (isSuperAdmin(user)) return; String base = switch (resource) { case "project" -> "project:project:list"; case "plan" -> "project:plan:list"; case "risk" -> "project:risk:list"; case "member" -> "project:member:list"; case "role" -> "project:role:list"; default -> throw badRequest("项目资源无效"); }; String permission = "read".equals(action) ? base : base + ":" + action; boolean allowed = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority())); if (!allowed) throw new BusinessException(ErrorCode.FORBIDDEN, "没有" + resourceLabel(resource) + actionLabel(action) + "权限"); }
    private boolean isSuperAdmin(AuthUser user) { return projectRepository.superAdminCount(user.id(), user.tenantId()) > 0; }

    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds) { addMember(projectId, userId, tenantId, roleIds, null, nextId()); }
    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds, long memberId) { addMember(projectId, userId, tenantId, roleIds, null, memberId); }
    private void addMember(long projectId, long userId, long tenantId, List<Long> roleIds, Long orgId, long memberId) { Map<String, Object> values = new LinkedHashMap<>(); values.put("id", memberId); values.put("tenantId", tenantId); values.put("projectId", projectId); values.put("userId", userId); values.put("orgId", orgId); projectRepository.createMember(values); saveMemberRoles(memberId, projectId, roleIds, tenantId); }
    private void saveMemberRoles(long memberId, long projectId, List<Long> roleIds, long tenantId) { Set<Long> ids = new HashSet<>(roleIds); projectRepository.deleteMemberRoles(memberId, tenantId); for (Long roleId : ids) { if (projectRepository.roleCount(roleId, projectId, tenantId) == 0) throw badRequest("项目角色不存在"); projectRepository.addMemberRole(memberId, roleId, tenantId); } }
    private void saveRoleMembers(long roleId, long projectId, List<Long> memberIds, long tenantId) { Set<Long> ids = new HashSet<>(memberIds); projectRepository.deleteRoleMemberLinks(roleId, tenantId); for (Long memberId : ids) { if (projectRepository.activeMemberCount(memberId, projectId, tenantId) == 0) throw badRequest("角色关联的人员必须是当前项目有效成员"); projectRepository.addMemberRole(memberId, roleId, tenantId); } }
    private void validatePlanParent(long projectId, long parentId, long tenantId) { if (parentId == 0) return; if (projectRepository.planCount(parentId, projectId, tenantId) == 0) throw badRequest("父计划不存在"); }
    private void validateWithinParent(long projectId, long parentId, Date start, Date end, long tenantId) { if (parentId == 0) return; Map<String, Object> parent = projectRepository.planDates(parentId, projectId, tenantId); if (parent == null) throw badRequest("父计划不存在"); Date parentStart = dateValue(parent.get("planned_start_date")); Date parentEnd = dateValue(parent.get("planned_end_date")); if (start != null && parentStart != null && start.before(parentStart)) throw badRequest("子计划开始日期不能早于父计划"); if (end != null && parentEnd != null && end.after(parentEnd)) throw badRequest("子计划结束日期不能晚于父计划"); }
    private void validateMainPlanSequence(long projectId, Long groupId, long currentPlanId, long candidateParentId, Date candidateStart, Date candidateEnd, long tenantId) {
        if (groupId == null || groupId <= 0 || candidateParentId != 0) return;
        List<Map<String, Object>> rows = projectRepository.groupPlanDates(projectId, tenantId, groupId);
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
    private List<Map<String, Object>> phaseOptions(String categoryCode, String categoryName, long tenantId) { return projectRepository.phaseOptions(tenantId, categoryCode, categoryName); }
    private void validatePhase(String value, String categoryCode, long tenantId, boolean required) { if (value == null || value.isBlank()) { if (required) throw badRequest("阶段不能为空"); return; } if (projectRepository.configValueCount(tenantId, categoryCode, phaseCategoryName(categoryCode), value) == 0) throw badRequest("阶段参数无效"); }
    private String parameterLabel(String categoryCode, String categoryName, Object value, long tenantId) { return value == null ? null : projectRepository.configLabel(tenantId, categoryCode, categoryName, value); }
    private String phaseCategoryName(String categoryCode) { return "PLAN_PHASE".equals(categoryCode) ? "计划阶段" : "项目阶段"; }
    private Date dateValue(Object value) { if (value == null) return null; if (value instanceof Date date) return date; return date(value); }
    private void savePlanOrganizations(long planId, Map<String, Object> input, long tenantId) { projectRepository.deletePlanOrganizations(planId, tenantId); long leadId = optionalLong(input.get("lead_org_id"), 0); if (leadId > 0) { validateOrganization(leadId, tenantId); projectRepository.createPlanOrganization(planId, leadId, "LEAD", tenantId); } for (Long orgId : ids(input.get("cooperating_org_ids"))) { validateOrganization(orgId, tenantId); projectRepository.createPlanOrganization(planId, orgId, "COOPERATING", tenantId); } }
    private void decoratePlanOrganizations(Map<String, Object> row, long tenantId) { long planId = ((Number) row.get("id")).longValue(); List<Map<String, Object>> parties = projectRepository.planOrganizations(planId, tenantId); row.put("lead_org_id", parties.stream().filter(item -> "LEAD".equals(item.get("party_type"))).map(item -> item.get("org_id")).findFirst().orElse(null)); row.put("lead_org_name", parties.stream().filter(item -> "LEAD".equals(item.get("party_type"))).map(item -> item.get("org_name")).findFirst().orElse(null)); row.put("cooperating_org_ids", parties.stream().filter(item -> "COOPERATING".equals(item.get("party_type"))).map(item -> item.get("org_id")).toList()); row.put("cooperating_org_names", parties.stream().filter(item -> "COOPERATING".equals(item.get("party_type"))).map(item -> item.get("org_name")).toList()); if (row.get("phase_name") == null) row.put("phase_name", row.get("phase")); }
    private void validateOrganization(long orgId, long tenantId) { if (projectRepository.activeOrganizationCount(orgId, tenantId) == 0) throw badRequest("组织不存在或已停用"); }
    private void ensurePlan(long projectId, long planId, long tenantId) { validatePlanParent(projectId, planId, tenantId); }
    private void deletePlanTree(long projectId, long planId, long tenantId) {
        List<Long> pending = new ArrayList<>(List.of(planId));
        Set<Long> all = new HashSet<>();
        while (!pending.isEmpty()) {
            long parent = pending.remove(0);
            if (!all.add(parent)) continue;
            List<Long> children = projectRepository.childPlanIds(parent, projectId, tenantId);
            pending.addAll(children);
        }
        for (Long id : all) {
            projectRepository.deletePlanOrganizations(id, tenantId);
            projectRepository.deletePlan(id, projectId, tenantId);
        }
    }
    private void ensureMember(long projectId, long memberId, long tenantId) { if (projectRepository.memberCount(memberId, projectId, tenantId) == 0) throw badRequest("项目成员不存在"); }
    private void ensureRole(long projectId, long roleId, long tenantId) { if (projectRepository.roleCount(roleId, projectId, tenantId) == 0) throw badRequest("项目角色不存在"); }
    private Map<String, Object> projectOrganization(long id, long projectId, long tenantId) { Map<String, Object> organization = projectRepository.organization(id, projectId, tenantId); if (organization == null) throw badRequest("项目组织不存在"); return organization; }
    private void ensureProjectOrganization(long projectId, long organizationId, long tenantId) { if (projectRepository.organizationCount(organizationId, projectId, tenantId) == 0) throw badRequest("项目组织不存在"); }
    private void ensureProjectOrganizationCodeAvailable(long projectId, String code, long excludedId, long tenantId) { if (projectRepository.organizationCodeCount(projectId, tenantId, code, excludedId) > 0) throw badRequest("项目组织编码已存在"); }
    private void ensureProjectOrganizationParent(long projectId, long parentId, long excludedId, long tenantId) { if (parentId <= 0) return; if (parentId == excludedId) throw badRequest("上级项目组织不能选择自己"); ensureProjectOrganization(projectId, parentId, tenantId); long current = parentId; while (current > 0) { if (current == excludedId) throw badRequest("不能将项目组织移动到自己的下级节点"); Long next = projectRepository.organizationParent(current, projectId, tenantId); current = next == null ? 0 : next; } }
    private void validateProjectOrganization(Long orgId, long projectId, long tenantId) { if (orgId != null) ensureProjectOrganization(projectId, orgId, tenantId); }
    private void validateUser(long userId, long tenantId) { if (projectRepository.activeUserCount(userId, tenantId) == 0) throw badRequest("用户不存在或已停用"); }
    private String required(Map<String, Object> input, String key, String label, int max) { String value = optional(input, key, null); if (value == null) throw badRequest(label + "不能为空"); if (value.length() > max) throw badRequest(label + "不能超过" + max + "个字符"); return value; }
    private String optional(Map<String, Object> input, String key, String fallback) { Object value = input.get(key); if (value == null || String.valueOf(value).isBlank()) return fallback; return String.valueOf(value).trim(); }
    private String nonBlankOrDefault(Object value, String fallback) { return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim(); }
    private long longValue(Object value, long fallback) { return value == null || String.valueOf(value).isBlank() ? fallback : optionalLong(value, fallback); }
    private long requiredLong(Object value, String label) { long parsed = optionalLong(value, 0); if (parsed <= 0) throw badRequest(label + "无效"); return parsed; }
    private long optionalLong(Object value, long fallback) { try { return value == null || String.valueOf(value).isBlank() ? fallback : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException exception) { throw badRequest("数字格式无效"); } }
    private Long nullableLong(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; long parsed = optionalLong(value, 0); return parsed <= 0 ? null : parsed; }
    private double progress(Object value) { double result; try { result = value == null || String.valueOf(value).isBlank() ? 0 : Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException exception) { throw badRequest("计划进度格式无效"); } if (result < 0 || result > 100) throw badRequest("计划进度必须在0到100之间"); return result; }
    private Date date(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; try { return Date.valueOf(LocalDate.parse(String.valueOf(value))); } catch (RuntimeException exception) { throw badRequest("日期格式必须为yyyy-MM-dd"); } }
    private List<Long> ids(Object value) { if (!(value instanceof Collection<?> values)) return List.of(); return values.stream().map(item -> optionalLong(item, 0)).filter(item -> item > 0).distinct().toList(); }
    private void validateStatus(String value, Set<String> allowed, String label) { if (!allowed.contains(value)) throw badRequest(label + "无效"); }
    private String actionLabel(String action) { return switch (action) { case "read" -> "查看"; case "create" -> "新增"; case "delete" -> "删除"; default -> "编辑"; }; }
    private String resourceLabel(String resource) { return switch (resource) { case "risk" -> "项目风险"; case "plan" -> "项目计划"; case "member" -> "项目成员"; case "role" -> "项目角色"; default -> "项目"; }; }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private void audit(AuthUser user, String operation, long targetId) {
        String[] segments = operation.split(":", 4);
        String targetType = segments.length > 1 ? segments[1] : "project";
        if (OperationAuditContext.capture(operation, targetType, String.valueOf(targetId), null)) return;
        projectRepository.createAudit(nextId(), user.tenantId(), user.id(), operation, String.valueOf(targetId));
    }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

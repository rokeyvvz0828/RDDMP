package com.ccb.release.application.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictReport;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryItemType;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.HistoricalApplication;
import com.ccb.release.application.model.ReleaseApplicationModels.StateActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.integration.ReleaseWorkflowStore;
import com.ccb.release.integration.ReleaseWorkflowStore.AttachmentSnapshot;
import com.ccb.release.integration.ReleaseWorkflowStore.RoundSnapshot;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.ResolvedBinding;
import com.ccb.release.workflow.service.ReleaseWorkflowBindingService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowStartDefinitionCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import com.ccb.workflow.integration.WorkflowTerminateCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ReleaseSubmissionService {
    public static final String MODULE_CODE = "release";
    public static final String MODULE_NAME = "配置管理";
    public static final String BUSINESS_TYPE = "release_application";
    private static final String TEST_REPORT = "TEST_REPORT";
    private static final Set<String> ATTACHMENT_CATEGORIES = Set.of(TEST_REPORT, "SUPPORTING");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReleaseApplicationStore applications;
    private final ReleaseWindowStore windows;
    private final ReleaseScenarioPolicy scenarios;
    private final ReleaseApplicationService applicationService;
    private final ReleaseWorkflowStore workflowStore;
    private final ReleaseWorkflowBindingService workflowBindings;
    private final ProjectAccessService projectAccessService;
    private final WorkflowBusinessGateway workflowGateway;
    private final AttachmentGateway attachmentGateway;
    private final ObjectMapper objectMapper;

    public ReleaseSubmissionService(ReleaseApplicationStore applications, ReleaseWindowStore windows,
                                    ReleaseScenarioPolicy scenarios, ReleaseApplicationService applicationService,
                                    ReleaseWorkflowStore workflowStore, ReleaseWorkflowBindingService workflowBindings,
                                    ProjectAccessService projectAccessService,
                                    WorkflowBusinessGateway workflowGateway,
                                    AttachmentGateway attachmentGateway, ObjectMapper objectMapper) {
        this.applications = applications;
        this.windows = windows;
        this.scenarios = scenarios;
        this.applicationService = applicationService;
        this.workflowStore = workflowStore;
        this.workflowBindings = workflowBindings;
        this.projectAccessService = projectAccessService;
        this.workflowGateway = workflowGateway;
        this.attachmentGateway = attachmentGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubmitResult submit(String code, SubmitRequest request, AuthUser user, boolean elevated) {
        if (request == null) throw badRequest("提交信息不能为空");
        Application application = requireApplication(code, user, true);
        ensureOwner(application, user, elevated);
        if (!Set.of(Status.DRAFT, Status.RETURNED, Status.WITHDRAWN).contains(application.status())) {
            throw conflict("当前状态不允许提交审批");
        }
        requireVersion(request.rowVersion(), application.rowVersion());

        ConflictReport conflicts = application.emergency()
                ? ConflictReport.empty() : applicationService.conflicts(application.applicationCode(), user);
        if (conflicts.hasInReview()) {
            throw conflict("前一申请仍在审批中，请先撤销后继续申请");
        }
        if (conflicts.hasConflicts() && !Objects.equals(conflicts.conflictToken(), normalized(request.conflictToken()))) {
            throw conflict("历史申请信息已变化，请确认冲突详情后重新提交");
        }
        ReleaseScenarioPolicy.Scenario scenario = deriveScenario(application, conflicts);
        validateSubmissionFields(application, scenario.versionType());
        ResolvedBinding workflowBinding = workflowBindings.resolve(application.projectId(), scenario.workflowScene(), user);

        if (application.emergency() && workflowStore.findReceivingWindow(application.tenantId(),
                application.projectId(), LocalDateTime.now(BUSINESS_ZONE)).isEmpty()) {
            throw conflict("当前项目没有可承接应急版本的投产窗口，请先维护当前或未来窗口");
        }

        bindAttachments(application, request.attachments(), user);
        List<AttachmentSnapshot> attachments = workflowStore.findActiveAttachments(application.tenantId(), application.id());
        if (application.emergency() && attachments.stream().noneMatch(item -> TEST_REPORT.equals(item.category()))) {
            throw badRequest("应急版本必须关联测试报告附件");
        }

        int roundNo = workflowStore.nextRoundNo(application.tenantId(), application.id());
        String digest = digest(application, scenario, workflowBinding, attachments);
        long roundId = workflowStore.insertStartingRound(application, roundNo, workflowBinding.workflowCode(), digest);
        WorkflowBusinessContext context = new WorkflowBusinessContext(MODULE_CODE, MODULE_NAME, BUSINESS_TYPE, application.applicationCode(),
                businessTitle(application), roundNo, application.projectId(), application.projectName(),
                "/release/applications/" + application.applicationCode(), digest);
        WorkflowStartResult started = workflowGateway.startByDefinitionId(new WorkflowStartDefinitionCommand(
                workflowBinding.workflowDefinitionId(), context,
                workflowVariables(application, scenario)), user);
        validateWorkflowResult(started, context);
        if (!workflowStore.completeWorkflowStart(roundId, application.tenantId(), started)) {
            throw conflict("审批轮次状态已变化，请刷新后重试");
        }
        if (!workflowStore.transitionApplicationToReview(application, request.rowVersion(), scenario.versionType(),
                scenario.characteristic(), workflowBinding.workflowCode(), user.id())) {
            throw conflict("版本申请已被其他人修改，请刷新后重试");
        }
        applications.appendEvent(nextId(), application.tenantId(), application.id(), "SUBMITTED", application.status(),
                Status.IN_REVIEW, null, json(Map.of("roundNo", roundNo, "workflowInstanceId", started.instanceId(),
                        "workflowDefinitionId", started.definitionId(), "workflowDefinitionVersion", started.definitionVersion(),
                        "dataDigest", digest)), user.id(), user.displayName());
        return new SubmitResult(application.applicationCode(), Status.IN_REVIEW.name(), application.rowVersion() + 1,
                roundNo, started.instanceId(), started.definitionId(), started.definitionVersion(), digest);
    }

    @Transactional
    public WorkflowActionResult withdraw(String code, StateActionRequest request, AuthUser user, boolean elevated) {
        if (request == null) throw badRequest("撤回信息不能为空");
        Application application = requireApplication(code, user, true);
        ensureOwner(application, user, elevated);
        if (application.status() != Status.IN_REVIEW) throw conflict("只有审批中的申请可以撤回");
        requireVersion(request.rowVersion(), application.rowVersion());
        String reason = required(request.reason(), "撤回原因", 1000);
        RoundSnapshot round = workflowStore.findLatestRoundForUpdate(application.tenantId(), application.id())
                .orElseThrow(() -> conflict("版本申请没有有效审批轮次"));
        if (round.workflowInstanceId() == null || !"IN_REVIEW".equals(round.roundStatus())) {
            throw conflict("审批流程当前不可撤回，请刷新后重试");
        }
        if (!workflowStore.markWithdrawalRequested(application.tenantId(), round.id())) {
            throw conflict("审批流程状态已变化，请刷新后重试");
        }
        workflowGateway.terminate(new WorkflowTerminateCommand(round.workflowInstanceId(), BUSINESS_TYPE,
                application.applicationCode(), round.roundNo(), reason), user);
        applications.appendEvent(nextId(), application.tenantId(), application.id(), "WITHDRAW_REQUESTED",
                Status.IN_REVIEW, Status.IN_REVIEW, reason,
                json(Map.of("roundNo", round.roundNo(), "workflowInstanceId", round.workflowInstanceId())),
                user.id(), user.displayName());
        return new WorkflowActionResult(application.applicationCode(), Status.IN_REVIEW.name(),
                "WITHDRAW_REQUESTED", round.roundNo(), round.workflowInstanceId(), application.rowVersion());
    }

    @Transactional
    public WorkflowActionResult conflictCancel(String code, StateActionRequest request, AuthUser user, boolean elevated) {
        if (request == null) throw badRequest("取消信息不能为空");
        Application application = requireApplication(code, user, true);
        ensureOwner(application, user, elevated);
        if (application.status() != Status.IN_REVIEW) throw conflict("只有审批中的申请可以取消");
        requireVersion(request.rowVersion(), application.rowVersion());
        String reason = required(request.reason(), "取消原因", 1000);
        RoundSnapshot round = workflowStore.findLatestRoundForUpdate(application.tenantId(), application.id())
                .orElseThrow(() -> conflict("版本申请没有有效审批轮次"));
        if (round.workflowInstanceId() == null || !"IN_REVIEW".equals(round.roundStatus())) {
            throw conflict("审批流程当前不可取消，请刷新后重试");
        }
        if (!workflowStore.markCancelRequested(application.tenantId(), round.id())) {
            throw conflict("审批流程状态已变化，请刷新后重试");
        }
        workflowGateway.terminate(new WorkflowTerminateCommand(round.workflowInstanceId(), BUSINESS_TYPE,
                application.applicationCode(), round.roundNo(), reason), user);
        applications.appendEvent(nextId(), application.tenantId(), application.id(), "CANCEL_REQUESTED",
                Status.IN_REVIEW, Status.IN_REVIEW, reason,
                json(Map.of("roundNo", round.roundNo(), "workflowInstanceId", round.workflowInstanceId())),
                user.id(), user.displayName());
        return new WorkflowActionResult(application.applicationCode(), Status.IN_REVIEW.name(),
                "CANCEL_REQUESTED", round.roundNo(), round.workflowInstanceId(), application.rowVersion());
    }

    public RoundView currentRound(String code, AuthUser user) {
        Application application = requireApplication(code, user, false);
        return workflowStore.findLatestRound(application.tenantId(), application.id()).map(RoundView::from).orElse(null);
    }

    public List<AttachmentView> attachments(String code, AuthUser user) {
        Application application = requireApplication(code, user, false);
        return workflowStore.findActiveAttachments(application.tenantId(), application.id()).stream()
                .map(item -> new AttachmentView(item.attachmentId(), item.category(), item.fileName()))
                .toList();
    }

    @Transactional
    public AttachmentDeleteResult deleteAttachment(String code, long attachmentId, StateActionRequest request,
                                                   AuthUser user, boolean elevated) {
        if (request == null) throw badRequest("附件删除信息不能为空");
        Application application = requireApplication(code, user, true);
        ensureOwner(application, user, elevated);
        if (!Set.of(Status.DRAFT, Status.RETURNED, Status.WITHDRAWN).contains(application.status())) {
            throw conflict("当前状态不允许删除附件");
        }
        requireVersion(request.rowVersion(), application.rowVersion());
        String reason = required(request.reason(), "删除原因", 1000);
        boolean exists = workflowStore.findActiveAttachments(application.tenantId(), application.id()).stream()
                .anyMatch(item -> item.attachmentId() == attachmentId);
        if (!exists) throw badRequest("申请附件不存在");
        attachmentGateway.deleteBound(attachmentId, BUSINESS_TYPE, application.applicationCode(), user);
        if (!workflowStore.retireAttachment(application.tenantId(), application.id(), attachmentId)
                || !workflowStore.bumpEditableApplicationVersion(application, application.rowVersion(), user.id())) {
            throw conflict("版本申请已被其他人修改，请刷新后重试");
        }
        applications.appendEvent(nextId(), application.tenantId(), application.id(), "ATTACHMENT_DELETED",
                application.status(), application.status(), reason, json(Map.of("attachmentId", attachmentId)),
                user.id(), user.displayName());
        return new AttachmentDeleteResult(attachmentId, application.rowVersion() + 1);
    }

    private ReleaseScenarioPolicy.Scenario deriveScenario(Application application, ConflictReport conflicts) {
        if (application.emergency()) return scenarios.emergency(false);
        ReleaseWindow window = windows.findById(application.windowId(), application.tenantId())
                .orElseThrow(() -> badRequest("投产窗口不存在"));
        ReleaseScenarioPolicy.Scenario scenario = scenarios.nonEmergency(window, hasAdditional(conflicts));
        if (!scenario.windowAvailable()) throw conflict(scenario.windowUnavailableReason());
        return scenario;
    }

    private boolean hasAdditional(ConflictReport report) {
        return report.applications().stream().anyMatch(this::isReleasedVersionChange);
    }

    private boolean isReleasedVersionChange(HistoricalApplication historical) {
        return Status.RELEASED.name().equals(historical.application().status()) && !historical.versionChanges().isEmpty();
    }

    private void validateSubmissionFields(Application application, VersionType versionType) {
        if (application.deliveries() == null || application.deliveries().isEmpty()) {
            throw badRequest("至少添加一个交付单元或文件介质");
        }
        if (application.emergency()) {
            required(application.emergencyDescription(), "测试缺陷及应急情况说明", 1000);
            if (application.windowId() != null || !application.requirementCodes().isEmpty()) {
                throw conflict("应急版本不能选择投产窗口或登记需求编号");
            }
        } else {
            if (application.windowId() == null || application.requirementCodes().isEmpty()) {
                throw conflict("非应急版本必须选择投产窗口并至少填写一个需求编号");
            }
            if (versionType == VersionType.URGENT) required(application.urgentReason(), "紧急原因", 1000);
        }
    }

    private void bindAttachments(Application application, List<AttachmentInput> inputs, AuthUser user) {
        List<AttachmentSnapshot> existing = workflowStore.findActiveAttachments(application.tenantId(), application.id());
        Map<Long, AttachmentSnapshot> existingById = new LinkedHashMap<>();
        existing.forEach(item -> existingById.put(item.attachmentId(), item));
        Set<Long> unique = new LinkedHashSet<>();
        for (AttachmentInput input : inputs == null ? List.<AttachmentInput>of() : inputs) {
            if (input == null || input.attachmentId() <= 0) throw badRequest("附件标识无效");
            if (!unique.add(input.attachmentId())) throw badRequest("同一附件不能重复提交");
            String category = required(input.category(), "附件类别", 32).toUpperCase();
            if (!ATTACHMENT_CATEGORIES.contains(category)) throw badRequest("附件类别无效");
            AttachmentSnapshot bound = existingById.get(input.attachmentId());
            if (bound != null) {
                if (!category.equals(bound.category())) throw conflict("已绑定附件的类别不允许修改");
                continue;
            }
            AttachmentItem item = attachmentGateway.get(input.attachmentId(), user);
            attachmentGateway.bind(new AttachmentBindingCommand(input.attachmentId(), BUSINESS_TYPE,
                    application.applicationCode(), application.projectId()), user);
            workflowStore.insertAttachment(application.tenantId(), application.id(), input.attachmentId(), category,
                    item.fileName(), application.rowVersion());
        }
    }

    private String digest(Application application, ReleaseScenarioPolicy.Scenario scenario, ResolvedBinding workflowBinding,
                          List<AttachmentSnapshot> attachments) {
        Map<String, Object> canonical = new java.util.TreeMap<>();
        canonical.put("applicationCode", application.applicationCode());
        canonical.put("project", List.of(application.projectId(), application.projectCode(), application.projectName()));
        canonical.put("emergency", application.emergency());
        canonical.put("windowId", application.windowId() == null ? "" : application.windowId());
        canonical.put("subsystem", List.of(application.subsystemId(), application.subsystemCode(), application.subsystemName()));
        canonical.put("versionType", scenario.versionType().name());
        canonical.put("characteristic", scenario.characteristic().name());
        canonical.put("workflowScene", scenario.workflowScene().name());
        canonical.put("workflowDefinitionId", workflowBinding.workflowDefinitionId());
        canonical.put("workflowCode", workflowBinding.workflowCode());
        canonical.put("emergencyDescription", Objects.toString(application.emergencyDescription(), ""));
        canonical.put("urgentReason", Objects.toString(application.urgentReason(), ""));
        canonical.put("description", Objects.toString(application.description(), ""));
        canonical.put("deliveries", application.deliveries().stream()
                .sorted(Comparator.comparing(DeliverySnapshot::itemKey))
                .map(item -> List.of(item.itemType().name(), item.itemKey(), item.deliveryUnitId(),
                        item.deliveryUnitCode(), item.deliveryUnitName(), item.artifactType().name(),
                        Objects.toString(item.artifactVersion(), ""), Objects.toString(item.filePath(), ""))).toList());
        canonical.put("requirements", application.requirementCodes().stream().sorted().toList());
        canonical.put("attachments", attachments.stream().sorted(Comparator.comparingLong(AttachmentSnapshot::attachmentId))
                .map(item -> List.of(item.attachmentId(), item.category(), item.fileName())).toList());
        try {
            byte[] json = objectMapper.writeValueAsBytes(canonical);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "版本申请摘要序列化失败");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, Object> workflowVariables(Application application, ReleaseScenarioPolicy.Scenario scenario) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("applicationCode", application.applicationCode());
        values.put("projectId", application.projectId());
        values.put("subsystemCode", application.subsystemCode());
        values.put("versionType", scenario.versionType().name());
        values.put("characteristic", scenario.characteristic().name());
        values.put("emergency", application.emergency());
        values.put("fileMedia", application.deliveries().stream()
                .filter(item -> item.itemType() == DeliveryItemType.FILE_MEDIA)
                .map(DeliverySnapshot::filePath).sorted().toList());
        return Map.copyOf(values);
    }

    private void validateWorkflowResult(WorkflowStartResult result, WorkflowBusinessContext expected) {
        if (result == null || result.instanceId() <= 0 || result.definitionId() <= 0 || result.definitionVersion() <= 0
                || result.context() == null || !Objects.equals(result.context().businessType(), expected.businessType())
                || !Objects.equals(result.context().businessKey(), expected.businessKey())
                || result.context().businessRound() != expected.businessRound()
                || !Objects.equals(result.context().dataDigest(), expected.dataDigest())) {
            throw conflict("审批流程启动结果与版本申请上下文不一致");
        }
    }

    private String businessTitle(Application application) {
        String value = "版本申请 " + application.applicationCode() + " - " + application.subsystemName();
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private Application requireApplication(String code, AuthUser user, boolean forUpdate) {
        String normalized = required(code, "申请单号", 64);
        var application = forUpdate ? applications.findByCodeForUpdate(normalized, user.tenantId())
                : applications.findByCode(normalized, user.tenantId());
        if (application.isPresent()) {
            projectAccessService.requireAccessible(application.get().projectId(), user);
            return application.get();
        }
        var owner = applications.findTenantId(normalized);
        if (owner.isPresent() && owner.getAsLong() != user.tenantId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该版本申请");
        }
        throw badRequest("版本申请不存在");
    }

    private void ensureOwner(Application application, AuthUser user, boolean elevated) {
        if (!elevated && application.requesterId() != user.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护本人申请");
        }
    }

    private void requireVersion(long supplied, long actual) {
        if (supplied != actual) throw conflict("版本申请已被其他人修改，请刷新后重试");
    }

    private String required(String value, String label, int max) {
        String normalized = normalized(value);
        if (normalized == null) throw badRequest(label + "不能为空");
        if (normalized.length() > max) throw badRequest(label + "长度不能超过 " + max);
        return normalized;
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "申请审计序列化失败");
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1000);
    }

    public record AttachmentInput(long attachmentId, String category) {
    }

    public record SubmitRequest(long rowVersion, String conflictToken, List<AttachmentInput> attachments) {
    }

    public record SubmitResult(String applicationCode, String status, long rowVersion, int roundNo,
                               long workflowInstanceId, long workflowDefinitionId,
                               int workflowDefinitionVersion, String dataDigest) {
    }

    public record WorkflowActionResult(String applicationCode, String status, String operationStatus,
                                       int roundNo, long workflowInstanceId, long rowVersion) {
    }

    public record RoundView(int roundNo, String workflowCode, Long workflowDefinitionId,
                            Integer workflowDefinitionVersion, Long workflowInstanceId, String roundStatus,
                            String dataDigest, LocalDateTime submittedAt, LocalDateTime completedAt) {
        private static RoundView from(RoundSnapshot value) {
            return new RoundView(value.roundNo(), value.workflowCode(), value.workflowDefinitionId(),
                    value.workflowDefinitionVersion(), value.workflowInstanceId(), value.roundStatus(), value.dataDigest(),
                    value.submittedAt(), value.completedAt());
        }
    }

    public record AttachmentView(long attachmentId, String category, String fileName) {
    }

    public record AttachmentDeleteResult(long attachmentId, long rowVersion) {
    }
}

package com.ccb.architecture.environment.service;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.Environment;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentInstance;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillInstanceItemCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.HistoryEvent;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceDisasterRecovery;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.OfflineInstanceCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionItemRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionPreviewResult;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RecordStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceItemCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequestItem;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore.DeploymentUnitRef;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore.PhysicalSubsystemRef;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.architecture.network.service.NetworkAccessService.ZoneRef;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** 具体环境和资源申请的业务规则（REQ-20260824-052 与 REQ-20260825-053）。 */
@Service
public class EnvironmentResourceService {
    public static final String ENVIRONMENT_TYPE_CATEGORY = "ARCH_ENVIRONMENT_TYPE";
    public static final String SERVER_TYPE_CATEGORY = "ARCH_SERVER_TYPE";
    public static final String JDK_VERSION_CATEGORY = "ARCH_JDK_VERSION";
    public static final String MIDDLEWARE_CATEGORY = "ARCH_MIDDLEWARE";
    public static final String OPERATING_SYSTEM_CATEGORY = "ARCH_OPERATING_SYSTEM";
    private static final String DEFAULT_SERVER_TYPE_CODE = "architecture.server-type.container";
    private static final int MAX_RESOURCE_ITEMS = 50;

    public enum AccessScope {
        OWN,
        MANAGE
    }

    public record EnvironmentCommand(String code, String name, String typeCode,
                                     String description, String remark, Long rowVersion) {
    }

    public record ResourceRequestCommand(Long physicalSubsystemId, Long environmentId,
                                         Long contactUserId, RequestType requestType, String reason,
                                         List<ResourceItemCommand> items,
                                         Long rowVersion) {
        public ResourceRequestCommand {
            items = List.copyOf(items == null ? List.of() : items);
        }
    }

    public record ResourceRequestDetail(ResourceRequest request,
                                        List<ResourceRequestItem> items,
                                        List<HistoryEvent> history) {
        public ResourceRequestDetail {
            items = List.copyOf(items == null ? List.of() : items);
            history = List.copyOf(history == null ? List.of() : history);
        }
    }

    public record SubmissionPreparation(long requestId, int nextRound, String digest) {
    }

    public record CancellationPreparation(long requestId, long workflowInstanceId, int businessRound) {
    }

    private final EnvironmentResourceStore store;
    private final ObjectMapper objectMapper;
    private final SystemReferenceQuery referenceQuery;
    private final AutomatedDeploymentProvider automatedDeploymentProvider;
    private final NetworkAccessService networkAccessService;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public EnvironmentResourceService(EnvironmentResourceStore store, ObjectMapper objectMapper,
                                      SystemReferenceQuery referenceQuery,
                                      AutomatedDeploymentProvider automatedDeploymentProvider,
                                      NetworkAccessService networkAccessService) {
        this(store, objectMapper, referenceQuery, automatedDeploymentProvider,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC(), networkAccessService);
    }

    EnvironmentResourceService(EnvironmentResourceStore store, ObjectMapper objectMapper,
                               SystemReferenceQuery referenceQuery,
                               AutomatedDeploymentProvider automatedDeploymentProvider,
                               LongSupplier idSupplier, Clock clock) {
        this(store, objectMapper, referenceQuery, automatedDeploymentProvider, idSupplier, clock, null);
    }

    EnvironmentResourceService(EnvironmentResourceStore store, ObjectMapper objectMapper,
                               SystemReferenceQuery referenceQuery,
                               AutomatedDeploymentProvider automatedDeploymentProvider,
                               LongSupplier idSupplier, Clock clock,
                               NetworkAccessService networkAccessService) {
        this.store = Objects.requireNonNull(store, "环境资源存储不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
        this.referenceQuery = Objects.requireNonNull(referenceQuery, "系统引用查询不能为空");
        this.automatedDeploymentProvider = Objects.requireNonNull(automatedDeploymentProvider, "自动部署提供器不能为空");
        this.networkAccessService = networkAccessService;
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public List<EnvironmentType> listEnvironmentTypes(AuthUser actor, RecordStatus status) {
        requireActor(actor);
        if (status == RecordStatus.INACTIVE) {
            return List.of();
        }
        return activeEnvironmentTypes(actor);
    }

    public List<Environment> listEnvironments(AuthUser actor, String typeCode, RecordStatus status,
                                              String keyword, int limit, int offset) {
        requireActor(actor);
        return decorateEnvironments(actor,
                store.listEnvironments(actor.tenantId(), trimToNull(typeCode), status, keyword, limit, offset));
    }

    public Environment detailEnvironment(AuthUser actor, long id) {
        requireActor(actor);
        return decorateEnvironment(actor,
                store.findEnvironment(actor.tenantId(), id).orElseThrow(() -> notFound("具体环境不存在")));
    }

    public com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceSummary environmentSummary(
            AuthUser actor, long id) {
        requireActor(actor);
        store.findEnvironment(actor.tenantId(), id).orElseThrow(() -> notFound("具体环境不存在"));
        return store.resourceSummary(actor.tenantId(), id);
    }

    @Transactional
    public Environment createEnvironment(AuthUser actor, EnvironmentCommand command) {
        requireActor(actor);
        EnvironmentInput input = validateEnvironmentInput(actor, command, null);
        long id = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        Environment environment = new Environment(id, actor.tenantId(), input.code(), input.name(),
                input.type().code(), input.type().name(), RecordStatus.ACTIVE,
                input.description(), input.remark(), 0, actor.id(), actor.id(), now, now);
        store.insertEnvironment(environment);
        return decorateEnvironment(actor, store.findEnvironment(actor.tenantId(), id).orElse(environment));
    }

    @Transactional
    public Environment updateEnvironment(AuthUser actor, long id, EnvironmentCommand command) {
        requireActor(actor);
        Objects.requireNonNull(command, "具体环境命令不能为空");
        store.lockEnvironment(actor.tenantId(), id).orElseThrow(() -> notFound("具体环境不存在"));
        long rowVersion = requiredRowVersion(command.rowVersion());
        EnvironmentInput input = validateEnvironmentInput(actor, command, id);
        if (!store.updateEnvironment(actor.tenantId(), id, rowVersion, input.code(), input.name(),
                input.type().code(), input.description(), input.remark(), actor.id())) {
            throw conflict("具体环境已被其他人修改，请刷新后重试");
        }
        return decorateEnvironment(actor,
                store.findEnvironment(actor.tenantId(), id).orElseThrow(() -> notFound("具体环境不存在")));
    }

    @Transactional
    public Environment changeEnvironmentStatus(AuthUser actor, long id, long rowVersion, RecordStatus toStatus) {
        requireActor(actor);
        Environment current = store.lockEnvironment(actor.tenantId(), id)
                .orElseThrow(() -> notFound("具体环境不存在"));
        if (current.status() == toStatus) {
            return current;
        }
        if (!store.updateEnvironmentStatus(actor.tenantId(), id, rowVersion,
                current.status(), toStatus, actor.id())) {
            throw conflict("具体环境已被其他人修改，请刷新后重试");
        }
        return decorateEnvironment(actor,
                store.findEnvironment(actor.tenantId(), id).orElseThrow(() -> notFound("具体环境不存在")));
    }

    @Transactional
    public void deleteEnvironment(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        store.lockEnvironment(actor.tenantId(), id).orElseThrow(() -> notFound("具体环境不存在"));
        if (!store.deleteEnvironment(actor.tenantId(), id, rowVersion)) {
            throw conflict("具体环境已被资源申请引用或已被其他人修改，不能删除");
        }
    }

    public List<DeploymentUnitRef> listDeploymentUnitOptions(AuthUser actor, long physicalSubsystemId, int limit) {
        requireActor(actor);
        PhysicalSubsystemRef physical = requireActivePhysical(actor.tenantId(), physicalSubsystemId);
        return store.listDeploymentUnits(actor.tenantId(), physical.id(), Math.min(Math.max(limit, 1), 200));
    }

    public List<ResourceRequest> listRequests(AuthUser actor, AccessScope scope, RequestStatus status,
                                              Long environmentId, Long physicalSubsystemId,
                                              int limit, int offset) {
        requireActor(actor);
        Long applicantId = scope == AccessScope.MANAGE ? null : actor.id();
        return decorateRequests(actor, store.listRequests(actor.tenantId(), applicantId, status, environmentId,
                physicalSubsystemId, limit, offset));
    }

    public ResourceRequestDetail detailRequest(AuthUser actor, AccessScope scope, long requestId) {
        requireActor(actor);
        ResourceRequest request = requireVisible(actor, scope, requestId);
        return new ResourceRequestDetail(decorateRequest(actor, request), store.listItems(actor.tenantId(), requestId),
                store.listHistory(actor.tenantId(), requestId));
    }

    @Transactional
    public ResourceRequestDetail createRequest(AuthUser actor, ResourceRequestCommand command) {
        requireActor(actor);
        RequestInput input = validateRequestInput(actor, command);
        long id = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        ResourceRequest request = new ResourceRequest(id, actor.tenantId(), requestNo(id),
                input.physical().id(), input.physical().code(), input.physical().shortName(),
                input.physical().name(), input.physical().businessGroupName(),
                input.physical().systemLevelCode(), input.physical().deploymentPlatform(),
                input.physical().disasterRecoveryMode(),
                input.environment().id(), input.environment().code(), input.environment().name(),
                input.environment().typeName(), actor.id(), input.contactUserId(), input.requestType(), input.reason(),
                RequestStatus.DRAFT, 0, null, null, null, null, false, 0,
                actor.id(), actor.id(), now, now);
        store.insertResourceRequest(request);
        List<ResourceRequestItem> items = toItems(actor.tenantId(), id, input.items());
        store.replaceItems(actor.tenantId(), id, items);
        ResourceRequest saved = store.findRequest(actor.tenantId(), id)
                .orElseThrow(() -> notFound("资源申请不存在"));
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), id, "CREATED",
                null, RequestStatus.DRAFT, 0, "创建资源申请草稿",
                snapshot(saved, store.listItems(actor.tenantId(), id)), null, actor.id(), now));
        return detailRequest(actor, AccessScope.OWN, id);
    }

    @Transactional
    public ResourceRequestDetail updateRequest(AuthUser actor, long requestId, ResourceRequestCommand command) {
        requireActor(actor);
        ResourceRequest current = requireVisible(actor, AccessScope.OWN, requestId);
        requireOwner(current, actor);
        if (current.status() != RequestStatus.DRAFT && current.status() != RequestStatus.RETURNED) {
            throw conflict("当前状态不允许编辑资源申请");
        }
        long rowVersion = requiredRowVersion(command == null ? null : command.rowVersion());
        RequestInput input = validateRequestInput(actor, command);
        if (!store.updateDraft(actor.tenantId(), requestId, current.status(), rowVersion,
                input.physical().id(), input.environment().id(), input.contactUserId(),
                input.requestType(), input.reason(), actor.id())) {
            throw conflict("资源申请已被其他人修改，请刷新后重试");
        }
        store.replaceItems(actor.tenantId(), requestId, toItems(actor.tenantId(), requestId, input.items()));
        ResourceRequest updated = store.findRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请不存在"));
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), requestId, "UPDATED",
                current.status(), updated.status(), current.currentBusinessRound(),
                "更新资源申请草稿", snapshot(updated, store.listItems(actor.tenantId(), requestId)),
                diff(current, updated), actor.id(), LocalDateTime.now(clock)));
        return detailRequest(actor, AccessScope.OWN, requestId);
    }

    @Transactional
    public void coordinateSubmission(AuthUser actor, long requestId, long expectedRowVersion,
                                     Consumer<SubmissionPreparation> workflowStarter) {
        requireActor(actor);
        Objects.requireNonNull(workflowStarter, "工作流启动器不能为空");
        ResourceRequest current = store.lockRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请不存在"));
        requireOwner(current, actor);
        if (current.status() != RequestStatus.DRAFT && current.status() != RequestStatus.RETURNED) {
            throw conflict("当前状态不允许提交审批");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("资源申请已被其他人修改，请刷新后重试");
        }
        List<ResourceRequestItem> items = store.listItems(actor.tenantId(), requestId);
        validateStillActive(actor, current, items);
        String digest = digest(current, items);
        if (!store.compareAndSetStatus(actor.tenantId(), requestId, current.status(),
                current.rowVersion(), RequestStatus.IN_REVIEW, actor.id())) {
            throw conflict("资源申请状态已被其他人修改，请刷新后重试");
        }
        ResourceRequest submitted = store.lockRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请不存在"));
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), requestId, "SUBMITTED",
                current.status(), RequestStatus.IN_REVIEW, current.currentBusinessRound(),
                "提交资源申请审批", snapshotWithDigest(submitted, items, digest),
                null, actor.id(), LocalDateTime.now(clock)));
        workflowStarter.accept(new SubmissionPreparation(requestId, current.currentBusinessRound() + 1, digest));
    }

    @Transactional
    public ResourceRequestDetail cancel(AuthUser actor, AccessScope scope, long requestId, long expectedRowVersion) {
        requireActor(actor);
        ResourceRequest current = requireVisible(actor, scope, requestId);
        requireOwner(current, actor);
        if (current.status() == RequestStatus.IN_REVIEW) {
            throw conflict("审批中的资源申请必须通过终止流程取消");
        }
        if (current.status() != RequestStatus.DRAFT && current.status() != RequestStatus.RETURNED) {
            throw conflict("当前状态不允许取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("资源申请已被其他人修改，请刷新后重试");
        }
        if (!store.compareAndSetStatus(actor.tenantId(), requestId, current.status(), current.rowVersion(),
                RequestStatus.CANCELLED, actor.id())) {
            throw conflict("资源申请状态已被其他人修改，请刷新后重试");
        }
        ResourceRequest cancelled = store.lockRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请不存在"));
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), requestId, "CANCELLED",
                current.status(), RequestStatus.CANCELLED, current.currentBusinessRound(),
                "取消资源申请", snapshot(cancelled, store.listItems(actor.tenantId(), requestId)),
                null, actor.id(), LocalDateTime.now(clock)));
        return detailRequest(actor, AccessScope.OWN, requestId);
    }

    @Transactional
    public void coordinateCancellation(AuthUser actor, long requestId, long expectedRowVersion,
                                       Consumer<CancellationPreparation> workflowTerminator) {
        requireActor(actor);
        Objects.requireNonNull(workflowTerminator, "工作流终止器不能为空");
        ResourceRequest current = store.lockRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请不存在"));
        requireOwner(current, actor);
        if (current.status() != RequestStatus.IN_REVIEW) {
            throw conflict("当前状态不允许发起取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("资源申请已被其他人修改，请刷新后重试");
        }
        if (current.currentWorkflowInstanceId() == null) {
            throw conflict("审批流程尚未启动，不能取消");
        }
        if (!store.compareAndSetCancellationRequested(actor.tenantId(), requestId,
                current.rowVersion(), true, actor.id())) {
            throw conflict("资源申请已被其他人修改，请刷新后重试");
        }
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), requestId, "CANCEL_REQUESTED",
                RequestStatus.IN_REVIEW, RequestStatus.IN_REVIEW, current.currentBusinessRound(),
                "登记取消请求并终止审批流程", null, null, actor.id(), LocalDateTime.now(clock)));
        workflowTerminator.accept(new CancellationPreparation(requestId,
                current.currentWorkflowInstanceId(), current.currentBusinessRound()));
    }

    @Transactional
    public void applyReviewOutcomeInCurrentTransaction(long tenantId, long requestId,
                                                       long expectedRowVersion, long operatorId,
                                                       RequestStatus outcome) {
        if (outcome != RequestStatus.RETURNED && outcome != RequestStatus.REJECTED) {
            throw new IllegalArgumentException("退回/拒绝之外的终态不允许通过评审路径落地");
        }
        ResourceRequest current = store.lockRequest(tenantId, requestId)
                .orElseThrow(() -> conflict("工作流事件关联的资源申请不存在"));
        if (current.status() != RequestStatus.IN_REVIEW || current.cancellationRequested()) {
            throw conflict("工作流事件对应的资源申请已变化或正在取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("资源申请行版本已变化，无法应用工作流结论");
        }
        if (!store.compareAndSetStatus(tenantId, requestId, RequestStatus.IN_REVIEW,
                current.rowVersion(), outcome, operatorId)) {
            throw conflict("资源申请状态已被其他人修改");
        }
        ResourceRequest updated = store.lockRequest(tenantId, requestId)
                .orElseThrow(() -> conflict("资源申请不存在"));
        store.insertHistory(new HistoryEvent(nextId(), tenantId, requestId, outcome.name(),
                RequestStatus.IN_REVIEW, outcome, current.currentBusinessRound(),
                outcome == RequestStatus.RETURNED ? "审批退回，等待修改后重提" : "审批拒绝",
                snapshot(updated, store.listItems(tenantId, requestId)), null, operatorId,
                LocalDateTime.now(clock)));
    }

    @Transactional
    public void applyApprovalInCurrentTransaction(long tenantId, long requestId,
                                                  long expectedRowVersion, long operatorId) {
        ResourceRequest current = store.lockRequest(tenantId, requestId)
                .orElseThrow(() -> conflict("工作流事件关联的资源申请不存在"));
        if (current.status() != RequestStatus.IN_REVIEW || current.cancellationRequested()) {
            throw conflict("工作流事件对应的资源申请已变化或正在取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("资源申请行版本已变化，无法应用工作流结论");
        }
        if (!store.compareAndSetStatus(tenantId, requestId, RequestStatus.IN_REVIEW,
                current.rowVersion(), RequestStatus.APPROVED, operatorId)) {
            throw conflict("资源申请状态已被其他人修改");
        }
        ResourceRequest updated = store.lockRequest(tenantId, requestId)
                .orElseThrow(() -> conflict("资源申请不存在"));
        store.insertHistory(new HistoryEvent(nextId(), tenantId, requestId, "APPROVED",
                RequestStatus.IN_REVIEW, RequestStatus.APPROVED, current.currentBusinessRound(),
                "审批通过，资源申请进入申请态；实际分配待后续搭建任务接入",
                snapshot(updated, store.listItems(tenantId, requestId)), null, operatorId,
                LocalDateTime.now(clock)));
    }

    @Transactional
    public void applyCancellationConfirmationInCurrentTransaction(long tenantId, long requestId,
                                                                  long expectedRowVersion, long operatorId) {
        ResourceRequest current = store.lockRequest(tenantId, requestId)
                .orElseThrow(() -> conflict("工作流事件关联的资源申请不存在"));
        if (current.status() != RequestStatus.IN_REVIEW || !current.cancellationRequested()) {
            throw conflict("工作流事件没有匹配的取消请求");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("资源申请行版本已变化，无法应用工作流结论");
        }
        if (!store.compareAndSetStatus(tenantId, requestId, RequestStatus.IN_REVIEW,
                current.rowVersion(), RequestStatus.CANCELLED, operatorId)) {
            throw conflict("资源申请状态已被其他人修改");
        }
        ResourceRequest updated = store.lockRequest(tenantId, requestId)
                .orElseThrow(() -> conflict("资源申请不存在"));
        store.insertHistory(new HistoryEvent(nextId(), tenantId, requestId, "CANCELLED",
                RequestStatus.IN_REVIEW, RequestStatus.CANCELLED, current.currentBusinessRound(),
                "审批流程已终止并取消资源申请", snapshot(updated, store.listItems(tenantId, requestId)),
                null, operatorId, LocalDateTime.now(clock)));
    }

    public String digest(ResourceRequest request, List<ResourceRequestItem> items) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("requestNo", request.requestNo());
            canonical.put("physicalSubsystemId", request.physicalSubsystemId());
            canonical.put("environmentId", request.environmentId());
            canonical.put("contactUserId", request.contactUserId());
            canonical.put("requestType", request.requestType().name());
            canonical.put("reason", request.reason());
            canonical.put("items", items.stream().map(this::itemSnapshot).toList());
            String json = objectMapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("资源申请摘要计算失败", exception);
        }
    }

    private List<EnvironmentType> activeEnvironmentTypes(AuthUser actor) {
        return referenceQuery.activeParameters(actor, ENVIRONMENT_TYPE_CATEGORY).stream()
                .filter(item -> trimToNull(item.code()) != null && trimToNull(item.label()) != null)
                .map(item -> new EnvironmentType(item.code().trim(), item.label().trim()))
                .toList();
    }

    private EnvironmentType requireActiveEnvironmentType(AuthUser actor, String input) {
        String requested = requireText(input, "环境类型", 128);
        return activeEnvironmentTypes(actor).stream()
                .filter(type -> type.code().equalsIgnoreCase(requested))
                .findFirst()
                .orElseThrow(() -> badRequest("环境类型参数无效或已停用，请在系统字典 ARCH_ENVIRONMENT_TYPE 中维护"));
    }

    private String validateParameter(AuthUser actor, String categoryCode, String input, String label) {
        String requested = requireText(input, label, 128);
        return referenceQuery.activeParameters(actor, categoryCode).stream()
                .map(SystemParameterReference::code)
                .filter(code -> code != null && code.trim().equalsIgnoreCase(requested))
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> badRequest(label + "参数无效或已停用，请在系统字典 " + categoryCode + " 中维护"));
    }

    private String validateOptionalParameter(AuthUser actor, String categoryCode, String input, String label) {
        String requested = trimToNull(input);
        return requested == null ? null : validateParameter(actor, categoryCode, requested, label);
    }

    private List<Environment> decorateEnvironments(AuthUser actor, List<Environment> environments) {
        Map<String, String> labels = environmentTypeLabels(actor);
        return environments.stream().map(environment -> withEnvironmentTypeName(environment, labels)).toList();
    }

    private Environment decorateEnvironment(AuthUser actor, Environment environment) {
        return withEnvironmentTypeName(environment, environmentTypeLabels(actor));
    }

    private Environment withEnvironmentTypeName(Environment environment, Map<String, String> labels) {
        String typeName = typeLabel(environment.typeCode(), labels);
        return new Environment(environment.id(), environment.tenantId(), environment.code(), environment.name(),
                environment.typeCode(), typeName, environment.status(), environment.description(),
                environment.remark(), environment.rowVersion(), environment.createdBy(), environment.updatedBy(),
                environment.createdAt(), environment.updatedAt());
    }

    private List<ResourceRequest> decorateRequests(AuthUser actor, List<ResourceRequest> requests) {
        Map<String, String> labels = environmentTypeLabels(actor);
        return requests.stream().map(request -> withEnvironmentTypeName(request, labels)).toList();
    }

    private ResourceRequest decorateRequest(AuthUser actor, ResourceRequest request) {
        return withEnvironmentTypeName(request, environmentTypeLabels(actor));
    }

    private ResourceRequest withEnvironmentTypeName(ResourceRequest request, Map<String, String> labels) {
        String typeName = typeLabel(request.environmentTypeName(), labels);
        return new ResourceRequest(request.id(), request.tenantId(), request.requestNo(),
                request.physicalSubsystemId(), request.physicalSubsystemCode(),
                request.physicalSubsystemShortName(), request.physicalSubsystemName(),
                request.physicalSubsystemBusinessGroupName(), request.physicalSubsystemSystemLevelCode(),
                request.physicalSubsystemDeploymentPlatform(), request.physicalSubsystemDisasterRecoveryMode(),
                request.environmentId(), request.environmentCode(), request.environmentName(), typeName,
                request.applicantId(), request.contactUserId(), request.requestType(), request.reason(),
                request.status(), request.currentBusinessRound(), request.currentWorkflowDefinitionId(),
                request.currentWorkflowVersionId(), request.currentWorkflowInstanceId(),
                request.currentPayloadDigest(), request.cancellationRequested(), request.rowVersion(),
                request.createdBy(), request.updatedBy(), request.createdAt(), request.updatedAt());
    }

    private Map<String, String> environmentTypeLabels(AuthUser actor) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (SystemParameterReference item : referenceQuery.activeParameters(actor, ENVIRONMENT_TYPE_CATEGORY)) {
            String code = trimToNull(item.code());
            String label = trimToNull(item.label());
            if (code != null && label != null) {
                labels.put(lookupKey(code), label);
            }
        }
        return labels;
    }

    private String typeLabel(String code, Map<String, String> labels) {
        String normalized = trimToNull(code);
        if (normalized == null) {
            return null;
        }
        return labels.getOrDefault(lookupKey(normalized), normalized);
    }

    private String lookupKey(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private RequestInput validateRequestInput(AuthUser actor, ResourceRequestCommand command) {
        Objects.requireNonNull(command, "资源申请命令不能为空");
        long physicalSubsystemId = requiredPositive(command.physicalSubsystemId(), "物理子系统");
        long environmentId = requiredPositive(command.environmentId(), "具体环境");
        long contactUserId = requiredPositive(command.contactUserId(), "资源申请联系人");
        if (referenceQuery.findUser(actor, contactUserId, true).isEmpty()) {
            throw badRequest("资源申请联系人不存在、已停用或不属于当前租户");
        }
        RequestType requestType = Objects.requireNonNull(command.requestType(), "申请类型不能为空");
        PhysicalSubsystemRef physical = requireActivePhysical(actor.tenantId(), physicalSubsystemId);
        Environment environment = requireActiveEnvironment(actor.tenantId(), environmentId);
        List<ItemInput> items = validateItems(actor, physical, command.items());
        return new RequestInput(physical, environment, requestType, trimToNull(command.reason()),
                contactUserId, items);
    }

    private EnvironmentInput validateEnvironmentInput(AuthUser actor, EnvironmentCommand command, Long excludeId) {
        Objects.requireNonNull(command, "具体环境命令不能为空");
        String code = normalizeCode(command.code(), "环境编码");
        String name = requireText(command.name(), "环境名称", 160);
        EnvironmentType type = requireActiveEnvironmentType(actor, command.typeCode());
        if (store.environmentCodeExists(actor.tenantId(), code, excludeId)) {
            throw conflict("环境编码已存在");
        }
        if (store.environmentNameExists(actor.tenantId(), name, excludeId)) {
            throw conflict("环境名称已存在");
        }
        return new EnvironmentInput(code, name, type, trimToNull(command.description()), trimToNull(command.remark()));
    }

    private void validateStillActive(AuthUser actor, ResourceRequest request, List<ResourceRequestItem> items) {
        requireActivePhysical(actor.tenantId(), request.physicalSubsystemId());
        requireActiveEnvironment(actor.tenantId(), request.environmentId());
        for (ResourceRequestItem item : items) {
            DeploymentUnitRef unit = store.findDeploymentUnit(actor.tenantId(), item.deploymentUnitId())
                    .orElseThrow(() -> badRequest("部署单元不存在：" + item.deploymentUnitId()));
            if (!"ACTIVE".equals(unit.status()) || unit.physicalSubsystemId() != request.physicalSubsystemId()) {
                throw badRequest("部署单元不属于当前物理子系统或已停用：" + unit.code());
            }
        }
    }

    private PhysicalSubsystemRef requireActivePhysical(long tenantId, long physicalSubsystemId) {
        PhysicalSubsystemRef physical = store.findPhysical(tenantId, physicalSubsystemId)
                .orElseThrow(() -> badRequest("物理子系统不存在"));
        if (physical.deleted() || !"ACTIVE".equals(physical.status())) {
            throw badRequest("物理子系统不是 ACTIVE 状态，不能发起资源申请");
        }
        return physical;
    }

    private Environment requireActiveEnvironment(long tenantId, long environmentId) {
        Environment environment = store.findEnvironment(tenantId, environmentId)
                .orElseThrow(() -> badRequest("具体环境不存在"));
        if (environment.status() != RecordStatus.ACTIVE) {
            throw badRequest("具体环境已停用，不能发起资源申请");
        }
        return environment;
    }

    private List<ItemInput> validateItems(AuthUser actor, PhysicalSubsystemRef physical,
                                          List<ResourceItemCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw badRequest("资源申请至少需要 1 条部署单元规格");
        }
        if (commands.size() > MAX_RESOURCE_ITEMS) {
            throw badRequest("资源申请明细不能超过 " + MAX_RESOURCE_ITEMS + " 条");
        }
        java.util.ArrayList<ItemInput> items = new java.util.ArrayList<>();
        for (ResourceItemCommand command : commands) {
            if (command == null) {
                throw badRequest("资源申请明细不能为空");
            }
            long unitId = requiredPositive(command.deploymentUnitId(), "部署单元");
            DeploymentUnitRef unit = store.findDeploymentUnit(actor.tenantId(), unitId)
                    .orElseThrow(() -> badRequest("部署单元不存在：" + unitId));
            if (!"ACTIVE".equals(unit.status())) {
                throw badRequest("部署单元已停用：" + unit.code());
            }
            if (unit.physicalSubsystemId() != physical.id()) {
                throw badRequest("部署单元 " + unit.code() + " 不属于所选物理子系统");
            }
            BigDecimal databaseStorage = nonNegativeInteger(command.databaseStorageGb(), "数据库存储需求（G）");
            BigDecimal fileStorage = nonNegativeInteger(command.fileStorageGb(), "文件存储需求（G）");
            BigDecimal cpu = nonNegativeInteger(command.cpuCores(), "CPU");
            BigDecimal memory = nonNegativeInteger(command.memoryGb(), "内存");
            int appWebGroups = nonNegativeInt(command.appWebGroupCount(), "AP、WEB组数");
            int nodes = nonNegativeInt(command.plannedNodeCount(), "生产环境节点数");
            BigDecimal sidecarCpu = nonNegativeInteger(command.sidecarCpuCores(), "总边车CPU");
            BigDecimal sidecarMemory = nonNegativeInteger(command.sidecarMemoryGb(), "总边车内存");
            BigDecimal extraCbs = nonNegativeInteger(command.extraCbsGb(), "额外的CBS容量C");
            BigDecimal localDisk = nonNegativeInteger(command.localDiskGb(), "本地盘需求（G）");
            boolean needsNft = Boolean.TRUE.equals(command.needsNft());
            boolean needsFserver = Boolean.TRUE.equals(command.needsFserver());
            boolean needsJobexecutor = Boolean.TRUE.equals(command.needsJobexecutor());
            boolean hasSidecar = Boolean.TRUE.equals(command.hasSidecar());
            if (!hasSidecar) {
                sidecarCpu = BigDecimal.ZERO;
                sidecarMemory = BigDecimal.ZERO;
            }
            String databaseName = optional(command.databaseName(), "数据库", 100);
            String databaseVersion = optional(command.databaseVersion(), "数据库版本", 100);
            boolean databaseUnit = isDatabaseDeploymentUnit(unit);
            String serverType = null;
            Long networkZoneId = null;
            String networkZoneName = null;
            String networkZone = null;
            String jdkVersion = null;
            String middleware = null;
            String operatingSystem = null;
            if (databaseUnit) {
                fileStorage = BigDecimal.ZERO;
                cpu = BigDecimal.ZERO;
                memory = BigDecimal.ZERO;
                appWebGroups = 0;
                nodes = 0;
                sidecarCpu = BigDecimal.ZERO;
                sidecarMemory = BigDecimal.ZERO;
                extraCbs = BigDecimal.ZERO;
                localDisk = BigDecimal.ZERO;
                needsNft = false;
                needsFserver = false;
                needsJobexecutor = false;
                hasSidecar = false;
                if (databaseStorage.signum() == 0 && databaseName == null && databaseVersion == null) {
                    throw badRequest("DB 明细至少填写数据库存储需求、数据库或数据库版本");
                }
            } else {
                databaseStorage = BigDecimal.ZERO;
                databaseName = null;
                databaseVersion = null;
                serverType = validateParameter(actor, SERVER_TYPE_CATEGORY,
                        trimToNull(command.serverType()) == null ? DEFAULT_SERVER_TYPE_CODE : command.serverType(),
                        "服务器类型");
                ZoneRef zone = resolveResourceItemNetworkZone(actor, command.networkZoneId(), unit, command.networkZone());
                networkZoneId = zone == null ? null : zone.id();
                networkZoneName = zone == null ? null : zone.name();
                networkZone = networkZoneName == null ? optional(command.networkZone(), "网络分区", 100) : networkZoneName;
                jdkVersion = validateOptionalParameter(actor, JDK_VERSION_CATEGORY, command.jdkVersion(), "JDK");
                middleware = validateOptionalParameter(actor, MIDDLEWARE_CATEGORY, command.middleware(), "中间件");
                operatingSystem = validateOptionalParameter(actor, OPERATING_SYSTEM_CATEGORY,
                        command.operatingSystem(), "产品化操作系统");
                if (cpu.signum() == 0 && memory.signum() == 0 && fileStorage.signum() == 0
                        && sidecarCpu.signum() == 0 && sidecarMemory.signum() == 0
                        && extraCbs.signum() == 0 && localDisk.signum() == 0
                        && appWebGroups == 0 && nodes == 0
                        && !needsNft && !needsFserver && !needsJobexecutor) {
                    throw badRequest("非 DB 明细至少填写一项资源容量或附加需求");
                }
            }
            items.add(new ItemInput(unit, trimToNull(unit.description()),
                    databaseStorage, fileStorage,
                    networkZoneId, networkZoneName, networkZone, serverType,
                    cpu, memory, appWebGroups, nodes, sidecarCpu, sidecarMemory, hasSidecar,
                    databaseName, databaseVersion, jdkVersion, middleware, operatingSystem,
                    extraCbs, localDisk, needsNft, needsFserver, needsJobexecutor,
                    optional(command.remark(), "备注", 1000)));
        }
        return List.copyOf(items);
    }

    private ZoneRef resolveResourceItemNetworkZone(AuthUser actor, Long requestedZoneId,
                                                   DeploymentUnitRef unit, String legacyText) {
        Long selectedZoneId = requestedZoneId == null ? unit.defaultNetworkZoneId() : requestedZoneId;
        if (selectedZoneId != null) {
            if (networkAccessService == null) {
                return new ZoneRef(selectedZoneId, null,
                        requestedZoneId == null ? unit.defaultNetworkZoneName() : null);
            }
            return networkAccessService.requireActiveLeafZone(actor.tenantId(), selectedZoneId, "资源申请网络分区");
        }
        if (networkAccessService != null) {
            throw badRequest("非 DB 资源申请明细必须选择网络分区");
        }
        return null;
    }

    private ZoneRef resolveFulfillmentNetworkZone(AuthUser actor, Long requestedZoneId,
                                                  ResourceRequestItem sourceItem, DeploymentUnitRef unit, int seq) {
        Long selectedZoneId = requestedZoneId;
        String fallbackName = null;
        if (selectedZoneId == null && sourceItem.networkZoneId() != null) {
            selectedZoneId = sourceItem.networkZoneId();
            fallbackName = sourceItem.networkZoneName();
        }
        if (selectedZoneId == null && unit.defaultNetworkZoneId() != null) {
            selectedZoneId = unit.defaultNetworkZoneId();
            fallbackName = unit.defaultNetworkZoneName();
        }
        if (selectedZoneId != null) {
            if (networkAccessService == null) {
                return new ZoneRef(selectedZoneId, null, fallbackName);
            }
            return networkAccessService.requireActiveLeafZone(actor.tenantId(), selectedZoneId,
                    "第 " + seq + " 台实例网络分区");
        }
        if (networkAccessService != null) {
            throw badRequest("第 " + seq + " 台实例必须选择网络分区");
        }
        return null;
    }

    private List<ResourceRequestItem> toItems(long tenantId, long requestId, List<ItemInput> inputs) {
        java.util.ArrayList<ResourceRequestItem> items = new java.util.ArrayList<>();
        int seq = 1;
        for (ItemInput input : inputs) {
            items.add(new ResourceRequestItem(nextId(), tenantId, requestId, seq++,
                    input.unit().id(), input.unit().code(), input.unit().name(), input.unit().kind(),
                    input.deploymentUnitDescription(), input.databaseStorageGb(), input.fileStorageGb(),
                    input.networkZoneId(), input.networkZoneName(), input.networkZone(),
                    input.serverType(), input.cpuCores(), input.memoryGb(),
                    input.appWebGroupCount(), input.plannedNodeCount(), input.sidecarCpuCores(),
                    input.sidecarMemoryGb(), input.hasSidecar(), input.databaseName(),
                    input.databaseVersion(), input.jdkVersion(), input.middleware(), input.operatingSystem(),
                    input.extraCbsGb(), input.localDiskGb(), input.needsNft(), input.needsFserver(),
                    input.needsJobexecutor(), input.remark(), null, null));
        }
        return List.copyOf(items);
    }

    private ResourceRequest requireVisible(AuthUser actor, AccessScope scope, long requestId) {
        ResourceRequest request = store.findRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请不存在"));
        if (scope == AccessScope.OWN && request.applicantId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本人发起的资源申请");
        }
        return request;
    }

    private void requireOwner(ResourceRequest request, AuthUser actor) {
        if (request.applicantId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作本人发起的资源申请");
        }
    }

    private String snapshot(ResourceRequest request, List<ResourceRequestItem> items) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", request.id());
            snapshot.put("requestNo", request.requestNo());
            snapshot.put("physicalSubsystemId", request.physicalSubsystemId());
            snapshot.put("physicalSubsystemCode", request.physicalSubsystemCode());
            snapshot.put("physicalSubsystemName", request.physicalSubsystemName());
            snapshot.put("physicalSubsystemBusinessGroupName", request.physicalSubsystemBusinessGroupName());
            snapshot.put("physicalSubsystemSystemLevelCode", request.physicalSubsystemSystemLevelCode());
            snapshot.put("physicalSubsystemDeploymentPlatform", request.physicalSubsystemDeploymentPlatform());
            snapshot.put("physicalSubsystemDisasterRecoveryMode", request.physicalSubsystemDisasterRecoveryMode());
            snapshot.put("environmentId", request.environmentId());
            snapshot.put("contactUserId", request.contactUserId());
            snapshot.put("requestType", request.requestType().name());
            snapshot.put("status", request.status().name());
            snapshot.put("items", items.stream().map(this::itemSnapshot).toList());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("资源申请快照序列化失败", exception);
        }
    }

    private String snapshotWithDigest(ResourceRequest request, List<ResourceRequestItem> items, String digest) {
        try {
            Map<String, Object> snapshot = objectMapper.readValue(snapshot(request, items),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            snapshot.put("payloadDigest", digest);
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("资源申请快照序列化失败", exception);
        }
    }

    private Map<String, Object> itemSnapshot(ResourceRequestItem item) {
        Map<String, Object> itemSnapshot = new LinkedHashMap<>();
        itemSnapshot.put("deploymentUnitId", item.deploymentUnitId());
        itemSnapshot.put("deploymentUnitCode", item.deploymentUnitCode());
        itemSnapshot.put("deploymentUnitName", item.deploymentUnitName());
        itemSnapshot.put("deploymentUnitKind", item.deploymentUnitKind());
        itemSnapshot.put("deploymentUnitDescription", item.deploymentUnitDescription());
        itemSnapshot.put("databaseStorageGb", item.databaseStorageGb());
        itemSnapshot.put("fileStorageGb", item.fileStorageGb());
        itemSnapshot.put("networkZoneId", item.networkZoneId());
        itemSnapshot.put("networkZoneName", item.networkZoneName());
        itemSnapshot.put("networkZone", item.networkZone());
        itemSnapshot.put("serverType", item.serverType());
        itemSnapshot.put("cpuCores", item.cpuCores());
        itemSnapshot.put("memoryGb", item.memoryGb());
        itemSnapshot.put("appWebGroupCount", item.appWebGroupCount());
        itemSnapshot.put("plannedNodeCount", item.plannedNodeCount());
        itemSnapshot.put("totalCpuCores", item.totalCpuCores());
        itemSnapshot.put("totalMemoryGb", item.totalMemoryGb());
        itemSnapshot.put("sidecarCpuCores", item.sidecarCpuCores());
        itemSnapshot.put("sidecarMemoryGb", item.sidecarMemoryGb());
        itemSnapshot.put("sidecarMemoryRatio", item.sidecarMemoryRatio());
        itemSnapshot.put("hasSidecar", item.hasSidecar());
        itemSnapshot.put("databaseName", item.databaseName());
        itemSnapshot.put("databaseVersion", item.databaseVersion());
        itemSnapshot.put("jdkVersion", item.jdkVersion());
        itemSnapshot.put("middleware", item.middleware());
        itemSnapshot.put("operatingSystem", item.operatingSystem());
        itemSnapshot.put("extraCbsGb", item.extraCbsGb());
        itemSnapshot.put("localDiskGb", item.localDiskGb());
        itemSnapshot.put("needsNft", item.needsNft());
        itemSnapshot.put("needsFserver", item.needsFserver());
        itemSnapshot.put("needsJobexecutor", item.needsJobexecutor());
        itemSnapshot.put("remark", item.remark());
        return itemSnapshot;
    }

    private String diff(ResourceRequest before, ResourceRequest after) {
        try {
            Map<String, Object> diff = new LinkedHashMap<>();
            if (before.physicalSubsystemId() != after.physicalSubsystemId()) {
                diff.put("physicalSubsystemId", List.of(before.physicalSubsystemId(), after.physicalSubsystemId()));
            }
            if (before.environmentId() != after.environmentId()) {
                diff.put("environmentId", List.of(before.environmentId(), after.environmentId()));
            }
            if (before.requestType() != after.requestType()) {
                diff.put("requestType", List.of(before.requestType().name(), after.requestType().name()));
            }
            if (!Objects.equals(before.reason(), after.reason())) {
                diff.put("reason", List.of(before.reason(), after.reason()));
            }
            if (before.contactUserId() != after.contactUserId()) {
                diff.put("contactUserId", List.of(before.contactUserId(), after.contactUserId()));
            }
            diff.put("itemsChanged", true);
            return objectMapper.writeValueAsString(diff);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("资源申请差异序列化失败", exception);
        }
    }

    private static String normalizeCode(String value, String label) {
        String normalized = requireText(value, label, 64).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]+")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + " 只能包含大写字母、数字、下划线或连字符");
        }
        return normalized;
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    private static long requiredPositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "必须为正整数");
        }
        return value;
    }

    private static long requiredRowVersion(Long rowVersion) {
        if (rowVersion == null || rowVersion < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "rowVersion 必须为非负整数");
        }
        return rowVersion;
    }

    private static BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (normalized.signum() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能小于 0");
        }
        return normalized;
    }

    private static BigDecimal nonNegativeInteger(BigDecimal value, String label) {
        BigDecimal normalized = nonNegative(value, label);
        if (normalized.scale() > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "必须为整数");
        }
        return normalized;
    }

    private static int nonNegativeInt(Integer value, String label) {
        int normalized = value == null ? 0 : value;
        if (normalized < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能小于 0");
        }
        return normalized;
    }

    private static String optional(String value, String label, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static boolean isDatabaseDeploymentUnit(DeploymentUnitRef unit) {
        return "DATABASE".equalsIgnoreCase(unit.kind());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requestNo(long id) {
        return "RR" + id;
    }

    private static void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("环境资源标识生成器返回无效值");
        }
        return value;
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    // ===== REQ-20260825-053：资源工单下发、环境部署实例生命周期与灾备关系 =====

    @Transactional(readOnly = true)
    public ProvisionPreviewResult previewAutomatedProvision(AuthUser actor, long requestId) {
        requireActor(actor);
        ResourceRequest request = store.findRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请工单不存在：" + requestId));
        if (request.status() != RequestStatus.APPROVED) {
            throw badRequest("只有审批通过 (APPROVED) 的资源工单才能执行自动部署下发");
        }
        List<ResourceRequestItem> items = store.listItems(actor.tenantId(), requestId);
        if (items.isEmpty()) {
            throw badRequest("资源申请工单无明细项，无法执行部署预览");
        }
        Map<Long, Integer> nextSequenceByUnit = new LinkedHashMap<>();
        List<ProvisionItemRequest> provisionItems = new java.util.ArrayList<>();
        for (ResourceRequestItem item : items) {
            int nodeCount = Math.max(1, item.plannedNodeCount());
            int nextSequenceStart = nextSequenceByUnit.computeIfAbsent(item.deploymentUnitId(),
                    ignored -> store.countInstancesForEnvironmentUnit(actor.tenantId(),
                            request.environmentId(), item.deploymentUnitId()) + 1);
            nextSequenceByUnit.put(item.deploymentUnitId(), nextSequenceStart + nodeCount);
            String subnetCidr = networkAccessService == null ? null
                    : networkAccessService.requirePrimaryActiveSubnetCidr(actor.tenantId(), item.networkZoneId(),
                    "自动部署明细 " + item.itemSeq());

            provisionItems.add(new ProvisionItemRequest(
                    item.id(),
                    item.itemSeq(),
                    item.deploymentUnitId(),
                    item.deploymentUnitCode(),
                    item.deploymentUnitName(),
                    item.deploymentUnitKind(),
                    item.cpuCores(),
                    item.memoryGb(),
                    item.databaseStorageGb(),
                    item.fileStorageGb(),
                    item.extraCbsGb(),
                    item.localDiskGb(),
                    item.plannedNodeCount(),
                    nextSequenceStart,
                    item.networkZoneId(),
                    item.networkZoneName(),
                    item.networkZone(),
                    subnetCidr,
                    item.serverType(),
                    request.physicalSubsystemDeploymentPlatform(),
                    item.databaseName(),
                    item.databaseVersion(),
                    item.jdkVersion(),
                    item.middleware(),
                    item.operatingSystem(),
                    item.needsNft(),
                    item.needsFserver(),
                    item.needsJobexecutor(),
                    item.remark()
            ));
        }

        ProvisionRequest provisionRequest = new ProvisionRequest(
                actor.tenantId(),
                request.id(),
                request.requestNo(),
                request.environmentId(),
                request.environmentCode(),
                request.environmentName(),
                request.physicalSubsystemId(),
                request.physicalSubsystemCode(),
                request.physicalSubsystemName(),
                provisionItems
        );
        return automatedDeploymentProvider.previewProvision(provisionRequest);
    }

    @Transactional
    public List<EnvironmentInstance> fulfillRequest(AuthUser actor, long requestId, FulfillmentCommand command) {
        requireActor(actor);
        if (command == null) {
            throw badRequest("办理下发参数不能为空");
        }
        if (command.instances().isEmpty()) {
            throw badRequest("请至少填报一台下发机器/实例");
        }
        ResourceRequest request = store.lockRequest(actor.tenantId(), requestId)
                .orElseThrow(() -> notFound("资源申请工单不存在：" + requestId));
        if (request.status() != RequestStatus.APPROVED) {
            throw conflict("只有审批通过 (APPROVED) 的工单允许办理下发，当前状态: " + request.status());
        }
        if (command.rowVersion() != null && !command.rowVersion().equals(request.rowVersion())) {
            throw conflict("资源申请工单已被其他操作更新，请刷新重试");
        }
        List<ResourceRequestItem> items = store.listItems(actor.tenantId(), requestId);
        Map<Long, ResourceRequestItem> itemsById = new LinkedHashMap<>();
        Map<Long, List<ResourceRequestItem>> itemsByUnitId = new LinkedHashMap<>();
        for (ResourceRequestItem item : items) {
            itemsById.put(item.id(), item);
            itemsByUnitId.computeIfAbsent(item.deploymentUnitId(), ignored -> new java.util.ArrayList<>()).add(item);
        }

        BigDecimal requestedCpu = items.stream().map(it -> it.cpuCores().multiply(BigDecimal.valueOf(it.plannedNodeCount()))
                .add(it.hasSidecar() ? it.sidecarCpuCores() : BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal requestedMem = items.stream().map(it -> it.memoryGb().multiply(BigDecimal.valueOf(it.plannedNodeCount()))
                .add(it.hasSidecar() ? it.sidecarMemoryGb() : BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal requestedStorage = items.stream().map(it -> it.databaseStorageGb().add(it.fileStorageGb()).add(it.extraCbsGb()).add(it.localDiskGb()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int requestedNodes = items.stream().mapToInt(ResourceRequestItem::plannedNodeCount).sum();

        BigDecimal actualCpu = command.instances().stream().map(i -> i.cpuCores() == null ? BigDecimal.ZERO : nonNegativeInteger(i.cpuCores(), "CPU核心数")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualMem = command.instances().stream().map(i -> i.memoryGb() == null ? BigDecimal.ZERO : nonNegativeInteger(i.memoryGb(), "内存容量")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualStorage = command.instances().stream().map(i -> (i.databaseStorageGb() == null ? BigDecimal.ZERO : nonNegativeInteger(i.databaseStorageGb(), "数据库存储"))
                .add(i.fileStorageGb() == null ? BigDecimal.ZERO : nonNegativeInteger(i.fileStorageGb(), "文件存储"))
                .add(i.extraCbsGb() == null ? BigDecimal.ZERO : nonNegativeInteger(i.extraCbsGb(), "CBS容量"))
                .add(i.localDiskGb() == null ? BigDecimal.ZERO : nonNegativeInteger(i.localDiskGb(), "本地盘容量"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        int actualNodes = command.instances().size();

        boolean hasDiff = requestedCpu.compareTo(actualCpu) != 0
                || requestedMem.compareTo(actualMem) != 0
                || requestedStorage.compareTo(actualStorage) != 0
                || requestedNodes != actualNodes;

        String diffReason = trimToNull(command.differenceReason());
        if (hasDiff && diffReason == null) {
            throw badRequest("实际下发资源与工单申请值存在差异（申请: " + requestedNodes + "节点/" + requestedCpu + "核/" + requestedMem + "GB/" + requestedStorage + "GB，实际: "
                    + actualNodes + "节点/" + actualCpu + "核/" + actualMem + "GB/" + actualStorage + "GB），必须填写差异原因");
        }

        FulfillmentMode mode = command.fulfillmentMode() == null ? FulfillmentMode.MANUAL : command.fulfillmentMode();
        LocalDateTime now = LocalDateTime.now(clock);
        List<EnvironmentInstance> createdList = new java.util.ArrayList<>();
        int seq = 1;
        for (FulfillInstanceItemCommand instCmd : command.instances()) {
            if (instCmd.deploymentUnitId() == null) {
                throw badRequest("第 " + seq + " 台实例必须指定所属部署单元");
            }
            ResourceRequestItem sourceItem;
            if (instCmd.sourceItemId() != null) {
                sourceItem = itemsById.get(instCmd.sourceItemId());
                if (sourceItem == null) {
                    throw badRequest("第 " + seq + " 台实例的来源明细不属于当前资源工单");
                }
                if (sourceItem.deploymentUnitId() != instCmd.deploymentUnitId()) {
                    throw badRequest("第 " + seq + " 台实例的来源明细与所属部署单元不一致");
                }
            } else {
                List<ResourceRequestItem> matchedItems = itemsByUnitId.getOrDefault(instCmd.deploymentUnitId(), List.of());
                if (matchedItems.isEmpty()) {
                    throw badRequest("第 " + seq + " 台实例所属部署单元不在当前资源工单明细中");
                }
                if (matchedItems.size() > 1) {
                    throw badRequest("第 " + seq + " 台实例所属部署单元在工单中存在多条需求明细，必须指定来源明细");
                }
                sourceItem = matchedItems.get(0);
            }
            DeploymentUnitRef unit = store.findDeploymentUnit(actor.tenantId(), instCmd.deploymentUnitId())
                    .orElseThrow(() -> badRequest("部署单元不存在：" + instCmd.deploymentUnitId()));
            if (!"ACTIVE".equals(unit.status())) {
                throw badRequest("部署单元已停用：" + unit.code());
            }
            if (unit.physicalSubsystemId() != request.physicalSubsystemId()) {
                throw badRequest("部署单元「" + unit.code() + "」不属于工单物理子系统");
            }
            if (unit.currentVersion() <= 0) {
                throw badRequest("部署单元「" + unit.code() + "」尚未发布版本，不能生成环境部署实例");
            }
            String machineName = requireText(instCmd.machineName(), "机器标识/主机名", 128);
            String ipAddress = requireText(instCmd.ipAddress(), "IP 地址", 64);

            // Rule 41: Guarantee same machine and IP only has 1 active instance in this environment
            var existingActive = store.findActiveInstanceByMachineOrIp(actor.tenantId(), request.environmentId(), machineName, ipAddress, null);
            if (existingActive.isPresent()) {
                throw conflict("具体环境「" + request.environmentName() + "」中已存在机器名或 IP 相同的在用实例：" + machineName + " / " + ipAddress);
            }

            String serverType = validateParameter(actor, SERVER_TYPE_CATEGORY,
                    instCmd.serverType() == null || instCmd.serverType().isBlank() ? DEFAULT_SERVER_TYPE_CODE : instCmd.serverType(), "服务器类型");
            String jdk = validateOptionalParameter(actor, JDK_VERSION_CATEGORY, instCmd.jdkVersion(), "JDK 版本");
            String middleware = validateOptionalParameter(actor, MIDDLEWARE_CATEGORY, instCmd.middleware(), "中间件");
            String os = validateOptionalParameter(actor, OPERATING_SYSTEM_CATEGORY, instCmd.operatingSystem(), "操作系统");
            String platform = optional(instCmd.deploymentPlatform() == null ? request.physicalSubsystemDeploymentPlatform() : instCmd.deploymentPlatform(), "部署平台", 64);
            ZoneRef zoneRef = resolveFulfillmentNetworkZone(actor, instCmd.networkZoneId(), sourceItem, unit, seq);
            Long zoneId = zoneRef == null ? null : zoneRef.id();
            String zoneName = zoneRef == null ? null : zoneRef.name();
            String zone = zoneName == null
                    ? optional(instCmd.networkZone() == null ? sourceItem.networkZone() : instCmd.networkZone(),
                    "网络分区", 100)
                    : zoneName;
            if (networkAccessService != null) {
                networkAccessService.requireIpInActiveSubnet(actor.tenantId(), zoneId, ipAddress,
                        "第 " + seq + " 台实例 IP 地址");
            }

            long instanceId = nextId();
            String instanceNo = "INS" + instanceId;

            EnvironmentInstance instance = new EnvironmentInstance(
                    instanceId,
                    actor.tenantId(),
                    instanceNo,
                    request.environmentId(),
                    request.environmentCode(),
                    request.environmentName(),
                    request.environmentTypeName(),
                    unit.id(),
                    unit.code(),
                    unit.name(),
                    unit.kind(),
                    unit.currentVersionId(),
                    unit.currentVersion(),
                    unit.currentVersion(),
                    false,
                    request.physicalSubsystemId(),
                    request.physicalSubsystemCode(),
                    request.physicalSubsystemName(),
                    request.id(),
                    request.requestNo(),
                    sourceItem.id(),
                    machineName,
                    ipAddress,
                    serverType,
                    platform,
                    zoneId,
                    zoneName,
                    zone,
                    InstanceStatus.ACTIVE,
                    instCmd.cpuCores() == null ? BigDecimal.ZERO : nonNegativeInteger(instCmd.cpuCores(), "CPU核心数"),
                    instCmd.memoryGb() == null ? BigDecimal.ZERO : nonNegativeInteger(instCmd.memoryGb(), "内存容量"),
                    instCmd.databaseStorageGb() == null ? BigDecimal.ZERO : nonNegativeInteger(instCmd.databaseStorageGb(), "数据库存储"),
                    instCmd.fileStorageGb() == null ? BigDecimal.ZERO : nonNegativeInteger(instCmd.fileStorageGb(), "文件存储"),
                    instCmd.extraCbsGb() == null ? BigDecimal.ZERO : nonNegativeInteger(instCmd.extraCbsGb(), "CBS容量"),
                    instCmd.localDiskGb() == null ? BigDecimal.ZERO : nonNegativeInteger(instCmd.localDiskGb(), "本地盘容量"),
                    optional(instCmd.databaseName(), "数据库库名", 100),
                    optional(instCmd.databaseVersion(), "数据库版本", 100),
                    jdk,
                    middleware,
                    os,
                    Boolean.TRUE.equals(instCmd.needsNft()),
                    Boolean.TRUE.equals(instCmd.needsFserver()),
                    Boolean.TRUE.equals(instCmd.needsJobexecutor()),
                    mode,
                    diffReason,
                    optional(instCmd.remark(), "备注", 1000),
                    null,
                    null,
                    null,
                    0L,
                    actor.id(),
                    actor.id(),
                    now,
                    now
            );
            store.insertInstance(instance);
            createdList.add(instance);
            seq++;
        }

        RequestStatus nextStatus = hasDiff ? RequestStatus.DIFF_FULFILLED : RequestStatus.FULFILLED;
        if (!store.compareAndSetStatus(actor.tenantId(), requestId, RequestStatus.APPROVED,
                request.rowVersion(), nextStatus, actor.id())) {
            throw conflict("资源申请工单已被其他操作更新，请刷新后重新办理下发");
        }

        Map<String, Object> summaryMap = new LinkedHashMap<>();
        summaryMap.put("fulfillmentMode", mode);
        summaryMap.put("instanceCount", createdList.size());
        summaryMap.put("hasDifference", hasDiff);
        summaryMap.put("toStatus", nextStatus);
        summaryMap.put("differenceReason", diffReason);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(summaryMap);
        } catch (JsonProcessingException e) {
            snapshotJson = "{}";
        }
        store.insertHistory(new HistoryEvent(
                nextId(),
                actor.tenantId(),
                requestId,
                nextStatus.name(),
                RequestStatus.APPROVED,
                nextStatus,
                request.currentBusinessRound(),
                fulfillmentSummary(createdList.size(), mode, hasDiff, diffReason),
                snapshotJson,
                null,
                actor.id(),
                now
        ));

        return List.copyOf(createdList);
    }

    private static String fulfillmentSummary(int instanceCount, FulfillmentMode mode, boolean hasDiff, String diffReason) {
        String modeLabel = mode == FulfillmentMode.AUTOMATED ? "自动部署" : "手动输入";
        if (hasDiff) {
            return "资源已差异下发，生成 " + instanceCount + " 个环境部署实例（" + modeLabel + "），差异原因：" + diffReason;
        }
        return "资源已按申请规格下发，生成 " + instanceCount + " 个环境部署实例（" + modeLabel + "）";
    }

    @Transactional(readOnly = true)
    public List<EnvironmentInstance> listInstances(AuthUser actor, Long environmentId, Long physicalSubsystemId,
                                                   Long deploymentUnitId, InstanceStatus status,
                                                   String keyword, int limit, int offset) {
        requireActor(actor);
        return store.listInstances(actor.tenantId(), environmentId, physicalSubsystemId, deploymentUnitId, status,
                keyword, limit <= 0 ? 50 : Math.min(limit, 200), Math.max(0, offset));
    }

    @Transactional(readOnly = true)
    public EnvironmentInstance detailInstance(AuthUser actor, long instanceId) {
        requireActor(actor);
        return store.findInstance(actor.tenantId(), instanceId)
                .orElseThrow(() -> notFound("环境部署实例不存在：" + instanceId));
    }

    @Transactional
    public EnvironmentInstance offlineInstance(AuthUser actor, long instanceId, OfflineInstanceCommand command) {
        requireActor(actor);
        if (command == null) {
            throw badRequest("下线参数不能为空");
        }
        String reason = requireText(command.offlineReason(), "下线原因", 1000);
        EnvironmentInstance instance = store.lockInstance(actor.tenantId(), instanceId)
                .orElseThrow(() -> notFound("环境部署实例不存在：" + instanceId));
        if (instance.status() != InstanceStatus.ACTIVE) {
            throw conflict("该实例当前已处于下线状态，无需重复下线");
        }
        if (command.rowVersion() != null && !command.rowVersion().equals(instance.rowVersion())) {
            throw conflict("实例已被其他人修改，请刷新后重试");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean ok = store.offlineInstance(actor.tenantId(), instanceId, instance.rowVersion(), reason, actor.id(), now);
        if (!ok) {
            throw conflict("实例下线失败，可能已被其他人操作");
        }
        return store.findInstance(actor.tenantId(), instanceId)
                .orElseThrow(() -> notFound("环境部署实例不存在：" + instanceId));
    }

    @Transactional(readOnly = true)
    public List<InstanceDisasterRecovery> listInstanceDisasterRecoveries(AuthUser actor, long instanceId) {
        requireActor(actor);
        store.findInstance(actor.tenantId(), instanceId)
                .orElseThrow(() -> notFound("环境部署实例不存在：" + instanceId));
        return store.listDisasterRecoveries(actor.tenantId(), null, instanceId);
    }

    @Transactional(readOnly = true)
    public List<InstanceDisasterRecovery> listDisasterRecoveries(AuthUser actor, Long deploymentUnitId, Long instanceId) {
        requireActor(actor);
        return store.listDisasterRecoveries(actor.tenantId(), deploymentUnitId, instanceId);
    }

    @Transactional
    public InstanceDisasterRecovery createDisasterRecovery(AuthUser actor, DisasterRecoveryCommand command) {
        requireActor(actor);
        if (command == null) {
            throw badRequest("灾备关系创建参数不能为空");
        }
        if (command.primaryInstanceId() == null || command.standbyInstanceId() == null) {
            throw badRequest("必须指定主实例和备实例");
        }
        if (command.primaryInstanceId().equals(command.standbyInstanceId())) {
            throw badRequest("主实例和备实例不能相同");
        }
        if (command.drMode() == null) {
            throw badRequest("必须指定灾备模式");
        }
        EnvironmentInstance primary = store.findInstance(actor.tenantId(), command.primaryInstanceId())
                .orElseThrow(() -> notFound("主实例不存在：" + command.primaryInstanceId()));
        EnvironmentInstance standby = store.findInstance(actor.tenantId(), command.standbyInstanceId())
                .orElseThrow(() -> notFound("备实例不存在：" + command.standbyInstanceId()));

        if (primary.status() != InstanceStatus.ACTIVE || standby.status() != InstanceStatus.ACTIVE) {
            throw badRequest("只有在用 (ACTIVE) 的实例才能建立灾备关系");
        }
        if (primary.deploymentUnitId() != standby.deploymentUnitId()) {
            throw badRequest("灾备关系只能在同一部署单元的实例之间建立（主实例属于「" + primary.deploymentUnitCode()
                    + "」，备实例属于「" + standby.deploymentUnitCode() + "」）");
        }
        if (command.deploymentUnitId() != null && !command.deploymentUnitId().equals(primary.deploymentUnitId())) {
            throw badRequest("灾备关系所属部署单元必须与主备实例所属部署单元一致");
        }
        if (store.findDisasterRecoveryPair(actor.tenantId(), primary.id(), standby.id()).isPresent()) {
            throw conflict("主实例「" + primary.machineName() + "」与备实例「" + standby.machineName() + "」之间已存在灾备关系");
        }

        long drId = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        InstanceDisasterRecovery dr = new InstanceDisasterRecovery(
                drId,
                actor.tenantId(),
                primary.deploymentUnitId(),
                primary.deploymentUnitCode(),
                primary.deploymentUnitName(),
                primary.id(),
                primary.machineName(),
                primary.ipAddress(),
                primary.environmentCode(),
                primary.environmentName(),
                standby.id(),
                standby.machineName(),
                standby.ipAddress(),
                standby.environmentCode(),
                standby.environmentName(),
                command.drMode(),
                optional(command.description(), "灾备说明", 1000),
                actor.id(),
                now,
                now
        );
        store.insertDisasterRecovery(dr);
        return dr;
    }

    @Transactional
    public void deleteDisasterRecovery(AuthUser actor, long id) {
        requireActor(actor);
        store.findDisasterRecovery(actor.tenantId(), id)
                .orElseThrow(() -> notFound("灾备关系记录不存在：" + id));
        boolean deleted = store.deleteDisasterRecovery(actor.tenantId(), id);
        if (!deleted) {
            throw conflict("灾备关系解除失败");
        }
    }

    @Transactional(readOnly = true)
    public List<EnvironmentInstance> listAvailableStandbyInstances(AuthUser actor, long deploymentUnitId, Long excludeInstanceId) {
        requireActor(actor);
        return store.listAvailableStandbyInstances(actor.tenantId(), deploymentUnitId, excludeInstanceId);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private static BusinessException notFound(String message) {
        throw new ArchitectureNotFoundException(message);
    }

    private record EnvironmentInput(String code, String name, EnvironmentType type,
                                    String description, String remark) {
    }

    private record RequestInput(PhysicalSubsystemRef physical, Environment environment,
                                RequestType requestType, String reason, long contactUserId,
                                List<ItemInput> items) {
    }

    private record ItemInput(DeploymentUnitRef unit, String deploymentUnitDescription,
                             BigDecimal databaseStorageGb,
                             BigDecimal fileStorageGb, Long networkZoneId, String networkZoneName,
                             String networkZone, String serverType,
                             BigDecimal cpuCores, BigDecimal memoryGb, int appWebGroupCount,
                             int plannedNodeCount, BigDecimal sidecarCpuCores,
                             BigDecimal sidecarMemoryGb, boolean hasSidecar, String databaseName,
                             String databaseVersion, String jdkVersion, String middleware,
                             String operatingSystem, BigDecimal extraCbsGb, BigDecimal localDiskGb,
                             boolean needsNft, boolean needsFserver, boolean needsJobexecutor,
                             String remark) {
    }
}

package com.ccb.architecture.environment.web;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.Environment;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentInstance;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.HistoryEvent;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceDisasterRecovery;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.OfflineInstanceCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionPreviewResult;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RecordStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceItemCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequestItem;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceSummary;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore.DeploymentUnitRef;
import com.ccb.architecture.environment.service.EnvironmentResourceService;
import com.ccb.architecture.environment.service.EnvironmentResourceService.AccessScope;
import com.ccb.architecture.environment.service.EnvironmentResourceService.EnvironmentCommand;
import com.ccb.architecture.environment.service.EnvironmentResourceService.ResourceRequestCommand;
import com.ccb.architecture.environment.service.EnvironmentResourceService.ResourceRequestDetail;
import com.ccb.architecture.environment.service.ResourceRequestSubmissionService;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** 具体环境和资源申请 HTTP 边界（REQ-20260824-052）。 */
@RestController
@RequestMapping("/api/architecture")
public class EnvironmentResourceController {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentResourceController.class);
    private static final String RESOURCE_MANAGE_AUTHORITY = "architecture:resource-request:manage";
    private static final String ARCHITECTURE_MANAGE_AUTHORITY = "architecture:manage";

    private final EnvironmentResourceService service;
    private final com.ccb.architecture.plan.service.PlanWorkOrderService planWorkOrderService;
    private final ResourceRequestSubmissionService workflowService;
    private final SystemOperationAudit operationAudit;

    public EnvironmentResourceController(EnvironmentResourceService service,
                                         ResourceRequestSubmissionService workflowService,
                                         SystemOperationAudit operationAudit,
                                         com.ccb.architecture.plan.service.PlanWorkOrderService planWorkOrderService) {
        this.service = service;
        this.workflowService = workflowService;
        this.operationAudit = operationAudit;
        this.planWorkOrderService = planWorkOrderService;
    }

    @GetMapping("/environment-types")
    @PreAuthorize("hasAnyAuthority('architecture:environment:view','architecture:environment:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<EnvironmentTypeResponse>> listEnvironmentTypes(
            @RequestParam(required = false) RecordStatus status,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listEnvironmentTypes(actor, status).stream().map(this::toType).toList());
    }

    @GetMapping("/environments")
    @PreAuthorize("hasAnyAuthority('architecture:environment:view','architecture:environment:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<EnvironmentResponse>> listEnvironments(
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listEnvironments(actor, typeCode, status, keyword, limit, offset)
                .stream().map(this::toEnvironment).toList());
    }

    @GetMapping("/environments/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:environment:view','architecture:environment:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<EnvironmentDetailResponse> detailEnvironment(@PathVariable long id,
                                                                    @AuthenticationPrincipal AuthUser actor) {
        Environment environment = service.detailEnvironment(actor, id);
        ResourceSummary summary = service.environmentSummary(actor, id);
        return success(new EnvironmentDetailResponse(toEnvironment(environment), toSummary(summary)));
    }

    @PostMapping("/environments")
    @PreAuthorize("hasAnyAuthority('architecture:environment:manage','architecture:manage')")
    public ApiResponse<EnvironmentResponse> createEnvironment(@RequestBody UpsertEnvironmentRequest request,
                                                              @AuthenticationPrincipal AuthUser actor) {
        Environment environment = audited(actor, "architecture.environment.create", "POST",
                "/api/architecture/environments", () -> service.createEnvironment(actor,
                        toEnvironmentCommand(request)));
        return success(toEnvironment(environment));
    }

    @PutMapping("/environments/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:environment:manage','architecture:manage')")
    public ApiResponse<EnvironmentResponse> updateEnvironment(@PathVariable long id,
                                                              @RequestBody UpsertEnvironmentRequest request,
                                                              @AuthenticationPrincipal AuthUser actor) {
        Environment environment = audited(actor, "architecture.environment.update", "PUT",
                "/api/architecture/environments/" + id, () -> service.updateEnvironment(actor, id,
                        toEnvironmentCommand(request)));
        return success(toEnvironment(environment));
    }

    @PostMapping("/environments/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('architecture:environment:manage','architecture:manage')")
    public ApiResponse<EnvironmentResponse> deactivateEnvironment(@PathVariable long id,
                                                                  @RequestBody RowVersionRequest request,
                                                                  @AuthenticationPrincipal AuthUser actor) {
        Environment environment = audited(actor, "architecture.environment.deactivate", "POST",
                "/api/architecture/environments/" + id + "/deactivate",
                () -> service.changeEnvironmentStatus(actor, id, requiredRowVersion(request),
                        RecordStatus.INACTIVE));
        return success(toEnvironment(environment));
    }

    @PostMapping("/environments/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('architecture:environment:manage','architecture:manage')")
    public ApiResponse<EnvironmentResponse> reactivateEnvironment(@PathVariable long id,
                                                                  @RequestBody RowVersionRequest request,
                                                                  @AuthenticationPrincipal AuthUser actor) {
        Environment environment = audited(actor, "architecture.environment.reactivate", "POST",
                "/api/architecture/environments/" + id + "/reactivate",
                () -> service.changeEnvironmentStatus(actor, id, requiredRowVersion(request),
                        RecordStatus.ACTIVE));
        return success(toEnvironment(environment));
    }

    @PostMapping("/environments/{id}/delete")
    @PreAuthorize("hasAnyAuthority('architecture:environment:manage','architecture:manage')")
    public ApiResponse<Void> deleteEnvironment(@PathVariable long id,
                                               @RequestBody RowVersionRequest request,
                                               @AuthenticationPrincipal AuthUser actor) {
        audited(actor, "architecture.environment.delete", "POST",
                "/api/architecture/environments/" + id + "/delete",
                () -> {
                    service.deleteEnvironment(actor, id, requiredRowVersion(request));
                    return null;
                });
        return success(null);
    }

    @GetMapping("/resource-requests/options/deployment-units")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:view','architecture:resource-request:apply',"
            + "'architecture:resource-request:manage','architecture:view','architecture:apply','architecture:manage')")
    public ApiResponse<List<DeploymentUnitOptionResponse>> deploymentUnitOptions(
            @RequestParam long physicalSubsystemId,
            @RequestParam(defaultValue = "100") int limit,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listDeploymentUnitOptions(actor, physicalSubsystemId, limit)
                .stream().map(this::toDeploymentUnit).toList());
    }

    @GetMapping("/resource-requests")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:view','architecture:resource-request:apply',"
            + "'architecture:resource-request:manage','architecture:view','architecture:apply','architecture:manage')")
    public ApiResponse<List<ResourceRequestSummaryResponse>> listResourceRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor,
            Authentication authentication) {
        return success(service.listRequests(actor, accessScope(authentication), status, environmentId,
                        physicalSubsystemId, limit, offset)
                .stream().map(this::toRequestSummary).toList());
    }

    @GetMapping("/resource-requests/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:view','architecture:resource-request:apply',"
            + "'architecture:resource-request:manage','architecture:view','architecture:apply','architecture:manage')")
    public ApiResponse<ResourceRequestDetailResponse> detailResourceRequest(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser actor,
            Authentication authentication) {
        return success(toRequestDetail(service.detailRequest(actor, accessScope(authentication), id)));
    }

    @PostMapping("/resource-requests")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:apply','architecture:resource-request:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<ResourceRequestDetailResponse> createResourceRequest(
            @RequestBody UpsertResourceRequestRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        ResourceRequestDetail detail = audited(actor, "architecture.resource-request.create", "POST",
                "/api/architecture/resource-requests", () -> service.createRequest(actor,
                        toRequestCommand(request, null)));
        if (request != null && request.planTaskId() != null) {
            planWorkOrderService.registerCreatedWorkOrder(actor.tenantId(), request.planTaskId(),
                    com.ccb.architecture.plan.model.PlanModels.WorkOrderType.RESOURCE_REQUEST,
                    detail.request().id());
        }
        return success(toRequestDetail(detail));
    }

    @PutMapping("/resource-requests/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:apply','architecture:resource-request:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<ResourceRequestDetailResponse> updateResourceRequest(
            @PathVariable long id,
            @RequestBody UpsertResourceRequestRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        ResourceRequestDetail detail = audited(actor, "architecture.resource-request.update", "PUT",
                "/api/architecture/resource-requests/" + id, () -> service.updateRequest(actor, id,
                        toRequestCommand(request, request == null ? null : request.rowVersion())));
        return success(toRequestDetail(detail));
    }

    @PostMapping("/resource-requests/{id}/submit")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:apply','architecture:resource-request:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<ResourceRequestDetailResponse> submitResourceRequest(
            @PathVariable long id,
            @RequestBody RowVersionRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        ResourceRequestDetail detail = audited(actor, "architecture.resource-request.submit", "POST",
                "/api/architecture/resource-requests/" + id + "/submit",
                () -> workflowService.submit(actor, id, requiredRowVersion(request)));
        return success(toRequestDetail(detail));
    }

    @PostMapping("/resource-requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:apply','architecture:resource-request:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<ResourceRequestDetailResponse> cancelResourceRequest(
            @PathVariable long id,
            @RequestBody RowVersionRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        ResourceRequestDetail detail = audited(actor, "architecture.resource-request.cancel", "POST",
                "/api/architecture/resource-requests/" + id + "/cancel",
                () -> workflowService.cancel(actor, id, requiredRowVersion(request)));
        return success(toRequestDetail(detail));
    }

    private <T> T audited(AuthUser actor, String operationCode, String method, String path,
                          Supplier<T> action) {
        try {
            T result = action.get();
            recordAudit(actor, operationCode, method, path, null, TraceId.getOrCreate());
            return result;
        } catch (BusinessException failure) {
            recordAudit(actor, operationCode, method, path, businessAuditMessage(failure), TraceId.getOrCreate());
            throw failure;
        } catch (RuntimeException failure) {
            recordAudit(actor, operationCode, method, path, "环境资源操作失败", TraceId.getOrCreate());
            throw failure;
        }
    }

    private void recordAudit(AuthUser actor, String operationCode, String method, String path,
                             String errorMessage, String traceId) {
        try {
            SystemOperationAuditCommand command = new SystemOperationAuditCommand(
                    actor, operationCode, method, path, errorMessage, traceId);
            if (errorMessage == null) {
                operationAudit.recordSuccess(command);
            } else {
                operationAudit.recordFailure(command);
            }
        } catch (RuntimeException auditFailure) {
            log.warn("环境资源审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    private static String businessAuditMessage(BusinessException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "环境资源操作失败" : message;
    }

    private EnvironmentCommand toEnvironmentCommand(UpsertEnvironmentRequest request) {
        return new EnvironmentCommand(request == null ? null : request.code(),
                request == null ? null : request.name(),
                request == null ? null : request.typeCode(),
                request == null ? null : request.description(),
                request == null ? null : request.remark(),
                request == null ? null : request.rowVersion());
    }

    private ResourceRequestCommand toRequestCommand(UpsertResourceRequestRequest request, Long rowVersion) {
        return new ResourceRequestCommand(
                request == null ? null : request.physicalSubsystemId(),
                request == null ? null : request.environmentId(),
                request == null ? null : request.contactUserId(),
                parseRequestType(request == null ? null : request.requestType()),
                request == null ? null : request.reason(),
                request == null ? List.of() : request.items(),
                rowVersion);
    }

    private RequestType parseRequestType(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("requestType 不能为空");
        }
        try {
            return RequestType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("requestType 必须为 INITIAL、EXPANSION、SHRINK 或 ADJUSTMENT");
        }
    }

    private AccessScope accessScope(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(authority ->
                RESOURCE_MANAGE_AUTHORITY.equals(authority.getAuthority())
                        || ARCHITECTURE_MANAGE_AUTHORITY.equals(authority.getAuthority()))) {
            return AccessScope.MANAGE;
        }
        return AccessScope.OWN;
    }

    private long requiredRowVersion(RowVersionRequest request) {
        Long rowVersion = request == null ? null : request.rowVersion();
        if (rowVersion == null || rowVersion < 0) {
            throw badRequest("rowVersion 必须为非负整数");
        }
        return rowVersion;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    @GetMapping("/resource-requests/{id}/preview-automated-provision")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:view','architecture:resource-request:apply',"
            + "'architecture:resource-request:manage','architecture:view','architecture:manage')")
    public ApiResponse<ProvisionPreviewResult> previewAutomatedProvision(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.previewAutomatedProvision(actor, id));
    }

    @PostMapping("/resource-requests/{id}/fulfill")
    @PreAuthorize("hasAnyAuthority('architecture:resource-request:manage','architecture:manage')")
    public ApiResponse<List<EnvironmentInstanceResponse>> fulfillResourceRequest(
            @PathVariable long id,
            @RequestBody FulfillmentCommand command,
            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.resource-request.fulfill", "POST",
                "/api/architecture/resource-requests/" + id + "/fulfill",
                () -> success(service.fulfillRequest(actor, id, command).stream().map(this::toInstance).toList()));
    }

    @GetMapping("/instances")
    @PreAuthorize("hasAnyAuthority('architecture:instance:view','architecture:instance:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<EnvironmentInstanceResponse>> listInstances(
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(required = false) Long deploymentUnitId,
            @RequestParam(required = false) InstanceStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listInstances(actor, environmentId, physicalSubsystemId, deploymentUnitId,
                status, keyword, limit, offset).stream().map(this::toInstance).toList());
    }

    @GetMapping("/instances/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:instance:view','architecture:instance:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<EnvironmentInstanceResponse> detailInstance(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser actor) {
        return success(toInstance(service.detailInstance(actor, id)));
    }

    @PostMapping("/instances/{id}/offline")
    @PreAuthorize("hasAnyAuthority('architecture:instance:manage','architecture:manage')")
    public ApiResponse<EnvironmentInstanceResponse> offlineInstance(
            @PathVariable long id,
            @RequestBody OfflineInstanceCommand command,
            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.instance.offline", "POST",
                "/api/architecture/instances/" + id + "/offline",
                () -> success(toInstance(service.offlineInstance(actor, id, command))));
    }

    @GetMapping("/instances/{id}/disaster-recoveries")
    @PreAuthorize("hasAnyAuthority('architecture:instance:view','architecture:instance:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<InstanceDisasterRecoveryResponse>> listInstanceDisasterRecoveries(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listInstanceDisasterRecoveries(actor, id).stream().map(this::toDr).toList());
    }

    @GetMapping("/instance-disaster-recoveries")
    @PreAuthorize("hasAnyAuthority('architecture:instance:view','architecture:instance:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<InstanceDisasterRecoveryResponse>> listDisasterRecoveries(
            @RequestParam(required = false) Long deploymentUnitId,
            @RequestParam(required = false) Long instanceId,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listDisasterRecoveries(actor, deploymentUnitId, instanceId).stream().map(this::toDr).toList());
    }

    @PostMapping("/instance-disaster-recoveries")
    @PreAuthorize("hasAnyAuthority('architecture:instance:manage','architecture:manage')")
    public ApiResponse<InstanceDisasterRecoveryResponse> createDisasterRecovery(
            @RequestBody DisasterRecoveryCommand command,
            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.instance-dr.create", "POST",
                "/api/architecture/instance-disaster-recoveries",
                () -> success(toDr(service.createDisasterRecovery(actor, command))));
    }

    @DeleteMapping("/instance-disaster-recoveries/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:instance:manage','architecture:manage')")
    public ApiResponse<Void> deleteDisasterRecovery(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.instance-dr.delete", "DELETE",
                "/api/architecture/instance-disaster-recoveries/" + id,
                () -> {
                    service.deleteDisasterRecovery(actor, id);
                    return success(null);
                });
    }

    @GetMapping("/instances/options/available-standbys")
    @PreAuthorize("hasAnyAuthority('architecture:instance:view','architecture:instance:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<EnvironmentInstanceResponse>> listAvailableStandbys(
            @RequestParam long deploymentUnitId,
            @RequestParam(required = false) Long excludeInstanceId,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listAvailableStandbyInstances(actor, deploymentUnitId, excludeInstanceId)
                .stream().map(this::toInstance).toList());
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

    private EnvironmentTypeResponse toType(EnvironmentType type) {
        return new EnvironmentTypeResponse(type.code(), type.name());
    }

    private EnvironmentResponse toEnvironment(Environment environment) {
        return new EnvironmentResponse(environment.id(), environment.code(), environment.name(),
                environment.typeCode(), environment.typeName(), environment.status(),
                environment.description(), environment.remark(), environment.rowVersion(),
                environment.createdBy(), environment.updatedBy(), environment.createdAt(), environment.updatedAt());
    }

    private ResourceSummaryResponse toSummary(ResourceSummary summary) {
        return new ResourceSummaryResponse(summary.environmentId(), summary.requestCount(),
                summary.approvedRequestCount(), summary.pendingRequestCount(), summary.requestedCpuCores(),
                summary.requestedMemoryGb(), summary.requestedStorageGb(), summary.requestedNodeCount(),
                summary.actualCpuCores(), summary.actualMemoryGb(), summary.actualStorageGb(),
                summary.actualNodeCount());
    }

    private DeploymentUnitOptionResponse toDeploymentUnit(DeploymentUnitRef unit) {
        return new DeploymentUnitOptionResponse(unit.id(), unit.code(), unit.name(), unit.kind(),
                unit.physicalSubsystemId(), unit.relatedDeploymentUnitName(), unit.deploymentUnitType(),
                unit.description(), unit.defaultNetworkZoneId(), unit.defaultNetworkZoneName());
    }

    private ResourceRequestSummaryResponse toRequestSummary(ResourceRequest request) {
        return new ResourceRequestSummaryResponse(request.id(), request.requestNo(), request.physicalSubsystemId(),
                request.physicalSubsystemCode(), request.physicalSubsystemShortName(),
                request.physicalSubsystemName(), request.physicalSubsystemBusinessGroupName(),
                request.physicalSubsystemSystemLevelCode(), request.physicalSubsystemDeploymentPlatform(),
                request.physicalSubsystemDisasterRecoveryMode(),
                request.environmentId(), request.environmentCode(), request.environmentName(),
                request.environmentTypeName(), request.applicantId(), request.contactUserId(),
                request.requestType(), request.reason(),
                request.status(), request.currentBusinessRound(),
                request.cancellationRequested(), request.rowVersion(), request.createdBy(), request.updatedBy(),
                request.createdAt(), request.updatedAt());
    }

    private ResourceRequestDetailResponse toRequestDetail(ResourceRequestDetail detail) {
        return new ResourceRequestDetailResponse(toRequestSummary(detail.request()),
                detail.items().stream().map(this::toRequestItem).toList(),
                detail.history().stream().map(this::toHistory).toList());
    }

    private ResourceRequestItemResponse toRequestItem(ResourceRequestItem item) {
        return new ResourceRequestItemResponse(item.id(), item.itemSeq(), item.deploymentUnitId(),
                item.deploymentUnitCode(), item.deploymentUnitName(), item.deploymentUnitKind(),
                item.relatedDeploymentUnitName(), item.deploymentUnitDescription(),
                item.deploymentUnitType(), item.databaseStorageGb(),
                item.fileStorageGb(), item.networkZoneId(), item.networkZoneName(), item.networkZone(),
                item.serverType(), item.cpuCores(), item.memoryGb(),
                item.appWebGroupCount(), item.plannedNodeCount(), item.totalCpuCores(), item.totalMemoryGb(),
                item.sidecarCpuCores(), item.sidecarMemoryGb(), item.sidecarMemoryRatio(), item.hasSidecar(),
                item.databaseName(), item.databaseVersion(), item.jdkVersion(), item.middleware(),
                item.operatingSystem(), item.extraCbsGb(), item.localDiskGb(), item.needsNft(),
                item.needsFserver(), item.needsJobexecutor(), item.remark());
    }

    private HistoryResponse toHistory(HistoryEvent event) {
        return new HistoryResponse(event.id(), event.eventType(), event.fromStatus(), event.toStatus(),
                event.businessRound(), event.summary(), event.snapshotJson(), event.diffJson(),
                event.operatorId(), event.occurredAt());
    }

    @JsonIgnoreProperties({"tenantId", "createdBy", "updatedBy", "accessScope"})
    public record UpsertEnvironmentRequest(String code, String name, String typeCode,
                                           String description, String remark, Long rowVersion) {
    }

    @JsonIgnoreProperties(value = {"tenantId", "applicantId", "accessScope"},
            ignoreUnknown = true)
    public record UpsertResourceRequestRequest(Long physicalSubsystemId, Long environmentId,
                                               Long contactUserId, String requestType, String reason,
                                               List<ResourceItemCommand> items, Long rowVersion,
                                               Long planTaskId) {
        public UpsertResourceRequestRequest {
            items = List.copyOf(items == null ? List.of() : items);
        }
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record RowVersionRequest(Long rowVersion) {
    }

    public record EnvironmentTypeResponse(String code, String name) {
    }

    public record EnvironmentResponse(long id, String code, String name, String typeCode,
                                      String typeName, RecordStatus status, String description, String remark,
                                      long rowVersion, long createdBy, long updatedBy,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record EnvironmentDetailResponse(EnvironmentResponse environment, ResourceSummaryResponse resourceSummary) {
    }

    public record ResourceSummaryResponse(long environmentId, long requestCount, long approvedRequestCount,
                                          long pendingRequestCount, BigDecimal requestedCpuCores,
                                          BigDecimal requestedMemoryGb, BigDecimal requestedStorageGb,
                                          long requestedNodeCount, BigDecimal actualCpuCores,
                                          BigDecimal actualMemoryGb, BigDecimal actualStorageGb,
                                          long actualNodeCount) {
    }

    public record DeploymentUnitOptionResponse(long id, String code, String name, String kind,
                                               long physicalSubsystemId, String relatedDeploymentUnitName,
                                               String deploymentUnitType, String description,
                                               Long defaultNetworkZoneId, String defaultNetworkZoneName) {
    }

    public record ResourceRequestSummaryResponse(long id, String requestNo, long physicalSubsystemId,
                                                 String physicalSubsystemCode, String physicalSubsystemShortName,
                                                 String physicalSubsystemName,
                                                 String physicalSubsystemBusinessGroupName,
                                                 String physicalSubsystemSystemLevelCode,
                                                 String physicalSubsystemDeploymentPlatform,
                                                 String physicalSubsystemDisasterRecoveryMode,
                                                 long environmentId, String environmentCode,
                                                 String environmentName, String environmentTypeName,
                                                 long applicantId, long contactUserId,
                                                 RequestType requestType, String reason,
                                                 RequestStatus status,
                                                 int currentBusinessRound, boolean cancellationRequested,
                                                 long rowVersion, long createdBy, long updatedBy,
                                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record ResourceRequestDetailResponse(ResourceRequestSummaryResponse request,
                                                List<ResourceRequestItemResponse> items,
                                                List<HistoryResponse> history) {
        public ResourceRequestDetailResponse {
            items = List.copyOf(items == null ? List.of() : items);
            history = List.copyOf(history == null ? List.of() : history);
        }
    }

    public record ResourceRequestItemResponse(long id, int itemSeq, long deploymentUnitId,
                                              String deploymentUnitCode, String deploymentUnitName,
                                              String deploymentUnitKind, String relatedDeploymentUnitName,
                                              String deploymentUnitDescription, String deploymentUnitType,
                                              BigDecimal databaseStorageGb, BigDecimal fileStorageGb,
                                              Long networkZoneId, String networkZoneName,
                                              String networkZone, String serverType, BigDecimal cpuCores,
                                              BigDecimal memoryGb, int appWebGroupCount,
                                              int plannedNodeCount, BigDecimal totalCpuCores,
                                              BigDecimal totalMemoryGb, BigDecimal sidecarCpuCores,
                                              BigDecimal sidecarMemoryGb, String sidecarMemoryRatio,
                                              boolean hasSidecar, String databaseName,
                                              String databaseVersion, String jdkVersion,
                                              String middleware, String operatingSystem,
                                              BigDecimal extraCbsGb, BigDecimal localDiskGb,
                                              boolean needsNft, boolean needsFserver,
                                              boolean needsJobexecutor, String remark) {
    }

    public record HistoryResponse(long id, String eventType, RequestStatus fromStatus,
                                  RequestStatus toStatus, int businessRound, String summary,
                                  String snapshotJson, String diffJson, long operatorId,
                                  LocalDateTime occurredAt) {
    }

    private EnvironmentInstanceResponse toInstance(EnvironmentInstance instance) {
        return new EnvironmentInstanceResponse(
                instance.id(), instance.instanceNo(), instance.environmentId(),
                instance.environmentCode(), instance.environmentName(), instance.environmentTypeName(),
                instance.deploymentUnitId(), instance.deploymentUnitCode(), instance.deploymentUnitName(),
                instance.deploymentUnitKind(), instance.deploymentUnitVersionId(),
                instance.deploymentUnitVersionNo(), instance.latestDeploymentUnitVersionNo(),
                instance.hasVersionDifference(),
                instance.physicalSubsystemId(), instance.physicalSubsystemCode(), instance.physicalSubsystemName(),
                instance.sourceRequestId(), instance.sourceRequestNo(), instance.sourceItemId(),
                instance.machineName(), instance.ipAddress(), instance.serverType(),
                instance.deploymentPlatform(), instance.networkZoneId(), instance.networkZoneName(),
                instance.networkZone(), instance.status(),
                instance.cpuCores(), instance.memoryGb(), instance.databaseStorageGb(),
                instance.fileStorageGb(), instance.extraCbsGb(), instance.localDiskGb(),
                instance.databaseName(), instance.databaseVersion(), instance.jdkVersion(),
                instance.middleware(), instance.operatingSystem(), instance.needsNft(),
                instance.needsFserver(), instance.needsJobexecutor(), instance.fulfillmentMode(),
                instance.differenceReason(), instance.remark(), instance.offlinedAt(),
                instance.offlinedBy(), instance.offlineReason(), instance.rowVersion(),
                instance.createdBy(), instance.updatedBy(), instance.createdAt(), instance.updatedAt()
        );
    }

    private InstanceDisasterRecoveryResponse toDr(InstanceDisasterRecovery dr) {
        return new InstanceDisasterRecoveryResponse(
                dr.id(), dr.deploymentUnitId(), dr.deploymentUnitCode(), dr.deploymentUnitName(),
                dr.primaryInstanceId(), dr.primaryMachineName(), dr.primaryIpAddress(),
                dr.primaryEnvironmentCode(), dr.primaryEnvironmentName(),
                dr.standbyInstanceId(), dr.standbyMachineName(), dr.standbyIpAddress(),
                dr.standbyEnvironmentCode(), dr.standbyEnvironmentName(),
                dr.drMode(), dr.description(), dr.createdBy(), dr.createdAt(), dr.updatedAt()
        );
    }

    public record EnvironmentInstanceResponse(
            long id, String instanceNo, long environmentId, String environmentCode,
            String environmentName, String environmentTypeName, long deploymentUnitId,
            String deploymentUnitCode, String deploymentUnitName, String deploymentUnitKind,
            Long deploymentUnitVersionId, int deploymentUnitVersionNo, int latestDeploymentUnitVersionNo,
            boolean hasVersionDifference, long physicalSubsystemId, String physicalSubsystemCode,
            String physicalSubsystemName, long sourceRequestId, String sourceRequestNo,
            Long sourceItemId, String machineName, String ipAddress, String serverType,
            String deploymentPlatform, Long networkZoneId, String networkZoneName,
            String networkZone, InstanceStatus status,
            BigDecimal cpuCores, BigDecimal memoryGb, BigDecimal databaseStorageGb,
            BigDecimal fileStorageGb, BigDecimal extraCbsGb, BigDecimal localDiskGb,
            String databaseName, String databaseVersion, String jdkVersion,
            String middleware, String operatingSystem, boolean needsNft,
            boolean needsFserver, boolean needsJobexecutor,
            com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentMode fulfillmentMode,
            String differenceReason, String remark, LocalDateTime offlinedAt,
            Long offlinedBy, String offlineReason, long rowVersion,
            long createdBy, long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
    }

    public record InstanceDisasterRecoveryResponse(
            long id, long deploymentUnitId, String deploymentUnitCode, String deploymentUnitName,
            long primaryInstanceId, String primaryMachineName, String primaryIpAddress,
            String primaryEnvironmentCode, String primaryEnvironmentName,
            long standbyInstanceId, String standbyMachineName, String standbyIpAddress,
            String standbyEnvironmentCode, String standbyEnvironmentName,
            DisasterRecoveryMode drMode, String description,
            long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
    }
}

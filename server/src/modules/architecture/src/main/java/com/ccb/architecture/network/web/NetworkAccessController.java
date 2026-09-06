package com.ccb.architecture.network.web;

import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ExemptionRuleStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ExternalNetworkAddress;
import com.ccb.architecture.network.model.NetworkAccessModels.ManagedEndpointInstance;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessExemptionRule;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessRelation;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZone;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneOption;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneSubnet;
import com.ccb.architecture.network.model.NetworkAccessModels.RecordStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import com.ccb.architecture.network.service.NetworkAccessApplicationSubmissionService;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.architecture.network.service.NetworkAccessService.AccessScope;
import com.ccb.architecture.network.service.NetworkAccessService.CloseRelationCommand;
import com.ccb.architecture.network.service.NetworkAccessService.ExemptionRuleCommand;
import com.ccb.architecture.network.service.NetworkAccessService.ExternalAddressCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkAccessCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkAccessDecisionCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkAccessDecisionResult;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkZoneCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkZoneSubnetCommand;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

/** 网络分区、外部地址、访问申请与访问关系 HTTP 边界。 */
@RestController
@RequestMapping("/api/architecture")
public class NetworkAccessController {
    private static final Logger log = LoggerFactory.getLogger(NetworkAccessController.class);
    private static final String NETWORK_ACCESS_MANAGE = "architecture:network-access:manage";
    private static final String ARCHITECTURE_MANAGE = "architecture:manage";

    private final NetworkAccessService service;
    private final NetworkAccessApplicationSubmissionService submissionService;
    private final SystemOperationAudit operationAudit;
    private final ProjectAccessService projectAccessService;

    public NetworkAccessController(NetworkAccessService service,
                                   NetworkAccessApplicationSubmissionService submissionService,
                                   SystemOperationAudit operationAudit,
                                   ProjectAccessService projectAccessService) {
        this.service = service;
        this.submissionService = submissionService;
        this.operationAudit = operationAudit;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/network-zones")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:view','architecture:network-zone:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkZone>> listZones(@RequestParam String projectRef,
                                                    @RequestParam(required = false) RecordStatus status,
                                                    @RequestParam(required = false) String keyword,
                                                    @AuthenticationPrincipal AuthUser actor) {
        return success(service.listZones(actor, project(projectRef, actor), status, keyword));
    }

    @GetMapping("/network-zones/options")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:view','architecture:network-zone:manage',"
            + "'architecture:resource-request:view','architecture:resource-request:apply',"
            + "'architecture:resource-request:manage','architecture:deployment-unit:view',"
            + "'architecture:deployment-unit:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkZoneOption>> zoneOptions(
            @RequestParam String projectRef,
            @RequestParam(defaultValue = "false") boolean leafOnly,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listZoneOptions(actor, project(projectRef, actor), leafOnly));
    }

    @PostMapping("/network-zones")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> createZone(@RequestParam String projectRef,
                                               @RequestBody NetworkZoneCommand command,
                                               @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.create", "POST", "/api/architecture/network-zones",
                () -> success(service.createZone(actor, project(projectRef, actor), command)));
    }

    @PutMapping("/network-zones/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> updateZone(@RequestParam String projectRef,
                                               @PathVariable long id,
                                               @RequestBody NetworkZoneCommand command,
                                               @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.update", "PUT", "/api/architecture/network-zones/" + id,
                () -> success(service.updateZone(actor, project(projectRef, actor), id, command)));
    }

    @PostMapping("/network-zones/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> deactivateZone(@RequestParam String projectRef,
                                                   @PathVariable long id,
                                                   @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.deactivate", "POST",
                "/api/architecture/network-zones/" + id + "/deactivate",
                () -> success(service.deactivateZone(actor, project(projectRef, actor), id)));
    }

    @PostMapping("/network-zones/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> reactivateZone(@RequestParam String projectRef,
                                                   @PathVariable long id,
                                                   @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.reactivate", "POST",
                "/api/architecture/network-zones/" + id + "/reactivate",
                () -> success(service.reactivateZone(actor, project(projectRef, actor), id)));
    }

    @GetMapping("/network-zones/{zoneId}/subnets")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:view','architecture:network-zone:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkZoneSubnet>> listSubnets(@RequestParam String projectRef,
                                                            @PathVariable long zoneId,
                                                            @RequestParam(required = false) RecordStatus status,
                                                            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listSubnets(actor, project(projectRef, actor), zoneId, status));
    }

    @PostMapping("/network-zones/{zoneId}/subnets")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> createSubnet(@RequestParam String projectRef,
                                                       @PathVariable long zoneId,
                                                       @RequestBody NetworkZoneSubnetCommand command,
                                                       @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.create", "POST",
                "/api/architecture/network-zones/" + zoneId + "/subnets",
                () -> success(service.createSubnet(actor, project(projectRef, actor), zoneId, command)));
    }

    @PutMapping("/network-zones/{zoneId}/subnets/{subnetId}")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> updateSubnet(@RequestParam String projectRef,
                                                       @PathVariable long zoneId,
                                                       @PathVariable long subnetId,
                                                       @RequestBody NetworkZoneSubnetCommand command,
                                                       @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.update", "PUT",
                "/api/architecture/network-zones/" + zoneId + "/subnets/" + subnetId,
                () -> success(service.updateSubnet(actor, project(projectRef, actor), zoneId, subnetId, command)));
    }

    @PostMapping("/network-zones/{zoneId}/subnets/{subnetId}/deactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> deactivateSubnet(@RequestParam String projectRef,
                                                           @PathVariable long zoneId,
                                                           @PathVariable long subnetId,
                                                           @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.deactivate", "POST",
                "/api/architecture/network-zones/" + zoneId + "/subnets/" + subnetId + "/deactivate",
                () -> success(service.deactivateSubnet(actor, project(projectRef, actor), zoneId, subnetId)));
    }

    @PostMapping("/network-zones/{zoneId}/subnets/{subnetId}/reactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> reactivateSubnet(@RequestParam String projectRef,
                                                           @PathVariable long zoneId,
                                                           @PathVariable long subnetId,
                                                           @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.reactivate", "POST",
                "/api/architecture/network-zones/" + zoneId + "/subnets/" + subnetId + "/reactivate",
                () -> success(service.reactivateSubnet(actor, project(projectRef, actor), zoneId, subnetId)));
    }

    @GetMapping("/external-network-addresses")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:apply',"
            + "'architecture:network-access:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<ExternalNetworkAddress>> listAddresses(
            @RequestParam String projectRef,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listAddresses(actor, project(projectRef, actor), status, keyword));
    }

    @PostMapping("/external-network-addresses")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> createAddress(@RequestParam String projectRef,
                                                             @RequestBody ExternalAddressCommand command,
                                                             @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.create", "POST",
                "/api/architecture/external-network-addresses",
                () -> success(service.createAddress(actor, project(projectRef, actor), command)));
    }

    @PutMapping("/external-network-addresses/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> updateAddress(@RequestParam String projectRef,
                                                             @PathVariable long id,
                                                             @RequestBody ExternalAddressCommand command,
                                                             @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.update", "PUT",
                "/api/architecture/external-network-addresses/" + id,
                () -> success(service.updateAddress(actor, project(projectRef, actor), id, command)));
    }

    @PostMapping("/external-network-addresses/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> deactivateAddress(@RequestParam String projectRef,
                                                                 @PathVariable long id,
                                                                 @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.deactivate", "POST",
                "/api/architecture/external-network-addresses/" + id + "/deactivate",
                () -> success(service.deactivateAddress(actor, project(projectRef, actor), id)));
    }

    @PostMapping("/external-network-addresses/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> reactivateAddress(@RequestParam String projectRef,
                                                                 @PathVariable long id,
                                                                 @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.reactivate", "POST",
                "/api/architecture/external-network-addresses/" + id + "/reactivate",
                () -> success(service.reactivateAddress(actor, project(projectRef, actor), id)));
    }

    @GetMapping("/network-access/options/instances")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:apply',"
            + "'architecture:network-access:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<ManagedEndpointInstance>> listEndpointInstances(
            @RequestParam String projectRef,
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long deploymentUnitId,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listEndpointInstances(
                actor, project(projectRef, actor), physicalSubsystemId, environmentId, deploymentUnitId));
    }

    @PostMapping("/network-access/decision")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:apply',"
            + "'architecture:network-access:manage','architecture:view','architecture:manage')")
    public ApiResponse<NetworkAccessDecisionResult> decideNetworkAccess(
            @RequestParam String projectRef,
            @RequestBody NetworkAccessDecisionCommand command,
            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-access.decision.evaluate", "POST",
                "/api/architecture/network-access/decision",
                () -> success(service.decideAccess(actor, project(projectRef, actor), command)));
    }

    @GetMapping("/network-access-exemption-rules")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkAccessExemptionRule>> listExemptionRules(
            @RequestParam String projectRef,
            @RequestParam(required = false) ExemptionRuleStatus status,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listExemptionRules(actor, project(projectRef, actor), status));
    }

    @PostMapping("/network-access-exemption-rules")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessExemptionRule> createExemptionRule(
            @RequestParam String projectRef,
            @RequestBody ExemptionRuleCommand command,
            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-access-exemption-rule.create", "POST",
                "/api/architecture/network-access-exemption-rules",
                () -> success(service.createExemptionRule(actor, project(projectRef, actor), command)));
    }

    @PutMapping("/network-access-exemption-rules/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessExemptionRule> updateExemptionRule(
            @RequestParam String projectRef,
            @PathVariable long id,
            @RequestBody ExemptionRuleCommand command,
            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-access-exemption-rule.update", "PUT",
                "/api/architecture/network-access-exemption-rules/" + id,
                () -> success(service.updateExemptionRule(actor, project(projectRef, actor), id, command)));
    }

    @PostMapping("/network-access-exemption-rules/{id}/enable")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessExemptionRule> enableExemptionRule(
            @RequestParam String projectRef,
            @PathVariable long id,
            @RequestBody RowVersionRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-exemption-rule.enable", "POST",
                "/api/architecture/network-access-exemption-rules/" + id + "/enable",
                () -> success(service.updateExemptionRuleStatus(
                        actor, project(projectRef, actor), id, rowVersion, ExemptionRuleStatus.ACTIVE)));
    }

    @PostMapping("/network-access-exemption-rules/{id}/disable")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessExemptionRule> disableExemptionRule(
            @RequestParam String projectRef,
            @PathVariable long id,
            @RequestBody RowVersionRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-exemption-rule.disable", "POST",
                "/api/architecture/network-access-exemption-rules/" + id + "/disable",
                () -> success(service.updateExemptionRuleStatus(
                        actor, project(projectRef, actor), id, rowVersion, ExemptionRuleStatus.DISABLED)));
    }

    @GetMapping("/network-access-applications")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:apply',"
            + "'architecture:network-access:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkAccessApplication>> listApplications(
            @RequestParam String projectRef,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor,
            Authentication authentication) {
        return success(service.listApplications(
                actor, project(projectRef, actor), accessScope(authentication), status, limit, offset));
    }

    @PostMapping("/network-access-applications")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:apply','architecture:network-access:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> createApplication(@RequestParam String projectRef,
                                                                   @RequestBody NetworkAccessCommand command,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-access-application.create", "POST",
                "/api/architecture/network-access-applications",
                () -> success(service.createApplication(actor, project(projectRef, actor), command)));
    }

    @PostMapping("/network-access-applications/{id}/submit")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:apply','architecture:network-access:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> submitApplication(@RequestParam String projectRef,
                                                                   @PathVariable long id,
                                                                   @RequestBody RowVersionRequest request,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.submit", "POST",
                "/api/architecture/network-access-applications/" + id + "/submit",
                () -> success(submissionService.submit(actor, project(projectRef, actor), id, rowVersion)));
    }

    @PostMapping("/network-access-applications/{id}/approve")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> approveApplication(@RequestParam String projectRef,
                                                                    @PathVariable long id,
                                                                    @RequestBody RowVersionRequest request,
                                                                    @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.approve", "POST",
                "/api/architecture/network-access-applications/" + id + "/approve",
                () -> success(service.approveApplication(actor, project(projectRef, actor), id, rowVersion)));
    }

    @PostMapping("/network-access-applications/{id}/reject")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> rejectApplication(@RequestParam String projectRef,
                                                                   @PathVariable long id,
                                                                   @RequestBody RowVersionRequest request,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.reject", "POST",
                "/api/architecture/network-access-applications/" + id + "/reject",
                () -> success(service.rejectApplication(actor, project(projectRef, actor), id, rowVersion)));
    }

    @PostMapping("/network-access-applications/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:apply','architecture:network-access:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> cancelApplication(@RequestParam String projectRef,
                                                                   @PathVariable long id,
                                                                   @RequestBody RowVersionRequest request,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.cancel", "POST",
                "/api/architecture/network-access-applications/" + id + "/cancel",
                () -> success(submissionService.cancel(actor, project(projectRef, actor), id, rowVersion)));
    }

    @GetMapping("/network-access-relations")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkAccessRelation>> listRelations(
            @RequestParam String projectRef,
            @RequestParam(required = false) RelationStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listRelations(actor, project(projectRef, actor), status, limit, offset));
    }

    @PostMapping("/network-access-relations/{id}/close")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessRelation> closeRelation(@RequestParam String projectRef,
                                                            @PathVariable long id,
                                                            @RequestBody CloseRelationCommand command,
                                                            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-access-relation.close", "POST",
                "/api/architecture/network-access-relations/" + id + "/close",
                () -> success(service.closeRelation(actor, project(projectRef, actor), id, command)));
    }

    private <T> T audited(AuthUser actor, String operationCode, String method, String path,
                          Supplier<T> action) {
        try {
            T result = action.get();
            recordAudit(actor, operationCode, method, path, null, TraceId.getOrCreate());
            return result;
        } catch (BusinessException failure) {
            recordAudit(actor, operationCode, method, path, failure.getMessage(), TraceId.getOrCreate());
            throw failure;
        } catch (RuntimeException failure) {
            recordAudit(actor, operationCode, method, path, "网络访问操作失败", TraceId.getOrCreate());
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
            log.warn("网络访问审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    private AccessScope accessScope(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(authority ->
                NETWORK_ACCESS_MANAGE.equals(authority.getAuthority())
                        || ARCHITECTURE_MANAGE.equals(authority.getAuthority()))) {
            return AccessScope.MANAGE;
        }
        return AccessScope.OWN;
    }

    private ProjectAccess project(String projectRef, AuthUser actor) {
        return projectAccessService.requireAccessible(projectRef, actor);
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

    public record RowVersionRequest(Long rowVersion) {
    }
}

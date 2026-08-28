package com.ccb.architecture.network.web;

import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ExternalNetworkAddress;
import com.ccb.architecture.network.model.NetworkAccessModels.ManagedEndpointInstance;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessRelation;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZone;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneOption;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneSubnet;
import com.ccb.architecture.network.model.NetworkAccessModels.RecordStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.architecture.network.service.NetworkAccessService.AccessScope;
import com.ccb.architecture.network.service.NetworkAccessService.CloseRelationCommand;
import com.ccb.architecture.network.service.NetworkAccessService.ExternalAddressCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkAccessCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkZoneCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkZoneSubnetCommand;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
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
    private final SystemOperationAudit operationAudit;

    public NetworkAccessController(NetworkAccessService service, SystemOperationAudit operationAudit) {
        this.service = service;
        this.operationAudit = operationAudit;
    }

    @GetMapping("/network-zones")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:view','architecture:network-zone:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkZone>> listZones(@RequestParam(required = false) RecordStatus status,
                                                    @RequestParam(required = false) String keyword,
                                                    @AuthenticationPrincipal AuthUser actor) {
        return success(service.listZones(actor, status, keyword));
    }

    @GetMapping("/network-zones/options")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:view','architecture:network-zone:manage',"
            + "'architecture:resource-request:view','architecture:resource-request:apply',"
            + "'architecture:resource-request:manage','architecture:deployment-unit:view',"
            + "'architecture:deployment-unit:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkZoneOption>> zoneOptions(
            @RequestParam(defaultValue = "false") boolean leafOnly,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listZoneOptions(actor, leafOnly));
    }

    @PostMapping("/network-zones")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> createZone(@RequestBody NetworkZoneCommand command,
                                               @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.create", "POST", "/api/architecture/network-zones",
                () -> success(service.createZone(actor, command)));
    }

    @PutMapping("/network-zones/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> updateZone(@PathVariable long id,
                                               @RequestBody NetworkZoneCommand command,
                                               @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.update", "PUT", "/api/architecture/network-zones/" + id,
                () -> success(service.updateZone(actor, id, command)));
    }

    @PostMapping("/network-zones/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> deactivateZone(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.deactivate", "POST",
                "/api/architecture/network-zones/" + id + "/deactivate",
                () -> success(service.deactivateZone(actor, id)));
    }

    @PostMapping("/network-zones/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZone> reactivateZone(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone.reactivate", "POST",
                "/api/architecture/network-zones/" + id + "/reactivate",
                () -> success(service.reactivateZone(actor, id)));
    }

    @GetMapping("/network-zones/{zoneId}/subnets")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:view','architecture:network-zone:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkZoneSubnet>> listSubnets(@PathVariable long zoneId,
                                                            @RequestParam(required = false) RecordStatus status,
                                                            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listSubnets(actor, zoneId, status));
    }

    @PostMapping("/network-zones/{zoneId}/subnets")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> createSubnet(@PathVariable long zoneId,
                                                       @RequestBody NetworkZoneSubnetCommand command,
                                                       @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.create", "POST",
                "/api/architecture/network-zones/" + zoneId + "/subnets",
                () -> success(service.createSubnet(actor, zoneId, command)));
    }

    @PutMapping("/network-zones/{zoneId}/subnets/{subnetId}")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> updateSubnet(@PathVariable long zoneId,
                                                       @PathVariable long subnetId,
                                                       @RequestBody NetworkZoneSubnetCommand command,
                                                       @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.update", "PUT",
                "/api/architecture/network-zones/" + zoneId + "/subnets/" + subnetId,
                () -> success(service.updateSubnet(actor, zoneId, subnetId, command)));
    }

    @PostMapping("/network-zones/{zoneId}/subnets/{subnetId}/deactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> deactivateSubnet(@PathVariable long zoneId,
                                                           @PathVariable long subnetId,
                                                           @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.deactivate", "POST",
                "/api/architecture/network-zones/" + zoneId + "/subnets/" + subnetId + "/deactivate",
                () -> success(service.deactivateSubnet(actor, zoneId, subnetId)));
    }

    @PostMapping("/network-zones/{zoneId}/subnets/{subnetId}/reactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-zone:manage','architecture:manage')")
    public ApiResponse<NetworkZoneSubnet> reactivateSubnet(@PathVariable long zoneId,
                                                           @PathVariable long subnetId,
                                                           @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-zone-subnet.reactivate", "POST",
                "/api/architecture/network-zones/" + zoneId + "/subnets/" + subnetId + "/reactivate",
                () -> success(service.reactivateSubnet(actor, zoneId, subnetId)));
    }

    @GetMapping("/external-network-addresses")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:apply',"
            + "'architecture:network-access:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<ExternalNetworkAddress>> listAddresses(
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listAddresses(actor, status, keyword));
    }

    @PostMapping("/external-network-addresses")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> createAddress(@RequestBody ExternalAddressCommand command,
                                                             @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.create", "POST",
                "/api/architecture/external-network-addresses",
                () -> success(service.createAddress(actor, command)));
    }

    @PutMapping("/external-network-addresses/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> updateAddress(@PathVariable long id,
                                                             @RequestBody ExternalAddressCommand command,
                                                             @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.update", "PUT",
                "/api/architecture/external-network-addresses/" + id,
                () -> success(service.updateAddress(actor, id, command)));
    }

    @PostMapping("/external-network-addresses/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> deactivateAddress(@PathVariable long id,
                                                                 @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.deactivate", "POST",
                "/api/architecture/external-network-addresses/" + id + "/deactivate",
                () -> success(service.deactivateAddress(actor, id)));
    }

    @PostMapping("/external-network-addresses/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<ExternalNetworkAddress> reactivateAddress(@PathVariable long id,
                                                                 @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.external-network-address.reactivate", "POST",
                "/api/architecture/external-network-addresses/" + id + "/reactivate",
                () -> success(service.reactivateAddress(actor, id)));
    }

    @GetMapping("/network-access/options/instances")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:apply',"
            + "'architecture:network-access:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<ManagedEndpointInstance>> listEndpointInstances(
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long deploymentUnitId,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listEndpointInstances(actor, physicalSubsystemId, environmentId, deploymentUnitId));
    }

    @GetMapping("/network-access-applications")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:apply',"
            + "'architecture:network-access:manage','architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkAccessApplication>> listApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor,
            Authentication authentication) {
        return success(service.listApplications(actor, accessScope(authentication), status, limit, offset));
    }

    @PostMapping("/network-access-applications")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:apply','architecture:network-access:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> createApplication(@RequestBody NetworkAccessCommand command,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-access-application.create", "POST",
                "/api/architecture/network-access-applications",
                () -> success(service.createApplication(actor, command)));
    }

    @PostMapping("/network-access-applications/{id}/submit")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:apply','architecture:network-access:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> submitApplication(@PathVariable long id,
                                                                   @RequestBody RowVersionRequest request,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.submit", "POST",
                "/api/architecture/network-access-applications/" + id + "/submit",
                () -> success(service.submitApplication(actor, id, rowVersion)));
    }

    @PostMapping("/network-access-applications/{id}/approve")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> approveApplication(@PathVariable long id,
                                                                    @RequestBody RowVersionRequest request,
                                                                    @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.approve", "POST",
                "/api/architecture/network-access-applications/" + id + "/approve",
                () -> success(service.approveApplication(actor, id, rowVersion)));
    }

    @PostMapping("/network-access-applications/{id}/reject")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> rejectApplication(@PathVariable long id,
                                                                   @RequestBody RowVersionRequest request,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.reject", "POST",
                "/api/architecture/network-access-applications/" + id + "/reject",
                () -> success(service.rejectApplication(actor, id, rowVersion)));
    }

    @PostMapping("/network-access-applications/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:apply','architecture:network-access:manage',"
            + "'architecture:apply','architecture:manage')")
    public ApiResponse<NetworkAccessApplication> cancelApplication(@PathVariable long id,
                                                                   @RequestBody RowVersionRequest request,
                                                                   @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = request == null || request.rowVersion() == null ? -1 : request.rowVersion();
        return audited(actor, "architecture.network-access-application.cancel", "POST",
                "/api/architecture/network-access-applications/" + id + "/cancel",
                () -> success(service.cancelApplication(actor, id, rowVersion)));
    }

    @GetMapping("/network-access-relations")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:view','architecture:network-access:manage',"
            + "'architecture:view','architecture:manage')")
    public ApiResponse<List<NetworkAccessRelation>> listRelations(
            @RequestParam(required = false) RelationStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.listRelations(actor, status, limit, offset));
    }

    @PostMapping("/network-access-relations/{id}/close")
    @PreAuthorize("hasAnyAuthority('architecture:network-access:manage','architecture:manage')")
    public ApiResponse<NetworkAccessRelation> closeRelation(@PathVariable long id,
                                                            @RequestBody CloseRelationCommand command,
                                                            @AuthenticationPrincipal AuthUser actor) {
        return audited(actor, "architecture.network-access-relation.close", "POST",
                "/api/architecture/network-access-relations/" + id + "/close",
                () -> success(service.closeRelation(actor, id, command)));
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

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

    public record RowVersionRequest(Long rowVersion) {
    }
}

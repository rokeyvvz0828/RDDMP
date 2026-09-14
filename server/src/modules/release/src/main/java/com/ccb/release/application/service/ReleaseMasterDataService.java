package com.ccb.release.application.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryInput;
import com.ccb.release.integration.ReleaseArchitectureDirectory;
import com.ccb.release.integration.ReleaseRequirementDirectory;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReleaseMasterDataService {
    private final ProjectAccessService projectAccessService;
    private final ReleaseArchitectureDirectory directory;
    private final ReleaseRequirementDirectory requirementDirectory;

    public ReleaseMasterDataService(ProjectAccessService projectAccessService,
                                    ReleaseArchitectureDirectory directory,
                                    ReleaseRequirementDirectory requirementDirectory) {
        this.projectAccessService = projectAccessService;
        this.directory = directory;
        this.requirementDirectory = requirementDirectory;
    }

    public PageResult<PhysicalSubsystemOption> physicalSubsystems(String projectRef, long page, long size,
                                                                   String keyword, AuthUser actor) {
        ProjectAccess project = projectAccessService.requireAccessible(projectRef, actor);
        var result = directory.searchPhysicalSubsystems(actor, project.id(), new PageQuery(page, size), keyword);
        return new PageResult<>(result.records().stream()
                .map(item -> new PhysicalSubsystemOption(String.valueOf(item.id()), item.code(), item.name()))
                .toList(), result.total(), result.page(), result.size());
    }

    public PageResult<DeliveryUnitOption> deliveryUnits(String projectRef, String physicalSubsystemId,
                                                         long page, long size, String keyword, AuthUser actor) {
        ProjectAccess project = projectAccessService.requireAccessible(projectRef, actor);
        long physicalId = positiveId(physicalSubsystemId, "物理子系统标识");
        var result = directory.searchDeliveryUnits(actor, project.id(), physicalId, new PageQuery(page, size), keyword);
        return new PageResult<>(result.records().stream().map(ReleaseMasterDataService::option).toList(),
                result.total(), result.page(), result.size());
    }

    public PageResult<RequirementOption> requirements(String projectRef, long page, long size,
                                                       String keyword, AuthUser actor) {
        ProjectAccess project = projectAccessService.requireAccessible(projectRef, actor);
        var result = requirementDirectory.searchActive(actor, project.projectRef(), new PageQuery(page, size), keyword);
        return new PageResult<>(result.records().stream()
                .map(item -> new RequirementOption(item.id(), item.number(), item.name(), item.status()))
                .toList(), result.total(), result.page(), result.size());
    }

    public List<String> requireActiveRequirements(ProjectAccess project, List<String> values, AuthUser actor) {
        List<String> requested = normalizeRequirementCodes(values);
        if (requested.isEmpty()) {
            return List.of();
        }
        var resolved = requirementDirectory.resolveActive(actor, project.projectRef(), new LinkedHashSet<>(requested))
                .orElseThrow(() -> conflict("关联需求已失效、已终止或不属于当前项目，请重新选择"));
        Map<String, ReleaseRequirementDirectory.Requirement> byNumber = new HashMap<>();
        resolved.forEach(item -> byNumber.put(item.number(), item));
        List<String> ordered = new ArrayList<>();
        for (String number : requested) {
            if (!byNumber.containsKey(number)) {
                throw conflict("关联需求已失效、已终止或不属于当前项目，请重新选择");
            }
            ordered.add(number);
        }
        return List.copyOf(ordered);
    }

    public TrustedSelection requireActiveSelection(ProjectAccess project, String physicalSubsystemId,
                                                    List<DeliveryInput> inputs, AuthUser actor) {
        long physicalId = positiveId(physicalSubsystemId, "物理子系统标识");
        List<Long> requestedIds = new ArrayList<>();
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (DeliveryInput input : inputs == null ? List.<DeliveryInput>of() : inputs) {
            if (input == null) {
                throw badRequest("交付单元信息不能为空");
            }
            long id = positiveId(input.deliveryUnitId(), "交付单元标识");
            if (!uniqueIds.add(id)) {
                throw badRequest("同一申请中交付单元不能重复");
            }
            requestedIds.add(id);
        }
        var resolved = directory.resolveActiveSelection(actor, project.id(), physicalId, uniqueIds)
                .orElseThrow(() -> conflict("物理子系统或交付单元已失效、归属已变化，请重新选择"));
        Map<Long, ReleaseArchitectureDirectory.DeliveryUnit> byId = new HashMap<>();
        resolved.deliveryUnits().forEach(item -> byId.put(item.id(), item));
        List<TrustedDeliveryUnit> ordered = new ArrayList<>();
        for (Long id : requestedIds) {
            ReleaseArchitectureDirectory.DeliveryUnit item = byId.get(id);
            if (item == null) {
                throw conflict("交付单元已失效或归属已变化，请重新选择");
            }
            ArtifactType artifactType = artifactType(item);
            ordered.add(new TrustedDeliveryUnit(String.valueOf(item.id()), item.code(), item.name(), artifactType));
        }
        var physical = resolved.physicalSubsystem();
        return new TrustedSelection(String.valueOf(physical.id()), physical.code(), physical.name(), List.copyOf(ordered));
    }

    private static DeliveryUnitOption option(ReleaseArchitectureDirectory.DeliveryUnit item) {
        String artifactType = item.artifactTypeCode() == null ? null : item.artifactTypeCode().trim().toUpperCase();
        boolean selectable = "IMAGE".equals(artifactType) || "BINARY".equals(artifactType);
        return new DeliveryUnitOption(String.valueOf(item.id()), item.code(), item.name(),
                selectable ? artifactType : null, selectable, selectable ? null : "请先在架构管理中配置制品类型");
    }

    private static ArtifactType artifactType(ReleaseArchitectureDirectory.DeliveryUnit item) {
        try {
            ArtifactType value = ArtifactType.valueOf(item.artifactTypeCode() == null
                    ? "" : item.artifactTypeCode().trim().toUpperCase());
            if (value == ArtifactType.FILE) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw conflict("交付单元“" + item.name() + "”未配置有效制品类型，请先在架构管理中维护");
        }
    }

    private static long positiveId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw badRequest(label + "不能为空，请重新选择");
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw badRequest(label + "无效，请重新选择");
        }
    }

    private static List<String> normalizeRequirementCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > 100) {
            throw badRequest("单张申请最多关联 100 个需求");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty() || normalized.length() > 128) {
                throw badRequest("需求编号无效");
            }
            unique.add(normalized);
        }
        return List.copyOf(unique);
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    public record PhysicalSubsystemOption(String id, String code, String name) {}

    public record DeliveryUnitOption(String id, String code, String name, String artifactType,
                                     boolean selectable, String unavailableReason) {}

    public record RequirementOption(long id, String number, String name, String status) {}

    public record TrustedSelection(String subsystemId, String subsystemCode, String subsystemName,
                                   List<TrustedDeliveryUnit> deliveryUnits) {}

    public record TrustedDeliveryUnit(String id, String code, String name, ArtifactType artifactType) {}
}

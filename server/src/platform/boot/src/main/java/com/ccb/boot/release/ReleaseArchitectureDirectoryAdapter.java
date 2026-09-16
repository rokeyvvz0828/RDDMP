package com.ccb.boot.release;

import com.ccb.architecture.integration.ReleaseMasterDataQuery;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.integration.ReleaseArchitectureDirectory;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

@Component
public class ReleaseArchitectureDirectoryAdapter implements ReleaseArchitectureDirectory {
    private final ReleaseMasterDataQuery query;

    public ReleaseArchitectureDirectoryAdapter(ReleaseMasterDataQuery query) {
        this.query = query;
    }

    @Override
    public PageResult<PhysicalSubsystem> searchPhysicalSubsystems(AuthUser actor, long projectId, PageQuery page,
                                                                   String keyword) {
        var result = query.searchPhysicalSubsystems(actor, projectId, page, keyword);
        return new PageResult<>(result.records().stream().map(ReleaseArchitectureDirectoryAdapter::physical).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public PageResult<DeliveryUnit> searchDeliveryUnits(AuthUser actor, long projectId, long physicalSubsystemId,
                                                         PageQuery page, String keyword) {
        var result = query.searchDeliveryUnits(actor, projectId, physicalSubsystemId, page, keyword);
        return new PageResult<>(result.records().stream().map(ReleaseArchitectureDirectoryAdapter::delivery).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public Optional<Selection> resolveActiveSelection(AuthUser actor, long projectId, long physicalSubsystemId,
                                                       Collection<Long> deliveryUnitIds) {
        return query.resolveActiveSelection(actor, projectId, physicalSubsystemId, deliveryUnitIds)
                .map(value -> new Selection(physical(value.physicalSubsystem()),
                        value.deliveryUnits().stream().map(ReleaseArchitectureDirectoryAdapter::delivery).toList()));
    }

    private static PhysicalSubsystem physical(ReleaseMasterDataQuery.PhysicalSubsystemRef value) {
        return new PhysicalSubsystem(value.id(), value.code(), value.name());
    }

    private static DeliveryUnit delivery(ReleaseMasterDataQuery.DeliveryUnitRef value) {
        return new DeliveryUnit(value.id(), value.physicalSubsystemId(), value.code(), value.name(),
                value.artifactTypeCode());
    }
}

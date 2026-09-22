package com.ccb.release.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 投产管理消费架构主数据的只读端口。 */
public interface ReleaseArchitectureDirectory {
    PageResult<PhysicalSubsystem> searchPhysicalSubsystems(AuthUser actor, long projectId, PageQuery page,
                                                            String keyword);

    PageResult<DeliveryUnit> searchDeliveryUnits(AuthUser actor, long projectId, long physicalSubsystemId,
                                                  PageQuery page, String keyword);

    Optional<Selection> resolveActiveSelection(AuthUser actor, long projectId, long physicalSubsystemId,
                                                Collection<Long> deliveryUnitIds);

    List<Environment> listActiveEnvironments(AuthUser actor, long projectId);

    Optional<Environment> resolveActiveEnvironment(AuthUser actor, long projectId, long environmentId);

    record PhysicalSubsystem(long id, String code, String name) {}

    record DeliveryUnit(long id, long physicalSubsystemId, String code, String name, String artifactTypeCode) {}

    record Selection(PhysicalSubsystem physicalSubsystem, List<DeliveryUnit> deliveryUnits) {}

    record Environment(long id, String code, String name, String typeName) {}
}

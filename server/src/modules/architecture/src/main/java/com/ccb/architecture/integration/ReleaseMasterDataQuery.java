package com.ccb.architecture.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 供投产管理消费的架构主数据最小只读投影；消费者仍需执行自己的业务授权。 */
public interface ReleaseMasterDataQuery {
    PageResult<PhysicalSubsystemRef> searchPhysicalSubsystems(AuthUser actor, long projectId, PageQuery page,
                                                               String keyword);

    PageResult<DeliveryUnitRef> searchDeliveryUnits(AuthUser actor, long projectId, long physicalSubsystemId,
                                                     PageQuery page, String keyword);

    Optional<Selection> resolveActiveSelection(AuthUser actor, long projectId, long physicalSubsystemId,
                                                Collection<Long> deliveryUnitIds);

    List<EnvironmentRef> listActiveEnvironments(AuthUser actor, long projectId);

    Optional<EnvironmentRef> resolveActiveEnvironment(AuthUser actor, long projectId, long environmentId);

    record PhysicalSubsystemRef(long id, String code, String name) {}

    record DeliveryUnitRef(long id, long physicalSubsystemId, String code, String name, String artifactTypeCode) {}

    record Selection(PhysicalSubsystemRef physicalSubsystem, List<DeliveryUnitRef> deliveryUnits) {}

    record EnvironmentRef(long id, String code, String name, String typeName) {}
}

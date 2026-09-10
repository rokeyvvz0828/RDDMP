package com.ccb.architecture.service;

import com.ccb.architecture.integration.DeploymentUnitReferenceCheckRequest;
import com.ccb.architecture.integration.DeploymentUnitReferenceChecker;
import com.ccb.architecture.integration.ReferenceCheckResult;
import com.ccb.architecture.persistence.DeliveryUnitStore;
import org.springframework.stereotype.Component;

/**
 * 把“部署单元被交付单元关联”纳入部署单元作废引用守卫。
 *
 * <p>关联双方同属本模块，但仍通过公开 SPI 注册，使守卫无需感知交付单元聚合；
 * 部署单元 ID 在租户内唯一，因此检查按租户维度执行。查询异常时不抛出，
 * 由守卫按不可判定失败关闭。</p>
 */
@Component
public class DeliveryUnitDeploymentUnitReferenceChecker implements DeploymentUnitReferenceChecker {
    private static final String CHECKER_KEY = "architecture-delivery-unit-relation";
    private static final String REFERENCED_SUMMARY = "部署单元仍被交付单元关联，请先解除关联后再作废";
    private static final String CLEAR_SUMMARY = "未发现交付单元关联";
    private static final String UNAVAILABLE_SUMMARY = "交付单元关联检查暂不可用";

    private final DeliveryUnitStore store;

    public DeliveryUnitDeploymentUnitReferenceChecker(DeliveryUnitStore store) {
        this.store = store;
    }

    @Override
    public String checkerKey() {
        return CHECKER_KEY;
    }

    @Override
    public ReferenceCheckResult check(DeploymentUnitReferenceCheckRequest request) {
        try {
            boolean referenced = store.hasDeliveryUnitRelationInTenant(request.tenantId(), request.unitId());
            return referenced ? ReferenceCheckResult.referenced(REFERENCED_SUMMARY)
                    : ReferenceCheckResult.clear(CLEAR_SUMMARY);
        } catch (RuntimeException exception) {
            return ReferenceCheckResult.indeterminate(UNAVAILABLE_SUMMARY);
        }
    }
}

package com.ccb.architecture.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交付单元领域模型：主记录、命令、查询与部署单元投影。
 *
 * <p>交付单元归属物理子系统在创建后不可变更；与部署单元的关联无方向，双方必须属于同一物理子系统。</p>
 */
public final class DeliveryUnitModels {

    private DeliveryUnitModels() {
    }

    /** 交付单元状态（受控值）。 */
    public enum DeliveryUnitStatus {
        ACTIVE,
        INACTIVE
    }

    /** 交付单元主记录。 */
    public record DeliveryUnit(
            long id,
            String code,
            long physicalSubsystemId,
            String name,
            String status,
            String description,
            String remark,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion) {
    }

    /**
     * 创建/更新交付单元命令；归属物理子系统只在创建时提供，更新时携带不同值会被拒绝。
     */
    public record DeliveryUnitCommand(
            Long physicalSubsystemId,
            String name,
            String description,
            String remark,
            List<Long> relatedDeploymentUnitIds,
            Long rowVersion) {
    }

    /** 交付单元分页查询条件。 */
    public record DeliveryUnitQuery(
            String name,
            Long physicalSubsystemId,
            String status) {

        public static DeliveryUnitQuery empty() {
            return new DeliveryUnitQuery(null, null, null);
        }
    }

    /** 部署单元投影，用于关联候选、关联校验与反向查询。 */
    public record DeploymentUnitRef(
            long id,
            String code,
            String name,
            String status,
            boolean deleted,
            long physicalSubsystemId) {
    }
}

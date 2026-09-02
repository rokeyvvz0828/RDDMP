package com.ccb.architecture.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部署单元领域模型：主记录、版本快照、命令、查询与导入批次/明细。
 */
public final class DeploymentUnitModels {

    private DeploymentUnitModels() {
    }

    /** 部署单元类型：应用、数据库、Web（受控值）。 */
    public enum DeploymentUnitKind {
        APPLICATION,
        DATABASE,
        WEB
    }

    /** 部署单元发布状态。 */
    public enum DeploymentUnitStatus {
        ACTIVE,
        INACTIVE,
        VOIDED
    }

    /** 导入批次状态。 */
    public enum ImportBatchStatus {
        PREVIEW,
        SUCCESS,
        PARTIAL,
        FAILED
    }

    /** 导入行状态。 */
    public enum ImportItemStatus {
        VALID,
        INVALID,
        SUCCESS,
        FAILED,
        SKIPPED
    }

    /** 部署单元主记录。 */
    public record DeploymentUnit(
            long id,
            String code,
            long physicalSubsystemId,
            String name,
            String kind,
            Long defaultNetworkZoneId,
            String defaultNetworkZoneName,
            String status,
            int currentVersion,
            String description,
            String remark,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion) {

    }

    /** 部署单元发布版本快照（不可改写）。 */
    public record DeploymentUnitVersion(
            long id,
            long unitId,
            int versionNo,
            String name,
            String kind,
            Long defaultNetworkZoneId,
            String defaultNetworkZoneName,
            String description,
            String remark,
            long publishedBy,
            LocalDateTime publishedAt) {

    }

    /** 创建/更新部署单元命令；归属物理子系统只在创建时提供。 */
    public record DeploymentUnitCommand(
            Long physicalSubsystemId,
            String name,
            String kind,
            List<Long> relatedDeploymentUnitIds,
            Long defaultNetworkZoneId,
            String description,
            String remark,
            Long rowVersion) {
    }

    /** 部署单元分页查询条件。 */
    public record DeploymentUnitQuery(
            String code,
            String name,
            Long physicalSubsystemId,
            String kind,
            String status) {

        public static DeploymentUnitQuery empty() {
            return new DeploymentUnitQuery(null, null, null, null, null);
        }
    }

    /** 导入批次记录。 */
    public record DeploymentUnitImportBatch(
            long id,
            String fileName,
            long fileSize,
            int totalRows,
            int validRows,
            int successRows,
            int failedRows,
            int skippedRows,
            String status,
            String errorMessage,
            long createdBy,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {
    }

    /** 导入行明细。 */
    public record DeploymentUnitImportItem(
            long id,
            long batchId,
            int lineNo,
            String rawJson,
            String rowStatus,
            String errorMessage,
            String note,
            Long unitId,
            LocalDateTime createdAt) {
    }
}

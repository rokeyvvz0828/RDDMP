package com.ccb.release.production.model;

import java.time.LocalDateTime;
import java.util.List;

public final class ProductionModels {
    private ProductionModels() {}

    public enum Result { RELEASED, SUCCEEDED, FAILED, NOT_DEPLOYED }

    public record Entry(long id, long tenantId, long windowId, long applicationId, String applicationCode,
                        LocalDateTime approvedAt, String subsystemId, String subsystemCode, String subsystemName,
                        String deliveryUnitId, String deliveryUnitCode, String deliveryUnitName,
                        String artifactType, String artifactVersion, String itemType, String filePath, String itemKey,
                        String versionType, String characteristic,
                        Result productionResult, LocalDateTime productionAt, String resultReason,
                        boolean activeCandidate, long rowVersion, LocalDateTime createdAt, LocalDateTime updatedAt,
                        String windowName) {
        public Entry(long id, long tenantId, long windowId, long applicationId, String applicationCode,
                     LocalDateTime approvedAt, String subsystemId, String subsystemCode, String subsystemName,
                     String deliveryUnitId, String deliveryUnitCode, String deliveryUnitName,
                     String artifactType, String artifactVersion, String itemType, String filePath, String itemKey,
                     String versionType, String characteristic,
                     Result productionResult, LocalDateTime productionAt, String resultReason,
                     boolean activeCandidate, long rowVersion, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, tenantId, windowId, applicationId, applicationCode, approvedAt, subsystemId, subsystemCode,
                    subsystemName, deliveryUnitId, deliveryUnitCode, deliveryUnitName, artifactType, artifactVersion,
                    itemType, filePath, itemKey, versionType, characteristic, productionResult, productionAt,
                    resultReason, activeCandidate, rowVersion, createdAt, updatedAt, null);
        }

        public Entry(long id, long tenantId, long windowId, long applicationId, String applicationCode,
                     LocalDateTime approvedAt, String subsystemId, String subsystemCode, String subsystemName,
                     String deliveryUnitId, String deliveryUnitCode, String deliveryUnitName,
                     String artifactType, String artifactVersion, String versionType, String characteristic,
                     Result productionResult, LocalDateTime productionAt, String resultReason,
                     boolean activeCandidate, long rowVersion, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, tenantId, windowId, applicationId, applicationCode, approvedAt, subsystemId, subsystemCode,
                    subsystemName, deliveryUnitId, deliveryUnitCode, deliveryUnitName, artifactType, artifactVersion,
                    "DELIVERY_UNIT", null, "UNIT:" + deliveryUnitCode, versionType, characteristic, productionResult,
                    productionAt, resultReason, activeCandidate, rowVersion, createdAt, updatedAt);
        }

        public Entry withWindowName(String value) {
            return new Entry(id, tenantId, windowId, applicationId, applicationCode, approvedAt, subsystemId,
                    subsystemCode, subsystemName, deliveryUnitId, deliveryUnitCode, deliveryUnitName, artifactType,
                    artifactVersion, itemType, filePath, itemKey, versionType, characteristic, productionResult,
                    productionAt, resultReason, activeCandidate, rowVersion, createdAt, updatedAt, value);
        }
    }

    public record UpdateResultRequest(String productionResult, LocalDateTime productionAt, String resultReason,
                                      String changeReason, long rowVersion) {
    }

    public record BatchEntryRequest(long id, long rowVersion) {
    }

    public record BatchUpdateResultRequest(List<BatchEntryRequest> entries, String productionResult,
                                           LocalDateTime productionAt, String resultReason, String changeReason) {
    }
}

package com.ccb.release.application.model;

import java.time.LocalDateTime;
import java.util.List;

public final class ReleaseApplicationModels {
    private ReleaseApplicationModels() {
    }

    public enum ArtifactType { IMAGE, BINARY, FILE }
    public enum DeliveryItemType { DELIVERY_UNIT, FILE_MEDIA }
    public enum VersionType { REGULAR, URGENT, EMERGENCY }
    public enum Characteristic { STANDARD, ADDITIONAL }
    public enum Status { DRAFT, IN_REVIEW, RETURNED, WITHDRAWN, CANCELLED, RELEASED }
    public enum ConflictAction { CANCEL_OLD, EDIT_OLD, CREATE_NEW }

    public record DeliveryInput(String deliveryUnitId, String deliveryUnitCode, String deliveryUnitName,
                                String artifactType, String artifactVersion) {
    }

    public record FileMediaInput(String filePath) {
    }

    public record DeliverySnapshot(long id, String deliveryUnitId, String deliveryUnitCode, String deliveryUnitName,
                                   ArtifactType artifactType, String artifactVersion,
                                   DeliveryItemType itemType, String filePath, String itemKey) {
        public DeliverySnapshot(long id, String deliveryUnitId, String deliveryUnitCode, String deliveryUnitName,
                                ArtifactType artifactType, String artifactVersion) {
            this(id, deliveryUnitId, deliveryUnitCode, deliveryUnitName, artifactType, artifactVersion,
                    DeliveryItemType.DELIVERY_UNIT, null, "UNIT:" + deliveryUnitCode);
        }
    }

    public record FileMediaSnapshot(long id, String filePath) {
    }

    public record CreateRequest(
            boolean emergency, Long windowId,
            String projectId, String projectCode, String projectName,
            String subsystemId, String subsystemCode, String subsystemName,
            List<DeliveryInput> deliveries, List<FileMediaInput> fileMedia, List<String> requirementCodes,
            String emergencyDescription, String urgentReason, String description
    ) {
        public CreateRequest(boolean emergency, Long windowId,
                             String projectId, String projectCode, String projectName,
                             String subsystemId, String subsystemCode, String subsystemName,
                             List<DeliveryInput> deliveries, List<String> requirementCodes,
                             String emergencyDescription, String urgentReason, String description) {
            this(emergency, windowId, projectId, projectCode, projectName, subsystemId, subsystemCode, subsystemName,
                    deliveries, List.of(), requirementCodes, emergencyDescription, urgentReason, description);
        }
    }

    public record UpdateRequest(
            long rowVersion, boolean emergency, Long windowId,
            String projectId, String projectCode, String projectName,
            String subsystemId, String subsystemCode, String subsystemName,
            List<DeliveryInput> deliveries, List<FileMediaInput> fileMedia, List<String> requirementCodes,
            String emergencyDescription, String urgentReason, String description
    ) {
        public UpdateRequest(long rowVersion, boolean emergency, Long windowId,
                             String projectId, String projectCode, String projectName,
                             String subsystemId, String subsystemCode, String subsystemName,
                             List<DeliveryInput> deliveries, List<String> requirementCodes,
                             String emergencyDescription, String urgentReason, String description) {
            this(rowVersion, emergency, windowId, projectId, projectCode, projectName, subsystemId, subsystemCode,
                    subsystemName, deliveries, List.of(), requirementCodes, emergencyDescription, urgentReason, description);
        }
    }

    public record StateActionRequest(long rowVersion, String reason) {
    }

    public record ConflictActionRequest(String action, String targetApplicationCode, Long targetRowVersion,
                                        String conflictToken, String reason) {
    }

    public record Application(
            long id, long tenantId, String applicationCode,
            String projectId, String projectCode, String projectName,
            boolean emergency, Long windowId, Long assignedWindowId,
            String subsystemId, String subsystemCode, String subsystemName,
            VersionType versionType, Characteristic characteristic, String workflowCode, Status status,
            long requesterId, String requesterName, String requesterDepartment,
            String emergencyDescription, String urgentReason, String description,
            LocalDateTime approvedAt, long rowVersion,
            long createdBy, long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<DeliverySnapshot> deliveries, List<String> requirementCodes
    ) {
    }

    public record Response(
            String applicationCode,
            String projectId, String projectCode, String projectName,
            boolean emergency, Long windowId, String windowCode, String windowName,
            String subsystemId, String subsystemCode, String subsystemName,
            String versionType, String characteristic, String workflowCode, String status,
            long requesterId, String requesterName, String requesterDepartment,
            String emergencyDescription, String urgentReason, String description,
            List<DeliverySnapshot> deliveries, List<FileMediaSnapshot> fileMedia, List<String> requirementCodes,
            boolean windowAvailable, String windowUnavailableReason,
            long rowVersion, LocalDateTime approvedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            ConflictReport conflicts
    ) {
        public Response(String applicationCode,
                        String projectId, String projectCode, String projectName,
                        boolean emergency, Long windowId, String windowCode, String windowName,
                        String subsystemId, String subsystemCode, String subsystemName,
                        String versionType, String characteristic, String workflowCode, String status,
                        long requesterId, String requesterName, String requesterDepartment,
                        String emergencyDescription, String urgentReason, String description,
                        List<DeliverySnapshot> deliveries, List<String> requirementCodes,
                        boolean windowAvailable, String windowUnavailableReason,
                        long rowVersion, LocalDateTime approvedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
                        ConflictReport conflicts) {
            this(applicationCode, projectId, projectCode, projectName, emergency, windowId, windowCode, windowName,
                    subsystemId, subsystemCode, subsystemName, versionType, characteristic, workflowCode, status,
                    requesterId, requesterName, requesterDepartment, emergencyDescription, urgentReason, description,
                    deliveries, List.of(), requirementCodes, windowAvailable, windowUnavailableReason, rowVersion,
                    approvedAt, createdAt, updatedAt, conflicts);
        }
    }

    public record VersionChange(String deliveryUnitCode, String deliveryUnitName,
                                String previousVersion, String currentVersion) {
    }

    public record RelatedHistoryView(
            String applicationCode, String status, String versionType, String characteristic,
            String requesterName, String requesterDepartment,
            LocalDateTime createdAt, LocalDateTime approvedAt,
            List<String> requirementCodes, String description,
            List<VersionChange> versionChanges
    ) {
    }

    public record HistoricalApplication(Response application, List<VersionChange> versionChanges,
                                        List<String> allowedActions) {
    }

    public record ConflictReport(String conflictToken, List<HistoricalApplication> applications) {
        public static ConflictReport empty() { return new ConflictReport(null, List.of()); }
        public boolean hasConflicts() { return !applications.isEmpty(); }
        public boolean hasInReview() {
            return applications.stream().anyMatch(item -> Status.IN_REVIEW.name().equals(item.application().status()));
        }
    }

    public record ConflictActionResult(String action, String navigateApplicationCode, ConflictReport conflicts) {
    }
}

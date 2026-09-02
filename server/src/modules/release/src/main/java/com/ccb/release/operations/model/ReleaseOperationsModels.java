package com.ccb.release.operations.model;

import java.time.LocalDateTime;
import java.util.List;

public final class ReleaseOperationsModels {
    private ReleaseOperationsModels() {
    }

    public enum TimelineType { NORMAL, ROLLBACK }
    public enum DrillStatus { PLANNED, RUNNING, COMPLETED }
    public enum IssuePriority { LOW, MEDIUM, HIGH, CRITICAL }
    public enum IssueStatus { OPEN, ANALYZING, RESOLVED, CLOSED }

    public record DrillPlan(long id, long tenantId, long projectId, String scenarioContent,
                            String environmentContent, long rowVersion, LocalDateTime updatedAt,
                            List<DrillRound> rounds) {
    }

    public record DrillPlanRequest(String scenarioContent, String environmentContent, long rowVersion) {
    }

    public record DrillRound(long id, long projectId, int roundNo, String roundName, LocalDateTime plannedAt,
                             DrillStatus status, String resultContent, long rowVersion, LocalDateTime updatedAt) {
    }

    public record DrillRoundRequest(String roundName, LocalDateTime plannedAt, String status,
                                    String resultContent, long rowVersion) {
    }

    public record Timeline(long id, long projectId, TimelineType timelineType, String timelineName,
                           String description, long rowVersion, LocalDateTime updatedAt,
                           List<TimelineItem> items) {
    }

    public record TimelineRequest(String timelineName, String description, long rowVersion) {
    }

    public record TimelineItem(long id, long projectId, int seqNo, String itemName, LocalDateTime plannedStart,
                               LocalDateTime plannedEnd, Long ownerId, String ownerName, String status,
                               String description, long rowVersion, LocalDateTime updatedAt) {
    }

    public record TimelineItemRequest(Integer seqNo, String itemName, LocalDateTime plannedStart,
                                      LocalDateTime plannedEnd, Long ownerId, String status,
                                      String description, long rowVersion) {
    }

    public record Issue(long id, long projectId, String issueNo, String issueTitle, IssuePriority priority,
                        IssueStatus issueStatus, LocalDateTime discoveredAt, Long ownerId, String ownerName,
                        String issueDescription, String analysisContent, String actionContent,
                        String followUpContent, LocalDateTime closedAt, long rowVersion, LocalDateTime updatedAt) {
    }

    public record IssueRequest(String issueNo, String issueTitle, String priority, String issueStatus,
                               LocalDateTime discoveredAt, Long ownerId, String issueDescription,
                               String analysisContent, String actionContent, String followUpContent,
                               LocalDateTime closedAt, long rowVersion) {
    }

    public record Group(long id, long projectId, String groupName, String description, long rowVersion,
                        LocalDateTime updatedAt, List<GroupMember> members) {
    }

    public record GroupRequest(String groupName, String description, long rowVersion) {
    }

    public record GroupMember(long id, long groupId, long projectMemberId, long userId, String memberName,
                              LocalDateTime createdAt) {
    }

    public record MemberOption(long id, long userId, String displayName, String username) {
    }
}

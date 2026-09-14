package com.ccb.development.model;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DevelopmentTaskModels {
    private DevelopmentTaskModels() {}
    public enum SourceMode { LINKED, STANDALONE }
    public enum TaskStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, CANCELLED }

    public record CreateTask(String projectRef, SourceMode sourceMode, Long sourceRequirementId, String sourceRevision,
                             Long systemId, String title, String description, Long ownerId,
                             LocalDate developmentPlanStart, LocalDate developmentPlanEnd,
                             LocalDate testPlanStart, LocalDate testPlanEnd, String requestId) {
        @JsonAnySetter public void unknown(String field, JsonNode value) { rejectField(field); }
    }
    public record UpdateTask(String title, String description, Long ownerId, LocalDate developmentPlanStart,
                             LocalDate developmentPlanEnd, LocalDate testPlanStart, LocalDate testPlanEnd,
                             @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) long rowVersion) {
        @JsonAnySetter public void unknown(String field, JsonNode value) { rejectField(field); }
    }
    public record TaskAction(String action, @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) long rowVersion, String reason) {
        @JsonAnySetter public void unknown(String field, JsonNode value) { rejectField(field); }
    }
    public record TaskQuery(String projectRef, Long systemId, Long ownerId, String status, String keyword, PageQuery page) {}
    public record UserView(String id, String name) {}
    public record SystemView(String id, String code, String name, String ownerId, String status, long rowVersion) {}
    public record SourceView(String id, String number, String revision, List<String> roles, List<String> systemCodes) {}
    public record TaskView(String id, String number, String projectRef, String title, String description,
                           SourceMode sourceMode, SourceView source, SystemView system, UserView owner, TaskStatus status,
                           LocalDate developmentPlanStart, LocalDate developmentPlanEnd,
                           LocalDate testPlanStart, LocalDate testPlanEnd, long workItemCount, long completedWorkItemCount,
                           List<String> allowedActions, long rowVersion, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record PendingRequirement(String sourceRequirementId, String sourceNumber, String sourceName, String summary,
                                     String sourceRevision, SystemView system, List<String> roles) {}
    public record TaskEntity(long id, long tenantId, long projectId, String projectRef, String number, SourceMode sourceMode,
                             Long sourceRequirementId, String sourceNumber, String sourceRevision,
                             List<String> sourceRoles, List<String> sourceSystemCodes, long systemId, long ownerId,
                             String title, String description, TaskStatus status, long rowVersion,
                             LocalDate developmentPlanStart, LocalDate developmentPlanEnd,
                             LocalDate testPlanStart, LocalDate testPlanEnd, String requestId, String requestHash,
                             long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Visibility(boolean admin, long actorId, List<Long> systemIds) {}
    public record ChangeView(String id, String objectType, String objectId, String action, JsonNode before, JsonNode after,
                             UserView actor, LocalDateTime createdAt, String traceId) {}

    public static String required(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw bad(label + "不能为空或超出长度限制");
        return value.trim();
    }
    public static String optional(String value, int max, String label) {
        if (value != null && value.length() > max) throw bad(label + "超出长度限制");
        return value == null ? "" : value;
    }
    public static void dates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) throw bad("结束日期不能早于开始日期");
        for (var date : new LocalDate[]{start, end}) if (date != null && (date.getYear() < 1900 || date.getYear() > 9999)) throw bad("日期超出支持范围");
    }
    public static void rejectField(String field) { throw bad("不允许提交字段：" + field); }
    public static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    public static BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    public static BusinessException missing() { return new BusinessException(40400, "记录不存在或不可访问"); }
}

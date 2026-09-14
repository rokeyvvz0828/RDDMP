package com.ccb.development.model;

import com.ccb.common.api.PageQuery;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.ccb.development.model.DevelopmentTaskModels.rejectField;

public final class DevelopmentWorkItemModels {
    private DevelopmentWorkItemModels() {}
    public enum WorkItemStatus { TODO, IN_PROGRESS, IN_REVIEW, DONE }
    public record WorkItemWrite(Long taskId,String title,String description,Long assigneeId,
                                LocalDate plannedStart,LocalDate plannedEnd,LocalDate actualStart,LocalDate actualEnd,
                                boolean blocked,String blockReason,Long rowVersion){
        @JsonAnySetter public void unknown(String field,JsonNode value){rejectField(field);}
    }
    public record WorkItemAction(String action,@JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) long rowVersion,String reason){
        @JsonAnySetter public void unknown(String field,JsonNode value){rejectField(field);}
    }
    public record WorkItemQuery(String projectRef,Long taskId,Long systemId,Long assigneeId,String status,String keyword,
                                LocalDate plannedFrom,LocalDate plannedTo,PageQuery page){}
    public record WorkItemEntity(long id,long tenantId,long taskId,String title,String description,long assigneeId,
                                 WorkItemStatus status,LocalDate plannedStart,LocalDate plannedEnd,LocalDate actualStart,LocalDate actualEnd,
                                 boolean blocked,String blockReason,long rowVersion,LocalDateTime createdAt,LocalDateTime updatedAt){}
    public record WorkItemView(String id,String taskId,String taskNumber,String taskTitle,SystemView system,SourceView source,
                               UserView assignee,String title,String description,WorkItemStatus status,
                               LocalDate plannedStart,LocalDate plannedEnd,LocalDate actualStart,LocalDate actualEnd,
                               boolean blocked,String blockReason,List<String> allowedActions,long rowVersion,List<String> warnings,
                               DevelopmentStageModels.DurationMetrics duration){}
    public record WorkItemPage(List<WorkItemView> records,long total,long page,long size,Map<String,Long> counts){}
    public record ItemPage(List<WorkItemEntity> records,long total,long page,long size,Map<String,Long> counts){}
}

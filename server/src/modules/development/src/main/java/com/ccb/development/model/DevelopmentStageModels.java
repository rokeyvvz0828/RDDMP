package com.ccb.development.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.ccb.development.model.DevelopmentTaskModels.rejectField;

public final class DevelopmentStageModels {
    private DevelopmentStageModels(){}
    public enum AttachmentKind { DESIGN, CODE_WALK, TEST_REPORT }
    public record StageWrite(LocalDate designPlanStart,LocalDate designPlanEnd,String designDocumentPath,List<Long> designAttachmentIds,
                              LocalDate implementationActualStart,LocalDate implementationActualEnd,List<Long> codeWalkAttachmentIds,
                              List<Long> testReportAttachmentIds,boolean notApplicableDesign,boolean notApplicableImplementation,
                              @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) long rowVersion){
        @JsonAnySetter public void unknown(String field,JsonNode value){rejectField(field);}
    }
    public record StageEntity(long taskId,LocalDate designPlanStart,LocalDate designPlanEnd,String designDocumentPath,
                               LocalDate implementationActualStart,LocalDate implementationActualEnd,boolean notApplicableDesign,
                               boolean notApplicableImplementation,long rowVersion,LocalDateTime designRegisteredAt,LocalDateTime testRegisteredAt){}
    public record AttachmentView(String id,String fileName,long fileSize,String contentType){}
    public record DurationMetrics(String status,Long plannedDays,Long actualDays,BigDecimal variancePercent,String calendarVersion,String calendarLabel){}
    public record StageView(String taskId,LocalDate designPlanStart,LocalDate designPlanEnd,String designDocumentPath,List<AttachmentView> designAttachments,
                             LocalDate implementationActualStart,LocalDate implementationActualEnd,List<AttachmentView> codeWalkAttachments,
                             List<AttachmentView> testReportAttachments,boolean notApplicableDesign,boolean notApplicableImplementation,boolean pureTest,
                             long rowVersion,long taskRowVersion,LocalDateTime designRegisteredAt,LocalDateTime testRegisteredAt,DurationMetrics duration,boolean readOnly){}
}

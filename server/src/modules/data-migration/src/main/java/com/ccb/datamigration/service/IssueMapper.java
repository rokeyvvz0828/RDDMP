package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface IssueMapper {
    Long count(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("deleted") boolean deleted, @Param("granularity") String granularity, @Param("systemCode") String systemCode, @Param("issueSource") String issueSource, @Param("defectType") String defectType, @Param("frequency") String frequency, @Param("keyword") String keyword);
    List<Map<String, Object>> page(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("deleted") boolean deleted, @Param("granularity") String granularity, @Param("systemCode") String systemCode, @Param("issueSource") String issueSource, @Param("defectType") String defectType, @Param("frequency") String frequency, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") long offset);
    List<Map<String, Object>> exportRows(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("granularity") String granularity, @Param("systemCode") String systemCode, @Param("issueSource") String issueSource, @Param("defectType") String defectType, @Param("frequency") String frequency, @Param("keyword") String keyword);
    List<Map<String, Object>> find(@Param("tenantId") long tenantId, @Param("id") long id, @Param("deleted") boolean deleted);
    List<Long> relationIds(@Param("tenantId") long tenantId, @Param("issueId") long issueId, @Param("type") String type);
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("issueCode") String issueCode, @Param("issueName") String issueName, @Param("granularity") String granularity, @Param("systemCode") String systemCode, @Param("issueSource") String issueSource, @Param("defectType") String defectType, @Param("issueDescription") String issueDescription, @Param("solution") String solution, @Param("meetingConclusion") String meetingConclusion, @Param("processingSteps") String processingSteps, @Param("businessScenario") String businessScenario, @Param("handler") String handler, @Param("responsibleParty") String responsibleParty, @Param("keywords") String keywords, @Param("frequency") String frequency, @Param("ownerId") long ownerId, @Param("createdBy") long createdBy, @Param("updatedBy") long updatedBy);
    int update(@Param("id") long id, @Param("tenantId") long tenantId, @Param("issueCode") String issueCode, @Param("issueName") String issueName, @Param("granularity") String granularity, @Param("systemCode") String systemCode, @Param("issueSource") String issueSource, @Param("defectType") String defectType, @Param("issueDescription") String issueDescription, @Param("solution") String solution, @Param("meetingConclusion") String meetingConclusion, @Param("processingSteps") String processingSteps, @Param("businessScenario") String businessScenario, @Param("handler") String handler, @Param("responsibleParty") String responsibleParty, @Param("keywords") String keywords, @Param("frequency") String frequency, @Param("updatedBy") long updatedBy);
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("deletedBy") long deletedBy);
    int restore(@Param("tenantId") long tenantId, @Param("id") long id, @Param("updatedBy") long updatedBy);
    int deleteRelations(@Param("tenantId") long tenantId, @Param("issueId") long issueId, @Param("type") String type);
    int insertRelation(@Param("tenantId") long tenantId, @Param("issueId") long issueId, @Param("type") String type, @Param("relatedId") long relatedId, @Param("createdBy") long createdBy);
    int deleteAllRelations(@Param("tenantId") long tenantId, @Param("issueId") long issueId);
    int purge(@Param("tenantId") long tenantId, @Param("id") long id);
    int purgeAllRelations(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int purgeAll(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    List<Map<String, Object>> systemName(@Param("tenantId") long tenantId, @Param("systemCode") String systemCode);
    List<Map<String, Object>> meetingOptions(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    List<Map<String, Object>> targetTableOptions(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    List<Long> targetTableProjects(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode);
    List<Map<String, Object>> targetFieldOptions(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode);
    Integer relationTargetCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("id") long id, @Param("type") String type);
    Integer invalidFieldRelationCount(@Param("tenantId") long tenantId, @Param("issueId") long issueId);
    Integer issueCodeCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("issueCode") String issueCode, @Param("currentId") Long currentId);
    Integer enabledComponentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId, @Param("projectId") long projectId, @Param("operation") String operation, @Param("entityId") long entityId);
}

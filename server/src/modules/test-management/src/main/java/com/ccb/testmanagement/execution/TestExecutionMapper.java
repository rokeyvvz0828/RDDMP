package com.ccb.testmanagement.execution;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
interface TestExecutionMapper {
    List<Map<String, Object>> systems(Map<String, Object> p); List<Map<String, Object>> rounds(Map<String, Object> p); List<Map<String, Object>> cycles(Map<String, Object> p); List<Map<String, Object>> directories(Map<String, Object> p);
    Long executionCount(Map<String, Object> p); List<Map<String, Object>> executions(Map<String, Object> p); List<Long> caseIdsByScope(Map<String, Object> p); List<Long> caseIdsByCode(Map<String, Object> p); Map<String, Object> testCase(Map<String, Object> p); Long directoryCaseCount(Map<String, Object> p); void insertExecution(Map<String, Object> p);
    void insertDirectory(Map<String, Object> p); void updateDirectory(Map<String, Object> p); Long directoryExecutionCount(Map<String, Object> p); Long directoryChildCount(Map<String, Object> p); void deleteDirectory(Map<String, Object> p);
    Map<String, Object> executionDetail(Map<String, Object> p); List<Map<String, Object>> attachmentRows(Map<String, Object> p); List<Map<String, Object>> defects(Map<String, Object> p); List<Map<String, Object>> traces(Map<String, Object> p); void updateResult(Map<String, Object> p); void moveExecution(Map<String, Object> p); void removeExecution(Map<String, Object> p);
    Long defectCount(Map<String, Object> p); Long defectExistsCount(Map<String, Object> p); Long activeRelationCount(Map<String, Object> p); void insertRelation(Map<String, Object> p); List<Long> activeDefectIds(Map<String, Object> p); Map<String, Object> snapshotSource(Map<String, Object> p); void snapshotRelation(Map<String, Object> p);
    List<Long> attachmentIds(Map<String, Object> p); void deleteAttachment(Map<String, Object> p); Long attachmentCount(Map<String, Object> p); void insertAttachment(Map<String, Object> p); void insertTrace(Map<String, Object> p);
    Long projectCount(Map<String, Object> p); Long enabledSystemCount(Map<String, Object> p); Long roundCount(Map<String, Object> p); Long cycleCount(Map<String, Object> p); Map<String, Object> directory(Map<String, Object> p); Map<String, Object> execution(Map<String, Object> p); Long writeRoleCount(Map<String, Object> p);
}

package com.ccb.testmanagement.execution;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
class TestExecutionRepository {
    private final TestExecutionMapper mapper;
    TestExecutionRepository(TestExecutionMapper mapper) { this.mapper = mapper; }
    List<Map<String,Object>> systems(Map<String,Object> p){return mapper.systems(p);} List<Map<String,Object>> rounds(Map<String,Object> p){return mapper.rounds(p);} List<Map<String,Object>> cycles(Map<String,Object> p){return mapper.cycles(p);} List<Map<String,Object>> directories(Map<String,Object> p){return mapper.directories(p);}
    long executionCount(Map<String,Object> p){return zero(mapper.executionCount(p));} List<Map<String,Object>> executions(Map<String,Object> p){return mapper.executions(p);} List<Long> caseIdsByScope(Map<String,Object> p){return mapper.caseIdsByScope(p);} List<Long> caseIdsByCode(Map<String,Object> p){return mapper.caseIdsByCode(p);} Map<String,Object> testCase(Map<String,Object> p){return mapper.testCase(p);} boolean existsInDirectory(Map<String,Object> p){return zero(mapper.directoryCaseCount(p))>0;} void insertExecution(Map<String,Object> p){mapper.insertExecution(p);}
    void insertDirectory(Map<String,Object> p){mapper.insertDirectory(p);} void updateDirectory(Map<String,Object> p){mapper.updateDirectory(p);} boolean directoryHasExecutions(Map<String,Object> p){return zero(mapper.directoryExecutionCount(p))>0;} boolean directoryHasChildren(Map<String,Object> p){return zero(mapper.directoryChildCount(p))>0;} void deleteDirectory(Map<String,Object> p){mapper.deleteDirectory(p);}
    Map<String,Object> executionDetail(Map<String,Object> p){return mapper.executionDetail(p);} List<Map<String,Object>> attachmentRows(Map<String,Object> p){return mapper.attachmentRows(p);} List<Map<String,Object>> defects(Map<String,Object> p){return mapper.defects(p);} List<Map<String,Object>> traces(Map<String,Object> p){return mapper.traces(p);} void updateResult(Map<String,Object> p){mapper.updateResult(p);} void moveExecution(Map<String,Object> p){mapper.moveExecution(p);} void removeExecution(Map<String,Object> p){mapper.removeExecution(p);}
    boolean hasUnresolvedDefects(Map<String,Object> p){return zero(mapper.defectCount(p))>0;} boolean defectExists(Map<String,Object> p){return zero(mapper.defectExistsCount(p))>0;} boolean hasActiveDefects(Map<String,Object> p){return zero(mapper.activeRelationCount(p))>0;} void insertRelation(Map<String,Object> p){mapper.insertRelation(p);} List<Long> activeDefectIds(Map<String,Object> p){return mapper.activeDefectIds(p);} Map<String,Object> snapshotSource(Map<String,Object> p){return mapper.snapshotSource(p);} void snapshotRelation(Map<String,Object> p){mapper.snapshotRelation(p);}
    List<Long> attachmentIds(Map<String,Object> p){return mapper.attachmentIds(p);} void deleteAttachment(Map<String,Object> p){mapper.deleteAttachment(p);} boolean attachmentExists(Map<String,Object> p){return zero(mapper.attachmentCount(p))>0;} void insertAttachment(Map<String,Object> p){mapper.insertAttachment(p);} void insertTrace(Map<String,Object> p){mapper.insertTrace(p);}
    boolean projectExists(Map<String,Object> p){return zero(mapper.projectCount(p))>0;} boolean enabledSystemExists(Map<String,Object> p){return zero(mapper.enabledSystemCount(p))>0;} boolean roundExists(Map<String,Object> p){return zero(mapper.roundCount(p))>0;} boolean cycleExists(Map<String,Object> p){return zero(mapper.cycleCount(p))>0;} Map<String,Object> directory(Map<String,Object> p){return mapper.directory(p);} Map<String,Object> execution(Map<String,Object> p){return mapper.execution(p);} boolean mayWrite(Map<String,Object> p){return zero(mapper.writeRoleCount(p))>0;}
    private static long zero(Long value){return value == null ? 0 : value;}
}

package com.ccb.testmanagement.scope;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
interface TestScopeMapper {
    List<Map<String, Object>> treeSystems(Map<String, Object> p);
    List<Map<String, Object>> treeDirectories(Map<String, Object> p);
    Long scopeCount(Map<String, Object> p);
    List<Map<String, Object>> scopes(Map<String, Object> p);
    Long projectCount(Map<String, Object> p);
    Long participatingSystemCount(Map<String, Object> p);
    List<Map<String, Object>> systemByReference(Map<String, Object> p);
    Map<String, Object> directory(Map<String, Object> p);
    Map<String, Object> directoryParent(Map<String, Object> p);
    List<Long> childDirectoryIds(Map<String, Object> p);
    Long directoryNameCount(Map<String, Object> p);
    List<Map<String, Object>> directoryByParentAndName(Map<String, Object> p);
    int insertDirectory(Map<String, Object> p);
    int updateDirectory(Map<String, Object> p);
    Map<String, Object> directoryResult(Map<String, Object> p);
    Long childDirectoryCount(Map<String, Object> p);
    int reassignScopeDirectory(Map<String, Object> p);
    int deleteDirectory(Map<String, Object> p);
    Map<String, Object> scopeRow(Map<String, Object> p);
    Map<String, Object> deletedScopeRow(Map<String, Object> p);
    Long caseCount(Map<String, Object> p);
    Integer nextScopeSequence(Map<String, Object> p);
    String systemCode(Map<String, Object> p);
    Long scopeCodeCount(Map<String, Object> p);
    int insertScope(Map<String, Object> p);
    int updateScope(Map<String, Object> p);
    int syncCaseCodes(Map<String, Object> p);
    Map<String, Object> scopeResult(Map<String, Object> p);
    int invalidateScope(Map<String, Object> p);
    int deleteScope(Map<String, Object> p);
    int restoreScope(Map<String, Object> p);
    Long dictionaryOptionCount(Map<String, Object> p);
    List<Map<String, Object>> activeScopeByCode(Map<String, Object> p);
    int insertAudit(Map<String, Object> p);
}

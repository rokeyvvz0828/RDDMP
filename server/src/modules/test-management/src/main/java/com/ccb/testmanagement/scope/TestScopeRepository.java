package com.ccb.testmanagement.scope;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
class TestScopeRepository {
    private final TestScopeMapper mapper;

    TestScopeRepository(TestScopeMapper mapper) { this.mapper = mapper; }

    List<Map<String, Object>> treeSystems(Map<String, Object> p) { return mapper.treeSystems(p); }
    List<Map<String, Object>> treeDirectories(Map<String, Object> p) { return mapper.treeDirectories(p); }
    long scopeCount(Map<String, Object> p) { return zero(mapper.scopeCount(p)); }
    List<Map<String, Object>> scopes(Map<String, Object> p) { return mapper.scopes(p); }
    boolean projectExists(Map<String, Object> p) { return zero(mapper.projectCount(p)) > 0; }
    boolean participatingSystemExists(Map<String, Object> p) { return zero(mapper.participatingSystemCount(p)) > 0; }
    List<Map<String, Object>> systemByReference(Map<String, Object> p) { return mapper.systemByReference(p); }
    Map<String, Object> directory(Map<String, Object> p) { return mapper.directory(p); }
    Map<String, Object> directoryParent(Map<String, Object> p) { return mapper.directoryParent(p); }
    List<Long> childDirectoryIds(Map<String, Object> p) { return mapper.childDirectoryIds(p); }
    boolean directoryNameExists(Map<String, Object> p) { return zero(mapper.directoryNameCount(p)) > 0; }
    List<Map<String, Object>> directoryByParentAndName(Map<String, Object> p) { return mapper.directoryByParentAndName(p); }
    void insertDirectory(Map<String, Object> p) { mapper.insertDirectory(p); }
    void updateDirectory(Map<String, Object> p) { mapper.updateDirectory(p); }
    Map<String, Object> directoryResult(Map<String, Object> p) { return mapper.directoryResult(p); }
    boolean hasChildDirectory(Map<String, Object> p) { return zero(mapper.childDirectoryCount(p)) > 0; }
    void reassignScopeDirectory(Map<String, Object> p) { mapper.reassignScopeDirectory(p); }
    void deleteDirectory(Map<String, Object> p) { mapper.deleteDirectory(p); }
    Map<String, Object> scopeRow(Map<String, Object> p) { return mapper.scopeRow(p); }
    Map<String, Object> deletedScopeRow(Map<String, Object> p) { return mapper.deletedScopeRow(p); }
    long caseCount(Map<String, Object> p) { return zero(mapper.caseCount(p)); }
    int nextScopeSequence(Map<String, Object> p) { Integer value = mapper.nextScopeSequence(p); return value == null ? 1 : value; }
    String systemCode(Map<String, Object> p) { return mapper.systemCode(p); }
    boolean scopeCodeExists(Map<String, Object> p) { return zero(mapper.scopeCodeCount(p)) > 0; }
    void insertScope(Map<String, Object> p) { mapper.insertScope(p); }
    void updateScope(Map<String, Object> p) { mapper.updateScope(p); }
    int syncCaseCodes(Map<String, Object> p) { return mapper.syncCaseCodes(p); }
    Map<String, Object> scopeResult(Map<String, Object> p) { return mapper.scopeResult(p); }
    void invalidateScope(Map<String, Object> p) { mapper.invalidateScope(p); }
    void deleteScope(Map<String, Object> p) { mapper.deleteScope(p); }
    void restoreScope(Map<String, Object> p) { mapper.restoreScope(p); }
    boolean dictionaryOptionExists(Map<String, Object> p) { return zero(mapper.dictionaryOptionCount(p)) > 0; }
    List<Map<String, Object>> activeScopeByCode(Map<String, Object> p) { return mapper.activeScopeByCode(p); }
    void insertAudit(Map<String, Object> p) { mapper.insertAudit(p); }

    private static long zero(Long value) { return value == null ? 0 : value; }
}

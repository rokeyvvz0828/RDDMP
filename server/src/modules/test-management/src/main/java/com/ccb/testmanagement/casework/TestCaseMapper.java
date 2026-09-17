package com.ccb.testmanagement.casework;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper interface TestCaseMapper {
 List<Map<String,Object>> treeSystems(Map<String,Object> p); List<Map<String,Object>> treeDirectories(Map<String,Object> p); List<Map<String,Object>> scopes(Map<String,Object> p);
 Long caseCount(Map<String,Object> p); List<Map<String,Object>> casePage(Map<String,Object> p); Map<String,Object> caseRow(Map<String,Object> p); List<Map<String,Object>> attachments(Map<String,Object> p); List<Map<String,Object>> attachmentRows(Map<String,Object> p); List<Long> attachmentIds(Map<String,Object> p);
 Long directoryNameCount(Map<String,Object> p); int insertDirectory(Map<String,Object> p); int updateDirectory(Map<String,Object> p); Map<String,Object> directorySaved(Map<String,Object> p); Long childDirectoryCount(Map<String,Object> p); int moveDirectoryCases(Map<String,Object> p); int deleteDirectory(Map<String,Object> p);
 int insertCase(Map<String,Object> p); int updateCase(Map<String,Object> p); int moveCase(Map<String,Object> p); int invalidateCase(Map<String,Object> p); int deleteCase(Map<String,Object> p); List<Map<String,Object>> caseByCode(Map<String,Object> p); List<Map<String,Object>> importCaseByCode(Map<String,Object> p);
 int deleteAttachment(Map<String,Object> p); Long attachmentExists(Map<String,Object> p); int insertAttachment(Map<String,Object> p);
 Long projectExists(Map<String,Object> p); Long enabledSystemExists(Map<String,Object> p); Map<String,Object> scope(Map<String,Object> p); Map<String,Object> scopeByCode(Map<String,Object> p); Map<String,Object> directory(Map<String,Object> p); String dictionaryOption(Map<String,Object> p); Long activeUserExists(Map<String,Object> p); Map<String,Object> directoryParent(Map<String,Object> p); List<Long> directoryChildren(Map<String,Object> p); Long nextSerial(Map<String,Object> p); Long serialExists(Map<String,Object> p); Map<String,Object> directoryByName(Map<String,Object> p); Map<String,Object> directoryPath(Map<String,Object> p); int insertAudit(Map<String,Object> p);
}

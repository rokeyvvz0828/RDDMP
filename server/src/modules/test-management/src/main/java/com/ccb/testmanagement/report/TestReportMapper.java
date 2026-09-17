package com.ccb.testmanagement.report;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;
@Mapper interface TestReportMapper {
 Map<String,Object> project(Map<String,Object> p); Long projectCount(Map<String,Object> p); List<Map<String,Object>> systems(Map<String,Object> p); List<Map<String,Object>> specials(Map<String,Object> p); List<Map<String,Object>> rounds(Map<String,Object> p); List<Map<String,Object>> cycles(Map<String,Object> p);
 Long reportCount(Map<String,Object> p); List<Map<String,Object>> reportPage(Map<String,Object> p); Long sameNameCount(Map<String,Object> p); int insertReport(Map<String,Object> p); int updateReport(Map<String,Object> p); Integer nextVersion(Map<String,Object> p); int insertVersion(Map<String,Object> p); int updateCurrentVersion(Map<String,Object> p);
 List<Map<String,Object>> reportVersions(Map<String,Object> p); Map<String,Object> report(Map<String,Object> p); Map<String,Object> version(Map<String,Object> p); List<Map<String,Object>> supplements(Map<String,Object> p); List<Map<String,Object>> history(Map<String,Object> p); Long supplementCount(Map<String,Object> p); int updateSupplement(Map<String,Object> p); int insertSupplement(Map<String,Object> p); int insertConfigurationAudit(Map<String,Object> p); int deleteReport(Map<String,Object> p); int insertTrace(Map<String,Object> p);
 Long specialCount(Map<String,Object> p); Long roundCount(Map<String,Object> p); Long cycleCount(Map<String,Object> p); Long participatingSystemCount(Map<String,Object> p); List<Map<String,Object>> archivedSnapshot(Map<String,Object> p);
 Long scopeCount(Map<String,Object> p); Long caseCount(Map<String,Object> p); Long executionCount(Map<String,Object> p); Long executionSuccessCount(Map<String,Object> p); Long executionFailedCount(Map<String,Object> p); Long executionBlockedCount(Map<String,Object> p); Long defectCount(Map<String,Object> p); Long openDefectCount(Map<String,Object> p); List<Map<String,Object>> scopeDetails(Map<String,Object> p); List<Map<String,Object>> defectDetails(Map<String,Object> p);
}

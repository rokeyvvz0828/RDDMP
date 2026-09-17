package com.ccb.testmanagement.analytics;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
interface TestAnalyticsMapper {
    List<Map<String, Object>> mine(Map<String, Object> p);
    List<Map<String, Object>> shared(Map<String, Object> p);
    List<Map<String, Object>> scopeDrilldown(Map<String, Object> p);
    List<Map<String, Object>> caseDrilldown(Map<String, Object> p);
    List<Map<String, Object>> executionDrilldown(Map<String, Object> p);
    List<Map<String, Object>> defectDrilldown(Map<String, Object> p);
    Long namedReportCount(Map<String, Object> p);
    Long ownedReportCount(Map<String, Object> p);
    Map<String, Object> report(Map<String, Object> p);
    int insertReport(Map<String, Object> p);
    int updateReport(Map<String, Object> p);
    int publishReport(Map<String, Object> p);
    int deleteReport(Map<String, Object> p);
    Long roundCount(Map<String, Object> p);
    int upsertSnapshot(Map<String, Object> p);
    List<Map<String, Object>> snapshots(Map<String, Object> p);
    List<Map<String, Object>> defectDistribution(Map<String, Object> p);
    List<Map<String, Object>> personnelWorkload(Map<String, Object> p);
    List<Map<String, Object>> executionProgress(Map<String, Object> p);
    List<Map<String, Object>> rounds(Map<String, Object> p);
    List<Map<String, Object>> cycles(Map<String, Object> p);
    List<Map<String, Object>> dictionaryOptions(Map<String, Object> p);
    List<Map<String, Object>> snapshotRows(Map<String, Object> p);
    String roundName(Map<String, Object> p);
    List<Map<String, Object>> scopeCoverage(Map<String, Object> p);
    List<Map<String, Object>> executionRows(Map<String, Object> p);
    List<Map<String, Object>> executionTrend(Map<String, Object> p);
    List<Map<String, Object>> defectRows(Map<String, Object> p);
    List<Map<String, Object>> defectTrend(Map<String, Object> p);
    List<Map<String, Object>> workloadRows(Map<String, Object> p);
    int insertTrace(Map<String, Object> p);
}

package com.ccb.testmanagement.analytics;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
class TestAnalyticsRepository {
    private final TestAnalyticsMapper mapper;
    TestAnalyticsRepository(TestAnalyticsMapper mapper) { this.mapper = mapper; }
    List<Map<String, Object>> mine(Map<String, Object> p) { return mapper.mine(p); }
    List<Map<String, Object>> shared(Map<String, Object> p) { return mapper.shared(p); }
    List<Map<String, Object>> scopeDrilldown(Map<String, Object> p) { return mapper.scopeDrilldown(p); }
    List<Map<String, Object>> caseDrilldown(Map<String, Object> p) { return mapper.caseDrilldown(p); }
    List<Map<String, Object>> executionDrilldown(Map<String, Object> p) { return mapper.executionDrilldown(p); }
    List<Map<String, Object>> defectDrilldown(Map<String, Object> p) { return mapper.defectDrilldown(p); }
    Long namedReportCount(Map<String, Object> p) { return mapper.namedReportCount(p); }
    Long ownedReportCount(Map<String, Object> p) { return mapper.ownedReportCount(p); }
    Map<String, Object> report(Map<String, Object> p) { return mapper.report(p); }
    void insertReport(Map<String, Object> p) { mapper.insertReport(p); }
    void updateReport(Map<String, Object> p) { mapper.updateReport(p); }
    void publishReport(Map<String, Object> p) { mapper.publishReport(p); }
    void deleteReport(Map<String, Object> p) { mapper.deleteReport(p); }
    Long roundCount(Map<String, Object> p) { return mapper.roundCount(p); }
    void upsertSnapshot(Map<String, Object> p) { mapper.upsertSnapshot(p); }
    List<Map<String, Object>> snapshots(Map<String, Object> p) { return mapper.snapshots(p); }
    List<Map<String, Object>> defectDistribution(Map<String, Object> p) { return mapper.defectDistribution(p); }
    List<Map<String, Object>> personnelWorkload(Map<String, Object> p) { return mapper.personnelWorkload(p); }
    List<Map<String, Object>> executionProgress(Map<String, Object> p) { return mapper.executionProgress(p); }
    List<Map<String, Object>> rounds(Map<String, Object> p) { return mapper.rounds(p); }
    List<Map<String, Object>> cycles(Map<String, Object> p) { return mapper.cycles(p); }
    List<Map<String, Object>> dictionaryOptions(Map<String, Object> p) { return mapper.dictionaryOptions(p); }
    List<Map<String, Object>> snapshotRows(Map<String, Object> p) { return mapper.snapshotRows(p); }
    String roundName(Map<String, Object> p) { return mapper.roundName(p); }
    List<Map<String, Object>> scopeCoverage(Map<String, Object> p) { return mapper.scopeCoverage(p); }
    List<Map<String, Object>> executionRows(Map<String, Object> p) { return mapper.executionRows(p); }
    List<Map<String, Object>> executionTrend(Map<String, Object> p) { return mapper.executionTrend(p); }
    List<Map<String, Object>> defectRows(Map<String, Object> p) { return mapper.defectRows(p); }
    List<Map<String, Object>> defectTrend(Map<String, Object> p) { return mapper.defectTrend(p); }
    List<Map<String, Object>> workloadRows(Map<String, Object> p) { return mapper.workloadRows(p); }
    void insertTrace(Map<String, Object> p) { mapper.insertTrace(p); }
}

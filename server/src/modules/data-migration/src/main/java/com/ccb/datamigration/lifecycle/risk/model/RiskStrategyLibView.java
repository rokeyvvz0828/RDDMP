package com.ccb.datamigration.lifecycle.risk.model;

import java.time.LocalDateTime;
import java.util.List;

/** 全局风险策略库条目视图（基线 14.2.4 策略沉淀与一键复用）。 */
public record RiskStrategyLibView(long id, String strategyCode, String strategyTitle,
                                  String riskTitlePattern, List<String> matchKeywords,
                                  String riskLevel, String probability,
                                  String prePreventMeasure, String responseStrategy,
                                  String degradePlan, String emergencyPlan,
                                  int useCount, String status,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
}

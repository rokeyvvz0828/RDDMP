package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DashboardMetricDefinitionTest {

    @Test
    void exposesExactlyTheApprovedMetricWhitelist() {
        Set<String> expected = Set.of(
                "OVERALL_PLAN", "COMPONENT_PLAN", "PROJECT_TOPIC", "COMPONENT_TOPIC",
                "REPORT", "MEETING", "ISSUE", "RELEASE_DRILL", "MAPPING_DOC", "RULE",
                "PARAMETER", "DEPENDENCY", "SCRIPT");

        assertEquals(expected, DashboardMetricDefinition.codes());
        assertEquals(expected, java.util.Arrays.stream(DashboardMetricDefinition.values())
                .map(DashboardMetricDefinition::code).collect(Collectors.toSet()));
    }

    @Test
    void planAndTopicDefinitionsKeepTheirGranularityApart() {
        assertEquals("PROJECT", DashboardMetricDefinition.require("overall_plan").granularity());
        assertEquals("SYSTEM", DashboardMetricDefinition.require("COMPONENT_PLAN").granularity());
        assertEquals("PROJECT", DashboardMetricDefinition.require("PROJECT_TOPIC").granularity());
        assertEquals("SYSTEM", DashboardMetricDefinition.require("component_topic").granularity());
        assertEquals("dm_plan", DashboardMetricDefinition.require("OVERALL_PLAN").tableName());
        assertEquals("dm_topic", DashboardMetricDefinition.require("COMPONENT_TOPIC").tableName());
    }

    @Test
    void currentDomainColumnsAreUsedForMeetingRuleAndParameter() {
        DashboardMetricDefinition meeting = DashboardMetricDefinition.require("MEETING");
        assertEquals("meeting_id", meeting.idColumn());
        assertTrue(meeting.drilldownSql().contains("meeting_code AS code"));
        assertTrue(meeting.drilldownSql().contains("meeting_title AS name"));

        assertTrue(DashboardMetricDefinition.require("RULE").drilldownSql().contains("rule_code AS code"));
        assertTrue(DashboardMetricDefinition.require("PARAMETER").drilldownSql().contains("parameter_name AS name"));
    }

    @Test
    void dependencyDrilldownProjectsParameterNamesWithoutLegacyColumns() {
        String drill = DashboardMetricDefinition.require("DEPENDENCY").drilldownSql();
        assertTrue(drill.contains("dm_parameter"), "DEPENDENCY 下钻应关联参数表");
        assertTrue(drill.contains("(SELECT p.parameter_name_en"));
        assertTrue(drill.contains("(SELECT p.parameter_name"));
        assertTrue(drill.contains(") AS code"));
        assertTrue(drill.contains(") AS name"));
        assertFalse(drill.contains("doc_code"));
        assertFalse(drill.contains("doc_name"));
        assertTrue(drill.contains("system_code AS systemCode"));
    }

    @Test
    void unknownMetricFailsClosed() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> DashboardMetricDefinition.require("dm_plan; DROP TABLE dm_plan"));
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
    }
}

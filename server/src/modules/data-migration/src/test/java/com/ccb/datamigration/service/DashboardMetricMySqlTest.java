package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.security.model.AuthUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DashboardMetricMySqlTest {
    private static final AuthUser USER = new AuthUser(7L, 3L, "developer", "", "研发人员", 11L, true);
    private static final long PROJECT = 91L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dashboard_metric")
            .withUsername("test")
            .withPassword("test");

    private JdbcTemplate jdbc;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        dropTables();
        createTables();
        insertFixtures();
        service = new DashboardService(jdbc,
                new DataMigrationPermissionService(new JdbcTemplate(), StubProjectAccess.allow()));
    }

    @Test
    void allMetricsMatchTheirCompleteDrilldownWithinTenantAndProject() {
        for (String code : DashboardMetricDefinition.codes()) {
            assertEquals(1L, service.metric(code, PROJECT, USER).get("count"), code);
            assertEquals(1L, service.drilldown(code, PROJECT, 1, 20, USER).total(), code);
            assertEquals(1, service.drilldown(code, PROJECT, 1, 20, USER).records().size(), code);
        }
    }

    @Test
    void dashboardQueriesUseProjectIndexesAndStayBelowLocalLatencyThresholds() {
        String plan = String.valueOf(jdbc.queryForList(
                "EXPLAIN ANALYZE " + DashboardMetricDefinition.OVERALL_PLAN.countSql(),
                DashboardMetricDefinition.OVERALL_PLAN.scopeArguments(USER.tenantId(), PROJECT).toArray()));
        assertTrue(plan.contains("idx_dm_plan_dashboard"), plan);

        List<Long> singleMetricSamples = new ArrayList<>();
        List<Long> metricSamples = new ArrayList<>();
        List<Long> drilldownSamples = new ArrayList<>();
        List<String> codes = List.copyOf(DashboardMetricDefinition.codes());
        for (int iteration = 0; iteration < 20; iteration++) {
            long metricStarted = System.nanoTime();
            for (String code : codes) {
                long singleMetricStarted = System.nanoTime();
                service.metric(code, PROJECT, USER);
                singleMetricSamples.add((System.nanoTime() - singleMetricStarted) / 1_000_000);
            }
            metricSamples.add((System.nanoTime() - metricStarted) / 1_000_000);

            long drilldownStarted = System.nanoTime();
            service.drilldown("OVERALL_PLAN", PROJECT, 1, 20, USER);
            drilldownSamples.add((System.nanoTime() - drilldownStarted) / 1_000_000);
        }
        long singleMetricP95 = percentile95(singleMetricSamples);
        long allMetricsP95 = percentile95(metricSamples);
        long drilldownP95 = percentile95(drilldownSamples);
        System.out.printf("Dashboard local P95: single metric=%dms, 13 metrics=%dms, drilldown=%dms%n",
                singleMetricP95, allMetricsP95, drilldownP95);
        assertTrue(singleMetricP95 <= 500, "single metric local P95=" + singleMetricP95);
        assertTrue(allMetricsP95 <= 2_000, "13 metrics local P95=" + allMetricsP95);
        assertTrue(drilldownP95 <= 800, "drilldown local P95=" + drilldownP95);
    }

    private long percentile95(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
    }

    private void dropTables() {
        for (String table : List.of("dm_plan", "dm_topic", "dm_report", "dm_meeting", "dm_issue",
                "dm_release_drill", "dm_mapping_doc", "dm_rule", "dm_parameter", "dm_dependency", "dm_script")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
    }

    private void createTables() {
        jdbc.execute("CREATE TABLE dm_plan (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, doc_code VARCHAR(96), doc_name VARCHAR(255), granularity VARCHAR(16), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_plan_dashboard (tenant_id, project_id, deleted, granularity, updated_at))");
        jdbc.execute("CREATE TABLE dm_topic (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, doc_code VARCHAR(96), doc_name VARCHAR(255), granularity VARCHAR(16), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_topic_dashboard (tenant_id, project_id, granularity, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_report (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, doc_code VARCHAR(96), doc_name VARCHAR(255), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_report_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_meeting (meeting_id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, meeting_code VARCHAR(96), meeting_title VARCHAR(255), granularity VARCHAR(16), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_meeting_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_issue (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, issue_code VARCHAR(96), issue_name VARCHAR(255), granularity VARCHAR(16), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_issue_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_release_drill (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, doc_code VARCHAR(96), doc_name VARCHAR(255), granularity VARCHAR(16), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_release_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_mapping_doc (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, doc_code VARCHAR(96), doc_name VARCHAR(255), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_mapping_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_rule (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, rule_code VARCHAR(96), rule_code_desc VARCHAR(500), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_rule_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_parameter (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, parameter_name VARCHAR(255), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_parameter_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_dependency (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, doc_code VARCHAR(96), doc_name VARCHAR(255), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_dependency_dashboard (tenant_id, project_id, deleted, updated_at))");
        jdbc.execute("CREATE TABLE dm_script (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, doc_code VARCHAR(96), doc_name VARCHAR(255), system_code VARCHAR(96), deleted TINYINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_dm_script_dashboard (tenant_id, project_id, deleted, updated_at))");
    }

    private void insertFixtures() {
        jdbc.update("INSERT INTO dm_plan VALUES (1,3,91,'PLAN-P','项目方案','PROJECT','',0,NOW()),(2,3,91,'PLAN-S','组件方案','SYSTEM','SYS-A',0,NOW()),(3,3,91,'PLAN-D','已删除','PROJECT','',1,NOW()),(4,3,92,'PLAN-O','其他项目','PROJECT','',0,NOW()),(5,4,91,'PLAN-T','其他租户','PROJECT','',0,NOW())");
        jdbc.update("INSERT INTO dm_topic VALUES (1,3,91,'TOPIC-P','项目专题','PROJECT',0,NOW()),(2,3,91,'TOPIC-S','组件专题','SYSTEM',0,NOW()),(3,3,91,'TOPIC-D','已删除','PROJECT',1,NOW()),(4,3,92,'TOPIC-O','其他项目','PROJECT',0,NOW())");
        jdbc.update("INSERT INTO dm_report VALUES (1,3,91,'REPORT-1','汇报资料',0,NOW())");
        jdbc.update("INSERT INTO dm_meeting VALUES (1,3,91,'MEETING-1','会议纪要','PROJECT',0,NOW())");
        jdbc.update("INSERT INTO dm_issue VALUES (1,3,91,'ISSUE-1','问题清单','SYSTEM','SYS-A',0,NOW())");
        jdbc.update("INSERT INTO dm_release_drill VALUES (1,3,91,'DRILL-1','投产演练','SYSTEM','SYS-A',0,NOW())");
        jdbc.update("INSERT INTO dm_mapping_doc VALUES (1,3,91,'MAP-1','迁移映射','SYS-A',0,NOW())");
        jdbc.update("INSERT INTO dm_rule VALUES (1,3,91,'RULE-1','规则说明','SYS-A',0,NOW())");
        jdbc.update("INSERT INTO dm_parameter VALUES (1,3,91,'参数一','SYS-A',0,NOW())");
        jdbc.update("INSERT INTO dm_dependency VALUES (1,3,91,'DEP-1','依赖文件','SYS-A',0,NOW())");
        jdbc.update("INSERT INTO dm_script VALUES (1,3,91,'SCRIPT-1','迁移程序','SYS-A',0,NOW())");
    }
}

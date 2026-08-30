package com.ccb.datamigration.service;

import java.time.LocalDate;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableScheduling
public class DashboardSnapshotScheduler {
    private final JdbcTemplate jdbc;

    public DashboardSnapshotScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void captureDailySnapshots() {
        LocalDate date = LocalDate.now();
        jdbc.update("DELETE FROM dm_dashboard_snapshot WHERE snapshot_date = ?", date);
        jdbc.update("INSERT INTO dm_dashboard_snapshot (tenant_id, snapshot_date, metric_code, metric_value) SELECT tenant_id, ?, 'PROJECT_TOTAL', COUNT(*) FROM pm_project WHERE deleted = 0 GROUP BY tenant_id", date);
        jdbc.update("INSERT INTO dm_dashboard_snapshot (tenant_id, snapshot_date, metric_code, metric_value) SELECT tenant_id, ?, 'COMPONENT_TOTAL', COUNT(*) FROM dm_component WHERE deleted = 0 GROUP BY tenant_id", date);
        jdbc.update("INSERT INTO dm_dashboard_snapshot (tenant_id, snapshot_date, project_id, metric_code, metric_value) SELECT tenant_id, ?, project_id, 'ASSET_TOTAL', COUNT(*) FROM dm_asset WHERE deleted = 0 GROUP BY tenant_id, project_id", date);
    }
}

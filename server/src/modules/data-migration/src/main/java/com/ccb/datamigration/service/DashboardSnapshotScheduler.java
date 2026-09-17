package com.ccb.datamigration.service;

import java.time.LocalDate;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class DashboardSnapshotScheduler {
    private final DashboardSnapshotMapper mapper;

    public DashboardSnapshotScheduler(DashboardSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void captureDailySnapshots() {
        LocalDate date = LocalDate.now();
        mapper.deleteByDate(date);
        mapper.insertProjectTotals(date);
        mapper.insertComponentTotals(date);
        mapper.insertAssetTotals(date);
    }
}

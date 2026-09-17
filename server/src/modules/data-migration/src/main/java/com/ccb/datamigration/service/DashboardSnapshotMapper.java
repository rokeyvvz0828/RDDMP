package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface DashboardSnapshotMapper {
    int deleteByDate(@Param("date") LocalDate date);
    int insertProjectTotals(@Param("date") LocalDate date);
    int insertComponentTotals(@Param("date") LocalDate date);
    int insertAssetTotals(@Param("date") LocalDate date);
}

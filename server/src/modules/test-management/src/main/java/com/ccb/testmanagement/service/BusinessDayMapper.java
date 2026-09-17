package com.ccb.testmanagement.service;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
interface BusinessDayMapper {
    Long environmentCount(Map<String,Object> p); List<Map<String,Object>> environmentPage(Map<String,Object> p); List<Map<String,Object>> activeEnvironments(Map<String,Object> p); Map<String,Object> environment(Map<String,Object> p);
    int insertEnvironment(Map<String,Object> p); int updateEnvironment(Map<String,Object> p); int renameSchedules(Map<String,Object> p); int renameRequirements(Map<String,Object> p); Long scheduleReferenceCount(Map<String,Object> p); Long requirementReferenceCount(Map<String,Object> p); int deleteEnvironment(Map<String,Object> p); Long uniqueEnvironmentCount(Map<String,Object> p); Long activeEnvironmentCount(Map<String,Object> p);
    Long scheduleCount(Map<String,Object> p); List<Map<String,Object>> schedulePage(Map<String,Object> p); List<Map<String,Object>> overview(Map<String,Object> p); Map<String,Object> schedule(Map<String,Object> p); List<Map<String,Object>> scheduleByNaturalKey(Map<String,Object> p); int insertSchedule(Map<String,Object> p); int updateSchedule(Map<String,Object> p); int deleteSchedule(Map<String,Object> p); Long scheduleExists(Map<String,Object> p);
    Long requirementCount(Map<String,Object> p); List<Map<String,Object>> requirementPage(Map<String,Object> p); Map<String,Object> requirement(Map<String,Object> p); int insertRequirement(Map<String,Object> p); int updateRequirement(Map<String,Object> p); int reviewRequirement(Map<String,Object> p); int deleteRequirement(Map<String,Object> p); Long requirementExists(Map<String,Object> p);
    int insertAudit(Map<String,Object> p);
}

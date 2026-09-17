package com.ccb.testmanagement.plan;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
interface TestPlanMapper {
    Map<String, Object> project(Map<String, Object> p); List<Map<String, Object>> systems(Map<String, Object> p); List<Map<String, Object>> specials(Map<String, Object> p);
    Long planCount(Map<String, Object> p); List<Map<String, Object>> plans(Map<String, Object> p); List<Map<String, Object>> versions(Map<String, Object> p);
    Long specialNameCount(Map<String, Object> p); int insertSpecial(Map<String, Object> p); Map<String, Object> special(Map<String, Object> p); int updateSpecial(Map<String, Object> p); Long specialPlanCount(Map<String, Object> p); int deleteSpecial(Map<String, Object> p);
    List<Map<String, Object>> matchingPlans(Map<String, Object> p); int insertPlan(Map<String, Object> p); Map<String, Object> plan(Map<String, Object> p); Long participatingSystemCount(Map<String, Object> p); Long specialCount(Map<String, Object> p);
    Integer nextVersion(Map<String, Object> p); int insertVersion(Map<String, Object> p); int touchPlan(Map<String, Object> p); Map<String, Object> version(Map<String, Object> p); int deletePlan(Map<String, Object> p);
    Long projectCount(Map<String, Object> p); int insertAudit(Map<String, Object> p);
}

package com.ccb.testmanagement.plan;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
class TestPlanRepository {
    private final TestPlanMapper mapper;
    TestPlanRepository(TestPlanMapper mapper) { this.mapper = mapper; }
    Map<String,Object> project(Map<String,Object> p){return mapper.project(p);} List<Map<String,Object>> systems(Map<String,Object> p){return mapper.systems(p);} List<Map<String,Object>> specials(Map<String,Object> p){return mapper.specials(p);}
    long planCount(Map<String,Object> p){return zero(mapper.planCount(p));} List<Map<String,Object>> plans(Map<String,Object> p){return mapper.plans(p);} List<Map<String,Object>> versions(Map<String,Object> p){return mapper.versions(p);}
    long specialNameCount(Map<String,Object> p){return zero(mapper.specialNameCount(p));} void insertSpecial(Map<String,Object> p){mapper.insertSpecial(p);} Map<String,Object> special(Map<String,Object> p){return mapper.special(p);} void updateSpecial(Map<String,Object> p){mapper.updateSpecial(p);} long specialPlanCount(Map<String,Object> p){return zero(mapper.specialPlanCount(p));} void deleteSpecial(Map<String,Object> p){mapper.deleteSpecial(p);}
    List<Map<String,Object>> matchingPlans(Map<String,Object> p){return mapper.matchingPlans(p);} void insertPlan(Map<String,Object> p){mapper.insertPlan(p);} Map<String,Object> plan(Map<String,Object> p){return mapper.plan(p);} boolean participatingSystem(Map<String,Object> p){return zero(mapper.participatingSystemCount(p))>0;} boolean specialExists(Map<String,Object> p){return zero(mapper.specialCount(p))>0;}
    int nextVersion(Map<String,Object> p){Integer value=mapper.nextVersion(p);return value==null?1:value;} void insertVersion(Map<String,Object> p){mapper.insertVersion(p);} void touchPlan(Map<String,Object> p){mapper.touchPlan(p);} Map<String,Object> version(Map<String,Object> p){return mapper.version(p);} void deletePlan(Map<String,Object> p){mapper.deletePlan(p);}
    boolean projectExists(Map<String,Object> p){return zero(mapper.projectCount(p))>0;} void insertAudit(Map<String,Object> p){mapper.insertAudit(p);} private static long zero(Long value){return value==null?0:value;}
}

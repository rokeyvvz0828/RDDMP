package com.ccb.requirement.service;

import com.ccb.requirement.support.StubJdbcTemplate;
import java.util.List;
import java.util.Map;

/** Test-only Mapper adapter: production services exercise Repository/Mapper boundaries without JDBC constructors. */
final class RequirementSqlMigrationTestMapper implements RequirementDifferenceMapper, RequirementLegacyMapper {
    private final StubJdbcTemplate jdbc;
    RequirementSqlMigrationTestMapper(StubJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private List<Map<String,Object>> rows(String sql) { return jdbc.queryForList(sql, new Object[0]); }
    private Map<String,Object> row(String sql) { return jdbc.queryForMap(sql, new Object[0]); }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class, new Object[0]); }
    private int write(String sql) { return jdbc.update(sql, new Object[0]); }
    @Override public long count(Map<String,Object> p){return count("SELECT COUNT(*) FROM req_difference");}
    @Override public List<Map<String,Object>> page(Map<String,Object> p){return rows("SELECT req_difference");}
    @Override public Map<String,Object> find(long t,long id){List<Map<String,Object>> r=rows("SELECT req_difference");return r.isEmpty()?Map.of():r.get(0);}
    @Override public int insert(Map<String,Object> p){return write("INSERT INTO `req_difference`");}
    @Override public int update(Map<String,Object> p){return write("UPDATE req_difference");}
    @Override public int softDelete(long t,long id,long u){return write("UPDATE req_difference SET deleted = 1");}
    @Override public Map<String,Object> project(long t,long id){return row("SELECT req_project");}
    @Override public int submitReview(long t,long id,String report,long instance,long u){return write("UPDATE req_difference SET review_status = '评审中'");}
    @Override public int cancelReview(long t,long id,String reason,long u){return write("UPDATE req_difference SET review_status = '待评审'");}
    @Override public Map<String,Object> activeUser(long t,long id){List<Map<String,Object>> r=rows("SELECT sys_user");return r.isEmpty()?Map.of():r.get(0);}
    @Override public int transfer(long t,long id,long u,String n,long by){return write("UPDATE req_difference SET current_handler_user_id");}
    @Override public int insertFlow(Map<String,Object> p){return write("INSERT INTO req_difference_flow_log");}
    @Override public List<Long> runningInstanceIds(long t,String key){return List.of();}
    @Override public int completePendingTasks(long t,long id){return write("UPDATE wf_task");}
    @Override public int terminateInstance(long t,long id){return write("UPDATE wf_instance");}
    @Override public int clearWorkflowInstance(long t,long id){return write("UPDATE req_difference SET workflow_instance_id = NULL");}
    @Override public List<Map<String,Object>> reviewers(long t){return rows("SELECT sys_user");}
    @Override public List<Map<String,Object>> userOptions(long t,String k){return rows("SELECT sys_user");}
    @Override public Long publishedDefinitionId(long t,String c){return count("SELECT wf_definition");}
    @Override public List<Map<String,Object>> approvalLogs(long t,long i){return rows("SELECT wf_task_action");}
    @Override public int systemCount(long t,long id){return (int)count("SELECT req_system");}
    @Override public Long maxSequence(long t,long p){return count("SELECT req_difference");}

    @Override public int insertLegacy(Map<String,Object> p){return write("INSERT INTO req_legacy_requirement");}
    @Override public int updateLegacy(Map<String,Object> p){return write("UPDATE req_legacy_requirement");}
    @Override public int softDeleteLegacy(long t,long id,long u){return write("UPDATE req_legacy_requirement SET deleted = 1");}
    @Override public int deleteSystemItems(long t,long r){return write("UPDATE req_legacy_system_item");} @Override public int deleteSystemMembers(long t,long r){return write("UPDATE req_legacy_system_member");} @Override public int deleteFlowLogs(long t,long r){return write("UPDATE req_flow_log");} @Override public int deleteLegacyMembers(long t,long r){return write("UPDATE req_legacy_member");} @Override public int deleteVersions(long t,long r){return write("UPDATE req_requirement_version");} @Override public int deleteWorkloads(long t,long r){return write("UPDATE req_workload");} @Override public int deleteSoftDocs(long t,long r){return write("UPDATE req_soft_doc");} @Override public int deleteCoordinations(long t,long r){return write("UPDATE req_coordination_item");}
    @Override public int transitionStage(Map<String,Object> p){return write("UPDATE req_legacy_requirement SET propose_stage_status = '进行中', workflow_instance_id = NULL");} @Override public int insertStageLog(Map<String,Object> p){return write("INSERT INTO req_stage_log");} @Override public List<Map<String,Object>> stageLogs(long t,long r){return rows("SELECT req_stage_log");}
    @Override public List<Map<String,Object>> members(long t,long r){return rows("SELECT req_legacy_member");} @Override public int userCount(long t,long u){return 1;} @Override public int legacyMemberCount(long t,long r,long u){return 0;} @Override public String userName(long t,long u){return null;} @Override public int insertMember(Map<String,Object> p){return write("INSERT INTO req_legacy_member");} @Override public Map<String,Object> member(long t,long i){return row("SELECT req_legacy_member");} @Override public int deleteMember(long t,long i){return write("UPDATE req_legacy_member");}
    @Override public List<Map<String,Object>> systemItems(long t,long r){return rows("SELECT req_legacy_system_item");} @Override public List<Map<String,Object>> systemItemsForRequirements(long t,List<Long> ids){return rows("SELECT req_legacy_system_item");} @Override public List<Map<String,Object>> systemMembersForItems(long t,List<Long> ids){return rows("SELECT req_legacy_system_member");} @Override public int insertSystemItem(Map<String,Object> p){return write("INSERT INTO req_legacy_system_item");} @Override public int upsertSystemMember(Map<String,Object> p){return write("INSERT INTO req_legacy_system_member");}
    @Override public List<Map<String,Object>> flowLogs(long t,long r){return rows("SELECT req_flow_log");} @Override public int updateFlow(long t,long i,Long u,String n,long by){return write("UPDATE req_legacy_requirement SET current_flow_user_id");} @Override public Map<String,Object> latestSender(long t,long r){return Map.of();} @Override public int insertFlowLog(Map<String,Object> p){return write("INSERT INTO req_flow_log");} @Override public List<Map<String,Object>> versions(long t,long r){return rows("SELECT req_requirement_version");} @Override public int updateVersion(long t,long i,String v,long by){return write("UPDATE req_legacy_requirement SET version_no");} @Override public int upsertVersion(Map<String,Object> p){return write("INSERT INTO req_requirement_version");} @Override public int exists(long t,long i){return 1;}
}

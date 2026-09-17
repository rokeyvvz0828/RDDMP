package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RequirementDifferenceRepository {
    private final RequirementDifferenceMapper mapper;
    public RequirementDifferenceRepository(RequirementDifferenceMapper mapper) { this.mapper = mapper; }
    public long count(Map<String, Object> p) { return mapper.count(p); }
    public List<Map<String, Object>> page(Map<String, Object> p) { return mapper.page(p); }
    public Map<String, Object> find(long tenantId, long id) { return mapper.find(tenantId, id); }
    public void insert(Map<String, Object> p) { mapper.insert(p); }
    public void update(Map<String, Object> p) { mapper.update(p); }
    public void softDelete(long tenantId, long id, long operatorId) { mapper.softDelete(tenantId, id, operatorId); }
    public Map<String, Object> project(long tenantId, long projectId) { return mapper.project(tenantId, projectId); }
    public void submitReview(long tenantId,long id,String report,long instanceId,long operatorId){mapper.submitReview(tenantId,id,report,instanceId,operatorId);}
    public void cancelReview(long tenantId,long id,String reason,long operatorId){mapper.cancelReview(tenantId,id,reason,operatorId);}
    public Map<String,Object> activeUser(long tenantId,long userId){return mapper.activeUser(tenantId,userId);}
    public void transfer(long tenantId,long id,long userId,String name,long operatorId){mapper.transfer(tenantId,id,userId,name,operatorId);}
    public void insertFlow(Map<String,Object> p){mapper.insertFlow(p);}
    public List<Long> runningInstanceIds(long tenantId,String key){return mapper.runningInstanceIds(tenantId,key);}
    public void completePendingTasks(long tenantId,long instanceId){mapper.completePendingTasks(tenantId,instanceId);}
    public void terminateInstance(long tenantId,long instanceId){mapper.terminateInstance(tenantId,instanceId);}
    public void clearWorkflowInstance(long tenantId,long id){mapper.clearWorkflowInstance(tenantId,id);}
    public List<Map<String,Object>> reviewers(long tenantId){return mapper.reviewers(tenantId);}
    public List<Map<String,Object>> userOptions(long tenantId,String keyword){return mapper.userOptions(tenantId,keyword);}
    public Long publishedDefinitionId(long tenantId,String code){return mapper.publishedDefinitionId(tenantId,code);}
    public List<Map<String,Object>> approvalLogs(long tenantId,long instanceId){return mapper.approvalLogs(tenantId,instanceId);}
    public int systemCount(long tenantId,long systemId){return mapper.systemCount(tenantId,systemId);}
    public Long maxSequence(long tenantId,long projectId){return mapper.maxSequence(tenantId,projectId);}
}

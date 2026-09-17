package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class RequirementProjectRepository {
    private final RequirementProjectMapper mapper;
    public RequirementProjectRepository(RequirementProjectMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> list(long tenantId, String keyword) { return mapper.list(tenantId, keyword); }
    public Map<String,Object> find(long tenantId, long id) { return mapper.findProject(tenantId, id); }
    public boolean duplicateProjectCode(long tenantId, String code) { return mapper.projectCodeCount(tenantId, code) > 0; }
    public boolean hasDifferences(long tenantId, long projectId) { return mapper.differenceCount(tenantId, projectId) > 0; }
    public void insertProject(Map<String,Object> p) { mapper.insertProject(p); }
    public void updateProject(Map<String,Object> p) { mapper.updateProject(p); }
    public void deleteProject(long tenantId, long id, long operatorId) { mapper.softDeleteProject(tenantId,id,operatorId); }
    public List<Map<String,Object>> members(long tenantId,long projectId) { return mapper.members(tenantId,projectId); }
    public boolean userExists(long tenantId,long userId) { return mapper.userCount(tenantId,userId)>0; }
    public boolean memberExists(long tenantId,long projectId,long userId) { return mapper.memberCount(tenantId,projectId,userId)>0; }
    public void insertMember(Map<String,Object> p) { mapper.insertMember(p); }
    public Map<String,Object> findMember(long tenantId,long id) { return mapper.findMember(tenantId,id); }
    public void deleteMember(long tenantId,long id) { mapper.softDeleteMember(tenantId,id); }
}

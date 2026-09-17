package com.ccb.architecture.repository;

import com.ccb.architecture.model.PhysicalSubsystem;
import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ArchitectureSubsystemRepository {
    private final ArchitectureSubsystemMapper mapper;

    public ArchitectureSubsystemRepository(ArchitectureSubsystemMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<PhysicalSubsystem> pagePhysical(long tenantId, PageQuery page, PhysicalSubsystemQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        PhysicalSubsystemQuery normalizedQuery = query == null ? PhysicalSubsystemQuery.empty() : query;
        Map<String,Object> p=params("tenantId",tenantId,"code",like(normalizedQuery.code()),"shortName",like(normalizedQuery.shortName()),"name",like(normalizedQuery.name()),"logicalSubsystemName",like(normalizedQuery.logicalSubsystemName()),"businessComponentCode",normalizedQuery.businessComponentCode(),"businessGroupName",like(normalizedQuery.businessGroupName()),"responsibleTeamOrgId",normalizedQuery.responsibleTeamOrgId(),"status",normalizedQuery.status(),"size",normalizedPage.size(),"offset",(normalizedPage.page()-1)*normalizedPage.size()); Long total=mapper.countPhysical(p); return new PageResult<>(mapper.physicals(p).stream().map(this::physical).toList(),total==null?0:total,normalizedPage.page(),normalizedPage.size());
    }

    public Optional<PhysicalSubsystem> findPhysical(long tenantId, long id) {
        return Optional.ofNullable(mapper.physical(params("tenantId",tenantId,"id",id))).map(this::physical);
    }

    public boolean physicalCodeExists(long tenantId, String code, Long excludeId) {
        Long count=mapper.countByCode(params("tenantId",tenantId,"value",code,"excludeId",excludeId));return count!=null&&count>0;
    }

    public boolean physicalNameExists(long tenantId, String name, Long excludeId) {
        Long count=mapper.countByName(params("tenantId",tenantId,"value",name,"excludeId",excludeId));return count!=null&&count>0;
    }

    public void insertPhysical(long id, long tenantId, PhysicalSubsystemCommand command,
                               String responsibleTeamNameSnapshot, long actorId) {
        mapper.insertPhysical(commandParams(id,tenantId,command,responsibleTeamNameSnapshot,actorId));
    }

    public int updatePhysical(long tenantId, long id, PhysicalSubsystemCommand command,
                              String responsibleTeamNameSnapshot, long actorId) {
        return mapper.updatePhysical(commandParams(id,tenantId,command,responsibleTeamNameSnapshot,actorId));
    }

    public int softDeletePhysical(long tenantId, long id, long actorId) {
        return mapper.softDeletePhysical(params("actorId",actorId,"tenantId",tenantId,"id",id));
    }

    private PhysicalSubsystem physical(Map<String,Object> r){Object owner=r.get("owner_user_id"),created=r.get("created_at"),updated=r.get("updated_at");return new PhysicalSubsystem(n(r,"id"),s(r,"code"),s(r,"short_name"),s(r,"name"),s(r,"logical_subsystem_name"),s(r,"business_component_code"),s(r,"business_group_name"),s(r,"deployment_platform"),s(r,"disaster_recovery_mode"),n(r,"responsible_team_org_id"),s(r,"responsible_team_name_snapshot"),s(r,"runtime_code"),s(r,"system_level_code"),s(r,"development_framework_code"),owner instanceof Number x?x.longValue():null,s(r,"description"),s(r,"remark"),n(r,"created_by"),n(r,"updated_by"),created instanceof Timestamp x?x.toLocalDateTime():null,updated instanceof Timestamp x?x.toLocalDateTime():null,s(r,"english_name"),s(r,"status"),n(r,"row_version"));} private long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}private String s(Map<String,Object>r,String k){return r.get(k)==null?null:String.valueOf(r.get(k));} private String like(String v){return v==null||v.isBlank()?null:"%"+v.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";} private Map<String,Object> commandParams(long id,long t,PhysicalSubsystemCommand c,String team,long actor){Map<String,Object>p=params("id",id,"tenantId",t,"code",c.code(),"shortName",c.shortName(),"name",c.name(),"logicalSubsystemName",c.logicalSubsystemName(),"businessComponentCode",c.businessComponentCode(),"businessGroupName",c.businessGroupName(),"deploymentPlatform",c.deploymentPlatform(),"disasterRecoveryMode",c.disasterRecoveryMode(),"responsibleTeamOrgId",c.responsibleTeamOrgId(),"responsibleTeamNameSnapshot",team,"runtimeCode",c.runtimeCode(),"systemLevelCode",c.systemLevelCode(),"developmentFrameworkCode",c.developmentFrameworkCode(),"ownerUserId",c.ownerUserId(),"description",c.description(),"remark",c.remark(),"actorId",actor);return p;} private Map<String,Object> params(Object...e){Map<String,Object>p=new LinkedHashMap<>();for(int i=0;i<e.length;i+=2)p.put(String.valueOf(e[i]),e[i+1]);return p;}
}

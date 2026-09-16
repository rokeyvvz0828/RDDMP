package com.ccb.development.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.development.config.DevelopmentSettings;
import com.ccb.development.integration.DevelopmentSourceDirectory;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Service
public class DevelopmentSourceResolver {
    private final DevelopmentSourceDirectory sources;
    private final DevelopmentSettings settings;
    public DevelopmentSourceResolver(DevelopmentSourceDirectory sources,DevelopmentSettings settings){this.sources=sources;this.settings=settings;}
    public record ResolvedSource(long id,String number,String revision,List<String> roles,List<String> systemCodes){}

    public ResolvedSource resolve(AuthUser actor,CreateTask request){
        if(request.sourceMode()==SourceMode.STANDALONE){
            if(request.sourceRequirementId()!=null||request.sourceRevision()!=null&&!request.sourceRevision().isBlank())throw bad("自主任务不应提交关联需求");
            return null;
        }
        if(request.sourceRequirementId()==null||request.sourceRequirementId()<=0)throw bad("请选择来源需求");
        var result=current(actor,request.projectRef(),request.sourceRequirementId(),request.systemId());
        if(!result.revision().equals(request.sourceRevision()))throw conflict("来源已修改，请刷新待承接清单");
        return result;
    }
    public ResolvedSource current(AuthUser actor,String projectRef,long sourceId,long systemId){
        var source=sources.requireCurrent(actor,projectRef,sourceId);
        if(!source.active())throw conflict("需求来源已失效");
        return forSystem(source,systemId,mappings(actor));
    }
    public ResolvedSource forSystem(DevelopmentSourceDirectory.SourceRequirement source,long systemId,Map<String,Long> mappings){
        Set<String> codes=new TreeSet<>();Set<String> roles=new TreeSet<>();
        for(var system:source.systems())if(Long.valueOf(systemId).equals(mappings.get(system.systemCode()))){
            codes.add(system.systemCode());system.roles().forEach(role->roles.add(role.name()));
        }
        if(codes.isEmpty()||roles.isEmpty())throw conflict("来源系统没有明确映射到所选物理系统");
        return new ResolvedSource(source.id(),source.number(),source.revision(),List.copyOf(roles),List.copyOf(codes));
    }
    public Map<String,Long> mappings(AuthUser actor){return settings.systemMappings(actor);}
    public PageResult<DevelopmentSourceDirectory.SourceRequirement> search(AuthUser actor,String projectRef,Set<String> codes,String keyword,PageQuery page){
        return sources.search(actor,new DevelopmentSourceDirectory.SourceQuery(projectRef,codes,keyword,page));
    }
}

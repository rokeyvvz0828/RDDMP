package com.ccb.development.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.development.integration.DevelopmentSystemDirectory;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.repository.DevelopmentChangeRepository;
import com.ccb.development.repository.DevelopmentTaskRepository;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Service
public class DevelopmentTaskService {
    private final DevelopmentTaskRepository tasks;
    private final DevelopmentAccessPolicy access;
    private final DevelopmentSourceResolver sources;
    private final DevelopmentNumberService numbers;
    private final DevelopmentChangeRepository changes;
    private final ObjectMapper json;

    public DevelopmentTaskService(DevelopmentTaskRepository tasks,DevelopmentAccessPolicy access,DevelopmentSourceResolver sources,
                                   DevelopmentNumberService numbers,DevelopmentChangeRepository changes,ObjectMapper json){
        this.tasks=tasks;this.access=access;this.sources=sources;this.numbers=numbers;this.changes=changes;this.json=json;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public TaskView create(AuthUser actor,CreateTask input){
        access.permission(actor,"development:task:create");
        var request=normalize(input);
        var project=access.project(actor,request.projectRef());
        var system=access.system(actor,project.id(),request.systemId());
        access.systemManager(actor,system);
        long ownerId=request.ownerId()==null?system.ownerId():request.ownerId();
        access.user(actor,ownerId,true);
        String hash=digest(request);
        // 同租户创建先取得短事务锁，避免幂等请求的不存在记录产生并发间隙锁死锁。
        numbers.lockCreates(actor);
        var existing=tasks.findRequest(actor,request.requestId(),true);
        if(existing.isPresent()){
            if(!existing.get().requestHash().equals(hash))throw conflict("同一请求标识不能用于不同内容");
            access.read(actor,existing.get());return view(actor,existing.get());
        }
        var source=sources.resolve(actor,request);
        if(source!=null&&tasks.claimed(actor.tenantId(),source.id(),request.systemId()))throw conflict("该需求系统已承接开发任务");
        try{
            String number=numbers.next(actor,source);
            long id=tasks.insert(actor,project.id(),request,number,hash,ownerId,source==null?null:source.number(),source==null?null:source.revision(),
                    source==null?List.of():source.roles(),source==null?List.of():source.systemCodes());
            if(source!=null)tasks.bindSource(actor.tenantId(),source.id(),system.id(),id,source.systemCodes());
            var result=view(actor,tasks.require(actor.tenantId(),id,false));
            changes.record(actor,id,"TASK",id,"CREATE",null,result);
            return result;
        }catch(DuplicateKeyException error){throw conflict("任务编号、来源绑定或请求标识已存在，请刷新后重试");}
    }

    @Transactional
    public TaskView update(AuthUser actor,long id,UpdateTask request){
        var task=tasks.require(actor.tenantId(),id,true);
        access.manage(actor,task,"development:task:update");access.editable(task);
        required(request.title(),200,"名称");optional(request.description(),8000,"内容");dates(request.developmentPlanStart(),request.developmentPlanEnd());dates(request.testPlanStart(),request.testPlanEnd());
        if(request.rowVersion()!=task.rowVersion())throw conflict("任务已被其他人修改，请刷新后重试");
        long ownerId=request.ownerId()==null?task.ownerId():request.ownerId();access.user(actor,ownerId,true);
        var before=view(actor,task);
        tasks.update(actor,id,request,ownerId);
        var after=view(actor,tasks.require(actor.tenantId(),id,false));
        changes.record(actor,id,"TASK",id,"UPDATE",before,after);
        return after;
    }

    @Transactional(readOnly=true)
    public TaskView detail(AuthUser actor,long id){var task=tasks.require(actor.tenantId(),id,false);access.read(actor,task);return view(actor,task);}

    @Transactional(readOnly=true)
    public PageResult<TaskView> list(AuthUser actor,TaskQuery query){
        access.permission(actor,"development:task:read");var project=access.project(actor,query.projectRef());
        if(query.status()!=null&&!query.status().isBlank())try{TaskStatus.valueOf(query.status());}catch(IllegalArgumentException error){throw bad("任务状态无效");}
        var result=tasks.list(actor.tenantId(),project.id(),query,access.visibility(actor,project.id()));
        return new PageResult<>(result.records().stream().map(t->view(actor,t)).toList(),result.total(),result.page(),result.size());
    }

    @Transactional(readOnly=true)
    public PageResult<PendingRequirement> pending(AuthUser actor,String projectRef,Long systemId,String keyword,PageQuery page){
        access.permission(actor,"development:task:read");var project=access.project(actor,projectRef);
        Map<Long,DevelopmentSystemDirectory.SystemRef> systems=access.manageableSystems(actor,project.id()).stream()
                .filter(s->systemId==null||s.id()==systemId).collect(Collectors.toMap(DevelopmentSystemDirectory.SystemRef::id,s->s));
        if(systems.isEmpty())return new PageResult<>(List.of(),0,page.page(),page.size());
        var mappings=sources.mappings(actor);
        Set<String> codes=mappings.entrySet().stream().filter(e->systems.containsKey(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet());
        if(codes.isEmpty())throw conflict("尚未维护当前系统的需求映射，请在参数管理中配置");
        List<PendingRequirement> records=new ArrayList<>();long total=0,offset=Math.multiplyExact(page.page()-1,page.size());
        // 来源接口有界读取，待承接计数在同一授权集合中排除已承接组合；不向浏览器拉取全量来源。
        for(long sourcePage=1;;sourcePage++){
            var result=sources.search(actor,projectRef,codes,keyword,new PageQuery(sourcePage,100));
            for(var source:result.records()){
                Set<Long> sourceSystems=new java.util.TreeSet<>();
                source.systems().forEach(s->{Long target=mappings.get(s.systemCode());if(target!=null&&systems.containsKey(target))sourceSystems.add(target);});
                for(long target:sourceSystems)if(!tasks.claimed(actor.tenantId(),source.id(),target)){
                    var resolved=sources.forSystem(source,target,mappings);
                    tasks.requireStableBinding(actor.tenantId(),source.id(),target,resolved.systemCodes());
                    if(total>=offset&&records.size()<page.size())records.add(new PendingRequirement(Long.toString(source.id()),source.number(),source.name(),source.summary(),
                            source.revision(),access.systemView(systems.get(target)),resolved.roles()));
                    total++;
                }
            }
            if(sourcePage*100>=result.total()||result.records().isEmpty())break;
        }
        return new PageResult<>(List.copyOf(records),total,page.page(),page.size());
    }

    public PageResult<ChangeView> changes(AuthUser actor,long taskId,PageQuery page){
        var task=tasks.require(actor.tenantId(),taskId,false);access.read(actor,task);
        return changes.list(actor,taskId,page,access.manages(actor,task));
    }
    public PageResult<SystemView> systems(AuthUser actor,String projectRef,PageQuery page,String keyword){return access.systemOptions(actor,projectRef,page,keyword);}
    public PageResult<UserView> users(AuthUser actor,String projectRef,PageQuery page,String keyword){return access.userOptions(actor,projectRef,page,keyword);}

    public TaskView view(AuthUser actor,TaskEntity task){
        boolean manager=access.manages(actor,task);List<String> actions=new ArrayList<>();
        if(manager){
            if(access.has(actor,"development:task:update")){
                if(task.status()==TaskStatus.NOT_STARTED||task.status()==TaskStatus.IN_PROGRESS){actions.add("UPDATE");actions.add("CANCEL");if(task.status()==TaskStatus.NOT_STARTED)actions.add("START");}
                if(task.status()==TaskStatus.COMPLETED)actions.add("REOPEN");if(task.status()==TaskStatus.CANCELLED)actions.add("RESTORE");
            }
            if((task.status()==TaskStatus.NOT_STARTED||task.status()==TaskStatus.IN_PROGRESS)&&access.has(actor,"development:task:complete"))actions.add("COMPLETE");
            if((task.status()==TaskStatus.NOT_STARTED||task.status()==TaskStatus.IN_PROGRESS)&&access.has(actor,"development:work-item:update"))actions.add("CREATE_WORK_ITEM");
        }
        return new TaskView(Long.toString(task.id()),task.number(),task.projectRef(),task.title(),task.description(),task.sourceMode(),
                task.sourceRequirementId()==null?null:new SourceView(task.sourceRequirementId().toString(),task.sourceNumber(),task.sourceRevision(),task.sourceRoles(),task.sourceSystemCodes()),
                access.systemView(access.system(actor,task.projectId(),task.systemId())),access.user(actor,task.ownerId(),false),task.status(),task.developmentPlanStart(),task.developmentPlanEnd(),
                task.testPlanStart(),task.testPlanEnd(),tasks.workItemCount(actor.tenantId(),task.id(),false,manager?null:actor.id()),
                tasks.workItemCount(actor.tenantId(),task.id(),true,manager?null:actor.id()),List.copyOf(actions),task.rowVersion(),task.createdAt(),task.updatedAt());
    }

    private CreateTask normalize(CreateTask r){
        if(r.sourceMode()==null||r.systemId()==null||r.systemId()<=0)throw bad("来源模式和物理系统不能为空");
        dates(r.developmentPlanStart(),r.developmentPlanEnd());dates(r.testPlanStart(),r.testPlanEnd());
        return new CreateTask(required(r.projectRef(),128,"项目"),r.sourceMode(),r.sourceRequirementId(),r.sourceRevision(),r.systemId(),required(r.title(),200,"名称"),
                optional(r.description(),8000,"内容"),r.ownerId(),r.developmentPlanStart(),r.developmentPlanEnd(),r.testPlanStart(),r.testPlanEnd(),required(r.requestId(),64,"请求标识"));
    }
    private String digest(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}catch(Exception ex){throw new IllegalStateException("无法生成请求摘要",ex);}}
}

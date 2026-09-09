package com.ccb.development.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.integration.DevelopmentSystemDirectory;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.repository.DevelopmentTaskRepository;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Service
public class DevelopmentAccessPolicy {
    private final DevelopmentTaskRepository tasks;
    private final DevelopmentSystemDirectory systems;
    private final SystemReferenceQuery references;
    private final ProjectAccessService projects;

    public DevelopmentAccessPolicy(DevelopmentTaskRepository tasks,DevelopmentSystemDirectory systems,
                                   SystemReferenceQuery references,ProjectAccessService projects){
        this.tasks=tasks;this.systems=systems;this.references=references;this.projects=projects;
    }

    public boolean has(AuthUser actor,String permission){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return actor!=null&&actor.enabled()&&auth!=null&&auth.isAuthenticated()&&auth.getPrincipal() instanceof AuthUser principal
                &&principal.id()==actor.id()&&principal.tenantId()==actor.tenantId()
                &&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission)||a.getAuthority().equals("development:admin"));
    }
    public void permission(AuthUser actor,String permission){if(!has(actor,permission))throw new BusinessException(ErrorCode.FORBIDDEN,"无此操作权限");}
    public ProjectAccess project(AuthUser actor,String projectRef){return projects.requireAccessible(required(projectRef,128,"项目"),actor);}
    public DevelopmentSystemDirectory.SystemRef system(AuthUser actor,long projectId,long id){return systems.find(actor,projectId,id).orElseThrow(com.ccb.development.model.DevelopmentTaskModels::missing);}
    public void systemManager(AuthUser actor,DevelopmentSystemDirectory.SystemRef system){
        if(system.ownerId()==null)throw conflict("物理系统尚未维护负责人");
        if(!"ACTIVE".equals(system.status()))throw conflict("物理系统当前不可用");
        if(!has(actor,"development:admin")&&system.ownerId()!=actor.id())throw new BusinessException(ErrorCode.FORBIDDEN,"只能为负责的系统创建任务");
    }
    public UserView user(AuthUser actor,long id,boolean activeOnly){
        var user=references.findUser(actor,id,activeOnly).orElseThrow(()->conflict("所选人员不存在或已停用"));
        return new UserView(Long.toString(user.id()),user.displayName());
    }
    public SystemView systemView(DevelopmentSystemDirectory.SystemRef system){
        return new SystemView(Long.toString(system.id()),system.code(),system.name(),system.ownerId()==null?null:system.ownerId().toString(),system.status(),system.rowVersion());
    }
    public boolean manages(AuthUser actor,TaskEntity task){
        if(task.tenantId()!=actor.tenantId())return false;
        if(has(actor,"development:admin")||task.ownerId()==actor.id())return true;
        return Long.valueOf(actor.id()).equals(system(actor,task.projectId(),task.systemId()).ownerId());
    }
    public void read(AuthUser actor,TaskEntity task){
        permission(actor,"development:task:read");
        var project=project(actor,task.projectRef());
        if(project.id()!=task.projectId()||task.tenantId()!=actor.tenantId())throw missing();
        system(actor,task.projectId(),task.systemId());
        if(!manages(actor,task)&&!tasks.assigned(actor.tenantId(),task.id(),actor.id()))throw new BusinessException(ErrorCode.FORBIDDEN,"无此任务访问权限");
    }
    public void manage(AuthUser actor,TaskEntity task,String permission){
        read(actor,task);permission(actor,permission);
        if(!manages(actor,task))throw new BusinessException(ErrorCode.FORBIDDEN,"仅任务负责人或系统负责人可管理");
    }
    public void editable(TaskEntity task){if(task.status()==TaskStatus.COMPLETED||task.status()==TaskStatus.CANCELLED)throw conflict("请先重新打开或恢复任务");}
    public List<DevelopmentSystemDirectory.SystemRef> manageableSystems(AuthUser actor,long projectId){
        List<DevelopmentSystemDirectory.SystemRef> result=new ArrayList<>();
        for(long page=1;;page++){
            var current=systems.searchManageable(actor,projectId,new PageQuery(page,100),null);
            result.addAll(current.records());
            if(page*100>=current.total()||current.records().isEmpty())break;
        }
        return List.copyOf(result);
    }
    public Visibility visibility(AuthUser actor,long projectId){
        if(has(actor,"development:admin"))return new Visibility(true,actor.id(),List.of());
        return new Visibility(false,actor.id(),manageableSystems(actor,projectId).stream().map(DevelopmentSystemDirectory.SystemRef::id).toList());
    }
    public PageResult<SystemView> systemOptions(AuthUser actor,String projectRef,PageQuery page,String keyword){
        permission(actor,"development:task:read");var project=project(actor,projectRef);
        var result=systems.searchManageable(actor,project.id(),page,keyword);
        return new PageResult<>(result.records().stream().map(this::systemView).toList(),result.total(),result.page(),result.size());
    }
    public PageResult<UserView> userOptions(AuthUser actor,String projectRef,PageQuery page,String keyword){
        permission(actor,"development:task:read");project(actor,projectRef);
        var result=references.searchActiveUsers(actor,page,keyword);
        return new PageResult<>(result.records().stream().map(u->new UserView(Long.toString(u.id()),u.displayName())).toList(),result.total(),result.page(),result.size());
    }
}

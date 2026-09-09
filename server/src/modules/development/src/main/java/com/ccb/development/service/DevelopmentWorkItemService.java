package com.ccb.development.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.model.DevelopmentWorkItemModels.*;
import com.ccb.development.repository.DevelopmentChangeRepository;
import com.ccb.development.repository.DevelopmentTaskRepository;
import com.ccb.development.repository.DevelopmentWorkItemRepository;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Service
public class DevelopmentWorkItemService {
    private final DevelopmentWorkItemRepository items;
    private final DevelopmentTaskRepository tasks;
    private final DevelopmentAccessPolicy access;
    private final DevelopmentTaskService taskService;
    private final DevelopmentChangeRepository changes;
    private final DevelopmentCalendarService calendar;

    public DevelopmentWorkItemService(DevelopmentWorkItemRepository items,DevelopmentTaskRepository tasks,DevelopmentAccessPolicy access,
                                       DevelopmentTaskService taskService,DevelopmentChangeRepository changes,DevelopmentCalendarService calendar){
        this.items=items;this.tasks=tasks;this.access=access;this.taskService=taskService;this.changes=changes;this.calendar=calendar;
    }

    @Transactional
    public WorkItemView create(AuthUser actor,WorkItemWrite request){
        access.permission(actor,"development:work-item:update");validate(request);
        var task=tasks.require(actor.tenantId(),request.taskId(),true);access.manage(actor,task,"development:work-item:update");access.editable(task);
        long assignee=request.assigneeId()==null?task.ownerId():request.assigneeId();access.user(actor,assignee,true);
        long id=items.insert(actor,request,assignee);var result=view(actor,items.require(actor.tenantId(),id,false),task,taskService.view(actor,task));
        changes.record(actor,task.id(),"WORK_ITEM",id,"CREATE",null,result);return result;
    }

    @Transactional
    public WorkItemView update(AuthUser actor,long id,WorkItemWrite request){
        access.permission(actor,"development:work-item:update");validate(request);
        var initial=items.require(actor.tenantId(),id,false);
        var task=tasks.require(actor.tenantId(),initial.taskId(),true);access.read(actor,task);access.editable(task);
        var item=items.require(actor.tenantId(),id,true);boolean manager=access.manages(actor,task);
        requireItemReader(actor,item,manager);
        if(request.rowVersion()==null)throw bad("保存工作项必须携带行版本");
        if(request.rowVersion()!=item.rowVersion())throw conflict("工作项已被其他人修改，请刷新后重试");
        if(request.taskId()!=item.taskId())throw bad("不能改变工作项所属任务");
        long assignee=request.assigneeId()==null?item.assigneeId():request.assigneeId();
        if(!manager&&(assignee!=item.assigneeId()||!item.title().equals(request.title().trim())
                ||!item.description().equals(optional(request.description(),8000,"内容"))||!Objects.equals(item.plannedStart(),request.plannedStart())
                ||!Objects.equals(item.plannedEnd(),request.plannedEnd())))throw new BusinessException(ErrorCode.FORBIDDEN,"指定人员只能维护实际日期和阻塞信息");
        access.user(actor,assignee,true);
        var summary=taskService.view(actor,task);var before=view(actor,item,task,summary);
        items.update(actor,id,request,assignee);
        if(item.status()==WorkItemStatus.DONE&&(!Objects.equals(item.plannedStart(),request.plannedStart())||!Objects.equals(item.plannedEnd(),request.plannedEnd())
                ||!Objects.equals(item.actualStart(),request.actualStart())||!Objects.equals(item.actualEnd(),request.actualEnd()))){
            calendar.freeze(actor,task.id(),"WORK_ITEM",id,item.rowVersion()+1);
        }
        var result=view(actor,items.require(actor.tenantId(),id,false),task,summary);
        changes.record(actor,task.id(),"WORK_ITEM",id,"UPDATE",before,result);return result;
    }

    @Transactional
    public WorkItemView action(AuthUser actor,long id,WorkItemAction request){
        access.permission(actor,"development:task:read");
        var initial=items.require(actor.tenantId(),id,false);
        var task=tasks.require(actor.tenantId(),initial.taskId(),true);access.read(actor,task);access.editable(task);
        var item=items.require(actor.tenantId(),id,true);
        boolean manager=access.manages(actor,task);requireItemReader(actor,item,manager);
        if(request.rowVersion()!=item.rowVersion())throw conflict("工作项已被其他人修改，请刷新后重试");
        String action=required(request.action(),32,"动作");
        if(List.of("ACCEPT","RETURN","REOPEN").contains(action)){
            access.manage(actor,task,"development:work-item:accept");
            if(!"ACCEPT".equals(action))required(request.reason(),1000,"操作原因");
        }else{
            access.permission(actor,"development:work-item:update");
            if(item.assigneeId()!=actor.id())throw new BusinessException(ErrorCode.FORBIDDEN,"仅指定人员可以推进和提交执行");
        }
        var status=transition(item.status(),action);var summary=taskService.view(actor,task);var before=view(actor,item,task,summary);
        items.status(actor,item,status);
        if(status==WorkItemStatus.DONE)calendar.freeze(actor,task.id(),"WORK_ITEM",id,item.rowVersion()+1);
        var after=view(actor,items.require(actor.tenantId(),id,false),task,summary);
        changes.record(actor,task.id(),"WORK_ITEM",id,action,before,new ActionResult(after,optional(request.reason(),1000,"操作原因")));
        return after;
    }

    public static WorkItemStatus transition(WorkItemStatus status,String action){
        return switch(action){
            case "START" -> {if(status!=WorkItemStatus.TODO)throw conflict("仅待办工作项可以开始");yield WorkItemStatus.IN_PROGRESS;}
            case "SUBMIT" -> {if(status!=WorkItemStatus.IN_PROGRESS)throw conflict("仅进行中工作项可以提交验收");yield WorkItemStatus.IN_REVIEW;}
            case "ACCEPT" -> {if(status!=WorkItemStatus.IN_REVIEW)throw conflict("必须先提交负责人验收");yield WorkItemStatus.DONE;}
            case "RETURN" -> {if(status!=WorkItemStatus.IN_REVIEW)throw conflict("仅待验收工作项可以退回");yield WorkItemStatus.IN_PROGRESS;}
            case "REOPEN" -> {if(status!=WorkItemStatus.DONE)throw conflict("仅已完成工作项可以重开");yield WorkItemStatus.IN_PROGRESS;}
            default -> throw bad("工作项动作无效");
        };
    }

    @Transactional(readOnly=true)
    public WorkItemView detail(AuthUser actor,long id){
        access.permission(actor,"development:task:read");var item=items.require(actor.tenantId(),id,false);
        var task=tasks.require(actor.tenantId(),item.taskId(),false);access.read(actor,task);requireItemReader(actor,item,access.manages(actor,task));
        return view(actor,item,task,taskService.view(actor,task));
    }

    @Transactional(readOnly=true)
    public WorkItemPage list(AuthUser actor,WorkItemQuery query){
        access.permission(actor,"development:task:read");var project=access.project(actor,query.projectRef());dates(query.plannedFrom(),query.plannedTo());
        if(query.status()!=null&&!query.status().isBlank())try{WorkItemStatus.valueOf(query.status());}catch(IllegalArgumentException error){throw bad("工作项状态无效");}
        if(query.taskId()!=null){var task=tasks.require(actor.tenantId(),query.taskId(),false);access.read(actor,task);if(task.projectId()!=project.id())throw missing();}
        var result=items.list(actor.tenantId(),project.id(),query,access.visibility(actor,project.id()));
        var parents=new HashMap<Long,TaskEntity>();var summaries=new HashMap<Long,TaskView>();var rows=new ArrayList<WorkItemView>();
        for(var item:result.records()){
            var parent=parents.computeIfAbsent(item.taskId(),id->tasks.require(actor.tenantId(),id,false));
            var summary=summaries.computeIfAbsent(item.taskId(),id->taskService.view(actor,parent));
            rows.add(view(actor,item,parent,summary));
        }
        return new WorkItemPage(List.copyOf(rows),result.total(),result.page(),result.size(),result.counts());
    }

    private WorkItemView view(AuthUser actor,WorkItemEntity item,TaskEntity task,TaskView summary){
        List<String> actions=new ArrayList<>();boolean manager=access.manages(actor,task);
        if(task.status()!=TaskStatus.COMPLETED&&task.status()!=TaskStatus.CANCELLED){
            if(access.has(actor,"development:work-item:update")&&(manager||item.assigneeId()==actor.id())){
                actions.add("UPDATE");if(manager)actions.add("EDIT_PLAN");
                if(item.assigneeId()==actor.id()){
                    if(item.status()==WorkItemStatus.TODO)actions.add("START");if(item.status()==WorkItemStatus.IN_PROGRESS)actions.add("SUBMIT");
                }
            }
            if(manager&&access.has(actor,"development:work-item:accept")){
                if(item.status()==WorkItemStatus.IN_REVIEW){actions.add("ACCEPT");actions.add("RETURN");}if(item.status()==WorkItemStatus.DONE)actions.add("REOPEN");
            }
        }
        List<String> warnings=new ArrayList<>();
        LocalDate from=Stream.of(task.developmentPlanStart(),task.testPlanStart()).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate to=Stream.of(task.developmentPlanEnd(),task.testPlanEnd()).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        if(from!=null&&item.plannedStart()!=null&&item.plannedStart().isBefore(from)||to!=null&&item.plannedEnd()!=null&&item.plannedEnd().isAfter(to))warnings.add("工作项排期超出任务整体计划范围");
        return new WorkItemView(Long.toString(item.id()),Long.toString(task.id()),task.number(),task.title(),summary.system(),summary.source(),access.user(actor,item.assigneeId(),false),
                item.title(),item.description(),item.status(),item.plannedStart(),item.plannedEnd(),item.actualStart(),item.actualEnd(),item.blocked(),item.blockReason(),List.copyOf(actions),item.rowVersion(),List.copyOf(warnings),
                DevelopmentCalendarService.measure(item.plannedStart(),item.plannedEnd(),item.actualStart(),item.actualEnd(),calendar.forItem(actor,item)));
    }

    private static void requireItemReader(AuthUser actor,WorkItemEntity item,boolean manager){if(!manager&&item.assigneeId()!=actor.id())throw new BusinessException(ErrorCode.FORBIDDEN,"无此工作项权限");}
    private static void validate(WorkItemWrite r){
        if(r.taskId()==null||r.taskId()<=0)throw bad("所属任务不能为空");required(r.title(),200,"工作项名称");optional(r.description(),8000,"内容");
        dates(r.plannedStart(),r.plannedEnd());dates(r.actualStart(),r.actualEnd());optional(r.blockReason(),1000,"阻塞原因");
        if(r.blocked())required(r.blockReason(),1000,"阻塞原因");
    }
    private record ActionResult(WorkItemView workItem,String reason){}
}

package com.ccb.development.repository;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.model.DevelopmentWorkItemModels.*;
import com.ccb.development.model.DevelopmentTaskModels.TaskStatus;
import com.ccb.development.service.DevelopmentWorkItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class DevelopmentWorkItemMySqlTest extends DevelopmentTaskMySqlTest {
    private DevelopmentWorkItemService items;

    @BeforeEach
    void workItems(){items=new DevelopmentWorkItemService(new DevelopmentWorkItemRepository(fixture.jdbc),fixture.repository,
            fixture.access,fixture.tasks,fixture.changes,new com.ccb.development.service.DevelopmentCalendarService(fixture.settings,
                    new DevelopmentCalendarRepository(fixture.jdbc,fixture.json),fixture.json));}

    @Test
    void assignedPersonSubmitsAndManagerAcceptsWithoutAutomaticallyClosingParent(){
        var task=as(OWNER,()->fixture.create(OWNER,standalone(null)));
        long taskId=Long.parseLong(task.id());
        var item=as(OWNER,()->fixture.tx.execute(s->items.create(OWNER,write(taskId,10L,null))));
        assertEquals(WorkItemStatus.TODO,item.status());
        long id=Long.parseLong(item.id());
        var started=as(OTHER,()->fixture.tx.execute(s->items.action(OTHER,id,new WorkItemAction("START",0,null))));
        assertEquals(WorkItemStatus.IN_PROGRESS,started.status());
        as(OTHER,()->fixture.tx.execute(s->items.action(OTHER,id,new WorkItemAction("SUBMIT",1,null))));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->as(OTHER,
                ()->fixture.tx.execute(s->items.action(OTHER,id,new WorkItemAction("ACCEPT",2,null))))).code());
        var accepted=as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("ACCEPT",2,null))));
        assertEquals(WorkItemStatus.DONE,accepted.status());
        assertEquals(TaskStatus.NOT_STARTED,as(OWNER,()->fixture.tasks.detail(OWNER,taskId)).status());
        assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("REOPEN",3,null)))));
        as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("REOPEN",3,"补充执行"))));
        assertEquals(WorkItemStatus.IN_PROGRESS,as(OWNER,()->items.detail(OWNER,id)).status());
    }

    @Test
    void taskAndCrossTaskViewsShareScopedRecordsAndColumnCounts(){
        long taskId=Long.parseLong(as(OWNER,()->fixture.create(OWNER,standalone(null))).id());
        var mine=as(OWNER,()->fixture.tx.execute(s->items.create(OWNER,write(taskId,10L,null))));
        var ownerItem=as(OWNER,()->fixture.tx.execute(s->items.create(OWNER,write(taskId,null,null))));
        assertEquals("9",ownerItem.assignee().id());
        var cross=as(OTHER,()->items.list(OTHER,query(null)));
        var inner=as(OTHER,()->items.list(OTHER,query(taskId)));
        assertEquals(1,cross.total());assertEquals(1,cross.counts().get("TODO"));
        assertEquals(List.of(mine.id()),cross.records().stream().map(WorkItemView::id).toList());
        assertEquals(cross.records(),inner.records());
        assertThrows(BusinessException.class,()->as(OTHER,()->items.detail(OTHER,Long.parseLong(ownerItem.id()))));
        assertEquals(2,as(OWNER,()->items.list(OWNER,query(taskId))).total());
        var history=as(OTHER,()->fixture.tasks.changes(OTHER,taskId,new PageQuery(1,100)));
        assertEquals(1,history.records().stream().filter(event->event.objectType().equals("WORK_ITEM")).count());
        assertTrue(history.records().stream().filter(event->event.objectType().equals("WORK_ITEM")).allMatch(event->event.objectId().equals(mine.id())));
        assertFalse(history.records().stream().filter(event->event.objectType().equals("TASK")).anyMatch(event->event.after()!=null&&event.after().has("workItemCount")));
    }

    @Test
    void assignedPersonCannotChangeOwnershipAndBlockingIsIndependentFromStatus(){
        long taskId=Long.parseLong(as(OWNER,()->fixture.create(OWNER,standalone(null))).id());
        var item=as(OWNER,()->fixture.tx.execute(s->items.create(OWNER,write(taskId,10L,null))));
        long id=Long.parseLong(item.id());
        assertThrows(BusinessException.class,()->as(OTHER,()->fixture.tx.execute(s->items.update(OTHER,id,write(taskId,9L,0L)))));
        var blocked=new WorkItemWrite(taskId,"工作项","内容",10L,null,null,null,null,true,"等待接口",0L);
        var updated=as(OTHER,()->fixture.tx.execute(s->items.update(OTHER,id,blocked)));
        assertTrue(updated.blocked());assertEquals(WorkItemStatus.TODO,updated.status());
        assertEquals(ErrorCode.CONFLICT,assertThrows(BusinessException.class,()->as(OTHER,()->fixture.tx.execute(s->items.update(OTHER,id,blocked)))).code());
        as(OWNER,()->fixture.tx.execute(s->{fixture.repository.status(OWNER,fixture.repository.require(7,taskId,true),TaskStatus.COMPLETED);return null;}));
        assertThrows(BusinessException.class,()->as(OTHER,()->fixture.tx.execute(s->items.action(OTHER,id,new WorkItemAction("START",1,null)))));
    }

    @Test
    void concurrentAcceptanceOfSameVersionSucceedsOnlyOnce()throws Exception{
        long taskId=Long.parseLong(as(OWNER,()->fixture.create(OWNER,standalone(null))).id());
        long id=Long.parseLong(as(OWNER,()->fixture.tx.execute(s->items.create(OWNER,write(taskId,null,null)))).id());
        as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("START",0,null))));
        as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("SUBMIT",1,null))));
        var executor=Executors.newFixedThreadPool(2);
        try{
            java.util.concurrent.Callable<Boolean> accept=()->as(OWNER,()->{
                try{fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("ACCEPT",2,null)));return true;}
                catch(BusinessException error){assertEquals(ErrorCode.CONFLICT,error.code());return false;}
            });
            int accepted=0;for(var result:executor.invokeAll(List.of(accept,accept)))if(result.get())accepted++;
            assertEquals(1,accepted);
        }finally{executor.shutdownNow();}
    }

    @Test
    void acceptedWorkItemFreezesCalendarAndDateAmendmentCreatesNewSnapshot(){
        long taskId=Long.parseLong(as(OWNER,()->fixture.create(OWNER,standalone(null))).id());
        var from=java.time.LocalDate.of(2026,9,7);var to=java.time.LocalDate.of(2026,9,11);
        long id=Long.parseLong(as(OWNER,()->fixture.tx.execute(s->items.create(OWNER,
                new WorkItemWrite(taskId,"工作项","内容",null,from,to,from,to,false,"",null)))).id());
        as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("START",0,null))));
        as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("SUBMIT",1,null))));
        var accepted=as(OWNER,()->fixture.tx.execute(s->items.action(OWNER,id,new WorkItemAction("ACCEPT",2,null))));
        assertEquals(5,accepted.duration().plannedDays());
        org.mockito.Mockito.when(fixture.references.activeParameters(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("DEVELOPMENT_CALENDAR")))
                .thenReturn(List.of(new com.ccb.system.capability.SystemParameterReference("DEFAULT","{\"restDates\":[\"2026-09-08\"]}")));
        assertEquals(accepted.duration(),as(OWNER,()->items.detail(OWNER,id)).duration());
        var amended=as(OWNER,()->fixture.tx.execute(s->items.update(OWNER,id,
                new WorkItemWrite(taskId,"工作项","内容",null,from,to.minusDays(1),from,to,false,"",3L))));
        assertEquals(3,amended.duration().plannedDays());assertEquals(4,amended.duration().actualDays());
        assertEquals(new java.math.BigDecimal("33.33"),amended.duration().variancePercent());
        assertEquals(2L,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_calendar_snapshot WHERE object_type='WORK_ITEM' AND object_id=?",Long.class,id));
    }

    static WorkItemWrite write(long taskId,Long assigneeId,Long version){return new WorkItemWrite(taskId,"工作项","内容",assigneeId,null,null,null,null,false,"",version);}
    static WorkItemQuery query(Long taskId){return new WorkItemQuery("PROJECT-A",taskId,null,null,null,null,null,null,new PageQuery(1,20));}
}

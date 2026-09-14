package com.ccb.development.repository;

import com.ccb.attachment.integration.*;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.model.DevelopmentStageModels.*;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.service.*;
import com.ccb.system.capability.SystemParameterReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DevelopmentStageMySqlTest extends DevelopmentTaskMySqlTest {
    private DevelopmentStageService stages;
    private DevelopmentCalendarService calendar;
    private final Map<Long,AttachmentItem> files=new HashMap<>();

    @BeforeEach
    void stages(){
        var attachments=mock(AttachmentGateway.class);
        when(attachments.get(anyLong(),any())).thenAnswer(call->{
            var item=files.get((Long)call.getArgument(0));if(item==null)throw new BusinessException(ErrorCode.FORBIDDEN,"不可访问附件");return item;
        });
        doAnswer(call->{
            AttachmentBindingCommand binding=call.getArgument(0);var item=files.get(binding.attachmentId());
            if(item!=null&&"TEMP".equals(item.status()))files.put(item.id(),new AttachmentItem(item.id(),item.fileName(),item.contentType(),item.fileSize(),item.fileExtension(),
                    "BOUND",binding.businessType(),binding.businessKey(),binding.projectRef(),item.uploaderId(),item.createdAt()));return null;
        }).when(attachments).bind(any(),any());
        calendar=new DevelopmentCalendarService(fixture.settings,new DevelopmentCalendarRepository(fixture.jdbc,fixture.json),fixture.json);
        stages=new DevelopmentStageService(fixture.repository,new DevelopmentStageRepository(fixture.jdbc),fixture.access,fixture.tasks,
                new DevelopmentSourceResolver(fixture.sources,fixture.settings),calendar,fixture.changes,attachments);
    }

    @Test
    void developmentRequiresBoundCodeWalkButNeverRequiresTestReport(){
        long id=Long.parseLong(as(OWNER,()->fixture.create(OWNER,standalone(null))).id());
        assertEquals(ErrorCode.CONFLICT,assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",0,null))))).code());
        putFile(99,"TEMP",null);
        var saved=as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(0,List.of(99L),false))));
        assertTrue(saved.testReportAttachments().isEmpty());
        assertEquals("BOUND",files.get(99L).status());
        assertEquals(Long.toString(id),files.get(99L).businessKey());
        long version=as(OWNER,()->fixture.tasks.detail(OWNER,id)).rowVersion();
        var completed=as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",version,null))));
        assertEquals(TaskStatus.COMPLETED,completed.status());
        assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(saved.rowVersion(),List.of(99L),false)))));
    }

    @Test
    void pureTestCanCompleteWithoutReportsOrPlaceholderWorkItems(){
        long id=Long.parseLong(as(OWNER,()->fixture.create(OWNER,linked(43))).id());
        var saved=as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(0,List.of(),true))));
        assertTrue(saved.pureTest());assertTrue(saved.notApplicableDesign());assertTrue(saved.notApplicableImplementation());
        assertTrue(saved.codeWalkAttachments().isEmpty());assertTrue(saved.testReportAttachments().isEmpty());
        var result=as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",saved.taskRowVersion(),null))));
        assertEquals(TaskStatus.COMPLETED,result.status());assertEquals(0,result.workItemCount());
    }

    @Test
    void foreignAttachmentAndTextPathCannotSatisfyCodeWalkGate(){
        long id=Long.parseLong(as(OWNER,()->fixture.create(OWNER,standalone(null))).id());
        putFile(99,"BOUND","another-task");
        assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(0,List.of(99L),false)))));
        var saved=as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(0,List.of(),false))));
        assertEquals("docs/design.md",saved.designDocumentPath());
        assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",saved.taskRowVersion(),null)))));
    }

    @Test
    void replacingFilesKeepsHistoricalReferencesAndCalendarDoesNotDriftAfterCompletion(){
        var created=as(OWNER,()->fixture.create(OWNER,standalone(null)));long id=Long.parseLong(created.id());
        as(OWNER,()->fixture.tx.execute(s->fixture.tasks.update(OWNER,id,new UpdateTask("任务","内容",null,date("2026-09-07"),date("2026-09-11"),null,null,0))));
        putFile(99,"TEMP",null);putFile(100,"TEMP",null);
        var first=as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(0,List.of(99L),false))));
        var second=as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(first.rowVersion(),List.of(100L),false))));
        assertEquals(2L,fixture.jdbc.queryForObject("SELECT COUNT(DISTINCT attachment_id) FROM dev_stage_attachment_ref WHERE task_id=?",Long.class,id));
        assertEquals("BOUND",files.get(99L).status());
        as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",second.taskRowVersion(),null))));
        var frozen=as(OWNER,()->stages.get(OWNER,id));assertEquals(5,frozen.duration().plannedDays());assertEquals(5,frozen.duration().actualDays());
        when(fixture.references.activeParameters(any(),eq("DEVELOPMENT_CALENDAR"))).thenReturn(List.of(new SystemParameterReference("DEFAULT","{\"restDates\":[\"2026-09-08\"]}")));
        var later=as(OWNER,()->stages.get(OWNER,id));
        assertEquals(frozen.duration(),later.duration());
        var reopened=as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("REOPEN",later.taskRowVersion(),"调整日期口径"))));
        assertEquals(TaskStatus.IN_PROGRESS,reopened.status());
        assertEquals(4,as(OWNER,()->stages.get(OWNER,id)).duration().plannedDays());
    }

    @Test
    void unfinishedWorkItemBlocksCompletionAndCancelRestoreKeepsSameTask(){
        long id=Long.parseLong(as(OWNER,()->fixture.create(OWNER,linked(43))).id());
        fixture.jdbc.update("INSERT INTO dev_work_item (tenant_id,task_id,title,description,assignee_id,created_by,updated_by) VALUES (7,?,'未完成工作','',9,9,9)",id);
        assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",0,null)))));
        var started=as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("START",0,null))));
        var cancelled=as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("CANCEL",started.rowVersion(),"调整计划"))));
        var restored=as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("RESTORE",cancelled.rowVersion(),"继续执行"))));
        assertEquals(Long.toString(id),restored.id());assertEquals(TaskStatus.IN_PROGRESS,restored.status());
        assertEquals(1L,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task",Long.class));
    }

    @Test
    void changingTestOnlySourceToMixedRolesRemovesCompletionExemption(){
        long id=Long.parseLong(as(OWNER,()->fixture.create(OWNER,linked(43))).id());
        var saved=as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(0,List.of(),true))));
        var mixed=new com.ccb.development.integration.DevelopmentSourceDirectory.SourceRequirement(100,"REQ-FIXTURE","虚构需求","内容",31,"revision-2",true,
                List.of(new com.ccb.development.integration.DevelopmentSourceDirectory.SourceSystem("SYS-B",java.util.Set.of(
                        com.ccb.development.integration.DevelopmentSourceDirectory.Role.CHANGE,
                        com.ccb.development.integration.DevelopmentSourceDirectory.Role.TEST),null)));
        when(fixture.sources.requireCurrent(any(),eq("PROJECT-A"),eq(100L))).thenReturn(mixed);
        assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",saved.taskRowVersion(),null)))));
        putFile(99,"TEMP",null);
        var corrected=as(OWNER,()->fixture.tx.execute(s->stages.save(OWNER,id,write(saved.rowVersion(),List.of(99L),false))));
        assertFalse(corrected.pureTest());
        assertEquals(TaskStatus.COMPLETED,as(OWNER,()->fixture.tx.execute(s->stages.action(OWNER,id,new TaskAction("COMPLETE",corrected.taskRowVersion(),null)))).status());
    }

    private void putFile(long id,String status,String task){files.put(id,new AttachmentItem(id,"虚构走查.txt","text/plain",20,"txt",status,
            task==null?null:"development-task",task,task==null?null:"PROJECT-A",9,LocalDateTime.now()));}
    private static LocalDate date(String value){return LocalDate.parse(value);}
    static StageWrite write(long version,List<Long> codeWalk,boolean pureTest){return new StageWrite(null,null,"docs/design.md",List.of(),
            date("2026-09-07"),date("2026-09-11"),codeWalk,List.of(),pureTest,pureTest,version);}
}

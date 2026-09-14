package com.ccb.development.repository;

import com.ccb.development.model.DevelopmentStageModels.*;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Repository
public class DevelopmentStageRepository {
    private final JdbcTemplate jdbc;
    public DevelopmentStageRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<StageEntity> find(long tenantId,long taskId){
        return jdbc.query("SELECT * FROM dev_task_stage WHERE tenant_id=? AND task_id=?",(r,n)->new StageEntity(r.getLong("task_id"),
                r.getObject("design_plan_start",LocalDate.class),r.getObject("design_plan_end",LocalDate.class),r.getString("design_document_path"),
                r.getObject("implementation_actual_start",LocalDate.class),r.getObject("implementation_actual_end",LocalDate.class),r.getBoolean("not_applicable_design"),
                r.getBoolean("not_applicable_implementation"),r.getLong("row_version"),time(r.getTimestamp("design_registered_at")),time(r.getTimestamp("test_registered_at"))),tenantId,taskId).stream().findFirst();
    }
    public StageEntity empty(long taskId){return new StageEntity(taskId,null,null,"",null,null,false,false,0,null,null);}
    public List<Long> attachments(long tenantId,long taskId,long version,AttachmentKind kind){
        return jdbc.queryForList("SELECT attachment_id FROM dev_stage_attachment_ref WHERE tenant_id=? AND task_id=? AND stage_version=? AND kind=? ORDER BY attachment_id",Long.class,
                tenantId,taskId,version,kind.name());
    }
    public void save(AuthUser actor,long taskId,StageWrite r,boolean exists,boolean designChanged,boolean testChanged){
        if(!exists)jdbc.update("INSERT INTO dev_task_stage (tenant_id,task_id,updated_by) VALUES (?,?,?)",actor.tenantId(),taskId,actor.id());
        int changed=jdbc.update("""
                UPDATE dev_task_stage SET design_plan_start=?,design_plan_end=?,design_document_path=?,implementation_actual_start=?,implementation_actual_end=?,
                    not_applicable_design=?,not_applicable_implementation=?,design_registered_at=IF(?,CURRENT_TIMESTAMP(3),design_registered_at),
                    test_registered_at=IF(?,CURRENT_TIMESTAMP(3),test_registered_at),row_version=row_version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP(3)
                WHERE tenant_id=? AND task_id=? AND row_version=?
                """,r.designPlanStart(),r.designPlanEnd(),r.designDocumentPath(),r.implementationActualStart(),r.implementationActualEnd(),r.notApplicableDesign(),
                r.notApplicableImplementation(),designChanged,testChanged,actor.id(),actor.tenantId(),taskId,r.rowVersion());
        if(changed!=1)throw conflict("阶段资料已被修改，请刷新后重试");
        refs(actor,taskId,r.rowVersion()+1,AttachmentKind.DESIGN,r.designAttachmentIds());
        refs(actor,taskId,r.rowVersion()+1,AttachmentKind.CODE_WALK,r.codeWalkAttachmentIds());
        refs(actor,taskId,r.rowVersion()+1,AttachmentKind.TEST_REPORT,r.testReportAttachmentIds());
    }
    private void refs(AuthUser actor,long taskId,long version,AttachmentKind kind,List<Long> ids){
        for(long id:ids)jdbc.update("INSERT INTO dev_stage_attachment_ref (tenant_id,task_id,stage_version,kind,attachment_id,created_by) VALUES (?,?,?,?,?,?)",
                actor.tenantId(),taskId,version,kind.name(),id,actor.id());
    }
    private static LocalDateTime time(Timestamp value){return value==null?null:value.toLocalDateTime();}
}

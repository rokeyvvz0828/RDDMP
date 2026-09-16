package com.ccb.development.repository;

import com.ccb.common.api.PageResult;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Repository
public class DevelopmentTaskRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<TaskEntity> mapper = this::map;

    public DevelopmentTaskRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public TaskEntity require(long tenantId, long id, boolean lock) {
        return jdbc.query("SELECT * FROM dev_task WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""), mapper, tenantId, id)
                .stream().findFirst().orElseThrow(com.ccb.development.model.DevelopmentTaskModels::missing);
    }

    public Optional<TaskEntity> findRequest(AuthUser actor, String requestId, boolean lock) {
        return jdbc.query("SELECT * FROM dev_task WHERE tenant_id=? AND created_by=? AND request_id=?" + (lock ? " FOR UPDATE" : ""),
                mapper, actor.tenantId(), actor.id(), requestId).stream().findFirst();
    }

    public long insert(AuthUser actor, long projectId, CreateTask request, String number, String hash, long ownerId,
                       String sourceNumber, String sourceRevision, List<String> roles, List<String> codes) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO dev_task (tenant_id,project_id,project_ref,task_no,source_mode,source_type,source_requirement_id,
                        source_number,source_revision,source_roles,source_system_codes,system_id,owner_id,title,description,
                        development_plan_start,development_plan_end,test_plan_start,test_plan_end,request_id,request_hash,created_by,updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            Object[] values = {actor.tenantId(), projectId, request.projectRef(), number, request.sourceMode().name(),
                    request.sourceMode()==SourceMode.LINKED ? "LEGACY" : null, request.sourceRequirementId(), sourceNumber,
                    sourceRevision, encode(roles), encode(codes), request.systemId(), ownerId, request.title().trim(),
                    optional(request.description(), 8000, "内容"), request.developmentPlanStart(), request.developmentPlanEnd(),
                    request.testPlanStart(), request.testPlanEnd(), request.requestId(), hash, actor.id(), actor.id()};
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void bindSource(long tenantId, long sourceId, long systemId, long taskId, List<String> codes) {
        for (String code : codes) jdbc.update("INSERT INTO dev_task_source_binding (tenant_id,source_type,source_requirement_id,source_system_code,task_id,system_id) VALUES (?,'LEGACY',?,?,?,?)",
                tenantId, sourceId, code, taskId, systemId);
    }

    public boolean claimed(long tenantId, long sourceId, long systemId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dev_task WHERE tenant_id=? AND source_type='LEGACY' AND source_requirement_id=? AND system_id=?",
                Long.class, tenantId, sourceId, systemId) > 0;
    }

    public void requireStableBinding(long tenantId,long sourceId,long systemId,List<String> codes) {
        if(codes.isEmpty())return;
        List<Object> args=new ArrayList<>(List.of(tenantId,sourceId,systemId));args.addAll(codes);
        long conflicts=jdbc.queryForObject("SELECT COUNT(*) FROM dev_task_source_binding WHERE tenant_id=? AND source_type='LEGACY' AND source_requirement_id=? AND system_id<>? AND source_system_code IN ("
                +String.join(",",java.util.Collections.nCopies(codes.size(),"?"))+")",Long.class,args.toArray());
        if(conflicts>0)throw conflict("系统映射与已有承接记录不一致，请修正映射后重试");
    }

    public boolean assigned(long tenantId, long taskId, long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dev_work_item WHERE tenant_id=? AND task_id=? AND assignee_id=?",
                Long.class, tenantId, taskId, userId) > 0;
    }

    public long workItemCount(long tenantId, long taskId, boolean completedOnly) {
        return workItemCount(tenantId,taskId,completedOnly,null);
    }

    public long workItemCount(long tenantId,long taskId,boolean completedOnly,Long assigneeId) {
        List<Object> args=new ArrayList<>(List.of(tenantId,taskId));
        String where=" WHERE tenant_id=? AND task_id=?"+(completedOnly?" AND status='DONE'":"");
        if(assigneeId!=null){where+=" AND assignee_id=?";args.add(assigneeId);}
        return jdbc.queryForObject("SELECT COUNT(*) FROM dev_work_item"+where,Long.class,args.toArray());
    }

    public void update(AuthUser actor, long taskId, UpdateTask request, long ownerId) {
        int count = jdbc.update("""
                UPDATE dev_task SET title=?,description=?,owner_id=?,development_plan_start=?,development_plan_end=?,
                    test_plan_start=?,test_plan_end=?,row_version=row_version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP(3)
                WHERE tenant_id=? AND id=? AND row_version=?
                """, request.title().trim(), optional(request.description(),8000,"内容"), ownerId,request.developmentPlanStart(),request.developmentPlanEnd(),
                request.testPlanStart(),request.testPlanEnd(),actor.id(),actor.tenantId(),taskId,request.rowVersion());
        if(count!=1) throw conflict("任务已被修改，请刷新后重试");
    }

    public void status(AuthUser actor, TaskEntity task, TaskStatus status) {
        if(jdbc.update("UPDATE dev_task SET status_before_cancel=IF(?='CANCELLED',status,status_before_cancel),status=?,row_version=row_version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND row_version=?",
                status.name(),status.name(),actor.id(),actor.tenantId(),task.id(),task.rowVersion())!=1) throw conflict("任务版本冲突");
    }

    public TaskStatus statusBeforeCancel(long tenantId,long taskId) {
        String value=jdbc.queryForObject("SELECT status_before_cancel FROM dev_task WHERE tenant_id=? AND id=?",String.class,tenantId,taskId);
        if(value==null)return TaskStatus.NOT_STARTED;
        TaskStatus status=TaskStatus.valueOf(value);
        if(status!=TaskStatus.NOT_STARTED&&status!=TaskStatus.IN_PROGRESS)throw conflict("取消前状态不可恢复");
        return status;
    }

    public void refreshSource(AuthUser actor, TaskEntity task, String revision, List<String> roles, List<String> codes) {
        jdbc.update("UPDATE dev_task SET source_revision=?,source_roles=?,source_system_codes=? WHERE tenant_id=? AND id=?",
                revision,encode(roles),encode(codes),actor.tenantId(),task.id());
    }

    public PageResult<TaskEntity> list(long tenantId, long projectId, TaskQuery query, Visibility visibility) {
        List<Object> args = new ArrayList<>(List.of(tenantId,projectId));
        StringBuilder where = new StringBuilder(" FROM dev_task t WHERE t.tenant_id=? AND t.project_id=? AND ");
        where.append(visibilitySql(visibility,args,"t"));
        if(query.systemId()!=null){where.append(" AND t.system_id=?");args.add(query.systemId());}
        if(query.ownerId()!=null){where.append(" AND t.owner_id=?");args.add(query.ownerId());}
        if(query.status()!=null&&!query.status().isBlank()){where.append(" AND t.status=?");args.add(query.status());}
        if(query.keyword()!=null&&!query.keyword().isBlank()){where.append(" AND (t.title LIKE ? OR t.task_no LIKE ?)");args.add("%"+query.keyword().trim()+"%");args.add("%"+query.keyword().trim()+"%");}
        long total = jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());
        args.add(query.page().size());args.add(Math.multiplyExact(query.page().page()-1,query.page().size()));
        return new PageResult<>(jdbc.query("SELECT t.*"+where+" ORDER BY t.updated_at DESC,t.id DESC LIMIT ? OFFSET ?",mapper,args.toArray()),total,query.page().page(),query.page().size());
    }

    public static String visibilitySql(Visibility visibility, List<Object> args, String alias) {
        if(visibility.admin()) return "1=1";
        args.add(visibility.actorId());
        String systemPart = "";
        if(!visibility.systemIds().isEmpty()){
            systemPart=" OR "+alias+".system_id IN ("+String.join(",",java.util.Collections.nCopies(visibility.systemIds().size(),"?"))+")";
            args.addAll(visibility.systemIds());
        }
        args.add(visibility.actorId());
        return "("+alias+".owner_id=?"+systemPart+" OR EXISTS (SELECT 1 FROM dev_work_item visible_item WHERE visible_item.tenant_id="+alias+".tenant_id AND visible_item.task_id="+alias+".id AND visible_item.assignee_id=?))";
    }

    public String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("无法序列化开发任务",ex);}
    }

    private List<String> strings(String value) {
        try{return json.readValue(value,new TypeReference<List<String>>(){});}catch(Exception ex){throw new IllegalStateException("开发任务快照无效",ex);}
    }

    private TaskEntity map(ResultSet r,int n)throws SQLException {
        return new TaskEntity(r.getLong("id"),r.getLong("tenant_id"),r.getLong("project_id"),r.getString("project_ref"),r.getString("task_no"),
                SourceMode.valueOf(r.getString("source_mode")),r.getObject("source_requirement_id",Long.class),r.getString("source_number"),r.getString("source_revision"),
                strings(r.getString("source_roles")),strings(r.getString("source_system_codes")),r.getLong("system_id"),r.getLong("owner_id"),r.getString("title"),r.getString("description"),
                TaskStatus.valueOf(r.getString("status")),r.getLong("row_version"),r.getObject("development_plan_start",java.time.LocalDate.class),r.getObject("development_plan_end",java.time.LocalDate.class),
                r.getObject("test_plan_start",java.time.LocalDate.class),r.getObject("test_plan_end",java.time.LocalDate.class),r.getString("request_id"),r.getString("request_hash"),r.getLong("created_by"),
                r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("updated_at").toLocalDateTime());
    }
}

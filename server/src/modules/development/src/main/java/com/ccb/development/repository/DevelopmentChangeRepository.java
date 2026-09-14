package com.ccb.development.repository;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DevelopmentChangeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SystemOperationAudit audit;

    public DevelopmentChangeRepository(JdbcTemplate jdbc,ObjectMapper json,SystemOperationAudit audit){this.jdbc=jdbc;this.json=json;this.audit=audit;}

    public void record(AuthUser actor,long taskId,String objectType,long objectId,String action,Object before,Object after){
        String trace=TraceId.getOrCreate();
        jdbc.update("INSERT INTO dev_task_change (tenant_id,task_id,object_type,object_id,action,before_json,after_json,actor_id,actor_name,trace_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(),taskId,objectType,objectId,action,encode(before),encode(after),actor.id(),actor.displayName(),trace);
        audit.recordSuccess(new SystemOperationAuditCommand(actor,"development."+objectType+"."+action,null,
                "/api/development/tasks/"+taskId,null,trace));
    }

    public PageResult<ChangeView> list(AuthUser actor,long taskId,PageQuery page){
        return list(actor,taskId,page,true);
    }

    public PageResult<ChangeView> list(AuthUser actor,long taskId,PageQuery page,boolean manager){
        var args=new java.util.ArrayList<Object>(java.util.List.of(actor.tenantId(),taskId));
        String where=" FROM dev_task_change e WHERE e.tenant_id=? AND e.task_id=?";
        if(!manager){
            where+=" AND (e.object_type<>'WORK_ITEM' OR EXISTS (SELECT 1 FROM dev_work_item w WHERE w.tenant_id=e.tenant_id AND w.task_id=e.task_id AND w.id=e.object_id AND w.assignee_id=?))";
            args.add(actor.id());
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());
        args.add(page.size());args.add(Math.multiplyExact(page.page()-1,page.size()));
        var rows=jdbc.query("SELECT e.*"+where+" ORDER BY e.id DESC LIMIT ? OFFSET ?",
                (r,n)->new ChangeView(r.getString("id"),r.getString("object_type"),r.getString("object_id"),r.getString("action"),
                        decode(r.getString("before_json")),decode(r.getString("after_json")),new UserView(r.getString("actor_id"),r.getString("actor_name")),
                        r.getTimestamp("created_at").toLocalDateTime(),r.getString("trace_id")),args.toArray());
        return new PageResult<>(rows,total,page.page(),page.size());
    }

    private String encode(Object value){try{return value==null?null:json.writeValueAsString(sanitize(json.valueToTree(value)));}catch(Exception ex){throw new IllegalStateException("无法保存业务历史",ex);}}
    private JsonNode decode(String value){try{return value==null?null:sanitize(json.readTree(value));}catch(Exception ex){throw new IllegalStateException("业务历史无效",ex);}}
    private JsonNode sanitize(JsonNode node){
        if(node instanceof com.fasterxml.jackson.databind.node.ObjectNode object){
            object.remove(java.util.List.of("allowedActions","warnings","workItemCount","completedWorkItemCount"));
        }
        if(node!=null&&node.isContainerNode())node.forEach(this::sanitize);
        return node;
    }
}

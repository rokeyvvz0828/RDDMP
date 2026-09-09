package com.ccb.development.repository;

import com.ccb.development.model.DevelopmentTaskModels.Visibility;
import com.ccb.development.model.DevelopmentWorkItemModels.*;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Repository
public class DevelopmentWorkItemRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<WorkItemEntity> mapper=this::map;
    public DevelopmentWorkItemRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public WorkItemEntity require(long tenantId,long id,boolean lock){
        return jdbc.query("SELECT * FROM dev_work_item WHERE tenant_id=? AND id=?"+(lock?" FOR UPDATE":""),mapper,tenantId,id)
                .stream().findFirst().orElseThrow(com.ccb.development.model.DevelopmentTaskModels::missing);
    }

    public long insert(AuthUser actor,WorkItemWrite request,long assigneeId){
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{
            var statement=connection.prepareStatement("""
                    INSERT INTO dev_work_item (tenant_id,task_id,title,description,assignee_id,planned_start,planned_end,
                        actual_start,actual_end,blocked,block_reason,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,Statement.RETURN_GENERATED_KEYS);
            Object[] values={actor.tenantId(),request.taskId(),request.title().trim(),optional(request.description(),8000,"内容"),assigneeId,
                    request.plannedStart(),request.plannedEnd(),request.actualStart(),request.actualEnd(),request.blocked(),optional(request.blockReason(),1000,"阻塞原因"),actor.id(),actor.id()};
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;
        },keys);
        return keys.getKey().longValue();
    }

    public void update(AuthUser actor,long id,WorkItemWrite request,long assigneeId){
        int count=jdbc.update("""
                UPDATE dev_work_item SET title=?,description=?,assignee_id=?,planned_start=?,planned_end=?,actual_start=?,actual_end=?,
                    blocked=?,block_reason=?,row_version=row_version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP(3)
                WHERE tenant_id=? AND id=? AND row_version=?
                """,request.title().trim(),optional(request.description(),8000,"内容"),assigneeId,request.plannedStart(),request.plannedEnd(),request.actualStart(),
                request.actualEnd(),request.blocked(),optional(request.blockReason(),1000,"阻塞原因"),actor.id(),actor.tenantId(),id,request.rowVersion());
        if(count!=1)throw conflict("工作项已被修改，请刷新后重试");
    }

    public void status(AuthUser actor,WorkItemEntity item,WorkItemStatus status){
        if(jdbc.update("UPDATE dev_work_item SET status=?,row_version=row_version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND row_version=?",
                status.name(),actor.id(),actor.tenantId(),item.id(),item.rowVersion())!=1)throw conflict("工作项版本冲突");
    }

    public ItemPage list(long tenantId,long projectId,WorkItemQuery query,Visibility visibility){
        List<Object> args=new ArrayList<>(List.of(tenantId,projectId));
        StringBuilder where=new StringBuilder(" FROM dev_work_item w JOIN dev_task t ON t.tenant_id=w.tenant_id AND t.id=w.task_id WHERE t.tenant_id=? AND t.project_id=?");
        if(!visibility.admin()){
            where.append(" AND (t.owner_id=?");args.add(visibility.actorId());
            if(!visibility.systemIds().isEmpty()){
                where.append(" OR t.system_id IN (").append(String.join(",",java.util.Collections.nCopies(visibility.systemIds().size(),"?"))).append(")");
                args.addAll(visibility.systemIds());
            }
            where.append(" OR w.assignee_id=?)");args.add(visibility.actorId());
        }
        if(query.taskId()!=null){where.append(" AND w.task_id=?");args.add(query.taskId());}
        if(query.systemId()!=null){where.append(" AND t.system_id=?");args.add(query.systemId());}
        if(query.assigneeId()!=null){where.append(" AND w.assignee_id=?");args.add(query.assigneeId());}
        if(query.keyword()!=null&&!query.keyword().isBlank()){
            where.append(" AND (w.title LIKE ? OR t.task_no LIKE ?)");args.add("%"+query.keyword().trim()+"%");args.add("%"+query.keyword().trim()+"%");
        }
        if(query.plannedFrom()!=null){where.append(" AND w.planned_end>=?");args.add(query.plannedFrom());}
        if(query.plannedTo()!=null){where.append(" AND w.planned_start<=?");args.add(query.plannedTo());}
        var counts=new LinkedHashMap<String,Long>();for(var status:WorkItemStatus.values())counts.put(status.name(),0L);
        jdbc.query("SELECT w.status,COUNT(*) AS item_count"+where+" GROUP BY w.status",r->{counts.put(r.getString("status"),r.getLong("item_count"));},args.toArray());
        long total;
        if(query.status()!=null&&!query.status().isBlank()){
            where.append(" AND w.status=?");args.add(query.status());total=counts.get(query.status());
        }else total=counts.values().stream().mapToLong(Long::longValue).sum();
        args.add(query.page().size());args.add(Math.multiplyExact(query.page().page()-1,query.page().size()));
        var records=jdbc.query("SELECT w.*"+where+" ORDER BY t.id DESC,w.updated_at DESC,w.id DESC LIMIT ? OFFSET ?",mapper,args.toArray());
        return new ItemPage(records,total,query.page().page(),query.page().size(),java.util.Map.copyOf(counts));
    }

    private WorkItemEntity map(ResultSet r,int n)throws SQLException{
        return new WorkItemEntity(r.getLong("id"),r.getLong("tenant_id"),r.getLong("task_id"),r.getString("title"),r.getString("description"),r.getLong("assignee_id"),
                WorkItemStatus.valueOf(r.getString("status")),r.getObject("planned_start",LocalDate.class),r.getObject("planned_end",LocalDate.class),
                r.getObject("actual_start",LocalDate.class),r.getObject("actual_end",LocalDate.class),r.getBoolean("blocked"),r.getString("block_reason"),r.getLong("row_version"),
                r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("updated_at").toLocalDateTime());
    }
}

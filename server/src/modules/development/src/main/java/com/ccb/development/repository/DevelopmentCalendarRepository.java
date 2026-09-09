package com.ccb.development.repository;

import com.ccb.development.config.DevelopmentSettings.CalendarDefinition;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DevelopmentCalendarRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public DevelopmentCalendarRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public void freeze(AuthUser actor,long taskId,String objectType,long objectId,long version,String digest,CalendarDefinition calendar){
        try{
            jdbc.update("INSERT INTO dev_calendar_snapshot (tenant_id,task_id,object_type,object_id,object_version,calendar_version,calendar_json,created_by) VALUES (?,?,?,?,?,?,?,?)",
                    actor.tenantId(),taskId,objectType,objectId,version,digest,json.writeValueAsString(calendar),actor.id());
        }catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("无法保存日历快照",error);}
    }
    public Optional<CalendarDefinition> latest(long tenantId,String objectType,long objectId){
        return jdbc.query("SELECT calendar_json FROM dev_calendar_snapshot WHERE tenant_id=? AND object_type=? AND object_id=? ORDER BY object_version DESC LIMIT 1",
                (r,n)->decode(r.getString(1)),tenantId,objectType,objectId).stream().findFirst();
    }
    private CalendarDefinition decode(String value){try{return json.readValue(value,CalendarDefinition.class);}catch(Exception error){throw new IllegalStateException("日历快照无效",error);}}
}

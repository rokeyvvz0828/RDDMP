package com.ccb.datamigration.lifecycle;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 生命周期平台审计骨架：复用 dm_operation_log 留痕（写操作审计，铁律 #8/#12）。 */
@Service
public class LifecycleAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public LifecycleAuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void audit(AuthUser user, String operationCode, String entityType, Long entityId,
                      String resultCode, String traceId, Map<String, Object> detail) {
        jdbc.update("INSERT INTO dm_operation_log "
                        + "(tenant_id, actor_id, operation_code, entity_type, entity_id, result_code, trace_id, detail_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                user.tenantId(), user.id(), operationCode, entityType, entityId,
                resultCode == null ? "SUCCESS" : resultCode, traceId, toJson(detail));
    }

    /** 统一操作码：LIFECYCLE_<实体>_<动作>。 */
    public String opCode(String action, String entityType) {
        return "LIFECYCLE_" + entityType + "_" + action;
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}

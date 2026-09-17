package com.ccb.requirement.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

final class RequirementChangeLogTestSupport {
    private RequirementChangeLogTestSupport() {
    }

    static RequirementChangeLogService service(JdbcTemplate jdbc) {
        RequirementChangeLogMapper mapper = new RequirementChangeLogMapper() {
            @Override
            public int insert(long id, long tenantId, String bizType, long bizId, String field,
                              String oldValue, String newValue, String changeType, long operatorId,
                              String operatorName, String source, String traceId) {
                return jdbc.update("INSERT INTO req_change_log", id, tenantId, bizType, bizId, field,
                        oldValue, newValue, changeType, operatorId, operatorName, source, traceId);
            }

            @Override
            public List<Map<String, Object>> list(long tenantId, String bizType, long bizId) {
                return jdbc.queryForList("SELECT * FROM req_change_log", tenantId, bizType, bizId);
            }
        };
        return new RequirementChangeLogService(new RequirementChangeLogRepository(mapper));
    }
}

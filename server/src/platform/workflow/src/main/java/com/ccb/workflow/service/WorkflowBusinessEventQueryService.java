package com.ccb.workflow.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowBusinessEventQueryService {
    private final JdbcTemplate jdbc;

    public WorkflowBusinessEventQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<Map<String, Object>> deliveries(PageQuery query, String status, AuthUser user) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        String where = " FROM wf_lifecycle_delivery d JOIN wf_lifecycle_event e ON e.event_id = d.event_id AND e.tenant_id = d.tenant_id WHERE d.tenant_id = ?" + (normalized == null ? "" : " AND d.status = ?");
        Object[] baseArgs = normalized == null ? new Object[]{user.tenantId()} : new Object[]{user.tenantId(), normalized};
        Object[] pageArgs = normalized == null ? new Object[]{user.tenantId(), (query.page() - 1) * query.size(), query.size()}
                : new Object[]{user.tenantId(), normalized, (query.page() - 1) * query.size(), query.size()};
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT d.event_id, d.subscriber_key, d.status, d.attempt_count, d.next_attempt_at, d.last_error, d.delivered_at, e.event_type, e.business_type, e.business_key, e.business_round, e.business_title, e.occurred_at" + where + " ORDER BY d.id DESC LIMIT ?, ?", pageArgs);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, baseArgs);
        return new PageResult<>(rows, total == null ? 0 : total, query.page(), query.size());
    }
}

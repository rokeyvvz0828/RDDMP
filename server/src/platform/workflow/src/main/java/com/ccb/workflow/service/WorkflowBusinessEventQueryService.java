package com.ccb.workflow.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowBusinessEventQueryService {
    private final WorkflowBusinessEventRepository repository;

    public WorkflowBusinessEventQueryService(WorkflowBusinessEventRepository repository) {
        this.repository = repository;
    }

    public PageResult<Map<String, Object>> deliveries(PageQuery query, String status, AuthUser user) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<Map<String, Object>> rows = repository.selectDeliveries(user.tenantId(), normalized, (query.page() - 1) * query.size(), query.size());
        long total = repository.countDeliveries(user.tenantId(), normalized);
        return new PageResult<>(rows, total, query.page(), query.size());
    }
}

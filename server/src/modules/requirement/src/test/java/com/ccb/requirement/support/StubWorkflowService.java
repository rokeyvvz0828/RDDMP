package com.ccb.requirement.support;

import com.ccb.security.model.AuthUser;
import com.ccb.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用 WorkflowService：覆盖 start 方法，返回模拟审批流实例 ID，
 * 不触发 Flowable/JDBC/事件链路。用于 RequirementDifferenceService/LegacyService 单测。
 */
public class StubWorkflowService extends WorkflowService {
    private static final ApplicationEventPublisher NOOP_PUBLISHER = event -> {
    };
    private final AtomicLong nextInstanceId = new AtomicLong(1000L);
    private final List<String> started = new java.util.ArrayList<>();

    public StubWorkflowService() {
        super(new StubJdbcTemplate(), new ObjectMapper(), null, null, null, NOOP_PUBLISHER);
    }

    @Override
    public Map<String, Object> start(long definitionId, String businessKey, Map<String, Object> variables, AuthUser user) {
        started.add(businessKey);
        long instanceId = nextInstanceId.getAndIncrement();
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("id", instanceId);
        instance.put("definition_id", definitionId);
        instance.put("business_key", businessKey);
        instance.put("status", "RUNNING");
        return instance;
    }

    public List<String> started() {
        return started;
    }
}

package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;

/** 需求基线：同一项目全部差异已评审后生成版本快照并整体锁定。 */
@Service
public class RequirementBaselineService {
    private final RequirementBaselineRepository repository;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;
    private final ObjectMapper objectMapper;

    public RequirementBaselineService(RequirementBaselineRepository repository, RequirementChangeLogService changeLog,
                                      RequirementSecurityService security, ObjectMapper objectMapper) {
        this.repository = repository;
        this.changeLog = changeLog;
        this.security = security;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(long projectId, AuthUser user) {
        security.requireProjectVisible(user, projectId);
        return repository.list(user.tenantId(), projectId);
    }

    public List<Map<String, Object>> items(long baselineId, AuthUser user) {
        Map<String, Object> baseline = repository.find(user.tenantId(), baselineId);
        security.requireProjectVisible(user, ((Number) baseline.get("project_id")).longValue());
        return repository.items(user.tenantId(), baselineId);
    }

    @Transactional
    public Map<String, Object> create(long projectId, String remark, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        Map<String, Object> project = repository.project(user.tenantId(), projectId);
        long pending = repository.pendingDifferenceCount(user.tenantId(), projectId);
        if (pending > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "存在未完成评审的差异，不能形成基线");
        }
        List<Map<String, Object>> differences = repository.reviewedDifferences(user.tenantId(), projectId);
        if (differences.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目下没有可纳入基线的已评审差异");
        }
        long existing = repository.baselineCount(user.tenantId(), projectId);
        long baselineId = RequirementIds.next();
        String baselineNo = "BL-" + project.get("project_code") + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + (existing + 1);
        String baselineName = project.get("project_name") + " 基线 " + (existing + 1);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("id", baselineId);
        baseline.put("tenant_id", user.tenantId());
        baseline.put("project_id", projectId);
        baseline.put("baseline_no", baselineNo);
        baseline.put("baseline_name", baselineName);
        baseline.put("status", "RELEASED");
        baseline.put("difference_count", differences.size());
        baseline.put("remark", remark);
        baseline.put("created_by", user.id());
        baseline.put("deleted", 0);
        repository.insertBaseline(baseline);

        for (Map<String, Object> difference : differences) {
            long differenceId = ((Number) difference.get("id")).longValue();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : difference.entrySet()) {
                if (!"tenant_id".equals(entry.getKey()) && !"deleted".equals(entry.getKey())) {
                    snapshot.put(entry.getKey(), entry.getValue());
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", RequirementIds.next());
            item.put("tenant_id", user.tenantId());
            item.put("baseline_id", baselineId);
            item.put("difference_id", differenceId);
            item.put("snapshot_json", toJson(snapshot));
            item.put("deleted", 0);
            repository.insertItem(item);
            repository.assignDifference(baselineId, user.id(), user.tenantId(), differenceId);
            changeLog.record("NEW_PROJECT_DIFF", differenceId, "BASELINE", "baseline_id", null,
                    String.valueOf(baselineId), user, "ONLINE");
        }
        repository.markProjectBaselined(user.id(), user.tenantId(), projectId);
        changeLog.record("BASELINE", baselineId, "BASELINE", "baseline_no", null, baselineNo, user, "ONLINE");
        return Map.of("id", baselineId, "baseline_no", baselineNo, "difference_count", differences.size());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "基线快照序列化失败");
        }
    }
}

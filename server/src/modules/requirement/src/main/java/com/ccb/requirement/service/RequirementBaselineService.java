package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** 需求基线：同一项目全部差异已评审后生成版本快照并整体锁定。 */
@Service
public class RequirementBaselineService {
    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;
    private final ObjectMapper objectMapper;

    public RequirementBaselineService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                      RequirementSecurityService security, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(long projectId, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        return jdbc.queryForList("""
                SELECT b.id, b.project_id, b.baseline_no, b.baseline_name, b.status, b.difference_count,
                       b.remark, b.created_by, b.created_at,
                       (SELECT COUNT(*) FROM req_baseline_item bi WHERE bi.baseline_id = b.id AND bi.deleted = 0) AS item_count
                FROM req_baseline b
                WHERE b.tenant_id = ? AND b.project_id = ? AND b.deleted = 0
                ORDER BY b.created_at DESC, b.id DESC
                """, user.tenantId(), projectId);
    }

    public List<Map<String, Object>> items(long baselineId, AuthUser user) {
        Map<String, Object> baseline = jdbc.queryForMap(
                "SELECT id, project_id, tenant_id FROM req_baseline WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), baselineId);
        security.requireProjectAccess(user, ((Number) baseline.get("project_id")).longValue());
        return jdbc.queryForList("""
                SELECT id, baseline_id, difference_id, snapshot_json, created_at
                FROM req_baseline_item WHERE tenant_id = ? AND baseline_id = ? AND deleted = 0
                ORDER BY id
                """, user.tenantId(), baselineId);
    }

    @Transactional
    public Map<String, Object> create(long projectId, String remark, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        Map<String, Object> project = jdbc.queryForMap(
                "SELECT id, project_code, project_name FROM req_project WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), projectId);
        Long pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_difference
                WHERE tenant_id = ? AND project_id = ? AND deleted = 0 AND review_status <> '已评审'
                """, Long.class, user.tenantId(), projectId);
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "存在未完成评审的差异，不能形成基线");
        }
        List<Map<String, Object>> differences = jdbc.queryForList("""
                SELECT * FROM req_difference WHERE tenant_id = ? AND project_id = ? AND deleted = 0 AND review_status = '已评审'
                ORDER BY seq_no, id
                """, user.tenantId(), projectId);
        if (differences.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目下没有可纳入基线的已评审差异");
        }
        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_baseline WHERE tenant_id = ? AND project_id = ? AND deleted = 0",
                Long.class, user.tenantId(), projectId);
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
        RequirementSql.insert(jdbc, "req_baseline", baseline);

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
            RequirementSql.insert(jdbc, "req_baseline_item", item);
            jdbc.update("UPDATE req_difference SET baseline_id = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                    baselineId, user.id(), user.tenantId(), differenceId);
            changeLog.record("NEW_PROJECT_DIFF", differenceId, "BASELINE", "baseline_id", null,
                    String.valueOf(baselineId), user, "ONLINE");
        }
        jdbc.update("UPDATE req_project SET status = '已基线', updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), projectId);
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

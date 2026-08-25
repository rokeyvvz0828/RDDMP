package com.ccb.datamigration.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 独立问题清单服务：所有问题业务字段直接存储在 dm_issue。 */
@Service
public class IssueService {
    private static final Set<String> GRANULARITIES = Set.of("PROJECT", "COMPONENT", "TABLE", "FIELD");
    private static final Set<String> ISSUE_SOURCES = Set.of("MIGRATION_CHECK", "SIT_FEEDBACK", "UAT_FEEDBACK", "DATA_LINE_FEEDBACK", "EXPERT_FEEDBACK", "RISK_IDENTIFICATION", "MIGRATION_RELEASE");
    private static final Set<String> DEFECT_TYPES = Set.of("REQUIREMENT", "DESIGN", "CODING", "DATA_QUALITY", "CLEANUP", "BUSINESS", "UNDERSTANDING", "PERFORMANCE", "MASKING", "OTHER");
    private static final Set<String> FREQUENCIES = Set.of("CLASSIC", "HIGH_FREQ", "LOW_FREQ", "SINGLE_CASE");

    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;

    public IssueService(JdbcTemplate jdbc, DataMigrationPermissionService permissions) { this.jdbc = jdbc; this.permissions = permissions; }

    public PageResult<Map<String, Object>> list(Long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, int page, int size, AuthUser user) {
        StringBuilder sql = new StringBuilder(baseSelect(false));
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, granularity, systemCode, issueSource, defectType, frequency, keyword, false);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        int safeSize = Math.max(1, Math.min(size, 100));
        sql.append(" ORDER BY i.updated_at DESC, i.id DESC LIMIT ? OFFSET ?"); args.add(safeSize); args.add(Math.max(0, page - 1) * safeSize);
        return new PageResult<>(jdbc.queryForList(sql.toString(), args.toArray()), total == null ? 0L : total, page, size);
    }

    public Map<String, Object> findById(long id, AuthUser user) { return findByIdInternal(id, user.tenantId(), false); }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = number(body.get("projectId"), "projectId"); ensureProject(projectId, user);
        String code = text(body.get("issueCode"), "issueCode"); String name = text(body.get("issueName"), "issueName");
        ensureCodeAvailable(projectId, code, null, user); validateEnums(body);
        long id = nextId();
        jdbc.update("INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, issue_name, granularity, system_code, system_name, issue_source, defect_type, issue_description, solution, meeting_conclusion, processing_steps, business_scenario, handler, responsible_party, keywords, frequency, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), projectId, code, name, textOrNull(body.get("granularity")), textOrNull(body.get("systemCode")), textOrNull(body.get("systemName")), textOrNull(body.get("issueSource")), textOrNull(body.get("defectType")), textOrNull(body.get("issueDescription")), textOrNull(body.get("solution")), textOrNull(body.get("meetingConclusion")), textOrNull(body.get("processingSteps")), textOrNull(body.get("businessScenario")), textOrNull(body.get("handler")), textOrNull(body.get("responsibleParty")), keywords(body.get("keywords")), textOrNull(body.get("frequency")), user.id(), user.id(), user.id());
        saveRelations(id, body, user); audit(user, "ISSUE_CREATE", id); return findByIdInternal(id, user.tenantId(), false);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> current = findByIdInternal(id, user.tenantId(), false); permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        long projectId = number(body.getOrDefault("projectId", current.get("project_id")), "projectId"); ensureProject(projectId, user);
        String code = text(body.getOrDefault("issueCode", current.get("asset_code")), "issueCode"); String name = text(body.getOrDefault("issueName", current.get("asset_name")), "issueName");
        ensureCodeAvailable(projectId, code, id, user); validateEnums(body);
        jdbc.update("UPDATE dm_issue SET project_id = ?, issue_code = ?, issue_name = ?, granularity = ?, system_code = ?, system_name = ?, issue_source = ?, defect_type = ?, issue_description = ?, solution = ?, meeting_conclusion = ?, processing_steps = ?, business_scenario = ?, handler = ?, responsible_party = ?, keywords = ?, frequency = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                projectId, code, name, textOrNull(body.get("granularity")), textOrNull(body.get("systemCode")), textOrNull(body.get("systemName")), textOrNull(body.get("issueSource")), textOrNull(body.get("defectType")), textOrNull(body.get("issueDescription")), textOrNull(body.get("solution")), textOrNull(body.get("meetingConclusion")), textOrNull(body.get("processingSteps")), textOrNull(body.get("businessScenario")), textOrNull(body.get("handler")), textOrNull(body.get("responsibleParty")), keywords(body.get("keywords")), textOrNull(body.get("frequency")), user.id(), id, user.tenantId());
        saveRelations(id, body, user); audit(user, "ISSUE_UPDATE", id); return findByIdInternal(id, user.tenantId(), false);
    }

    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : ids == null ? List.<Long>of() : ids) { Map<String, Object> row = findByIdInternal(id, user.tenantId(), false); permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue()); jdbc.update("UPDATE dm_issue SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", user.id(), id, user.tenantId()); audit(user, "ISSUE_DELETE", id); }
    }

    public PageResult<Map<String, Object>> recycleBinList(Long projectId, String keyword, int page, int size, AuthUser user) {
        permissions.requireAdmin(user); StringBuilder sql = new StringBuilder(baseSelect(true)); List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, null, null, null, null, null, keyword, true); Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        int safeSize = Math.max(1, Math.min(size, 100)); sql.append(" ORDER BY i.deleted_at DESC, i.id DESC LIMIT ? OFFSET ?"); args.add(safeSize); args.add(Math.max(0, page - 1) * safeSize);
        return new PageResult<>(jdbc.queryForList(sql.toString(), args.toArray()), total == null ? 0L : total, page, size);
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) { permissions.requireAdmin(user); for (Long id : ids == null ? List.<Long>of() : ids) { jdbc.update("UPDATE dm_issue SET deleted = 0, deleted_by = NULL, deleted_at = NULL, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 1", user.id(), id, user.tenantId()); audit(user, "ISSUE_RESTORE", id); } }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) { permissions.requireAdmin(user); for (Long id : ids == null ? List.<Long>of() : ids) { jdbc.update("DELETE FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = 'ISSUE'", user.tenantId(), id); jdbc.update("DELETE FROM dm_issue WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId()); audit(user, "ISSUE_PURGE", id); } }

    @Transactional
    public void purgeAll(AuthUser user) { permissions.requireAdmin(user); jdbc.update("DELETE r FROM dm_asset_relation r JOIN dm_issue i ON i.id = r.source_asset_id AND i.tenant_id = r.tenant_id WHERE r.tenant_id = ? AND r.source_asset_type = 'ISSUE' AND i.deleted = 1", user.tenantId()); jdbc.update("DELETE FROM dm_issue WHERE tenant_id = ? AND deleted = 1", user.tenantId()); audit(user, "ISSUE_PURGE_ALL", 0L); }

    public List<Map<String, Object>> getProjectOptions(AuthUser user) { return jdbc.queryForList("SELECT id AS value, project_name AS label FROM pm_project WHERE tenant_id = ? AND deleted = 0 ORDER BY project_name", user.tenantId()); }
    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) { if (projectId == null) return List.of(); return jdbc.queryForList("SELECT c.physical_subsystem_code AS value, CONCAT(c.physical_subsystem_code, ' - ', COALESCE(s.short_name, s.name, '')) AS label FROM dm_component c LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 WHERE c.tenant_id = ? AND c.project_id = ? AND c.deleted = 0 ORDER BY c.physical_subsystem_code", user.tenantId(), projectId); }
    public String getSystemName(String systemCode, AuthUser user) { if (systemCode == null || systemCode.isBlank()) return null; List<Map<String, Object>> rows = jdbc.queryForList("SELECT COALESCE(short_name, name, '') AS system_name FROM arch_physical_subsystem WHERE tenant_id = ? AND code = ? AND deleted = 0", user.tenantId(), systemCode.trim()); return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("system_name")); }
    public List<Map<String, Object>> getMeetingOptions(Long projectId, AuthUser user) { if (projectId == null) return List.of(); return jdbc.queryForList("SELECT id AS value, asset_name AS label FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND asset_type = 'MEETING' AND deleted = 0 ORDER BY asset_name", user.tenantId(), projectId); }
    public List<Map<String, Object>> getTargetTableOptions(Long projectId, AuthUser user) { if (projectId == null) return List.of(); return jdbc.queryForList("SELECT id AS value, table_name_en AS label FROM dm_target_table WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY table_name_en", user.tenantId(), projectId); }
    public List<Map<String, Object>> getTargetFieldOptions(Long tableId, AuthUser user) { if (tableId == null) return List.of(); return jdbc.queryForList("SELECT id AS value, field_name_en AS label FROM dm_target_table_field WHERE tenant_id = ? AND table_id = ? AND deleted = 0 ORDER BY field_name_en", user.tenantId(), tableId); }

    private String baseSelect(boolean deleted) { return "SELECT i.id, i.project_id, p.project_name, i.issue_code AS asset_code, i.issue_name AS asset_name, i.granularity, i.system_code AS systemCode, i.system_name AS systemName, i.issue_source AS issueSource, i.defect_type AS defectType, i.issue_description AS issueDescription, i.solution, i.meeting_conclusion AS meetingConclusion, i.processing_steps AS processingSteps, i.business_scenario AS businessScenario, i.handler, i.responsible_party AS responsibleParty, i.keywords, i.frequency, i.owner_id, i.created_at, i.updated_at, i.created_by, i.updated_by, i.deleted_by, i.deleted_at, u1.display_name AS created_by_name, u2.display_name AS updated_by_name, u3.display_name AS deleted_by_name FROM dm_issue i LEFT JOIN pm_project p ON i.project_id = p.id AND p.tenant_id = i.tenant_id AND p.deleted = 0 LEFT JOIN sys_user u1 ON u1.id = i.created_by AND u1.tenant_id = i.tenant_id LEFT JOIN sys_user u2 ON u2.id = i.updated_by AND u2.tenant_id = i.tenant_id LEFT JOIN sys_user u3 ON u3.id = i.deleted_by AND u3.tenant_id = i.tenant_id WHERE i.tenant_id = ? AND i.deleted = " + (deleted ? "1" : "0"); }
    private void appendFilters(StringBuilder sql, List<Object> args, Long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, boolean deleted) { if (projectId != null) { sql.append(" AND i.project_id = ?"); args.add(projectId); } if (!deleted && granularity != null && GRANULARITIES.contains(granularity)) { sql.append(" AND i.granularity = ?"); args.add(granularity); } if (!deleted && systemCode != null && !systemCode.isBlank()) { sql.append(" AND i.system_code LIKE ?"); args.add("%" + systemCode.trim() + "%"); } if (!deleted && issueSource != null && ISSUE_SOURCES.contains(issueSource)) { sql.append(" AND i.issue_source = ?"); args.add(issueSource); } if (!deleted && defectType != null && DEFECT_TYPES.contains(defectType)) { sql.append(" AND i.defect_type = ?"); args.add(defectType); } if (!deleted && frequency != null && FREQUENCIES.contains(frequency)) { sql.append(" AND i.frequency = ?"); args.add(frequency); } if (keyword != null && !keyword.isBlank()) { String value = "%" + keyword.trim() + "%"; sql.append(" AND (i.issue_code LIKE ? OR i.issue_name LIKE ? OR i.issue_description LIKE ? OR i.solution LIKE ? OR i.meeting_conclusion LIKE ? OR i.processing_steps LIKE ? OR i.business_scenario LIKE ? OR i.handler LIKE ? OR i.responsible_party LIKE ? OR i.keywords LIKE ?)"); for (int i = 0; i < 10; i++) args.add(value); } }
    private Map<String, Object> findByIdInternal(long id, long tenantId, boolean deleted) { List<Map<String, Object>> rows = jdbc.queryForList(baseSelect(deleted) + " AND i.id = ?", tenantId, id); if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不存在"); Map<String, Object> result = rows.get(0); result.put("relatedMeetingMinutes", relationIds(id, tenantId, "MEETING")); result.put("relatedTables", relationIds(id, tenantId, "TABLE")); result.put("relatedFields", relationIds(id, tenantId, "FIELD")); return result; }
    private List<Long> relationIds(long issueId, long tenantId, String type) { return jdbc.queryForList("SELECT target_asset_id FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = 'ISSUE' AND target_asset_type = ?", Long.class, tenantId, issueId, type); }
    private void saveRelations(long issueId, Map<String, Object> body, AuthUser user) { saveRelationType(issueId, body.get("relatedMeetingMinutes"), "MEETING", user); saveRelationType(issueId, body.get("relatedTables"), "TABLE", user); saveRelationType(issueId, body.get("relatedFields"), "FIELD", user); }
    private void saveRelationType(long issueId, Object raw, String targetType, AuthUser user) { jdbc.update("DELETE FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = 'ISSUE' AND target_asset_type = ?", user.tenantId(), issueId, targetType); for (Object value : asList(raw)) { long targetId; try { targetId = Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "关联目标 ID 无效"); } ensureRelationTarget(targetId, targetType, user); jdbc.update("INSERT INTO dm_asset_relation (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_by) VALUES (?, ?, ?, 'ISSUE', ?, ?, ?)", nextId(), user.tenantId(), issueId, targetId, targetType, user.id()); } }
    private void ensureRelationTarget(long id, String type, AuthUser user) { String sql = switch (type) { case "MEETING" -> "SELECT COUNT(*) FROM dm_asset WHERE id = ? AND tenant_id = ? AND asset_type = 'MEETING' AND deleted = 0"; case "TABLE" -> "SELECT COUNT(*) FROM dm_target_table WHERE id = ? AND tenant_id = ? AND deleted = 0"; case "FIELD" -> "SELECT COUNT(*) FROM dm_target_table_field WHERE id = ? AND tenant_id = ? AND deleted = 0"; default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的关联类型"); }; if (!exists(sql, id, user.tenantId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "关联目标不存在或无权访问"); }
    private void ensureCodeAvailable(long projectId, String code, Long currentId, AuthUser user) { String sql = "SELECT COUNT(*) FROM dm_issue WHERE tenant_id = ? AND project_id = ? AND issue_code = ? AND deleted = 0" + (currentId == null ? "" : " AND id <> ?"); List<Object> args = new ArrayList<>(List.of(user.tenantId(), projectId, code)); if (currentId != null) args.add(currentId); if (exists(sql, args.toArray())) throw new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在"); }
    private void validateEnums(Map<String, Object> body) { validateEnum(body.get("granularity"), GRANULARITIES, "颗粒度"); validateEnum(body.get("issueSource"), ISSUE_SOURCES, "问题来源"); validateEnum(body.get("defectType"), DEFECT_TYPES, "缺陷类型"); validateEnum(body.get("frequency"), FREQUENCIES, "问题频率"); }
    private void validateEnum(Object value, Set<String> allowed, String label) { if (value != null && !String.valueOf(value).isBlank() && !allowed.contains(String.valueOf(value))) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "值无效"); }
    private void ensureProject(long id, AuthUser user) { if (!exists("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在"); }
    private boolean exists(String sql, Object... args) { Integer count = jdbc.queryForObject(sql, Integer.class, args); return count != null && count > 0; }
    private static String text(Object value, String field) { if (value == null || String.valueOf(value).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空"); return String.valueOf(value).trim(); }
    private static String textOrNull(Object value) { if (value == null) return null; String text = String.valueOf(value).trim(); return text.isEmpty() ? null : text; }
    private static long number(Object value, String field) { try { return Long.parseLong(text(value, field)); } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 必须为数字"); } }
    private static String keywords(Object value) { if (value == null) return null; if (value instanceof Collection<?> c) return c.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).distinct().reduce((a, b) -> a + "," + b).orElse(null); return textOrNull(value); }
    private static List<?> asList(Object value) { return value instanceof Collection<?> c ? new ArrayList<>(c) : value == null ? List.of() : List.of(value); }
    private void audit(AuthUser user, String operation, long id) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'ISSUE', ?)", user.tenantId(), user.id(), operation, id); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

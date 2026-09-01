package com.ccb.datamigration.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;

    public IssueService(JdbcTemplate jdbc, DataMigrationPermissionService permissions) { this.jdbc = jdbc; this.permissions = permissions; }

    public PageResult<Map<String, Object>> list(Long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, int page, int size, AuthUser user) {
        StringBuilder sql = new StringBuilder(baseSelect(false));
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, granularity, systemCode, issueSource, defectType, frequency, keyword, false);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        sql.append(" ORDER BY i.updated_at DESC, i.id DESC LIMIT ? OFFSET ?"); args.add(safeSize); args.add((safePage - 1) * safeSize);
        return new PageResult<>(jdbc.queryForList(sql.toString(), args.toArray()), total == null ? 0L : total, safePage, safeSize);
    }

    public Map<String, Object> findById(long id, AuthUser user) { return findByIdInternal(id, user.tenantId(), false); }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = number(body.get("projectId"), "projectId"); ensureProject(projectId, user);
        String code = text(body.get("issueCode"), "issueCode"); String name = text(body.get("issueName"), "issueName");
        String systemCode = textOrNull(body.get("systemCode")); ensureSystemCode(projectId, systemCode, user);
        ensureCodeAvailable(projectId, code, null, user); validateEnums(body);
        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, issue_name, granularity, system_code, issue_source, defect_type, issue_description, solution, meeting_conclusion, processing_steps, business_scenario, handler, responsible_party, keywords, frequency, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, user.tenantId(), projectId, code, name, textOrNull(body.get("granularity")), systemCode, textOrNull(body.get("issueSource")), textOrNull(body.get("defectType")), textOrNull(body.get("issueDescription")), textOrNull(body.get("solution")), textOrNull(body.get("meetingConclusion")), textOrNull(body.get("processingSteps")), textOrNull(body.get("businessScenario")), textOrNull(body.get("handler")), textOrNull(body.get("responsibleParty")), keywords(body.get("keywords")), textOrNull(body.get("frequency")), user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw issueCodeConflict(ex);
        }
        saveRelations(id, projectId, body, user); audit(user, "ISSUE_CREATE", id); return findByIdInternal(id, user.tenantId(), false);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> current = findByIdInternal(id, user.tenantId(), false); permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        long projectId = number(body.getOrDefault("projectId", current.get("project_id")), "projectId"); ensureProject(projectId, user);
        String code = text(body.getOrDefault("issueCode", current.get("asset_code")), "issueCode"); String name = text(body.getOrDefault("issueName", current.get("asset_name")), "issueName");
        String systemCode = textOrNull(body.get("systemCode")); ensureSystemCode(projectId, systemCode, user);
        ensureCodeAvailable(projectId, code, id, user); validateEnums(body);
        try {
            int changed = jdbc.update("UPDATE dm_issue SET project_id = ?, issue_code = ?, issue_name = ?, granularity = ?, system_code = ?, issue_source = ?, defect_type = ?, issue_description = ?, solution = ?, meeting_conclusion = ?, processing_steps = ?, business_scenario = ?, handler = ?, responsible_party = ?, keywords = ?, frequency = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    projectId, code, name, textOrNull(body.get("granularity")), systemCode, textOrNull(body.get("issueSource")), textOrNull(body.get("defectType")), textOrNull(body.get("issueDescription")), textOrNull(body.get("solution")), textOrNull(body.get("meetingConclusion")), textOrNull(body.get("processingSteps")), textOrNull(body.get("businessScenario")), textOrNull(body.get("handler")), textOrNull(body.get("responsibleParty")), keywords(body.get("keywords")), textOrNull(body.get("frequency")), user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
        } catch (DataIntegrityViolationException ex) {
            throw issueCodeConflict(ex);
        }
        saveRelations(id, projectId, body, user); audit(user, "ISSUE_UPDATE", id); return findByIdInternal(id, user.tenantId(), false);
    }

    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : normalizeIds(ids)) {
            Map<String, Object> row = findByIdInternal(id, user.tenantId(), false);
            permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
            int changed = jdbc.update("UPDATE dm_issue SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
            audit(user, "ISSUE_DELETE", id);
        }
    }

    public PageResult<Map<String, Object>> recycleBinList(Long projectId, String keyword, int page, int size, AuthUser user) {
        permissions.requireAdmin(user); StringBuilder sql = new StringBuilder(baseSelect(true)); List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, null, null, null, null, null, keyword, true); Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        int safePage = Math.max(1, page); int safeSize = normalizePageSize(size); sql.append(" ORDER BY i.deleted_at DESC, i.id DESC LIMIT ? OFFSET ?"); args.add(safeSize); args.add((safePage - 1) * safeSize);
        return new PageResult<>(jdbc.queryForList(sql.toString(), args.toArray()), total == null ? 0L : total, safePage, safeSize);
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        List<Long> restoreIds = normalizeIds(ids);
        for (Long id : restoreIds) {
            Map<String, Object> row = findByIdInternal(id, user.tenantId(), true);
            ensureCodeAvailable(((Number) row.get("project_id")).longValue(), String.valueOf(row.get("asset_code")), id, user);
        }
        for (int index = 0; index < restoreIds.size(); index++) {
            Long id = restoreIds.get(index);
            try {
                int changed = jdbc.update("UPDATE dm_issue SET deleted = 0, deleted_by = NULL, deleted_at = NULL, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 1", user.id(), id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw issueCodeConflict(ex);
            }
            audit(user, "ISSUE_RESTORE", id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        List<Long> purgeIds = normalizeIds(ids);
        for (Long id : purgeIds) findByIdInternal(id, user.tenantId(), true);
        for (Long id : purgeIds) {
            jdbc.update("DELETE FROM dm_issue_relation WHERE tenant_id = ? AND issue_id = ?", user.tenantId(), id);
            int changed = jdbc.update("DELETE FROM dm_issue WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
            audit(user, "ISSUE_PURGE", id);
        }
    }

    @Transactional
    public void purgeAll(AuthUser user) { permissions.requireAdmin(user); jdbc.update("DELETE FROM dm_issue_relation WHERE tenant_id = ? AND (issue_id IN (SELECT id FROM dm_issue WHERE tenant_id = ? AND deleted = 1) OR (related_type = 'MEETING' AND related_id IN (SELECT meeting_id FROM dm_meeting WHERE tenant_id = ? AND deleted = 1)))", user.tenantId(), user.tenantId(), user.tenantId()); jdbc.update("DELETE FROM dm_issue WHERE tenant_id = ? AND deleted = 1", user.tenantId()); audit(user, "ISSUE_PURGE_ALL", 0L); }

    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) { if (projectId == null) return List.of(); return jdbc.queryForList("SELECT c.physical_subsystem_code AS value, CONCAT(c.physical_subsystem_code, ' - ', COALESCE(s.short_name, s.name, '')) AS label FROM dm_component c LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 WHERE c.tenant_id = ? AND c.project_id = ? AND c.deleted = 0 ORDER BY c.physical_subsystem_code", user.tenantId(), projectId); }
    public String getSystemName(String systemCode, AuthUser user) { if (systemCode == null || systemCode.isBlank()) return null; List<Map<String, Object>> rows = jdbc.queryForList("SELECT COALESCE(short_name, name, '') AS system_name FROM arch_physical_subsystem WHERE tenant_id = ? AND code = ? AND deleted = 0", user.tenantId(), systemCode.trim()); return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("system_name")); }
    public List<Map<String, Object>> getMeetingOptions(Long projectId, AuthUser user) { if (projectId == null) return List.of(); return jdbc.queryForList("SELECT meeting_id AS value, meeting_title AS label FROM dm_meeting WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY meeting_title, meeting_id", user.tenantId(), projectId); }
    public List<Map<String, Object>> getTargetTableOptions(Long projectId, AuthUser user) { if (projectId == null) return List.of(); return jdbc.queryForList("SELECT id AS value, table_name_en AS label FROM dm_target_table WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY table_name_en", user.tenantId(), projectId); }
    public List<Map<String, Object>> getTargetFieldOptions(Long tableId, AuthUser user) { if (tableId == null) return List.of(); return jdbc.queryForList("SELECT id AS value, field_name_en AS label FROM dm_target_table_field WHERE tenant_id = ? AND table_id = ? AND deleted = 0 ORDER BY field_name_en", user.tenantId(), tableId); }

    private String baseSelect(boolean deleted) {
        return "SELECT i.id, i.project_id, p.project_name, i.issue_code AS asset_code, i.issue_name AS asset_name, i.granularity, i.system_code AS systemCode, COALESCE(s.short_name, s.name) AS systemName, i.issue_source AS issueSource, i.defect_type AS defectType, i.issue_description AS issueDescription, i.solution, i.meeting_conclusion AS meetingConclusion, i.processing_steps AS processingSteps, i.business_scenario AS businessScenario, i.handler, i.responsible_party AS responsibleParty, i.keywords, i.frequency, i.owner_id, i.created_at, i.updated_at, i.created_by, i.updated_by, i.deleted_by, i.deleted_at, u1.display_name AS created_by_name, u2.display_name AS updated_by_name, u3.display_name AS deleted_by_name, "
                + "(SELECT GROUP_CONCAT(m.meeting_title ORDER BY m.meeting_title SEPARATOR ', ') FROM dm_issue_relation r JOIN dm_meeting m ON m.meeting_id = r.related_id AND m.tenant_id = r.tenant_id AND m.deleted = 0 WHERE r.tenant_id = i.tenant_id AND r.issue_id = i.id AND r.related_type = 'MEETING') AS relatedMeetingMinuteNames, "
                + "(SELECT GROUP_CONCAT(t.table_name_en ORDER BY t.table_name_en SEPARATOR ', ') FROM dm_issue_relation r JOIN dm_target_table t ON t.id = r.related_id AND t.tenant_id = r.tenant_id AND t.deleted = 0 WHERE r.tenant_id = i.tenant_id AND r.issue_id = i.id AND r.related_type = 'TABLE') AS relatedTableNames, "
                + "(SELECT GROUP_CONCAT(f.field_name_en ORDER BY f.field_name_en SEPARATOR ', ') FROM dm_issue_relation r JOIN dm_target_table_field f ON f.id = r.related_id AND f.tenant_id = r.tenant_id AND f.deleted = 0 WHERE r.tenant_id = i.tenant_id AND r.issue_id = i.id AND r.related_type = 'FIELD') AS relatedFieldNames "
                + "FROM dm_issue i LEFT JOIN pm_project p ON i.project_id = p.id AND p.tenant_id = i.tenant_id AND p.deleted = 0 LEFT JOIN arch_physical_subsystem s ON s.code = i.system_code AND s.tenant_id = i.tenant_id AND s.deleted = 0 LEFT JOIN sys_user u1 ON u1.id = i.created_by AND u1.tenant_id = i.tenant_id LEFT JOIN sys_user u2 ON u2.id = i.updated_by AND u2.tenant_id = i.tenant_id LEFT JOIN sys_user u3 ON u3.id = i.deleted_by AND u3.tenant_id = i.tenant_id WHERE i.tenant_id = ? AND i.deleted = " + (deleted ? "1" : "0");
    }

    private void appendFilters(StringBuilder sql, List<Object> args, Long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, boolean deleted) {
        if (projectId != null) { sql.append(" AND i.project_id = ?"); args.add(projectId); }
        if (!deleted && granularity != null && GRANULARITIES.contains(granularity)) { sql.append(" AND i.granularity = ?"); args.add(granularity); }
        if (!deleted && systemCode != null && !systemCode.isBlank()) { sql.append(" AND i.system_code = ?"); args.add(systemCode.trim()); }
        if (!deleted && issueSource != null && ISSUE_SOURCES.contains(issueSource)) { sql.append(" AND i.issue_source = ?"); args.add(issueSource); }
        if (!deleted && defectType != null && DEFECT_TYPES.contains(defectType)) { sql.append(" AND i.defect_type = ?"); args.add(defectType); }
        if (!deleted && frequency != null && FREQUENCIES.contains(frequency)) { sql.append(" AND i.frequency = ?"); args.add(frequency); }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (i.issue_code LIKE ? OR i.issue_name LIKE ? OR i.keywords LIKE ?)");
            args.add(value); args.add(value); args.add(value);
        }
    }

    public List<Map<String, Object>> exportRows(Long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, AuthUser user) {
        StringBuilder sql = new StringBuilder(baseSelect(false));
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, granularity, systemCode, issueSource, defectType, frequency, keyword, false);
        sql.append(" ORDER BY i.updated_at DESC, i.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
    private Map<String, Object> findByIdInternal(long id, long tenantId, boolean deleted) { List<Map<String, Object>> rows = jdbc.queryForList(baseSelect(deleted) + " AND i.id = ?", tenantId, id); if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不存在"); Map<String, Object> result = rows.get(0); result.put("relatedMeetingMinutes", relationIds(id, tenantId, "MEETING")); result.put("relatedTables", relationIds(id, tenantId, "TABLE")); result.put("relatedFields", relationIds(id, tenantId, "FIELD")); return result; }
    private List<Long> relationIds(long issueId, long tenantId, String type) { return jdbc.queryForList("SELECT related_id FROM dm_issue_relation WHERE tenant_id = ? AND issue_id = ? AND related_type = ?", Long.class, tenantId, issueId, type); }
    private void saveRelations(long issueId, long projectId, Map<String, Object> body, AuthUser user) {
        if (body.containsKey("relatedMeetingMinutes")) saveRelationType(issueId, projectId, body.get("relatedMeetingMinutes"), "MEETING", user);
        if (body.containsKey("relatedTables")) saveRelationType(issueId, projectId, body.get("relatedTables"), "TABLE", user);
        if (body.containsKey("relatedFields")) saveRelationType(issueId, projectId, body.get("relatedFields"), "FIELD", user);
        ensureFieldRelationsBelongToRelatedTables(issueId, user);
    }

    private void saveRelationType(long issueId, long projectId, Object raw, String targetType, AuthUser user) {
        List<Long> targetIds = new ArrayList<>();
        for (Object value : asList(raw)) {
            long targetId;
            try { targetId = Long.parseLong(String.valueOf(value)); }
            catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "关联目标 ID 无效"); }
            if (!targetIds.contains(targetId)) targetIds.add(targetId);
        }
        for (Long targetId : targetIds) ensureRelationTarget(targetId, targetType, projectId, user);
        jdbc.update("DELETE FROM dm_issue_relation WHERE tenant_id = ? AND issue_id = ? AND related_type = ?", user.tenantId(), issueId, targetType);
        for (Long targetId : targetIds) {
            jdbc.update("INSERT INTO dm_issue_relation (tenant_id, issue_id, related_type, related_id, created_by) VALUES (?, ?, ?, ?, ?)", user.tenantId(), issueId, targetType, targetId, user.id());
        }
    }

    private void ensureRelationTarget(long id, String type, long projectId, AuthUser user) {
        String sql = switch (type) {
            case "MEETING" -> "SELECT COUNT(*) FROM dm_meeting WHERE meeting_id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0";
            case "TABLE" -> "SELECT COUNT(*) FROM dm_target_table WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0";
            case "FIELD" -> "SELECT COUNT(*) FROM dm_target_table_field f JOIN dm_target_table t ON t.id = f.table_id AND t.tenant_id = f.tenant_id WHERE f.id = ? AND f.tenant_id = ? AND t.project_id = ? AND f.deleted = 0 AND t.deleted = 0";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的关联类型");
        };
        if (!exists(sql, id, user.tenantId(), projectId)) throw new BusinessException(ErrorCode.BAD_REQUEST, "关联目标不存在、项目不一致或无权访问");
    }

    private void ensureFieldRelationsBelongToRelatedTables(long issueId, AuthUser user) {
        String sql = "SELECT COUNT(*) FROM dm_issue_relation rf JOIN dm_target_table_field f ON f.id = rf.related_id AND f.tenant_id = rf.tenant_id LEFT JOIN dm_issue_relation rt ON rt.tenant_id = rf.tenant_id AND rt.issue_id = rf.issue_id AND rt.related_type = 'TABLE' AND rt.related_id = f.table_id WHERE rf.tenant_id = ? AND rf.issue_id = ? AND rf.related_type = 'FIELD' AND rt.id IS NULL";
        if (exists(sql, user.tenantId(), issueId)) throw new BusinessException(ErrorCode.BAD_REQUEST, "关联字段必须属于已关联的目标表");
    }
    private void ensureCodeAvailable(long projectId, String code, Long currentId, AuthUser user) { String sql = "SELECT COUNT(*) FROM dm_issue WHERE tenant_id = ? AND project_id = ? AND issue_code = ? AND deleted = 0" + (currentId == null ? "" : " AND id <> ?"); List<Object> args = new ArrayList<>(List.of(user.tenantId(), projectId, code)); if (currentId != null) args.add(currentId); if (exists(sql, args.toArray())) throw new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在"); }
    private void validateEnums(Map<String, Object> body) { validateEnum(body.get("granularity"), GRANULARITIES, "颗粒度"); validateEnum(body.get("issueSource"), ISSUE_SOURCES, "问题来源"); validateEnum(body.get("defectType"), DEFECT_TYPES, "缺陷类型"); validateEnum(body.get("frequency"), FREQUENCIES, "问题频率"); }
    private void validateEnum(Object value, Set<String> allowed, String label) { if (value != null && !String.valueOf(value).isBlank() && !allowed.contains(String.valueOf(value))) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "值无效"); }
    private void ensureProject(long id, AuthUser user) { if (!exists("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在"); }
    private void ensureSystemCode(long projectId, String systemCode, AuthUser user) { if (systemCode != null && !exists("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND project_id = ? AND physical_subsystem_code = ? AND deleted = 0", user.tenantId(), projectId, systemCode)) throw new BusinessException(ErrorCode.BAD_REQUEST, "系统编号不属于所选项目"); }
    private static int normalizePageSize(int size) { return PAGE_SIZES.contains(size) ? size : 20; }
    private boolean exists(String sql, Object... args) { Integer count = jdbc.queryForObject(sql, Integer.class, args); return count != null && count > 0; }
    private static String text(Object value, String field) { if (value == null || String.valueOf(value).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空"); return String.valueOf(value).trim(); }
    private static String textOrNull(Object value) { if (value == null) return null; String text = String.valueOf(value).trim(); return text.isEmpty() ? null : text; }
    private static long number(Object value, String field) { try { return Long.parseLong(text(value, field)); } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 必须为数字"); } }
    private static String keywords(Object value) { if (value == null) return null; if (value instanceof Collection<?> c) return c.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).distinct().reduce((a, b) -> a + "," + b).orElse(null); return textOrNull(value); }
    private static List<?> asList(Object value) { return value instanceof Collection<?> c ? new ArrayList<>(c) : value == null ? List.of() : List.of(value); }
    private static List<Long> normalizeIds(List<Long> ids) { return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList(); }
    private static BusinessException issueCodeConflict(DataIntegrityViolationException cause) {
        BusinessException conflict = new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在");
        conflict.initCause(cause);
        return conflict;
    }
    private void audit(AuthUser user, String operation, long id) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'ISSUE', ?)", user.tenantId(), user.id(), operation, id); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

package com.ccb.datamigration.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 问题清单专属服务。
 * 复用 dm_asset 表，asset_type='ISSUE'，structured_data JSON 存储全部业务字段。
 * 支持多维度筛选、分页、批量新增、逻辑删除和回收站。
 */
@Service
public class IssueService {
    private static final String ASSET_TYPE = "ISSUE";

    private static final Set<String> GRANULARITIES = Set.of("PROJECT", "COMPONENT", "TABLE", "FIELD");
    private static final Set<String> ISSUE_SOURCES = Set.of(
            "MIGRATION_CHECK", "SIT_FEEDBACK", "UAT_FEEDBACK", "DATA_LINE_FEEDBACK",
            "EXPERT_FEEDBACK", "RISK_IDENTIFICATION", "MIGRATION_RELEASE");
    private static final Set<String> DEFECT_TYPES = Set.of(
            "REQUIREMENT", "DESIGN", "CODING", "DATA_QUALITY", "CLEANUP",
            "BUSINESS", "UNDERSTANDING", "PERFORMANCE", "MASKING", "OTHER");
    private static final Set<String> FREQUENCIES = Set.of("CLASSIC", "HIGH_FREQ", "LOW_FREQ", "SINGLE_CASE");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DataMigrationPermissionService permissions;

    public IssueService(JdbcTemplate jdbc, ObjectMapper objectMapper, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.permissions = permissions;
    }

    /**
     * 分页查询问题清单列表
     */
    public PageResult<Map<String, Object>> list(Long projectId, String granularity, String systemCode,
                                                 String issueSource, String defectType, String frequency,
                                                 String keyword, int page, int size, AuthUser user) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.project_id, p.project_name, a.asset_code, a.asset_name, a.structured_data, "
                        + "a.owner_id, a.created_at, a.updated_at, a.created_by, a.updated_by, "
                        + "u1.display_name AS created_by_name, u2.display_name AS updated_by_name "
                        + "FROM dm_asset a "
                        + "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = a.created_by AND u1.tenant_id = a.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = a.updated_by AND u2.tenant_id = a.tenant_id "
                        + "WHERE a.tenant_id = ? AND a.asset_type = '" + ASSET_TYPE + "' AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));

        if (projectId != null) {
            sql.append(" AND a.project_id = ?");
            args.add(projectId);
        }
        if (granularity != null && !granularity.isBlank() && GRANULARITIES.contains(granularity)) {
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.granularity')) = ?");
            args.add(granularity);
        }
        if (systemCode != null && !systemCode.isBlank()) {
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.systemCode')) LIKE ?");
            args.add("%" + systemCode.trim() + "%");
        }
        if (issueSource != null && !issueSource.isBlank() && ISSUE_SOURCES.contains(issueSource)) {
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.issueSource')) = ?");
            args.add(issueSource);
        }
        if (defectType != null && !defectType.isBlank() && DEFECT_TYPES.contains(defectType)) {
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.defectType')) = ?");
            args.add(defectType);
        }
        if (frequency != null && !frequency.isBlank() && FREQUENCIES.contains(frequency)) {
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.frequency')) = ?");
            args.add(frequency);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.asset_code LIKE ? OR a.asset_name LIKE ? "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.issueDescription')) LIKE ? "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.solution')) LIKE ? "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.meetingConclusion')) LIKE ? "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.processingSteps')) LIKE ? "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.relatedMeetingMinutes')) LIKE ? "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.relatedTables')) LIKE ? "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(a.structured_data, '$.relatedFields')) LIKE ?)");
            for (int i = 0; i < 9; i++) args.add(value);
        }

        String countSql = "SELECT COUNT(*) FROM (" + sql + ") t";
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        if (total == null) total = 0L;

        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        return new PageResult<>(records, total, page, size);
    }

    /**
     * 获取单条问题详情
     */
    public Map<String, Object> findById(long id, AuthUser user) {
        return findByIdInternal(id, user.tenantId(), false);
    }

    /**
     * 单条新增问题
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = number(body.get("projectId"), "projectId");
        ensureProject(projectId, user);
        String issueCode = text(body.get("issueCode"), "issueCode");
        String issueName = text(body.get("issueName"), "issueName");

        // 问题编号同一项目下不允许重复
        if (exists("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND asset_type = ? AND asset_code = ? AND deleted = 0",
                user.tenantId(), projectId, ASSET_TYPE, issueCode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在");
        }

        Map<String, Object> structuredData = buildStructuredData(body);

        long id = nextId();
        String structuredDataJson = json(structuredData);
        jdbc.update("INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, structured_data, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), projectId, ASSET_TYPE, issueCode, issueName, structuredDataJson, user.id(), user.id(), user.id());
        
        // 保存关联会议纪要关系
        saveMeetingRelations(id, body.get("relatedMeetingMinutes"), user);
        // 保存关联表关系
        saveTableRelations(id, body.get("relatedTables"), user);
        // 保存关联字段关系
        saveFieldRelations(id, body.get("relatedFields"), user);
        
        audit(user, "ISSUE_CREATE", id);
        return findByIdInternal(id, user.tenantId(), false);
    }

    /**
     * 更新问题
     */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> current = findByIdInternal(id, user.tenantId(), false);
        permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());

        long projectId = number(body.getOrDefault("projectId", current.get("project_id")), "projectId");
        ensureProject(projectId, user);
        String issueCode = text(body.getOrDefault("issueCode", current.get("asset_code")), "issueCode");
        String issueName = text(body.getOrDefault("issueName", current.get("asset_name")), "issueName");

        // 问题编号唯一性检查（排除自身）
        if (exists("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND asset_type = ? AND asset_code = ? AND deleted = 0 AND id <> ?",
                user.tenantId(), projectId, ASSET_TYPE, issueCode, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在");
        }

        Map<String, Object> structuredData = buildStructuredData(body);

        String structuredDataJson = json(structuredData);
        jdbc.update("UPDATE dm_asset SET project_id = ?, asset_code = ?, asset_name = ?, structured_data = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND asset_type = ? AND deleted = 0",
                projectId, issueCode, issueName, structuredDataJson, user.id(), id, user.tenantId(), ASSET_TYPE);
        
        // 更新关联会议纪要关系
        saveMeetingRelations(id, body.get("relatedMeetingMinutes"), user);
        // 更新关联表关系
        saveTableRelations(id, body.get("relatedTables"), user);
        // 更新关联字段关系
        saveFieldRelations(id, body.get("relatedFields"), user);
        
        audit(user, "ISSUE_UPDATE", id);
        return findByIdInternal(id, user.tenantId(), false);
    }

    /**
     * 批量逻辑删除
     */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : ids) {
            Map<String, Object> existing = findByIdInternal(id, user.tenantId(), false);
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
            jdbc.update("UPDATE dm_asset SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND asset_type = ? AND deleted = 0",
                    user.id(), id, user.tenantId(), ASSET_TYPE);
            audit(user, "ISSUE_DELETE", id);
        }
    }

    /**
     * 回收站列表
     */
    public PageResult<Map<String, Object>> recycleBinList(Long projectId, String keyword, int page, int size, AuthUser user) {
        permissions.requireAdmin(user);

        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.project_id, p.project_name, a.asset_code, a.asset_name, a.structured_data, "
                        + "a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at, "
                        + "u3.display_name AS deleted_by_name "
                        + "FROM dm_asset a "
                        + "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN sys_user u3 ON u3.id = a.deleted_by AND u3.tenant_id = a.tenant_id "
                        + "WHERE a.tenant_id = ? AND a.asset_type = '" + ASSET_TYPE + "' AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));

        if (projectId != null) {
            sql.append(" AND a.project_id = ?");
            args.add(projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.asset_code LIKE ? OR a.asset_name LIKE ?)");
            args.add(value);
            args.add(value);
        }

        String countSql = "SELECT COUNT(*) FROM (" + sql + ") t";
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        if (total == null) total = 0L;

        sql.append(" ORDER BY a.deleted_at DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        return new PageResult<>(records, total, page, size);
    }

    /**
     * 恢复
     */
    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            jdbc.update("UPDATE dm_asset SET deleted = 0, deleted_by = NULL, deleted_at = NULL WHERE id = ? AND tenant_id = ? AND asset_type = ? AND deleted = 1",
                    id, user.tenantId(), ASSET_TYPE);
            audit(user, "ISSUE_RESTORE", id);
        }
    }

    /**
     * 彻底删除
     */
    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            jdbc.update("DELETE FROM dm_asset WHERE id = ? AND tenant_id = ? AND asset_type = ? AND deleted = 1",
                    id, user.tenantId(), ASSET_TYPE);
            audit(user, "ISSUE_PURGE", id);
        }
    }

    /**
     * 清空回收站
     */
    @Transactional
    public void purgeAll(AuthUser user) {
        permissions.requireAdmin(user);
        jdbc.update("DELETE FROM dm_asset WHERE tenant_id = ? AND asset_type = ? AND deleted = 1",
                user.tenantId(), ASSET_TYPE);
        audit(user, "ISSUE_PURGE_ALL", 0L);
    }

    // ============ 关联数据查询 ============

    /**
     * 获取项目选项列表
     */
    public List<Map<String, Object>> getProjectOptions(AuthUser user) {
        return jdbc.queryForList(
                "SELECT id AS value, project_name AS label FROM pm_project WHERE tenant_id = ? AND deleted = 0 ORDER BY project_name",
                user.tenantId());
    }

    /**
     * 获取系统选项列表（只查当前项目相关的系统）
     */
    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) {
        if (projectId == null) return List.of();
        return jdbc.queryForList(
                "SELECT c.physical_subsystem_code AS value, CONCAT(c.physical_subsystem_code, ' - ', COALESCE(s.short_name, s.name, '')) AS label "
                        + "FROM dm_component c "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 "
                        + "WHERE c.tenant_id = ? AND c.project_id = ? AND c.deleted = 0 ORDER BY c.physical_subsystem_code",
                user.tenantId(), projectId);
    }

    /**
     * 获取系统名称（根据系统编号）
     */
    public String getSystemName(String systemCode, AuthUser user) {
        if (systemCode == null || systemCode.isBlank()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(short_name, name, '') AS system_name FROM arch_physical_subsystem WHERE tenant_id = ? AND code = ? AND deleted = 0",
                user.tenantId(), systemCode.trim());
        if (rows.isEmpty()) return null;
        return String.valueOf(rows.get(0).get("system_name"));
    }

    /**
     * 获取会议纪要选项列表（只查当前项目相关的）
     */
    public List<Map<String, Object>> getMeetingOptions(Long projectId, AuthUser user) {
        if (projectId == null) return List.of();
        return jdbc.queryForList(
                "SELECT id AS value, asset_name AS label FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND asset_type = 'MEETING' AND deleted = 0 ORDER BY asset_name",
                user.tenantId(), projectId);
    }

    /**
     * 获取目标表选项列表（只查当前项目相关的）
     */
    public List<Map<String, Object>> getTargetTableOptions(Long projectId, AuthUser user) {
        if (projectId == null) return List.of();
        return jdbc.queryForList(
                "SELECT id AS value, table_name_en AS label FROM dm_target_table WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY table_name_en",
                user.tenantId(), projectId);
    }

    /**
     * 获取目标表字段选项列表（根据表ID）
     */
    public List<Map<String, Object>> getTargetFieldOptions(Long tableId, AuthUser user) {
        if (tableId == null) return List.of();
        return jdbc.queryForList(
                "SELECT id AS value, field_name_en AS label FROM dm_target_table_field WHERE tenant_id = ? AND table_id = ? AND deleted = 0 ORDER BY field_name_en",
                user.tenantId(), tableId);
    }

    // ============ 内部工具 ============

    private Map<String, Object> findByIdInternal(long id, long tenantId, boolean deleted) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id, a.project_id, p.project_name, a.asset_code, a.asset_name, a.structured_data, "
                        + "a.owner_id, a.created_at, a.updated_at, a.created_by, a.updated_by, "
                        + "a.deleted_by, a.deleted_at, "
                        + "u1.display_name AS created_by_name, u2.display_name AS updated_by_name, "
                        + "u3.display_name AS deleted_by_name "
                        + "FROM dm_asset a "
                        + "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = a.created_by AND u1.tenant_id = a.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = a.updated_by AND u2.tenant_id = a.tenant_id "
                        + "LEFT JOIN sys_user u3 ON u3.id = a.deleted_by AND u3.tenant_id = a.tenant_id "
                        + "WHERE a.id = ? AND a.tenant_id = ? AND a.asset_type = '" + ASSET_TYPE + "' AND a.deleted = ?",
                id, tenantId, deleted ? 1 : 0);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不存在");
        Map<String, Object> result = rows.get(0);
        // 从关联表查询关联的会议纪要ID列表
        List<Long> relatedMeetingIds = getRelatedMeetingIds(id, tenantId);
        result.put("relatedMeetingMinutes", relatedMeetingIds);
        // 从关联表查询关联的目标表ID列表
        List<Long> relatedTableIds = getRelatedTableIds(id, tenantId);
        result.put("relatedTables", relatedTableIds);
        // 从关联表查询关联的字段ID列表
        List<Long> relatedFieldIds = getRelatedFieldIds(id, tenantId);
        result.put("relatedFields", relatedFieldIds);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildStructuredData(Map<String, Object> body) {
        Map<String, Object> sd = new LinkedHashMap<>();
        sd.put("granularity", optStr(body.get("granularity")));
        sd.put("systemCode", optStr(body.get("systemCode")));
        sd.put("systemName", optStr(body.get("systemName")));
        sd.put("issueSource", optStr(body.get("issueSource")));
        sd.put("defectType", optStr(body.get("defectType")));
        sd.put("issueDescription", optStr(body.get("issueDescription")));
        sd.put("solution", optStr(body.get("solution")));
        sd.put("meetingConclusion", optStr(body.get("meetingConclusion")));
        sd.put("processingSteps", optStr(body.get("processingSteps")));
        sd.put("businessScenario", optStr(body.get("businessScenario")));
        sd.put("handler", optStr(body.get("handler")));
        sd.put("responsibleParty", optStr(body.get("responsibleParty")));
        // 关键字索引（列表）
        sd.put("keywords", normalizeList(body.get("keywords")));
        // 关联纪要已迁移至 dm_asset_relation 表，不再存储在 structured_data 中
        sd.put("frequency", optStr(body.get("frequency")));
        // 关联表和关联字段已迁移至 dm_asset_relation 表，不再存储在 structured_data 中
        return sd;
    }

    private List<Object> normalizeList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) return new ArrayList<>((List<?>) value);
        return new ArrayList<>();
    }

    private String optStr(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private String text(Object value, String field) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return String.valueOf(value).trim();
    }

    private long number(Object value, String field) {
        try {
            return Long.parseLong(text(value, field));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 必须为数字");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 序列化失败");
        }
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private void ensureProject(long id, AuthUser user) {
        if (!exists("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在");
        }
    }

    /**
     * 保存问题与会议纪要的关联关系到 dm_asset_relation 表
     */
    private void saveMeetingRelations(long issueId, Object meetingMinutesObj, AuthUser user) {
        // 先删除该问题的所有会议纪要关联
        jdbc.update("DELETE FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = ? AND target_asset_type = ?",
                user.tenantId(), issueId, "ISSUE", "MEETING");

        List<?> meetingIds = normalizeList(meetingMinutesObj);
        if (meetingIds.isEmpty()) {
            return;
        }

        for (Object meetingIdObj : meetingIds) {
            long meetingId;
            try {
                meetingId = Long.parseLong(String.valueOf(meetingIdObj));
            } catch (NumberFormatException e) {
                continue;
            }
            long relationId = nextId();
            jdbc.update("INSERT INTO dm_asset_relation (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    relationId, user.tenantId(), issueId, "ISSUE", meetingId, "MEETING", user.id());
        }
    }

    /**
     * 查询问题关联的会议纪要ID列表
     */
    private List<Long> getRelatedMeetingIds(long issueId, long tenantId) {
        return jdbc.queryForList(
                "SELECT target_asset_id FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = ? AND target_asset_type = ?",
                Long.class, tenantId, issueId, "ISSUE", "MEETING");
    }

    /**
     * 保存问题与目标表的关联关系到 dm_asset_relation 表
     */
    private void saveTableRelations(long issueId, Object tablesObj, AuthUser user) {
        // 先删除该问题的所有目标表关联
        jdbc.update("DELETE FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = ? AND target_asset_type = ?",
                user.tenantId(), issueId, "ISSUE", "TABLE");

        List<?> tableIds = normalizeList(tablesObj);
        if (tableIds.isEmpty()) {
            return;
        }

        for (Object tableIdObj : tableIds) {
            long tableId;
            try {
                tableId = Long.parseLong(String.valueOf(tableIdObj));
            } catch (NumberFormatException e) {
                continue;
            }
            long relationId = nextId();
            jdbc.update("INSERT INTO dm_asset_relation (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    relationId, user.tenantId(), issueId, "ISSUE", tableId, "TABLE", user.id());
        }
    }

    /**
     * 保存问题与字段的关联关系到 dm_asset_relation 表
     */
    private void saveFieldRelations(long issueId, Object fieldsObj, AuthUser user) {
        // 先删除该问题的所有字段关联
        jdbc.update("DELETE FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = ? AND target_asset_type = ?",
                user.tenantId(), issueId, "ISSUE", "FIELD");

        List<?> fieldIds = normalizeList(fieldsObj);
        if (fieldIds.isEmpty()) {
            return;
        }

        for (Object fieldIdObj : fieldIds) {
            long fieldId;
            try {
                fieldId = Long.parseLong(String.valueOf(fieldIdObj));
            } catch (NumberFormatException e) {
                continue;
            }
            long relationId = nextId();
            jdbc.update("INSERT INTO dm_asset_relation (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    relationId, user.tenantId(), issueId, "ISSUE", fieldId, "FIELD", user.id());
        }
    }

    /**
     * 查询问题关联的目标表ID列表
     */
    private List<Long> getRelatedTableIds(long issueId, long tenantId) {
        return jdbc.queryForList(
                "SELECT target_asset_id FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = ? AND target_asset_type = ?",
                Long.class, tenantId, issueId, "ISSUE", "TABLE");
    }

    /**
     * 查询问题关联的字段ID列表
     */
    private List<Long> getRelatedFieldIds(long issueId, long tenantId) {
        return jdbc.queryForList(
                "SELECT target_asset_id FROM dm_asset_relation WHERE tenant_id = ? AND source_asset_id = ? AND source_asset_type = ? AND target_asset_type = ?",
                Long.class, tenantId, issueId, "ISSUE", "FIELD");
    }

    private void audit(AuthUser user, String operation, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'ISSUE', ?)",
                user.tenantId(), user.id(), operation, id);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}

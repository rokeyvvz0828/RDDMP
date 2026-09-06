package com.ccb.datamigration.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectComponentService {
    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;

    public ProjectComponentService(JdbcTemplate jdbc, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    /** 分页查询组件清单：维护页可见全部启用/停用记录。 */
    public PageResult<Map<String, Object>> components(AuthUser user, Long projectId, PageQuery page) {
        return components(user, projectId, null, null, null, null, null, null, page);
    }

    public PageResult<Map<String, Object>> components(AuthUser user, Long projectId, String businessGroupName,
                                                      String systemCode, String responsibleTeam, String systemKeyword,
                                                      Integer totalCheck, String keyword, PageQuery page) {
        PageQuery normalized = page == null ? new PageQuery(1, 20) : page;
        long scope = permissions.requireProject(projectId, user);
        ComponentFilter filter = buildFilter(scope, businessGroupName, systemCode, responsibleTeam,
                systemKeyword, totalCheck, keyword);
        String select = "SELECT c.project_id, p.project_code, p.project_name, c.system_code, c.enabled, "
                        + "s.business_group_name, s.short_name AS system_short_name, s.name AS system_name, "
                        + "s.description AS system_description, s.responsible_team_name_snapshot AS responsible_team_name, "
                        + "c.total_check, c.owner_id, c.created_at, u1.display_name AS created_by_name, "
                        + "c.updated_at, u2.display_name AS updated_by_name "
                        + "FROM dm_component c "
                        + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.system_code AND s.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                        + "WHERE c.tenant_id = ?";
        String countSql = "SELECT COUNT(*) FROM dm_component c "
                        + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.system_code AND s.deleted = 0 "
                        + "WHERE c.tenant_id = ?";
        StringBuilder where = new StringBuilder(select);
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        applyFilter(where, args, filter);
        StringBuilder countWhere = new StringBuilder(countSql);
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(user.tenantId());
        applyFilter(countWhere, countArgs, filter);
        Long total = jdbc.queryForObject(countWhere.toString(), Long.class, countArgs.toArray());
        where.append(" ORDER BY c.updated_at DESC, c.system_code LIMIT ? OFFSET ?");
        args.add(normalized.size());
        args.add((normalized.page() - 1) * normalized.size());
        List<Map<String, Object>> records = jdbc.queryForList(where.toString(), args.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalized.page(), normalized.size());
    }

    /**
     * 业务系统下拉：仅返回当前项目启用中的系统，前端本地随输随筛（编号/名称均可、不区分大小写）。
     */
    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        String sql = "SELECT c.system_code AS value, CONCAT(c.system_code, ' - ', COALESCE(s.short_name, s.name, '')) AS label "
                        + "FROM dm_component c "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.system_code AND s.deleted = 0 "
                        + "WHERE c.tenant_id = ? AND c.project_id = ? AND c.enabled = 1 "
                        + "ORDER BY c.system_code";
        return jdbc.queryForList(sql, user.tenantId(), scope);
    }

    /** 按筛选条件导出全量元数据字段（维护页可用，含停用记录）。 */
    public byte[] exportComponents(AuthUser user, Long projectId, String businessGroupName, String systemCode,
                                   String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword) {
        long scope = permissions.requireProject(projectId, user);
        ComponentFilter filter = buildFilter(scope, businessGroupName, systemCode, responsibleTeam,
                systemKeyword, totalCheck, keyword);
        StringBuilder sql = new StringBuilder(
                "SELECT p.project_code, p.project_name, c.system_code, c.enabled, "
                        + "s.business_group_name, s.short_name AS system_short_name, "
                        + "s.name AS system_name, s.description AS system_description, "
                        + "s.responsible_team_name_snapshot AS responsible_team_name, c.total_check, "
                        + "c.created_at, u1.display_name AS created_by_name, "
                        + "c.updated_at, u2.display_name AS updated_by_name "
                        + "FROM dm_component c "
                        + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.system_code AND s.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                        + "WHERE c.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        applyFilter(sql, args, filter);
        sql.append(" ORDER BY c.updated_at DESC, c.system_code");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        String[] columns = {"project_code", "project_name", "system_code", "enabled",
                "business_group_name", "system_short_name", "system_name", "system_description",
                "responsible_team_name", "total_check", "created_at", "created_by_name",
                "updated_at", "updated_by_name"};
        String[] headers = {"项目编号", "项目名称", "系统编号", "启用状态", "所属事业群",
                "系统简称", "系统名称", "系统描述", "负责团队", "是否涉及总分核对", "创建时间", "创建人", "更新时间", "更新人"};
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("system-components");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int index = 1;
            for (Map<String, Object> data : rows) {
                Row row = sheet.createRow(index++);
                for (int i = 0; i < columns.length; i++) {
                    Object value = data.get(columns[i]);
                    row.createCell(i).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to export XLSX");
        }
    }

    private record ComponentFilter(long projectId, String businessGroupName, String systemCode,
                                   String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword) {
    }

    private static ComponentFilter buildFilter(long projectId, String businessGroupName, String systemCode,
                                               String responsibleTeam, String systemKeyword, Integer totalCheck,
                                               String keyword) {
        return new ComponentFilter(projectId, blankToNull(businessGroupName), blankToNull(systemCode),
                blankToNull(responsibleTeam), blankToNull(systemKeyword), totalCheck, blankToNull(keyword));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void applyFilter(StringBuilder sql, List<Object> args, ComponentFilter filter) {
        sql.append(" AND c.project_id = ?");
        args.add(filter.projectId());
        if (filter.businessGroupName() != null) { sql.append(" AND s.business_group_name LIKE ?"); args.add("%" + filter.businessGroupName() + "%"); }
        if (filter.systemCode() != null) { sql.append(" AND LOWER(c.system_code) LIKE LOWER(?)"); args.add("%" + filter.systemCode() + "%"); }
        if (filter.responsibleTeam() != null) { sql.append(" AND s.responsible_team_name_snapshot LIKE ?"); args.add("%" + filter.responsibleTeam() + "%"); }
        if (filter.systemKeyword() != null) { sql.append(" AND (LOWER(s.short_name) LIKE LOWER(?) OR LOWER(s.name) LIKE LOWER(?))"); args.add("%" + filter.systemKeyword() + "%"); args.add("%" + filter.systemKeyword() + "%"); }
        if (filter.totalCheck() != null) { sql.append(" AND c.total_check = ?"); args.add(filter.totalCheck()); }
        if (filter.keyword() != null) { sql.append(" AND (LOWER(c.system_code) LIKE LOWER(?) OR LOWER(s.short_name) LIKE LOWER(?) OR LOWER(s.name) LIKE LOWER(?))"); args.add("%" + filter.keyword() + "%"); args.add("%" + filter.keyword() + "%"); args.add("%" + filter.keyword() + "%"); }
    }

    @Transactional
    public Map<String, Object> createComponent(Map<String, Object> body, AuthUser user) {
        long projectId = number(body, "projectId");
        String systemCode = requireText(body, "systemCode");
        permissions.requireAccessible(projectId, user);
        if (exists("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND project_id = ? AND system_code = ?",
                user.tenantId(), projectId, systemCode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 " + systemCode + " 已在该项目的组件清单中，请勿重复新增");
        }
        int totalCheck = integer(body, "totalCheck", 0);
        jdbc.update("INSERT INTO dm_component (tenant_id, project_id, system_code, enabled, total_check, owner_id, created_by) VALUES (?, ?, ?, 1, ?, ?, ?)",
                user.tenantId(), projectId, systemCode, totalCheck, user.id(), user.id());
        audit(user, "COMPONENT_CREATE", projectId, systemCode);
        return componentView(projectId, systemCode, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateComponent(Map<String, Object> body, AuthUser user) {
        permissions.requireAdmin(user);
        long projectId = number(body, "projectId");
        String systemCode = requireText(body, "systemCode");
        Map<String, Object> stored = find(projectId, systemCode, user.tenantId());
        permissions.requireStoredProject(stored.get("project_id"), user);
        int totalCheck = integer(body, "totalCheck", 0);
        jdbc.update("UPDATE dm_component SET total_check = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND project_id = ? AND system_code = ?",
                totalCheck, user.id(), user.tenantId(), projectId, systemCode);
        audit(user, "COMPONENT_UPDATE", projectId, systemCode);
        return componentView(projectId, systemCode, user.tenantId());
    }

    @Transactional
    public void deleteComponent(long projectId, String systemCode, AuthUser user) {
        Map<String, Object> row = find(projectId, systemCode.trim(), user.tenantId());
        long storedProjectId = permissions.requireStoredProject(row.get("project_id"), user);
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        if (hasContentAssets(storedProjectId, systemCode.trim(), user)) throw new BusinessException(ErrorCode.CONFLICT, "该组件存在相关业务数据，拒绝物理删除，请先清理或停用");
        jdbc.update("DELETE FROM dm_component WHERE tenant_id = ? AND project_id = ? AND system_code = ?",
                user.tenantId(), storedProjectId, systemCode.trim());
        audit(user, "COMPONENT_DELETE", storedProjectId, systemCode.trim());
    }

    @Transactional
    public Map<String, Object> setEnabled(long projectId, String systemCode, boolean enabled, AuthUser user) {
        Map<String, Object> row = find(projectId, systemCode.trim(), user.tenantId());
        long storedProjectId = permissions.requireStoredProject(row.get("project_id"), user);
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        jdbc.update("UPDATE dm_component SET enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND project_id = ? AND system_code = ?",
                enabled ? 1 : 0, user.id(), user.tenantId(), storedProjectId, systemCode.trim());
        audit(user, enabled ? "COMPONENT_ENABLE" : "COMPONENT_DISABLE", storedProjectId, systemCode.trim());
        return componentView(storedProjectId, systemCode.trim(), user.tenantId());
    }

    private Map<String, Object> componentView(long projectId, String systemCode, long tenantId) {
        return jdbc.queryForMap("SELECT c.project_id, p.project_code, p.project_name, c.system_code, c.enabled, "
                + "s.business_group_name, s.short_name AS system_short_name, s.name AS system_name, "
                + "s.description AS system_description, s.responsible_team_name_snapshot AS responsible_team_name, "
                + "c.total_check, c.owner_id, "
                + "c.created_at, u1.display_name AS created_by_name, "
                + "c.updated_at, u2.display_name AS updated_by_name "
                + "FROM dm_component c "
                + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.system_code AND s.deleted = 0 "
                + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                + "WHERE c.tenant_id = ? AND c.project_id = ? AND c.system_code = ?", tenantId, projectId, systemCode);
    }

    private Map<String, Object> find(long projectId, String systemCode, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT project_id, owner_id, enabled FROM dm_component WHERE tenant_id = ? AND project_id = ? AND system_code = ?",
                tenantId, projectId, systemCode);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found");
        return rows.get(0);
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    /** 跨全部内容表统计组件占用（含映射/依赖/方案/专题等业务数据），任一活动行即拒绝物理删除。 */
    private boolean hasContentAssets(long projectId, String systemCode, AuthUser user) {
        String where = "tenant_id = ? AND project_id = ? AND system_code = ? AND deleted = 0";
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < ContentAssetTables.ALL_TABLES.size(); i++) {
            args.add(user.tenantId());
            args.add(projectId);
            args.add(systemCode);
        }
        Integer total = jdbc.queryForObject("SELECT COALESCE(SUM(cnt), 0) FROM (" + ContentAssetTables.activeCountUnionSql(where, ContentAssetTables.ALL_TABLES) + ") x", Integer.class, args.toArray());
        return total != null && total > 0;
    }

    private void audit(AuthUser user, String op, long projectId, String systemCode) {
        String detailJson = "{\"projectId\":" + projectId + ",\"systemCode\":\"" + systemCode.replace("\"", "") + "\"}";
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id, detail_json) VALUES (?, ?, ?, ?, 'COMPONENT', NULL, ?)",
                user.tenantId(), user.id(), projectId, op, detailJson);
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, key + " is required");
        return String.valueOf(value).trim();
    }

    private static long number(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, key + " is required");
        try { return Long.parseLong(String.valueOf(raw).trim()); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, key + " must be numeric"); }
    }

    private static int integer(Map<String, Object> body, String key, int defaultValue) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) return defaultValue;
        try { int value = Integer.parseInt(String.valueOf(raw).trim()); return value == 1 ? 1 : 0; }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, key + " must be numeric"); }
    }
}

package com.ccb.datamigration.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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

    /** 分页查询组件清单：筛选 + 关联项目（pm_project）/ 物理子系统 / 用户显示名。 */
    public PageResult<Map<String, Object>> components(AuthUser user, Long projectId, PageQuery page) {
        return components(user, projectId, null, null, null, null, null, null, page);
    }

    public PageResult<Map<String, Object>> components(AuthUser user, Long projectId, String businessGroupName,
                                                      String systemCode, String responsibleTeam, String systemKeyword,
                                                      Integer totalCheck, String keyword, PageQuery page) {
        PageQuery normalized = page == null ? new PageQuery(1, 20) : page;
        // T32 决策 D1/D3：组件清单必须落在具体项目内，projectId 缺失或不可访问不得返回数据。
        long scope = permissions.requireProject(projectId, user);
        ComponentFilter filter = buildFilter(scope, businessGroupName, systemCode, responsibleTeam,
                systemKeyword, totalCheck, keyword);
        StringBuilder select = new StringBuilder(
                "SELECT c.id, c.project_id, p.project_code, p.project_name, c.physical_subsystem_code, "
                        + "s.business_group_name, s.short_name AS system_short_name, s.name AS system_name, "
                        + "s.description AS system_description, s.responsible_team_name_snapshot AS responsible_team_name, "
                        + "c.total_check, "
                        + "c.created_at, u1.display_name AS created_by_name, "
                        + "c.updated_at, u2.display_name AS updated_by_name "
                        + "FROM dm_component c "
                        + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                        + "WHERE c.tenant_id = ? AND c.deleted = 0");
                        StringBuilder where = new StringBuilder(select);
                        List<Object> args = new ArrayList<>();
                        args.add(user.tenantId());
                        applyFilter(where, args, filter);
                        // 筛选条件可能引用 arch_physical_subsystem（s.*），COUNT 必须保持相同 JOIN 结构。
                        String countSql = "SELECT COUNT(*) FROM dm_component c "
                        + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 "
                        + "WHERE c.tenant_id = ? AND c.deleted = 0";
        StringBuilder countWhere = new StringBuilder(countSql);
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(user.tenantId());
        applyFilter(countWhere, countArgs, filter);
        Long total = jdbc.queryForObject(countWhere.toString(), Long.class, countArgs.toArray());
        where.append(" ORDER BY c.updated_at DESC, c.id DESC LIMIT ? OFFSET ?");
        args.add(normalized.size());
        args.add((normalized.page() - 1) * normalized.size());
        List<Map<String, Object>> records = jdbc.queryForList(where.toString(), args.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalized.page(), normalized.size());
    }

    /** 按筛选条件导出全量元数据字段。 */
    public byte[] exportComponents(AuthUser user, Long projectId, String businessGroupName, String systemCode,
                                   String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword) {
        long scope = permissions.requireProject(projectId, user);
        ComponentFilter filter = buildFilter(scope, businessGroupName, systemCode, responsibleTeam,
                systemKeyword, totalCheck, keyword);
        StringBuilder sql = new StringBuilder(
                "SELECT p.project_code, p.project_name, "
                        + "c.physical_subsystem_code, s.business_group_name, s.short_name AS system_short_name, "
                        + "s.name AS system_name, s.description AS system_description, "
                        + "s.responsible_team_name_snapshot AS responsible_team_name, c.total_check, "
                        + "c.created_at, u1.display_name AS created_by_name, "
                        + "c.updated_at, u2.display_name AS updated_by_name "
                        + "FROM dm_component c "
                        + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                        + "WHERE c.tenant_id = ? AND c.deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        applyFilter(sql, args, filter);
        sql.append(" ORDER BY c.updated_at DESC, c.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        String[] columns = {"project_code", "project_name", "physical_subsystem_code",
                "business_group_name", "system_short_name", "system_name", "system_description",
                "responsible_team_name", "total_check", "created_at", "created_by_name",
                "updated_at", "updated_by_name"};
        String[] headers = {"项目编号", "项目名称", "系统编号", "所属事业群",
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
        // T32：项目隔离谓词恒定拼接。
        sql.append(" AND c.project_id = ?");
        args.add(filter.projectId());
        if (filter.businessGroupName() != null) { sql.append(" AND s.business_group_name LIKE ?"); args.add("%" + filter.businessGroupName() + "%"); }
        if (filter.systemCode() != null) { sql.append(" AND c.physical_subsystem_code LIKE ?"); args.add("%" + filter.systemCode() + "%"); }
        if (filter.responsibleTeam() != null) { sql.append(" AND s.responsible_team_name_snapshot LIKE ?"); args.add("%" + filter.responsibleTeam() + "%"); }
        if (filter.systemKeyword() != null) { sql.append(" AND (s.short_name LIKE ? OR s.name LIKE ?)"); args.add("%" + filter.systemKeyword() + "%"); args.add("%" + filter.systemKeyword() + "%"); }
        if (filter.totalCheck() != null) { sql.append(" AND c.total_check = ?"); args.add(filter.totalCheck()); }
        if (filter.keyword() != null) { sql.append(" AND (c.physical_subsystem_code LIKE ? OR s.short_name LIKE ? OR s.name LIKE ?)"); args.add("%" + filter.keyword() + "%"); args.add("%" + filter.keyword() + "%"); args.add("%" + filter.keyword() + "%"); }
    }

    @Transactional
    public Map<String, Object> createComponent(Map<String, Object> body, AuthUser user) {
        long projectId = number(body, "projectId"); requireText(body, "physicalSubsystemCode");
        ensureProject(projectId, user);
        // 组件身份由系统编号（物理子系统编号）承担，项目内全局唯一。
        String physicalCode = String.valueOf(body.get("physicalSubsystemCode")).trim();
        if (exists("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND project_id = ? AND physical_subsystem_code = ?", user.tenantId(), projectId, physicalCode)) throw new BusinessException(ErrorCode.CONFLICT, "Physical subsystem already registered in project");
        int totalCheck = integer(body, "totalCheck", 0);
        long id = nextId();
        jdbc.update("INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code, total_check, owner_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, physicalCode, totalCheck, user.id(), user.id());
        audit(user, "COMPONENT_CREATE", "COMPONENT", projectId, id);
        return componentView(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateComponent(long id, Map<String, Object> body, AuthUser user) {
        permissions.requireAdmin(user);
        Map<String, Object> stored = find("SELECT id, project_id, owner_id FROM dm_component WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        long projectId = permissions.requireStoredProject(stored.get("project_id"), user);
        int totalCheck = integer(body, "totalCheck", 0);
        // 规格约束：仅允许修改"是否涉及总分核对"，其余字段保持不可变。
        jdbc.update("UPDATE dm_component SET total_check = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", totalCheck, user.id(), id, user.tenantId());
        audit(user, "COMPONENT_UPDATE", "COMPONENT", projectId, id);
        return componentView(id, user.tenantId());
    }

    @Transactional
    public void deleteComponent(long id, AuthUser user) {
        Map<String, Object> row = find("SELECT id, project_id, owner_id FROM dm_component WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        long projectId = permissions.requireStoredProject(row.get("project_id"), user);
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        if (hasContentAssets(id, user)) throw new BusinessException(ErrorCode.CONFLICT, "Component has related assets");
        jdbc.update("UPDATE dm_component SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId()); audit(user, "COMPONENT_DELETE", "COMPONENT", projectId, id);
    }

    private Map<String, Object> componentView(long id, long tenantId) {
        return jdbc.queryForMap("SELECT c.id, c.project_id, p.project_code, p.project_name, c.physical_subsystem_code, "
                + "s.business_group_name, s.short_name AS system_short_name, s.name AS system_name, "
                + "s.description AS system_description, s.responsible_team_name_snapshot AS responsible_team_name, "
                + "c.total_check, "
                + "c.created_at, u1.display_name AS created_by_name, "
                + "c.updated_at, u2.display_name AS updated_by_name "
                + "FROM dm_component c "
                + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 "
                + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                + "WHERE c.id = ? AND c.tenant_id = ? AND c.deleted = 0", id, tenantId);
    }

    private Map<String, Object> find(String sql, Object... args) { try { return jdbc.queryForMap(sql, args); } catch (Exception ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "Record not found"); } }
    private void ensureProject(long id, AuthUser user) { permissions.requireAccessible(id, user); }
    private boolean exists(String sql, Object... args) { Integer count = jdbc.queryForObject(sql, Integer.class, args); return count != null && count > 0; }

    /** 跨全部内容与结构化表统计组件占用，任一表有活动行即视为占用。 */
    private boolean hasContentAssets(long componentId, AuthUser user) {
        String where = "tenant_id = ? AND component_id = ? AND deleted = 0";
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < ContentAssetTables.ALL_TABLES.size(); i++) { args.add(user.tenantId()); args.add(componentId); }
        Integer total = jdbc.queryForObject("SELECT COALESCE(SUM(cnt), 0) FROM (" + ContentAssetTables.activeCountUnionSql(where, ContentAssetTables.ALL_TABLES) + ") x", Integer.class, args.toArray());
        return total != null && total > 0;
    }
    private void audit(AuthUser user, String op, String type, long projectId, long id) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, ?, ?, ?)", user.tenantId(), user.id(), projectId, op, type, id); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
    private static void requireText(Map<String, Object> body, String key) { if (body.get(key) == null || String.valueOf(body.get(key)).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, key + " is required"); }
    private static long number(Map<String, Object> body, String key) { requireText(body, key); try { return Long.parseLong(String.valueOf(body.get(key))); } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, key + " must be numeric"); } }
    private static int integer(Map<String, Object> body, String key, int defaultValue) { Object raw = body.get(key); if (raw == null || String.valueOf(raw).trim().isEmpty()) return defaultValue; try { int value = Integer.parseInt(String.valueOf(raw).trim()); return value == 1 ? 1 : 0; } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, key + " must be numeric"); } }
}

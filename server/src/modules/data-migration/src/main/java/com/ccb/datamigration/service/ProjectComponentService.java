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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final DataMigrationCodeValueService codeValues;

    public ProjectComponentService(JdbcTemplate jdbc, DataMigrationPermissionService permissions,
                                   DataMigrationCodeValueService codeValues) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.codeValues = codeValues;
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
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.project_id = c.project_id AND s.code = c.system_code AND s.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                        + "WHERE c.tenant_id = ?";
        String countSql = "SELECT COUNT(*) FROM dm_component c "
                        + "JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.project_id = c.project_id AND s.code = c.system_code AND s.deleted = 0 "
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
        attachPersons(records, user.tenantId(), scope);
        return new PageResult<>(records, total == null ? 0 : total, normalized.page(), normalized.size());
    }

    /**
     * 业务系统下拉：仅返回当前项目启用中的系统，前端本地随输随筛（编号/名称均可、不区分大小写）。
     */
    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        String sql = "SELECT c.system_code AS value, CONCAT(c.system_code, ' - ', COALESCE(s.short_name, s.name, '')) AS label "
                        + "FROM dm_component c "
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.project_id = c.project_id AND s.code = c.system_code AND s.deleted = 0 "
                        + "WHERE c.tenant_id = ? AND c.project_id = ? AND c.enabled = 1 "
                        + "ORDER BY c.system_code";
        return jdbc.queryForList(sql, user.tenantId(), scope);
    }

    /**
     * 项目成员选项：仅返回当前项目未删除且启用的项目成员（pm_project_member JOIN sys_user），
     * 供“组件清单关联人员”选择；成员口径与平台项目成员一致，停用成员与非项目成员不出现在选项中。
     */
    public List<Map<String, Object>> getMemberOptions(Long projectId, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        return jdbc.queryForList(
                "SELECT m.user_id, u.display_name, u.username "
                        + "FROM pm_project_member m "
                        + "JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.status = 1 AND u.deleted = 0 "
                        + "WHERE m.tenant_id = ? AND m.project_id = ? AND m.deleted = 0 AND m.status = 1 "
                        + "ORDER BY u.display_name, m.user_id", user.tenantId(), scope);
    }

    /**
     * 查询某一系统的关联人员（含职责中文标签）：读接口沿用类级集合权限，项目数据范围由
     * requireProject + 组件存在校验保证，跨项目 systemCode 直接以组件不存在拒绝。
     */
    public List<Map<String, Object>> getPersons(Long projectId, String systemCode, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        String normalized = systemCode.trim();
        find(projectId, normalized, user.tenantId());
        return jdbc.queryForList(
                "SELECT p.user_id, u.display_name, p.person_role, "
                        + "COALESCE(c.config_value, p.person_role) AS person_role_label "
                        + "FROM dm_component_person p "
                        + "JOIN sys_user u ON u.id = p.user_id AND u.tenant_id = p.tenant_id AND u.deleted = 0 "
                        + "LEFT JOIN sys_dict_type t ON t.tenant_id = p.tenant_id AND t.dict_code = 'DM_COMPONENT_PERSON_ROLE' AND t.deleted = 0 "
                        + "LEFT JOIN sys_config c ON c.tenant_id = p.tenant_id AND c.category_id = t.id "
                        + "AND c.config_key = CONCAT('DM_COMPONENT_PERSON_ROLE.', p.person_role) AND c.deleted = 0 "
                        + "WHERE p.tenant_id = ? AND p.project_id = ? AND p.system_code = ? ORDER BY p.user_id",
                user.tenantId(), scope, normalized);
    }

    /**
     * 全量替换保存某系统的关联人员（一人一角色一条）：写权限由控制器 RBAC 声明并在服务端
     * requireAdmin 复核；校验组件存在、职责/角色属于 DM_COMPONENT_PERSON_ROLE 字典、每个成员
     * 是当前项目启用成员；同一事务内先删除后插入并写 dm_operation_log（COMPONENT_PERSON_SAVE）。
     */
    @Transactional
    public void savePersons(Map<String, Object> body, AuthUser user) {
        long projectId = number(body, "projectId");
        String systemCode = requireText(body, "systemCode").trim();
        permissions.requireAdmin(user);
        long scope = permissions.requireProject(projectId, user);
        find(projectId, systemCode, user.tenantId());
        List<Map<String, Object>> persons = requirePersonList(body.get("persons"));
        validatePersons(persons, scope, user);
        jdbc.update("DELETE FROM dm_component_person WHERE tenant_id = ? AND project_id = ? AND system_code = ?",
                user.tenantId(), scope, systemCode);
        for (Map<String, Object> person : persons) {
            long userId = number(person, "userId");
            String personRole = requireText(person, "personRole");
            jdbc.update("INSERT INTO dm_component_person (tenant_id, project_id, system_code, user_id, person_role, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    user.tenantId(), scope, systemCode, userId, personRole, user.id(), user.id());
        }
        auditComponentPerson(user, scope, systemCode, persons.size());
    }

    private void validatePersons(List<Map<String, Object>> persons, long projectId, AuthUser user) {
        Set<Long> seen = new LinkedHashSet<>();
        for (Map<String, Object> person : persons) {
            long userId = number(person, "userId");
            String personRole = requireText(person, "personRole");
            if (!seen.add(userId)) throw new BusinessException(ErrorCode.BAD_REQUEST, "人员 " + userId + " 重复，请在同一角色下只保留一条");
            codeValues.requireActive(DataMigrationCodeValueService.DM_COMPONENT_PERSON_ROLE, "职责/角色", personRole, user);
            Integer member = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pm_project_member m "
                            + "JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.status = 1 AND u.deleted = 0 "
                            + "WHERE m.tenant_id = ? AND m.project_id = ? AND m.deleted = 0 AND m.status = 1 AND m.user_id = ?",
                    Integer.class, user.tenantId(), projectId, userId);
            if (member == null || member == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "成员 " + userId + " 不是当前项目启用成员，无法关联");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> requirePersonList(Object raw) {
        if (!(raw instanceof List<?> list)) throw new BusinessException(ErrorCode.BAD_REQUEST, "persons is required");
        List<Map<String, Object>> persons = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new BusinessException(ErrorCode.BAD_REQUEST, "persons item must be an object");
            persons.add((Map<String, Object>) map);
        }
        return persons;
    }

    private void attachPersons(List<Map<String, Object>> records, long tenantId, long projectId) {
        Map<String, List<Map<String, Object>>> groups = personsGroupedBySystem(tenantId, projectId);
        for (Map<String, Object> record : records) {
            List<Map<String, Object>> persons = groups.getOrDefault(String.valueOf(record.get("system_code")), List.of());
            record.put("persons", persons);
            record.put("person_count", persons.size());
        }
    }

    private void attachPersonsLabel(List<Map<String, Object>> records, long tenantId, long projectId) {
        Map<String, List<Map<String, Object>>> groups = personsGroupedBySystem(tenantId, projectId);
        for (Map<String, Object> record : records) {
            String joined = groups.getOrDefault(String.valueOf(record.get("system_code")), List.of())
                    .stream()
                    .map(person -> person.get("display_name") + "（" + person.get("person_role_label") + "）")
                    .collect(Collectors.joining(", "));
            record.put("person_label", joined.isEmpty() ? "-" : joined);
        }
    }

    /** 按项目一次批查全部系统的人员关系（含职责中文标签，字典缺失时回退业务编码）。 */
    private Map<String, List<Map<String, Object>>> personsGroupedBySystem(long tenantId, long projectId) {
        List<Map<String, Object>> persons = jdbc.queryForList(
                "SELECT p.system_code, p.user_id, u.display_name, p.person_role, "
                        + "COALESCE(c.config_value, p.person_role) AS person_role_label "
                        + "FROM dm_component_person p "
                        + "JOIN sys_user u ON u.id = p.user_id AND u.tenant_id = p.tenant_id AND u.deleted = 0 "
                        + "LEFT JOIN sys_dict_type t ON t.tenant_id = p.tenant_id AND t.dict_code = 'DM_COMPONENT_PERSON_ROLE' AND t.deleted = 0 "
                        + "LEFT JOIN sys_config c ON c.tenant_id = p.tenant_id AND c.category_id = t.id "
                        + "AND c.config_key = CONCAT('DM_COMPONENT_PERSON_ROLE.', p.person_role) AND c.deleted = 0 "
                        + "WHERE p.tenant_id = ? AND p.project_id = ? ORDER BY p.user_id", tenantId, projectId);
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> person : persons) {
            groups.computeIfAbsent(String.valueOf(person.get("system_code")), key -> new ArrayList<>()).add(person);
        }
        return groups;
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
                        + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.project_id = c.project_id AND s.code = c.system_code AND s.deleted = 0 "
                        + "LEFT JOIN sys_user u1 ON u1.id = c.created_by AND u1.tenant_id = c.tenant_id "
                        + "LEFT JOIN sys_user u2 ON u2.id = c.updated_by AND u2.tenant_id = c.tenant_id "
                        + "WHERE c.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        applyFilter(sql, args, filter);
        sql.append(" ORDER BY c.updated_at DESC, c.system_code");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        attachPersonsLabel(rows, user.tenantId(), scope);
        String[] columns = {"project_code", "project_name", "system_code", "enabled",
                "business_group_name", "system_short_name", "system_name", "system_description",
                "responsible_team_name", "total_check", "created_at", "created_by_name",
                "updated_at", "updated_by_name", "person_label"};
        String[] headers = {"项目编号", "项目名称", "系统编号", "启用状态", "所属事业群",
                "系统简称", "系统名称", "系统描述", "负责团队", "是否涉及总分核对", "创建时间", "创建人", "更新时间", "更新人", "关联人员（角色）"};
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
        jdbc.update("DELETE FROM dm_component_person WHERE tenant_id = ? AND project_id = ? AND system_code = ?",
                user.tenantId(), storedProjectId, systemCode.trim());
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
                + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.project_id = c.project_id AND s.code = c.system_code AND s.deleted = 0 "
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

    private void auditComponentPerson(AuthUser user, long projectId, String systemCode, int personCount) {
        String detailJson = "{\"projectId\":" + projectId + ",\"systemCode\":\"" + systemCode.replace("\"", "") + "\",\"personCount\":" + personCount + "}";
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id, detail_json) VALUES (?, ?, ?, ?, 'COMPONENT', NULL, ?)",
                user.tenantId(), user.id(), projectId, "COMPONENT_PERSON_SAVE", detailJson);
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

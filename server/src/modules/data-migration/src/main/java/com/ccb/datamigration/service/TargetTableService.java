package com.ccb.datamigration.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 目标表结构 / 中间表结构（基础资料管理）服务。
 * 表信息存 dm_target_table，字段明细存 dm_target_table_field，按 table_category 区分类型。
 */
@Service
public class TargetTableService {
    private static final List<String> IMPORT_COLUMNS = List.of(
            "所属项目编码", "系统编号", "表英文名称", "表中文名称", "表含义",
            "字段英文名称", "字段中文名称", "字段含义", "码值说明",
            "是否关键栏位", "ORACLE字段类型", "mysql字段类型", "是否可空", "是否主键", "数据字典编号");
    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;

    public TargetTableService(JdbcTemplate jdbc, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private String categoryOf(String category) {
        if (category == null || category.isBlank() || "TARGET".equalsIgnoreCase(category)) return "TARGET";
        if ("INTERMEDIATE".equalsIgnoreCase(category)) return "INTERMEDIATE";
        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的表结构类别");
    }

    // ============ 列表（字段粒度分页） ============
    public PageResult<Map<String, Object>> list(String category, Map<String, Object> params, AuthUser user, PageQuery page) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "read");
        StringBuilder sql = new StringBuilder(
                "SELECT t.table_code, f.field_code, f.field_name_en, f.field_name_cn, f.field_meaning, " +
                        "f.code_description, f.is_key_field, f.oracle_type, f.mysql_type, f.is_nullable, f.is_primary_key, f.dict_code, " +
                        "t.project_id, t.system_code, t.table_name_en, t.table_name_cn, t.table_meaning, t.owner_id, t.created_at, t.updated_at, " +
                        "p.project_name, ps.business_group_name AS business_group, ps.name AS system_name, u.display_name AS owner_name " +
                        "FROM dm_target_table_field f " +
                        "JOIN dm_target_table t ON t.table_code = f.table_code AND t.tenant_id = f.tenant_id AND t.deleted = 0 " +
                        "LEFT JOIN pm_project p ON p.id = t.project_id AND p.tenant_id = t.tenant_id AND p.deleted = 0 " +
                        "LEFT JOIN dm_component dc ON dc.tenant_id = t.tenant_id AND dc.project_id = t.project_id AND dc.physical_subsystem_code = t.system_code AND dc.deleted = 0 " +
                        "LEFT JOIN arch_physical_subsystem ps ON ps.tenant_id = dc.tenant_id AND ps.code = dc.physical_subsystem_code AND ps.deleted = 0 " +
                        "LEFT JOIN sys_user u ON u.id = t.owner_id AND u.tenant_id = t.tenant_id AND u.deleted = 0 " +
                        "WHERE f.tenant_id = ? AND f.deleted = 0 AND t.table_category = ?");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        args.add(cat);
        // T32 决策 D1/D3：表结构列表必须限定在可访问的具体项目内，缺失 projectId 返回 400。
        long scope = permissions.requireProject(parseProjectId(params.get("projectId")), user);
        sql.append(" AND t.project_id = ?");
        args.add(scope);
        Object systemCode = params.get("systemCode");
        if (systemCode != null && !String.valueOf(systemCode).isBlank()) {
            sql.append(" AND t.system_code = ?");
            args.add(String.valueOf(systemCode).trim());
        }
        Object isKeyField = params.get("isKeyField");
        if (isKeyField != null && !String.valueOf(isKeyField).isBlank()) {
            sql.append(" AND f.is_key_field = ?");
            args.add(Integer.parseInt(String.valueOf(isKeyField)));
        }
        Object dictCode = params.get("dictCode");
        if (dictCode != null && !String.valueOf(dictCode).isBlank()) {
            sql.append(" AND f.dict_code LIKE ?");
            args.add("%" + String.valueOf(dictCode).trim() + "%");
        }
        Object tableKeyword = params.get("tableKeyword");
        if (tableKeyword != null && !String.valueOf(tableKeyword).isBlank()) {
            sql.append(" AND (t.table_name_en LIKE ? OR t.table_name_cn LIKE ?)");
            String k = "%" + String.valueOf(tableKeyword).trim() + "%";
            args.add(k);
            args.add(k);
        }
        Object fieldKeyword = params.get("fieldKeyword");
        if (fieldKeyword != null && !String.valueOf(fieldKeyword).isBlank()) {
            sql.append(" AND (f.field_name_en LIKE ? OR f.field_name_cn LIKE ?)");
            String k = "%" + String.valueOf(fieldKeyword).trim() + "%";
            args.add(k);
            args.add(k);
        }
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") s", Integer.class, args.toArray());
        sql.append(" ORDER BY t.updated_at DESC, t.table_code DESC, f.field_code ASC LIMIT ? OFFSET ?");
        args.add(page.size());
        args.add((page.page() - 1) * page.size());
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return new PageResult<>(rows, total, page.page(), page.size());
    }

    // ============ 查看（表 + 字段） ============
    public Map<String, Object> getDetail(long tableCode, String category, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "read");
        List<Map<String, Object>> tables = jdbc.queryForList(
                "SELECT t.*, p.project_name, ps.business_group_name AS business_group, ps.name AS system_name, u.display_name AS owner_name " +
                        "FROM dm_target_table t " +
                        "LEFT JOIN pm_project p ON p.id = t.project_id AND p.tenant_id = t.tenant_id AND p.deleted = 0 " +
                        "LEFT JOIN dm_component dc ON dc.tenant_id = t.tenant_id AND dc.project_id = t.project_id AND dc.physical_subsystem_code = t.system_code AND dc.deleted = 0 " +
                        "LEFT JOIN arch_physical_subsystem ps ON ps.tenant_id = dc.tenant_id AND ps.code = dc.physical_subsystem_code AND ps.deleted = 0 " +
                        "LEFT JOIN sys_user u ON u.id = t.owner_id AND u.tenant_id = t.tenant_id AND u.deleted = 0 " +
                        "WHERE t.table_code = ? AND t.tenant_id = ? AND t.deleted = 0 AND t.table_category = ?",
                tableCode, user.tenantId(), cat);
        if (tables.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "目标表不存在");
        Map<String, Object> table = tables.get(0);
        permissions.requireStoredProject(table.get("project_id"), user);
        List<Map<String, Object>> fields = jdbc.queryForList(
                "SELECT f.* FROM dm_target_table_field f WHERE f.table_code = ? AND f.tenant_id = ? AND f.deleted = 0 ORDER BY f.field_code ASC",
                tableCode, user.tenantId());
        table.put("fields", fields);
        return table;
    }

    // ============ 新增表（可带字段） ============
    @Transactional
    public Map<String, Object> createTable(String category, Map<String, Object> body, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "create");
        long projectId = num(body.get("projectId"), "projectId");
        ensureProject(projectId, user);
        String systemCode = text(body.get("systemCode"), "systemCode");
        ensureSystemBelongsToProject(projectId, systemCode, user);
        String tableNameEn = noSpace(body.get("tableNameEn"), "tableNameEn");
        String tableNameCn = noSpace(body.get("tableNameCn"), "tableNameCn");
        ensureTableUnique(user.tenantId(), projectId, systemCode, tableNameEn, tableNameCn, 0L);
        long tableCode = nextId();
        String meaning = opt(body.get("tableMeaning"));
        jdbc.update("INSERT INTO dm_target_table (tenant_id, table_code, project_id, system_code, table_name_en, table_name_cn, table_meaning, table_category, owner_id, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                user.tenantId(), tableCode, projectId, systemCode, tableNameEn, tableNameCn, meaning, cat, user.id(), user.id(), user.id());
        audit(user, "TARGET_TABLE_CREATE", projectId, tableCode);
        List<?> fields = body.get("fields") instanceof List ? (List<?>) body.get("fields") : List.of();
        for (Object f : fields) {
            if (f instanceof Map) addFieldInternal(tableCode, user, (Map<String, Object>) f, projectId, systemCode);
        }
        return getDetail(tableCode, category, user);
    }

    // ============ 修改表信息 ============
    @Transactional
    public Map<String, Object> updateTable(long tableCode, String category, Map<String, Object> body, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "update");
        Map<String, Object> current = requireTable(tableCode, cat, user);
        permissions.requireStoredProject(current.get("project_id"), user);
        permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        long projectId = ((Number) current.get("project_id")).longValue();
        String systemCode = String.valueOf(current.get("system_code"));
        String tableNameEn = noSpace(body.getOrDefault("tableNameEn", current.get("table_name_en")), "tableNameEn");
        String tableNameCn = noSpace(body.getOrDefault("tableNameCn", current.get("table_name_cn")), "tableNameCn");
        ensureTableUnique(user.tenantId(), projectId, systemCode, tableNameEn, tableNameCn, tableCode);
        String meaning = opt(body.getOrDefault("tableMeaning", current.get("table_meaning")));
        jdbc.update("UPDATE dm_target_table SET table_name_en = ?, table_name_cn = ?, table_meaning = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE table_code = ? AND tenant_id = ? AND deleted = 0",
                tableNameEn, tableNameCn, meaning, user.id(), tableCode, user.tenantId());
        audit(user, "TARGET_TABLE_UPDATE", projectId, tableCode);
        return getDetail(tableCode, category, user);
    }

    // ============ 删除表（同步删字段） ============
    @Transactional
    public void deleteTables(Collection<Long> tableCodes, String category, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "delete");
        for (Long tableCode : tableCodes == null ? List.<Long>of() : tableCodes) {
            Map<String, Object> current = requireTable(tableCode, cat, user);
                        long projectId = permissions.requireStoredProject(current.get("project_id"), user);
            permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
            jdbc.update("UPDATE dm_target_table_field SET deleted = 1 WHERE table_code = ? AND tenant_id = ? AND deleted = 0", tableCode, user.tenantId());
            jdbc.update("UPDATE dm_target_table SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE table_code = ? AND tenant_id = ? AND deleted = 0", user.id(), tableCode, user.tenantId());
            audit(user, "TARGET_TABLE_DELETE", projectId, tableCode);
        }
    }

    // ============ 字段：列表 ============
    public List<Map<String, Object>> listFields(long tableCode, String category, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "read");
        Map<String, Object> table = requireTable(tableCode, cat, user);
        permissions.requireStoredProject(table.get("project_id"), user);
        return jdbc.queryForList("SELECT f.* FROM dm_target_table_field f WHERE f.table_code = ? AND f.tenant_id = ? AND f.deleted = 0 ORDER BY f.field_code ASC", tableCode, user.tenantId());
    }

    // ============ 字段：新增 ============
    @Transactional
    public Map<String, Object> addField(long tableCode, String category, Map<String, Object> body, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "update");
        Map<String, Object> table = requireTable(tableCode, cat, user);
        permissions.requireStoredProject(table.get("project_id"), user);
        permissions.requireWrite(user, ((Number) table.get("owner_id")).longValue());
        long projectId = ((Number) table.get("project_id")).longValue();
        String systemCode = String.valueOf(table.get("system_code"));
        Map<String, Object> field = addFieldInternal(tableCode, user, body, projectId, systemCode);
        audit(user, "TARGET_TABLE_FIELD_CREATE", projectId, tableCode);
        return field;
    }

    // ============ 字段：行编辑 ============
    @Transactional
    public Map<String, Object> updateField(long fieldCode, String category, Map<String, Object> body, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "update");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT f.*, t.table_code AS table_code, t.owner_id, t.project_id, t.system_code FROM dm_target_table_field f JOIN dm_target_table t ON t.table_code = f.table_code AND t.tenant_id = f.tenant_id AND t.deleted = 0 WHERE f.field_code = ? AND f.tenant_id = ? AND f.deleted = 0 AND t.table_category = ?",
                fieldCode, user.tenantId(), cat);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不存在");
        Map<String, Object> current = rows.get(0);
        long projectId = permissions.requireStoredProject(current.get("project_id"), user);
        permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        long tableCode = ((Number) current.get("table_code")).longValue();
        String fieldNameEn = noSpace(body.getOrDefault("fieldNameEn", current.get("field_name_en")), "fieldNameEn");
        String fieldNameCn = noSpace(body.getOrDefault("fieldNameCn", current.get("field_name_cn")), "fieldNameCn");
        ensureFieldUnique(user.tenantId(), tableCode, fieldNameEn, fieldNameCn, fieldCode);
        String fieldMeaning = opt(body.getOrDefault("fieldMeaning", current.get("field_meaning")));
        String codeDescription = opt(body.getOrDefault("codeDescription", current.get("code_description")));
        int isKeyField = bool(body.getOrDefault("isKeyField", current.get("is_key_field")));
        String oracleType = opt(body.getOrDefault("oracleType", current.get("oracle_type")));
        String mysqlType = opt(body.getOrDefault("mysqlType", current.get("mysql_type")));
        int isNullable = bool(body.getOrDefault("isNullable", current.get("is_nullable")));
        int isPrimaryKey = bool(body.getOrDefault("isPrimaryKey", current.get("is_primary_key")));
        String dictCode = noSpaceOpt(body.getOrDefault("dictCode", current.get("dict_code")), "dictCode");
        jdbc.update("UPDATE dm_target_table_field SET field_name_en = ?, field_name_cn = ?, field_meaning = ?, code_description = ?, is_key_field = ?, oracle_type = ?, mysql_type = ?, is_nullable = ?, is_primary_key = ?, dict_code = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE field_code = ? AND tenant_id = ? AND deleted = 0",
                fieldNameEn, fieldNameCn, fieldMeaning, codeDescription, isKeyField, oracleType, mysqlType, isNullable, isPrimaryKey, dictCode, user.id(), fieldCode, user.tenantId());
        audit(user, "TARGET_TABLE_FIELD_UPDATE", projectId, tableCode);
        return jdbc.queryForMap("SELECT f.* FROM dm_target_table_field f WHERE f.field_code = ? AND f.tenant_id = ? AND f.deleted = 0", fieldCode, user.tenantId());
    }

    // ============ 字段：删除（单条，级联删空表） ============
    @Transactional
    public void deleteField(long fieldCode, String category, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "delete");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT f.*, t.owner_id, t.project_id FROM dm_target_table_field f JOIN dm_target_table t ON t.table_code = f.table_code AND t.tenant_id = f.tenant_id AND t.deleted = 0 WHERE f.field_code = ? AND f.tenant_id = ? AND f.deleted = 0 AND t.table_category = ?",
                fieldCode, user.tenantId(), cat);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不存在");
        Map<String, Object> current = rows.get(0);
        long projectId = permissions.requireStoredProject(current.get("project_id"), user);
        long tableCode = ((Number) current.get("table_code")).longValue();
        permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        jdbc.update("UPDATE dm_target_table_field SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE field_code = ? AND tenant_id = ? AND deleted = 0", user.id(), fieldCode, user.tenantId());
        audit(user, "TARGET_TABLE_FIELD_DELETE", projectId, tableCode);
        // 字段被全部删除时，同步删除对应的表
        cascadeDeleteTableIfEmpty(tableCode, projectId, cat, user);
    }

    // ============ 字段：批量删除（级联删空表） ============
    @Transactional
    public void deleteFields(Collection<Long> fieldCodes, String category, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "delete");
        Map<Long, Long> affectedTables = new LinkedHashMap<>();
        for (Long fieldCode : fieldCodes == null ? List.<Long>of() : fieldCodes) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT f.*, t.owner_id, t.project_id FROM dm_target_table_field f JOIN dm_target_table t ON t.table_code = f.table_code AND t.tenant_id = f.tenant_id AND t.deleted = 0 WHERE f.field_code = ? AND f.tenant_id = ? AND f.deleted = 0 AND t.table_category = ?",
                    fieldCode, user.tenantId(), cat);
            if (rows.isEmpty()) continue;
            Map<String, Object> current = rows.get(0);
            long projectId = permissions.requireStoredProject(current.get("project_id"), user);
            permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
            long tableCode = ((Number) current.get("table_code")).longValue();
            jdbc.update("UPDATE dm_target_table_field SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE field_code = ? AND tenant_id = ? AND deleted = 0", user.id(), fieldCode, user.tenantId());
            audit(user, "TARGET_TABLE_FIELD_DELETE", projectId, tableCode);
            affectedTables.put(tableCode, projectId);
        }
        // 每张受影响的表：若字段已全删，则同步删除表
        for (Map.Entry<Long, Long> entry : affectedTables.entrySet()) cascadeDeleteTableIfEmpty(entry.getKey(), entry.getValue(), cat, user);
    }

    // 若表下已无未删除字段，则软删除该表；projectId 来自待删字段关联表的实体上下文
    private void cascadeDeleteTableIfEmpty(long tableCode, long projectId, String cat, AuthUser user) {
        Integer remain = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_target_table_field WHERE table_code = ? AND tenant_id = ? AND deleted = 0",
                Integer.class, tableCode, user.tenantId());
        if (remain != null && remain == 0) {
            jdbc.update("UPDATE dm_target_table SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE table_code = ? AND tenant_id = ? AND deleted = 0 AND table_category = ?", user.id(), tableCode, user.tenantId(), cat);
            audit(user, "TARGET_TABLE_DELETE", projectId, tableCode);
        }
    }

    // ============ Excel 导入 ============
    /**
     * T32 决策 D4：导入必须携带并校验 projectId；文件行内“所属项目编码”解析后的项目与请求项目不一致时逐行失败。
     * 写入归属一律采用请求项目，不采信行内自由值。
     */
    @Transactional
    public Map<String, Object> importTables(String category, Long projectId, byte[] file, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "create");
        long scope = permissions.requireProject(projectId, user);
        int accepted = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 模板为空");
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String name = header.getCell(i) == null ? "" : header.getCell(i).getStringCellValue().trim();
                colIndex.put(name, i);
            }
            Long currentProjectId = null;
            String currentSystemCode = null;
            Map<String, Object> currentTableBody = null;
            List<Map<String, Object>> currentFields = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String projCode = cell(row, colIndex.getOrDefault("所属项目编码", -1));
                String sysCode = cell(row, colIndex.getOrDefault("系统编号", -1));
                String tableNameEn = cell(row, colIndex.getOrDefault("表英文名称", -1)).trim();
                String tableNameCn = cell(row, colIndex.getOrDefault("表中文名称", -1)).trim();
                if (tableNameEn.isEmpty() && tableNameCn.isEmpty()) continue;
                try {
                    Long rowProjectId = resolveProject(projCode, user);
                    if (rowProjectId.longValue() != scope) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "所属项目 " + projCode.trim() + " 与所选项目不一致");
                    }
                    String normalizedSystemCode = sysCode.trim();
                    String normalizedTableNameEn = tableNameEn;
                    String normalizedTableNameCn = tableNameCn;
                    boolean tableChanged = !Objects.equals(rowProjectId, currentProjectId)
                            || !normalizedSystemCode.equals(Optional.ofNullable(currentSystemCode).orElse(""))
                            || currentTableBody == null
                            || !normalizedTableNameEn.equals(String.valueOf(currentTableBody.get("tableNameEn")))
                            || !normalizedTableNameCn.equals(String.valueOf(currentTableBody.get("tableNameCn")));
                    if (tableChanged) {
                        // flush previous table
                        if (currentTableBody != null) {
                            if (createTableSafely(cat, currentTableBody, currentFields, user, errors)) accepted++;
                            else failed++;
                        }
                        currentProjectId = rowProjectId;
                        currentSystemCode = normalizedSystemCode;
                        currentTableBody = new LinkedHashMap<>();
                        currentTableBody.put("projectId", rowProjectId);
                        currentTableBody.put("systemCode", currentSystemCode);
                        currentTableBody.put("tableNameEn", normalizedTableNameEn);
                        currentTableBody.put("tableNameCn", normalizedTableNameCn);
                        currentTableBody.put("tableMeaning", cell(row, colIndex.getOrDefault("表含义", -1)).trim());
                        currentFields = new ArrayList<>();
                    }
                    Map<String, Object> field = new LinkedHashMap<>();
                    field.put("fieldNameEn", cell(row, colIndex.getOrDefault("字段英文名称", -1)).trim());
                    field.put("fieldNameCn", cell(row, colIndex.getOrDefault("字段中文名称", -1)).trim());
                    field.put("fieldMeaning", cell(row, colIndex.getOrDefault("字段含义", -1)).trim());
                    field.put("codeDescription", cell(row, colIndex.getOrDefault("码值说明", -1)).trim());
                    field.put("isKeyField", yes(cell(row, colIndex.getOrDefault("是否关键栏位", -1))));
                    field.put("oracleType", cell(row, colIndex.getOrDefault("ORACLE字段类型", -1)).trim());
                    field.put("mysqlType", cell(row, colIndex.getOrDefault("mysql字段类型", -1)).trim());
                    field.put("isNullable", yes(cell(row, colIndex.getOrDefault("是否可空", -1))));
                    field.put("isPrimaryKey", yes(cell(row, colIndex.getOrDefault("是否主键", -1))));
                    field.put("dictCode", cell(row, colIndex.getOrDefault("数据字典编号", -1)).trim());
                    currentFields.add(field);
                } catch (BusinessException ex) {
                    failed++;
                    errors.add("行 " + (r + 1) + ": " + ex.getMessage());
                }
            }
            if (currentTableBody != null) {
                if (createTableSafely(cat, currentTableBody, currentFields, user, errors)) accepted++;
                else failed++;
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 解析失败");
        }
        return Map.of("accepted", accepted, "failed", failed, "errors", errors);
    }

    private boolean createTableSafely(String category, Map<String, Object> tableBody, List<Map<String, Object>> fields, AuthUser user, List<String> errors) {
        try {
            validateImportFields(fields);
            tableBody.put("fields", fields);
            createTable(category, tableBody, user);
            return true;
        } catch (BusinessException ex) {
            errors.add("表 " + tableBody.get("tableNameEn") + ": " + ex.getMessage());
            return false;
        }
    }

    private void validateImportFields(List<Map<String, Object>> fields) {
        Set<String> englishNames = new HashSet<>();
        Set<String> chineseNames = new HashSet<>();
        for (Map<String, Object> field : fields) {
            String englishName = noSpace(field.get("fieldNameEn"), "fieldNameEn");
            String chineseName = noSpace(field.get("fieldNameCn"), "fieldNameCn");
            if (!englishNames.add(englishName)) {
                throw new BusinessException(ErrorCode.CONFLICT, "字段英文名称在该表下已存在");
            }
            if (!chineseNames.add(chineseName)) {
                throw new BusinessException(ErrorCode.CONFLICT, "字段中文名称在该表下已存在");
            }
            noSpaceOpt(field.get("dictCode"), "dictCode");
        }
    }

    private Long resolveProject(String projCode, AuthUser user) {
        if (projCode == null || projCode.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "所属项目编码不能为空");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM pm_project WHERE project_code = ? AND tenant_id = ? AND deleted = 0", projCode.trim(), user.tenantId());
        if (rows.isEmpty()) {
            try {
                rows = jdbc.queryForList("SELECT id FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Long.parseLong(projCode.trim()), user.tenantId());
            } catch (NumberFormatException ignore) {
                rows = List.of();
            }
        }
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在: " + projCode);
        Long resolved = ((Number) rows.get(0).get("id")).longValue();
        // T32：模板中写了不属于当前调用者可访问项目的编码，同样按行失败，不得越项目写入。
        permissions.requireAccessible(resolved, user);
        return resolved;
    }

    // ============ Excel 导出 ============
    public byte[] exportTables(String category, Map<String, Object> params, List<Long> fieldCodes, AuthUser user) {
        String cat = categoryOf(category);
        permissions.requireCategoryPermission(user, cat, "read");
        // T32：导出同样强制项目范围，按 fieldCode 导出时仍限定在该项目内，避免跨项目脱离。
        long scope = permissions.requireProject(parseProjectId(params == null ? null : params.get("projectId")), user);
        List<Map<String, Object>> rows;
        if (fieldCodes != null && !fieldCodes.isEmpty()) {
            String placeholders = fieldCodes.stream().map(i -> "?").collect(Collectors.joining(","));
            rows = jdbc.queryForList(
                    "SELECT f.field_code, t.table_code AS table_code, f.field_name_en, f.field_name_cn, f.field_meaning, f.code_description, f.is_key_field, f.oracle_type, f.mysql_type, f.is_nullable, f.is_primary_key, f.dict_code, " +
                            "t.project_id, t.system_code, t.table_name_en, t.table_name_cn, t.table_meaning, p.project_name, ps.business_group_name AS business_group, ps.name AS system_name, u.display_name AS owner_name, t.created_at, t.updated_at " +
                            "FROM dm_target_table_field f JOIN dm_target_table t ON t.table_code = f.table_code AND t.tenant_id = f.tenant_id AND t.deleted = 0 " +
                    "LEFT JOIN pm_project p ON p.id = t.project_id AND p.tenant_id = t.tenant_id AND p.deleted = 0 " +
                            "LEFT JOIN dm_component dc ON dc.tenant_id = t.tenant_id AND dc.project_id = t.project_id AND dc.physical_subsystem_code = t.system_code AND dc.deleted = 0 " +
                            "LEFT JOIN arch_physical_subsystem ps ON ps.tenant_id = dc.tenant_id AND ps.code = dc.physical_subsystem_code AND ps.deleted = 0 " +
                            "LEFT JOIN sys_user u ON u.id = t.owner_id AND u.tenant_id = t.tenant_id AND u.deleted = 0 " +
                            "WHERE f.tenant_id = ? AND f.deleted = 0 AND t.table_category = ? AND t.project_id = ? AND f.field_code IN (" + placeholders + ") ORDER BY t.table_code ASC, f.field_code ASC",
                    concat(List.of(user.tenantId(), cat, scope), fieldCodes).toArray());
        } else {
            // 复用 list 的筛选逻辑但导出全量（不分页）
            Map<String, Object> p = params == null ? Map.of() : params;
            StringBuilder sql = new StringBuilder(
                            "SELECT f.field_code, t.table_code AS table_code, f.field_name_en, f.field_name_cn, f.field_meaning, f.code_description, f.is_key_field, f.oracle_type, f.mysql_type, f.is_nullable, f.is_primary_key, f.dict_code, " +
                            "t.project_id, t.system_code, t.table_name_en, t.table_name_cn, t.table_meaning, p.project_name, ps.business_group_name AS business_group, ps.name AS system_name, u.display_name AS owner_name, t.created_at, t.updated_at " +
                            "FROM dm_target_table_field f JOIN dm_target_table t ON t.table_code = f.table_code AND t.tenant_id = f.tenant_id AND t.deleted = 0 " +
                            "LEFT JOIN pm_project p ON p.id = t.project_id AND p.tenant_id = t.tenant_id AND p.deleted = 0 " +
                            "LEFT JOIN dm_component dc ON dc.tenant_id = t.tenant_id AND dc.project_id = t.project_id AND dc.physical_subsystem_code = t.system_code AND dc.deleted = 0 " +
                            "LEFT JOIN arch_physical_subsystem ps ON ps.tenant_id = dc.tenant_id AND ps.code = dc.physical_subsystem_code AND ps.deleted = 0 " +
                            "LEFT JOIN sys_user u ON u.id = t.owner_id AND u.tenant_id = t.tenant_id AND u.deleted = 0 " +
                            "WHERE f.tenant_id = ? AND f.deleted = 0 AND t.table_category = ?");
            List<Object> args = new ArrayList<>(List.of(user.tenantId(), cat));
            sql.append(" AND t.project_id = ?");
            args.add(scope);
            Object systemCode = p.get("systemCode");
            if (systemCode != null && !String.valueOf(systemCode).isBlank()) { sql.append(" AND t.system_code = ?"); args.add(String.valueOf(systemCode).trim()); }
            Object isKeyField = p.get("isKeyField");
            if (isKeyField != null && !String.valueOf(isKeyField).isBlank()) { sql.append(" AND f.is_key_field = ?"); args.add(Integer.parseInt(String.valueOf(isKeyField))); }
            Object dictCode = p.get("dictCode");
            if (dictCode != null && !String.valueOf(dictCode).isBlank()) { sql.append(" AND f.dict_code LIKE ?"); args.add("%" + String.valueOf(dictCode).trim() + "%"); }
            Object tableKeyword = p.get("tableKeyword");
            if (tableKeyword != null && !String.valueOf(tableKeyword).isBlank()) { sql.append(" AND (t.table_name_en LIKE ? OR t.table_name_cn LIKE ?)"); String k = "%" + String.valueOf(tableKeyword).trim() + "%"; args.add(k); args.add(k); }
            Object fieldKeyword = p.get("fieldKeyword");
            if (fieldKeyword != null && !String.valueOf(fieldKeyword).isBlank()) { sql.append(" AND (f.field_name_en LIKE ? OR f.field_name_cn LIKE ?)"); String k = "%" + String.valueOf(fieldKeyword).trim() + "%"; args.add(k); args.add(k); }
            sql.append(" ORDER BY t.table_code ASC, f.field_code ASC");
            rows = jdbc.queryForList(sql.toString(), args.toArray());
        }
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("表结构");
            Row header = sheet.createRow(0);
            String[] headers = {"表编号", "表英文名称", "表中文名称", "表含义", "所属项目", "所属事业群", "所属系统编号", "系统名称",
                    "字段编号", "字段英文名称", "字段中文名称", "字段含义", "码值说明", "是否关键栏位", "ORACLE字段类型", "mysql字段类型", "是否可空", "是否主键", "数据字典编号",
                    "创建人", "创建时间", "更新时间"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int r = 1;
            for (Map<String, Object> row : rows) {
                Row xr = sheet.createRow(r++);
                xr.createCell(0).setCellValue(str(row.get("table_code")));
                xr.createCell(1).setCellValue(str(row.get("table_name_en")));
                xr.createCell(2).setCellValue(str(row.get("table_name_cn")));
                xr.createCell(3).setCellValue(str(row.get("table_meaning")));
                xr.createCell(4).setCellValue(str(row.get("project_name")));
                xr.createCell(5).setCellValue(str(row.get("business_group")));
                xr.createCell(6).setCellValue(str(row.get("system_code")));
                xr.createCell(7).setCellValue(str(row.get("system_name")));
                xr.createCell(8).setCellValue(str(row.get("field_code")));
                xr.createCell(9).setCellValue(str(row.get("field_name_en")));
                xr.createCell(10).setCellValue(str(row.get("field_name_cn")));
                xr.createCell(11).setCellValue(str(row.get("field_meaning")));
                xr.createCell(12).setCellValue(str(row.get("code_description")));
                xr.createCell(13).setCellValue(yes(((Number) row.getOrDefault("is_key_field", 0)).intValue()));
                xr.createCell(14).setCellValue(str(row.get("oracle_type")));
                xr.createCell(15).setCellValue(str(row.get("mysql_type")));
                xr.createCell(16).setCellValue(yes(((Number) row.getOrDefault("is_nullable", 1)).intValue()));
                xr.createCell(17).setCellValue(yes(((Number) row.getOrDefault("is_primary_key", 0)).intValue()));
                xr.createCell(18).setCellValue(str(row.get("dict_code")));
                xr.createCell(19).setCellValue(str(row.get("owner_name")));
                xr.createCell(20).setCellValue(str(row.get("created_at")));
                xr.createCell(21).setCellValue(str(row.get("updated_at")));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    // ============ 模板下载 ============
    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("目标表结构模板");
            Row header = sheet.createRow(0);
            for (int i = 0; i < IMPORT_COLUMNS.size(); i++) header.createCell(i).setCellValue(IMPORT_COLUMNS.get(i));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
        }
    }

    // ============ 内部工具 ============
    private Map<String, Object> addFieldInternal(long tableCode, AuthUser user, Map<String, Object> body, long projectId, String systemCode) {
        String fieldNameEn = noSpace(body.get("fieldNameEn"), "fieldNameEn");
        String fieldNameCn = noSpace(body.get("fieldNameCn"), "fieldNameCn");
        ensureFieldUnique(user.tenantId(), tableCode, fieldNameEn, fieldNameCn, 0L);
        long fieldCode = nextId();
        String fieldMeaning = opt(body.get("fieldMeaning"));
        String codeDescription = opt(body.get("codeDescription"));
        int isKeyField = bool(body.get("isKeyField"));
        String oracleType = opt(body.get("oracleType"));
        String mysqlType = opt(body.get("mysqlType"));
        int isNullable = bool(body.get("isNullable"));
        int isPrimaryKey = bool(body.get("isPrimaryKey"));
        String dictCode = noSpaceOpt(body.get("dictCode"), "dictCode");
        jdbc.update("INSERT INTO dm_target_table_field (tenant_id, field_code, table_code, field_name_en, field_name_cn, field_meaning, code_description, is_key_field, oracle_type, mysql_type, is_nullable, is_primary_key, dict_code, owner_id, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                user.tenantId(), fieldCode, tableCode, fieldNameEn, fieldNameCn, fieldMeaning, codeDescription, isKeyField, oracleType, mysqlType, isNullable, isPrimaryKey, dictCode, user.id(), user.id(), user.id());
        return jdbc.queryForMap("SELECT f.* FROM dm_target_table_field f WHERE f.field_code = ? AND f.tenant_id = ? AND f.deleted = 0", fieldCode, user.tenantId());
    }

    private Map<String, Object> requireTable(long tableCode, String cat, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM dm_target_table WHERE table_code = ? AND tenant_id = ? AND deleted = 0 AND table_category = ?", tableCode, user.tenantId(), cat);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "目标表不存在");
        return rows.get(0);
    }

    private void ensureProject(long projectId, AuthUser user) {
        permissions.requireAccessible(projectId, user);
    }

    /** T32：从查询参数解析 projectId；空值交由 requireProject 统一返回 400，非法数字同样返回 400。 */
    private static Long parseProjectId(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId 必须为数字");
        }
    }

    private void ensureSystemBelongsToProject(long projectId, String systemCode, AuthUser user) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_component c JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0 WHERE c.project_id = ? AND c.physical_subsystem_code = ? AND c.tenant_id = ? AND c.deleted = 0",
                Integer.class, projectId, systemCode, user.tenantId());
        if (c == null || c == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "系统编号不属于所选项目下的组件清单");
    }
    private void ensureTableUnique(long tenantId, long projectId, String systemCode, String tableNameEn, String tableNameCn, long excludeCode) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dm_target_table WHERE tenant_id = ? AND project_id = ? AND system_code = ? AND table_name_en = ? AND table_code <> ?", Integer.class, tenantId, projectId, systemCode, tableNameEn, excludeCode) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "表英文名称在该项目+系统编号下已存在（含已删除记录）");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dm_target_table WHERE tenant_id = ? AND project_id = ? AND system_code = ? AND table_name_cn = ? AND table_code <> ?", Integer.class, tenantId, projectId, systemCode, tableNameCn, excludeCode) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "表中文名称在该项目+系统编号下已存在（含已删除记录）");
    }

    private void ensureFieldUnique(long tenantId, long tableCode, String fieldNameEn, String fieldNameCn, long excludeCode) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dm_target_table_field WHERE tenant_id = ? AND table_code = ? AND field_name_en = ? AND field_code <> ?", Integer.class, tenantId, tableCode, fieldNameEn, excludeCode) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "字段英文名称在该表下已存在（含已删除记录）");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dm_target_table_field WHERE tenant_id = ? AND table_code = ? AND field_name_cn = ? AND field_code <> ?", Integer.class, tenantId, tableCode, fieldNameCn, excludeCode) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "字段中文名称在该表下已存在（含已删除记录）");
    }

    private void audit(AuthUser user, String op, long projectId, long code) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, ?, 'TARGET_TABLE', ?)", user.tenantId(), user.id(), projectId, op, code);
    }

    private String noSpace(Object v, String field) {
        if (v == null || String.valueOf(v).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        String s = String.valueOf(v).trim();
        if (s.contains(" ")) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不允许含空格");
        return s;
    }

    private String noSpaceOpt(Object v, String field) {
        if (v == null || String.valueOf(v).trim().isEmpty()) return null;
        String s = String.valueOf(v).trim();
        if (s.contains(" ")) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不允许含空格");
        return s;
    }

    private String text(Object v, String field) {
        if (v == null || String.valueOf(v).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        return String.valueOf(v).trim();
    }

    private String opt(Object v) {
        return v == null ? null : String.valueOf(v).trim().isEmpty() ? null : String.valueOf(v).trim();
    }

    private long num(Object v, String field) {
        try { return Long.parseLong(text(v, field)); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 必须为数字"); }
    }

    private int bool(Object v) {
        if (v == null) return 0;
        String s = String.valueOf(v).trim();
        return (s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("Y") || s.equalsIgnoreCase("是")) ? 1 : 0;
    }

    private String cell(Row row, int idx) {
        if (row == null || idx < 0) return "";
        var c = row.getCell(idx);
        if (c == null) return "";
        return switch (c.getCellType()) {
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> c.getStringCellValue();
        };
    }

    private String str(Object v) { return v == null ? "" : String.valueOf(v); }
    private String yes(int v) { return v == 1 ? "是" : "否"; }
    private boolean yes(String s) { return s != null && (s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("Y") || s.equalsIgnoreCase("是")); }
    private List<Object> concat(List<Object> a, List<Long> b) { List<Object> r = new ArrayList<>(a); r.addAll(b); return r; }
}

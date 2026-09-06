package com.ccb.datamigration.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 迁移检核规则专属服务（并入 REQ-20260820-031，对标迁移方案/迁移映射域化范式）。
 *
 * <p>存储于 {@code dm_rule}（V194：删除 doc_code/doc_name，重建 check_target_type/rule_category/rule_code
 * 及表/字段与说明列）。一条规则即一行业务记录；规则编码由用户录入且全局唯一；检核目标类型与检核规则大类
 * 由“系统管理/参数管理”维护；关联系统为当前项目启用 dm_component。支持多维组合筛选、分页、单条新增/编辑、
 * Excel 批量导入（逐行校验、失败跳过）、模板下载、条件化批量导出、逻辑删除与统一回收站。
 */
@Service
public class RuleService {
    private static final String CONTENT_TYPE = "RULE";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final int MAX_RULE_CODE_LENGTH = 96;
    private static final int MAX_OPTIONAL_FIELD_LENGTH = 255;
    private static final int MAX_DESC_LENGTH = 500;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
    private static final String[] LIST_COLUMNS = {
            "项目名称", "检核目标类型", "检核规则大类", "系统编号", "系统名称",
            "表英文名", "表中文名", "字段英文名称", "字段中文名称",
            "规则编码", "规则编码说明", "检核规则说明"
    };
    private static final String[] TEMPLATE_COLUMNS = {
            "表英文名", "表中文名", "字段英文名称", "字段中文名称",
            "规则编码", "规则编码说明", "检核规则说明"
    };

    private static final String SYSTEM_JOIN =
            " LEFT JOIN dm_component c ON c.tenant_id = a.tenant_id AND c.project_id = a.project_id AND c.system_code = a.system_code " +
            " LEFT JOIN arch_physical_subsystem sys ON sys.tenant_id = c.tenant_id AND sys.code = c.system_code AND sys.deleted = 0 ";
    private static final String SELECT_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, a.check_target_type, a.rule_category, a.system_code, " +
            "sys.short_name AS system_short_name, sys.name AS system_name, " +
            "a.rule_code, a.rule_code_desc, a.rule_description, " +
            "a.table_name_en, a.table_name_cn, a.field_name_en, a.field_name_cn, " +
            "a.owner_id, a.created_by, a.created_at, a.updated_by, a.updated_at ";
    private static final String RECYCLE_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, 'RULE' AS asset_type, " +
            "a.rule_code AS asset_code, a.rule_code AS asset_name, " +
            "a.check_target_type, a.rule_category, a.system_code, sys.name AS system_name, " +
            "a.table_name_en, a.table_name_cn, a.field_name_en, a.field_name_cn, " +
            "a.rule_code_desc, a.rule_description, a.owner_id, " +
            "a.created_at, a.updated_at, a.deleted_by, a.deleted_at ";

    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;
    private final DataMigrationCodeValueService codeValues;

    public RuleService(JdbcTemplate jdbc, DataMigrationPermissionService permissions,
                       UserDirectoryPort userDirectory, DataMigrationCodeValueService codeValues) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
        this.codeValues = codeValues;
    }

    /** 列表：租户 + 项目恒定过滤，检核目标类型/规则大类/关联系统/规则关键字/表字段关键字组合筛选并服务端分页。 */
    public PageResult<Map<String, Object>> list(Long projectId, String checkTargetType, String ruleCategory,
                                                String systemCode, String ruleKeyword, String keyword,
                                                int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM dm_rule a WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> countArgs = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(countSql, countArgs, scope, checkTargetType, ruleCategory, systemCode, ruleKeyword, keyword);
        Long total = jdbc.queryForObject(countSql.toString(), Long.class, countArgs.toArray());

        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append("FROM dm_rule a ").append(SYSTEM_JOIN)
                .append("LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, scope, checkTargetType, ruleCategory, systemCode, ruleKeyword, keyword);
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((long) (safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        decorateCodeLabels(records, user);
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total == null ? 0L : total, safePage, safeSize);
    }

    /** 详情：单条完整信息（点击规则编码/编码说明查看）。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT_COLUMNS + "FROM dm_rule a " + SYSTEM_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 0", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移检核规则不存在");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateCodeLabels(rows, user);
        decorateUsers(rows, user.tenantId());
        return row;
    }

    /** 关联系统下拉：{@code projectId} 必填，仅返回该项目 {@code dm_component} 启用系统的系统编号，与迁移方案页口径一致。 */

    /** 新增单条：全量字段录入；ruleCode 全局唯一，目标类型/规则大类必须是启用参数，系统归属当前项目。 */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        permissions.requireAccessible(projectId, user);
        String checkTargetType = requireText(body.get("checkTargetType"), "检核目标类型不能为空");
        String ruleCategory = requireText(body.get("ruleCategory"), "检核规则大类不能为空");
        String systemCode = requireText(body.get("systemCode"), "关联系统不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_RULE_TARGET_TYPE, "检核目标类型", checkTargetType, user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_RULE_CATEGORY, "检核规则大类", ruleCategory, user);
        ensureSystemBelongsToProject(systemCode, projectId, user);
        String ruleCode = requireRuleCode(body.get("ruleCode"));
        if (ruleCodeExists(ruleCode, user.tenantId(), null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "规则编码已存在");
        }

        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_rule (id, tenant_id, project_id, system_code, check_target_type, rule_category, " +
                    "rule_code, rule_code_desc, rule_description, table_name_en, table_name_cn, field_name_en, field_name_cn, " +
                    "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, user.tenantId(), projectId, systemCode.trim(), checkTargetType, ruleCategory,
                    ruleCode, optionalText(body.get("ruleCodeDesc")),
                    optionalText(body.get("ruleDescription")),
                    optionalText(body.get("tableNameEn")), optionalText(body.get("tableNameCn")),
                    optionalText(body.get("fieldNameEn")), optionalText(body.get("fieldNameCn")),
                    user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "规则编码已存在");
        }
        audit(user, "RULE_CREATE", projectId, id);
        return detail(id, user);
    }

    /** 编辑单条：归属恒取库中记录；可选字段缺省保留原值；ruleCode 在排除自身后仍全局唯一。 */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> existing = findRaw(id, user.tenantId());
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(existing.get("project_id"), user);

        String checkTargetType = body.containsKey("checkTargetType")
                ? requireText(body.get("checkTargetType"), "检核目标类型不能为空") : (String) existing.get("check_target_type");
        String ruleCategory = body.containsKey("ruleCategory")
                ? requireText(body.get("ruleCategory"), "检核规则大类不能为空") : (String) existing.get("rule_category");
        String systemCode = body.containsKey("systemCode")
                ? requireText(body.get("systemCode"), "关联系统不能为空") : (String) existing.get("system_code");
        codeValues.requireActive(DataMigrationCodeValueService.DM_RULE_TARGET_TYPE, "检核目标类型", checkTargetType, user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_RULE_CATEGORY, "检核规则大类", ruleCategory, user);
        ensureSystemBelongsToProject(systemCode, projectId, user);

        String ruleCode = body.containsKey("ruleCode")
                ? requireRuleCode(body.get("ruleCode")) : (String) existing.get("rule_code");
        if (ruleCodeExists(ruleCode, user.tenantId(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "规则编码已存在");
        }

        jdbc.update("UPDATE dm_rule SET check_target_type = ?, rule_category = ?, system_code = ?, rule_code = ?, " +
                        "rule_code_desc = ?, rule_description = ?, table_name_en = ?, table_name_cn = ?, " +
                        "field_name_en = ?, field_name_cn = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id = ? AND tenant_id = ? AND deleted = 0",
                checkTargetType, ruleCategory, systemCode, ruleCode,
                optionalTextOrDefault(body, existing.get("rule_code_desc"), "ruleCodeDesc"),
                optionalTextOrDefault(body, existing.get("rule_description"), "ruleDescription"),
                optionalTextOrDefault(body, existing.get("table_name_en"), "tableNameEn"),
                optionalTextOrDefault(body, existing.get("table_name_cn"), "tableNameCn"),
                optionalTextOrDefault(body, existing.get("field_name_en"), "fieldNameEn"),
                optionalTextOrDefault(body, existing.get("field_name_cn"), "fieldNameCn"),
                user.id(), id, user.tenantId());
        audit(user, "RULE_UPDATE", projectId, id);
        return detail(id, user);
    }

    /** 批量逻辑删除：记录删除人/时间，进入统一回收站；仅可删除本人（管理员任何可删）。 */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : normalizeIds(ids)) {
            Map<String, Object> existing = findRaw(id, user.tenantId());
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
            int changed = jdbc.update("UPDATE dm_rule SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP, " +
                    "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    user.id(), user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移检核规则状态已变化，请刷新后重试");
            audit(user, "RULE_DELETE", projectId, id);
        }
    }

    // ============ Excel 模板 / 批量导入 / 条件化导出 ============

    public byte[] downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移检核规则");
            writeHeaderRow(sheet, TEMPLATE_COLUMNS);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
        }
    }

    /** 条件化批量下载：导出列与列表展示字段一致，仅服务当前项目内活动行。 */
    public byte[] export(Long projectId, String checkTargetType, String ruleCategory, String systemCode,
                         String ruleKeyword, String keyword, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移检核规则");
            writeHeaderRow(sheet, LIST_COLUMNS);
            StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                    .append("FROM dm_rule a ").append(SYSTEM_JOIN)
                    .append("LEFT JOIN pm_project p ON a.project_id = p.id AND a.tenant_id = p.tenant_id AND p.deleted = 0 ")
                    .append("WHERE a.tenant_id = ? AND a.deleted = 0");
            List<Object> args = new ArrayList<>(List.of(user.tenantId()));
            appendFilters(sql, args, scope, checkTargetType, ruleCategory, systemCode, ruleKeyword, keyword);
            sql.append(" ORDER BY a.rule_code ASC");
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            decorateCodeLabels(rows, user);
            int index = 1;
            for (Map<String, Object> data : rows) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(String.valueOf(data.getOrDefault("project_name", "")));
                row.createCell(1).setCellValue(String.valueOf(data.getOrDefault("check_target_type_name", data.getOrDefault("check_target_type", ""))));
                row.createCell(2).setCellValue(String.valueOf(data.getOrDefault("rule_category_name", data.getOrDefault("rule_category", ""))));
                row.createCell(3).setCellValue(String.valueOf(data.getOrDefault("system_code", "")));
                row.createCell(4).setCellValue(String.valueOf(data.getOrDefault("system_name", "")));
                row.createCell(5).setCellValue(String.valueOf(data.getOrDefault("table_name_en", "")));
                row.createCell(6).setCellValue(String.valueOf(data.getOrDefault("table_name_cn", "")));
                row.createCell(7).setCellValue(String.valueOf(data.getOrDefault("field_name_en", "")));
                row.createCell(8).setCellValue(String.valueOf(data.getOrDefault("field_name_cn", "")));
                row.createCell(9).setCellValue(String.valueOf(data.getOrDefault("rule_code", "")));
                row.createCell(10).setCellValue(String.valueOf(data.getOrDefault("rule_code_desc", "")));
                row.createCell(11).setCellValue(String.valueOf(data.getOrDefault("rule_description", "")));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "XLSX 导出失败");
        }
    }

    /**
     * Excel 批量导入：前置维度由请求参数固定（所属项目/检核目标类型/检核规则大类/关联系统）；
     * 逐行解析模板列，规则编码必填且全局唯一，校验失败的行跳过并逐行报错，不影响其余行入库。
     */
    public Map<String, Object> importRules(Long projectId, String checkTargetType, String ruleCategory,
                                           String systemCode, MultipartFile file, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        String targetType = requireText(checkTargetType, "检核目标类型不能为空");
        String category = requireText(ruleCategory, "检核规则大类不能为空");
        String sysCode = requireText(systemCode, "关联系统不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_RULE_TARGET_TYPE, "检核目标类型", targetType, user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_RULE_CATEGORY, "检核规则大类", category, user);
        ensureSystemBelongsToProject(sysCode, scope, user);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能超过 50 MB");

        int rows = 0;
        int accepted = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 超过 5000 行");
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                rows++;
                try {
                    Row row = sheet.getRow(i);
                    String tableNameEn = cell(row, 0, formatter);
                    String tableNameCn = cell(row, 1, formatter);
                    String fieldNameEn = cell(row, 2, formatter);
                    String fieldNameCn = cell(row, 3, formatter);
                    String ruleCode = cell(row, 4, formatter);
                    String ruleCodeDesc = cell(row, 5, formatter);
                    String ruleDescription = cell(row, 6, formatter);
                    if (ruleCode.isBlank()) throw new IllegalArgumentException("规则编码为空");
                    if (ruleCode.length() > MAX_RULE_CODE_LENGTH) throw new IllegalArgumentException("规则编码超过 96 字符");
                    if (!seenCodes.add(ruleCode)) throw new IllegalArgumentException("文件内规则编码重复");
                    if (ruleCodeExists(ruleCode, user.tenantId(), null)) throw new IllegalArgumentException("规则编码已存在");
                    long id = nextId();
                    jdbc.update("INSERT INTO dm_rule (id, tenant_id, project_id, system_code, check_target_type, rule_category, " +
                                    "rule_code, rule_code_desc, rule_description, table_name_en, table_name_cn, field_name_en, field_name_cn, " +
                                    "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            id, user.tenantId(), scope, sysCode, targetType, category,
                            ruleCode, blankAsNull(ruleCodeDesc), blankAsNull(ruleDescription),
                            tableNameEn, tableNameCn, fieldNameEn, fieldNameCn,
                            user.id(), user.id(), user.id());
                    accepted++;
                    audit(user, "RULE_IMPORT", scope, id);
                } catch (Exception ex) {
                    errors.add("第 " + (i + 1) + " 行：" + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件解析失败");
        }
        return Map.of("rows", rows, "accepted", accepted, "failed", errors.size(), "errors", errors);
    }

    // ============ 统一回收站 SPI ============

    public long countRecycleBin(long projectId, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dm_rule a WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<Map<String, Object>> fetchRecycleBinPage(long projectId, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        if (limit <= 0) return List.of();
        StringBuilder sql = new StringBuilder(RECYCLE_COLUMNS)
                .append("FROM dm_rule a ").append(SYSTEM_JOIN)
                .append("LEFT JOIN pm_project p ON a.project_id = p.id AND a.tenant_id = p.tenant_id AND p.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        sql.append(" ORDER BY a.rule_code ASC, a.id ASC LIMIT ?");
        args.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        decorateUsers(rows, user.tenantId());
        return rows;
    }

    public Map<String, Object> findRecycleBinDetail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                RECYCLE_COLUMNS + "FROM dm_rule a " + SYSTEM_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND a.tenant_id = p.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 1", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移检核规则不存在于回收站");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(rows, user.tenantId());
        return row;
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            try {
                int changed = jdbc.update("UPDATE dm_rule SET deleted = 0, deleted_by = NULL, deleted_at = NULL, " +
                        "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 1",
                        id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移检核规则状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "规则编码已存在活动记录，无法恢复");
            }
            audit(user, "RULE_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            int changed = jdbc.update("DELETE FROM dm_rule WHERE id = ? AND tenant_id = ? AND deleted = 1",
                    id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移检核规则状态已变化，请刷新后重试");
            audit(user, "RULE_PURGE", projectId, id);
        }
    }

    // ============ 私有辅助 ============

    private void appendFilters(StringBuilder sql, List<Object> args, long projectId, String checkTargetType,
                               String ruleCategory, String systemCode, String ruleKeyword, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (checkTargetType != null && !checkTargetType.isBlank()) {
            sql.append(" AND a.check_target_type = ?");
            args.add(checkTargetType.trim());
        }
        if (ruleCategory != null && !ruleCategory.isBlank()) {
            sql.append(" AND a.rule_category = ?");
            args.add(ruleCategory.trim());
        }
        if (systemCode != null && !systemCode.isBlank()) {
            sql.append(" AND a.system_code = ?");
            args.add(systemCode.trim());
        }
        if (ruleKeyword != null && !ruleKeyword.isBlank()) {
            String value = "%" + ruleKeyword.trim() + "%";
            sql.append(" AND (a.rule_code LIKE ? OR a.rule_code_desc LIKE ? OR a.rule_description LIKE ?)");
            args.add(value);
            args.add(value);
            args.add(value);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.table_name_cn LIKE ? OR a.table_name_en LIKE ? OR a.field_name_en LIKE ?)");
            args.add(value);
            args.add(value);
            args.add(value);
        }
    }

    private void appendRecycleFilters(StringBuilder sql, List<Object> args, long projectId, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.rule_code LIKE ? OR a.rule_code_desc LIKE ?)");
            args.add(value);
            args.add(value);
        }
    }

    /** 新列表/详情返回的码值名称（用启用参数项映射；停用/未知值回退为码值本身，保证存量可读）。 */
    private void decorateCodeLabels(List<Map<String, Object>> rows, AuthUser user) {
        if (rows.isEmpty()) return;
        Map<String, String> targetLabels = optionsToMap(codeValues.options(DataMigrationCodeValueService.DM_RULE_TARGET_TYPE, user));
        Map<String, String> categoryLabels = optionsToMap(codeValues.options(DataMigrationCodeValueService.DM_RULE_CATEGORY, user));
        for (Map<String, Object> row : rows) {
            Object target = row.get("check_target_type");
            Object category = row.get("rule_category");
            row.put("check_target_type_name", targetLabels.getOrDefault(String.valueOf(target), String.valueOf(target)));
            row.put("rule_category_name", categoryLabels.getOrDefault(String.valueOf(category), String.valueOf(category)));
        }
    }

    private Map<String, String> optionsToMap(List<Map<String, Object>> options) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> option : options) {
            Object value = option.get("value");
            Object label = option.get("label");
            if (value != null) map.put(String.valueOf(value), label == null ? null : String.valueOf(label));
        }
        return map;
    }

    private void ensureSystemBelongsToProject(String systemCode, long projectId, AuthUser user) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_component c WHERE c.tenant_id = ? AND c.project_id = ? AND c.system_code = ? AND c.enabled = 1",
                Integer.class, user.tenantId(), projectId, systemCode);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关联系统不存在或不属于当前项目");
        }
    }

    private boolean ruleCodeExists(String ruleCode, long tenantId, Long excludeId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dm_rule WHERE tenant_id = ? AND rule_code = ?");
        List<Object> args = new ArrayList<>(List.of(tenantId, ruleCode));
        if (excludeId != null) {
            sql.append(" AND id <> ?");
            args.add(excludeId);
        }
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id, system_code, check_target_type, rule_category, rule_code, rule_code_desc, " +
                "rule_description, table_name_en, table_name_cn, field_name_en, field_name_cn, owner_id " +
                "FROM dm_rule WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移检核规则不存在");
        return rows.get(0);
    }

    private long requireRecycleBinScope(long id, AuthUser user) {
        List<Long> projects = jdbc.queryForList(
                "SELECT project_id FROM dm_rule WHERE id = ? AND tenant_id = ? AND deleted = 1",
                Long.class, id, user.tenantId());
        if (projects.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移检核规则不存在于回收站");
        long projectId = projects.get(0);
        permissions.requireStoredProject(projectId, user);
        return projectId;
    }

    private void decorateUsers(List<Map<String, Object>> rows, long tenantId) {
        if (userDirectory == null) return;
        for (Map<String, Object> row : rows) {
            Object created = row.get("created_by");
            if (created instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("created_by_name", item.displayName()));
            Object updated = row.get("updated_by");
            if (updated instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("updated_by_name", item.displayName()));
            Object deleted = row.get("deleted_by");
            if (deleted instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("deleted_by_name", item.displayName()));
        }
    }

    private void audit(AuthUser user, String operation, long projectId, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id) " +
                "VALUES (?, ?, ?, ?, 'RULE', ?)", user.tenantId(), user.id(), projectId, operation, id);
    }

    private int normalizePageSize(int size) {
        return PAGE_SIZES.contains(size) ? size : 20;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private static void writeHeaderRow(Sheet sheet, String[] columns) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
    }

    private static String cell(Row row, int index, DataFormatter formatter) {
        return row == null || row.getCell(index) == null ? "" : formatter.formatCellValue(row.getCell(index)).trim();
    }

    private static String blankAsNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private String requireText(Object value, String message) {
        String text = optionalText(value, null);
        if (text == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return text;
    }

    private String requireRuleCode(Object value) {
        String code = optionalText(value, null);
        if (code == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "规则编码不能为空");
        if (code.codePointCount(0, code.length()) > MAX_RULE_CODE_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "规则编码不能超过 96 字符");
        }
        return code;
    }

    private String optionalText(Object value) {
        return optionalText(value, null);
    }

    private String optionalText(Object value, Object otherValue) {
        Object candidate = value != null ? value : otherValue;
        if (candidate == null) return null;
        String text = String.valueOf(candidate).trim();
        return text.isEmpty() ? null : String.valueOf(candidate).trim();
    }

    private String optionalTextOrDefault(Map<String, Object> body, Object defaultValue, String key) {
        if (body.containsKey(key)) {
            String value = optionalText(body.get(key), null);
            return value == null ? null : limitOptionalString(value);
        }
        if (defaultValue == null) return null;
        return limitOptionalString(String.valueOf(defaultValue));
    }

    private String limitOptionalString(String value) {
        if (value == null) return null;
        if (value.codePointCount(0, value.length()) > MAX_OPTIONAL_FIELD_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文本长度超过 255 字符");
        }
        return value;
    }

    private long requireLong(Object value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}

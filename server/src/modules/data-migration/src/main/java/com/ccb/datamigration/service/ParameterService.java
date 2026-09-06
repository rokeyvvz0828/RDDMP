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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 迁移参数专属服务（REQ-20260906-067，对标迁移检核规则域化范式）。
 *
 * <p>存储于 {@code dm_parameter}（V197 域化：重建 parameter_type/parameter_scope/system_code/parameter_name/
 * parameter_description 维度列）。参数类型与参数范围分类由“系统管理/参数管理”维护；关联系统为当前项目启用
 * dm_component。支持多维组合筛选、分页、单条新增/编辑、Excel 批量导入（逐行校验、失败跳过）、模板下载、
 * 条件化批量导出、逻辑删除与统一回收站。参数名称在 租户+项目+系统编号 内唯一（含软删行）。
 */
@Service
public class ParameterService {
    private static final String CONTENT_TYPE = "PARAMETER";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESC_LENGTH = 500;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
    private static final String[] LIST_COLUMNS = {
            "项目名称", "参数类型", "参数范围分类", "系统编号", "系统名称", "参数名称", "参数说明"
    };
    private static final String[] TEMPLATE_COLUMNS = {
            "参数类型", "参数范围分类", "系统编号", "参数名称", "参数说明"
    };

    private static final String SYSTEM_JOIN =
            " LEFT JOIN dm_component c ON c.tenant_id = a.tenant_id AND c.project_id = a.project_id AND c.system_code = a.system_code " +
            " LEFT JOIN arch_physical_subsystem sys ON sys.tenant_id = c.tenant_id AND sys.code = c.system_code AND sys.deleted = 0 ";
    private static final String SELECT_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, a.parameter_type, a.parameter_scope, a.system_code, " +
            "sys.short_name AS system_short_name, sys.name AS system_name, " +
            "a.parameter_name, a.parameter_description, " +
            "a.owner_id, a.created_by, a.created_at, a.updated_by, a.updated_at ";
    private static final String RECYCLE_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, 'PARAMETER' AS asset_type, " +
            "a.parameter_name AS asset_code, a.parameter_name AS asset_name, " +
            "a.parameter_type, a.parameter_scope, a.system_code, sys.name AS system_name, a.parameter_description, " +
            "a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at ";

    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;
    private final DataMigrationCodeValueService codeValues;

    public ParameterService(JdbcTemplate jdbc, DataMigrationPermissionService permissions,
                            UserDirectoryPort userDirectory, DataMigrationCodeValueService codeValues) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
        this.codeValues = codeValues;
    }

    /** 列表：租户 + 项目恒定过滤，参数类型/范围/关联系统/关键字组合筛选并服务端分页。 */
    public PageResult<Map<String, Object>> list(Long projectId, String parameterType, String parameterScope,
                                                String systemCode, String keyword,
                                                int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM dm_parameter a WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> countArgs = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(countSql, countArgs, scope, parameterType, parameterScope, systemCode, keyword);
        Long total = jdbc.queryForObject(countSql.toString(), Long.class, countArgs.toArray());

        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append("FROM dm_parameter a ").append(SYSTEM_JOIN)
                .append("LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, scope, parameterType, parameterScope, systemCode, keyword);
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((long) (safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        decorateCodeLabels(records, user);
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total == null ? 0L : total, safePage, safeSize);
    }

    /** 详情：单条完整信息。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT_COLUMNS + "FROM dm_parameter a " + SYSTEM_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 0", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移参数不存在");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateCodeLabels(rows, user);
        decorateUsers(rows, user.tenantId());
        return row;
    }

    /** 新增单条：全量字段录入；类型/范围必须是启用参数，系统归属当前项目，参数名称在 项目+系统 内唯一。 */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        permissions.requireAccessible(projectId, user);
        String parameterType = requireText(body.get("parameterType"), "参数类型不能为空");
        String parameterScope = requireText(body.get("parameterScope"), "参数范围分类不能为空");
        String systemCode = requireText(body.get("systemCode"), "关联系统不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_PARAMETER_TYPE, "参数类型", parameterType, user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_PARAMETER_SCOPE, "参数范围分类", parameterScope, user);
        ensureSystemBelongsToProject(systemCode, projectId, user);
        String parameterName = requireName(body.get("parameterName"));
        if (nameExists(projectId, systemCode, parameterName, user.tenantId(), null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称在同一项目同一系统下已存在");
        }
        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_parameter (id, tenant_id, project_id, system_code, parameter_type, parameter_scope, " +
                            "parameter_name, parameter_description, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, user.tenantId(), projectId, systemCode.trim(), parameterType, parameterScope,
                    parameterName, optionalText(body.get("parameterDescription")),
                    user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称在同一项目同一系统下已存在");
        }
        audit(user, "PARAMETER_CREATE", projectId, id);
        return detail(id, user);
    }

    /** 编辑单条：归属恒取库中记录；可选字段缺省保留原值；参数名称在排除自身后项目/系统内唯一。 */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> existing = findRaw(id, user.tenantId());
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(existing.get("project_id"), user);

        String parameterType = body.containsKey("parameterType")
                ? requireText(body.get("parameterType"), "参数类型不能为空") : (String) existing.get("parameter_type");
        String parameterScope = body.containsKey("parameterScope")
                ? requireText(body.get("parameterScope"), "参数范围分类不能为空") : (String) existing.get("parameter_scope");
        String systemCode = body.containsKey("systemCode")
                ? requireText(body.get("systemCode"), "关联系统不能为空") : (String) existing.get("system_code");
        codeValues.requireActive(DataMigrationCodeValueService.DM_PARAMETER_TYPE, "参数类型", parameterType, user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_PARAMETER_SCOPE, "参数范围分类", parameterScope, user);
        ensureSystemBelongsToProject(systemCode, projectId, user);

        String parameterName = body.containsKey("parameterName")
                ? requireName(body.get("parameterName")) : (String) existing.get("parameter_name");
        if (nameExists(projectId, systemCode, parameterName, user.tenantId(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称在同一项目同一系统下已存在");
        }
        try {
            jdbc.update("UPDATE dm_parameter SET parameter_type = ?, parameter_scope = ?, system_code = ?, parameter_name = ?, " +
                            "parameter_description = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                            "WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    parameterType, parameterScope, systemCode, parameterName,
                    optionalTextOrDefault(body, existing.get("parameter_description"), "parameterDescription"),
                    user.id(), id, user.tenantId());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称在同一项目同一系统下已存在");
        }
        audit(user, "PARAMETER_UPDATE", projectId, id);
        return detail(id, user);
    }

    /** 批量逻辑删除：记录删除人/时间，进入统一回收站；仅可删除本人（管理员任何可删）。 */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : normalizeIds(ids)) {
            Map<String, Object> existing = findRaw(id, user.tenantId());
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
            int changed = jdbc.update("UPDATE dm_parameter SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP, " +
                            "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    user.id(), user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移参数状态已变化，请刷新后重试");
            audit(user, "PARAMETER_DELETE", projectId, id);
        }
    }

    // ============ Excel 模板 / 批量导入 / 条件化导出 ============

    /** 标准五列模板：参数类型/参数范围分类/系统编号/参数名称/参数说明；维度逐行填写。 */
    public byte[] downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移参数");
            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_COLUMNS.length; i++) header.createCell(i).setCellValue(TEMPLATE_COLUMNS[i]);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "迁移参数模板生成失败");
        }
    }

    /** 条件导出：按当前筛选导出，Excel 显示列表列。 */
    public byte[] export(Long projectId, String parameterType, String parameterScope, String systemCode,
                         String keyword, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("迁移参数");
            writeHeaderRow(sheet, LIST_COLUMNS);
            StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                    .append("FROM dm_parameter a ").append(SYSTEM_JOIN)
                    .append("LEFT JOIN pm_project p ON a.project_id = p.id AND a.tenant_id = p.tenant_id AND p.deleted = 0 ")
                    .append("WHERE a.tenant_id = ? AND a.deleted = 0");
            List<Object> args = new ArrayList<>(List.of(user.tenantId()));
            appendFilters(sql, args, scope, parameterType, parameterScope, systemCode, keyword);
            sql.append(" ORDER BY a.updated_at DESC, a.id DESC");
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            decorateCodeLabels(rows, user);
            int index = 1;
            for (Map<String, Object> data : rows) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(String.valueOf(data.getOrDefault("project_name", "")));
                row.createCell(1).setCellValue(String.valueOf(data.getOrDefault("parameter_type_name", data.getOrDefault("parameter_type", ""))));
                row.createCell(2).setCellValue(String.valueOf(data.getOrDefault("parameter_scope_name", data.getOrDefault("parameter_scope", ""))));
                row.createCell(3).setCellValue(String.valueOf(data.getOrDefault("system_code", "")));
                row.createCell(4).setCellValue(String.valueOf(data.getOrDefault("system_name", "")));
                row.createCell(5).setCellValue(String.valueOf(data.getOrDefault("parameter_name", "")));
                row.createCell(6).setCellValue(String.valueOf(data.getOrDefault("parameter_description", "")));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "迁移参数导出失败");
        }
    }

    /**
     * Excel 批量导入：前置条件为选定所属项目（请求参数 projectId）。模板五列逐行填写，
     * 参数类型/范围按系统参数管理展示名（标签）填写并映射为码值，系统编号必须属于当前项目；
     * 参数名称必填且 租户+项目+系统编号 内唯一。失败行跳过并逐行报错，不影响其余行入库。
     */
    public Map<String, Object> importParameters(Long projectId, MultipartFile file, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能超过 50 MB");

        Map<String, String> typeLabelToCode = optionLabelToCode(DataMigrationCodeValueService.DM_PARAMETER_TYPE, user);
        Map<String, String> scopeLabelToCode = optionLabelToCode(DataMigrationCodeValueService.DM_PARAMETER_SCOPE, user);

        int rows = 0;
        int accepted = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 缺少工作表");
            if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 超过 5000 行");
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                rows++;
                try {
                    Row row = sheet.getRow(i);
                    String typeLabel = cell(row, 0, formatter);
                    String scopeLabel = cell(row, 1, formatter);
                    String systemCode = cell(row, 2, formatter);
                    String parameterName = cell(row, 3, formatter);
                    String parameterDescription = cell(row, 4, formatter);

                    String parameterType = requireOptionCode(typeLabelToCode, typeLabel, "参数类型");
                    String parameterScope = requireOptionCode(scopeLabelToCode, scopeLabel, "参数范围分类");
                    if (systemCode.isBlank()) throw new IllegalArgumentException("系统编号为空");
                    if (parameterName.isBlank()) throw new IllegalArgumentException("参数名称为空");
                    if (parameterName.codePointCount(0, parameterName.length()) > MAX_NAME_LENGTH) {
                        throw new IllegalArgumentException("参数名称超过 255 字符");
                    }
                    if (!seenKeys.add(scope + "|" + systemCode + "|" + parameterName)) {
                        throw new IllegalArgumentException("文件内同一系统下参数名称重复");
                    }
                    if (nameExists(scope, systemCode, parameterName, user.tenantId(), null)) {
                        throw new IllegalArgumentException("参数名称在同一项目同一系统下已存在");
                    }
                    ensureSystemBelongsToProject(systemCode, scope, user);
                    if (parameterDescription.codePointCount(0, parameterDescription.length()) > MAX_DESC_LENGTH) {
                        throw new IllegalArgumentException("参数说明超过 500 字符");
                    }
                    long id = nextId();
                    try {
                        jdbc.update("INSERT INTO dm_parameter (id, tenant_id, project_id, system_code, parameter_type, parameter_scope, " +
                                        "parameter_name, parameter_description, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                id, user.tenantId(), scope, systemCode.trim(), parameterType, parameterScope,
                                parameterName, blankAsNull(parameterDescription),
                                user.id(), user.id(), user.id());
                    } catch (DataIntegrityViolationException ex) {
                        throw new IllegalArgumentException("参数名称在同一项目同一系统下已存在");
                    }
                    accepted++;
                    audit(user, "PARAMETER_IMPORT", scope, id);
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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dm_parameter a WHERE a.tenant_id = ? AND a.deleted = 1");
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
                .append("FROM dm_parameter a ").append(SYSTEM_JOIN)
                .append("LEFT JOIN pm_project p ON a.project_id = p.id AND a.tenant_id = p.tenant_id AND p.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        sql.append(" ORDER BY a.parameter_name ASC, a.id ASC LIMIT ?");
        args.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        decorateUsers(rows, user.tenantId());
        return rows;
    }

    public Map<String, Object> findRecycleBinDetail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                RECYCLE_COLUMNS + "FROM dm_parameter a " + SYSTEM_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND a.tenant_id = p.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 1", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移参数不存在于回收站");
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
            Map<String, Object> row = findRawIncludingDeleted(id, user.tenantId());
            if (row != null) {
                String systemCode = String.valueOf(row.get("system_code"));
                String parameterName = String.valueOf(row.get("parameter_name"));
                if (activeNameExists(projectId, systemCode, parameterName, user.tenantId(), id)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "参数名称已存在活动记录，无法恢复");
                }
            }
            try {
                int changed = jdbc.update("UPDATE dm_parameter SET deleted = 0, deleted_by = NULL, deleted_at = NULL, " +
                                "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 1",
                        id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移参数状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "参数名称已存在活动记录，无法恢复");
            }
            audit(user, "PARAMETER_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            int changed = jdbc.update("DELETE FROM dm_parameter WHERE id = ? AND tenant_id = ? AND deleted = 1",
                    id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移参数状态已变化，请刷新后重试");
            audit(user, "PARAMETER_PURGE", projectId, id);
        }
    }

    // ============ 私有辅助 ============

    private void appendFilters(StringBuilder sql, List<Object> args, long projectId, String parameterType,
                               String parameterScope, String systemCode, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (parameterType != null && !parameterType.isBlank()) {
            sql.append(" AND a.parameter_type = ?");
            args.add(parameterType.trim());
        }
        if (parameterScope != null && !parameterScope.isBlank()) {
            sql.append(" AND a.parameter_scope = ?");
            args.add(parameterScope.trim());
        }
        if (systemCode != null && !systemCode.isBlank()) {
            sql.append(" AND a.system_code = ?");
            args.add(systemCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = "%" + keyword.trim() + "%";
            sql.append(" AND (a.parameter_name LIKE ? OR a.parameter_description LIKE ?)");
            args.add(k);
            args.add(k);
        }
    }

    private void appendRecycleFilters(StringBuilder sql, List<Object> args, long projectId, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (keyword != null && !keyword.isBlank()) {
            String k = "%" + keyword.trim() + "%";
            sql.append(" AND (a.parameter_name LIKE ? OR a.parameter_description LIKE ?)");
            args.add(k);
            args.add(k);
        }
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tenant_id, project_id, system_code, parameter_type, parameter_scope, parameter_name, parameter_description, owner_id " +
                "FROM dm_parameter WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移参数不存在");
        return rows.get(0);
    }

    private Map<String, Object> findRawIncludingDeleted(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id, system_code, parameter_name FROM dm_parameter WHERE id = ? AND tenant_id = ? AND deleted = 1",
                id, tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long requireRecycleBinScope(long id, AuthUser user) {
        List<Long> projects = jdbc.queryForList(
                "SELECT project_id FROM dm_parameter WHERE id = ? AND tenant_id = ? AND deleted = 1",
                Long.class, id, user.tenantId());
        if (projects.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移参数不存在于回收站");
        long projectId = projects.get(0);
        permissions.requireStoredProject(projectId, user);
        return projectId;
    }

    private boolean nameExists(long projectId, String systemCode, String parameterName, long tenantId, Long excludeId) {
        return exists("SELECT COUNT(*) FROM dm_parameter WHERE tenant_id = ? AND project_id = ? AND system_code = ? AND parameter_name = ? AND id <> ?",
                tenantId, projectId, systemCode, parameterName, excludeId == null ? -1L : excludeId);
    }

    private boolean activeNameExists(long projectId, String systemCode, String parameterName, long tenantId, Long excludeId) {
        return exists("SELECT COUNT(*) FROM dm_parameter WHERE tenant_id = ? AND project_id = ? AND system_code = ? AND parameter_name = ? AND deleted = 0 AND id <> ?",
                tenantId, projectId, systemCode, parameterName, excludeId == null ? -1L : excludeId);
    }

    private void ensureSystemBelongsToProject(String systemCode, long projectId, AuthUser user) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_component c " +
                "WHERE c.tenant_id = ? AND c.project_id = ? AND c.system_code = ? AND c.enabled = 1",
                Integer.class, user.tenantId(), projectId, systemCode);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "关联系统不存在或不属于当前项目");
    }

    private Map<String, String> optionLabelToCode(String category, AuthUser user) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> option : codeValues.options(category, user)) {
            Object label = option.get("label");
            if (label != null) map.put(String.valueOf(label).trim(), String.valueOf(option.get("value")));
        }
        return map;
    }

    private static String requireOptionCode(Map<String, String> labelToCode, String label, String field) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException(field + "为空");
        String code = labelToCode.get(label.trim());
        if (code == null) throw new IllegalArgumentException(field + "值无效或已停用，请从系统管理/参数管理启用后重试");
        return code;
    }

    private void decorateCodeLabels(List<Map<String, Object>> rows, AuthUser user) {
        Map<String, String> types = optionValueToLabel(DataMigrationCodeValueService.DM_PARAMETER_TYPE, user);
        Map<String, String> scopes = optionValueToLabel(DataMigrationCodeValueService.DM_PARAMETER_SCOPE, user);
        for (Map<String, Object> row : rows) {
            Object type = row.get("parameter_type");
            if (type != null) row.put("parameter_type_name", types.getOrDefault(String.valueOf(type), String.valueOf(type)));
            Object scope = row.get("parameter_scope");
            if (scope != null) row.put("parameter_scope_name", scopes.getOrDefault(String.valueOf(scope), String.valueOf(scope)));
        }
    }

    private Map<String, String> optionValueToLabel(String category, AuthUser user) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> option : codeValues.options(category, user)) {
            Object value = option.get("value");
            if (value != null) map.put(String.valueOf(value), String.valueOf(option.get("label")));
        }
        return map;
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
                "VALUES (?, ?, ?, ?, 'PARAMETER', ?)", user.tenantId(), user.id(), projectId, operation, id);
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

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private String requireText(Object value, String message) {
        String text = optionalText(value);
        if (text == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return text;
    }

    private String requireName(Object value) {
        String name = optionalText(value);
        if (name == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "参数名称不能为空");
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数名称不能超过 255 字符");
        }
        return name;
    }

    private Optional<String> optionalTextOpt(Object value) {
        if (value == null) return Optional.empty();
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private String optionalText(Object value) {
        return optionalTextOpt(value).orElse(null);
    }

    private String optionalTextOrDefault(Map<String, Object> body, Object defaultValue, String key) {
        if (body.containsKey(key)) {
            return optionalText(body.get(key));
        }
        return blankAsNull(String.valueOf(defaultValue));
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

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 迁移过程依赖关系专属服务（REQ-20260910-068）。
 *
 * <p>dm_dependency 从通用文件型资产改造为「迁移参数 + 使用方系统」的关系表。
 * 本服务负责分页列表、关联投影、单条维护、批量导入、逻辑删除、统一回收站和审计。
 * 列表通过 parameter_id 关联 dm_parameter 获得参数中英文名和提供方系统，
 * 两侧系统编号再关联 dm_component + arch_physical_subsystem 获得中文名。
 */
@Service
public class DependencyService {
    private static final String CONTENT_TYPE = "DEPENDENCY";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
    private static final String[] TEMPLATE_COLUMNS = {"参数英文名", "使用方系统编号"};

    /**
     * 参数 + 两侧系统名的关联投影：
     * - a = dm_dependency（使用方关系）
     * - p = dm_parameter（提供方参数）
     * - pc = dm_component（提供方组件记录）
     * - psys = arch_physical_subsystem（提供方系统中文名）
     * - cc = dm_component（使用方组件记录）
     * - csys = arch_physical_subsystem（使用方系统中文名）
     */
    private static final String PARAM_JOIN =
            " LEFT JOIN dm_parameter p ON p.tenant_id = a.tenant_id AND p.id = a.parameter_id AND p.deleted = 0 "
          + " LEFT JOIN dm_component pc ON pc.tenant_id = a.tenant_id AND pc.project_id = a.project_id AND pc.system_code = p.system_code AND pc.enabled = 1 "
          + " LEFT JOIN arch_physical_subsystem psys ON psys.tenant_id = a.tenant_id AND psys.code = p.system_code AND psys.deleted = 0 "
          + " LEFT JOIN dm_component cc ON cc.tenant_id = a.tenant_id AND cc.project_id = a.project_id AND cc.system_code = a.system_code AND cc.enabled = 1 "
          + " LEFT JOIN arch_physical_subsystem csys ON csys.tenant_id = a.tenant_id AND csys.code = a.system_code AND csys.deleted = 0 ";

    private static final String SELECT_COLUMNS =
            "SELECT a.id, a.project_id, a.parameter_id, a.system_code AS consumer_system_code, "
          + "p.parameter_name_en, p.parameter_name, p.system_code AS provider_system_code, "
          + "psys.name AS provider_system_name, csys.name AS consumer_system_name, "
          + "a.owner_id, a.created_by, a.created_at, a.updated_by, a.updated_at ";

    private static final String RECYCLE_COLUMNS =
            "SELECT a.id, a.project_id, 'DEPENDENCY' AS asset_type, "
          + "p.parameter_name_en AS asset_code, p.parameter_name AS asset_name, "
          + "a.system_code AS consumer_system_code, csys.name AS consumer_system_name, "
          + "p.system_code AS provider_system_code, psys.name AS provider_system_name, "
          + "a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at ";

    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;

    public DependencyService(JdbcTemplate jdbc, DataMigrationPermissionService permissions,
                             UserDirectoryPort userDirectory) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
    }

    // ==================== 列表与查询 ====================

    /** 列表：项目恒定过滤 + 使用方系统筛选 + 关键字匹配参数中英文名 / 两侧系统编号或名称。 */
    public PageResult<Map<String, Object>> list(Long projectId, String consumerSystemCode, String keyword,
                                                int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM dm_dependency a "
              + "LEFT JOIN dm_parameter p ON p.tenant_id = a.tenant_id AND p.id = a.parameter_id AND p.deleted = 0 "
              + "LEFT JOIN arch_physical_subsystem psys ON psys.tenant_id = a.tenant_id AND psys.code = p.system_code AND psys.deleted = 0 "
              + "LEFT JOIN arch_physical_subsystem csys ON csys.tenant_id = a.tenant_id AND csys.code = a.system_code AND csys.deleted = 0 "
              + "WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> countArgs = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(countSql, countArgs, scope, consumerSystemCode, keyword);
        Long total = jdbc.queryForObject(countSql.toString(), Long.class, countArgs.toArray());

        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append("FROM dm_dependency a ").append(PARAM_JOIN)
                .append("WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, scope, consumerSystemCode, keyword);
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((long) (safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total == null ? 0L : total, safePage, safeSize);
    }

    private void appendFilters(StringBuilder sql, List<Object> args, long projectId,
                               String consumerSystemCode, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (consumerSystemCode != null && !consumerSystemCode.isBlank()) {
            sql.append(" AND a.system_code = ?");
            args.add(consumerSystemCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            sql.append(" AND (p.parameter_name LIKE ? OR p.parameter_name_en LIKE ? "
                     + "OR p.system_code LIKE ? OR psys.name LIKE ? "
                     + "OR a.system_code LIKE ? OR csys.name LIKE ?)");
            args.add(kw); args.add(kw); args.add(kw); args.add(kw); args.add(kw); args.add(kw);
        }
    }

    /** 详情：单条完整投影。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT_COLUMNS + "FROM dm_dependency a " + PARAM_JOIN
              + "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 0",
                user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "依赖关系不存在");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(rows, user.tenantId());
        return row;
    }

    /** 当前项目可选参数（用于下拉选择，返回 id/parameter_name_en/parameter_name/system_code/system_name）。 */
    public List<Map<String, Object>> listParameterOptions(Long projectId, String keyword, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.parameter_name_en, p.parameter_name, p.system_code, s.name AS system_name "
              + "FROM dm_parameter p "
              + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = p.tenant_id AND s.code = p.system_code AND s.deleted = 0 "
              + "WHERE p.tenant_id = ? AND p.project_id = ? AND p.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), scope));
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            sql.append(" AND (p.parameter_name LIKE ? OR p.parameter_name_en LIKE ? OR p.system_code LIKE ? OR s.name LIKE ?)");
            args.add(kw); args.add(kw); args.add(kw); args.add(kw);
        }
        sql.append(" ORDER BY p.parameter_name_en ASC, p.id ASC LIMIT 100");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 当前项目可选参数分页（批量新增表格用），支持按系统筛选与关键字匹配，未加 LIMIT 100 上限。 */
    public PageResult<Map<String, Object>> listParameterPage(Long projectId, String systemCode, String keyword,
                                                             int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        StringBuilder where = new StringBuilder("WHERE p.tenant_id = ? AND p.project_id = ? AND p.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), scope));
        if (systemCode != null && !systemCode.isBlank()) {
            where.append(" AND p.system_code = ?");
            args.add(systemCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            where.append(" AND (p.parameter_name LIKE ? OR p.parameter_name_en LIKE ? OR p.system_code LIKE ? OR s.name LIKE ?)");
            args.add(kw); args.add(kw); args.add(kw); args.add(kw);
        }
        String from = "FROM dm_parameter p "
                + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = p.tenant_id AND s.code = p.system_code AND s.deleted = 0 ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());

        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.parameter_name_en, p.parameter_name, p.system_code, s.name AS system_name "
              + from + where + " ORDER BY p.parameter_name_en ASC, p.id ASC LIMIT ? OFFSET ?");
        List<Object> sqlArgs = new ArrayList<>(args);
        sqlArgs.add(safeSize);
        sqlArgs.add((long) (safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), sqlArgs.toArray());
        return new PageResult<>(records, total == null ? 0L : total, safePage, safeSize);
    }

    // ==================== 单条维护 ====================

    /** 新增：校验参数存在、使用方系统有效、关系唯一。 */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        long scope = permissions.requireProject(projectId, user);

        long parameterId = requireLong(body.get("parameterId"), "参数ID不能为空");
        String consumerSystemCode = requireText(body.get("consumerSystemCode"), "使用方系统编号不能为空");

        validateParameterExists(parameterId, scope, user.tenantId());
        validateSystemEnabled(consumerSystemCode, scope, user.tenantId());
        if (relationExists(parameterId, consumerSystemCode, scope, user.tenantId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一参数与使用方系统的依赖关系已存在");
        }

        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_dependency (id, tenant_id, project_id, parameter_id, system_code, "
                    + "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id, user.tenantId(), scope, parameterId, consumerSystemCode.trim(),
                    user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一参数与使用方系统的依赖关系已存在");
        }
        audit(user, "DEPENDENCY_CREATE", scope, id);
        return detail(id, user);
    }

    /** 批量新增：多个使用方系统 × 多个迁移参数全组合写入；已存在关系跳过，任一参数/系统非法整体拒绝。 */
    @Transactional
    public Map<String, Object> batchCreate(Map<String, Object> body, AuthUser user) {
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        long scope = permissions.requireProject(projectId, user);

        List<Long> parameterIds = requireIdList(body.get("parameterIds"), "请选择至少一个迁移参数");
        List<String> systemCodes = requireTextList(body.get("consumerSystemCodes"), "请选择至少一个使用方系统");

        for (long parameterId : parameterIds) {
            validateParameterExists(parameterId, scope, user.tenantId());
        }
        for (String systemCode : systemCodes) {
            validateSystemEnabled(systemCode, scope, user.tenantId());
        }

        int accepted = 0;
        int skipped = 0;
        for (long parameterId : parameterIds) {
            for (String systemCode : systemCodes) {
                if (relationExists(parameterId, systemCode, scope, user.tenantId())) {
                    skipped++;
                    continue;
                }
                long id = nextId();
                try {
                    jdbc.update("INSERT INTO dm_dependency (id, tenant_id, project_id, parameter_id, system_code, "
                            + "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            id, user.tenantId(), scope, parameterId, systemCode,
                            user.id(), user.id(), user.id());
                } catch (DataIntegrityViolationException ex) {
                    skipped++;
                    continue;
                }
                accepted++;
                audit(user, "DEPENDENCY_CREATE", scope, id);
            }
        }
        return Map.of("accepted", accepted, "skipped", skipped);
    }

    /** 编辑：仅允许修改 parameter_id 和 consumer_system_code；保留关系唯一约束。 */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> existing = requireExisting(id, user);
        long projectId = ((Number) existing.get("project_id")).longValue();
        requireWritePermission(user, existing);

        long parameterId = body.containsKey("parameterId")
                ? requireLong(body.get("parameterId"), "参数ID不能为空")
                : ((Number) existing.get("parameter_id")).longValue();
        String consumerSystemCode = body.containsKey("consumerSystemCode")
                ? requireText(body.get("consumerSystemCode"), "使用方系统编号不能为空")
                : (String) existing.get("consumer_system_code");

        if (parameterId != ((Number) existing.get("parameter_id")).longValue()) {
            validateParameterExists(parameterId, projectId, user.tenantId());
        }
        if (!consumerSystemCode.equals(existing.get("consumer_system_code"))) {
            validateSystemEnabled(consumerSystemCode, projectId, user.tenantId());
        }

        try {
            jdbc.update("UPDATE dm_dependency SET parameter_id = ?, system_code = ?, updated_by = ? "
                    + "WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0",
                    parameterId, consumerSystemCode.trim(), user.id(),
                    id, user.tenantId(), projectId);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一参数与使用方系统的依赖关系已存在");
        }
        audit(user, "DEPENDENCY_UPDATE", projectId, id);
        return detail(id, user);
    }

    /** 逻辑删除：写 deleted 三件套 + 审计。 */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        List<Long> safeIds = normalizeIds(ids);
        if (safeIds.isEmpty()) return;

        for (long id : safeIds) {
            Map<String, Object> existing = requireExisting(id, user);
            long projectId = ((Number) existing.get("project_id")).longValue();
            requireWritePermission(user, existing);
            jdbc.update("UPDATE dm_dependency SET deleted = 1, deleted_by = ?, deleted_at = NOW() "
                    + "WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0",
                    user.id(), id, user.tenantId(), projectId);
            audit(user, "DEPENDENCY_DELETE", projectId, id);
        }
    }

    // ==================== 模板与批量导入 ====================

    /** 下载两列 Excel 模板。 */
    public byte[] downloadTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("依赖关系");
            writeHeaderRow(sheet, TEMPLATE_COLUMNS);
            sheet.setColumnWidth(0, 24 * 256);
            sheet.setColumnWidth(1, 24 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
        }
    }

    /**
     * 批量导入：按「参数英文名 + 使用方系统编号」逐行处理。
     * 参数英文名在当前项目内做不区分大小写的精确匹配，恰好一条才解析为 parameter_id；
     * 使用方系统必须是当前项目已启用的组件；同一关系在文件内和库内均不可重复。
     * 合法行独立写入，错误行跳过并返回原因，不覆盖已有关系。
     */
    public Map<String, Object> importDependencies(Long projectId, MultipartFile file, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);

        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能超过 50 MB");

        int rows = 0;
        int accepted = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenRelations = new LinkedHashSet<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 缺少工作表");
            if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 超过 5000 行");

            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);
            if (header == null
                    || !TEMPLATE_COLUMNS[0].equals(cell(header, 0, formatter))
                    || !TEMPLATE_COLUMNS[1].equals(cell(header, 1, formatter))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 表头必须为：参数英文名、使用方系统编号");
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                rows++;
                try {
                    Row row = sheet.getRow(i);
                    String parameterNameEn = cell(row, 0, formatter);
                    String consumerSystemCode = cell(row, 1, formatter);

                    if (parameterNameEn.isBlank()) throw new IllegalArgumentException("参数英文名不能为空");
                    if (consumerSystemCode.isBlank()) throw new IllegalArgumentException("使用方系统编号不能为空");

                    long parameterId = resolveParameterId(parameterNameEn, scope, user.tenantId());
                    validateSystemEnabled(consumerSystemCode, scope, user.tenantId());

                    String relationKey = parameterId + "|" + consumerSystemCode;
                    if (!seenRelations.add(relationKey)) {
                        throw new IllegalArgumentException("文件内同一参数与使用方系统重复");
                    }
                    if (relationExists(parameterId, consumerSystemCode, scope, user.tenantId())) {
                        throw new IllegalArgumentException("该依赖关系已存在");
                    }

                    long id = nextId();
                    try {
                        jdbc.update("INSERT INTO dm_dependency (id, tenant_id, project_id, parameter_id, system_code, "
                                + "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                id, user.tenantId(), scope, parameterId, consumerSystemCode.trim(),
                                user.id(), user.id(), user.id());
                    } catch (DataIntegrityViolationException ex) {
                        throw new IllegalArgumentException("该依赖关系已存在");
                    }
                    accepted++;
                    audit(user, "DEPENDENCY_IMPORT", scope, id);
                } catch (Exception ex) {
                    errors.add("第 " + (i + 1) + " 行：" + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件解析失败");
        }
        return Map.of("rows", rows, "accepted", accepted, "failed", errors.size(), "errors", errors);
    }

    /** 按英文名在当前项目内不区分大小写精确查找参数 ID；零条或多条均抛异常。 */
    private long resolveParameterId(String parameterNameEn, long projectId, long tenantId) {
        List<Map<String, Object>> matches = jdbc.queryForList(
                "SELECT id FROM dm_parameter WHERE tenant_id = ? AND project_id = ? AND deleted = 0 "
              + "AND LOWER(parameter_name_en) = ?",
                tenantId, projectId, parameterNameEn.trim().toLowerCase(Locale.ROOT));
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("参数英文名不存在");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("参数英文名不唯一，请调整参数数据");
        }
        return ((Number) matches.get(0).get("id")).longValue();
    }

    private boolean relationExists(long parameterId, String systemCode, long projectId, long tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_dependency WHERE tenant_id = ? AND project_id = ? "
              + "AND parameter_id = ? AND system_code = ? AND deleted = 0",
                Integer.class, tenantId, projectId, parameterId, systemCode);
        return count != null && count > 0;
    }

    // ==================== 统一回收站 ====================

    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM dm_dependency a "
              + "LEFT JOIN dm_parameter p ON p.tenant_id = a.tenant_id AND p.id = a.parameter_id "
              + "LEFT JOIN arch_physical_subsystem psys ON psys.tenant_id = a.tenant_id AND psys.code = p.system_code AND psys.deleted = 0 "
              + "LEFT JOIN arch_physical_subsystem csys ON csys.tenant_id = a.tenant_id AND csys.code = a.system_code AND csys.deleted = 0 "
              + "WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        StringBuilder sql = new StringBuilder(RECYCLE_COLUMNS)
                .append("FROM dm_dependency a ")
                .append("LEFT JOIN dm_parameter p ON p.tenant_id = a.tenant_id AND p.id = a.parameter_id ")
                .append("LEFT JOIN arch_physical_subsystem psys ON psys.tenant_id = a.tenant_id AND psys.code = p.system_code AND psys.deleted = 0 ")
                .append("LEFT JOIN arch_physical_subsystem csys ON csys.tenant_id = a.tenant_id AND csys.code = a.system_code AND csys.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        sql.append(" ORDER BY p.parameter_name_en ASC, a.id ASC LIMIT ?");
        args.add(Math.max(1, limit));
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        decorateRecycleUsers(records, user.tenantId());
        return records;
    }

    public Map<String, Object> detail(String type, long id, AuthUser user) {
        permissions.requireAdmin(user);
        List<Map<String, Object>> rows = jdbc.queryForList(
                RECYCLE_COLUMNS + "FROM dm_dependency a "
              + "LEFT JOIN dm_parameter p ON p.tenant_id = a.tenant_id AND p.id = a.parameter_id "
              + "LEFT JOIN arch_physical_subsystem psys ON psys.tenant_id = a.tenant_id AND psys.code = p.system_code AND psys.deleted = 0 "
              + "LEFT JOIN arch_physical_subsystem csys ON csys.tenant_id = a.tenant_id AND csys.code = a.system_code AND csys.deleted = 0 "
              + "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 1",
                user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "依赖关系不在回收站中");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateRecycleUsers(rows, user.tenantId());
        return row;
    }

    @Transactional
    public void restore(String type, List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        List<Long> safeIds = normalizeIds(ids);
        for (long id : safeIds) {
            Map<String, Object> row = requireDeleted(id, user);
            long projectId = ((Number) row.get("project_id")).longValue();
            long parameterId = ((Number) row.get("parameter_id")).longValue();
            String systemCode = (String) row.get("consumer_system_code");

            // 恢复前重新校验参数和系统有效性
            if (!parameterActive(parameterId, projectId, user.tenantId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "ID " + id + " 关联的参数已不存在，无法恢复");
            }
            if (!systemEnabled(systemCode, projectId, user.tenantId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "ID " + id + " 的使用方系统未启用，无法恢复");
            }
            try {
                jdbc.update("UPDATE dm_dependency SET deleted = 0, deleted_by = NULL, deleted_at = NULL, updated_by = ? "
                        + "WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 1",
                        user.id(), id, user.tenantId(), projectId);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "ID " + id + " 恢复后与现有依赖关系冲突，请先删除冲突记录");
            }
            audit(user, "DEPENDENCY_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(String type, List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        List<Long> safeIds = normalizeIds(ids);
        for (long id : safeIds) {
            Map<String, Object> row = requireDeleted(id, user);
            long projectId = ((Number) row.get("project_id")).longValue();
            jdbc.update("DELETE FROM dm_dependency WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 1",
                    id, user.tenantId(), projectId);
            audit(user, "DEPENDENCY_PURGE", projectId, id);
        }
    }

    private void appendRecycleFilters(StringBuilder sql, List<Object> args, long projectId, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            sql.append(" AND (p.parameter_name LIKE ? OR p.parameter_name_en LIKE ? "
                     + "OR p.system_code LIKE ? OR psys.name LIKE ? "
                     + "OR a.system_code LIKE ? OR csys.name LIKE ?)");
            args.add(kw); args.add(kw); args.add(kw); args.add(kw); args.add(kw); args.add(kw);
        }
    }

    // ==================== 校验与辅助 ====================

    private Map<String, Object> requireExisting(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM dm_dependency WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "依赖关系不存在");
        return rows.get(0);
    }

    private Map<String, Object> requireDeleted(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id, a.project_id, a.parameter_id, a.system_code AS consumer_system_code "
              + "FROM dm_dependency a WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 1",
                user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "依赖关系不在回收站中");
        permissions.requireStoredProject(rows.get(0).get("project_id"), user);
        return rows.get(0);
    }

    private void validateParameterExists(long parameterId, long projectId, long tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_parameter WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0",
                Integer.class, tenantId, projectId, parameterId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移参数不存在");
        }
    }

    private boolean parameterActive(long parameterId, long projectId, long tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_parameter WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0",
                Integer.class, tenantId, projectId, parameterId);
        return count != null && count > 0;
    }

    private void validateSystemEnabled(String systemCode, long projectId, long tenantId) {
        if (!systemEnabled(systemCode, projectId, tenantId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "使用方系统不存在或未启用");
        }
    }

    private boolean systemEnabled(String systemCode, long projectId, long tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND project_id = ? AND system_code = ? AND enabled = 1",
                Integer.class, tenantId, projectId, systemCode);
        return count != null && count > 0;
    }

    private void requireWritePermission(AuthUser user, Map<String, Object> record) {
        Object ownerId = record.get("owner_id");
        if (ownerId instanceof Number number) {
            permissions.requireWrite(user, number.longValue());
        }
    }

    private void decorateUsers(List<Map<String, Object>> records, long tenantId) {
        for (Map<String, Object> row : records) {
            Object created = row.get("created_by");
            if (created instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("created_by_name", item.displayName()));
            Object updated = row.get("updated_by");
            if (updated instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("updated_by_name", item.displayName()));
        }
    }

    private void decorateRecycleUsers(List<Map<String, Object>> records, long tenantId) {
        for (Map<String, Object> row : records) {
            Object deleted = row.get("deleted_by");
            if (deleted instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("deleted_by_name", item.displayName()));
        }
    }

    private void audit(AuthUser user, String operation, long projectId, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id) "
                + "VALUES (?, ?, ?, ?, 'DEPENDENCY', ?)",
                user.tenantId(), user.id(), projectId, operation, id);
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

    private String requireText(Object value, String message) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return String.valueOf(value).trim();
    }

    private long requireLong(Object value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private List<Long> requireIdList(Object value, String message) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : raw) {
            if (item == null) continue;
            try {
                ids.add(Long.parseLong(String.valueOf(item).trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法项，交由非空兜底
            }
        }
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return distinct;
    }

    private List<String> requireTextList(Object value, String message) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        List<String> items = raw.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (items.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return items;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}

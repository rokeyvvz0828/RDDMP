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
 * Excel 批量导入（维度逐行填写，逐行校验、失败跳过）、模板下载、条件化批量导出、逻辑删除与统一回收站。
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
            "检核目标类型", "检核规则大类", "系统编号",
            "表英文名", "表中文名", "字段英文名称", "字段中文名称",
            "规则编码", "规则编码说明", "检核规则说明"
    };

    private final RuleRepository repository;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;
    private final DataMigrationCodeValueService codeValues;

    public RuleService(RuleRepository repository, DataMigrationPermissionService permissions,
                       UserDirectoryPort userDirectory, DataMigrationCodeValueService codeValues) {
        this.repository = repository;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
        this.codeValues = codeValues;
    }

    /** 列表：租户 + 项目恒定过滤，检核目标类型/规则大类/关联系统/规则关键字/表字段关键字组合筛选并服务端分页。 */
    public PageResult<Map<String, Object>> list(Long projectId, String checkTargetType, String ruleCategory,
                                                String systemCode, String ruleKeyword, String keyword,
                                                int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        Map<String, Object> filters = filters(user.tenantId(), scope, checkTargetType, ruleCategory, systemCode, ruleKeyword, keyword, 0);
        long total = repository.count(filters);
        filters.put("limit", safeSize);
        filters.put("offset", (long) (safePage - 1) * safeSize);
        List<Map<String, Object>> records = repository.list(filters);
        decorateCodeLabels(records, user);
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 详情：单条完整信息（点击规则编码/编码说明查看）。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        Map<String, Object> row = repository.require(user.tenantId(), id);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateCodeLabels(List.of(row), user);
        decorateUsers(List.of(row), user.tenantId());
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
            repository.insert(ruleValues(id, user.tenantId(), projectId, systemCode.trim(), checkTargetType, ruleCategory,
                    ruleCode, optionalText(body.get("ruleCodeDesc")), optionalText(body.get("ruleDescription")),
                    optionalText(body.get("tableNameEn")), optionalText(body.get("tableNameCn")),
                    optionalText(body.get("fieldNameEn")), optionalText(body.get("fieldNameCn")), user.id()));
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

        repository.update(ruleValues(id, user.tenantId(), projectId, systemCode, checkTargetType, ruleCategory, ruleCode,
                optionalTextOrDefault(body, existing.get("rule_code_desc"), "ruleCodeDesc"),
                optionalTextOrDefault(body, existing.get("rule_description"), "ruleDescription"),
                optionalTextOrDefault(body, existing.get("table_name_en"), "tableNameEn"),
                optionalTextOrDefault(body, existing.get("table_name_cn"), "tableNameCn"),
                optionalTextOrDefault(body, existing.get("field_name_en"), "fieldNameEn"),
                optionalTextOrDefault(body, existing.get("field_name_cn"), "fieldNameCn"), user.id()));
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
            int changed = repository.softDelete(user.tenantId(), id, user.id());
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
            Map<String, Object> filters = filters(user.tenantId(), scope, checkTargetType, ruleCategory, systemCode, ruleKeyword, keyword, 0);
            filters.put("export", true);
            List<Map<String, Object>> rows = repository.list(filters);
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
     * Excel 批量导入：前置条件仅为选定所属项目（请求参数 projectId）。模板维度逐行填写，
     * 检核目标类型/检核规则大类按系统参数管理展示名（标签）填写并映射为码值，
     * 系统编号必须属于当前项目；规则编码必填且全局唯一。失败行跳过并逐行报错，不影响其余行入库。
     */
    public Map<String, Object> importRules(Long projectId, MultipartFile file, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能超过 50 MB");

        Map<String, String> targetLabelToCode = optionLabelToCode(DataMigrationCodeValueService.DM_RULE_TARGET_TYPE, user);
        Map<String, String> categoryLabelToCode = optionLabelToCode(DataMigrationCodeValueService.DM_RULE_CATEGORY, user);

        int rows = 0;
        int accepted = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 缺少工作表");
            if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 超过 5000 行");
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                rows++;
                try {
                    Row row = sheet.getRow(i);
                    String targetLabel = cell(row, 0, formatter);
                    String categoryLabel = cell(row, 1, formatter);
                    String sysCode = cell(row, 2, formatter);
                    String tableNameEn = cell(row, 3, formatter);
                    String tableNameCn = cell(row, 4, formatter);
                    String fieldNameEn = cell(row, 5, formatter);
                    String fieldNameCn = cell(row, 6, formatter);
                    String ruleCode = cell(row, 7, formatter);
                    String ruleCodeDesc = cell(row, 8, formatter);
                    String ruleDescription = cell(row, 9, formatter);
                    String targetType = requireOptionCode(targetLabelToCode, targetLabel, "检核目标类型");
                    String category = requireOptionCode(categoryLabelToCode, categoryLabel, "检核规则大类");
                    if (sysCode.isBlank()) throw new IllegalArgumentException("系统编号为空");
                    if (ruleCode.isBlank()) throw new IllegalArgumentException("规则编码为空");
                    if (ruleCode.length() > MAX_RULE_CODE_LENGTH) throw new IllegalArgumentException("规则编码超过 96 字符");
                    if (!seenCodes.add(ruleCode)) throw new IllegalArgumentException("文件内规则编码重复");
                    if (ruleCodeExists(ruleCode, user.tenantId(), null)) throw new IllegalArgumentException("规则编码已存在");
                    ensureSystemBelongsToProject(sysCode, scope, user);
                    validateOptionalLength(ruleCodeDesc, MAX_OPTIONAL_FIELD_LENGTH, "规则编码说明");
                    validateOptionalLength(ruleDescription, MAX_DESC_LENGTH, "检核规则说明");
                    validateOptionalLength(tableNameEn, MAX_OPTIONAL_FIELD_LENGTH, "表英文名");
                    validateOptionalLength(tableNameCn, MAX_OPTIONAL_FIELD_LENGTH, "表中文名");
                    validateOptionalLength(fieldNameEn, MAX_OPTIONAL_FIELD_LENGTH, "字段英文名称");
                    validateOptionalLength(fieldNameCn, MAX_OPTIONAL_FIELD_LENGTH, "字段中文名称");
                    long id = nextId();
                    repository.insert(ruleValues(id, user.tenantId(), scope, sysCode.trim(), targetType, category,
                            ruleCode, blankAsNull(ruleCodeDesc), blankAsNull(ruleDescription), tableNameEn, tableNameCn,
                            fieldNameEn, fieldNameCn, user.id()));
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
        return repository.count(filters(user.tenantId(), projectId, null, null, null, null, keyword, 1));
    }

    public List<Map<String, Object>> fetchRecycleBinPage(long projectId, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        if (limit <= 0) return List.of();
        Map<String, Object> filters = filters(user.tenantId(), projectId, null, null, null, null, keyword, 1);
        filters.put("limit", limit);
        List<Map<String, Object>> rows = repository.list(filters);
        decorateUsers(rows, user.tenantId());
        return rows;
    }

    public Map<String, Object> findRecycleBinDetail(long id, AuthUser user) {
        Map<String, Object> row = repository.requireDeleted(user.tenantId(), id);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(List.of(row), user.tenantId());
        return row;
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            try {
                int changed = repository.restore(user.tenantId(), id);
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
            int changed = repository.purge(user.tenantId(), id);
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移检核规则状态已变化，请刷新后重试");
            audit(user, "RULE_PURGE", projectId, id);
        }
    }

    // ============ 私有辅助 ============

    private Map<String, Object> filters(long tenantId, long projectId, String checkTargetType, String ruleCategory,
                                        String systemCode, String ruleKeyword, String keyword, int deleted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenantId", tenantId);
        values.put("projectId", projectId);
        values.put("checkTargetType", optionalText(checkTargetType));
        values.put("ruleCategory", optionalText(ruleCategory));
        values.put("systemCode", optionalText(systemCode));
        values.put("ruleKeyword", optionalText(ruleKeyword));
        values.put("keyword", optionalText(keyword));
        values.put("deleted", deleted);
        return values;
    }

    private Map<String, Object> ruleValues(long id, long tenantId, long projectId, String systemCode,
                                           String checkTargetType, String ruleCategory, String ruleCode,
                                           String ruleCodeDesc, String ruleDescription, String tableNameEn,
                                           String tableNameCn, String fieldNameEn, String fieldNameCn, long actorId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id); values.put("tenantId", tenantId); values.put("projectId", projectId);
        values.put("systemCode", systemCode); values.put("checkTargetType", checkTargetType); values.put("ruleCategory", ruleCategory);
        values.put("ruleCode", ruleCode); values.put("ruleCodeDesc", ruleCodeDesc); values.put("ruleDescription", ruleDescription);
        values.put("tableNameEn", tableNameEn); values.put("tableNameCn", tableNameCn);
        values.put("fieldNameEn", fieldNameEn); values.put("fieldNameCn", fieldNameCn);
        values.put("ownerId", actorId); values.put("createdBy", actorId); values.put("updatedBy", actorId);
        return values;
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
        if (!repository.enabledComponent(user.tenantId(), projectId, systemCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关联系统不存在或不属于当前项目");
        }
    }

    private boolean ruleCodeExists(String ruleCode, long tenantId, Long excludeId) {
        return repository.ruleCodeExists(tenantId, ruleCode, excludeId);
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        return repository.requireRaw(tenantId, id);
    }

    private long requireRecycleBinScope(long id, AuthUser user) {
        long projectId = repository.requireDeletedProject(user.tenantId(), id);
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
        repository.audit(user.tenantId(), user.id(), projectId, operation, id);
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

    private static void validateOptionalLength(String value, int max, String field) {
        if (value != null && !value.isBlank() && value.codePointCount(0, value.length()) > max) {
            throw new IllegalArgumentException(field + "超过 " + max + " 字符");
        }
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

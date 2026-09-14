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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 迁移参数专属服务（REQ-20260906-067，对标迁移检核规则域化范式）。
 *
 * <p>存储于 {@code dm_parameter}（V197/V198 域化：重建参数维度并增加参数英文名）。参数类型与参数范围分类由
 * “系统管理/参数管理”维护；关联系统为当前项目启用
 * dm_component。支持多维组合筛选、分页、单条新增/编辑（可携带字段批量创建）、Excel 批量导入
 * （十一列：参数 + 字段，逐组校验、失败跳过）、模板下载、条件化批量导出、逻辑删除与统一回收站。
 * 中英文参数名称在 租户+项目+系统编号 内唯一（含软删行），其中英文名不区分大小写；参数字段
 * 存于 dm_parameter_field（REQ-20260910-069），同参数内字段中英文名唯一（软删行不占用名称）。
 */
@Service
public class ParameterService {
    private static final String CONTENT_TYPE = "PARAMETER";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESC_LENGTH = 500;
    private static final int MAX_FIELD_NAME_LENGTH = 128;
    private static final int MAX_FIELD_DESC_LENGTH = 500;
    private static final Pattern ENGLISH_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern FIELD_ENGLISH_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
    private static final String[] LIST_COLUMNS = {
            "项目名称", "参数类型", "参数范围分类", "系统编号", "系统名称", "参数名称", "参数英文名", "参数说明"
    };
    private static final String[] TEMPLATE_COLUMNS = {
            "参数类型", "参数范围分类", "系统编号", "参数名称", "参数英文名", "参数说明",
            "字段英文名", "字段中文名", "字段类型", "字段长度", "字段说明"
    };
    private static final String FIELD_SELECT =
            "SELECT f.id, f.parameter_id, f.field_name_en, f.field_name_cn, f.field_type, f.field_length, " +
            "f.field_description, f.sort_no, f.owner_id, f.created_by, f.created_at, f.updated_by, f.updated_at ";

    private static final String SYSTEM_JOIN =
            " LEFT JOIN dm_component c ON c.tenant_id = a.tenant_id AND c.project_id = a.project_id AND c.system_code = a.system_code " +
            " LEFT JOIN arch_physical_subsystem sys ON sys.tenant_id = c.tenant_id AND sys.code = c.system_code AND sys.deleted = 0 ";
    private static final String SELECT_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, a.parameter_type, a.parameter_scope, a.system_code, " +
            "sys.short_name AS system_short_name, sys.name AS system_name, " +
            "a.parameter_name, a.parameter_name_en, a.parameter_description, " +
            "(SELECT COUNT(*) FROM dm_parameter_field f WHERE f.parameter_id = a.id AND f.tenant_id = a.tenant_id AND f.deleted = 0) AS field_count, " +
            "a.owner_id, a.created_by, a.created_at, a.updated_by, a.updated_at ";
    private static final String RECYCLE_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, 'PARAMETER' AS asset_type, " +
            "a.parameter_name AS asset_code, a.parameter_name AS asset_name, a.parameter_name_en, " +
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
        row.put("fields", listFieldsInternal(id, user));
        row.put("field_count", ((List<?>) row.get("fields")).size());
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
        String parameterNameEn = requireEnglishName(body.get("parameterNameEn"));
        if (nameExists(projectId, systemCode, parameterName, user.tenantId(), null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称在同一项目同一系统下已存在");
        }
        if (englishNameExists(projectId, systemCode, parameterNameEn, user.tenantId(), null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数英文名在同一项目同一系统下已存在");
        }
        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_parameter (id, tenant_id, project_id, system_code, parameter_type, parameter_scope, " +
                            "parameter_name, parameter_name_en, parameter_description, owner_id, created_by, updated_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, user.tenantId(), projectId, systemCode.trim(), parameterType, parameterScope,
                    parameterName, parameterNameEn, optionalText(body.get("parameterDescription")),
                    user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称或参数英文名在同一项目同一系统下已存在");
        }
        audit(user, "PARAMETER_CREATE", projectId, id);
        List<?> fields = body.get("fields") instanceof List<?> list ? list : List.of();
        addFieldsInternal(id, projectId, fields, user);
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
        String parameterNameEn = body.containsKey("parameterNameEn")
                ? requireEnglishName(body.get("parameterNameEn")) : (String) existing.get("parameter_name_en");
        if (nameExists(projectId, systemCode, parameterName, user.tenantId(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称在同一项目同一系统下已存在");
        }
        if (englishNameExists(projectId, systemCode, parameterNameEn, user.tenantId(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数英文名在同一项目同一系统下已存在");
        }
        try {
            jdbc.update("UPDATE dm_parameter SET parameter_type = ?, parameter_scope = ?, system_code = ?, parameter_name = ?, " +
                            "parameter_name_en = ?, parameter_description = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                            "WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    parameterType, parameterScope, systemCode, parameterName, parameterNameEn,
                    optionalTextOrDefault(body, existing.get("parameter_description"), "parameterDescription"),
                    user.id(), id, user.tenantId());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "参数名称或参数英文名在同一项目同一系统下已存在");
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
            // REQ-20260910-068：存在活动依赖时不得删除参数
            if (hasActiveDependency(id, user.tenantId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "参数存在活动依赖关系，不能删除");
            }
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
            int changed = jdbc.update("UPDATE dm_parameter SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP, " +
                            "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    user.id(), user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移参数状态已变化，请刷新后重试");
            jdbc.update("UPDATE dm_parameter_field SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP, " +
                            "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE parameter_id = ? AND tenant_id = ? AND deleted = 0",
                    user.id(), user.id(), id, user.tenantId());
            audit(user, "PARAMETER_DELETE", projectId, id);
        }
    }

    // ============ 字段信息子表（REQ-20260910-069） ============

    /** 字段列表：参数必须在当前项目内可访问；按排序号升序返回并装饰类型码值标签。 */
    public List<Map<String, Object>> listFields(long parameterId, AuthUser user) {
        Map<String, Object> parameter = requireParameter(parameterId, user);
        permissions.requireStoredProject(parameter.get("project_id"), user);
        return listFieldsInternal(parameterId, user);
    }

    /** 批量新增字段：body 为 JSON 数组；组内与库内中英文名唯一，全部通过后同事务写入。 */
    @Transactional
    public List<Map<String, Object>> batchAddFields(long parameterId, List<?> items, AuthUser user) {
        Map<String, Object> parameter = requireParameter(parameterId, user);
        permissions.requireWrite(user, ((Number) parameter.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(parameter.get("project_id"), user);
        addFieldsInternal(parameterId, projectId, items, user);
        return listFieldsInternal(parameterId, user);
    }

    /** 单条编辑字段：归属按参数实体校验；可选字段缺省保留原值。 */
    @Transactional
    public Map<String, Object> updateField(long parameterId, long fieldId, Map<String, Object> body, AuthUser user) {
        Map<String, Object> parameter = requireParameter(parameterId, user);
        permissions.requireWrite(user, ((Number) parameter.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(parameter.get("project_id"), user);
        Map<String, Object> field = requireField(parameterId, fieldId, user.tenantId());
        String fieldNameEn = body.containsKey("fieldNameEn")
                ? requireFieldEnglishName(body.get("fieldNameEn"))
                : String.valueOf(field.get("field_name_en"));
        String fieldNameCn = body.containsKey("fieldNameCn")
                ? requireFieldChineseName(body.get("fieldNameCn"))
                : String.valueOf(field.get("field_name_cn"));
        String fieldType = body.containsKey("fieldType")
                ? requireFieldType(body.get("fieldType"), user)
                : String.valueOf(field.get("field_type"));
        Integer fieldLength = body.containsKey("fieldLength")
                ? optionalFieldLength(body.get("fieldLength"))
                : numberOrNull(field.get("field_length"));
        String fieldDescription = body.containsKey("fieldDescription")
                ? optionalFieldDescription(body.get("fieldDescription"))
                : blankAsNull(String.valueOf(field.get("field_description")));
        if (fieldNameEnExists(parameterId, fieldNameEn, user.tenantId(), fieldId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "字段英文名在参数内已存在");
        }
        if (fieldNameCnExists(parameterId, fieldNameCn, user.tenantId(), fieldId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "字段中文名在参数内已存在");
        }
        try {
            jdbc.update("UPDATE dm_parameter_field SET field_name_en = ?, field_name_cn = ?, field_type = ?, " +
                            "field_length = ?, field_description = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                            "WHERE id = ? AND tenant_id = ? AND parameter_id = ? AND deleted = 0",
                    fieldNameEn, fieldNameCn, fieldType, fieldLength, fieldDescription,
                    user.id(), fieldId, user.tenantId(), parameterId);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "字段英文名或字段中文名在参数内已存在");
        }
        audit(user, "FIELD_UPDATE", projectId, parameterId);
        return queryField(fieldId, user.tenantId());
    }

    /** 单条逻辑删除字段。 */
    @Transactional
    public void deleteField(long parameterId, long fieldId, AuthUser user) {
        deleteFields(parameterId, List.of(fieldId), user);
    }

    /** 批量逻辑删除字段：仅可删本人（管理员任何可删），按参数实体校验归属。 */
    @Transactional
    public void deleteFields(long parameterId, Collection<Long> fieldIds, AuthUser user) {
        List<Long> ids = normalizeIds(fieldIds);
        if (ids.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "字段列表不能为空");
        Map<String, Object> parameter = requireParameter(parameterId, user);
        permissions.requireWrite(user, ((Number) parameter.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(parameter.get("project_id"), user);
        String placeholders = ids.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        int changed = jdbc.update("UPDATE dm_parameter_field SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP, " +
                        "updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id IN (" + placeholders + ") AND tenant_id = ? AND parameter_id = ? AND deleted = 0",
                concatArgs(List.of(user.id(), user.id()), ids, List.of(user.tenantId(), parameterId)));
        if (changed != ids.size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "部分字段不存在或状态已变化，请刷新后重试");
        }
        audit(user, "FIELD_DELETE", projectId, parameterId);
    }

    /**
     * 批量写入字段（新增参数携带 fields 或字段抽屉批量新增共用）。
     * 所有校验与唯一检查通过后才逐条写入；同参数内中英文名不区分大小写唯一。
     */
    private void addFieldsInternal(long parameterId, long projectId, List<?> items, AuthUser user) {
        if (items == null || items.isEmpty()) return;
        Set<String> englishNames = new LinkedHashSet<>();
        Set<String> chineseNames = new LinkedHashSet<>();
        int sortNo = maxSortNo(parameterId, user.tenantId());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字段信息格式不正确");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) map;
            String fieldNameEn = requireFieldEnglishName(body.get("fieldNameEn"));
            String fieldNameCn = requireFieldChineseName(body.get("fieldNameCn"));
            String fieldType = requireFieldType(body.get("fieldType"), user);
            Integer fieldLength = optionalFieldLength(body.get("fieldLength"));
            String fieldDescription = optionalFieldDescription(body.get("fieldDescription"));
            if (!englishNames.add(fieldNameEn.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.CONFLICT, "字段英文名在参数内重复");
            }
            if (!chineseNames.add(fieldNameCn.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.CONFLICT, "字段中文名在参数内重复");
            }
            if (fieldNameEnExists(parameterId, fieldNameEn, user.tenantId(), null)) {
                throw new BusinessException(ErrorCode.CONFLICT, "字段英文名在参数内已存在");
            }
            if (fieldNameCnExists(parameterId, fieldNameCn, user.tenantId(), null)) {
                throw new BusinessException(ErrorCode.CONFLICT, "字段中文名在参数内已存在");
            }
            long fieldId = nextId();
            try {
                jdbc.update("INSERT INTO dm_parameter_field (id, tenant_id, parameter_id, field_name_en, field_name_cn, " +
                                "field_type, field_length, field_description, sort_no, owner_id, created_by, updated_by) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        fieldId, user.tenantId(), parameterId, fieldNameEn, fieldNameCn, fieldType,
                        fieldLength, fieldDescription, ++sortNo, user.id(), user.id(), user.id());
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "字段英文名或字段中文名在参数内已存在");
            }
            audit(user, "FIELD_CREATE", projectId, parameterId);
        }
    }

    private List<Map<String, Object>> listFieldsInternal(long parameterId, AuthUser user) {
        List<Map<String, Object>> fields = jdbc.queryForList(
                FIELD_SELECT + "FROM dm_parameter_field f " +
                "WHERE f.tenant_id = ? AND f.parameter_id = ? AND f.deleted = 0 " +
                "ORDER BY f.sort_no ASC, f.id ASC", user.tenantId(), parameterId);
        Map<String, String> types = optionValueToLabel(DataMigrationCodeValueService.DM_PARAMETER_FIELD_TYPE, user);
        for (Map<String, Object> field : fields) {
            Object type = field.get("field_type");
            if (type != null) {
                field.put("field_type_name", types.getOrDefault(String.valueOf(type), String.valueOf(type)));
            }
        }
        return fields;
    }

    private Map<String, Object> requireParameter(long parameterId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id, owner_id FROM dm_parameter WHERE id = ? AND tenant_id = ? AND deleted = 0",
                parameterId, user.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移参数不存在");
        permissions.requireStoredProject(rows.get(0).get("project_id"), user);
        return rows.get(0);
    }

    private Map<String, Object> requireField(long parameterId, long fieldId, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                FIELD_SELECT + "FROM dm_parameter_field f " +
                "WHERE f.id = ? AND f.tenant_id = ? AND f.parameter_id = ? AND f.deleted = 0",
                fieldId, tenantId, parameterId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不存在");
        return rows.get(0);
    }

    private Map<String, Object> queryField(long fieldId, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                FIELD_SELECT + "FROM dm_parameter_field f " +
                "WHERE f.id = ? AND f.tenant_id = ? AND f.deleted = 0",
                fieldId, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不存在");
        return rows.get(0);
    }

    private int maxSortNo(long parameterId, long tenantId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_no), 0) FROM dm_parameter_field WHERE tenant_id = ? AND parameter_id = ?",
                Integer.class, tenantId, parameterId);
        return max == null ? 0 : max;
    }

    private String requireFieldEnglishName(Object value) {
        String name = optionalText(value);
        if (name == null || name.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "字段英文名不能为空");
        if (name.length() > MAX_FIELD_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段英文名不能超过 128 字符");
        }
        if (!FIELD_ENGLISH_NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段英文名只能包含字母、数字和下划线，不允许空格");
        }
        return name;
    }

    private String requireFieldChineseName(Object value) {
        String name = optionalText(value);
        if (name == null || name.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "字段中文名不能为空");
        if (name.codePointCount(0, name.length()) > MAX_FIELD_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段中文名不能超过 128 字符");
        }
        if (name.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段中文名不允许空格");
        }
        return name;
    }

    private String requireFieldType(Object value, AuthUser user) {
        String type = requireText(value, "字段类型不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_PARAMETER_FIELD_TYPE, "字段类型", type, user);
        return type;
    }

    private Integer optionalFieldLength(Object value) {
        String text = optionalText(value);
        if (text == null) return null;
        try {
            long length = Long.parseLong(text);
            if (length <= 0 || length > 2147483647L) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字段长度必须为正整数");
            }
            return (int) length;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段长度必须为正整数");
        }
    }

    private String optionalFieldDescription(Object value) {
        String text = optionalText(value);
        if (text == null) return null;
        if (text.codePointCount(0, text.length()) > MAX_FIELD_DESC_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段说明不能超过 500 字符");
        }
        return text;
    }

    private boolean fieldNameEnExists(long parameterId, String nameEn, long tenantId, Long excludeId) {
        return exists("SELECT COUNT(*) FROM dm_parameter_field WHERE tenant_id = ? AND parameter_id = ? " +
                        "AND field_name_en = ? AND deleted = 0 AND id <> ?",
                tenantId, parameterId, nameEn, excludeId == null ? -1L : excludeId);
    }

    private boolean fieldNameCnExists(long parameterId, String nameCn, long tenantId, Long excludeId) {
        return exists("SELECT COUNT(*) FROM dm_parameter_field WHERE tenant_id = ? AND parameter_id = ? " +
                        "AND field_name_cn = ? AND deleted = 0 AND id <> ?",
                tenantId, parameterId, nameCn, excludeId == null ? -1L : excludeId);
    }

    private Integer numberOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Object[] concatArgs(List<?> head, List<Long> middle, List<?> tail) {
        List<Object> args = new ArrayList<>(head);
        args.addAll(middle);
        args.addAll(tail);
        return args.toArray();
    }

    // ============ Excel 模板 / 批量导入 / 条件化导出 ============

    /** 标准十一列模板：参数类型/参数范围分类/系统编号/参数名称/参数英文名/参数说明 + 五列字段信息；每行一个字段。 */
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
                row.createCell(6).setCellValue(String.valueOf(data.getOrDefault("parameter_name_en", "")));
                row.createCell(7).setCellValue(String.valueOf(data.getOrDefault("parameter_description", "")));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "迁移参数导出失败");
        }
    }

    /**
     * Excel 批量导入（REQ-20260910-069 扩展十一列）：前置条件为选定所属项目（请求参数 projectId）。
     * 模板列：参数类型/参数范围分类/系统编号/参数名称/参数英文名/参数说明/字段英文名/字段中文名/字段类型/
     * 字段长度/字段说明；每行一个字段，同一参数（系统编号 + 参数名称）连续多行归为一个参数组并累加字段，
     * 参数信息可整组重复；字段列全空视为纯参数行（兼容原六列模板）；整行全空跳过。
     * 失败行跳过并逐行报错，不影响其余参数组入库。
     */
    @Transactional
    public Map<String, Object> importParameters(Long projectId, MultipartFile file, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能超过 50 MB");

        Map<String, String> typeLabelToCode = optionLabelToCode(DataMigrationCodeValueService.DM_PARAMETER_TYPE, user);
        Map<String, String> scopeLabelToCode = optionLabelToCode(DataMigrationCodeValueService.DM_PARAMETER_SCOPE, user);
        Map<String, String> fieldTypeLabelToCode = optionLabelToCode(DataMigrationCodeValueService.DM_PARAMETER_FIELD_TYPE, user);

        int rows = 0;
        List<String> errors = new ArrayList<>();
        int[] acceptedRef = new int[1];
        Set<String> seenNameKeys = new LinkedHashSet<>();
        Set<String> seenEnglishNameKeys = new LinkedHashSet<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 缺少工作表");
            if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 超过 5000 行");
            DataFormatter formatter = new DataFormatter();
            String groupType = null, groupScope = null, groupSystemCode = null;
            String groupName = null, groupNameEn = null, groupDesc = null;
            List<Map<String, Object>> groupFields = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                rows++;
                try {
                    Row row = sheet.getRow(i);
                    String typeLabel = cell(row, 0, formatter);
                    String scopeLabel = cell(row, 1, formatter);
                    String systemCode = cell(row, 2, formatter);
                    String parameterName = cell(row, 3, formatter);
                    String parameterNameEn = cell(row, 4, formatter);
                    String parameterDescription = cell(row, 5, formatter);
                    String fieldNameEn = cell(row, 6, formatter);
                    String fieldNameCn = cell(row, 7, formatter);
                    String fieldTypeLabel = cell(row, 8, formatter);
                    String fieldLengthRaw = cell(row, 9, formatter);
                    String fieldDescription = cell(row, 10, formatter);

                    boolean allBlank = typeLabel.isBlank() && scopeLabel.isBlank() && systemCode.isBlank()
                            && parameterName.isBlank() && parameterNameEn.isBlank() && parameterDescription.isBlank()
                            && fieldNameEn.isBlank() && fieldNameCn.isBlank() && fieldTypeLabel.isBlank()
                            && fieldLengthRaw.isBlank() && fieldDescription.isBlank();
                    if (allBlank) continue;

                    boolean newGroup = groupName == null
                            || (!parameterName.isBlank() && (!parameterName.equals(groupName)
                            || !systemCode.equals(groupSystemCode)));
                    if (newGroup) {
                        if (groupName != null) {
                            flushParameterGroup(scope, typeLabelToCode, scopeLabelToCode, fieldTypeLabelToCode,
                                    groupType, groupScope, groupSystemCode, groupName, groupNameEn, groupDesc,
                                    groupFields, user, errors, acceptedRef);
                            // 前一组已落盘：清空组状态，后续任一校验失败时不会在循环末尾重复落盘
                            groupType = null;
                            groupScope = null;
                            groupSystemCode = null;
                            groupName = null;
                            groupNameEn = null;
                            groupDesc = null;
                            groupFields = new ArrayList<>();
                        }
                        if (!seenNameKeys.add(scope + "|" + systemCode + "|" + parameterName)) {
                            throw new IllegalArgumentException("文件内同一系统下参数名称重复");
                        }
                        String englishKey = scope + "|" + systemCode + "|" + parameterNameEn.toLowerCase(Locale.ROOT);
                        if (!seenEnglishNameKeys.add(englishKey)) {
                            throw new IllegalArgumentException("文件内同一系统下参数英文名重复");
                        }
                        groupType = typeLabel;
                        groupScope = scopeLabel;
                        groupSystemCode = systemCode;
                        groupName = parameterName;
                        groupNameEn = parameterNameEn;
                        groupDesc = parameterDescription;
                        groupFields = new ArrayList<>();
                    } else {
                        requireGroupConsistent(typeLabel, groupType, "参数类型");
                        requireGroupConsistent(scopeLabel, groupScope, "参数范围分类");
                        requireGroupConsistent(systemCode, groupSystemCode, "系统编号");
                        requireGroupConsistent(parameterNameEn, groupNameEn, "参数英文名");
                        requireGroupConsistent(parameterDescription, groupDesc, "参数说明");
                    }
                    if (!fieldNameEn.isBlank() || !fieldNameCn.isBlank() || !fieldTypeLabel.isBlank()
                            || !fieldLengthRaw.isBlank() || !fieldDescription.isBlank()) {
                        Map<String, Object> field = new LinkedHashMap<>();
                        field.put("fieldNameEn", fieldNameEn);
                        field.put("fieldNameCn", fieldNameCn);
                        field.put("fieldTypeLabel", fieldTypeLabel);
                        field.put("fieldLength", fieldLengthRaw);
                        field.put("fieldDescription", fieldDescription);
                        groupFields.add(field);
                    }
                } catch (Exception ex) {
                    errors.add("第 " + (i + 1) + " 行：" + messageOf(ex));
                }
            }
            if (groupName != null) {
                flushParameterGroup(scope, typeLabelToCode, scopeLabelToCode, fieldTypeLabelToCode,
                        groupType, groupScope, groupSystemCode, groupName, groupNameEn, groupDesc,
                        groupFields, user, errors, acceptedRef);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件解析失败");
        }
        return Map.of("rows", rows, "accepted", acceptedRef[0], "failed", errors.size(), "errors", errors);
    }

    /** 落盘一个参数组：先全量校验再写入参数与字段；任一步失败仅该组失败并继续。 */
    private void flushParameterGroup(long scope, Map<String, String> typeLabelToCode, Map<String, String> scopeLabelToCode,
                                     Map<String, String> fieldTypeLabelToCode, String groupType, String groupScope,
                                     String groupSystemCode, String groupName, String groupNameEn, String groupDesc,
                                     List<Map<String, Object>> groupFields, AuthUser user,
                                     List<String> errors, int[] acceptedRef) {
        try {
            if (groupSystemCode == null || groupSystemCode.isBlank()) throw new IllegalArgumentException("系统编号为空");
            String normalizedSystem = groupSystemCode.trim();
            String parameterType = requireOptionCode(typeLabelToCode, groupType, "参数类型");
            String parameterScope = requireOptionCode(scopeLabelToCode, groupScope, "参数范围分类");
            String normalizedName = requireName(groupName);
            String normalizedNameEn = requireEnglishName(groupNameEn);
            String normalizedDesc = blankAsNull(groupDesc);
            if (normalizedDesc != null && normalizedDesc.codePointCount(0, normalizedDesc.length()) > MAX_DESC_LENGTH) {
                throw new IllegalArgumentException("参数说明超过 500 字符");
            }
            if (nameExists(scope, normalizedSystem, normalizedName, user.tenantId(), null)) {
                throw new IllegalArgumentException("参数名称在同一项目同一系统下已存在");
            }
            if (englishNameExists(scope, normalizedSystem, normalizedNameEn, user.tenantId(), null)) {
                throw new IllegalArgumentException("参数英文名在同一项目同一系统下已存在");
            }
            ensureSystemBelongsToProject(normalizedSystem, scope, user);
            List<Map<String, Object>> validatedFields = validateImportFields(fieldTypeLabelToCode, groupFields);
            long id = nextId();
            try {
                jdbc.update("INSERT INTO dm_parameter (id, tenant_id, project_id, system_code, parameter_type, parameter_scope, " +
                                "parameter_name, parameter_name_en, parameter_description, owner_id, created_by, updated_by) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, user.tenantId(), scope, normalizedSystem, parameterType, parameterScope,
                        normalizedName, normalizedNameEn, normalizedDesc, user.id(), user.id(), user.id());
            } catch (DataIntegrityViolationException ex) {
                throw new IllegalArgumentException("参数名称或参数英文名在同一项目同一系统下已存在");
            }
            audit(user, "PARAMETER_IMPORT", scope, id);
            int sortNo = 0;
            for (Map<String, Object> field : validatedFields) {
                long fieldId = nextId();
                try {
                    jdbc.update("INSERT INTO dm_parameter_field (id, tenant_id, parameter_id, field_name_en, field_name_cn, " +
                                    "field_type, field_length, field_description, sort_no, owner_id, created_by, updated_by) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            fieldId, user.tenantId(), id, field.get("fieldNameEn"), field.get("fieldNameCn"),
                            field.get("fieldType"), field.get("fieldLength"), field.get("fieldDescription"),
                            ++sortNo, user.id(), user.id(), user.id());
                } catch (DataIntegrityViolationException ex) {
                    throw new IllegalArgumentException("字段英文名或字段中文名在参数内已存在");
                }
                audit(user, "FIELD_CREATE", scope, id);
            }
            acceptedRef[0]++;
        } catch (Exception ex) {
            errors.add("参数「" + groupName + "」：" + messageOf(ex));
        }
    }

    /** 导入字段行校验：必填、字符规则、长度、类型码值与组内中英文名唯一；通过后返回可写入字段。 */
    private List<Map<String, Object>> validateImportFields(Map<String, String> fieldTypeLabelToCode,
                                                           List<Map<String, Object>> groupFields) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> englishNames = new LinkedHashSet<>();
        Set<String> chineseNames = new LinkedHashSet<>();
        for (Map<String, Object> raw : groupFields) {
            String en = String.valueOf(raw.get("fieldNameEn"));
            String cn = String.valueOf(raw.get("fieldNameCn"));
            String typeLabel = String.valueOf(raw.get("fieldTypeLabel"));
            String lengthRaw = String.valueOf(raw.get("fieldLength"));
            String desc = String.valueOf(raw.get("fieldDescription"));
            if (en == null || en.isBlank()) throw new IllegalArgumentException("字段英文名为空");
            if (en.length() > MAX_FIELD_NAME_LENGTH || !FIELD_ENGLISH_NAME_PATTERN.matcher(en).matches()) {
                throw new IllegalArgumentException("字段英文名只能包含字母、数字和下划线且不超过 128 字符");
            }
            if (cn == null || cn.isBlank()) throw new IllegalArgumentException("字段中文名为空");
            if (cn.codePointCount(0, cn.length()) > MAX_FIELD_NAME_LENGTH) {
                throw new IllegalArgumentException("字段中文名不能超过 128 字符");
            }
            if (cn.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("字段中文名不允许空格");
            }
            if (!englishNames.add(en.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("字段英文名在参数内重复");
            }
            if (!chineseNames.add(cn.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("字段中文名在参数内重复");
            }
            String fieldType = requireOptionCode(fieldTypeLabelToCode, typeLabel, "字段类型");
            Integer fieldLength = null;
            if (lengthRaw != null && !lengthRaw.isBlank()) {
                try {
                    long length = Long.parseLong(lengthRaw);
                    if (length <= 0 || length > 2147483647L) throw new NumberFormatException("out of range");
                    fieldLength = (int) length;
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("字段长度必须为正整数");
                }
            }
            if (desc != null && !desc.isBlank() && desc.codePointCount(0, desc.length()) > MAX_FIELD_DESC_LENGTH) {
                throw new IllegalArgumentException("字段说明超过 500 字符");
            }
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("fieldNameEn", en);
            field.put("fieldNameCn", cn);
            field.put("fieldType", fieldType);
            field.put("fieldLength", fieldLength);
            field.put("fieldDescription", blankAsNull(desc));
            result.add(field);
        }
        return result;
    }

    private static void requireGroupConsistent(String value, String expected, String label) {
        if (value != null && !value.isBlank() && expected != null && !value.equals(expected)) {
            throw new IllegalArgumentException(label + "与同一参数首行信息不一致");
        }
    }

    private static String messageOf(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
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
                String parameterNameEn = String.valueOf(row.get("parameter_name_en"));
                if (activeNameExists(projectId, systemCode, parameterName, user.tenantId(), id)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "参数名称已存在活动记录，无法恢复");
                }
                if (activeEnglishNameExists(projectId, systemCode, parameterNameEn, user.tenantId(), id)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "参数英文名已存在活动记录，无法恢复");
                }
            }
            try {
                int changed = jdbc.update("UPDATE dm_parameter SET deleted = 0, deleted_by = NULL, deleted_at = NULL, " +
                                "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 1",
                        id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移参数状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "参数名称或参数英文名已存在活动记录，无法恢复");
            }
            jdbc.update("UPDATE dm_parameter_field SET deleted = 0, deleted_by = NULL, deleted_at = NULL, " +
                            "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE parameter_id = ? AND tenant_id = ? AND deleted = 1",
                    user.id(), id, user.tenantId());
            audit(user, "PARAMETER_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            // REQ-20260910-068：存在任意依赖记录时不得彻底删除参数
            if (hasAnyDependency(id, user.tenantId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "参数存在依赖关系记录，不能彻底删除");
            }
            jdbc.update("DELETE FROM dm_parameter_field WHERE parameter_id = ? AND tenant_id = ?",
                    id, user.tenantId());
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
            sql.append(" AND (a.parameter_name LIKE ? OR a.parameter_name_en LIKE ? OR a.parameter_description LIKE ?)");
            args.add(k);
            args.add(k);
            args.add(k);
        }
    }

    private void appendRecycleFilters(StringBuilder sql, List<Object> args, long projectId, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (keyword != null && !keyword.isBlank()) {
            String k = "%" + keyword.trim() + "%";
            sql.append(" AND (a.parameter_name LIKE ? OR a.parameter_name_en LIKE ? OR a.parameter_description LIKE ?)");
            args.add(k);
            args.add(k);
            args.add(k);
        }
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tenant_id, project_id, system_code, parameter_type, parameter_scope, parameter_name, parameter_name_en, parameter_description, owner_id " +
                "FROM dm_parameter WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移参数不存在");
        return rows.get(0);
    }

    private Map<String, Object> findRawIncludingDeleted(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id, system_code, parameter_name, parameter_name_en FROM dm_parameter WHERE id = ? AND tenant_id = ? AND deleted = 1",
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

    private boolean englishNameExists(long projectId, String systemCode, String parameterNameEn, long tenantId, Long excludeId) {
        return exists("SELECT COUNT(*) FROM dm_parameter WHERE tenant_id = ? AND project_id = ? AND system_code = ? AND parameter_name_en = ? AND id <> ?",
                tenantId, projectId, systemCode, parameterNameEn, excludeId == null ? -1L : excludeId);
    }

    private boolean activeEnglishNameExists(long projectId, String systemCode, String parameterNameEn,
                                            long tenantId, Long excludeId) {
        return exists("SELECT COUNT(*) FROM dm_parameter WHERE tenant_id = ? AND project_id = ? AND system_code = ? AND parameter_name_en = ? AND deleted = 0 AND id <> ?",
                tenantId, projectId, systemCode, parameterNameEn, excludeId == null ? -1L : excludeId);
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

    private List<Long> normalizeIds(Collection<Long> ids) {
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

    private String requireEnglishName(Object value) {
        String name = optionalText(value);
        if (name == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "参数英文名不能为空");
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数英文名不能超过 255 字符");
        }
        if (!ENGLISH_NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数英文名只能包含字母、数字和下划线");
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

    /** 检查参数是否有活动依赖关系（REQ-20260910-068 引用保护）。 */
    private boolean hasActiveDependency(long parameterId, long tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_dependency WHERE tenant_id = ? AND parameter_id = ? AND deleted = 0",
                Integer.class, tenantId, parameterId);
        return count != null && count > 0;
    }

    /** 检查参数是否有任意依赖记录（含已删除，REQ-20260910-068 彻底删除保护）。 */
    private boolean hasAnyDependency(long parameterId, long tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_dependency WHERE tenant_id = ? AND parameter_id = ?",
                Integer.class, tenantId, parameterId);
        return count != null && count > 0;
    }
}

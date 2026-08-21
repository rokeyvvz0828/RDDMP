/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/BusinessDayService.java
 * 说明：营业日环境、日历安排、跑批需求和业务审计的领域服务。
 * 用途：集中执行租户隔离、字段校验、自然键覆盖、引用保护、用户绑定与持久化事务。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryItem;
import com.ccb.system.model.UserDirectoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BusinessDayService {
    private static final Set<String> BATCH_TYPES = Set.of("全量", "增量", "初始化", "翻数");
    private static final Set<String> THEMES = Set.of("brand", "success", "warning", "danger", "accent");
    private static final Set<String> ADOPTIONS = Set.of("PENDING", "ACCEPTED", "REJECTED");
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 1000);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final UserDirectoryPort userDirectory;

    record BatchFields(boolean hasBatch, String type, Time time, String systemsJson, String validationContent) { }

    public BusinessDayService(JdbcTemplate jdbc, ObjectMapper objectMapper, UserDirectoryPort userDirectory) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.userDirectory = userDirectory;
    }

    // 关键逻辑：所有查询首先绑定认证用户 tenantId，分页条件不得由客户端覆盖租户边界。
    public PageResult<Map<String, Object>> environments(PageQuery query, String keyword, Boolean enabled, AuthUser user) {
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        StringBuilder where = new StringBuilder(" WHERE e.tenant_id = ? AND e.deleted = 0");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.env_code LIKE ? OR e.env_name LIKE ? OR COALESCE(e.purpose, '') LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (enabled != null) { where.append(" AND e.enabled = ?"); args.add(enabled ? 1 : 0); }
        long total = count("tm_test_environment e" + where, args);
        List<Object> pageArgs = pageArgs(args, query);
        String select = "SELECT e.id, e.env_code, e.env_name, e.purpose, e.theme, e.sort_no, e.enabled, e.remark, "
                + "e.created_at, e.updated_at, "
                + "(SELECT COUNT(*) FROM tm_calendar_schedule s WHERE s.tenant_id=e.tenant_id AND s.env_code=e.env_code AND s.deleted=0) schedule_count, "
                + "(SELECT COUNT(*) FROM tm_batch_requirement r WHERE r.tenant_id=e.tenant_id AND r.env_code=e.env_code AND r.deleted=0) requirement_count "
                + "FROM tm_test_environment e" + where + " ORDER BY e.sort_no, e.id DESC LIMIT ?, ?";
        return new PageResult<>(jdbc.queryForList(select, pageArgs.toArray()), total, query.page(), query.size());
    }

    public List<Map<String, Object>> activeEnvironments(AuthUser user) {
        return jdbc.queryForList("SELECT id, env_code, env_name, purpose, theme, sort_no FROM tm_test_environment "
                + "WHERE tenant_id = ? AND deleted = 0 AND enabled = 1 ORDER BY sort_no, id", user.tenantId());
    }

    @Transactional
    public Map<String, Object> createEnvironment(Map<String, Object> body, AuthUser user) {
        String code = envCode(body.get("env_code"));
        requireUniqueEnvironment(code, null, user.tenantId());
        long id = nextId();
        jdbc.update("INSERT INTO tm_test_environment (id, tenant_id, env_code, env_name, purpose, theme, sort_no, enabled, remark, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), code, requiredText(body, "env_name", "环境名称", 128),
                optionalText(body, "purpose", 255), theme(body.get("theme")), integer(body.get("sort_no"), 0),
                bool(body.get("enabled"), true) ? 1 : 0, optionalText(body, "remark", 500), user.id(), user.id());
        audit("ENVIRONMENT", id, "CREATE", Map.of("env_code", code), user);
        return environment(id, user.tenantId());
    }

    // 关键逻辑：环境编码变更与所有关联记录更新必须处于同一事务，避免日历引用断裂。
    @Transactional
    public Map<String, Object> updateEnvironment(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> old = environment(id, user.tenantId());
        String oldCode = String.valueOf(old.get("env_code"));
        String code = envCode(body.get("env_code"));
        requireUniqueEnvironment(code, id, user.tenantId());
        jdbc.update("UPDATE tm_test_environment SET env_code=?, env_name=?, purpose=?, theme=?, sort_no=?, enabled=?, remark=?, updated_by=? "
                        + "WHERE id=? AND tenant_id=? AND deleted=0",
                code, requiredText(body, "env_name", "环境名称", 128), optionalText(body, "purpose", 255),
                theme(body.get("theme")), integer(body.get("sort_no"), 0), bool(body.get("enabled"), true) ? 1 : 0,
                optionalText(body, "remark", 500), user.id(), id, user.tenantId());
        if (!oldCode.equals(code)) {
            jdbc.update("UPDATE tm_calendar_schedule SET env_code=?, updated_by=? WHERE tenant_id=? AND env_code=? AND deleted=0",
                    code, user.id(), user.tenantId(), oldCode);
            jdbc.update("UPDATE tm_batch_requirement SET env_code=?, updated_by=? WHERE tenant_id=? AND env_code=? AND deleted=0",
                    code, user.id(), user.tenantId(), oldCode);
        }
        audit("ENVIRONMENT", id, "UPDATE", Map.of("old_env_code", oldCode, "env_code", code), user);
        return environment(id, user.tenantId());
    }

    // 关键逻辑：环境删除先统计两类业务引用，存在引用时返回冲突而不是级联删除历史数据。
    @Transactional
    public void deleteEnvironment(long id, AuthUser user) {
        Map<String, Object> row = environment(id, user.tenantId());
        String code = String.valueOf(row.get("env_code"));
        long schedules = count("tm_calendar_schedule WHERE tenant_id=? AND env_code=? AND deleted=0", List.of(user.tenantId(), code));
        long requirements = count("tm_batch_requirement WHERE tenant_id=? AND env_code=? AND deleted=0", List.of(user.tenantId(), code));
        if (schedules + requirements > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "环境仍被 " + schedules + " 条日历安排和 " + requirements + " 条跑批需求引用");
        }
        jdbc.update("UPDATE tm_test_environment SET deleted=1, updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", user.id(), id, user.tenantId());
        audit("ENVIRONMENT", id, "DELETE", Map.of("env_code", code), user);
    }

    public PageResult<Map<String, Object>> schedules(PageQuery query, String keyword, String envCode,
                                                      String dateFrom, String dateTo, Boolean hasBatch,
                                                      String batchType, AuthUser user) {
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        StringBuilder where = new StringBuilder(" WHERE s.tenant_id=? AND s.deleted=0");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.business_date LIKE ? OR COALESCE(s.validation_content,'') LIKE ? OR COALESCE(s.maintainer,'') LIKE ?)");
            String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like);
        }
        if (envCode != null && !envCode.isBlank()) { where.append(" AND s.env_code=?"); args.add(envCode(envCode)); }
        if (dateFrom != null && !dateFrom.isBlank()) { where.append(" AND s.natural_date>=?"); args.add(Date.valueOf(date(dateFrom, "开始日期"))); }
        if (dateTo != null && !dateTo.isBlank()) { where.append(" AND s.natural_date<=?"); args.add(Date.valueOf(date(dateTo, "结束日期"))); }
        if (hasBatch != null) { where.append(" AND s.has_batch=?"); args.add(hasBatch ? 1 : 0); }
        if (batchType != null && !batchType.isBlank()) { where.append(" AND s.batch_type=?"); args.add(batchType(batchType)); }
        long total = count("tm_calendar_schedule s" + where, args);
        List<Object> pageArgs = pageArgs(args, query);
        List<Map<String, Object>> rows = jdbc.queryForList(scheduleSelect() + where
                + " ORDER BY s.natural_date DESC, s.id DESC LIMIT ?, ?", pageArgs.toArray());
        decorateSystems(rows);
        return new PageResult<>(rows, total, query.page(), query.size());
    }

    public List<Map<String, Object>> overview(String month, String envCode, AuthUser user) {
        YearMonth value;
        try { value = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month); }
        catch (DateTimeParseException exception) { throw bad("月份格式应为 YYYY-MM"); }
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), Date.valueOf(value.atDay(1)), Date.valueOf(value.atEndOfMonth())));
        String envFilter = "";
        if (envCode != null && !envCode.isBlank()) { envFilter = " AND s.env_code=?"; args.add(envCode(envCode)); }
        List<Map<String, Object>> rows = jdbc.queryForList(scheduleSelect()
                + " WHERE s.tenant_id=? AND s.deleted=0 AND s.natural_date BETWEEN ? AND ?" + envFilter
                + " ORDER BY s.natural_date, e.sort_no, s.id", args.toArray());
        decorateSystems(rows);
        return rows;
    }

    @Transactional
    public Map<String, Object> saveSchedule(Map<String, Object> body, AuthUser user) {
        return saveSchedule(null, body, user, "CREATE");
    }

    @Transactional
    public Map<String, Object> updateSchedule(long id, Map<String, Object> body, AuthUser user) {
        requireEntity("tm_calendar_schedule", id, user.tenantId(), "日历安排");
        return saveSchedule(id, body, user, "UPDATE");
    }

    // 关键逻辑：环境编码和自然日组成业务自然键；新增命中自然键时原子覆盖并返回 overwritten 标识。
    private Map<String, Object> saveSchedule(Long requestedId, Map<String, Object> body, AuthUser user, String action) {
        String code = requireActiveEnvironment(body.get("env_code"), user.tenantId());
        LocalDate naturalDate = date(String.valueOf(body.get("natural_date")), "自然日");
        String businessDate = businessDate(body.get("business_date"));
        boolean hasBatch = bool(body.get("has_batch"), false);
        BatchFields batch = batchFields(body, hasBatch);
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM tm_calendar_schedule WHERE tenant_id=? AND env_code=? AND natural_date=? AND deleted=0",
                user.tenantId(), code, Date.valueOf(naturalDate));
        Long id = requestedId;
        boolean overwritten = false;
        if (!existing.isEmpty()) {
            long existingId = ((Number) existing.get(0).get("id")).longValue();
            if (requestedId != null && requestedId != existingId) throw new BusinessException(ErrorCode.CONFLICT, "该环境在此自然日已有日历安排");
            id = existingId;
            overwritten = requestedId == null;
        }
        if (id == null) {
            id = nextId();
            jdbc.update("INSERT INTO tm_calendar_schedule (id,tenant_id,env_code,natural_date,business_date,has_batch,batch_type,batch_time,systems_json,validation_content,maintainer,created_by,updated_by) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, user.tenantId(), code, Date.valueOf(naturalDate), businessDate, batch.hasBatch() ? 1 : 0, batch.type(), batch.time(),
                    batch.systemsJson(), batch.validationContent(), maintainer(body, user), user.id(), user.id());
        } else {
            jdbc.update("UPDATE tm_calendar_schedule SET env_code=?,natural_date=?,business_date=?,has_batch=?,batch_type=?,batch_time=?,systems_json=?,validation_content=?,maintainer=?,updated_by=? "
                            + "WHERE id=? AND tenant_id=? AND deleted=0",
                    code, Date.valueOf(naturalDate), businessDate, batch.hasBatch() ? 1 : 0, batch.type(), batch.time(), batch.systemsJson(),
                    batch.validationContent(), maintainer(body, user), user.id(), id, user.tenantId());
        }
        audit("SCHEDULE", id, overwritten ? "OVERWRITE" : action, Map.of("env_code", code, "natural_date", naturalDate.toString()), user);
        Map<String, Object> result = schedule(id, user.tenantId());
        result.put("overwritten", overwritten);
        return result;
    }

    @Transactional
    public void deleteSchedule(long id, AuthUser user) {
        int changed = jdbc.update("UPDATE tm_calendar_schedule SET deleted=1,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", user.id(), id, user.tenantId());
        if (changed == 0) throw notFound("日历安排");
        audit("SCHEDULE", id, "DELETE", Map.of(), user);
    }

    // 关键逻辑：批量导入在单事务内逐行复用日历校验与覆盖规则，任一非法行会整体回滚。
    @Transactional
    public Map<String, Object> importSchedules(List<Map<String, Object>> records, AuthUser user) {
        if (records.isEmpty()) throw bad("导入文件没有有效数据行");
        if (records.size() > 2000) throw bad("单次最多导入 2000 行");
        int created = 0;
        int overwritten = 0;
        for (Map<String, Object> record : records) {
            Map<String, Object> saved = saveSchedule(null, record, user, "IMPORT");
            if (Boolean.TRUE.equals(saved.get("overwritten"))) overwritten++; else created++;
        }
        audit("SCHEDULE", 0, "IMPORT", Map.of("created", created, "overwritten", overwritten), user);
        return Map.of("total", records.size(), "created", created, "overwritten", overwritten);
    }

    public List<Map<String, Object>> allSchedulesForExport(String envCode, String dateFrom, String dateTo, AuthUser user) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int page = 1; page <= 50; page++) {
            PageResult<Map<String, Object>> sample = schedules(new PageQuery(page, 100), null, envCode, dateFrom, dateTo, null, null, user);
            result.addAll(sample.records());
            if (result.size() >= sample.total()) break;
        }
        return result;
    }

    public PageResult<Map<String, Object>> requirements(PageQuery query, String keyword, String envCode,
                                                         String naturalDate, String adoption, AuthUser user) {
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        StringBuilder where = new StringBuilder(" WHERE r.tenant_id=? AND r.deleted=0");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (r.business_date LIKE ? OR COALESCE(r.validation_content,'') LIKE ?)");
            String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like);
        }
        if (envCode != null && !envCode.isBlank()) { where.append(" AND r.env_code=?"); args.add(envCode(envCode)); }
        if (naturalDate != null && !naturalDate.isBlank()) { where.append(" AND r.natural_date LIKE ?"); args.add(naturalDate.trim() + "%"); }
        if (adoption != null && !adoption.isBlank()) { where.append(" AND r.adoption=?"); args.add(adoption(adoption)); }
        long total = count("tm_batch_requirement r" + where, args);
        List<Object> pageArgs = pageArgs(args, query);
        List<Map<String, Object>> rows = jdbc.queryForList(requirementSelect() + where
                + " ORDER BY r.created_at DESC, r.id DESC LIMIT ?, ?", pageArgs.toArray());
        decorateRequirements(rows, user.tenantId());
        return new PageResult<>(rows, total, query.page(), query.size());
    }

    @Transactional
    public Map<String, Object> createRequirement(Map<String, Object> body, AuthUser user) {
        return persistRequirement(null, body, user, "CREATE");
    }

    @Transactional
    public Map<String, Object> updateRequirement(long id, Map<String, Object> body, AuthUser user) {
        requireEntity("tm_batch_requirement", id, user.tenantId(), "跑批需求");
        return persistRequirement(id, body, user, "UPDATE");
    }

    // 关键逻辑：提出人必须经平台用户目录校验为当前租户有效用户，业务模块不直接访问 sys_user。
    private Map<String, Object> persistRequirement(Long requestedId, Map<String, Object> body, AuthUser user, String action) {
        String code = requireActiveEnvironment(body.get("env_code"), user.tenantId());
        String naturalDate = requirementDate(body.get("natural_date"));
        String businessDate = businessDate(body.get("business_date"));
        // 关键逻辑：跑批需求在领域层固定为跑批，客户端传 has_batch=false 也不能绕过字段矩阵。
        BatchFields batch = batchFields(body, true);
        long proposerId = longValue(body.get("proposer_id"), "请选择提出人");
        userDirectory.findActive(user.tenantId(), proposerId).orElseThrow(() -> bad("提出人不是当前租户的有效用户"));
        long id = requestedId == null ? nextId() : requestedId;
        if (requestedId == null) {
            jdbc.update("INSERT INTO tm_batch_requirement (id,tenant_id,env_code,natural_date,business_date,has_batch,batch_type,batch_time,systems_json,validation_content,proposer_id,created_by,updated_by) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, user.tenantId(), code, naturalDate, businessDate, 1, batch.type(), batch.time(), batch.systemsJson(),
                    batch.validationContent(), proposerId, user.id(), user.id());
        } else {
            jdbc.update("UPDATE tm_batch_requirement SET env_code=?,natural_date=?,business_date=?,has_batch=?,batch_type=?,batch_time=?,systems_json=?,validation_content=?,proposer_id=?,updated_by=? "
                            + "WHERE id=? AND tenant_id=? AND deleted=0",
                    code, naturalDate, businessDate, 1, batch.type(), batch.time(), batch.systemsJson(),
                    batch.validationContent(), proposerId, user.id(), id, user.tenantId());
        }
        audit("REQUIREMENT", id, action, Map.of("env_code", code, "proposer_id", proposerId), user);
        return requirement(id, user.tenantId());
    }

    // 关键逻辑：评审只更新结论和评审审计，不隐式创建或修改日历安排。
    @Transactional
    public Map<String, Object> reviewRequirement(long id, String value, String comment, AuthUser user) {
        String normalized = adoption(value);
        if ("PENDING".equals(normalized)) throw bad("评审结果只能选择采纳或不采纳");
        int changed = jdbc.update("UPDATE tm_batch_requirement SET adoption=?,review_comment=?,reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP,updated_by=? "
                        + "WHERE id=? AND tenant_id=? AND deleted=0",
                normalized, bounded(comment, 500, "评审意见"), user.id(), user.id(), id, user.tenantId());
        if (changed == 0) throw notFound("跑批需求");
        audit("REQUIREMENT", id, "REVIEW", Map.of("adoption", normalized), user);
        return requirement(id, user.tenantId());
    }

    @Transactional
    public void deleteRequirement(long id, AuthUser user) {
        int changed = jdbc.update("UPDATE tm_batch_requirement SET deleted=1,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", user.id(), id, user.tenantId());
        if (changed == 0) throw notFound("跑批需求");
        audit("REQUIREMENT", id, "DELETE", Map.of(), user);
    }

    public List<Map<String, Object>> allRequirementsForExport(String envCode, String naturalDate, String adoption, AuthUser user) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int page = 1; page <= 50; page++) {
            PageResult<Map<String, Object>> sample = requirements(new PageQuery(page, 100), null, envCode, naturalDate, adoption, user);
            result.addAll(sample.records());
            if (result.size() >= sample.total()) break;
        }
        return result;
    }

    public List<UserDirectoryItem> users(String keyword, AuthUser user) {
        return userDirectory.listActive(user.tenantId(), keyword, 100);
    }

    private Map<String, Object> environment(long id, long tenantId) {
        return jdbc.queryForList("SELECT id,env_code,env_name,purpose,theme,sort_no,enabled,remark,created_at,updated_at FROM tm_test_environment WHERE id=? AND tenant_id=? AND deleted=0", id, tenantId)
                .stream().findFirst().orElseThrow(() -> notFound("测试环境"));
    }

    private Map<String, Object> schedule(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(scheduleSelect() + " WHERE s.id=? AND s.tenant_id=? AND s.deleted=0", id, tenantId);
        if (rows.isEmpty()) throw notFound("日历安排");
        decorateSystems(rows);
        return rows.get(0);
    }

    private Map<String, Object> requirement(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(requirementSelect() + " WHERE r.id=? AND r.tenant_id=? AND r.deleted=0", id, tenantId);
        if (rows.isEmpty()) throw notFound("跑批需求");
        decorateRequirements(rows, tenantId);
        return rows.get(0);
    }

    private String scheduleSelect() {
        return "SELECT s.id,s.env_code,e.env_name,e.theme,s.natural_date,s.business_date,s.has_batch,s.batch_type,s.batch_time,s.systems_json,s.validation_content,s.maintainer,s.created_at,s.updated_at "
                + "FROM tm_calendar_schedule s LEFT JOIN tm_test_environment e ON e.tenant_id=s.tenant_id AND e.env_code=s.env_code AND e.deleted=0";
    }

    private String requirementSelect() {
        return "SELECT r.id,r.env_code,e.env_name,e.theme,r.natural_date,r.business_date,r.has_batch,r.batch_type,r.batch_time,r.systems_json,r.validation_content,r.proposer_id,r.reviewer_id,r.adoption,r.review_comment,r.reviewed_at,r.created_at,r.updated_at "
                + "FROM tm_batch_requirement r LEFT JOIN tm_test_environment e ON e.tenant_id=r.tenant_id AND e.env_code=r.env_code AND e.deleted=0";
    }

    private void decorateSystems(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object json = row.remove("systems_json");
            try { row.put("systems", json == null ? List.of() : objectMapper.readValue(String.valueOf(json), STRING_LIST)); }
            catch (JsonProcessingException exception) { row.put("systems", List.of()); }
        }
    }

    private void decorateRequirements(List<Map<String, Object>> rows, long tenantId) {
        decorateSystems(rows);
        Map<Long, UserDirectoryItem> cache = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            attachUser(row, "proposer", number(row.get("proposer_id")), tenantId, cache);
            attachUser(row, "reviewer", number(row.get("reviewer_id")), tenantId, cache);
        }
    }

    private void attachUser(Map<String, Object> row, String prefix, Long id, long tenantId, Map<Long, UserDirectoryItem> cache) {
        if (id == null) return;
        UserDirectoryItem item = cache.computeIfAbsent(id, key -> userDirectory.findActive(tenantId, key).orElse(null));
        if (item == null) { row.put(prefix + "_name", "用户#" + id); return; }
        row.put(prefix + "_name", item.displayName());
        row.put(prefix + "_username", item.username());
        row.put(prefix + "_org_name", item.orgName());
        row.put(prefix + "_mobile_phone", item.mobilePhone());
    }

    private void requireUniqueEnvironment(String code, Long exceptId, long tenantId) {
        String except = exceptId == null ? "" : " AND id<>?";
        List<Object> args = new ArrayList<>(List.of(tenantId, code));
        if (exceptId != null) args.add(exceptId);
        if (count("tm_test_environment WHERE tenant_id=? AND env_code=? AND deleted=0" + except, args) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "环境编码已存在");
        }
    }

    private String requireActiveEnvironment(Object value, long tenantId) {
        String code = envCode(value);
        if (count("tm_test_environment WHERE tenant_id=? AND env_code=? AND deleted=0 AND enabled=1", List.of(tenantId, code)) == 0) {
            throw bad("测试环境不存在或已停用");
        }
        return code;
    }

    private void requireEntity(String table, long id, long tenantId, String label) {
        if (count(table + " WHERE id=? AND tenant_id=? AND deleted=0", List.of(id, tenantId)) == 0) throw notFound(label);
    }

    // 关键逻辑：每次写操作在同一业务事务内写入模块自有审计表，失败时业务写入也随事务回滚。
    private void audit(String entityType, long entityId, String action, Map<String, Object> detail, AuthUser user) {
        try {
            jdbc.update("INSERT INTO tm_business_day_audit (id,tenant_id,entity_type,entity_id,action_code,operator_id,detail_json) VALUES (?,?,?,?,?,?,?)",
                    nextId(), user.tenantId(), entityType, entityId, action, user.id(), objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审计记录序列化失败");
        }
    }

    private String systemsJson(Object value) {
        List<?> raw = value instanceof List<?> list ? list : List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object item : raw) {
            String text = String.valueOf(item == null ? "" : item).trim();
            if (text.isEmpty()) continue;
            if (text.length() > 80) throw bad("单个涉及系统名称不能超过 80 个字符");
            normalized.add(text);
        }
        if (normalized.size() > 30) throw bad("涉及系统最多 30 个");
        try { return objectMapper.writeValueAsString(normalized); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "涉及系统序列化失败"); }
    }

    // 关键逻辑：所有手工和导入写入共享同一字段矩阵；翻数只豁免空值，非空值仍执行既有格式与限额校验。
    BatchFields batchFields(Map<String, Object> body, boolean hasBatch) {
        if (!hasBatch) return new BatchFields(false, null, null, systemsJson(List.of()), null);
        String type = batchType(body.get("batch_type"));
        boolean turnover = "翻数".equals(type);
        Time batchTime = turnover ? optionalTime(body.get("batch_time")) : time(body.get("batch_time"));
        String systems = systemsJson(body.get("systems"));
        if (!turnover && "[]".equals(systems)) throw bad("跑批时至少填写一个涉及系统");
        String validation = turnover
                ? optionalText(body, "validation_content", 1000)
                : requiredText(body, "validation_content", "跑批验证内容", 1000);
        return new BatchFields(true, type, batchTime, systems, validation);
    }

    private String envCode(Object value) {
        String code = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{1,64}")) throw bad("环境编码仅支持 1-64 位字母、数字、下划线和短横线");
        return code;
    }

    private String theme(Object value) {
        String theme = value == null || String.valueOf(value).isBlank() ? "brand" : String.valueOf(value);
        if (!THEMES.contains(theme)) throw bad("环境主题无效");
        return theme;
    }

    private String batchType(Object value) {
        String type = String.valueOf(value == null ? "" : value).trim();
        if (!BATCH_TYPES.contains(type)) throw bad("请选择有效的跑批类型");
        return type;
    }

    private String adoption(String value) {
        String result = value == null ? "PENDING" : value.trim().toUpperCase(Locale.ROOT);
        if (!ADOPTIONS.contains(result)) throw bad("采纳状态无效");
        return result;
    }

    private LocalDate date(String value, String label) {
        try { return LocalDate.parse(value); }
        catch (RuntimeException exception) { throw bad(label + "格式应为 YYYY-MM-DD"); }
    }

    private String requirementDate(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        try {
            if (text.length() == 7) YearMonth.parse(text); else LocalDate.parse(text);
            return text;
        } catch (DateTimeParseException exception) { throw bad("需求日期格式应为 YYYY-MM 或 YYYY-MM-DD"); }
    }

    private String businessDate(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        try { LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE); return text; }
        catch (DateTimeParseException exception) { throw bad("营业日格式应为 YYYYMMDD"); }
    }

    private Time time(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (text.isEmpty()) throw bad("跑批时必须填写跑批时间");
        try { return Time.valueOf(LocalTime.parse(text.length() == 5 ? text + ":00" : text)); }
        catch (RuntimeException exception) { throw bad("跑批时间格式应为 HH:mm"); }
    }

    private Time optionalTime(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return text.isEmpty() ? null : time(text);
    }

    private String maintainer(Map<String, Object> body, AuthUser user) {
        String value = optionalText(body, "maintainer", 128);
        return value == null || value.isBlank() ? user.displayName() : value;
    }

    private String requiredText(Map<String, Object> body, String key, String label, int max) {
        String value = String.valueOf(body.getOrDefault(key, "")).trim();
        if (value.isEmpty()) throw bad("请填写" + label);
        if (value.length() > max) throw bad(label + "不能超过 " + max + " 个字符");
        return value;
    }

    private String optionalText(Map<String, Object> body, String key, int max) {
        return bounded(body.get(key) == null ? null : String.valueOf(body.get(key)), max, key);
    }

    private String bounded(String value, int max, String label) {
        if (value == null) return null;
        String text = value.trim();
        if (text.length() > max) throw bad(label + "不能超过 " + max + " 个字符");
        return text.isEmpty() ? null : text;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Set.of("true", "1", "yes").contains(String.valueOf(value).toLowerCase(Locale.ROOT));
    }

    private int integer(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw bad("排序号必须是整数"); }
    }

    private long longValue(Object value, String message) {
        try { return Long.parseLong(String.valueOf(value)); }
        catch (RuntimeException exception) { throw bad(message); }
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private long count(String fromWhere, List<Object> args) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromWhere, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    private List<Object> pageArgs(List<Object> args, PageQuery query) {
        List<Object> result = new ArrayList<>(args);
        result.add((query.page() - 1) * query.size());
        result.add(query.size());
        return result;
    }

    private long nextId() {
        long floor = System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
        return IDS.updateAndGet(previous -> Math.max(previous + 1, floor));
    }

    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private BusinessException notFound(String label) { return new BusinessException(ErrorCode.BAD_REQUEST, label + "不存在"); }
}

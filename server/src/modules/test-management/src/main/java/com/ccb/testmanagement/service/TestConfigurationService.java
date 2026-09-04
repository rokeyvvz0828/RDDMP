/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestConfigurationService.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

// 关键逻辑：所有读写以认证用户的租户、测试大类和项目为共同边界；写入由事务与审计保持一致性。

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryItem;
import com.ccb.system.model.UserDirectoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/** 测试管理配置服务：所有写入均以测试大类、项目、租户三重边界约束。 */
@Service
public class TestConfigurationService {
    private static final Set<String> DOMAINS = Set.of("application-assembly", "user-testing", "non-functional", "security");
    private static final Set<String> ROLE_CODES = Set.of("TEST_MANAGER", "TESTER", "DEVELOPER");
    private static final Set<String> ROUND_STATUSES = Set.of("DRAFT", "ACTIVE", "CLOSED");
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 1000);
    private static final List<DictionarySeed> DEFAULT_DICTIONARIES = List.of(
            dictionary("case_type", "案例类型", "LOCAL", "建设案例|BUILD,投产案例|RELEASE,优化案例|OPTIMIZE"),
            dictionary("case_nature", "案例性质", "LOCAL", "正向|POSITIVE,反向|NEGATIVE"),
            dictionary("case_priority", "案例优先级", "LOCAL", "高|HIGH,中|MEDIUM,低|LOW"),
            dictionary("func_type", "功能类型", "LOCAL", "联机交易|ONLINE,批处理|BATCH,报表|REPORT"),
            dictionary("change_status", "变动状态", "LOCAL", "新增|NEW,修改|CHANGE"),
            dictionary("importance", "业务重要程度", "LOCAL", "高|HIGH,中|MEDIUM,低|LOW"),
            dictionary("accounting_flag", "是否核算相关", "LOCAL", "是|YES,否|NO"),
            dictionary("defect_category", "缺陷分类", "LOCAL", "功能缺陷|FUNCTION,数据缺陷|DATA,界面缺陷|UI,性能缺陷|PERFORMANCE,安全缺陷|SECURITY,环境问题|ENVIRONMENT,需求问题|REQUIREMENT,操作理解|OPERATION,其他|OTHER"),
            dictionary("severity", "缺陷严重程度", "LOCAL", "致命|FATAL,严重|SERIOUS,一般|NORMAL,轻微|MINOR"),
            dictionary("defect_priority", "缺陷优先级", "LOCAL", "高|HIGH,中|MEDIUM,低|LOW"),
            dictionary("urgency", "紧急程度", "LOCAL", "高|HIGH,中|MEDIUM,低|LOW"),
            dictionary("report_type", "报告类型", "SYSTEM", "轮次报告|ROUND,周期报告|CYCLE"),
            dictionary("test_env", "测试环境", "EXTERNAL", "SIT环境|SIT,UAT环境|UAT,性能环境|PERFORMANCE,灾备环境|DR")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final UserDirectoryPort users;

    public TestConfigurationService(JdbcTemplate jdbc, ObjectMapper objectMapper, UserDirectoryPort users) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.users = users;
    }

    /** 项目来源为既有项目模块；本模块不创建或修改项目主数据。 */
    public List<Map<String, Object>> projectOptions(AuthUser user) {
        return jdbc.queryForList("SELECT id, project_code, project_name, status FROM pm_project "
                + "WHERE tenant_id=? AND deleted=0 ORDER BY project_name, id", user.tenantId());
    }

    /** 临时规则：当前项目候选为当前租户所有未删除物理子系统。 */
    public PageResult<Map<String, Object>> systems(String domain, long projectId, PageQuery page, String keyword, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId());
        List<Object> args = new ArrayList<>(List.of(domain, projectId, user.tenantId()));
        StringBuilder where = new StringBuilder(" WHERE p.tenant_id=? AND p.deleted=0");
        if (hasText(keyword)) {
            where.append(" AND (p.code LIKE ? OR p.short_name LIKE ? OR p.name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM arch_physical_subsystem p" + where, Long.class, args.subList(2, args.size()).toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page.page() - 1) * page.size()); pageArgs.add(page.size());
        String sql = "SELECT p.id AS physical_subsystem_id,p.code,p.short_name,COALESCE(NULLIF(p.short_name,''),REPLACE(p.name,'物理子系统','')) AS name,"
                + "COALESCE(s.enabled,0) AS enabled,s.remark,s.updated_at "
                + "FROM arch_physical_subsystem p LEFT JOIN tm_test_participating_system s "
                + "ON s.tenant_id=p.tenant_id AND s.test_domain=? AND s.project_id=? "
                + "AND s.physical_subsystem_id=p.id AND s.deleted=0" + where
                + " ORDER BY p.code,p.id LIMIT ?,?";
        return new PageResult<>(jdbc.queryForList(sql, pageArgs.toArray()), total == null ? 0 : total, page.page(), page.size());
    }

    @Transactional
    public Map<String, Object> setSystem(String domain, long projectId, long physicalId, Map<String, Object> body, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId()); requirePhysical(physicalId, user.tenantId());
        boolean enabled = bool(body.get("enabled"), false);
        if (!enabled && !bool(body.get("confirmed"), false)) {
            return Map.of("confirmation_required", true, "impact", systemImpact(domain, projectId, physicalId, user.tenantId()));
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM tm_test_participating_system WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND deleted=0",
                user.tenantId(), domain, projectId, physicalId);
        long id;
        if (rows.isEmpty()) {
            id = nextId();
            jdbc.update("INSERT INTO tm_test_participating_system (id,tenant_id,test_domain,project_id,physical_subsystem_id,enabled,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?)",
                    id, user.tenantId(), domain, projectId, physicalId, enabled ? 1 : 0, text(body.get("remark"), 500, "备注"), user.id(), user.id());
        } else {
            id = number(rows.get(0).get("id"));
            jdbc.update("UPDATE tm_test_participating_system SET enabled=?,remark=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",
                    enabled ? 1 : 0, text(body.get("remark"), 500, "备注"), user.id(), id, user.tenantId());
        }
        audit(domain, projectId, "SYSTEM", id, enabled ? "ENABLE" : "DISABLE", Map.of("physical_subsystem_id", physicalId), user);
        return jdbc.queryForMap("SELECT id,physical_subsystem_id,enabled,remark,updated_at FROM tm_test_participating_system WHERE id=? AND tenant_id=?", id, user.tenantId());
    }

    public Map<String, Object> systemImpact(String domain, long projectId, long physicalId, long tenantId) {
        domain(domain); requireProject(projectId, tenantId); requirePhysical(physicalId, tenantId);
        return Map.of("unfinished_defects", 0, "latest_executions", 0, "message", "后续缺陷和执行模块尚未启用，当前无受影响业务数据");
    }

    public PageResult<Map<String, Object>> roles(String domain, long projectId, long physicalId, PageQuery page, AuthUser user) {
        domain(domain); requireParticipatingSystem(domain, projectId, physicalId, user.tenantId());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_system_role WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND deleted=0",
                Long.class, user.tenantId(), domain, projectId, physicalId);
        List<Map<String, Object>> records = jdbc.queryForList("SELECT r.id,r.user_id,r.role_code,r.role_name,r.created_at FROM tm_test_system_role r "
                + "WHERE r.tenant_id=? AND r.test_domain=? AND r.project_id=? AND r.physical_subsystem_id=? AND r.deleted=0 ORDER BY r.role_name,r.id LIMIT ?,?",
                user.tenantId(), domain, projectId, physicalId, (page.page() - 1) * page.size(), page.size());
        return new PageResult<>(records, total == null ? 0 : total, page.page(), page.size());
    }

    @Transactional
    public Map<String, Object> assignRole(String domain, long projectId, long physicalId, Map<String, Object> body, AuthUser user) {
        domain(domain); requireParticipatingSystem(domain, projectId, physicalId, user.tenantId());
        long userId = positive(body.get("user_id"), "用户");
        UserDirectoryItem member = users.findActive(user.tenantId(), userId).orElseThrow(() -> bad("用户不存在、未启用或不属于当前租户"));
        String code = roleCode(body.get("role_code"));
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_system_role WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND user_id=? AND role_code=? AND deleted=0",
                Long.class, user.tenantId(), domain, projectId, physicalId, userId, code);
        if (exists != null && exists > 0) throw conflict("该用户已拥有此系统角色");
        long id = nextId();
        jdbc.update("INSERT INTO tm_test_system_role (id,tenant_id,test_domain,project_id,physical_subsystem_id,user_id,role_code,role_name,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, user.tenantId(), domain, projectId, physicalId, userId, code, roleName(code), user.id(), user.id());
        audit(domain, projectId, "SYSTEM_ROLE", id, "CREATE", Map.of("user_id", userId, "username", member.username()), user);
        return jdbc.queryForMap("SELECT id,user_id,role_code,role_name,created_at FROM tm_test_system_role WHERE id=? AND tenant_id=?", id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> deleteRole(String domain, long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> role = role(id, domain, user.tenantId());
        if (!bool(body.get("confirmed"), false)) return Map.of("confirmation_required", true, "impact", Map.of("unfinished_defects", 0, "latest_executions", 0));
        jdbc.update("UPDATE tm_test_system_role SET deleted=1,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", user.id(), id, user.tenantId());
        audit(domain, number(role.get("project_id")), "SYSTEM_ROLE", id, "DELETE", Map.of("user_id", number(role.get("user_id"))), user);
        return Map.of("deleted", true);
    }

    public List<UserDirectoryItem> users(String keyword, AuthUser user) { return users.listActive(user.tenantId(), keyword, 50); }

    /** 两段式导入：本方法先完成全部主数据与业务规则校验；仅零错误时才进入写入循环。 */
    @Transactional
    public Map<String,Object> importSystems(String domain, long projectId, List<Map<String,Object>> rows, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId());
        Map<String,Map<String,Object>> prepared = new LinkedHashMap<>(); List<Map<String,Object>> errors = new ArrayList<>();
        for (Map<String,Object> row : rows) {
            String code = text(row.get("physical_code"), 32, "物理子系统编号");
            Long physicalId = findPhysicalByCode(code, user.tenantId());
            if (code == null) errors.add(importError(row, "物理子系统编号不能为空"));
            else if (physicalId == null) errors.add(importError(row, "物理子系统不存在或不属于当前租户：" + code));
            else {
                try { Map<String,Object> item = new LinkedHashMap<>(); item.put("physical_id", physicalId); item.put("enabled", importBoolean(row.get("enabled"), "是否参测")); item.put("remark", row.get("remark")); prepared.put(code, item); }
                catch (BusinessException exception) { errors.add(importError(row, exception.getMessage())); }
            }
        }
        if (!errors.isEmpty()) return importResult(rows.size(), prepared.size(), 0, errors);
        for (Map<String,Object> item : prepared.values()) setSystem(domain, projectId, number(item.get("physical_id")), Map.of("enabled", item.get("enabled"), "confirmed", true, "remark", item.get("remark") == null ? "" : item.get("remark")), user);
        audit(domain, projectId, "SYSTEM", 0, "IMPORT", Map.of("rows", prepared.size()), user);
        return importResult(rows.size(), prepared.size(), prepared.size(), List.of());
    }

    @Transactional
    public Map<String,Object> importRoles(String domain, long projectId, List<Map<String,Object>> rows, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId());
        Map<String,Map<String,Object>> prepared = new LinkedHashMap<>(); List<Map<String,Object>> errors = new ArrayList<>();
        for (Map<String,Object> row : rows) {
            String physicalCode = text(row.get("physical_code"), 32, "物理子系统编号"); Long physicalId = findPhysicalByCode(physicalCode, user.tenantId());
            Long userId = importPositive(row.get("user_id")); String code = String.valueOf(row.getOrDefault("role_code", "")).trim().toUpperCase(Locale.ROOT);
            String error = physicalCode == null ? "物理子系统编号不能为空" : physicalId == null ? "物理子系统不存在或不属于当前租户：" + physicalCode
                    : userId == null ? "用户ID无效" : !ROLE_CODES.contains(code) ? "角色编码无效" : null;
            if (error == null && users.findActive(user.tenantId(), userId).isEmpty()) error = "用户不存在、未启用或不属于当前租户";
            if (error == null) { try { requireParticipatingSystem(domain, projectId, physicalId, user.tenantId()); } catch (BusinessException exception) { error = "物理子系统尚未设为参测系统"; } }
            if (error != null) errors.add(importError(row, error));
            else prepared.put(physicalId + ":" + userId + ":" + code, Map.of("physical_id", physicalId, "user_id", userId, "role_code", code));
        }
        if (!errors.isEmpty()) return importResult(rows.size(), prepared.size(), 0, errors);
        int created = 0;
        for (Map<String,Object> item : prepared.values()) {
            long physicalId=number(item.get("physical_id")), userId=number(item.get("user_id")); String code=String.valueOf(item.get("role_code"));
            Long exists=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_system_role WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND user_id=? AND role_code=? AND deleted=0",Long.class,user.tenantId(),domain,projectId,physicalId,userId,code);
            if (exists != null && exists > 0) continue;
            long id=nextId(); jdbc.update("INSERT INTO tm_test_system_role (id,tenant_id,test_domain,project_id,physical_subsystem_id,user_id,role_code,role_name,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",id,user.tenantId(),domain,projectId,physicalId,userId,code,roleName(code),user.id(),user.id()); created++;
        }
        audit(domain, projectId, "SYSTEM_ROLE", 0, "IMPORT", Map.of("rows", prepared.size(), "created", created), user);
        return importResult(rows.size(), prepared.size(), created, List.of());
    }

    public PageResult<Map<String, Object>> rounds(String domain, long projectId, PageQuery page, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId());
        return page("tm_test_round", "test_domain=? AND project_id=?", List.of(user.tenantId(), domain, projectId), page, "sort_no,id", "id,round_code,round_name,planned_start_date,planned_end_date,status,sort_no,remark,updated_at");
    }

    @Transactional
    public Map<String, Object> saveRound(String domain, long projectId, Long id, Map<String, Object> body, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId());
        String requestedCode = text(body.get("round_code"), 64, "轮次编码");
        String code = requestedCode == null && id == null ? nextRoundCode(domain, projectId, user.tenantId()) : code(requestedCode, "轮次编码");
        String name = required(body.get("round_name"), "轮次名称", 128);
        DateRange dates = dates(body); String status = roundStatus(body.get("status"));
        unique("tm_test_round", "round_code", code, id, user.tenantId(), domain, projectId, null, "轮次编码");
        unique("tm_test_round", "round_name", name, id, user.tenantId(), domain, projectId, null, "轮次名称");
        long saved = id == null ? nextId() : id;
        if (id == null) jdbc.update("INSERT INTO tm_test_round (id,tenant_id,test_domain,project_id,round_code,round_name,planned_start_date,planned_end_date,status,sort_no,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                saved,user.tenantId(),domain,projectId,code,name,dates.start(),dates.end(),status,integer(body.get("sort_no")),text(body.get("remark"),500,"备注"),user.id(),user.id());
        else { requireRound(id, domain, projectId, user.tenantId()); jdbc.update("UPDATE tm_test_round SET round_code=?,round_name=?,planned_start_date=?,planned_end_date=?,status=?,sort_no=?,remark=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",
                code,name,dates.start(),dates.end(),status,integer(body.get("sort_no")),text(body.get("remark"),500,"备注"),user.id(),id,user.tenantId()); }
        audit(domain, projectId, "ROUND", saved, id == null ? "CREATE" : "UPDATE", Map.of("round_code", code), user);
        return jdbc.queryForMap("SELECT id,round_code,round_name,planned_start_date,planned_end_date,status,sort_no,remark,updated_at FROM tm_test_round WHERE id=? AND tenant_id=?", saved, user.tenantId());
    }

    @Transactional
    public void deleteRound(String domain, long projectId, long id, AuthUser user) {
        requireRound(id, domain, projectId, user.tenantId());
        Long cycles = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_cycle WHERE tenant_id=? AND round_id=? AND deleted=0", Long.class, user.tenantId(), id);
        if (cycles != null && cycles > 0) throw conflict("轮次仍包含测试周期，不能删除");
        jdbc.update("UPDATE tm_test_round SET deleted=1,updated_by=? WHERE id=? AND tenant_id=?", user.id(), id, user.tenantId());
        audit(domain, projectId, "ROUND", id, "DELETE", Map.of(), user);
    }

    public PageResult<Map<String, Object>> cycles(String domain, long projectId, long roundId, PageQuery page, AuthUser user) {
        requireRound(roundId, domain(domain), projectId, user.tenantId());
        return page("tm_test_cycle", "round_id=?", List.of(user.tenantId(), roundId), page, "sort_no,id", "id,cycle_code,cycle_name,planned_start_date,planned_end_date,status,sort_no,remark,updated_at");
    }

    @Transactional
    public Map<String, Object> saveCycle(String domain, long projectId, long roundId, Long id, Map<String, Object> body, AuthUser user) {
        requireRound(roundId, domain(domain), projectId, user.tenantId());
        String requestedCode = text(body.get("cycle_code"), 64, "周期编码");
        String code = requestedCode == null && id == null ? nextCycleCode(roundId, user.tenantId()) : code(requestedCode, "周期编码");
        String name = required(body.get("cycle_name"), "周期名称", 128); DateRange dates = dates(body);
        unique("tm_test_cycle", "cycle_code", code, id, user.tenantId(), null, null, roundId, "周期编码");
        unique("tm_test_cycle", "cycle_name", name, id, user.tenantId(), null, null, roundId, "周期名称");
        long saved = id == null ? nextId() : id;
        if (id == null) jdbc.update("INSERT INTO tm_test_cycle (id,tenant_id,round_id,cycle_code,cycle_name,planned_start_date,planned_end_date,status,sort_no,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                saved,user.tenantId(),roundId,code,name,dates.start(),dates.end(),roundStatus(body.get("status")),integer(body.get("sort_no")),text(body.get("remark"),500,"备注"),user.id(),user.id());
        else { requireCycle(id, roundId, user.tenantId()); jdbc.update("UPDATE tm_test_cycle SET cycle_code=?,cycle_name=?,planned_start_date=?,planned_end_date=?,status=?,sort_no=?,remark=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",
                code,name,dates.start(),dates.end(),roundStatus(body.get("status")),integer(body.get("sort_no")),text(body.get("remark"),500,"备注"),user.id(),id,user.tenantId()); }
        audit(domain, projectId, "CYCLE", saved, id == null ? "CREATE" : "UPDATE", Map.of("cycle_code", code), user);
        Map<String,Object> result=jdbc.queryForMap("SELECT id,cycle_code,cycle_name,planned_start_date,planned_end_date,status,sort_no,remark,updated_at FROM tm_test_cycle WHERE id=? AND tenant_id=?",saved,user.tenantId());
        result.put("outside_round_warning", outsideRound(roundId, dates, user.tenantId())); return result;
    }

    @Transactional
    public void deleteCycle(String domain, long projectId, long roundId, long id, AuthUser user) {
        requireRound(roundId, domain(domain), projectId, user.tenantId()); requireCycle(id, roundId, user.tenantId());
        jdbc.update("UPDATE tm_test_cycle SET deleted=1,updated_by=? WHERE id=? AND tenant_id=?", user.id(), id, user.tenantId());
        audit(domain, projectId, "CYCLE", id, "DELETE", Map.of(), user);
    }

    public PageResult<Map<String, Object>> dictionaries(String domain, long projectId, PageQuery page, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId());
        ensureDefaultDictionaries(domain, projectId, user);
        return page("tm_test_dictionary", "test_domain=? AND project_id=?", List.of(user.tenantId(), domain, projectId), page, "dictionary_name,id", "id,dictionary_code,dictionary_name,source_type,enabled,remark,updated_at");
    }

    @Transactional
    public Map<String, Object> saveDictionary(String domain, long projectId, Long id, Map<String,Object> body, AuthUser user) {
        domain(domain); requireProject(projectId, user.tenantId()); String code=code(body.get("dictionary_code"),"字典编码"); String name=required(body.get("dictionary_name"),"字典名称",128);
        unique("tm_test_dictionary","dictionary_code",code,id,user.tenantId(),domain,projectId,null,"字典编码"); long saved=id==null?nextId():id;
        if(id==null) jdbc.update("INSERT INTO tm_test_dictionary (id,tenant_id,test_domain,project_id,dictionary_code,dictionary_name,source_type,enabled,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",saved,user.tenantId(),domain,projectId,code,name,"LOCAL",bool(body.get("enabled"),true)?1:0,text(body.get("remark"),500,"备注"),user.id(),user.id());
        else { requireDictionary(id,domain,projectId,user.tenantId()); jdbc.update("UPDATE tm_test_dictionary SET dictionary_code=?,dictionary_name=?,enabled=?,remark=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",code,name,bool(body.get("enabled"),true)?1:0,text(body.get("remark"),500,"备注"),user.id(),id,user.tenantId()); }
        audit(domain,projectId,"DICTIONARY",saved,id==null?"CREATE":"UPDATE",Map.of("dictionary_code",code),user);
        return jdbc.queryForMap("SELECT id,dictionary_code,dictionary_name,source_type,enabled,remark,updated_at FROM tm_test_dictionary WHERE id=? AND tenant_id=?",saved,user.tenantId());
    }

    @Transactional
    public void deleteDictionary(String domain, long projectId, long id, AuthUser user) {
        requireDictionary(id, domain(domain), projectId, user.tenantId());
        Long options = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_dictionary_option WHERE tenant_id=? AND dictionary_id=? AND deleted=0", Long.class, user.tenantId(), id);
        if (options != null && options > 0) throw conflict("字典仍包含选项，不能删除");
        jdbc.update("UPDATE tm_test_dictionary SET deleted=1,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", user.id(), id, user.tenantId());
        audit(domain, projectId, "DICTIONARY", id, "DELETE", Map.of(), user);
    }

    public PageResult<Map<String,Object>> options(String domain,long projectId,long dictionaryId,PageQuery page,AuthUser user){requireDictionary(dictionaryId,domain(domain),projectId,user.tenantId());return page("tm_test_dictionary_option","dictionary_id=?",List.of(user.tenantId(),dictionaryId),page,"sort_no,id","id,option_code,option_name,enabled,sort_no,remark,updated_at");}

    @Transactional
    public Map<String,Object> saveOption(String domain,long projectId,long dictionaryId,Long id,Map<String,Object> body,AuthUser user){requireDictionary(dictionaryId,domain(domain),projectId,user.tenantId());String code=code(body.get("option_code"),"选项编码");String name=required(body.get("option_name"),"选项名称",128);unique("tm_test_dictionary_option","option_code",code,id,user.tenantId(),null,null,dictionaryId,"选项编码");long saved=id==null?nextId():id;if(id==null)jdbc.update("INSERT INTO tm_test_dictionary_option (id,tenant_id,dictionary_id,option_code,option_name,enabled,sort_no,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",saved,user.tenantId(),dictionaryId,code,name,bool(body.get("enabled"),true)?1:0,integer(body.get("sort_no")),text(body.get("remark"),500,"备注"),user.id(),user.id());else{requireOption(id,dictionaryId,user.tenantId());jdbc.update("UPDATE tm_test_dictionary_option SET option_code=?,option_name=?,enabled=?,sort_no=?,remark=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",code,name,bool(body.get("enabled"),true)?1:0,integer(body.get("sort_no")),text(body.get("remark"),500,"备注"),user.id(),id,user.tenantId());}audit(domain,projectId,"DICTIONARY_OPTION",saved,id==null?"CREATE":"UPDATE",Map.of("option_code",code),user);return jdbc.queryForMap("SELECT id,option_code,option_name,enabled,sort_no,remark,updated_at FROM tm_test_dictionary_option WHERE id=? AND tenant_id=?",saved,user.tenantId());}

    @Transactional
    public void deleteOption(String domain,long projectId,long dictionaryId,long id,AuthUser user){requireDictionary(dictionaryId,domain(domain),projectId,user.tenantId());requireOption(id,dictionaryId,user.tenantId());jdbc.update("UPDATE tm_test_dictionary_option SET deleted=1,updated_by=? WHERE id=? AND tenant_id=?",user.id(),id,user.tenantId());audit(domain,projectId,"DICTIONARY_OPTION",id,"DELETE",Map.of(),user);}

    private PageResult<Map<String,Object>> page(String table,String condition,List<Object> bound,PageQuery page,String order,String fields){List<Object> args=new ArrayList<>(bound);String where=" WHERE tenant_id=? AND deleted=0 AND "+condition;Long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+where,Long.class,args.toArray());args.add((page.page()-1)*page.size());args.add(page.size());return new PageResult<>(jdbc.queryForList("SELECT "+fields+" FROM "+table+where+" ORDER BY "+order+" LIMIT ?,?",args.toArray()),total==null?0:total,page.page(),page.size());}
    private void requireProject(long id,long tenant){if(jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id=? AND tenant_id=? AND deleted=0",Long.class,id,tenant)==0)throw bad("项目不存在或不属于当前租户");}
    private void requirePhysical(long id,long tenant){if(jdbc.queryForObject("SELECT COUNT(*) FROM arch_physical_subsystem WHERE id=? AND tenant_id=? AND deleted=0",Long.class,id,tenant)==0)throw bad("物理子系统不存在或不属于当前租户");}
    private void requireParticipatingSystem(String d,long p,long s,long t){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_participating_system WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND enabled=1 AND deleted=0",Long.class,t,d,p,s);if(n==null||n==0)throw bad("请先启用该参测系统");}
    private void requireRound(long id,String d,long p,long t){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_round WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0",Long.class,id,t,d,p);if(n==null||n==0)throw bad("测试轮次不存在");}
    private void requireCycle(long id,long r,long t){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_cycle WHERE id=? AND round_id=? AND tenant_id=? AND deleted=0",Long.class,id,r,t);if(n==null||n==0)throw bad("测试周期不存在");}
    private void requireDictionary(long id,String d,long p,long t){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_dictionary WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0",Long.class,id,t,d,p);if(n==null||n==0)throw bad("字典不存在");}
    private void requireOption(long id,long d,long t){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_dictionary_option WHERE id=? AND dictionary_id=? AND tenant_id=? AND deleted=0",Long.class,id,d,t);if(n==null||n==0)throw bad("字典选项不存在");}
    private Map<String,Object> role(long id,String d,long t){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,project_id,user_id FROM tm_test_system_role WHERE id=? AND tenant_id=? AND test_domain=? AND deleted=0",id,t,d);if(rows.isEmpty())throw bad("系统角色不存在");return rows.get(0);}
    private void unique(String table,String column,String value,Long id,long tenant,String d,Long p,Long parent,String label){StringBuilder sql=new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" WHERE tenant_id=? AND ").append(column).append("=? AND deleted=0");List<Object> args=new ArrayList<>(List.of(tenant,value));if(d!=null){sql.append(" AND test_domain=? AND project_id=?");args.add(d);args.add(p);}if(parent!=null){sql.append(table.equals("tm_test_cycle")?" AND round_id=?":" AND dictionary_id=?");args.add(parent);}if(id!=null){sql.append(" AND id<>?");args.add(id);}Long n=jdbc.queryForObject(sql.toString(),Long.class,args.toArray());if(n!=null&&n>0)throw conflict(label+"已存在");}
    private boolean outsideRound(long roundId,DateRange cycle,long tenant){Map<String,Object> r=jdbc.queryForMap("SELECT planned_start_date,planned_end_date FROM tm_test_round WHERE id=? AND tenant_id=?",roundId,tenant);Date start=(Date)r.get("planned_start_date"),end=(Date)r.get("planned_end_date");return (start!=null&&cycle.start()!=null&&cycle.start().before(start))||(end!=null&&cycle.end()!=null&&cycle.end().after(end));}
    private void audit(String d,long project,String type,long id,String action,Map<String,Object> detail,AuthUser user){try{jdbc.update("INSERT INTO tm_test_configuration_audit (id,tenant_id,test_domain,project_id,entity_type,entity_id,action_code,operator_id,detail_json) VALUES (?,?,?,?,?,?,?,?,?)",nextId(),user.tenantId(),d,project,type,id,action,user.id(),objectMapper.writeValueAsString(detail));}catch(JsonProcessingException e){throw new BusinessException(ErrorCode.INTERNAL_ERROR,"配置审计序列化失败");}}
    private String domain(String value){if(!DOMAINS.contains(value))throw bad("测试大类无效");return value;}
    private String roleCode(Object value){String code=String.valueOf(value==null?"":value).trim().toUpperCase(Locale.ROOT);if(!ROLE_CODES.contains(code))throw bad("系统角色无效");return code;}
    private String roleName(String c){return Map.of("TEST_MANAGER","测试经理","TESTER","测试人员","DEVELOPER","开发人员").get(c);}
    private static DictionarySeed dictionary(String code,String name,String source,String items){return new DictionarySeed(code,name,source,items);}
    @Transactional
    private void ensureDefaultDictionaries(String domain,long projectId,AuthUser user){
        for(DictionarySeed seed:DEFAULT_DICTIONARIES){
            List<Map<String,Object>> existing=jdbc.queryForList("SELECT id FROM tm_test_dictionary WHERE tenant_id=? AND test_domain=? AND project_id=? AND dictionary_code=? AND deleted=0",user.tenantId(),domain,projectId,seed.code());
            long dictionaryId;
            if(existing.isEmpty()){
                dictionaryId=nextId();
                jdbc.update("INSERT INTO tm_test_dictionary (id,tenant_id,test_domain,project_id,dictionary_code,dictionary_name,source_type,enabled,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",dictionaryId,user.tenantId(),domain,projectId,seed.code(),seed.name(),seed.source(),1,"系统预置字典",user.id(),user.id());
            }else dictionaryId=number(existing.get(0).get("id"));
            int sort=1;
            for(String item:seed.items().split(",")){
                String[] parts=item.split("\\|",2); String optionName=parts[0]; String optionCode=parts[1];
                Long count=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_dictionary_option WHERE tenant_id=? AND dictionary_id=? AND option_code=? AND deleted=0",Long.class,user.tenantId(),dictionaryId,optionCode);
                if(count==null||count==0)jdbc.update("INSERT INTO tm_test_dictionary_option (id,tenant_id,dictionary_id,option_code,option_name,enabled,sort_no,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",nextId(),user.tenantId(),dictionaryId,optionCode,optionName,1,sort,"系统预置选项",user.id(),user.id());
                sort++;
            }
        }
    }
    private String nextRoundCode(String domain,long project,long tenant){
        for(int number=1;number<1_000_000;number++){
            String candidate="R"+String.format("%03d",number);
            Long exists=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_round WHERE tenant_id=? AND test_domain=? AND project_id=? AND round_code=? AND deleted=0",Long.class,tenant,domain,project,candidate);
            if(exists==null||exists==0)return candidate;
        }
        throw conflict("轮次编号已用尽");
    }
    private String nextCycleCode(long round,long tenant){
        for(int number=1;number<1_000_000;number++){
            String candidate="C"+String.format("%03d",number);
            Long exists=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_cycle WHERE tenant_id=? AND round_id=? AND cycle_code=? AND deleted=0",Long.class,tenant,round,candidate);
            if(exists==null||exists==0)return candidate;
        }
        throw conflict("周期编号已用尽");
    }
    private String roundStatus(Object v){String s=v==null?"DRAFT":String.valueOf(v).trim().toUpperCase(Locale.ROOT);if(!ROUND_STATUSES.contains(s))throw bad("状态无效");return s;}
    private String code(Object v,String label){String s=String.valueOf(v==null?"":v).trim().toUpperCase(Locale.ROOT);if(!s.matches("[A-Z0-9_-]{1,64}"))throw bad(label+"仅支持 1-64 位字母、数字、下划线和短横线");return s;}
    private String required(Object v,String label,int max){String s=text(v,max,label);if(s==null)throw bad("请填写"+label);return s;}
    private String text(Object v,int max,String label){if(v==null)return null;String s=String.valueOf(v).trim();if(s.length()>max)throw bad(label+"不能超过 "+max+" 个字符");return s.isEmpty()?null:s;}
    private long positive(Object v,String label){try{long n=Long.parseLong(String.valueOf(v));if(n<=0)throw new NumberFormatException();return n;}catch(RuntimeException e){throw bad(label+"无效");}}
    private long number(Object v){return ((Number)v).longValue();}
    private int integer(Object v){if(v==null||String.valueOf(v).isBlank())return 0;try{return Integer.parseInt(String.valueOf(v));}catch(RuntimeException e){throw bad("排序号必须是整数");}}
    private boolean bool(Object v,boolean fallback){if(v==null)return fallback;if(v instanceof Boolean b)return b;if(v instanceof Number n)return n.intValue()!=0;return Set.of("true","1","yes").contains(String.valueOf(v).toLowerCase(Locale.ROOT));}
    private DateRange dates(Map<String,Object> b){Date start=date(b.get("planned_start_date"),"计划开始日期"),end=date(b.get("planned_end_date"),"计划结束日期");if(start!=null&&end!=null&&start.after(end))throw bad("计划结束日期不能早于开始日期");return new DateRange(start,end);}
    private Date date(Object v,String label){if(v==null||String.valueOf(v).isBlank())return null;try{return Date.valueOf(LocalDate.parse(String.valueOf(v)));}catch(RuntimeException e){throw bad(label+"格式应为 YYYY-MM-DD");}}
    private long nextId(){long floor=System.currentTimeMillis()*1000+ThreadLocalRandom.current().nextInt(1000);return IDS.updateAndGet(previous->Math.max(previous+1,floor));}
    private Long findPhysicalByCode(String code,long tenant){if(code==null)return null;List<Map<String,Object>> rows=jdbc.queryForList("SELECT id FROM arch_physical_subsystem WHERE tenant_id=? AND code=? AND deleted=0",tenant,code);return rows.isEmpty()?null:number(rows.get(0).get("id"));}
    private Map<String,Object> importError(Map<String,Object> row,String message){return Map.of("row_number",row.getOrDefault("row_number",0),"message",message);}
    private Map<String,Object> importResult(int total,int valid,int written,List<Map<String,Object>> errors){return Map.of("total",total,"valid",valid,"written",written,"errors",errors,"success",errors.isEmpty());}
    private boolean importBoolean(Object value,String label){String text=String.valueOf(value==null?"":value).trim().toLowerCase(Locale.ROOT);if(Set.of("是","true","1","yes").contains(text))return true;if(Set.of("否","false","0","no").contains(text))return false;throw bad(label+"仅支持 是/否");}
    private Long importPositive(Object value){try{long parsed=Long.parseLong(String.valueOf(value).trim());return parsed>0?parsed:null;}catch(RuntimeException exception){return null;}}
    private boolean hasText(String s){return s!=null&&!s.isBlank();}
    private BusinessException bad(String s){return new BusinessException(ErrorCode.BAD_REQUEST,s);}
    private BusinessException conflict(String s){return new BusinessException(ErrorCode.CONFLICT,s);}
    private record DictionarySeed(String code,String name,String source,String items){}
    private record DateRange(Date start,Date end){}
}

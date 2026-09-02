/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/plan/TestPlanService.java
 * 说明：测试方案的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.plan;

// 关键逻辑：所有读写以认证用户的租户、测试大类和项目为共同边界；写入由事务与审计保持一致性。

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 测试方案服务：方案级别由树节点决定，版本永远追加而不覆盖。 */
@Service
public class TestPlanService {
    public static final String BUSINESS_TYPE = "TEST_PLAN_VERSION";
    private static final Set<String> DOMAINS = Set.of("application-assembly", "user-testing", "non-functional", "security");
    private static final Set<String> NODE_TYPES = Set.of("PROJECT", "SYSTEM", "SPECIAL");
    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachments;

    public TestPlanService(JdbcTemplate jdbc, AttachmentGateway attachments) { this.jdbc = jdbc; this.attachments = attachments; }

    public Map<String, Object> tree(String domain, long projectId, AuthUser user) {
        scope(domain, projectId, user);
        Map<String, Object> project = jdbc.queryForMap("SELECT id,project_code,project_name FROM pm_project WHERE id=? AND tenant_id=? AND deleted=0", projectId, user.tenantId());
        List<Map<String, Object>> systems = jdbc.queryForList("SELECT s.physical_subsystem_id AS id,p.code,COALESCE(NULLIF(p.short_name,''),REPLACE(p.name,'物理子系统','')) AS name,p.short_name FROM tm_test_participating_system s JOIN arch_physical_subsystem p ON p.id=s.physical_subsystem_id AND p.tenant_id=s.tenant_id AND p.deleted=0 WHERE s.tenant_id=? AND s.test_domain=? AND s.project_id=? AND s.enabled=1 AND s.deleted=0 ORDER BY p.code,p.id", user.tenantId(), domain, projectId);
        List<Map<String, Object>> specials = jdbc.queryForList("SELECT s.id,s.node_name,s.updated_at,(SELECT COUNT(*) FROM tm_test_plan p WHERE p.tenant_id=s.tenant_id AND p.test_domain=s.test_domain AND p.project_id=s.project_id AND p.special_node_id=s.id AND p.deleted=0) AS plan_count FROM tm_test_plan_special_node s WHERE s.tenant_id=? AND s.test_domain=? AND s.project_id=? AND s.deleted=0 ORDER BY s.node_name,s.id", user.tenantId(), domain, projectId);
        return Map.of("project", project, "systems", systems, "specials", specials);
    }

    public PageResult<Map<String, Object>> plans(String domain, long projectId, Map<String, Object> node, PageQuery page, AuthUser user) {
        Node target = requireNode(domain, projectId, node, user);
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), domain, projectId, target.type()));
        StringBuilder where = new StringBuilder(" WHERE p.tenant_id=? AND p.test_domain=? AND p.project_id=? AND p.node_type=? AND p.deleted=0");
        nodeWhere(where, args, target);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_plan p" + where, Long.class, args.toArray());
        List<Object> rowsArgs = new ArrayList<>(args); rowsArgs.add((page.page() - 1) * page.size()); rowsArgs.add(page.size());
        String sql = "SELECT p.id,p.plan_name,p.updated_at,v.id AS version_id,v.version_no,v.version_note,v.attachment_id,v.file_name,v.file_extension,v.file_size,v.uploaded_by,v.created_at AS uploaded_at,(SELECT u.display_name FROM sys_user u WHERE u.id=v.uploaded_by AND u.tenant_id=v.tenant_id AND u.deleted=0) AS uploader_name FROM tm_test_plan p JOIN tm_test_plan_version v ON v.id=(SELECT x.id FROM tm_test_plan_version x WHERE x.tenant_id=p.tenant_id AND x.plan_id=p.id AND x.deleted=0 ORDER BY x.version_no DESC,x.id DESC LIMIT 1)" + where + " ORDER BY p.updated_at DESC,p.id DESC LIMIT ?,?";
        return new PageResult<>(jdbc.queryForList(sql, rowsArgs.toArray()), total == null ? 0 : total, page.page(), page.size());
    }

    public Map<String, Object> current(String domain, long projectId, Map<String, Object> node, AuthUser user) {
        PageResult<Map<String, Object>> result = plans(domain, projectId, node, new PageQuery(1, 1), user);
        if (result.records().isEmpty()) return Map.of();
        return result.records().get(0);
    }

    public List<Map<String, Object>> versions(String domain, long projectId, long planId, AuthUser user) {
        requirePlan(planId, domain, projectId, user.tenantId());
        return jdbc.queryForList("SELECT v.id AS version_id,v.version_no,v.version_note,v.attachment_id,v.file_name,v.file_extension,v.file_size,v.uploaded_by,v.created_at AS uploaded_at,(SELECT u.display_name FROM sys_user u WHERE u.id=v.uploaded_by AND u.tenant_id=v.tenant_id AND u.deleted=0) AS uploader_name FROM tm_test_plan_version v WHERE v.tenant_id=? AND v.plan_id=? AND v.deleted=0 ORDER BY v.version_no DESC,v.id DESC", user.tenantId(), planId);
    }

    @Transactional
    public Map<String, Object> createSpecial(String domain, long projectId, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user); String name = required(body.get("node_name"), "专项名称", 100);
        Long duplicates = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_plan_special_node WHERE tenant_id=? AND test_domain=? AND project_id=? AND node_name=? AND deleted=0", Long.class, user.tenantId(), domain, projectId, name);
        if (duplicates != null && duplicates > 0) throw conflict("专项名称已存在"); long id = next();
        jdbc.update("INSERT INTO tm_test_plan_special_node(id,tenant_id,test_domain,project_id,node_name,created_by,updated_by) VALUES(?,?,?,?,?,?,?)", id, user.tenantId(), domain, projectId, name, user.id(), user.id());
        audit(domain, projectId, null, null, "CREATE_SPECIAL", user, Map.of("node_name", name));
        return jdbc.queryForMap("SELECT id,node_name,updated_at FROM tm_test_plan_special_node WHERE id=? AND tenant_id=?", id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateSpecial(String domain, long projectId, long id, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user); requireSpecial(id, domain, projectId, user.tenantId()); String name = required(body.get("node_name"), "专项名称", 100);
        Long duplicates = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_plan_special_node WHERE tenant_id=? AND test_domain=? AND project_id=? AND node_name=? AND id<>? AND deleted=0", Long.class, user.tenantId(), domain, projectId, name, id);
        if (duplicates != null && duplicates > 0) throw conflict("专项名称已存在"); jdbc.update("UPDATE tm_test_plan_special_node SET node_name=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", name, user.id(), id, user.tenantId());
        audit(domain, projectId, null, null, "UPDATE_SPECIAL", user, Map.of("node_name", name)); return jdbc.queryForMap("SELECT id,node_name,updated_at FROM tm_test_plan_special_node WHERE id=? AND tenant_id=?", id, user.tenantId());
    }

    @Transactional
    public void deleteSpecial(String domain, long projectId, long id, AuthUser user) {
        scope(domain, projectId, user); requireSpecial(id, domain, projectId, user.tenantId());
        Long plans = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_plan WHERE tenant_id=? AND test_domain=? AND project_id=? AND special_node_id=? AND deleted=0", Long.class, user.tenantId(), domain, projectId, id);
        if (plans != null && plans > 0) throw conflict("该专项下已有方案，不能删除"); jdbc.update("UPDATE tm_test_plan_special_node SET deleted=1,updated_by=? WHERE id=? AND tenant_id=?", user.id(), id, user.tenantId()); audit(domain, projectId, null, null, "DELETE_SPECIAL", user, Map.of("special_id", id));
    }

    @Transactional
    public Map<String, Object> upload(String domain, long projectId, Long planId, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user); String note = required(body.get("version_note"), "版本说明", 200); long attachmentId = positive(body.get("attachment_id"), "方案文件");
        AttachmentItem attachment = attachments.get(attachmentId, user); validateAttachment(attachment, user);
        long savedPlan; String planName; Node node;
        if (planId != null) { Map<String, Object> plan = requirePlan(planId, domain, projectId, user.tenantId()); savedPlan = planId; planName = String.valueOf(plan.get("plan_name")); node = new Node(String.valueOf(plan.get("node_type")), numberOrNull(plan.get("physical_subsystem_id")), numberOrNull(plan.get("special_node_id"))); }
        else { node = requireNode(domain, projectId, body, user); planName = required(body.get("plan_name"), "方案名称", 100); List<Map<String, Object>> same = matchingPlans(domain, projectId, node, planName, user.tenantId());
            if (!same.isEmpty()) { savedPlan = number(same.get(0).get("id")); int nextVersion = nextVersion(savedPlan, user.tenantId()); if (!bool(body.get("confirm_version"))) return Map.of("version_confirmation_required", true, "plan_id", savedPlan, "next_version", nextVersion, "plan_name", planName); }
            else { savedPlan = next(); jdbc.update("INSERT INTO tm_test_plan(id,tenant_id,test_domain,project_id,node_type,physical_subsystem_id,special_node_id,plan_name,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)", savedPlan, user.tenantId(), domain, projectId, node.type(), node.systemId(), node.specialId(), planName, user.id(), user.id()); }
        }
        int versionNo = nextVersion(savedPlan, user.tenantId()); long versionId = next(); attachments.bind(new AttachmentBindingCommand(attachmentId, BUSINESS_TYPE, String.valueOf(versionId), String.valueOf(projectId)), user);
        jdbc.update("INSERT INTO tm_test_plan_version(id,tenant_id,plan_id,version_no,version_note,attachment_id,file_name,file_extension,file_size,uploaded_by) VALUES(?,?,?,?,?,?,?,?,?,?)", versionId, user.tenantId(), savedPlan, versionNo, note, attachmentId, attachment.fileName(), extension(attachment), attachment.fileSize(), user.id());
        jdbc.update("UPDATE tm_test_plan SET updated_by=? WHERE id=? AND tenant_id=?", user.id(), savedPlan, user.tenantId()); audit(domain, projectId, savedPlan, versionId, versionNo == 1 ? "CREATE" : "UPLOAD_VERSION", user, Map.of("plan_name", planName, "version_no", versionNo));
        return version(savedPlan, versionId, user.tenantId());
    }

    @Transactional
    public void deletePlan(String domain, long projectId, long id, AuthUser user) {
        scope(domain, projectId, user); requirePlan(id, domain, projectId, user.tenantId()); jdbc.update("UPDATE tm_test_plan SET deleted=1,deleted_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", user.id(), id, user.tenantId()); audit(domain, projectId, id, null, "DELETE", user, Map.of());
    }

    private List<Map<String, Object>> matchingPlans(String d, long p, Node node, String name, long tenant) { List<Object> args = new ArrayList<>(List.of(tenant, d, p, node.type(), name)); StringBuilder sql = new StringBuilder("SELECT id FROM tm_test_plan WHERE tenant_id=? AND test_domain=? AND project_id=? AND node_type=? AND plan_name=? AND deleted=0"); nodeWhere(sql, args, node); sql.append(" ORDER BY id LIMIT 1"); return jdbc.queryForList(sql.toString(), args.toArray()); }
    private void nodeWhere(StringBuilder sql, List<Object> args, Node node) { if ("SYSTEM".equals(node.type())) { sql.append(" AND physical_subsystem_id=?"); args.add(node.systemId()); } else if ("SPECIAL".equals(node.type())) { sql.append(" AND special_node_id=?"); args.add(node.specialId()); } }
    private Node requireNode(String d, long p, Map<String, Object> body, AuthUser u) { String type = String.valueOf(body.getOrDefault("node_type", "")).trim().toUpperCase(Locale.ROOT); if (!NODE_TYPES.contains(type)) throw bad("方案节点无效"); if ("PROJECT".equals(type)) return new Node(type, null, null); if ("SYSTEM".equals(type)) { long id = positive(body.get("physical_subsystem_id"), "参测系统"); Long n = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_participating_system WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND enabled=1 AND deleted=0", Long.class, u.tenantId(), d, p, id); if (n == null || n == 0) throw bad("请选择已参测系统"); return new Node(type, id, null); } long id = positive(body.get("special_node_id"), "专项节点"); requireSpecial(id, d, p, u.tenantId()); return new Node(type, null, id); }
    private Map<String, Object> requirePlan(long id, String d, long p, long tenant) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,node_type,physical_subsystem_id,special_node_id,plan_name FROM tm_test_plan WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0", id, tenant, d, p); if (rows.isEmpty()) throw bad("测试方案不存在或不属于当前项目"); return rows.get(0); }
    private void requireSpecial(long id, String d, long p, long tenant) { Long n = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_plan_special_node WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0", Long.class, id, tenant, d, p); if (n == null || n == 0) throw bad("专项节点不存在或不属于当前项目"); }
    private Map<String, Object> version(long planId, long versionId, long tenant) { return jdbc.queryForMap("SELECT v.id AS version_id,v.plan_id,v.version_no,v.version_note,v.attachment_id,v.file_name,v.file_extension,v.file_size,v.uploaded_by,v.created_at AS uploaded_at FROM tm_test_plan_version v WHERE v.id=? AND v.plan_id=? AND v.tenant_id=? AND v.deleted=0", versionId, planId, tenant); }
    private int nextVersion(long id, long tenant) { Integer n = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tm_test_plan_version WHERE tenant_id=? AND plan_id=? AND deleted=0", Integer.class, tenant, id); return n == null ? 1 : n; }
    private void scope(String d, long p, AuthUser u) { if (!DOMAINS.contains(d)) throw bad("测试大类无效"); Long n = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id=? AND tenant_id=? AND deleted=0", Long.class, p, u.tenantId()); if (n == null || n == 0) throw bad("项目不存在或无权访问"); }
    private void validateAttachment(AttachmentItem item, AuthUser user) { String extension = extension(item); if (!Set.of("docx", "xlsx").contains(extension)) throw bad("仅支持 .docx 或 .xlsx 格式，请另存为新格式后上传"); if (item.fileSize() > 50L * 1024 * 1024) throw bad("单个方案文件不能超过50MB"); if (item.uploaderId() != user.id()) throw bad("只能绑定本人上传的方案文件"); }
    private void audit(String d, long p, Long plan, Long version, String action, AuthUser u, Map<String, Object> detail) { jdbc.update("INSERT INTO tm_test_plan_audit(id,tenant_id,test_domain,project_id,plan_id,version_id,action_code,operator_id,detail_json) VALUES(?,?,?,?,?,?,?,?,?)", next(), u.tenantId(), d, p, plan, version, action, u.id(), "{}"); }
    private static String extension(AttachmentItem item) { String value = item.fileExtension(); if (value == null || value.isBlank()) { String name = item.fileName(); int index = name == null ? -1 : name.lastIndexOf('.'); value = index < 0 ? "" : name.substring(index + 1); } return value.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""); }
    private static long next() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
    private static long positive(Object value, String name) { try { long id = Long.parseLong(String.valueOf(value)); if (id > 0) return id; } catch (Exception ignored) { } throw bad(name + "无效"); }
    private static Long numberOrNull(Object value) { return value instanceof Number number ? number.longValue() : value == null ? null : Long.parseLong(String.valueOf(value)); }
    private static long number(Object value) { return ((Number) value).longValue(); }
    private static String required(Object value, String name, int max) { String text = value == null ? "" : String.valueOf(value).trim(); if (text.isBlank() || text.length() > max) throw bad(name + "无效"); return text; }
    private static boolean bool(Object value) { return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)); }
    private static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private static BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    private record Node(String type, Long systemId, Long specialId) { }
}

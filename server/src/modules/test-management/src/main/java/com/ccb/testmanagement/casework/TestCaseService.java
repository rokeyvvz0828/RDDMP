/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/casework/TestCaseService.java
 * 说明：测试案例的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.casework;

// 关键逻辑：所有读写以认证用户的租户、测试大类和项目为共同边界；写入由事务与审计保持一致性。

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** 测试案例领域：范围提供编号前缀和所属系统，案例目录独立维护。 */
@Service
public class TestCaseService {
    public static final String BUSINESS_TYPE = "TEST_CASE";
    private static final Set<String> DOMAINS = Set.of("application-assembly", "user-testing", "non-functional", "security");
    private static final Set<String> CASE_STATUS = Set.of("UNEXECUTED", "RUNNING", "FAILED", "SUCCESS", "INVALID");
    private static final Set<String> ACCOUNTING_STATUS = Set.of("UNEXECUTED", "PENDING_REVIEW", "SUCCESS", "FAILED", "INVALID");
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 1000);
    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachments;
    private final ObjectMapper objectMapper;

    public TestCaseService(JdbcTemplate jdbc, AttachmentGateway attachments, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.attachments = attachments;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> tree(String domain, long projectId, AuthUser user) {
        project(domain, projectId, user);
        List<Map<String, Object>> systems = jdbc.queryForList("SELECT s.physical_subsystem_id AS id,p.code,p.short_name,COALESCE(NULLIF(p.short_name,''),REPLACE(p.name,'物理子系统','')) AS name FROM tm_test_participating_system s JOIN arch_physical_subsystem p ON p.id=s.physical_subsystem_id AND p.tenant_id=s.tenant_id AND p.deleted=0 WHERE s.tenant_id=? AND s.test_domain=? AND s.project_id=? AND s.enabled=1 AND s.deleted=0 ORDER BY p.code,p.id", user.tenantId(), domain, projectId);
        List<Map<String, Object>> directories = jdbc.queryForList("SELECT d.id,d.physical_subsystem_id,d.parent_id,d.directory_name,d.sort_no,(SELECT COUNT(*) FROM tm_test_case c WHERE c.tenant_id=d.tenant_id AND c.directory_id=d.id AND c.deleted=0) AS case_count FROM tm_test_case_directory d WHERE d.tenant_id=? AND d.test_domain=? AND d.project_id=? AND d.deleted=0 ORDER BY d.physical_subsystem_id,d.sort_no,d.id", user.tenantId(), domain, projectId);
        return Map.of("systems", systems, "directories", directories);
    }

    public List<Map<String, Object>> scopes(String domain, long projectId, Long systemId, AuthUser user) {
        project(domain, projectId, user);
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), domain, projectId));
        String condition = "";
        if (systemId != null) { enabledSystem(domain, projectId, systemId, user.tenantId()); condition = " AND s.physical_subsystem_id=?"; args.add(systemId); }
        return jdbc.queryForList("SELECT s.id,s.scope_code,s.scope_name,s.physical_subsystem_id,p.code AS physical_system_code,COALESCE(NULLIF(p.short_name,''),REPLACE(p.name,'物理子系统','')) AS physical_system_name FROM tm_test_scope s JOIN arch_physical_subsystem p ON p.id=s.physical_subsystem_id AND p.tenant_id=s.tenant_id AND p.deleted=0 WHERE s.tenant_id=? AND s.test_domain=? AND s.project_id=? AND s.deleted=0" + condition + " ORDER BY s.scope_code", args.toArray());
    }

    public PageResult<Map<String, Object>> list(String domain, long projectId, Long systemId, Long directoryId, Long scopeId, String keyword, Collection<String> caseTypes, Collection<String> caseNatures, Collection<String> priorities, Collection<String> statuses, Collection<String> accountingResults, Long designerId, String executionReference, String sortBy, String sortOrder, PageQuery page, AuthUser user) {
        project(domain, projectId, user);
        StringBuilder where = new StringBuilder(" WHERE c.tenant_id=? AND c.test_domain=? AND c.project_id=? AND c.deleted=0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), domain, projectId));
        if (systemId != null) { enabledSystem(domain, projectId, systemId, user.tenantId()); where.append(" AND c.physical_subsystem_id=?"); args.add(systemId); }
        if (directoryId != null) { directory(directoryId, domain, projectId, user.tenantId()); List<Long> ids = descendantDirectories(directoryId, user.tenantId()); where.append(" AND c.directory_id IN (").append(placeholders(ids.size())).append(")"); args.addAll(ids); }
        if (scopeId != null) { scopeRecord(scopeId, domain, projectId, user.tenantId()); where.append(" AND c.scope_id=?"); args.add(scopeId); }
        String cleanKeyword = text(keyword, 200, "编号/名称关键字");
        if (cleanKeyword != null) { String like = "%" + cleanKeyword + "%"; where.append(" AND (c.case_code LIKE ? OR c.case_name LIKE ? OR s.scope_code LIKE ? OR s.scope_name LIKE ?)"); args.add(like); args.add(like); args.add(like); args.add(like); }
        in(where, args, "c.case_type", caseTypes, null, "案例类型");
        in(where, args, "c.test_level", caseNatures, null, "案例性质");
        in(where, args, "c.priority", priorities, null, "案例优先级");
        in(where, args, "c.accounting_result", accountingResults, ACCOUNTING_STATUS, "核算核对结果");
        if (designerId != null) { userExists(designerId, user.tenantId()); where.append(" AND c.created_by=?"); args.add(designerId); }
        String status = caseStatusExpression();
        List<String> requestedStatuses = cleanValues(statuses, CASE_STATUS, "案例状态");
        if (!requestedStatuses.isEmpty()) { where.append(" AND ").append(status).append(" IN (").append(placeholders(requestedStatuses.size())).append(")"); args.addAll(requestedStatuses); }
        if ("REFERENCED".equalsIgnoreCase(executionReference)) where.append(" AND EXISTS (SELECT 1 FROM tm_test_execution e0 WHERE e0.tenant_id=c.tenant_id AND e0.case_id=c.id AND e0.deleted=0)");
        if ("UNREFERENCED".equalsIgnoreCase(executionReference)) where.append(" AND NOT EXISTS (SELECT 1 FROM tm_test_execution e0 WHERE e0.tenant_id=c.tenant_id AND e0.case_id=c.id AND e0.deleted=0)");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_case c JOIN tm_test_scope s ON s.id=c.scope_id AND s.tenant_id=c.tenant_id AND s.deleted=0" + where, Long.class, args.toArray());
        String sql = "SELECT c.id,c.case_code,c.case_name,c.physical_subsystem_id,c.scope_id,s.scope_code,s.scope_name,p.code AS physical_system_code,COALESCE(NULLIF(p.short_name,''),REPLACE(p.name,'物理子系统','')) AS physical_system_name,c.directory_id,d.directory_name,c.case_type,c.test_level AS case_nature,c.priority,c.invalidated,c.invalid_reason,c.accounting_result,c.accounting_confirmed,c.accounting_confirmed_by,confirmer.display_name AS accounting_confirmer_name,c.created_by,creator.display_name AS created_by_name,c.created_at,c.updated_by,updater.display_name AS updated_by_name,c.updated_at," + status + " AS status,(SELECT COUNT(*) FROM tm_test_case_attachment a WHERE a.tenant_id=c.tenant_id AND a.case_id=c.id AND a.deleted=0) AS attachment_count,(SELECT COUNT(*) FROM tm_test_execution e0 WHERE e0.tenant_id=c.tenant_id AND e0.case_id=c.id AND e0.deleted=0) AS execution_reference_count FROM tm_test_case c JOIN tm_test_scope s ON s.id=c.scope_id AND s.tenant_id=c.tenant_id AND s.deleted=0 JOIN arch_physical_subsystem p ON p.id=c.physical_subsystem_id AND p.tenant_id=c.tenant_id AND p.deleted=0 LEFT JOIN tm_test_case_directory d ON d.id=c.directory_id AND d.tenant_id=c.tenant_id AND d.deleted=0 LEFT JOIN sys_user creator ON creator.id=c.created_by AND creator.tenant_id=c.tenant_id AND creator.deleted=0 LEFT JOIN sys_user updater ON updater.id=c.updated_by AND updater.tenant_id=c.tenant_id AND updater.deleted=0 LEFT JOIN sys_user confirmer ON confirmer.id=c.accounting_confirmed_by AND confirmer.tenant_id=c.tenant_id AND confirmer.deleted=0" + where + " ORDER BY " + order(sortBy, sortOrder) + " LIMIT ?,?";
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add((page.page() - 1) * page.size()); pageArgs.add(page.size());
        return new PageResult<>(jdbc.queryForList(sql, pageArgs.toArray()), total == null ? 0 : total, page.page(), page.size());
    }

    public Map<String, Object> detail(String domain, long projectId, long id, AuthUser user) {
        Map<String, Object> result = new LinkedHashMap<>(caseRow(id, domain, projectId, user.tenantId()));
        Map<String, Object> scope = scopeRecord(number(result.get("scope_id")), domain, projectId, user.tenantId());
        result.put("scope_code", scope.get("scope_code")); result.put("physical_subsystem_id", scope.get("physical_subsystem_id"));
        result.put("case_nature", result.get("test_level")); result.put("status", Boolean.TRUE.equals(result.get("invalidated")) || number(result.get("invalidated")) == 1 ? "INVALID" : "UNEXECUTED");
        result.put("attachments", jdbc.queryForList("SELECT a.attachment_id AS id,f.file_name,f.file_size,f.content_type,a.sort_no FROM tm_test_case_attachment a LEFT JOIN att_file f ON f.id=a.attachment_id AND f.tenant_id=a.tenant_id WHERE a.tenant_id=? AND a.case_id=? AND a.deleted=0 ORDER BY a.sort_no,a.id", user.tenantId(), id));
        return result;
    }

    public Map<String, Object> previewCode(String domain, long projectId, long scopeId, AuthUser user) {
        Map<String, Object> scope = scopeRecord(scopeId, domain, projectId, user.tenantId());
        int serial = nextSerial(scopeId, user.tenantId());
        return Map.of("scope_id", scopeId, "scope_code", scope.get("scope_code"), "case_code", caseCode(String.valueOf(scope.get("scope_code")), serial));
    }

    @Transactional
    public Map<String, Object> saveDirectory(String domain, long projectId, Long id, Map<String, Object> body, AuthUser user) {
        project(domain, projectId, user);
        long systemId = positive(body.get("physical_subsystem_id"), "参测系统"); enabledSystem(domain, projectId, systemId, user.tenantId());
        Long parentId = positiveOrNull(body.get("parent_id"));
        if (parentId != null) { Map<String, Object> parent = directory(parentId, domain, projectId, user.tenantId()); if (number(parent.get("physical_subsystem_id")) != systemId) throw bad("父目录必须属于同一参测系统"); if (id != null && (id.equals(parentId) || descendantDirectories(id, user.tenantId()).contains(parentId))) throw bad("目录不能移动到自身或子目录下"); }
        if (id == null && depth(parentId, user.tenantId()) >= 5) throw bad("案例目录最多五层");
        String name = required(body.get("directory_name"), "目录名称", 100); int sort = integer(body.get("sort_no"));
        Long sameName = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_case_directory WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND parent_id " + (parentId == null ? "IS NULL" : "=?") + " AND directory_name=? AND deleted=0" + (id == null ? "" : " AND id<>?"), Long.class, directoryArgs(user.tenantId(), domain, projectId, systemId, parentId, name, id));
        if (sameName != null && sameName > 0) throw conflict("同级案例目录名称已存在");
        long saved = id == null ? next() : id;
        if (id == null) jdbc.update("INSERT INTO tm_test_case_directory(id,tenant_id,test_domain,project_id,physical_subsystem_id,parent_id,directory_name,sort_no,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)", saved, user.tenantId(), domain, projectId, systemId, parentId, name, sort, user.id(), user.id());
        else { directory(id, domain, projectId, user.tenantId()); jdbc.update("UPDATE tm_test_case_directory SET physical_subsystem_id=?,parent_id=?,directory_name=?,sort_no=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", systemId, parentId, name, sort, user.id(), id, user.tenantId()); }
        audit(domain, projectId, "CASE_DIRECTORY", saved, id == null ? "CREATE" : "UPDATE", Map.of("directory_name", name, "parent_id", parentId == null ? 0 : parentId), user);
        return jdbc.queryForMap("SELECT id,physical_subsystem_id,parent_id,directory_name,sort_no,updated_at FROM tm_test_case_directory WHERE id=? AND tenant_id=?", saved, user.tenantId());
    }

    @Transactional
    public void deleteDirectory(String domain, long projectId, long id, Long targetDirectoryId, AuthUser user) {
        Map<String, Object> current = directory(id, domain, projectId, user.tenantId());
        Long target = targetDirectoryId == null ? positiveOrNull(current.get("parent_id")) : targetDirectoryId;
        if (target != null) { Map<String, Object> targetRow = directory(target, domain, projectId, user.tenantId()); if (number(targetRow.get("physical_subsystem_id")) != number(current.get("physical_subsystem_id"))) throw bad("移交目录必须属于同一参测系统"); if (target == id) throw bad("移交目录不能是当前目录"); }
        Long children = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_case_directory WHERE tenant_id=? AND parent_id=? AND deleted=0", Long.class, user.tenantId(), id);
        if (children != null && children > 0) throw conflict("目录仍有子目录，请先调整子目录");
        jdbc.update("UPDATE tm_test_case SET directory_id=?,updated_by=? WHERE tenant_id=? AND directory_id=? AND deleted=0", target, user.id(), user.tenantId(), id);
        jdbc.update("UPDATE tm_test_case_directory SET deleted=1,deleted_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", user.id(), id, user.tenantId());
        audit(domain, projectId, "CASE_DIRECTORY", id, "DELETE", Map.of("target_directory_id", target == null ? 0 : target), user);
    }

    @Transactional
    public Map<String, Object> save(String domain, long projectId, Long id, Map<String, Object> body, AuthUser user) {
        project(domain, projectId, user);
        long scopeId = positive(body.get("scope_id"), "所属范围序号"); Map<String, Object> scope = scopeRecord(scopeId, domain, projectId, user.tenantId());
        Map<String, Object> old = id == null ? null : caseRow(id, domain, projectId, user.tenantId());
        if (old != null && number(old.get("scope_id")) != scopeId && !Boolean.TRUE.equals(body.get("confirm_scope_sync"))) return Map.of("id", id, "confirmation_required", true, "affected_case_count", 1, "old_scope_code", scopeRecord(number(old.get("scope_id")), domain, projectId, user.tenantId()).get("scope_code"), "scope_code", scope.get("scope_code"));
        long systemId = number(scope.get("physical_subsystem_id"));
        long directoryId = positive(body.get("directory_id"), "所属目录"); Map<String, Object> directory = directory(directoryId, domain, projectId, user.tenantId()); if (number(directory.get("physical_subsystem_id")) != systemId) throw bad("所属目录必须属于所属范围所在系统");
        String name = required(body.get("case_name"), "案例名称", 200); Map<String, Object> values = caseValues(domain, projectId, body, user, false);
        long saved = id == null ? next() : id; int serial = old == null ? nextSerial(scopeId, user.tenantId()) : serialForScope(scopeId, (int) number(old.get("case_serial_no")), saved, user.tenantId()); String code = caseCode(String.valueOf(scope.get("scope_code")), serial);
        if (old == null) jdbc.update("INSERT INTO tm_test_case(id,tenant_id,test_domain,project_id,physical_subsystem_id,scope_id,directory_id,case_code,case_serial_no,case_name,case_type,test_level,priority,accounting_result,accounting_confirmed,accounting_confirmed_by,accounting_confirmed_at,precondition_html,steps_html,expected_result_html,remark,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", saved, user.tenantId(), domain, projectId, systemId, scopeId, directoryId, code, serial, name, values.get("case_type"), values.get("case_nature"), values.get("priority"), values.get("accounting_result"), values.get("accounting_confirmed"), values.get("accounting_confirmed_by"), values.get("accounting_confirmed_at"), values.get("precondition_html"), values.get("steps_html"), values.get("expected_result_html"), values.get("remark"), user.id(), user.id());
        else jdbc.update("UPDATE tm_test_case SET physical_subsystem_id=?,scope_id=?,directory_id=?,case_code=?,case_serial_no=?,case_name=?,case_type=?,test_level=?,priority=?,accounting_result=?,accounting_confirmed=?,accounting_confirmed_by=?,accounting_confirmed_at=?,precondition_html=?,steps_html=?,expected_result_html=?,remark=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", systemId, scopeId, directoryId, code, serial, name, values.get("case_type"), values.get("case_nature"), values.get("priority"), values.get("accounting_result"), values.get("accounting_confirmed"), values.get("accounting_confirmed_by"), values.get("accounting_confirmed_at"), values.get("precondition_html"), values.get("steps_html"), values.get("expected_result_html"), values.get("remark"), user.id(), saved, user.tenantId());
        syncAttachments(saved, body.get("attachment_ids"), user, projectId);
        audit(domain, projectId, "CASE", saved, old == null ? "CREATE" : "UPDATE", Map.of("old_case_code", old == null ? "" : old.get("case_code"), "case_code", code, "scope_id", scopeId), user);
        return detail(domain, projectId, saved, user);
    }

    @Transactional
    public Map<String, Object> move(String domain, long projectId, List<Long> ids, long targetDirectoryId, AuthUser user) {
        project(domain, projectId, user); if (ids.isEmpty()) throw bad("请选择案例"); Map<String, Object> target = directory(targetDirectoryId, domain, projectId, user.tenantId()); int moved = 0;
        for (long id : ids) { Map<String, Object> row = caseRow(id, domain, projectId, user.tenantId()); if (number(row.get("physical_subsystem_id")) != number(target.get("physical_subsystem_id"))) throw bad("目标目录必须与案例所属系统一致"); jdbc.update("UPDATE tm_test_case SET directory_id=?,updated_by=? WHERE id=? AND tenant_id=?", targetDirectoryId, user.id(), id, user.tenantId()); audit(domain, projectId, "CASE", id, "MOVE", Map.of("target_directory_id", targetDirectoryId), user); moved++; }
        return Map.of("moved", moved, "target_directory_id", targetDirectoryId);
    }

    @Transactional
    public Map<String, Object> invalidate(String domain, long projectId, long id, boolean invalidated, String reason, AuthUser user) {
        caseRow(id, domain, projectId, user.tenantId()); String cleanReason = text(reason, 500, "无效原因"); if (invalidated && cleanReason == null) throw bad("请填写无效原因");
        jdbc.update("UPDATE tm_test_case SET invalidated=?,invalidated_by=?,invalidated_at=?,invalid_reason=?,updated_by=? WHERE id=? AND tenant_id=?", invalidated ? 1 : 0, invalidated ? user.id() : null, invalidated ? new Timestamp(System.currentTimeMillis()) : null, invalidated ? cleanReason : null, user.id(), id, user.tenantId());
        audit(domain, projectId, "CASE", id, invalidated ? "INVALIDATE" : "REVOKE_INVALID", Map.of("reason", cleanReason == null ? "" : cleanReason), user);
        return Map.of("id", id, "invalidated", invalidated, "status", invalidated ? "INVALID" : "UNEXECUTED");
    }

    @Transactional
    public Map<String, Object> delete(String domain, long projectId, long id, AuthUser user) {
        caseRow(id, domain, projectId, user.tenantId());
        if (executionReferenceCount(id, user.tenantId()) > 0) throw conflict("该案例被执行记录引用，只能置无效");
        for (Map<String, Object> file : jdbc.queryForList("SELECT attachment_id FROM tm_test_case_attachment WHERE tenant_id=? AND case_id=? AND deleted=0", user.tenantId(), id)) attachments.deleteBound(number(file.get("attachment_id")), BUSINESS_TYPE, String.valueOf(id), user);
        jdbc.update("UPDATE tm_test_case_attachment SET deleted=1,deleted_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND case_id=?", user.tenantId(), id);
        jdbc.update("UPDATE tm_test_case SET deleted=1,deleted_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND tenant_id=?", user.id(), id, user.tenantId());
        audit(domain, projectId, "CASE", id, "DELETE", Map.of(), user); return Map.of("id", id, "deleted", true);
    }

    public Map<String, Object> batchPreview(String domain, long projectId, List<Long> ids, Map<String, Object> body, AuthUser user) {
        project(domain, projectId, user); List<Map<String, Object>> cases = selectedCases(ids, domain, projectId, user.tenantId()); String field = required(body.get("field"), "批量调整字段", 64); validateBatchField(field, body, domain, projectId, user);
        List<Map<String, Object>> preview = new ArrayList<>();
        for (Map<String, Object> row : cases) if (preview.size() < 100) preview.add(Map.of("id", row.get("id"), "case_code", row.get("case_code"), "case_name", row.get("case_name"), "old_value", displayValue(row, field), "new_value", batchValue(field, body)));
        return Map.of("selected_count", cases.size(), "affected_case_count", cases.size(), "field", field, "requires_scope_sync", "scope_id".equals(field), "records", preview);
    }

    /** 批量操作同时支持表格选择和粘贴案例编号；所有编号仍按当前项目、测试大类和租户收口。 */
    public List<Long> resolveCaseCodes(String domain, long projectId, Collection<String> codes, AuthUser user) {
        project(domain, projectId, user); if (codes == null || codes.isEmpty()) return List.of();
        List<Long> result = new ArrayList<>(); Set<Long> unique = new LinkedHashSet<>();
        for (String raw : codes) for (String part : String.valueOf(raw).split("[,，\\s]+")) { String code = text(part, 160, "案例编号"); if (code == null) continue; List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM tm_test_case WHERE tenant_id=? AND test_domain=? AND project_id=? AND case_code=? AND deleted=0", user.tenantId(), domain, projectId, code); if (rows.isEmpty()) throw bad("案例编号不存在或不属于当前项目：" + code); unique.add(number(rows.get(0).get("id"))); }
        result.addAll(unique); return result;
    }

    @Transactional
    public Map<String, Object> batchUpdate(String domain, long projectId, List<Long> ids, Map<String, Object> body, AuthUser user) {
        Map<String, Object> preview = batchPreview(domain, projectId, ids, body, user); if (!Boolean.TRUE.equals(body.get("confirmed"))) return new LinkedHashMap<>(preview);
        String field = String.valueOf(preview.get("field")); if ("scope_id".equals(field) && !Boolean.TRUE.equals(body.get("confirm_scope_sync"))) { Map<String, Object> result = new LinkedHashMap<>(preview); result.put("confirmation_required", true); return result; }
        int updated = 0; for (Map<String, Object> row : selectedCases(ids, domain, projectId, user.tenantId())) { Map<String, Object> input = new LinkedHashMap<>(row); input.put("case_nature", row.get("test_level")); input.put("attachment_ids", attachmentsOf(number(row.get("id")), user.tenantId())); input.put(field, body.get("value")); input.put("confirm_scope_sync", true); save(domain, projectId, number(row.get("id")), input, user); updated++; }
        audit(domain, projectId, "CASE", 0, "BATCH_UPDATE", Map.of("field", field, "count", updated), user); return Map.of("updated", updated, "field", field);
    }

    public Map<String, Object> previewImport(String domain, long projectId, List<Map<String, Object>> rows, AuthUser user) { return validateImport(domain, projectId, rows, user); }

    @Transactional
    public Map<String, Object> importCases(String domain, long projectId, List<Map<String, Object>> rows, String duplicateAction, AuthUser user) {
        Map<String, Object> validation = validateImport(domain, projectId, rows, user); if (!Boolean.TRUE.equals(validation.get("success"))) return validation;
        @SuppressWarnings("unchecked") List<Map<String, Object>> prepared = (List<Map<String, Object>>) validation.get("rows"); int created = 0; int updated = 0; int skipped = 0;
        for (Map<String, Object> row : prepared) {
            String existing = String.valueOf(row.getOrDefault("existing_id", ""));
            if (!existing.isBlank() && !"null".equals(existing)) { if (!"OVERWRITE".equalsIgnoreCase(duplicateAction)) { skipped++; continue; } updateImported(domain, projectId, Long.parseLong(existing), row, user); updated++; }
            else { insertImported(domain, projectId, row, user); created++; }
        }
        audit(domain, projectId, "CASE", 0, "IMPORT", Map.of("created", created, "updated", updated, "skipped", skipped), user);
        Map<String, Object> result = new LinkedHashMap<>(validation); result.put("written", created + updated); result.put("created", created); result.put("updated", updated); result.put("skipped", skipped); result.remove("rows"); return result;
    }

    public List<Map<String, Object>> exportRows(String domain, long projectId, Long systemId, Long directoryId, Long scopeId, String keyword, Collection<String> caseTypes, Collection<String> caseNatures, Collection<String> priorities, Collection<String> statuses, Collection<String> accountingResults, Long designerId, String executionReference, AuthUser user) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : list(domain, projectId, systemId, directoryId, scopeId, keyword, caseTypes, caseNatures, priorities, statuses, accountingResults, designerId, executionReference, "case_code", "ascending", new PageQuery(1, 2000), user).records()) {
            Map<String, Object> copy = new LinkedHashMap<>(row); copy.put("directory_path", directoryPath(positiveOrNull(copy.get("directory_id")), user.tenantId())); result.add(copy);
        }
        return result;
    }

    private Map<String, Object> validateImport(String domain, long projectId, List<Map<String, Object>> rows, AuthUser user) {
        project(domain, projectId, user); if (rows == null || rows.isEmpty()) throw bad("导入文件没有有效数据行");
        List<Map<String, Object>> errors = new ArrayList<>(); List<Map<String, Object>> prepared = new ArrayList<>(); Set<String> codes = new LinkedHashSet<>(); Set<String> directories = new LinkedHashSet<>(); Map<Long, Integer> serials = new LinkedHashMap<>(); int duplicates = 0;
        for (Map<String, Object> source : rows) {
            Map<String, Object> row = new LinkedHashMap<>(source);
            try {
                Map<String, Object> scope = scopeByCode(required(row.get("scope_code"), "所属范围序号", 128), domain, projectId, user.tenantId()); long scopeId = number(scope.get("id")); String scopeCode = String.valueOf(scope.get("scope_code"));
                String requestedCode = text(row.get("case_code"), 160, "案例编号"); int serial;
                if (requestedCode == null) { serial = serials.compute(scopeId, (key, value) -> value == null ? nextSerial(scopeId, user.tenantId()) : value + 1); requestedCode = caseCode(scopeCode, serial); }
                else { serial = importSerial(requestedCode, scopeCode); }
                if (!codes.add(requestedCode)) throw bad("文件内案例编号重复：" + requestedCode);
                List<Map<String, Object>> existing = jdbc.queryForList("SELECT id,deleted FROM tm_test_case WHERE tenant_id=? AND test_domain=? AND project_id=? AND case_code=?", user.tenantId(), domain, projectId, requestedCode);
                if (!existing.isEmpty() && number(existing.get(0).get("deleted")) == 1) throw bad("案例编号已在回收站占用，不能复用：" + requestedCode);
                if (!existing.isEmpty()) { row.put("existing_id", existing.get(0).get("id")); duplicates++; }
                row.put("scope_id", scopeId); row.put("physical_subsystem_id", scope.get("physical_subsystem_id")); row.put("scope_code", scopeCode); row.put("case_code", requestedCode); row.put("case_serial_no", serial);
                row.put("case_name", required(row.get("case_name"), "案例名称", 200)); row.put("case_type", dictionary(domain, projectId, "case_type", row.get("case_type"), "案例类型", user)); row.put("case_nature", dictionary(domain, projectId, "case_nature", row.get("case_nature"), "案例性质", user)); row.put("priority", dictionary(domain, projectId, "case_priority", row.get("priority"), "案例优先级", user)); row.put("accounting_result", accounting(row.get("accounting_result")));
                String path = required(row.get("directory_path"), "所属目录", 500); if (pathDepth(path) > 5) throw bad("所属目录最多五层"); row.put("directory_path", path); directories.add(scopeId + ":" + path); prepared.add(row);
            } catch (BusinessException exception) { errors.add(Map.of("row_number", row.getOrDefault("row_number", 0), "message", exception.getMessage())); }
        }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("total", rows.size()); result.put("valid", prepared.size()); result.put("failed", errors.size()); result.put("duplicate", duplicates); result.put("directories", directories.size()); result.put("success", errors.isEmpty()); result.put("errors", errors); result.put("rows", prepared); result.put("generated_codes", prepared.stream().filter(row -> text(row.get("case_code"), 160, "案例编号") != null).map(row -> Map.of("case_code", row.get("case_code"), "case_name", row.get("case_name"), "scope_code", row.get("scope_code"))).toList()); return result;
    }

    private void insertImported(String domain, long projectId, Map<String, Object> row, AuthUser user) {
        long directoryId = ensureDirectory(domain, projectId, number(row.get("physical_subsystem_id")), String.valueOf(row.get("directory_path")), user); long id = next();
        jdbc.update("INSERT INTO tm_test_case(id,tenant_id,test_domain,project_id,physical_subsystem_id,scope_id,directory_id,case_code,case_serial_no,case_name,case_type,test_level,priority,accounting_result,accounting_confirmed,precondition_html,steps_html,expected_result_html,remark,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id, user.tenantId(), domain, projectId, row.get("physical_subsystem_id"), row.get("scope_id"), directoryId, row.get("case_code"), row.get("case_serial_no"), row.get("case_name"), row.get("case_type"), row.get("case_nature"), row.get("priority"), row.get("accounting_result"), 0, plainHtml(row.get("precondition_html")), plainHtml(row.get("steps_html")), plainHtml(row.get("expected_result_html")), text(row.get("remark"), 500, "备注"), user.id(), user.id());
        audit(domain, projectId, "CASE", id, "IMPORT_CREATE", Map.of("case_code", row.get("case_code")), user);
    }

    private void updateImported(String domain, long projectId, long id, Map<String, Object> row, AuthUser user) {
        Map<String, Object> old = caseRow(id, domain, projectId, user.tenantId()); long directoryId = ensureDirectory(domain, projectId, number(row.get("physical_subsystem_id")), String.valueOf(row.get("directory_path")), user);
        jdbc.update("UPDATE tm_test_case SET physical_subsystem_id=?,scope_id=?,directory_id=?,case_code=?,case_serial_no=?,case_name=?,case_type=?,test_level=?,priority=?,accounting_result=?,precondition_html=?,steps_html=?,expected_result_html=?,remark=?,updated_by=? WHERE id=? AND tenant_id=?", row.get("physical_subsystem_id"), row.get("scope_id"), directoryId, row.get("case_code"), row.get("case_serial_no"), nonBlank(row.get("case_name"), old.get("case_name")), nonBlank(row.get("case_type"), old.get("case_type")), nonBlank(row.get("case_nature"), old.get("test_level")), nonBlank(row.get("priority"), old.get("priority")), nonBlank(row.get("accounting_result"), old.get("accounting_result")), nonBlank(plainHtml(row.get("precondition_html")), old.get("precondition_html")), nonBlank(plainHtml(row.get("steps_html")), old.get("steps_html")), nonBlank(plainHtml(row.get("expected_result_html")), old.get("expected_result_html")), nonBlank(text(row.get("remark"), 500, "备注"), old.get("remark")), user.id(), id, user.tenantId());
        audit(domain, projectId, "CASE", id, "IMPORT_OVERWRITE", Map.of("old_case_code", old.get("case_code"), "case_code", row.get("case_code")), user);
    }

    private Map<String, Object> caseValues(String domain, long projectId, Map<String, Object> body, AuthUser user, boolean allowEmptyContent) {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("case_type", dictionary(domain, projectId, "case_type", body.get("case_type"), "案例类型", user)); values.put("case_nature", dictionary(domain, projectId, "case_nature", body.get("case_nature"), "案例性质", user)); values.put("priority", dictionary(domain, projectId, "case_priority", body.get("priority"), "案例优先级", user));
        String accounting = accounting(body.get("accounting_result")); Long confirmer = positiveOrNull(body.get("accounting_confirmed_by")); if (confirmer != null) userExists(confirmer, user.tenantId()); values.put("accounting_result", accounting); values.put("accounting_confirmed_by", confirmer); values.put("accounting_confirmed", confirmer == null ? 0 : 1); values.put("accounting_confirmed_at", confirmer == null ? null : new Timestamp(System.currentTimeMillis()));
        values.put("precondition_html", html(body.get("precondition_html"), "前置条件", false)); values.put("steps_html", html(body.get("steps_html"), "操作步骤", !allowEmptyContent)); values.put("expected_result_html", html(body.get("expected_result_html"), "预期结果", !allowEmptyContent)); values.put("remark", text(body.get("remark"), 500, "备注")); return values;
    }

    private void validateBatchField(String field, Map<String, Object> body, String domain, long projectId, AuthUser user) {
        if (Set.of("case_type", "case_nature", "priority", "accounting_result", "accounting_confirmed_by", "directory_id", "scope_id", "case_name", "precondition_html", "steps_html", "expected_result_html", "remark").contains(field)) {
            Map<String, Object> probe = new LinkedHashMap<>(); probe.put(field, body.get("value"));
            if ("case_type".equals(field)) dictionary(domain, projectId, "case_type", body.get("value"), "案例类型", user);
            if ("case_nature".equals(field)) dictionary(domain, projectId, "case_nature", body.get("value"), "案例性质", user);
            if ("priority".equals(field)) dictionary(domain, projectId, "case_priority", body.get("value"), "案例优先级", user);
            if ("accounting_result".equals(field)) accounting(body.get("value"));
            if ("accounting_confirmed_by".equals(field) && positiveOrNull(body.get("value")) != null) userExists(positiveOrNull(body.get("value")), user.tenantId());
            if ("directory_id".equals(field)) directory(positive(body.get("value"), "所属目录"), domain, projectId, user.tenantId());
            if ("scope_id".equals(field)) scopeRecord(positive(body.get("value"), "所属范围序号"), domain, projectId, user.tenantId());
            return;
        }
        throw bad("不支持批量调整字段：" + field);
    }

    private List<Map<String, Object>> selectedCases(List<Long> ids, String domain, long projectId, long tenantId) { if (ids == null || ids.isEmpty()) throw bad("请选择案例"); List<Map<String, Object>> rows = new ArrayList<>(); for (Long id : new LinkedHashSet<>(ids)) rows.add(caseRow(id, domain, projectId, tenantId)); return rows; }
    private String displayValue(Map<String, Object> row, String field) { return String.valueOf(row.getOrDefault("case_nature".equals(field) ? "test_level" : field, "")); }
    private Object batchValue(String field, Map<String, Object> body) { return body.get("value"); }
    private void syncAttachments(long caseId, Object raw, AuthUser user, long projectId) { Set<Long> wanted = ids(raw); for (Map<String, Object> old : jdbc.queryForList("SELECT attachment_id FROM tm_test_case_attachment WHERE tenant_id=? AND case_id=? AND deleted=0", user.tenantId(), caseId)) { long id = number(old.get("attachment_id")); if (!wanted.contains(id)) { attachments.deleteBound(id, BUSINESS_TYPE, String.valueOf(caseId), user); jdbc.update("UPDATE tm_test_case_attachment SET deleted=1,deleted_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND case_id=? AND attachment_id=?", user.tenantId(), caseId, id); } } int sort = 0; for (long id : wanted) { AttachmentItem item = attachments.get(id, user); if (item.fileSize() > 50L * 1024 * 1024) throw bad("单个案例附件不能超过50MB"); Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_case_attachment WHERE tenant_id=? AND case_id=? AND attachment_id=? AND deleted=0", Long.class, user.tenantId(), caseId, id); if (exists == null || exists == 0) { attachments.bind(new AttachmentBindingCommand(id, BUSINESS_TYPE, String.valueOf(caseId), String.valueOf(projectId)), user); jdbc.update("INSERT INTO tm_test_case_attachment(id,tenant_id,case_id,attachment_id,sort_no,created_by) VALUES(?,?,?,?,?,?)", next(), user.tenantId(), caseId, id, sort, user.id()); } sort++; } }
    private List<Long> attachmentsOf(long id, long tenantId) { return jdbc.query("SELECT attachment_id FROM tm_test_case_attachment WHERE tenant_id=? AND case_id=? AND deleted=0", (result, index) -> result.getLong(1), tenantId, id); }
    private Set<Long> ids(Object raw) { Set<Long> result = new LinkedHashSet<>(); if (raw instanceof Iterable<?> values) for (Object value : values) { Long id = positiveOrNull(value); if (id != null) result.add(id); } return result; }
    private void project(String domain, long projectId, AuthUser user) { if (!DOMAINS.contains(domain)) throw bad("测试大类无效"); if (projectId <= 0) throw bad("请选择项目"); Long count = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id=? AND tenant_id=? AND deleted=0", Long.class, projectId, user.tenantId()); if (count == null || count == 0) throw bad("项目不存在或不属于当前租户"); }
    private void enabledSystem(String domain, long projectId, long systemId, long tenantId) { Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_participating_system WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND enabled=1 AND deleted=0", Long.class, tenantId, domain, projectId, systemId); if (count == null || count == 0) throw bad("请选择已启用的参测系统"); }
    private Map<String, Object> scopeRecord(long id, String domain, long projectId, long tenantId) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,scope_code,physical_subsystem_id FROM tm_test_scope WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0", id, tenantId, domain, projectId); if (rows.isEmpty()) throw bad("所属范围序号不存在或不属于当前项目"); return rows.get(0); }
    private Map<String, Object> scopeByCode(String code, String domain, long projectId, long tenantId) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,scope_code,physical_subsystem_id FROM tm_test_scope WHERE scope_code=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0", code, tenantId, domain, projectId); if (rows.isEmpty()) throw bad("所属范围序号不存在：" + code); return rows.get(0); }
    private Map<String, Object> directory(long id, String domain, long projectId, long tenantId) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,physical_subsystem_id,parent_id,directory_name FROM tm_test_case_directory WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0", id, tenantId, domain, projectId); if (rows.isEmpty()) throw bad("案例目录不存在或不属于当前项目"); return rows.get(0); }
    private Map<String, Object> caseRow(long id, String domain, long projectId, long tenantId) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM tm_test_case WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0", id, tenantId, domain, projectId); if (rows.isEmpty()) throw bad("测试案例不存在或不属于当前项目"); return rows.get(0); }
    private String dictionary(String domain, long projectId, String dictionaryCode, Object value, String label, AuthUser user) { String target = required(value, label, 64); List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.option_code FROM tm_test_dictionary d JOIN tm_test_dictionary_option o ON o.dictionary_id=d.id AND o.tenant_id=d.tenant_id AND o.deleted=0 AND o.enabled=1 WHERE d.tenant_id=? AND d.test_domain=? AND d.project_id=? AND d.dictionary_code=? AND d.deleted=0 AND d.enabled=1 AND (o.option_code=? OR o.option_name=?)", user.tenantId(), domain, projectId, dictionaryCode, target, target); if (rows.isEmpty()) throw bad(label + "字典项无效：" + target); return String.valueOf(rows.get(0).get("option_code")); }
    private String accounting(Object value) { String result = text(value, 64, "核算核对结果"); if (result == null) return "UNEXECUTED"; result = Map.of("未执行", "UNEXECUTED", "待核对", "PENDING_REVIEW", "一致", "SUCCESS", "不一致", "FAILED", "不适用", "INVALID").getOrDefault(result, result.toUpperCase(Locale.ROOT)); if (!ACCOUNTING_STATUS.contains(result)) throw bad("核算核对结果无效：" + result); return result; }
    private void userExists(long id, long tenantId) { Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND tenant_id=? AND deleted=0 AND status=1", Long.class, id, tenantId); if (count == null || count == 0) throw bad("核算结果确认人不存在或不可用"); }
    private int depth(Long parentId, long tenantId) { int result = 0; Long current = parentId; while (current != null) { result++; if (result > 5) return result; List<Map<String, Object>> rows = jdbc.queryForList("SELECT parent_id FROM tm_test_case_directory WHERE id=? AND tenant_id=? AND deleted=0", current, tenantId); current = rows.isEmpty() ? null : positiveOrNull(rows.get(0).get("parent_id")); } return result; }
    private List<Long> descendantDirectories(long root, long tenantId) { List<Long> result = new ArrayList<>(List.of(root)); for (int index = 0; index < result.size(); index++) result.addAll(jdbc.queryForList("SELECT id FROM tm_test_case_directory WHERE tenant_id=? AND parent_id=? AND deleted=0", Long.class, tenantId, result.get(index))); return result; }
    private Object[] directoryArgs(long tenantId, String domain, long projectId, long systemId, Long parentId, String name, Long id) { List<Object> args = new ArrayList<>(List.of(tenantId, domain, projectId, systemId)); if (parentId != null) args.add(parentId); args.add(name); if (id != null) args.add(id); return args.toArray(); }
    private int nextSerial(long scopeId, long tenantId) { Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(case_serial_no),0)+1 FROM tm_test_case WHERE tenant_id=? AND scope_id=?", Integer.class, tenantId, scopeId); return next == null ? 1 : next; }
    private int serialForScope(long scopeId, int current, long caseId, long tenantId) { Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_case WHERE tenant_id=? AND scope_id=? AND case_serial_no=? AND id<>?", Long.class, tenantId, scopeId, current, caseId); return exists != null && exists > 0 ? nextSerial(scopeId, tenantId) : current; }
    private String caseCode(String scopeCode, int serial) { return scopeCode + "-" + String.format(Locale.ROOT, "%04d", serial); }
    private int importSerial(String code, String scopeCode) { String prefix = scopeCode + "-"; if (!code.startsWith(prefix) || !code.substring(prefix.length()).matches("[0-9]{4,}")) throw bad("案例编号前缀必须与所属范围序号一致：" + scopeCode); return Integer.parseInt(code.substring(prefix.length())); }
    private Long ensureDirectory(String domain, long projectId, long systemId, String path, AuthUser user) { Long parent = null; for (String raw : path.replace('/', '\\').split("\\\\")) { String name = raw.trim(); if (name.isEmpty()) continue; List<Map<String, Object>> found = jdbc.queryForList("SELECT id FROM tm_test_case_directory WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND parent_id " + (parent == null ? "IS NULL" : "=?") + " AND directory_name=? AND deleted=0", directoryArgs(user.tenantId(), domain, projectId, systemId, parent, name, null)); if (found.isEmpty()) { if (depth(parent, user.tenantId()) >= 5) throw bad("所属目录最多五层：" + path); Map<String, Object> input = new LinkedHashMap<>(); input.put("physical_subsystem_id", systemId); input.put("parent_id", parent); input.put("directory_name", name); input.put("sort_no", 0); parent = number(saveDirectory(domain, projectId, null, input, user).get("id")); } else parent = number(found.get(0).get("id")); } if (parent == null) throw bad("所属目录不能为空"); return parent; }
    private String directoryPath(Long id, long tenantId) { if (id == null) return ""; List<String> names = new ArrayList<>(); Long current = id; while (current != null) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT parent_id,directory_name FROM tm_test_case_directory WHERE id=? AND tenant_id=? AND deleted=0", current, tenantId); if (rows.isEmpty()) break; names.add(String.valueOf(rows.get(0).get("directory_name"))); current = positiveOrNull(rows.get(0).get("parent_id")); } java.util.Collections.reverse(names); return String.join("\\", names); }
    private int pathDepth(String path) { int count = 0; for (String item : path.replace('/', '\\').split("\\\\")) if (!item.trim().isEmpty()) count++; return count; }
    private String caseStatusExpression() { return "CASE WHEN c.invalidated=1 THEN 'INVALID' ELSE COALESCE((SELECT e.execution_status FROM tm_test_execution e WHERE e.tenant_id=c.tenant_id AND e.case_id=c.id AND e.deleted=0 ORDER BY COALESCE(e.executed_at,e.created_at) DESC,e.updated_at DESC,e.id DESC LIMIT 1),'UNEXECUTED') END"; }
    private int executionReferenceCount(long caseId, long tenantId) { return 0; }
    private String order(String field, String direction) { Map<String, String> fields = Map.of("case_code", "c.case_code", "case_name", "c.case_name", "scope_code", "s.scope_code", "case_type", "c.case_type", "case_nature", "c.test_level", "priority", "c.priority", "accounting_result", "c.accounting_result", "created_at", "c.created_at", "updated_at", "c.updated_at"); return fields.getOrDefault(field == null ? "" : field, "c.updated_at") + ("ascending".equalsIgnoreCase(direction) || "asc".equalsIgnoreCase(direction) ? " ASC" : " DESC") + ",c.id DESC"; }
    private void in(StringBuilder where, List<Object> args, String column, Collection<String> values, Set<String> allowed, String label) { List<String> clean = cleanValues(values, allowed, label); if (!clean.isEmpty()) { where.append(" AND ").append(column).append(" IN (").append(placeholders(clean.size())).append(")"); args.addAll(clean); } }
    private List<String> cleanValues(Collection<String> raw, Set<String> allowed, String label) { if (raw == null) return List.of(); List<String> values = new ArrayList<>(); for (String value : raw) { String clean = text(value, 64, label); if (clean != null) { clean = clean.toUpperCase(Locale.ROOT); if (allowed != null && !allowed.contains(clean)) throw bad(label + "无效：" + clean); values.add(clean); } } return values; }
    private String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    private String html(Object value, String field, boolean required) { String text = text(value, 200000, field); if (required && text == null) throw bad(field + "不能为空"); return text == null ? null : text.replace("<script", "&lt;script"); }
    private String plainHtml(Object value) { String text = text(value, 200000, "导入内容"); return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>"); }
    private Object nonBlank(Object value, Object fallback) { return value == null || String.valueOf(value).isBlank() ? fallback : value; }
    private void audit(String domain, long projectId, String type, long id, String action, Map<String, Object> detail, AuthUser user) { try { jdbc.update("INSERT INTO tm_test_scope_case_audit(id,tenant_id,test_domain,project_id,entity_type,entity_id,action_code,operator_id,detail_json) VALUES(?,?,?,?,?,?,?,?,?)", next(), user.tenantId(), domain, projectId, type, id, action, user.id(), objectMapper.writeValueAsString(detail)); } catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审计记录序列化失败"); } }
    private long next() { return IDS.incrementAndGet(); }
    private long positive(Object value, String field) { Long number = positiveOrNull(value); if (number == null || number <= 0) throw bad(field + "无效"); return number; }
    private Long positiveOrNull(Object value) { if (value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value))) return null; try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException exception) { throw bad("编号无效"); } }
    private long number(Object value) { return ((Number) value).longValue(); }
    private int integer(Object value) { if (value == null || String.valueOf(value).isBlank()) return 0; try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException exception) { throw bad("排序值无效"); } }
    private String required(Object value, String field, int max) { String result = text(value, max, field); if (result == null) throw bad(field + "不能为空"); return result; }
    private String text(Object value, int max, String field) { if (value == null) return null; String result = String.valueOf(value).trim(); if (result.isEmpty() || "null".equals(result)) return null; if (result.length() > max) throw bad(field + "不能超过" + max + "个字符"); return result; }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
}

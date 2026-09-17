/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/scope/TestScopeService.java
 * 说明：测试范围的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.scope;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** 测试范围领域：目录、范围、编号、回收与工作簿导入均受租户/大类/项目边界保护。 */
@Service
public class TestScopeService {
    private static final Set<String> DOMAINS = Set.of("application-assembly", "user-testing", "non-functional", "security");
    private static final Set<String> STATUS = Set.of("SUCCESS", "FAILED", "RUNNING", "INVALID", "UNEXECUTED");
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 1000);
    private final TestScopeRepository repository;
    private final ObjectMapper objectMapper;

    public TestScopeService(TestScopeRepository repository, ObjectMapper objectMapper) { this.repository = repository; this.objectMapper = objectMapper; }

    public Map<String, Object> tree(String domain, long projectId, AuthUser user) {
        scope(domain, projectId, user);
        Map<String, Object> p = context(domain, projectId, user);
        return Map.of("systems", repository.treeSystems(p), "directories", repository.treeDirectories(p));
    }

    public PageResult<Map<String, Object>> list(String domain, long projectId, Long systemId, Long directoryId, String keyword, Collection<String> statuses, Collection<String> functionTypes, Collection<String> changeStatuses, Collection<String> importances, Collection<String> accountingFlags, String coverage, boolean recycled, String sortBy, String sortOrder, PageQuery page, AuthUser user) {
        scope(domain, projectId, user);
        if (systemId != null) requireSystem(domain, projectId, systemId, user.tenantId());
        List<Long> directoryIds = null;
        if (directoryId != null) { directory(directoryId, domain, projectId, user.tenantId()); directoryIds = descendantDirectoryIds(directoryId, user.tenantId()); }
        Map<String, Object> p = context(domain, projectId, user);
        p.put("deleted", recycled ? 1 : 0); p.put("systemId", systemId); p.put("directoryIds", directoryIds);
        String cleanKeyword = text(keyword, 100, "关键词");
        p.put("keyword", cleanKeyword == null ? null : "%" + cleanKeyword + "%");
        p.put("statuses", validValues(statuses, STATUS, "状态")); p.put("functionTypes", validValues(functionTypes, null, "function_type"));
        p.put("changeStatuses", validValues(changeStatuses, null, "change_status")); p.put("importances", validValues(importances, null, "importance"));
        p.put("accountingFlags", validValues(accountingFlags, null, "accounting_flag")); p.put("coverage", coverage == null ? null : coverage.toUpperCase(Locale.ROOT));
        p.put("sortBy", sortBy); p.put("ascending", "ascending".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder));
        long total = repository.scopeCount(p);
        p.put("offset", (page.page() - 1) * page.size()); p.put("size", page.size());
        return new PageResult<>(repository.scopes(p), total, page.page(), page.size());
    }

    @Transactional
    public Map<String, Object> saveDirectory(String domain, long projectId, Long id, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user);
        long systemId = positive(body.get("physical_subsystem_id"), "参测系统"); requireSystem(domain, projectId, systemId, user.tenantId());
        Long parentId = positiveOrNull(body.get("parent_id"));
        if (parentId != null) { Map<String, Object> parent = directory(parentId, domain, projectId, user.tenantId()); if (number(parent.get("physical_subsystem_id")) != systemId) throw bad("父目录必须属于同一参测系统"); if (id != null && (parentId.equals(id) || descendantDirectoryIds(id, user.tenantId()).contains(parentId))) throw bad("目录不能移动到自身或子目录下"); }
        if (id == null && depth(parentId, user.tenantId()) >= 5) throw bad("目录最多五层");
        String name = required(body.get("directory_name"), "目录名称", 100); int sortNo = integer(body.get("sort_no"));
        Map<String, Object> p = context(domain, projectId, user); p.put("id", id); p.put("systemId", systemId); p.put("parentId", parentId); p.put("name", name); p.put("sortNo", sortNo);
        if (repository.directoryNameExists(p)) throw conflict("同级目录名称已存在");
        long saved = id == null ? next() : id; p.put("id", saved);
        if (id == null) repository.insertDirectory(p); else { directory(id, domain, projectId, user.tenantId()); repository.updateDirectory(p); }
        audit(domain, projectId, "SCOPE_DIRECTORY", saved, id == null ? "CREATE" : "UPDATE", Map.of("directory_name", name, "parent_id", parentId == null ? 0 : parentId), user);
        return repository.directoryResult(params("id", saved, "tenantId", user.tenantId()));
    }

    @Transactional
    public void deleteDirectory(String domain, long projectId, long id, Long targetDirectoryId, AuthUser user) {
        Map<String, Object> row = directory(id, domain, projectId, user.tenantId()); Long target = targetDirectoryId == null ? positiveOrNull(row.get("parent_id")) : targetDirectoryId;
        if (target != null) { Map<String, Object> targetRow = directory(target, domain, projectId, user.tenantId()); if (number(targetRow.get("physical_subsystem_id")) != number(row.get("physical_subsystem_id"))) throw bad("移交目录必须属于同一参测系统"); if (target == id) throw bad("移交目录不能是当前目录"); }
        if (repository.hasChildDirectory(params("tenantId", user.tenantId(), "id", id))) throw conflict("目录仍有子目录，请先调整子目录");
        repository.reassignScopeDirectory(params("targetDirectoryId", target, "userId", user.id(), "tenantId", user.tenantId(), "directoryId", id)); repository.deleteDirectory(params("userId", user.id(), "id", id, "tenantId", user.tenantId()));
        audit(domain, projectId, "SCOPE_DIRECTORY", id, "DELETE", Map.of("target_directory_id", target == null ? 0 : target), user);
    }

    public Map<String, Object> previewCodeChange(String domain, long projectId, long id, String requestedCode, AuthUser user) {
        Map<String, Object> old = scopeRow(id, domain, projectId, user.tenantId()); String code = scopeCode(required(requestedCode, "序号", 128), systemCode(number(old.get("physical_subsystem_id")), user.tenantId())); uniqueScopeCode(domain, projectId, code, id, user.tenantId());
        long cases = repository.caseCount(params("tenantId", user.tenantId(), "scopeId", id));
        return Map.of("scope_id", id, "old_scope_code", old.get("scope_code"), "scope_code", code, "affected_case_count", cases, "confirmation_required", !code.equals(String.valueOf(old.get("scope_code"))));
    }

    @Transactional
    public Map<String, Object> saveScope(String domain, long projectId, Long id, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user);
        long systemId = positive(body.get("physical_subsystem_id"), "参测系统"); requireSystem(domain, projectId, systemId, user.tenantId()); long directoryId = positive(body.get("directory_id"), "所属目录");
        Map<String, Object> directory = directory(directoryId, domain, projectId, user.tenantId()); if (number(directory.get("physical_subsystem_id")) != systemId) throw bad("所属目录必须属于同一所属系统");
        String scopeName = required(body.get("scope_name"), "功能/用例/批处理名称", 100); String requested = text(body.get("scope_code"), 128, "序号"); String scopeCode = requested == null ? nextScopeCode(domain, projectId, systemId, user.tenantId()) : scopeCode(requested, systemCode(systemId, user.tenantId())); uniqueScopeCode(domain, projectId, scopeCode, id, user.tenantId());
        Map<String, Object> values = scopeValues(domain, projectId, body, user); long saved = id == null ? next() : id;
        Map<String, Object> p = context(domain, projectId, user); p.put("id", saved); p.put("systemId", systemId); p.put("directoryId", directoryId); p.put("scopeCode", scopeCode); p.put("scopeName", scopeName); p.put("leafMenu", values.get("leaf_menu")); p.put("functionType", values.get("function_type")); p.put("changeStatus", values.get("change_status")); p.put("importance", values.get("importance")); p.put("accountingFlag", values.get("accounting_flag"));
        int affected = 0;
        if (id == null) repository.insertScope(p); else { Map<String, Object> old = scopeRow(id, domain, projectId, user.tenantId()); if (!scopeCode.equals(String.valueOf(old.get("scope_code"))) && !Boolean.TRUE.equals(body.get("confirm_code_sync"))) return previewCodeChange(domain, projectId, id, scopeCode, user); repository.updateScope(p); if (!scopeCode.equals(String.valueOf(old.get("scope_code")))) affected = repository.syncCaseCodes(params("scopeCode", scopeCode, "userId", user.id(), "tenantId", user.tenantId(), "scopeId", id)); }
        Map<String, Object> detail = new LinkedHashMap<>(); detail.put("scope_code", scopeCode); detail.put("affected_case_count", affected); audit(domain, projectId, "SCOPE", saved, id == null ? "CREATE" : "UPDATE", detail, user);
        Map<String, Object> result = repository.scopeResult(params("id", saved, "tenantId", user.tenantId())); if (affected > 0) { result = new LinkedHashMap<>(result); result.put("affected_case_count", affected); } return result;
    }

    @Transactional
    public Map<String, Object> invalidate(String domain, long projectId, long id, boolean invalidated, String reason, AuthUser user) {
        Map<String, Object> row = scopeRow(id, domain, projectId, user.tenantId()); String clean = text(reason, 500, "无效原因"); if (invalidated && clean == null) throw bad("请填写无效原因");
        repository.invalidateScope(params("invalidated", invalidated ? 1 : 0, "invalidatedBy", invalidated ? user.id() : null, "invalidatedAt", invalidated ? new Timestamp(System.currentTimeMillis()) : null, "reason", invalidated ? clean : null, "userId", user.id(), "id", id, "tenantId", user.tenantId()));
        audit(domain, projectId, "SCOPE", id, invalidated ? "INVALIDATE" : "REVOKE_INVALID", Map.of("scope_code", String.valueOf(row.get("scope_code")), "reason", clean == null ? "" : clean), user); return Map.of("id", id, "invalidated", invalidated);
    }

    @Transactional
    public Map<String, Object> deleteScope(String domain, long projectId, long id, AuthUser user) {
        Map<String, Object> row = scopeRow(id, domain, projectId, user.tenantId()); long cases = repository.caseCount(params("tenantId", user.tenantId(), "scopeId", id)); repository.deleteScope(params("userId", user.id(), "id", id, "tenantId", user.tenantId())); audit(domain, projectId, "SCOPE", id, "DELETE", Map.of("scope_code", row.get("scope_code"), "associated_case_count", cases), user); return Map.of("id", id, "associated_case_count", cases);
    }

    @Transactional
    public Map<String, Object> restoreScope(String domain, long projectId, long id, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user); Map<String, Object> row = repository.deletedScopeRow(params("id", id, "tenantId", user.tenantId(), "domain", domain, "projectId", projectId)); if (row == null) throw bad("回收站中不存在该测试范围");
        String old = String.valueOf(row.get("scope_code")); String requested = text(body.get("scope_code"), 128, "序号"); String target = requested == null ? old : scopeCode(requested, systemCode(number(row.get("physical_subsystem_id")), user.tenantId())); uniqueScopeCode(domain, projectId, target, id, user.tenantId()); repository.restoreScope(params("scopeCode", target, "userId", user.id(), "id", id, "tenantId", user.tenantId())); if (!target.equals(old)) repository.syncCaseCodes(params("scopeCode", target, "userId", user.id(), "tenantId", user.tenantId(), "scopeId", id)); audit(domain, projectId, "SCOPE", id, "RESTORE", Map.of("old_scope_code", old, "scope_code", target), user); return Map.of("id", id, "scope_code", target);
    }

    public Map<String, Object> previewImport(String domain, long projectId, List<Map<String, Object>> rows, AuthUser user) { return validateImport(domain, projectId, rows, user); }

    @Transactional
    public Map<String, Object> importScopes(String domain, long projectId, List<Map<String, Object>> rows, String duplicateAction, AuthUser user) {
        Map<String, Object> validation = validateImport(domain, projectId, rows, user); if (!Boolean.TRUE.equals(validation.get("success"))) return validation; @SuppressWarnings("unchecked") List<Map<String, Object>> prepared = (List<Map<String, Object>>) validation.get("rows"); int created = 0, updated = 0, skipped = 0;
        for (Map<String, Object> row : prepared) { String existing = String.valueOf(row.getOrDefault("existing_id", "")); if (!existing.isBlank() && !"null".equals(existing)) { if ("SKIP".equalsIgnoreCase(duplicateAction)) { skipped++; continue; } Map<String, Object> copy = new LinkedHashMap<>(row); copy.put("directory_id", ensureDirectory(domain, projectId, number(copy.get("physical_subsystem_id")), String.valueOf(copy.get("directory_path")), user)); copy.put("confirm_code_sync", true); saveScope(domain, projectId, Long.parseLong(existing), copy, user); updated++; } else { Map<String, Object> copy = new LinkedHashMap<>(row); copy.put("directory_id", ensureDirectory(domain, projectId, number(copy.get("physical_subsystem_id")), String.valueOf(copy.get("directory_path")), user)); saveScope(domain, projectId, null, copy, user); created++; } }
        audit(domain, projectId, "SCOPE", 0, "IMPORT", Map.of("created", created, "updated", updated, "skipped", skipped), user); Map<String, Object> result = new LinkedHashMap<>(validation); result.put("written", created + updated); result.put("created", created); result.put("updated", updated); result.put("skipped", skipped); result.remove("rows"); return result;
    }

    public List<Map<String, Object>> exportRows(String domain, long projectId, Long systemId, Long directoryId, String keyword, Collection<String> statuses, Collection<String> functionTypes, Collection<String> changeStatuses, Collection<String> importances, Collection<String> accountingFlags, String coverage, AuthUser user) {
        List<Map<String, Object>> result = new ArrayList<>(); for (Map<String, Object> row : list(domain, projectId, systemId, directoryId, keyword, statuses, functionTypes, changeStatuses, importances, accountingFlags, coverage, false, "scope_code", "ascending", new PageQuery(1, 2000), user).records()) { Map<String, Object> copy = new LinkedHashMap<>(row); Long id = positiveOrNull(copy.get("directory_id")); copy.put("directory_path", id == null ? "" : directoryPath(id, user.tenantId())); result.add(copy); } return result;
    }

    private Map<String, Object> validateImport(String domain, long projectId, List<Map<String, Object>> rows, AuthUser user) {
        scope(domain, projectId, user); if (rows == null || rows.isEmpty()) throw bad("导入文件没有有效数据行"); List<Map<String, Object>> errors = new ArrayList<>(), prepared = new ArrayList<>(); Set<String> fileCodes = new LinkedHashSet<>(), autoDirectories = new LinkedHashSet<>(); int duplicate = 0;
        for (Map<String, Object> source : rows) { Map<String, Object> row = new LinkedHashMap<>(source); try { String systemRef = required(row.get("physical_system_code"), "所属系统", 64); Long systemId = systemByReference(domain, projectId, systemRef, user.tenantId()); if (systemId == null) throw bad("所属系统不存在或未启用：" + systemRef); String scopeName = required(row.get("scope_name"), "功能/用例/批处理名称", 100); String scopeCode = scopeCode(required(row.get("scope_code"), "序号", 128), systemCode(systemId, user.tenantId())); if (!fileCodes.add(scopeCode)) throw bad("文件内序号重复：" + scopeCode); List<Map<String, Object>> exists = repository.activeScopeByCode(params("tenantId", user.tenantId(), "domain", domain, "projectId", projectId, "scopeCode", scopeCode)); if (!exists.isEmpty()) { row.put("existing_id", exists.get(0).get("id")); duplicate++; } row.put("scope_name", scopeName); row.put("scope_code", scopeCode); row.put("physical_subsystem_id", systemId); String path = required(row.get("directory_path"), "所属目录", 500); row.put("directory_path", path); autoDirectories.add(systemId + ":" + path); validateDictionary(domain, projectId, "func_type", row.get("function_type"), "功能类型", user); validateDictionary(domain, projectId, "change_status", row.get("change_status"), "变动状态", user); validateDictionary(domain, projectId, "importance", row.get("importance"), "业务重要程度", user); validateDictionary(domain, projectId, "accounting_flag", row.get("accounting_flag"), "是否核算相关", user); prepared.add(row); } catch (BusinessException exception) { errors.add(Map.of("row_number", row.getOrDefault("row_number", 0), "message", exception.getMessage())); } }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("total", rows.size()); result.put("valid", prepared.size()); result.put("failed", errors.size()); result.put("duplicate", duplicate); result.put("directories", autoDirectories.size()); result.put("success", errors.isEmpty()); result.put("errors", errors); result.put("rows", prepared); return result;
    }

    private void validateDictionary(String domain, long projectId, String dictionaryCode, Object value, String label, AuthUser user) { String target = required(value, label, 64); if (!repository.dictionaryOptionExists(params("tenantId", user.tenantId(), "domain", domain, "projectId", projectId, "dictionaryCode", dictionaryCode, "value", target))) throw bad(label + "字典项无效：" + target); }
    private Map<String, Object> scopeValues(String domain, long projectId, Map<String, Object> body, AuthUser user) { Map<String, Object> values = new LinkedHashMap<>(); values.put("leaf_menu", text(body.get("leaf_menu"), 200, "末级菜单名称")); for (String[] item : List.of(new String[]{"function_type", "func_type", "功能类型"}, new String[]{"change_status", "change_status", "变动状态"}, new String[]{"importance", "importance", "业务重要程度"}, new String[]{"accounting_flag", "accounting_flag", "是否核算相关"})) { String value = required(body.get(item[0]), item[2], 64); validateDictionary(domain, projectId, item[1], value, item[2], user); values.put(item[0], value); } return values; }
    private Long ensureDirectory(String domain, long projectId, long systemId, String path, AuthUser user) { Long parent = null; for (String raw : path.replace('/', '\\').split("\\\\")) { String name = raw.trim(); if (name.isEmpty()) continue; Map<String, Object> p = context(domain, projectId, user); p.put("systemId", systemId); p.put("parentId", parent); p.put("name", name); List<Map<String, Object>> found = repository.directoryByParentAndName(p); if (found.isEmpty()) { if (depth(parent, user.tenantId()) >= 5) throw bad("范围目录路径超过五层：" + path); Map<String, Object> input = new LinkedHashMap<>(); input.put("physical_subsystem_id", systemId); input.put("parent_id", parent); input.put("directory_name", name); input.put("sort_no", 0); parent = number(saveDirectory(domain, projectId, null, input, user).get("id")); } else parent = number(found.get(0).get("id")); } if (parent == null) throw bad("范围目录路径不能为空"); return parent; }
    private void scope(String domain, long projectId, AuthUser user) { domain(domain); if (projectId <= 0) throw bad("请选择项目"); if (!repository.projectExists(params("projectId", projectId, "tenantId", user.tenantId()))) throw bad("项目不存在或不属于当前租户"); }
    private void domain(String domain) { if (!DOMAINS.contains(domain)) throw bad("测试大类无效"); }
    private void requireSystem(String domain, long projectId, long systemId, long tenantId) { if (!repository.participatingSystemExists(params("tenantId", tenantId, "domain", domain, "projectId", projectId, "systemId", systemId))) throw bad("请选择已启用的参测系统"); }
    private Long systemByReference(String domain, long projectId, String value, long tenantId) { List<Map<String, Object>> rows = repository.systemByReference(params("tenantId", tenantId, "domain", domain, "projectId", projectId, "value", value)); return rows.isEmpty() ? null : number(rows.get(0).get("id")); }
    private Map<String, Object> directory(long id, String domain, long projectId, long tenantId) { Map<String, Object> row = repository.directory(params("id", id, "tenantId", tenantId, "domain", domain, "projectId", projectId)); if (row == null) throw bad("范围目录不存在或不属于当前项目"); return row; }
    private String directoryPath(long id, long tenantId) { List<String> names = new ArrayList<>(); Long current = id; while (current != null) { Map<String, Object> row = repository.directoryParent(params("id", current, "tenantId", tenantId)); if (row == null) break; names.add(String.valueOf(row.get("directory_name"))); current = positiveOrNull(row.get("parent_id")); } java.util.Collections.reverse(names); return String.join("\\", names); }
    private Map<String, Object> scopeRow(long id, String domain, long projectId, long tenantId) { Map<String, Object> row = repository.scopeRow(params("id", id, "tenantId", tenantId, "domain", domain, "projectId", projectId)); if (row == null) throw bad("测试范围不存在或不属于当前项目"); return row; }
    private List<Long> descendantDirectoryIds(long root, long tenantId) { List<Long> result = new ArrayList<>(List.of(root)); for (int index = 0; index < result.size(); index++) result.addAll(repository.childDirectoryIds(params("tenantId", tenantId, "parentId", result.get(index)))); return result; }
    private int depth(Long parent, long tenantId) { int result = 0; Long current = parent; while (current != null) { result++; if (result > 5) return result; Map<String, Object> row = repository.directoryParent(params("id", current, "tenantId", tenantId)); current = row == null ? null : positiveOrNull(row.get("parent_id")); } return result; }
    private String nextScopeCode(String domain, long projectId, long systemId, long tenantId) { return systemCode(systemId, tenantId) + "-" + String.format(Locale.ROOT, "%04d", repository.nextScopeSequence(params("tenantId", tenantId, "domain", domain, "projectId", projectId, "systemId", systemId))); }
    private String systemCode(long systemId, long tenantId) { return repository.systemCode(params("systemId", systemId, "tenantId", tenantId)); }
    private String scopeCode(String value, String systemCode) { String code = required(value, "序号", 128).toUpperCase(Locale.ROOT); if (!code.matches(java.util.regex.Pattern.quote(systemCode.toUpperCase(Locale.ROOT)) + "-[0-9]{4,}")) throw bad("序号格式必须为“" + systemCode + "-流水号”"); return code; }
    private void uniqueScopeCode(String domain, long projectId, String code, Long id, long tenantId) { if (repository.scopeCodeExists(params("tenantId", tenantId, "domain", domain, "projectId", projectId, "scopeCode", code, "id", id))) throw conflict("序号已存在"); }
    private List<String> validValues(Collection<String> raw, Set<String> allowed, String label) { if (raw == null) return List.of(); List<String> result = new ArrayList<>(); for (String value : raw) { String clean = text(value, 64, label); if (clean != null) { clean = clean.toUpperCase(Locale.ROOT); if (allowed != null && !allowed.contains(clean)) throw bad(label + "无效：" + clean); result.add(clean); } } return result; }
    private void audit(String domain, long projectId, String type, long id, String action, Map<String, Object> detail, AuthUser user) { try { repository.insertAudit(params("id", next(), "tenantId", user.tenantId(), "domain", domain, "projectId", projectId, "entityType", type, "entityId", id, "actionCode", action, "userId", user.id(), "detailJson", objectMapper.writeValueAsString(detail))); } catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审计记录序列化失败"); } }
    private Map<String, Object> context(String domain, long projectId, AuthUser user) { return params("tenantId", user.tenantId(), "domain", domain, "projectId", projectId, "userId", user.id()); }
    private Map<String, Object> params(Object... pairs) { Map<String, Object> result = new LinkedHashMap<>(); for (int index = 0; index < pairs.length; index += 2) result.put(String.valueOf(pairs[index]), pairs[index + 1]); return result; }
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

package com.ccb.datamigration.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 独立问题清单服务：所有问题业务字段直接存储在 dm_issue。 */
@Service
public class IssueService {
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private final IssueRepository repository;
    private final DataMigrationPermissionService permissions;
    private final DataMigrationCodeValueService codeValues;

    public IssueService(IssueRepository repository, DataMigrationPermissionService permissions, DataMigrationCodeValueService codeValues) {
        this.repository = repository;
        this.permissions = permissions;
        this.codeValues = codeValues;
    }

    public PageResult<Map<String, Object>> list(Long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        long total = repository.count(user.tenantId(), scope, false, granularity, systemCode, issueSource, defectType, frequency, keyword);
        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        return new PageResult<>(repository.page(user.tenantId(), scope, false, granularity, systemCode, issueSource, defectType, frequency, keyword, safeSize, (long) (safePage - 1) * safeSize), total, safePage, safeSize);
    }

    public Map<String, Object> findById(long id, AuthUser user) {
        Map<String, Object> row = findByIdInternal(id, user.tenantId(), false);
        permissions.requireStoredProject(row.get("project_id"), user);
        return row;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = number(body.get("projectId"), "projectId"); ensureProject(projectId, user);
        String code = text(body.get("issueCode"), "issueCode"); String name = text(body.get("issueName"), "issueName");
        String systemCode = textOrNull(body.get("systemCode")); ensureSystemCode(projectId, systemCode, user);
        ensureCodeAvailable(projectId, code, null, user); validateEnums(body, user);
        long id = nextId();
        try {
            repository.insert(id, user.tenantId(), projectId, code, name, textOrNull(body.get("granularity")), systemCode, textOrNull(body.get("issueSource")), textOrNull(body.get("defectType")), textOrNull(body.get("issueDescription")), textOrNull(body.get("solution")), textOrNull(body.get("meetingConclusion")), textOrNull(body.get("processingSteps")), textOrNull(body.get("businessScenario")), textOrNull(body.get("handler")), textOrNull(body.get("responsibleParty")), keywords(body.get("keywords")), textOrNull(body.get("frequency")), user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw issueCodeConflict(ex);
        }
        saveRelations(id, projectId, body, user); audit(user, "ISSUE_CREATE", projectId, id); return findByIdInternal(id, user.tenantId(), false);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> current = findByIdInternal(id, user.tenantId(), false); permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        // T32 决策 D2：维护操作的归属恒取库中记录，入参 projectId 一律忽略，UPDATE 不再包含 project_id
        long projectId = permissions.requireStoredProject(current.get("project_id"), user);
        String code = text(body.getOrDefault("issueCode", current.get("asset_code")), "issueCode"); String name = text(body.getOrDefault("issueName", current.get("asset_name")), "issueName");
        String systemCode = textOrNull(body.get("systemCode")); ensureSystemCode(projectId, systemCode, user);
        ensureCodeAvailable(projectId, code, id, user); validateEnums(body, user);
        try {
            int changed = repository.update(id, user.tenantId(), code, name, textOrNull(body.get("granularity")), systemCode, textOrNull(body.get("issueSource")), textOrNull(body.get("defectType")), textOrNull(body.get("issueDescription")), textOrNull(body.get("solution")), textOrNull(body.get("meetingConclusion")), textOrNull(body.get("processingSteps")), textOrNull(body.get("businessScenario")), textOrNull(body.get("handler")), textOrNull(body.get("responsibleParty")), keywords(body.get("keywords")), textOrNull(body.get("frequency")), user.id());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
        } catch (DataIntegrityViolationException ex) {
            throw issueCodeConflict(ex);
        }
        saveRelations(id, projectId, body, user); audit(user, "ISSUE_UPDATE", projectId, id); return findByIdInternal(id, user.tenantId(), false);
    }

    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : normalizeIds(ids)) {
            Map<String, Object> row = findByIdInternal(id, user.tenantId(), false);
            long projectId = permissions.requireStoredProject(row.get("project_id"), user);
            permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
            int changed = repository.softDelete(user.tenantId(), id, user.id());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
            audit(user, "ISSUE_DELETE", projectId, id);
        }
    }

    public PageResult<Map<String, Object>> recycleBinList(Long projectId, String keyword, int page, int size, AuthUser user) {
        permissions.requireAdmin(user); long scope = permissions.requireProject(projectId, user);
        long total = repository.count(user.tenantId(), scope, true, null, null, null, null, null, keyword);
        int safePage = Math.max(1, page); int safeSize = normalizePageSize(size);
        return new PageResult<>(repository.page(user.tenantId(), scope, true, null, null, null, null, null, keyword, safeSize, (long) (safePage - 1) * safeSize), total, safePage, safeSize);
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        List<Long> restoreIds = normalizeIds(ids);
        Map<Long, Long> projectIds = new HashMap<>();
        for (Long id : restoreIds) {
            Map<String, Object> row = findByIdInternal(id, user.tenantId(), true);
            long projectId = permissions.requireStoredProject(row.get("project_id"), user);
            ensureCodeAvailable(projectId, String.valueOf(row.get("asset_code")), id, user);
            projectIds.put(id, projectId);
        }
        for (int index = 0; index < restoreIds.size(); index++) {
            Long id = restoreIds.get(index);
            try {
                int changed = repository.restore(user.tenantId(), id, user.id());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw issueCodeConflict(ex);
            }
            audit(user, "ISSUE_RESTORE", projectIds.get(id), id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        List<Long> purgeIds = normalizeIds(ids);
        Map<Long, Long> projectIds = new HashMap<>();
        for (Long id : purgeIds) {
            Map<String, Object> row = findByIdInternal(id, user.tenantId(), true);
            long projectId = permissions.requireStoredProject(row.get("project_id"), user);
            projectIds.put(id, projectId);
        }
        for (Long id : purgeIds) {
            repository.deleteAllRelations(user.tenantId(), id);
            int changed = repository.purge(user.tenantId(), id);
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
            audit(user, "ISSUE_PURGE", projectIds.get(id), id);
        }
    }

    @Transactional
    public void purgeAll(Long projectId, AuthUser user) { permissions.requireAdmin(user); long scope = permissions.requireProject(projectId, user); repository.purgeAll(user.tenantId(), scope); audit(user, "ISSUE_PURGE_ALL", scope, 0L); }

    public String getSystemName(String systemCode, AuthUser user) { return systemCode == null || systemCode.isBlank() ? null : repository.systemName(user.tenantId(), systemCode.trim()); }
    public List<Map<String, Object>> getMeetingOptions(Long projectId, AuthUser user) { long scope = permissions.requireProject(projectId, user); return repository.meetingOptions(user.tenantId(), scope); }
    public List<Map<String, Object>> getTargetTableOptions(Long projectId, AuthUser user) { long scope = permissions.requireProject(projectId, user); return repository.targetTableOptions(user.tenantId(), scope); }
    public List<Map<String, Object>> getTargetFieldOptions(Long tableCode, AuthUser user) { if (tableCode == null) return List.of(); List<Long> projects = repository.targetTableProjects(user.tenantId(), tableCode); if (projects.isEmpty()) return List.of(); permissions.requireAccessible(projects.get(0), user); return repository.targetFieldOptions(user.tenantId(), tableCode); }

    public List<Map<String, Object>> exportRows(Long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        return repository.exportRows(user.tenantId(), scope, granularity, systemCode, issueSource, defectType, frequency, keyword);
    }
    private Map<String, Object> findByIdInternal(long id, long tenantId, boolean deleted) { Map<String, Object> result = repository.require(tenantId, id, deleted); result.put("relatedMeetingMinutes", repository.relationIds(tenantId, id, "MEETING")); result.put("relatedTables", repository.relationIds(tenantId, id, "TABLE")); result.put("relatedFields", repository.relationIds(tenantId, id, "FIELD")); return result; }
    private void saveRelations(long issueId, long projectId, Map<String, Object> body, AuthUser user) {
        if (body.containsKey("relatedMeetingMinutes")) saveRelationType(issueId, projectId, body.get("relatedMeetingMinutes"), "MEETING", user);
        if (body.containsKey("relatedTables")) saveRelationType(issueId, projectId, body.get("relatedTables"), "TABLE", user);
        if (body.containsKey("relatedFields")) saveRelationType(issueId, projectId, body.get("relatedFields"), "FIELD", user);
        ensureFieldRelationsBelongToRelatedTables(issueId, user);
    }

    private void saveRelationType(long issueId, long projectId, Object raw, String targetType, AuthUser user) {
        List<Long> targetIds = new ArrayList<>();
        for (Object value : asList(raw)) {
            long targetId;
            try { targetId = Long.parseLong(String.valueOf(value)); }
            catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "关联目标编号无效"); }
            if (!targetIds.contains(targetId)) targetIds.add(targetId);
        }
        for (Long targetId : targetIds) ensureRelationTarget(targetId, targetType, projectId, user);
        repository.replaceRelations(user.tenantId(), issueId, targetType, targetIds, user.id());
    }

    private void ensureRelationTarget(long id, String type, long projectId, AuthUser user) {
        if (!Set.of("MEETING", "TABLE", "FIELD").contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的关联类型");
        if (!repository.relationTargetExists(user.tenantId(), projectId, id, type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "关联目标不存在、项目不一致或无权访问");
    }

    private void ensureFieldRelationsBelongToRelatedTables(long issueId, AuthUser user) {
        if (repository.hasInvalidFieldRelations(user.tenantId(), issueId)) throw new BusinessException(ErrorCode.BAD_REQUEST, "关联字段必须属于已关联的目标表");
    }
    private void ensureCodeAvailable(long projectId, String code, Long currentId, AuthUser user) { if (repository.issueCodeExists(user.tenantId(), projectId, code, currentId)) throw new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在（含已删除记录）"); }
    private void validateEnums(Map<String, Object> body, AuthUser user) {
        codeValues.requireActive(DataMigrationCodeValueService.DM_ISSUE_GRANULARITY, "颗粒度", stringValue(body.get("granularity")), user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_ISSUE_SOURCE, "问题来源", stringValue(body.get("issueSource")), user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_DEFECT_TYPE, "缺陷类型", stringValue(body.get("defectType")), user);
        codeValues.requireActive(DataMigrationCodeValueService.DM_ISSUE_FREQUENCY, "问题频率", stringValue(body.get("frequency")), user);
    }
    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
    private void ensureProject(long id, AuthUser user) { permissions.requireAccessible(id, user); }
    private void ensureSystemCode(long projectId, String systemCode, AuthUser user) { if (systemCode != null && !repository.enabledComponentExists(user.tenantId(), projectId, systemCode)) throw new BusinessException(ErrorCode.BAD_REQUEST, "系统编号不属于所选项目或已停用"); }
    private static int normalizePageSize(int size) { return PAGE_SIZES.contains(size) ? size : 20; }
    private static String text(Object value, String field) { if (value == null || String.valueOf(value).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空"); return String.valueOf(value).trim(); }
    private static String textOrNull(Object value) { if (value == null) return null; String text = String.valueOf(value).trim(); return text.isEmpty() ? null : text; }
    private static long number(Object value, String field) { try { return Long.parseLong(text(value, field)); } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 必须为数字"); } }
    private static String keywords(Object value) { if (value == null) return null; if (value instanceof Collection<?> c) return c.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).distinct().reduce((a, b) -> a + "," + b).orElse(null); return textOrNull(value); }
    private static List<?> asList(Object value) { return value instanceof Collection<?> c ? new ArrayList<>(c) : value == null ? List.of() : List.of(value); }
    private static List<Long> normalizeIds(List<Long> ids) { return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList(); }
    private static BusinessException issueCodeConflict(DataIntegrityViolationException cause) {
        BusinessException conflict = new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在");
        conflict.initCause(cause);
        return conflict;
    }
    private void audit(AuthUser user, String operation, long projectId, long id) { repository.insertAudit(user.tenantId(), user.id(), projectId, operation, id); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

package com.ccb.datamigration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StructuredAssetService {
    /** 迁移参数已域化为专属服务（ParameterService），不再作为通用结构化资源承载（REQ-20260906-067）。 */
    static final Set<String> TYPES = Set.of();
    private final StructuredAssetRepository repository;
    private final ObjectMapper objectMapper;
    private final DataMigrationPermissionService permissions;
    private final ContentDocCodeGenerator docCodes;
    public StructuredAssetService(StructuredAssetRepository repository, ObjectMapper objectMapper, DataMigrationPermissionService permissions,
                                  ContentDocCodeGenerator docCodes) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.permissions = permissions;
        this.docCodes = docCodes;
    }
    /** T32：列表必须落在单个可访问项目内，SQL 恒定 {@code project_id} 过滤。 */
    public List<Map<String,Object>> list(String type, Long projectId, String keyword, AuthUser user) {
        validateType(type);
        long scope = permissions.requireProject(projectId, user);
        return repository.list(type, user.tenantId(), scope, Optional.ofNullable(keyword).orElse(""));
    }
    public Map<String,Object> save(String type, Map<String,Object> body, AuthUser user) {
        validateType(type);
        if (body.get("projectId") == null || body.get("assetName") == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset fields are required");
        long projectId;
        try { projectId = Long.parseLong(String.valueOf(body.get("projectId"))); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId must be numeric"); }
        // T32：新增归属取前端 projectId，但必须是本租户存在且调用者可访问的项目
        permissions.requireAccessible(projectId, user);
        String systemCode = body.get("systemCode") == null || String.valueOf(body.get("systemCode")).isBlank() ? "" : String.valueOf(body.get("systemCode")).trim();
        if (!systemCode.isBlank()) ensureComponent(systemCode, projectId, user);
        String structuredData;
        try { structuredData = objectMapper.writeValueAsString(body.getOrDefault("structuredData", Map.of())); }
        catch (JsonProcessingException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "structuredData must be valid JSON"); }
        long id = System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
        repository.insert(type, id, user.tenantId(), projectId, systemCode, docCodes.generate(type), body.get("assetName"),
                structuredData, user.id(), user.id(), user.id());
        audit(user, "STRUCTURED_CREATE", projectId, id);
        return repository.require(type, id, user.tenantId(), false);
    }

    @Transactional
    public Map<String,Object> update(String type, long id, Map<String,Object> body, AuthUser user) {
        validateType(type);
        Map<String,Object> current = repository.require(type, id, user.tenantId(), false);
        permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        if (body.get("assetName") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset fields are required");
        }
        // T32 决策 D2：维护操作的归属恒取库中记录，入参 projectId 一律忽略，UPDATE 不再包含 project_id
        long projectId = permissions.requireStoredProject(current.get("project_id"), user);
        String systemCode = body.get("systemCode") == null || String.valueOf(body.get("systemCode")).isBlank()
                ? "" : String.valueOf(body.get("systemCode")).trim();
        if (!systemCode.isBlank()) ensureComponent(systemCode, projectId, user);
        String name = text(body.get("assetName"), "assetName");
        String structuredData = json(body.getOrDefault("structuredData", Map.of()));
        repository.update(type, systemCode, name, structuredData, id, user.tenantId());
        audit(user, "STRUCTURED_UPDATE", projectId, id);
        return repository.require(type, id, user.tenantId(), false);
    }

    @Transactional
    public void delete(Collection<Long> ids, String type, AuthUser user) {
        validateType(type);
        for (Long id : ids == null ? List.<Long>of() : ids) {
            Map<String,Object> row = repository.require(type, id, user.tenantId(), false);
            long projectId = permissions.requireStoredProject(row.get("project_id"), user);
            permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
            if (repository.hasActiveRelation(user.tenantId(), id)) throw new BusinessException(ErrorCode.CONFLICT, "Structured asset has related records");
            repository.softDelete(type, user.id(), id, user.tenantId());
            audit(user, "STRUCTURED_DELETE", projectId, id);
        }
    }

    /** 统一回收站：结构化表软删总数（T32 按项目统计，SQL COUNT，不拉明细）。 */
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        validateType(type);
        return repository.countDeleted(type, user.tenantId(), projectId, keyword == null ? null : keyword.trim());
    }

    /** 统一回收站原生分页（T32 按项目）：结构化表软删按业务编号（doc_code）升序、空值末尾，取前 {@code limit} 行。 */
    public List<Map<String,Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        validateType(type);
        if (limit <= 0) return List.of();
        return repository.listDeletedPage(type, user.tenantId(), projectId, keyword == null ? null : keyword.trim(), limit);
    }

    /** 查询结构化内容的软删除详情。 */
    public Map<String, Object> findDeletedDetail(String type, long id, AuthUser user) {
        validateType(type);
        Map<String, Object> row = repository.requireDeletedDetail(type, id, user.tenantId());
        permissions.requireStoredProject(row.get("project_id"), user);
        return row;
    }

    /** 统一回收站：结构化表恢复（管理员权限由回收站入口统一校验）。 */
    @Transactional
    public void restore(String type, Collection<Long> ids, AuthUser user) {
        validateType(type);
        for (Long id : ids) {
            // T32：恢复前按库中归属做项目隔离校验，跨项目 id 直接拒绝
            long projectId = permissions.requireStoredProject(repository.require(type, id, user.tenantId(), true).get("project_id"), user);
            try {
                int changed = repository.restore(type, id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "Structured asset state changed, please retry");
            } catch (DataIntegrityViolationException ex) {
                // 恢复后活动行 doc_code 与既有活动行冲突（uk_dm_*_active_code）：与统一方案同构，翻译为 CONFLICT(40900)。
                throw new BusinessException(ErrorCode.CONFLICT, "内容编号在该项目下已存在，无法恢复");
            }
            audit(user, "STRUCTURED_RESTORE", projectId, id);
        }
    }

    /** 统一回收站：结构化表彻底删除。 */
    @Transactional
    public void purge(String type, Collection<Long> ids, AuthUser user) {
        validateType(type);
        for (Long id : ids) {
            long projectId = permissions.requireStoredProject(repository.require(type, id, user.tenantId(), true).get("project_id"), user);
            int changed = repository.purge(type, id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset not found in recycle bin");
            audit(user, "STRUCTURED_PURGE", projectId, id);
        }
    }

    private static void validateType(String type) {
        if (type == null || !TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
    }

    private void ensureComponent(String systemCode, long projectId, AuthUser user) {
        if (!repository.enabledComponentExists(systemCode, projectId, user.tenantId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "structuredData must be valid JSON"); }
    }

    private static String text(Object value, String field) {
        if (value == null || String.valueOf(value).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " is required");
        return String.valueOf(value).trim();
    }

    private void audit(AuthUser user, String operation, long projectId, long id) {
        repository.insertAudit(user.tenantId(), user.id(), projectId, operation, id);
    }
}

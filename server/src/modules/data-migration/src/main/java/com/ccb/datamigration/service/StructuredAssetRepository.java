package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class StructuredAssetRepository {
    private final StructuredAssetMapper mapper;

    public StructuredAssetRepository(StructuredAssetMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list(String type, long tenantId, long projectId, String keyword) {
        return mapper.list(tableFor(type), type, tenantId, projectId, keyword);
    }

    public void insert(String type, long id, long tenantId, long projectId, String systemCode, String docCode,
                       Object docName, String structuredData, long ownerId, long createdBy, long updatedBy) {
        mapper.insert(tableFor(type), id, tenantId, projectId, systemCode, docCode, docName, structuredData, ownerId,
                createdBy, updatedBy);
    }

    public Map<String, Object> require(String type, long id, long tenantId, boolean deleted) {
        List<Map<String, Object>> rows = mapper.find(tableFor(type), type, id, tenantId, deleted ? 1 : 0);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset not found");
        }
        return rows.get(0);
    }

    public void update(String type, String systemCode, String docName, String structuredData, long id, long tenantId) {
        mapper.update(tableFor(type), systemCode, docName, structuredData, id, tenantId);
    }

    public int softDelete(String type, long deletedBy, long id, long tenantId) {
        return mapper.softDelete(tableFor(type), deletedBy, id, tenantId);
    }

    public long countDeleted(String type, long tenantId, long projectId, String keyword) {
        Long total = mapper.countDeleted(tableFor(type), tenantId, projectId, keyword);
        return total == null ? 0L : total;
    }

    public List<Map<String, Object>> listDeletedPage(String type, long tenantId, long projectId, String keyword, int limit) {
        return mapper.listDeletedPage(tableFor(type), type, tenantId, projectId, keyword, limit);
    }

    public Map<String, Object> requireDeletedDetail(String type, long id, long tenantId) {
        List<Map<String, Object>> rows = mapper.findDeletedDetail(tableFor(type), type, id, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset not found in recycle bin");
        }
        return rows.get(0);
    }

    public int restore(String type, long id, long tenantId) {
        return mapper.restore(tableFor(type), id, tenantId);
    }

    public int purge(String type, long id, long tenantId) {
        return mapper.purge(tableFor(type), id, tenantId);
    }

    public boolean hasActiveRelation(long tenantId, long id) {
        String idText = String.valueOf(id);
        return mapper.relatedCounts(tenantId, id, idText).stream().mapToLong(Long::longValue).sum() > 0;
    }

    public boolean enabledComponentExists(String systemCode, long projectId, long tenantId) {
        Integer count = mapper.enabledComponentCount(systemCode, projectId, tenantId);
        return count != null && count > 0;
    }

    public void insertAudit(long tenantId, long actorId, long projectId, String operation, long entityId) {
        mapper.insertAudit(tenantId, actorId, projectId, operation, entityId);
    }

    private static String tableFor(String type) {
        return ContentAssetTables.tableFor(type);
    }
}

package com.ccb.datamigration.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ExcelRepository {
    private final ExcelMapper mapper;

    public ExcelRepository(ExcelMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> exportRows(String type, long tenantId, long projectId, String systemCode,
                                                 String keyword) {
        return mapper.exportRows(tableFor(type), type, tenantId, projectId, systemCode, keyword);
    }

    public boolean enabledComponentExists(String systemCode, long projectId, long tenantId) {
        Integer count = mapper.enabledComponentCount(systemCode, projectId, tenantId);
        return count != null && count > 0;
    }

    public void insertAsset(String type, long id, long tenantId, long projectId, String systemCode, String docCode,
                            String docName, String structuredData, long ownerId, long createdBy, long updatedBy) {
        mapper.insertAsset(tableFor(type), id, tenantId, projectId, systemCode, docCode, docName, structuredData,
                ownerId, createdBy, updatedBy);
    }

    public void insertAudit(long tenantId, long actorId, long projectId, String detailJson) {
        mapper.insertAudit(tenantId, actorId, projectId, detailJson);
    }

    private static String tableFor(String type) {
        return ContentAssetTables.tableFor(type);
    }
}

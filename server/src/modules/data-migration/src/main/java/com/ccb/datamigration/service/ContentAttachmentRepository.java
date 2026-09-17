package com.ccb.datamigration.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ContentAttachmentRepository {
    private final ContentAttachmentMapper mapper;

    public ContentAttachmentRepository(ContentAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> listActive(long tenantId, String businessType, long businessId) { return mapper.listActive(tenantId, businessType, businessId); }
    public List<Map<String, Object>> listDeleted(long tenantId, String businessType, long businessId) { return mapper.listDeleted(tenantId, businessType, businessId); }
    public List<Long> activeAttachmentIds(long tenantId, String businessType, long businessId) { return mapper.activeAttachmentIds(tenantId, businessType, businessId); }
    public List<Long> deletedAttachmentIds(long tenantId, String businessType, long businessId) { return mapper.deletedAttachmentIds(tenantId, businessType, businessId); }
    public int updateSort(int sortOrder, long businessId, long attachmentId, long tenantId, String businessType) { return mapper.updateSort(sortOrder, businessId, attachmentId, tenantId, businessType); }
    public int restoreAttachment(String fileName, int sortOrder, long businessId, long attachmentId, long tenantId, String businessType) { return mapper.restoreAttachment(fileName, sortOrder, businessId, attachmentId, tenantId, businessType); }
    public int insert(long id, long tenantId, String businessType, long businessId, long attachmentId, String fileName, int sortOrder, long createdBy) { return mapper.insert(id, tenantId, businessType, businessId, attachmentId, fileName, sortOrder, createdBy); }
    public int softDeleteAttachment(long deletedBy, long tenantId, String businessType, long businessId, long attachmentId) { return mapper.softDeleteAttachment(deletedBy, tenantId, businessType, businessId, attachmentId); }
    public int restoreById(long id, long tenantId) { return mapper.restoreById(id, tenantId); }
    public int purgeById(long id, long tenantId) { return mapper.purgeById(id, tenantId); }
    public List<Long> attachmentIds(long tenantId, String businessType, long businessId) { return mapper.attachmentIds(tenantId, businessType, businessId); }
    public int deleteAll(long tenantId, String businessType, long businessId) { return mapper.deleteAll(tenantId, businessType, businessId); }
    public List<Map<String, Object>> softDeletedRows(long tenantId, List<Long> ids) { return ids.isEmpty() ? List.of() : mapper.softDeletedRows(tenantId, ids); }
    public List<Long> softDeletedBusinessIds(long id, long tenantId) { return mapper.softDeletedBusinessIds(id, tenantId); }
}

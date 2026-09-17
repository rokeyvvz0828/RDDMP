package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentGateway;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

final class ContentAttachmentTestSupport {
    private ContentAttachmentTestSupport() {
    }

    static ContentAttachmentService service(JdbcTemplate jdbc, AttachmentGateway attachmentGateway) {
        ContentAttachmentMapper mapper = new ContentAttachmentMapper() {
            @Override public List<Map<String, Object>> listActive(long tenantId, String businessType, long businessId) { return jdbc.queryForList("SELECT id, attachment_id, file_name, sort_order, created_by, created_at FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 0 ORDER BY sort_order ASC, created_at ASC", tenantId, businessType, businessId); }
            @Override public List<Map<String, Object>> listDeleted(long tenantId, String businessType, long businessId) { return jdbc.queryForList("SELECT id, attachment_id, file_name, sort_order, deleted_by, deleted_at FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 1 ORDER BY deleted_at DESC", tenantId, businessType, businessId); }
            @Override public List<Long> activeAttachmentIds(long tenantId, String businessType, long businessId) { return jdbc.queryForList("SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 0", Long.class, tenantId, businessType, businessId); }
            @Override public List<Long> deletedAttachmentIds(long tenantId, String businessType, long businessId) { return jdbc.queryForList("SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 1", Long.class, tenantId, businessType, businessId); }
            @Override public int updateSort(int sortOrder, long businessId, long attachmentId, long tenantId, String businessType) { return jdbc.update("UPDATE dm_content_attachment SET sort_order = ? WHERE business_id = ? AND attachment_id = ? AND tenant_id = ? AND business_type = ? AND deleted = 0", sortOrder, businessId, attachmentId, tenantId, businessType); }
            @Override public int restoreAttachment(String fileName, int sortOrder, long businessId, long attachmentId, long tenantId, String businessType) { return jdbc.update("UPDATE dm_content_attachment SET deleted = 0, deleted_by = NULL, deleted_at = NULL, file_name = ?, sort_order = ? WHERE business_id = ? AND attachment_id = ? AND tenant_id = ? AND business_type = ? AND deleted = 1", fileName, sortOrder, businessId, attachmentId, tenantId, businessType); }
            @Override public int insert(long id, long tenantId, String businessType, long businessId, long attachmentId, String fileName, int sortOrder, long createdBy) { return jdbc.update("INSERT INTO dm_content_attachment (id, tenant_id, business_type, business_id, attachment_id, file_name, sort_order, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, tenantId, businessType, businessId, attachmentId, fileName, sortOrder, createdBy); }
            @Override public int softDeleteAttachment(long deletedBy, long tenantId, String businessType, long businessId, long attachmentId) { return jdbc.update("UPDATE dm_content_attachment SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND attachment_id = ? AND deleted = 0", deletedBy, tenantId, businessType, businessId, attachmentId); }
            @Override public int restoreById(long id, long tenantId) { return jdbc.update("UPDATE dm_content_attachment SET deleted = 0, deleted_by = NULL, deleted_at = NULL WHERE id = ? AND tenant_id = ? AND deleted = 1", id, tenantId); }
            @Override public int purgeById(long id, long tenantId) { return jdbc.update("DELETE FROM dm_content_attachment WHERE id = ? AND tenant_id = ? AND deleted = 1", id, tenantId); }
            @Override public List<Long> attachmentIds(long tenantId, String businessType, long businessId) { return jdbc.queryForList("SELECT DISTINCT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ?", Long.class, tenantId, businessType, businessId); }
            @Override public int deleteAll(long tenantId, String businessType, long businessId) { return jdbc.update("DELETE FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ?", tenantId, businessType, businessId); }
            @Override public List<Map<String, Object>> softDeletedRows(long tenantId, List<Long> ids) { return ids.stream().flatMap(id -> jdbc.queryForList("SELECT id, business_id FROM dm_content_attachment WHERE tenant_id = ? AND deleted = 1 AND id = ?", tenantId, id).stream()).toList(); }
            @Override public List<Long> softDeletedBusinessIds(long id, long tenantId) { return jdbc.queryForList("SELECT business_id FROM dm_content_attachment WHERE id = ? AND tenant_id = ? AND deleted = 1", Long.class, id, tenantId); }
        };
        return new ContentAttachmentService(new ContentAttachmentRepository(mapper), attachmentGateway, null);
    }
}

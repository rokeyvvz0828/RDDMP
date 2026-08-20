package com.ccb.attachment.service;

import com.ccb.infrastructure.storage.MinioStorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AttachmentCleanupService {
    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;

    public AttachmentCleanupService(JdbcTemplate jdbc, MinioStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${ccb.attachment.cleanup-delay-ms:3600000}")
    public void scheduledCleanup() {
        cleanupBatch(100);
    }

    public int cleanupBatch(int limit) {
        int bounded = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, tenant_id, object_key, status, cleanup_attempts FROM att_file WHERE (status = 'TEMP' AND expires_at <= CURRENT_TIMESTAMP) OR (status = 'DELETED' AND cleanup_status IN ('PENDING','RETRY')) ORDER BY id LIMIT ?", bounded);
        for (Map<String, Object> row : rows) cleanup(row);
        return rows.size();
    }

    private void cleanup(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        long tenantId = ((Number) row.get("tenant_id")).longValue();
        if ("TEMP".equals(row.get("status"))) {
            int changed = jdbc.update("UPDATE att_file SET status = 'DELETED', deleted_at = CURRENT_TIMESTAMP, cleanup_status = 'PENDING' WHERE id = ? AND tenant_id = ? AND status = 'TEMP' AND expires_at <= CURRENT_TIMESTAMP", id, tenantId);
            if (changed != 1) return;
        }
        try {
            storage.delete(String.valueOf(row.get("object_key")));
            jdbc.update("UPDATE att_file SET cleanup_status = 'DONE', cleanup_error = NULL WHERE id = ? AND tenant_id = ? AND status = 'DELETED'", id, tenantId);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            jdbc.update("UPDATE att_file SET cleanup_status = 'RETRY', cleanup_attempts = cleanup_attempts + 1, cleanup_error = ? WHERE id = ? AND tenant_id = ? AND status = 'DELETED'", message.substring(0, Math.min(message.length(), 1000)), id, tenantId);
        }
    }
}

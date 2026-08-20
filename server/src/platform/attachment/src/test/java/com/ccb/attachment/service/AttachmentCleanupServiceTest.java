package com.ccb.attachment.service;

import com.ccb.infrastructure.storage.MinioStorageProperties;
import com.ccb.infrastructure.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentCleanupServiceTest {
    @Test
    void expiresTempAttachmentThenDeletesObject() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        StubStorage storage = new StubStorage(false);

        int processed = new AttachmentCleanupService(jdbc, storage).cleanupBatch(100);

        assertEquals(1, processed);
        assertEquals("DELETED", jdbc.status);
        assertEquals("DONE", jdbc.cleanupStatus);
        assertEquals("attachments/1/object-1", storage.deletedKey);
    }

    @Test
    void keepsFailedObjectDeletionRetryable() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        jdbc.status = "DELETED";
        StubStorage storage = new StubStorage(true);

        new AttachmentCleanupService(jdbc, storage).cleanupBatch(1);

        assertEquals("RETRY", jdbc.cleanupStatus);
        assertEquals(1, jdbc.cleanupAttempts);
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private String status = "TEMP";
        private String cleanupStatus;
        private int cleanupAttempts;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of("id", 31L, "tenant_id", 1L, "object_key", "attachments/1/object-1",
                    "status", status, "cleanup_attempts", cleanupAttempts));
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("status = 'DELETED', deleted_at")) {
                status = "DELETED";
                cleanupStatus = "PENDING";
            } else if (sql.contains("cleanup_status = 'DONE'")) {
                cleanupStatus = "DONE";
            } else if (sql.contains("cleanup_status = 'RETRY'")) {
                cleanupStatus = "RETRY";
                cleanupAttempts++;
            }
            return 1;
        }
    }

    private static final class StubStorage extends MinioStorageService {
        private final boolean fail;
        private String deletedKey;

        private StubStorage(boolean fail) {
            super(storageProperties());
            this.fail = fail;
        }

        @Override
        public void delete(String objectKey) {
            deletedKey = objectKey;
            if (fail) throw new IllegalStateException("storage unavailable");
        }

        private static MinioStorageProperties storageProperties() {
            MinioStorageProperties properties = new MinioStorageProperties();
            properties.setEndpoint("http://127.0.0.1:9000");
            properties.setAccessKey("test-access-key");
            properties.setSecretKey("test-secret-key");
            properties.setBucket("test-bucket");
            properties.setPresignedExpirySeconds(60);
            return properties;
        }
    }
}

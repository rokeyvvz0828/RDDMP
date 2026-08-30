package com.ccb.attachment.service;

import com.ccb.attachment.config.AttachmentProperties;
import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageProperties;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 1L, true);

    @Test
    void uploadsAsUploaderOwnedTempAttachmentAndNormalizesExtension() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        StubStorage storage = new StubStorage();
        AttachmentService service = service(jdbc, storage);
        String longExtension = "A".repeat(40);

        var item = service.upload(new MockMultipartFile("file", "../测试报告." + longExtension,
                "application/octet-stream", "report".getBytes()), USER);

        assertEquals("测试报告." + longExtension, item.fileName());
        assertEquals("a".repeat(32), item.fileExtension());
        assertEquals("TEMP", item.status());
        assertEquals(USER.id(), item.uploaderId());
        assertEquals(storage.objectKey, jdbc.row.get("object_key"));
        assertTrue(storage.objectKey.endsWith("." + "a".repeat(32)));
    }

    @Test
    void treatsTrailingDotAsNoExtension() {
        StubStorage storage = new StubStorage();
        AttachmentService service = service(new StubJdbcTemplate(), storage);

        var item = service.upload(new MockMultipartFile("file", "说明材料.", "text/plain", "x".getBytes()), USER);

        assertNull(item.fileExtension());
        assertTrue(storage.objectKey.endsWith(".bin"));
    }

    @Test
    void bindsOnlyUploaderOwnedTempAttachmentAndKeepsSameBindingIdempotent() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        AttachmentService service = service(jdbc, new StubStorage());
        long attachmentId = service.upload(new MockMultipartFile("file", "report.pdf", "application/pdf", "x".getBytes()), USER).id();
        AttachmentBindingCommand command = new AttachmentBindingCommand(attachmentId, "release", "SQ-001", "P1");

        service.bind(command, USER);
        int bindingUpdates = jdbc.bindingUpdates;
        service.bind(command, USER);

        assertEquals("BOUND", jdbc.row.get("status"));
        assertEquals("release", jdbc.row.get("business_type"));
        assertEquals("SQ-001", jdbc.row.get("business_key"));
        assertEquals(bindingUpdates, jdbc.bindingUpdates);
        AuthUser otherUser = new AuthUser(8L, 1L, "other", "", "其他用户", 1L, false);
        assertThrows(BusinessException.class,
                () -> service.bind(new AttachmentBindingCommand(attachmentId, "release", "SQ-002", "P1"), otherUser));
    }

    private AttachmentService service(StubJdbcTemplate jdbc, StubStorage storage) {
        AttachmentAccessPolicy allowRelease = new AttachmentAccessPolicy() {
            public String businessType() { return "release"; }
            public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) { return true; }
        };
        return new AttachmentService(jdbc, storage, source -> "preview:" + source,
                new AttachmentAccessPolicyRegistry(List.of(allowRelease)), new AttachmentProperties());
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> row = new LinkedHashMap<>();
        private int bindingUpdates;

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO att_file")) {
                row.put("id", args[0]);
                row.put("tenant_id", args[1]);
                row.put("file_name", args[2]);
                row.put("content_type", args[3]);
                row.put("file_size", args[4]);
                row.put("object_key", args[5]);
                row.put("file_extension", args[6]);
                row.put("status", "TEMP");
                row.put("uploader_id", args[7]);
                row.put("business_type", null);
                row.put("business_key", null);
                row.put("project_ref", null);
                row.put("created_at", Timestamp.valueOf(LocalDateTime.of(2026, 8, 14, 9, 0)));
            } else if (sql.startsWith("UPDATE att_file SET status = 'BOUND'")) {
                bindingUpdates++;
                row.put("status", "BOUND");
                row.put("business_type", args[0]);
                row.put("business_key", args[1]);
                row.put("project_ref", args[2]);
            }
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return row.isEmpty() ? List.of() : List.of(row);
        }
    }

    private static final class StubStorage extends MinioStorageService {
        private String objectKey;

        private StubStorage() {
            super(storageProperties());
        }

        @Override
        public void put(String objectKey, InputStream input, long size, String contentType) {
            this.objectKey = objectKey;
        }

        @Override
        public String presignedUrl(String objectKey) {
            return "https://storage.local/" + objectKey;
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

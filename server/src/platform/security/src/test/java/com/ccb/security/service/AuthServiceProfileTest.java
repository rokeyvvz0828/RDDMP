package com.ccb.security.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageProperties;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthMe;
import com.ccb.security.model.AuthUser;
import com.ccb.security.repository.AuthRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthServiceProfileTest {
    @Test
    void keepsUserProfileAvailableWhenAvatarStorageIsUnavailable() {
        AuthUser user = new AuthUser(1, 1, "admin", "hash", "管理员", 1, true,
                "平台管理部", "avatars/1/1/admin.png");
        AuthService service = new AuthService(new ProfileRepository(), new BCryptPasswordEncoder(), null,
                new UnavailableStorage());

        AuthMe profile = service.me(user);

        assertNull(profile.avatarUrl());
        assertEquals(List.of("ADMIN"), profile.roles());
        assertEquals(List.of("system:admin"), profile.permissions());
    }

    private static final class ProfileRepository extends AuthRepository {
        private ProfileRepository() {
            super(null);
        }

        @Override
        public List<String> findRoles(long userId, long tenantId) {
            return List.of("ADMIN");
        }

        @Override
        public List<String> findPermissions(long userId, long tenantId) {
            return List.of("system:admin");
        }
    }

    private static final class UnavailableStorage extends MinioStorageService {
        private UnavailableStorage() {
            super(properties());
        }

        @Override
        public String presignedUrl(String objectKey) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File storage service unavailable");
        }

        private static MinioStorageProperties properties() {
            MinioStorageProperties properties = new MinioStorageProperties();
            properties.setEndpoint("http://127.0.0.1:9000");
            properties.setAccessKey("test-access-key");
            properties.setSecretKey("test-secret-key");
            properties.setBucket("test-bucket");
            properties.setPresignedExpirySeconds(3600);
            return properties;
        }
    }
}

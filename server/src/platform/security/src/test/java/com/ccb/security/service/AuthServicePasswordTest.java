package com.ccb.security.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.security.model.ChangePasswordCommand;
import com.ccb.security.repository.AuthRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServicePasswordTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void rejectsWrongOldPasswordWithoutUpdating() {
        RecordingRepository repository = new RecordingRepository(encoder.encode("old-pass"));
        AuthService service = service(repository);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changePassword(repository.user, new ChangePasswordCommand("wrong", "new-pass", "new-pass")));

        assertEquals("原密码不正确", exception.getMessage());
        assertEquals(0, repository.updateCount);
    }

    @Test
    void updatesPasswordWithEncodedHashForAuthenticatedTenantUser() {
        RecordingRepository repository = new RecordingRepository(encoder.encode("old-pass"));
        AuthService service = service(repository);

        service.changePassword(repository.user, new ChangePasswordCommand("old-pass", "new-pass", "new-pass"));

        assertEquals(1, repository.updateCount);
        assertEquals(1, repository.updatedUserId);
        assertEquals(1, repository.updatedTenantId);
        assertTrue(encoder.matches("new-pass", repository.updatedHash));
    }

    private AuthService service(RecordingRepository repository) {
        return new AuthService(repository, encoder, null, null);
    }

    private static final class RecordingRepository extends AuthRepository {
        private final AuthUser user;
        private int updateCount;
        private long updatedUserId;
        private long updatedTenantId;
        private String updatedHash;

        private RecordingRepository(String oldHash) {
            super(null);
            user = new AuthUser(1, 1, "admin", oldHash, "管理员", 1, true);
        }

        @Override
        public Optional<AuthUser> findById(long id, long tenantId) {
            return id == user.id() && tenantId == user.tenantId() ? Optional.of(user) : Optional.empty();
        }

        @Override
        public int updatePassword(long userId, long tenantId, String passwordHash) {
            updateCount++;
            updatedUserId = userId;
            updatedTenantId = tenantId;
            updatedHash = passwordHash;
            return 1;
        }
    }
}

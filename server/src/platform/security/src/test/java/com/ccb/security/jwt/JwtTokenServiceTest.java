package com.ccb.security.jwt;

import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {
    private final JwtTokenService service = new JwtTokenService(
            "a-development-secret-with-at-least-32-bytes", 900_000, 604_800_000);
    private final AuthUser user = new AuthUser(7, 1, "tester", "hash", "Tester", 1, true);

    @Test
    void accessAndRefreshTokensHaveDifferentTypes() {
        var pair = service.issue(user);

        assertEquals("access", service.parseAccess(pair.accessToken()).get("token_type"));
        assertEquals("refresh", service.parseRefresh(pair.refreshToken()).get("token_type"));
        assertThrows(Exception.class, () -> service.parseAccess(pair.refreshToken()));
    }

    @Test
    void revokedRefreshTokenCannotBeUsed() {
        var pair = service.issue(user);
        service.revokeRefresh(pair.refreshToken());

        assertThrows(Exception.class, () -> service.parseRefresh(pair.refreshToken()));
    }
}

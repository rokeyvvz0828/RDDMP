package com.ccb.security.jwt;

import com.ccb.security.model.AuthUser;
import com.ccb.security.model.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtTokenService {
    private final SecretKey key;
    private final long accessTtlMillis;
    private final long refreshTtlMillis;
    private final JdbcTemplate jdbc;
    private final Set<String> revokedRefreshTokens = ConcurrentHashMap.newKeySet();

    public JwtTokenService(String secret, long accessTtlMillis, long refreshTtlMillis) {
        this(secret, accessTtlMillis, refreshTtlMillis, null);
    }

    @Autowired
    public JwtTokenService(@Value("${ccb.security.jwt.secret}") String secret,
                           @Value("${ccb.security.jwt.access-ttl-millis:900000}") long accessTtlMillis,
                           @Value("${ccb.security.jwt.refresh-ttl-millis:604800000}") long refreshTtlMillis,
                           JdbcTemplate jdbc) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = accessTtlMillis;
        this.refreshTtlMillis = refreshTtlMillis;
        this.jdbc = jdbc;
    }

    public TokenPair issue(AuthUser user) {
        Instant now = Instant.now();
        long accessTtl = ttl("security.jwt.access-ttl-millis", accessTtlMillis);
        long refreshTtl = ttl("security.jwt.refresh-ttl-millis", refreshTtlMillis);
        return new TokenPair(create(user, now, accessTtl, "access"),
                create(user, now, refreshTtl, "refresh"),
                accessTtl / 1000, refreshTtl / 1000, "Bearer");
    }

    public Claims parseAccess(String token) {
        Claims claims = parse(token);
        if (!"access".equals(claims.get("token_type", String.class))) {
            throw new JwtException("not an access token");
        }
        return claims;
    }

    public Claims parseRefresh(String token) {
        if (revokedRefreshTokens.contains(token)) {
            throw new JwtException("refresh token revoked");
        }
        Claims claims = parse(token);
        if (!"refresh".equals(claims.get("token_type", String.class))) {
            throw new JwtException("not a refresh token");
        }
        return claims;
    }

    public void revokeRefresh(String token) {
        if (token != null && !token.isBlank()) {
            revokedRefreshTokens.add(token);
        }
    }

    private long ttl(String key, long fallback) {
        if (jdbc == null) return fallback;
        try {
            String value = jdbc.query("SELECT config_value FROM sys_config WHERE tenant_id = 1 AND config_key = ? AND deleted = 0",
                    rs -> rs.next() ? rs.getString("config_value") : null, key);
            long parsed = value == null ? fallback : Long.parseLong(value);
            return parsed > 0 ? parsed : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String create(AuthUser user, Instant now, long ttlMillis, String tokenType) {
        return Jwts.builder().subject(Long.toString(user.id())).id(UUID.randomUUID().toString())
                .claim("tenant_id", user.tenantId()).claim("token_type", tokenType)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key).compact();
    }

    private Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("token is blank");
        }
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}

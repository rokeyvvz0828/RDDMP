package com.ccb.security.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.jwt.JwtTokenService;
import com.ccb.security.model.AuthMe;
import com.ccb.security.model.AuthUser;
import com.ccb.security.model.LoginCommand;
import com.ccb.security.model.RouteNode;
import com.ccb.security.model.TokenPair;
import com.ccb.security.repository.AuthRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final MinioStorageService storage;

    public AuthService(AuthRepository repository, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
                       MinioStorageService storage) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.storage = storage;
    }

    public TokenPair login(LoginCommand command, HttpServletRequest request) {
        AuthUser user = repository.findByUsername(command.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(command.password(), user.passwordHash())) {
            repository.recordLogin(command.username(), false, "invalid credentials", clientIp(request), userAgent(request));
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }
        if (!user.enabled()) {
            repository.recordLogin(command.username(), false, "account disabled", clientIp(request), userAgent(request));
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Account is disabled");
        }
        repository.updateLastLogin(user.id());
        repository.recordLogin(command.username(), true, null, clientIp(request), userAgent(request));
        return tokenService.issue(user);
    }

    public TokenPair refresh(String refreshToken) {
        try {
            Claims claims = tokenService.parseRefresh(refreshToken);
            AuthUser user = currentUser(claims);
            tokenService.revokeRefresh(refreshToken);
            return tokenService.issue(user);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    public void logout(String refreshToken) { tokenService.revokeRefresh(refreshToken); }

    public AuthMe me(AuthUser user) {
        return new AuthMe(user.id(), user.tenantId(), user.username(), user.displayName(), user.orgId(),
                user.orgName(), storage.presignedUrl(user.avatarObjectKey()),
                repository.findRoles(user.id(), user.tenantId()), repository.findPermissions(user.id(), user.tenantId()));
    }

    public List<RouteNode> routes(AuthUser user) {
        List<RouteNode> flat = repository.findRoutes(user.id(), user.tenantId());
        List<RouteNode> roots = new ArrayList<>();
        for (RouteNode node : flat) if (node.parentId() == 0) roots.add(node);
        return roots.stream().map(root -> attachChildren(root, flat)).toList();
    }

    public AuthUser currentUser(Claims claims) {
        long tenantId = claims.get("tenant_id", Number.class).longValue();
        long userId = Long.parseLong(claims.getSubject());
        return repository.findById(userId, tenantId).filter(AuthUser::enabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Account is disabled or missing"));
    }

    private RouteNode attachChildren(RouteNode parent, List<RouteNode> flat) {
        List<RouteNode> children = flat.stream().filter(node -> node.parentId() == parent.id())
                .map(node -> attachChildren(node, flat)).toList();
        return new RouteNode(parent.id(), parent.parentId(), parent.menuType(), parent.menuName(), parent.routeName(),
                parent.routePath(), parent.componentPath(), parent.permissionCode(), parent.icon(), parent.sortNo(), children);
    }

    private String clientIp(HttpServletRequest request) { return request == null ? null : request.getRemoteAddr(); }
    private String userAgent(HttpServletRequest request) { return request == null ? null : request.getHeader("User-Agent"); }
}
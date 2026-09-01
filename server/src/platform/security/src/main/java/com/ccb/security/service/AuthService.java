package com.ccb.security.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.jwt.JwtTokenService;
import com.ccb.security.model.AuthMe;
import com.ccb.security.model.AuthUser;
import com.ccb.security.model.ChangePasswordCommand;
import com.ccb.security.model.LoginCommand;
import com.ccb.security.model.RouteNode;
import com.ccb.security.model.TokenPair;
import com.ccb.security.repository.AuthRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    public void changePassword(AuthUser principal, ChangePasswordCommand command) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (!command.newPassword().equals(command.confirmPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "两次输入的新密码不一致");
        }

        AuthUser current = repository.findById(principal.id(), principal.tenantId())
                .filter(AuthUser::enabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用"));
        if (!passwordEncoder.matches(command.oldPassword(), current.passwordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        if (passwordEncoder.matches(command.newPassword(), current.passwordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        String passwordHash = passwordEncoder.encode(command.newPassword());
        if (repository.updatePassword(current.id(), current.tenantId(), passwordHash) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码保存失败，请稍后重试");
        }
    }

    public AuthMe updateOwnAvatar(AuthUser principal, MultipartFile file) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        validateImage(file);
        String oldKey = repository.findAvatarObjectKey(principal.id(), principal.tenantId());
        String objectKey = "avatars/" + principal.tenantId() + "/" + principal.id() + "/" + UUID.randomUUID() + extension(file.getContentType());
        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (java.io.IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
        }
        if (repository.updateAvatarObjectKey(principal.id(), principal.tenantId(), objectKey) == 0) {
            storage.delete(objectKey);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像保存失败，请稍后重试");
        }
        if (oldKey != null && !oldKey.isBlank()) storage.delete(oldKey);
        AuthUser updated = repository.findById(principal.id(), principal.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用"));
        return me(updated);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择头像文件");
        if (file.getSize() > 2 * 1024 * 1024) throw new BusinessException(ErrorCode.BAD_REQUEST, "头像不能超过 2MB");
        String type = file.getContentType();
        if (type == null || !Set.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(type.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像仅支持 JPG、PNG、GIF 或 WebP");
        }
    }

    private String extension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    public AuthMe me(AuthUser user) {
        return new AuthMe(user.id(), user.tenantId(), user.username(), user.displayName(), user.orgId(),
                user.orgName(), avatarUrl(user), roles(user), permissions(user));
    }

    public List<String> roles(AuthUser user) {
        return repository.findRoles(user.id(), user.tenantId());
    }

    public List<String> permissions(AuthUser user) {
        return repository.findPermissions(user.id(), user.tenantId());
    }

    private String avatarUrl(AuthUser user) {
        if (storage == null) return null;
        try {
            return storage.presignedUrl(user.avatarObjectKey());
        } catch (BusinessException exception) {
            return null;
        }
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

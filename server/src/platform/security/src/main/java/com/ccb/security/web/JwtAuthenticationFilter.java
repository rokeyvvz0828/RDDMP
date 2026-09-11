package com.ccb.security.web;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.jwt.JwtTokenService;
import com.ccb.security.model.AuthUser;
import com.ccb.security.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern PROJECT_API_PATH = Pattern.compile("^/api/project/(\\d+)(?:/.*)?$");
    private final JwtTokenService tokenService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenService tokenService, AuthService authService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/api/auth/login".equals(path) || "/api/auth/refresh".equals(path)
                || "/actuator/health".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = tokenService.parseAccess(header.substring(7));
                AuthUser user = authService.currentUser(claims);
                Long projectId = authService.resolveProjectId(request, user);
                validateProjectPath(request, projectId);
                ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authService.roles(user).forEach(role ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                authService.permissions(user, projectId).forEach(permission ->
                        authorities.add(new SimpleGrantedAuthority(permission)));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            } catch (BusinessException exception) {
                SecurityContextHolder.clearContext();
                if (exception.code() == ErrorCode.BAD_REQUEST || exception.code() == ErrorCode.FORBIDDEN) {
                    writeProjectContextError(response, exception);
                    return;
                }
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void validateProjectPath(HttpServletRequest request, Long projectId) {
        Matcher matcher = PROJECT_API_PATH.matcher(request.getServletPath());
        if (!matcher.matches()) return;
        if (projectId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目请求缺少X-Project-Id");
        if (Long.parseLong(matcher.group(1)) != projectId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求项目与当前项目不一致");
        }
    }

    private void writeProjectContextError(HttpServletResponse response, BusinessException exception) throws IOException {
        response.setStatus(exception.code() == ErrorCode.FORBIDDEN
                ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.failure(exception.code(), exception.getMessage(), TraceId.getOrCreate()));
    }
}

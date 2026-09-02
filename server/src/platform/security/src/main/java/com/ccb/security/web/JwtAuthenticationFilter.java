package com.ccb.security.web;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.jwt.JwtTokenService;
import com.ccb.security.model.AuthUser;
import com.ccb.security.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokenService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtTokenService tokenService, AuthService authService) {
        this.tokenService = tokenService;
        this.authService = authService;
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
                ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authService.roles(user).forEach(role ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                authService.permissions(user).forEach(permission ->
                        authorities.add(new SimpleGrantedAuthority(permission)));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            } catch (JwtException | IllegalArgumentException | BusinessException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}

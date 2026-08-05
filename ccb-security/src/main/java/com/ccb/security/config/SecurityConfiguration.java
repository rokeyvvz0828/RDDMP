package com.ccb.security.config;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.jwt.JwtTokenService;
import com.ccb.security.service.AuthService;
import com.ccb.security.web.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService unusedUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Database-backed authentication is used");
        };
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenService tokenService, AuthService authService) {
        return new JwtAuthenticationFilter(tokenService, authService);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(jsonError(objectMapper, 40100, "Authentication required"))
                        .accessDeniedHandler(jsonDenied(objectMapper)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint jsonError(ObjectMapper mapper, int code, String message) {
        return (request, response, exception) ->
                writeError(mapper, response, HttpServletResponse.SC_UNAUTHORIZED, code, message);
    }

    private AccessDeniedHandler jsonDenied(ObjectMapper mapper) {
        return (request, response, exception) ->
                writeError(mapper, response, HttpServletResponse.SC_FORBIDDEN, 40300, "Forbidden");
    }

    private void writeError(ObjectMapper mapper, HttpServletResponse response, int status, int code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), ApiResponse.failure(code, message, TraceId.getOrCreate()));
    }
}
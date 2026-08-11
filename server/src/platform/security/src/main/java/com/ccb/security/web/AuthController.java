package com.ccb.security.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthMe;
import com.ccb.security.model.AuthUser;
import com.ccb.security.model.ChangePasswordCommand;
import com.ccb.security.model.LoginCommand;
import com.ccb.security.model.LogoutCommand;
import com.ccb.security.model.RefreshCommand;
import com.ccb.security.model.RouteNode;
import com.ccb.security.model.TokenPair;
import com.ccb.security.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenPair> login(@Valid @RequestBody LoginCommand command, HttpServletRequest request) {
        return ApiResponse.success(authService.login(command, request), TraceId.getOrCreate());
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshCommand command) {
        return ApiResponse.success(authService.refresh(command.refreshToken()), TraceId.getOrCreate());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutCommand command) {
        authService.logout(command == null ? null : command.refreshToken());
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody ChangePasswordCommand command) {
        authService.changePassword(user, command);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/me")
    public ApiResponse<AuthMe> me(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(authService.me(user), TraceId.getOrCreate());
    }

    @GetMapping("/routes")
    public ApiResponse<List<RouteNode>> routes(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(authService.routes(user), TraceId.getOrCreate());
    }

    @GetMapping("/permission-check")
    @PreAuthorize("hasAuthority('system:admin')")
    public ApiResponse<String> permissionCheck() {
        return ApiResponse.success("allowed", TraceId.getOrCreate());
    }
}

/*
 * 文件：server/src/platform/system/src/main/java/com/ccb/system/web/UserProfileController.java
 * 说明：人员档案批量查询的只读接口，供已登录用户在业务页面展示人员信息。
 * 用途：按标识批量返回姓名、账号、手机号、组织、头像与角色，并回报未命中的标识。
 * 作者：Codex
 */
package com.ccb.system.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.service.UserProfileService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/platform/user-profiles")
@PreAuthorize("isAuthenticated()")
public class UserProfileController {
    /** 单次查询上限；超过时拒绝而不是静默截断，避免调用方误以为拿到了完整结果。 */
    public static final int MAX_USER_IDS = 50;

    private final UserProfileService service;
    private final MinioStorageService storage;

    public UserProfileController(UserProfileService service, MinioStorageService storage) {
        this.service = service;
        this.storage = storage;
    }

    public record QueryRequest(List<Long> userIds) {
    }

    public record RoleResponse(long id, String code, String name) {
        static RoleResponse from(UserProfileService.RoleItem role) {
            return new RoleResponse(role.id(), role.code(), role.name());
        }
    }

    /** 响应字段即白名单：不含口令、租户、删除标记、登录时间与对象存储 key。 */
    public record ProfileResponse(long id, String username, String displayName, String mobilePhone,
                                  String orgName, String avatarUrl, List<RoleResponse> roles, int status) {
        static ProfileResponse from(UserProfileService.Row row, String avatarUrl) {
            return new ProfileResponse(row.id(), row.username(), row.displayName(), row.mobilePhone(),
                    row.orgName(), avatarUrl, row.roles().stream().map(RoleResponse::from).toList(), row.status());
        }
    }

    public record QueryResponse(List<ProfileResponse> profiles, List<Long> missingIds) {
    }

    @PostMapping("/query")
    public ApiResponse<QueryResponse> query(@AuthenticationPrincipal AuthUser actor,
                                            @RequestBody QueryRequest request) {
        List<Long> requested = normalize(request == null ? null : request.userIds());
        if (requested.size() > MAX_USER_IDS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多查询 " + MAX_USER_IDS + " 个人员");
        }
        if (requested.isEmpty()) {
            return ApiResponse.success(new QueryResponse(List.of(), List.of()), TraceId.getOrCreate());
        }
        List<UserProfileService.Row> rows = service.query(actor, requested);
        List<ProfileResponse> profiles = rows.stream()
                .map(row -> ProfileResponse.from(row, avatarUrl(row)))
                .toList();
        Set<Long> found = rows.stream().map(UserProfileService.Row::id).collect(Collectors.toSet());
        List<Long> missingIds = requested.stream().filter(id -> !found.contains(id)).toList();
        return ApiResponse.success(new QueryResponse(profiles, missingIds), TraceId.getOrCreate());
    }

    private String avatarUrl(UserProfileService.Row row) {
        String objectKey = row.avatarObjectKey();
        return objectKey == null || objectKey.isBlank() ? null : storage.presignedUrl(objectKey);
    }

    private List<Long> normalize(List<Long> userIds) {
        if (userIds == null) {
            return List.of();
        }
        return userIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}

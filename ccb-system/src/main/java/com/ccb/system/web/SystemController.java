package com.ccb.system.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.SystemPage;
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import com.ccb.system.service.SystemService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasAuthority('system:access')")
public class SystemController {
    private final SystemService service;
    private final OrganizationService organizationService;

    public SystemController(SystemService service, OrganizationService organizationService) {
        this.service = service;
        this.organizationService = organizationService;
    }

    @GetMapping("/orgs/tree")
    public ApiResponse<List<OrgTreeNode>> orgTree(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(organizationService.tree(user), TraceId.getOrCreate());
    }

    @GetMapping("/{resource}")
    public ApiResponse<SystemPage<Map<String, Object>>> list(@PathVariable String resource,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "20") long size,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Long orgId,
                                                               @RequestParam(required = false) Long categoryId,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(resource, new PageQuery(page, size), keyword, orgId, categoryId, user), TraceId.getOrCreate());
    }

    @PostMapping("/{resource}")
    public ApiResponse<Map<String, Object>> create(@PathVariable String resource, @RequestBody Map<String, Object> input,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(resource, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/{resource}/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String resource, @PathVariable long id,
                                                    @RequestBody Map<String, Object> input,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(resource, id, input, user), TraceId.getOrCreate());
    }

    @PatchMapping("/{resource}/{id}/status")
    public ApiResponse<Void> status(@PathVariable String resource, @PathVariable long id,
                                    @RequestParam int value, @AuthenticationPrincipal AuthUser user) {
        service.updateStatus(resource, id, value, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @DeleteMapping("/{resource}/{id}")
    public ApiResponse<Void> delete(@PathVariable String resource, @PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.delete(resource, id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/roles/options")
    public ApiResponse<List<Map<String, Object>>> roleOptions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.roleOptions(user), TraceId.getOrCreate());
    }

    @GetMapping("/roles/permission-catalog")
    public ApiResponse<Map<String, Object>> permissionCatalog(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.permissionCatalog(user), TraceId.getOrCreate());
    }

    @GetMapping("/roles/{roleId}/permissions")
    public ApiResponse<Map<String, Object>> rolePermissions(@PathVariable long roleId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.rolePermissions(roleId, user), TraceId.getOrCreate());
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<Void> saveRolePermissions(@PathVariable long roleId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        service.saveRolePermissions(roleId, (List<?>) input.get("permissionIds"), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/users/{userId}/roles")
    public ApiResponse<List<Long>> userRoles(@PathVariable long userId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.userRoleIds(userId, user), TraceId.getOrCreate());
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<Void> saveUserRoles(@PathVariable long userId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        service.saveUserRoles(userId, (List<?>) input.get("roleIds"), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping(value = "/users/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadAvatar(@PathVariable long id, @RequestPart("file") MultipartFile file,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.uploadAvatar(id, file, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/users/{id}/avatar")
    public ApiResponse<Map<String, Object>> deleteAvatar(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.clearAvatar(id, user), TraceId.getOrCreate());
    }
}
/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestPlanController.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.web;

// 关键逻辑：控制器仅适配 HTTP 参数与细粒度权限；租户、测试大类、项目和实体边界统一由领域服务校验。

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.testmanagement.plan.TestPlanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 测试方案 API：查询对所有方案阅读者开放，节点和版本写操作由细粒度权限保护。 */
@RestController
@RequestMapping("/api/test-management/plans/{domain}")
public class TestPlanController {
    private final TestPlanService service;

    public TestPlanController(TestPlanService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans')")
    public ApiResponse<Map<String, Object>> tree(
            @PathVariable String domain,
            @RequestParam long projectId,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.tree(domain, projectId, user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans')")
    public ApiResponse<PageResult<Map<String, Object>>> plans(
            @PathVariable String domain,
            @RequestParam long projectId,
            @RequestParam String nodeType,
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(required = false) Long specialNodeId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.plans(domain, projectId, node(nodeType, physicalSubsystemId, specialNodeId), new PageQuery(page, Math.min(size, 100)), user));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans')")
    public ApiResponse<Map<String, Object>> current(
            @PathVariable String domain,
            @RequestParam long projectId,
            @RequestParam String nodeType,
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(required = false) Long specialNodeId,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.current(domain, projectId, node(nodeType, physicalSubsystemId, specialNodeId), user));
    }

    @GetMapping("/{planId}/versions")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans')")
    public ApiResponse<List<Map<String, Object>>> versions(
            @PathVariable String domain,
            @PathVariable long planId,
            @RequestParam long projectId,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.versions(domain, projectId, planId, user));
    }

    @PostMapping("/special-nodes")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans:create')")
    public ApiResponse<Map<String, Object>> createSpecial(
            @PathVariable String domain,
            @RequestParam long projectId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.createSpecial(domain, projectId, body, user));
    }

    @PutMapping("/special-nodes/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans:update')")
    public ApiResponse<Map<String, Object>> updateSpecial(
            @PathVariable String domain,
            @PathVariable long id,
            @RequestParam long projectId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.updateSpecial(domain, projectId, id, body, user));
    }

    @DeleteMapping("/special-nodes/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans:delete')")
    public ApiResponse<Void> deleteSpecial(
            @PathVariable String domain,
            @PathVariable long id,
            @RequestParam long projectId,
            @AuthenticationPrincipal AuthUser user) {
        service.deleteSpecial(domain, projectId, id, user);
        return ok(null);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans:create')")
    public ApiResponse<Map<String, Object>> upload(
            @PathVariable String domain,
            @RequestParam long projectId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.upload(domain, projectId, null, body, user));
    }

    @PostMapping("/{planId}/versions")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans:update')")
    public ApiResponse<Map<String, Object>> uploadVersion(
            @PathVariable String domain,
            @PathVariable long planId,
            @RequestParam long projectId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.upload(domain, projectId, planId, body, user));
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':plans:delete')")
    public ApiResponse<Void> deletePlan(
            @PathVariable String domain,
            @PathVariable long planId,
            @RequestParam long projectId,
            @AuthenticationPrincipal AuthUser user) {
        service.deletePlan(domain, projectId, planId, user);
        return ok(null);
    }

    private Map<String, Object> node(String type, Long physicalSubsystemId, Long specialNodeId) {
        if (physicalSubsystemId != null) {
            return Map.of("node_type", type, "physical_subsystem_id", physicalSubsystemId);
        }
        if (specialNodeId != null) {
            return Map.of("node_type", type, "special_node_id", specialNodeId);
        }
        return Map.of("node_type", type);
    }

    private <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }
}

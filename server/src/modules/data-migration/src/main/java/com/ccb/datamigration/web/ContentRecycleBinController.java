package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ContentRecycleBinService;
import com.ccb.security.model.AuthUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 统一内容回收站控制器（REQ-20260831-050）：聚合各内容表软删记录，按内容类型筛选、恢复与彻底删除。
 *
 * <p>T26 扩展：列表接口新增 {@code page}/{@code size} 参数，返回 {@link PageResult}，与会议/汇报/问题
 * 单模块列表保持一致的分页契约；合并后按业务编号 {@code asset_code} 字典序全局排序，跨类型同序。
 * 恢复/删除请求体携带内容类型以分发到对应服务，管理员权限校验。
 */
@RestController
@RequestMapping("/api/data-migration/recycle-bin")
@PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
public class ContentRecycleBinController {
    private final ContentRecycleBinService service;

    public ContentRecycleBinController(ContentRecycleBinService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(required = false) List<String> contentTypes,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        Set<String> filter = contentTypes == null ? Set.of() : new LinkedHashSet<>(contentTypes);
        return ApiResponse.success(service.list(filter, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/{type}/{id:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String type, @PathVariable long id,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(type, id, user), TraceId.getOrCreate());
    }

    @PostMapping("/restore")
    public ApiResponse<Void> restore(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        service.restore(dispatchType(body), dispatchIds(body), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/purge")
    public ApiResponse<Void> purge(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        service.purge(dispatchType(body), dispatchIds(body), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    private static String dispatchType(Map<String, Object> body) {
        Object type = body == null ? null : body.get("type");
        return type == null ? null : String.valueOf(type);
    }

    private static List<Long> dispatchIds(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        if (!(raw instanceof List<?> list)) throw new com.ccb.common.exception.BusinessException(
                com.ccb.common.exception.ErrorCode.BAD_REQUEST, "ids is required");
        return list.stream().map(v -> ((Number) v).longValue()).toList();
    }
}

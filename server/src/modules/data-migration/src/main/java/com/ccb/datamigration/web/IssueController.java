package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.IssueService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 问题清单 Controller。
 * 路由前缀 /api/data-migration/issues
 */
@RestController
@RequestMapping("/api/data-migration/issues")
public class IssueController {
    private final IssueService service;

    public IssueController(IssueService service) {
        this.service = service;
    }

    /**
     * 分页查询问题清单列表
     */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String issueSource,
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) String frequency,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, granularity, systemCode, issueSource, defectType, frequency, keyword, page, size, user), TraceId.getOrCreate());
    }

    /**
     * 获取单条问题详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.findById(id, user), TraceId.getOrCreate());
    }

    /**
     * 新增问题
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    /**
     * 更新问题
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, body, user), TraceId.getOrCreate());
    }

    /**
     * 批量删除
     */
    @DeleteMapping
    public ApiResponse<Void> delete(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.delete(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 回收站列表
     */
    @GetMapping("/recycle-bin")
    public ApiResponse<PageResult<Map<String, Object>>> recycleBin(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.recycleBinList(projectId, keyword, page, size, user), TraceId.getOrCreate());
    }

    /**
     * 恢复
     */
    @PostMapping("/restore")
    public ApiResponse<Void> restore(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.restore(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 彻底删除
     */
    @DeleteMapping("/purge")
    public ApiResponse<Void> purge(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.purge(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 清空回收站
     */
    @DeleteMapping("/purge-all")
    public ApiResponse<Void> purgeAll(@AuthenticationPrincipal AuthUser user) {
        service.purgeAll(user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    // ============ 关联数据查询 ============

    /**
     * 获取项目选项
     */
    @GetMapping("/options/projects")
    public ApiResponse<List<Map<String, Object>>> projectOptions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getProjectOptions(user), TraceId.getOrCreate());
    }

    /**
     * 获取系统选项（根据项目）
     */
    @GetMapping("/options/systems")
    public ApiResponse<List<Map<String, Object>>> systemOptions(
            @RequestParam(required = false) Long projectId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getSystemOptions(projectId, user), TraceId.getOrCreate());
    }

    /**
     * 获取系统名称（根据系统编号）
     */
    @GetMapping("/options/system-name")
    public ApiResponse<String> systemName(
            @RequestParam String systemCode, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getSystemName(systemCode, user), TraceId.getOrCreate());
    }

    /**
     * 获取会议纪要选项（根据项目）
     */
    @GetMapping("/options/meetings")
    public ApiResponse<List<Map<String, Object>>> meetingOptions(
            @RequestParam(required = false) Long projectId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getMeetingOptions(projectId, user), TraceId.getOrCreate());
    }

    /**
     * 获取目标表选项（根据项目）
     */
    @GetMapping("/options/target-tables")
    public ApiResponse<List<Map<String, Object>>> targetTableOptions(
            @RequestParam(required = false) Long projectId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getTargetTableOptions(projectId, user), TraceId.getOrCreate());
    }

    /**
     * 获取目标表字段选项（根据表ID）
     */
    @GetMapping("/options/target-fields")
    public ApiResponse<List<Map<String, Object>>> targetFieldOptions(
            @RequestParam Long tableId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getTargetFieldOptions(tableId, user), TraceId.getOrCreate());
    }
}

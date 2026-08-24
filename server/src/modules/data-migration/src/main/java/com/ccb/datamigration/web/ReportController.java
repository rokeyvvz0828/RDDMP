package com.ccb.datamigration.web;

import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ReportService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 汇报材料专属控制器。
 * 提供分页查询、单条/批量上传、编辑、逻辑删除、下载和回收站管理。
 */
@RestController
@RequestMapping("/api/data-migration/reports")
@PreAuthorize("hasAnyAuthority('data-migration:content:reports','data-migration:write','data-migration:manage','system:admin')")
public class ReportController {

    private final ReportService reportService;
    private final AttachmentGateway attachmentGateway;

    public ReportController(ReportService reportService, AttachmentGateway attachmentGateway) {
        this.reportService = reportService;
        this.attachmentGateway = attachmentGateway;
    }

    /**
     * 1. 分页查询汇报材料列表
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String reportPeriod,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(reportService.list(projectId, reportPeriod, keyword, page, size, user), TraceId.getOrCreate());
    }

    /**
     * 2. 单条上传汇报材料
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam long projectId,
            @RequestParam String reportPeriod,
            @RequestParam String reportName,
            @RequestParam(required = false) String reportDate,
            @RequestParam String keywords,
            @RequestParam Long attachmentId,
            @RequestParam(required = false) String checksumMd5,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(reportService.upload(projectId, reportPeriod, reportName, reportDate, keywords, attachmentId, checksumMd5, user), TraceId.getOrCreate());
    }

    /**
     * 3. 批量上传汇报材料
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<List<Map<String, Object>>> batchUpload(
            @RequestParam long projectId,
            @RequestParam String reportPeriod,
            @RequestParam List<Long> attachmentIds,
            @RequestParam List<String> checksumMd5s,
            @AuthenticationPrincipal AuthUser user) {
        // 获取附件信息
        List<AttachmentItem> attachments = attachmentIds.stream()
            .map(id -> attachmentGateway.get(id, user))
            .toList();
        return ApiResponse.success(
            reportService.batchUpload(projectId, reportPeriod, attachments, checksumMd5s, user),
            TraceId.getOrCreate());
    }

    /**
     * 4. 编辑汇报材料
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable long id,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String reportPeriod,
            @RequestParam(required = false) String reportName,
            @RequestParam(required = false) String reportDate,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) Long attachmentId,
            @RequestParam(required = false) String checksumMd5,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(reportService.update(id, projectId, reportPeriod, reportName, reportDate, keywords, attachmentId, checksumMd5, user), TraceId.getOrCreate());
    }

    /**
     * 5. 逻辑删除汇报材料
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(
            @RequestBody List<Long> ids,
            @AuthenticationPrincipal AuthUser user) {
        reportService.delete(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 6. 下载汇报材料
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<String> download(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(reportService.download(id, user), TraceId.getOrCreate());
    }

    /**
     * 7. 回收站列表
     */
    @GetMapping("/recycle-bin")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<PageResult<Map<String, Object>>> recycleBinList(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String reportPeriod,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(reportService.recycleBinList(projectId, reportPeriod, keyword, page, size, user), TraceId.getOrCreate());
    }

    /**
     * 8. 恢复汇报材料
     */
    @PostMapping("/recycle-bin/restore")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<Void> restore(
            @RequestBody List<Long> ids,
            @AuthenticationPrincipal AuthUser user) {
        reportService.restore(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 9. 确认清理汇报材料
     */
    @PostMapping("/recycle-bin/purge")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<Void> purge(
            @RequestBody List<Long> ids,
            @AuthenticationPrincipal AuthUser user) {
        reportService.purge(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 检查MD5是否可用
     */
    @GetMapping("/check-md5")
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Boolean>> checkMd5(
            @RequestParam String md5,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(Map.of("available", reportService.isMd5Available(md5, user)), TraceId.getOrCreate());
    }

    /**
     * 获取项目选项列表
     */
    @GetMapping("/project-options")
    @PreAuthorize("hasAnyAuthority('data-migration:content:reports','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<List<Map<String, Object>>> getProjectOptions(
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(reportService.getProjectOptions(user), TraceId.getOrCreate());
    }
}

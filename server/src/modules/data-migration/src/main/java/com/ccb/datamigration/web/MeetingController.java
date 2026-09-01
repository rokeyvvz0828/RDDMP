package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.MeetingService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会议纪要 Controller。
 * 路由前缀 /api/data-migration/meetings
 */
@RestController
@RequestMapping("/api/data-migration/meetings")
@PreAuthorize("hasAnyAuthority('data-migration:content:meetings','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class MeetingController {
    private final MeetingService service;

    public MeetingController(MeetingService service) {
        this.service = service;
    }

    /**
     * 分页查询会议纪要列表
     */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String meetingSource,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, meetingSource, granularity, systemId, keyword, page, size, user), TraceId.getOrCreate());
    }

    /**
     * 获取单条会议纪要详情
     */
    @GetMapping("/{meetingId:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long meetingId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.findById(meetingId, user), TraceId.getOrCreate());
    }

    /**
     * 创建会议纪要
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:meetings:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    /**
     * 更新会议纪要
     */
    @PutMapping("/{meetingId:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:meetings:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long meetingId, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(meetingId, body, user), TraceId.getOrCreate());
    }

    /**
     * 批量删除会议纪要（逻辑删除）
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:meetings:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(@RequestBody List<Long> meetingIds, @AuthenticationPrincipal AuthUser user) {
        service.delete(meetingIds, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 回收站入口已收敛到统一回收站（{@code /api/data-migration/recycle-bin}）。
     *
     * <p>T26 下线旧前端入口：{@code GET /meetings/recycle-bin}、{@code POST /meetings/restore}、
     * {@code DELETE /meetings/purge}、{@code DELETE /meetings/purge-all}。内部 {@code MeetingService#countRecycleBin}/
     * {@code fetchRecycleBinPage} 与 {@code restore}/{@code purge}/{@code purgeAll} 保留供 {@code MeetingRecycleBinSource} SPI 调用，
     * 业务规则（管理员校验、CONFLICT、关系级联、审计）不变。T27 为所有数字型 {@code @PathVariable} 加 {@code \\d+}
     * 正则约束，使上述已下线的字面路径（如 {@code /meetings/recycle-bin}）不再被 {@code /{meetingId}} 详情路由吞掉而
     * 返回 500，改为正确 404。附件级回收站（{@code /attachments/recycle-bin*}）
     * 仍保留在会议页，受 {@code uk_dm_meeting_att_active} 与父会议未删前置校验约束，不并入统一信封。
     */

    // ============ 附件管理 ============

    /**
     * 获取会议纪要的附件列表
     */
    @GetMapping("/{meetingId:\\d+}/attachments")
    public ApiResponse<List<Map<String, Object>>> getAttachments(@PathVariable long meetingId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getMeetingAttachments(meetingId, user), TraceId.getOrCreate());
    }

    /**
     * 软删除附件（移入回收站）
     */
    @DeleteMapping("/{meetingId:\\d+}/attachments/{attachmentId:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:meetings:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> deleteAttachment(@PathVariable long meetingId, @PathVariable long attachmentId, @AuthenticationPrincipal AuthUser user) {
        service.deleteAttachment(meetingId, attachmentId, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 获取附件回收站列表
     */
    @GetMapping("/{meetingId:\\d+}/attachments/recycle-bin")
    public ApiResponse<List<Map<String, Object>>> getAttachmentRecycleBin(@PathVariable long meetingId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getAttachmentRecycleBin(meetingId, user), TraceId.getOrCreate());
    }

    /**
     * 恢复附件
     */
    @PostMapping("/{meetingId:\\d+}/attachments/{attachmentId:\\d+}/restore")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<Void> restoreAttachment(@PathVariable long meetingId, @PathVariable long attachmentId, @AuthenticationPrincipal AuthUser user) {
        service.restoreAttachment(meetingId, attachmentId, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 全局附件回收站列表（跨会议）
     */
    @GetMapping("/attachments/recycle-bin")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<PageResult<Map<String, Object>>> attachmentRecycleBin(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.attachmentRecycleBinList(projectId, keyword, page, size, user), TraceId.getOrCreate());
    }

    /**
     * 批量恢复附件
     */
    @PostMapping("/attachments/restore")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<Void> restoreAttachments(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.restoreAttachments(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 彻底删除附件
     */
    @DeleteMapping("/attachments/purge")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<Void> purgeAttachments(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.purgeAttachments(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    /**
     * 清空附件回收站
     */
    @DeleteMapping("/attachments/purge-all")
    @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')")
    public ApiResponse<Void> purgeAllAttachments(@AuthenticationPrincipal AuthUser user) {
        service.purgeAllAttachments(user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    // ============ 关联数据查询 ============

    /**
     * 获取系统选项（根据项目）
     */
    @GetMapping("/options/systems")
    public ApiResponse<List<Map<String, Object>>> systemOptions(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getSystemOptions(projectId, user), TraceId.getOrCreate());
    }

    /**
     * 获取问题选项（根据项目）
     */
    @GetMapping("/options/issues")
    public ApiResponse<List<Map<String, Object>>> issueOptions(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getIssueOptions(projectId, user), TraceId.getOrCreate());
    }
}

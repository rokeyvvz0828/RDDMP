package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.common.api.PageResult;
import com.ccb.datamigration.lifecycle.issue.IssueService;
import com.ccb.datamigration.lifecycle.issue.model.IssueView;
import com.ccb.datamigration.lifecycle.issue.model.KnowledgeEntryView;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 问题管理 + 历史问题知识库接口（基线第 13 章，T8）：台账/导入/整改闭环/对账同步/知识检索复用。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class IssueController {
    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping("/issues")
    public ApiResponse<PageResult<IssueView>> pageIssues(
            @RequestParam(required = false) String issueStatus,
            @RequestParam(required = false) String issueSource,
            @RequestParam(required = false) String issueCode,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) Long rectifierId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.listIssues(user, issueStatus, issueSource, issueCode,
                componentId, rectifierId, page, size));
    }

    @PostMapping("/issues")
    public ApiResponse<IssueView> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.createIssue(user, body, "MANUAL"));
    }

    @PutMapping("/issues/{issueId}")
    public ApiResponse<IssueView> update(@PathVariable long issueId, @RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.updateIssue(user, issueId, body, true));
    }

    @PostMapping("/issue/import")
    public ApiResponse<Map<String, Object>> batchImport(@RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        Object rows = body.get("rows");
        if (!(rows instanceof List<?> list)) {
            throw new IllegalArgumentException("rows 必须是列表");
        }
        List<Map<String, Object>> items = list.stream()
                .map(v -> (Map<String, Object>) v).toList();
        return ok(issueService.importIssues(user, items));
    }

    @PostMapping("/issue/reconcile")
    public ApiResponse<Map<String, Object>> reconcile(@AuthenticationPrincipal AuthUser user) {
        return ok(issueService.reconcile(user));
    }

    @PostMapping("/issues/{issueId}/rectify")
    public ApiResponse<IssueView> rectify(@PathVariable long issueId, @RequestBody Map<String, Object> body,
                                          @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.rectify(user, issueId,
                str(body, "rectifyProgress"), str(body, "record")));
    }

    @PostMapping("/issues/{issueId}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable long issueId, @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.close(user, issueId, str(body, "solution")));
    }

    @PostMapping("/issues/{issueId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable long issueId, @RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal AuthUser user) {
        issueService.cancel(user, issueId, str(body, "reason"));
        return ok(null);
    }

    @GetMapping("/issue/knowledge/match")
    public ApiResponse<List<KnowledgeEntryView>> match(@RequestParam(required = false) String title,
                                                       @RequestParam(required = false) String desc,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.recommendTop5(user, title, desc));
    }

    @GetMapping("/knowledge")
    public ApiResponse<List<KnowledgeEntryView>> listKnowledge(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.listKnowledge(user, keyword, tagId, status));
    }

    @PostMapping("/knowledge/{knowledgeId}/refer")
    public ApiResponse<KnowledgeEntryView> refer(@PathVariable long knowledgeId, @RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.refer(user, knowledgeId, lng(body.get("issueId"))));
    }

    @PostMapping("/knowledge/{knowledgeId}/invalidate")
    public ApiResponse<KnowledgeEntryView> invalidate(@PathVariable long knowledgeId, @AuthenticationPrincipal AuthUser user) {
        return ok(issueService.invalidate(user, knowledgeId));
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long lng(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    /** 统一成功响应助手（补齐 TraceId）。 */
    private static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }
}

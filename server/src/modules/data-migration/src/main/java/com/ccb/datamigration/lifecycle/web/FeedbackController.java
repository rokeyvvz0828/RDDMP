package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.lifecycle.feedback.FeedbackService;
import com.ccb.datamigration.lifecycle.feedback.model.FeedbackView;
import com.ccb.security.model.AuthUser;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 任务进度反馈接口（基线第 12 章，T6）：反馈保存/提审/问题上报/风险上报/作业日志（四段门禁，管理员不可代填）。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle/feedback")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/orders/{orderId}/processes/{seq}")
    public ApiResponse<FeedbackView> feedbackView(@PathVariable long orderId, @PathVariable int seq,
                                                  @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(feedbackService.feedbackView(user, orderId, seq), TraceId.getOrCreate());
    }

    @PutMapping("/orders/{orderId}/processes/{seq}")
    public ApiResponse<FeedbackView> saveFeedback(@PathVariable long orderId, @PathVariable int seq,
                                                  @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(feedbackService.saveFeedback(user, orderId, seq, body), TraceId.getOrCreate());
    }

    @PostMapping("/orders/{orderId}/processes/{seq}/submit-audit")
    public ApiResponse<Map<String, Object>> submitAudit(@PathVariable long orderId, @PathVariable int seq,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(feedbackService.submitAudit(user, orderId, seq), TraceId.getOrCreate());
    }

    @PostMapping("/orders/{orderId}/processes/{seq}/issues")
    public ApiResponse<Map<String, Object>> reportIssue(@PathVariable long orderId, @PathVariable int seq,
                                                        @RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(feedbackService.reportIssue(user, orderId, seq, body), TraceId.getOrCreate());
    }

    @PostMapping("/orders/{orderId}/processes/{seq}/risks")
    public ApiResponse<Map<String, Object>> reportRisk(@PathVariable long orderId, @PathVariable int seq,
                                                       @RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(feedbackService.reportRisk(user, orderId, seq, body), TraceId.getOrCreate());
    }

    @PostMapping("/orders/{orderId}/processes/{seq}/work-log")
    public ApiResponse<Map<String, Object>> addWorkLog(@PathVariable long orderId, @PathVariable int seq,
                                                       @RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(feedbackService.addWorkLog(user, orderId, seq, stringOrNull(body.get("content"))),
                TraceId.getOrCreate());
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.isBlank() || "null".equals(s) ? null : s;
    }
}

package com.ccb.attachment.web;

import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.attachment.service.AttachmentService;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/attachments")
@PreAuthorize("isAuthenticated()")
public class AttachmentController {
    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<AttachmentItem> upload(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.upload(file, user), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    public ApiResponse<AttachmentItem> get(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.get(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<Map<String, String>> preview(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(Map.of("previewUrl", service.preview(id, user)), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/download")
    public ApiResponse<Map<String, String>> download(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(Map.of("downloadUrl", service.download(id, user)), TraceId.getOrCreate());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemp(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.deleteTemp(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}

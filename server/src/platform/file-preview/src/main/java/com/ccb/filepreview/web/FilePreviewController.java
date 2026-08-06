package com.ccb.filepreview.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.filepreview.model.FilePreviewCapabilities;
import com.ccb.filepreview.model.FilePreviewResponse;
import com.ccb.filepreview.service.FilePreviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/file-previews")
public class FilePreviewController {
    private static final Logger log = LoggerFactory.getLogger(FilePreviewController.class);

    private final FilePreviewService service;

    public FilePreviewController(FilePreviewService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public ApiResponse<FilePreviewCapabilities> capabilities() {
        return ApiResponse.success(service.capabilities(), TraceId.getOrCreate());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FilePreviewResponse> upload(@RequestPart("file") MultipartFile file,
                                                   Principal principal) {
        FilePreviewResponse response = service.upload(file);
        log.info("File preview uploaded operator={} previewId={} size={} traceId={}",
                principal.getName(), response.previewId(), response.size(), TraceId.getOrCreate());
        return ApiResponse.success(response, TraceId.getOrCreate());
    }

    @DeleteMapping("/{previewId}")
    public ApiResponse<Void> delete(@PathVariable String previewId, Principal principal) {
        service.delete(previewId);
        log.info("File preview deleted operator={} previewId={} traceId={}",
                principal.getName(), previewId, TraceId.getOrCreate());
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}

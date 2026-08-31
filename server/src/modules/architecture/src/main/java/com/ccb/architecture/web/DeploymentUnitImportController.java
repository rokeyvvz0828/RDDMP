package com.ccb.architecture.web;

import com.ccb.architecture.service.DeploymentUnitImportService;
import com.ccb.architecture.service.DeploymentUnitImportService.ImportBatchSummary;
import com.ccb.architecture.service.DeploymentUnitImportService.ImportBatchView;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 部署单元初始化导入 API。上传、确认与错误报告导出均要求
 * {@code architecture:deployment-unit:manage}；批次台账查询要求查看权限。
 */
@RestController
@RequestMapping("/api/architecture/deployment-unit-imports")
public class DeploymentUnitImportController {
    private static final String VIEW_PERMISSION =
            "hasAnyAuthority('architecture:deployment-unit:view', 'architecture:view', 'architecture:apply', 'architecture:manage')";
    private static final String MANAGE_PERMISSION = "hasAuthority('architecture:deployment-unit:manage')";

    private final DeploymentUnitImportService service;

    public DeploymentUnitImportController(DeploymentUnitImportService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<ImportBatchView> upload(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.upload(actor, file, traceId), traceId);
    }

    @GetMapping
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<PageResult<ImportBatchSummary>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.listBatches(actor, new PageQuery(page, size)), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<ImportBatchView> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.batchDetail(actor, id), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<ImportBatchView> confirm(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.confirm(actor, id, traceId), traceId);
    }

    @GetMapping("/{id}/error-report")
    @PreAuthorize(MANAGE_PERMISSION)
    public ResponseEntity<byte[]> errorReport(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        byte[] csv = service.errorReport(actor, id);
        String fileName = "deployment-unit-import-" + id + "-errors.csv";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/template")
    @PreAuthorize(MANAGE_PERMISSION)
    public ResponseEntity<byte[]> template() {
        byte[] xlsx = service.template();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("deployment-unit-import-template.xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}

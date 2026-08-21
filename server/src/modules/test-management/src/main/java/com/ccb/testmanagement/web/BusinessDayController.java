/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/BusinessDayController.java
 * 说明：营业日管理 REST API 与 XLSX 下载入口。
 * 用途：在 /api/test-management/business-days 下装配查询、CRUD、评审、导入导出和用户选项接口。
 * 作者：hengguan
 */
package com.ccb.testmanagement.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryItem;
import com.ccb.testmanagement.service.BusinessDayService;
import com.ccb.testmanagement.service.BusinessDayWorkbookService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test-management/business-days")
// 关键逻辑：类级权限保护所有读取接口，写入、评审、导入和导出再叠加各自细粒度权限。
@PreAuthorize("hasAuthority('test-management:business-day:access')")
public class BusinessDayController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final BusinessDayService service;
    private final BusinessDayWorkbookService workbooks;

    public BusinessDayController(BusinessDayService service, BusinessDayWorkbookService workbooks) {
        this.service = service;
        this.workbooks = workbooks;
    }

    @GetMapping("/environments")
    public ApiResponse<PageResult<Map<String, Object>>> environments(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean enabled,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.environments(new PageQuery(page, size), keyword, enabled, user));
    }

    @GetMapping("/environment-options")
    public ApiResponse<List<Map<String, Object>>> environmentOptions(@AuthenticationPrincipal AuthUser user) {
        return ok(service.activeEnvironments(user));
    }

    @PostMapping("/environments")
    @PreAuthorize("hasAuthority('test-management:business-day:access:create')")
    public ApiResponse<Map<String, Object>> createEnvironment(@RequestBody Map<String, Object> body,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ok(service.createEnvironment(body, user));
    }

    @PutMapping("/environments/{id}")
    @PreAuthorize("hasAuthority('test-management:business-day:access:update')")
    public ApiResponse<Map<String, Object>> updateEnvironment(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ok(service.updateEnvironment(id, body, user));
    }

    @DeleteMapping("/environments/{id}")
    @PreAuthorize("hasAuthority('test-management:business-day:access:delete')")
    public ApiResponse<Void> deleteEnvironment(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.deleteEnvironment(id, user);
        return ok(null);
    }

    @GetMapping("/schedules")
    public ApiResponse<PageResult<Map<String, Object>>> schedules(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String envCode,
            @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Boolean hasBatch, @RequestParam(required = false) String batchType,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.schedules(new PageQuery(page, size), keyword, envCode, dateFrom, dateTo, hasBatch, batchType, user));
    }

    @GetMapping("/overview")
    public ApiResponse<List<Map<String, Object>>> overview(@RequestParam(required = false) String month,
                                                            @RequestParam(required = false) String envCode,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ok(service.overview(month, envCode, user));
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAuthority('test-management:business-day:access:create')")
    public ApiResponse<Map<String, Object>> createSchedule(@RequestBody Map<String, Object> body,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ok(service.saveSchedule(body, user));
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAuthority('test-management:business-day:access:update')")
    public ApiResponse<Map<String, Object>> updateSchedule(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ok(service.updateSchedule(id, body, user));
    }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasAuthority('test-management:business-day:access:delete')")
    public ApiResponse<Void> deleteSchedule(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.deleteSchedule(id, user);
        return ok(null);
    }

    @GetMapping("/schedules/template")
    @PreAuthorize("hasAuthority('test-management:business-day:access:import')")
    public ResponseEntity<byte[]> scheduleTemplate() {
        return download("营业日日历导入模板.xlsx", workbooks.scheduleTemplate());
    }

    @PostMapping(value = "/schedules/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('test-management:business-day:access:import')")
    public ApiResponse<Map<String, Object>> importSchedules(@RequestPart("file") MultipartFile file,
                                                             @AuthenticationPrincipal AuthUser user) {
        return ok(service.importSchedules(workbooks.parseSchedules(file), user));
    }

    @GetMapping("/schedules/export")
    @PreAuthorize("hasAuthority('test-management:business-day:access:export')")
    public ResponseEntity<byte[]> exportSchedules(@RequestParam(required = false) String envCode,
                                                   @RequestParam(required = false) String dateFrom,
                                                   @RequestParam(required = false) String dateTo,
                                                   @AuthenticationPrincipal AuthUser user) {
        return download("营业日日历安排-" + LocalDate.now() + ".xlsx",
                workbooks.schedules(service.allSchedulesForExport(envCode, dateFrom, dateTo, user)));
    }

    @GetMapping("/requirements")
    public ApiResponse<PageResult<Map<String, Object>>> requirements(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String envCode,
            @RequestParam(required = false) String naturalDate, @RequestParam(required = false) String adoption,
            @AuthenticationPrincipal AuthUser user) {
        return ok(service.requirements(new PageQuery(page, size), keyword, envCode, naturalDate, adoption, user));
    }

    @PostMapping("/requirements")
    @PreAuthorize("hasAuthority('test-management:business-day:access:create')")
    public ApiResponse<Map<String, Object>> createRequirement(@RequestBody Map<String, Object> body,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ok(service.createRequirement(body, user));
    }

    @PutMapping("/requirements/{id}")
    @PreAuthorize("hasAuthority('test-management:business-day:access:update')")
    public ApiResponse<Map<String, Object>> updateRequirement(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ok(service.updateRequirement(id, body, user));
    }

    @PatchMapping("/requirements/{id}/adoption")
    @PreAuthorize("hasAuthority('test-management:business-day:access:review')")
    public ApiResponse<Map<String, Object>> reviewRequirement(@PathVariable long id, @RequestBody Map<String, String> body,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ok(service.reviewRequirement(id, body.get("adoption"), body.get("comment"), user));
    }

    @DeleteMapping("/requirements/{id}")
    @PreAuthorize("hasAuthority('test-management:business-day:access:delete')")
    public ApiResponse<Void> deleteRequirement(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.deleteRequirement(id, user);
        return ok(null);
    }

    @GetMapping("/requirements/export")
    @PreAuthorize("hasAuthority('test-management:business-day:access:export')")
    public ResponseEntity<byte[]> exportRequirements(@RequestParam(required = false) String envCode,
                                                      @RequestParam(required = false) String naturalDate,
                                                      @RequestParam(required = false) String adoption,
                                                      @AuthenticationPrincipal AuthUser user) {
        return download("营业日跑批需求-" + LocalDate.now() + ".xlsx",
                workbooks.requirements(service.allRequirementsForExport(envCode, naturalDate, adoption, user)));
    }

    @GetMapping("/users")
    public ApiResponse<List<UserDirectoryItem>> users(@RequestParam(required = false) String keyword,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ok(service.users(keyword, user));
    }

    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success(data, TraceId.getOrCreate()); }

    private ResponseEntity<byte[]> download(String filename, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(XLSX));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}

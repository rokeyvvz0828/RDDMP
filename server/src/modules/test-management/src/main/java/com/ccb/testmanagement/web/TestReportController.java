/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestReportController.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.web;

// 关键逻辑：控制器仅适配 HTTP 参数与细粒度权限；租户、测试大类、项目和实体边界统一由领域服务校验。

import com.ccb.common.api.*;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.testmanagement.report.TestReportService;
import com.ccb.testmanagement.service.TestReportDocumentService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 测试报告 HTTP 接口。 */
@RestController @RequestMapping("/api/test-management/reports/{domain}") public class TestReportController {
 private final TestReportService reports;private final TestReportDocumentService documents;public TestReportController(TestReportService reports,TestReportDocumentService documents){this.reports=reports;this.documents=documents;}
 @GetMapping("/tree") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports')") public ApiResponse<Map<String,Object>> tree(@PathVariable String domain,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){return ok(reports.tree(domain,projectId,u));}
 @GetMapping("/options") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports')") public ApiResponse<Map<String,Object>> options(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@AuthenticationPrincipal AuthUser u){return ok(reports.options(domain,projectId,physicalSubsystemId,u));}
 @GetMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports')") public ApiResponse<PageResult<Map<String,Object>>> list(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long specialNodeId,@RequestParam(required=false) String scopeType,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser u){return ok(reports.list(domain,projectId,physicalSubsystemId,specialNodeId,scopeType,keyword,new PageQuery(page,Math.min(100,size)),u));}
 @PostMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports:create')") public ApiResponse<Map<String,Object>> generate(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long specialNodeId,@RequestParam(required=false) String scopeType,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(reports.generate(domain,projectId,physicalSubsystemId,specialNodeId,scopeType,null,body,u));}
 @PostMapping("/{id}/regenerate") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports:create')") public ApiResponse<Map<String,Object>> regenerate(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long specialNodeId,@RequestParam(required=false) String scopeType,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(reports.generate(domain,projectId,physicalSubsystemId,specialNodeId,scopeType,id,body,u));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports')") public ApiResponse<Map<String,Object>> detail(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestParam(required=false) Long versionId,@AuthenticationPrincipal AuthUser u){return ok(reports.detail(domain,projectId,id,versionId,u));}
 @GetMapping("/{id}/versions") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports')") public ApiResponse<List<Map<String,Object>>> history(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){return ok(reports.history(domain,projectId,id,u));}
 @PutMapping("/{id}/versions/{versionId}/supplement") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports:update')") public ApiResponse<Void> supplement(@PathVariable String domain,@PathVariable long id,@PathVariable long versionId,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){reports.supplement(domain,projectId,id,versionId,body,u);return ok(null);}
 @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports:delete')") public ApiResponse<Void> delete(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){reports.delete(domain,projectId,id,u);return ok(null);}
 @GetMapping("/{id}/export/{format}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':reports:export')") public ResponseEntity<byte[]> export(@PathVariable String domain,@PathVariable long id,@PathVariable String format,@RequestParam long projectId,@RequestParam(required=false) Long versionId,@AuthenticationPrincipal AuthUser u){if(!Set.of("docx","pdf").contains(format.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("导出格式无效");Map<String,Object> data=reports.detail(domain,projectId,id,versionId,u);Map<?,?> report=(Map<?,?>)data.get("report"),version=(Map<?,?>)data.get("version");String project=String.valueOf(reports.tree(domain,projectId,u).get("project") instanceof Map<?,?> row?row.get("project_name"):"测试项目");String snapshot=String.valueOf(version.get("generated_at")).replaceAll("[^0-9]","");String name=project+"-"+report.get("report_name")+"-V"+version.get("version_no")+"-"+snapshot;reports.recordDownload(domain,projectId,id,versionId,format,u);if("pdf".equalsIgnoreCase(format))return file(name+".pdf",documents.pdf(data),MediaType.APPLICATION_PDF);return file(name+".docx",documents.docx(data),MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));}
 private <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.getOrCreate());} private ResponseEntity<byte[]> file(String name,byte[] bytes,MediaType type){HttpHeaders h=new HttpHeaders();h.setContentType(type);h.setContentDisposition(ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build());h.setContentLength(bytes.length);return ResponseEntity.ok().headers(h).body(bytes);}
}

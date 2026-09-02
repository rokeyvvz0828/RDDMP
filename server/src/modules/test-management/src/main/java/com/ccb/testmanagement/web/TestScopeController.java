/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestScopeController.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.web;

// 关键逻辑：控制器仅适配 HTTP 参数与细粒度权限；租户、测试大类、项目和实体边界统一由领域服务校验。

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.testmanagement.scope.TestScopeService;
import com.ccb.testmanagement.service.ScopeCaseWorkbookService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 测试范围 API：路径权限和服务端实体范围校验同时存在。 */
@RestController
@RequestMapping("/api/test-management/scopes/{domain}")
public class TestScopeController {
    private final TestScopeService service; private final ScopeCaseWorkbookService workbooks;
    public TestScopeController(TestScopeService service, ScopeCaseWorkbookService workbooks) { this.service=service; this.workbooks=workbooks; }
    @GetMapping("/tree") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope')") public ApiResponse<Map<String,Object>> tree(@PathVariable String domain,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){return ok(service.tree(domain,projectId,user));}
    @GetMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope')") public ApiResponse<PageResult<Map<String,Object>>> list(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long directoryId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> status,@RequestParam(required=false) List<String> functionType,@RequestParam(required=false) List<String> changeStatus,@RequestParam(required=false) List<String> importance,@RequestParam(required=false) List<String> accountingFlag,@RequestParam(required=false) String coverage,@RequestParam(defaultValue="false") boolean recycled,@RequestParam(required=false) String sortBy,@RequestParam(required=false) String sortOrder,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long size,@AuthenticationPrincipal AuthUser user){return ok(service.list(domain,projectId,physicalSubsystemId,directoryId,keyword,status,functionType,changeStatus,importance,accountingFlag,coverage,recycled,sortBy,sortOrder,new PageQuery(page,Math.min(size,100)),user));}
    @PostMapping("/directories") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:create')") public ApiResponse<Map<String,Object>> createDirectory(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDirectory(domain,projectId,null,body,user));}
    @PutMapping("/directories/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:update')") public ApiResponse<Map<String,Object>> updateDirectory(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDirectory(domain,projectId,id,body,user));}
    @DeleteMapping("/directories/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:delete')") public ApiResponse<Void> deleteDirectory(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestParam(required=false) Long targetDirectoryId,@AuthenticationPrincipal AuthUser user){service.deleteDirectory(domain,projectId,id,targetDirectoryId,user);return ok(null);}
    @PostMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:create')") public ApiResponse<Map<String,Object>> create(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveScope(domain,projectId,null,body,user));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:update')") public ApiResponse<Map<String,Object>> update(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveScope(domain,projectId,id,body,user));}
    @PostMapping("/{id}/code-preview") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:update')") public ApiResponse<Map<String,Object>> codePreview(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.previewCodeChange(domain,projectId,id,String.valueOf(body.get("scope_code")),user));}
    @PutMapping("/{id}/invalidated") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:update')") public ApiResponse<Map<String,Object>> invalidated(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.invalidate(domain,projectId,id,Boolean.TRUE.equals(body.get("invalidated")),String.valueOf(body.getOrDefault("reason", "")),user));}
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:delete')") public ApiResponse<Map<String,Object>> delete(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){return ok(service.deleteScope(domain,projectId,id,user));}
    @PutMapping("/{id}/restore") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:delete')") public ApiResponse<Map<String,Object>> restore(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.restoreScope(domain,projectId,id,body,user));}
    @GetMapping("/template") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:import')") public ResponseEntity<byte[]> template(@PathVariable String domain){return file("测试范围导入模板.xlsx",workbooks.scopeTemplate());}
    @PostMapping(value="/import/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:import')") public ApiResponse<Map<String,Object>> importPreview(@PathVariable String domain,@RequestParam long projectId,@RequestPart("file") MultipartFile file,@AuthenticationPrincipal AuthUser user){return ok(service.previewImport(domain,projectId,workbooks.scopes(file),user));}
    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:import')") public ApiResponse<Map<String,Object>> imports(@PathVariable String domain,@RequestParam long projectId,@RequestParam(defaultValue="SKIP") String duplicateAction,@RequestPart("file") MultipartFile file,@AuthenticationPrincipal AuthUser user){return ok(service.importScopes(domain,projectId,workbooks.scopes(file),duplicateAction,user));}
    @GetMapping("/export") @PreAuthorize("hasAuthority('test-management:' + #domain + ':scope:export')") public ResponseEntity<byte[]> export(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long directoryId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> status,@RequestParam(required=false) List<String> functionType,@RequestParam(required=false) List<String> changeStatus,@RequestParam(required=false) List<String> importance,@RequestParam(required=false) List<String> accountingFlag,@RequestParam(required=false) String coverage,@AuthenticationPrincipal AuthUser user){return file("测试范围导出.xlsx",workbooks.scopeExport(service.exportRows(domain,projectId,physicalSubsystemId,directoryId,keyword,status,functionType,changeStatus,importance,accountingFlag,coverage,user)));}
    private <T> ApiResponse<T> ok(T data){return ApiResponse.success(data, TraceId.getOrCreate());}
    private ResponseEntity<byte[]> file(String name,byte[] body){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));h.setContentDisposition(ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build());h.setContentLength(body.length);return ResponseEntity.ok().headers(h).body(body);}
}

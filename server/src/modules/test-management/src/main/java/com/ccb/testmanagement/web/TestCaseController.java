/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestCaseController.java
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
import com.ccb.testmanagement.casework.TestCaseService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 测试案例 API；所有实际实体范围仍由服务层复核。 */
@RestController
@RequestMapping("/api/test-management/cases/{domain}")
public class TestCaseController {
    private final TestCaseService service; private final ScopeCaseWorkbookService workbooks;
    public TestCaseController(TestCaseService service, ScopeCaseWorkbookService workbooks){this.service=service;this.workbooks=workbooks;}
    @GetMapping("/tree") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases')") public ApiResponse<Map<String,Object>> tree(@PathVariable String domain,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){return ok(service.tree(domain,projectId,user));}
    @GetMapping("/scopes") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases')") public ApiResponse<List<Map<String,Object>>> scopes(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@AuthenticationPrincipal AuthUser user){return ok(service.scopes(domain,projectId,physicalSubsystemId,user));}
    @GetMapping("/code-preview") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:create')") public ApiResponse<Map<String,Object>> codePreview(@PathVariable String domain,@RequestParam long projectId,@RequestParam long scopeId,@AuthenticationPrincipal AuthUser user){return ok(service.previewCode(domain,projectId,scopeId,user));}
    @GetMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases')") public ApiResponse<PageResult<Map<String,Object>>> list(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long directoryId,@RequestParam(required=false) Long scopeId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> caseType,@RequestParam(required=false) List<String> caseNature,@RequestParam(required=false) List<String> priority,@RequestParam(required=false) List<String> status,@RequestParam(required=false) List<String> accountingResult,@RequestParam(required=false) Long designerId,@RequestParam(required=false) String executionReference,@RequestParam(required=false) String sortBy,@RequestParam(required=false) String sortOrder,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long size,@AuthenticationPrincipal AuthUser user){return ok(service.list(domain,projectId,physicalSubsystemId,directoryId,scopeId,keyword,caseType,caseNature,priority,status,accountingResult,designerId,executionReference,sortBy,sortOrder,new PageQuery(page,Math.min(size,100)),user));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases')") public ApiResponse<Map<String,Object>> detail(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){return ok(service.detail(domain,projectId,id,user));}
    @PostMapping("/directories") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:create')") public ApiResponse<Map<String,Object>> createDirectory(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDirectory(domain,projectId,null,body,user));}
    @PutMapping("/directories/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:update')") public ApiResponse<Map<String,Object>> updateDirectory(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDirectory(domain,projectId,id,body,user));}
    @DeleteMapping("/directories/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:delete')") public ApiResponse<Void> deleteDirectory(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestParam(required=false) Long targetDirectoryId,@AuthenticationPrincipal AuthUser user){service.deleteDirectory(domain,projectId,id,targetDirectoryId,user);return ok(null);}
    @PostMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:create')") public ApiResponse<Map<String,Object>> create(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.save(domain,projectId,null,body,user));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:update')") public ApiResponse<Map<String,Object>> update(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.save(domain,projectId,id,body,user));}
    @PutMapping("/{id}/invalidated") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:update')") public ApiResponse<Map<String,Object>> invalidated(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.invalidate(domain,projectId,id,Boolean.TRUE.equals(body.get("invalidated")),String.valueOf(body.getOrDefault("reason","")),user));}
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:delete')") public ApiResponse<Void> delete(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){service.delete(domain,projectId,id,user);return ok(null);}
    @PutMapping("/move") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:update')") public ApiResponse<Map<String,Object>> move(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.move(domain,projectId,ids(domain,projectId,body,user),Long.parseLong(String.valueOf(body.get("target_directory_id"))),user));}
    @PostMapping("/batch/preview") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:update')") public ApiResponse<Map<String,Object>> preview(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.batchPreview(domain,projectId,ids(domain,projectId,body,user),body,user));}
    @PutMapping("/batch") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:update')") public ApiResponse<Map<String,Object>> batch(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.batchUpdate(domain,projectId,ids(domain,projectId,body,user),body,user));}
    @GetMapping("/template") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:import')") public ResponseEntity<byte[]> template(@PathVariable String domain){return file("测试案例导入模板.xlsx",workbooks.caseTemplate());}
    @PostMapping(value="/import/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:import')") public ApiResponse<Map<String,Object>> importPreview(@PathVariable String domain,@RequestParam long projectId,@RequestPart("file") MultipartFile file,@AuthenticationPrincipal AuthUser user){return ok(service.previewImport(domain,projectId,workbooks.cases(file),user));}
    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:import')") public ApiResponse<Map<String,Object>> imports(@PathVariable String domain,@RequestParam long projectId,@RequestParam(defaultValue="SKIP") String duplicateAction,@RequestPart("file") MultipartFile file,@AuthenticationPrincipal AuthUser user){return ok(service.importCases(domain,projectId,workbooks.cases(file),duplicateAction,user));}
    @GetMapping("/export") @PreAuthorize("hasAuthority('test-management:' + #domain + ':cases:export')") public ResponseEntity<byte[]> export(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long directoryId,@RequestParam(required=false) Long scopeId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> caseType,@RequestParam(required=false) List<String> caseNature,@RequestParam(required=false) List<String> priority,@RequestParam(required=false) List<String> status,@RequestParam(required=false) List<String> accountingResult,@RequestParam(required=false) Long designerId,@RequestParam(required=false) String executionReference,@AuthenticationPrincipal AuthUser user){return file("测试案例导出.xlsx",workbooks.caseExport(service.exportRows(domain,projectId,physicalSubsystemId,directoryId,scopeId,keyword,caseType,caseNature,priority,status,accountingResult,designerId,executionReference,user)));}
    private List<Long> ids(String domain,long projectId,Map<String,Object> body,AuthUser user){List<Long> values=new ArrayList<>();Object raw=body.get("ids");if(raw instanceof List<?> list)for(Object value:list){try{values.add(Long.parseLong(String.valueOf(value)));}catch(NumberFormatException ignored){}}Object codes=body.get("case_codes");if(codes instanceof List<?> list)values.addAll(service.resolveCaseCodes(domain,projectId,list.stream().map(String::valueOf).toList(),user));else if(codes != null)values.addAll(service.resolveCaseCodes(domain,projectId,List.of(String.valueOf(codes)),user));return values.stream().distinct().toList();}
    private <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.getOrCreate());}
    private ResponseEntity<byte[]> file(String name,byte[] body){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));h.setContentDisposition(ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build());h.setContentLength(body.length);return ResponseEntity.ok().headers(h).body(body);}
}

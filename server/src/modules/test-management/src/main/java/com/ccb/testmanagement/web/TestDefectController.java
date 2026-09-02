/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestDefectController.java
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
import com.ccb.testmanagement.defect.TestDefectService;
import com.ccb.testmanagement.service.ExecutionDefectWorkbookService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 测试缺陷 HTTP 入口。 */
@RestController @RequestMapping("/api/test-management/defects/{domain}")
public class TestDefectController {
    private final TestDefectService service; private final ExecutionDefectWorkbookService workbooks; public TestDefectController(TestDefectService service,ExecutionDefectWorkbookService workbooks){this.service=service;this.workbooks=workbooks;}
    @GetMapping("/tree") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects')") public ApiResponse<Map<String,Object>> tree(@PathVariable String domain,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){return ok(service.tree(domain,projectId,u));}
    @GetMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects')") public ApiResponse<PageResult<Map<String,Object>>> list(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> status,@RequestParam(required=false) List<String> category,@RequestParam(required=false) List<String> severity,@RequestParam(required=false) List<String> priority,@RequestParam(required=false) List<String> urgency,@RequestParam(required=false) Long handlerId,@RequestParam(required=false) Long proposerId,@RequestParam(required=false) String executionLink,@RequestParam(defaultValue="false") boolean recycle,@RequestParam(required=false) String quick,@RequestParam(required=false) String sortBy,@RequestParam(required=false) String sortOrder,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser u){return ok(service.list(domain,projectId,physicalSubsystemId,keyword,status,category,severity,priority,urgency,handlerId,proposerId,executionLink,recycle,quick,sortBy,sortOrder,new PageQuery(page,Math.min(size,100)),u));}
    @GetMapping("/export") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:export')") public ResponseEntity<byte[]> export(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> status,@RequestParam(required=false) List<String> category,@RequestParam(required=false) List<String> severity,@RequestParam(required=false) List<String> priority,@RequestParam(required=false) List<String> urgency,@AuthenticationPrincipal AuthUser u){return file("测试缺陷导出.xlsx",workbooks.defectExport(service.exportRows(domain,projectId,physicalSubsystemId,keyword,status,category,severity,priority,urgency,u)));}
    @PostMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:create')") public ApiResponse<Map<String,Object>> create(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(service.create(domain,projectId,body,u));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects')") public ApiResponse<Map<String,Object>> detail(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){return ok(service.detail(domain,projectId,id,u));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:update')") public ApiResponse<Map<String,Object>> update(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(service.update(domain,projectId,id,body,u));}
    @PostMapping("/{id}/transition") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:update')") public ApiResponse<Map<String,Object>> transition(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(service.transition(domain,projectId,id,String.valueOf(body.get("status")),u));}
    @PostMapping("/{id}/executions") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:update')") public ApiResponse<Void> associate(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){service.associate(id,ids(body.get("execution_ids")),domain,projectId,u);return ok(null);}
    @DeleteMapping("/{id}/executions/{executionId}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:delete')") public ApiResponse<Void> detach(@PathVariable String domain,@PathVariable long id,@PathVariable long executionId,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){service.detach(id,executionId,domain,projectId,u);return ok(null);}
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:delete')") public ApiResponse<Void> remove(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){service.remove(domain,projectId,id,u);return ok(null);}
    @PostMapping("/{id}/restore") @PreAuthorize("hasAuthority('test-management:' + #domain + ':defects:delete')") public ApiResponse<Void> restore(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){service.restore(domain,projectId,id,u);return ok(null);}
    private static List<Long> ids(Object x){List<Long>out=new ArrayList<>();if(x instanceof Collection<?> c)for(Object v:c)out.add(Long.parseLong(String.valueOf(v)));return out;}private <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.getOrCreate());}private ResponseEntity<byte[]> file(String name,byte[] body){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));h.setContentDisposition(ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build());h.setContentLength(body.length);return ResponseEntity.ok().headers(h).body(body);}
}

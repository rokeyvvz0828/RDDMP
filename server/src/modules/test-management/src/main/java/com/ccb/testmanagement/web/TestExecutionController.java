/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestExecutionController.java
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
import com.ccb.testmanagement.execution.TestExecutionService;
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

/** 测试执行 HTTP 入口；实体边界、系统角色和业务校验均在服务端重判。 */
@RestController @RequestMapping("/api/test-management/execution/{domain}")
public class TestExecutionController {
    private final TestExecutionService service; private final ExecutionDefectWorkbookService workbooks;
    public TestExecutionController(TestExecutionService service,ExecutionDefectWorkbookService workbooks){this.service=service;this.workbooks=workbooks;}
    @GetMapping("/tree") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution')") public ApiResponse<Map<String,Object>> tree(@PathVariable String domain,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){return ok(service.tree(domain,projectId,user));}
    @GetMapping @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution')") public ApiResponse<PageResult<Map<String,Object>>> list(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long roundId,@RequestParam(required=false) Long cycleId,@RequestParam(required=false) Long directoryId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> status,@RequestParam(required=false) Long executorId,@RequestParam(required=false) String defectLink,@RequestParam(required=false) String validity,@RequestParam(required=false) String sortBy,@RequestParam(required=false) String sortOrder,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser user){return ok(service.list(domain,projectId,physicalSubsystemId,roundId,cycleId,directoryId,keyword,status,executorId,defectLink,validity,sortBy,sortOrder,new PageQuery(page,Math.min(size,100)),user));}
    @PostMapping("/import/preview") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:import')") public ApiResponse<Map<String,Object>> preview(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.previewImport(domain,projectId,body,user));}
    @PostMapping("/import") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:import')") public ApiResponse<Map<String,Object>> imports(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.confirmImport(domain,projectId,body,user));}
    @GetMapping("/export") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:export')") public ResponseEntity<byte[]> export(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long roundId,@RequestParam(required=false) Long cycleId,@RequestParam(required=false) Long directoryId,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> status,@RequestParam(required=false) Long executorId,@RequestParam(required=false) String defectLink,@RequestParam(required=false) String validity,@AuthenticationPrincipal AuthUser user){return file("测试执行导出.xlsx",workbooks.executionExport(service.exportRows(domain,projectId,exportParams(physicalSubsystemId,roundId,cycleId,directoryId,keyword,status,executorId,defectLink,validity),user)));}
    @PostMapping("/directories") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:create')") public ApiResponse<Map<String,Object>> createDirectory(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDirectory(domain,projectId,null,body,user));}
    @PutMapping("/directories/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:update')") public ApiResponse<Map<String,Object>> updateDirectory(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDirectory(domain,projectId,id,body,user));}
    @DeleteMapping("/directories/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:delete')") public ApiResponse<Void> deleteDirectory(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){service.deleteDirectory(domain,projectId,id,user);return ok(null);}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution')") public ApiResponse<Map<String,Object>> detail(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){return ok(service.detail(domain,projectId,id,user));}
    @PutMapping("/{id}/result") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:update')") public ApiResponse<Map<String,Object>> saveResult(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveResult(domain,projectId,id,body,user));}
    @PostMapping("/batch/status") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:update')") public ApiResponse<Map<String,Object>> batch(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.batchStatus(domain,projectId,body,user));}
    @PostMapping("/move") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:update')") public ApiResponse<Map<String,Object>> move(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.move(domain,projectId,ids(body.get("ids")),number(body.get("target_directory_id")),user));}
    @PostMapping("/remove") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:delete')") public ApiResponse<Map<String,Object>> remove(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.remove(domain,projectId,ids(body.get("ids")),user));}
    @PostMapping("/{id}/defects") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:update')") public ApiResponse<Void> associate(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){service.associate(id,ids(body.get("defect_ids")),domain,projectId,user);return ok(null);}
    @DeleteMapping("/{id}/defects/{defectId}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':execution:delete')") public ApiResponse<Void> detach(@PathVariable String domain,@PathVariable long id,@PathVariable long defectId,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){service.detach(id,defectId,domain,projectId,user);return ok(null);}
    private static List<Long> ids(Object raw){List<Long>out=new ArrayList<>();if(raw instanceof Collection<?> values)for(Object value:values)out.add(number(value));return out;} private static long number(Object value){try{return Long.parseLong(String.valueOf(value));}catch(Exception error){throw new IllegalArgumentException("编号无效");}} private static Map<String,Object> exportParams(Long system,Long round,Long cycle,Long directory,String keyword,List<String> status,Long executor,String defectLink,String validity){Map<String,Object> out=new HashMap<>();out.put("physical_subsystem_id",system);out.put("round_id",round);out.put("cycle_id",cycle);out.put("directory_id",directory);out.put("keyword",keyword);out.put("status",status);out.put("executor_id",executor);out.put("defect_link",defectLink);out.put("validity",validity);return out;} private <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.getOrCreate());} private ResponseEntity<byte[]> file(String name,byte[] body){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));h.setContentDisposition(ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build());h.setContentLength(body.length);return ResponseEntity.ok().headers(h).body(body);}
}

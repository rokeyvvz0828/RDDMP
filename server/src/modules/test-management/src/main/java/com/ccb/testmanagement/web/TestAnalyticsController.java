/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestAnalyticsController.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.web;

// 关键逻辑：控制器仅适配 HTTP 参数与细粒度权限；租户、测试大类、项目和实体边界统一由领域服务校验。

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.testmanagement.analytics.TestAnalyticsAdvancedService;
import com.ccb.testmanagement.analytics.TestAnalyticsService;
import com.ccb.testmanagement.service.TestAnalyticsWorkbookService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 分析统计 HTTP 接口；预置报表、设计器与快照对比均限定测试大类和当前项目。 */
@RestController @RequestMapping("/api/test-management/analytics/{domain}")
public class TestAnalyticsController {
 private final TestAnalyticsService analytics; private final TestAnalyticsAdvancedService advanced; private final TestAnalyticsWorkbookService books;
 public TestAnalyticsController(TestAnalyticsService analytics,TestAnalyticsAdvancedService advanced,TestAnalyticsWorkbookService books){this.analytics=analytics;this.advanced=advanced;this.books=books;}
 @GetMapping("/tree") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics')") public ApiResponse<Map<String,Object>> tree(@PathVariable String domain,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){return ok(analytics.tree(domain,projectId,u));}
 @GetMapping("/filters") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics')") public ApiResponse<Map<String,Object>> filters(@PathVariable String domain,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){return ok(advanced.filters(domain,projectId,u));}
 @GetMapping("/preset/{key}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics')") public ApiResponse<Map<String,Object>> preset(@PathVariable String domain,@PathVariable String key,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long roundId,@RequestParam(required=false) Long cycleId,@RequestParam(required=false) String view,@RequestParam(required=false) String perspective,@AuthenticationPrincipal AuthUser u){return ok(advanced.preset(domain,projectId,key,filter(physicalSubsystemId,roundId,cycleId,view,perspective),u));}
 @GetMapping("/compare") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics')") public ApiResponse<List<Map<String,Object>>> compare(@PathVariable String domain,@RequestParam long projectId,@RequestParam List<Long> roundIds,@AuthenticationPrincipal AuthUser u){return ok(advanced.compare(domain,projectId,roundIds,u));}
 @GetMapping("/view/{key}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics')") public ApiResponse<Map<String,Object>> view(@PathVariable String domain,@PathVariable String key,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long roundId,@RequestParam(required=false) Long cycleId,@AuthenticationPrincipal AuthUser u){return ok(analytics.analyze(domain,projectId,key,filter(physicalSubsystemId,roundId,cycleId,null,null),u));}
 @GetMapping("/drilldown/{entity}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics')") public ApiResponse<List<Map<String,Object>>> drilldown(@PathVariable String domain,@PathVariable String entity,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long roundId,@RequestParam(required=false) Long cycleId,@AuthenticationPrincipal AuthUser u){return ok(analytics.drilldown(domain,projectId,entity,filter(physicalSubsystemId,roundId,cycleId,null,null),u));}
 @PostMapping("/reports") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics:create')") public ApiResponse<Map<String,Object>> save(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(analytics.save(domain,projectId,null,body,u));}
 @PutMapping("/reports/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics:update')") public ApiResponse<Map<String,Object>> update(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(analytics.save(domain,projectId,id,body,u));}
 @PostMapping("/reports/{id}/publish") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics:publish')") public ApiResponse<Void> publish(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){analytics.publish(domain,projectId,id,Boolean.TRUE.equals(body.get("shared")),u);return ok(null);}
 @DeleteMapping("/reports/{id}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics:delete')") public ApiResponse<Void> delete(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser u){analytics.delete(domain,projectId,id,u);return ok(null);}
 @PostMapping("/archive") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics:archive')") public ApiResponse<List<Map<String,Object>>> archive(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser u){return ok(advanced.archive(domain,projectId,number(body.get("round_id")),u));}
 @GetMapping("/snapshots") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics')") public ApiResponse<List<Map<String,Object>>> snapshots(@PathVariable String domain,@RequestParam long projectId,@RequestParam(required=false) Long roundId,@AuthenticationPrincipal AuthUser u){return ok(analytics.snapshots(domain,projectId,roundId,u));}
 @GetMapping("/export/{key}") @PreAuthorize("hasAuthority('test-management:' + #domain + ':analytics:export')") public ResponseEntity<byte[]> export(@PathVariable String domain,@PathVariable String key,@RequestParam long projectId,@RequestParam(required=false) Long physicalSubsystemId,@RequestParam(required=false) Long roundId,@RequestParam(required=false) Long cycleId,@RequestParam(required=false) String view,@RequestParam(required=false) String perspective,@AuthenticationPrincipal AuthUser u){byte[] body=books.export(advanced.preset(domain,projectId,key,filter(physicalSubsystemId,roundId,cycleId,view,perspective),u));return file("分析统计.xlsx",body);}
 private Map<String,Object> filter(Long s,Long r,Long c,String view,String perspective){Map<String,Object> x=new HashMap<>();x.put("physical_subsystem_id",s);x.put("round_id",r);x.put("cycle_id",c);if(view!=null)x.put("view",view);if(perspective!=null)x.put("perspective",perspective);return x;}private long number(Object v){try{return Long.parseLong(String.valueOf(v));}catch(Exception e){throw new IllegalArgumentException("轮次无效");}}private ResponseEntity<byte[]> file(String name,byte[] body){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));h.setContentDisposition(ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build());return ResponseEntity.ok().headers(h).body(body);}private <T>ApiResponse<T>ok(T data){return ApiResponse.success(data,TraceId.getOrCreate());}
}

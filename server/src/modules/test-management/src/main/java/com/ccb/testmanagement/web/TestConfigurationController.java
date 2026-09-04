/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestConfigurationController.java
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
import com.ccb.system.model.UserDirectoryItem;
import com.ccb.testmanagement.service.TestConfigurationService;
import com.ccb.testmanagement.service.TestConfigurationWorkbookService;
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

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/** 管理配置 API；权限表达式把路径中的测试大类绑定到对应菜单权限。 */
@RestController
@RequestMapping("/api/test-management/configuration/{domain}")
public class TestConfigurationController {
    private final TestConfigurationService service;
    private final TestConfigurationWorkbookService workbooks;
    public TestConfigurationController(TestConfigurationService service, TestConfigurationWorkbookService workbooks) { this.service = service; this.workbooks = workbooks; }

    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<List<Map<String,Object>>> projects(@PathVariable String domain, @AuthenticationPrincipal AuthUser user) { return ok(service.projectOptions(user)); }

    @GetMapping("/systems")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<PageResult<Map<String,Object>>> systems(@PathVariable String domain,@RequestParam long projectId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@RequestParam(required=false) String keyword,@AuthenticationPrincipal AuthUser user){return ok(service.systems(domain,projectId,new PageQuery(page,size),keyword,user));}

    @PutMapping("/systems/{physicalId}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:update')")
    public ApiResponse<Map<String,Object>> setSystem(@PathVariable String domain,@PathVariable long physicalId,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.setSystem(domain,projectId,physicalId,body,user));}

    @GetMapping("/systems/template")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:import')")
    public ResponseEntity<byte[]> systemTemplate(@PathVariable String domain){return download("参测系统导入模板.xlsx",workbooks.systemTemplate());}

    @PostMapping(value="/systems/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:import')")
    public ApiResponse<Map<String,Object>> importSystems(@PathVariable String domain,@RequestParam long projectId,@RequestPart("file") MultipartFile file,@AuthenticationPrincipal AuthUser user){return ok(service.importSystems(domain,projectId,workbooks.systems(file),user));}

    @GetMapping("/systems/{physicalId}/impact")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<Map<String,Object>> systemImpact(@PathVariable String domain,@PathVariable long physicalId,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){return ok(service.systemImpact(domain,projectId,physicalId,user.tenantId()));}

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<PageResult<Map<String,Object>>> roles(@PathVariable String domain,@RequestParam long projectId,@RequestParam long physicalId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser user){return ok(service.roles(domain,projectId,physicalId,new PageQuery(page,size),user));}

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:create')")
    public ApiResponse<Map<String,Object>> assignRole(@PathVariable String domain,@RequestParam long projectId,@RequestParam long physicalId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.assignRole(domain,projectId,physicalId,body,user));}

    @GetMapping("/roles/template")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:import')")
    public ResponseEntity<byte[]> roleTemplate(@PathVariable String domain){return download("系统角色导入模板.xlsx",workbooks.roleTemplate());}

    @PostMapping(value="/roles/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:import')")
    public ApiResponse<Map<String,Object>> importRoles(@PathVariable String domain,@RequestParam long projectId,@RequestPart("file") MultipartFile file,@AuthenticationPrincipal AuthUser user){return ok(service.importRoles(domain,projectId,workbooks.roles(file),user));}

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:delete')")
    public ApiResponse<Map<String,Object>> deleteRole(@PathVariable String domain,@PathVariable long id,@RequestBody(required=false) Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.deleteRole(domain,id,body==null?Map.of():body,user));}

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<List<UserDirectoryItem>> users(@PathVariable String domain,@RequestParam(required=false) String keyword,@AuthenticationPrincipal AuthUser user){return ok(service.users(keyword,user));}

    @GetMapping("/rounds")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<PageResult<Map<String,Object>>> rounds(@PathVariable String domain,@RequestParam long projectId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser user){return ok(service.rounds(domain,projectId,new PageQuery(page,size),user));}

    @PostMapping("/rounds")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:create')")
    public ApiResponse<Map<String,Object>> createRound(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveRound(domain,projectId,null,body,user));}

    @PutMapping("/rounds/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:update')")
    public ApiResponse<Map<String,Object>> updateRound(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveRound(domain,projectId,id,body,user));}

    @DeleteMapping("/rounds/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:delete')")
    public ApiResponse<Void> deleteRound(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){service.deleteRound(domain,projectId,id,user);return ok(null);}

    @GetMapping("/rounds/{roundId}/cycles")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<PageResult<Map<String,Object>>> cycles(@PathVariable String domain,@PathVariable long roundId,@RequestParam long projectId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser user){return ok(service.cycles(domain,projectId,roundId,new PageQuery(page,size),user));}

    @PostMapping("/rounds/{roundId}/cycles")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:create')")
    public ApiResponse<Map<String,Object>> createCycle(@PathVariable String domain,@PathVariable long roundId,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveCycle(domain,projectId,roundId,null,body,user));}

    @PutMapping("/rounds/{roundId}/cycles/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:update')")
    public ApiResponse<Map<String,Object>> updateCycle(@PathVariable String domain,@PathVariable long roundId,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveCycle(domain,projectId,roundId,id,body,user));}

    @DeleteMapping("/rounds/{roundId}/cycles/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:delete')")
    public ApiResponse<Void> deleteCycle(@PathVariable String domain,@PathVariable long roundId,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){service.deleteCycle(domain,projectId,roundId,id,user);return ok(null);}

    @GetMapping("/dictionaries")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<PageResult<Map<String,Object>>> dictionaries(@PathVariable String domain,@RequestParam long projectId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser user){return ok(service.dictionaries(domain,projectId,new PageQuery(page,size),user));}

    @PostMapping("/dictionaries")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:create')")
    public ApiResponse<Map<String,Object>> createDictionary(@PathVariable String domain,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDictionary(domain,projectId,null,body,user));}

    @PutMapping("/dictionaries/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:update')")
    public ApiResponse<Map<String,Object>> updateDictionary(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveDictionary(domain,projectId,id,body,user));}

    @DeleteMapping("/dictionaries/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:delete')")
    public ApiResponse<Void> deleteDictionary(@PathVariable String domain,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){service.deleteDictionary(domain,projectId,id,user);return ok(null);}

    @GetMapping("/dictionaries/{dictionaryId}/options")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration')")
    public ApiResponse<PageResult<Map<String,Object>>> options(@PathVariable String domain,@PathVariable long dictionaryId,@RequestParam long projectId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@AuthenticationPrincipal AuthUser user){return ok(service.options(domain,projectId,dictionaryId,new PageQuery(page,size),user));}

    @PostMapping("/dictionaries/{dictionaryId}/options")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:create')")
    public ApiResponse<Map<String,Object>> createOption(@PathVariable String domain,@PathVariable long dictionaryId,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveOption(domain,projectId,dictionaryId,null,body,user));}

    @PutMapping("/dictionaries/{dictionaryId}/options/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:update')")
    public ApiResponse<Map<String,Object>> updateOption(@PathVariable String domain,@PathVariable long dictionaryId,@PathVariable long id,@RequestParam long projectId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ok(service.saveOption(domain,projectId,dictionaryId,id,body,user));}

    @DeleteMapping("/dictionaries/{dictionaryId}/options/{id}")
    @PreAuthorize("hasAuthority('test-management:' + #domain + ':configuration:delete')")
    public ApiResponse<Void> deleteOption(@PathVariable String domain,@PathVariable long dictionaryId,@PathVariable long id,@RequestParam long projectId,@AuthenticationPrincipal AuthUser user){service.deleteOption(domain,projectId,dictionaryId,id,user);return ok(null);}

    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success(data, TraceId.getOrCreate()); }
    private ResponseEntity<byte[]> download(String filename, byte[] bytes) { HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());headers.setContentLength(bytes.length);return ResponseEntity.ok().headers(headers).body(bytes); }
}

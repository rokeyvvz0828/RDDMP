package com.ccb.development.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.service.DevelopmentTaskService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/development")
public class DevelopmentTaskController {
    private final DevelopmentTaskService tasks;
    public DevelopmentTaskController(DevelopmentTaskService tasks){this.tasks=tasks;}

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<PageResult<TaskView>> list(@AuthenticationPrincipal AuthUser actor,@RequestParam String projectRef,
            @RequestParam(required=false) Long systemId,@RequestParam(required=false) Long ownerId,@RequestParam(required=false) String status,
            @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size){
        return ok(tasks.list(actor,new TaskQuery(projectRef,systemId,ownerId,status,keyword,new PageQuery(page,size))));
    }
    @PostMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('development:task:create','development:admin')")
    public ApiResponse<TaskView> create(@AuthenticationPrincipal AuthUser actor,@RequestBody CreateTask request){return ok(tasks.create(actor,request));}
    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<TaskView> detail(@AuthenticationPrincipal AuthUser actor,@PathVariable long id){return ok(tasks.detail(actor,id));}
    @PutMapping("/tasks/{id}")
    @PreAuthorize("hasAnyAuthority('development:task:update','development:admin')")
    public ApiResponse<TaskView> update(@AuthenticationPrincipal AuthUser actor,@PathVariable long id,@RequestBody UpdateTask request){return ok(tasks.update(actor,id,request));}
    @GetMapping("/tasks/{id}/changes")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<PageResult<ChangeView>> changes(@AuthenticationPrincipal AuthUser actor,@PathVariable long id,
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size){return ok(tasks.changes(actor,id,new PageQuery(page,size)));}
    @GetMapping("/pending-requirements")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<PageResult<PendingRequirement>> pending(@AuthenticationPrincipal AuthUser actor,@RequestParam String projectRef,
            @RequestParam(required=false) Long systemId,@RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size){return ok(tasks.pending(actor,projectRef,systemId,keyword,new PageQuery(page,size)));}
    @GetMapping("/options/systems")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<PageResult<SystemView>> systems(@AuthenticationPrincipal AuthUser actor,@RequestParam String projectRef,
            @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size){
        return ok(tasks.systems(actor,projectRef,new PageQuery(page,size),keyword));
    }
    @GetMapping("/options/users")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<PageResult<UserView>> users(@AuthenticationPrincipal AuthUser actor,@RequestParam String projectRef,
            @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size){
        return ok(tasks.users(actor,projectRef,new PageQuery(page,size),keyword));
    }
    private static <T> ApiResponse<T> ok(T value){return ApiResponse.success(value,TraceId.getOrCreate());}
}

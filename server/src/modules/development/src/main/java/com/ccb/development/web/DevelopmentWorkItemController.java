package com.ccb.development.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.trace.TraceId;
import com.ccb.development.model.DevelopmentWorkItemModels.*;
import com.ccb.development.service.DevelopmentWorkItemService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/development/work-items")
public class DevelopmentWorkItemController {
    private final DevelopmentWorkItemService items;
    public DevelopmentWorkItemController(DevelopmentWorkItemService items){this.items=items;}
    @GetMapping
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<WorkItemPage> list(@AuthenticationPrincipal AuthUser actor,@RequestParam String projectRef,
            @RequestParam(required=false) Long taskId,@RequestParam(required=false) Long systemId,@RequestParam(required=false) Long assigneeId,
            @RequestParam(required=false) String status,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) LocalDate plannedFrom,@RequestParam(required=false) LocalDate plannedTo,
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size){
        return ok(items.list(actor,new WorkItemQuery(projectRef,taskId,systemId,assigneeId,status,keyword,plannedFrom,plannedTo,new PageQuery(page,size))));
    }
    @PostMapping
    @PreAuthorize("hasAnyAuthority('development:work-item:update','development:admin')")
    public ApiResponse<WorkItemView> create(@AuthenticationPrincipal AuthUser actor,@RequestBody WorkItemWrite request){return ok(items.create(actor,request));}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<WorkItemView> detail(@AuthenticationPrincipal AuthUser actor,@PathVariable long id){return ok(items.detail(actor,id));}
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('development:work-item:update','development:admin')")
    public ApiResponse<WorkItemView> update(@AuthenticationPrincipal AuthUser actor,@PathVariable long id,@RequestBody WorkItemWrite request){return ok(items.update(actor,id,request));}
    @PostMapping("/{id}/actions")
    @PreAuthorize("hasAnyAuthority('development:work-item:update','development:work-item:accept','development:admin')")
    public ApiResponse<WorkItemView> action(@AuthenticationPrincipal AuthUser actor,@PathVariable long id,@RequestBody WorkItemAction request){return ok(items.action(actor,id,request));}
    private static <T> ApiResponse<T> ok(T value){return ApiResponse.success(value,TraceId.getOrCreate());}
}

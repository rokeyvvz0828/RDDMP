package com.ccb.architecture.web;

import com.ccb.architecture.service.SubsystemParticipationService;
import com.ccb.architecture.service.SubsystemParticipationService.ParticipationView;
import com.ccb.architecture.service.SubsystemParticipationService.ReplaceCommand;
import com.ccb.architecture.service.SubsystemParticipationService.Candidate;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 参与人是独立子资源，此入口不能修改系统负责人及其他工单管理属性。 */
@RestController
@RequestMapping("/api/architecture/physical-subsystems/{id}/participants")
public class SubsystemParticipationController {
    private static final String VIEW = "hasAnyAuthority('architecture:physical:list','architecture:view','architecture:apply','architecture:manage')";
    private static final String EDIT = "hasAnyAuthority('architecture:physical:update','architecture:apply','architecture:manage')";
    private final SubsystemParticipationService service;
    private final ProjectAccessService projects;

    public SubsystemParticipationController(SubsystemParticipationService service, ProjectAccessService projects) {
        this.service = service;
        this.projects = projects;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public ApiResponse<ParticipationView> detail(@PathVariable long id, @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor, Authentication authentication) {
        ParticipationView view = service.detail(actor, projects.requireAccessible(projectRef, actor), id,
                has(authentication, "architecture:manage"));
        boolean editable = has(authentication, "architecture:physical:update")
                || has(authentication, "architecture:apply") || has(authentication, "architecture:manage");
        return ApiResponse.success(new ParticipationView(view.ownerUserId(), view.explicitParticipantUserIds(),
                view.effectiveParticipantUserIds(), view.rowVersion(), editable && view.canManage(), view.participants()), TraceId.getOrCreate());
    }

    @GetMapping("/candidates")
    @PreAuthorize(EDIT)
    public ApiResponse<List<Candidate>> candidates(@PathVariable long id, @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor, Authentication authentication) {
        return ApiResponse.success(service.candidates(actor, projects.requireAccessible(projectRef, actor), id,
                has(authentication, "architecture:manage")), TraceId.getOrCreate());
    }

    @PutMapping
    @PreAuthorize(EDIT)
    public ApiResponse<ParticipationView> replace(@PathVariable long id, @RequestParam String projectRef,
            @RequestBody ReplaceCommand command, @AuthenticationPrincipal AuthUser actor, Authentication authentication) {
        return ApiResponse.success(service.replace(actor, projects.requireAccessible(projectRef, actor), id, command,
                has(authentication, "architecture:manage"), TraceId.getOrCreate()), TraceId.getOrCreate());
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}

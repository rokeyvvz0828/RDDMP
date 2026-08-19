package com.ccb.project.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.project.model.ProjectMembership;
import com.ccb.project.model.ProjectStatus;
import com.ccb.project.model.ProjectSummary;
import com.ccb.project.service.ProjectService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@PreAuthorize("hasAuthority('project:list')")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<ProjectSummary>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProjectStatus status,
            @AuthenticationPrincipal AuthUser user) {
        return success(service.list(new PageQuery(page, size), keyword, status, user));
    }

    @GetMapping("/available")
    public ApiResponse<List<ProjectSummary>> available(@AuthenticationPrincipal AuthUser user) {
        return success(service.available(user));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectSummary> get(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) {
        return success(service.get(projectId, user));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:list:create')")
    public ApiResponse<ProjectSummary> create(@Valid @RequestBody ProjectCommands.CreateProject command,
                                              @AuthenticationPrincipal AuthUser user) {
        return success(service.create(command, user));
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasAuthority('project:list:update')")
    public ApiResponse<ProjectSummary> update(@PathVariable long projectId,
                                              @Valid @RequestBody ProjectCommands.UpdateProject command,
                                              @AuthenticationPrincipal AuthUser user) {
        return success(service.update(projectId, command, user));
    }

    @PostMapping("/{projectId}/archive")
    @PreAuthorize("hasAuthority('project:list:archive')")
    public ApiResponse<ProjectSummary> archive(@PathVariable long projectId,
                                               @Valid @RequestBody ProjectCommands.VersionCommand command,
                                               @AuthenticationPrincipal AuthUser user) {
        return success(service.archive(projectId, command, user));
    }

    @PostMapping("/{projectId}/restore")
    @PreAuthorize("hasAuthority('project:list:archive')")
    public ApiResponse<ProjectSummary> restore(@PathVariable long projectId,
                                               @Valid @RequestBody ProjectCommands.VersionCommand command,
                                               @AuthenticationPrincipal AuthUser user) {
        return success(service.restore(projectId, command, user));
    }

    @GetMapping("/{projectId}/members")
    public ApiResponse<List<ProjectMembership>> members(@PathVariable long projectId,
                                                        @AuthenticationPrincipal AuthUser user) {
        return success(service.members(projectId, user));
    }

    @GetMapping("/member-candidates")
    @PreAuthorize("hasAuthority('project:list:member')")
    public ApiResponse<PageResult<UserDirectoryUser>> memberCandidates(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser user) {
        return success(service.memberCandidates(new PageQuery(page, size), keyword, user));
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasAuthority('project:list:member')")
    public ApiResponse<ProjectSummary> addMember(@PathVariable long projectId,
                                                 @Valid @RequestBody ProjectCommands.AddMember command,
                                                 @AuthenticationPrincipal AuthUser user) {
        return success(service.addMember(projectId, command, user));
    }

    @PatchMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasAuthority('project:list:member')")
    public ApiResponse<ProjectSummary> changeMemberRole(@PathVariable long projectId,
                                                        @PathVariable long userId,
                                                        @Valid @RequestBody ProjectCommands.ChangeMemberRole command,
                                                        @AuthenticationPrincipal AuthUser user) {
        return success(service.changeMemberRole(projectId, userId, command, user));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasAuthority('project:list:member')")
    public ApiResponse<ProjectSummary> removeMember(@PathVariable long projectId,
                                                    @PathVariable long userId,
                                                    @RequestParam long version,
                                                    @AuthenticationPrincipal AuthUser user) {
        return success(service.removeMember(projectId, userId, version, user));
    }

    @PostMapping("/{projectId}/owner-transfer")
    @PreAuthorize("hasAuthority('project:list:member')")
    public ApiResponse<ProjectSummary> transferOwner(@PathVariable long projectId,
                                                     @Valid @RequestBody ProjectCommands.TransferOwner command,
                                                     @AuthenticationPrincipal AuthUser user) {
        return success(service.transferOwner(projectId, command, user));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> validationFailure(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                ErrorCode.BAD_REQUEST, "项目请求参数无效", TraceId.getOrCreate()));
    }
}

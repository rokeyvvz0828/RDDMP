package com.ccb.project.web;

import com.ccb.project.model.ProjectRole;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProjectCommands {
    private ProjectCommands() {
    }

    public record CreateProject(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String projectCode,
            @NotBlank @Size(max = 128) String projectName) {
    }

    public record UpdateProject(
            @NotBlank @Size(max = 128) String projectName,
            @Min(0) long version) {
    }

    public record VersionCommand(@Min(0) long version) {
    }

    public record AddMember(
            @Min(1) long userId,
            @NotNull ProjectRole role,
            @Min(0) long version) {
    }

    public record ChangeMemberRole(
            @NotNull ProjectRole role,
            @Min(0) long version) {
    }

    public record TransferOwner(
            @Min(1) long newOwnerUserId,
            @Min(0) long version) {
    }
}

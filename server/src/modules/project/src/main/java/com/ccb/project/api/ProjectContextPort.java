package com.ccb.project.api;

import com.ccb.project.model.ProjectAction;
import com.ccb.project.model.ProjectMembership;
import com.ccb.project.model.ProjectSummary;
import com.ccb.security.model.AuthUser;

import java.util.List;

public interface ProjectContextPort {
    ProjectSummary requireAccess(long projectId, AuthUser user, ProjectAction action);

    ProjectMembership membership(long projectId, AuthUser user);

    List<ProjectSummary> available(AuthUser user);
}

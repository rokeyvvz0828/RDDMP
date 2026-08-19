package com.ccb.project.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectControllerSecurityTest {
    @Test
    void declaresPlatformPermissionForEveryProtectedOperation() {
        assertEquals("hasAuthority('project:list')",
                ProjectController.class.getAnnotation(PreAuthorize.class).value());
        Map<String, String> permissions = Arrays.stream(ProjectController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .collect(Collectors.toMap(Method::getName,
                        method -> method.getAnnotation(PreAuthorize.class).value()));

        assertEquals("hasAuthority('project:list:create')", permissions.get("create"));
        assertEquals("hasAuthority('project:list:update')", permissions.get("update"));
        assertEquals("hasAuthority('project:list:archive')", permissions.get("archive"));
        assertEquals("hasAuthority('project:list:archive')", permissions.get("restore"));
        assertEquals("hasAuthority('project:list:member')", permissions.get("memberCandidates"));
        assertEquals("hasAuthority('project:list:member')", permissions.get("addMember"));
        assertEquals("hasAuthority('project:list:member')", permissions.get("changeMemberRole"));
        assertEquals("hasAuthority('project:list:member')", permissions.get("removeMember"));
        assertEquals("hasAuthority('project:list:member')", permissions.get("transferOwner"));
    }
}

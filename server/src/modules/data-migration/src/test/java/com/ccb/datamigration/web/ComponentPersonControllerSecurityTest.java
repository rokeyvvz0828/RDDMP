package com.ccb.datamigration.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 组件清单关联人员 Controller 权限声明测试：读端点沿用类级集合权限，保存端点必须持有
 * data-migration:manage 或 system:admin。
 */
class ComponentPersonControllerSecurityTest {

    @Test
    void protectsPersonsWriteRouteWithManageOrAdminPermission() {
        PreAuthorize root = ProjectComponentController.class.getAnnotation(PreAuthorize.class);
        assertTrue(root.value().contains("data-migration:components"), root.value());
        assertTrue(root.value().contains("system:admin"), root.value());

        var save = Arrays.stream(ProjectComponentController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("savePersons"))
                .findFirst().orElseThrow();
        PreAuthorize saveGuard = save.getAnnotation(PreAuthorize.class);
        assertTrue(saveGuard != null, "savePersons 必须声明写权限");
        assertTrue(saveGuard.value().contains("data-migration:manage"), saveGuard.value());
        assertTrue(saveGuard.value().contains("system:admin"), saveGuard.value());

        var persons = Arrays.stream(ProjectComponentController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("persons"))
                .findFirst().orElseThrow();
        assertTrue(persons.getAnnotation(PreAuthorize.class) == null, "persons 读端点沿用类级权限");

        var memberOptions = Arrays.stream(ProjectComponentController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("memberOptions"))
                .findFirst().orElseThrow();
        assertTrue(memberOptions.getAnnotation(PreAuthorize.class) == null, "member-options 读端点沿用类级权限");
    }
}

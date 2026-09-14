package com.ccb.datamigration.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

class DashboardControllerSecurityTest {

    @Test
    void protectsDashboardRoutesAndKeepsCompatibilityAliases() {
        PreAuthorize permission = DashboardController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(permission);
        for (String authority : new String[]{"data-migration:dashboard", "data-migration:access",
                "data-migration:write", "data-migration:manage", "system:admin"}) {
            assertTrue(permission.value().contains(authority), authority);
        }

        RequestMapping root = DashboardController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/data-migration/dashboard"}, root.value());
        assertArrayEquals(new String[]{"/overall"}, getMapping("overall").value());
        assertArrayEquals(new String[]{"/component", "/components"}, getMapping("component").value());
        assertArrayEquals(new String[]{"/overall/metrics/{metricCode}"}, getMapping("metric").value());
        assertArrayEquals(new String[]{"/overall/drilldowns/{metricCode}"}, getMapping("drilldown").value());
    }

    @Test
    void projectIdIsRequiredAndDrilldownDefaultsToTwentyRows() {
        RequestParam metricProject = requestParam(method("metric"), "projectId");
        assertTrue(metricProject.required());

        RequestParam size = requestParam(method("drilldown"), "size");
        assertEquals("20", size.defaultValue());
    }

    private RequestParam requestParam(Method method, String name) {
        return Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .filter(annotation -> annotation != null && name.equals(annotation.value()))
                .findFirst().orElseThrow();
    }

    private GetMapping getMapping(String name) {
        return method(name).getAnnotation(GetMapping.class);
    }

    private Method method(String name) {
        return Arrays.stream(DashboardController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
    }
}

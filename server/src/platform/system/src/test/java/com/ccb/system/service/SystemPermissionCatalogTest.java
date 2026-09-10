package com.ccb.system.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPermissionCatalogTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MinioStorageService storage;

    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "hash", "管理员", 1L, true);

    @Test
    void permissionCatalogIncludesMenuStatus() {
        SystemService service = spy(new SystemService(jdbc, passwordEncoder, storage));
        doNothing().when(service).requireAction("roles", "read", admin);
        Map<String, Object> menu = new LinkedHashMap<>(Map.of("id", 101L, "status", 1));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("menu_type, status, route_path"), eq(1L))).thenReturn(List.of(menu));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("SELECT id, action_code"), eq(1L), eq(101L))).thenReturn(List.of());

        Map<String, Object> catalog = service.permissionCatalog(admin);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> menus = (List<Map<String, Object>>) catalog.get("menus");
        assertEquals(1, menus.get(0).get("status"));
    }

    @Test
    void permissionCodeAndActionCodeAreImmutable() {
        SystemService service = serviceWithAccess("update");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7001L), eq(1L))).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.updatePermission(
                7001L,
                Map.of("permission_code", "project:plan:list:update", "action_code", "update"),
                admin));

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE sys_menu_permission SET"),
                org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void referencedPermissionCannotBeDeleted() {
        SystemService service = serviceWithAccess("delete");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7001L), eq(1L))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("SELECT (SELECT COUNT(*)"),
                eq(Integer.class), eq(7001L), eq(1L), eq(7001L), eq(1L))).thenReturn(2);

        assertThrows(BusinessException.class, () -> service.deletePermission(7001L, admin));

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.startsWith("DELETE FROM sys_menu_permission"),
                org.mockito.ArgumentMatchers.any(Object[].class));
    }

    private SystemService serviceWithAccess(String action) {
        SystemService service = spy(new SystemService(jdbc, passwordEncoder, storage));
        doNothing().when(service).requireAction("role-permissions", action, admin);
        return service;
    }
}

package com.ccb.system.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPermissionCatalogTest {
    @Mock private SystemMapper mapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MinioStorageService storage;

    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "hash", "管理员", 1L, true);

    @Test
    void permissionCatalogIncludesMenuStatus() {
        SystemService service = spy(new SystemService(new SystemRepository(mapper), passwordEncoder, storage));
        doNothing().when(service).requireAction("roles", "read", admin);
        Map<String, Object> menu = new LinkedHashMap<>(Map.of("id", 101L, "status", 1));
        when(mapper.selectPermissionMenus(1L)).thenReturn(List.of(menu));
        when(mapper.selectMenuActions(101L, 1L)).thenReturn(List.of());

        Map<String, Object> catalog = service.permissionCatalog(admin);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> menus = (List<Map<String, Object>>) catalog.get("menus");
        assertEquals(1, menus.get(0).get("status"));
    }

    @Test
    void permissionCodeAndActionCodeAreImmutable() {
        SystemService service = serviceWithAccess("update");
        when(mapper.countPermission(7001L, 1L)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.updatePermission(
                7001L,
                Map.of("permission_code", "project:plan:list:update", "action_code", "update"),
                admin));

        verify(mapper, never()).updatePermission(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void referencedPermissionCannotBeDeleted() {
        SystemService service = serviceWithAccess("delete");
        when(mapper.countPermission(7001L, 1L)).thenReturn(1L);
        when(mapper.countPermissionReferences(7001L, 1L)).thenReturn(2L);

        assertThrows(BusinessException.class, () -> service.deletePermission(7001L, admin));

        verify(mapper, never()).deletePermission(7001L, 1L);
    }

    private SystemService serviceWithAccess(String action) {
        SystemService service = spy(new SystemService(new SystemRepository(mapper), passwordEncoder, storage));
        doNothing().when(service).requireAction("role-permissions", action, admin);
        return service;
    }
}

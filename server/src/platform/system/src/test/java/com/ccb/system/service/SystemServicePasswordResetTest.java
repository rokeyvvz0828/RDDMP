package com.ccb.system.service;

import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemServicePasswordResetTest {
    @Mock private SystemMapper mapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MinioStorageService storage;

    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "hash", "管理员", 1L, true);

    @Test
    void hashesAndUpdatesNonBlankPasswordWithinTenant() {
        SystemService service = serviceWithAccess();
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-bcrypt-hash");
        stubSuccessfulUpdate();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("password", "NewPassword123");
        input.put("display_name", "测试用户");
        service.update("users", 42L, input, admin);

        verify(passwordEncoder).encode("NewPassword123");
        ArgumentCaptor<Map<String, Object>> update = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateUser(update.capture());
        assertEquals("new-bcrypt-hash", update.getValue().get("password_hash"));
        assertEquals("测试用户", update.getValue().get("display_name"));
        assertEquals(42L, update.getValue().get("id"));
        assertEquals(1L, update.getValue().get("tenantId"));
    }

    @Test
    void blankPasswordKeepsExistingHash() {
        SystemService service = serviceWithAccess();
        stubSuccessfulUpdate();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("password", "  ");
        input.put("display_name", "测试用户");
        service.update("users", 42L, input, admin);

        verify(passwordEncoder, never()).encode(anyString());
        ArgumentCaptor<Map<String, Object>> update = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateUser(update.capture());
        assertFalse(update.getValue().containsKey("password_hash"));
        assertEquals("测试用户", update.getValue().get("display_name"));
        assertEquals(42L, update.getValue().get("id"));
        assertEquals(1L, update.getValue().get("tenantId"));
    }

    private SystemService serviceWithAccess() {
        SystemService service = spy(new SystemService(new SystemRepository(mapper), passwordEncoder, storage));
        doNothing().when(service).requireAction("users", "update", admin);
        return service;
    }

    private void stubSuccessfulUpdate() {
        when(mapper.updateUser(any())).thenReturn(1);
        Map<String, Object> user = new HashMap<>();
        user.put("id", 42L);
        user.put("username", "test-user");
        user.put("display_name", "测试用户");
        user.put("org_id", null);
        user.put("avatar_object_key", null);
        when(mapper.selectUser(42L, 1L)).thenReturn(user);
    }
}

package com.ccb.system.service;

import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemServicePasswordResetTest {
    @Mock private JdbcTemplate jdbc;
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
        SqlUpdate update = userUpdate();
        String assignments = update.sql().substring("UPDATE sys_user SET ".length(), update.sql().indexOf(" WHERE"));
        String[] columns = assignments.replace(" = ?", "").split(", ");
        int passwordIndex = java.util.Arrays.asList(columns).indexOf("password_hash");
        int nameIndex = java.util.Arrays.asList(columns).indexOf("display_name");
        assertEquals("new-bcrypt-hash", update.args()[passwordIndex]);
        assertEquals("测试用户", update.args()[nameIndex]);
        assertEquals(42L, update.args()[2]);
        assertEquals(1L, update.args()[3]);
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
        SqlUpdate update = userUpdate();
        assertFalse(update.sql().contains("password"));
        assertEquals("测试用户", update.args()[0]);
        assertEquals(42L, update.args()[1]);
        assertEquals(1L, update.args()[2]);
    }

    private SystemService serviceWithAccess() {
        SystemService service = spy(new SystemService(jdbc, passwordEncoder, storage));
        doNothing().when(service).requireAction("users", "update", admin);
        return service;
    }

    private void stubSuccessfulUpdate() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        Map<String, Object> user = new HashMap<>();
        user.put("id", 42L);
        user.put("username", "test-user");
        user.put("display_name", "测试用户");
        user.put("org_id", null);
        user.put("avatar_object_key", null);
        when(jdbc.queryForMap(anyString(), eq(42L), eq(1L))).thenReturn(user);
    }

    private SqlUpdate userUpdate() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), args.capture());
        for (int index = 0; index < sql.getAllValues().size(); index++) {
            if (sql.getAllValues().get(index).startsWith("UPDATE sys_user SET")) {
                return new SqlUpdate(sql.getAllValues().get(index), args.getAllValues().get(index));
            }
        }
        throw new AssertionError("User update SQL was not executed");
    }

    private record SqlUpdate(String sql, Object[] args) {}
}

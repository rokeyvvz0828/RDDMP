package com.ccb.security.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRepositoryAuditAccessTest {
    @Test
    void systemPermissionsRemainGlobalAndExcludeProjectBusinessPermissions() {
        RecordingMapper mapper = new RecordingMapper();
        AuthRepository repository = new AuthRepository(mapper.proxy());

        repository.findPermissions(7L, 1L);

        assertTrue(mapper.called("findSystemPermissions"));
        assertFalse(mapper.called("findProjectPermissions"));
    }

    @Test
    void projectPermissionsUnionOwnerPmAndAssignedRoleWithoutSystemPermissions() {
        RecordingMapper mapper = new RecordingMapper();
        AuthRepository repository = new AuthRepository(mapper.proxy());

        repository.findPermissions(7L, 1L, 9001L);

        assertTrue(mapper.called("findSystemPermissions"));
        assertTrue(mapper.called("findProjectPermissions"));
        String sql = mapperXml();
        assertTrue(sql.contains("project.owner_id = #{userId}"));
        assertTrue(sql.contains("role.role_code = 'PM'"));
        assertTrue(sql.contains("pm_project_role_permission"));
        assertTrue(sql.contains("permission.permission_code NOT LIKE 'system:%'"));
    }

    @Test
    void routeCatalogIsFilteredByTheSamePermissionSetInAuthService() {
        RecordingMapper mapper = new RecordingMapper();
        AuthRepository repository = new AuthRepository(mapper.proxy());

        repository.findRoutes(7L, 1L, 9001L);

        assertTrue(mapper.called("findRoutes"));
        String routes = mapperXml().substring(mapperXml().indexOf("<select id=\"findRoutes\""),
                mapperXml().indexOf("</select>", mapperXml().indexOf("<select id=\"findRoutes\"")));
        assertTrue(routes.contains("FROM sys_menu"));
        assertTrue(routes.contains("status = 1 AND visible = 1 AND deleted = 0"));
        assertFalse(routes.contains("pm_project"));
    }

    private static String mapperXml() {
        try (InputStream stream = AuthRepositoryAuditAccessTest.class.getResourceAsStream("/mapper/security/AuthMapper.xml")) {
            if (stream == null) throw new IllegalStateException("AuthMapper.xml is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class RecordingMapper {
        private final List<String> calls = new ArrayList<>();

        private AuthMapper proxy() {
            return (AuthMapper) Proxy.newProxyInstance(AuthMapper.class.getClassLoader(), new Class<?>[]{AuthMapper.class},
                    (proxy, method, args) -> {
                        calls.add(method.getName());
                        if (method.getReturnType() == List.class) return List.of();
                        if (method.getReturnType() == int.class) return 0;
                        return null;
                    });
        }

        private boolean called(String method) {
            return calls.contains(method);
        }
    }
}

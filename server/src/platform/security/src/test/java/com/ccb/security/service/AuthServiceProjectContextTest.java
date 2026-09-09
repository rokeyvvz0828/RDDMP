package com.ccb.security.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.security.model.RouteNode;
import com.ccb.security.repository.AuthRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceProjectContextTest {
    private final AuthUser user = new AuthUser(7L, 1L, "member", "hash", "成员", 1L, true);

    @Test
    void resolvesAndCachesAccessibleProjectHeader() {
        ProjectRepository repository = new ProjectRepository(true);
        AuthService service = service(repository);
        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = request("9001", attributes);

        assertEquals(9001L, service.resolveProjectId(request, user));
        assertEquals(9001L, attributes.get(AuthService.PROJECT_CONTEXT_ATTRIBUTE));
    }

    @Test
    void rejectsForgedProjectHeader() {
        AuthService service = service(new ProjectRepository(false));
        HttpServletRequest request = request("9002", new HashMap<>());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveProjectId(request, user));

        assertEquals(ErrorCode.FORBIDDEN, exception.code());
    }

    @Test
    void routesKeepNamespaceAccessMenuAndAuthorizedLeaf() {
        ProjectRepository repository = new ProjectRepository(true, List.of("project:plan:list"));
        AuthService service = service(repository);

        List<RouteNode> routes = service.routes(user, 9001L);

        assertEquals(1, routes.size());
        assertEquals("项目管理", routes.get(0).menuName());
        assertEquals(1, routes.get(0).children().size());
        assertEquals("项目计划", routes.get(0).children().get(0).menuName());
    }

    @Test
    void routesExcludeProjectAccessMenuWithoutProjectPermission() {
        ProjectRepository repository = new ProjectRepository(true, List.of("system:user:list"));
        AuthService service = service(repository);

        List<RouteNode> routes = service.routes(user, 9001L);

        assertTrue(routes.isEmpty());
    }

    private AuthService service(AuthRepository repository) {
        return new AuthService(repository, new BCryptPasswordEncoder(), null, null);
    }

    private HttpServletRequest request(String projectId, Map<String, Object> attributes) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> "X-Project-Id".equals(args[0]) ? projectId : null;
                    case "getAttribute" -> attributes.get(String.valueOf(args[0]));
                    case "setAttribute" -> {
                        attributes.put(String.valueOf(args[0]), args[1]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    private static final class ProjectRepository extends AuthRepository {
        private final boolean accessible;
        private final List<String> permissions;

        private ProjectRepository(boolean accessible) {
            this(accessible, List.of("project:plan:list"));
        }

        private ProjectRepository(boolean accessible, List<String> permissions) {
            super(null);
            this.accessible = accessible;
            this.permissions = permissions;
        }

        @Override
        public boolean hasProjectAccess(long userId, long tenantId, long projectId) {
            return accessible;
        }

        @Override
        public List<String> findPermissions(long userId, long tenantId, Long projectId) {
            return permissions;
        }

        @Override
        public List<RouteNode> findRoutes(long userId, long tenantId, Long projectId) {
            return List.of(
                    new RouteNode(500L, 0L, "menu", "项目管理", "Project", "/projects", "project/index",
                            "project:access", "tickets", 1, List.of()),
                    new RouteNode(501L, 500L, "menu", "项目计划", "ProjectPlan", "/projects/plan", "project/plan",
                            "project:plan:list", "calendar", 1, List.of()),
                    new RouteNode(502L, 500L, "menu", "项目风险", "ProjectRisk", "/projects/risk", "project/risk",
                            "project:risk:list", "triangle-alert", 2, List.of()));
        }
    }
}

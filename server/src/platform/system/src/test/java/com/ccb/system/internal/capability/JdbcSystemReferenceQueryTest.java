package com.ccb.system.internal.capability;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemUserReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcSystemReferenceQueryTest {
    @Mock private SystemCapabilityRepository repository;
    private JdbcSystemReferenceQuery query;
    private final AuthUser actor = new AuthUser(7L, 9L, "tester", "", "测试用户", 1L, true);

    @BeforeEach
    void setUp() {
        query = new JdbcSystemReferenceQuery(repository);
    }

    @Test
    void searchesOnlyActiveTenantUsersAndEscapesLikeKeyword() {
        Map<String, Object> params = Map.of("tenantId", 9L, "keyword", "%a\\%\\_\\\\b%", "size", 5L, "offset", 5L);
        when(repository.activeUsers(params)).thenReturn(List.of(Map.of("id", 12L, "display_name", "演示用户",
                "username", "demo", "mobile_phone", "13800000000", "status", 1)));
        when(repository.activeUserCount(params)).thenReturn(1L);

        PageResult<SystemUserReference> result = query.searchActiveUsers(actor, new PageQuery(2, 5), "a%_\\b");

        assertEquals(1L, result.total());
        assertEquals(2L, result.page());
        assertEquals(5L, result.size());
        assertEquals(new SystemUserReference(12L, "演示用户", "demo", "13800000000", true), result.records().get(0));
        verify(repository).activeUsers(params);
        verify(repository).activeUserCount(params);
    }

    @Test
    void canReadInactiveUserWhenCallerRequestsCurrentState() {
        Map<String, Object> params = Map.of("userId", 18L, "tenantId", 9L, "activeOnly", false);
        when(repository.user(params)).thenReturn(Map.of("id", 18L, "display_name", "停用用户", "username", "disabled", "status", 0));

        assertEquals(new SystemUserReference(18L, "停用用户", "disabled", null, false),
                query.findUser(actor, 18L, false).orElseThrow());
        verify(repository).user(params);
    }

    @Test
    void findsActiveParametersByTenantAndNormalizedCategory() {
        Map<String, Object> params = Map.of("tenantId", 9L, "categoryCode", "ARCH_DEPLOYMENT_PLATFORM");
        when(repository.activeParameters(params)).thenReturn(List.of(Map.of("config_key", "P2", "config_value", "员工渠道平台（P2）")));

        assertEquals(List.of(new SystemParameterReference("P2", "员工渠道平台（P2）")),
                query.activeParameters(actor, " arch_deployment_platform "));
        verify(repository).activeParameters(params);
    }
}

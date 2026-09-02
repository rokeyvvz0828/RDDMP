package com.ccb.testmanagement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import com.ccb.system.model.UserDirectoryItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import java.util.Optional;

/** 配置入口先校验大类，避免任意路径参数触发跨租户主数据查询。 */
@ExtendWith(MockitoExtension.class)
class TestConfigurationServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock UserDirectoryPort users;
    private final AuthUser operator = new AuthUser(1, 1, "admin", "", "管理员", 1, true);

    @Test
    void rejectsUnknownDomainBeforeReadingAnyMasterData() {
        TestConfigurationService service = new TestConfigurationService(jdbc, new ObjectMapper(), users);

        assertThrows(BusinessException.class, () -> service.systems("unsafe-domain", 1,
                new com.ccb.common.api.PageQuery(1, 20), null, operator));

        verifyNoInteractions(jdbc, users);
    }

    @Test
    void acceptsTesterAsAValidSystemRole() {
        TestConfigurationService service = new TestConfigurationService(jdbc, new ObjectMapper(), users);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L, 0L);
        when(users.findActive(1, 1003L)).thenReturn(Optional.of(new UserDirectoryItem(1003, "tester", "测试人员", 1, "测试中心", null)));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("role_code", "TESTER", "role_name", "测试人员"));

        Map<String, Object> result = service.assignRole("user-testing", 910000000003001L, 910000000003103L,
                Map.of("user_id", 1003, "role_code", "TESTER"), operator);

        assertEquals("TESTER", result.get("role_code"));
        assertEquals("测试人员", result.get("role_name"));
    }
}

package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceProjectScopeTest {
    @Mock JdbcTemplate jdbc;
    @Mock DataMigrationPermissionService permissions;

    @Test
    void repeatedSystemCodeAcrossProjectsFailsClosedWithoutProjectContext() {
        IssueService service = new IssueService(jdbc, new ObjectMapper(), permissions);
        AuthUser user = new AuthUser(1, 1, "admin", "", "管理员", 1, true);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("system_name", "项目甲支付系统"),
                Map.of("system_name", "项目乙支付系统")));

        assertNull(service.getSystemName("PAYMENT_AP", user));
    }
}

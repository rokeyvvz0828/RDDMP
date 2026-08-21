/*
 * 文件：server/src/modules/test-management/src/test/java/com/ccb/testmanagement/service/BusinessDayServiceTest.java
 * 说明：营业日领域服务输入边界与跑批字段矩阵单元测试。
 * 用途：验证危险输入、跑批关闭清理、翻数豁免和非翻数必填规则。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BusinessDayServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock UserDirectoryPort users;
    private final AuthUser operator = new AuthUser(1, 1, "admin", "", "管理员", 1, true);

    @Test
    void rejectsUnsafeEnvironmentCodeBeforeDatabaseWrite() {
        BusinessDayService service = new BusinessDayService(jdbc, new ObjectMapper(), users);
        assertThrows(BusinessException.class, () -> service.createEnvironment(
                Map.of("env_code", "SIT 1;DROP", "env_name", "测试环境"), operator));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void rejectsInvalidOverviewMonth() {
        BusinessDayService service = new BusinessDayService(jdbc, new ObjectMapper(), users);
        assertThrows(BusinessException.class, () -> service.overview("2026-13", null, operator));
    }

    @Test
    void clearsDependentFieldsWhenScheduleDoesNotRunBatch() {
        BusinessDayService service = new BusinessDayService(jdbc, new ObjectMapper(), users);

        // 关键逻辑：关闭跑批后即使客户端残留旧值，服务端也必须统一清空，不能信任前端显隐状态。
        BusinessDayService.BatchFields fields = service.batchFields(Map.of(
                "batch_type", "增量",
                "batch_time", "22:00",
                "systems", java.util.List.of("核心系统"),
                "validation_content", "核对日终结果"), false);

        assertFalse(fields.hasBatch());
        assertNull(fields.type());
        assertNull(fields.time());
        assertEquals("[]", fields.systemsJson());
        assertNull(fields.validationContent());
    }

    @Test
    void forcesRequirementBatchAndAllowsTurnoverWithoutDependentFields() {
        BusinessDayService service = new BusinessDayService(jdbc, new ObjectMapper(), users);

        BusinessDayService.BatchFields fields = service.batchFields(Map.of("has_batch", false, "batch_type", "翻数"), true);

        assertTrue(fields.hasBatch());
        assertEquals("翻数", fields.type());
        assertNull(fields.time());
        assertEquals("[]", fields.systemsJson());
        assertNull(fields.validationContent());
    }

    @Test
    void validatesNonEmptyOptionalTurnoverFields() {
        BusinessDayService service = new BusinessDayService(jdbc, new ObjectMapper(), users);

        assertThrows(BusinessException.class, () -> service.batchFields(Map.of(
                "batch_type", "翻数", "batch_time", "25:00"), true));
    }

    @Test
    void requiresAllDependentFieldsForNonTurnoverBatch() {
        BusinessDayService service = new BusinessDayService(jdbc, new ObjectMapper(), users);

        assertThrows(BusinessException.class, () -> service.batchFields(Map.of(
                "batch_type", "增量",
                "systems", java.util.List.of("核心系统"),
                "validation_content", "核对日终结果"), true));
        assertThrows(BusinessException.class, () -> service.batchFields(Map.of(
                "batch_type", "增量",
                "batch_time", "22:00",
                "validation_content", "核对日终结果"), true));
        assertThrows(BusinessException.class, () -> service.batchFields(Map.of(
                "batch_type", "增量",
                "batch_time", "22:00",
                "systems", java.util.List.of("核心系统")), true));
    }
}

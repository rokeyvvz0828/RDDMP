package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.support.StubJdbcTemplate;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementLegacyServiceTest {
    private static final AuthUser MEMBER = new AuthUser(9L, 1L, "mock.product", "", "演示产品经理", 1L, true);

    private Fixture fixture(String stageStatus) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("business_group", "零售一组");
        row.put("current_stage", "PROPOSE");
        row.put("propose_stage_status", stageStatus);
        row.put("docking_stage_status", "未开始");
        row.put("workload_stage_status", "未开始");
        row.put("project_stage_status", "未开始");
        row.put("soft_stage_status", "未开始");
        row.put("launch_stage_status", "未开始");
        StubJdbcTemplate jdbc = new StubJdbcTemplate(count -> 1L, List.of(row), Map.of());
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        RequirementLegacyService service = new RequirementLegacyService(jdbc, changeLog, security);
        return new Fixture(jdbc, service);
    }

    @Test
    void stageStartTransitionsToInProgressAndLogs() {
        Fixture fixture = fixture("未开始");
        fixture.service().stageTransition(1L, "PROPOSE", "START", "开始需求提出", MEMBER);
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("propose_stage_status = ?")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_stage_log")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("STAGE_TRANSITION")));
    }

    @Test
    void stageCompleteFromNotStartedThrowsConflict() {
        Fixture fixture = fixture("未开始");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "COMPLETE", null, MEMBER));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void invalidStageActionThrowsBadRequest() {
        Fixture fixture = fixture("未开始");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "JUMP", null, MEMBER));
        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
    }

    private record Fixture(StubJdbcTemplate jdbc, RequirementLegacyService service) {
    }
}

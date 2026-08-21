package com.ccb.release.application.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseScenarioPolicyTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void derivesRegularUrgentAdditionalAndEmergencyWorkflowScenes() {
        ReleaseScenarioPolicy regular = policy("2026-08-05T04:00:00Z");
        assertEquals(Scene.REGULAR, regular.nonEmergency(window(true), false).workflowScene());
        assertEquals(Scene.REGULAR_ADDITIONAL, regular.nonEmergency(window(true), true).workflowScene());

        ReleaseScenarioPolicy urgent = policy("2026-08-15T04:00:00Z");
        var urgentAdditional = urgent.nonEmergency(window(true), true);
        assertEquals(VersionType.URGENT, urgentAdditional.versionType());
        assertEquals(Characteristic.ADDITIONAL, urgentAdditional.characteristic());
        assertEquals(Scene.URGENT_ADDITIONAL, urgentAdditional.workflowScene());
        assertEquals(Scene.EMERGENCY, urgent.emergency(false).workflowScene());
    }

    @Test
    void exposesClosedSwitchButRejectsUnavailableDateRanges() {
        var scenario = policy("2026-08-05T04:00:00Z").nonEmergency(window(false), false);
        assertFalse(scenario.windowAvailable());
        assertEquals("该投产窗口已关闭常规版本申请", scenario.windowUnavailableReason());

        assertThrows(BusinessException.class, () -> policy("2026-07-31T04:00:00Z").nonEmergency(window(true), false));
        assertThrows(BusinessException.class, () -> policy("2026-08-20T04:00:00Z").nonEmergency(window(true), false));
    }

    private ReleaseScenarioPolicy policy(String instant) {
        return new ReleaseScenarioPolicy(Clock.fixed(Instant.parse(instant), ZONE));
    }

    private ReleaseWindow window(boolean enabled) {
        return new ReleaseWindow(20L, 1L, "WIN-202608-001", "八月窗口", "P-001", "P001", "项目",
                time(1), time(10), time(20), time(21), enabled, null, 0, 7, 7, time(1), time(1));
    }

    private LocalDateTime time(int day) { return LocalDateTime.of(2026, 8, day, 0, 0); }
}

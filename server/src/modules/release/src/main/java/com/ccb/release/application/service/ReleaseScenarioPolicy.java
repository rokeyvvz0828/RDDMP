package com.ccb.release.application.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ReleaseScenarioPolicy {
    private final Clock clock;

    public ReleaseScenarioPolicy() {
        this(Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    ReleaseScenarioPolicy(Clock clock) {
        this.clock = clock;
    }

    public Scenario emergency(boolean additional) {
        return new Scenario(VersionType.EMERGENCY, Characteristic.STANDARD, Scene.EMERGENCY, true, null);
    }

    public Scenario nonEmergency(ReleaseWindow window, boolean additional) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(window.declarationStart())) throw badRequest("尚未到投产窗口申报开始时间");
        if (!now.isBefore(window.productionStart())) throw badRequest("该投产窗口已进入投产期或已关闭");
        VersionType type = now.isAfter(window.declarationEnd()) ? VersionType.URGENT : VersionType.REGULAR;
        Characteristic characteristic = additional ? Characteristic.ADDITIONAL : Characteristic.STANDARD;
        Scene workflowScene = type == VersionType.URGENT
                ? (additional ? Scene.URGENT_ADDITIONAL : Scene.URGENT)
                : (additional ? Scene.REGULAR_ADDITIONAL : Scene.REGULAR);
        String reason = window.regularEnabled() ? null : "该投产窗口已关闭常规版本申请";
        return new Scenario(type, characteristic, workflowScene, window.regularEnabled(), reason);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public record Scenario(VersionType versionType, Characteristic characteristic, Scene workflowScene,
                           boolean windowAvailable, String windowUnavailableReason) {
    }
}

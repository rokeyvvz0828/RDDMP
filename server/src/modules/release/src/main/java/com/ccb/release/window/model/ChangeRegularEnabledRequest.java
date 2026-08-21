package com.ccb.release.window.model;

public record ChangeRegularEnabledRequest(Boolean regularEnabled, Long rowVersion, String changeReason) {
}

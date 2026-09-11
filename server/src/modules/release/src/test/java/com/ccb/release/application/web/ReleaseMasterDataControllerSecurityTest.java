package com.ccb.release.application.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseMasterDataControllerSecurityTest {
    @Test
    void protectsMasterDataRoutesWithReleasePermissions() {
        assertThat(ReleaseMasterDataController.class.getAnnotation(PreAuthorize.class).value())
                .contains("release:access", "system:admin");
        Arrays.stream(ReleaseMasterDataController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("physicalSubsystems")
                        || method.getName().equals("deliveryUnits"))
                .forEach(method -> assertThat(method.getAnnotation(PreAuthorize.class).value())
                        .contains("release:application:view", "release:application:create",
                                "release:application:update", "system:admin"));
    }
}

package com.ccb.release.reporting.model;

import java.util.Map;

public final class ReleaseAnalyticsModels {
    private ReleaseAnalyticsModels() {}

    public record Summary(long windowCount, long applicationCount, long subsystemCount, long deliveryUnitCount,
                          long fileMediaCount,
                          long requirementCount, Map<String, Long> versionTypes, Map<String, Long> productionResults) {
        public Summary(long windowCount, long applicationCount, long subsystemCount, long deliveryUnitCount,
                       long requirementCount, Map<String, Long> versionTypes, Map<String, Long> productionResults) {
            this(windowCount, applicationCount, subsystemCount, deliveryUnitCount, 0, requirementCount,
                    versionTypes, productionResults);
        }
    }
}

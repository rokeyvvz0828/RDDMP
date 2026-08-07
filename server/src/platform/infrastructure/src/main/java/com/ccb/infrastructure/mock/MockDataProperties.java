package com.ccb.infrastructure.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ccb.mock-data")
public class MockDataProperties {
    private boolean enabled;
    private String resource = "classpath:mock/mock-data.json";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
}

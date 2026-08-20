package com.ccb.attachment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ccb.attachment")
public class AttachmentProperties {
    private long maxFileSizeBytes = 50L * 1024 * 1024;
    private Duration tempRetention = Duration.ofHours(24);

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public Duration getTempRetention() { return tempRetention; }
    public void setTempRetention(Duration tempRetention) { this.tempRetention = tempRetention; }
}

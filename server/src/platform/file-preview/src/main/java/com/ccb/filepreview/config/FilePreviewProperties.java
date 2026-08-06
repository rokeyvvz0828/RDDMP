package com.ccb.filepreview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ccb.file-preview")
public class FilePreviewProperties {
    private boolean enabled;
    private String kkBaseUrl;
    private long maxFileSizeBytes;
    private List<String> allowedExtensions = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKkBaseUrl() { return kkBaseUrl; }
    public void setKkBaseUrl(String kkBaseUrl) { this.kkBaseUrl = kkBaseUrl; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public List<String> getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(List<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
}

package com.ccb.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ccb.storage.minio")
public class MinioStorageProperties {
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey;
    private String secretKey;
    private String bucket = "ccb-platform";
    private long presignedExpirySeconds = 3600;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public long getPresignedExpirySeconds() { return presignedExpirySeconds; }
    public void setPresignedExpirySeconds(long presignedExpirySeconds) { this.presignedExpirySeconds = presignedExpirySeconds; }
}
package com.ccb.infrastructure.storage;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService {
    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioStorageProperties properties;
    private final MinioClient client;

    public MinioStorageService(MinioStorageProperties properties) {
        this.properties = properties;
        this.client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    public void put(String objectKey, InputStream input, long size, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(input, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            log.warn("MinIO put failed for bucket {}", properties.getBucket(), exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File storage service unavailable");
        }
    }

    public String presignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry((int) Math.min(properties.getPresignedExpirySeconds(), Integer.MAX_VALUE), TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            log.warn("MinIO presigned URL creation failed for bucket {}", properties.getBucket(), exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File storage service unavailable");
        }
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
        } catch (Exception exception) {
            log.warn("MinIO delete failed for bucket {}", properties.getBucket(), exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File storage service unavailable");
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
    }
}

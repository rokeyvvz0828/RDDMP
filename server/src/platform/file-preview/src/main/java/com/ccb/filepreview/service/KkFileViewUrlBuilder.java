package com.ccb.filepreview.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.filepreview.config.FilePreviewProperties;
import com.ccb.filepreview.model.FilePreviewUrlProvider;
import com.ccb.infrastructure.storage.MinioStorageProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class KkFileViewUrlBuilder implements FilePreviewUrlProvider {
    private final FilePreviewProperties properties;
    private final MinioStorageProperties storageProperties;
    private final String previewEndpoint;
    private final MinioClient previewClient;

    public KkFileViewUrlBuilder(FilePreviewProperties properties, MinioStorageProperties storageProperties) {
        this(properties, storageProperties, "");
    }

    @Autowired
    public KkFileViewUrlBuilder(FilePreviewProperties properties,
                                MinioStorageProperties storageProperties,
                                @Value("${MINIO_PREVIEW_ENDPOINT:}") String previewEndpoint) {
        this.properties = properties;
        this.storageProperties = storageProperties;
        this.previewEndpoint = previewEndpoint;
        this.previewClient = previewEndpoint == null || previewEndpoint.isBlank()
                ? null
                : MinioClient.builder()
                .endpoint(parseHttpUri(previewEndpoint, "Invalid MinIO preview endpoint").toString())
                .credentials(storageProperties.getAccessKey(), storageProperties.getSecretKey())
                .region("us-east-1")
                .build();
    }

    @Override
    public String build(String sourceUrl) {
        URI source = parseHttpUri(sourceUrl, "Invalid preview source URL");
        URI storage = parseHttpUri(storageProperties.getEndpoint(), "Invalid MinIO endpoint");
        if (!sameOrigin(source, storage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Preview source is not trusted");
        }

        URI kkBase = parseHttpUri(properties.getKkBaseUrl(), "Invalid kkFileView base URL");
        if (kkBase.getUserInfo() != null || kkBase.getQuery() != null || kkBase.getFragment() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid kkFileView base URL");
        }
        String baseUrl = kkBase.toString().replaceAll("/+$", "");
        String previewSourceUrl = buildPreviewSource(source);
        String base64 = Base64.getEncoder().encodeToString(previewSourceUrl.getBytes(StandardCharsets.UTF_8));
        String encoded = URLEncoder.encode(base64, StandardCharsets.UTF_8);
        return baseUrl + "/onlinePreview?url=" + encoded;
    }

    @Override
    public String previewUrl(String sourceUrl) {
        return build(sourceUrl);
    }

    private String buildPreviewSource(URI source) {
        if (previewClient == null) return source.toString();

        URI preview = parseHttpUri(previewEndpoint, "Invalid MinIO preview endpoint");
        if (preview.getUserInfo() != null || preview.getQuery() != null || preview.getFragment() != null
                || (preview.getPath() != null && !preview.getPath().isBlank() && !"/".equals(preview.getPath()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid MinIO preview endpoint");
        }

        String path = source.getPath();
        if (path == null || path.length() <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid preview source path");
        }
        String normalizedPath = path.substring(1);
        int separator = normalizedPath.indexOf('/');
        if (separator <= 0 || separator == normalizedPath.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid preview source path");
        }
        String bucket = normalizedPath.substring(0, separator);
        String object = normalizedPath.substring(separator + 1);
        if (!storageProperties.getBucket().equals(bucket)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Preview source bucket is not trusted");
        }
        try {
            return previewClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(object)
                    .expiry((int) Math.min(storageProperties.getPresignedExpirySeconds(), Integer.MAX_VALUE),
                            java.util.concurrent.TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to create preview source URL");
        }
    }

    private URI parseHttpUri(String value, String message) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}

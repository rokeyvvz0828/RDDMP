package com.ccb.filepreview.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.filepreview.config.FilePreviewProperties;
import com.ccb.filepreview.model.FilePreviewUrlProvider;
import com.ccb.infrastructure.storage.MinioStorageProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class KkFileViewUrlBuilder implements FilePreviewUrlProvider {
    private final FilePreviewProperties properties;
    private final MinioStorageProperties storageProperties;

    public KkFileViewUrlBuilder(FilePreviewProperties properties, MinioStorageProperties storageProperties) {
        this.properties = properties;
        this.storageProperties = storageProperties;
    }

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
        String base64 = Base64.getEncoder().encodeToString(sourceUrl.getBytes(StandardCharsets.UTF_8));
        String encoded = URLEncoder.encode(base64, StandardCharsets.UTF_8);
        return baseUrl + "/onlinePreview?url=" + encoded;
    }

    @Override
    public String previewUrl(String sourceUrl) {
        return build(sourceUrl);
    }

    private URI parseHttpUri(String value, String message) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
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

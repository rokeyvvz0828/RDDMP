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
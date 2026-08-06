package com.ccb.filepreview.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.filepreview.config.FilePreviewProperties;
import com.ccb.infrastructure.storage.MinioStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KkFileViewUrlBuilderTest {
    private FilePreviewProperties previewProperties;
    private MinioStorageProperties storageProperties;

    @BeforeEach
    void setUp() {
        previewProperties = new FilePreviewProperties();
        previewProperties.setKkBaseUrl("http://localhost:8012/");
        storageProperties = new MinioStorageProperties();
        storageProperties.setEndpoint("http://minio.example.test:9000");
    }

    @Test
    void encodesTrustedMinioUrlForOnlinePreview() {
        String source = "http://minio.example.test:9000/bucket/a%20b.pdf?X-Amz-Signature=a+b/c=";
        String result = new KkFileViewUrlBuilder(previewProperties, storageProperties).build(source);

        assertEquals("http://localhost:8012/onlinePreview", result.substring(0, result.indexOf('?')));
        String encoded = result.substring(result.indexOf("url=") + 4);
        String decodedBase64 = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        assertEquals(source, new String(Base64.getDecoder().decode(decodedBase64), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsSourceFromAnotherHost() {
        KkFileViewUrlBuilder builder = new KkFileViewUrlBuilder(previewProperties, storageProperties);
        assertThrows(BusinessException.class, () -> builder.build("http://127.0.0.1:9000/private.pdf"));
    }

    @Test
    void rejectsNonHttpSourceAndInvalidKkBaseUrl() {
        KkFileViewUrlBuilder builder = new KkFileViewUrlBuilder(previewProperties, storageProperties);
        assertThrows(BusinessException.class, () -> builder.build("file:///etc/passwd"));

        previewProperties.setKkBaseUrl("javascript:alert(1)");
        assertThrows(BusinessException.class,
                () -> builder.build("http://minio.example.test:9000/bucket/demo.pdf"));
    }
}

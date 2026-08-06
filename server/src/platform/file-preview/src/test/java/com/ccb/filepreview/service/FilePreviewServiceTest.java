package com.ccb.filepreview.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.filepreview.config.FilePreviewProperties;
import com.ccb.filepreview.model.FilePreviewResponse;
import com.ccb.infrastructure.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilePreviewServiceTest {
    private FilePreviewProperties properties;
    private MinioStorageService storageService;
    private KkFileViewUrlBuilder urlBuilder;
    private FilePreviewService service;

    @BeforeEach
    void setUp() {
        properties = new FilePreviewProperties();
        properties.setEnabled(true);
        properties.setMaxFileSizeBytes(1024);
        properties.setAllowedExtensions(List.of("pdf", "png"));
        storageService = mock(MinioStorageService.class);
        urlBuilder = mock(KkFileViewUrlBuilder.class);
        service = new FilePreviewService(properties, storageService, urlBuilder);
    }

    @Test
    void uploadsAllowedFileAndReturnsOpaquePreviewId() {
        MockMultipartFile file = new MockMultipartFile("file", "delivery plan.PDF", "application/pdf", "pdf".getBytes());
        when(storageService.presignedUrl(startsWith("file-preview/"))).thenReturn("http://minio.test/file.pdf");
        when(urlBuilder.build("http://minio.test/file.pdf")).thenReturn("http://kk.test/onlinePreview?url=encoded");

        FilePreviewResponse response = service.upload(file);

        assertEquals("delivery plan.PDF", response.fileName());
        assertTrue(response.previewId().endsWith(".pdf"));
        assertEquals("http://kk.test/onlinePreview?url=encoded", response.previewUrl());
        verify(storageService).put(startsWith("file-preview/"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("application/pdf"));
    }

    @Test
    void rejectsUnsupportedAndOversizedFilesBeforeStorage() {
        assertThrows(BusinessException.class, () -> service.upload(
                new MockMultipartFile("file", "script.html", "text/html", "x".getBytes())));
        assertThrows(BusinessException.class, () -> service.upload(
                new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[1025])));
        verify(storageService, never()).put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidDeleteIdentifier() {
        assertThrows(BusinessException.class, () -> service.delete("../../avatar.png"));
        verify(storageService, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRequestsWhenFeatureIsDisabled() {
        properties.setEnabled(false);
        assertEquals(false, service.capabilities().enabled());
        assertEquals(List.of("pdf", "png"), service.capabilities().allowedExtensions().stream().sorted().toList());
        assertThrows(BusinessException.class, () -> service.upload(
                new MockMultipartFile("file", "demo.pdf", "application/pdf", "pdf".getBytes())));
    }
}

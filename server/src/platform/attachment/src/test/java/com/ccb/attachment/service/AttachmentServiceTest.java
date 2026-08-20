package com.ccb.attachment.service;

import com.ccb.attachment.model.AttachmentItem;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.filepreview.model.FilePreviewUrlProvider;
import com.ccb.infrastructure.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private MinioStorageService storage;
    @Mock
    private FilePreviewUrlProvider previewUrlProvider;
    @Mock
    private MultipartFile file;

    @Test
    void rejectsEmptyFileBeforeStorage() {
        when(file.isEmpty()).thenReturn(true);
        AttachmentService service = new AttachmentService(jdbc, storage, previewUrlProvider);

        assertThrows(BusinessException.class, () -> service.uploadAndBind("PROJECT", 1001L, file, 1L, 7L));
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void rejectsUnsupportedFileTypeBeforeStorage() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn("payload.exe");
        AttachmentService service = new AttachmentService(jdbc, storage, previewUrlProvider);

        assertThrows(BusinessException.class, () -> service.uploadAndBind("PROJECT", 1001L, file, 1L, 7L));
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void removesObjectWhenMetadataPersistenceFails() throws Exception {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn("说明.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));
        doThrow(new RuntimeException("database unavailable")).when(jdbc).update(contains("INSERT INTO sys_attachment"), any(Object[].class));
        AttachmentService service = new AttachmentService(jdbc, storage, previewUrlProvider);

        assertThrows(RuntimeException.class, () -> service.uploadAndBind("PROJECT", 1001L, file, 1L, 7L));
        verify(storage).delete(anyString());
    }

    @Test
    void listsByPageAndFileNameInNewestFirstOrder() {
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class))).thenReturn(21L);
        when(jdbc.query(contains("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(java.util.List.of());
        AttachmentService service = new AttachmentService(jdbc, storage, previewUrlProvider);

        PageResult<AttachmentItem> result = service.list("PROJECT", 1001L, 1L, new PageQuery(2, 10), "report");

        assertEquals(21L, result.total());
        assertEquals(2L, result.page());
        assertEquals(10L, result.size());
        verify(jdbc).queryForObject(contains("file_name LIKE ?"), eq(Long.class), any(Object[].class));
    }
}

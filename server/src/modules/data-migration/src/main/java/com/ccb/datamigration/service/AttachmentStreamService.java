package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * 受认证的附件流代理：业务模块只通过 AttachmentGateway 获取元数据，
 * 再调用平台下载契约取得短时地址并将内容作为二进制响应写回浏览器。
 */
@Service
public class AttachmentStreamService {
    private final AttachmentGateway attachmentGateway;
    private final ObjectMapper objectMapper;

    public AttachmentStreamService(AttachmentGateway attachmentGateway, ObjectMapper objectMapper) {
        this.attachmentGateway = attachmentGateway;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<StreamingResponseBody> stream(long attachmentId, AuthUser user, HttpServletRequest request) {
        AttachmentItem item = attachmentGateway.get(attachmentId, user);
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        URI endpoint = URI.create(request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort())
                + "/api/attachments/" + attachmentId + "/download");
        StreamingResponseBody body = output -> {
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.toString()).openConnection();
            connection.setRequestMethod("GET");
            if (token != null && !token.isBlank()) connection.setRequestProperty(HttpHeaders.AUTHORIZATION, token);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);
            if (connection.getResponseCode() / 100 != 2) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件下载失败");
            JsonNode envelope = objectMapper.readTree(connection.getInputStream());
            String downloadUrl = envelope.path("data").path("downloadUrl").asText("");
            if (downloadUrl.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件下载地址不可用");
            HttpURLConnection content = (HttpURLConnection) new URL(downloadUrl).openConnection();
            content.setConnectTimeout(5000);
            content.setReadTimeout(30000);
            try (InputStream input = content.getInputStream()) {
                input.transferTo(output);
            } finally {
                content.disconnect();
            }
            connection.disconnect();
        };
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (item.contentType() != null && !item.contentType().isBlank()) mediaType = MediaType.parseMediaType(item.contentType());
        } catch (IllegalArgumentException ignored) { }
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encode(item.fileName()))
                .body(body);
    }

    private String encode(String fileName) {
        return java.net.URLEncoder.encode(fileName == null ? "attachment" : fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}

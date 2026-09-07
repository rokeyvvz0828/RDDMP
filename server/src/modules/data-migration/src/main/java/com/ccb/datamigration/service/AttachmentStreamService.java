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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    /**
     * 将多个附件在服务端流式打包为 ZIP 返回。调用方保证 files 来自业务表关系行
     * （attachment_id / file_name），并已通过项目隔离与实体归属校验。
     */
    public ResponseEntity<StreamingResponseBody> streamZip(List<Map<String, Object>> files, String zipName,
                                                           AuthUser user, HttpServletRequest request) {
        if (files == null || files.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "暂无可下载的源文件");
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        String baseUri = request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        StreamingResponseBody body = output -> {
            Set<String> usedNames = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map<String, Object> file : files) {
                    Object rawId = file.get("attachment_id");
                    if (!(rawId instanceof Number number)) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件关系无效");
                    long attachmentId = number.longValue();
                    AttachmentItem item = attachmentGateway.get(attachmentId, user);
                    String entryName = uniqueEntryName(fileName(item, rawId), usedNames);
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (InputStream input = openContent(baseUri, attachmentId, token)) {
                        input.transferTo(zip);
                    } finally {
                        zip.closeEntry();
                    }
                }
            }
        };
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encode(zipName))
                .body(body);
    }

    private InputStream openContent(String baseUri, long attachmentId, String token) throws java.io.IOException {
        URI endpoint = URI.create(baseUri + "/api/attachments/" + attachmentId + "/download");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.toString()).openConnection();
        connection.setRequestMethod("GET");
        if (token != null && !token.isBlank()) connection.setRequestProperty(HttpHeaders.AUTHORIZATION, token);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        if (connection.getResponseCode() / 100 != 2) {
            connection.disconnect();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件下载失败");
        }
        JsonNode envelope = objectMapper.readTree(connection.getInputStream());
        String downloadUrl = envelope.path("data").path("downloadUrl").asText("");
        if (downloadUrl.isBlank()) {
            connection.disconnect();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件下载地址不可用");
        }
        HttpURLConnection content = (HttpURLConnection) new URL(downloadUrl).openConnection();
        content.setConnectTimeout(5000);
        content.setReadTimeout(30000);
        connection.disconnect();
        if (content.getResponseCode() / 100 != 2) {
            content.disconnect();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件下载失败");
        }
        return content.getInputStream();
    }

    private String fileName(AttachmentItem item, Object fallback) {
        if (item.fileName() != null && !item.fileName().isBlank()) return item.fileName();
        return fallback == null ? "file" : String.valueOf(fallback);
    }

    private static String uniqueEntryName(String candidate, Set<String> usedNames) {
        String base = candidate == null || candidate.isBlank() ? "file" : candidate;
        String candidateName = base;
        int sequence = 1;
        while (usedNames.contains(candidateName)) {
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                candidateName = base.substring(0, dot) + "-" + sequence + base.substring(dot);
            } else {
                candidateName = base + "-" + sequence;
            }
            sequence++;
        }
        usedNames.add(candidateName);
        return candidateName;
    }

    private String encode(String fileName) {
        return java.net.URLEncoder.encode(fileName == null ? "attachment" : fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}

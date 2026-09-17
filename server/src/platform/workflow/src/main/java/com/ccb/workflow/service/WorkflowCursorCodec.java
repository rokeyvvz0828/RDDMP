package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

final class WorkflowCursorCodec {
    record Position(Timestamp createdAt, long id) { }

    Position decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] values = decoded.split(":", -1);
            if (values.length != 2) throw invalid();
            long timestamp = Long.parseLong(values[0]);
            long id = Long.parseLong(values[1]);
            if (timestamp < 0 || id <= 0) throw invalid();
            return new Position(Timestamp.from(Instant.ofEpochMilli(timestamp)), id);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    String encode(Object createdAt, long id) {
        if (createdAt == null || id <= 0) return null;
        long timestamp = toInstant(createdAt).toEpochMilli();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((timestamp + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        if (value instanceof java.util.Date date) return date.toInstant();
        throw invalid();
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "分页游标无效");
    }
}

package com.ccb.common.api;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record ApiResponse<T>(int code, String message, T data, String traceId, String timestamp) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(0, "OK", data, traceId, now());
    }

    public static <T> ApiResponse<T> failure(int code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, now());
    }

    private static String now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(FORMATTER);
    }
}
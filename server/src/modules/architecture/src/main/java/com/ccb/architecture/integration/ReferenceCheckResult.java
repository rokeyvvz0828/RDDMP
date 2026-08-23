package com.ccb.architecture.integration;

import java.util.Objects;

/**
 * 引用检查的中性输出值。
 */
public record ReferenceCheckResult(Status status, String safeSummary) {
    private static final int MAX_SAFE_SUMMARY_LENGTH = 240;

    public ReferenceCheckResult {
        status = Objects.requireNonNull(status, "status 不能为空");
        safeSummary = normalizeSafeSummary(safeSummary);
    }

    public static ReferenceCheckResult clear(String safeSummary) {
        return new ReferenceCheckResult(Status.CLEAR, safeSummary);
    }

    public static ReferenceCheckResult referenced(String safeSummary) {
        return new ReferenceCheckResult(Status.REFERENCED, safeSummary);
    }

    public static ReferenceCheckResult indeterminate(String safeSummary) {
        return new ReferenceCheckResult(Status.INDETERMINATE, safeSummary);
    }

    /**
     * 仅返回可对外记录或展示的简短摘要；原始异常必须留在实现方内部日志中。
     */
    private static String normalizeSafeSummary(String value) {
        if (value == null) {
            throw new IllegalArgumentException("safeSummary 不能为空");
        }
        String normalized = value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\p{Cntrl}", " ")
                .trim()
                .replaceAll(" +", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("safeSummary 不能为空白");
        }
        return normalized.length() <= MAX_SAFE_SUMMARY_LENGTH
                ? normalized
                : normalized.substring(0, MAX_SAFE_SUMMARY_LENGTH);
    }

    public enum Status {
        CLEAR,
        REFERENCED,
        INDETERMINATE
    }
}

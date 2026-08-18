package com.ccb.requirement.support;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** 请求字段取值与受控枚举校验。 */
public final class RequirementValues {
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"));

    private RequirementValues() {
    }

    public static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static String requireText(Map<String, Object> values, String key, String message) {
        String value = text(values, key);
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return value;
    }

    public static String date(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        if (value instanceof Number number) {
            LocalDate excelDate = LocalDate.of(1899, 12, 30).plusDays(number.longValue());
            return excelDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, formatter).format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "日期格式不正确（应为 yyyy-MM-dd）：" + raw);
    }

    public static void requireOption(String field, String value) {
        if (value == null || value.isBlank() || RequirementEnums.isOption(field, value)) {
            return;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "字段取值不在受控枚举内：" + field + " = " + value);
    }

    public static int intOf(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数值字段格式不正确：" + value);
        }
    }
}

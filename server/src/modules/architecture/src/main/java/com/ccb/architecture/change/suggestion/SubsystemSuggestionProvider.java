package com.ccb.architecture.change.suggestion;

import java.util.Map;
import java.util.Objects;
import java.util.List;

/**
 * 仅生成候选建议，不直接修改申请草稿或发布主记录。
 */
public interface SubsystemSuggestionProvider {

    /**
     * 根据当前草稿字段生成候选建议。调用方必须显式采用返回值。
     */
    List<Suggestion> suggest(SuggestionRequest request);

    /**
     * 当前草稿中可供 provider 判断的字段快照。
     */
    record SuggestionRequest(Map<String, String> fieldValues) {
        public SuggestionRequest {
            fieldValues = fieldValues == null ? Map.of() : Map.copyOf(fieldValues);
        }

        public boolean hasText(String field) {
            String value = fieldValues.get(field);
            return value != null && !value.isBlank();
        }
    }

    /**
     * 不可变建议值，来源和说明用于向用户解释候选值的产生方式。
     */
    record Suggestion(String field, String value, String source, String explanation) {
        public Suggestion {
            field = requiredText(field, "field");
            value = requiredText(value, "value");
            source = requiredText(source, "source");
            explanation = requiredText(explanation, "explanation");
        }
    }

    private static String requiredText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " 不能为空").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空白");
        }
        return normalized;
    }
}

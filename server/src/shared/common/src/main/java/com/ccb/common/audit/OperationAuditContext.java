package com.ccb.common.audit;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Request-scoped bridge between domain audit calls and the global HTTP audit interceptor. */
public final class OperationAuditContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private OperationAuditContext() {
    }

    public static void begin() {
        CURRENT.set(new State());
    }

    public static boolean capture(String operationCode, String targetType, String targetId, String errorMessage) {
        State state = CURRENT.get();
        if (state == null) return false;
        state.operationCode = normalize(operationCode, 128);
        state.targetType = normalize(targetType, 64);
        state.targetId = normalize(targetId, 128);
        state.errorMessage = errorMessage == null || errorMessage.isBlank() ? null : "Business operation failed";
        return true;
    }

    public static void addChangedFields(Collection<String> fields) {
        State state = CURRENT.get();
        if (state == null || fields == null) return;
        fields.stream()
                .map(OperationAuditContext::normalizeField)
                .filter(field -> field != null && !isSensitiveField(field))
                .limit(50)
                .forEach(state.changedFields::add);
    }

    public static Snapshot snapshot() {
        State state = CURRENT.get();
        return state == null ? Snapshot.empty() : new Snapshot(state.operationCode, state.targetType,
                state.targetId, state.errorMessage, Set.copyOf(state.changedFields));
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String normalizeField(String value) {
        String normalized = normalize(value, 80);
        return normalized != null && normalized.matches("[A-Za-z0-9_.-]+") ? normalized : null;
    }

    private static boolean isSensitiveField(String field) {
        String normalized = field.toLowerCase();
        return normalized.contains("password") || normalized.contains("token")
                || normalized.contains("secret") || normalized.contains("credential")
                || normalized.contains("file") || normalized.contains("content")
                || normalized.contains("comment") || normalized.contains("remark");
    }

    private static final class State {
        private String operationCode;
        private String targetType;
        private String targetId;
        private String errorMessage;
        private final LinkedHashSet<String> changedFields = new LinkedHashSet<>();
    }

    public record Snapshot(String operationCode, String targetType, String targetId,
                           String errorMessage, Set<String> changedFields) {
        private static Snapshot empty() {
            return new Snapshot(null, null, null, null, Set.of());
        }
    }
}

package com.ccb.boot.audit;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class OperationAuditPolicy {
    private static final List<Pattern> SENSITIVE_GETS = List.of(
            pattern("/api/system/(users|roles|menus|orgs|params|param-categories)(/.*)?"),
            pattern("/api/workflows/(tasks/[^/]+/(context)|instances/[^/]+/(detail|timeline))"),
            pattern("/api/(attachments|project/[^/]+/attachments)/[^/]+/(preview|download)"),
            pattern("/api/data-migration/(issues/[^/]+|reports/[^/]+/download|assets/[^/]+/download)"),
            pattern("/api/system/audit/(operations|logins)")
    );
    private static final List<Pattern> EXCLUDED_WRITES = List.of(
            pattern("/api/auth/(login|refresh|logout)"),
            pattern("/api/.*/(preview|code-preview|check-md5)"),
            pattern("/api/requirements/imports/preview"),
            pattern("/api/.*/import/preview"),
            pattern("/api/release/applications(/[^/]+)?/conflicts/preview")
    );

    public Decision decide(String method, String path) {
        String normalizedMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        String normalizedPath = normalizePath(path);
        if (!normalizedPath.startsWith("/api/") || "/actuator/health".equals(normalizedPath)) {
            return Decision.skip();
        }
        if ("GET".equals(normalizedMethod)) {
            if (isExport(normalizedPath)) {
                return describe(normalizedPath, "EXPORT");
            }
            return matches(SENSITIVE_GETS, normalizedPath)
                    ? describe(normalizedPath, "SENSITIVE_QUERY") : Decision.skip();
        }
        if (!List.of("POST", "PUT", "PATCH", "DELETE").contains(normalizedMethod)
                || matches(EXCLUDED_WRITES, normalizedPath)) {
            return Decision.skip();
        }
        String type = approval(normalizedPath) ? "APPROVAL"
                : normalizedPath.contains("/import") ? "IMPORT"
                : isExport(normalizedPath) ? "EXPORT"
                : switch (normalizedMethod) {
                    case "POST" -> "CREATE";
                    case "DELETE" -> "DELETE";
                    default -> "UPDATE";
                };
        return describe(normalizedPath, type);
    }

    private Decision describe(String path, String operationType) {
        String[] segments = path.substring("/api/".length()).split("/");
        String first = segments.length == 0 ? "platform" : segments[0];
        Module module = module(first);
        String targetType = targetType(segments);
        return new Decision(true, module.code(), module.name(), operationType, targetType,
                module.code() + ":" + targetType + ":" + operationType.toLowerCase(Locale.ROOT));
    }

    private String targetType(String[] segments) {
        for (int index = 1; index < segments.length; index++) {
            String segment = segments[index];
            if (!segment.isBlank() && !segment.matches("[0-9]+") && !looksLikeBusinessId(segment)
                    && !List.of("preview", "download", "export", "import", "detail", "timeline").contains(segment)) {
                return segment.length() <= 64 ? segment : segment.substring(0, 64);
            }
        }
        return segments.length == 0 || segments[0].isBlank() ? "platform" : truncate(segments[0], 64);
    }

    private boolean looksLikeBusinessId(String value) {
        return value.length() > 12 && value.matches("[A-Za-z]+[-_][A-Za-z0-9_-]+");
    }

    private boolean approval(String path) {
        return path.matches(".*/(approve|reject|return|withdraw|terminate|actions)(/.*)?$")
                || path.contains("/tasks/");
    }

    private boolean isExport(String path) {
        return path.endsWith("/export") || path.contains("/export/") || path.endsWith("/download");
    }

    private boolean matches(List<Pattern> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    private static Pattern pattern(String value) {
        return Pattern.compile("^" + value + "$");
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    private Module module(String segment) {
        return switch (segment) {
            case "system" -> new Module("system", "系统管理");
            case "project" -> new Module("project", "项目管理");
            case "workflows" -> new Module("workflow", "工作流");
            case "release" -> new Module("release", "配置管理");
            case "requirements" -> new Module("requirement", "需求管理");
            case "architecture" -> new Module("architecture", "架构管理");
            case "test-management" -> new Module("test-management", "测试管理");
            case "data-migration" -> new Module("data-migration", "数据迁移");
            case "attachments", "file-previews" -> new Module("attachment", "附件管理");
            case "ai" -> new Module("ai", "智能能力");
            case "auth" -> new Module("security", "账号安全");
            default -> new Module(truncate(segment, 64), "平台能力");
        };
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record Module(String code, String name) {
    }

    public record Decision(boolean included, String moduleCode, String moduleName,
                           String operationType, String targetType, String operationCode) {
        private static Decision skip() {
            return new Decision(false, null, null, null, null, null);
        }
    }
}

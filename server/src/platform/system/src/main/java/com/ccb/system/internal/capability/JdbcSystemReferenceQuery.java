package com.ccb.system.internal.capability;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class JdbcSystemReferenceQuery implements SystemReferenceQuery {
    private final SystemCapabilityRepository repository;

    public JdbcSystemReferenceQuery(SystemCapabilityRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<SystemUserReference> searchActiveUsers(AuthUser actor, PageQuery page, String keyword) {
        Objects.requireNonNull(actor, "actor 不能为空");
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        String normalizedKeyword = normalizeKeyword(keyword);
        String pattern = normalizedKeyword == null ? null : "%" + escapeLike(normalizedKeyword) + "%";
        Map<String, Object> params = params("tenantId", actor.tenantId(), "keyword", pattern, "size", normalizedPage.size(), "offset", (normalizedPage.page() - 1) * normalizedPage.size());
        return new PageResult<>(repository.activeUsers(params).stream().map(this::user).toList(), repository.activeUserCount(params), normalizedPage.page(), normalizedPage.size());
    }

    @Override
    public Optional<SystemUserReference> findUser(AuthUser actor, long userId, boolean activeOnly) {
        Objects.requireNonNull(actor, "actor 不能为空");
        return Optional.ofNullable(repository.user(params("userId", userId, "tenantId", actor.tenantId(), "activeOnly", activeOnly))).map(this::user);
    }

    @Override
    public List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode) {
        Objects.requireNonNull(actor, "actor 不能为空");
        if (categoryCode == null || categoryCode.isBlank()) {
            return List.of();
        }
        String normalizedCategory = categoryCode.trim().toUpperCase(Locale.ROOT);
        return repository.activeParameters(params("tenantId", actor.tenantId(), "categoryCode", normalizedCategory)).stream()
                .map(row -> new SystemParameterReference(String.valueOf(row.get("config_key")), String.valueOf(row.get("config_value")))).toList();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private SystemUserReference user(Map<String, Object> row) { return new SystemUserReference(((Number) row.get("id")).longValue(), String.valueOf(row.get("display_name")), String.valueOf(row.get("username")), row.get("mobile_phone") == null ? null : String.valueOf(row.get("mobile_phone")), ((Number) row.get("status")).intValue() == 1); }
    private Map<String, Object> params(Object... entries) { Map<String, Object> result = new LinkedHashMap<>(); for (int index = 0; index < entries.length; index += 2) result.put(String.valueOf(entries[index]), entries[index + 1]); return result; }
}

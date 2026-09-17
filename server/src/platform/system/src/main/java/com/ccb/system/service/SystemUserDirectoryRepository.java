package com.ccb.system.service;

import com.ccb.system.model.UserDirectoryItem;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SystemUserDirectoryRepository {
    private final SystemUserDirectoryMapper mapper;

    public SystemUserDirectoryRepository(SystemUserDirectoryMapper mapper) { this.mapper = mapper; }

    public List<UserDirectoryItem> listActive(long tenantId, String keyword, int limit) {
        return mapper.selectActive(tenantId, keyword, Math.max(1, Math.min(limit, 100))).stream().map(this::toItem).toList();
    }

    public Optional<UserDirectoryItem> findActive(long tenantId, long userId) {
        return Optional.ofNullable(mapper.selectActiveById(tenantId, userId)).map(this::toItem);
    }

    private UserDirectoryItem toItem(Map<String, Object> row) {
        return new UserDirectoryItem(((Number) row.get("id")).longValue(), (String) row.get("username"),
                (String) row.get("display_name"), ((Number) row.get("org_id")).longValue(),
                (String) row.get("org_name"), (String) row.get("mobile_phone"));
    }
}

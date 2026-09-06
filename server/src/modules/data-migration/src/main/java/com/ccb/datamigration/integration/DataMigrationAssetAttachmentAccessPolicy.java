package com.ccb.datamigration.integration;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.datamigration.service.ContentAssetTables;
import com.ccb.datamigration.service.ContentFileAssetService;
import com.ccb.datamigration.service.DataMigrationPermissionService;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DataMigrationAssetAttachmentAccessPolicy implements AttachmentAccessPolicy {
    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;

    public DataMigrationAssetAttachmentAccessPolicy(JdbcTemplate jdbc, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Override
    public String businessType() { return ContentFileAssetService.BUSINESS_TYPE; }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null) return false;
        long id;
        try { id = Long.parseLong(businessKey); } catch (NumberFormatException ex) { return false; }
        // 跨 10 张内容表按 (id, tenant) 定位归属行；与注入服务解耦，避免 AttachmentGateway→Registry→Policy 循环依赖。
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (String table : ContentAssetTables.ALL_TABLES) {
            if (sql.length() > 0) sql.append(" UNION ALL ");
            sql.append("SELECT '").append(ContentAssetTables.typeFor(table)).append("' AS asset_type, owner_id, deleted FROM ")
               .append(table).append(" WHERE id = ? AND tenant_id = ?");
            args.add(id); args.add(user.tenantId());
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        if (rows.isEmpty()) return false;
        var row = rows.get(0);
        boolean deleted = ((Number) row.get("deleted")).intValue() != 0;
        if (deleted && operation != AttachmentOperation.DELETE) return false;
        if (operation == AttachmentOperation.DELETE) return permissions.isAdmin(user) || ((Number) row.get("owner_id")).longValue() == user.id();
        return true;
    }
}

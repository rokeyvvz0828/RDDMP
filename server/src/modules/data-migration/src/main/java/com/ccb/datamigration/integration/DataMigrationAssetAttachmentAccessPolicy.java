package com.ccb.datamigration.integration;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.datamigration.service.AssetService;
import com.ccb.datamigration.service.DataMigrationPermissionService;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataMigrationAssetAttachmentAccessPolicy implements AttachmentAccessPolicy {
    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;

    public DataMigrationAssetAttachmentAccessPolicy(JdbcTemplate jdbc, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Override
    public String businessType() { return AssetService.BUSINESS_TYPE; }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null) return false;
        long id;
        try { id = Long.parseLong(businessKey); } catch (NumberFormatException ex) { return false; }
        var rows = jdbc.queryForList("SELECT owner_id, deleted FROM dm_asset WHERE id = ? AND tenant_id = ?", id, user.tenantId());
        if (rows.isEmpty()) return false;
        var row = rows.get(0);
        boolean deleted = ((Number) row.get("deleted")).intValue() != 0;
        if (deleted && operation != AttachmentOperation.DELETE) return false;
        if (operation == AttachmentOperation.DELETE) return permissions.isAdmin(user) || ((Number) row.get("owner_id")).longValue() == user.id();
        return true;
    }
}

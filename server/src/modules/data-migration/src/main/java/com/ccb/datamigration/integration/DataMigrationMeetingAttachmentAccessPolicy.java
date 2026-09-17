package com.ccb.datamigration.integration;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.datamigration.service.DataMigrationPermissionService;
import com.ccb.datamigration.service.MeetingService;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;
import java.util.Map;

/** 附件平台访问策略：会议附件只能随所属会议的租户和生命周期访问。 */
@Component
public class DataMigrationMeetingAttachmentAccessPolicy implements AttachmentAccessPolicy {
    private final DataMigrationAttachmentAccessMapper mapper;
    private final DataMigrationPermissionService permissions;

    public DataMigrationMeetingAttachmentAccessPolicy(DataMigrationAttachmentAccessMapper mapper, DataMigrationPermissionService permissions) {
        this.mapper = mapper;
        this.permissions = permissions;
    }

    @Override
    public String businessType() {
        return MeetingService.BUSINESS_TYPE;
    }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null) return false;
        long meetingId;
        try {
            meetingId = Long.parseLong(businessKey);
        } catch (NumberFormatException ex) {
            return false;
        }
        var row = mapper.meeting(Map.of("id", meetingId, "tenantId", user.tenantId()));
        if (row == null) return false;
        boolean deleted = ((Number) row.get("deleted")).intValue() != 0;
        if (operation != AttachmentOperation.DELETE && deleted) return false;
        return operation == AttachmentOperation.DELETE
                ? permissions.isAdmin(user) || ((Number) row.get("created_by")).longValue() == user.id()
                : true;
    }
}

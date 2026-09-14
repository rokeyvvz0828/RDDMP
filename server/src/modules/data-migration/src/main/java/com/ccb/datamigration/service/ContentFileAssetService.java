package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件型内容资产通用能力（REQ-20260911-070 通用链路下线后的保留面）：
 * 仅承载被专属服务复用的附件能力（BUSINESS_TYPE、resolveAttachment、replaceMainFile）；
 * 各内容类型的列表、上传、软删与回收站均由专属 Service/Controller/RecycleBinSource 承接。
 */
@Service
public class ContentFileAssetService {
    public static final String BUSINESS_TYPE = "DATA_MIGRATION_ASSET";

    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;
    private final ContentAttachmentService attachments;

    @Autowired
    public ContentFileAssetService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway,
                                   ContentAttachmentService attachments) {
        this.jdbc = jdbc;
        this.attachmentGateway = attachmentGateway;
        this.attachments = attachments;
    }

    /** 附件投影写入口：文件型资产主文件替换（sort_order=0），旧主文件解绑。 */
    @Transactional
    public void replaceMainFile(String businessType, long businessId, long projectId, AttachmentItem attachment, AuthUser user) {
        List<Long> oldIds = jdbc.queryForList(
                "SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND sort_order = 0 AND deleted = 0",
                Long.class, user.tenantId(), businessType, businessId);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("attachmentId", attachment.id());
        entry.put("fileName", attachment.fileName());
        attachments.replaceAll(businessType, BUSINESS_TYPE, businessId, projectId, List.of(entry), user);
        for (Long oldId : oldIds) {
            if (oldId != attachment.id()) {
                attachmentGateway.deleteBound(oldId, BUSINESS_TYPE, String.valueOf(businessId), user);
            }
        }
    }

    public AttachmentItem resolveAttachment(Long attachmentId, AuthUser user) {
        if (attachmentId == null || attachmentId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先通过公共附件接口上传文件");
        }
        AttachmentItem item = attachmentGateway.get(attachmentId, user);
        if (item.uploaderId() != user.id() || !"TEMP".equals(item.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "附件必须是当前用户上传的临时附件");
        }
        return item;
    }
}

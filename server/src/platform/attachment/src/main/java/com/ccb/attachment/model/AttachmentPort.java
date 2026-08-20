package com.ccb.attachment.model;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.web.multipart.MultipartFile;

/** 业务模块使用的持久附件公开契约。 */
public interface AttachmentPort {
    AttachmentItem uploadAndBind(String businessType, long businessId, MultipartFile file, long tenantId, long uploaderId);

    PageResult<AttachmentItem> list(String businessType, long businessId, long tenantId, PageQuery pageQuery, String keyword);

    AttachmentLink preview(long attachmentId, String businessType, long businessId, long tenantId);

    AttachmentLink download(long attachmentId, String businessType, long businessId, long tenantId);

    void delete(long attachmentId, String businessType, long businessId, long tenantId);
}

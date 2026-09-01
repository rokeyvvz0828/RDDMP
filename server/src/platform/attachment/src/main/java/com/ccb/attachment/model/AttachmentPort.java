package com.ccb.attachment.model;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 业务模块使用的持久附件公开契约。 */
public interface AttachmentPort {
    default AttachmentItem uploadAndBind(String businessType, long businessId, MultipartFile file,
                                         long tenantId, long uploaderId) {
        return uploadAndBind(businessType, businessId, file, null, tenantId, uploaderId);
    }

    AttachmentItem uploadAndBind(String businessType, long businessId, MultipartFile file,
                                 Long categoryId, long tenantId, long uploaderId);

    List<AttachmentCategory> listCategories(String businessType, long businessId, long tenantId);

    AttachmentCategory createCategory(String businessType, long businessId, String name,
                                      long tenantId, long creatorId);

    AttachmentItem updateCategory(long attachmentId, String businessType, long businessId,
                                  Long categoryId, long tenantId);

    PageResult<AttachmentItem> list(String businessType, long businessId, long tenantId, PageQuery pageQuery,
                                    String keyword, Long categoryId);

    AttachmentLink preview(long attachmentId, String businessType, long businessId, long tenantId);

    AttachmentLink download(long attachmentId, String businessType, long businessId, long tenantId);

    void delete(long attachmentId, String businessType, long businessId, long tenantId);
}

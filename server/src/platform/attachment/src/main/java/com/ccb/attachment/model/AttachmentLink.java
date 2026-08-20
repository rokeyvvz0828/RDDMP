package com.ccb.attachment.model;

/** 附件的短时访问地址，不向调用方暴露对象键。 */
public record AttachmentLink(
        long attachmentId,
        String fileName,
        String url
) {
}

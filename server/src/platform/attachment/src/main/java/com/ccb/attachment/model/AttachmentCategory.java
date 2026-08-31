package com.ccb.attachment.model;

/** 项目附件分类元数据，不包含对象存储信息。 */
public record AttachmentCategory(
        long id,
        String name,
        int sortNo
) {
}

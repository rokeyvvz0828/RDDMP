package com.ccb.filepreview.model;

/** 为平台业务对象生成受信任的 kkFileView 预览地址。 */
public interface FilePreviewUrlProvider {
    String build(String sourceUrl);
}

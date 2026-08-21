/*
 * 文件：server/src/platform/system/src/main/java/com/ccb/system/model/UserDirectoryItem.java
 * 说明：系统用户目录对业务模块公开的最小只读用户信息。
 * 用途：传递用户主键、账号、显示名、组织和手机号，不暴露认证敏感字段。
 * 作者：hengguan
 */
package com.ccb.system.model;

public record UserDirectoryItem(long id, String username, String displayName, long orgId,
                                String orgName, String mobilePhone) {
}

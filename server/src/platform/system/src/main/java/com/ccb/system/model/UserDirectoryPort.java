/*
 * 文件：server/src/platform/system/src/main/java/com/ccb/system/model/UserDirectoryPort.java
 * 说明：平台系统模块提供的有效用户只读目录契约。
 * 用途：允许营业日等业务模块按租户查询并校验提出人，禁止直接读取系统用户表。
 * 作者：hengguan
 */
package com.ccb.system.model;

import java.util.List;
import java.util.Optional;

public interface UserDirectoryPort {
    List<UserDirectoryItem> listActive(long tenantId, String keyword, int limit);

    Optional<UserDirectoryItem> findActive(long tenantId, long userId);
}

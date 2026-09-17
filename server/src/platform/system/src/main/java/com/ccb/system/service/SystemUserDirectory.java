/*
 * 文件：server/src/platform/system/src/main/java/com/ccb/system/service/SystemUserDirectory.java
 * 说明：系统用户只读目录的 Repository 适配实现。
 * 用途：按租户、启用状态和删除标记查询必要用户展示字段。
 * 作者：hengguan
 */
package com.ccb.system.service;

import com.ccb.system.model.UserDirectoryItem;
import com.ccb.system.model.UserDirectoryPort;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class SystemUserDirectory implements UserDirectoryPort {
    private final SystemUserDirectoryRepository repository;

    public SystemUserDirectory(SystemUserDirectoryRepository repository) {
        this.repository = repository;
    }

    // 关键逻辑：查询强制绑定 tenantId、status=1、deleted=0，并把返回数量限制在 100 以内。
    @Override
    public List<UserDirectoryItem> listActive(long tenantId, String keyword, int limit) {
        return repository.listActive(tenantId, keyword == null ? null : keyword.trim(), limit);
    }

    @Override
    public Optional<UserDirectoryItem> findActive(long tenantId, long userId) {
        return repository.findActive(tenantId, userId);
    }
}

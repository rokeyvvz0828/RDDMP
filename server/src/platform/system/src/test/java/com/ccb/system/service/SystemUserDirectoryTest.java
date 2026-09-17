/*
 * 文件：server/src/platform/system/src/test/java/com/ccb/system/service/SystemUserDirectoryTest.java
 * 说明：系统用户目录公开契约的适配器测试。
 * 用途：验证业务模块可通过租户范围的目录查询获得最小用户信息。
 * 作者：hengguan
 */
package com.ccb.system.service;

import com.ccb.system.model.UserDirectoryItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserDirectoryTest {
    @Mock SystemUserDirectoryRepository repository;

    @Test
    void listsOnlyThroughTheTenantScopedActiveUserQuery() {
        UserDirectoryItem expected = new UserDirectoryItem(7, "tester", "测试员", 2, "测试部", "13800000000");
        when(repository.listActive(9, "测试", 999)).thenReturn(List.of(expected));

        List<UserDirectoryItem> result = new SystemUserDirectory(repository).listActive(9, "测试", 999);

        assertEquals(List.of(expected), result);
    }
}

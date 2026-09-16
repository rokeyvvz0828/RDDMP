package com.ccb.architecture.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.Optional;

/** 开发任务接入的最小只读系统投影；find 不授予管理权限，由消费者执行实体授权。 */
public interface PhysicalSystemDirectoryQuery {
    PageResult<SystemRef> searchManageable(AuthUser actor, long projectId, PageQuery page, String keyword);
    Optional<SystemRef> find(AuthUser actor, long projectId, long systemId);

    record SystemRef(long id, String code, String name, Long ownerId, String status, long rowVersion) {}
}

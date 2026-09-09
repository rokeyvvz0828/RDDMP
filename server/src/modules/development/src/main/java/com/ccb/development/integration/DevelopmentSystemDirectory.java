package com.ccb.development.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.Optional;

/** 系统主数据只读端口；任务负责人和指定人员不因此获得系统管理权限。 */
public interface DevelopmentSystemDirectory {
    PageResult<SystemRef> searchManageable(AuthUser actor, long projectId, PageQuery page, String keyword);
    Optional<SystemRef> find(AuthUser actor, long projectId, long systemId);

    record SystemRef(long id, String code, String name, Long ownerId, String status, long rowVersion) {}
}

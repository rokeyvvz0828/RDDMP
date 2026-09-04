package com.ccb.system.capability;

import com.ccb.security.model.AuthUser;

/** 供业务模块执行项目数据范围校验的只读契约。 */
public interface ProjectAccessService {
    ProjectAccess requireAccessible(String projectRef, AuthUser actor);
}

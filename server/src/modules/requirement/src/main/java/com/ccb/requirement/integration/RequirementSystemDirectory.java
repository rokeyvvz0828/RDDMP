package com.ccb.requirement.integration;

import com.ccb.security.model.AuthUser;

import java.util.List;
import java.util.Optional;

/**
 * 需求域读取"涉及系统"的只读端口。
 * <p>需求管理不再自建系统清单，物理子系统主数据统一由架构管理维护；
 * 由组合根 platform/boot 提供实现，需求模块不直接依赖 business/architecture。
 */
public interface RequirementSystemDirectory {
    /** 按项目列出有效物理子系统（可按编码/名称关键字过滤）。 */
    List<SystemRef> searchActive(AuthUser actor, long projectId, String keyword);

    Optional<SystemRef> find(AuthUser actor, long projectId, long systemId);

    /** 按物理子系统编码查询有效系统；编码在租户内唯一（uk_arch_physical_code）。 */
    Optional<SystemRef> findByCode(AuthUser actor, long projectId, String code);

    record SystemRef(long id, String code, String name, String businessGroup, Long ownerId, String status) {
    }
}

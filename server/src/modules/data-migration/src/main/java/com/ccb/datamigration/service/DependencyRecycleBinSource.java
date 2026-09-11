package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 迁移过程依赖关系（{@code dm_dependency}）接入统一回收站的来源（REQ-20260910-068）。
 *
 * <p>DEPENDENCY 从通用文件资产链路（{@link ContentFileAssetService}）
 * 剥离后，由本来源承接其软删计数、列表、详情、恢复与彻底删除，全部委托 {@link DependencyService}，
 * 保留管理员校验、参数/系统有效性复验、关系唯一冲突与审计规则。
 */
@Component
public class DependencyRecycleBinSource implements RecycleBinSource {

    private final DependencyService dependencyService;

    public DependencyRecycleBinSource(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("DEPENDENCY");
    }

    @Override
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        return dependencyService.countDeleted(type, projectId, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        return dependencyService.listDeletedPage(type, projectId, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return dependencyService.detail(type, id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        dependencyService.restore(type, ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        dependencyService.purge(type, ids, user);
    }
}

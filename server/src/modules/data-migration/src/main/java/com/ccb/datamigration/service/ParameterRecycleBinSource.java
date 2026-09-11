package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 迁移参数（{@code dm_parameter}）接入统一回收站的来源（REQ-20260906-067）。
 *
 * <p>PARAMETER 已由通用结构化资产链路域化为专属业务表（{@code dm_parameter}），本来源承接其
 * 软删列表、详情、恢复与彻底删除，全部委托 {@link ParameterService}，保留管理员校验、参数名称唯一冲突
 * 与审计规则。
 */
@Component
public class ParameterRecycleBinSource implements RecycleBinSource {

    private final ParameterService parameterService;

    public ParameterRecycleBinSource(ParameterService parameterService) {
        this.parameterService = parameterService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("PARAMETER");
    }

    @Override
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        return parameterService.countRecycleBin(projectId, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        return parameterService.fetchRecycleBinPage(projectId, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return parameterService.findRecycleBinDetail(id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        parameterService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        parameterService.purge(ids, user);
    }
}

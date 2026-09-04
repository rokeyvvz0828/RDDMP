package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 迁移方案（{@code dm_plan}）接入统一回收站的来源（REQ-20260820-031 增量）。
 *
 * <p>PLAN 从通用文件型资产链路（{@link ContentFileAssetService#MANAGED_TYPES}）剥离后，由本来源承接其
 * 软删列表、详情、恢复与彻底删除，全部委托 {@link PlanService}，原样保留管理员校验、活动维度唯一冲突翻译、
 * 附件解绑与审计规则。{@code ContentRecycleBinService} 按各来源 {@code supports()} 建注册表并禁止重复认领，
 * 因此 {@code MANAGED_TYPES} 移除 PLAN 与本来源认领 PLAN 必须同批生效。
 */
@Component
public class PlanRecycleBinSource implements RecycleBinSource {

    private final PlanService planService;

    public PlanRecycleBinSource(PlanService planService) {
        this.planService = planService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("PLAN");
    }

    @Override
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        return planService.countRecycleBin(projectId, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        return planService.fetchRecycleBinPage(projectId, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return planService.findRecycleBinDetail(id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        planService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        planService.purge(ids, user);
    }
}

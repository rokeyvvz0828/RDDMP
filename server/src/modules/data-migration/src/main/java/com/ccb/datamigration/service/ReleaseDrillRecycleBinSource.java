package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 投产及演练资料（{@code dm_release_drill}）接入统一回收站的来源（REQ-20260820-031 增量）。
 *
 * <p>RELEASE_DRILL 从通用文件型资产链路（{@link ContentFileAssetService#MANAGED_TYPES}）剥离后，
 * 由本来源承接其软删列表、详情、恢复与彻底删除，全部委托 {@link ReleaseDrillService}，
 * 原样保留管理员校验、附件解绑与审计规则。{@code ContentRecycleBinService} 按各来源
 * {@code supports()} 建注册表并禁止重复认领，因此 MANAGED_TYPES 移除 RELEASE_DRILL 与本来源
 * 认领 RELEASE_DRILL 必须同批生效。
 */
@Component
public class ReleaseDrillRecycleBinSource implements RecycleBinSource {

    private final ReleaseDrillService releaseDrillService;

    public ReleaseDrillRecycleBinSource(ReleaseDrillService releaseDrillService) {
        this.releaseDrillService = releaseDrillService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("RELEASE_DRILL");
    }

    @Override
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        return releaseDrillService.countRecycleBin(projectId, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        return releaseDrillService.fetchRecycleBinPage(projectId, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return releaseDrillService.findRecycleBinDetail(id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        releaseDrillService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        releaseDrillService.purge(ids, user);
    }
}

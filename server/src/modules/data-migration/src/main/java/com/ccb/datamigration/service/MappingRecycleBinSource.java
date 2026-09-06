package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 迁移映射（{@code dm_mapping_doc}）接入统一回收站的来源（REQ-20260820-031 增量）。
 *
 * <p>MAPPING_DOC 从通用文件型资产链路（{@link ContentFileAssetService#MANAGED_TYPES}）剥离后，
 * 由本来源承接其软删列表、详情、恢复与彻底删除，全部委托 {@link MappingService}，
 * 原样保留管理员校验、附件解绑与审计规则。
 */
@Component
public class MappingRecycleBinSource implements RecycleBinSource {

    private final MappingService mappingService;

    public MappingRecycleBinSource(MappingService mappingService) {
        this.mappingService = mappingService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("MAPPING_DOC");
    }

    @Override
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        return mappingService.countRecycleBin(projectId, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        return mappingService.fetchRecycleBinPage(projectId, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return mappingService.findRecycleBinDetail(id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        mappingService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        mappingService.purge(ids, user);
    }
}

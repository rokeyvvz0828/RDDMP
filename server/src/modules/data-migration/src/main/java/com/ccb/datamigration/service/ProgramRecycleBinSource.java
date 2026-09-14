package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 迁移程序（{@code dm_script}）接入统一回收站的来源（REQ-20260820-031 增量）。
 *
 * <p>SCRIPT 已由通用文件型资产链路域化为专属业务表（{@code dm_script}），
 * 本来源承接其软删列表、详情、恢复与彻底删除，全部委托 {@link ProgramService}，原样保留管理员
 * 校验、多附件解绑与审计规则。
 */
@Component
public class ProgramRecycleBinSource implements RecycleBinSource {

    private final ProgramService programService;

    public ProgramRecycleBinSource(ProgramService programService) {
        this.programService = programService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("SCRIPT");
    }

    @Override
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        return programService.countRecycleBin(projectId, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        return programService.fetchRecycleBinPage(projectId, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return programService.findRecycleBinDetail(id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        programService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        programService.purge(ids, user);
    }
}

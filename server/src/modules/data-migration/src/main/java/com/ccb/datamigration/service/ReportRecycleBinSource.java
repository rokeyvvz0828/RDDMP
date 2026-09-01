package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 汇报材料（{@code dm_report}）接入统一回收站的来源。
 *
 * <p>{@code dm_report} 本就是文件型内容表之一（见 {@link ContentAssetTables#FILE_TABLES}），行结构与
 * 其余内容资产同构，可直接投影为统一信封列。列表、恢复与彻底删除全部委托 {@link ReportService}，
 * 从而原样保留其管理员校验、MD5 查重域、附件解绑与审计规则——统一回收站不复制这些业务逻辑。
 *
 * <p>T26 已下线旧 {@code /reports/recycle-bin} 端点，本来源为统一回收站唯一入口；不改变既有汇报材料页行为。
 */
@Component
public class ReportRecycleBinSource implements RecycleBinSource {

    private final ReportService reportService;

    public ReportRecycleBinSource(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("REPORT");
    }

    @Override
    public long countDeleted(String type, String keyword, AuthUser user) {
        return reportService.countRecycleBin(null, null, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, String keyword, int limit, AuthUser user) {
        return reportService.fetchRecycleBinPage(null, null, keyword, limit, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        reportService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        reportService.purge(ids, user);
    }
}

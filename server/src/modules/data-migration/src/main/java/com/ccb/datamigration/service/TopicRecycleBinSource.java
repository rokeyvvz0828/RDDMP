package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 专题材料（{@code dm_topic}）接入统一回收站的来源（REQ-20260820-031 增量）。
 *
 * <p>TOPIC 已由通用文件型资产链路域化为专属业务表（{@code dm_topic}），本来源承接其
 * 软删列表、详情、恢复与彻底删除，全部委托 {@link TopicService}，原样保留管理员校验、状态冲突翻译、
 * 系统关系清理、附件解绑与审计规则。{@code ContentRecycleBinService} 按各来源 {@code supports()} 建注册表
 * 并禁止重复认领。
 */
@Component
public class TopicRecycleBinSource implements RecycleBinSource {

    private final TopicService topicService;

    public TopicRecycleBinSource(TopicService topicService) {
        this.topicService = topicService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("TOPIC");
    }

    @Override
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        return topicService.countRecycleBin(projectId, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        return topicService.fetchRecycleBinPage(projectId, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return topicService.findRecycleBinDetail(id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        topicService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        topicService.purge(ids, user);
    }
}

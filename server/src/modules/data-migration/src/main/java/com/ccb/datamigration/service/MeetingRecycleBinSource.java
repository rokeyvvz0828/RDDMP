package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 会议纪要（{@code dm_meeting}）文档级统一回收站来源。
 *
 * <p>统一回收站仅纳入文档级会议；<b>附件级（{@code dm_content_attachment business_type='MEETING'}）
 * 仍保留在 {@code /meetings} 页内的独立回收站</b>，原因：
 * <ul>
 *   <li>附件行模型（{@code attachment_id/file_name/business_id}）不属于内容资产信封；</li>
 *   <li>恢复受 {@code uk_dm_meeting_att_active} 约束且需要校验父会议未删，属属主专属规则；</li>
 *   <li>跨会议全局与单会议内两种视图与统一页"一行=一条资产"的语义冲突。</li>
 * </ul>
 *
 * <p>列表/恢复/彻底删除全部委托 {@link MeetingService#countRecycleBin}、{@link MeetingService#fetchRecycleBinPage}、{@link MeetingService#restore}
 * 与 {@link MeetingService#purge}，原样保留管理员校验、状态冲突（CONFLICT）、关系级联
 * （{@code dm_meeting_system} 与 {@code dm_issue_relation}）、附件绑定/解绑与审计；统一层不复制这些规则。
 * T26 已下线旧 {@code /meetings/recycle-bin} 端点，本来源为统一回收站唯一入口。
 *
 * <p>V103 为 {@code dm_meeting} 新增 {@code meeting_code}（真实业务编号），统一信封 {@code asset_code} 从该列投影，
 * 与 {@code dm_report.doc_code} / {@code dm_issue.issue_code} 同构。
 */
@Component
public class MeetingRecycleBinSource implements RecycleBinSource {

    private final MeetingService meetingService;

    public MeetingRecycleBinSource(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @Override
    public Set<String> supports() {
        return Set.of("MEETING");
    }

    @Override
    public long countDeleted(String type, String keyword, AuthUser user) {
        return meetingService.countRecycleBin(null, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, String keyword, int limit, AuthUser user) {
        List<Map<String, Object>> raw = meetingService.fetchRecycleBinPage(null, keyword, limit, user);
        List<Map<String, Object>> projected = new ArrayList<>(raw.size());
        for (Map<String, Object> row : raw) projected.add(project(row));
        return projected;
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return project(meetingService.findRecycleBinDetail(id, user));
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        meetingService.restore(ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        meetingService.purge(ids, user);
    }

    /**
     * 把会议纪要行投影为统一信封列（{@code id / asset_type / asset_code / asset_name}），保留原有列作为类型专属附加列。
     * 抽出为静态方法以便在无 Spring 上下文条件下单元测试。
     *
     * <p>V103 为 {@code dm_meeting} 新增 {@code meeting_code} 列后，{@code MeetingService.baseSelect} 已直接将其
     * 别名为 {@code asset_code}；本方法仍显式从 {@code meeting_code} 回写到 {@code asset_code}，避免不同来源行（历史
     * 射内测、无 SQL 别名的直接写入）导致回收站“编号”列回退为空。
     */
    static Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        Object meetingId = row.get("meeting_id");
        if (meetingId != null) out.put("id", meetingId);
        out.put("asset_type", "MEETING");
        Object title = row.get("meeting_title");
        if (title != null) out.put("asset_name", title);
        // V103 后会议具备真实业务编号（dm_meeting.meeting_code）；优先取 meeting_code，兼容行内已存在的 asset_code 别名。
        Object code = row.get("meeting_code");
        if (code != null) {
            out.put("asset_code", code);
        } else if (!out.containsKey("asset_code")) {
            // 无编号旧行为：不伪造。统一回收站前端会展示为空白。
            out.remove("asset_code");
        }
        return out;
    }
}

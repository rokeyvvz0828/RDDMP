package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 会议纪要统一回收站来源验证（REQ-20260820-031 演进）：认领标签、信封投影与向后兼容保留原列。
 */
class MeetingRecycleBinSourceTest {

    @Test
    void supportsOnlyMeetingType() {
        // 无需真实 MeetingService 即可断言认领集合，避免与旧端点重复。
        assertEquals(Set.of("MEETING"), new MeetingRecycleBinSource(null).supports());
    }

    @Test
    void projectAddsEnvelopeKeysAndPreservesOriginalColumns() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("meeting_id", 9001L);
        raw.put("meeting_title", "投产演练复盘");
        raw.put("project_name", "示例项目");
        raw.put("granularity", "PROJECT");
        raw.put("meeting_source", "MEETING_MINUTES");
        raw.put("deleted_by_name", "张三");
        raw.put("deleted_at", "2026-09-01 10:00:00");

        Map<String, Object> row = MeetingRecycleBinSource.project(raw);

        assertEquals(9001L, row.get("id"));
        assertEquals("MEETING", row.get("asset_type"));
        assertEquals("投产演练复盘", row.get("asset_name"));
        // 属主原列必须保留，供类型专属附加列使用（信封 + 惰性详情）。
        for (String key : Set.of("meeting_id", "meeting_title", "project_name",
                "granularity", "meeting_source", "deleted_by_name", "deleted_at")) {
            assertTrue(row.containsKey(key), "projected row lost original key: " + key);
        }
        // 旧行为兼容：行内无 meeting_code 时不伪造 asset_code（前端展示为空白）。
        assertTrue(!row.containsKey("asset_code") || row.get("asset_code") == null,
                "asset_code must not be fabricated when meeting_code absent");
    }

    @Test
    void projectMapsMeetingCodeToAssetCodeAfterV103() {
        // V103 后 dm_meeting.meeting_code 存在，需作为统一信封 asset_code 投影，与 REPORT/ISSUE 同构。
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("meeting_id", 9100L);
        raw.put("meeting_code", "MEET-9100");
        raw.put("meeting_title", "方案评审会");

        Map<String, Object> row = MeetingRecycleBinSource.project(raw);

        assertEquals(9100L, row.get("id"));
        assertEquals("MEETING", row.get("asset_type"));
        assertEquals("方案评审会", row.get("asset_name"));
        assertEquals("MEET-9100", row.get("asset_code"));
        // meeting_code 原列仍保留，便于前端展示与搜索。
        assertEquals("MEET-9100", row.get("meeting_code"));
    }

    @Test
    void projectPreservesExistingAssetCodeAliasFromSql() {
        // MeetingService.baseSelect 已把 meeting_code 别名为 asset_code；projection 不应覆盖为 null。
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("meeting_id", 9101L);
        raw.put("meeting_code", "MEET-9101");
        raw.put("asset_code", "MEET-9101"); // SQL alias already present
        raw.put("meeting_title", "射内测保留");

        Map<String, Object> row = MeetingRecycleBinSource.project(raw);

        assertEquals("MEET-9101", row.get("asset_code"));
        assertEquals("MEET-9101", row.get("meeting_code"));
    }

    @Test
    void projectHandlesMissingOptionalColumnsWithoutCrashing() {
        Map<String, Object> minimal = new LinkedHashMap<>();
        minimal.put("meeting_id", 42L);

        Map<String, Object> row = MeetingRecycleBinSource.project(minimal);

        assertEquals(42L, row.get("id"));
        assertEquals("MEETING", row.get("asset_type"));
        assertTrue(!row.containsKey("asset_name") || row.get("asset_name") == null);
    }
}

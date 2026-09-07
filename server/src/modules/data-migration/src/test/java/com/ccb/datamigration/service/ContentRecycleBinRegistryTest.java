package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 统一回收站注册表分发行为验证（REQ-20260820-031 演进）：类型到来源的自动路由、
 * REPORT 纳入统一列表、恢复/彻底删除按类型分发、重复认领与未知类型的拒绝；
 * T32 追加项目隔离：projectId 必填且逐源透传。
 */
class ContentRecycleBinRegistryTest {

    private static final long PROJECT = 9001L;

    /** 记录被分发到的类型的桩来源。 */
    private static final class RecordingSource implements RecycleBinSource {
        private final Set<String> types;
        final List<String> listed = new ArrayList<>();
        final List<Long> countedProjects = new ArrayList<>();
        final List<Long> listedProjects = new ArrayList<>();
        final List<String> restored = new ArrayList<>();
        final List<String> purged = new ArrayList<>();
        final List<String> detailed = new ArrayList<>();

        RecordingSource(Set<String> types) { this.types = types; }

        @Override public Set<String> supports() { return types; }

        @Override public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
            countedProjects.add(projectId);
            return 1L;
        }

        @Override public List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
            listed.add(type);
            listedProjects.add(projectId);
            return List.of(Map.of("asset_type", type, "deleted_at", "2026-09-01 00:00:00"));
        }

        @Override public Map<String, Object> detail(String type, long id, AuthUser user) {
            detailed.add(type);
            return Map.of("asset_type", type, "id", id);
        }

        @Override public void restore(String type, List<Long> ids, AuthUser user) { restored.add(type); }

        @Override public void purge(String type, List<Long> ids, AuthUser user) { purged.add(type); }
    }

    @Test
    void registryCoversContentAssetsAndReport() {
        ContentRecycleBinService service = new ContentRecycleBinService(List.of(
                new RecordingSource(Set.of("PLAN", "RULE")),
                new RecordingSource(Set.of("REPORT"))));
        assertTrue(service.supportedTypes().containsAll(Set.of("PLAN", "RULE", "REPORT")));
    }

    @Test
    void listWithoutFilterAggregatesAllTypesIncludingReport() {
        RecordingSource assets = new RecordingSource(Set.of("PLAN"));
        RecordingSource report = new RecordingSource(Set.of("REPORT"));
        ContentRecycleBinService service = new ContentRecycleBinService(List.of(assets, report));
        List<Map<String, Object>> rows = service.list(Set.of(), PROJECT, null, 1, 50, null).records();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> "REPORT".equals(row.get("asset_type"))));
        assertTrue(assets.listed.contains("PLAN") && report.listed.contains("REPORT"));
        // T32：请求项目必须逐源透传，不得退化成租户级查询。
        assertEquals(List.of(PROJECT), assets.countedProjects);
        assertEquals(List.of(PROJECT), assets.listedProjects);
        assertEquals(List.of(PROJECT), report.listedProjects);
    }

    @Test
    void listRequiresProjectScope() {
        ContentRecycleBinService service = new ContentRecycleBinService(List.of(new RecordingSource(Set.of("PLAN"))));
        assertThrows(BusinessException.class, () -> service.list(Set.of(), null, null, 1, 20, null));
        assertThrows(BusinessException.class, () -> service.list(Set.of(), 0L, null, 1, 20, null));
    }

    @Test
    void restoreAndPurgeDispatchToOwningSourceOnly() {
        RecordingSource assets = new RecordingSource(Set.of("PLAN"));
        RecordingSource report = new RecordingSource(Set.of("REPORT"));
        ContentRecycleBinService service = new ContentRecycleBinService(List.of(assets, report));
        service.restore("REPORT", List.of(1L), null);
        service.purge("REPORT", List.of(2L), null);
        assertEquals(List.of("REPORT"), report.restored);
        assertEquals(List.of("REPORT"), report.purged);
        assertTrue(assets.restored.isEmpty() && assets.purged.isEmpty());
    }

    @Test
    void detailDispatchesToOwningSource() {
        RecordingSource assets = new RecordingSource(Set.of("PLAN"));
        RecordingSource report = new RecordingSource(Set.of("REPORT"));
        ContentRecycleBinService service = new ContentRecycleBinService(List.of(assets, report));
        Map<String, Object> detail = service.detail("REPORT", 7L, null);
        assertEquals(7L, detail.get("id"));
        assertEquals(List.of("REPORT"), report.detailed);
        assertTrue(assets.detailed.isEmpty());
    }

    @Test
    void duplicateTypeClaimIsRejectedAtConstruction() {
        RecordingSource a = new RecordingSource(Set.of("PLAN"));
        RecordingSource b = new RecordingSource(Set.of("PLAN"));
        assertThrows(IllegalStateException.class, () -> new ContentRecycleBinService(List.of(a, b)));
    }

    @Test
    void unsupportedTypeIsRejected() {
        ContentRecycleBinService service = new ContentRecycleBinService(List.of(new RecordingSource(Set.of("PLAN"))));
        assertThrows(BusinessException.class, () -> service.restore("GHOST", List.of(1L), null));
        assertThrows(BusinessException.class, () -> service.list(Set.of("GHOST"), PROJECT, null, 1, 20, null));
    }
}

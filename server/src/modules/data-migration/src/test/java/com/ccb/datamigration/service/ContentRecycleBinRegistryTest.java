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
 * REPORT 纳入统一列表、恢复/彻底删除按类型分发、重复认领与未知类型的拒绝。
 */
class ContentRecycleBinRegistryTest {

    /** 记录被分发到的类型的桩来源。 */
    private static final class RecordingSource implements RecycleBinSource {
        private final Set<String> types;
        final List<String> listed = new ArrayList<>();
        final List<String> restored = new ArrayList<>();
        final List<String> purged = new ArrayList<>();

        RecordingSource(Set<String> types) { this.types = types; }

        @Override public Set<String> supports() { return types; }

        @Override public long countDeleted(String type, String keyword, AuthUser user) {
            return 1L;
        }

        @Override public List<Map<String, Object>> listDeletedPage(String type, String keyword, int limit, AuthUser user) {
            listed.add(type);
            return List.of(Map.of("asset_type", type, "deleted_at", "2026-09-01 00:00:00"));
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
        List<Map<String, Object>> rows = service.list(Set.of(), null, 1, 50, null).records();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> "REPORT".equals(row.get("asset_type"))));
        assertTrue(assets.listed.contains("PLAN") && report.listed.contains("REPORT"));
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
    void duplicateTypeClaimIsRejectedAtConstruction() {
        RecordingSource a = new RecordingSource(Set.of("PLAN"));
        RecordingSource b = new RecordingSource(Set.of("PLAN"));
        assertThrows(IllegalStateException.class, () -> new ContentRecycleBinService(List.of(a, b)));
    }

    @Test
    void unsupportedTypeIsRejected() {
        ContentRecycleBinService service = new ContentRecycleBinService(List.of(new RecordingSource(Set.of("PLAN"))));
        assertThrows(BusinessException.class, () -> service.restore("GHOST", List.of(1L), null));
        assertThrows(BusinessException.class, () -> service.list(Set.of("GHOST"), null, 1, 20, null));
    }
}

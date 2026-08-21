package com.ccb.release.application.service;

import com.ccb.common.api.PageQuery;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseApplicationPersistenceContractTest {
    @Test
    void listKeywordSearchesApplicationCodeAndSubsystemCodeAndName() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ReleaseApplicationStore store = new ReleaseApplicationStore(jdbc);

        store.findPage(1L, "P1", "AUTH", null, false, 7L, new PageQuery(1, 20));

        assertTrue(jdbc.sql.stream().anyMatch(value -> value.contains(
                "application_code LIKE ? OR subsystem_code LIKE ? OR subsystem_name LIKE ?")));
    }

    @Test
    void updateRetiresPriorChildrenAndNeverDeletesBusinessHistory() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ReleaseApplicationStore store = new ReleaseApplicationStore(jdbc);

        store.update(application(), 2L);

        assertTrue(jdbc.sql.stream().anyMatch(value -> value.contains("rel_application_delivery SET active = 0")));
        assertTrue(jdbc.sql.stream().anyMatch(value -> value.contains("rel_application_requirement SET active = 0")));
        assertTrue(jdbc.sql.stream().anyMatch(value -> value.contains("application_revision, active")));
        assertTrue(jdbc.sql.stream().anyMatch(value -> value.contains("item_type, file_path, item_key")));
        assertFalse(jdbc.sql.stream().anyMatch(value -> value.toUpperCase().contains("DELETE FROM REL_APPLICATION")));
    }

    @Test
    void conflictAndAdditionalRelationUseStableItemKey() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ReleaseApplicationStore store = new ReleaseApplicationStore(jdbc);

        store.findConflictIds(1L, 20L, List.of("UNIT:UNIT1"), null);
        store.insertRelation(40L, 1L, 10L, 11L, "UNIT1", "ADDITIONAL", "v1", "v2", "追加", 7L);

        assertTrue(jdbc.sql.stream().anyMatch(value -> value.contains("d.item_key IN")));
        assertTrue(jdbc.sql.stream().anyMatch(value -> value.contains(
                "delivery_unit_code, item_type, item_key, file_path")));
    }

    @Test
    void relatedHistoryLookupUsesTenantAdditionalTypeAndCurrentActiveItemKeys() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ReleaseApplicationStore store = new ReleaseApplicationStore(jdbc);

        store.findRelatedApplicationIds(1L, 10L);

        String sql = jdbc.sql.get(0);
        assertTrue(sql.contains("r.tenant_id = ?"));
        assertTrue(sql.contains("r.application_id = ?"));
        assertTrue(sql.contains("r.relation_type = 'ADDITIONAL'"));
        assertTrue(sql.contains("current_item.active = 1"));
        assertTrue(sql.contains("current_item.item_key = r.item_key"));
        assertTrue(sql.contains("related.deleted = 0"));
        assertTrue(sql.contains("SELECT DISTINCT r.related_application_id"));
    }

    private Application application() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 15, 12, 0);
        return new Application(10L, 1L, "SQ-001", "P1", "P001", "项目", false, 20L, null,
                "S1", "SYS1", "系统", VersionType.URGENT, Characteristic.STANDARD, "release.regular.overdue",
                Status.DRAFT, 7L, "研发人员", "研发部", null, "紧急原因", "说明", null, 3L,
                7L, 7L, now, now, List.of(new DeliverySnapshot(30L, "D1", "UNIT1", "交付单元",
                ArtifactType.IMAGE, "v2")), List.of("REQ-1"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> sql = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            this.sql.add(sql);
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.sql.add(sql);
            return requiredType.cast(0L);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql.add(sql);
            return List.of();
        }
    }
}

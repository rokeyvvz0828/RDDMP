package com.ccb.architecture.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcReleaseMasterDataQueryTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "fixture", "", "申请人", 1, true);

    @Test
    void searchesPhysicalSubsystemsWithinTenantProjectAndActiveState() {
        Fixture jdbc = new Fixture();
        var result = new JdbcReleaseMasterDataQuery(jdbc)
                .searchPhysicalSubsystems(ACTOR, 31, new PageQuery(1, 20), "认证");

        assertThat(result.records()).containsExactly(new com.ccb.architecture.integration.ReleaseMasterDataQuery
                .PhysicalSubsystemRef(42, "W0042A", "认证系统"));
        assertThat(jdbc.queries).allMatch(sql -> sql.contains("tenant_id = ?") && sql.contains("project_id = ?")
                && sql.contains("status = 'ACTIVE'") && sql.contains("deleted = 0"));
        assertThat(jdbc.lastArgs).startsWith(7L, 31L);
    }

    @Test
    void searchesAndResolvesOnlyActiveChildrenOfRequestedPhysicalSubsystem() {
        Fixture jdbc = new Fixture();
        var query = new JdbcReleaseMasterDataQuery(jdbc);

        var page = query.searchDeliveryUnits(ACTOR, 31, 42, new PageQuery(1, 20), null);
        var selection = query.resolveActiveSelection(ACTOR, 31, 42, List.of(101L)).orElseThrow();

        assertThat(page.records()).extracting("id").containsExactly(101L);
        assertThat(selection.physicalSubsystem().id()).isEqualTo(42L);
        assertThat(selection.deliveryUnits()).extracting("artifactTypeCode").containsExactly("IMAGE");
        assertThat(jdbc.queries.stream().filter(sql -> sql.contains("arch_delivery_unit")))
                .allMatch(sql -> sql.contains("unit.physical_subsystem_id = ?")
                        && sql.contains("physical.status = 'ACTIVE'") && sql.contains("unit.status = 'ACTIVE'"));
    }

    @Test
    void exactResolutionFailsWhenAnyRequestedDeliveryUnitIsMissingOrInactive() {
        Fixture jdbc = new Fixture();
        jdbc.omitDelivery = true;

        assertThat(new JdbcReleaseMasterDataQuery(jdbc)
                .resolveActiveSelection(ACTOR, 31, 42, List.of(101L))).isEmpty();
    }

    @Test
    void listsAndResolvesActiveProjectEnvironmentsWithoutAssumingADeletedColumn() {
        Fixture jdbc = new Fixture();
        JdbcReleaseMasterDataQuery query = new JdbcReleaseMasterDataQuery(jdbc);

        assertThat(query.listActiveEnvironments(ACTOR, 31))
                .containsExactly(new com.ccb.architecture.integration.ReleaseMasterDataQuery
                        .EnvironmentRef(201L, "UAT", "集成测试环境", "architecture.environment-type.uat"));
        assertThat(query.resolveActiveEnvironment(ACTOR, 31, 201L)).contains(
                new com.ccb.architecture.integration.ReleaseMasterDataQuery
                        .EnvironmentRef(201L, "UAT", "集成测试环境", "architecture.environment-type.uat"));
        assertThat(jdbc.queries.stream().filter(sql -> sql.contains("arch_environment")))
                .allMatch(sql -> sql.contains("tenant_id = ?") && sql.contains("project_id = ?")
                        && sql.contains("status = 'ACTIVE'") && !sql.contains("deleted = 0"));
    }

    @Test
    void rejectsDisabledActorsAndInvalidIdentifiersBeforeQuerying() {
        Fixture jdbc = new Fixture();
        AuthUser disabled = new AuthUser(9, 7, "fixture", "", "申请人", 1, false);
        JdbcReleaseMasterDataQuery query = new JdbcReleaseMasterDataQuery(jdbc);

        assertThatThrownBy(() -> query.searchPhysicalSubsystems(disabled, 31, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> query.searchDeliveryUnits(ACTOR, 31, 0, null, null))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queries).isEmpty();
    }

    private static final class Fixture extends JdbcTemplate {
        private final List<String> queries = new ArrayList<>();
        private Object[] lastArgs = new Object[0];
        private boolean omitDelivery;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queries.add(sql);
            lastArgs = args;
            if (sql.contains("arch_delivery_unit")) {
                return omitDelivery ? List.of() : List.of(Map.of(
                        "id", 101L, "physical_subsystem_id", 42L, "code", "DUW0042A001",
                        "name", "认证服务", "artifact_type_code", "IMAGE"));
            }
            if (sql.contains("arch_environment")) {
                return List.of(Map.of("id", 201L, "code", "UAT", "name", "集成测试环境",
                        "type_code", "architecture.environment-type.uat"));
            }
            return List.of(Map.of("id", 42L, "code", "W0042A", "name", "认证系统"));
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            queries.add(sql);
            lastArgs = args;
            return type.cast(1L);
        }
    }
}

package com.ccb.boot.release;

import com.ccb.architecture.integration.ReleaseMasterDataQuery;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleaseArchitectureDirectoryAdapterTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "fixture", "", "申请人", 1, true);

    @Test
    void convertsSearchAndExactSelectionWithoutLeakingArchitectureTypes() {
        ReleaseMasterDataQuery query = mock(ReleaseMasterDataQuery.class);
        PageQuery page = new PageQuery(1, 20);
        var physical = new ReleaseMasterDataQuery.PhysicalSubsystemRef(42, "W0042A", "认证系统");
        var delivery = new ReleaseMasterDataQuery.DeliveryUnitRef(101, 42, "DUW0042A001", "认证服务", "IMAGE");
        when(query.searchPhysicalSubsystems(ACTOR, 31, page, "认证"))
                .thenReturn(new PageResult<>(List.of(physical), 1, 1, 20));
        when(query.searchDeliveryUnits(ACTOR, 31, 42, page, null))
                .thenReturn(new PageResult<>(List.of(delivery), 1, 1, 20));
        when(query.resolveActiveSelection(ACTOR, 31, 42, List.of(101L)))
                .thenReturn(Optional.of(new ReleaseMasterDataQuery.Selection(physical, List.of(delivery))));
        ReleaseArchitectureDirectoryAdapter adapter = new ReleaseArchitectureDirectoryAdapter(query);

        assertThat(adapter.searchPhysicalSubsystems(ACTOR, 31, page, "认证").records().get(0).code())
                .isEqualTo("W0042A");
        assertThat(adapter.searchDeliveryUnits(ACTOR, 31, 42, page, null).records().get(0).artifactTypeCode())
                .isEqualTo("IMAGE");
        assertThat(adapter.resolveActiveSelection(ACTOR, 31, 42, List.of(101L)).orElseThrow()
                .deliveryUnits().get(0).name()).isEqualTo("认证服务");
    }
}

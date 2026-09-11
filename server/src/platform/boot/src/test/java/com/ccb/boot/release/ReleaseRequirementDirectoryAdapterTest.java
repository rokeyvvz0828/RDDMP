package com.ccb.boot.release;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.requirement.integration.ReleaseRequirementQuery;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleaseRequirementDirectoryAdapterTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "fixture", "", "申请人", 1, true);

    @Test
    void convertsSearchAndExactResolutionWithoutLeakingRequirementTypes() {
        ReleaseRequirementQuery query = mock(ReleaseRequirementQuery.class);
        PageQuery page = new PageQuery(1, 20);
        var requirement = new ReleaseRequirementQuery.RequirementRef(101, "REQ-001", "统一登录改造", "软需编制");
        when(query.searchActive(ACTOR, "PROJECT-A", page, "登录"))
                .thenReturn(new PageResult<>(List.of(requirement), 1, 1, 20));
        when(query.resolveActive(ACTOR, "PROJECT-A", List.of("REQ-001")))
                .thenReturn(Optional.of(List.of(requirement)));
        ReleaseRequirementDirectoryAdapter adapter = new ReleaseRequirementDirectoryAdapter(query);

        assertThat(adapter.searchActive(ACTOR, "PROJECT-A", page, "登录").records().get(0).name())
                .isEqualTo("统一登录改造");
        assertThat(adapter.resolveActive(ACTOR, "PROJECT-A", List.of("REQ-001")).orElseThrow().get(0).status())
                .isEqualTo("软需编制");
    }
}

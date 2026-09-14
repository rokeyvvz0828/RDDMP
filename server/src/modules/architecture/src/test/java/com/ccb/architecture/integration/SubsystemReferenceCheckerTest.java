package com.ccb.architecture.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubsystemReferenceCheckerTest {

    @Test
    void usesImmutableNeutralRequestAndResultValues() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(
                7L,
                ReferenceCheckRequest.SubsystemKind.PHYSICAL,
                41L,
                ReferenceCheckRequest.Operation.VOID);
        ReferenceCheckResult result = ReferenceCheckResult.indeterminate(" 引用检查\n暂不可用\t ");

        assertThat(ReferenceCheckRequest.class.isRecord()).isTrue();
        assertThat(ReferenceCheckResult.class.isRecord()).isTrue();
        assertThat(request.tenantId()).isEqualTo(7L);
        assertThat(request.subsystemKind()).isEqualTo(ReferenceCheckRequest.SubsystemKind.PHYSICAL);
        assertThat(request.subsystemId()).isEqualTo(41L);
        assertThat(request.operation()).isEqualTo(ReferenceCheckRequest.Operation.VOID);
        assertThat(result.status()).isEqualTo(ReferenceCheckResult.Status.INDETERMINATE);
        assertThat(result.safeSummary()).isEqualTo("引用检查 暂不可用");
    }

    @Test
    void exposesAllReferenceCheckOutcomesAndRejectsInvalidNeutralInput() {
        assertThat(ReferenceCheckResult.clear("未发现有效引用").status())
                .isEqualTo(ReferenceCheckResult.Status.CLEAR);
        assertThat(ReferenceCheckResult.referenced("存在有效引用").status())
                .isEqualTo(ReferenceCheckResult.Status.REFERENCED);
        assertThat(ReferenceCheckResult.indeterminate("无法确认引用状态").status())
                .isEqualTo(ReferenceCheckResult.Status.INDETERMINATE);

        assertThatThrownBy(() -> new ReferenceCheckRequest(
                0L,
                ReferenceCheckRequest.SubsystemKind.PHYSICAL,
                41L,
                ReferenceCheckRequest.Operation.VOID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new ReferenceCheckResult(ReferenceCheckResult.Status.CLEAR, " \n\t "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safeSummary");
    }
}

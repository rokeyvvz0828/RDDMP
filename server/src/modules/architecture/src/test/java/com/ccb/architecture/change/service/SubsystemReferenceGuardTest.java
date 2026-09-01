package com.ccb.architecture.change.service;

import com.ccb.architecture.integration.ReferenceCheckRequest;
import com.ccb.architecture.integration.ReferenceCheckResult;
import com.ccb.architecture.integration.SubsystemReferenceChecker;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ccb.architecture.integration.ReferenceCheckRequest.Operation.CREATE_REFERENCE;
import static com.ccb.architecture.integration.ReferenceCheckRequest.Operation.OFFLINE;
import static com.ccb.architecture.integration.ReferenceCheckRequest.Operation.VOID;
import static com.ccb.architecture.integration.ReferenceCheckRequest.SubsystemKind.LOGICAL;
import static com.ccb.architecture.integration.ReferenceCheckRequest.SubsystemKind.PHYSICAL;
import static com.ccb.architecture.integration.ReferenceCheckResult.Status.CLEAR;
import static com.ccb.architecture.integration.ReferenceCheckResult.Status.INDETERMINATE;
import static com.ccb.architecture.integration.ReferenceCheckResult.Status.REFERENCED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubsystemReferenceGuardTest {

    private final ArchitectureSubsystemRepository repository = mock(ArchitectureSubsystemRepository.class);

    @Test
    void logicalOfflineIsBlockedByActivePhysicalSubsystemsBeforeExternalCheck() {
        SubsystemReferenceChecker checker = mock(SubsystemReferenceChecker.class);
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, LOGICAL, 11L, OFFLINE);
        when(repository.countActivePhysicalByLogical(1L, 11L)).thenReturn(2L);

        ReferenceCheckResult result = new SubsystemReferenceGuard(repository, List.of(checker)).check(request);

        assertThat(result.status()).isEqualTo(REFERENCED);
        assertThat(result.safeSummary()).contains("ACTIVE");
        verify(checker, never()).check(request);
        verify(repository, never()).countPhysicalHistoryByLogical(1L, 11L);
    }

    @Test
    void logicalVoidIsBlockedByAllPhysicalHistoryIncludingNonActiveRecords() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, LOGICAL, 12L, VOID);
        when(repository.countPhysicalHistoryByLogical(1L, 12L)).thenReturn(3L);

        ReferenceCheckResult result = new SubsystemReferenceGuard(repository, List.of()).check(request);

        assertThat(result.status()).isEqualTo(REFERENCED);
        assertThat(result.safeSummary()).contains("发布历史");
        verify(repository).countPhysicalHistoryByLogical(1L, 12L);
        verify(repository, never()).countActivePhysicalByLogical(1L, 12L);
    }

    @Test
    void emptyProviderListIsHealthyAndClear() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, PHYSICAL, 21L, OFFLINE);

        ReferenceCheckResult result = new SubsystemReferenceGuard(repository, List.of()).check(request);

        assertThat(result.status()).isEqualTo(CLEAR);
        assertThat(result.safeSummary()).isEqualTo("未发现有效引用");
    }

    @Test
    void referencedProviderTakesPriorityOverEarlierIndeterminateResult() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, PHYSICAL, 22L, VOID);
        SubsystemReferenceChecker unavailable = checkerReturning(null);
        SubsystemReferenceChecker referenced = checkerReturning(
                ReferenceCheckResult.referenced("发布任务仍引用该物理子系统"));

        ReferenceCheckResult result = new SubsystemReferenceGuard(
                repository, List.of(unavailable, referenced)).check(request);

        assertThat(result.status()).isEqualTo(REFERENCED);
        assertThat(result.safeSummary()).isEqualTo("发布任务仍引用该物理子系统");
    }

    @Test
    void providerExceptionFailsClosedWithoutLeakingExceptionMessage() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, PHYSICAL, 23L, VOID);
        SubsystemReferenceChecker checker = mock(SubsystemReferenceChecker.class);
        when(checker.check(request)).thenThrow(new IllegalStateException("token=top-secret"));
        SubsystemReferenceGuard guard = new SubsystemReferenceGuard(repository, List.of(checker));

        ReferenceCheckResult result = guard.check(request);

        assertThat(result.status()).isEqualTo(INDETERMINATE);
        assertThat(result.safeSummary()).isEqualTo("外部引用检查暂不可用");
        assertThat(result.safeSummary()).doesNotContain("token", "secret");
        assertThatThrownBy(() -> guard.requireClear(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(SubsystemReferenceGuard.SERVICE_UNAVAILABLE));
    }

    @Test
    void nullProviderResultFailsClosed() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, PHYSICAL, 24L, OFFLINE);

        ReferenceCheckResult result = new SubsystemReferenceGuard(
                repository, List.of(checkerReturning(null))).check(request);

        assertThat(result.status()).isEqualTo(INDETERMINATE);
        assertThat(result.safeSummary()).isEqualTo("外部引用检查暂不可用");
    }

    @Test
    void explicitIndeterminateResultFailsClosedWithSafeSummary() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, PHYSICAL, 25L, VOID);
        SubsystemReferenceGuard guard = new SubsystemReferenceGuard(repository, List.of(checkerReturning(
                ReferenceCheckResult.indeterminate("依赖模块暂不可判定\n请稍后重试"))));

        ReferenceCheckResult result = guard.check(request);

        assertThat(result.status()).isEqualTo(INDETERMINATE);
        assertThat(result.safeSummary()).isEqualTo("依赖模块暂不可判定 请稍后重试");
        assertThatThrownBy(() -> guard.requireClear(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(SubsystemReferenceGuard.SERVICE_UNAVAILABLE));
    }

    @Test
    void referencedResultBecomesConflict() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, PHYSICAL, 26L, OFFLINE);
        SubsystemReferenceGuard guard = new SubsystemReferenceGuard(repository, List.of(checkerReturning(
                ReferenceCheckResult.referenced("仍存在有效引用"))));

        assertThatThrownBy(() -> guard.requireClear(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("仍存在有效引用");
                });
    }

    @Test
    void internalRepositoryFailureAlsoFailsClosed() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, LOGICAL, 13L, VOID);
        when(repository.countPhysicalHistoryByLogical(1L, 13L))
                .thenThrow(new IllegalStateException("jdbc-password=secret"));

        ReferenceCheckResult result = new SubsystemReferenceGuard(repository, List.of()).check(request);

        assertThat(result.status()).isEqualTo(INDETERMINATE);
        assertThat(result.safeSummary()).isEqualTo("模块内引用检查暂不可用");
        assertThat(result.safeSummary()).doesNotContain("jdbc", "password", "secret");
    }

    @Test
    void createReferenceCannotBypassOfflineOrVoidGuard() {
        ReferenceCheckRequest request = new ReferenceCheckRequest(1L, PHYSICAL, 27L, CREATE_REFERENCE);
        SubsystemReferenceGuard guard = new SubsystemReferenceGuard(repository, List.of());

        assertThatThrownBy(() -> guard.check(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    private SubsystemReferenceChecker checkerReturning(ReferenceCheckResult result) {
        SubsystemReferenceChecker checker = mock(SubsystemReferenceChecker.class);
        when(checker.check(org.mockito.ArgumentMatchers.any())).thenReturn(result);
        return checker;
    }
}

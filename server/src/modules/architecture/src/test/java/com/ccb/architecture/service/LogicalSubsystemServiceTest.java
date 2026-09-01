package com.ccb.architecture.service;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.model.PhysicalSubsystem;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.org.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogicalSubsystemServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);

    @Mock
    private ArchitectureSubsystemRepository repository;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private SystemReferenceQuery referenceQuery;
    @Mock
    private SystemOperationAudit operationAudit;
    @Mock
    private TransactionTemplate transactions;

    private LogicalSubsystemService service;

    @BeforeEach
    void setUp() {
        service = new LogicalSubsystemService(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    @Test
    void 列表使用认证租户并标准化状态筛选() {
        PageResult<LogicalSubsystem> expected = new PageResult<>(List.of(logical()), 1, 1, 20);
        when(repository.pageLogical(eq(7L), any(PageQuery.class), any(LogicalSubsystemQuery.class)))
                .thenReturn(expected);

        PageResult<LogicalSubsystem> actual = service.list(ACTOR, new PageQuery(1, 20),
                new LogicalSubsystemQuery(" A0001 ", " 商城 ", " 商城系统 ", 11L, " offline "));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<LogicalSubsystemQuery> query = ArgumentCaptor.forClass(LogicalSubsystemQuery.class);
        verify(repository).pageLogical(eq(7L), any(PageQuery.class), query.capture());
        assertThat(query.getValue()).isEqualTo(new LogicalSubsystemQuery("A0001", "商城", "商城系统", 11L, "OFFLINE"));
    }

    @Test
    void 详情返回V82字段和物理子系统摘要() {
        when(repository.findLogical(7L, 101L)).thenReturn(Optional.of(logical()));
        when(repository.findPhysicalByLogical(7L, 101L)).thenReturn(List.of(physical()));

        LogicalSubsystem actual = service.detail(ACTOR, 101L);

        assertThat(actual.numberSequence()).isEqualTo(1);
        assertThat(actual.status()).isEqualTo("OFFLINE");
        assertThat(actual.sortNo()).isEqualTo(8);
        assertThat(actual.rowVersion()).isEqualTo(3L);
        assertThat(actual.physicalSubsystems()).singleElement().satisfies(summary -> {
            assertThat(summary.code()).isEqualTo("W00011");
            assertThat(summary.numberSlot()).isEqualTo("1");
            assertThat(summary.englishName()).isEqualTo("Mall Platform");
            assertThat(summary.status()).isEqualTo("ACTIVE");
            assertThat(summary.rowVersion()).isEqualTo(4L);
        });
        verify(repository).findLogical(7L, 101L);
        verify(repository).findPhysicalByLogical(7L, 101L);
    }

    @Test
    void 旧新增入口在认证后立即要求工单且无副作用() {
        assertWorkOrderRequired(() -> service.create(ACTOR, null, "trace-create"));

        verifyNoInteractions(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    @Test
    void 旧修改入口在认证后立即要求工单且无副作用() {
        assertWorkOrderRequired(() -> service.update(ACTOR, -1L, null, "trace-update"));

        verifyNoInteractions(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    @Test
    void 旧删除入口在认证后立即要求工单且无副作用() {
        assertWorkOrderRequired(() -> service.delete(ACTOR, -1L, "trace-delete"));

        verifyNoInteractions(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    @Test
    void 无有效租户时仍在工单兼容判断前返回未认证() {
        AuthUser missingTenant = new AuthUser(9, 0, "architect", "hash", "架构管理员", 11, true);

        assertThatThrownBy(() -> service.create(missingTenant, null, "trace-auth"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    private void assertWorkOrderRequired(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED");
                });
    }

    private LogicalSubsystem logical() {
        return new LogicalSubsystem(101L, "A0001", "商城", "商城系统", 11L,
                "P2", "APPLICATION", "CHANNEL", 21L, "系统描述", null,
                9L, 9L, LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0),
                1, "OFFLINE", 8, 3L, List.of());
    }

    private PhysicalSubsystem physical() {
        return new PhysicalSubsystem(201L, "W00011", "商城物理", "商城物理平台", 101L,
                "渠道", 12L, "平台研发团队", "architecture.runtime.7x24", "A", "Spring", 30L,
                "描述", null, 9L, 9L,
                LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0),
                "1", "Mall Platform", "ACTIVE", 4L);
    }
}

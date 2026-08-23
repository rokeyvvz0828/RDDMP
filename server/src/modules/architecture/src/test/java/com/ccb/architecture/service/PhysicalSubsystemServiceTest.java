package com.ccb.architecture.service;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.PhysicalSubsystem;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.architecture.service.PhysicalSubsystemService.PhysicalSubsystemView;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
class PhysicalSubsystemServiceTest {
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

    private PhysicalSubsystemService service;

    @BeforeEach
    void setUp() {
        service = new PhysicalSubsystemService(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    @Test
    void 列表使用认证租户并返回V82状态和逻辑摘要() {
        when(repository.pagePhysical(eq(7L), any(PageQuery.class), any(PhysicalSubsystemQuery.class)))
                .thenReturn(new PageResult<>(List.of(physical("VOIDED")), 1, 1, 20));
        when(organizationService.tree(ACTOR)).thenReturn(List.of(organization(12L, "平台研发团队", 1)));
        when(repository.findLogical(7L, 101L)).thenReturn(Optional.of(logical()));
        when(referenceQuery.findUser(ACTOR, 30L, false))
                .thenReturn(Optional.of(new SystemUserReference(30L, "系统负责人", "owner", null, true)));
        when(referenceQuery.findUser(ACTOR, 9L, false))
                .thenReturn(Optional.of(new SystemUserReference(9L, "架构管理员", "architect", null, true)));

        PageResult<PhysicalSubsystemView> result = service.list(ACTOR, new PageQuery(1, 20),
                new PhysicalSubsystemQuery(" W0001 ", null, null, " 渠道 ", 12L, 101L, " voided "));

        assertThat(result.records()).singleElement().satisfies(view -> {
            assertThat(view.code()).isEqualTo("W00011");
            assertThat(view.numberSlot()).isEqualTo("1");
            assertThat(view.englishName()).isEqualTo("Mall Platform");
            assertThat(view.status()).isEqualTo("VOIDED");
            assertThat(view.rowVersion()).isEqualTo(4L);
            assertThat(view.logicalSubsystemCode()).isEqualTo("A0001");
            assertThat(view.logicalSubsystemNumberSequence()).isEqualTo(1);
            assertThat(view.logicalSubsystemStatus()).isEqualTo("OFFLINE");
        });
        ArgumentCaptor<PhysicalSubsystemQuery> query = ArgumentCaptor.forClass(PhysicalSubsystemQuery.class);
        verify(repository).pagePhysical(eq(7L), any(PageQuery.class), query.capture());
        assertThat(query.getValue()).isEqualTo(
                new PhysicalSubsystemQuery("W0001", null, null, "渠道", 12L, 101L, "VOIDED"));
    }

    @Test
    void 详情保留状态字段并在团队停用时使用快照名称() {
        when(repository.findPhysical(7L, 201L)).thenReturn(Optional.of(physical("OFFLINE")));
        when(organizationService.tree(ACTOR)).thenReturn(List.of(organization(12L, "已停用团队", 0)));
        when(repository.findLogical(7L, 101L)).thenReturn(Optional.of(logical()));

        PhysicalSubsystemView view = service.detail(ACTOR, 201L);

        assertThat(view.status()).isEqualTo("OFFLINE");
        assertThat(view.numberSlot()).isEqualTo("1");
        assertThat(view.englishName()).isEqualTo("Mall Platform");
        assertThat(view.responsibleTeamValid()).isFalse();
        assertThat(view.responsibleTeamDisplayName()).isEqualTo("平台研发团队快照");
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

    private PhysicalSubsystem physical(String status) {
        return new PhysicalSubsystem(201L, "W00011", "商城物理", "商城物理平台", 101L,
                "渠道", 12L, "平台研发团队快照", "architecture.runtime.7x24", "A", "Spring", 30L,
                "描述", null, 9L, 9L,
                LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0),
                "1", "Mall Platform", status, 4L);
    }

    private OrgTreeNode organization(long id, String name, int status) {
        return new OrgTreeNode(id, 0L, "TEAM", name, 1, status, new ArrayList<>(), new ArrayList<>());
    }
}

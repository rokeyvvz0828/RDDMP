package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkAccessModels.AccessDecision;
import com.ccb.architecture.network.model.NetworkAccessModels.AccessProtocol;
import com.ccb.architecture.network.model.NetworkAccessModels.DecisionBasis;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointCommand;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointKind;
import com.ccb.architecture.network.model.NetworkAccessModels.ExemptionRuleStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ManagedEndpointInstance;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessExemptionRule;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessRelation;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneSubnet;
import com.ccb.architecture.network.model.NetworkAccessModels.RecordStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ValidityType;
import com.ccb.architecture.network.persistence.NetworkAccessStore;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkAccessDecisionServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 12, 31, 23, 59);

    @Mock
    private NetworkAccessStore store;

    private final AtomicLong ids = new AtomicLong(900_000L);
    private NetworkAccessService service;

    @BeforeEach
    void setUp() {
        service = new NetworkAccessService(store, new ObjectMapper(), ids::incrementAndGet,
                Clock.fixed(Instant.parse("2026-08-28T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 缺失有效期类型时严格返回需要申请() {
        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(null, null,
                        AccessProtocol.TCP, "443", START, END, null));

        assertThat(result.decision()).isEqualTo(AccessDecision.NEEDS_APPLICATION);
        assertThat(result.needsApplication()).isTrue();
        assertThat(result.basis()).isEqualTo(DecisionBasis.STRICT_REQUIRED);
    }

    @Test
    void 同一启用子网内访问不需要申请() {
        EndpointCommand source = managed(100L, 200L, 300L, List.of(11L));
        EndpointCommand target = managed(100L, 200L, 300L, List.of(21L));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 100L, 200L, 300L, 800L, "src", "10.16.32.10")));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(21L)))
                .thenReturn(List.of(instance(21L, 100L, 200L, 300L, 800L, "dst", "10.16.32.11")));
        when(store.listSubnets(7L, null, RecordStatus.ACTIVE))
                .thenReturn(List.of(subnet(800L, "10.16.32.0/24")));

        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(source, target,
                        AccessProtocol.TCP, "1-65535", START, END, ValidityType.LIMITED));

        assertThat(result.decision()).isEqualTo(AccessDecision.NOT_REQUIRED);
        assertThat(result.needsApplication()).isFalse();
        assertThat(result.basis()).isEqualTo(DecisionBasis.SUBNET_INTERNAL);
        assertThat(result.reasonCodes()).containsExactly("SAME_SUBNET_INTERNAL");
        assertThat(result.coveringRelationNos()).isEmpty();
        assertThat(result.coveringRuleCodes()).isEmpty();
    }

    @Test
    void 不同启用子网且无覆盖关系时需要申请() {
        EndpointCommand source = managed(100L, 200L, 300L, List.of(11L));
        EndpointCommand target = managed(100L, 200L, 300L, List.of(21L));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 100L, 200L, 300L, 800L, "src", "10.16.32.10")));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(21L)))
                .thenReturn(List.of(instance(21L, 100L, 200L, 300L, 800L, "dst", "10.16.48.10")));
        when(store.listSubnets(7L, null, RecordStatus.ACTIVE))
                .thenReturn(List.of(subnet(800L, "10.16.32.0/24"), subnet(800L, "10.16.48.0/24")));
        when(store.listRelations(7L, RelationStatus.ACTIVE, 2000, 0)).thenReturn(List.of());
        when(store.listExemptionRules(7L, ExemptionRuleStatus.ACTIVE)).thenReturn(List.of());

        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(source, target,
                        AccessProtocol.TCP, "443", START, END, ValidityType.LIMITED));

        assertThat(result.decision()).isEqualTo(AccessDecision.NEEDS_APPLICATION);
        assertThat(result.basis()).isEqualTo(DecisionBasis.STRICT_REQUIRED);
        assertThat(result.reasonCodes()).contains("NO_FULL_COVERAGE");
    }

    @Test
    void 子网内部证据不足时需要申请() {
        EndpointCommand source = managed(100L, 200L, 300L, List.of(11L, 12L));
        EndpointCommand target = managed(100L, 200L, 300L, List.of(21L));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L, 12L)))
                .thenReturn(List.of(
                        instance(11L, 100L, 200L, 300L, 800L, "src-1", "10.16.32.10"),
                        instance(12L, 100L, 200L, 300L, 800L, "src-2", null)));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(21L)))
                .thenReturn(List.of(instance(21L, 100L, 200L, 300L, 800L, "dst", "10.16.32.11")));
        lenient().when(store.listSubnets(7L, null, RecordStatus.ACTIVE))
                .thenReturn(List.of(subnet(800L, "10.16.32.0/24")));
        when(store.listRelations(7L, RelationStatus.ACTIVE, 2000, 0)).thenReturn(List.of());

        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(source, target,
                        AccessProtocol.TCP, "443", START, END, ValidityType.LIMITED));

        assertThat(result.decision()).isEqualTo(AccessDecision.NEEDS_APPLICATION);
        assertThat(result.basis()).isEqualTo(DecisionBasis.STRICT_REQUIRED);
        assertThat(result.reasonCodes()).contains("NO_FULL_COVERAGE");
    }

    @Test
    void 来源目标选择同一实例时判定按输入无效处理() {
        EndpointCommand source = managed(100L, 200L, 300L, List.of(11L));
        EndpointCommand target = managed(100L, 200L, 300L, List.of(11L));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 100L, 200L, 300L, 800L, "same", "10.16.32.10")));

        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(source, target,
                        AccessProtocol.TCP, "443", START, END, ValidityType.LIMITED));

        assertThat(result.decision()).isEqualTo(AccessDecision.NEEDS_APPLICATION);
        assertThat(result.basis()).isEqualTo(DecisionBasis.STRICT_REQUIRED);
        assertThat(result.reasonCodes()).containsExactly("INVALID_OR_INCOMPLETE_INPUT");
    }

    @Test
    void 有效关系完整覆盖时不需要申请() {
        EndpointCommand source = managed(100L, 200L, 300L, List.of(11L));
        EndpointCommand target = managed(101L, 201L, 301L, List.of(21L));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 100L, 200L, 300L, 800L, "src", "10.1.1.10")));
        when(store.listEndpointInstances(7L, 101L, 201L, 301L, List.of(21L)))
                .thenReturn(List.of(instance(21L, 101L, 201L, 301L, 801L, "dst", "10.2.1.20")));
        when(store.listRelations(7L, RelationStatus.ACTIVE, 2000, 0))
                .thenReturn(List.of(relation("443,8443-8445", START.minusDays(1), END.plusDays(1))));

        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(source, target,
                        AccessProtocol.TCP, "443,8444", START, END, ValidityType.LIMITED));

        assertThat(result.decision()).isEqualTo(AccessDecision.NOT_REQUIRED);
        assertThat(result.basis()).isEqualTo(DecisionBasis.RELATION_COVERED);
        assertThat(result.coveringRelationNos()).containsExactly("NAR100");
    }

    @Test
    void 端口覆盖不完整时需要申请() {
        EndpointCommand source = managed(100L, 200L, 300L, List.of(11L));
        EndpointCommand target = managed(101L, 201L, 301L, List.of(21L));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 100L, 200L, 300L, 800L, "src", "10.1.1.10")));
        when(store.listEndpointInstances(7L, 101L, 201L, 301L, List.of(21L)))
                .thenReturn(List.of(instance(21L, 101L, 201L, 301L, 801L, "dst", "10.2.1.20")));
        when(store.listRelations(7L, RelationStatus.ACTIVE, 2000, 0))
                .thenReturn(List.of(relation("443", START.minusDays(1), END.plusDays(1))));
        when(store.listExemptionRules(7L, ExemptionRuleStatus.ACTIVE)).thenReturn(List.of());

        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(source, target,
                        AccessProtocol.TCP, "443,8443", START, END, ValidityType.LIMITED));

        assertThat(result.decision()).isEqualTo(AccessDecision.NEEDS_APPLICATION);
        assertThat(result.reasonCodes()).contains("NO_FULL_COVERAGE");
    }

    @Test
    void 免申请规则完整覆盖时不需要申请() {
        EndpointCommand source = managed(100L, 200L, 300L, List.of(11L));
        EndpointCommand target = managed(101L, 201L, 301L, List.of(21L));
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 100L, 200L, 300L, 800L, "src", "10.1.1.10")));
        when(store.listEndpointInstances(7L, 101L, 201L, 301L, List.of(21L)))
                .thenReturn(List.of(instance(21L, 101L, 201L, 301L, 801L, "dst", "10.2.1.20")));
        when(store.listRelations(7L, RelationStatus.ACTIVE, 2000, 0)).thenReturn(List.of());
        when(store.listExemptionRules(7L, ExemptionRuleStatus.ACTIVE))
                .thenReturn(List.of(rule(800L, 801L, "443,8443-8445")));

        NetworkAccessService.NetworkAccessDecisionResult result = service.decideAccess(ACTOR,
                new NetworkAccessService.NetworkAccessDecisionCommand(source, target,
                        AccessProtocol.TCP, "8444", START, END, ValidityType.LIMITED));

        assertThat(result.decision()).isEqualTo(AccessDecision.NOT_REQUIRED);
        assertThat(result.basis()).isEqualTo(DecisionBasis.RULE_EXEMPT);
        assertThat(result.coveringRuleCodes()).containsExactly("EXEMPT_1");
    }

    private EndpointCommand managed(long physicalId, long environmentId, long deploymentUnitId,
                                    List<Long> instanceIds) {
        return new EndpointCommand(EndpointKind.MANAGED, physicalId, environmentId, deploymentUnitId,
                null, instanceIds);
    }

    private ManagedEndpointInstance instance(long id, long physicalId, long environmentId, long deploymentUnitId,
                                             long networkZoneId, String machineName, String ipAddress) {
        return new ManagedEndpointInstance(id, "INS" + id, physicalId, "P" + physicalId, "物理子系统",
                environmentId, "ENV" + environmentId, "环境", deploymentUnitId, "DU" + deploymentUnitId,
                "部署单元", machineName, ipAddress, networkZoneId, "分区" + networkZoneId);
    }

    private NetworkAccessRelation relation(String ports, LocalDateTime validFrom, LocalDateTime validUntil) {
        return new NetworkAccessRelation(100L, 7L, "NAR100", 90L,
                EndpointKind.MANAGED,
                "[{\"id\":11,\"machineName\":\"src\",\"ipAddress\":\"10.1.1.10\",\"networkZoneId\":800}]",
                EndpointKind.MANAGED,
                "[{\"id\":21,\"machineName\":\"dst\",\"ipAddress\":\"10.2.1.20\",\"networkZoneId\":801}]",
                AccessProtocol.TCP, ports, "历史关系", null, validFrom, validUntil,
                RelationStatus.ACTIVE, null, null, null, 0L, ACTOR.id(), ACTOR.id(), START, START);
    }

    private NetworkAccessExemptionRule rule(long sourceZone, long targetZone, String ports) {
        return new NetworkAccessExemptionRule(200L, 7L, "EXEMPT_1", "免申请规则",
                sourceZone, "来源分区", targetZone, "目标分区", AccessProtocol.TCP,
                ports, START.minusDays(1), END.plusDays(1), ValidityType.LIMITED,
                ExemptionRuleStatus.ACTIVE, null, 0L, ACTOR.id(), ACTOR.id(), START, START);
    }

    private NetworkZoneSubnet subnet(long networkZoneId, String cidrBlock) {
        return new NetworkZoneSubnet(300L + networkZoneId, 7L, networkZoneId, "ZONE" + networkZoneId,
                "分区" + networkZoneId, cidrBlock, null, null, RecordStatus.ACTIVE,
                null, 0L, ACTOR.id(), ACTOR.id(), START, START);
    }
}

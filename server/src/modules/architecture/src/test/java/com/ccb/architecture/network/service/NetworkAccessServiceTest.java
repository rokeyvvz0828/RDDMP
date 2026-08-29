package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkAccessModels.AccessProtocol;
import com.ccb.architecture.network.model.NetworkAccessModels.AddressType;
import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointCommand;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointKind;
import com.ccb.architecture.network.model.NetworkAccessModels.ExternalNetworkAddress;
import com.ccb.architecture.network.model.NetworkAccessModels.ManagedEndpointInstance;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessRelation;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZone;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneSubnet;
import com.ccb.architecture.network.model.NetworkAccessModels.RecordStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import com.ccb.architecture.network.persistence.NetworkAccessStore;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkAccessServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 26, 10, 0);

    @Mock
    private NetworkAccessStore store;

    private final AtomicLong ids = new AtomicLong(900_000L);
    private NetworkAccessService service;

    @BeforeEach
    void setUp() {
        service = new NetworkAccessService(store, new ObjectMapper(), ids::incrementAndGet,
                Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 创建访问申请快照托管实例和外部地址() {
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 300L, 800L, "vm-src", "10.10.1.1")));
        when(store.findAddress(7L, 50L)).thenReturn(Optional.of(activeAddress()));
        ArgumentCaptor<NetworkAccessApplication> saved = ArgumentCaptor.forClass(NetworkAccessApplication.class);

        NetworkAccessApplication result = service.createApplication(ACTOR, new NetworkAccessService.NetworkAccessCommand(
                managed(100L, 200L, 300L, List.of(11L)),
                external(50L),
                AccessProtocol.TCP,
                "443",
                "应用访问外部 API",
                "RDDMP 内记录关系，外部策略线下开通",
                TIME,
                null,
                null));

        assertThat(result.status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(result.applicationNo()).isEqualTo("NAA900001");
        verify(store).insertApplication(saved.capture());
        assertThat(saved.getValue().sourceSnapshotJson()).contains("vm-src", "10.10.1.1", "生产应用区");
        assertThat(saved.getValue().targetSnapshotJson()).contains("external.example.com", "外部 API");
    }

    @Test
    void 托管端点实例缺少结构化网络分区时拒绝创建申请() {
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 300L, null, "vm-src", "10.10.1.1")));

        assertThatThrownBy(() -> service.createApplication(ACTOR, new NetworkAccessService.NetworkAccessCommand(
                managed(100L, 200L, 300L, List.of(11L)),
                external(50L),
                AccessProtocol.TCP,
                "443",
                "应用访问外部 API",
                null,
                TIME,
                null,
                null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实例缺少结构化网络分区");
        verify(store, never()).insertApplication(any(NetworkAccessApplication.class));
    }

    @Test
    void 来源目标不能选择同一环境部署实例() {
        when(store.listEndpointInstances(7L, 100L, 200L, 300L, List.of(11L)))
                .thenReturn(List.of(instance(11L, 300L, 800L, "vm-same", "10.10.1.1")));

        assertThatThrownBy(() -> service.createApplication(ACTOR, new NetworkAccessService.NetworkAccessCommand(
                managed(100L, 200L, 300L, List.of(11L)),
                managed(100L, 200L, 300L, List.of(11L)),
                AccessProtocol.TCP,
                "443",
                "同实例误选",
                null,
                TIME,
                null,
                null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源端点和目标端点不能选择同一环境部署实例");
        verify(store, never()).insertApplication(any(NetworkAccessApplication.class));
    }

    @Test
    void 批准申请生成访问关系快照且不重新计算端点实例() {
        NetworkAccessApplication reviewing = application(ApplicationStatus.IN_REVIEW, 2L);
        NetworkAccessApplication approved = application(ApplicationStatus.APPROVED, 3L);
        when(store.lockApplication(7L, 900L)).thenReturn(Optional.of(reviewing));
        when(store.updateApplicationStatus(7L, 900L, ApplicationStatus.IN_REVIEW, 2L,
                ApplicationStatus.APPROVED, ACTOR.id())).thenReturn(true);
        when(store.findApplication(7L, 900L)).thenReturn(Optional.of(approved));
        ArgumentCaptor<NetworkAccessRelation> relation = ArgumentCaptor.forClass(NetworkAccessRelation.class);

        NetworkAccessApplication result = service.approveApplication(ACTOR, 900L, 2L);

        assertThat(result.status()).isEqualTo(ApplicationStatus.APPROVED);
        verify(store).insertRelation(relation.capture());
        assertThat(relation.getValue().relationNo()).isEqualTo("NAR900001");
        assertThat(relation.getValue().applicationId()).isEqualTo(900L);
        assertThat(relation.getValue().sourceSnapshotJson()).isEqualTo(reviewing.sourceSnapshotJson());
        assertThat(relation.getValue().targetSnapshotJson()).isEqualTo(reviewing.targetSnapshotJson());
        assertThat(relation.getValue().status()).isEqualTo(RelationStatus.ACTIVE);
    }

    @Test
    void 创建网络分区网段规范化CIDR并要求叶子分区() {
        when(store.findZone(7L, 800L)).thenReturn(Optional.of(activeZone(800L, null, "P8_APP", "P8开放AP", 2)));
        when(store.hasActiveChildZones(7L, 800L)).thenReturn(false);
        when(store.subnetCidrExists(7L, "10.16.32.0/20", null)).thenReturn(false);
        when(store.findSubnet(7L, 900001L)).thenReturn(Optional.of(subnet(900001L, "10.16.32.0/20")));
        ArgumentCaptor<NetworkZoneSubnet> saved = ArgumentCaptor.forClass(NetworkZoneSubnet.class);

        NetworkZoneSubnet result = service.createSubnet(ACTOR, 800L,
                new NetworkAccessService.NetworkZoneSubnetCommand("10.16.33.8/20",
                        "10.16.32.1", "开放区 AP 下发", null, null));

        verify(store).insertSubnet(saved.capture());
        assertThat(saved.getValue().cidrBlock()).isEqualTo("10.16.32.0/20");
        assertThat(saved.getValue().gatewayIp()).isEqualTo("10.16.32.1");
        assertThat(result.cidrBlock()).isEqualTo("10.16.32.0/20");
    }

    @Test
    void 非叶子分区不能维护网段且实例IP必须落入启用网段() {
        when(store.findZone(7L, 800L)).thenReturn(Optional.of(activeZone(800L, null, "P8", "P8开放区", 1)));
        when(store.hasActiveChildZones(7L, 800L)).thenReturn(true);

        assertThatThrownBy(() -> service.createSubnet(ACTOR, 800L,
                new NetworkAccessService.NetworkZoneSubnetCommand("10.16.32.0/20",
                        null, "父区网段", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须选择启用叶子分区");

        when(store.listSubnets(7L, 800L, RecordStatus.ACTIVE))
                .thenReturn(List.of(subnet(900001L, "10.16.32.0/20")));

        assertThatThrownBy(() -> service.requireIpInActiveSubnet(7L, 800L,
                "10.17.1.10", "第 1 台实例 IP 地址"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于网络分区");
    }

    private EndpointCommand managed(long physicalId, long environmentId, long deploymentUnitId,
                                    List<Long> instanceIds) {
        return new EndpointCommand(EndpointKind.MANAGED, physicalId, environmentId, deploymentUnitId,
                null, instanceIds);
    }

    private EndpointCommand external(long addressId) {
        return new EndpointCommand(EndpointKind.EXTERNAL, null, null, null, addressId, List.of());
    }

    private ManagedEndpointInstance instance(long id, long deploymentUnitId, Long networkZoneId,
                                             String machineName, String ipAddress) {
        return new ManagedEndpointInstance(id, "INS" + id, 100L, "W0001A", "接入系统",
                200L, "PROD-A", "生产环境 A", deploymentUnitId, "DW0001A001",
                "接入应用", machineName, ipAddress, networkZoneId,
                networkZoneId == null ? null : "生产应用区");
    }

    private ExternalNetworkAddress activeAddress() {
        return new ExternalNetworkAddress(50L, 7L, AddressType.DOMAIN, "external.example.com",
                "外部 API", "第三方服务", RecordStatus.ACTIVE, null, 0L, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }

    private NetworkZone activeZone(long id, Long parentId, String code, String name, int restrictionLevel) {
        return new NetworkZone(id, 7L, parentId, null, code, name, restrictionLevel,
                RecordStatus.ACTIVE, null, null, 0L, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }

    private NetworkZoneSubnet subnet(long id, String cidrBlock) {
        return new NetworkZoneSubnet(id, 7L, 800L, "P8_APP", "P8开放AP", cidrBlock,
                null, "开放区 AP 下发", RecordStatus.ACTIVE, null, 0L, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }

    private NetworkAccessApplication application(ApplicationStatus status, long rowVersion) {
        return new NetworkAccessApplication(900L, 7L, "NAA900", ACTOR.id(),
                EndpointKind.MANAGED, 100L, 200L, 300L, null,
                "[{\"machineName\":\"vm-src\",\"networkZoneName\":\"生产应用区\"}]",
                EndpointKind.EXTERNAL, null, null, null, 50L,
                "[{\"displayName\":\"外部 API\",\"addressValue\":\"external.example.com\"}]",
                AccessProtocol.TCP, "443", "应用访问外部 API", "审批生成关系", null, null,
                status, rowVersion, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }
}

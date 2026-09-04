package com.ccb.architecture.environment.service;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.Environment;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentInstance;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillInstanceItemCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.HistoryEvent;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceDisasterRecovery;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.OfflineInstanceCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionPreviewResult;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RecordStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceItemCommand;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequestItem;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore.DeploymentUnitRef;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore.PhysicalSubsystemRef;
import com.ccb.architecture.environment.service.EnvironmentResourceService.AccessScope;
import com.ccb.architecture.environment.service.EnvironmentResourceService.ResourceRequestCommand;
import com.ccb.architecture.environment.service.EnvironmentResourceService.SubmissionPreparation;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.architecture.network.service.NetworkAccessService.ZoneRef;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentResourceServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);
    private static final AuthUser OTHER = new AuthUser(10L, 7L, "other", "hash", "其他人", 12L, true);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Mock
    private EnvironmentResourceStore store;
    @Mock
    private SystemReferenceQuery referenceQuery;
    @Mock
    private NetworkAccessService networkAccessService;

    private final AtomicLong ids = new AtomicLong(900000L);
    private EnvironmentResourceService service;

    @BeforeEach
    void setUp() {
        lenient().when(referenceQuery.activeParameters(ACTOR, EnvironmentResourceService.ENVIRONMENT_TYPE_CATEGORY))
                .thenReturn(List.of(new SystemParameterReference("architecture.environment-type.dev", "开发环境")));
        lenient().when(referenceQuery.activeParameters(ACTOR, EnvironmentResourceService.SERVER_TYPE_CATEGORY))
                .thenReturn(List.of(new SystemParameterReference("architecture.server-type.container", "容器")));
        lenient().when(referenceQuery.activeParameters(ACTOR, EnvironmentResourceService.JDK_VERSION_CATEGORY))
                .thenReturn(List.of(
                        new SystemParameterReference("architecture.jdk.jdk8", "JDK 8"),
                        new SystemParameterReference("architecture.jdk.jdk17", "JDK 17")));
        lenient().when(referenceQuery.activeParameters(ACTOR, EnvironmentResourceService.MIDDLEWARE_CATEGORY))
                .thenReturn(List.of(
                        new SystemParameterReference("architecture.middleware.tomcat9", "Tomcat 9"),
                        new SystemParameterReference("architecture.middleware.ibm-mq-9-1", "IBM MQ 9.1")));
        lenient().when(referenceQuery.activeParameters(ACTOR, EnvironmentResourceService.OPERATING_SYSTEM_CATEGORY))
                .thenReturn(List.of(
                        new SystemParameterReference("architecture.os.rhel8-5", "RHEL 8.5"),
                        new SystemParameterReference("architecture.os.suse12-sp5", "SUSE Linux 12 SP5")));
        lenient().when(referenceQuery.findUser(ACTOR, ACTOR.id(), true)).thenReturn(Optional.of(
                new com.ccb.system.capability.SystemUserReference(ACTOR.id(), ACTOR.username(), ACTOR.displayName(),
                        null, true)));
        service = new EnvironmentResourceService(store, new ObjectMapper(), referenceQuery,
                new MockAutomatedDeploymentProvider(), ids::incrementAndGet,
                Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 创建资源申请校验环境物理子系统和部署单元并写入草稿() {
        ResourceRequest saved = request(900001L, RequestStatus.DRAFT, ACTOR.id(), 0, false);
        ResourceRequestItem savedItem = item(900002L, 900001L, 300L);
        when(store.findPhysical(7L, 100L)).thenReturn(Optional.of(activePhysical()));
        when(store.findEnvironment(7L, 200L)).thenReturn(Optional.of(activeEnvironment()));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L)));
        when(store.findRequest(7L, 900001L)).thenReturn(Optional.of(saved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(savedItem));
        when(store.listHistory(7L, 900001L)).thenReturn(List.of());

        var detail = service.createRequest(ACTOR, command(100L, 200L, 300L));

        assertThat(detail.request().status()).isEqualTo(RequestStatus.DRAFT);
        assertThat(detail.items()).hasSize(1);
        verify(store).insertResourceRequest(any(ResourceRequest.class));
        verify(store).replaceItems(eq(7L), eq(900001L), any());
        verify(store).insertHistory(any(HistoryEvent.class));
    }

    @Test
    void 登记表明细允许零节点数据库存储需求() {
        ResourceRequest saved = request(900001L, RequestStatus.DRAFT, ACTOR.id(), 0, false);
        when(store.findPhysical(7L, 100L)).thenReturn(Optional.of(activePhysical()));
        when(store.findEnvironment(7L, 200L)).thenReturn(Optional.of(activeEnvironment()));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(databaseUnit(300L, 100L)));
        when(store.findRequest(7L, 900001L)).thenReturn(Optional.of(saved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(databaseItem(900002L, 900001L, 300L)));
        when(store.listHistory(7L, 900001L)).thenReturn(List.of());

        service.createRequest(ACTOR, new ResourceRequestCommand(100L, 200L, ACTOR.id(), RequestType.INITIAL,
                "数据库扩容", List.of(new ResourceItemCommand(300L, BigDecimal.valueOf(500),
                BigDecimal.ZERO, "开放区", "architecture.server-type.container",
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                false, "GoldenDB", "6.1", null, null, null, BigDecimal.ZERO,
                BigDecimal.ZERO, false, false, false, "仅测试登记表口径")), null));

        verify(store).insertResourceRequest(any(ResourceRequest.class));
        verify(store).replaceItems(eq(7L), eq(900001L), any());
    }

    @Test
    void 部署单元不属于所选物理子系统时拒绝创建() {
        when(store.findPhysical(7L, 100L)).thenReturn(Optional.of(activePhysical()));
        when(store.findEnvironment(7L, 200L)).thenReturn(Optional.of(activeEnvironment()));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 999L)));

        assertThatThrownBy(() -> service.createRequest(ACTOR, command(100L, 200L, 300L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void 资源容量不允许小数() {
        when(store.findPhysical(7L, 100L)).thenReturn(Optional.of(activePhysical()));
        when(store.findEnvironment(7L, 200L)).thenReturn(Optional.of(activeEnvironment()));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L)));

        ResourceRequestCommand command = new ResourceRequestCommand(100L, 200L, ACTOR.id(), RequestType.INITIAL,
                "新环境资源", List.of(new ResourceItemCommand(300L, BigDecimal.ZERO,
                BigDecimal.valueOf(100), "开放区", "architecture.server-type.container", BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(4), 1, 2, BigDecimal.ZERO, BigDecimal.ZERO,
                false, null, null, "architecture.jdk.jdk17", "architecture.middleware.tomcat9",
                "architecture.os.rhel8-5",
                BigDecimal.ZERO, BigDecimal.ZERO, false, false, false, "应用节点")), null);

        assertThatThrownBy(() -> service.createRequest(ACTOR, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CPU必须为整数");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 关闭边车时忽略边车容量和占比() {
        ResourceRequest saved = request(900001L, RequestStatus.DRAFT, ACTOR.id(), 0, false);
        ResourceRequestItem savedItem = item(900002L, 900001L, 300L);
        when(store.findPhysical(7L, 100L)).thenReturn(Optional.of(activePhysical()));
        when(store.findEnvironment(7L, 200L)).thenReturn(Optional.of(activeEnvironment()));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L)));
        when(store.findRequest(7L, 900001L)).thenReturn(Optional.of(saved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(savedItem));
        when(store.listHistory(7L, 900001L)).thenReturn(List.of());
        ArgumentCaptor<List<ResourceRequestItem>> items = ArgumentCaptor.forClass(List.class);

        service.createRequest(ACTOR, new ResourceRequestCommand(100L, 200L, ACTOR.id(), RequestType.INITIAL,
                "应用资源", List.of(new ResourceItemCommand(300L, BigDecimal.ZERO,
                BigDecimal.valueOf(100), "开放区", "architecture.server-type.container", BigDecimal.valueOf(2),
                BigDecimal.valueOf(4), 1, 2, BigDecimal.valueOf(3), BigDecimal.ONE,
                false, null, null, "architecture.jdk.jdk17", "architecture.middleware.tomcat9",
                "architecture.os.rhel8-5", BigDecimal.ZERO, BigDecimal.ZERO,
                false, false, false, "关闭边车时前端残留值应被忽略")), null));

        verify(store).replaceItems(eq(7L), eq(900001L), items.capture());
        ResourceRequestItem persisted = items.getValue().get(0);
        assertThat(persisted.hasSidecar()).isFalse();
        assertThat(persisted.sidecarCpuCores()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(persisted.sidecarMemoryGb()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(persisted.totalCpuCores()).isEqualByComparingTo("4");
        assertThat(persisted.totalMemoryGb()).isEqualByComparingTo("8");
        assertThat(persisted.sidecarMemoryRatio()).isNull();

        ResourceRequestItem zeroNodeSidecar = new ResourceRequestItem(900003L, 7L, 900001L, 1, 300L,
                "DW0001A001", "接入应用", "APPLICATION", "接入应用节点",
                BigDecimal.ZERO, BigDecimal.ZERO, "开放区", "architecture.server-type.container",
                BigDecimal.ZERO, BigDecimal.valueOf(8), 0, 0, BigDecimal.ZERO, BigDecimal.ONE,
                true, null, null, "architecture.jdk.jdk17", "architecture.middleware.tomcat9",
                "architecture.os.rhel8-5", BigDecimal.ZERO, BigDecimal.ZERO,
                false, false, false, null, TIME, TIME);
        assertThat(zeroNodeSidecar.totalMemoryGb()).isEqualByComparingTo("1");
        assertThat(zeroNodeSidecar.sidecarMemoryRatio()).isNull();
    }

    @Test
    void 他人草稿即使有查看范围也不能编辑() {
        ResourceRequest foreign = request(900010L, RequestStatus.DRAFT, OTHER.id(), 1, false);
        when(store.findRequest(7L, 900010L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.updateRequest(ACTOR, 900010L,
                command(100L, 200L, 300L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 提交前复验当前引用仍有效并生成工作流摘要() {
        ResourceRequest draft = request(900020L, RequestStatus.DRAFT, ACTOR.id(), 3, false);
        ResourceRequest submitted = request(900020L, RequestStatus.IN_REVIEW, ACTOR.id(), 4, false);
        ResourceRequestItem requestItem = item(900021L, 900020L, 300L);
        AtomicReference<SubmissionPreparation> preparation = new AtomicReference<>();
        when(store.lockRequest(7L, 900020L)).thenReturn(Optional.of(draft)).thenReturn(Optional.of(submitted));
        when(store.listItems(7L, 900020L)).thenReturn(List.of(requestItem));
        when(store.findPhysical(7L, 100L)).thenReturn(Optional.of(activePhysical()));
        when(store.findEnvironment(7L, 200L)).thenReturn(Optional.of(activeEnvironment()));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L)));
        when(store.compareAndSetStatus(7L, 900020L, RequestStatus.DRAFT, 3L,
                RequestStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        service.coordinateSubmission(ACTOR, 900020L, 3L, preparation::set);

        assertThat(preparation.get()).isNotNull();
        assertThat(preparation.get().nextRound()).isEqualTo(1);
        assertThat(preparation.get().digest()).hasSize(64);
        verify(store).insertHistory(any(HistoryEvent.class));
    }

    @Test
    void 审批通过只进入申请批准状态且历史标明实际分配待后续接入() {
        ResourceRequest review = request(900030L, RequestStatus.IN_REVIEW, ACTOR.id(), 5, false);
        ResourceRequest approved = request(900030L, RequestStatus.APPROVED, ACTOR.id(), 6, false);
        when(store.lockRequest(7L, 900030L)).thenReturn(Optional.of(review)).thenReturn(Optional.of(approved));
        when(store.compareAndSetStatus(7L, 900030L, RequestStatus.IN_REVIEW, 5L,
                RequestStatus.APPROVED, 101L)).thenReturn(true);
        when(store.listItems(7L, 900030L)).thenReturn(List.of(item(900031L, 900030L, 300L)));
        ArgumentCaptor<HistoryEvent> history = ArgumentCaptor.forClass(HistoryEvent.class);

        service.applyApprovalInCurrentTransaction(7L, 900030L, 5L, 101L);

        verify(store).compareAndSetStatus(7L, 900030L, RequestStatus.IN_REVIEW, 5L,
                RequestStatus.APPROVED, 101L);
        verify(store).insertHistory(history.capture());
        assertThat(history.getValue().summary()).contains("实际分配待后续搭建任务接入");
    }

    @Test
    void own范围禁止查看他人资源申请() {
        ResourceRequest foreign = request(900040L, RequestStatus.DRAFT, OTHER.id(), 1, false);
        when(store.findRequest(7L, 900040L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.detailRequest(ACTOR, AccessScope.OWN, 900040L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private ResourceRequestCommand command(long physicalId, long environmentId, long unitId) {
        return new ResourceRequestCommand(physicalId, environmentId, ACTOR.id(), RequestType.INITIAL, "新环境资源",
                List.of(new ResourceItemCommand(unitId, BigDecimal.ZERO,
                BigDecimal.valueOf(100), "开放区", "architecture.server-type.container", BigDecimal.valueOf(2),
                BigDecimal.valueOf(4), 1, 2, BigDecimal.ZERO, BigDecimal.ZERO,
                false, null, null, "architecture.jdk.jdk17", "architecture.middleware.tomcat9",
                "architecture.os.rhel8-5",
                BigDecimal.ZERO, BigDecimal.ZERO, false, false, false, "应用节点")), null);
    }

    private PhysicalSubsystemRef activePhysical() {
        return new PhysicalSubsystemRef(100L, "W0001A", "EACP", "电子渠道接入", "上海",
                "P8", "architecture.system-level.a-plus", "architecture.disaster-recovery.active-standby",
                "ACTIVE", false);
    }

    private Environment activeEnvironment() {
        return new Environment(200L, 7L, "DEV-A", "开发环境 A", "architecture.environment-type.dev",
                "开发环境", RecordStatus.ACTIVE, null, null, 0, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }

    private DeploymentUnitRef activeUnit(long id, long physicalId) {
        return activeUnit(id, physicalId, 1_001L, 1);
    }

    private DeploymentUnitRef activeUnit(long id, long physicalId, long currentVersionId, int currentVersion) {
        return new DeploymentUnitRef(id, "DW0001A001", "接入应用", "APPLICATION", "ACTIVE",
                physicalId, "接入应用节点", currentVersionId, currentVersion);
    }

    private DeploymentUnitRef databaseUnit(long id, long physicalId) {
        return new DeploymentUnitRef(id, "DW0001D001", "接入数据库", "DATABASE", "ACTIVE",
                physicalId, "接入数据库", 1_002L, 1);
    }

    private EnvironmentResourceService serviceWithNetworkAccess() {
        return new EnvironmentResourceService(store, new ObjectMapper(), referenceQuery,
                new MockAutomatedDeploymentProvider(), ids::incrementAndGet,
                Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC),
                networkAccessService);
    }

    private ResourceRequest request(long id, RequestStatus status, long applicantId,
                                    long rowVersion, boolean cancellationRequested) {
        return new ResourceRequest(id, 7L, "RR" + id, 100L, "W0001A", "EACP",
                "电子渠道接入", "上海", "architecture.system-level.a-plus", "P8",
                "architecture.disaster-recovery.active-standby",
                200L, "DEV-A", "开发环境 A", "architecture.environment-type.dev", applicantId, applicantId,
                RequestType.INITIAL, "新环境资源", status, 0, null, null, null, null, cancellationRequested,
                rowVersion, applicantId, applicantId, TIME, TIME);
    }

    private ResourceRequestItem item(long id, long requestId, long unitId) {
        return new ResourceRequestItem(id, 7L, requestId, 1, unitId, "DW0001A001",
                "接入应用", "APPLICATION", "接入应用节点",
                BigDecimal.ZERO, BigDecimal.valueOf(100),
                "开放区", "architecture.server-type.container", BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                1, 2, BigDecimal.ZERO, BigDecimal.ZERO, false, null, null,
                "architecture.jdk.jdk17", "architecture.middleware.tomcat9", "architecture.os.rhel8-5",
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, false, false, null, TIME, TIME);
    }

    private ResourceRequestItem itemWithZone(long id, long requestId, long unitId) {
        return new ResourceRequestItem(id, 7L, requestId, 1, unitId, "DW0001A001",
                "接入应用", "APPLICATION", "接入应用节点",
                BigDecimal.ZERO, BigDecimal.valueOf(100),
                800L, "P8开放AP", "P8开放AP", "architecture.server-type.container",
                BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                1, 2, BigDecimal.ZERO, BigDecimal.ZERO, false, null, null,
                "architecture.jdk.jdk17", "architecture.middleware.tomcat9", "architecture.os.rhel8-5",
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, false, false, null, TIME, TIME);
    }

    private ResourceRequestItem databaseItem(long id, long requestId, long unitId) {
        return new ResourceRequestItem(id, 7L, requestId, 1, unitId, "DW0001D001",
                "接入数据库", "DATABASE", "接入数据库",
                BigDecimal.valueOf(500), BigDecimal.ZERO,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, false, "GoldenDB", "6.1", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false, false, null, TIME, TIME);
    }

    @Test
    void 自动部署预览生成正确带出Mock机器名和IP() {
        ResourceRequest approved = request(900001L, RequestStatus.APPROVED, ACTOR.id(), 0, false);
        ResourceRequestItem item = item(900002L, 900001L, 300L);
        when(store.findRequest(7L, 900001L)).thenReturn(Optional.of(approved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(item));
        when(store.countInstancesForEnvironmentUnit(7L, 200L, 300L)).thenReturn(5);

        ProvisionPreviewResult preview = service.previewAutomatedProvision(ACTOR, 900001L);

        assertThat(preview.success()).isTrue();
        assertThat(preview.instances()).hasSize(2);
        assertThat(preview.instances().get(0).sourceItemId()).isEqualTo(900002L);
        assertThat(preview.instances().get(0).machineName()).isEqualTo("dev-a-dw0001a001-0006");
        assertThat(preview.instances().get(1).machineName()).isEqualTo("dev-a-dw0001a001-0007");
        assertThat(preview.instances()).extracting("ipAddress").doesNotHaveDuplicates();
        assertThat(preview.instances().get(0).ipAddress()).matches("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
        assertThat(preview.instances().get(0).cpuCores()).isEqualByComparingTo("2");
        assertThat(preview.instances().get(0).memoryGb()).isEqualByComparingTo("4");
    }

    @Test
    void 自动部署预览按网络分区启用网段生成IP() {
        service = serviceWithNetworkAccess();
        ResourceRequest approved = request(900001L, RequestStatus.APPROVED, ACTOR.id(), 0, false);
        ResourceRequestItem item = itemWithZone(900002L, 900001L, 300L);
        when(store.findRequest(7L, 900001L)).thenReturn(Optional.of(approved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(item));
        when(store.countInstancesForEnvironmentUnit(7L, 200L, 300L)).thenReturn(0);
        when(networkAccessService.requirePrimaryActiveSubnetCidr(7L, 800L, "自动部署明细 1"))
                .thenReturn("10.16.32.0/29");

        ProvisionPreviewResult preview = service.previewAutomatedProvision(ACTOR, 900001L);

        assertThat(preview.instances()).hasSize(2);
        assertThat(preview.instances()).extracting("ipAddress")
                .allMatch(ip -> ip.toString().matches("10\\.16\\.32\\.[1-6]"));
    }

    @Test
    void 审批通过工单手动填报完成下发生成环境部署实例() {
        ResourceRequest approved = request(900001L, RequestStatus.APPROVED, ACTOR.id(), 0, false);
        ResourceRequestItem item = item(900002L, 900001L, 300L);
        when(store.lockRequest(7L, 900001L)).thenReturn(Optional.of(approved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(item));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L)));
        when(store.findActiveInstanceByMachineOrIp(eq(7L), eq(200L), any(), any(), eq(null)))
                .thenReturn(Optional.empty());
        when(store.compareAndSetStatus(7L, 900001L, RequestStatus.APPROVED, 0L,
                RequestStatus.FULFILLED, ACTOR.id())).thenReturn(true);

        FulfillInstanceItemCommand node1 = new FulfillInstanceItemCommand(
                900002L, 300L, "vm-eacp-01", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, "architecture.jdk.jdk17", "architecture.middleware.tomcat9",
                "architecture.os.rhel8-5", false, false, false, FulfillmentMode.MANUAL, "节点1");

        FulfillInstanceItemCommand node2 = new FulfillInstanceItemCommand(
                900002L, 300L, "vm-eacp-02", "10.10.1.2", "architecture.server-type.container",
                "TSF", "开放区", BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, "architecture.jdk.jdk17", "architecture.middleware.tomcat9",
                "architecture.os.rhel8-5", false, false, false, FulfillmentMode.MANUAL, "节点2");

        FulfillmentCommand cmd = new FulfillmentCommand(FulfillmentMode.MANUAL, null, List.of(node1, node2), 0L);

        List<EnvironmentInstance> result = service.fulfillRequest(ACTOR, 900001L, cmd);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).machineName()).isEqualTo("vm-eacp-01");
        assertThat(result.get(0).ipAddress()).isEqualTo("10.10.1.1");
        assertThat(result.get(0).status()).isEqualTo(InstanceStatus.ACTIVE);
        verify(store, org.mockito.Mockito.times(2)).insertInstance(any(EnvironmentInstance.class));
        verify(store).compareAndSetStatus(7L, 900001L, RequestStatus.APPROVED, 0L,
                RequestStatus.FULFILLED, ACTOR.id());
        ArgumentCaptor<HistoryEvent> history = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(store).insertHistory(history.capture());
        assertThat(history.getValue().eventType()).isEqualTo("FULFILLED");
        assertThat(history.getValue().toStatus()).isEqualTo(RequestStatus.FULFILLED);
        assertThat(history.getValue().summary()).contains("已按申请规格下发");
    }

    @Test
    void 下发实例记录部署单元当前发布版本快照() {
        ResourceRequest approved = request(900001L, RequestStatus.APPROVED, ACTOR.id(), 0, false);
        ResourceRequestItem item = item(900002L, 900001L, 300L);
        when(store.lockRequest(7L, 900001L)).thenReturn(Optional.of(approved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(item));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L, 910003L, 3)));
        when(store.findActiveInstanceByMachineOrIp(eq(7L), eq(200L), any(), any(), eq(null)))
                .thenReturn(Optional.empty());
        when(store.compareAndSetStatus(7L, 900001L, RequestStatus.APPROVED, 0L,
                RequestStatus.DIFF_FULFILLED, ACTOR.id())).thenReturn(true);

        FulfillInstanceItemCommand node1 = new FulfillInstanceItemCommand(
                900002L, 300L, "vm-eacp-01", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, false, false, false, FulfillmentMode.MANUAL, "节点1");
        FulfillmentCommand cmd = new FulfillmentCommand(FulfillmentMode.MANUAL, "单节点部署", List.of(node1), 0L);

        List<EnvironmentInstance> result = service.fulfillRequest(ACTOR, 900001L, cmd);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deploymentUnitVersionId()).isEqualTo(910003L);
        assertThat(result.get(0).deploymentUnitVersionNo()).isEqualTo(3);
        assertThat(result.get(0).latestDeploymentUnitVersionNo()).isEqualTo(3);
        assertThat(result.get(0).hasVersionDifference()).isFalse();
        ArgumentCaptor<HistoryEvent> history = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(store).insertHistory(history.capture());
        assertThat(history.getValue().eventType()).isEqualTo("DIFF_FULFILLED");
        assertThat(history.getValue().toStatus()).isEqualTo(RequestStatus.DIFF_FULFILLED);
        assertThat(history.getValue().summary()).contains("已差异下发");
    }

    @Test
    void 实际下发资源与申请存在差异时强制要求记录差异原因() {
        ResourceRequest approved = request(900001L, RequestStatus.APPROVED, ACTOR.id(), 0, false);
        ResourceRequestItem item = item(900002L, 900001L, 300L); // plannedNodeCount = 2, cpu = 2, total = 4
        when(store.lockRequest(7L, 900001L)).thenReturn(Optional.of(approved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(item));

        // Actual is only 1 node, so requested != actual
        FulfillInstanceItemCommand node1 = new FulfillInstanceItemCommand(
                900002L, 300L, "vm-eacp-01", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, false, false, false, FulfillmentMode.MANUAL, "节点1");

        FulfillmentCommand cmdWithoutReason = new FulfillmentCommand(FulfillmentMode.MANUAL, null, List.of(node1), 0L);

        assertThatThrownBy(() -> service.fulfillRequest(ACTOR, 900001L, cmdWithoutReason))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实际下发资源与工单申请值存在差异");
    }

    @Test
    void 同环境存在同名机器或IP在用实例时下发拒绝() {
        ResourceRequest approved = request(900001L, RequestStatus.APPROVED, ACTOR.id(), 0, false);
        ResourceRequestItem item = item(900002L, 900001L, 300L);
        when(store.lockRequest(7L, 900001L)).thenReturn(Optional.of(approved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(item));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L)));

        EnvironmentInstance existing = new EnvironmentInstance(
                999L, 7L, "INS999", 200L, "DEV-A", "开发环境 A", "开发环境",
                300L, "DW0001A001", "接入应用", "APPLICATION", null, 1, 1, false,
                100L, "W0001A", "电子渠道接入", 900000L, "RR900000", null,
                "vm-duplicate", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", InstanceStatus.ACTIVE, BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                null, null, null, false, false, false, FulfillmentMode.MANUAL, null, null,
                null, null, null, 0L, 1L, 1L, TIME, TIME);

        when(store.findActiveInstanceByMachineOrIp(eq(7L), eq(200L), eq("vm-duplicate"), eq("10.10.1.1"), eq(null)))
                .thenReturn(Optional.of(existing));

        FulfillInstanceItemCommand node1 = new FulfillInstanceItemCommand(
                900002L, 300L, "vm-duplicate", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", BigDecimal.valueOf(4), BigDecimal.valueOf(8),
                BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, false, false, false, FulfillmentMode.MANUAL, "重复节点");

        FulfillmentCommand cmd = new FulfillmentCommand(FulfillmentMode.MANUAL, "缩减为1台大规格", List.of(node1), 0L);

        assertThatThrownBy(() -> service.fulfillRequest(ACTOR, 900001L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在机器名或 IP 相同的在用实例");
    }

    @Test
    void 手动下发IP不属于网络分区启用网段时拒绝() {
        service = serviceWithNetworkAccess();
        ResourceRequest approved = request(900001L, RequestStatus.APPROVED, ACTOR.id(), 0, false);
        ResourceRequestItem item = itemWithZone(900002L, 900001L, 300L);
        when(store.lockRequest(7L, 900001L)).thenReturn(Optional.of(approved));
        when(store.listItems(7L, 900001L)).thenReturn(List.of(item));
        when(store.findDeploymentUnit(7L, 300L)).thenReturn(Optional.of(activeUnit(300L, 100L)));
        when(store.findActiveInstanceByMachineOrIp(eq(7L), eq(200L), any(), any(), eq(null)))
                .thenReturn(Optional.empty());
        when(networkAccessService.requireActiveLeafZone(7L, 800L, "第 1 台实例网络分区"))
                .thenReturn(new ZoneRef(800L, "P8_APP", "P8开放AP"));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.BAD_REQUEST,
                        "第 1 台实例 IP 地址「10.17.1.10」不属于网络分区「P8开放AP」的启用网段"))
                .when(networkAccessService).requireIpInActiveSubnet(7L, 800L,
                        "10.17.1.10", "第 1 台实例 IP 地址");

        FulfillInstanceItemCommand node1 = new FulfillInstanceItemCommand(
                900002L, 300L, "vm-eacp-01", "10.17.1.10", "architecture.server-type.container",
                "TSF", 800L, "P8开放AP", BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, false, false, false, FulfillmentMode.MANUAL, "越界节点");

        FulfillmentCommand cmd = new FulfillmentCommand(FulfillmentMode.MANUAL, "单节点差异下发", List.of(node1), 0L);

        assertThatThrownBy(() -> service.fulfillRequest(ACTOR, 900001L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于网络分区");
    }

    @Test
    void 实例下线校验状态与版本并更新下线事实() {
        EnvironmentInstance active = new EnvironmentInstance(
                900010L, 7L, "INS900010", 200L, "DEV-A", "开发环境 A", "开发环境",
                300L, "DW0001A001", "接入应用", "APPLICATION", null, 1, 1, false,
                100L, "W0001A", "电子渠道接入", 900001L, "RR900001", null,
                "vm-eacp-01", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", InstanceStatus.ACTIVE, BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                null, null, null, false, false, false, FulfillmentMode.MANUAL, null, null,
                null, null, null, 0L, 1L, 1L, TIME, TIME);

        EnvironmentInstance offlined = new EnvironmentInstance(
                900010L, 7L, "INS900010", 200L, "DEV-A", "开发环境 A", "开发环境",
                300L, "DW0001A001", "接入应用", "APPLICATION", null, 1, 1, false,
                100L, "W0001A", "电子渠道接入", 900001L, "RR900001", null,
                "vm-eacp-01", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", InstanceStatus.OFFLINE, BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                null, null, null, false, false, false, FulfillmentMode.MANUAL, null, null,
                LocalDateTime.now(), ACTOR.id(), "缩容下线机器", 1L, 1L, ACTOR.id(), TIME, TIME);

        when(store.lockInstance(7L, 900010L)).thenReturn(Optional.of(active));
        when(store.offlineInstance(eq(7L), eq(900010L), eq(0L), eq("缩容下线机器"), eq(ACTOR.id()), any()))
                .thenReturn(true);
        when(store.findInstance(7L, 900010L)).thenReturn(Optional.of(offlined));

        EnvironmentInstance res = service.offlineInstance(ACTOR, 900010L, new OfflineInstanceCommand("缩容下线机器", 0L));

        assertThat(res.status()).isEqualTo(InstanceStatus.OFFLINE);
        assertThat(res.offlineReason()).isEqualTo("缩容下线机器");
    }

    @Test
    void 同部署单元实例成功建立灾备关系跨部署单元拒绝() {
        EnvironmentInstance instA = new EnvironmentInstance(
                900010L, 7L, "INS900010", 200L, "DEV-A", "开发环境 A", "开发环境",
                300L, "DW0001A001", "接入应用", "APPLICATION", null, 1, 1, false,
                100L, "W0001A", "电子渠道接入", 900001L, "RR900001", null,
                "vm-eacp-01", "10.10.1.1", "architecture.server-type.container",
                "TSF", "开放区", InstanceStatus.ACTIVE, BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                null, null, null, false, false, false, FulfillmentMode.MANUAL, null, null,
                null, null, null, 0L, 1L, 1L, TIME, TIME);

        EnvironmentInstance instB = new EnvironmentInstance(
                900011L, 7L, "INS900011", 201L, "DR-A", "灾备环境 A", "灾备环境",
                300L, "DW0001A001", "接入应用", "APPLICATION", null, 1, 1, false,
                100L, "W0001A", "电子渠道接入", 900002L, "RR900002", null,
                "vm-eacp-dr-01", "10.20.1.1", "architecture.server-type.container",
                "TSF", "开放区", InstanceStatus.ACTIVE, BigDecimal.valueOf(2), BigDecimal.valueOf(4),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                null, null, null, false, false, false, FulfillmentMode.MANUAL, null, null,
                null, null, null, 0L, 1L, 1L, TIME, TIME);

        EnvironmentInstance instDiffUnit = new EnvironmentInstance(
                900012L, 7L, "INS900012", 200L, "DEV-A", "开发环境 A", "开发环境",
                301L, "DW0001D001", "接入数据库", "DATABASE", null, 1, 1, false,
                100L, "W0001A", "电子渠道接入", 900003L, "RR900003", null,
                "vm-db-01", "10.10.2.1", "architecture.server-type.container",
                "TSF", "开放区", InstanceStatus.ACTIVE, BigDecimal.valueOf(4), BigDecimal.valueOf(8),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                null, null, null, false, false, false, FulfillmentMode.MANUAL, null, null,
                null, null, null, 0L, 1L, 1L, TIME, TIME);

        when(store.findInstance(7L, 900010L)).thenReturn(Optional.of(instA));
        when(store.findInstance(7L, 900011L)).thenReturn(Optional.of(instB));
        when(store.findInstance(7L, 900012L)).thenReturn(Optional.of(instDiffUnit));
        when(store.findDisasterRecoveryPair(7L, 900010L, 900011L)).thenReturn(Optional.empty());

        // Same DU across environments -> Success
        DisasterRecoveryCommand validCmd = new DisasterRecoveryCommand(
                300L, 900010L, 900011L, DisasterRecoveryMode.PRIMARY_STANDBY, "主备关系");
        InstanceDisasterRecovery dr = service.createDisasterRecovery(ACTOR, validCmd);
        assertThat(dr.primaryInstanceId()).isEqualTo(900010L);
        assertThat(dr.standbyInstanceId()).isEqualTo(900011L);
        assertThat(dr.drMode()).isEqualTo(DisasterRecoveryMode.PRIMARY_STANDBY);

        // Different DU -> Error
        DisasterRecoveryCommand invalidCmd = new DisasterRecoveryCommand(
                300L, 900010L, 900012L, DisasterRecoveryMode.PRIMARY_STANDBY, "不同DU");
        assertThatThrownBy(() -> service.createDisasterRecovery(ACTOR, invalidCmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能在同一部署单元的实例之间建立");
    }
}

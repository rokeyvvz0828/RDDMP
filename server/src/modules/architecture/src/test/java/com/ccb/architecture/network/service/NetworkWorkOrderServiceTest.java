package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultCommand;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HistoryEvent;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
import com.ccb.architecture.network.service.NetworkWorkOrderService.AccessScope;
import com.ccb.architecture.network.service.NetworkWorkOrderService.CreateCommand;
import com.ccb.architecture.network.service.NetworkWorkOrderService.SubmissionPreparation;
import com.ccb.architecture.network.service.NetworkWorkOrderService.UpdateCommand;
import com.ccb.architecture.network.service.NetworkWorkOrderService.WorkOrderDetail;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkWorkOrderServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);
    private static final AuthUser OTHER = new AuthUser(10L, 7L, "other", "hash", "其他人", 12L, true);
    private static final AuthUser MANAGER = new AuthUser(11L, 7L, "manager", "hash", "网络办理人员", 12L, true);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 10, 0);

    @Mock
    private NetworkWorkOrderStore store;
    @Mock
    private AttachmentGateway attachmentGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong ids = new AtomicLong(900001L);
    private NetworkWorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new NetworkWorkOrderService(store, attachmentGateway, objectMapper,
                ids::incrementAndGet,
                Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC));
    }

    private WorkOrder workOrder(long id, Kind kind, ActionType actionType, String subject,
                                WorkOrderStatus status, long applicantId, long rowVersion) {
        return new WorkOrder(id, 7L, kind, actionType, subject, applicantId, "原因", status,
                "{\"clbName\":\"CLB-A\",\"purpose\":\"流量接入\",\"description\":null}",
                "[]", null, null, "[]", null, null, 0, null, null, null, null, false,
                rowVersion, applicantId, applicantId, TIME, TIME);
    }

    private CreateCommand clbCreate(String clbName) {
        return new CreateCommand(Kind.CLB, ActionType.OPEN, Map.of("clbName", clbName, "purpose", "流量接入"),
                "新环境开通", List.of());
    }

    private CreateCommand certCreate(String certType, String subjectName, List<Long> attachments) {
        return new CreateCommand(Kind.CERT, ActionType.APPLY,
                Map.of("certType", certType, "subjectName", subjectName, "purpose", "上线准备"),
                "上线准备", attachments);
    }

    @Test
    void 创建CLB草稿投影subject并写入历史() {
        when(store.listHistory(anyLong(), anyLong())).thenReturn(List.of());

        WorkOrderDetail detail = service.create(ACTOR, clbCreate(" CLB-A "));

        assertThat(detail.workOrder().kind()).isEqualTo(Kind.CLB);
        assertThat(detail.workOrder().subject()).isEqualTo("CLB-A");
        assertThat(detail.workOrder().status()).isEqualTo(WorkOrderStatus.DRAFT);
        verify(store).insertWorkOrder(any(WorkOrder.class));
        verify(store).insertHistory(any(HistoryEvent.class));
    }

    @Test
    void 创建拒绝跨kind动作() {
        assertThatThrownBy(() -> service.create(ACTOR,
                new CreateCommand(Kind.CLB, ActionType.ADD, Map.of("clbName", "CLB-A", "purpose", "x"),
                        null, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void 创建CERT拒绝非法证书类型() {
        assertThatThrownBy(() -> service.create(ACTOR, certCreate("INTERNAL", "demo.example.test", List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void 创建CERT拒绝私钥类附件() {
        when(attachmentGateway.get(eq(55001L), eq(ACTOR))).thenReturn(
                new AttachmentItem(55001L, "server.key", "application/octet-stream", 10L,
                        "key", "TEMP", null, null, null, ACTOR.id(), TIME));
        assertThatThrownBy(() -> service.create(ACTOR, certCreate("SSL", "demo.example.test", List.of(55001L))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void 创建CERT允许公开证书附件并绑定() {
        when(attachmentGateway.get(eq(55002L), eq(ACTOR))).thenReturn(
                new AttachmentItem(55002L, "public.crt", "application/x-x509-ca-cert", 10L,
                        "crt", "TEMP", null, null, null, ACTOR.id(), TIME));
        when(store.listHistory(anyLong(), anyLong())).thenReturn(List.of());

        service.create(ACTOR, certCreate("ssl", "DEMO.EXAMPLE.TEST", List.of(55002L)));

        verify(attachmentGateway).bind(any(), eq(ACTOR));
    }

    @Test
    void 创建DNS域名归一化为小写() {
        when(store.listHistory(anyLong(), anyLong())).thenReturn(List.of());

        WorkOrderDetail detail = service.create(ACTOR, new CreateCommand(Kind.DNS, ActionType.ADD,
                Map.of("domainName", "Demo.Example.Test", "purpose", "演示"), "新增域名", List.of()));

        assertThat(detail.workOrder().subject()).isEqualTo("demo.example.test");
    }

    @Test
    void 更新仅限本人且草稿状态() {
        WorkOrder owned = workOrder(900005L, Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.DRAFT,
                ACTOR.id(), 1);
        when(store.findWorkOrder(7L, 900005L)).thenReturn(Optional.of(owned));
        when(store.updateDraft(eq(7L), eq(900005L), eq(WorkOrderStatus.DRAFT), eq(1L),
                any(), any(), any(), eq(ACTOR.id()))).thenReturn(true);
        when(store.lockWorkOrder(7L, 900005L)).thenReturn(Optional.of(owned));
        when(store.listHistory(7L, 900005L)).thenReturn(List.of());

        service.update(ACTOR, 900005L, new UpdateCommand(1L, "调整用途",
                Map.of("clbName", "CLB-A", "purpose", "新用途"), List.of()));

        verify(store).insertHistory(any(HistoryEvent.class));
    }

    @Test
    void 更新他人草稿返回403() {
        WorkOrder foreign = workOrder(900005L, Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.DRAFT,
                OTHER.id(), 1);
        when(store.findWorkOrder(7L, 900005L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.update(ACTOR, 900005L,
                new UpdateCommand(1L, "x", Map.of("clbName", "CLB-A", "purpose", "y"), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 提交把草稿推进审批并调用工作流启动器() {
        WorkOrder draft = workOrder(900006L, Kind.DNS, ActionType.ADD, "demo.example.test",
                WorkOrderStatus.DRAFT, ACTOR.id(), 2);
        AtomicReference<SubmissionPreparation> preparation = new AtomicReference<>();
        when(store.lockWorkOrder(7L, 900006L)).thenReturn(Optional.of(draft));
        when(store.compareAndSetStatus(7L, 900006L, WorkOrderStatus.DRAFT, 2L,
                WorkOrderStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        service.coordinateSubmission(ACTOR, 900006L, 2L, prep -> preparation.set(prep));

        assertThat(preparation.get()).isNotNull();
        assertThat(preparation.get().nextRound()).isEqualTo(1);
        assertThat(preparation.get().digest()).hasSize(64);
        verify(store).insertHistory(any(HistoryEvent.class));
    }

    @Test
    void 提交审批中工单返回409() {
        WorkOrder review = workOrder(900006L, Kind.DNS, ActionType.ADD, "demo.example.test",
                WorkOrderStatus.IN_REVIEW, ACTOR.id(), 2);
        when(store.lockWorkOrder(7L, 900006L)).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> service.coordinateSubmission(ACTOR, 900006L, 2L, prep -> { }))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void 审批中取消必须走终止流程() {
        WorkOrder review = workOrder(900007L, Kind.CERT, ActionType.RENEW, "demo.example.test",
                WorkOrderStatus.IN_REVIEW, ACTOR.id(), 3);
        when(store.findWorkOrder(7L, 900007L)).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> service.cancel(ACTOR, AccessScope.OWN, 900007L, 3L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void 草稿同步取消进入CANCELLED() {
        WorkOrder draft = workOrder(900008L, Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.RETURNED,
                ACTOR.id(), 4);
        when(store.findWorkOrder(7L, 900008L)).thenReturn(Optional.of(draft));
        when(store.lockWorkOrder(7L, 900008L)).thenReturn(Optional.of(workOrder(900008L,
                Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.CANCELLED, ACTOR.id(), 5)));
        when(store.compareAndSetStatus(7L, 900008L, WorkOrderStatus.RETURNED, 4L,
                WorkOrderStatus.CANCELLED, ACTOR.id())).thenReturn(true);
        when(store.listHistory(7L, 900008L)).thenReturn(List.of());

        WorkOrderDetail detail = service.cancel(ACTOR, AccessScope.OWN, 900008L, 4L);

        assertThat(detail.workOrder().status()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void 批准事件推进COMPLETED() {
        WorkOrder review = workOrder(900009L, Kind.CLB, ActionType.ADJUST, "CLB-A",
                WorkOrderStatus.IN_REVIEW, ACTOR.id(), 6);
        when(store.lockWorkOrder(7L, 900009L))
                .thenReturn(Optional.of(review))
                .thenReturn(Optional.of(workOrder(900009L, Kind.CLB, ActionType.ADJUST, "CLB-A",
                        WorkOrderStatus.COMPLETED, ACTOR.id(), 7)));
        when(store.compareAndSetStatus(7L, 900009L, WorkOrderStatus.IN_REVIEW, 6L,
                WorkOrderStatus.COMPLETED, 101L)).thenReturn(true);

        service.applyCompletionInCurrentTransaction(7L, 900009L, 6L, 101L);

        verify(store).insertHistory(any(HistoryEvent.class));
    }

    @Test
    void 取消确认需要已登记取消请求() {
        WorkOrder review = workOrder(900010L, Kind.DNS, ActionType.REMOVE, "demo.example.test",
                WorkOrderStatus.IN_REVIEW, ACTOR.id(), 8);
        when(store.lockWorkOrder(7L, 900010L)).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> service.applyCancellationConfirmationInCurrentTransaction(7L, 900010L, 8L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void 办理结果登记仅限审批中或已完成() {
        WorkOrder review = workOrder(900011L, Kind.CERT, ActionType.REVOKE, "demo.example.test",
                WorkOrderStatus.IN_REVIEW, ACTOR.id(), 9);
        WorkOrder updated = new WorkOrder(900011L, 7L, Kind.CERT, ActionType.REVOKE,
                "demo.example.test", ACTOR.id(), "原因", WorkOrderStatus.IN_REVIEW,
                "{\"certType\":\"SSL\",\"subjectName\":\"demo.example.test\",\"purpose\":\"x\",\"description\":null}",
                "[]", com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultStatus.SUCCESS,
                "外部配置已完成", "[]", MANAGER.id(), TIME, 1, null, null, null, null, false, 10,
                ACTOR.id(), ACTOR.id(), TIME, TIME);
        when(store.lockWorkOrder(7L, 900011L))
                .thenReturn(Optional.of(review))
                .thenReturn(Optional.of(updated));
        when(store.updateHandlingResult(eq(7L), eq(900011L), eq(9L), eq("SUCCESS"), any(), any(),
                eq(MANAGER.id()))).thenReturn(true);
        when(store.listHistory(7L, 900011L)).thenReturn(List.of());

        WorkOrderDetail detail = service.registerHandlingResult(MANAGER, 900011L, 9L,
                new HandlingResultCommand("success", "外部配置已完成", List.of()));

        assertThat(detail.workOrder().resultStatus()).isEqualTo(
                com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultStatus.SUCCESS);
        verify(store).insertHistory(any(HistoryEvent.class));
    }

    @Test
    void 草稿状态拒绝登记结果() {
        WorkOrder draft = workOrder(900012L, Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.DRAFT,
                ACTOR.id(), 0);
        when(store.lockWorkOrder(7L, 900012L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.registerHandlingResult(MANAGER, 900012L, 0L,
                new HandlingResultCommand("SUCCESS", "x", List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void 移除附件仅限草稿且属于工单() {
        WorkOrder draft = workOrder(900013L, Kind.DNS, ActionType.ADD, "demo.example.test",
                WorkOrderStatus.DRAFT, ACTOR.id(), 11);
        when(store.lockWorkOrder(7L, 900013L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.removeAttachment(ACTOR, 900013L, 11L, 55099L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void 查看他人工单按OWN范围返回403() {
        WorkOrder foreign = workOrder(900014L, Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.DRAFT,
                OTHER.id(), 1);
        when(store.findWorkOrder(7L, 900014L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.detail(ACTOR, AccessScope.OWN, 900014L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 不存在工单返回404() {
        when(store.findWorkOrder(7L, 999999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(ACTOR, AccessScope.MANAGE, 999999L))
                .isInstanceOf(ArchitectureNotFoundException.class);
    }

    @Test
    void 行版本不匹配时更新返回409() {
        WorkOrder owned = workOrder(900015L, Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.DRAFT,
                ACTOR.id(), 1);
        when(store.findWorkOrder(7L, 900015L)).thenReturn(Optional.of(owned));
        when(store.updateDraft(anyLong(), anyLong(), any(), eq(99L), any(), any(), any(), anyLong()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.update(ACTOR, 900015L,
                new UpdateCommand(99L, "x", Map.of("clbName", "CLB-A", "purpose", "y"), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void 摘要对相同内容稳定且对内容变化敏感() {
        WorkOrder first = workOrder(900016L, Kind.CLB, ActionType.OPEN, "CLB-A", WorkOrderStatus.DRAFT,
                ACTOR.id(), 0);
        WorkOrder second = workOrder(900017L, Kind.CLB, ActionType.OPEN, "CLB-B", WorkOrderStatus.DRAFT,
                ACTOR.id(), 0);
        String digestA = service.digest(first);
        String digestB = service.digest(second);
        assertThat(digestA).isEqualTo(service.digest(first));
        assertThat(digestA).isNotEqualTo(digestB);
    }
}

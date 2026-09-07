package com.ccb.architecture.network.integration;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.*;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectWorkflowDirectoryService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetworkAttachmentAccessPolicyTest {
    final AuthUser actor = new AuthUser(20,7,"member","test","测试成员",1L,true);
    final NetworkWorkOrderStore store = mock(NetworkWorkOrderStore.class);
    final ProjectWorkflowDirectoryService projects = mock(ProjectWorkflowDirectoryService.class);
    final NetworkAttachmentAccessPolicy policy = new NetworkAttachmentAccessPolicy(store, projects);
    final WorkOrder order = mock(WorkOrder.class);
    @BeforeEach void setup() {
        when(store.findWorkOrderById(7,100)).thenReturn(Optional.of(order));
        when(order.projectId()).thenReturn(70L);
        when(order.applicantId()).thenReturn(10L);
        when(order.status()).thenReturn(WorkOrderStatus.DRAFT);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void 查看或申请角色不能读取他人附件() {
        auth(actor,"architecture:network-work-order:view","architecture:network-work-order:apply");
        for (var operation : AttachmentOperation.values()) assertThat(policy.canAccess(actor,"100",operation)).isFalse();
    }
    @Test void 本人可读草稿并可删除() {
        when(order.applicantId()).thenReturn(actor.id());
        for (var operation : AttachmentOperation.values()) assertThat(policy.canAccess(actor,"100",operation)).isTrue();
    }
    @Test void 管理者可读但不能删除已审批附件() {
        auth(actor,"architecture:network-work-order:manage");
        when(order.status()).thenReturn(WorkOrderStatus.IN_REVIEW);
        assertThat(policy.canAccess(actor,"100",AttachmentOperation.READ)).isTrue();
        assertThat(policy.canAccess(actor,"100",AttachmentOperation.DELETE)).isFalse();
    }
    @Test void 他人认证上下文不能冒用管理权限() {
        auth(new AuthUser(30,7,"other","test","其他成员",1L,true),"architecture:network-work-order:manage");
        assertThat(policy.canAccess(actor,"100",AttachmentOperation.READ)).isFalse();
    }
    @Test void 项目已退出则拒绝附件() {
        doThrow(new IllegalStateException("项目不可见")).when(projects).requireAccessible(70,actor);
        when(order.applicantId()).thenReturn(actor.id());
        assertThat(policy.canAccess(actor,"100",AttachmentOperation.READ)).isFalse();
    }
    void auth(AuthUser user,String... roles) { SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user,null,roles)); }
}

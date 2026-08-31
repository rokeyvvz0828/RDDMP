package com.ccb.architecture.network.integration;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 网络专项工单附件的实体授权策略（REQ-20260823-051）。
 *
 * <p>附件只承载申请材料或公开证书：读/预览/下载需要当前租户内可读该工单（本人或具备
 * 网络专项工单权限）；删除仅限草稿/退回且本人或具备 manage 权限。判断失败一律拒绝。</p>
 */
@Component
public class NetworkAttachmentAccessPolicy implements AttachmentAccessPolicy {
    public static final String BUSINESS_TYPE = "architecture_network_work_order";
    private static final String VIEW_AUTHORITY = "architecture:network-work-order:view";
    private static final String APPLY_AUTHORITY = "architecture:network-work-order:apply";
    private static final String MANAGE_AUTHORITY = "architecture:network-work-order:manage";

    private final NetworkWorkOrderStore store;

    public NetworkAttachmentAccessPolicy(NetworkWorkOrderStore store) {
        this.store = store;
    }

    @Override
    public String businessType() {
        return BUSINESS_TYPE;
    }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null || businessKey.isBlank()) {
            return false;
        }
        long workOrderId;
        try {
            workOrderId = Long.parseLong(businessKey.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        if (workOrderId <= 0) {
            return false;
        }
        WorkOrder workOrder = store.findWorkOrder(user.tenantId(), workOrderId).orElse(null);
        if (workOrder == null) {
            return false;
        }
        boolean manage = hasAuthority(MANAGE_AUTHORITY);
        boolean canRead = workOrder.applicantId() == user.id()
                || manage
                || hasAuthority(VIEW_AUTHORITY)
                || hasAuthority(APPLY_AUTHORITY);
        if (operation == AttachmentOperation.DELETE) {
            return canRead
                    && (workOrder.status() == WorkOrderStatus.DRAFT
                        || workOrder.status() == WorkOrderStatus.RETURNED)
                    && (workOrder.applicantId() == user.id() || manage);
        }
        return canRead;
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}

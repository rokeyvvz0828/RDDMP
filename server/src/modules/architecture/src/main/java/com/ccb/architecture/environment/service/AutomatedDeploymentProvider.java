package com.ccb.architecture.environment.service;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionPreviewResult;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionResult;

/**
 * 自动化部署与资源下发提供器接口。
 * <p>
 * 供后续对接真实云平台、K8s/TSF、PaaS 平台或基础设施自动化运维系统，默认提供 Mock 实现。
 */
public interface AutomatedDeploymentProvider {

    /**
     * 根据资源工单信息生成自动部署下发预览（包含计算生成的机器标识、IP、资源配额与日志）。
     *
     * @param request 下发申请信息
     * @return 预览结果
     */
    ProvisionPreviewResult previewProvision(ProvisionRequest request);

    /**
     * 执行自动部署与资源下发。
     *
     * @param request 下发申请信息
     * @return 部署执行结果
     */
    ProvisionResult provision(ProvisionRequest request);
}

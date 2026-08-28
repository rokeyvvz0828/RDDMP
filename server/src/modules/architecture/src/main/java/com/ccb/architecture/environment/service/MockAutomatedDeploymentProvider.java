package com.ccb.architecture.environment.service;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionItemRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionPreviewResult;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionResult;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ProvisionedInstance;
import com.ccb.architecture.network.service.NetworkCidr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 自动化部署与资源下发 Mock 实现。
 * <p>
 * 模拟根据工单规格自动分配机器名、IP、容量配额及技术栈。
 */
@Component
public class MockAutomatedDeploymentProvider implements AutomatedDeploymentProvider {

    private static final String DEFAULT_SERVER_TYPE = "architecture.server-type.container";
    private static final String DEFAULT_DEPLOYMENT_PLATFORM = "TSF";

    @Override
    public ProvisionPreviewResult previewProvision(ProvisionRequest request) {
        String executionId = "MOCK-PREV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        List<ProvisionedInstance> instances = generateInstances(request, executionId);
        return new ProvisionPreviewResult(true, executionId, "自动化部署预览生成成功（Mock 引擎）", instances);
    }

    @Override
    public ProvisionResult provision(ProvisionRequest request) {
        String executionId = "MOCK-EXEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        List<ProvisionedInstance> instances = generateInstances(request, executionId);
        return new ProvisionResult(true, executionId, "自动化部署下发执行成功（Mock 引擎）", instances);
    }

    private List<ProvisionedInstance> generateInstances(ProvisionRequest request, String executionId) {
        List<ProvisionedInstance> instances = new ArrayList<>();
        Set<String> generatedIps = new HashSet<>();
        String envCode = request.environmentCode() == null ? "env" : request.environmentCode().toLowerCase(Locale.ROOT);

        for (ProvisionItemRequest item : request.items()) {
            int nodeCount = Math.max(1, item.plannedNodeCount());
            String unitCode = item.deploymentUnitCode() == null ? "du" : item.deploymentUnitCode().toLowerCase(Locale.ROOT).replace('_', '-');
            String serverType = item.serverType() == null || item.serverType().isBlank() ? DEFAULT_SERVER_TYPE : item.serverType();
            String deploymentPlatform = item.deploymentPlatform() == null || item.deploymentPlatform().isBlank() ? DEFAULT_DEPLOYMENT_PLATFORM : item.deploymentPlatform();
            String networkZone = item.networkZoneName() == null || item.networkZoneName().isBlank()
                    ? (item.networkZone() == null || item.networkZone().isBlank() ? "ZONE-A" : item.networkZone())
                    : item.networkZoneName();

            for (int i = 0; i < nodeCount; i++) {
                int sequence = Math.max(1, item.nextSequenceStart()) + i;
                String machineName = String.format("%s-%s-%04d", envCode, unitCode, sequence);
                String ipAddress = item.networkSubnetCidr() == null || item.networkSubnetCidr().isBlank()
                        ? randomPrivateIp(generatedIps)
                        : NetworkCidr.suggestAddress(item.networkSubnetCidr(), sequence, generatedIps);
                String mockLog = String.format("【Mock自动化部署】执行ID: %s，已完成部署单元 [%s] 节点 %d/%d 分配：机器 [%s]，IP [%s]，网络分区 [%s]。",
                        executionId, item.deploymentUnitCode(), i + 1, nodeCount, machineName, ipAddress, networkZone);

                instances.add(new ProvisionedInstance(
                        item.sourceItemId(),
                        item.itemSeq(),
                        item.deploymentUnitId(),
                        item.deploymentUnitCode(),
                        item.deploymentUnitName(),
                        machineName,
                        ipAddress,
                        serverType,
                        deploymentPlatform,
                        item.networkZoneId(),
                        item.networkZoneName(),
                        networkZone,
                        item.cpuCores(),
                        item.memoryGb(),
                        item.databaseStorageGb(),
                        item.fileStorageGb(),
                        item.extraCbsGb(),
                        item.localDiskGb(),
                        item.databaseName(),
                        item.databaseVersion(),
                        item.jdkVersion(),
                        item.middleware(),
                        item.operatingSystem(),
                        item.needsNft(),
                        item.needsFserver(),
                        item.needsJobexecutor(),
                        item.remark(),
                        mockLog
                ));
            }
        }
        return List.copyOf(instances);
    }

    private String randomPrivateIp(Set<String> generatedIps) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempts = 0; attempts < 100; attempts++) {
            String ip = String.format("10.%d.%d.%d",
                    random.nextInt(20, 240),
                    random.nextInt(1, 255),
                    random.nextInt(10, 245));
            if (generatedIps.add(ip)) {
                return ip;
            }
        }
        String fallback = "10.239.254." + random.nextInt(10, 245);
        generatedIps.add(fallback);
        return fallback;
    }
}

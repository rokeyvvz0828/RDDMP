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
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneOption;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneSubnet;
import com.ccb.architecture.network.model.NetworkAccessModels.RecordStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import com.ccb.architecture.network.persistence.NetworkAccessStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** 网络分区、外部地址、访问申请与关系业务规则。 */
@Service
public class NetworkAccessService {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_\\-]{2,64}");
    private static final Pattern SIMPLE_IP_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    public enum AccessScope {
        OWN,
        MANAGE
    }

    public record NetworkZoneCommand(Long parentId, String code, String name, Integer restrictionLevel,
                                     String description, String remark, Long rowVersion) {
    }

    public record NetworkZoneSubnetCommand(String cidrBlock, String gatewayIp, String purpose,
                                           String remark, Long rowVersion) {
    }

    public record ExternalAddressCommand(AddressType addressType, String addressValue, String displayName,
                                          String purpose, String remark, Long rowVersion) {
    }

    public record NetworkAccessCommand(EndpointCommand source, EndpointCommand target, AccessProtocol protocol,
                                       String ports, String purpose, String processDescription,
                                       LocalDateTime validFrom, LocalDateTime validUntil,
                                       Long rowVersion) {
    }

    public record CloseRelationCommand(String closeReason, Long rowVersion) {
    }

    public record ZoneRef(long id, String code, String name) {
    }

    private final NetworkAccessStore store;
    private final ObjectMapper objectMapper;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public NetworkAccessService(NetworkAccessStore store, ObjectMapper objectMapper) {
        this(store, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC());
    }

    NetworkAccessService(NetworkAccessStore store, ObjectMapper objectMapper,
                         LongSupplier idSupplier, Clock clock) {
        this.store = Objects.requireNonNull(store, "网络访问存储不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Transactional(readOnly = true)
    public List<NetworkZone> listZones(AuthUser actor, RecordStatus status, String keyword) {
        requireActor(actor);
        return store.listZones(actor.tenantId(), status, keyword);
    }

    @Transactional(readOnly = true)
    public List<NetworkZoneOption> listZoneOptions(AuthUser actor, boolean leafOnly) {
        requireActor(actor);
        return store.listZones(actor.tenantId(), RecordStatus.ACTIVE, null).stream()
                .filter(zone -> !leafOnly || !store.hasActiveChildZones(actor.tenantId(), zone.id()))
                .map(zone -> new NetworkZoneOption(zone.id(), zone.code(), zone.name(), zone.parentId(),
                        zone.parentName(), zone.restrictionLevel(),
                        !store.hasActiveChildZones(actor.tenantId(), zone.id())))
                .toList();
    }

    @Transactional
    public NetworkZone createZone(AuthUser actor, NetworkZoneCommand command) {
        requireActor(actor);
        PreparedZone prepared = prepareZone(actor, command, null);
        long id = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        NetworkZone zone = new NetworkZone(id, actor.tenantId(), prepared.parentId(), prepared.parentName(),
                prepared.code(), prepared.name(), prepared.restrictionLevel(), RecordStatus.ACTIVE,
                prepared.description(), prepared.remark(), 0L, actor.id(), actor.id(), now, now);
        store.insertZone(zone);
        return store.findZone(actor.tenantId(), id).orElse(zone);
    }

    @Transactional
    public NetworkZone updateZone(AuthUser actor, long id, NetworkZoneCommand command) {
        requireActor(actor);
        NetworkZone current = store.lockZone(actor.tenantId(), id).orElseThrow(() -> notFound("网络分区不存在"));
        if (current.status() != RecordStatus.ACTIVE) {
            throw conflict("已停用网络分区不能修改");
        }
        if (command == null || command.rowVersion() == null || command.rowVersion() < 0) {
            throw badRequest("rowVersion 必须为非负整数");
        }
        if (!Objects.equals(current.parentId(), command.parentId()) && store.hasActiveChildZones(actor.tenantId(), id)) {
            throw conflict("存在启用子分区的分区不能调整父级");
        }
        if (!Objects.equals(current.parentId(), command.parentId()) && store.hasActiveSubnets(actor.tenantId(), id)) {
            throw conflict("存在启用网段的分区不能调整父级");
        }
        PreparedZone prepared = prepareZone(actor, command, id);
        if (!store.updateZone(actor.tenantId(), id, command.rowVersion(), prepared.parentId(), prepared.code(),
                prepared.name(), prepared.restrictionLevel(), prepared.description(), prepared.remark(), actor.id())) {
            throw conflict("网络分区已被其他操作修改，请刷新重试");
        }
        return store.findZone(actor.tenantId(), id).orElseThrow(() -> notFound("网络分区不存在"));
    }

    @Transactional
    public NetworkZone deactivateZone(AuthUser actor, long id) {
        requireActor(actor);
        NetworkZone current = store.lockZone(actor.tenantId(), id).orElseThrow(() -> notFound("网络分区不存在"));
        if (store.hasActiveChildZones(actor.tenantId(), id)) {
            throw conflict("存在启用子分区的网络分区不能停用");
        }
        if (store.hasActiveSubnets(actor.tenantId(), id)) {
            throw conflict("存在启用网段的网络分区不能停用");
        }
        if (!store.updateZoneStatus(actor.tenantId(), id, RecordStatus.ACTIVE, RecordStatus.INACTIVE, actor.id())) {
            throw conflict("网络分区当前状态不允许停用");
        }
        return store.findZone(actor.tenantId(), id).orElse(current);
    }

    @Transactional
    public NetworkZone reactivateZone(AuthUser actor, long id) {
        requireActor(actor);
        NetworkZone current = store.lockZone(actor.tenantId(), id).orElseThrow(() -> notFound("网络分区不存在"));
        if (current.parentId() != null) {
            NetworkZone parent = requireActiveZone(actor.tenantId(), current.parentId());
            if (current.restrictionLevel() < parent.restrictionLevel()) {
                throw conflict("子分区限制级别低于父分区，不能启用");
            }
        }
        if (!store.updateZoneStatus(actor.tenantId(), id, RecordStatus.INACTIVE, RecordStatus.ACTIVE, actor.id())) {
            throw conflict("网络分区当前状态不允许启用");
        }
        return store.findZone(actor.tenantId(), id).orElse(current);
    }

    @Transactional(readOnly = true)
    public ZoneRef requireActiveLeafZone(long tenantId, Long zoneId, String label) {
        if (zoneId == null || zoneId <= 0) {
            throw badRequest(label + "不能为空");
        }
        NetworkZone zone = requireActiveZone(tenantId, zoneId);
        if (store.hasActiveChildZones(tenantId, zoneId)) {
            throw badRequest(label + "必须选择启用叶子分区");
        }
        return new ZoneRef(zone.id(), zone.code(), zone.name());
    }

    @Transactional(readOnly = true)
    public List<NetworkZoneSubnet> listSubnets(AuthUser actor, Long zoneId, RecordStatus status) {
        requireActor(actor);
        return store.listSubnets(actor.tenantId(), zoneId, status);
    }

    @Transactional
    public NetworkZoneSubnet createSubnet(AuthUser actor, long zoneId, NetworkZoneSubnetCommand command) {
        requireActor(actor);
        requireActiveLeafZone(actor.tenantId(), zoneId, "网络分区网段归属分区");
        PreparedSubnet prepared = prepareSubnet(actor, command, null);
        long id = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        NetworkZoneSubnet subnet = new NetworkZoneSubnet(id, actor.tenantId(), zoneId, null, null,
                prepared.cidrBlock(), prepared.gatewayIp(), prepared.purpose(), RecordStatus.ACTIVE,
                prepared.remark(), 0L, actor.id(), actor.id(), now, now);
        store.insertSubnet(subnet);
        return store.findSubnet(actor.tenantId(), id).orElse(subnet);
    }

    @Transactional
    public NetworkZoneSubnet updateSubnet(AuthUser actor, long zoneId, long subnetId,
                                          NetworkZoneSubnetCommand command) {
        requireActor(actor);
        NetworkZoneSubnet current = store.lockSubnet(actor.tenantId(), subnetId)
                .orElseThrow(() -> notFound("网络分区网段不存在"));
        if (current.networkZoneId() != zoneId) {
            throw badRequest("网络分区网段不属于当前分区");
        }
        if (current.status() != RecordStatus.ACTIVE) {
            throw conflict("已停用网络分区网段不能修改");
        }
        if (command == null || command.rowVersion() == null || command.rowVersion() < 0) {
            throw badRequest("rowVersion 必须为非负整数");
        }
        requireActiveLeafZone(actor.tenantId(), zoneId, "网络分区网段归属分区");
        PreparedSubnet prepared = prepareSubnet(actor, command, subnetId);
        if (!store.updateSubnet(actor.tenantId(), subnetId, command.rowVersion(), prepared.cidrBlock(),
                prepared.gatewayIp(), prepared.purpose(), prepared.remark(), actor.id())) {
            throw conflict("网络分区网段已被其他操作修改，请刷新重试");
        }
        return store.findSubnet(actor.tenantId(), subnetId).orElseThrow(() -> notFound("网络分区网段不存在"));
    }

    @Transactional
    public NetworkZoneSubnet deactivateSubnet(AuthUser actor, long zoneId, long subnetId) {
        requireActor(actor);
        NetworkZoneSubnet current = store.lockSubnet(actor.tenantId(), subnetId)
                .orElseThrow(() -> notFound("网络分区网段不存在"));
        if (current.networkZoneId() != zoneId) {
            throw badRequest("网络分区网段不属于当前分区");
        }
        if (!store.updateSubnetStatus(actor.tenantId(), subnetId, RecordStatus.ACTIVE,
                RecordStatus.INACTIVE, actor.id())) {
            throw conflict("网络分区网段当前状态不允许停用");
        }
        return store.findSubnet(actor.tenantId(), subnetId).orElse(current);
    }

    @Transactional
    public NetworkZoneSubnet reactivateSubnet(AuthUser actor, long zoneId, long subnetId) {
        requireActor(actor);
        NetworkZoneSubnet current = store.lockSubnet(actor.tenantId(), subnetId)
                .orElseThrow(() -> notFound("网络分区网段不存在"));
        if (current.networkZoneId() != zoneId) {
            throw badRequest("网络分区网段不属于当前分区");
        }
        requireActiveLeafZone(actor.tenantId(), zoneId, "网络分区网段归属分区");
        if (!store.updateSubnetStatus(actor.tenantId(), subnetId, RecordStatus.INACTIVE,
                RecordStatus.ACTIVE, actor.id())) {
            throw conflict("网络分区网段当前状态不允许启用");
        }
        return store.findSubnet(actor.tenantId(), subnetId).orElse(current);
    }

    @Transactional(readOnly = true)
    public NetworkZoneSubnet requireIpInActiveSubnet(long tenantId, Long zoneId, String ipAddress, String label) {
        if (zoneId == null || zoneId <= 0) {
            throw badRequest(label + "缺少网络分区");
        }
        NetworkZone zone = requireActiveZone(tenantId, zoneId);
        String ip = required(ipAddress, label, 64);
        try {
            NetworkCidr.parseIpv4(ip);
        } catch (IllegalArgumentException exception) {
            throw badRequest(label + "格式无效：" + ip);
        }
        List<NetworkZoneSubnet> subnets = store.listSubnets(tenantId, zoneId, RecordStatus.ACTIVE);
        if (subnets.isEmpty()) {
            throw badRequest("网络分区「" + zone.name() + "」未配置启用网段，不能下发实例 IP");
        }
        for (NetworkZoneSubnet subnet : subnets) {
            if (NetworkCidr.contains(subnet.cidrBlock(), ip)) {
                return subnet;
            }
        }
        throw badRequest(label + "「" + ip + "」不属于网络分区「" + zone.name() + "」的启用网段");
    }

    @Transactional(readOnly = true)
    public String requirePrimaryActiveSubnetCidr(long tenantId, Long zoneId, String label) {
        if (zoneId == null || zoneId <= 0) {
            throw badRequest(label + "缺少网络分区");
        }
        NetworkZone zone = requireActiveZone(tenantId, zoneId);
        return store.listSubnets(tenantId, zoneId, RecordStatus.ACTIVE).stream()
                .findFirst()
                .map(NetworkZoneSubnet::cidrBlock)
                .orElseThrow(() -> badRequest("网络分区「" + zone.name() + "」未配置启用网段，不能自动下发实例 IP"));
    }

    @Transactional(readOnly = true)
    public List<ExternalNetworkAddress> listAddresses(AuthUser actor, RecordStatus status, String keyword) {
        requireActor(actor);
        return store.listAddresses(actor.tenantId(), status, keyword);
    }

    @Transactional
    public ExternalNetworkAddress createAddress(AuthUser actor, ExternalAddressCommand command) {
        requireActor(actor);
        PreparedAddress prepared = prepareAddress(actor, command, null);
        long id = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        ExternalNetworkAddress address = new ExternalNetworkAddress(id, actor.tenantId(), prepared.addressType(),
                prepared.addressValue(), prepared.displayName(), prepared.purpose(), RecordStatus.ACTIVE,
                prepared.remark(), 0L, actor.id(), actor.id(), now, now);
        store.insertAddress(address);
        return store.findAddress(actor.tenantId(), id).orElse(address);
    }

    @Transactional
    public ExternalNetworkAddress updateAddress(AuthUser actor, long id, ExternalAddressCommand command) {
        requireActor(actor);
        store.lockAddress(actor.tenantId(), id).orElseThrow(() -> notFound("外部网络地址不存在"));
        if (command == null || command.rowVersion() == null || command.rowVersion() < 0) {
            throw badRequest("rowVersion 必须为非负整数");
        }
        PreparedAddress prepared = prepareAddress(actor, command, id);
        if (!store.updateAddress(actor.tenantId(), id, command.rowVersion(), prepared.addressType(),
                prepared.addressValue(), prepared.displayName(), prepared.purpose(), prepared.remark(), actor.id())) {
            throw conflict("外部网络地址已被其他操作修改，请刷新重试");
        }
        return store.findAddress(actor.tenantId(), id).orElseThrow(() -> notFound("外部网络地址不存在"));
    }

    @Transactional
    public ExternalNetworkAddress deactivateAddress(AuthUser actor, long id) {
        requireActor(actor);
        ExternalNetworkAddress current = store.lockAddress(actor.tenantId(), id)
                .orElseThrow(() -> notFound("外部网络地址不存在"));
        if (!store.updateAddressStatus(actor.tenantId(), id, RecordStatus.ACTIVE, RecordStatus.INACTIVE, actor.id())) {
            throw conflict("外部网络地址当前状态不允许停用");
        }
        return store.findAddress(actor.tenantId(), id).orElse(current);
    }

    @Transactional
    public ExternalNetworkAddress reactivateAddress(AuthUser actor, long id) {
        requireActor(actor);
        ExternalNetworkAddress current = store.lockAddress(actor.tenantId(), id)
                .orElseThrow(() -> notFound("外部网络地址不存在"));
        if (!store.updateAddressStatus(actor.tenantId(), id, RecordStatus.INACTIVE, RecordStatus.ACTIVE, actor.id())) {
            throw conflict("外部网络地址当前状态不允许启用");
        }
        return store.findAddress(actor.tenantId(), id).orElse(current);
    }

    @Transactional(readOnly = true)
    public List<ManagedEndpointInstance> listEndpointInstances(AuthUser actor, Long physicalSubsystemId,
                                                               Long environmentId, Long deploymentUnitId) {
        requireActor(actor);
        return store.listEndpointInstances(actor.tenantId(), physicalSubsystemId, environmentId, deploymentUnitId, List.of());
    }

    @Transactional(readOnly = true)
    public List<NetworkAccessApplication> listApplications(AuthUser actor, AccessScope scope,
                                                           ApplicationStatus status, int limit, int offset) {
        requireActor(actor);
        Long applicantId = scope == AccessScope.MANAGE ? null : actor.id();
        return store.listApplications(actor.tenantId(), applicantId, status, normalizeLimit(limit), Math.max(offset, 0));
    }

    @Transactional
    public NetworkAccessApplication createApplication(AuthUser actor, NetworkAccessCommand command) {
        requireActor(actor);
        Objects.requireNonNull(command, "网络访问申请不能为空");
        PreparedEndpoint source = prepareEndpoint(actor, command.source(), "来源");
        PreparedEndpoint target = prepareEndpoint(actor, command.target(), "目标");
        AccessProtocol protocol = Objects.requireNonNull(command.protocol(), "协议不能为空");
        String ports = required(command.ports(), "端口", 128);
        String purpose = required(command.purpose(), "用途", 1000);
        String process = optional(command.processDescription(), "处理说明", 1000);
        validateValidity(command.validFrom(), command.validUntil());
        long id = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        NetworkAccessApplication application = new NetworkAccessApplication(
                id, actor.tenantId(), "NAA" + id, actor.id(),
                source.kind(), source.physicalSubsystemId(), source.environmentId(), source.deploymentUnitId(),
                source.externalAddressId(), source.snapshotJson(),
                target.kind(), target.physicalSubsystemId(), target.environmentId(), target.deploymentUnitId(),
                target.externalAddressId(), target.snapshotJson(),
                protocol, ports, purpose, process, command.validFrom(), command.validUntil(),
                ApplicationStatus.DRAFT, 0L, actor.id(), actor.id(), now, now);
        store.insertApplication(application);
        return store.findApplication(actor.tenantId(), id).orElse(application);
    }

    @Transactional
    public NetworkAccessApplication submitApplication(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        NetworkAccessApplication application = requireVisibleApplication(actor, AccessScope.OWN, id);
        if (application.status() != ApplicationStatus.DRAFT) {
            throw conflict("只有草稿网络访问申请可以提交");
        }
        if (!store.updateApplicationStatus(actor.tenantId(), id, ApplicationStatus.DRAFT, rowVersion,
                ApplicationStatus.IN_REVIEW, actor.id())) {
            throw conflict("网络访问申请已被其他操作修改，请刷新重试");
        }
        return store.findApplication(actor.tenantId(), id).orElseThrow(() -> notFound("网络访问申请不存在"));
    }

    @Transactional
    public NetworkAccessApplication approveApplication(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        NetworkAccessApplication application = store.lockApplication(actor.tenantId(), id)
                .orElseThrow(() -> notFound("网络访问申请不存在"));
        if (application.status() != ApplicationStatus.IN_REVIEW) {
            throw conflict("只有审批中的网络访问申请可以批准");
        }
        if (!store.updateApplicationStatus(actor.tenantId(), id, ApplicationStatus.IN_REVIEW, rowVersion,
                ApplicationStatus.APPROVED, actor.id())) {
            throw conflict("网络访问申请已被其他操作修改，请刷新重试");
        }
        long relationId = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        NetworkAccessRelation relation = new NetworkAccessRelation(relationId, actor.tenantId(), "NAR" + relationId,
                application.id(), application.sourceKind(), application.sourceSnapshotJson(),
                application.targetKind(), application.targetSnapshotJson(), application.protocol(),
                application.ports(), application.purpose(), application.processDescription(),
                application.validFrom(), application.validUntil(), RelationStatus.ACTIVE, null, null, null,
                0L, actor.id(), actor.id(), now, now);
        store.insertRelation(relation);
        return store.findApplication(actor.tenantId(), id).orElse(application);
    }

    @Transactional
    public NetworkAccessApplication rejectApplication(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        NetworkAccessApplication application = store.lockApplication(actor.tenantId(), id)
                .orElseThrow(() -> notFound("网络访问申请不存在"));
        if (application.status() != ApplicationStatus.IN_REVIEW) {
            throw conflict("只有审批中的网络访问申请可以拒绝");
        }
        if (!store.updateApplicationStatus(actor.tenantId(), id, ApplicationStatus.IN_REVIEW, rowVersion,
                ApplicationStatus.REJECTED, actor.id())) {
            throw conflict("网络访问申请已被其他操作修改，请刷新重试");
        }
        return store.findApplication(actor.tenantId(), id).orElse(application);
    }

    @Transactional
    public NetworkAccessApplication cancelApplication(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        NetworkAccessApplication application = requireVisibleApplication(actor, AccessScope.OWN, id);
        if (application.status() != ApplicationStatus.DRAFT && application.status() != ApplicationStatus.IN_REVIEW) {
            throw conflict("当前状态不允许取消网络访问申请");
        }
        if (!store.updateApplicationStatus(actor.tenantId(), id, application.status(), rowVersion,
                ApplicationStatus.CANCELLED, actor.id())) {
            throw conflict("网络访问申请已被其他操作修改，请刷新重试");
        }
        return store.findApplication(actor.tenantId(), id).orElse(application);
    }

    @Transactional(readOnly = true)
    public List<NetworkAccessRelation> listRelations(AuthUser actor, RelationStatus status, int limit, int offset) {
        requireActor(actor);
        return store.listRelations(actor.tenantId(), status, normalizeLimit(limit), Math.max(offset, 0));
    }

    @Transactional
    public NetworkAccessRelation closeRelation(AuthUser actor, long id, CloseRelationCommand command) {
        requireActor(actor);
        NetworkAccessRelation current = store.lockRelation(actor.tenantId(), id)
                .orElseThrow(() -> notFound("网络访问关系不存在"));
        long rowVersion = command == null || command.rowVersion() == null ? -1 : command.rowVersion();
        if (rowVersion < 0) {
            throw badRequest("rowVersion 必须为非负整数");
        }
        String reason = required(command.closeReason(), "关闭原因", 1000);
        if (!store.closeRelation(actor.tenantId(), id, rowVersion, reason, actor.id(), LocalDateTime.now(clock))) {
            throw conflict("网络访问关系已被其他操作修改，请刷新重试");
        }
        return store.lockRelation(actor.tenantId(), id).orElse(current);
    }

    private PreparedZone prepareZone(AuthUser actor, NetworkZoneCommand command, Long excludeId) {
        Objects.requireNonNull(command, "网络分区命令不能为空");
        Long parentId = command.parentId();
        if (excludeId != null && parentId != null && parentId.equals(excludeId)) {
            throw badRequest("网络分区父级不能选择自身");
        }
        NetworkZone parent = null;
        if (parentId != null) {
            parent = requireActiveZone(actor.tenantId(), parentId);
            if (store.hasActiveSubnets(actor.tenantId(), parentId)) {
                throw conflict("存在启用网段的网络分区不能新增子分区");
            }
        }
        String code = normalizeCode(command.code(), "网络分区编码");
        String name = required(command.name(), "网络分区名称", 160);
        int level = command.restrictionLevel() == null ? 0 : command.restrictionLevel();
        if (level < 0) {
            throw badRequest("限制级别不能为负数");
        }
        if (parent != null && level < parent.restrictionLevel()) {
            throw badRequest("子分区限制级别不能低于父分区");
        }
        if (store.zoneCodeExists(actor.tenantId(), code, excludeId)) {
            throw conflict("网络分区编码已存在");
        }
        if (store.zoneNameExists(actor.tenantId(), parentId, name, excludeId)) {
            throw conflict("同一父分区下网络分区名称已存在");
        }
        return new PreparedZone(parentId, parent == null ? null : parent.name(), code, name, level,
                optional(command.description(), "说明", 1000), optional(command.remark(), "备注", 1000));
    }

    private PreparedSubnet prepareSubnet(AuthUser actor, NetworkZoneSubnetCommand command, Long excludeId) {
        Objects.requireNonNull(command, "网络分区网段命令不能为空");
        String cidrBlock;
        try {
            cidrBlock = NetworkCidr.normalizeCidr(required(command.cidrBlock(), "CIDR 网段", 64));
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
        if (store.subnetCidrExists(actor.tenantId(), cidrBlock, excludeId)) {
            throw conflict("网络分区网段 CIDR 已存在");
        }
        String gatewayIp = optional(command.gatewayIp(), "网关 IP", 64);
        if (gatewayIp != null) {
            try {
                NetworkCidr.parseIpv4(gatewayIp);
                if (!NetworkCidr.contains(cidrBlock, gatewayIp)) {
                    throw badRequest("网关 IP 必须位于 CIDR 网段内");
                }
            } catch (IllegalArgumentException exception) {
                throw badRequest("网关 IP 格式无效：" + gatewayIp);
            }
        }
        return new PreparedSubnet(cidrBlock, gatewayIp, optional(command.purpose(), "用途", 500),
                optional(command.remark(), "备注", 1000));
    }

    private PreparedAddress prepareAddress(AuthUser actor, ExternalAddressCommand command, Long excludeId) {
        Objects.requireNonNull(command, "外部网络地址命令不能为空");
        AddressType type = Objects.requireNonNull(command.addressType(), "地址类型不能为空");
        String value = required(command.addressValue(), "地址值", 255);
        if (type == AddressType.IP && !SIMPLE_IP_PATTERN.matcher(value).matches()) {
            throw badRequest("IP 地址格式无效");
        }
        if (type == AddressType.CIDR && !value.contains("/")) {
            throw badRequest("CIDR 地址必须包含掩码");
        }
        if (type == AddressType.DOMAIN && !value.contains(".")) {
            throw badRequest("域名地址格式无效");
        }
        if (store.addressExists(actor.tenantId(), type, value, excludeId)) {
            throw conflict("外部网络地址已存在");
        }
        String displayName = required(command.displayName(), "显示名称", 160);
        return new PreparedAddress(type, value, displayName,
                optional(command.purpose(), "用途", 500), optional(command.remark(), "备注", 1000));
    }

    private PreparedEndpoint prepareEndpoint(AuthUser actor, EndpointCommand command, String label) {
        Objects.requireNonNull(command, label + "端点不能为空");
        EndpointKind kind = Objects.requireNonNull(command.kind(), label + "端点类型不能为空");
        if (kind == EndpointKind.EXTERNAL) {
            long externalId = requiredId(command.externalAddressId(), label + "外部地址");
            ExternalNetworkAddress address = store.findAddress(actor.tenantId(), externalId)
                    .orElseThrow(() -> badRequest(label + "外部地址不存在"));
            if (address.status() != RecordStatus.ACTIVE) {
                throw badRequest(label + "外部地址已停用");
            }
            return new PreparedEndpoint(kind, null, null, null, externalId,
                    serialize(List.of(Map.of(
                            "kind", "EXTERNAL",
                            "addressId", address.id(),
                            "addressType", address.addressType().name(),
                            "addressValue", address.addressValue(),
                            "displayName", address.displayName()))));
        }
        long physicalId = requiredId(command.physicalSubsystemId(), label + "物理子系统");
        long environmentId = requiredId(command.environmentId(), label + "具体环境");
        long deploymentUnitId = requiredId(command.deploymentUnitId(), label + "部署单元");
        List<ManagedEndpointInstance> selected = store.listEndpointInstances(actor.tenantId(), physicalId,
                environmentId, deploymentUnitId, command.instanceIds());
        if (selected.isEmpty()) {
            throw badRequest(label + "未选择任何在用环境部署实例");
        }
        if (!command.instanceIds().isEmpty() && selected.size() != new LinkedHashSet<>(command.instanceIds()).size()) {
            throw badRequest(label + "存在不属于当前级联条件的实例或已下线实例");
        }
        Set<Long> unitIds = new LinkedHashSet<>();
        for (ManagedEndpointInstance instance : selected) {
            unitIds.add(instance.deploymentUnitId());
            if (instance.networkZoneId() == null) {
                throw badRequest(label + "实例缺少结构化网络分区：" + instance.machineName());
            }
        }
        if (unitIds.size() != 1 || !unitIds.contains(deploymentUnitId)) {
            throw badRequest(label + "实例必须限定在一个部署单元内");
        }
        return new PreparedEndpoint(kind, physicalId, environmentId, deploymentUnitId, null,
                serialize(selected));
    }

    private NetworkAccessApplication requireVisibleApplication(AuthUser actor, AccessScope scope, long id) {
        NetworkAccessApplication application = store.findApplication(actor.tenantId(), id)
                .orElseThrow(() -> notFound("网络访问申请不存在"));
        if (scope == AccessScope.OWN && application.applicantId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作本人发起的网络访问申请");
        }
        return application;
    }

    private NetworkZone requireActiveZone(long tenantId, long zoneId) {
        NetworkZone zone = store.findZone(tenantId, zoneId).orElseThrow(() -> badRequest("网络分区不存在"));
        if (zone.status() != RecordStatus.ACTIVE) {
            throw badRequest("网络分区已停用：" + zone.name());
        }
        return zone;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("网络访问快照序列化失败", exception);
        }
    }

    private static void validateValidity(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw badRequest("有效期结束时间必须晚于开始时间");
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 200);
    }

    private static long requiredId(Long value, String label) {
        if (value == null || value <= 0) {
            throw badRequest(label + "不能为空");
        }
        return value;
    }

    private static String normalizeCode(String value, String label) {
        String normalized = required(value, label, 64).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw badRequest(label + "只能包含大写字母、数字、下划线或中划线，长度 2-64");
        }
        return normalized;
    }

    private static String required(String value, String label, int max) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > max) {
            throw badRequest(label + "不能为空且最长 " + max + " 个字符");
        }
        return normalized;
    }

    private static String optional(String value, String label, int max) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > max) {
            throw badRequest(label + "最长 " + max + " 个字符");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("网络访问标识生成器返回无效值");
        }
        return value;
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private static ArchitectureNotFoundException notFound(String message) {
        return new ArchitectureNotFoundException(message);
    }

    private record PreparedZone(Long parentId, String parentName, String code, String name,
                                int restrictionLevel, String description, String remark) {
    }

    private record PreparedSubnet(String cidrBlock, String gatewayIp, String purpose, String remark) {
    }

    private record PreparedAddress(AddressType addressType, String addressValue, String displayName,
                                   String purpose, String remark) {
    }

    private record PreparedEndpoint(EndpointKind kind, Long physicalSubsystemId, Long environmentId,
                                    Long deploymentUnitId, Long externalAddressId, String snapshotJson) {
    }
}

package com.ccb.release.production.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.production.model.ProductionModels.BatchEntryRequest;
import com.ccb.release.production.model.ProductionModels.BatchUpdateResultRequest;
import com.ccb.release.production.model.ProductionModels.Entry;
import com.ccb.release.production.model.ProductionModels.Result;
import com.ccb.release.production.model.ProductionModels.UpdateResultRequest;
import com.ccb.release.production.persistence.ReleaseProductionStore;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReleaseProductionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ReleaseProductionStore store;
    private final ReleaseApplicationStore applications;
    private final ReleaseWindowStore windows;
    private final Clock clock;

    @Autowired
    public ReleaseProductionService(ReleaseProductionStore store, ReleaseApplicationStore applications,
                                    ReleaseWindowStore windows) {
        this(store, applications, windows, Clock.system(BUSINESS_ZONE));
    }

    ReleaseProductionService(ReleaseProductionStore store, ReleaseApplicationStore applications,
                             ReleaseWindowStore windows, Clock clock) {
        this.store = store;
        this.applications = applications;
        this.windows = windows;
        this.clock = clock;
    }

    public List<Entry> baseline(long windowId, AuthUser user) {
        return store.findBaseline(user.tenantId(), windowId);
    }

    @Transactional
    public void refreshReleasedCandidates(long applicationId, AuthUser operator) {
        Application application = applications.findById(applicationId, operator.tenantId())
                .orElseThrow(() -> badRequest("版本申请不存在"));
        if (application.status() != Status.RELEASED || application.approvedAt() == null) {
            throw conflict("只有制品准出申请可以生成投产候选");
        }
        Long windowId = application.assignedWindowId() != null ? application.assignedWindowId() : application.windowId();
        if (windowId == null) throw conflict("制品准出申请尚未分配承接窗口");
        for (DeliverySnapshot delivery : application.deliveries()) {
            Entry source = store.findBySource(operator.tenantId(), windowId, application.id(), delivery.itemKey())
                    .orElse(null);
            Entry active = store.findActiveForUpdate(operator.tenantId(), windowId, application.subsystemCode(),
                    delivery.itemKey()).orElse(null);
            if (source != null) {
                if (active == null) {
                    store.activate(source.id(), operator.tenantId(), operator.id());
                } else if (active.id() != source.id() && laterThan(source, active)) {
                    store.deactivate(active.id(), operator.tenantId(), operator.id());
                    store.activate(source.id(), operator.tenantId(), operator.id());
                }
                continue;
            }
            if (active != null && !laterThan(application, active)) continue;
            if (active != null) store.deactivate(active.id(), operator.tenantId(), operator.id());
            store.insert(entry(application, delivery, windowId), operator.id());
        }
    }

    @Transactional
    public Entry updateResult(long entryId, UpdateResultRequest request, AuthUser user) {
        return updateResultInternal(entryId, request, user);
    }

    @Transactional
    public List<Entry> updateResults(BatchUpdateResultRequest request, AuthUser user) {
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw badRequest("请选择需要维护的投产基线明细");
        }
        if (request.entries().size() > 200) throw badRequest("单次最多批量维护 200 条投产基线明细");
        HashSet<Long> entryIds = new HashSet<>();
        for (BatchEntryRequest item : request.entries()) {
            if (item == null || item.id() <= 0 || !entryIds.add(item.id())) throw badRequest("批量明细存在无效或重复数据");
        }
        return request.entries().stream().map(item -> updateResultInternal(item.id(),
                new UpdateResultRequest(request.productionResult(), request.productionAt(), request.resultReason(),
                        request.changeReason(), item.rowVersion()), user)).toList();
    }

    private Entry updateResultInternal(long entryId, UpdateResultRequest request, AuthUser user) {
        if (request == null) throw badRequest("投产结果信息不能为空");
        Entry before = store.findByIdForUpdate(entryId, user.tenantId()).orElseThrow(() -> badRequest("投产基线明细不存在"));
        ensureWindowEnded(before, user.tenantId());
        if (before.productionResult() != Result.RELEASED) throw conflict("投产结果已维护，不能重复维护");
        if (before.rowVersion() != request.rowVersion()) throw conflict("投产结果已被其他人修改，请刷新后重试");
        Result result;
        try { result = Result.valueOf(required(request.productionResult(), "投产结果", 24).toUpperCase()); }
        catch (IllegalArgumentException exception) { throw badRequest("投产结果无效"); }
        if (result == Result.RELEASED) throw badRequest("请选择投产成功、投产失败或未投产");
        String changeReason = required(request.changeReason(), "修改原因", 1000);
        String resultReason = optional(request.resultReason(), 1000);
        LocalDateTime productionAt = request.productionAt();
        if (result == Result.SUCCEEDED && productionAt == null) throw badRequest("投产成功必须填写投产时间");
        if ((result == Result.FAILED || result == Result.NOT_DEPLOYED) && resultReason == null) {
            throw badRequest("投产失败或未投产必须填写原因");
        }
        if (result != Result.FAILED && result != Result.NOT_DEPLOYED) resultReason = null;
        if (result != Result.SUCCEEDED) productionAt = null;
        if (!store.updateResult(entryId, user.tenantId(), before.rowVersion(), result, productionAt, resultReason, user.id())) {
            throw conflict("投产结果已被其他人修改，请刷新后重试");
        }
        store.appendResultLog(nextId(), user.tenantId(), before, result, productionAt, changeReason,
                user.id(), user.displayName());
        return new Entry(before.id(), before.tenantId(), before.windowId(), before.applicationId(), before.applicationCode(),
                before.approvedAt(), before.subsystemId(), before.subsystemCode(), before.subsystemName(), before.deliveryUnitId(),
                before.deliveryUnitCode(), before.deliveryUnitName(), before.artifactType(), before.artifactVersion(),
                before.itemType(), before.filePath(), before.itemKey(), before.versionType(), before.characteristic(),
                result, productionAt, resultReason, before.activeCandidate(),
                before.rowVersion() + 1, before.createdAt(), null);
    }

    private void ensureWindowEnded(Entry entry, long tenantId) {
        ReleaseWindow window = windows.findById(entry.windowId(), tenantId)
                .orElseThrow(() -> badRequest("投产窗口不存在"));
        if (LocalDateTime.now(clock).isBefore(window.productionEnd())) {
            throw conflict("投产窗口尚未结束，结束时间为 " + window.productionEnd().format(MINUTE)
                    + "，不能提前维护投产结果");
        }
    }

    public List<Entry> currentVersions(String projectId, AuthUser user) {
        return store.findCurrentVersions(user.tenantId(), projectId);
    }

    public List<Entry> history(String subsystemCode, String deliveryCode, AuthUser user) {
        return store.findHistory(user.tenantId(), required(subsystemCode, "物理子系统编码", 64),
                required(deliveryCode, "交付单元编码", 64));
    }

    public List<Entry> historyByEntry(long entryId, AuthUser user) {
        Entry anchor = store.findById(entryId, user.tenantId())
                .orElseThrow(() -> badRequest("生产版本记录不存在"));
        return store.findHistoryByItemKey(user.tenantId(), anchor.subsystemCode(), anchor.itemKey());
    }

    private boolean laterThan(Application application, Entry active) {
        int compared = application.approvedAt().compareTo(active.approvedAt());
        return compared > 0 || (compared == 0 && application.id() > active.applicationId());
    }

    private boolean laterThan(Entry candidate, Entry active) {
        int compared = candidate.approvedAt().compareTo(active.approvedAt());
        return compared > 0 || (compared == 0 && candidate.applicationId() > active.applicationId());
    }

    private Entry entry(Application application, DeliverySnapshot delivery, long windowId) {
        return new Entry(nextId(), application.tenantId(), windowId, application.id(), application.applicationCode(),
                application.approvedAt(), application.subsystemId(), application.subsystemCode(), application.subsystemName(),
                delivery.deliveryUnitId(), delivery.deliveryUnitCode(), delivery.deliveryUnitName(), delivery.artifactType().name(),
                delivery.artifactVersion(), delivery.itemType().name(), delivery.filePath(), delivery.itemKey(),
                application.versionType().name(), application.characteristic().name(), Result.RELEASED, null, null,
                true, 0, null, null);
    }

    private String required(String value, String label, int max) {
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (normalized == null) throw badRequest(label + "不能为空");
        if (normalized.length() > max) throw badRequest(label + "长度不能超过 " + max);
        return normalized;
    }
    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw badRequest("文本长度不能超过 " + max);
        return normalized;
    }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

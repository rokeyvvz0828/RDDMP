package com.ccb.release.window.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.window.model.ChangeRegularEnabledRequest;
import com.ccb.release.window.model.CreateReleaseWindowRequest;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.model.ReleaseWindowResponse;
import com.ccb.release.window.model.ReleaseWindowStatus;
import com.ccb.release.window.model.UpdateReleaseWindowRequest;
import com.ccb.release.window.model.WindowFieldChange;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReleaseWindowService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private final ReleaseWindowStore store;
    private final Clock clock;

    @Autowired
    public ReleaseWindowService(ReleaseWindowStore store) { this(store, Clock.system(BUSINESS_ZONE)); }
    ReleaseWindowService(ReleaseWindowStore store, Clock clock) { this.store = store; this.clock = clock; }

    public PageResult<ReleaseWindowResponse> list(long page, long size, String projectId, String keyword, AuthUser user) {
        PageResult<ReleaseWindow> result = store.findPage(user.tenantId(), projectId, keyword, new PageQuery(page, size));
        return new PageResult<>(result.records().stream().map(this::response).toList(), result.total(), result.page(), result.size());
    }

    public ReleaseWindowResponse detail(long id, AuthUser user) {
        return response(requireWindow(id, user.tenantId(), false));
    }

    @Transactional
    public ReleaseWindowResponse create(CreateReleaseWindowRequest request, AuthUser user) {
        if (request == null) throw badRequest("投产窗口信息不能为空");
        String name = required(request.windowName(), "窗口名称", 128);
        String projectId = required(request.projectId(), "项目标识", 64);
        String projectCode = required(request.projectCode(), "项目编码", 64);
        String projectName = required(request.projectName(), "项目名称", 128);
        validateTimes(request.declarationStart(), request.declarationEnd(), request.productionStart(), request.productionEnd());
        store.lockProjectWindows(user.tenantId(), projectId);
        ensureNoOverlap(user.tenantId(), projectId, request.declarationStart(), request.productionEnd(), null);
        String prefix = "WIN-" + request.productionStart().format(MONTH) + "-";
        String code = prefix + String.format("%03d", store.nextMonthlySequence(user.tenantId(), prefix));
        long id = nextId();
        ReleaseWindow window = new ReleaseWindow(id, user.tenantId(), code, name, projectId, projectCode, projectName,
                request.declarationStart(), request.declarationEnd(), request.productionStart(), request.productionEnd(),
                request.regularEnabled() == null || request.regularEnabled(), optional(request.description(), 1000),
                0, user.id(), user.id(), null, null);
        store.insert(window);
        return response(store.findById(id, user.tenantId()).orElse(window));
    }

    @Transactional
    public ReleaseWindowResponse update(long id, UpdateReleaseWindowRequest request, AuthUser user) {
        if (request == null) throw badRequest("投产窗口信息不能为空");
        ReleaseWindow snapshot = requireWindow(id, user.tenantId(), false);
        requireExpectedVersion(request.rowVersion(), snapshot.rowVersion());
        ensureImmutable(snapshot, request.windowCode(), request.projectId(), request.projectCode(), request.projectName());
        String reason = required(request.changeReason(), "修改原因", 500);
        String name = required(request.windowName(), "窗口名称", 128);
        if (request.regularEnabled() == null) throw badRequest("常规版本申请开关不能为空");
        validateTimes(request.declarationStart(), request.declarationEnd(), request.productionStart(), request.productionEnd());
        store.lockProjectWindows(user.tenantId(), snapshot.projectId());
        ReleaseWindow current = requireWindow(id, user.tenantId(), true);
        requireExpectedVersion(request.rowVersion(), current.rowVersion());
        ensureImmutable(current, request.windowCode(), request.projectId(), request.projectCode(), request.projectName());
        ensureNoOverlap(user.tenantId(), snapshot.projectId(), request.declarationStart(), request.productionEnd(), id);
        ReleaseWindow updated = new ReleaseWindow(current.id(), current.tenantId(), current.windowCode(), name,
                current.projectId(), current.projectCode(), current.projectName(), request.declarationStart(), request.declarationEnd(),
                request.productionStart(), request.productionEnd(), request.regularEnabled(),
                optional(request.description(), 1000), current.rowVersion() + 1, current.createdBy(), user.id(), current.createdAt(), null);
        List<WindowFieldChange> changes = changes(current, updated);
        if (changes.isEmpty()) return response(current);
        if (!store.update(updated, current.rowVersion())) throw conflict("投产窗口已被其他人修改，请刷新后重试");
        store.appendChanges(user.tenantId(), id, changes, reason, user.id(), nextId());
        return response(store.findById(id, user.tenantId()).orElse(updated));
    }

    @Transactional
    public ReleaseWindowResponse changeRegularEnabled(long id, ChangeRegularEnabledRequest request, AuthUser user) {
        if (request == null || request.regularEnabled() == null) throw badRequest("常规版本申请开关不能为空");
        ReleaseWindow current = requireWindow(id, user.tenantId(), true);
        requireExpectedVersion(request.rowVersion(), current.rowVersion());
        String reason = required(request.changeReason(), "修改原因", 500);
        if (current.regularEnabled() == request.regularEnabled()) return response(current);
        ReleaseWindow updated = new ReleaseWindow(current.id(), current.tenantId(), current.windowCode(), current.windowName(),
                current.projectId(), current.projectCode(), current.projectName(), current.declarationStart(), current.declarationEnd(),
                current.productionStart(), current.productionEnd(), request.regularEnabled(), current.description(),
                current.rowVersion() + 1, current.createdBy(), user.id(), current.createdAt(), null);
        if (!store.update(updated, current.rowVersion())) throw conflict("投产窗口已被其他人修改，请刷新后重试");
        store.appendChanges(user.tenantId(), id, List.of(new WindowFieldChange("regular_enabled",
                String.valueOf(current.regularEnabled()), String.valueOf(updated.regularEnabled()))), reason, user.id(), nextId());
        return response(store.findById(id, user.tenantId()).orElse(updated));
    }

    private ReleaseWindow requireWindow(long id, long tenantId, boolean forUpdate) {
        var window = forUpdate ? store.findByIdForUpdate(id, tenantId) : store.findById(id, tenantId);
        if (window.isPresent()) return window.get();
        var owner = store.findTenantId(id);
        if (owner.isPresent() && owner.getAsLong() != tenantId) throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该投产窗口");
        throw badRequest("投产窗口不存在");
    }

    private void ensureImmutable(ReleaseWindow current, String code, String projectId, String projectCode, String projectName) {
        if (!Objects.equals(current.windowCode(), code) || !Objects.equals(current.projectId(), projectId)
                || !Objects.equals(current.projectCode(), projectCode) || !Objects.equals(current.projectName(), projectName)) {
            throw conflict("窗口编码和所属项目创建后不允许修改");
        }
    }

    private void validateTimes(LocalDateTime declarationStart, LocalDateTime declarationEnd,
                               LocalDateTime productionStart, LocalDateTime productionEnd) {
        if (declarationStart == null || declarationEnd == null || productionStart == null || productionEnd == null) {
            throw badRequest("申报和投产时间不能为空");
        }
        for (LocalDateTime value : List.of(declarationStart, declarationEnd, productionStart, productionEnd)) {
            if (value.getSecond() != 0 || value.getNano() != 0) throw badRequest("窗口时间必须精确到分钟");
        }
        if (!declarationStart.isBefore(declarationEnd) || !declarationEnd.isBefore(productionStart)
                || !productionStart.isBefore(productionEnd)) {
            throw badRequest("时间必须满足申报开始 < 申报截止 < 投产开始 < 投产结束");
        }
    }

    private void ensureNoOverlap(long tenantId, String projectId, LocalDateTime start, LocalDateTime end, Long excludedId) {
        if (store.hasOverlap(tenantId, projectId, start, end, excludedId)) throw conflict("同一项目的投产窗口周期不允许重叠");
    }

    private void requireExpectedVersion(Long expected, long actual) {
        if (expected == null) throw badRequest("rowVersion 不能为空");
        if (expected != actual) throw conflict("投产窗口已被其他人修改，请刷新后重试");
    }

    private List<WindowFieldChange> changes(ReleaseWindow before, ReleaseWindow after) {
        List<WindowFieldChange> values = new ArrayList<>();
        changed(values, "window_name", before.windowName(), after.windowName());
        changed(values, "declaration_start", before.declarationStart(), after.declarationStart());
        changed(values, "declaration_end", before.declarationEnd(), after.declarationEnd());
        changed(values, "production_start", before.productionStart(), after.productionStart());
        changed(values, "production_end", before.productionEnd(), after.productionEnd());
        changed(values, "regular_enabled", before.regularEnabled(), after.regularEnabled());
        changed(values, "description", before.description(), after.description());
        return values;
    }

    private void changed(List<WindowFieldChange> target, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) target.add(new WindowFieldChange(field, text(before), text(after)));
    }

    private ReleaseWindowResponse response(ReleaseWindow window) {
        ReleaseWindowStatus status = status(window, LocalDateTime.now(clock));
        String reason = unavailableReason(window, status);
        return new ReleaseWindowResponse(window.id(), window.windowCode(), window.windowName(), window.projectId(),
                window.projectCode(), window.projectName(), window.declarationStart(), window.declarationEnd(), window.productionStart(),
                window.productionEnd(), window.regularEnabled(), window.description(), status.name(), status.label(), reason == null,
                reason, window.rowVersion(), window.createdAt(), window.updatedAt());
    }

    ReleaseWindowStatus status(ReleaseWindow window, LocalDateTime now) {
        if (now.isBefore(window.declarationStart())) return ReleaseWindowStatus.UPCOMING;
        if (!now.isAfter(window.declarationEnd())) return ReleaseWindowStatus.DECLARATION_OPEN;
        if (now.isBefore(window.productionStart())) return ReleaseWindowStatus.URGENT;
        if (!now.isAfter(window.productionEnd())) return ReleaseWindowStatus.IN_PRODUCTION;
        return ReleaseWindowStatus.CLOSED;
    }

    private String unavailableReason(ReleaseWindow window, ReleaseWindowStatus status) {
        if (!window.regularEnabled()) return "该投产窗口已关闭常规版本申请";
        return switch (status) {
            case UPCOMING -> "尚未到申报开始时间";
            case DECLARATION_OPEN, URGENT -> null;
            case IN_PRODUCTION -> "已进入投产期，仅允许应急版本";
            case CLOSED -> "投产窗口已关闭";
        };
    }

    private String required(String value, String label, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw badRequest(label + "不能为空");
        if (normalized.length() > max) throw badRequest(label + "长度不能超过 " + max);
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw badRequest("文本长度不能超过 " + max);
        return normalized;
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

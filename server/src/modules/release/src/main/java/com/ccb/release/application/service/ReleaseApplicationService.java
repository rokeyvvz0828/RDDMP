package com.ccb.release.application.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictAction;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictActionResult;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictReport;
import com.ccb.release.application.model.ReleaseApplicationModels.CreateRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryInput;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryItemType;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.FileMediaInput;
import com.ccb.release.application.model.ReleaseApplicationModels.FileMediaSnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.HistoricalApplication;
import com.ccb.release.application.model.ReleaseApplicationModels.RelatedHistoryView;
import com.ccb.release.application.model.ReleaseApplicationModels.Response;
import com.ccb.release.application.model.ReleaseApplicationModels.StateActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.UpdateRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionChange;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReleaseApplicationService {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReleaseApplicationStore store;
    private final ReleaseWindowStore windowStore;
    private final ReleaseScenarioPolicy scenarioPolicy;
    private final ObjectMapper objectMapper;

    public ReleaseApplicationService(ReleaseApplicationStore store, ReleaseWindowStore windowStore,
                                     ReleaseScenarioPolicy scenarioPolicy, ObjectMapper objectMapper) {
        this.store = store;
        this.windowStore = windowStore;
        this.scenarioPolicy = scenarioPolicy;
        this.objectMapper = objectMapper;
    }

    public PageResult<Response> list(long page, long size, String projectId, String keyword, String status,
                                     boolean mineOnly, AuthUser user) {
        String normalizedStatus = status == null || status.isBlank() ? null : parseStatus(status).name();
        PageResult<Application> result = store.findPage(user.tenantId(), projectId, keyword, normalizedStatus, mineOnly, user.id(),
                new PageQuery(page, size));
        return new PageResult<>(result.records().stream().map(value -> response(value, ConflictReport.empty())).toList(),
                result.total(), result.page(), result.size());
    }

    public Response detail(String code, AuthUser user) {
        Application value = requireApplication(code, user.tenantId(), false);
        return response(value, conflicts(value));
    }

    public List<RelatedHistoryView> relatedHistory(String code, AuthUser user) {
        Application current = requireApplication(code, user.tenantId(), false);
        if (current.characteristic() != Characteristic.ADDITIONAL) return List.of();

        List<RelatedHistoryView> history = new ArrayList<>();
        for (Long relatedId : new LinkedHashSet<>(store.findRelatedApplicationIds(user.tenantId(), current.id()))) {
            if (relatedId == null || relatedId == current.id()) continue;
            Application historical = store.findById(relatedId, user.tenantId()).orElse(null);
            if (historical == null) continue;
            List<VersionChange> changes = versionChanges(historical, current.deliveries());
            if (changes.isEmpty()) continue;
            history.add(new RelatedHistoryView(historical.applicationCode(), historical.status().name(),
                    historical.versionType().name(), historical.characteristic().name(), historical.requesterName(),
                    historical.requesterDepartment(), historical.createdAt(), historical.approvedAt(),
                    historical.requirementCodes(), historical.description(), changes));
        }
        history.sort(Comparator
                .comparing(RelatedHistoryView::approvedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RelatedHistoryView::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RelatedHistoryView::applicationCode, Comparator.reverseOrder()));
        return List.copyOf(history);
    }

    @Transactional
    public Response create(CreateRequest request, AuthUser user) {
        Draft draft = prepare(request, null, user);
        ConflictFacts facts = conflictFacts(draft, null, user);
        ensureNoInReviewConflicts(facts);
        ReleaseScenarioPolicy.Scenario scenario = scenario(draft, facts.additional());
        validateScenarioFields(draft, scenario.versionType());
        String prefix = "SQ-" + LocalDateTime.now(BUSINESS_ZONE).format(MONTH) + "-";
        String code = prefix + String.format("%03d", store.nextMonthlySequence(user.tenantId(), prefix));
        long id = nextId();
        Application value = application(id, code, Status.DRAFT, 0, draft, scenario, user, user, null, null);
        store.insert(value);
        persistAdditionalRelations(value, facts, user.id());
        store.appendEvent(nextId(), user.tenantId(), id, "CREATED", null, Status.DRAFT, null,
                json(Map.of("applicationCode", code)), user.id(), user.displayName());
        Application persisted = store.findByCode(code, user.tenantId()).orElse(value);
        return response(persisted, conflicts(persisted));
    }

    @Transactional
    public Response update(String code, UpdateRequest request, AuthUser user, boolean elevated) {
        if (request == null) throw badRequest("版本申请信息不能为空");
        Application current = requireApplication(code, user.tenantId(), true);
        ensureOwner(current, user, elevated);
        ensureEditable(current);
        requireVersion(request.rowVersion(), current.rowVersion());
        if (!Objects.equals(current.projectId(), normalized(request.projectId()))
                || !Objects.equals(current.projectCode(), normalized(request.projectCode()))
                || !Objects.equals(current.projectName(), normalized(request.projectName()))) {
            throw conflict("版本申请所属项目不允许修改");
        }
        Draft draft = prepare(request, current.id(), user);
        ConflictFacts facts = conflictFacts(draft, current.id(), user);
        ensureNoInReviewConflicts(facts);
        ReleaseScenarioPolicy.Scenario scenario = scenario(draft, facts.additional());
        validateScenarioFields(draft, scenario.versionType());
        Application updated = application(current.id(), current.applicationCode(), current.status(), current.rowVersion() + 1,
                draft, scenario, new AuthUser(current.requesterId(), current.tenantId(), "", "", current.requesterName(),
                        0, true, current.requesterDepartment(), null), user, current.createdAt(), current.approvedAt());
        if (!store.update(updated, current.rowVersion())) throw conflict("版本申请已被其他人修改，请刷新后重试");
        store.appendEvent(nextId(), user.tenantId(), current.id(), "UPDATED", current.status(), current.status(), "编辑申请",
                json(Map.of("before", current, "after", updated)), user.id(), user.displayName());
        persistAdditionalRelations(updated, facts, user.id());
        Application persisted = store.findByCode(code, user.tenantId()).orElse(updated);
        return response(persisted, conflicts(persisted));
    }

    public ConflictReport conflicts(String code, AuthUser user) {
        return conflicts(requireApplication(code, user.tenantId(), false));
    }

    public ConflictReport preview(CreateRequest request, AuthUser user) {
        Draft draft = prepare(request, null, user);
        ConflictFacts facts = conflictFacts(draft, null, user);
        ReleaseScenarioPolicy.Scenario scenario = scenario(draft, facts.additional());
        validateScenarioFields(draft, scenario.versionType());
        return conflictReport(previewSubject(draft, null, user.id()), draft.deliveries(), user.id(), facts);
    }

    public ConflictReport preview(String code, UpdateRequest request, AuthUser user, boolean elevated) {
        if (request == null) throw badRequest("版本申请信息不能为空");
        Application current = requireApplication(code, user.tenantId(), false);
        ensureOwner(current, user, elevated);
        ensureEditable(current);
        requireVersion(request.rowVersion(), current.rowVersion());
        if (!Objects.equals(current.projectId(), normalized(request.projectId()))
                || !Objects.equals(current.projectCode(), normalized(request.projectCode()))
                || !Objects.equals(current.projectName(), normalized(request.projectName()))) {
            throw conflict("版本申请所属项目不允许修改");
        }
        Draft draft = prepare(request, current.id(), user);
        ConflictFacts facts = conflictFacts(draft, current.id(), user);
        ReleaseScenarioPolicy.Scenario scenario = scenario(draft, facts.additional());
        validateScenarioFields(draft, scenario.versionType());
        return conflictReport(previewSubject(draft, current.id(), current.requesterId()),
                draft.deliveries(), current.requesterId(), facts);
    }

    @Transactional
    public ConflictActionResult resolveConflict(String code, ConflictActionRequest request, AuthUser user, boolean elevated) {
        if (request == null) throw badRequest("冲突处理信息不能为空");
        Application current = requireApplication(code, user.tenantId(), true);
        ensureOwner(current, user, elevated);
        ConflictReport report = conflicts(current);
        if (!Objects.equals(report.conflictToken(), request.conflictToken())) throw conflict("冲突信息已变化，请刷新后重新确认");
        ConflictAction action = parseConflictAction(request.action());
        if (action == ConflictAction.CREATE_NEW) {
            ensureNoInReviewConflicts(report);
            return new ConflictActionResult(action.name(), code, report);
        }
        HistoricalApplication targetFact = report.applications().stream()
                .filter(item -> item.application().applicationCode().equals(request.targetApplicationCode()))
                .findFirst().orElseThrow(() -> conflict("目标申请单不在当前冲突列表中"));
        if (!targetFact.allowedActions().contains(action.name())) throw conflict("目标申请单当前状态不允许该操作");
        Application target = requireApplication(request.targetApplicationCode(), user.tenantId(), true);
        ensureOwner(target, user, elevated);
        if (action == ConflictAction.EDIT_OLD) {
            return new ConflictActionResult(action.name(), target.applicationCode(), report);
        }
        if (request.targetRowVersion() == null) throw badRequest("目标申请单 rowVersion 不能为空");
        cancelInternal(target, request.targetRowVersion(), request.reason(), user);
        return new ConflictActionResult(action.name(), code, conflicts(current));
    }

    @Transactional
    public Response withdraw(String code, StateActionRequest request, AuthUser user, boolean elevated) {
        Application current = requireApplication(code, user.tenantId(), true);
        ensureOwner(current, user, elevated);
        if (current.status() != Status.IN_REVIEW) throw conflict("只有审批中的申请可以撤回");
        transition(current, Status.WITHDRAWN, request, "WITHDRAWN", user);
        return response(store.findByCode(code, user.tenantId()).orElse(withStatus(current, Status.WITHDRAWN)), ConflictReport.empty());
    }

    @Transactional
    public Response cancel(String code, StateActionRequest request, AuthUser user, boolean elevated) {
        Application current = requireApplication(code, user.tenantId(), true);
        ensureOwner(current, user, elevated);
        cancelInternal(current, request == null ? null : request.rowVersion(), request == null ? null : request.reason(), user);
        return response(store.findByCode(code, user.tenantId()).orElse(withStatus(current, Status.CANCELLED)), ConflictReport.empty());
    }

    private void cancelInternal(Application current, Long version, String reason, AuthUser user) {
        if (current.status() == Status.CANCELLED || current.status() == Status.RELEASED) {
            throw conflict("当前状态不允许取消申请");
        }
        if (current.status() == Status.IN_REVIEW) {
            throw conflict("审批中的申请需先通过流程撤回后再取消");
        }
        transition(current, Status.CANCELLED, new StateActionRequest(version == null ? -1 : version, reason), "CANCELLED", user);
    }

    private void transition(Application current, Status target, StateActionRequest request, String event, AuthUser user) {
        if (request == null) throw badRequest("操作信息不能为空");
        requireVersion(request.rowVersion(), current.rowVersion());
        String reason = required(request.reason(), "操作原因", 1000);
        if (!store.transition(current.id(), current.tenantId(), current.status(), target, current.rowVersion(), user.id())) {
            throw conflict("申请状态已变化，请刷新后重试");
        }
        store.appendEvent(nextId(), current.tenantId(), current.id(), event, current.status(), target, reason, null,
                user.id(), user.displayName());
    }

    private Draft prepare(CreateRequest request, Long excludedId, AuthUser user) {
        if (request == null) throw badRequest("版本申请信息不能为空");
        return prepare(request.emergency(), request.windowId(), request.projectId(), request.projectCode(), request.projectName(),
                request.subsystemId(), request.subsystemCode(), request.subsystemName(), request.deliveries(),
                request.fileMedia(), request.requirementCodes(), request.emergencyDescription(), request.urgentReason(),
                request.description(), user);
    }

    private Draft prepare(UpdateRequest request, Long excludedId, AuthUser user) {
        return prepare(request.emergency(), request.windowId(), request.projectId(), request.projectCode(), request.projectName(),
                request.subsystemId(), request.subsystemCode(), request.subsystemName(), request.deliveries(),
                request.fileMedia(), request.requirementCodes(), request.emergencyDescription(), request.urgentReason(),
                request.description(), user);
    }

    private Draft prepare(boolean emergency, Long windowId, String projectId, String projectCode, String projectName,
                          String subsystemId, String subsystemCode, String subsystemName, List<DeliveryInput> deliveryInputs,
                          List<FileMediaInput> fileMediaInputs, List<String> requirements, String emergencyDescription,
                          String urgentReason, String description, AuthUser user) {
        String normalizedProjectId = required(projectId, "项目标识", 64);
        String normalizedProjectCode = required(projectCode, "项目编码", 64);
        String normalizedProjectName = required(projectName, "项目名称", 128);
        String normalizedSubsystemId = required(subsystemId, "物理子系统标识", 64);
        String normalizedSubsystemCode = required(subsystemCode, "物理子系统编码", 64);
        String normalizedSubsystemName = required(subsystemName, "物理子系统名称", 128);
        List<DeliveryInput> normalizedDeliveryInputs = deliveryInputs == null ? List.of() : deliveryInputs;
        List<FileMediaInput> normalizedFileMediaInputs = fileMediaInputs == null ? List.of() : fileMediaInputs;
        if (normalizedDeliveryInputs.isEmpty() && normalizedFileMediaInputs.isEmpty()) {
            throw badRequest("至少添加一个交付单元或文件介质");
        }
        Set<String> deliveryCodes = new LinkedHashSet<>();
        List<DeliverySnapshot> deliveries = new ArrayList<>();
        for (DeliveryInput input : normalizedDeliveryInputs) {
            if (input == null) throw badRequest("交付单元信息不能为空");
            String code = required(input.deliveryUnitCode(), "交付单元编码", 64);
            if (!deliveryCodes.add(code)) throw badRequest("同一申请中交付单元不能重复：" + code);
            String version = required(input.artifactVersion(), "制品版本", 128);
            if (version.chars().anyMatch(Character::isWhitespace)) throw badRequest("制品版本不允许包含空格");
            ArtifactType artifactType;
            try { artifactType = ArtifactType.valueOf(required(input.artifactType(), "制品类型", 24).toUpperCase()); }
            catch (IllegalArgumentException exception) { throw badRequest("制品类型只支持 IMAGE 或 BINARY"); }
            if (artifactType == ArtifactType.FILE) throw badRequest("普通交付单元的制品类型只支持 IMAGE 或 BINARY");
            deliveries.add(new DeliverySnapshot(nextId(), required(input.deliveryUnitId(), "交付单元标识", 64), code,
                    required(input.deliveryUnitName(), "交付单元名称", 128), artifactType, version));
        }
        Set<String> filePaths = new LinkedHashSet<>();
        for (FileMediaInput input : normalizedFileMediaInputs) {
            if (input == null) throw badRequest("文件介质信息不能为空");
            String path = normalizeFilePath(input.filePath());
            if (!filePaths.add(path)) throw badRequest("同一申请中文件路径不能重复：" + path);
            deliveries.add(new DeliverySnapshot(nextId(), "FILE", "FILE", "文件介质", ArtifactType.FILE, null,
                    DeliveryItemType.FILE_MEDIA, path, fileItemKey(path)));
        }
        List<String> normalizedRequirements = normalizeRequirements(requirements);
        ReleaseWindow window = null;
        if (emergency) {
            if (windowId != null) throw badRequest("应急版本不选择投产窗口");
            if (!normalizedRequirements.isEmpty()) throw badRequest("应急版本不登记需求编号");
        } else {
            if (windowId == null) throw badRequest("非应急版本必须选择投产窗口");
            window = windowStore.findById(windowId, user.tenantId()).orElseThrow(() -> badRequest("投产窗口不存在"));
            if (!window.projectId().equals(normalizedProjectId)) throw badRequest("投产窗口与当前项目不一致");
            if (normalizedRequirements.isEmpty()) throw badRequest("非应急版本至少填写一个需求编号");
        }
        return new Draft(emergency, windowId, normalizedProjectId, normalizedProjectCode, normalizedProjectName,
                normalizedSubsystemId, normalizedSubsystemCode, normalizedSubsystemName, deliveries,
                normalizedRequirements, optional(emergencyDescription, 1000), optional(urgentReason, 1000),
                optional(description, 2000), window);
    }

    private ReleaseScenarioPolicy.Scenario scenario(Draft draft, boolean additional) {
        return draft.emergency() ? scenarioPolicy.emergency(false) : scenarioPolicy.nonEmergency(draft.window(), additional);
    }

    private void validateScenarioFields(Draft draft, VersionType type) {
        if (type == VersionType.EMERGENCY) {
            if (draft.emergencyDescription() == null) throw badRequest("应急版本必须填写测试缺陷及应急情况说明");
        } else {
            if (draft.emergencyDescription() != null) throw badRequest("非应急版本不填写应急情况说明");
            if (type == VersionType.URGENT && draft.urgentReason() == null) throw badRequest("紧急版本必须填写紧急原因");
        }
    }

    private ConflictFacts conflictFacts(Draft draft, Long excludedId, AuthUser user) {
        if (draft.emergency()) return new ConflictFacts(List.of(), false);
        List<Long> ids = store.findConflictIds(user.tenantId(), draft.windowId(),
                draft.deliveries().stream().map(DeliverySnapshot::itemKey).toList(), excludedId);
        List<Application> historical = ids.stream().map(id -> store.findById(id, user.tenantId()).orElse(null))
                .filter(Objects::nonNull).toList();
        boolean additional = historical.stream().filter(value -> value.status() == Status.RELEASED)
                .anyMatch(value -> hasItemChange(value, draft.deliveries()));
        return new ConflictFacts(historical, additional);
    }

    private ConflictReport conflicts(Application current) {
        if (current.emergency() || current.windowId() == null) return ConflictReport.empty();
        Draft draft = from(current);
        ConflictFacts facts = conflictFacts(draft, current.id(), actor(current));
        return conflictReport(current.applicationCode() + '|' + current.rowVersion(), current.deliveries(),
                current.requesterId(), facts);
    }

    private ConflictReport conflictReport(String subject, List<DeliverySnapshot> currentDeliveries,
                                          long requesterId, ConflictFacts facts) {
        if (facts.historical().isEmpty()) return ConflictReport.empty();
        List<HistoricalApplication> applications = facts.historical().stream().map(old -> {
            List<VersionChange> changes = versionChanges(old, currentDeliveries);
            return new HistoricalApplication(response(old, ConflictReport.empty()), changes, allowedActions(old, requesterId));
        }).toList();
        return new ConflictReport(conflictToken(subject, facts.historical()), applications);
    }

    private List<String> allowedActions(Application old, long requesterId) {
        List<String> actions = new ArrayList<>();
        if (old.requesterId() == requesterId && Set.of(Status.DRAFT, Status.RETURNED, Status.WITHDRAWN).contains(old.status())) {
            actions.add(ConflictAction.CANCEL_OLD.name());
            actions.add(ConflictAction.EDIT_OLD.name());
        }
        if (old.status() != Status.IN_REVIEW) actions.add(ConflictAction.CREATE_NEW.name());
        return actions;
    }

    private String conflictToken(String subject, List<Application> historical) {
        StringBuilder raw = new StringBuilder(subject);
        historical.stream().sorted(Comparator.comparingLong(Application::id)).forEach(value -> {
            raw.append('|').append(value.applicationCode()).append(':').append(value.status()).append(':').append(value.rowVersion());
            value.deliveries().stream().sorted(Comparator.comparing(DeliverySnapshot::itemKey))
                    .forEach(delivery -> raw.append(':').append(delivery.itemKey()).append('=')
                            .append(delivery.itemType() == DeliveryItemType.FILE_MEDIA
                                    ? delivery.filePath() : delivery.artifactVersion()));
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String previewSubject(Draft draft, Long excludedId, long requesterId) {
        StringBuilder subject = new StringBuilder("PREVIEW|").append(requesterId).append('|')
                .append(draft.projectId()).append('|').append(draft.windowId()).append('|').append(excludedId);
        draft.deliveries().stream().sorted(Comparator.comparing(DeliverySnapshot::itemKey)).forEach(item -> subject
                .append('|').append(item.itemKey()).append('=')
                .append(item.itemType() == DeliveryItemType.FILE_MEDIA ? item.filePath() : item.artifactVersion()));
        return subject.toString();
    }

    private void ensureNoInReviewConflicts(ConflictFacts facts) {
        if (facts.historical().stream().anyMatch(value -> value.status() == Status.IN_REVIEW)) {
            throw conflict("前一申请仍在审批中，请先撤销后继续申请");
        }
    }

    private void ensureNoInReviewConflicts(ConflictReport report) {
        if (report.hasInReview()) throw conflict("前一申请仍在审批中，请先撤销后继续申请");
    }

    private void persistAdditionalRelations(Application current, ConflictFacts facts, long operatorId) {
        if (current.characteristic() != Characteristic.ADDITIONAL) return;
        long id = nextId();
        for (Application old : facts.historical()) {
            if (old.status() != Status.RELEASED) continue;
            for (ItemChange change : itemChanges(old, current.deliveries())) {
                DeliverySnapshot item = change.current();
                store.insertRelation(id++, current.tenantId(), current.id(), old.id(), item.deliveryUnitCode(),
                        item.itemType(), item.itemKey(), item.filePath(), "ADDITIONAL",
                        change.previous().artifactVersion(), item.artifactVersion(), "制品准出后交付内容变更", operatorId);
            }
        }
    }

    private boolean hasItemChange(Application historical, List<DeliverySnapshot> current) {
        return !itemChanges(historical, current).isEmpty();
    }

    private List<VersionChange> versionChanges(Application historical, List<DeliverySnapshot> current) {
        List<VersionChange> changes = new ArrayList<>();
        for (ItemChange change : itemChanges(historical, current)) {
            DeliverySnapshot item = change.current();
            if (item.itemType() == DeliveryItemType.FILE_MEDIA) {
                changes.add(new VersionChange("FILE", "文件介质", change.previous().filePath(), item.filePath()));
            } else {
                changes.add(new VersionChange(item.deliveryUnitCode(), item.deliveryUnitName(),
                        change.previous().artifactVersion(), item.artifactVersion()));
            }
        }
        return changes;
    }

    private List<ItemChange> itemChanges(Application historical, List<DeliverySnapshot> current) {
        Map<String, DeliverySnapshot> previousByKey = new HashMap<>();
        historical.deliveries().forEach(value -> previousByKey.put(value.itemKey(), value));
        List<ItemChange> changes = new ArrayList<>();
        for (DeliverySnapshot item : current) {
            DeliverySnapshot previous = previousByKey.get(item.itemKey());
            if (previous == null) continue;
            if (item.itemType() == DeliveryItemType.FILE_MEDIA
                    || !Objects.equals(previous.artifactVersion(), item.artifactVersion())) {
                changes.add(new ItemChange(previous, item));
            }
        }
        return changes;
    }

    private Application application(long id, String code, Status status, long rowVersion, Draft draft,
                                    ReleaseScenarioPolicy.Scenario scenario, AuthUser requester, AuthUser operator,
                                    LocalDateTime createdAt, LocalDateTime approvedAt) {
        return new Application(id, operator.tenantId(), code, draft.projectId(), draft.projectCode(), draft.projectName(),
                draft.emergency(), draft.windowId(), null, draft.subsystemId(), draft.subsystemCode(), draft.subsystemName(),
                scenario.versionType(), scenario.characteristic(), null, status, requester.id(),
                requester.displayName(), requester.orgName(), draft.emergencyDescription(), draft.urgentReason(), draft.description(),
                approvedAt, rowVersion, requester.id(), operator.id(), createdAt, null, draft.deliveries(), draft.requirements());
    }

    private Response response(Application value, ConflictReport report) {
        ReleaseWindow window = value.windowId() == null ? null : windowStore.findById(value.windowId(), value.tenantId()).orElse(null);
        String unavailable = windowUnavailableReason(window);
        List<DeliverySnapshot> ordinaryDeliveries = value.deliveries().stream()
                .filter(item -> item.itemType() == DeliveryItemType.DELIVERY_UNIT).toList();
        List<FileMediaSnapshot> fileMedia = value.deliveries().stream()
                .filter(item -> item.itemType() == DeliveryItemType.FILE_MEDIA)
                .map(item -> new FileMediaSnapshot(item.id(), item.filePath())).toList();
        return new Response(value.applicationCode(), value.projectId(), value.projectCode(), value.projectName(), value.emergency(),
                value.windowId(), window == null ? null : window.windowCode(), window == null ? null : window.windowName(),
                value.subsystemId(), value.subsystemCode(), value.subsystemName(), value.versionType().name(),
                value.characteristic().name(), value.workflowCode(), value.status().name(), value.requesterId(), value.requesterName(),
                value.requesterDepartment(), value.emergencyDescription(), value.urgentReason(), value.description(),
                ordinaryDeliveries, fileMedia, value.requirementCodes(), value.emergency() || unavailable == null, unavailable,
                value.rowVersion(), value.approvedAt(), value.createdAt(), value.updatedAt(), report);
    }

    private String windowUnavailableReason(ReleaseWindow window) {
        if (window == null) return null;
        if (!window.regularEnabled()) return "该投产窗口已关闭常规版本申请";
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (now.isBefore(window.declarationStart())) return "尚未到申报开始时间";
        if (!now.isBefore(window.productionStart())) return now.isAfter(window.productionEnd()) ? "投产窗口已关闭" : "已进入投产期";
        return null;
    }

    private Application requireApplication(String code, long tenantId, boolean forUpdate) {
        String normalized = required(code, "申请单号", 64);
        var result = forUpdate ? store.findByCodeForUpdate(normalized, tenantId) : store.findByCode(normalized, tenantId);
        if (result.isPresent()) return result.get();
        var owner = store.findTenantId(normalized);
        if (owner.isPresent() && owner.getAsLong() != tenantId) throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该版本申请");
        throw badRequest("版本申请不存在");
    }

    private void ensureOwner(Application current, AuthUser user, boolean elevated) {
        if (!elevated && current.requesterId() != user.id()) throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护本人申请");
    }

    private void ensureEditable(Application current) {
        if (!Set.of(Status.DRAFT, Status.RETURNED, Status.WITHDRAWN).contains(current.status())) {
            throw conflict("当前状态不允许编辑申请");
        }
    }

    private void requireVersion(long supplied, long actual) {
        if (supplied != actual) throw conflict("版本申请已被其他人修改，请刷新后重试");
    }

    private List<String> normalizeRequirements(List<String> values) {
        if (values == null) return List.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) normalized.add(required(value, "需求编号", 128));
        return List.copyOf(normalized);
    }

    private Draft from(Application value) {
        ReleaseWindow window = value.windowId() == null ? null : windowStore.findById(value.windowId(), value.tenantId()).orElse(null);
        return new Draft(value.emergency(), value.windowId(), value.projectId(), value.projectCode(), value.projectName(),
                value.subsystemId(), value.subsystemCode(), value.subsystemName(), value.deliveries(), value.requirementCodes(),
                value.emergencyDescription(), value.urgentReason(), value.description(), window);
    }

    private AuthUser actor(Application value) {
        return new AuthUser(value.requesterId(), value.tenantId(), "", "", value.requesterName(), 0, true,
                value.requesterDepartment(), null);
    }

    private Application withStatus(Application value, Status status) {
        return new Application(value.id(), value.tenantId(), value.applicationCode(), value.projectId(), value.projectCode(),
                value.projectName(), value.emergency(), value.windowId(), value.assignedWindowId(), value.subsystemId(),
                value.subsystemCode(), value.subsystemName(), value.versionType(), value.characteristic(), value.workflowCode(), status,
                value.requesterId(), value.requesterName(), value.requesterDepartment(), value.emergencyDescription(), value.urgentReason(),
                value.description(), value.approvedAt(), value.rowVersion() + 1, value.createdBy(), value.updatedBy(), value.createdAt(),
                value.updatedAt(), value.deliveries(), value.requirementCodes());
    }

    private Status parseStatus(String value) {
        try { return Status.valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { throw badRequest("申请状态无效"); }
    }

    private ConflictAction parseConflictAction(String value) {
        try { return ConflictAction.valueOf(required(value, "冲突操作", 32).toUpperCase()); }
        catch (IllegalArgumentException exception) { throw badRequest("冲突操作无效"); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "申请审计序列化失败"); }
    }

    private String required(String value, String label, int max) {
        String normalized = normalized(value);
        if (normalized == null) throw badRequest(label + "不能为空");
        if (normalized.length() > max) throw badRequest(label + "长度不能超过 " + max);
        return normalized;
    }

    private String normalized(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    static String normalizeFilePath(String value) {
        if (value == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径不能为空");
        if (value.chars().anyMatch(character -> character <= 0x1f || character == 0x7f)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径不允许包含控制字符");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径不能为空");
        if (normalized.length() > 1024) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径长度不能超过 1024");
        return normalized;
    }

    static String fileItemKey(String normalizedPath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedPath.getBytes(StandardCharsets.UTF_8));
            return "FILE:" + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
    private String optional(String value, int max) {
        String normalized = normalized(value);
        if (normalized != null && normalized.length() > max) throw badRequest("文本长度不能超过 " + max);
        return normalized;
    }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }

    private record Draft(boolean emergency, Long windowId, String projectId, String projectCode, String projectName,
                         String subsystemId, String subsystemCode, String subsystemName, List<DeliverySnapshot> deliveries,
                         List<String> requirements, String emergencyDescription, String urgentReason, String description,
                         ReleaseWindow window) {
    }
    private record ConflictFacts(List<Application> historical, boolean additional) {
    }
    private record ItemChange(DeliverySnapshot previous, DeliverySnapshot current) {
    }
}

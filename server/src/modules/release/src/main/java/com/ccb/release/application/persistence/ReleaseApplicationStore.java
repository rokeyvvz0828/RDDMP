package com.ccb.release.application.persistence;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryItemType;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class ReleaseApplicationStore {
    private final ReleaseApplicationMapper mapper;

    public ReleaseApplicationStore(ReleaseApplicationMapper mapper) { this.mapper = mapper; }

    public PageResult<Application> findPage(long tenantId, String projectId, Long windowId, String keyword, String status,
                                            boolean mineOnly, long requesterId, PageQuery page) {
        Map<String, Object> params = params("tenantId", tenantId, "projectId", blankToNull(projectId), "windowId", windowId,
                "keyword", blankToNull(keyword), "status", blankToNull(status), "mineOnly", mineOnly,
                "requesterId", requesterId, "size", page.size(), "offset", (page.page() - 1) * page.size());
        Long total = mapper.count(params);
        return new PageResult<>(mapper.page(params).stream().map(this::withChildren).toList(), total == null ? 0 : total, page.page(), page.size());
    }

    public Optional<Application> findByCode(String code, long tenantId) { return Optional.ofNullable(mapper.byCode(params("code", code, "tenantId", tenantId))).map(this::withChildren); }
    public Optional<Application> findByCodeForUpdate(String code, long tenantId) { return Optional.ofNullable(mapper.byCodeForUpdate(params("code", code, "tenantId", tenantId))).map(this::withChildren); }
    public Optional<Application> findById(long id, long tenantId) { return Optional.ofNullable(mapper.byId(params("id", id, "tenantId", tenantId))).map(this::withChildren); }
    public OptionalLong findTenantId(String code) { Long tenantId = mapper.tenantId(params("code", code)); return tenantId == null ? OptionalLong.empty() : OptionalLong.of(tenantId); }

    public int nextMonthlySequence(long tenantId, String prefix) {
        String code = mapper.latestCode(params("tenantId", tenantId, "prefix", prefix));
        if (code == null) return 1;
        try { return Integer.parseInt(code.substring(code.lastIndexOf('-') + 1)) + 1; }
        catch (RuntimeException ignored) { return 1; }
    }

    public void insert(Application value) { mapper.insert(applicationParams(value)); insertChildren(value); }

    public boolean update(Application value, long expectedVersion) {
        Map<String, Object> params = applicationParams(value); params.put("expectedVersion", expectedVersion);
        if (mapper.update(params) != 1) return false;
        Map<String, Object> child = params("tenantId", value.tenantId(), "applicationId", value.id());
        mapper.deactivateDeliveries(child); mapper.deactivateRequirements(child); insertChildren(value);
        return true;
    }

    public boolean transition(long id, long tenantId, Status from, Status to, long expectedVersion, long operatorId) {
        return mapper.transition(params("id", id, "tenantId", tenantId, "fromStatus", from.name(), "toStatus", to.name(), "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public void appendEvent(long id, long tenantId, long applicationId, String type, Status from, Status to,
                            String reason, String payloadJson, long operatorId, String operatorName) {
        mapper.event(params("id", id, "tenantId", tenantId, "applicationId", applicationId, "type", type,
                "fromStatus", from == null ? null : from.name(), "toStatus", to == null ? null : to.name(), "reason", reason,
                "payloadJson", payloadJson, "operatorId", operatorId, "operatorName", operatorName));
    }

    public List<Long> findConflictIds(long tenantId, long windowId, List<String> itemKeys, Long excludedId) {
        return itemKeys.isEmpty() ? List.of() : mapper.conflicts(params("tenantId", tenantId, "windowId", windowId, "itemKeys", itemKeys, "excludedId", excludedId));
    }

    public List<Long> findRelatedApplicationIds(long tenantId, long applicationId) { return mapper.relatedIds(params("tenantId", tenantId, "applicationId", applicationId)); }

    public void insertRelation(long id, long tenantId, long applicationId, long relatedId, String deliveryCode,
                               String type, String previousVersion, String currentVersion, String reason, long operatorId) {
        insertRelation(id, tenantId, applicationId, relatedId, deliveryCode, DeliveryItemType.DELIVERY_UNIT, "UNIT:" + deliveryCode, null, type, previousVersion, currentVersion, reason, operatorId);
    }

    public void insertRelation(long id, long tenantId, long applicationId, long relatedId, String deliveryCode,
                               DeliveryItemType itemType, String itemKey, String filePath, String type,
                               String previousVersion, String currentVersion, String reason, long operatorId) {
        mapper.relation(params("id", id, "tenantId", tenantId, "applicationId", applicationId, "relatedId", relatedId,
                "deliveryCode", deliveryCode, "itemType", itemType.name(), "itemKey", itemKey, "filePath", filePath,
                "type", type, "previousVersion", previousVersion, "currentVersion", currentVersion, "reason", reason, "operatorId", operatorId));
    }

    private void insertChildren(Application value) {
        for (DeliverySnapshot delivery : value.deliveries()) mapper.delivery(params("id", delivery.id(), "tenantId", value.tenantId(), "applicationId", value.id(), "deliveryUnitId", delivery.deliveryUnitId(), "deliveryUnitCode", delivery.deliveryUnitCode(), "deliveryUnitName", delivery.deliveryUnitName(), "itemType", delivery.itemType().name(), "filePath", delivery.filePath(), "itemKey", delivery.itemKey(), "artifactType", delivery.artifactType().name(), "artifactVersion", delivery.artifactVersion(), "applicationRevision", value.rowVersion()));
        for (String requirement : value.requirementCodes()) mapper.requirement(params("id", nextId(), "tenantId", value.tenantId(), "applicationId", value.id(), "requirementCode", requirement, "applicationRevision", value.rowVersion()));
    }

    private Application withChildren(Application base) {
        Map<String, Object> params = params("tenantId", base.tenantId(), "applicationId", base.id());
        return new Application(base.id(), base.tenantId(), base.applicationCode(), base.projectId(), base.projectCode(), base.projectName(), base.emergency(), base.windowId(), base.assignedWindowId(), base.subsystemId(), base.subsystemCode(), base.subsystemName(), base.versionType(), base.characteristic(), base.workflowCode(), base.status(), base.requesterId(), base.requesterName(), base.requesterDepartment(), base.emergencyDescription(), base.urgentReason(), base.description(), base.approvedAt(), base.rowVersion(), base.createdBy(), base.updatedBy(), base.createdAt(), base.updatedAt(), mapper.deliveries(params), mapper.requirements(params));
    }

    private Map<String, Object> applicationParams(Application value) { return params("id", value.id(), "tenantId", value.tenantId(), "applicationCode", value.applicationCode(), "projectId", value.projectId(), "projectCode", value.projectCode(), "projectName", value.projectName(), "emergency", value.emergency(), "windowId", value.windowId(), "assignedWindowId", value.assignedWindowId(), "subsystemId", value.subsystemId(), "subsystemCode", value.subsystemCode(), "subsystemName", value.subsystemName(), "versionType", value.versionType().name(), "characteristic", value.characteristic().name(), "workflowCode", value.workflowCode(), "status", value.status().name(), "requesterId", value.requesterId(), "requesterName", value.requesterName(), "requesterDepartment", value.requesterDepartment(), "emergencyDescription", value.emergencyDescription(), "urgentReason", value.urgentReason(), "description", value.description(), "createdBy", value.createdBy(), "updatedBy", value.updatedBy()); }
    private Map<String, Object> params(Object... values) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); return result; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}

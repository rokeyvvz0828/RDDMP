package com.ccb.architecture.service;

import com.ccb.architecture.integration.ReleaseMasterDataQuery;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class JdbcReleaseMasterDataQuery implements ReleaseMasterDataQuery {
    private static final String ACTIVE_PHYSICAL_FILTER =
            " FROM arch_physical_subsystem WHERE tenant_id = ? AND project_id = ? "
                    + "AND deleted = 0 AND status = 'ACTIVE'";
    private static final String ACTIVE_DELIVERY_FROM = " FROM arch_delivery_unit unit "
            + "JOIN arch_physical_subsystem physical "
            + "ON physical.tenant_id = unit.tenant_id AND physical.project_id = unit.project_id "
            + "AND physical.id = unit.physical_subsystem_id AND physical.deleted = 0 AND physical.status = 'ACTIVE' "
            + "WHERE unit.tenant_id = ? AND unit.project_id = ? AND unit.physical_subsystem_id = ? "
            + "AND unit.deleted = 0 AND unit.status = 'ACTIVE'";

    private final JdbcTemplate jdbc;

    public JdbcReleaseMasterDataQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<PhysicalSubsystemRef> searchPhysicalSubsystems(AuthUser actor, long projectId, PageQuery page,
                                                                     String keyword) {
        requireActor(actor);
        requireProject(projectId);
        PageQuery normalizedPage = page == null ? new PageQuery(1, 100) : page;
        StringBuilder filter = new StringBuilder(ACTIVE_PHYSICAL_FILTER);
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId));
        addKeyword(filter, args, keyword, "code", "name");
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filter, Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add(Math.multiplyExact(normalizedPage.page() - 1, normalizedPage.size()));
        List<PhysicalSubsystemRef> records = jdbc.queryForList(
                        "SELECT id, code, name" + filter + " ORDER BY code, id LIMIT ? OFFSET ?", listArgs.toArray())
                .stream().map(JdbcReleaseMasterDataQuery::physical).toList();
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    @Override
    public PageResult<DeliveryUnitRef> searchDeliveryUnits(AuthUser actor, long projectId, long physicalSubsystemId,
                                                            PageQuery page, String keyword) {
        requireActor(actor);
        requireProject(projectId);
        requirePhysical(physicalSubsystemId);
        PageQuery normalizedPage = page == null ? new PageQuery(1, 100) : page;
        StringBuilder filter = new StringBuilder(ACTIVE_DELIVERY_FROM);
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId, physicalSubsystemId));
        addKeyword(filter, args, keyword, "unit.code", "unit.name");
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filter, Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add(Math.multiplyExact(normalizedPage.page() - 1, normalizedPage.size()));
        List<DeliveryUnitRef> records = jdbc.queryForList(
                        "SELECT unit.id, unit.physical_subsystem_id, unit.code, unit.name, unit.artifact_type_code"
                                + filter + " ORDER BY unit.code, unit.id LIMIT ? OFFSET ?", listArgs.toArray())
                .stream().map(JdbcReleaseMasterDataQuery::delivery).toList();
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    @Override
    public Optional<Selection> resolveActiveSelection(AuthUser actor, long projectId, long physicalSubsystemId,
                                                       Collection<Long> deliveryUnitIds) {
        requireActor(actor);
        requireProject(projectId);
        requirePhysical(physicalSubsystemId);
        Set<Long> normalizedIds = normalizeIds(deliveryUnitIds);
        Optional<PhysicalSubsystemRef> physical = jdbc.queryForList(
                        "SELECT id, code, name" + ACTIVE_PHYSICAL_FILTER + " AND id = ?",
                        actor.tenantId(), projectId, physicalSubsystemId)
                .stream().findFirst().map(JdbcReleaseMasterDataQuery::physical);
        if (physical.isEmpty()) {
            return Optional.empty();
        }
        if (normalizedIds.isEmpty()) {
            return Optional.of(new Selection(physical.get(), List.of()));
        }
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId, physicalSubsystemId));
        args.addAll(normalizedIds);
        String placeholders = String.join(", ", normalizedIds.stream().map(id -> "?").toList());
        List<DeliveryUnitRef> units = jdbc.queryForList(
                        "SELECT unit.id, unit.physical_subsystem_id, unit.code, unit.name, unit.artifact_type_code"
                                + ACTIVE_DELIVERY_FROM + " AND unit.id IN (" + placeholders + ") ORDER BY unit.id",
                        args.toArray())
                .stream().map(JdbcReleaseMasterDataQuery::delivery).toList();
        return units.size() == normalizedIds.size()
                ? Optional.of(new Selection(physical.get(), units))
                : Optional.empty();
    }

    private static Set<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "交付单元标识无效");
            }
            result.add(id);
        }
        return result;
    }

    private static void addKeyword(StringBuilder sql, List<Object> args, String keyword, String codeColumn,
                                   String nameColumn) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String value = "%" + escapeLike(keyword.trim()) + "%";
        sql.append(" AND (").append(codeColumn).append(" LIKE ? ESCAPE '\\\\' OR ")
                .append(nameColumn).append(" LIKE ? ESCAPE '\\\\')");
        args.add(value);
        args.add(value);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static PhysicalSubsystemRef physical(Map<String, Object> row) {
        return new PhysicalSubsystemRef(number(row, "id"), (String) row.get("code"), (String) row.get("name"));
    }

    private static DeliveryUnitRef delivery(Map<String, Object> row) {
        return new DeliveryUnitRef(number(row, "id"), number(row, "physical_subsystem_id"),
                (String) row.get("code"), (String) row.get("name"), (String) row.get("artifact_type_code"));
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static void requireActor(AuthUser actor) {
        if (actor == null || !actor.enabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户不可访问架构主数据");
        }
    }

    private static void requireProject(long projectId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识无效");
        }
    }

    private static void requirePhysical(long physicalSubsystemId) {
        if (physicalSubsystemId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "物理子系统标识无效");
        }
    }
}

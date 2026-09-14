package com.ccb.architecture.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Optional;

/** 系统参与关系持久化；所有写入和执行资格锁统一锁定物理系统父记录。 */
@Repository
public class SubsystemParticipationStore {
    public record SystemScope(long id, Long ownerUserId, long rowVersion) { }
    private final JdbcTemplate jdbc;

    public SubsystemParticipationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<SystemScope> findSystem(long tenantId, long projectId, long systemId, boolean lock) {
        return jdbc.query("""
                SELECT id, owner_user_id, row_version FROM arch_physical_subsystem
                WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0
                """ + (lock ? " FOR UPDATE" : ""), (rs, n) ->
                new SystemScope(rs.getLong("id"), rs.getObject("owner_user_id", Long.class), rs.getLong("row_version")),
                tenantId, projectId, systemId).stream().findFirst();
    }

    public Optional<Long> findUnitSystem(long tenantId, long projectId, long unitId) {
        return jdbc.query("""
                SELECT physical_subsystem_id FROM arch_deployment_unit
                WHERE tenant_id = ? AND project_id = ? AND id = ?
                """, (rs, n) -> rs.getLong(1), tenantId, projectId, unitId).stream().findFirst();
    }

    /** 仅查询本模块的系统归属关系；有效项目成员校验由服务层公开契约完成。 */
    public List<Long> participatingSystemIds(long tenantId, long projectId, long userId) {
        return jdbc.query("""
                SELECT s.id FROM arch_physical_subsystem s
                WHERE s.tenant_id = ? AND s.project_id = ? AND s.deleted = 0
                  AND (s.owner_user_id = ? OR EXISTS (
                      SELECT 1 FROM arch_subsystem_participant p
                      WHERE p.tenant_id = s.tenant_id AND p.project_id = s.project_id
                        AND p.subsystem_id = s.id AND p.user_id = ?)) ORDER BY s.id
                """, (rs, n) -> rs.getLong(1), tenantId, projectId, userId, userId);
    }

    public List<Long> findExplicit(long tenantId, long projectId, long systemId) {
        return jdbc.query("""
                SELECT user_id FROM arch_subsystem_participant
                WHERE tenant_id = ? AND project_id = ? AND subsystem_id = ? ORDER BY user_id
                """ + (TransactionSynchronizationManager.isActualTransactionActive() ? " FOR SHARE" : ""),
                (rs, n) -> rs.getLong(1), tenantId, projectId, systemId);
    }

    public boolean hasPendingResponsibility(long tenantId, long projectId, long systemId, long userId) {
        String target = """
                  AND ((t.target_type = 'PHYSICAL_SUBSYSTEM' AND t.target_id = ?)
                    OR (t.target_type = 'DEPLOYMENT_UNIT' AND u.physical_subsystem_id = ?))
                """;
        String lock = TransactionSynchronizationManager.isActualTransactionActive() ? " FOR UPDATE" : "";
        // 守卫必须读取最新责任，而不是发布事务更早建立的一致性快照。
        if (!jdbc.query("""
                SELECT t.id FROM arch_plan_task t
                LEFT JOIN arch_deployment_unit u ON t.target_type = 'DEPLOYMENT_UNIT'
                  AND u.tenant_id = t.tenant_id AND u.project_id = t.project_id AND u.id = t.target_id
                WHERE t.tenant_id = ? AND t.project_id = ? AND t.owner_user_id = ?
                  AND t.cancelled = 0 AND t.status NOT IN ('COMPLETED','CANCELLED')
                """ + target + " LIMIT 1" + lock, (rs, n) -> rs.getLong(1),
                tenantId, projectId, userId, systemId, systemId).isEmpty()) return true;
        return !jdbc.query("""
                SELECT b.id FROM arch_plan_block b
                JOIN arch_plan_task t ON t.tenant_id = b.tenant_id AND t.project_id = b.project_id AND t.id = b.task_id
                LEFT JOIN arch_deployment_unit u ON t.target_type = 'DEPLOYMENT_UNIT'
                  AND u.tenant_id = t.tenant_id AND u.project_id = t.project_id AND u.id = t.target_id
                WHERE b.tenant_id = ? AND b.project_id = ? AND b.owner_user_id = ? AND b.status = 'OPEN'
                """ + target + " LIMIT 1" + lock, (rs, n) -> rs.getLong(1),
                tenantId, projectId, userId, systemId, systemId).isEmpty();
    }

    public boolean hasProjectPendingResponsibility(long tenantId, long projectId, long userId) {
        String lock = TransactionSynchronizationManager.isActualTransactionActive() ? " FOR UPDATE" : "";
        // 使用当前锁读，而非事务早先快照；完成/取消任务不阻止退出，OPEN阻塞独立校验。
        if (!jdbc.query("""
                SELECT id FROM arch_plan_task
                WHERE tenant_id = ? AND project_id = ? AND owner_user_id = ?
                  AND cancelled = 0 AND status NOT IN ('COMPLETED','CANCELLED') LIMIT 1
                """ + lock, (rs, n) -> rs.getLong(1), tenantId, projectId, userId).isEmpty()) return true;
        return !jdbc.query("""
                SELECT id FROM arch_plan_block
                WHERE tenant_id = ? AND project_id = ? AND owner_user_id = ? AND status = 'OPEN' LIMIT 1
                """ + lock, (rs, n) -> rs.getLong(1), tenantId, projectId, userId).isEmpty();
    }

    public boolean advanceVersion(long tenantId, long projectId, long systemId, long version, long actorId) {
        return jdbc.update("""
                UPDATE arch_physical_subsystem SET row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND project_id = ? AND id = ? AND row_version = ? AND deleted = 0
                """, actorId, tenantId, projectId, systemId, version) == 1;
    }

    public void replace(long tenantId, long projectId, long systemId, List<Long> userIds, long actorId) {
        jdbc.update("DELETE FROM arch_subsystem_participant WHERE tenant_id = ? AND project_id = ? AND subsystem_id = ?",
                tenantId, projectId, systemId);
        for (long userId : userIds) {
            jdbc.update("""
                    INSERT INTO arch_subsystem_participant (tenant_id, project_id, subsystem_id, user_id, created_by)
                    VALUES (?, ?, ?, ?, ?)
                    """, tenantId, projectId, systemId, userId, actorId);
        }
    }

    public void recordChange(long tenantId, long projectId, long systemId, long actorId,
                             List<Long> before, List<Long> after, String reason, String traceId) {
        jdbc.update("""
                INSERT INTO arch_subsystem_participant_audit
                  (tenant_id, project_id, subsystem_id, actor_user_id, before_user_ids, after_user_ids, reason, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, tenantId, projectId, systemId, actorId, before.toString(), after.toString(), reason, traceId);
    }
}

package com.ccb.architecture.change.persistence;

import com.ccb.architecture.change.model.SubsystemNumberKind;
import com.ccb.architecture.change.model.SubsystemNumberReleaseReason;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;
import com.ccb.architecture.change.number.SubsystemNumberCapacityExceededException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * 基于 MySQL 行锁的全局编号保留存储。
 *
 * <p>本类不自行开启事务。调用方必须把编号保留、业务草稿和最终发布放在同一真实事务中。</p>
 */
@Repository
public class JdbcSubsystemNumberReservationStore implements SubsystemNumberReservationStore {
    private static final RowMapper<SubsystemNumberReservation> RESERVATION_MAPPER = (rs, rowNum) -> {
        SubsystemNumberKind kind = SubsystemNumberKind.valueOf(rs.getString("reservation_kind"));
        String namespaceCode = rs.getString("namespace_code");
        Integer logicalSequence = logicalSequence(kind, namespaceCode);
        return new SubsystemNumberReservation(
                rs.getLong("allocation_scope"),
                namespaceCode,
                rs.getInt("ordinal"),
                rs.getLong("tenant_id"),
                rs.getLong("application_id"),
                rs.getInt("line_no"),
                kind,
                logicalSequence,
                null);
    };

    private final JdbcTemplate jdbc;

    public JdbcSubsystemNumberReservationStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc 不能为空");
    }

    @Override
    public Optional<SubsystemNumberReservation> findByApplicationLine(SubsystemNumberRequest request) {
        requireTransaction();
        Objects.requireNonNull(request, "request 不能为空");
        return reservationsByIdentity(request.tenantId(), request.applicationId(), request.kind(), request.lineNo(), false)
                .stream()
                .findFirst();
    }

    @Override
    public SubsystemNumberReservation reserve(SubsystemNumberRequest request, IntPredicate candidateAvailable) {
        requireTransaction();
        Objects.requireNonNull(request, "request 不能为空");
        Objects.requireNonNull(candidateAvailable, "candidateAvailable 不能为空");

        lockApplication(request.tenantId(), request.applicationId());
        Optional<SubsystemNumberReservation> existing = reservationsByIdentity(
                request.tenantId(), request.applicationId(), request.kind(), request.lineNo(), false)
                .stream()
                .findFirst();
        if (existing.isPresent()) {
            assertMatchesRequest(existing.get(), request);
            return existing.get();
        }

        lockNamespace(request.namespaceCode());
        while (true) {
            Optional<Integer> recycled = smallestRecycledForUpdate(request.namespaceCode());
            if (recycled.isPresent()) {
                int ordinal = recycled.get();
                ensureCapacity(request, ordinal);
                if (!candidateAvailable.test(ordinal)) {
                    deleteRecycled(request.namespaceCode(), ordinal);
                    continue;
                }
                deleteRecycled(request.namespaceCode(), ordinal);
                return insertReservation(request, ordinal);
            }

            int ordinal = nextOrdinalForUpdate(request.namespaceCode());
            ensureCapacity(request, ordinal);
            advanceNamespace(request.namespaceCode(), ordinal + 1);
            if (!candidateAvailable.test(ordinal)) {
                continue;
            }
            return insertReservation(request, ordinal);
        }
    }

    @Override
    public void release(SubsystemNumberReservation reservation, SubsystemNumberReleaseReason reason) {
        requireTransaction();
        Objects.requireNonNull(reservation, "reservation 不能为空");
        Objects.requireNonNull(reason, "reason 不能为空");
        if (reason != SubsystemNumberReleaseReason.REJECTED && reason != SubsystemNumberReleaseReason.CANCELLED) {
            throw new IllegalArgumentException("只有 REJECTED/CANCELLED 可以释放编号");
        }
        assertValidNamespace(reservation);
        lockApplication(reservation.tenantId(), reservation.applicationId());
        lockNamespace(reservation.namespaceCode());

        Optional<SubsystemNumberReservation> current = reservationsByIdentity(
                reservation.tenantId(), reservation.applicationId(), reservation.kind(), reservation.lineNo(), true)
                .stream()
                .findFirst();
        if (current.isEmpty()) {
            return;
        }
        assertSameReservation(current.get(), reservation);
        deleteReservation(current.get());
        jdbc.update("""
                        INSERT INTO arch_subsystem_number_recycled
                            (allocation_scope, namespace_code, ordinal, tenant_id, application_id,
                             reservation_kind, line_no, release_reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                current.get().allocationScope(), current.get().namespaceCode(), current.get().ordinal(),
                current.get().tenantId(), current.get().applicationId(), current.get().kind().name(),
                current.get().lineNo(), reason.name());
    }

    @Override
    public void consume(SubsystemNumberReservation reservation) {
        requireTransaction();
        Objects.requireNonNull(reservation, "reservation 不能为空");
        assertValidNamespace(reservation);
        lockApplication(reservation.tenantId(), reservation.applicationId());
        lockNamespace(reservation.namespaceCode());

        Optional<SubsystemNumberReservation> current = reservationsByIdentity(
                reservation.tenantId(), reservation.applicationId(), reservation.kind(), reservation.lineNo(), true)
                .stream()
                .findFirst();
        if (current.isEmpty()) {
            return;
        }
        assertSameReservation(current.get(), reservation);
        deleteReservation(current.get());
    }

    @Override
    public boolean isPublishedCodeOccupied(String code) {
        requireTransaction();
        Objects.requireNonNull(code, "code 不能为空");
        Integer occupied = jdbc.queryForObject("""
                SELECT CASE WHEN
                    EXISTS (SELECT 1 FROM arch_logical_subsystem WHERE code = ?)
                    OR EXISTS (SELECT 1 FROM arch_physical_subsystem WHERE code = ?)
                THEN 1 ELSE 0 END
                """, Integer.class, code, code);
        return occupied != null && occupied == 1;
    }

    private void lockApplication(long tenantId, long applicationId) {
        List<Long> rows = jdbc.queryForList("""
                SELECT id
                FROM arch_subsystem_change_application
                WHERE tenant_id = ? AND id = ?
                FOR UPDATE
                """, Long.class, tenantId, applicationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("编号所属申请不存在");
        }
    }

    private void lockNamespace(String namespaceCode) {
        jdbc.update("""
                INSERT INTO arch_subsystem_number_namespace (allocation_scope, namespace_code, next_ordinal)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE namespace_code = VALUES(namespace_code)
                """, SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE, namespaceCode);
        List<Integer> rows = jdbc.queryForList("""
                SELECT next_ordinal
                FROM arch_subsystem_number_namespace
                WHERE allocation_scope = ? AND namespace_code = ?
                FOR UPDATE
                """, Integer.class, SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE, namespaceCode);
        if (rows.size() != 1) {
            throw new IllegalStateException("无法锁定编号命名空间 " + namespaceCode);
        }
    }

    private Optional<Integer> smallestRecycledForUpdate(String namespaceCode) {
        return jdbc.queryForList("""
                SELECT ordinal
                FROM arch_subsystem_number_recycled
                WHERE allocation_scope = ? AND namespace_code = ?
                ORDER BY ordinal
                LIMIT 1
                FOR UPDATE
                """, Integer.class, SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE, namespaceCode)
                .stream()
                .findFirst();
    }

    private int nextOrdinalForUpdate(String namespaceCode) {
        List<Integer> rows = jdbc.queryForList("""
                SELECT next_ordinal
                FROM arch_subsystem_number_namespace
                WHERE allocation_scope = ? AND namespace_code = ?
                FOR UPDATE
                """, Integer.class, SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE, namespaceCode);
        if (rows.size() != 1) {
            throw new IllegalStateException("编号命名空间不存在 " + namespaceCode);
        }
        return rows.get(0);
    }

    private void advanceNamespace(String namespaceCode, int nextOrdinal) {
        int updated = jdbc.update("""
                UPDATE arch_subsystem_number_namespace
                SET next_ordinal = ?
                WHERE allocation_scope = ? AND namespace_code = ?
                """, nextOrdinal, SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE, namespaceCode);
        if (updated != 1) {
            throw new IllegalStateException("推进编号命名空间失败 " + namespaceCode);
        }
    }

    private void deleteRecycled(String namespaceCode, int ordinal) {
        int deleted = jdbc.update("""
                DELETE FROM arch_subsystem_number_recycled
                WHERE allocation_scope = ? AND namespace_code = ? AND ordinal = ?
                """, SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE, namespaceCode, ordinal);
        if (deleted != 1) {
            throw new IllegalStateException("回收编号在锁内丢失 " + namespaceCode + "/" + ordinal);
        }
    }

    private SubsystemNumberReservation insertReservation(SubsystemNumberRequest request, int ordinal) {
        jdbc.update("""
                        INSERT INTO arch_subsystem_number_reservation
                            (allocation_scope, namespace_code, ordinal, tenant_id, application_id,
                             reservation_kind, line_no)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                request.allocationScope(), request.namespaceCode(), ordinal, request.tenantId(),
                request.applicationId(), request.kind().name(), request.lineNo());
        return SubsystemNumberReservation.unformatted(request, ordinal);
    }

    private List<SubsystemNumberReservation> reservationsByIdentity(
            long tenantId, long applicationId, SubsystemNumberKind kind, int lineNo, boolean forUpdate) {
        return jdbc.query("""
                        SELECT allocation_scope, namespace_code, ordinal, tenant_id, application_id,
                               reservation_kind, line_no
                        FROM arch_subsystem_number_reservation
                        WHERE tenant_id = ? AND application_id = ? AND reservation_kind = ? AND line_no = ?
                        """ + (forUpdate ? " FOR UPDATE" : ""),
                RESERVATION_MAPPER, tenantId, applicationId, kind.name(), lineNo);
    }

    private void deleteReservation(SubsystemNumberReservation reservation) {
        int deleted = jdbc.update("""
                DELETE FROM arch_subsystem_number_reservation
                WHERE allocation_scope = ? AND namespace_code = ? AND ordinal = ?
                """, reservation.allocationScope(), reservation.namespaceCode(), reservation.ordinal());
        if (deleted != 1) {
            throw new IllegalStateException("活动编号保留在锁内丢失");
        }
    }

    private void ensureCapacity(SubsystemNumberRequest request, int ordinal) {
        if (ordinal < 1 || ordinal > request.maximumOrdinal()) {
            throw new SubsystemNumberCapacityExceededException(request.kind(), ordinal, request.maximumOrdinal());
        }
    }

    private void assertMatchesRequest(SubsystemNumberReservation reservation, SubsystemNumberRequest request) {
        if (reservation.allocationScope() != request.allocationScope()
                || !reservation.namespaceCode().equals(request.namespaceCode())
                || reservation.tenantId() != request.tenantId()
                || reservation.applicationId() != request.applicationId()
                || reservation.lineNo() != request.lineNo()
                || reservation.kind() != request.kind()
                || !Objects.equals(reservation.logicalSequence(), request.logicalSequence())) {
            throw new IllegalStateException("既有编号保留与申请行不一致");
        }
    }

    private void assertSameReservation(SubsystemNumberReservation current, SubsystemNumberReservation supplied) {
        if (current.allocationScope() != supplied.allocationScope()
                || !current.namespaceCode().equals(supplied.namespaceCode())
                || current.ordinal() != supplied.ordinal()
                || current.tenantId() != supplied.tenantId()
                || current.applicationId() != supplied.applicationId()
                || current.lineNo() != supplied.lineNo()
                || current.kind() != supplied.kind()
                || !Objects.equals(current.logicalSequence(), supplied.logicalSequence())) {
            throw new IllegalStateException("活动编号保留已变化，拒绝处理陈旧对象");
        }
    }

    private void assertValidNamespace(SubsystemNumberReservation reservation) {
        String expected = reservation.kind() == SubsystemNumberKind.LOGICAL
                ? "LOGICAL"
                : "PHYSICAL:" + reservation.logicalSequence();
        if (reservation.allocationScope() != SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE
                || !expected.equals(reservation.namespaceCode())) {
            throw new IllegalArgumentException("编号保留的命名空间无效");
        }
    }

    private static Integer logicalSequence(SubsystemNumberKind kind, String namespaceCode) {
        if (kind == SubsystemNumberKind.LOGICAL) {
            if (!"LOGICAL".equals(namespaceCode)) {
                throw new IllegalStateException("逻辑编号命名空间无效 " + namespaceCode);
            }
            return null;
        }
        String prefix = "PHYSICAL:";
        if (!namespaceCode.startsWith(prefix)) {
            throw new IllegalStateException("物理编号命名空间无效 " + namespaceCode);
        }
        try {
            return Integer.valueOf(namespaceCode.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("物理编号命名空间无效 " + namespaceCode, exception);
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("编号保留操作必须在真实数据库事务中执行");
        }
    }
}

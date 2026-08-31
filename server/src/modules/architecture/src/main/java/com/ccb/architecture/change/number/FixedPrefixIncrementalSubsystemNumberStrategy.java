package com.ccb.architecture.change.number;

import com.ccb.architecture.change.model.SubsystemNumberKind;
import com.ccb.architecture.change.model.SubsystemNumberReleaseReason;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;
import com.ccb.architecture.change.persistence.SubsystemNumberReservationStore;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * 首期固定前缀策略：逻辑为 A0001..A9999，物理为 W + 父逻辑四位序号 + 1..9,A..Z。
 */
@Component
public final class FixedPrefixIncrementalSubsystemNumberStrategy implements SubsystemNumberStrategy {
    private static final String PHYSICAL_SLOTS = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final SubsystemNumberReservationStore store;

    public FixedPrefixIncrementalSubsystemNumberStrategy(SubsystemNumberReservationStore store) {
        this.store = Objects.requireNonNull(store, "store 不能为空");
    }

    @Override
    public SubsystemNumberReservation reserve(SubsystemNumberRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        return format(store.reserve(request,
                ordinal -> !store.isPublishedCodeOccupied(format(request, ordinal))));
    }

    @Override
    public void release(SubsystemNumberReservation reservation, SubsystemNumberReleaseReason reason) {
        Objects.requireNonNull(reservation, "reservation 不能为空");
        Objects.requireNonNull(reason, "reason 不能为空");
        if (reason != SubsystemNumberReleaseReason.REJECTED && reason != SubsystemNumberReleaseReason.CANCELLED) {
            throw new IllegalArgumentException("只有 REJECTED/CANCELLED 可以释放编号");
        }
        store.release(reservation, reason);
    }

    @Override
    public void consume(SubsystemNumberReservation reservation) {
        store.consume(Objects.requireNonNull(reservation, "reservation 不能为空"));
    }

    private SubsystemNumberReservation format(SubsystemNumberReservation reservation) {
        return reservation.withCode(format(reservation.kind(), reservation.logicalSequence(), reservation.ordinal()));
    }

    private String format(SubsystemNumberRequest request, int ordinal) {
        return format(request.kind(), request.logicalSequence(), ordinal);
    }

    private String format(SubsystemNumberKind kind, Integer logicalSequence, int ordinal) {
        int maximum = kind == SubsystemNumberKind.LOGICAL
                ? SubsystemNumberRequest.LOGICAL_MAX_ORDINAL
                : SubsystemNumberRequest.PHYSICAL_MAX_ORDINAL;
        if (ordinal < 1 || ordinal > maximum) {
            throw new SubsystemNumberCapacityExceededException(kind, ordinal, maximum);
        }
        if (kind == SubsystemNumberKind.LOGICAL) {
            return String.format(Locale.ROOT, "A%04d", ordinal);
        }
        if (logicalSequence == null || logicalSequence < 1 || logicalSequence > SubsystemNumberRequest.LOGICAL_MAX_ORDINAL) {
            throw new IllegalArgumentException("物理编号缺少有效父逻辑序号");
        }
        return String.format(Locale.ROOT, "W%04d%s", logicalSequence, PHYSICAL_SLOTS.charAt(ordinal - 1));
    }
}

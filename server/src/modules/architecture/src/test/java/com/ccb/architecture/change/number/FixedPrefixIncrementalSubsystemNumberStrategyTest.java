package com.ccb.architecture.change.number;

import com.ccb.architecture.change.model.SubsystemNumberKind;
import com.ccb.architecture.change.model.SubsystemNumberReleaseReason;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;
import com.ccb.architecture.change.persistence.SubsystemNumberReservationStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedPrefixIncrementalSubsystemNumberStrategyTest {

    @Test
    void formatsLogicalAndPhysicalBoundariesWithFixedPrefix() {
        assertThat(reserve(SubsystemNumberRequest.logical(1, 101), 1).code()).isEqualTo("A0001");
        assertThat(reserve(SubsystemNumberRequest.logical(1, 101), 9_999).code()).isEqualTo("A9999");
        assertThat(reserve(SubsystemNumberRequest.physical(1, 101, 1, 1), 1).code()).isEqualTo("W00011");
        assertThat(reserve(SubsystemNumberRequest.physical(1, 101, 1, 1), 9).code()).isEqualTo("W00019");
        assertThat(reserve(SubsystemNumberRequest.physical(1, 101, 1, 1), 10).code()).isEqualTo("W0001A");
        assertThat(reserve(SubsystemNumberRequest.physical(1, 101, 1, 1), 35).code()).isEqualTo("W0001Z");
    }

    @Test
    void rejectsOrdinalBeyondLogicalOrPhysicalCapacity() {
        assertThatThrownBy(() -> reserve(SubsystemNumberRequest.logical(1, 101), 10_000))
                .isInstanceOf(SubsystemNumberCapacityExceededException.class);
        assertThatThrownBy(() -> reserve(SubsystemNumberRequest.physical(1, 101, 1, 1), 36))
                .isInstanceOf(SubsystemNumberCapacityExceededException.class);
    }

    @Test
    void returnedApplicationKeepsItsExistingReservationAndDoesNotAllocateAgain() {
        FakeReservationStore store = new FakeReservationStore(7, 8);
        FixedPrefixIncrementalSubsystemNumberStrategy strategy = new FixedPrefixIncrementalSubsystemNumberStrategy(store);
        SubsystemNumberRequest request = SubsystemNumberRequest.logical(7, 701);

        SubsystemNumberReservation first = strategy.reserve(request);
        SubsystemNumberReservation resubmitted = strategy.reserve(request);

        assertThat(first.code()).isEqualTo("A0007");
        assertThat(resubmitted).isEqualTo(first);
        assertThat(store.allocationAttempts).isEqualTo(1);
        assertThat(store.remainingOrdinals()).containsExactly(8);
    }

    @Test
    void skipsPublishedCodeInsideTheStoreCandidateSelection() {
        FakeReservationStore store = new FakeReservationStore(1, 2);
        store.occupyPublishedCode("A0001");
        FixedPrefixIncrementalSubsystemNumberStrategy strategy = new FixedPrefixIncrementalSubsystemNumberStrategy(store);

        SubsystemNumberReservation reservation = strategy.reserve(SubsystemNumberRequest.logical(9, 901));

        assertThat(reservation.code()).isEqualTo("A0002");
        assertThat(store.allocationAttempts).isEqualTo(2);
    }

    @Test
    void releasesOnlyTheRejectedReservation() {
        FakeReservationStore store = new FakeReservationStore(1);
        FixedPrefixIncrementalSubsystemNumberStrategy strategy = new FixedPrefixIncrementalSubsystemNumberStrategy(store);
        SubsystemNumberReservation reservation = strategy.reserve(SubsystemNumberRequest.logical(9, 901));

        strategy.release(reservation, SubsystemNumberReleaseReason.REJECTED);

        assertThat(store.releaseReasons).containsEntry(reservation.identity(), SubsystemNumberReleaseReason.REJECTED);
        assertThat(store.consumed).isEmpty();
    }

    @Test
    void consumesOnlyTheApprovedReservation() {
        FakeReservationStore store = new FakeReservationStore(1);
        FixedPrefixIncrementalSubsystemNumberStrategy strategy = new FixedPrefixIncrementalSubsystemNumberStrategy(store);
        SubsystemNumberReservation reservation = strategy.reserve(SubsystemNumberRequest.logical(9, 901));

        strategy.consume(reservation);

        assertThat(store.consumed).contains(reservation.identity());
        assertThat(store.releaseReasons).isEmpty();
    }

    private SubsystemNumberReservation reserve(SubsystemNumberRequest request, int ordinal) {
        return new FixedPrefixIncrementalSubsystemNumberStrategy(new FakeReservationStore(ordinal)).reserve(request);
    }

    private static final class FakeReservationStore implements SubsystemNumberReservationStore {
        private final Deque<Integer> ordinals = new ArrayDeque<>();
        private final Map<SubsystemNumberReservation.Identity, SubsystemNumberReservation> reservations = new HashMap<>();
        private final Map<SubsystemNumberReservation.Identity, SubsystemNumberReleaseReason> releaseReasons = new HashMap<>();
        private final java.util.Set<SubsystemNumberReservation.Identity> consumed = new java.util.HashSet<>();
        private int allocationAttempts;

        private FakeReservationStore(int... ordinals) {
            for (int ordinal : ordinals) {
                this.ordinals.addLast(ordinal);
            }
        }

        @Override
        public Optional<SubsystemNumberReservation> findByApplicationLine(SubsystemNumberRequest request) {
            return Optional.ofNullable(reservations.get(SubsystemNumberReservation.Identity.from(request)));
        }

        @Override
        public SubsystemNumberReservation reserve(SubsystemNumberRequest request, IntPredicate candidateAvailable) {
            SubsystemNumberReservation.Identity identity = SubsystemNumberReservation.Identity.from(request);
            SubsystemNumberReservation existing = reservations.get(identity);
            if (existing != null) {
                return existing;
            }
            while (!ordinals.isEmpty()) {
                allocationAttempts++;
                int ordinal = ordinals.removeFirst();
                if (!candidateAvailable.test(ordinal)) {
                    continue;
                }
                SubsystemNumberReservation reservation = SubsystemNumberReservation.unformatted(request, ordinal);
                reservations.put(identity, reservation);
                return reservation;
            }
            throw new AssertionError("测试存储未提供可用候选值");
        }

        @Override
        public void release(SubsystemNumberReservation reservation, SubsystemNumberReleaseReason reason) {
            reservations.remove(reservation.identity());
            releaseReasons.put(reservation.identity(), reason);
        }

        @Override
        public void consume(SubsystemNumberReservation reservation) {
            reservations.remove(reservation.identity());
            consumed.add(reservation.identity());
        }

        @Override
        public boolean isPublishedCodeOccupied(String code) {
            return publishedCodes.contains(code);
        }

        private final java.util.Set<String> publishedCodes = new java.util.HashSet<>();

        private void occupyPublishedCode(String code) {
            publishedCodes.add(code);
        }

        private Deque<Integer> remainingOrdinals() {
            return ordinals;
        }
    }
}

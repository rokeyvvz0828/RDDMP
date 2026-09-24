package com.ccb.datamigration.lifecycle.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 时效三档判定器穷举（基线 18.3 独立维度）：>24h NORMAL / ≤24h NEAR_OVERDUE / ≤0 OVERDUE / 豁免与终态不预警。 */
class SlaAdjudicatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 12, 0);

    @Test
    void moreThan24HoursIsNormal() {
        assertThat(SlaAdjudicator.adjudicate(NOW, NOW.plusHours(25))).isEqualTo("NORMAL");
        assertThat(SlaAdjudicator.adjudicate(NOW, null)).isEqualTo("NORMAL");
    }

    @Test
    void within24HoursIsNearOverdue() {
        assertThat(SlaAdjudicator.adjudicate(NOW, NOW.plusHours(24))).isEqualTo("NEAR_OVERDUE");
        assertThat(SlaAdjudicator.adjudicate(NOW, NOW.plusMinutes(1))).isEqualTo("NEAR_OVERDUE");
    }

    @Test
    void atOrBeforeNowIsOverdue() {
        assertThat(SlaAdjudicator.adjudicate(NOW, NOW)).isEqualTo("OVERDUE");
        assertThat(SlaAdjudicator.adjudicate(NOW, NOW.minusHours(3))).isEqualTo("OVERDUE");
    }

    @Test
    void warningParticipationExcludesExemptTerminalAndSuspended() {
        assertThat(SlaAdjudicator.participatesInWarning("EXECUTING", false)).isTrue();
        assertThat(SlaAdjudicator.participatesInWarning("EXECUTING", true)).isFalse();
        assertThat(SlaAdjudicator.participatesInWarning("CLOSED", false)).isFalse();
        assertThat(SlaAdjudicator.participatesInWarning("ARCHIVED", false)).isFalse();
        assertThat(SlaAdjudicator.participatesInWarning("CANCELLED", false)).isFalse();
        assertThat(SlaAdjudicator.participatesInWarning("SUSPENDED", false)).isFalse();
    }
}

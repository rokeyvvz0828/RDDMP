package com.ccb.datamigration.lifecycle.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ccb.datamigration.lifecycle.order.QuadrantClosureValidator.Outcome;
import org.junit.jupiter.api.Test;

/** 四象限闭环判定器穷举（基线 18.2 唯一出口）：A0 自动闭环 / A1 置审核 / 准出未达 NOT_READY。 */
class QuadrantClosureValidatorTest {

    @Test
    void a0b0ExitFilledAutoCloses() {
        assertThat(QuadrantClosureValidator.evaluate(false, false, true, false)).isEqualTo(Outcome.AUTO_CLOSED);
    }

    @Test
    void a0b1NeedsDeliverableToAutoClose() {
        assertThat(QuadrantClosureValidator.evaluate(false, true, true, false)).isEqualTo(Outcome.NOT_READY);
        assertThat(QuadrantClosureValidator.evaluate(false, true, true, true)).isEqualTo(Outcome.AUTO_CLOSED);
    }

    @Test
    void a1b0ExitFilledWaitsForAudit() {
        assertThat(QuadrantClosureValidator.evaluate(true, false, true, false)).isEqualTo(Outcome.WAIT_AUDIT);
    }

    @Test
    void a1b1RequiresExitAndDeliverableThenWaitsForAudit() {
        assertThat(QuadrantClosureValidator.evaluate(true, true, false, false)).isEqualTo(Outcome.NOT_READY);
        assertThat(QuadrantClosureValidator.evaluate(true, true, true, false)).isEqualTo(Outcome.NOT_READY);
        assertThat(QuadrantClosureValidator.evaluate(true, true, true, true)).isEqualTo(Outcome.WAIT_AUDIT);
    }

    @Test
    void missingExitAlwaysNotReady() {
        assertThat(QuadrantClosureValidator.evaluate(false, false, false, true)).isEqualTo(Outcome.NOT_READY);
        assertThat(QuadrantClosureValidator.evaluate(true, true, false, true)).isEqualTo(Outcome.NOT_READY);
    }
}

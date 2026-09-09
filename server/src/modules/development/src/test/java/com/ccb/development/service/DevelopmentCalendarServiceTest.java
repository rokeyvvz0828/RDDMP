package com.ccb.development.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.development.config.DevelopmentSettings.CalendarDefinition;
import com.ccb.development.model.DevelopmentStageModels.DurationMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@DisplayName("DevelopmentCalendarService 纯函数单元测试")
class DevelopmentCalendarServiceTest {

    private final CalendarDefinition defaultCalendar = new CalendarDefinition(
            List.of(1, 2, 3, 4, 5), List.of(), List.of()
    );

    @Test
    @DisplayName("workdays: 基础工作日与单日边界（首尾计入、同日、周末）")
    void workdays_basicAndSingleDay() {
        // 同工作日 = 1，周末 = 0
        assertThat(DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), defaultCalendar)).isEqualTo(1L);
        assertThat(DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12), defaultCalendar)).isZero();
        // 2026-09-07(周一) 到 2026-09-11(周五) = 5
        assertThat(DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11), defaultCalendar)).isEqualTo(5L);
        // 纯周末区间 = 0
        assertThat(DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13), defaultCalendar)).isZero();
    }

    @Test
    @DisplayName("workdays: 调休覆盖生效与幂等（周末补班+1、工作日休息-1、不重复计入）")
    void workdays_overridesAndIdempotence() {
        var calendar = new CalendarDefinition(
                List.of(1, 2, 3, 4, 5),
                List.of(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 8)),
                List.of(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 13))
        );
        // 周末补班覆盖: 9-12到9-13原本0天，补班后为1天
        assertThat(DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13), calendar)).isEqualTo(1L);
        // 工作日休息覆盖及幂等: 9-7到9-11原本5天，9-9休息扣1天，9-8本是工作日不重复计算 -> 4天
        assertThat(DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11), calendar)).isEqualTo(4L);
    }

    @Test
    @DisplayName("workdays: 日期倒序抛出 BusinessException")
    void workdays_reversedDatesThrowsException() {
        assertThrows(BusinessException.class, () -> DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 7), defaultCalendar));
    }

    @Test
    @DisplayName("workdays: 跨年计算与超大区间合理性（限定2秒内响应）")
    void workdays_crossYearAndLargeRangePerformance() {
        // 跨年: 2026-12-31(周四) 到 2027-01-04(周一)，工作日为周四、周五、周一共 3 天
        assertThat(DevelopmentCalendarService.workdays(
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 4), defaultCalendar)).isEqualTo(3L);

        // 大跨度 1900-9999 性能与合理性校验，不主观猜测具体天数
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            long days = DevelopmentCalendarService.workdays(
                    LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31), defaultCalendar);
            assertThat(days).isPositive();
        });
    }

    @Test
    @DisplayName("measure: 正常计算偏差百分比 (实际-计划)/计划*100 保留2位HALF_UP")
    void measure_normalVariance() {
        // 计划 5 天 (9-7 到 9-11)，实际 7 天 (9-7 到 9-15 跨周末: 5+2=7)，偏差 (7-5)/5*100 = 40.00%
        DurationMetrics metrics = DevelopmentCalendarService.measure(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 15),
                defaultCalendar
        );
        assertThat(metrics.status()).isEqualTo("available");
        assertThat(metrics.plannedDays()).isEqualTo(5L);
        assertThat(metrics.actualDays()).isEqualTo(7L);
        assertThat(metrics.variancePercent()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("measure: 实际为0且计划非零时偏差计算为 -100.00%")
    void measure_actualZeroPlannedNonZero() {
        // 计划 2 天 (9-7 到 9-8)，实际 0 天 (9-12 到 9-13 周末)，偏差 (0-2)/2*100 = -100.00%
        DurationMetrics metrics = DevelopmentCalendarService.measure(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13),
                defaultCalendar
        );
        assertThat(metrics.status()).isEqualTo("available");
        assertThat(metrics.plannedDays()).isEqualTo(2L);
        assertThat(metrics.actualDays()).isZero();
        assertThat(metrics.variancePercent()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    @DisplayName("measure: 缺失日期或计划为0天时状态为 unavailable 且偏差为 null")
    void measure_missingDatesOrZeroPlanned() {
        // 缺失日期（计划未排）
        DurationMetrics missingDate = DevelopmentCalendarService.measure(
                null, LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11),
                defaultCalendar
        );
        assertThat(missingDate.status()).isEqualTo("unavailable");
        assertNull(missingDate.variancePercent());

        // 计划为 0 天 (计划落在周末 9-12 到 9-13)
        DurationMetrics zeroPlanned = DevelopmentCalendarService.measure(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11),
                defaultCalendar
        );
        assertThat(zeroPlanned.status()).isEqualTo("unavailable");
        assertThat(zeroPlanned.plannedDays()).isZero();
        assertNull(zeroPlanned.variancePercent());
    }

    @Test
    @DisplayName("measure: 稳定64位SHA256摘要，配置顺序无关且语义敏感")
    void measure_stableSha256Digest() {
        var cal1 = new CalendarDefinition(
                List.of(1, 2, 3, 4, 5),
                List.of(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19)),
                List.of(LocalDate.of(2026, 9, 10))
        );
        var cal2Reordered = new CalendarDefinition(
                List.of(5, 4, 3, 2, 1),
                List.of(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 12)),
                List.of(LocalDate.of(2026, 9, 10))
        );
        var cal3Modified = new CalendarDefinition(
                List.of(1, 2, 3, 4, 5),
                List.of(LocalDate.of(2026, 9, 12)),
                List.of(LocalDate.of(2026, 9, 10))
        );

        var m1 = DevelopmentCalendarService.measure(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11), cal1);
        var m2 = DevelopmentCalendarService.measure(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11), cal2Reordered);
        var m3 = DevelopmentCalendarService.measure(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11), cal3Modified);

        assertThat(m1.calendarVersion()).matches("^[a-fA-F0-9]{64}$");
        assertThat(m1.calendarVersion()).isEqualTo(m2.calendarVersion());
        assertThat(m1.calendarVersion()).isNotEqualTo(m3.calendarVersion());
    }
}

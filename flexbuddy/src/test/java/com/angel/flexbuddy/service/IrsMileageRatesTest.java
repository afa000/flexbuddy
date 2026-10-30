package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class IrsMileageRatesTest {
    @Test
    void theRateChangesOnTheDayTheIrsSetsIt() {
        assertThat(IrsMileageRates.rateOn(LocalDate.of(2025, 12, 31))).isEqualByComparingTo("0.700");
        assertThat(IrsMileageRates.rateOn(LocalDate.of(2026, 1, 1))).isEqualByComparingTo("0.725");
        assertThat(IrsMileageRates.rateOn(LocalDate.of(2026, 6, 30))).isEqualByComparingTo("0.725");
        assertThat(IrsMileageRates.rateOn(LocalDate.of(2026, 7, 1))).isEqualByComparingTo("0.760");
        assertThat(IrsMileageRates.rateOn(LocalDate.of(2030, 1, 1))).isEqualByComparingTo("0.760");
    }

    @Test
    void datesBeforeTheTableUseTheEarliestRate() {
        assertThat(IrsMileageRates.rateOn(LocalDate.of(2023, 12, 31))).isEqualByComparingTo("0.670");
        assertThat(IrsMileageRates.rateOn(LocalDate.of(2019, 5, 5))).isEqualByComparingTo("0.670");
    }

    @Test
    void sinceIsTheDateOfTheEntryInEffect() {
        assertThat(IrsMileageRates.sinceOn(LocalDate.of(2026, 10, 30))).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(IrsMileageRates.sinceOn(LocalDate.of(2025, 6, 1))).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void aRangeReportsEveryRateInsideIt() {
        assertThat(IrsMileageRates.ratesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)))
                .containsExactly(new BigDecimal("0.725"));
        assertThat(IrsMileageRates.ratesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .containsExactly(new BigDecimal("0.725"), new BigDecimal("0.760"));
        assertThat(MileageRates.irs().singleRate(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isNull();
        assertThat(MileageRates.fixed(new BigDecimal("0.650")).singleRate(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31))).isEqualByComparingTo("0.650");
    }
}

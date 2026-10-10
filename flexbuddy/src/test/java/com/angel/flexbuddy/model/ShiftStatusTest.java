package com.angel.flexbuddy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.i18n.Messages;

class ShiftStatusTest {

    /** The English text of the problem with these values, or null when they are valid. */
    private static String problem(ShiftStatus status, BigDecimal basePay, BigDecimal tips, BigDecimal miles) {
        String key = status.problemKey(basePay, tips, miles);
        return key == null ? null : Messages.english(key);
    }

    @Test
    void parseSet_acceptsACommaSeparatedListAllOrTheDefault() {
        assertThat(ShiftStatus.parseSet(null, ShiftStatus.HISTORY)).isEqualTo(ShiftStatus.HISTORY);
        assertThat(ShiftStatus.parseSet(" ", ShiftStatus.EARNINGS)).isEqualTo(ShiftStatus.EARNINGS);
        assertThat(ShiftStatus.parseSet(" all ", ShiftStatus.HISTORY)).isEqualTo(ShiftStatus.ALL);
        assertThat(ShiftStatus.parseSet("scheduled, Completed", ShiftStatus.HISTORY))
                .isEqualTo(Set.of(ShiftStatus.SCHEDULED, ShiftStatus.COMPLETED));
    }

    @Test
    void parseSet_rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> ShiftStatus.parseSet("completed,worked", ShiftStatus.HISTORY))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessage("Unknown status: worked");
    }

    @Test
    void validate_appliesTheRulesForEachStatus() {
        assertThat(problem(ShiftStatus.SCHEDULED, money("84.00"), BigDecimal.ZERO, null)).isNull();
        assertThat(problem(ShiftStatus.SCHEDULED, BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isEqualTo("A scheduled shift needs the offered pay.");
        assertThat(problem(ShiftStatus.SCHEDULED, money("84.00"), money("5.00"), null))
                .isEqualTo("A scheduled shift cannot have tips yet.");
        assertThat(problem(ShiftStatus.SCHEDULED, money("84.00"), BigDecimal.ZERO, money("12.0")))
                .isEqualTo("A scheduled shift cannot have miles yet.");
        assertThat(problem(ShiftStatus.COMPLETED, money("84.00"), money("10.00"), money("30.0"))).isNull();
        assertThat(problem(ShiftStatus.COMPLETED, BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isEqualTo("A completed shift needs base pay greater than 0.");
        assertThat(problem(ShiftStatus.CANCELLED, BigDecimal.ZERO, BigDecimal.ZERO, money("20.0"))).isNull();
        assertThat(problem(ShiftStatus.CANCELLED, money("18.00"), money("2.00"), null))
                .isEqualTo("A cancelled shift cannot have tips.");
        assertThat(problem(ShiftStatus.FORFEITED, BigDecimal.ZERO, BigDecimal.ZERO, null)).isNull();
        assertThat(problem(ShiftStatus.FORFEITED, money("-1.00"), BigDecimal.ZERO, null))
                .isEqualTo("A forfeited shift needs base pay of 0 or more.");
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}

package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.dto.BlockEvaluationResponse;
import com.angel.flexbuddy.importcore.figures.Block;
import com.angel.flexbuddy.importcore.figures.BlockOffers;
import com.angel.flexbuddy.importcore.figures.BlockStatus;
import com.angel.flexbuddy.importcore.figures.CostCategory;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.VehicleCostMethod;

/**
 * The server maps its enums to the shared library's by name. A constant added on one side only would make a block,
 * expense or evaluation fail to map, so these fail first.
 */
class FigureInputsTest {

    @Test
    void everyServerEnumHasALibraryCounterpartWithTheSameNames() {
        assertSameNames(ShiftStatus.values(), BlockStatus.values());
        assertSameNames(ExpenseCategory.values(), CostCategory.values());
        assertSameNames(VehicleCostMethod.values(), com.angel.flexbuddy.importcore.figures.VehicleCostMethod.values());
        assertSameNames(BlockEvaluationResponse.Basis.values(), BlockOffers.Basis.values());
        assertSameNames(BlockEvaluationResponse.Verdict.values(), BlockOffers.Verdict.values());
    }

    @Test
    void aShiftMapsToTheSameValuesAndAMissingStatusStaysMissing() {
        Shift shift = new Shift(7L, "VEA7", LocalDate.of(2026, 9, 14), LocalTime.of(22, 0), LocalTime.of(1, 30),
                new BigDecimal("70.00"), new BigDecimal("4.00"));
        shift.setMiles(new BigDecimal("35.0"));
        shift.setStatus(null);

        Block block = FigureInputs.block(shift);

        assertThat(block.status()).isNull();
        assertThat(block.effectiveStatus()).isEqualTo(BlockStatus.COMPLETED);
        assertThat(block.station()).isEqualTo("VEA7");
        assertThat(block.workedMinutes()).isEqualTo(shift.getWorkedMinutes()).isEqualTo(210);
        assertThat(block.earnedPay()).isEqualByComparingTo(shift.getEarnedPay());
        assertThat(block.countedMiles()).isEqualByComparingTo(shift.getCountedMiles());
    }

    @Test
    void categoriesMapBothWays() {
        for (ExpenseCategory category : ExpenseCategory.values()) {
            assertThat(FigureInputs.category(FigureInputs.category(category))).isEqualTo(category);
        }
        assertThat(FigureInputs.status(null)).isNull();
        assertThat(FigureInputs.method(null)).isNull();
    }

    private static void assertSameNames(Enum<?>[] server, Enum<?>[] library) {
        assertThat(Arrays.stream(library).map(Enum::name))
                .as("library counterpart of %s", server[0].getDeclaringClass().getSimpleName())
                .containsExactly(Arrays.stream(server).map(Enum::name).toArray(String[]::new));
    }
}

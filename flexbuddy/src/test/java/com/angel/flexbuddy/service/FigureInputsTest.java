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
import com.angel.flexbuddy.importcore.figures.EarningsReport;
import com.angel.flexbuddy.importcore.figures.ReportBlock;
import com.angel.flexbuddy.importcore.figures.ReportCost;
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
        assertSameNames(com.angel.flexbuddy.dto.GroupBy.values(), EarningsReport.GroupBy.values());
    }

    @Test
    void aShiftsRouteFiguresAreTheLibrarysAndAnExpenseCarriesItsLink() {
        Shift shift = new Shift(7L, "VEA7", LocalDate.of(2026, 9, 14), LocalTime.of(22, 0), LocalTime.of(1, 30),
                new BigDecimal("70.00"), new BigDecimal("4.00"));
        shift.setActualStart(LocalTime.of(22, 5));
        shift.setActualEnd(LocalTime.of(1, 0));
        shift.setStops(35);
        shift.setPackages(null);
        shift.setReturns(2);

        ReportBlock block = FigureInputs.reportBlock(shift);

        assertThat(block.id()).isEqualTo(7L);
        assertThat(block.actualMinutes()).isEqualTo(shift.getActualMinutes()).isEqualTo(175);
        assertThat(block.clockedMinutes()).isEqualTo(shift.getClockedMinutes()).isEqualTo(175);
        assertThat(block.finishedEarlyMinutes()).isEqualTo(shift.getFinishedEarlyMinutes()).isEqualTo(35);
        assertThat(block.minutesPerStop()).isEqualByComparingTo(shift.getMinutesPerStop()).isEqualByComparingTo("5.0");
        assertThat(block.returnsBase()).isEqualTo(shift.getReturnsBase()).isEqualTo(35);
        assertThat(block.returnsRate()).isEqualByComparingTo(shift.getReturnsRate()).isEqualByComparingTo("5.7");

        com.angel.flexbuddy.model.Expense linked = new com.angel.flexbuddy.model.Expense();
        linked.setShift(shift);
        linked.setCategory(ExpenseCategory.TOLL);
        linked.setAmount(new BigDecimal("3.00"));
        com.angel.flexbuddy.model.Expense unlinked = new com.angel.flexbuddy.model.Expense();
        unlinked.setCategory(ExpenseCategory.FUEL);
        java.util.Map<Long, ExpenseService.LinkedShift> links = java.util.Map.of(7L,
                new ExpenseService.LinkedShift(7L, "VEA7", ShiftStatus.SCHEDULED, false));

        ReportCost cost = FigureInputs.reportCost(linked, links);
        assertThat(cost.linkedBlockId()).isEqualTo(7L);
        assertThat(cost.linkedStatus()).isEqualTo(BlockStatus.SCHEDULED);
        assertThat(cost.waits()).isTrue();
        assertThat(ExpenseService.countedStation(linked, links)).isEqualTo("VEA7");
        assertThat(FigureInputs.reportCost(unlinked, links).linked()).isFalse();
        // A link that can't be found counts as unlinked, as before.
        assertThat(FigureInputs.reportCost(linked, java.util.Map.of()).linked()).isFalse();
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

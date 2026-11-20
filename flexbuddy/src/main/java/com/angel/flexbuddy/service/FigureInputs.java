package com.angel.flexbuddy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.importcore.figures.Block;
import com.angel.flexbuddy.importcore.figures.BlockStatus;
import com.angel.flexbuddy.importcore.figures.Cost;
import com.angel.flexbuddy.importcore.figures.CostCategory;
import com.angel.flexbuddy.importcore.figures.EarningsReport;
import com.angel.flexbuddy.importcore.figures.ReportBlock;
import com.angel.flexbuddy.importcore.figures.ReportCost;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.VehicleCostMethod;

/**
 * Maps the server's entities and enums to the shared library's plain inputs. The enums map by name;
 * {@code FigureInputsTest} fails if the two sides stop matching.
 */
public final class FigureInputs {

    private FigureInputs() {
    }

    public static BlockStatus status(ShiftStatus status) {
        return status == null ? null : BlockStatus.valueOf(status.name());
    }

    public static CostCategory category(ExpenseCategory category) {
        return category == null ? null : CostCategory.valueOf(category.name());
    }

    public static ExpenseCategory category(CostCategory category) {
        return category == null ? null : ExpenseCategory.valueOf(category.name());
    }

    public static com.angel.flexbuddy.importcore.figures.VehicleCostMethod method(VehicleCostMethod method) {
        return method == null ? null : com.angel.flexbuddy.importcore.figures.VehicleCostMethod.valueOf(method.name());
    }

    public static Block block(Shift shift) {
        return new Block(status(shift.getStatus()), shift.getStation(), shift.getDate(), shift.getStartTime(),
                shift.getEndTime(), shift.getBasePay(), shift.getTips(), shift.getMiles());
    }

    public static List<Block> blocks(List<Shift> shifts) {
        List<Block> blocks = new ArrayList<>(shifts.size());
        for (Shift shift : shifts) blocks.add(block(shift));
        return blocks;
    }

    public static Cost cost(Expense expense) {
        return new Cost(category(expense.getCategory()), expense.getAmount(), expense.getDate());
    }

    public static List<Cost> costs(List<Expense> expenses) {
        List<Cost> costs = new ArrayList<>(expenses.size());
        for (Expense expense : expenses) costs.add(cost(expense));
        return costs;
    }

    public static ReportBlock reportBlock(Shift shift) {
        return new ReportBlock(shift.getId(), block(shift), shift.getActualMinutes(), shift.getStops(),
                shift.getPackages(), shift.getReturns());
    }

    public static List<ReportBlock> reportBlocks(List<Shift> shifts) {
        List<ReportBlock> blocks = new ArrayList<>(shifts.size());
        for (Shift shift : shifts) blocks.add(reportBlock(shift));
        return blocks;
    }

    /** An expense with the block it is linked to, from {@link ExpenseService#linkedShifts}; unlinked when not found. */
    public static ReportCost reportCost(Expense expense, Map<Long, ExpenseService.LinkedShift> links) {
        ExpenseService.LinkedShift link = ExpenseService.linkOf(expense, links);
        return link == null ? ReportCost.unlinked(cost(expense))
                : new ReportCost(cost(expense), link.id(), status(link.status()), link.deleted(), link.station());
    }

    public static List<ReportCost> reportCosts(List<Expense> expenses, Map<Long, ExpenseService.LinkedShift> links) {
        List<ReportCost> costs = new ArrayList<>(expenses.size());
        for (Expense expense : expenses) costs.add(reportCost(expense, links));
        return costs;
    }

    public static EarningsReport.GroupBy groupBy(GroupBy groupBy) {
        return groupBy == null ? null : EarningsReport.GroupBy.valueOf(groupBy.name());
    }
}

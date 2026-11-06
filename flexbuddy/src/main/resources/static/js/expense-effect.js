// What saving an expense does to estimated net, by category and vehicle cost method, as NetEarningsCalculator works
// it out: with standard mileage the rate already covers fuel and maintenance; tolls, parking and other costs always
// come off net, and with actual expenses every category does. NetEarningsCalculatorTest checks the same table.
// An expense linked to a block that is still scheduled is left out of net until the block happens
// (ShiftReportService.reportExpenses), unless the mileage rate covers it anyway.
(() => {
    const CATEGORIES = ['FUEL', 'TOLL', 'PARKING', 'MAINTENANCE', 'OTHER'];
    const COVERED_BY_MILEAGE = ['FUEL', 'MAINTENANCE'];

    /** The message key for the effect, or undefined while the method or the category isn't known. */
    function effectKey(category, method, linkedToScheduled = false) {
        if (!CATEGORIES.includes(category)) return undefined;
        if (method === 'STANDARD_MILEAGE' && COVERED_BY_MILEAGE.includes(category)) return 'js.expenseEffect.coveredByMileage';
        if (method !== 'STANDARD_MILEAGE' && method !== 'ACTUAL_EXPENSES') return undefined;
        return linkedToScheduled ? 'js.expenseEffect.afterBlock' : 'js.expenseEffect.lowersNet';
    }

    window.flexbuddyExpenseEffect = {effectKey};
})();

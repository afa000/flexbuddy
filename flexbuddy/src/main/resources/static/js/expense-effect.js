// What saving an expense does to estimated net, by category and vehicle cost method, as NetEarningsCalculator works
// it out: with standard mileage the rate already covers fuel and maintenance; tolls, parking and other costs always
// come off net, and with actual expenses every category does. NetEarningsCalculatorTest checks the same table.
(() => {
    const CATEGORIES = ['FUEL', 'TOLL', 'PARKING', 'MAINTENANCE', 'OTHER'];
    const COVERED_BY_MILEAGE = ['FUEL', 'MAINTENANCE'];

    /** The message key for the effect, or undefined while the method or the category isn't known. */
    function effectKey(category, method) {
        if (!CATEGORIES.includes(category)) return undefined;
        if (method === 'STANDARD_MILEAGE') {
            return COVERED_BY_MILEAGE.includes(category) ? 'js.expenseEffect.coveredByMileage' : 'js.expenseEffect.lowersNet';
        }
        return method === 'ACTUAL_EXPENSES' ? 'js.expenseEffect.lowersNet' : undefined;
    }

    window.flexbuddyExpenseEffect = {effectKey};
})();

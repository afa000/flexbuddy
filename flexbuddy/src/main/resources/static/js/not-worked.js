// Moving a block that was marked completed by mistake back to scheduled clears what only a worked block can hold.
// The confirmation names exactly those saved values, so nothing disappears without the driver seeing it first.
(() => {
    const present = value => value !== null && value !== undefined && value !== '';

    /**
     * The saved values the correction clears, as [key, ...args] message entries in the order the dialog shows them.
     * Tips of zero aren't listed because nothing changes, but zero miles are: the driver recorded them.
     * `format` supplies money and number formatting so the page and the tests can each bring their own.
     */
    function clearedItems(shift, format) {
        const details = shift?.details ?? {};
        const items = [];
        if (Number(shift?.tips) > 0) items.push(['js.notWorked.tips', format.money(shift.tips)]);
        if (present(shift?.miles)) items.push(['js.notWorked.miles', format.number(shift.miles)]);
        if (present(details.odometerStart) || present(details.odometerEnd)) items.push(['js.notWorked.odometer']);
        if (present(details.actualStart) || present(details.actualEnd)) items.push(['js.notWorked.actualTimes']);
        if ([details.stops, details.packages, details.returns].some(present)) items.push(['js.notWorked.route']);
        return items;
    }

    /** Only a saved completed block offers the correction. */
    function canMarkNotWorked(mode, savedStatus) {
        return mode === 'edit' && savedStatus === 'COMPLETED';
    }

    window.flexbuddyNotWorked = {clearedItems, canMarkNotWorked};
})();

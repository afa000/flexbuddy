// The import review never picks Completed or Scheduled for the driver: a screenshot of an accepted block doesn't show
// whether it was worked. The server's suggestion becomes a hint under the Status choice instead.
(() => {
    /** The message key of the hint for a read screenshot, or undefined before one is read. */
    function hintKey(candidate) {
        if (!candidate) return undefined;
        if (candidate.date?.value == null) return 'js.app.importStatusNoDate';
        return candidate.suggestedStatus === 'SCHEDULED'
            ? 'js.app.importStatusLooksScheduled'
            : 'js.app.importStatusLooksPast';
    }

    /** The Status choice a newly read or reset import starts on: none. */
    const INITIAL_STATUS = '';

    window.flexbuddyImportStatus = {hintKey, INITIAL_STATUS};
})();

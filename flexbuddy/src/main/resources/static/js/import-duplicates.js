// Chooses which notice the import review form shows for the matches the server found. A block that is already logged
// (EXACT or LIKELY) wins over "complete this scheduled shift", and an edit to the read values drops it, because the
// match was for the values that were read.
(() => {
    const LOGGED_KINDS = ['EXACT', 'LIKELY'];

    /** The already-logged match to warn about, or undefined once the driver has edited the values it was found for. */
    function loggedMatch(candidate, edited) {
        if (edited) return undefined;
        return candidate?.duplicates?.find(duplicate => LOGGED_KINDS.includes(duplicate.kind));
    }

    /** {logged, schedule}: at most one is set; the schedule match only applies to a completed import. */
    function choose(candidate, status, edited) {
        const logged = loggedMatch(candidate, edited);
        if (logged) return {logged, schedule: undefined};
        const schedule = status === 'COMPLETED'
            ? candidate?.duplicates?.find(duplicate => duplicate.kind === 'SCHEDULED_MATCH')
            : undefined;
        return {logged: undefined, schedule};
    }

    window.flexbuddyImportDuplicates = {choose};
})();

// Several screenshots picked at once: one row per file, read one at a time and reviewed one at a time. Nothing here
// talks to the server; app.js sends each read and each save, and these functions decide what every row says and which
// file comes next. A file is only ever saved by the driver pressing a save button on its own review.
//
// Item states: waiting, reading, ready (read, not saved), failed (couldn't be read), invalid (not a PNG/JPEG or too
// big; never sent), repeat (same bytes as an earlier file; never read), saved, skipped, stopped.
(() => {
    const MAX_FILES = 10;
    const MAX_BYTES = 5 * 1024 * 1024;
    const TYPES = ['image/png', 'image/jpeg'];
    // ScheduledShiftMatcher.MINIMUM_OVERLAP_MINUTES: the same rule the server uses for blocks already saved.
    const MINIMUM_OVERLAP_MINUTES = 30;
    const UNFINISHED = ['waiting', 'reading', 'ready'];
    const UNSAVED = ['waiting', 'reading', 'ready', 'failed'];

    /** The message key for a file the server would refuse anyway, or undefined for a PNG/JPEG of 5 MB or less. */
    function fileProblemKey(file) {
        if (!TYPES.includes(file.type)) return 'js.app.choosePngJpeg';
        if (file.size > MAX_BYTES) return 'js.app.screenshotTooBig';
        return undefined;
    }

    /**
     * One item per file, in the order picked, or {problemKey} for more than ten files. Each item gets its own request
     * ids up front, so every retry of its save is the same request to the server. `signatures[i]` identifies file i's
     * bytes; a file whose bytes match an earlier one is a repeat and is never read.
     */
    function createItems(files, signatures, makeId) {
        if (files.length > MAX_FILES) return {problemKey: 'js.importQueue.tooMany'};
        const items = [];
        files.forEach((file, index) => {
            const item = {
                number: index + 1, file, name: file.name, size: file.size, state: 'waiting',
                requestId: makeId(), statusRequestId: makeId(), signature: signatures[index]
            };
            const problemKey = fileProblemKey(file);
            const original = problemKey || !item.signature ? undefined
                : items.find(other => other.state === 'waiting' && other.signature === item.signature);
            if (problemKey) Object.assign(item, {state: 'invalid', problemKey});
            else if (original) Object.assign(item, {state: 'repeat', repeatOf: original.number});
            items.push(item);
        });
        return {items};
    }

    function nextToRead(items) {
        return items.find(item => item.state === 'waiting');
    }

    /** The next file read and not yet saved after `after` (a file number), wrapping round to the first one. */
    function nextToReview(items, after = 0) {
        const ready = items.filter(item => item.state === 'ready');
        return ready.find(item => item.number > after) ?? ready[0];
    }

    /**
     * What a failed read means for the batch. `status` is the HTTP status, or 0 when no answer came (offline, a lost
     * connection, the time limit). The server's refusal of this one screenshot is final and the next file is read; a
     * busy server or no answer can be tried again, and stops the reading until the driver says to go on.
     */
    function readFailure(status) {
        if ([400, 413, 415, 422].includes(status)) return {retry: false, pause: false};
        return {retry: true, pause: status === 0 || status === 503 || status === 429};
    }

    /** True once no file is left to read or review; failed rows can still be tried again. */
    function isFinished(items) {
        return !items.some(item => UNFINISHED.includes(item.state));
    }

    /** True while some file could still be saved, so leaving the page would lose it. */
    function hasUnsaved(items) {
        return items.some(item => UNSAVED.includes(item.state));
    }

    /** Stop importing: every file not saved yet is marked so, and the saved ones stay as they are. */
    function stop(items) {
        items.forEach(item => {
            if (UNSAVED.includes(item.state)) item.state = 'stopped';
        });
    }

    function savedCount(items) {
        return items.filter(item => item.state === 'saved').length;
    }

    function minutesOf(time) {
        const [hours, minutes] = String(time).split(':').map(Number);
        return hours * 60 + minutes;
    }

    /** The block's start and end in minutes counted from a fixed day; an end before the start is the next morning. */
    function span(block) {
        const [year, month, day] = String(block.date).split('-').map(Number);
        const dayStart = Date.UTC(year, month - 1, day) / 60000; // utc-day: only differences between days are used
        const start = dayStart + minutesOf(block.startTime);
        let end = dayStart + minutesOf(block.endTime);
        if (end < start) end += 24 * 60;
        return {start, end};
    }

    /** ScheduledShiftMatcher.stationMatches: a blank station matches any; otherwise equal, or one contains the other. */
    function stationMatches(candidate, saved) {
        if (!candidate || !candidate.trim()) return true;
        if (!saved) return false;
        const imported = candidate.trim().toLowerCase();
        const logged = saved.trim().toLowerCase();
        return imported === logged || imported.includes(logged) || logged.includes(imported);
    }

    function overlaps(first, second) {
        const a = span(first);
        const b = span(second);
        return Math.min(a.end, b.end) - Math.max(a.start, b.start) >= MINIMUM_OVERLAP_MINUTES;
    }

    function complete(values) {
        return Boolean(values && values.date && values.startTime && values.endTime);
    }

    /**
     * The block another file of this batch already saved that `values` (the review form's current station, date,
     * times and status) is the same block as. The server's own notices can't know about it when this file was read
     * before that save. A block saved as scheduled that this file marks Completed is offered for completion, as the
     * server does; any other is "already added". Undefined when there is none.
     */
    function savedMatch(values, items, current) {
        if (!complete(values)) return undefined;
        const hit = items.find(item => item !== current && item.state === 'saved' && item.saved
            && stationMatches(values.station, item.saved.station) && overlaps(values, item.saved));
        if (!hit) return undefined;
        const completes = (hit.saved.nowStatus ?? hit.saved.status) === 'SCHEDULED' && values.status === 'COMPLETED';
        return {
            kind: completes ? 'SCHEDULED_MATCH' : 'EXACT',
            shiftId: hit.saved.shiftId ?? '',
            number: hit.number,
            message: t(completes ? 'js.importQueue.scheduledInBatch' : 'js.importQueue.addedInBatch', hit.number)
        };
    }

    /**
     * After a scheduled block is completed, the rows that added it still say they added it as scheduled, but the
     * batch check now treats the block as completed, so no other file offers to complete it again.
     */
    function noteCompleted(items, shiftId) {
        items.forEach(item => {
            if (item.saved && item.saved.shiftId === shiftId) item.saved.nowStatus = 'COMPLETED';
        });
    }

    function sameNumber(first, second) {
        if (first == null || second == null) return first == null && second == null;
        return Number(first) === Number(second);
    }

    /**
     * True when the block the server answered with holds what was sent. A repeated save is answered with the block
     * the first copy made, so after a lost answer and an edit the two differ and the driver is told.
     */
    function sameAsSent(sent, shift) {
        const time = value => (value ? String(value).slice(0, 5) : '');
        return String(sent.station).trim().toLowerCase() === String(shift.station ?? '').trim().toLowerCase()
            && sent.date === shift.date && sent.startTime === time(shift.startTime) && sent.endTime === time(shift.endTime)
            && sent.status === shift.status && sameNumber(sent.basePay, shift.basePay) && sameNumber(sent.tips, shift.tips)
            && sameNumber(sent.miles, shift.miles);
    }

    /** The line above the list: which file is under review, why reading stopped, or how many were added. */
    function headline(items, current, paused) {
        if (isFinished(items)) return t('js.importQueue.finished', savedCount(items), items.length);
        const shown = current ?? items.find(item => item.state === 'reading')?.number;
        if (shown !== undefined) return t('js.importQueue.progress', shown, items.length);
        if (paused) return t('js.importQueue.paused');
        return '';
    }

    /** What one row says about its file. `formatDate` turns 2026-11-03 into the driver's short date. */
    function rowText(item, current, formatDate) {
        switch (item.state) {
            case 'waiting': return t('js.importQueue.waiting');
            case 'reading': return t('js.importQueue.reading');
            case 'ready': return t(item.number === current ? 'js.importQueue.reviewing' : 'js.importQueue.ready');
            case 'invalid': return t(item.problemKey);
            case 'failed': return item.error || t('js.app.screenshotNotProcessed');
            case 'repeat': return t('js.importQueue.repeat', item.repeatOf);
            case 'skipped': return t(item.uncertain ? 'js.importQueue.maybeSaved' : 'js.importQueue.skipped');
            case 'stopped': return t(item.uncertain ? 'js.importQueue.maybeSaved' : 'js.importQueue.stopped');
            case 'saved': {
                const saved = item.saved;
                const key = saved.kind === 'completed' ? 'js.importQueue.completedScheduled'
                    : saved.status === 'SCHEDULED' ? 'js.importQueue.addedScheduled' : 'js.importQueue.addedCompleted';
                const parts = [t(key), saved.station, saved.date ? formatDate(saved.date) : ''].filter(Boolean);
                const line = parts.join(' · ');
                return saved.changed ? `${line}. ${t('js.importQueue.savedBeforeShort')}` : line;
            }
            default: return '';
        }
    }

    /** The buttons a row offers: Review for a file waiting its turn, Try again for a read that can be retried, Open for a saved block. */
    function rowActions(item, current) {
        if (item.state === 'ready' && item.number !== current) return ['review'];
        if (item.state === 'failed' && item.retry) return ['retry'];
        if (item.state === 'saved' && item.saved?.shiftId != null) return ['open'];
        return [];
    }

    window.flexbuddyImportQueue = {
        MAX_FILES, fileProblemKey, createItems, nextToRead, nextToReview, readFailure, isFinished, hasUnsaved, stop,
        savedCount, savedMatch, noteCompleted, sameAsSent, headline, rowText, rowActions
    };
})();

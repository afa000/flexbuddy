// Translations: the page's messages arrive as JSON in #flexbuddyMessages, rendered for the page's language. t() looks
// a key up and fills {0}, {1}, …; tn() picks the .one or .other form for a count. The helpers are pure apart from
// reading that JSON once, so they run under `node --test`.
(() => {
    let messages = window.flexbuddyMessages || {};
    try {
        const node = typeof document.getElementById === 'function' && document.getElementById('flexbuddyMessages');
        if (node) messages = JSON.parse(node.textContent || '{}');
    } catch {
        // A broken bundle leaves keys visible rather than breaking the page.
    }

    /** Fills {0}, {1}, … like Java's MessageFormat, where '' stands for one apostrophe when arguments are given. */
    function format(text, args) {
        if (!args.length) return text;
        return text.replace(/''/g, '\u0000').replace(/\{(\d+)\}/g, (match, index) => (index < args.length ? String(args[index]) : match))
            .replace(/\u0000/g, "'");
    }

    function t(key, ...args) {
        const text = messages[key];
        return text === undefined ? key : format(text, args);
    }

    /** "1 block" / "3 blocks": key.one and key.other, with the count as {0}. */
    function tn(count, key, ...args) {
        return t(`${key}.${count === 1 ? 'one' : 'other'}`, count, ...args);
    }

    /** The BCP 47 tag for dates and numbers: the page's language, US region, e.g. "en-US" or "es-US". */
    function appLocale() {
        const lang = (typeof document.documentElement?.lang === 'string' && document.documentElement.lang) || 'en';
        return `${lang.split('-')[0]}-US`;
    }

    window.flexbuddyI18n = {t, tn, appLocale, format};
    window.t = t;
    window.tn = tn;
    window.appLocale = appLocale;
})();

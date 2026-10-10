// Two-step sign-in settings: the copy and download buttons for recovery codes and the setup key, and the theme button
// this page shares with Account. The pure helpers run under `node --test`.
(() => {
    /** The text saved to the recovery file, dated with the device's own calendar day. */
    function recoveryFileText(codes, dateIso) {
        return [
            'FlexBuddy recovery codes',
            `Made ${dateIso}`,
            '',
            'Each code works once. Keep this file somewhere safe.',
            '',
            ...codes,
            ''
        ].join('\n');
    }

    /** Today's date on this device as YYYY-MM-DD, built from local parts so the evening is not tomorrow. */
    function localIsoDate(date = new Date()) {
        const pad = value => String(value).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    window.flexbuddyTwoFactor = {recoveryFileText, localIsoDate};

    // Everything below needs a real page.
    if (typeof document.addEventListener !== 'function' || typeof document.querySelector !== 'function') return;

    function flash(button, label) {
        const original = button.textContent;
        button.textContent = label;
        window.setTimeout(() => { button.textContent = original; }, 2000);
    }

    async function copy(text, button) {
        try {
            await navigator.clipboard.writeText(text);
            flash(button, 'Copied');
        } catch {
            flash(button, 'Copy failed');
        }
    }

    const codes = [...document.querySelectorAll('[data-recovery-code]')].map(element => element.textContent.trim());
    const copyAll = document.querySelector('#copyRecoveryCodes');
    if (copyAll) copyAll.addEventListener('click', () => copy(codes.join('\n'), copyAll));
    const download = document.querySelector('#downloadRecoveryCodes');
    if (download && codes.length) {
        download.href = `data:text/plain;charset=utf-8,${encodeURIComponent(recoveryFileText(codes, localIsoDate()))}`;
    }
    const copyKey = document.querySelector('#copySetupKey');
    if (copyKey) {
        copyKey.addEventListener('click', () => {
            const key = document.querySelector(`#${copyKey.dataset.copyFrom}`).textContent.replace(/\s+/g, '');
            copy(key, copyKey);
        });
    }

    const themeButton = document.querySelector('#themeToggleButton');
    if (themeButton) {
        const label = theme => (theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode');
        const show = theme => {
            themeButton.setAttribute('aria-label', label(theme));
            themeButton.title = label(theme);
        };
        themeButton.addEventListener('click', () => {
            const theme = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
            document.documentElement.dataset.theme = theme;
            try { localStorage.setItem('flexbuddy-theme', theme); } catch { /* the choice just is not remembered */ }
            show(theme);
            window.flexbuddyPwa?.applyThemeColor(theme);
        });
        show(document.documentElement.dataset.theme);
    }
})();

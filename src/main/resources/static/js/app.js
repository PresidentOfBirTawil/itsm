/*
 * Small helpers used on many pages. Kept out of the pages themselves (no
 * inline onclick or onsubmit), so the Content Security Policy can forbid
 * inline scripts.
 *
 *   <form data-confirm="Really?">        asks before the form is sent
 *   <button data-fill-target="#note"     copies a text into a text box
 *           data-fill-text="...">
 *   <button data-theme-toggle>            switches between dark and light (remembered)
 *   <kbd data-shortcut-mod>               shows ⌘ on a Mac and Ctrl elsewhere
 *   <button data-desktop-alerts>          asks the browser for permission to show desktop alerts
 *   <button data-install-app>             installs Quell as an app (browsers that offer it)
 *
 * It also registers the service worker (static/sw.js) that makes Quell an
 * installable app with an offline page.
 */
(function () {
    'use strict';

    document.addEventListener('submit', function (event) {
        var question = event.target.getAttribute && event.target.getAttribute('data-confirm');
        if (question && !window.confirm(question)) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    // The dark/light switch in the navbar.
    function showThemeIcon() {
        var dark = document.documentElement.getAttribute('data-bs-theme') !== 'light';
        document.querySelectorAll('[data-theme-toggle] i').forEach(function (icon) {
            icon.classList.toggle('bi-sun', dark);
            icon.classList.toggle('bi-moon-stars', !dark);
        });
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.title = dark ? 'Switch to light mode' : 'Switch to dark mode';
        });
    }

    window.quellToggleTheme = function () {
        var next = document.documentElement.getAttribute('data-bs-theme') === 'light' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-bs-theme', next);
        try {
            localStorage.setItem('quell-theme', next);
        } catch (e) {
            // Not remembered in a private window; it still switches.
        }
        showThemeIcon();
        document.dispatchEvent(new CustomEvent('quell:theme', { detail: next }));
    };

    document.addEventListener('click', function (event) {
        if (event.target.closest('[data-theme-toggle]')) {
            window.quellToggleTheme();
        }
    });
    showThemeIcon();

    // Shortcut hints: ⌘ on a Mac, Ctrl elsewhere.
    var mac = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);
    document.querySelectorAll('[data-shortcut-mod]').forEach(function (key) {
        key.textContent = mac ? '⌘' : 'Ctrl';
    });

    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-fill-target]');
        if (!button) {
            return;
        }
        var target = document.querySelector(button.getAttribute('data-fill-target'));
        if (target) {
            target.value = button.getAttribute('data-fill-text');
            target.focus();
            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
            target.dispatchEvent(new Event('input', { bubbles: true }));
        }
    });

    // ---- desktop alerts (shown by live.js when a notification arrives in the background) ----

    function showAlertsState() {
        if (!('Notification' in window)) {
            return;
        }
        document.querySelectorAll('[data-desktop-alerts]').forEach(function (button) {
            var label = button.querySelector('span');
            button.classList.remove('d-none');
            button.disabled = Notification.permission !== 'default';
            if (Notification.permission === 'granted') {
                label.textContent = 'Desktop alerts are on';
                button.title = 'You get an alert for new notifications while Quell is in the background.';
            } else if (Notification.permission === 'denied') {
                label.textContent = 'Desktop alerts are blocked';
                button.title = 'Allow notifications for this site in your browser settings to turn them on.';
            } else {
                label.textContent = 'Turn on desktop alerts';
                button.title = 'Get an alert for new notifications, even while Quell is in the background.';
            }
        });
    }

    document.addEventListener('click', function (event) {
        if (!event.target.closest('[data-desktop-alerts]') || !('Notification' in window)) {
            return;
        }
        Notification.requestPermission().then(function (permission) {
            showAlertsState();
            if (permission === 'granted') {
                try {
                    new Notification('Desktop alerts are on', {
                        body: 'You will be told about new notifications while Quell is in the background.',
                        icon: '/img/logo-192.png'
                    });
                } catch (e) {
                    // Phones show alerts through the service worker only; nothing to demonstrate here.
                }
            }
        });
    });
    showAlertsState();

    // ---- the installable app (Progressive Web App) ----

    if ('serviceWorker' in navigator && window.isSecureContext) {
        window.addEventListener('load', function () {
            navigator.serviceWorker.register('/sw.js').catch(function () {
                // Without it Quell works the same, only without the offline page.
            });
        });
    }

    var installPrompt = null;
    window.addEventListener('beforeinstallprompt', function (event) {
        event.preventDefault();                  // offer it in the menu instead of the browser's own bar
        installPrompt = event;
        document.querySelectorAll('[data-install-app]').forEach(function (button) {
            button.classList.remove('d-none');
        });
    });
    window.addEventListener('appinstalled', function () {
        installPrompt = null;
        document.querySelectorAll('[data-install-app]').forEach(function (button) {
            button.classList.add('d-none');
        });
    });
    document.addEventListener('click', function (event) {
        if (event.target.closest('[data-install-app]') && installPrompt) {
            installPrompt.prompt();
        }
    });
})();

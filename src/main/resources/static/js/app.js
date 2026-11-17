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
})();

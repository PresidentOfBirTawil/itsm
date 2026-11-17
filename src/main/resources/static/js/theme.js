/*
 * Dark or light: dark unless this browser chose light before. Runs in <head>,
 * before the page is drawn, so the page never flashes the other theme.
 * The toggle in the navbar (app.js) changes it.
 */
(function () {
    'use strict';
    var theme = 'dark';
    try {
        theme = localStorage.getItem('quell-theme') || 'dark';
    } catch (e) {
        // Private windows may refuse storage: dark it is.
    }
    document.documentElement.setAttribute('data-bs-theme', theme === 'light' ? 'light' : 'dark');
})();

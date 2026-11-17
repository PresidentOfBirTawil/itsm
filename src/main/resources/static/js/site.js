/*
 * The product site: the main download button offers the installer for the
 * visitor's own computer (Windows, Mac or Linux).
 */
(function () {
    'use strict';
    var button = document.querySelector('[data-os-download]');
    if (!button) {
        return;
    }
    var platform = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || navigator.userAgent;
    var label = button.querySelector('span');
    if (/Mac|iPhone|iPad/i.test(platform) && button.dataset.mac) {
        button.href = button.dataset.mac;
        label.textContent = 'Download for Mac';
        button.querySelector('i').className = 'bi bi-apple me-2';
    } else if (/Linux|X11/i.test(platform) && !/Android/i.test(navigator.userAgent) && button.dataset.linux) {
        button.href = button.dataset.linux;
        label.textContent = 'Download for Linux';
    }
})();

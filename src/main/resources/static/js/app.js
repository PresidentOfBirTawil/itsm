/*
 * Small helpers used on many pages. Kept out of the pages themselves (no
 * inline onclick or onsubmit), so the Content Security Policy can forbid
 * inline scripts.
 *
 *   <form data-confirm="Really?">        asks before the form is sent
 *   <button data-fill-target="#note"     copies a text into a text box
 *           data-fill-text="...">
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

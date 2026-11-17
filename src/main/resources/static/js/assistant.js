/*
 * The reporting assistant's chat (templates/employee/assistant.html).
 * Without this script every answer is an ordinary form post and the page
 * reloads. With it, answers are sent in the background: the person's message
 * appears at once, a typing indicator shows while the assistant thinks, and
 * the server's newly rendered chat replaces the old one.
 */
(function () {
    'use strict';

    var chat = document.querySelector('[data-chat]');
    var input = document.getElementById('assistant-text');
    if (!chat || !input || !window.fetch) {
        return;
    }
    var busy = false;

    function log() {
        return document.querySelector('[data-chat-log]');
    }

    function scrollDown() {
        var element = log();
        if (element) {
            element.scrollTop = element.scrollHeight;
        }
    }

    function bubble(text, incoming) {
        var row = document.createElement('div');
        row.className = 'chat-row chat-new ' + (incoming ? 'chat-in' : 'chat-out');
        if (incoming) {
            var avatar = document.querySelector('.chat-avatar');
            if (avatar) {
                row.appendChild(avatar.cloneNode(true));
            }
        }
        var body = document.createElement('div');
        body.className = 'chat-bubble';
        if (text === null) {
            body.classList.add('chat-typing');
            body.setAttribute('aria-label', 'The assistant is typing');
            body.innerHTML = '<span></span><span></span><span></span>';
        } else {
            var span = document.createElement('span');
            span.className = 'chat-text';
            span.textContent = text;
            body.appendChild(span);
        }
        row.appendChild(body);
        log().appendChild(row);
        scrollDown();
        return row;
    }

    function setBusy(value) {
        busy = value;
        document.querySelectorAll('.chat-chip, [data-chat-input] button[type=submit]').forEach(function (button) {
            button.disabled = value;
        });
    }

    /** Swaps in the chat the server rendered; only the messages that were not on screen yet slide in. */
    function replaceChat(html, shown) {
        var next = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-chat]');
        if (!next) {
            throw new Error('Unexpected answer');
        }
        next.querySelectorAll('.chat-row').forEach(function (row, index) {
            if (index >= shown) {
                row.classList.add('chat-new');
            }
        });
        var current = document.querySelector('[data-chat]');
        current.replaceWith(next);
        input.placeholder = next.getAttribute('data-placeholder') || '';
        scrollDown();
        input.focus({ preventScroll: true });
    }

    function send(form) {
        if (busy) {
            return;
        }
        var data = new FormData(form);
        var text = data.get('text');
        var restarting = !data.has('text');
        if (!restarting && !String(text).trim()) {
            input.focus();
            return;
        }
        // What the person said: the button's label, or their own words.
        var pressed = form.querySelector('.chat-chip');
        if (!restarting) {
            bubble(pressed ? pressed.textContent.replace(' (suggested)', '') : String(text).trim(), false);
        }
        // Messages on screen once the person's own has been added (none after a restart).
        var shown = restarting ? 0 : log().querySelectorAll('.chat-row').length;
        if (form.hasAttribute('data-chat-input')) {
            input.value = '';
        }
        var replies = document.querySelector('.chat-replies');
        if (replies) {
            replies.remove();
        }
        setBusy(true);
        var typing = bubble(null, true);
        var started = Date.now();

        fetch(form.action, {
            method: 'POST',
            body: new URLSearchParams(data),
            headers: { 'X-Requested-With': 'fetch' },
            credentials: 'same-origin'
        }).then(function (response) {
            if (response.redirected && response.url.indexOf('/login') !== -1) {
                window.location.href = response.url;
                throw new Error('Signed out');
            }
            if (!response.ok) {
                throw new Error('Status ' + response.status);
            }
            return response.text();
        }).then(function (html) {
            // A short pause, so the answer does not appear before the question has been read.
            var wait = Math.max(0, 450 - (Date.now() - started));
            setTimeout(function () {
                replaceChat(html, shown);
                setBusy(false);
            }, wait);
        }).catch(function () {
            typing.remove();
            setBusy(false);
            bubble('Sorry, something went wrong. Reload the page and try again.', true);
        });
    }

    document.addEventListener('submit', function (event) {
        var form = event.target.closest('[data-chat-form]');
        if (!form) {
            return;
        }
        event.preventDefault();
        send(form);
    });

    scrollDown();
})();

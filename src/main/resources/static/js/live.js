/*
 * Live updates. Every signed-in page opens one connection to /live (server-sent
 * events) and reacts to three kinds of message:
 *
 *   bell      {"unread": 3}          the number on the bell changes at once
 *   incident  {"code": "INC-0007"}   an incident was created or changed
 *   refresh   {}                     everything may have changed
 *
 * For "incident" and "refresh" the page fetches itself again in the background
 * and replaces only the parts marked with data-live="name". So the server
 * renders everything, with the same access rules as for a normal page view,
 * and nothing here knows how a dashboard is built.
 *
 * The incident page is different, because it has forms someone may be typing
 * in: it only shows a notice with a link to load the latest version.
 *
 * Without JavaScript, or if the connection fails, every page still works; it
 * just has to be refreshed by hand.
 */
(function () {
    'use strict';

    if (!window.EventSource) {
        return;
    }

    var REFRESH_DELAY_MS = 400;   // several messages in a row cause one refresh
    var source = new EventSource('/live');
    var refreshTimer = null;
    var leaving = false;

    // ---- the bell ----

    function showUnread(count) {
        var badge = document.querySelector('.navbar-bell-count');
        if (!badge) {
            return;
        }
        var before = parseInt(badge.textContent, 10) || 0;
        badge.textContent = count;
        badge.classList.toggle('d-none', count <= 0);
        if (count > before) {
            highlight(badge.closest('.navbar-bell'));
        }
    }

    // ---- replacing the live parts of the page ----

    function highlight(element) {
        if (!element) {
            return;
        }
        element.classList.remove('live-updated');
        void element.offsetWidth;                 // restart the animation if it is still running
        element.classList.add('live-updated');
    }

    function refreshSoon() {
        if (leaving || !document.querySelector('[data-live]')) {
            return;
        }
        clearTimeout(refreshTimer);
        refreshTimer = setTimeout(refresh, REFRESH_DELAY_MS);
    }

    function refresh() {
        if (leaving) {
            return;
        }
        fetch(window.location.href, { credentials: 'same-origin', headers: { 'X-Requested-With': 'live' } })
            .then(function (response) {
                // Signed out meanwhile, or an error page: leave what is on screen alone.
                return response.ok && !response.redirected ? response.text() : null;
            })
            .then(function (html) {
                if (!html || leaving) {
                    return;
                }
                var fresh = new DOMParser().parseFromString(html, 'text/html');
                document.querySelectorAll('[data-live]').forEach(function (region) {
                    var replacement = fresh.querySelector('[data-live="' + region.getAttribute('data-live') + '"]');
                    if (replacement && replacement.innerHTML !== region.innerHTML) {
                        region.innerHTML = replacement.innerHTML;
                        highlight(region);
                    }
                });
                var bell = fresh.querySelector('.navbar-bell-count');
                if (bell) {
                    showUnread(parseInt(bell.textContent, 10) || 0);
                }
            })
            .catch(function () {
                // The network is down. The next message will try again.
            });
    }

    // ---- the incident page: say that it changed, do not touch the forms ----

    function incidentChanged(code) {
        var page = document.querySelector('[data-live-incident]');
        if (page && page.getAttribute('data-live-incident') === code) {
            var notice = document.querySelector('[data-live-notice]');
            if (notice && !leaving) {
                notice.classList.remove('d-none');
            }
        }
    }

    // ---- messages from the server ----

    function data(event) {
        try {
            return JSON.parse(event.data);
        } catch (e) {
            return {};
        }
    }

    function connected(isConnected) {
        var dot = document.querySelector('[data-live-status]');
        if (!dot) {
            return;
        }
        dot.classList.toggle('live-on', isConnected);
        if (isConnected) {
            dot.title = 'Live updates are on';
        } else if (source.readyState === EventSource.CLOSED) {
            dot.title = 'Live updates are off. Reload the page to switch them on again.';   // e.g. signed out
        } else {
            dot.title = 'Live updates are reconnecting';                                    // the browser retries by itself
        }
    }

    source.addEventListener('open', function () { connected(true); });
    source.addEventListener('error', function () { connected(false); });

    source.addEventListener('bell', function (event) {
        showUnread(data(event).unread || 0);
        if (document.querySelector('[data-live="notifications"]')) {
            refreshSoon();                        // the list behind the bell is open: show the new entry
        }
    });

    source.addEventListener('incident', function (event) {
        incidentChanged(data(event).code);
        refreshSoon();
    });

    source.addEventListener('refresh', refreshSoon);

    // A page that is being left (a form was sent, a link was followed) must not
    // fetch itself any more: the next page gets its own connection.
    function stop() {
        leaving = true;
        clearTimeout(refreshTimer);
    }
    document.addEventListener('submit', stop);
    window.addEventListener('pagehide', function () {
        stop();
        source.close();
    });
})();

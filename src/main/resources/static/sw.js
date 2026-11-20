/*
 * The service worker that makes Quell an installable app.
 *
 *  - The look of the app (styles, scripts, fonts, icons) is kept in a cache,
 *    so pages open fast and the offline page can be drawn without a network.
 *    A cached file is used at once and refreshed in the background.
 *  - Pages always come from the server, so nobody ever sees stale or someone
 *    else's data. Only when the server cannot be reached is the offline page
 *    shown instead.
 *  - Everything else (live updates, files, forms) is left to the browser.
 *  - Clicking a desktop alert opens the notification it is about.
 */
'use strict';

var CACHE = 'quell-shell-v4';
var OFFLINE_PAGE = '/offline.html';
var SHELL = [
    OFFLINE_PAGE,
    '/css/app.css',
    '/js/theme.js',
    '/js/app.js',
    '/img/logo.svg',
    '/img/logo-192.png',
    '/favicon.svg',
    '/webjars/bootstrap/css/bootstrap.min.css',
    '/webjars/bootstrap-icons/font/bootstrap-icons.min.css',
    '/css/fonts.css',
    '/fonts/geist-latin-wght-normal.woff2'
];
var STATIC = /^\/(css|js|img|webjars)\/|^\/favicon\.svg$/;

self.addEventListener('install', function (event) {
    event.waitUntil(caches.open(CACHE).then(function (cache) {
        // One missing file must not stop the app from installing.
        return Promise.all(SHELL.map(function (url) {
            return cache.add(new Request(url, { credentials: 'same-origin' })).catch(function () {});
        }));
    }).then(function () {
        return self.skipWaiting();
    }));
});

self.addEventListener('activate', function (event) {
    event.waitUntil(caches.keys().then(function (names) {
        return Promise.all(names.filter(function (name) {
            return name.indexOf('quell-') === 0 && name !== CACHE;
        }).map(function (name) {
            return caches.delete(name);
        }));
    }).then(function () {
        return self.clients.claim();
    }));
});

self.addEventListener('fetch', function (event) {
    var request = event.request;
    if (request.method !== 'GET') {
        return;
    }
    var url = new URL(request.url);
    if (url.origin !== self.location.origin) {
        return;
    }
    if (request.mode === 'navigate') {
        event.respondWith(fetch(request).catch(function () {
            return caches.match(OFFLINE_PAGE);
        }));
        return;
    }
    if (STATIC.test(url.pathname)) {
        event.respondWith(caches.open(CACHE).then(function (cache) {
            return cache.match(request).then(function (cached) {
                var fresh = fetch(request).then(function (response) {
                    if (response.ok) {
                        cache.put(request, response.clone());
                    }
                    return response;
                });
                if (cached) {
                    fresh.catch(function () {});
                    return cached;
                }
                return fresh;
            });
        }));
    }
});

self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    var target = (event.notification.data && event.notification.data.url) || '/notifications';
    event.waitUntil(self.clients.matchAll({ type: 'window' }).then(function (windows) {
        // Reuse an open Quell window if there is one, otherwise open a new one.
        var open = windows.filter(function (client) { return 'focus' in client; })[0];
        if (!open) {
            return self.clients.openWindow(target);
        }
        return open.focus().then(function (focused) {
            return focused.navigate(target);
        }).catch(function () {
            return self.clients.openWindow(target);
        });
    }));
});

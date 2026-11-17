/*
 * The command palette: press Ctrl+K (⌘K on a Mac), or click the search box in
 * the navbar, and type. It offers:
 *   - every page in this user's navbar ("Go to"),
 *   - a few actions (new incident, switch theme, sign out),
 *   - incidents by code or title and knowledge articles (/search/quick).
 * Arrow keys move, Enter opens, Escape closes.
 */
(function () {
    'use strict';

    var backdrop = null;
    var input = null;
    var list = null;
    var items = [];
    var selected = 0;
    var timer = null;
    var serverResults = [];
    var lastQuery = '';

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined && text !== null) {
            node.textContent = text;
        }
        return node;
    }

    /** The pages of this user's navbar, plus a few actions. */
    function commands() {
        var seen = {};
        var found = [];
        document.querySelectorAll('.app-nav a.nav-link[href], .app-nav a.dropdown-item[href]').forEach(function (link) {
            var url = link.getAttribute('href');
            if (!url || url === '#' || seen[url]) {
                return;
            }
            seen[url] = true;
            var icon = link.querySelector('i');
            found.push({
                group: 'Go to',
                icon: icon ? Array.prototype.find.call(icon.classList, function (c) { return c.indexOf('bi-') === 0; }) : 'bi-arrow-right',
                title: link.textContent.replace(/\s+/g, ' ').trim(),
                url: url
            });
        });
        var dark = document.documentElement.getAttribute('data-bs-theme') !== 'light';
        found.push({ group: 'Actions', icon: dark ? 'bi-sun' : 'bi-moon-stars',
            title: dark ? 'Switch to light mode' : 'Switch to dark mode', run: function () { window.quellToggleTheme(); } });
        var logout = document.querySelector('form[action$="/logout"]');
        if (logout) {
            found.push({ group: 'Actions', icon: 'bi-box-arrow-right', title: 'Sign out', run: function () { logout.submit(); } });
        }
        return found;
    }

    function matches(command, query) {
        if (!query) {
            return true;
        }
        var haystack = (command.title + ' ' + command.group).toLowerCase();
        return query.toLowerCase().split(/\s+/).every(function (word) { return haystack.indexOf(word) >= 0; });
    }

    function render() {
        var query = input.value.trim();
        items = commands().filter(function (command) { return matches(command, query); }).concat(serverResults);
        if (selected >= items.length) {
            selected = Math.max(0, items.length - 1);
        }
        list.replaceChildren();
        if (items.length === 0) {
            list.appendChild(element('div', 'palette-empty', query.length < 2 ? 'Type to search' : 'Nothing found for “' + query + '”'));
            return;
        }
        var group = null;
        items.forEach(function (item, index) {
            if (item.group !== group) {
                group = item.group;
                list.appendChild(element('div', 'palette-group', group));
            }
            var row = element(item.url ? 'a' : 'div', 'palette-item' + (index === selected ? ' selected' : ''));
            if (item.url) {
                row.href = item.url;
            }
            row.setAttribute('role', 'option');
            row.appendChild(element('i', 'bi ' + (item.icon || 'bi-arrow-right')));
            row.appendChild(element('span', 'text-truncate', item.title));
            if (item.meta) {
                row.appendChild(element('span', 'palette-meta', item.meta));
            }
            row.addEventListener('mouseenter', function () {
                selected = index;
                highlight();
            });
            row.addEventListener('click', function (event) {
                event.preventDefault();
                choose(index);
            });
            list.appendChild(row);
        });
    }

    function highlight() {
        list.querySelectorAll('.palette-item').forEach(function (row, index) {
            row.classList.toggle('selected', index === selected);
            if (index === selected) {
                row.scrollIntoView({ block: 'nearest' });
            }
        });
    }

    function choose(index) {
        var item = items[index];
        if (!item) {
            return;
        }
        close();
        if (item.run) {
            item.run();
        } else if (item.url) {
            window.location.href = item.url;
        }
    }

    function searchServer() {
        var query = input.value.trim();
        if (query === lastQuery) {
            return;
        }
        lastQuery = query;
        if (query.length < 2) {
            serverResults = [];
            render();
            return;
        }
        fetch('/search/quick?q=' + encodeURIComponent(query), { credentials: 'same-origin' })
            .then(function (response) { return response.ok ? response.json() : []; })
            .then(function (results) {
                if (input && input.value.trim() === query) {
                    serverResults = results;
                    render();
                }
            })
            .catch(function () { /* searching is optional */ });
    }

    function open() {
        if (backdrop) {
            return;
        }
        backdrop = element('div', 'palette-backdrop');
        var palette = element('div', 'palette');
        palette.setAttribute('role', 'dialog');
        palette.setAttribute('aria-label', 'Search and jump anywhere');
        var field = element('div', 'position-relative');
        field.appendChild(element('i', 'bi bi-search palette-search-icon'));
        input = element('input', 'palette-input');
        input.type = 'text';
        input.placeholder = 'Search incidents and articles, or jump to a page…';
        input.setAttribute('aria-label', 'Search');
        field.appendChild(input);
        list = element('div', 'palette-results');
        list.setAttribute('role', 'listbox');
        var footer = element('div', 'palette-footer');
        [['↑↓', 'to move'], ['Enter', 'to open'], ['Esc', 'to close']].forEach(function (hint) {
            var part = element('span');
            part.appendChild(element('kbd', null, hint[0]));
            part.appendChild(document.createTextNode(' ' + hint[1]));
            footer.appendChild(part);
        });
        palette.appendChild(field);
        palette.appendChild(list);
        palette.appendChild(footer);
        backdrop.appendChild(palette);
        backdrop.addEventListener('mousedown', function (event) {
            if (event.target === backdrop) {
                close();
            }
        });
        input.addEventListener('input', function () {
            selected = 0;
            render();
            clearTimeout(timer);
            timer = setTimeout(searchServer, 180);
        });
        input.addEventListener('keydown', function (event) {
            if (event.key === 'ArrowDown') {
                event.preventDefault();
                selected = Math.min(selected + 1, items.length - 1);
                highlight();
            } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                selected = Math.max(selected - 1, 0);
                highlight();
            } else if (event.key === 'Enter') {
                event.preventDefault();
                choose(selected);
            } else if (event.key === 'Escape') {
                close();
            }
        });
        document.body.appendChild(backdrop);
        serverResults = [];
        lastQuery = '';
        selected = 0;
        render();
        input.focus();
    }

    function close() {
        if (backdrop) {
            backdrop.remove();
            backdrop = null;
            input = null;
        }
    }

    document.addEventListener('keydown', function (event) {
        var typing = /input|textarea|select/i.test((event.target.tagName || '')) || event.target.isContentEditable;
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
            event.preventDefault();
            if (backdrop) {
                close();
            } else {
                open();
            }
        } else if (event.key === '/' && !typing && !backdrop) {
            event.preventDefault();
            open();
        }
    });

    document.addEventListener('click', function (event) {
        if (event.target.closest('[data-command-palette]')) {
            open();
        }
    });
})();

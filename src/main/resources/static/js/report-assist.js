/*
 * Smart suggestions on the report form. While the employee types, asks the
 * server (/employee/incidents/assist) what it makes of the text and shows:
 *   - a category, impact and urgency, each with its reason and a "Use" button;
 *   - knowledge articles that may solve the problem without a ticket.
 * Nothing is chosen for the employee: every suggestion needs a click.
 */
(function () {
    'use strict';

    var panel = document.getElementById('assist');
    var title = document.getElementById('title');
    var description = document.getElementById('description');
    if (!panel || !title || !description) {
        return;
    }
    var fields = panel.querySelector('[data-assist-fields]');
    var articlesBox = panel.querySelector('[data-assist-articles]');
    var articlesList = articlesBox.querySelector('ul');
    var timer = null;
    var lastQuery = '';

    var targets = {
        category: { select: document.getElementById('categoryId'), name: 'Category' },
        impact: { select: document.getElementById('impact'), name: 'Impact' },
        urgency: { select: document.getElementById('urgency'), name: 'Urgency' }
    };

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function suggestionRow(key, suggestion) {
        var target = targets[key];
        if (!suggestion || !target.select || target.select.value === suggestion.value) {
            return null;
        }
        var row = element('div', 'd-flex flex-wrap align-items-center gap-2 small');
        row.appendChild(element('span', 'badge text-bg-light border', target.name));
        var label = element('strong', '', suggestion.label);
        row.appendChild(label);
        if (suggestion.percent) {
            row.appendChild(element('span', 'badge text-bg-primary', suggestion.percent + '% sure'));
        }
        row.appendChild(element('span', 'text-body-secondary', '· ' + suggestion.reason));
        var use = element('button', 'btn btn-sm btn-primary py-0 ms-auto', 'Use');
        use.type = 'button';
        use.addEventListener('click', function () {
            target.select.value = suggestion.value;
            target.select.dispatchEvent(new Event('change', { bubbles: true }));
            row.remove();
            refreshVisibility();
        });
        row.appendChild(use);
        return row;
    }

    function refreshVisibility() {
        var hasFields = fields.children.length > 0;
        var hasArticles = articlesList.children.length > 0;
        articlesBox.classList.toggle('d-none', !hasArticles);
        panel.classList.toggle('d-none', !hasFields && !hasArticles);
    }

    function render(advice) {
        fields.replaceChildren();
        ['category', 'impact', 'urgency'].forEach(function (key) {
            var row = suggestionRow(key, advice[key]);
            if (row) {
                fields.appendChild(row);
            }
        });
        articlesList.replaceChildren();
        (advice.articles || []).forEach(function (article) {
            var item = element('li', 'mt-1');
            var link = element('a', '', article.title);
            link.href = article.url;
            link.target = '_blank';
            link.rel = 'noopener';
            item.appendChild(element('i', 'bi bi-journal-bookmark me-1'));
            item.appendChild(link);
            item.appendChild(element('span', 'text-body-secondary', ' · ' + article.percent + '% match'));
            articlesList.appendChild(item);
        });
        refreshVisibility();
    }

    function ask() {
        var query = 'title=' + encodeURIComponent(title.value) + '&description=' + encodeURIComponent(description.value);
        if (query === lastQuery || (title.value + description.value).trim().length < 4) {
            return;
        }
        lastQuery = query;
        fetch('/employee/incidents/assist?' + query, { credentials: 'same-origin' })
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (advice) { if (advice) { render(advice); } })
            .catch(function () { /* suggestions are optional */ });
    }

    function soon() {
        clearTimeout(timer);
        timer = setTimeout(ask, 450);
    }

    title.addEventListener('input', soon);
    description.addEventListener('input', soon);
    Object.keys(targets).forEach(function (key) {
        if (targets[key].select) {
            targets[key].select.addEventListener('change', function () { lastQuery = ''; soon(); });
        }
    });
    if ((title.value + description.value).trim()) {
        ask();
    }
})();

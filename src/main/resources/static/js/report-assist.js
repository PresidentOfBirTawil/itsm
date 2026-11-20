/*
 * Smart suggestions on the report form. While the employee types, asks the
 * server (/employee/incidents/assist) what it makes of the text and shows:
 *   - a known problem with the chosen service, with "I'm affected too";
 *   - a service, category, impact and urgency, each with its reason and a "Use" button;
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

    var knownBox = panel.querySelector('[data-assist-known]');
    var suggestionsHeading = panel.querySelector('[data-assist-heading]');
    var csrf = document.querySelector('input[name="_csrf"]');
    var targets = {
        service: { select: document.getElementById('serviceId'), name: 'Service' },
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
        var hasKnown = knownBox.children.length > 0;
        articlesBox.classList.toggle('d-none', !hasArticles);
        knownBox.classList.toggle('d-none', !hasKnown);
        // Only the "Suggestions" heading: the known problems above it have bold headings of their own.
        suggestionsHeading.classList.toggle('d-none', !hasFields && !hasArticles);
        panel.classList.toggle('d-none', !hasFields && !hasArticles && !hasKnown);
    }

    /** A known problem: say so, and offer to join it with one click instead of reporting it again. */
    function knownIssue(issue) {
        var box = element('div', 'alert alert-warning mb-2 py-2');
        var heading = element('div', 'fw-semibold small');
        heading.appendChild(element('i', 'bi bi-broadcast me-1'));
        heading.appendChild(document.createTextNode('Known problem with ' + issue.service + ': ' + issue.title));
        box.appendChild(heading);
        box.appendChild(element('div', 'small', issue.code + ' · ' + issue.priority + ' · reported ' + issue.since
            + ' · ' + issue.affected + (issue.affected === 1 ? ' person' : ' people') + ' affected'));
        if (issue.following) {
            // The employee reported it or already said "I'm affected too": nothing to join.
            var following = element('div', 'mt-2 d-flex flex-wrap align-items-center gap-2');
            var link = element('a', 'btn btn-sm btn-outline-secondary py-0', 'Following');
            link.href = '/employee/incidents/' + encodeURIComponent(issue.code);
            following.appendChild(link);
            following.appendChild(element('span', 'small', 'You are already on it and will be told when it is fixed.'));
            box.appendChild(following);
            return box;
        }
        var form = element('form', 'mt-2 d-flex flex-wrap align-items-center gap-2');
        form.method = 'post';
        form.action = '/employee/incidents/' + encodeURIComponent(issue.code) + '/affected';
        if (csrf) {
            var token = element('input');
            token.type = 'hidden';
            token.name = csrf.name;
            token.value = csrf.value;
            form.appendChild(token);
        }
        var join = element('button', 'btn btn-sm btn-primary', "I'm affected too");
        join.type = 'submit';
        form.appendChild(join);
        form.appendChild(element('span', 'small', 'You will be told when it is fixed. No need to report it again.'));
        box.appendChild(form);
        return box;
    }

    function render(advice) {
        fields.replaceChildren();
        knownBox.replaceChildren();
        (advice.knownIssues || []).forEach(function (issue) {
            knownBox.appendChild(knownIssue(issue));
        });
        ['service', 'category', 'impact', 'urgency'].forEach(function (key) {
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
        var service = targets.service.select ? targets.service.select.value : '';
        var query = 'title=' + encodeURIComponent(title.value) + '&description=' + encodeURIComponent(description.value)
            + (service ? '&serviceId=' + encodeURIComponent(service) : '');
        if (query === lastQuery || ((title.value + description.value).trim().length < 4 && !service)) {
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

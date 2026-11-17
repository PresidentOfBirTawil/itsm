/*
 * Attaching files on the incident page (and the report form): drag a file
 * onto the drop zone, paste a screenshot with Ctrl+V / Cmd+V, or click to
 * choose one. The file is checked here first (type and the 5 MB limit), so a
 * mistake is shown at once instead of after a long upload; the server checks
 * everything again. Without this script the plain file input still works.
 *
 * Usage: <form data-attach-form> containing a [data-drop-zone] label and an
 * <input type="file">. Add data-attach-auto to send the form as soon as a
 * file is chosen.
 */
(function () {
    'use strict';

    var MAX_BYTES = 5 * 1024 * 1024;
    var ALLOWED = /^(image\/(png|jpeg|gif|webp)|application\/pdf|text\/plain|text\/csv)$/;
    var TEXT_NAMES = /\.(txt|log|csv)$/i;

    function sizeLabel(bytes) {
        return bytes < 1024 * 1024 ? Math.max(1, Math.round(bytes / 1024)) + ' KB'
            : (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function problemWith(file) {
        if (file.size > MAX_BYTES) {
            return file.name + ' is ' + sizeLabel(file.size) + '; files can be at most 5 MB.';
        }
        if (!ALLOWED.test(file.type) && !TEXT_NAMES.test(file.name)) {
            return file.name + ' cannot be attached. Attach a screenshot, a PDF or a text file.';
        }
        return null;
    }

    document.querySelectorAll('[data-attach-form]').forEach(function (form) {
        var input = form.querySelector('input[type=file]');
        var zone = form.querySelector('[data-drop-zone]');
        var label = form.querySelector('[data-drop-label]');
        if (!input || !zone || !label || !window.DataTransfer) {
            return;
        }
        var original = label.innerHTML;

        function show(file, problem) {
            zone.classList.toggle('drop-zone-error', !!problem);
            zone.classList.toggle('drop-zone-ready', !problem && !!file);
            if (!file) {
                label.innerHTML = original;
                return;
            }
            label.textContent = problem || (file.name + ' · ' + sizeLabel(file.size) + ' · ready to attach');
        }

        function use(file) {
            var problem = problemWith(file);
            if (problem) {
                input.value = '';
                show(file, problem);
                return;
            }
            var transfer = new DataTransfer();
            transfer.items.add(file);
            input.files = transfer.files;
            show(file, null);
            if (form.hasAttribute('data-attach-auto')) {
                form.requestSubmit();
            }
        }

        input.addEventListener('change', function () {
            if (input.files.length) {
                use(input.files[0]);
            } else {
                show(null, null);
            }
        });

        ['dragenter', 'dragover'].forEach(function (name) {
            zone.addEventListener(name, function (event) {
                event.preventDefault();
                zone.classList.add('drop-zone-over');
            });
        });
        ['dragleave', 'drop'].forEach(function (name) {
            zone.addEventListener(name, function () {
                zone.classList.remove('drop-zone-over');
            });
        });
        zone.addEventListener('drop', function (event) {
            event.preventDefault();
            if (event.dataTransfer.files.length) {
                use(event.dataTransfer.files[0]);
            }
        });

        // A screenshot pasted anywhere on the page (unless into a text box).
        document.addEventListener('paste', function (event) {
            var target = event.target;
            if (target && (target.tagName === 'TEXTAREA' || (target.tagName === 'INPUT' && target.type === 'text'))) {
                return;
            }
            var items = (event.clipboardData && event.clipboardData.items) || [];
            for (var i = 0; i < items.length; i++) {
                if (items[i].kind === 'file') {
                    var file = items[i].getAsFile();
                    if (file) {
                        event.preventDefault();
                        var extension = (file.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
                        use(new File([file], 'pasted-screenshot.' + extension, { type: file.type }));
                        zone.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        return;
                    }
                }
            }
        });

        form.addEventListener('submit', function (event) {
            if (input.required && !input.files.length) {
                event.preventDefault();
                show({ name: '', size: 0 }, 'Choose a file to attach first.');
            }
        });
    });
})();

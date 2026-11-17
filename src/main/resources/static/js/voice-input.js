/*
 * Voice input: a "Dictate" button next to a text box turns speech into text,
 * using the speech recognition built into Chrome, Edge and Safari. In
 * browsers without it the button stays hidden. Usage:
 *   <button data-dictate="#description">Dictate</button>
 */
(function () {
    'use strict';

    var Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    var buttons = document.querySelectorAll('[data-dictate]');
    if (!Recognition || buttons.length === 0) {
        return;
    }

    buttons.forEach(function (button) {
        var target = document.querySelector(button.getAttribute('data-dictate'));
        if (!target) {
            return;
        }
        button.classList.remove('d-none');
        var label = button.querySelector('span');
        var recognition = null;

        function stop() {
            if (recognition) {
                recognition.stop();
            }
        }

        button.addEventListener('click', function () {
            if (recognition) {
                stop();
                return;
            }
            recognition = new Recognition();
            recognition.lang = document.documentElement.lang || 'en';
            recognition.interimResults = false;
            recognition.continuous = true;
            var before = target.value;
            recognition.onresult = function (event) {
                var spoken = '';
                for (var i = 0; i < event.results.length; i++) {
                    spoken += event.results[i][0].transcript;
                }
                target.value = (before ? before.replace(/\s*$/, ' ') : '') + spoken.trim();
                target.dispatchEvent(new Event('input', { bubbles: true }));
            };
            recognition.onend = function () {
                recognition = null;
                button.classList.remove('text-danger');
                label.textContent = 'Dictate';
            };
            recognition.onerror = recognition.onend;
            recognition.start();
            button.classList.add('text-danger');
            label.textContent = 'Listening… click to stop';
        });
    });
})();

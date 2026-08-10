/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Parent-owned persistence for non-semantic Browser presentation geometry.
 *
 * The contained console has an opaque origin and therefore does not own
 * durable browser storage. This tiny broker persists only validated bottom
 * panel proportions. It carries no bearer/session data and performs no I/O
 * other than same-browser localStorage access.
 */
(function (window, document) {
    'use strict';

    var CHANNEL = 'kanger.layout.v1';
    var STORAGE_KEY = 'kanger.console.layout.bottom-v1';
    var PANEL_IDS = {
        'container-results': true,
        'container-solutions': true,
        'container-hypothesis': true,
        'container-logging': true
    };

    function frame() {
        return document.getElementById('console-frame');
    }

    function normalized(value) {
        var source;
        try {
            source = typeof value === 'string' ? JSON.parse(value) : value;
        } catch (ignored) {
            return null;
        }
        if (!source || typeof source !== 'object' || Array.isArray(source)) {
            return null;
        }
        var result = {};
        for (var name in source) {
            if (!Object.prototype.hasOwnProperty.call(source, name)
                    || !PANEL_IDS[name]) {
                continue;
            }
            var weight = Number(source[name]);
            if (isFinite(weight) && weight > 0 && weight < 100000) {
                result[name] = weight;
            }
        }
        return result;
    }

    function readLayout() {
        try {
            return normalized(localStorage.getItem(STORAGE_KEY) || '{}') || {};
        } catch (ignored) {
            return {};
        }
    }

    function writeLayout(value) {
        var safe = normalized(value);
        if (!safe) {
            return false;
        }
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(safe));
            return true;
        } catch (ignored) {
            return false;
        }
    }

    window.addEventListener('message', function (event) {
        var target = frame();
        if (!target || event.source !== target.contentWindow) {
            return;
        }
        var data = event.data;
        if (!data || data.channel !== CHANNEL) {
            return;
        }
        if (data.type === 'get') {
            event.source.postMessage({
                channel: CHANNEL,
                type: 'value',
                value: readLayout()
            }, '*');
            return;
        }
        if (data.type === 'set') {
            writeLayout(data.value);
        }
    });

    window.KANGER_LAYOUT_PERSISTENCE = Object.freeze({
        version: 1,
        installed: true
    });
}(window, document));

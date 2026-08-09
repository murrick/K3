/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Parent-owned local file download broker for the sandboxed KANGER editor.
 *
 * The opaque console cannot initiate downloads directly while its canonical
 * sandbox remains exactly allow-scripts. The child may therefore request a
 * download of editor text; the parent validates the request, starts a normal
 * browser download and acknowledges that the download was initiated.
 *
 * This channel has no Server, session-token, command or workspace authority.
 */
(function (window, document) {
    'use strict';

    var CHANNEL = 'kanger.localfile.v1';
    var MAX_TEXT_BYTES = 4 * 1024 * 1024;

    function frame() {
        return document.getElementById('console-frame');
    }

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function byteLength(text) {
        if (typeof window.TextEncoder === 'function') {
            return new window.TextEncoder().encode(text).length;
        }
        return unescape(encodeURIComponent(text)).length;
    }

    function validName(value) {
        var name = stringValue(value).trim();
        if (!name || name.length > 160 || /[\\\/]/.test(name)
                || !/\.k$/i.test(name)) {
            return '';
        }
        return name;
    }

    function reply(type, requestId, name, message) {
        var target = frame();
        if (!target || !target.contentWindow) {
            return;
        }
        target.contentWindow.postMessage({
            channel: CHANNEL,
            type: type,
            request_id: requestId,
            name: name || '',
            message: message || ''
        }, '*');
    }

    function startDownload(name, text) {
        var blob = new window.Blob([text], {type: 'text/plain;charset=utf-8'});
        var url = window.URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = name;
        link.rel = 'noopener';
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.setTimeout(function () {
            window.URL.revokeObjectURL(url);
        }, 1000);
    }

    window.addEventListener('message', function (event) {
        var target = frame();
        if (!target || event.source !== target.contentWindow
                || event.origin !== 'null') {
            return;
        }
        var data = event.data;
        if (!data || data.channel !== CHANNEL || data.type !== 'save') {
            return;
        }

        var requestId = Number(data.request_id);
        var name = validName(data.name);
        var text = typeof data.text === 'string' ? data.text : null;
        if (!Number.isInteger(requestId) || requestId <= 0) {
            return;
        }
        if (!name) {
            reply('error', requestId, '', 'Invalid .k file name');
            return;
        }
        if (text === null) {
            reply('error', requestId, name, 'Editor content is not text');
            return;
        }
        if (byteLength(text) > MAX_TEXT_BYTES) {
            reply('error', requestId, name, 'Editor content is too large to download');
            return;
        }

        try {
            startDownload(name, text);
            reply('saved', requestId, name, 'Download started');
        } catch (error) {
            reply('error', requestId, name,
                    error && error.message ? error.message : 'Download failed');
        }
    }, true);

    window.KANGER_LOCAL_FILE_DOWNLOAD = Object.freeze({
        version: 1,
        installed: true,
        maxBytes: MAX_TEXT_BYTES
    });
}(window, document));

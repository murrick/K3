/*
 * KANGER parser-time browser capability loader.
 *
 * Keep the historical script name and ordering while separating the immutable
 * CodeMirror mode from the KANGER operation, workspace, error, canonical
 * dialogue and presentation authorities.
 *
 * The historical console still emits one legacy startup sequence
 * (help -> use -> get). A one-shot migration adapter installed after the
 * trusted boundary rewrites only that generated `use` to canonical `storage`.
 * Operator input remains exact raw dialogue after bootstrap completes.
 */
(function (window, document) {
    'use strict';

    document.write('<script src="javascript-mode-vendor.js"><\/script>');
    document.write('<script src="operation.js"><\/script>');
    document.write('<script src="workspace.js"><\/script>');
    document.write('<script src="error.js"><\/script>');
    document.write('<script src="dialogue.js"><\/script>');
    document.write('<script src="presentation.js"><\/script>');

    function installStartupAdapter() {
        if (window.KANGER_STARTUP_ADAPTER
                || typeof window.logRequest !== 'function'
                || typeof window.command !== 'function') {
            return;
        }

        var originalLogRequest = window.logRequest;
        var originalCommand = window.command;
        var state = 'idle';
        var rewriteUse = false;

        window.logRequest = function (text, callback) {
            var raw = text === null || text === undefined ? '' : String(text);
            if (state === 'idle'
                    && raw.indexOf('// KANGER session started at ') === 0) {
                state = 'session';
            } else if (state === 'session' && raw === 'help') {
                state = 'help';
            } else if (state === 'help' && raw === 'use') {
                state = 'storage-log';
                rewriteUse = true;
                raw = 'storage';
            } else if (state === 'storage-command' && raw === 'get') {
                state = 'done';
            }
            return originalLogRequest(raw, callback);
        };

        window.command = function (line, callback) {
            var raw = line === null || line === undefined ? '' : String(line);
            if (rewriteUse && state === 'storage-log' && raw === 'use') {
                rewriteUse = false;
                state = 'storage-command';
                return originalCommand('storage', callback);
            }
            return originalCommand(raw, callback);
        };

        window.KANGER_STARTUP_ADAPTER = Object.freeze({
            version: 1,
            installed: true
        });
    }

    if (window.jQuery && window.jQuery.fn
            && typeof window.jQuery.fn.ready === 'function') {
        var originalReady = window.jQuery.fn.ready;
        window.jQuery.fn.ready = function (callback) {
            return originalReady.call(this, function () {
                var result = callback.apply(this, arguments);
                installStartupAdapter();
                return result;
            });
        };
    }
}(window, document));

/*
 * KANGER parser-time CodeMirror and operation loader.
 *
 * Keep the historical script name and ordering while separating the immutable
 * CodeMirror mode from the KANGER browser operation protocol.
 */
(function (document) {
    'use strict';
    document.write('<script src="javascript-mode-vendor.js"><\/script>');
    document.write('<script src="operation.js"><\/script>');
}(document));

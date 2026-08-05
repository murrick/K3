/*
 * KANGER parser-time browser capability loader.
 *
 * Keep the historical script name and ordering while separating the immutable
 * CodeMirror mode from the KANGER operation, workspace and error authorities.
 */
(function (document) {
    'use strict';
    document.write('<script src="javascript-mode-vendor.js"><\/script>');
    document.write('<script src="operation.js"><\/script>');
    document.write('<script src="workspace.js"><\/script>');
    document.write('<script src="error.js"><\/script>');
}(document));

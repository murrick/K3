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
 *
 * The same migration boundary also supplies editor-local Open/Save behavior
 * and preserves the editor buffer after a failed compile. Local file I/O never
 * touches Server source storage; compile transport remains owned by the
 * historical/canonical query path already present in the Browser.
 */
(function (window, document) {
    'use strict';

    document.write('<script src="javascript-mode-vendor.js"><\/script>');
    document.write('<script src="operation.js"><\/script>');
    document.write('<script src="workspace.js"><\/script>');
    document.write('<script src="error.js"><\/script>');
    document.write('<script src="dialogue.js"><\/script>');
    document.write('<script src="presentation.js"><\/script>');

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

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
            var raw = stringValue(text);
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
            var raw = stringValue(line);
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

    function installEditorFileAdapter() {
        if (window.KANGER_EDITOR_FILE_ADAPTER
                || typeof window.openEditor !== 'function'
                || typeof window.openConsole !== 'function'
                || typeof window.compileSource !== 'function'
                || typeof window.refreshScreen !== 'function') {
            return;
        }

        var originalOpenEditor = window.openEditor;
        var originalOpenConsole = window.openConsole;
        var originalCompileSource = window.compileSource;
        var originalRefreshScreen = window.refreshScreen;
        var compilePending = false;
        var suppressNextConsoleClose = false;
        var restoringAfterCompile = false;
        var localFileName = '';
        var tools = null;
        var status = null;
        var fallbackInput = null;

        function editor() {
            return window.editor;
        }

        function editorText() {
            var instance = editor();
            return instance && typeof instance.getValue === 'function'
                    ? stringValue(instance.getValue()) : '';
        }

        function normalizedFileName(value) {
            var text = stringValue(value).replace(/^.*[\\\/]/, '').trim();
            if (!text) {
                text = 'source.k';
            }
            if (!/\.k$/i.test(text)) {
                text += '.k';
            }
            return text;
        }

        function workspaceFileName() {
            try {
                var authority = window.KANGER_WORKSPACE_STATE;
                var snapshot = authority
                        && typeof authority.snapshot === 'function'
                        ? authority.snapshot() : null;
                var source = snapshot && snapshot.workspace
                        ? snapshot.workspace.source : null;
                if (source && source.logical_name) {
                    return normalizedFileName(source.logical_name);
                }
            } catch (ignored) {
            }
            return 'source.k';
        }

        function setStatus(message, error) {
            if (!status) {
                return;
            }
            var text = stringValue(message);
            status.textContent = text;
            status.title = text;
            status.style.display = text ? '' : 'none';
            status.style.color = error ? '#ffd0d0' : '#d8e2e8';
        }

        function focusEditor() {
            var instance = editor();
            if (instance && typeof instance.refresh === 'function') {
                instance.refresh();
            }
            if (instance && typeof instance.focus === 'function') {
                instance.focus();
            }
        }

        function applyLocalText(text, name) {
            var instance = editor();
            if (!instance || typeof instance.setValue !== 'function') {
                setStatus('Editor is not available', true);
                return;
            }
            localFileName = normalizedFileName(name);
            instance.setValue(stringValue(text));
            if (typeof instance.setCursor === 'function') {
                instance.setCursor(0, 0);
            }
            setStatus('Opened ' + localFileName, false);
            focusEditor();
        }

        function readFileObject(file) {
            if (!file) {
                return;
            }
            var name = normalizedFileName(file.name || localFileName);
            if (typeof file.text === 'function') {
                file.text().then(function (text) {
                    applyLocalText(text, name);
                }, function (error) {
                    setStatus('Open failed: ' + stringValue(
                            error && error.message ? error.message : error), true);
                });
                return;
            }
            if (typeof window.FileReader === 'function') {
                var reader = new window.FileReader();
                reader.onload = function () {
                    applyLocalText(reader.result, name);
                };
                reader.onerror = function () {
                    setStatus('Open failed', true);
                };
                reader.readAsText(file, 'UTF-8');
                return;
            }
            setStatus('This browser cannot read local files', true);
        }

        function ensureFallbackInput() {
            if (fallbackInput) {
                return fallbackInput;
            }
            fallbackInput = document.createElement('input');
            fallbackInput.type = 'file';
            fallbackInput.accept = '.k,text/plain';
            fallbackInput.style.display = 'none';
            fallbackInput.addEventListener('change', function () {
                var file = fallbackInput.files && fallbackInput.files.length
                        ? fallbackInput.files[0] : null;
                readFileObject(file);
            });
            if (document.body) {
                document.body.appendChild(fallbackInput);
            }
            return fallbackInput;
        }

        function openLocalFile() {
            setStatus('', false);
            var input = ensureFallbackInput();
            input.value = '';
            if (typeof input.click === 'function') {
                input.click();
            }
        }

        function fallbackDownload(text, name) {
            if (typeof window.Blob !== 'function'
                    || !window.URL
                    || typeof window.URL.createObjectURL !== 'function') {
                setStatus('This browser cannot save local files', true);
                return;
            }
            var blob = new window.Blob([text], {
                type: 'text/plain;charset=utf-8'
            });
            var url = window.URL.createObjectURL(blob);
            var link = document.createElement('a');
            link.href = url;
            link.download = name;
            link.style.display = 'none';
            if (document.body) {
                document.body.appendChild(link);
            }
            if (typeof link.click === 'function') {
                link.click();
            }
            if (link.parentNode && typeof link.parentNode.removeChild === 'function') {
                link.parentNode.removeChild(link);
            }
            window.setTimeout(function () {
                window.URL.revokeObjectURL(url);
            }, 0);
            setStatus('Saved ' + name, false);
        }

        function saveLocalFile() {
            var text = editorText();
            var name = normalizedFileName(localFileName || workspaceFileName());
            localFileName = name;
            fallbackDownload(text, name);
        }

        function makeAction(label, handler) {
            var action = document.createElement('span');
            action.textContent = label;
            action.style.cursor = 'pointer';
            action.style.paddingRight = '10px';
            action.style.userSelect = 'none';
            action.addEventListener('click', handler);
            return action;
        }

        function ensureTools() {
            if (tools) {
                return;
            }
            var title = document.getElementById('console-title');
            var header = title ? title.parentNode : null;
            if (!header) {
                return;
            }

            status = document.createElement('span');
            status.id = 'editor-local-status';
            status.style.cssFloat = 'left';
            status.style.marginLeft = '14px';
            status.style.maxWidth = '55vw';
            status.style.overflow = 'hidden';
            status.style.textOverflow = 'ellipsis';
            status.style.whiteSpace = 'nowrap';
            status.style.fontFamily = 'monospace';
            status.style.fontSize = '11px';
            status.style.display = 'none';
            header.appendChild(status);

            tools = document.createElement('span');
            tools.id = 'editor-local-actions';
            tools.style.cssFloat = 'right';
            tools.style.display = 'none';
            tools.appendChild(makeAction('Open', openLocalFile));
            tools.appendChild(makeAction('Save', saveLocalFile));
            header.appendChild(tools);
        }

        function showTools(on) {
            ensureTools();
            if (tools) {
                tools.style.display = on ? '' : 'none';
            }
            if (!on) {
                setStatus('', false);
            }
        }

        window.openEditor = function () {
            if (!restoringAfterCompile) {
                localFileName = workspaceFileName();
            }
            var result = originalOpenEditor.apply(window, arguments);
            showTools(true);
            if (!restoringAfterCompile) {
                setStatus('', false);
            }
            focusEditor();
            return result;
        };

        window.openConsole = function () {
            if (suppressNextConsoleClose) {
                suppressNextConsoleClose = false;
                focusEditor();
                return;
            }
            showTools(false);
            return originalOpenConsole.apply(window, arguments);
        };

        window.compileSource = function () {
            compilePending = true;
            suppressNextConsoleClose = false;
            setStatus('Compiling…', false);
            return originalCompileSource.apply(window, arguments);
        };

        window.refreshScreen = function (data) {
            var failedCompile = false;
            var failureMessage = '';
            if (compilePending) {
                compilePending = false;
                if (!data || stringValue(data.result).toUpperCase() !== 'OK') {
                    suppressNextConsoleClose = true;
                    failedCompile = true;
                    failureMessage = data && (data.description || data.message)
                            ? (data.description || data.message)
                            : 'Compile failed';
                    setStatus(stringValue(failureMessage), true);
                } else {
                    suppressNextConsoleClose = false;
                    setStatus('', false);
                }
            }
            var result = originalRefreshScreen.apply(window, arguments);
            if (failedCompile) {
                window.setTimeout(function () {
                    restoringAfterCompile = true;
                    try {
                        /*
                         * The historical compile callback calls openConsole()
                         * after refreshScreen(). The editor adapter suppresses
                         * the historical hide, but the presentation wrapper
                         * still restores the shell to its non-modal home.
                         * Re-entering openEditor(null) after that callback
                         * reasserts only the presentation overlay; the buffer
                         * is not rewritten because historical openEditor is a
                         * no-op for null text.
                         */
                        window.openEditor(null);
                    } finally {
                        restoringAfterCompile = false;
                    }
                    setStatus(stringValue(failureMessage), true);
                    focusEditor();
                }, 0);
            }
            return result;
        };

        ensureTools();
        showTools(false);

        window.KANGER_EDITOR_FILE_ADAPTER = Object.freeze({
            version: 2,
            installed: true,
            localOnly: true,
            sandboxSafe: true
        });
    }

    if (window.jQuery && window.jQuery.fn
            && typeof window.jQuery.fn.ready === 'function') {
        var originalReady = window.jQuery.fn.ready;
        window.jQuery.fn.ready = function (callback) {
            return originalReady.call(this, function () {
                var result = callback.apply(this, arguments);
                installStartupAdapter();
                installEditorFileAdapter();
                return result;
            });
        };
    }
}(window, document));

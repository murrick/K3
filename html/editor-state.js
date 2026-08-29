/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Final Browser authority for Editor-local staging state.
 *
 * CONTEXT  - clean textual projection of the current semantic Mind level.
 * EDITED   - local text differs from the last applied semantic projection.
 * RECOVERY - exact rejected source_recovery text before the user edits it.
 *
 * No state in this adapter identifies an active/current server .k file.
 */
(function (window, document) {
    'use strict';

    var CHANNEL = 'kanger.localfile.v1';
    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 400;
    var base = {};
    var suppressEditorChange = false;
    var compilePending = false;
    var preserveEditorAfterCompileReject = false;
    var nextSaveRequestId = 1000000;
    var pendingSave = Object.create(null);
    var state = {
        mode: 'CONTEXT',
        dirty: false,
        fileName: '',
        baselineText: ''
    };

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function normalizedFileName(value) {
        var text = stringValue(value).replace(/^.*[\\\/]/, '').trim();
        if (!text) {
            return 'source.k';
        }
        if (!/\.k$/i.test(text)) {
            text += '.k';
        }
        return text;
    }

    function editor() {
        return window.editor;
    }

    function editorText() {
        var instance = editor();
        return instance && typeof instance.getValue === 'function'
                ? stringValue(instance.getValue()) : '';
    }

    function titleNode() {
        return document.getElementById('console-title');
    }

    function statusNode() {
        return document.getElementById('editor-local-status');
    }

    function editorVisible() {
        var container = document.getElementById('editor');
        return !!container && container.style.display !== 'none';
    }

    function setStatus(message, error) {
        var status = statusNode();
        if (!status) {
            return;
        }
        var text = stringValue(message);
        status.textContent = text;
        status.title = text;
        status.style.display = text ? '' : 'none';
        status.style.color = error ? '#ffd0d0' : '#d8e2e8';
    }

    function compileDiagnostic(data) {
        var boundary = window.KANGER_ERROR_BOUNDARY;
        if (boundary && typeof boundary.describe === 'function') {
            return stringValue(boundary.describe(data));
        }
        if (!data) {
            return 'Compile rejected';
        }
        return stringValue(data.description || data.code || 'Compile rejected');
    }

    function renderState() {
        var title = titleNode();
        if (!title || !editorVisible()) {
            return;
        }
        title.textContent = state.dirty ? 'Editor *' : 'Editor';
        title.title = state.mode
                + (state.fileName ? '; local=' + state.fileName : '');
    }

    function setCleanContext(text) {
        state.mode = 'CONTEXT';
        state.dirty = false;
        state.fileName = '';
        state.baselineText = stringValue(text);
        renderState();
    }

    function setEdited() {
        state.mode = 'EDITED';
        state.dirty = true;
        renderState();
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

    function onEditorChange() {
        if (suppressEditorChange) {
            return;
        }
        if (state.mode === 'RECOVERY' || editorText() !== state.baselineText) {
            setEdited();
        } else {
            state.mode = 'CONTEXT';
            state.dirty = false;
            renderState();
        }
    }

    function openEditor(text) {
        if (text === null || text === undefined) {
            var shown = base.openEditor.apply(window, arguments);
            renderState();
            focusEditor();
            return shown;
        }
        suppressEditorChange = true;
        var result;
        try {
            result = base.openEditor.apply(window, arguments);
        } finally {
            suppressEditorChange = false;
        }
        setCleanContext(editorText());
        setStatus('', false);
        focusEditor();
        return result;
    }

    function openConsole() {
        if (preserveEditorAfterCompileReject) {
            preserveEditorAfterCompileReject = false;
            renderState();
            focusEditor();
            return;
        }
        return base.openConsole.apply(window, arguments);
    }

    function fetchSemanticContext() {
        if (typeof window.logRequest === 'function') {
            window.logRequest('// Source Code', function () {
                requestSemanticContext();
            });
        } else {
            requestSemanticContext();
        }
    }

    function requestSemanticContext() {
        window.post({
            context: 'query',
            parameters: {
                token: window.token,
                source: ''
            }
        }, function (data) {
            if (data && data.result === 'OK') {
                if (typeof window.refreshScreen === 'function') {
                    window.refreshScreen(data);
                }
                window.openEditor(stringValue(data.source));
            } else if (typeof window.logResponse === 'function') {
                window.logResponse(data);
            }
        });
    }

    function showSourceEditor() {
        if (state.dirty) {
            window.openEditor(null);
            setStatus(state.mode === 'RECOVERY'
                    ? 'Rejected source recovery' : 'Local edits not applied',
                    state.mode === 'RECOVERY');
            return;
        }
        fetchSemanticContext();
    }

    function showFunctionEditor() {
        return base.showFunctionEditor.apply(window, arguments);
    }

    function compileSource() {
        compilePending = true;
        preserveEditorAfterCompileReject = false;
        return base.compileSource.apply(window, arguments);
    }

    function refreshScreen(data) {
        var result = base.refreshScreen.apply(window, arguments);
        if (compilePending) {
            compilePending = false;
            if (data && stringValue(data.result).toUpperCase() === 'OK') {
                state.mode = 'CONTEXT';
                state.dirty = false;
                state.baselineText = editorText();
                preserveEditorAfterCompileReject = false;
                setStatus('', false);
                renderState();
            } else {
                if (state.mode !== 'RECOVERY') {
                    state.mode = 'EDITED';
                }
                state.dirty = true;
                preserveEditorAfterCompileReject = true;
                setStatus(compileDiagnostic(data), true);
                renderState();
            }
        }
        return result;
    }

    function recover(recovery) {
        if (!recovery || typeof recovery !== 'object') {
            return false;
        }
        var text = stringValue(recovery.text);
        state.fileName = normalizedFileName(recovery.logical_name || state.fileName);
        suppressEditorChange = true;
        try {
            base.openEditor.call(window, text);
        } finally {
            suppressEditorChange = false;
        }
        state.mode = 'RECOVERY';
        state.dirty = true;
        state.baselineText = text;
        setStatus('Recovery: ' + state.fileName, true);
        renderState();
        focusEditor();
        return true;
    }

    function saveAction(node) {
        while (node && node !== document) {
            if (node.parentNode
                    && node.parentNode.id === 'editor-local-actions'
                    && stringValue(node.textContent).trim() === 'Save') {
                return true;
            }
            if (node.id === 'editor-local-actions') {
                return false;
            }
            node = node.parentNode;
        }
        return false;
    }

    function saveLocal(event) {
        if (!saveAction(event.target)) {
            return;
        }
        if (typeof event.preventDefault === 'function') {
            event.preventDefault();
        }
        if (typeof event.stopImmediatePropagation === 'function') {
            event.stopImmediatePropagation();
        } else if (typeof event.stopPropagation === 'function') {
            event.stopPropagation();
        }
        var requestId = ++nextSaveRequestId;
        var name = normalizedFileName(state.fileName || 'source.k');
        state.fileName = name;
        pendingSave[requestId] = name;
        setStatus('Saving ' + name + '…', false);
        window.parent.postMessage({
            channel: CHANNEL,
            type: 'save',
            request_id: requestId,
            name: name,
            text: editorText()
        }, '*');
    }

    function captureLocalFileName(event) {
        var input = event.target;
        if (!input || stringValue(input.type).toLowerCase() !== 'file'
                || !input.files || !input.files.length) {
            return;
        }
        state.fileName = normalizedFileName(input.files[0].name);
    }

    function onLocalFileMessage(event) {
        if (event.source !== window.parent) {
            return;
        }
        var data = event.data;
        if (!data || data.channel !== CHANNEL) {
            return;
        }
        var requestId = Number(data.request_id);
        if (!Number.isInteger(requestId) || !pendingSave[requestId]) {
            return;
        }
        var name = pendingSave[requestId];
        delete pendingSave[requestId];
        if (data.type === 'saved') {
            setStatus('Download started: ' + name, false);
        } else if (data.type === 'error') {
            setStatus('Save failed: '
                    + stringValue(data.message || 'Download failed'), true);
        }
    }

    function snapshot() {
        return Object.freeze({
            mode: state.mode,
            dirty: state.dirty,
            fileName: state.fileName,
            text: editorText()
        });
    }

    function installEditorListener() {
        var instance = editor();
        if (instance && typeof instance.on === 'function') {
            instance.on('change', onEditorChange);
        }
    }

    function install() {
        if (installed) {
            return;
        }
        if (!window.KANGER_WORKSPACE_STATE
                || Number(window.KANGER_WORKSPACE_STATE.version) < 2
                || !window.KANGER_EDITOR_FILE_ADAPTER
                || !window.KANGER_BROWSER_SOAK_CONVERGENCE
                || typeof window.openEditor !== 'function'
                || typeof window.openConsole !== 'function'
                || typeof window.showSourceEditor !== 'function'
                || typeof window.showFunctionEditor !== 'function'
                || typeof window.compileSource !== 'function'
                || typeof window.refreshScreen !== 'function'
                || !editor()) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }

        installed = true;
        base.openEditor = window.openEditor;
        base.openConsole = window.openConsole;
        base.showSourceEditor = window.showSourceEditor;
        base.showFunctionEditor = window.showFunctionEditor;
        base.compileSource = window.compileSource;
        base.refreshScreen = window.refreshScreen;

        window.openEditor = openEditor;
        window.openConsole = openConsole;
        window.showSourceEditor = showSourceEditor;
        window.showFunctionEditor = showFunctionEditor;
        window.compileSource = compileSource;
        window.refreshScreen = refreshScreen;

        installEditorListener();
        window.addEventListener('click', saveLocal, true);
        window.addEventListener('change', captureLocalFileName, true);
        window.addEventListener('message', onLocalFileMessage, true);

        state.baselineText = editorText();
        window.KANGER_EDITOR_STATE = Object.freeze({
            version: 1,
            installed: true,
            modes: Object.freeze(['CONTEXT', 'EDITED', 'RECOVERY']),
            snapshot: snapshot,
            recover: recover
        });
    }

    window.setTimeout(install, 0);
}(window, document));

/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Sandboxed editor local-file save adapter.
 *
 * Open remains owned by the historical editor adapter through a normal file
 * input. Save is intercepted here and delegated to the parent-owned local file
 * broker because the canonical opaque iframe sandbox intentionally has no
 * allow-downloads capability.
 */
(function (window, document) {
    'use strict';

    var CHANNEL = 'kanger.localfile.v1';
    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 250;
    var nextRequestId = 0;
    var pending = Object.create(null);
    var localFileName = '';
    var originalOpenEditor = null;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
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

    function editorText() {
        return window.editor && typeof window.editor.getValue === 'function'
                ? stringValue(window.editor.getValue()) : '';
    }

    function statusNode() {
        return document.getElementById('editor-local-status');
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

    function save() {
        var requestId = ++nextRequestId;
        var name = normalizedFileName(localFileName || workspaceFileName());
        localFileName = name;
        pending[requestId] = name;
        setStatus('Saving ' + name + '…', false);
        window.parent.postMessage({
            channel: CHANNEL,
            type: 'save',
            request_id: requestId,
            name: name,
            text: editorText()
        }, '*');
    }

    function saveAction(node) {
        while (node && node !== document) {
            if (node.parentNode
                    && node.parentNode.id === 'editor-local-actions'
                    && stringValue(node.textContent).trim() === 'Save') {
                return node;
            }
            if (node.id === 'editor-local-actions') {
                return null;
            }
            node = node.parentNode;
        }
        return null;
    }

    function onClick(event) {
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
        save();
    }

    function onFileChosen(event) {
        var input = event.target;
        if (!input || stringValue(input.type).toLowerCase() !== 'file'
                || !input.files || !input.files.length) {
            return;
        }
        localFileName = normalizedFileName(input.files[0].name);
    }

    function onMessage(event) {
        if (event.source !== window.parent) {
            return;
        }
        var data = event.data;
        if (!data || data.channel !== CHANNEL) {
            return;
        }
        var requestId = Number(data.request_id);
        if (!Number.isInteger(requestId) || !pending[requestId]) {
            return;
        }
        var name = pending[requestId];
        delete pending[requestId];
        if (data.type === 'saved') {
            setStatus('Download started: ' + name, false);
        } else if (data.type === 'error') {
            setStatus('Save failed: ' + stringValue(data.message || 'Download failed'), true);
        }
    }

    function wrapOpenEditor() {
        if (originalOpenEditor || typeof window.openEditor !== 'function') {
            return;
        }
        originalOpenEditor = window.openEditor;
        window.openEditor = function () {
            if (arguments.length && arguments[0] !== null
                    && arguments[0] !== undefined) {
                localFileName = workspaceFileName();
            }
            return originalOpenEditor.apply(window, arguments);
        };
    }

    function install() {
        if (installed) {
            return;
        }
        if (!document.body
                || !window.KANGER_EDITOR_FILE_ADAPTER
                || !document.getElementById('editor-local-actions')) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        installed = true;
        wrapOpenEditor();
        document.addEventListener('click', onClick, true);
        document.addEventListener('change', onFileChosen, true);
        window.addEventListener('message', onMessage, true);
        window.KANGER_EDITOR_LOCAL_FILE_SAVE = Object.freeze({
            version: 1,
            installed: true,
            parentOwnedDownload: true
        });
    }

    window.setTimeout(install, 0);
}(window, document));

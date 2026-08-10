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

/*
 * 3.7.0.6 development-soak corrections.
 *
 * This late adapter repairs presentation/projection boundaries exposed by the
 * VPS soak without taking command-language or execution authority. It owns:
 *   - editor compile EOF normalization at the transport edge;
 *   - active semantic projection cleanup for deleted statement rows;
 *   - bottom-panel visibility derived from committed projection content;
 *   - small presentation decoration convergence.
 */
(function (window, document) {
    'use strict';

    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 300;
    var observer = null;
    var scheduled = false;
    var applying = false;
    var originalPost = null;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function normalizeCompilePacket(packet) {
        if (!packet || packet.context !== 'query'
                || !packet.parameters
                || typeof packet.parameters !== 'object'
                || packet.parameters.compile === null
                || packet.parameters.compile === undefined) {
            return packet;
        }
        var encoded = stringValue(packet.parameters.compile);
        var source;
        try {
            source = decodeURIComponent(encoded);
        } catch (ignored) {
            return packet;
        }
        if (source && !/[\r\n]$/.test(source)) {
            packet.parameters.compile = encodeURIComponent(source + '\n');
        }
        return packet;
    }

    function installCompileBoundary() {
        if (originalPost || typeof window.post !== 'function') {
            return;
        }
        originalPost = window.post;
        window.post = function (packet, callback) {
            normalizeCompilePacket(packet);
            return originalPost(packet, callback);
        };
    }

    function operationCommitted() {
        var protocol = window.KANGER_OPERATION_PROTOCOL;
        if (!protocol || typeof protocol.snapshot !== 'function') {
            return false;
        }
        var snapshot = protocol.snapshot();
        return !!snapshot && Number(snapshot.lastCommittedSnapshotId) > 0;
    }

    function contentNode(id) {
        return document.getElementById(id);
    }

    function hasContent(id) {
        var node = contentNode(id);
        return !!node && !!node.firstChild;
    }

    function visible(id) {
        var node = document.getElementById(id);
        return !!node && node.style.display !== 'none';
    }

    function expectedVisible(id) {
        if (id === 'container-results') {
            return hasContent('query-results');
        }
        if (id === 'container-solutions') {
            return hasContent('query-solutions');
        }
        if (id === 'container-hypothesis') {
            return hasContent('query-hypothesis');
        }
        if (id === 'container-logging') {
            return true;
        }
        return visible(id);
    }

    function setVisible(id, isVisible) {
        var node = document.getElementById(id);
        if (node && visible(id) !== isVisible) {
            node.style.display = isVisible ? '' : 'none';
        }
    }

    function deletedColor(node) {
        var value = stringValue(node && node.style ? node.style.color : '')
                .replace(/\s+/g, '').toLowerCase();
        return value === '#777' || value === '#777777'
                || value === 'rgb(119,119,119)';
    }

    function updatePredicateCount(row, count) {
        if (!row || !row.childNodes) {
            return;
        }
        for (var i = row.childNodes.length - 1; i >= 0; i--) {
            var child = row.childNodes[i];
            if (child && child.nodeType === 3) {
                var value = stringValue(child.nodeValue || child.textContent);
                if (/\s\d+\s*$/.test(value)) {
                    var next = value.replace(/\s\d+\s*$/, ' ' + count);
                    if (child.nodeValue !== undefined) {
                        child.nodeValue = next;
                    } else {
                        child.textContent = next;
                    }
                    return;
                }
            }
        }
    }

    function pruneDeletedStatements() {
        var statements = contentNode('statements');
        if (!statements || !statements.querySelectorAll) {
            return;
        }
        var rows = statements.querySelectorAll('[id^="PS"]');
        for (var i = rows.length - 1; i >= 0; i--) {
            if (deletedColor(rows[i]) && rows[i].parentNode) {
                rows[i].parentNode.removeChild(rows[i]);
            }
        }
        var groups = statements.querySelectorAll('[id^="PRL"]');
        for (i = groups.length - 1; i >= 0; i--) {
            var group = groups[i];
            var active = group.querySelectorAll
                    ? group.querySelectorAll('[id^="PS"]').length : 0;
            var predicateId = stringValue(group.id).substring(3);
            var predicate = document.getElementById('PR' + predicateId);
            if (!active) {
                if (predicate && predicate.parentNode) {
                    predicate.parentNode.removeChild(predicate);
                }
                if (group.parentNode) {
                    group.parentNode.removeChild(group);
                }
            } else {
                updatePredicateCount(predicate, active);
            }
        }
    }

    function decorateSolutionTree() {
        var solutions = contentNode('query-solutions');
        if (!solutions || !solutions.querySelectorAll) {
            return;
        }
        var actions = solutions.querySelectorAll('.kanger-row-action');
        for (var i = 0; i < actions.length; i++) {
            if (stringValue(actions[i].textContent).trim() === 'tree') {
                actions[i].textContent = '○ tree';
            }
        }
    }

    function syncProjection() {
        scheduled = false;
        if (applying || !operationCommitted()) {
            return;
        }
        applying = true;
        try {
            pruneDeletedStatements();
            setVisible('container-results', expectedVisible('container-results'));
            setVisible('container-solutions', expectedVisible('container-solutions'));
            setVisible('container-hypothesis', expectedVisible('container-hypothesis'));
            setVisible('container-logging', true);
            decorateSolutionTree();
            if (window.KANGER_BOTTOM_LAYOUT_AUTHORITY
                    && typeof window.KANGER_BOTTOM_LAYOUT_AUTHORITY.refresh === 'function') {
                window.KANGER_BOTTOM_LAYOUT_AUTHORITY.refresh();
            }
        } finally {
            applying = false;
        }
    }

    function scheduleSync() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        window.setTimeout(syncProjection, 0);
    }

    function observe(id, options) {
        var node = contentNode(id);
        if (node && observer) {
            observer.observe(node, options);
        }
    }

    function projectionMutation(mutations) {
        for (var i = 0; i < mutations.length; i++) {
            var mutation = mutations[i];
            if (mutation.type === 'childList') {
                scheduleSync();
                return;
            }
            if (mutation.type === 'attributes'
                    && mutation.target && mutation.target.id
                    && visible(mutation.target.id)
                        !== expectedVisible(mutation.target.id)) {
                scheduleSync();
                return;
            }
        }
    }

    function installObserver() {
        if (!window.MutationObserver || observer) {
            return;
        }
        observer = new window.MutationObserver(projectionMutation);
        var contents = [
            'statements', 'query-results', 'query-solutions',
            'query-hypothesis', 'query-log'
        ];
        for (var i = 0; i < contents.length; i++) {
            observe(contents[i], {childList: true, subtree: true});
        }
        var panels = [
            'container-results', 'container-solutions',
            'container-hypothesis', 'container-logging'
        ];
        for (i = 0; i < panels.length; i++) {
            observe(panels[i], {attributes: true, attributeFilter: ['style']});
        }
    }

    function install() {
        if (installed) {
            return;
        }
        if (!document.body
                || !window.KANGER_OPERATION_PROTOCOL
                || !window.KANGER_WORKSPACE_STATE
                || !window.KANGER_DIALOGUE_TRANSPORT
                || !window.KANGER_PRESENTATION) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        installed = true;
        installCompileBoundary();
        installObserver();
        window.addEventListener('resize', scheduleSync);
        scheduleSync();
        window.KANGER_SOAK_CORRECTIONS = Object.freeze({
            version: 1,
            installed: true,
            normalizeCompilePacket: normalizeCompilePacket,
            refresh: scheduleSync
        });
    }

    window.setTimeout(install, 0);
}(window, document));

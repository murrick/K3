/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * KANGER 3.7.0.5 Browser presentation authority.
 *
 * The layer owns screen geometry and screen-to-command composition only.
 * It never parses or executes operator language and never handles bearer data.
 */
(function (window, document) {
    'use strict';

    var installed = false;
    var technicalOpen = false;
    var observer = null;
    var originalPlaceElements = null;
    var originalOpenConsole = null;
    var originalOpenEditor = null;
    var retryCount = 0;
    var MAX_RETRIES = 100;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function addClass(node, name) {
        if (!node) {
            return;
        }
        if (node.classList) {
            node.classList.add(name);
            return;
        }
        var classes = stringValue(node.className).split(/\s+/);
        if (classes.indexOf(name) < 0) {
            classes.push(name);
            node.className = classes.join(' ').replace(/^\s+|\s+$/g, '');
        }
    }

    function removeClass(node, name) {
        if (!node) {
            return;
        }
        if (node.classList) {
            node.classList.remove(name);
            return;
        }
        var classes = stringValue(node.className).split(/\s+/);
        var result = [];
        for (var i = 0; i < classes.length; i++) {
            if (classes[i] && classes[i] !== name) {
                result.push(classes[i]);
            }
        }
        node.className = result.join(' ');
    }

    function hasClass(node, name) {
        if (!node) {
            return false;
        }
        if (node.classList) {
            return node.classList.contains(name);
        }
        return (' ' + stringValue(node.className) + ' ')
                .indexOf(' ' + name + ' ') >= 0;
    }

    function injectStylesheet() {
        if (!document.head || document.getElementById('kanger-presentation-css')) {
            return;
        }
        var link = document.createElement('link');
        link.id = 'kanger-presentation-css';
        link.rel = 'stylesheet';
        link.href = 'presentation.css';
        document.head.appendChild(link);
    }

    function inputElement() {
        return document.getElementById('query-input');
    }

    function compose(fragment) {
        var input = inputElement();
        var text = stringValue(fragment);
        if (!input || !text) {
            return false;
        }
        var current = stringValue(input.value);
        var start = Number(input.selectionStart);
        var end = Number(input.selectionEnd);
        if (!isFinite(start) || start < 0 || start > current.length) {
            start = current.length;
        }
        if (!isFinite(end) || end < start || end > current.length) {
            end = start;
        }
        input.value = current.substring(0, start)
                + text + current.substring(end);
        var caret = start + text.length;
        input.selectionStart = caret;
        input.selectionEnd = caret;
        if (typeof input.focus === 'function') {
            input.focus();
        }
        return true;
    }

    function quoteArgument(value) {
        var text = stringValue(value);
        if (!/[\s"\\]/.test(text)) {
            return text;
        }
        return '"' + text.replace(/\\/g, '\\\\')
                .replace(/"/g, '\\"') + '"';
    }

    function ancestorWithAttribute(node, name) {
        while (node && node !== document) {
            if (node.getAttribute && node.getAttribute(name) !== null) {
                return node;
            }
            node = node.parentNode;
        }
        return null;
    }

    function ancestorWithIdPrefix(node, prefix) {
        while (node && node !== document) {
            var id = stringValue(node.id);
            if (id.indexOf(prefix) === 0) {
                return node;
            }
            node = node.parentNode;
        }
        return null;
    }

    function strongText(node) {
        if (!node) {
            return '';
        }
        var children = node.childNodes || [];
        for (var i = 0; i < children.length; i++) {
            var child = children[i];
            if (stringValue(child.tagName).toUpperCase() === 'STRONG') {
                return stringValue(child.textContent);
            }
        }
        return '';
    }

    function stopSemanticExecution(event) {
        if (event && typeof event.preventDefault === 'function') {
            event.preventDefault();
        }
        if (event && typeof event.stopImmediatePropagation === 'function') {
            event.stopImmediatePropagation();
        } else if (event && typeof event.stopPropagation === 'function') {
            event.stopPropagation();
        }
    }

    function resultCell(node) {
        var current = node;
        while (current && current !== document) {
            if (stringValue(current.tagName).toUpperCase() === 'TD') {
                return current;
            }
            current = current.parentNode;
        }
        return null;
    }

    function isFirstCell(cell) {
        return !!cell && !!cell.parentNode
                && cell.parentNode.firstChild === cell;
    }

    function isHeaderCell(cell) {
        if (!cell || !cell.parentNode || !cell.parentNode.parentNode) {
            return false;
        }
        return cell.parentNode.parentNode.firstChild === cell.parentNode;
    }

    function inContainer(node, id) {
        var container = document.getElementById(id);
        while (node && node !== document) {
            if (node === container) {
                return true;
            }
            node = node.parentNode;
        }
        return false;
    }

    function workspaceSnapshot() {
        if (!window.KANGER_WORKSPACE_STATE
                || typeof window.KANGER_WORKSPACE_STATE.snapshot !== 'function') {
            return null;
        }
        return window.KANGER_WORKSPACE_STATE.snapshot();
    }

    function onCompositionClick(event) {
        var target = event.target;
        var information = ancestorWithAttribute(target, 'data-kanger-info');
        if (information) {
            stopSemanticExecution(event);
            return;
        }
        var projection = ancestorWithAttribute(target, 'data-kanger-compose');
        if (projection) {
            stopSemanticExecution(event);
            compose(projection.getAttribute('data-kanger-compose'));
            return;
        }

        if (ancestorWithIdPrefix(target, 'SN')) {
            return;
        }

        var statement = ancestorWithIdPrefix(target, 'PS');
        if (statement && inContainer(statement, 'statements')) {
            stopSemanticExecution(event);
            compose(strongText(statement));
            return;
        }

        var predicate = ancestorWithIdPrefix(target, 'PR');
        if (predicate && stringValue(predicate.id).indexOf('PRL') !== 0
                && inContainer(predicate, 'statements')) {
            stopSemanticExecution(event);
            var predicateName = strongText(predicate);
            if (predicateName) {
                compose(predicateName + '(');
            }
            return;
        }

        var fn = ancestorWithIdPrefix(target, 'FN');
        if (fn && inContainer(fn, 'functions')) {
            stopSemanticExecution(event);
            var functionName = strongText(fn);
            if (functionName) {
                compose(functionName + '(');
            }
            return;
        }

        var solution = ancestorWithIdPrefix(target, 'SOL');
        if (solution && inContainer(solution, 'query-solutions')) {
            stopSemanticExecution(event);
            compose(strongText(solution));
            return;
        }

        var hypothesis = ancestorWithIdPrefix(target, 'HYL');
        if (hypothesis && inContainer(hypothesis, 'query-hypothesis')) {
            stopSemanticExecution(event);
            var origin = strongText(hypothesis);
            if (origin.charAt(0) === '?') {
                origin = '!~' + origin.substring(1);
            }
            compose(origin);
            return;
        }

        var cell = resultCell(target);
        if (cell && inContainer(cell, 'query-results')
                && !isFirstCell(cell) && !isHeaderCell(cell)) {
            stopSemanticExecution(event);
            compose(stringValue(cell.textContent));
            return;
        }

        var sourceTarget = ancestorWithIdPrefix(target, 'source-name');
        if (sourceTarget) {
            var sourceState = workspaceSnapshot();
            var source = sourceState && sourceState.workspace
                    ? sourceState.workspace.source : null;
            if (source && source.logical_name) {
                stopSemanticExecution(event);
                compose('get ' + quoteArgument(source.logical_name));
            }
            return;
        }

        var storageTarget = ancestorWithIdPrefix(target, 'db-name');
        if (storageTarget) {
            var storageState = workspaceSnapshot();
            var storage = storageState && storageState.workspace
                    ? storageState.workspace.storage : null;
            stopSemanticExecution(event);
            if (storage && storage.active && storage.logical_name) {
                compose('storage use ' + quoteArgument(storage.logical_name));
            } else {
                compose('storage');
            }
            return;
        }

        if (ancestorWithIdPrefix(target, 'transaction')) {
            stopSemanticExecution(event);
            compose('transaction');
        }
    }

    function addAction(row, label, command) {
        if (!row || !row.getAttribute
                || row.getAttribute('data-kanger-actions') === '1') {
            return;
        }
        row.setAttribute('data-kanger-actions', '1');
        addClass(row, 'kanger-semantic-live');
        var action = document.createElement('span');
        action.className = 'kanger-row-action';
        action.textContent = label;
        action.setAttribute('data-kanger-compose', command);
        action.title = 'Compose: ' + command;
        row.appendChild(action);
    }

    function predicateTooltip(row) {
        var id = stringValue(row && row.id).substring(2);
        var name = strongText(row);
        var lines = ['Predicate ' + id + (name ? ' · ' + name : '')];
        var details = document.getElementById('PRL' + id);
        var children = details ? details.childNodes || [] : [];
        var count = 0;
        for (var i = 0; i < children.length; i++) {
            var child = children[i];
            if (stringValue(child.id).indexOf('PS') !== 0) {
                continue;
            }
            var origin = strongText(child);
            lines.push(stringValue(child.id).substring(2)
                    + ': ' + (origin || stringValue(child.textContent)));
            count += 1;
        }
        if (!count) {
            lines.push('No statements');
        }
        return lines.join('\n');
    }

    function addPredicateInfo(row) {
        if (!row || !row.getAttribute) {
            return;
        }
        addClass(row, 'kanger-semantic-live');
        var action = row.__kangerPredicateInfo;
        if (!action) {
            action = document.createElement('span');
            action.className = 'kanger-row-action';
            action.textContent = 'ⓘ';
            action.setAttribute('data-kanger-info', 'predicate');
            action.setAttribute('aria-label', 'Predicate details');
            action.style.cursor = 'help';
            action.style.padding = '0 2px';
            action.style.marginLeft = '4px';
            row.appendChild(action);
            row.__kangerPredicateInfo = action;
        }
        action.title = predicateTooltip(row);
    }

    function decorateSemanticRows() {
        var container = document.getElementById('statements');
        var nodes;
        var i;
        if (container && container.querySelectorAll) {
            nodes = container.querySelectorAll('[id^="PR"]');
            for (i = 0; i < nodes.length; i++) {
                if (stringValue(nodes[i].id).indexOf('PRL') !== 0) {
                    addPredicateInfo(nodes[i]);
                }
            }
            nodes = container.querySelectorAll('[id^="PS"]');
            for (i = 0; i < nodes.length; i++) {
                addAction(nodes[i], 'tree',
                        'base tree ' + stringValue(nodes[i].id).substring(2));
            }
        }

        container = document.getElementById('functions');
        if (container && container.querySelectorAll) {
            nodes = container.querySelectorAll('[id^="FN"]');
            for (i = 0; i < nodes.length; i++) {
                addAction(nodes[i], 'source',
                        'function source ' + stringValue(nodes[i].id).substring(2));
            }
        }

        container = document.getElementById('query-solutions');
        if (container && container.querySelectorAll) {
            nodes = container.querySelectorAll('[id^="SOL"]');
            for (i = 0; i < nodes.length; i++) {
                addAction(nodes[i], 'tree',
                        'solution tree ' + stringValue(nodes[i].id).substring(3));
            }
        }

        container = document.getElementById('query-hypothesis');
        if (container && container.querySelectorAll) {
            nodes = container.querySelectorAll('[id^="HYL"]');
            for (i = 0; i < nodes.length; i++) {
                addAction(nodes[i], 'accept',
                        'when accept ' + stringValue(nodes[i].id).substring(3));
            }
        }
    }

    function createTechRow(parent, id) {
        var row = document.createElement('div');
        row.id = id;
        row.className = 'kanger-tech-row';
        parent.appendChild(row);
        return row;
    }

    function createTechSection(parent, title, rows) {
        var section = document.createElement('div');
        section.className = 'kanger-tech-section';
        var heading = document.createElement('div');
        heading.className = 'kanger-tech-heading';
        heading.textContent = title;
        section.appendChild(heading);
        for (var i = 0; i < rows.length; i++) {
            createTechRow(section, rows[i]);
        }
        parent.appendChild(section);
    }

    function ensureTechnicalPanel() {
        var existing = document.getElementById('technical-panel');
        if (existing) {
            return existing;
        }
        var container = document.getElementById('container');
        if (!container) {
            return null;
        }
        var panel = document.createElement('aside');
        panel.id = 'technical-panel';
        panel.className = 'kanger-collapsed';

        var toggle = document.createElement('div');
        toggle.className = 'kanger-tech-toggle';
        toggle.textContent = 'TECH';
        toggle.setAttribute('role', 'button');
        toggle.setAttribute('tabindex', '0');
        toggle.setAttribute('aria-expanded', 'false');
        toggle.title = 'Technical / administrative context';
        panel.appendChild(toggle);

        var body = document.createElement('div');
        body.className = 'kanger-tech-body';
        createTechSection(body, 'Dialogue', [
            'tech-parser', 'tech-session', 'tech-generation',
            'tech-operation', 'tech-snapshot'
        ]);
        createTechSection(body, 'Workspace', [
            'tech-source', 'tech-storage', 'tech-storage-generation'
        ]);
        createTechSection(body, 'Authority', [
            'tech-rendering', 'tech-containment'
        ]);
        panel.appendChild(body);
        container.appendChild(panel);

        function activate() {
            toggleTechnical();
        }
        toggle.addEventListener('click', activate);
        toggle.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
                if (typeof event.preventDefault === 'function') {
                    event.preventDefault();
                }
                activate();
            }
        });
        return panel;
    }

    function setText(id, value) {
        var target = document.getElementById(id);
        if (target) {
            target.textContent = stringValue(value);
        }
        return target;
    }

    function renderTechnicalPanel() {
        var operation = window.KANGER_OPERATION_PROTOCOL
                && typeof window.KANGER_OPERATION_PROTOCOL.snapshot === 'function'
                ? window.KANGER_OPERATION_PROTOCOL.snapshot() : null;
        var workspaceState = workspaceSnapshot();
        var workspace = workspaceState ? workspaceState.workspace : null;

        setText('tech-parser', 'parser: kanger-command / server');
        setText('tech-session', 'session: parent-owned');
        setText('tech-rendering', 'rendering: trusted text/DOM');
        setText('tech-containment', 'network: parent containment');

        if (operation) {
            setText('tech-generation', 'generation: ' + operation.generation);
            setText('tech-operation', operation.activeOperationId
                    ? 'operation: #' + operation.activeOperationId + ' '
                        + operation.activeOperationName
                    : 'operation: idle');
            setText('tech-snapshot', 'snapshot: '
                    + operation.lastCommittedSnapshotId
                    + (operation.currentSnapshotId
                            ? ' -> ' + operation.currentSnapshotId : ''));
        } else {
            setText('tech-generation', 'generation: -');
            setText('tech-operation', 'operation: -');
            setText('tech-snapshot', 'snapshot: -');
        }

        var source = workspace ? workspace.source : null;
        var sourceRow = setText('tech-source', source
                ? 'source: ' + (source.logical_name || 'unsaved')
                    + (source.dirty ? ' *' : '')
                : 'source: -');
        if (sourceRow) {
            if (source && source.logical_name) {
                sourceRow.setAttribute('data-kanger-compose',
                        'get ' + quoteArgument(source.logical_name));
            } else {
                sourceRow.removeAttribute('data-kanger-compose');
            }
        }

        var storage = workspace ? workspace.storage : null;
        var storageRow = setText('tech-storage', storage && storage.active
                ? 'storage: ' + storage.logical_name
                : 'storage: unused');
        if (storageRow) {
            storageRow.setAttribute('data-kanger-compose',
                    storage && storage.active && storage.logical_name
                            ? 'storage use ' + quoteArgument(storage.logical_name)
                            : 'storage');
        }
        var physical = storage && storage.physical_generation
                ? storage.physical_generation : null;
        setText('tech-storage-generation', physical && physical.present
                ? 'physical: generation; wal=' + (physical.wal_segments || 0)
                : 'physical: -');
    }

    function labelPresentation() {
        var superNode = document.getElementById('super');
        if (superNode && superNode.children && superNode.children.length) {
            addClass(superNode.children[0], 'kanger-header');
            addClass(superNode.children[superNode.children.length - 1],
                    'kanger-footer');
        }
        var statementsTitle = document.getElementById('container-left');
        if (statementsTitle && statementsTitle.firstElementChild) {
            statementsTitle.firstElementChild.textContent = 'Base / Statements';
        }
        setText('title-functions', 'Functions');
        var logging = document.getElementById('container-logging');
        if (logging && logging.firstElementChild) {
            logging.firstElementChild.textContent = 'Log';
        }
        var consoleTitle = document.getElementById('console-title');
        var editor = document.getElementById('editor');
        if (consoleTitle && (!editor || editor.style.display === 'none')) {
            consoleTitle.textContent = 'Dialogue';
        }
        var consoleButton = document.getElementById('console-button');
        if (consoleButton && (!editor || editor.style.display === 'none')) {
            consoleButton.textContent = 'Source';
        }
    }

    function applyLayout() {
        var body = document.body;
        var container = document.getElementById('container');
        var left = document.getElementById('container-left');
        var center = document.getElementById('container-right');
        var bottom = document.getElementById('container-div');
        if (!body || !container || !left || !center || !bottom) {
            return;
        }
        addClass(body, 'kanger-presentation');
        addClass(container, 'kanger-grid');
        addClass(left, 'kanger-semantic');
        addClass(center, 'kanger-center');
        addClass(bottom, 'kanger-bottom');

        var leftWidth = parseInt(left.style.width, 10);
        if (!isFinite(leftWidth) || leftWidth < 150) {
            leftWidth = 240;
        }
        document.documentElement.style.setProperty(
                '--kanger-left', leftWidth + 'px');

        var bottomHeight = parseInt(bottom.style.height, 10);
        if (!isFinite(bottomHeight) || bottomHeight < 90) {
            bottomHeight = 320;
        }
        bottom.style.flex = '0 0 ' + bottomHeight + 'px';
        bottom.style.height = bottomHeight + 'px';

        var panels = [
            'container-results', 'container-solutions',
            'container-hypothesis', 'container-logging'
        ];
        for (var i = 0; i < panels.length; i++) {
            var panel = document.getElementById(panels[i]);
            if (panel && panel.style.display !== 'none') {
                panel.style.flex = '1 1 0';
                panel.style.width = 'auto';
                panel.style.height = '100%';
            }
        }

        if (window.editor && typeof window.editor.setSize === 'function') {
            window.editor.setSize('100%', '100%');
        }
        ensureTechnicalPanel();
        labelPresentation();
        decorateSemanticRows();
        renderTechnicalPanel();
    }

    function toggleTechnical() {
        technicalOpen = !technicalOpen;
        var container = document.getElementById('container');
        var panel = ensureTechnicalPanel();
        if (!container || !panel) {
            return false;
        }
        var toggle = panel.firstElementChild;
        if (technicalOpen) {
            removeClass(panel, 'kanger-collapsed');
            addClass(container, 'kanger-tech-open');
            if (toggle) {
                toggle.setAttribute('aria-expanded', 'true');
                toggle.textContent = 'TECH  <';
            }
        } else {
            addClass(panel, 'kanger-collapsed');
            removeClass(container, 'kanger-tech-open');
            if (toggle) {
                toggle.setAttribute('aria-expanded', 'false');
                toggle.textContent = 'TECH';
            }
        }
        applyLayout();
        return technicalOpen;
    }

    function installMutationObserver() {
        if (!window.MutationObserver || observer) {
            return;
        }
        observer = new window.MutationObserver(function () {
            decorateSemanticRows();
            renderTechnicalPanel();
        });
        var ids = [
            'statements', 'functions', 'query-results',
            'query-solutions', 'query-hypothesis', 'query-log'
        ];
        for (var i = 0; i < ids.length; i++) {
            var target = document.getElementById(ids[i]);
            if (target) {
                observer.observe(target, {childList: true, subtree: true});
            }
        }
    }

    function wrapLegacyLayout() {
        if (typeof window.placeElements === 'function' && !originalPlaceElements) {
            originalPlaceElements = window.placeElements;
            window.placeElements = function (data) {
                var result = originalPlaceElements.apply(window, arguments);
                applyLayout();
                return result;
            };
        }
        if (typeof window.openConsole === 'function' && !originalOpenConsole) {
            originalOpenConsole = window.openConsole;
            window.openConsole = function () {
                var result = originalOpenConsole.apply(window, arguments);
                labelPresentation();
                applyLayout();
                return result;
            };
        }
        if (typeof window.openEditor === 'function' && !originalOpenEditor) {
            originalOpenEditor = window.openEditor;
            window.openEditor = function () {
                var result = originalOpenEditor.apply(window, arguments);
                var title = document.getElementById('console-title');
                if (title) {
                    title.textContent = 'Source';
                }
                applyLayout();
                return result;
            };
        }
    }

    function authoritiesReady() {
        return !!(window.KANGER_TRUSTED_RENDERING
                && window.KANGER_OPERATION_PROTOCOL
                && window.KANGER_WORKSPACE_STATE
                && window.KANGER_ERROR_BOUNDARY
                && window.KANGER_DIALOGUE_TRANSPORT);
    }

    function install() {
        if (installed) {
            return;
        }
        if (!document.body || !document.getElementById('super')
                || !authoritiesReady()) {
            retryCount += 1;
            if (retryCount <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        installed = true;
        wrapLegacyLayout();
        ensureTechnicalPanel();
        document.addEventListener('click', onCompositionClick, true);
        window.addEventListener('resize', function () {
            window.setTimeout(applyLayout, 0);
        });
        installMutationObserver();
        applyLayout();
        window.KANGER_PRESENTATION = Object.freeze({
            version: 1,
            installed: true,
            compose: compose,
            toggleTechnical: toggleTechnical,
            refresh: applyLayout,
            snapshot: function () {
                return Object.freeze({
                    technicalOpen: technicalOpen,
                    leftWidth: document.getElementById('container-left')
                            ? document.getElementById('container-left').style.width : ''
                });
            }
        });
    }

    injectStylesheet();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            window.setTimeout(install, 0);
        });
    } else {
        window.setTimeout(install, 0);
    }
}(window, document));

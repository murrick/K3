/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * KANGER Browser presentation authority.
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
                addAction(nodes[i], '⊕',
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
            'tech-storage', 'tech-storage-generation'
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
                && window.KANGER_WORKSPACE_STATE.version === 2
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

/*
 * 3.7.0.5 live layout refinements.
 *
 * Keeps the qualified presentation authority intact while adding two purely
 * presentational capabilities: adjustable bottom-panel proportions and a
 * viewport-modal Source editor. No command, transport or bearer authority is
 * introduced here.
 */
(function (window, document) {
    'use strict';

    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 100;
    var PANEL_IDS = [
        'container-results', 'container-solutions',
        'container-hypothesis', 'container-logging'
    ];
    var splitters = [];
    var bottomWeights = {};
    var activeSplit = null;
    var originalPlaceElements = null;
    var originalOpenConsole = null;
    var originalOpenEditor = null;
    var editorOverlayActive = false;
    var editorHome = null;
    var editorNext = null;
    var editorStyle = null;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function bottomContainer() {
        return document.getElementById('container-div');
    }

    function allPanels() {
        var panels = [];
        for (var i = 0; i < PANEL_IDS.length; i++) {
            var panel = document.getElementById(PANEL_IDS[i]);
            if (panel) {
                panels.push(panel);
            }
        }
        return panels;
    }

    function visiblePanels() {
        var panels = allPanels();
        var visible = [];
        for (var i = 0; i < panels.length; i++) {
            if (panels[i].style.display !== 'none') {
                visible.push(panels[i]);
            }
        }
        return visible;
    }

    function panelWidth(panel) {
        if (panel && typeof panel.getBoundingClientRect === 'function') {
            var rect = panel.getBoundingClientRect();
            if (rect && isFinite(rect.width) && rect.width > 0) {
                return rect.width;
            }
        }
        var fallback = parseFloat(panel && panel.style
                ? panel.style.flexGrow : '');
        return isFinite(fallback) && fallback > 0 ? fallback : 1;
    }

    function captureBottomWeights(panels) {
        for (var i = 0; i < panels.length; i++) {
            bottomWeights[panels[i].id] = panelWidth(panels[i]);
        }
    }

    function splitterStyle(splitter) {
        splitter.style.flex = '0 0 4px';
        splitter.style.minWidth = '4px';
        splitter.style.maxWidth = '4px';
        splitter.style.height = '100%';
        splitter.style.padding = '0';
        splitter.style.margin = '0';
        splitter.style.background = '#b7bdc4';
        splitter.style.cursor = 'col-resize';
        splitter.style.userSelect = 'none';
        splitter.style.touchAction = 'none';
        splitter.style.overflow = 'hidden';
    }

    function beginSplit(event, splitter) {
        var boundary = Number(splitter.__kangerBoundary);
        var panels = visiblePanels();
        if (!isFinite(boundary) || boundary < 0
                || boundary + 1 >= panels.length) {
            return;
        }
        captureBottomWeights(panels);
        var left = panels[boundary];
        var right = panels[boundary + 1];
        var startLeft = panelWidth(left);
        var startRight = panelWidth(right);
        if (startLeft <= 0 || startRight <= 0) {
            return;
        }
        activeSplit = {
            left: left,
            right: right,
            startX: Number(event.clientX) || 0,
            startLeft: startLeft,
            startRight: startRight,
            leftWidth: startLeft,
            rightWidth: startRight
        };
        if (event && typeof event.preventDefault === 'function') {
            event.preventDefault();
        }
        if (document.body && document.body.style) {
            document.body.style.userSelect = 'none';
            document.body.style.cursor = 'col-resize';
        }
    }

    function ensureSplitters() {
        var bottom = bottomContainer();
        if (!bottom || splitters.length) {
            return;
        }
        for (var i = 0; i < PANEL_IDS.length - 1; i++) {
            (function () {
                var splitter = document.createElement('div');
                splitter.className = 'kanger-bottom-splitter';
                splitter.setAttribute('role', 'separator');
                splitter.setAttribute('aria-orientation', 'vertical');
                splitter.title = 'Drag to resize panels';
                splitterStyle(splitter);
                splitter.addEventListener('mousedown', function (event) {
                    beginSplit(event, splitter);
                });
                bottom.appendChild(splitter);
                splitters.push(splitter);
            }());
        }
    }

    function applyBottomLayout() {
        var bottom = bottomContainer();
        if (!bottom) {
            return;
        }
        ensureSplitters();
        var panels = visiblePanels();
        var visibleIds = {};
        var i;
        for (i = 0; i < panels.length; i++) {
            visibleIds[panels[i].id] = true;
            var weight = Number(bottomWeights[panels[i].id]);
            if (!isFinite(weight) || weight <= 0) {
                weight = 1;
            }
            panels[i].style.flex = weight + ' 1 0px';
            panels[i].style.width = 'auto';
            panels[i].style.height = '100%';
            panels[i].style.order = String(i * 2);
        }
        var all = allPanels();
        for (i = 0; i < all.length; i++) {
            if (!visibleIds[all[i].id]) {
                all[i].style.order = String(PANEL_IDS.length * 2 + i);
            }
        }
        for (i = 0; i < splitters.length; i++) {
            var splitter = splitters[i];
            if (i < panels.length - 1) {
                splitter.__kangerBoundary = i;
                splitter.style.display = '';
                splitter.style.order = String(i * 2 + 1);
                splitterStyle(splitter);
            } else {
                splitter.style.display = 'none';
            }
        }
    }

    function onSplitMove(event) {
        if (!activeSplit) {
            return;
        }
        var delta = (Number(event.clientX) || 0) - activeSplit.startX;
        var total = activeSplit.startLeft + activeSplit.startRight;
        var minimum = Math.min(100, Math.max(40, total / 4));
        var leftWidth = activeSplit.startLeft + delta;
        leftWidth = Math.max(minimum, Math.min(total - minimum, leftWidth));
        var rightWidth = total - leftWidth;
        activeSplit.leftWidth = leftWidth;
        activeSplit.rightWidth = rightWidth;
        activeSplit.left.style.flex = '0 0 ' + leftWidth + 'px';
        activeSplit.right.style.flex = '0 0 ' + rightWidth + 'px';
        if (event && typeof event.preventDefault === 'function') {
            event.preventDefault();
        }
    }

    function finishSplit() {
        if (!activeSplit) {
            return;
        }
        bottomWeights[activeSplit.left.id] = activeSplit.leftWidth;
        bottomWeights[activeSplit.right.id] = activeSplit.rightWidth;
        activeSplit = null;
        if (document.body && document.body.style) {
            document.body.style.userSelect = '';
            document.body.style.cursor = '';
        }
        applyBottomLayout();
    }

    function styleValue(node, property) {
        if (!node || !node.style) {
            return {value: '', priority: ''};
        }
        if (typeof node.style.getPropertyValue === 'function') {
            return {
                value: node.style.getPropertyValue(property),
                priority: typeof node.style.getPropertyPriority === 'function'
                        ? node.style.getPropertyPriority(property) : ''
            };
        }
        return {value: stringValue(node.style[property]), priority: ''};
    }

    function rememberEditorStyle(node) {
        var names = [
            'position', 'top', 'right', 'bottom', 'left',
            'width', 'height', 'min-height', 'z-index',
            'background', 'flex', 'margin', 'padding'
        ];
        var values = {};
        for (var i = 0; i < names.length; i++) {
            values[names[i]] = styleValue(node, names[i]);
        }
        return values;
    }

    function setImportant(node, property, value) {
        if (node && node.style && typeof node.style.setProperty === 'function') {
            node.style.setProperty(property, value, 'important');
        } else if (node && node.style) {
            node.style[property] = value;
        }
    }

    function restoreEditorStyle(node, values) {
        if (!node || !node.style || !values) {
            return;
        }
        for (var name in values) {
            if (!Object.prototype.hasOwnProperty.call(values, name)) {
                continue;
            }
            var item = values[name];
            if (typeof node.style.setProperty === 'function') {
                node.style.setProperty(name, item.value, item.priority || '');
            } else {
                node.style[name] = item.value;
            }
        }
    }

    function activateEditorOverlay() {
        if (editorOverlayActive) {
            return;
        }
        var shell = document.getElementById('container-console');
        var editor = document.getElementById('editor');
        if (!shell || !editor || editor.style.display === 'none') {
            return;
        }
        editorOverlayActive = true;
        editorHome = shell.parentNode;
        editorNext = shell.nextSibling;
        editorStyle = rememberEditorStyle(shell);
        if (document.body && shell.parentNode !== document.body) {
            document.body.appendChild(shell);
        }
        setImportant(shell, 'position', 'fixed');
        setImportant(shell, 'top', 'var(--kanger-header)');
        setImportant(shell, 'right', '0');
        setImportant(shell, 'bottom', 'var(--kanger-footer)');
        setImportant(shell, 'left', '0');
        setImportant(shell, 'width', '100vw');
        setImportant(shell, 'height',
                'calc(100vh - var(--kanger-header) - var(--kanger-footer))');
        setImportant(shell, 'min-height', '0');
        setImportant(shell, 'z-index', '10000');
        setImportant(shell, 'background', '#fff');
        setImportant(shell, 'flex', 'none');
        setImportant(shell, 'margin', '0');
        setImportant(shell, 'padding', '0');
        setImportant(editor, 'flex', '1 1 auto');
        setImportant(editor, 'height', 'calc(100% - var(--kanger-title))');
        setImportant(editor, 'min-height', '0');
        if (window.editor && typeof window.editor.setSize === 'function') {
            window.editor.setSize('100%', '100%');
        }
        if (window.editor && typeof window.editor.refresh === 'function') {
            window.editor.refresh();
        }
    }

    function deactivateEditorOverlay() {
        if (!editorOverlayActive) {
            return;
        }
        var shell = document.getElementById('container-console');
        var editor = document.getElementById('editor');
        if (shell) {
            restoreEditorStyle(shell, editorStyle);
            if (editorHome && shell.parentNode !== editorHome) {
                if (editorNext && editorNext.parentNode === editorHome
                        && typeof editorHome.insertBefore === 'function') {
                    editorHome.insertBefore(shell, editorNext);
                } else {
                    editorHome.appendChild(shell);
                }
            }
        }
        if (editor) {
            if (typeof editor.style.removeProperty === 'function') {
                editor.style.removeProperty('flex');
                editor.style.removeProperty('height');
                editor.style.removeProperty('min-height');
            } else {
                editor.style.flex = '';
                editor.style.height = '';
                editor.style.minHeight = '';
            }
        }
        editorOverlayActive = false;
        editorHome = null;
        editorNext = null;
        editorStyle = null;
        if (window.KANGER_PRESENTATION
                && typeof window.KANGER_PRESENTATION.refresh === 'function') {
            window.KANGER_PRESENTATION.refresh();
        }
        applyBottomLayout();
    }

    function wrapLayoutEntrypoints() {
        if (typeof window.placeElements === 'function' && !originalPlaceElements) {
            originalPlaceElements = window.placeElements;
            window.placeElements = function () {
                var result = originalPlaceElements.apply(window, arguments);
                applyBottomLayout();
                return result;
            };
        }
        if (typeof window.openConsole === 'function' && !originalOpenConsole) {
            originalOpenConsole = window.openConsole;
            window.openConsole = function () {
                var result = originalOpenConsole.apply(window, arguments);
                deactivateEditorOverlay();
                applyBottomLayout();
                return result;
            };
        }
        if (typeof window.openEditor === 'function' && !originalOpenEditor) {
            originalOpenEditor = window.openEditor;
            window.openEditor = function () {
                var result = originalOpenEditor.apply(window, arguments);
                activateEditorOverlay();
                return result;
            };
        }
    }

    function install() {
        if (installed) {
            return;
        }
        if (!document.body || !window.KANGER_PRESENTATION
                || !window.KANGER_PRESENTATION.installed) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        installed = true;
        ensureSplitters();
        wrapLayoutEntrypoints();
        document.addEventListener('mousemove', onSplitMove, true);
        document.addEventListener('mouseup', finishSplit, true);
        window.addEventListener('resize', function () {
            window.setTimeout(applyBottomLayout, 0);
        });
        document.addEventListener('click', function (event) {
            var node = event.target;
            while (node && node !== document) {
                if (stringValue(node.className).indexOf('kanger-tech-toggle') >= 0) {
                    window.setTimeout(applyBottomLayout, 0);
                    break;
                }
                node = node.parentNode;
            }
        }, false);
        applyBottomLayout();
    }

    window.setTimeout(install, 0);
}(window, document));

/*
 * Canonical TECH telemetry adapter.
 *
 * Owns one internal authenticated read of the hardcoded canonical `status`
 * command when the TECH panel is opened. It deliberately bypasses operator
 * dialogue presentation/history and never polls. Canonical parsing and STATUS
 * semantics remain server-owned; this adapter only renders status.schema=1.
 */
(function (window, document) {
    'use strict';

    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 100;
    var requestSerial = 0;
    var lastStatus = null;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function value(value) {
        return value === null || value === undefined || value === ''
                ? 'unavailable' : String(value);
    }

    function metric(value) {
        return value === null || value === undefined
                ? 'unavailable' : String(value);
    }

    function bytes(value) {
        if (value === null || value === undefined) {
            return 'unavailable';
        }
        var amount = Number(value);
        if (!isFinite(amount) || amount < 0) {
            return 'unavailable';
        }
        var units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
        var scaled = amount;
        var index = 0;
        while (scaled >= 1024 && index < units.length - 1) {
            scaled /= 1024;
            index += 1;
        }
        var shown = index === 0 ? String(Math.round(scaled))
                : scaled.toFixed(scaled >= 100 ? 0 : (scaled >= 10 ? 1 : 2));
        return shown + ' ' + units[index];
    }

    function uptime(value) {
        if (value === null || value === undefined) {
            return 'unavailable';
        }
        var millis = Number(value);
        if (!isFinite(millis) || millis < 0) {
            return 'unavailable';
        }
        var seconds = Math.floor(millis / 1000);
        var days = Math.floor(seconds / 86400);
        seconds %= 86400;
        var hours = Math.floor(seconds / 3600);
        seconds %= 3600;
        var minutes = Math.floor(seconds / 60);
        seconds %= 60;
        var parts = [];
        if (days) {
            parts.push(days + 'd');
        }
        if (hours || days) {
            parts.push(hours + 'h');
        }
        if (minutes || hours || days) {
            parts.push(minutes + 'm');
        }
        parts.push(seconds + 's');
        return parts.join(' ');
    }

    function setText(id, text) {
        var node = document.getElementById(id);
        if (node) {
            node.textContent = stringValue(text);
        }
    }

    function createRow(parent, id) {
        var row = document.createElement('div');
        row.id = id;
        row.className = 'kanger-tech-row';
        parent.appendChild(row);
    }

    function createSection(parent, title, rows) {
        var section = document.createElement('div');
        section.className = 'kanger-tech-section';
        var heading = document.createElement('div');
        heading.className = 'kanger-tech-heading';
        heading.textContent = title;
        section.appendChild(heading);
        for (var i = 0; i < rows.length; i++) {
            createRow(section, rows[i]);
        }
        parent.appendChild(section);
    }

    function ensureSections() {
        if (document.getElementById('tech-status-state')) {
            return true;
        }
        var panel = document.getElementById('technical-panel');
        if (!panel || !panel.childNodes || panel.childNodes.length < 2) {
            return false;
        }
        var body = panel.childNodes[1];
        if (!body) {
            return false;
        }
        createSection(body, 'Canonical STATUS', [
            'tech-status-state', 'tech-status-source'
        ]);
        createSection(body, 'Core', [
            'tech-core-transaction', 'tech-core-levels',
            'tech-core-pending', 'tech-core-objects'
        ]);
        createSection(body, 'Storage', [
            'tech-canonical-storage', 'tech-storage-backend',
            'tech-storage-volume', 'tech-storage-wal',
            'tech-storage-cache', 'tech-storage-cache-io'
        ]);
        createSection(body, 'Session', [
            'tech-canonical-session', 'tech-session-user-dir',
            'tech-session-database-dir', 'tech-session-sources-dir'
        ]);
        createSection(body, 'Runtime', [
            'tech-runtime-version', 'tech-runtime-build',
            'tech-runtime-java', 'tech-runtime-jvm',
            'tech-runtime-uptime', 'tech-runtime-heap', 'tech-runtime-os'
        ]);
        return true;
    }

    function presentationOpen() {
        return !!(window.KANGER_PRESENTATION
                && typeof window.KANGER_PRESENTATION.snapshot === 'function'
                && window.KANGER_PRESENTATION.snapshot().technicalOpen);
    }

    function operationGeneration() {
        if (!window.KANGER_OPERATION_PROTOCOL
                || typeof window.KANGER_OPERATION_PROTOCOL.snapshot !== 'function') {
            return 0;
        }
        return Number(window.KANGER_OPERATION_PROTOCOL.snapshot().generation) || 0;
    }

    function renderStatus(status, generation) {
        if (!status || Number(status.schema) !== 1) {
            setText('tech-status-state', 'status: unavailable');
            setText('tech-status-source', 'source: invalid canonical snapshot');
            return;
        }
        lastStatus = status;
        setText('tech-status-state', 'status: current');
        setText('tech-status-source', 'source: status.schema=1; generation=' + generation);

        var core = status.core || {};
        var transaction = core.transaction || {};
        var levels = core.levels || {};
        var objects = core.objects || {};
        setText('tech-core-transaction', 'transaction: U'
                + metric(transaction.level)
                + '; compatibility=' + value(transaction.compatibility)
                + '; quiescent=' + metric(transaction.quiescent));
        setText('tech-core-levels', 'mind: current=' + metric(levels.mind)
                + '; root=' + metric(levels.root_mind));
        setText('tech-core-pending', 'pending children: current='
                + metric(transaction.current_pending_children)
                + '; root=' + metric(transaction.root_pending_children));
        setText('tech-core-objects', 'objects: ' + metric(objects.count));

        var storage = status.storage || {};
        setText('tech-canonical-storage', 'storage: '
                + (storage.current === null || storage.current === undefined
                        ? 'none' : String(storage.current))
                + '; state=' + value(storage.state));
        setText('tech-storage-backend', 'backend: ' + value(storage.backend));
        setText('tech-storage-volume', 'volume: bases=' + metric(storage.bases)
                + '; records=' + metric(storage.records)
                + '; physical=' + bytes(storage.physical_bytes));
        setText('tech-storage-wal', 'wal pending bases: '
                + metric(storage.wal_pending_bases));
        setText('tech-storage-cache', 'cache: used=' + bytes(storage.cache_used_bytes)
                + '; max=' + bytes(storage.cache_max_bytes)
                + '; entries=' + metric(storage.cache_entries));
        setText('tech-storage-cache-io', 'cache io: hits=' + metric(storage.cache_hits)
                + '; misses=' + metric(storage.cache_misses)
                + '; evictions=' + metric(storage.cache_evictions));

        var session = status.session || {};
        setText('tech-canonical-session', 'user=' + metric(session.user)
                + '; mind=' + metric(session.mind));
        setText('tech-session-user-dir', 'user.dir: ' + value(session.user_dir));
        setText('tech-session-database-dir', 'database.dir: '
                + value(session.database_dir));
        setText('tech-session-sources-dir', 'sources.dir: '
                + value(session.sources_dir));

        var runtime = status.runtime || {};
        var heap = runtime.heap || {};
        setText('tech-runtime-version', 'version: ' + value(runtime.version));
        setText('tech-runtime-build', 'build: branch=' + value(runtime.source_branch)
                + '; date=' + value(runtime.build_date));
        setText('tech-runtime-java', 'java: ' + value(runtime.java));
        setText('tech-runtime-jvm', 'jvm: ' + value(runtime.jvm));
        setText('tech-runtime-uptime', 'uptime: ' + uptime(runtime.uptime_ms));
        setText('tech-runtime-heap', 'heap: used=' + bytes(heap.used_bytes)
                + '; committed=' + bytes(heap.committed_bytes)
                + '; max=' + bytes(heap.max_bytes));
        setText('tech-runtime-os', 'os: ' + value(runtime.os)
                + '; arch=' + value(runtime.arch));
    }

    function renderFailure(description) {
        setText('tech-status-state', 'status: unavailable');
        setText('tech-status-source', 'source: ' + value(description));
    }

    function requestStatus(retryOnGenerationChange) {
        if (!ensureSections()) {
            return;
        }
        var serial = ++requestSerial;
        var generation = operationGeneration();
        setText('tech-status-state', 'status: loading...');
        setText('tech-status-source', 'source: canonical status');
        if (!window.token || !window.jQuery
                || typeof window.jQuery.post !== 'function') {
            renderFailure('transport unavailable');
            return;
        }
        var request = window.jQuery.post(
                window.apihost,
                JSON.stringify({
                    context: 'dialogue',
                    parameters: {
                        token: window.token,
                        line: 'status'
                    }
                }),
                function (data) {
                    if (serial !== requestSerial || !presentationOpen()) {
                        return;
                    }
                    var currentGeneration = operationGeneration();
                    if (currentGeneration !== generation
                            && retryOnGenerationChange !== false) {
                        window.setTimeout(function () {
                            if (presentationOpen()) {
                                requestStatus(false);
                            }
                        }, 0);
                        return;
                    }
                    if (!data || data.result !== 'OK'
                            || !data.status || Number(data.status.schema) !== 1) {
                        renderFailure(data && data.description
                                ? data.description : 'canonical snapshot unavailable');
                        return;
                    }
                    renderStatus(data.status, currentGeneration);
                });
        if (request && typeof request.fail === 'function') {
            request.fail(function (xhr, state, error) {
                if (serial !== requestSerial || !presentationOpen()) {
                    return;
                }
                renderFailure(error || state || 'transport failure');
            });
        }
    }

    function onToggle(event) {
        var node = event && event.currentTarget;
        if (!node || stringValue(node.className).indexOf('kanger-tech-toggle') < 0) {
            return;
        }
        window.setTimeout(function () {
            if (presentationOpen()) {
                requestStatus(true);
            } else {
                requestSerial += 1;
            }
        }, 0);
    }

    function install() {
        if (installed) {
            return;
        }
        if (!window.KANGER_PRESENTATION || !window.KANGER_PRESENTATION.installed
                || !ensureSections()) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        var panel = document.getElementById('technical-panel');
        var toggle = panel && panel.firstElementChild;
        if (!toggle) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        installed = true;
        toggle.addEventListener('click', onToggle);
        toggle.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
                onToggle({currentTarget: toggle});
            }
        });
        window.KANGER_TECH_STATUS = Object.freeze({
            version: 1,
            installed: true,
            refresh: function () {
                if (presentationOpen()) {
                    requestStatus(true);
                }
            },
            snapshot: function () {
                return lastStatus;
            }
        });
        if (presentationOpen()) {
            requestStatus(true);
        }
    }

    window.setTimeout(install, 0);
}(window, document));

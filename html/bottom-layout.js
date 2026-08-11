/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Final presentation authority for the dynamic bottom panel strip.
 *
 * Historical placeElements() changes panel visibility asynchronously. Earlier
 * presentation refinements sized the strip before that callback completed,
 * while a separate persistence wrapper later restored only the panels that had
 * already been visible. A newly visible panel could therefore inherit a unit
 * flex weight next to persisted pixel-sized weights, keep a stale order, and
 * lose the splitter that should precede it.
 *
 * This adapter owns only geometry. It observes visibility transitions, keeps
 * the canonical Results -> Solutions -> Hypothesis -> Log order, rebuilds the
 * visible splitter boundaries, and persists/restores proportional weights via
 * the parent-owned kanger.layout.v1 broker. It has no command, transport,
 * workspace, session or bearer authority.
 */
document.write('<script src="editor-local-file.js"><\/script>');

/*
 * Legacy account-menu hover compatibility.
 *
 * console.html historically hides the menu synchronously on mouseout from the
 * user-name span and from the menu itself. Because the two nodes are siblings,
 * and because mouseout bubbles while moving between menu items, the dropdown
 * can disappear before the pointer reaches an action. Replace only those
 * inline hover handlers with a short handoff delay. Authentication and account
 * actions remain owned by the historical handlers already attached to items.
 */
(function (window, document) {
    'use strict';

    var closeTimer = null;
    var retries = 0;
    var MAX_RETRIES = 250;
    var CLOSE_DELAY_MS = 250;

    function cancelClose() {
        if (closeTimer !== null) {
            window.clearTimeout(closeTimer);
            closeTimer = null;
        }
    }

    function show(menu) {
        cancelClose();
        menu.style.display = 'block';
    }

    function scheduleClose(menu) {
        cancelClose();
        closeTimer = window.setTimeout(function () {
            menu.style.display = 'none';
            closeTimer = null;
        }, CLOSE_DELAY_MS);
    }

    function install() {
        var trigger = document.getElementById('user-name');
        var menu = document.getElementById('user-menu');
        if (!trigger || !menu) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        if (menu.getAttribute('data-kanger-hover-stable') === '1') {
            return;
        }
        menu.setAttribute('data-kanger-hover-stable', '1');

        trigger.removeAttribute('onmouseover');
        trigger.removeAttribute('onmouseout');
        menu.removeAttribute('onmouseover');
        menu.removeAttribute('onmouseout');

        trigger.addEventListener('mouseenter', function () {
            show(menu);
        });
        trigger.addEventListener('mouseleave', function () {
            scheduleClose(menu);
        });
        menu.addEventListener('mouseenter', function () {
            show(menu);
        });
        menu.addEventListener('mouseleave', function () {
            scheduleClose(menu);
        });
    }

    window.setTimeout(install, 0);
}(window, document));

(function (window, document) {
    'use strict';

    var CHANNEL = 'kanger.layout.v1';
    var PANEL_IDS = [
        'container-results', 'container-solutions',
        'container-hypothesis', 'container-logging'
    ];
    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 250;
    var weights = {};
    var splitters = [];
    var observer = null;
    var visibility = '';
    var scheduled = false;
    var applying = false;

    function panel(id) {
        return document.getElementById(id);
    }

    function visible(node) {
        return !!node && node.style.display !== 'none';
    }

    function visiblePanels() {
        var result = [];
        for (var i = 0; i < PANEL_IDS.length; i++) {
            var node = panel(PANEL_IDS[i]);
            if (visible(node)) {
                result.push(node);
            }
        }
        return result;
    }

    function currentVisibility() {
        var value = [];
        for (var i = 0; i < PANEL_IDS.length; i++) {
            value.push(visible(panel(PANEL_IDS[i])) ? '1' : '0');
        }
        return value.join('');
    }

    function widthOf(node) {
        if (!node || typeof node.getBoundingClientRect !== 'function') {
            return 0;
        }
        var width = Number(node.getBoundingClientRect().width);
        return isFinite(width) && width > 0 ? width : 0;
    }

    function positive(value) {
        var number = Number(value);
        return isFinite(number) && number > 0 ? number : 0;
    }

    function ingest(value) {
        if (!value || typeof value !== 'object' || Array.isArray(value)) {
            return;
        }
        for (var i = 0; i < PANEL_IDS.length; i++) {
            var id = PANEL_IDS[i];
            var weight = positive(value[id]);
            if (weight) {
                weights[id] = weight;
            }
        }
    }

    function averageKnown(panels) {
        var total = 0;
        var count = 0;
        for (var i = 0; i < panels.length; i++) {
            var weight = positive(weights[panels[i].id]);
            if (weight) {
                total += weight;
                count += 1;
            }
        }
        return count ? total / count : 0;
    }

    function initialWeight(node, panels) {
        var stored = positive(weights[node.id]);
        if (stored) {
            return stored;
        }
        var measured = widthOf(node);
        if (measured) {
            return measured;
        }
        var average = averageKnown(panels);
        return average || 1;
    }

    function setImportant(node, name, value) {
        if (node && node.style && typeof node.style.setProperty === 'function') {
            node.style.setProperty(name, value, 'important');
        } else if (node && node.style) {
            node.style[name] = value;
        }
    }

    function reconcile() {
        scheduled = false;
        if (applying) {
            return;
        }
        var bottom = document.getElementById('container-div');
        if (!bottom) {
            return;
        }
        applying = true;
        try {
            var panels = visiblePanels();
            var visibleIds = {};
            var i;

            for (i = 0; i < panels.length; i++) {
                var node = panels[i];
                visibleIds[node.id] = true;
                var weight = initialWeight(node, panels);
                weights[node.id] = weight;
                node.style.order = String(i * 2);
                node.style.flex = weight + ' 1 0px';
                node.style.width = 'auto';
                node.style.height = '100%';
            }

            for (i = 0; i < PANEL_IDS.length; i++) {
                var hidden = panel(PANEL_IDS[i]);
                if (hidden && !visibleIds[hidden.id]) {
                    hidden.style.order = String(PANEL_IDS.length * 2 + i);
                }
            }

            splitters = Array.prototype.slice.call(
                    bottom.querySelectorAll('.kanger-bottom-splitter'));
            for (i = 0; i < splitters.length; i++) {
                var splitter = splitters[i];
                if (i < panels.length - 1) {
                    splitter.style.display = '';
                    splitter.style.order = String(i * 2 + 1);
                    splitter.__kangerBoundary = i;
                    setImportant(splitter, 'flex', '0 0 4px');
                    setImportant(splitter, 'width', '4px');
                    setImportant(splitter, 'min-width', '4px');
                    setImportant(splitter, 'max-width', '4px');
                    setImportant(splitter, 'height', '100%');
                    splitter.style.cursor = 'col-resize';
                } else {
                    splitter.style.display = 'none';
                }
            }
            visibility = currentVisibility();
        } finally {
            applying = false;
        }
    }

    function scheduleReconcile() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        window.setTimeout(reconcile, 0);
    }

    function capture() {
        var panels = visiblePanels();
        if (!panels.length) {
            return;
        }
        var changed = false;
        for (var i = 0; i < panels.length; i++) {
            var width = widthOf(panels[i]);
            if (width) {
                weights[panels[i].id] = width;
                changed = true;
            }
        }
        if (changed && window.parent) {
            window.parent.postMessage({
                channel: CHANNEL,
                type: 'set',
                value: weights
            }, '*');
        }
        scheduleReconcile();
    }

    function requestWeights() {
        if (window.parent) {
            window.parent.postMessage({
                channel: CHANNEL,
                type: 'get'
            }, '*');
        }
    }

    /*
     * Register before the older compatibility persistence listener. The newer
     * authority consumes the parent's value so two independent restore paths
     * cannot race each other after refresh.
     */
    window.addEventListener('message', function (event) {
        if (event.source !== window.parent) {
            return;
        }
        var data = event.data;
        if (!data || data.channel !== CHANNEL || data.type !== 'value') {
            return;
        }
        if (typeof event.stopImmediatePropagation === 'function') {
            event.stopImmediatePropagation();
        }
        ingest(data.value);
        scheduleReconcile();
    }, true);

    function installObserver() {
        if (!window.MutationObserver || observer) {
            return;
        }
        visibility = currentVisibility();
        observer = new window.MutationObserver(function () {
            var next = currentVisibility();
            if (next !== visibility) {
                visibility = next;
                scheduleReconcile();
            }
        });
        for (var i = 0; i < PANEL_IDS.length; i++) {
            var node = panel(PANEL_IDS[i]);
            if (node) {
                observer.observe(node, {
                    attributes: true,
                    attributeFilter: ['style']
                });
            }
        }
    }

    function install() {
        if (installed) {
            return;
        }
        var bottom = document.getElementById('container-div');
        if (!document.body
                || !window.KANGER_PRESENTATION
                || !window.KANGER_PRESENTATION.installed
                || !bottom
                || !bottom.querySelector('.kanger-bottom-splitter')) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }
        installed = true;
        installObserver();
        document.addEventListener('mouseup', function () {
            window.setTimeout(capture, 0);
        }, true);
        window.addEventListener('resize', scheduleReconcile);
        requestWeights();
        reconcile();
        window.KANGER_BOTTOM_LAYOUT_AUTHORITY = Object.freeze({
            version: 1,
            installed: true,
            refresh: scheduleReconcile,
            snapshot: function () {
                return Object.freeze({
                    visibility: currentVisibility(),
                    weights: Object.freeze({
                        results: positive(weights['container-results']),
                        solutions: positive(weights['container-solutions']),
                        hypothesis: positive(weights['container-hypothesis']),
                        log: positive(weights['container-logging'])
                    })
                });
            }
        });
    }

    window.setTimeout(install, 0);
}(window, document));

/*
 * 3.7.0.8 residual Browser-soak convergence.
 *
 * This layer owns only view/session presentation mechanics. It preserves the
 * exact author buffer across Editor <-> Console navigation, scrolls dialogue
 * back to the latest activity, provides one local clear-input action, and
 * retires the competing historical left-splitter geometry path. It delegates
 * Server source retrieval to the existing source-editor function and does not
 * parse or execute KANGER commands.
 */
(function (window, document) {
    'use strict';

    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 300;

    var originalOpenConsole = null;
    var originalOpenEditor = null;
    var originalShowSourceEditor = null;
    var originalShowFunctionEditor = null;
    var originalRefreshScreen = null;

    var sourceEditorActive = false;
    var sourceFetchPending = false;
    var localSourceText = null;
    var localSourceReusable = false;
    var localSourceSignature = '';
    var serverSourceSignature = '';

    var geometryObserver = null;
    var geometryApplying = false;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function editorText() {
        return window.editor && typeof window.editor.getValue === 'function'
                ? stringValue(window.editor.getValue()) : '';
    }

    function editorVisible() {
        var editor = document.getElementById('editor');
        return !!editor && editor.style.display !== 'none';
    }

    function sourceProjection(value) {
        var workspace = value && value.workspace;
        return workspace && workspace.source ? workspace.source : null;
    }

    function sourceSignature(source) {
        if (!source || typeof source !== 'object') {
            return '';
        }
        return [
            stringValue(source.logical_name),
            source.has_text === true ? '1' : '0',
            stringValue(source.bytes_utf8),
            stringValue(source.repository_state),
            source.persisted === true ? '1' : '0',
            source.dirty === true ? '1' : '0'
        ].join('|');
    }

    function snapshotSourceSignature() {
        try {
            var authority = window.KANGER_WORKSPACE_STATE;
            var snapshot = authority
                    && typeof authority.snapshot === 'function'
                    ? authority.snapshot() : null;
            return sourceSignature(sourceProjection(snapshot));
        } catch (ignored) {
            return '';
        }
    }

    function observeServerSource(data) {
        var signature = sourceSignature(sourceProjection(data));
        if (!signature) {
            return;
        }
        serverSourceSignature = signature;

        if (sourceEditorActive && editorVisible()) {
            localSourceText = editorText();
            localSourceReusable = true;
            localSourceSignature = signature;
            return;
        }

        if (localSourceReusable && localSourceSignature
                && localSourceSignature !== signature) {
            localSourceReusable = false;
        }
    }

    function scrollHistoryToLatest() {
        var history = document.getElementById('query-history');
        if (history) {
            history.scrollTop = history.scrollHeight;
        }
    }

    function wrapEditorNavigation() {
        originalOpenConsole = window.openConsole;
        originalOpenEditor = window.openEditor;
        originalShowSourceEditor = window.showSourceEditor;
        originalShowFunctionEditor = window.showFunctionEditor;
        originalRefreshScreen = window.refreshScreen;

        window.openEditor = function () {
            if (sourceFetchPending && arguments.length
                    && arguments[0] !== null && arguments[0] !== undefined) {
                localSourceText = stringValue(arguments[0]);
                localSourceReusable = true;
                localSourceSignature = serverSourceSignature
                        || snapshotSourceSignature();
                sourceFetchPending = false;
                sourceEditorActive = true;
            }
            return originalOpenEditor.apply(window, arguments);
        };

        window.openConsole = function () {
            if (sourceEditorActive && editorVisible()) {
                localSourceText = editorText();
                localSourceReusable = true;
                var signature = serverSourceSignature
                        || snapshotSourceSignature();
                if (signature) {
                    localSourceSignature = signature;
                }
            }
            var result = originalOpenConsole.apply(window, arguments);
            sourceEditorActive = false;
            window.setTimeout(scrollHistoryToLatest, 0);
            return result;
        };

        window.showSourceEditor = function () {
            var current = serverSourceSignature || snapshotSourceSignature();
            if (current) {
                serverSourceSignature = current;
            }
            if (localSourceReusable && localSourceText !== null
                    && (!current || !localSourceSignature
                        || current === localSourceSignature)) {
                sourceEditorActive = true;
                window.openEditor(localSourceText);
                return;
            }
            sourceFetchPending = true;
            sourceEditorActive = false;
            return originalShowSourceEditor.apply(window, arguments);
        };

        window.showFunctionEditor = function () {
            sourceFetchPending = false;
            sourceEditorActive = false;
            return originalShowFunctionEditor.apply(window, arguments);
        };

        window.refreshScreen = function () {
            var data = arguments.length ? arguments[0] : null;
            var result = originalRefreshScreen.apply(window, arguments);
            observeServerSource(data);
            return result;
        };
    }

    function ensureClearInput() {
        var container = document.getElementById('console-input');
        var input = document.getElementById('query-input');
        if (!container || !input || document.getElementById('kanger-query-clear')) {
            return;
        }

        var button = document.createElement('button');
        button.id = 'kanger-query-clear';
        button.type = 'button';
        button.textContent = '×';
        button.title = 'Clear command input';
        button.setAttribute('aria-label', 'Clear command input');

        button.style.boxSizing = 'border-box';
        button.style.flex = '0 0 18px';
        button.style.width = '18px';
        button.style.height = '18px';
        button.style.margin = '0 6px 0 2px';
        button.style.padding = '0';
        button.style.border = '1px solid #8d969e';
        button.style.borderRadius = '50%';
        button.style.background = 'transparent';
        button.style.color = '#59636c';
        button.style.fontFamily = 'Arial, sans-serif';
        button.style.fontSize = '15px';
        button.style.fontWeight = 'normal';
        button.style.lineHeight = '14px';
        button.style.textAlign = 'center';
        button.style.cursor = 'pointer';

        button.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            input.value = '';
            input.style.height = '20px';
            input.focus();
        });
        container.appendChild(button);
    }

    function leftWidthBounds() {
        var container = document.getElementById('container');
        var total = container && container.clientWidth
                ? container.clientWidth : document.documentElement.clientWidth;
        var minimum = 120;
        var maximum = Math.max(minimum, total - 190);
        return {minimum: minimum, maximum: maximum};
    }

    function setLeftWidth(value, persist) {
        var numeric = Number(value);
        if (!isFinite(numeric)) {
            return;
        }
        var bounds = leftWidthBounds();
        numeric = Math.max(bounds.minimum, Math.min(bounds.maximum, numeric));
        document.documentElement.style.setProperty('--kanger-left', numeric + 'px');
        if (persist && typeof window.setCookie === 'function') {
            window.setCookie('sx', numeric + 'px');
        }
    }

    function sanitizeLegacyGeometry() {
        if (geometryApplying) {
            return;
        }
        geometryApplying = true;
        try {
            var left = document.getElementById('container-left');
            var right = document.getElementById('container-right');
            if (left && left.style && left.style.width) {
                left.style.removeProperty('width');
            }
            if (right && right.style) {
                if (right.style.width) {
                    right.style.removeProperty('width');
                }
                if (right.style.left) {
                    right.style.removeProperty('left');
                }
            }
        } finally {
            geometryApplying = false;
        }
    }

    function observeLegacyGeometry() {
        if (!window.MutationObserver || geometryObserver) {
            return;
        }
        geometryObserver = new window.MutationObserver(function () {
            sanitizeLegacyGeometry();
        });
        var left = document.getElementById('container-left');
        var right = document.getElementById('container-right');
        if (left) {
            geometryObserver.observe(left, {
                attributes: true,
                attributeFilter: ['style']
            });
        }
        if (right) {
            geometryObserver.observe(right, {
                attributes: true,
                attributeFilter: ['style']
            });
        }
        sanitizeLegacyGeometry();
    }

    function replaceLeftSplitter() {
        var oldHandle = document.getElementById('container-left-size');
        if (!oldHandle || !oldHandle.parentNode) {
            return false;
        }

        var handle = oldHandle.cloneNode(true);
        handle.removeAttribute('onmousedown');
        oldHandle.parentNode.replaceChild(handle, oldHandle);

        window.sizeX = null;
        window.widthFrom = null;

        var saved = typeof window.getCookie === 'function'
                ? parseFloat(stringValue(window.getCookie('sx'))) : NaN;
        if (isFinite(saved)) {
            setLeftWidth(saved, false);
        }

        handle.addEventListener('mousedown', function (event) {
            if (event.button !== undefined && event.button !== 0) {
                return;
            }
            event.preventDefault();
            event.stopImmediatePropagation();
            window.sizeX = null;

            function move(next) {
                next.preventDefault();
                next.stopImmediatePropagation();
                window.sizeX = null;
                setLeftWidth(next.clientX, true);
                sanitizeLegacyGeometry();
            }

            function finish(next) {
                if (next && typeof next.preventDefault === 'function') {
                    next.preventDefault();
                }
                if (next && typeof next.stopImmediatePropagation === 'function') {
                    next.stopImmediatePropagation();
                }
                window.sizeX = null;
                document.removeEventListener('mousemove', move, true);
                document.removeEventListener('mouseup', finish, true);
            }

            document.addEventListener('mousemove', move, true);
            document.addEventListener('mouseup', finish, true);
            setLeftWidth(event.clientX, true);
            sanitizeLegacyGeometry();
        }, true);
        return true;
    }

    function install() {
        if (installed) {
            return;
        }
        if (!document.body
                || !window.KANGER_PRESENTATION
                || !window.KANGER_DIALOGUE_TRANSPORT
                || !window.KANGER_SOAK_CORRECTIONS
                || typeof window.openConsole !== 'function'
                || typeof window.openEditor !== 'function'
                || typeof window.showSourceEditor !== 'function'
                || typeof window.showFunctionEditor !== 'function'
                || typeof window.refreshScreen !== 'function'
                || !window.editor
                || !document.getElementById('container-left-size')) {
            retries += 1;
            if (retries <= MAX_RETRIES) {
                window.setTimeout(install, 10);
            }
            return;
        }

        installed = true;
        serverSourceSignature = snapshotSourceSignature();
        wrapEditorNavigation();
        ensureClearInput();
        replaceLeftSplitter();
        observeLegacyGeometry();

        window.KANGER_BROWSER_SOAK_CONVERGENCE = Object.freeze({
            version: 1,
            installed: true,
            exactEditorViewReuse: true,
            leftGeometryAuthority: '--kanger-left'
        });
    }

    window.setTimeout(install, 0);
}(window, document));

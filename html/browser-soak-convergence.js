/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Final Browser convergence layer for the residual 3.7.0.7 VPS-soak findings.
 *
 * This layer owns presentation/session mechanics only:
 *   - preserve the exact source-editor document across Editor <-> Console view
 *     changes without reloading a compiler-normalized representation;
 *   - return dialogue history to the latest activity when leaving Editor;
 *   - provide one local clear-input control;
 *   - make the CSS-grid custom property the only live left-splitter geometry
 *     owner and remove the legacy inline drag listener from that handle.
 *
 * It does not parse or execute KANGER commands. Workspace change detection uses
 * only the authoritative Server workspace projection already present in
 * responses.
 */
(function (window, document) {
    'use strict';

    var installed = false;
    var retries = 0;
    var MAX_RETRIES = 300;

    var originalOpenConsole = null;
    var originalShowSourceEditor = null;
    var originalShowFunctionEditor = null;
    var originalRefreshScreen = null;

    var sourceEditorActive = false;
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
            // A compile response arrives while the exact author buffer is still
            // alive in CodeMirror. Bind that buffer to the resulting Server
            // projection instead of later re-reading a reconstructed source.
            localSourceText = editorText();
            localSourceReusable = true;
            localSourceSignature = signature;
            return;
        }

        if (localSourceReusable && localSourceSignature
                && localSourceSignature !== signature) {
            // An explicit Server-side workspace/source transition occurred
            // while Console was active. The next Editor open must fetch that
            // authoritative source instead of reviving an older local buffer.
            localSourceReusable = false;
        }
    }

    function scrollHistoryToLatest() {
        var history = document.getElementById('query-history');
        if (history) {
            history.scrollTop = history.scrollHeight;
        }
    }

    function openSourceFromServer() {
        window.post({
            context: 'query',
            parameters: {
                token: window.token,
                source: ''
            }
        }, function (data) {
            if (!data || data.result !== 'OK') {
                return;
            }
            var signature = sourceSignature(sourceProjection(data));
            if (signature) {
                serverSourceSignature = signature;
                localSourceSignature = signature;
            } else {
                var snapshot = snapshotSourceSignature();
                if (snapshot) {
                    serverSourceSignature = snapshot;
                    localSourceSignature = snapshot;
                }
            }
            localSourceText = stringValue(data.source);
            localSourceReusable = true;
            sourceEditorActive = true;
            window.openEditor(localSourceText);
        });
    }

    function wrapEditorNavigation() {
        originalOpenConsole = window.openConsole;
        originalShowSourceEditor = window.showSourceEditor;
        originalShowFunctionEditor = window.showFunctionEditor;
        originalRefreshScreen = window.refreshScreen;

        window.openConsole = function () {
            if (sourceEditorActive && editorVisible()) {
                localSourceText = editorText();
                localSourceReusable = true;
                var signature = serverSourceSignature || snapshotSourceSignature();
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
            openSourceFromServer();
        };

        window.showFunctionEditor = function () {
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

        // Cloning is intentional: it removes the anonymous 3.7.0.7 listener
        // as well as the historical inline onmousedown handler in one atomic
        // DOM replacement, leaving exactly one drag authority below.
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
                || typeof window.showSourceEditor !== 'function'
                || typeof window.showFunctionEditor !== 'function'
                || typeof window.refreshScreen !== 'function'
                || typeof window.post !== 'function'
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

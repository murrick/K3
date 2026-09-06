/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 *
 * Canonical TECH telemetry adapter.
 *
 * Owns one internal parent-brokered authenticated read of canonical STATUS
 * telemetry when the TECH panel is opened. It deliberately bypasses operator
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

    function buildDate(value) {
        var text = value === null || value === undefined ? '' : String(value);
        return text ? text.replace('_', ' ') : 'unavailable';
    }

    function injectStyles() {
        if (!document.head || document.getElementById('kanger-tech-status-css')) {
            return;
        }
        var style = document.createElement('style');
        style.id = 'kanger-tech-status-css';
        style.textContent = [
            ':root { --kanger-tech-open: 25vw; }',
            'body.kanger-presentation .kanger-tech-body { font-variant-numeric: tabular-nums; }',
            'body.kanger-presentation .kanger-tech-section { margin-bottom: 16px; }',
            'body.kanger-presentation .kanger-tech-heading-line { display: flex; align-items: center; gap: 8px; }',
            'body.kanger-presentation .kanger-tech-heading-text { flex: 1 1 auto; min-width: 0; }',
            'body.kanger-presentation .kanger-tech-canonical-boundary { border-top: 1px solid var(--kanger-border); margin-top: 2px; padding-top: 14px; }',
            'body.kanger-presentation .kanger-tech-metric-row { display: grid; grid-template-columns: 86px minmax(0, 1fr); column-gap: 8px; align-items: baseline; min-height: 18px; white-space: normal; }',
            'body.kanger-presentation .kanger-tech-label { color: var(--kanger-muted); font-family: helvetica, sans-serif; font-size: 10px; line-height: 1.45; }',
            'body.kanger-presentation .kanger-tech-value { min-width: 0; color: var(--kanger-ink); overflow-wrap: anywhere; }',
            'body.kanger-presentation .kanger-tech-secondary .kanger-tech-value { color: var(--kanger-muted); }',
            'body.kanger-presentation .kanger-tech-path .kanger-tech-value { color: #4d5862; font-size: 11px; }',
            'body.kanger-presentation .kanger-tech-badge { flex: 0 0 auto; box-sizing: border-box; min-height: 16px; padding: 1px 5px; border: 1px solid #c6ccd1; border-radius: 8px; background: #edf0f2; color: #4b555e; font-family: helvetica, sans-serif; font-size: 9px; font-weight: bold; letter-spacing: .04em; line-height: 12px; white-space: nowrap; }',
            'body.kanger-presentation .kanger-tech-badge-ok { border-color: #aebdb5; background: #e5ebe8; color: #34463d; }',
            'body.kanger-presentation .kanger-tech-badge-warn { border-color: #c8b8ae; background: #eee9e5; color: #604b40; }',
            'body.kanger-presentation .kanger-tech-metric-row[hidden] { display: none !important; }',
            '@media (max-width: 920px) { body.kanger-presentation .kanger-tech-metric-row { grid-template-columns: 74px minmax(0, 1fr); column-gap: 6px; } }'
        ].join('\n');
        document.head.appendChild(style);
    }

    function createRow(parent, specification) {
        var row = document.createElement('div');
        row.id = specification.id;
        row.className = 'kanger-tech-row kanger-tech-metric-row'
                + (specification.className ? ' ' + specification.className : '');

        var label = document.createElement('span');
        label.className = 'kanger-tech-label';
        label.textContent = specification.label;
        row.appendChild(label);

        var metricNode = document.createElement('span');
        metricNode.id = specification.id + '-value';
        metricNode.className = 'kanger-tech-value';
        row.appendChild(metricNode);

        parent.appendChild(row);
    }

    function createSection(parent, title, rows, options) {
        options = options || {};
        var section = document.createElement('div');
        section.className = 'kanger-tech-section'
                + (options.boundary ? ' kanger-tech-canonical-boundary' : '');

        var heading = document.createElement('div');
        heading.className = 'kanger-tech-heading kanger-tech-heading-line';

        var headingText = document.createElement('span');
        headingText.className = 'kanger-tech-heading-text';
        headingText.textContent = title;
        heading.appendChild(headingText);

        if (options.badgeId) {
            var badge = document.createElement('span');
            badge.id = options.badgeId;
            badge.className = 'kanger-tech-badge';
            heading.appendChild(badge);
        }

        section.appendChild(heading);
        for (var i = 0; i < rows.length; i++) {
            createRow(section, rows[i]);
        }
        parent.appendChild(section);
    }

    function setMetric(id, text) {
        var node = document.getElementById(id + '-value');
        if (node) {
            node.textContent = stringValue(text);
        }
    }

    function setBadge(id, text, state) {
        var node = document.getElementById(id);
        if (!node) {
            return;
        }
        node.textContent = stringValue(text);
        node.className = 'kanger-tech-badge'
                + (state ? ' kanger-tech-badge-' + state : '');
    }

    function setHidden(id, hidden) {
        var node = document.getElementById(id);
        if (node) {
            node.hidden = !!hidden;
        }
    }

    function ensureSections() {
        injectStyles();
        if (document.getElementById('tech-status-generation')) {
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
            {id: 'tech-status-schema', label: 'Schema'},
            {id: 'tech-status-generation', label: 'Generation'},
            {id: 'tech-status-source', label: 'Source', className: 'kanger-tech-secondary'}
        ], {boundary: true, badgeId: 'tech-status-badge'});

        createSection(body, 'Core', [
            {id: 'tech-core-transaction', label: 'Transaction'},
            {id: 'tech-core-compatibility', label: 'Compatibility'},
            {id: 'tech-core-state', label: 'State'},
            {id: 'tech-core-levels', label: 'Mind'},
            {id: 'tech-core-pending', label: 'Pending'},
            {id: 'tech-core-objects', label: 'Objects'}
        ]);

        createSection(body, 'Storage', [
            {id: 'tech-canonical-storage', label: 'Name'},
            {id: 'tech-storage-backend', label: 'Backend'},
            {id: 'tech-storage-volume', label: 'Volume'},
            {id: 'tech-storage-wal', label: 'WAL'},
            {id: 'tech-storage-cache', label: 'Cache'},
            {id: 'tech-storage-cache-io', label: 'Cache I/O'}
        ], {badgeId: 'tech-storage-badge'});

        createSection(body, 'Session', [
            {id: 'tech-session-user', label: 'User'},
            {id: 'tech-session-mind', label: 'Mind'},
            {id: 'tech-session-user-dir', label: 'Home', className: 'kanger-tech-path'},
            {id: 'tech-session-database-dir', label: 'Database', className: 'kanger-tech-path'},
            {id: 'tech-session-sources-dir', label: 'Sources', className: 'kanger-tech-path'}
        ]);

        createSection(body, 'Runtime', [
            {id: 'tech-runtime-build', label: 'Build'},
            {id: 'tech-runtime-built', label: 'Built'},
            {id: 'tech-runtime-java', label: 'Java'},
            {id: 'tech-runtime-system', label: 'System'},
            {id: 'tech-runtime-uptime', label: 'Uptime'},
            {id: 'tech-runtime-heap', label: 'Heap'}
        ], {badgeId: 'tech-runtime-badge'});
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
            renderFailure('invalid canonical snapshot');
            return;
        }
        lastStatus = status;
        setBadge('tech-status-badge', 'CURRENT', 'ok');
        setMetric('tech-status-schema', status.schema);
        setMetric('tech-status-generation', generation);
        setHidden('tech-status-source', true);

        var core = status.core || {};
        var transaction = core.transaction || {};
        var levels = core.levels || {};
        var objects = core.objects || {};
        var quiescent = transaction.quiescent;
        setMetric('tech-core-transaction', 'U' + metric(transaction.level));
        setMetric('tech-core-compatibility', value(transaction.compatibility));
        setMetric('tech-core-state', quiescent === true ? 'QUIESCENT'
                : (quiescent === false ? 'ACTIVE' : 'unavailable'));
        setMetric('tech-core-levels', 'current ' + metric(levels.mind)
                + ' · root ' + metric(levels.root_mind));
        setMetric('tech-core-pending', 'current '
                + metric(transaction.current_pending_children)
                + ' · root ' + metric(transaction.root_pending_children));
        setMetric('tech-core-objects', metric(objects.count));

        var storage = status.storage || {};
        var storageName = storage.current === null || storage.current === undefined
                ? 'none' : String(storage.current);
        var storageState = value(storage.state);
        setBadge('tech-storage-badge', storageState.toUpperCase(),
                storageState === 'open' ? 'ok' : 'neutral');
        setMetric('tech-canonical-storage', storageName);
        setMetric('tech-storage-backend', value(storage.backend));
        setMetric('tech-storage-volume', metric(storage.bases) + ' bases · '
                + metric(storage.records) + ' records · '
                + bytes(storage.physical_bytes));
        setMetric('tech-storage-wal', metric(storage.wal_pending_bases)
                + ' pending bases');
        setMetric('tech-storage-cache', bytes(storage.cache_used_bytes)
                + ' / ' + bytes(storage.cache_max_bytes)
                + ' · ' + metric(storage.cache_entries) + ' entries');
        setMetric('tech-storage-cache-io', metric(storage.cache_hits) + ' hits · '
                + metric(storage.cache_misses) + ' misses · '
                + metric(storage.cache_evictions) + ' evictions');

        var session = status.session || {};
        setMetric('tech-session-user', metric(session.user));
        setMetric('tech-session-mind', metric(session.mind));
        setMetric('tech-session-user-dir', value(session.user_dir));
        setMetric('tech-session-database-dir', value(session.database_dir));
        setMetric('tech-session-sources-dir', value(session.sources_dir));

        var runtime = status.runtime || {};
        var heap = runtime.heap || {};
        setBadge('tech-runtime-badge', value(runtime.version), 'neutral');
        setMetric('tech-runtime-build', value(runtime.source_branch));
        setMetric('tech-runtime-built', buildDate(runtime.build_date));
        setMetric('tech-runtime-java', value(runtime.java) + ' · ' + value(runtime.jvm));
        setMetric('tech-runtime-system', value(runtime.os) + ' · ' + value(runtime.arch));
        setMetric('tech-runtime-uptime', uptime(runtime.uptime_ms));
        setMetric('tech-runtime-heap', bytes(heap.used_bytes) + ' / '
                + bytes(heap.committed_bytes) + ' / ' + bytes(heap.max_bytes));
    }

    function renderFailure(description) {
        setBadge('tech-status-badge', 'UNAVAILABLE', 'warn');
        setMetric('tech-status-schema', 'unavailable');
        setMetric('tech-status-generation', operationGeneration());
        setMetric('tech-status-source', value(description));
        setHidden('tech-status-source', false);
    }

    function renderLoading() {
        setBadge('tech-status-badge', 'LOADING', 'neutral');
        setMetric('tech-status-schema', '1');
        setMetric('tech-status-generation', operationGeneration());
        setHidden('tech-status-source', true);
    }

    function requestStatus(retryOnGenerationChange) {
        if (!ensureSections()) {
            return;
        }
        var serial = ++requestSerial;
        var generation = operationGeneration();
        renderLoading();
        if (typeof window.post !== 'function') {
            renderFailure('transport unavailable');
            return;
        }
        try {
            window.post({
                context: 'command',
                parameters: {
                    status: ''
                }
            }, function (data) {
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
        } catch (error) {
            if (serial !== requestSerial || !presentationOpen()) {
                return;
            }
            renderFailure(error && error.message
                    ? error.message : 'transport failure');
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
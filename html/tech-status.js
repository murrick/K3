/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 *
 * Canonical TECH telemetry adapter.
 *
 * Owns one internal parent-brokered authenticated read of the hardcoded
 * canonical `status` command when the TECH panel is opened. It deliberately
 * bypasses operator dialogue presentation/history and never polls. Canonical
 * parsing and STATUS semantics remain server-owned; this adapter only renders
 * status.schema=1.
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
        if (typeof window.post !== 'function') {
            renderFailure('transport unavailable');
            return;
        }
        try {
            window.post({
                context: 'dialogue',
                parameters: {
                    line: 'status'
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
/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Supported KANGER browser operation and snapshot protocol.
 *
 * This adapter is loaded after the trusted-rendering adapter and before the
 * historical console registers its jQuery.ready callback. It serializes
 * authoritative mutations, rejects stale callbacks and commits semantic
 * screen refreshes as one generation-consistent snapshot.
 */
(function (window, document) {
    'use strict';

    var DEFAULT_OPERATION_TIMEOUT_MS = 20000;
    var configuredTimeout = Number(window.KANGER_OPERATION_TIMEOUT_MS);
    var OPERATION_TIMEOUT_MS = isFinite(configuredTimeout)
            && configuredTimeout > 0 ? configuredTimeout
            : DEFAULT_OPERATION_TIMEOUT_MS;
    var SNAPSHOT_TARGET_IDS = [
        'statements',
        'functions',
        'query-results',
        'query-solutions',
        'query-hypothesis',
        'query-log'
    ];
    var SNAPSHOT_RENDERERS = [
        'showStatements',
        'showFunctions',
        'showResults',
        'showSolutions',
        'showHypothesis',
        'showLog'
    ];

    var installed = false;
    var state = {
        generation: 0,
        nextOperationId: 0,
        nextSnapshotId: 0,
        activeMutation: null,
        settlingMutation: null,
        currentSnapshot: null,
        snapshotCapture: null,
        snapshotRequested: false,
        snapshotTimer: null,
        pendingSnapshotData: null,
        lastCommittedSnapshotId: 0
    };

    var original = {};
    var originalGetElementById = null;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function hasOwn(object, name) {
        return !!object && Object.prototype.hasOwnProperty.call(object, name);
    }

    function clearElement(element) {
        if (!element) {
            return;
        }
        while (element.firstChild) {
            element.removeChild(element.firstChild);
        }
    }

    function requestParameters(packet) {
        return packet && packet.parameters && typeof packet.parameters === 'object'
                ? packet.parameters : {};
    }

    function isHistoryPacket(packet) {
        return !!packet && packet.context === 'history';
    }

    function nonEmpty(parameters, key) {
        return hasOwn(parameters, key)
                && parameters[key] !== null
                && parameters[key] !== undefined
                && stringValue(parameters[key]) !== '';
    }

    function isMutationPacket(packet) {
        if (!packet || typeof packet !== 'object') {
            return false;
        }
        // Raw dialogue is intentionally classified conservatively as one
        // serialized operation. The Browser must not parse the command line to
        // decide whether a canonical intent is read-only or mutating.
        if (packet.context === 'dialogue') {
            return true;
        }
        var parameters = requestParameters(packet);
        if (packet.context === 'query') {
            return hasOwn(parameters, 'request')
                    || hasOwn(parameters, 'compile')
                    || hasOwn(parameters, 'transaction');
        }
        if (packet.context === 'command') {
            return nonEmpty(parameters, 'get')
                    || nonEmpty(parameters, 'use')
                    || hasOwn(parameters, 'put')
                    || hasOwn(parameters, 'delete')
                    || hasOwn(parameters, 'close')
                    || hasOwn(parameters, 'drop')
                    || hasOwn(parameters, 'reindex')
                    || hasOwn(parameters, 'erase')
                    || hasOwn(parameters, 'quit');
        }
        return packet.context === 'login' || packet.context === 'mail';
    }

    function operationName(packet) {
        if (packet && packet.context === 'dialogue') {
            return 'dialogue';
        }
        var parameters = requestParameters(packet);
        var keys = [
            'compile', 'transaction', 'request', 'get', 'put', 'delete',
            'use', 'close', 'drop', 'reindex', 'erase', 'quit'
        ];
        for (var i = 0; i < keys.length; i++) {
            if (hasOwn(parameters, keys[i])) {
                return keys[i];
            }
        }
        return packet && packet.context ? packet.context : 'operation';
    }

    function decorate(data, values) {
        if (!data || typeof data !== 'object') {
            return data;
        }
        Object.keys(values).forEach(function (name) {
            data[name] = values[name];
        });
        return data;
    }

    function localError(code, description, operationId, blockingId) {
        var data = {
            result: 'error',
            code: code,
            description: description,
            client_operation_id: operationId,
            client_generation: state.generation
        };
        if (blockingId) {
            data.blocking_operation_id = blockingId;
        }
        return data;
    }

    function callSoon(callback, value) {
        if (typeof callback !== 'function') {
            return;
        }
        setTimeout(function () {
            callback(value);
        }, 0);
    }

    function invalidateSnapshot() {
        if (state.currentSnapshot) {
            state.currentSnapshot.cancelled = true;
            state.currentSnapshot = null;
        }
        state.snapshotCapture = null;
    }

    function snapshotIsCurrent(snapshot) {
        return !!snapshot && !snapshot.cancelled
                && state.currentSnapshot === snapshot
                && snapshot.generation === state.generation;
    }

    function moveChildren(from, to) {
        clearElement(to);
        while (from.firstChild) {
            to.appendChild(from.firstChild);
        }
    }

    function completeSettlingMutation(snapshot) {
        if (state.settlingMutation
                && snapshot.generation === state.settlingMutation.generation) {
            state.settlingMutation = null;
            if (typeof window.dropQueryStatus === 'function') {
                window.dropQueryStatus();
            }
        }
    }

    function maybeCommitSnapshot(snapshot) {
        if (!snapshot.closed || snapshot.pending !== 0
                || !snapshotIsCurrent(snapshot)) {
            return;
        }
        for (var i = 0; i < SNAPSHOT_TARGET_IDS.length; i++) {
            var id = SNAPSHOT_TARGET_IDS[i];
            moveChildren(snapshot.staging[id], originalGetElementById(id));
        }
        state.lastCommittedSnapshotId = snapshot.id;
        state.currentSnapshot = null;
        if (typeof original.placeElements === 'function') {
            original.placeElements(snapshot.reasonData || undefined);
        }
        completeSettlingMutation(snapshot);
        if (state.snapshotRequested && !state.activeMutation) {
            scheduleSnapshot(state.pendingSnapshotData);
        }
    }

    function snapshotPost(snapshot, packet, callback) {
        snapshot.pending += 1;
        var completed = false;
        function complete(data) {
            if (completed) {
                return;
            }
            completed = true;
            if (snapshotIsCurrent(snapshot) && typeof callback === 'function') {
                decorate(data, {
                    client_generation: snapshot.generation,
                    client_snapshot_id: snapshot.id
                });
                callback(data);
            }
            snapshot.pending -= 1;
            maybeCommitSnapshot(snapshot);
        }
        try {
            return original.post(packet, complete);
        } catch (error) {
            complete(localError(
                    'snapshot_transport_failed',
                    error && error.message ? error.message
                            : 'Snapshot request failed',
                    0,
                    0));
            return undefined;
        }
    }

    function createStagingTargets() {
        var staging = {};
        for (var i = 0; i < SNAPSHOT_TARGET_IDS.length; i++) {
            var id = SNAPSHOT_TARGET_IDS[i];
            var real = originalGetElementById(id);
            var tagName = real && real.tagName
                    ? stringValue(real.tagName).toLowerCase() : 'div';
            staging[id] = document.createElement(tagName || 'div');
            staging[id].id = id;
        }
        return staging;
    }

    function startSnapshot(reasonData) {
        state.snapshotRequested = false;
        state.pendingSnapshotData = reasonData || state.pendingSnapshotData;
        if (state.activeMutation) {
            state.snapshotRequested = true;
            return;
        }

        invalidateSnapshot();
        var snapshot = {
            id: ++state.nextSnapshotId,
            generation: state.generation,
            pending: 0,
            closed: false,
            cancelled: false,
            staging: createStagingTargets(),
            reasonData: state.pendingSnapshotData
        };
        state.pendingSnapshotData = null;
        state.currentSnapshot = snapshot;
        state.snapshotCapture = snapshot;

        var savedGetElementById = document.getElementById;
        var savedSetQueryStatus = window.setQueryStatus;
        var savedDropQueryStatus = window.dropQueryStatus;
        document.getElementById = function (id) {
            return snapshot.staging[id] || originalGetElementById(id);
        };
        window.setQueryStatus = function () {};
        window.dropQueryStatus = function () {};
        try {
            for (var i = 0; i < SNAPSHOT_RENDERERS.length; i++) {
                var renderer = original[SNAPSHOT_RENDERERS[i]];
                if (typeof renderer === 'function') {
                    renderer();
                }
            }
        } finally {
            document.getElementById = savedGetElementById;
            window.setQueryStatus = savedSetQueryStatus;
            window.dropQueryStatus = savedDropQueryStatus;
            state.snapshotCapture = null;
            snapshot.closed = true;
            maybeCommitSnapshot(snapshot);
        }
    }

    function scheduleSnapshot(reasonData) {
        state.snapshotRequested = true;
        if (reasonData) {
            state.pendingSnapshotData = reasonData;
        }
        if (state.activeMutation || state.snapshotTimer) {
            return;
        }
        state.snapshotTimer = setTimeout(function () {
            state.snapshotTimer = null;
            if (state.snapshotRequested && !state.activeMutation) {
                startSnapshot(state.pendingSnapshotData);
            }
        }, 0);
    }

    function beginMutationSettlement(operation) {
        state.settlingMutation = {
            id: operation.id,
            name: operation.name,
            generation: state.generation
        };
    }

    function finishMutation(operation, data, callback) {
        if (!state.activeMutation
                || state.activeMutation.id !== operation.id) {
            scheduleSnapshot();
            return;
        }
        clearTimeout(operation.timer);
        state.activeMutation = null;
        state.generation += 1;
        decorate(data, {
            client_operation_id: operation.id,
            client_generation: state.generation
        });
        var confirmationPending = !!(data
                && data.result === 'confirmation_required');
        if (confirmationPending) {
            if (typeof window.dropQueryStatus === 'function') {
                window.dropQueryStatus();
            }
        } else {
            beginMutationSettlement(operation);
        }
        if (typeof callback === 'function') {
            callback(data);
        }
        scheduleSnapshot(data);
    }

    function timeoutMutation(operation, callback) {
        if (!state.activeMutation
                || state.activeMutation.id !== operation.id) {
            return;
        }
        state.activeMutation = null;
        state.generation += 1;
        beginMutationSettlement(operation);
        var data = localError(
                'operation_timeout',
                'Operation #' + operation.id
                        + ' timed out; its late response will be ignored.',
                operation.id,
                0);
        data.client_generation = state.generation;
        if (typeof callback === 'function') {
            callback(data);
        }
        scheduleSnapshot(data);
    }

    function mutationPost(packet, callback) {
        var requestedId = ++state.nextOperationId;
        var blockingMutation = state.activeMutation || state.settlingMutation;
        if (blockingMutation) {
            callSoon(callback, localError(
                    'operation_busy',
                    'Operation #' + blockingMutation.id
                            + ' is still in progress.',
                    requestedId,
                    blockingMutation.id));
            return undefined;
        }

        invalidateSnapshot();
        var operation = {
            id: requestedId,
            name: operationName(packet),
            generation: state.generation,
            timer: null
        };
        state.activeMutation = operation;
        if (typeof window.setQueryStatus === 'function') {
            window.setQueryStatus(
                    'Operation #' + operation.id + ': ' + operation.name);
        }
        operation.timer = setTimeout(function () {
            timeoutMutation(operation, callback);
        }, OPERATION_TIMEOUT_MS);
        try {
            return original.post(packet, function (data) {
                finishMutation(operation, data, callback);
            });
        } catch (error) {
            finishMutation(operation, localError(
                    'operation_transport_failed',
                    error && error.message ? error.message
                            : 'Operation transport failed',
                    operation.id,
                    0), callback);
            return undefined;
        }
    }

    function readPost(packet, callback) {
        var generation = state.generation;
        try {
            return original.post(packet, function (data) {
                if (generation !== state.generation) {
                    return;
                }
                decorate(data, {client_generation: generation});
                if (typeof callback === 'function') {
                    callback(data);
                }
            });
        } catch (error) {
            callSoon(callback, localError(
                    'read_transport_failed',
                    error && error.message ? error.message
                            : 'Read request failed',
                    0,
                    0));
            return undefined;
        }
    }

    function installPostBoundary() {
        window.post = function (packet, callback) {
            if (state.snapshotCapture) {
                return snapshotPost(state.snapshotCapture, packet, callback);
            }
            if (isHistoryPacket(packet)) {
                return original.post(packet, callback);
            }
            if (isMutationPacket(packet)) {
                return mutationPost(packet, callback);
            }
            return readPost(packet, callback);
        };
    }

    function installHistorySeparation() {
        window.logRequest = function (queryText, callback) {
            original.logRequest(queryText);
            if (typeof callback === 'function') {
                callback();
            }
        };
        window.logResponse = function (data, presentation, callback) {
            original.logResponse(data, presentation);
            if (typeof callback === 'function') {
                callback(data);
            }
        };
    }

    function installSnapshotBoundary() {
        for (var i = 0; i < SNAPSHOT_RENDERERS.length; i++) {
            (function (name) {
                window[name] = function () {
                    scheduleSnapshot();
                };
            }(SNAPSHOT_RENDERERS[i]));
        }
        window.refreshScreen = function (data, presentation) {
            window.logResponse(data, presentation);
            scheduleSnapshot(data);
        };
    }

    function operationSnapshot() {
        return Object.freeze({
            generation: state.generation,
            activeOperationId: state.activeMutation
                    ? state.activeMutation.id : 0,
            activeOperationName: state.activeMutation
                    ? state.activeMutation.name : '',
            settlingOperationId: state.settlingMutation
                    ? state.settlingMutation.id : 0,
            settlingOperationName: state.settlingMutation
                    ? state.settlingMutation.name : '',
            currentSnapshotId: state.currentSnapshot
                    ? state.currentSnapshot.id : 0,
            lastCommittedSnapshotId: state.lastCommittedSnapshotId
        });
    }

    function install() {
        if (installed) {
            return;
        }
        installed = true;
        if (!window.KANGER_TRUSTED_RENDERING
                || !window.KANGER_TRUSTED_RENDERING.installed) {
            throw new Error(
                    'KANGER operation protocol requires trusted rendering');
        }

        originalGetElementById = document.getElementById.bind(document);
        original.post = window.post;
        original.logRequest = window.logRequest;
        original.logResponse = window.logResponse;
        original.placeElements = window.placeElements;
        for (var i = 0; i < SNAPSHOT_RENDERERS.length; i++) {
            original[SNAPSHOT_RENDERERS[i]] =
                    window[SNAPSHOT_RENDERERS[i]];
        }

        installPostBoundary();
        installHistorySeparation();
        installSnapshotBoundary();
        window.KANGER_OPERATION_PROTOCOL = Object.freeze({
            version: 1,
            snapshot: operationSnapshot,
            requestSnapshot: scheduleSnapshot
        });
    }

    function observeTrustedRendering() {
        var trusted = window.KANGER_TRUSTED_RENDERING;
        if (trusted && trusted.installed) {
            install();
            return;
        }
        var observed = trusted;
        Object.defineProperty(window, 'KANGER_TRUSTED_RENDERING', {
            configurable: true,
            enumerable: true,
            get: function () {
                return observed;
            },
            set: function (value) {
                observed = value;
                if (value && value.installed) {
                    install();
                }
            }
        });
    }

    observeTrustedRendering();
}(window, document));

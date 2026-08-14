/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Supported KANGER runtime workspace authority.
 *
 * The server workspace projection contains runtime authorities only: storage
 * and transaction state. Source files are external semantic transport objects;
 * explicit source names are canonicalized here only at the transport boundary.
 */
(function (window, document) {
    'use strict';

    var installed = false;
    var original = {};
    var state = {
        generation: -1,
        workspace: null
    };

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function numberValue(value, fallback) {
        var parsed = Number(value);
        return isFinite(parsed) ? parsed : fallback;
    }

    function canonicalSourceName(value) {
        var normalized = stringValue(value).trim().replace(/\\/g, '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (normalized && !/\.k$/i.test(normalized)) {
            normalized += '.k';
        }
        return normalized;
    }

    function canonicalStorageName(value) {
        var normalized = stringValue(value).trim().replace(/[\\/]+/g, '.');
        normalized = normalized.replace(/\.+/g, '.');
        normalized = normalized.replace(/^\.+|\.+$/g, '');
        return normalized;
    }

    function normalizePacket(packet) {
        if (!packet || packet.context !== 'command'
                || !packet.parameters
                || typeof packet.parameters !== 'object') {
            return packet;
        }
        var parameters = packet.parameters;
        ['get', 'put', 'delete'].forEach(function (name) {
            if (Object.prototype.hasOwnProperty.call(parameters, name)
                    && stringValue(parameters[name]).trim()) {
                parameters[name] = canonicalSourceName(parameters[name]);
            }
        });
        ['use', 'drop', 'reindex'].forEach(function (name) {
            if (Object.prototype.hasOwnProperty.call(parameters, name)
                    && stringValue(parameters[name]).trim()) {
                parameters[name] = canonicalStorageName(parameters[name]);
            }
        });
        return packet;
    }

    function renderStorage(storage) {
        var target = document.getElementById('db-name');
        if (!target || !storage) {
            return;
        }
        if (storage.active) {
            target.textContent = 'DB: ' + stringValue(storage.logical_name);
            target.style.color = '#000';
            target.style.fontWeight = 'bold';
            var generation = storage.physical_generation || {};
            target.title = 'canonical=' + stringValue(storage.canonical_name)
                    + '; generation=' + (generation.present ? 'present' : 'missing')
                    + '; wal=' + stringValue(generation.wal_segments || 0);
        } else {
            target.textContent = 'DB: unused';
            target.style.color = '#777';
            target.style.fontWeight = '';
            target.title = 'No active physical generation';
        }
    }

    function currentOperationGeneration() {
        if (!window.KANGER_OPERATION_PROTOCOL
                || typeof window.KANGER_OPERATION_PROTOCOL.snapshot !== 'function') {
            return state.generation;
        }
        return numberValue(
                window.KANGER_OPERATION_PROTOCOL.snapshot().generation,
                state.generation);
    }

    function applyProjection(data, requestGeneration) {
        if (!data || !data.workspace) {
            return false;
        }
        var responseGeneration = numberValue(
                data.client_generation, requestGeneration);
        if (responseGeneration < currentOperationGeneration()
                || responseGeneration < state.generation) {
            return false;
        }
        state.generation = responseGeneration;
        state.workspace = data.workspace;
        renderStorage(data.workspace.storage);
        return true;
    }

    function routeSourceRecovery(data) {
        if (!data || !data.source_recovery) {
            return;
        }
        var authority = window.KANGER_EDITOR_STATE;
        if (authority && typeof authority.recover === 'function') {
            authority.recover(data.source_recovery);
        }
    }

    function installPostBoundary() {
        window.post = function (packet, callback) {
            normalizePacket(packet);
            var requestGeneration = currentOperationGeneration();
            return original.post(packet, function (data) {
                try {
                    if (typeof callback === 'function') {
                        callback(data);
                    }
                } finally {
                    applyProjection(data, requestGeneration);
                    routeSourceRecovery(data);
                }
            });
        };
    }

    function installTypedErrors() {
        window.logResponse = function (data, presentation, callback) {
            var effective = presentation;
            if ((effective === null || effective === undefined)
                    && data && data.result !== 'OK' && data.code) {
                effective = '[' + stringValue(data.code) + '] '
                        + stringValue(data.description);
            }
            return original.logResponse(data, effective, callback);
        };
    }

    function snapshot() {
        return Object.freeze({
            generation: state.generation,
            workspace: state.workspace
        });
    }

    function install() {
        if (installed) {
            return;
        }
        installed = true;
        if (!window.KANGER_OPERATION_PROTOCOL) {
            throw new Error(
                    'KANGER workspace state requires operation protocol');
        }
        original.post = window.post;
        original.logResponse = window.logResponse;
        installPostBoundary();
        installTypedErrors();
        window.KANGER_WORKSPACE_STATE = Object.freeze({
            version: 2,
            snapshot: snapshot,
            canonicalSourceName: canonicalSourceName,
            canonicalStorageName: canonicalStorageName
        });
    }

    function observeOperationProtocol() {
        var protocol = window.KANGER_OPERATION_PROTOCOL;
        if (protocol) {
            install();
            return;
        }
        var observed = protocol;
        Object.defineProperty(window, 'KANGER_OPERATION_PROTOCOL', {
            configurable: true,
            enumerable: true,
            get: function () {
                return observed;
            },
            set: function (value) {
                observed = value;
                if (value) {
                    install();
                }
            }
        });
    }

    observeOperationProtocol();
}(window, document));

/*
 * Loaded here so the authority is available to observe operation responses,
 * but it deliberately installs late, after the historical Editor/soak wrappers
 * have finished. This keeps one final owner of local Editor staging state.
 */
document.write('<script src="editor-state.js"><\\/script>');

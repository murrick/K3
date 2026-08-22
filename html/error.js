/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Supported KANGER browser error taxonomy and reaction contract.
 */
(function (window) {
    'use strict';

    var installed = false;
    var originalPost = null;
    var sourcePresentationInstalled = false;
    var sourcePresentationRetries = 0;
    var MAX_SOURCE_PRESENTATION_RETRIES = 400;
    var sourcePresentationBase = {};
    var pendingCompile = null;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function sessionCode(code, description) {
        var normalized = stringValue(code).toLowerCase();
        return normalized.indexOf('session') >= 0
                || normalized.indexOf('token') >= 0
                || normalized === 'authentication_error'
                || normalized === 'authentication_failed'
                || /authentication|session|token/i.test(stringValue(description));
    }

    function inferDomain(code, description) {
        var normalized = stringValue(code).toLowerCase();
        if (sessionCode(normalized, description)) {
            return 'session';
        }
        if (normalized.indexOf('transport') >= 0) {
            return 'transport';
        }
        if (normalized.indexOf('protocol') >= 0
                || normalized.indexOf('http_') === 0
                || normalized.indexOf('snapshot_') === 0) {
            return 'protocol';
        }
        if (normalized.indexOf('containment') >= 0
                || normalized === 'api_host_missing'
                || normalized === 'direct_login_disabled') {
            return 'containment';
        }
        if (normalized.indexOf('operation_') === 0) {
            return 'operation';
        }
        return 'application';
    }

    function retryable(domain, code) {
        var normalized = stringValue(code).toLowerCase();
        if (normalized === 'operation_busy'
                || normalized === 'containment_busy'
                || normalized === 'transport_timeout'
                || normalized === 'transport_unavailable'
                || normalized === 'session_probe_unavailable') {
            return true;
        }
        return domain === 'transport';
    }

    function local(domain, code, description, canRetry,
            sessionAction, outcome) {
        return {
            result: 'error',
            code: code,
            description: description,
            error: {
                schema: 1,
                domain: domain,
                code: code,
                retryable: !!canRetry,
                session_action: sessionAction || 'retain',
                operation_outcome: outcome || 'unknown'
            }
        };
    }

    function normalize(data) {
        if (!data || typeof data !== 'object' || data.result === 'OK') {
            return data;
        }
        var code = stringValue(data.code || 'application_error');
        data.code = code;
        if (!data.description) {
            data.description = code;
        }
        if (!data.error || data.error.schema !== 1) {
            var domain = inferDomain(code, data.description);
            data.error = {
                schema: 1,
                domain: domain,
                code: code,
                retryable: retryable(domain, code),
                session_action: domain === 'session' ? 'verify' : 'retain',
                operation_outcome: domain === 'application'
                        ? 'confirmed' : (domain === 'operation'
                                && code === 'operation_busy'
                                ? 'not_applied' : 'unknown')
            };
        }
        return data;
    }

    function describe(data) {
        if (!data || data.result === 'OK') {
            return '';
        }
        var normalized = normalize(data);
        return '[' + normalized.error.domain + ':'
                + normalized.code + '] ' + stringValue(normalized.description);
    }

    function sourceRecovery(data) {
        if (!data || data.result === 'OK') {
            return null;
        }
        var recovery = data.source_recovery;
        if (!recovery || recovery.schema !== 1
                || recovery.text === null || recovery.text === undefined) {
            return null;
        }
        return recovery;
    }

    function exposeSourceRecovery(data) {
        var recovery = sourceRecovery(data);
        if (!recovery) {
            return;
        }
        window.setTimeout(function () {
            if (typeof window.openEditor === 'function') {
                window.openEditor(stringValue(recovery.text));
            }
        }, 0);
    }

    function editorInstance() {
        return window.editor;
    }

    function editorText() {
        var instance = editorInstance();
        return instance && typeof instance.getValue === 'function'
                ? stringValue(instance.getValue()) : '';
    }

    function activeOperationId() {
        var protocol = window.KANGER_OPERATION_PROTOCOL;
        if (!protocol || typeof protocol.snapshot !== 'function') {
            return 0;
        }
        var snapshot = protocol.snapshot();
        var operationId = Number(snapshot && snapshot.activeOperationId);
        return Number.isInteger(operationId) && operationId > 0
                ? operationId : 0;
    }

    function canonicalSourceSpan(data, submittedSource) {
        if (!data || data.result === 'OK'
                || !data.error || data.error.schema !== 1) {
            return null;
        }
        var source = data.error.source;
        if (!source || typeof source !== 'object') {
            return null;
        }
        var offset = Number(source.offset);
        var length = Number(source.length);
        if (!Number.isInteger(offset) || !Number.isInteger(length)
                || offset < 0 || length < 0
                || length > submittedSource.length
                || offset > submittedSource.length - length) {
            return null;
        }
        return {offset: offset, length: length};
    }

    function applySourceDiagnostic(data, submitted) {
        if (!submitted || editorText() !== submitted.source) {
            return false;
        }
        var span = canonicalSourceSpan(data, submitted.source);
        if (!span) {
            return false;
        }
        if (typeof window.openEditor === 'function') {
            window.openEditor(null);
            if (editorText() !== submitted.source) {
                return false;
            }
        }
        var instance = editorInstance();
        if (!instance || typeof instance.posFromIndex !== 'function') {
            return false;
        }
        var start = instance.posFromIndex(span.offset);
        if (span.length === 0) {
            if (typeof instance.setCursor !== 'function') {
                return false;
            }
            instance.setCursor(start);
            return true;
        }
        if (typeof instance.setSelection !== 'function') {
            return false;
        }
        var end = instance.posFromIndex(span.offset + span.length);
        instance.setSelection(start, end);
        return true;
    }

    function installSourcePresentation() {
        if (sourcePresentationInstalled) {
            return;
        }
        if (!window.KANGER_ERROR_BOUNDARY
                || !window.KANGER_EDITOR_STATE
                || !window.KANGER_OPERATION_PROTOCOL
                || typeof window.compileSource !== 'function'
                || typeof window.refreshScreen !== 'function'
                || !editorInstance()) {
            sourcePresentationRetries += 1;
            if (sourcePresentationRetries <= MAX_SOURCE_PRESENTATION_RETRIES) {
                window.setTimeout(installSourcePresentation, 10);
            }
            return;
        }

        sourcePresentationInstalled = true;
        sourcePresentationBase.compileSource = window.compileSource;
        sourcePresentationBase.refreshScreen = window.refreshScreen;

        window.compileSource = function () {
            var activeBefore = activeOperationId();
            var submittedSource = editorText();
            var result = sourcePresentationBase.compileSource.apply(
                    window, arguments);
            var activeAfter = activeOperationId();
            if (activeBefore === 0 && activeAfter > 0) {
                pendingCompile = {
                    operationId: activeAfter,
                    source: submittedSource
                };
            }
            return result;
        };

        window.refreshScreen = function (data) {
            var result = sourcePresentationBase.refreshScreen.apply(
                    window, arguments);
            if (pendingCompile && data
                    && Number(data.client_operation_id)
                            === pendingCompile.operationId) {
                var submitted = pendingCompile;
                pendingCompile = null;
                if (stringValue(data.result).toUpperCase() !== 'OK') {
                    window.setTimeout(function () {
                        applySourceDiagnostic(data, submitted);
                    }, 0);
                }
            }
            return result;
        };

        window.KANGER_ERROR_SOURCE_PRESENTATION = Object.freeze({
            version: 1,
            installed: true
        });
    }

    function installPostBoundary() {
        originalPost = window.post;
        window.post = function (packet, callback) {
            try {
                return originalPost(packet, function (data) {
                    var normalized = normalize(data);
                    if (typeof callback === 'function') {
                        callback(normalized);
                    }
                    exposeSourceRecovery(normalized);
                });
            } catch (error) {
                var failure = local('transport', 'transport_exception',
                        error && error.message ? error.message
                                : 'Browser transport failed',
                        true, 'retain', 'unknown');
                if (typeof callback === 'function') {
                    setTimeout(function () {
                        callback(failure);
                    }, 0);
                }
                return undefined;
            }
        };
    }

    function installLogBoundary() {
        if (typeof window.logResponse !== 'function') {
            return;
        }
        var originalLogResponse = window.logResponse;
        window.logResponse = function (data, presentation, callback) {
            var normalized = normalize(data);
            var effective = presentation;
            if ((effective === null || effective === undefined)
                    && normalized && normalized.result !== 'OK') {
                effective = describe(normalized);
            }
            return originalLogResponse(normalized, effective, callback);
        };
    }

    function install() {
        if (installed) {
            return;
        }
        installed = true;
        if (!window.KANGER_WORKSPACE_STATE) {
            throw new Error('KANGER error boundary requires workspace authority');
        }
        if (typeof window.post !== 'function') {
            throw new Error('KANGER error boundary requires a transport');
        }
        installPostBoundary();
        installLogBoundary();
        window.KANGER_ERROR_BOUNDARY = Object.freeze({
            version: 1,
            installed: true,
            normalize: normalize,
            local: local,
            describe: describe
        });
        installSourcePresentation();
    }

    function observeWorkspace() {
        var workspace = window.KANGER_WORKSPACE_STATE;
        if (workspace) {
            install();
            return;
        }
        var observed = workspace;
        Object.defineProperty(window, 'KANGER_WORKSPACE_STATE', {
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

    observeWorkspace();
}(window));

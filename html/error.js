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

    function installPostBoundary() {
        originalPost = window.post;
        window.post = function (packet, callback) {
            try {
                return originalPost(packet, function (data) {
                    var normalized = normalize(data);
                    if (typeof callback === 'function') {
                        callback(normalized);
                    }
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

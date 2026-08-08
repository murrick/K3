/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Browser boundary for the canonical KANGER command dialogue.
 *
 * The Browser deliberately does not parse, abbreviate, normalize or dispatch
 * operator language. Historical command/query entry points are reduced to one
 * raw dialogue transport. Canonical parsing remains owned by kanger-command on
 * the Server; Core prefixes travel through the same raw envelope and are split
 * from ordinary commands only by the shared Server parser.
 */
(function (window) {
    'use strict';

    var installed = false;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function dispatch(line, callback) {
        var raw = stringValue(line);
        return window.post({
            context: 'dialogue',
            parameters: {
                token: window.token,
                line: raw
            }
        }, function (data) {
            if (typeof window.refreshScreen === 'function') {
                window.refreshScreen(data);
            } else if (typeof window.logResponse === 'function') {
                window.logResponse(data);
            }
            if (typeof callback === 'function') {
                callback(data);
            }
        });
    }

    function install() {
        if (installed) {
            return;
        }
        installed = true;
        if (!window.KANGER_ERROR_BOUNDARY
                || !window.KANGER_ERROR_BOUNDARY.installed) {
            throw new Error('KANGER dialogue transport requires the error boundary');
        }
        if (typeof window.post !== 'function') {
            throw new Error('KANGER dialogue transport requires Browser transport');
        }

        window.command = dispatch;
        window.query = dispatch;
        window.KANGER_DIALOGUE_TRANSPORT = Object.freeze({
            version: 1,
            installed: true,
            dispatch: dispatch
        });
    }

    function observeErrorBoundary() {
        var boundary = window.KANGER_ERROR_BOUNDARY;
        if (boundary && boundary.installed) {
            install();
            return;
        }
        var observed = boundary;
        Object.defineProperty(window, 'KANGER_ERROR_BOUNDARY', {
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

    observeErrorBoundary();
}(window));

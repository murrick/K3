/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Parent-owned KANGER console containment boundary.
 *
 * The supported console runs in an opaque sandbox. The bearer token remains in
 * the parent page and every child API request crosses a generation-bound
 * postMessage broker. The child receives only a non-secret session sentinel.
 */
(function (window, document) {
    'use strict';

    var API_HOST = String(window.KANGER_API_HOST || '').replace(/\/$/, '');
    var SESSION_KEY = 'kanger.applicationSession.v1';
    var SESSION_SEQUENCE_KEY = 'kanger.applicationSession.sequence';
    var SESSION_CHANNEL = 'kanger.session.v1';
    var CONTAINMENT_CHANNEL = 'kanger.containment.v1';
    var LAYOUT_PREFIX = 'kanger.console.layout.';
    var SESSION_SENTINEL = '__KANGER_PARENT_SESSION__';
    // The Browser broker must outlive the public nginx 120s response budget.
    var REQUEST_TIMEOUT_MS = 125000;
    var MAX_REQUEST_BYTES = 262144;
    var MAX_INFLIGHT = 32;
    var installed = false;
    var inflight = Object.create(null);

    function frame() {
        return document.getElementById('console-frame');
    }

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function own(object, name) {
        return !!object && Object.prototype.hasOwnProperty.call(object, name);
    }

    function currentSession() {
        var value;
        try {
            value = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null');
        } catch (ignored) {
            return null;
        }
        if (!value || typeof value !== 'object'
                || !stringValue(value.token)
                || !isFinite(Number(value.generation))
                || Number(value.generation) <= 0) {
            return null;
        }
        return {
            token: stringValue(value.token),
            login: stringValue(value.login),
            generation: Number(value.generation)
        };
    }

    function sameSession(left, right) {
        return !!left && !!right
                && left.token === right.token
                && left.generation === right.generation;
    }

    function nextGeneration() {
        var current = Number(sessionStorage.getItem(SESSION_SEQUENCE_KEY) || '0');
        if (!isFinite(current) || current < 0) {
            current = 0;
        }
        current += 1;
        sessionStorage.setItem(SESSION_SEQUENCE_KEY, String(current));
        return current;
    }

    function storeSession(token, login) {
        var session = {
            token: stringValue(token),
            login: stringValue(login),
            generation: nextGeneration()
        };
        if (!session.token) {
            throw new Error('Credential change returned an empty session token');
        }
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
        return session;
    }

    function clearSession(expected) {
        var stored = currentSession();
        if (!expected || !stored || sameSession(stored, expected)) {
            sessionStorage.removeItem(SESSION_KEY);
        }
    }

    function errorEnvelope(domain, code, description, retryable,
            sessionAction, outcome) {
        return {
            result: 'error',
            code: code,
            description: description,
            error: {
                schema: 1,
                domain: domain,
                code: code,
                retryable: !!retryable,
                session_action: sessionAction || 'retain',
                operation_outcome: outcome || 'unknown'
            }
        };
    }

    function sessionCode(code, description) {
        var normalized = stringValue(code).toLowerCase();
        if (normalized.indexOf('session') >= 0
                || normalized.indexOf('token') >= 0
                || normalized === 'authentication_error'
                || normalized === 'authentication_failed') {
            return true;
        }
        return /authentication|session|token/i.test(stringValue(description));
    }

    function normalizeApplication(data) {
        if (!data || typeof data !== 'object' || data.result === 'OK') {
            return data;
        }
        if (data.error && data.error.schema === 1) {
            return data;
        }
        var code = stringValue(data.code || 'application_error');
        var domain = sessionCode(code, data.description)
                ? 'session' : 'application';
        data.code = code;
        data.error = {
            schema: 1,
            domain: domain,
            code: code,
            retryable: false,
            session_action: domain === 'session' ? 'verify' : 'retain',
            operation_outcome: 'confirmed'
        };
        return data;
    }

    function safeInlineJson(value) {
        return JSON.stringify(value)
                .replace(/</g, '\\u003c')
                .replace(/>/g, '\\u003e')
                .replace(/&/g, '\\u0026')
                .replace(/\u2028/g, '\\u2028')
                .replace(/\u2029/g, '\\u2029');
    }

    function layoutSnapshot() {
        var result = {};
        ['sx', 'qy'].forEach(function (name) {
            try {
                result[name] = localStorage.getItem(LAYOUT_PREFIX + name) || '';
            } catch (ignored) {
                result[name] = '';
            }
        });
        return result;
    }

    function childBridgeSource(session) {
        var bootstrap = {
            channel: CONTAINMENT_CHANNEL,
            sessionChannel: SESSION_CHANNEL,
            generation: session.generation,
            parentOrigin: location.origin,
            login: session.login,
            sentinel: SESSION_SENTINEL,
            layout: layoutSnapshot(),
            requestTimeoutMs: REQUEST_TIMEOUT_MS + 2000
        };
        return [
            '<script>',
            '(function () {',
            "    'use strict';",
            '    var bootstrap = Object.freeze(' + safeInlineJson(bootstrap) + ');',
            '    var callbacks = Object.create(null);',
            '    var nextRequestId = 0;',
            '    var layout = bootstrap.layout || {};',
            '    function fallbackError(domain, code, description, retryable, action, outcome) {',
            '        return {result: "error", code: code, description: description, error: {',
            '            schema: 1, domain: domain, code: code, retryable: !!retryable,',
            '            session_action: action || "retain", operation_outcome: outcome || "unknown"',
            '        }};',
            '    }',
            '    function localError(domain, code, description, retryable, action, outcome) {',
            '        if (window.KANGER_ERROR_BOUNDARY && typeof window.KANGER_ERROR_BOUNDARY.local === "function") {',
            '            return window.KANGER_ERROR_BOUNDARY.local(domain, code, description, retryable, action, outcome);',
            '        }',
            '        return fallbackError(domain, code, description, retryable, action, outcome);',
            '    }',
            '    function send(channel, type, payload) {',
            '        window.parent.postMessage({',
            '            channel: channel, type: type, generation: bootstrap.generation, payload: payload || {}',
            '        }, bootstrap.parentOrigin);',
            '    }',
            '    function sanitize(packet) {',
            '        var copy;',
            '        try { copy = JSON.parse(JSON.stringify(packet || {})); }',
            '        catch (ignored) { return null; }',
            '        if (!copy.parameters || typeof copy.parameters !== "object" || Array.isArray(copy.parameters)) {',
            '            copy.parameters = {};',
            '        }',
            '        delete copy.parameters.token;',
            '        return copy;',
            '    }',
            '    function proxyPost(packet, callback) {',
            '        var safe = sanitize(packet);',
            '        var requestId = ++nextRequestId;',
            '        if (!safe) {',
            '            if (typeof callback === "function") {',
            '                callback(localError("containment", "containment_invalid_packet",',
            '                    "Request packet is not serializable", false, "retain", "not_applied"));',
            '            }',
            '            return undefined;',
            '        }',
            '        var timer = setTimeout(function () {',
            '            var pending = callbacks[requestId];',
            '            if (!pending) { return; }',
            '            delete callbacks[requestId];',
            '            pending(localError("transport", "transport_timeout",',
            '                "Parent API broker did not answer before the deadline", true, "retain", "unknown"));',
            '        }, bootstrap.requestTimeoutMs);',
            '        callbacks[requestId] = function (data) {',
            '            clearTimeout(timer);',
            '            if (typeof callback === "function") { callback(data); }',
            '        };',
            '        send(bootstrap.channel, "api.request", {request_id: requestId, packet: safe});',
            '        return requestId;',
            '    }',
            '    function blockedNetwork() {',
            '        throw new Error("Direct child network access is disabled");',
            '    }',
            '    window.fetch = blockedNetwork;',
            '    window.WebSocket = blockedNetwork;',
            '    window.EventSource = blockedNetwork;',
            '    if (window.XMLHttpRequest && window.XMLHttpRequest.prototype) {',
            '        window.XMLHttpRequest.prototype.open = blockedNetwork;',
            '    }',
            '    try { navigator.sendBeacon = function () { return false; }; } catch (ignored) {}',
            '    window.apihost = "";',
            '    window.token = bootstrap.sentinel;',
            '    window.post = proxyPost;',
            '    window.getCookie = function (name) {',
            '        if (name === "token") { return bootstrap.sentinel; }',
            '        if (name === "login") { return bootstrap.login; }',
            '        return Object.prototype.hasOwnProperty.call(layout, name) ? String(layout[name] || "") : "";',
            '    };',
            '    window.setCookie = function (name, value) {',
            '        if (name === "token" || name === "login") { return; }',
            '        layout[name] = String(value || "");',
            '        send(bootstrap.channel, "layout.set", {name: name, value: layout[name]});',
            '    };',
            '    window.loginCheck = function (callback) {',
            '        window.post({context: "command", parameters: {ping: ""}}, function (data) {',
            '            if (data && data.result === "OK") {',
            '                callback({result: "OK", token: bootstrap.sentinel});',
            '            } else {',
            '                send(bootstrap.sessionChannel, "session.invalid", {error: data && data.error});',
            '            }',
            '        });',
            '    };',
            '    window.login = function () {',
            '        send(bootstrap.sessionChannel, "session.invalid", {',
            '            error: localError("containment", "direct_login_disabled",',
            '                "Direct console login is disabled", false, "verify", "not_applied").error',
            '        });',
            '    };',
            '    window.commandQuit = function () {',
            '        if (typeof window.setQueryStatus === "function") { window.setQueryStatus("Signing out"); }',
            '        send(bootstrap.sessionChannel, "session.logout");',
            '    };',
            '    window.password = function () {',
            '        send(bootstrap.sessionChannel, "session.credentials.change", {',
            '            currentPassword: document.getElementById("password-old-pass").value,',
            '            login: document.getElementById("login-pass").value,',
            '            password: document.getElementById("password-pass").value',
            '        });',
            '    };',
            '    window.addEventListener("message", function (event) {',
            '        if (event.source !== window.parent || event.origin !== bootstrap.parentOrigin) { return; }',
            '        var data = event.data;',
            '        if (!data || data.generation !== bootstrap.generation) { return; }',
            '        if (data.channel === bootstrap.channel',
            '                && (data.type === "api.response" || data.type === "api.error")) {',
            '            var requestId = Number(data.payload && data.payload.request_id);',
            '            var callback = callbacks[requestId];',
            '            if (!callback) { return; }',
            '            delete callbacks[requestId];',
            '            callback(data.payload.data);',
            '            return;',
            '        }',
            '        if (data.channel !== bootstrap.sessionChannel) { return; }',
            '        if (data.type === "session.error" || data.type === "session.credentials.error") {',
            '            if (typeof window.dropQueryStatus === "function") { window.dropQueryStatus(); }',
            '            var payload = data.payload || {};',
            '            alert(payload.description || (payload.error && payload.error.code) || "Session operation failed");',
            '        }',
            '    });',
            '    window.KANGER_CONTAINMENT_TRANSPORT = Object.freeze({',
            '        version: 1, installed: true, sentinel: bootstrap.sentinel',
            '    });',
            '}());',
            '<\/script>'
        ].join('\n');
    }

    function cspMeta() {
        var origin = location.origin;
        var policy = [
            "default-src 'none'",
            "script-src 'unsafe-inline' " + origin,
            "style-src 'unsafe-inline' " + origin,
            "font-src " + origin,
            "img-src data:",
            "connect-src 'none'",
            "object-src 'none'",
            "frame-src 'none'",
            "child-src 'none'",
            "worker-src 'none'",
            "form-action 'none'"
        ].join('; ');
        return '<meta http-equiv="Content-Security-Policy" content="'
                + policy.replace(/&/g, '&amp;').replace(/"/g, '&quot;') + '">';
    }

    function transformConsoleDocument(value) {
        var session = currentSession();
        if (!session) {
            throw new Error('Cannot launch a contained console without a parent session');
        }
        var source = stringValue(value);
        if (!source || source.indexOf('</head>') < 0) {
            throw new Error('Contained console document has no head boundary');
        }
        if (source.indexOf(session.token) < 0) {
            throw new Error('Contained console document did not contain the expected bearer token');
        }
        source = source.split(session.token).join('');
        source = source.replace('<head>', '<head>' + cspMeta());
        source = source.replace('</head>', childBridgeSource(session) + '\n</head>');
        if (source.indexOf(session.token) >= 0) {
            throw new Error('Bearer token escaped the parent containment boundary');
        }
        return source;
    }

    function installSrcdocBoundary() {
        var target = frame();
        if (!target || !window.HTMLIFrameElement
                || !window.HTMLIFrameElement.prototype) {
            throw new Error('KANGER containment requires the console iframe');
        }
        var descriptor = Object.getOwnPropertyDescriptor(
                window.HTMLIFrameElement.prototype, 'srcdoc');
        if (!descriptor || typeof descriptor.set !== 'function'
                || typeof descriptor.get !== 'function') {
            throw new Error('KANGER containment requires a configurable srcdoc boundary');
        }
        Object.defineProperty(target, 'srcdoc', {
            configurable: false,
            enumerable: descriptor.enumerable,
            get: function () {
                return descriptor.get.call(target);
            },
            set: function (value) {
                descriptor.set.call(target, transformConsoleDocument(value));
            }
        });
    }

    function postToChild(type, payload, expected, channel) {
        var target = frame();
        if (!target || !target.contentWindow || !expected
                || !sameSession(currentSession(), expected)) {
            return;
        }
        target.contentWindow.postMessage({
            channel: channel || CONTAINMENT_CHANNEL,
            type: type,
            generation: expected.generation,
            payload: payload || {}
        }, '*');
    }

    async function fetchWithTimeout(packet) {
        if (!API_HOST) {
            return errorEnvelope('containment', 'api_host_missing',
                    'KANGER API host is not configured', false,
                    'retain', 'not_applied');
        }
        var controller = typeof AbortController === 'function'
                ? new AbortController() : null;
        var timer = controller ? setTimeout(function () {
            controller.abort();
        }, REQUEST_TIMEOUT_MS) : null;
        try {
            var response = await fetch(API_HOST + '/', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                cache: 'no-store',
                credentials: 'omit',
                signal: controller ? controller.signal : undefined,
                body: JSON.stringify(packet)
            });
            var text = await response.text();
            var data;
            try {
                data = text ? JSON.parse(text) : {};
            } catch (ignored) {
                return errorEnvelope('protocol', 'protocol_invalid_json',
                        'Server returned an invalid JSON response', false,
                        'retain', 'unknown');
            }
            if (!response.ok) {
                var httpError = errorEnvelope('protocol',
                        'http_' + response.status,
                        stringValue(data.description || ('HTTP ' + response.status)),
                        response.status === 429 || response.status >= 500,
                        'retain', 'unknown');
                httpError.http_status = response.status;
                return httpError;
            }
            return normalizeApplication(data);
        } catch (error) {
            if (error && error.name === 'AbortError') {
                return errorEnvelope('transport', 'transport_timeout',
                        'KANGER request timed out', true,
                        'retain', 'unknown');
            }
            return errorEnvelope('transport', 'transport_unavailable',
                    error && error.message ? error.message
                            : 'KANGER transport is unavailable',
                    true, 'retain', 'unknown');
        } finally {
            if (timer) {
                clearTimeout(timer);
            }
        }
    }

    function normalizePacket(packet, session) {
        var serialized;
        try {
            serialized = JSON.stringify(packet || {});
        } catch (ignored) {
            return {error: errorEnvelope('containment',
                    'containment_invalid_packet',
                    'Request packet is not serializable', false,
                    'retain', 'not_applied')};
        }
        if (serialized.length > MAX_REQUEST_BYTES) {
            return {error: errorEnvelope('containment',
                    'containment_request_too_large',
                    'Request exceeds the browser containment limit', false,
                    'retain', 'not_applied')};
        }
        var copy = JSON.parse(serialized);
        var allowed = {
            version: true,
            command: true,
            query: true,
            dialogue: true,
            history: true,
            login: true
        };
        if (!copy || typeof copy !== 'object' || !allowed[copy.context]) {
            return {error: errorEnvelope('containment',
                    'containment_context_denied',
                    'Request context is not available to the console', false,
                    'retain', 'not_applied')};
        }
        if (!copy.parameters || typeof copy.parameters !== 'object'
                || Array.isArray(copy.parameters)) {
            copy.parameters = {};
        }
        delete copy.parameters.token;
        delete copy.parameters.pending_action_token;
        if (copy.context === 'login') {
            var permittedLogin = own(copy.parameters, 'resend')
                    || own(copy.parameters, 'info');
            if (!permittedLogin || own(copy.parameters, 'register')
                    || own(copy.parameters, 'confirm')
                    || own(copy.parameters, 'password')
                    || own(copy.parameters, 'currentpassword')) {
                return {error: errorEnvelope('containment',
                        'containment_login_action_denied',
                        'Credential and registration actions require the parent session boundary',
                        false, 'retain', 'not_applied')};
            }
        }
        if (copy.context !== 'version') {
            copy.parameters.token = session.token;
        }
        return {packet: copy};
    }

    function isClosedSessionResult(result) {
        return !!result && result.result === 'OK'
                && result.session && result.session.schema === 1
                && result.session.state === 'closed';
    }

    async function handleApiRequest(data, expected) {
        var payload = data.payload || {};
        var requestId = Number(payload.request_id);
        if (!Number.isInteger(requestId) || requestId <= 0) {
            return;
        }
        var key = expected.generation + ':' + requestId;
        if (own(inflight, key)) {
            postToChild('api.error', {
                request_id: requestId,
                data: errorEnvelope('containment', 'containment_duplicate_request',
                        'Duplicate child request id', false,
                        'retain', 'not_applied')
            }, expected);
            return;
        }
        if (Object.keys(inflight).length >= MAX_INFLIGHT) {
            postToChild('api.error', {
                request_id: requestId,
                data: errorEnvelope('containment', 'containment_busy',
                        'Parent API broker is at its in-flight limit', true,
                        'retain', 'not_applied')
            }, expected);
            return;
        }
        var normalized = normalizePacket(payload.packet, expected);
        if (normalized.error) {
            postToChild('api.error', {
                request_id: requestId,
                data: normalized.error
            }, expected);
            return;
        }
        inflight[key] = true;
        try {
            var result = await fetchWithTimeout(normalized.packet);
            if (!sameSession(currentSession(), expected)) {
                return;
            }
            var closed = isClosedSessionResult(result);
            postToChild(result && result.result === 'error'
                    ? 'api.error' : 'api.response', {
                request_id: requestId,
                data: result
            }, expected);
            if (closed) {
                clearSession(expected);
                setTimeout(function () {
                    location.reload();
                }, 0);
            }
        } finally {
            delete inflight[key];
        }
    }

    async function probeSession(expected) {
        var result = await fetchWithTimeout({
            context: 'command',
            parameters: {token: expected.token, ping: ''}
        });
        if (!sameSession(currentSession(), expected)) {
            return 'changed';
        }
        if (result && result.result === 'OK') {
            return 'valid';
        }
        if (result && result.error && result.error.domain === 'session') {
            return 'invalid';
        }
        return 'unavailable';
    }

    async function handleLogout(expected) {
        var result = await fetchWithTimeout({
            context: 'command',
            parameters: {token: expected.token, quit: ''}
        });
        if (!sameSession(currentSession(), expected)) {
            return;
        }
        if (result && result.result === 'OK') {
            clearSession(expected);
            location.reload();
            return;
        }
        var probe = await probeSession(expected);
        if (probe === 'invalid') {
            clearSession(expected);
            location.reload();
            return;
        }
        postToChild('session.error', {
            description: probe === 'unavailable'
                    ? 'Logout could not be verified; the parent session was retained.'
                    : stringValue(result.description || 'Server refused to close the session'),
            error: result.error
        }, expected, SESSION_CHANNEL);
    }

    async function handleSessionInvalid(expected) {
        var probe = await probeSession(expected);
        if (probe === 'invalid') {
            clearSession(expected);
            location.reload();
        } else if (probe === 'unavailable') {
            postToChild('session.error', {
                description: 'Session validity could not be verified; the parent session was retained.',
                error: errorEnvelope('transport', 'session_probe_unavailable',
                        'Session probe is unavailable', true,
                        'retain', 'unknown').error
            }, expected, SESSION_CHANNEL);
        }
    }

    async function handleCredentialChange(expected, payload) {
        var result = await fetchWithTimeout({
            context: 'login',
            parameters: {
                token: expected.token,
                currentlogin: expected.login,
                currentpassword: stringValue(payload.currentPassword),
                login: stringValue(payload.login),
                password: stringValue(payload.password)
            }
        });
        if (!sameSession(currentSession(), expected)) {
            return;
        }
        if (result && result.result === 'OK' && result.token) {
            storeSession(result.token,
                    stringValue(result.login || payload.login || expected.login));
            location.reload();
            return;
        }
        postToChild('session.credentials.error', {
            description: stringValue(result.description || 'Credential change failed'),
            error: result.error
        }, expected, SESSION_CHANNEL);
        if (result && result.error && result.error.domain === 'session') {
            handleSessionInvalid(expected);
        }
    }

    function handleLayout(payload) {
        var name = stringValue(payload && payload.name);
        if (!/^[A-Za-z0-9_.-]{1,64}$/.test(name)) {
            return;
        }
        try {
            localStorage.setItem(LAYOUT_PREFIX + name,
                    stringValue(payload && payload.value));
        } catch (ignored) {
        }
    }

    function onMessage(event) {
        var target = frame();
        if (!target || event.source !== target.contentWindow
                || event.origin !== 'null') {
            return;
        }
        var data = event.data;
        var expected = currentSession();
        if (!data || !expected || data.generation !== expected.generation) {
            return;
        }
        if (data.channel === CONTAINMENT_CHANNEL) {
            if (data.type === 'api.request') {
                handleApiRequest(data, expected);
            } else if (data.type === 'layout.set') {
                handleLayout(data.payload || {});
            }
            return;
        }
        if (data.channel !== SESSION_CHANNEL) {
            return;
        }
        event.stopImmediatePropagation();
        if (data.type === 'session.logout') {
            handleLogout(expected);
        } else if (data.type === 'session.invalid') {
            handleSessionInvalid(expected);
        } else if (data.type === 'session.credentials.change') {
            handleCredentialChange(expected, data.payload || {});
        }
    }

    function assertFramePolicy() {
        var target = frame();
        if (!target) {
            return;
        }
        if (target.getAttribute('sandbox') !== 'allow-scripts') {
            throw new Error('Owner console sandbox must be exactly allow-scripts');
        }
    }

    function install() {
        if (installed) {
            return;
        }
        installed = true;
        assertFramePolicy();
        installSrcdocBoundary();
        window.addEventListener('message', onMessage, true);
        document.addEventListener('DOMContentLoaded', assertFramePolicy);
        window.KANGER_CONTAINMENT_BOUNDARY = Object.freeze({
            version: 1,
            installed: true,
            channel: CONTAINMENT_CHANNEL,
            snapshot: function () {
                return Object.freeze({
                    sessionGeneration: currentSession()
                            ? currentSession().generation : 0,
                    inflight: Object.keys(inflight).length
                });
            }
        });
    }

    install();
}(window, document));

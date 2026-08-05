/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
(function () {
    'use strict';

    var API_HOST = String(window.KANGER_API_HOST || '').replace(/\/$/, '');
    var SESSION_KEY = 'kanger.applicationSession.v1';
    var SESSION_SEQUENCE_KEY = 'kanger.applicationSession.sequence';
    var PENDING_TOKEN_KEY = 'kanger.pendingActionToken';
    var BRIDGE_CHANNEL = 'kanger.session.v1';
    var CONSOLE_SESSION_MARKER = 'window.apihost = "http://localhost:1964";\n        window.token = "";';
    var REQUEST_TIMEOUT_MS = 15000;
    var SESSION_PROBE_MS = 30000;
    var PROBE_VALID = 'valid';
    var PROBE_INVALID = 'invalid';
    var PROBE_UNAVAILABLE = 'unavailable';

    var state = {
        capabilities: null,
        pendingActionToken: sessionStorage.getItem(PENDING_TOKEN_KEY) || '',
        consoleTemplate: null,
        session: null,
        sessionMonitor: null,
        sessionProbeInFlight: false,
        loginInFlight: false,
        credentialChangeInFlight: false
    };

    var views = {
        login: document.getElementById('login-view'),
        register: document.getElementById('register-view'),
        pending: document.getElementById('pending-view')
    };
    var authShell = document.getElementById('auth-shell');
    var consoleView = document.getElementById('console-view');
    var consoleFrame = document.getElementById('console-frame');
    var notice = document.getElementById('notice');
    var policy = document.getElementById('policy');
    var version = document.getElementById('version');
    var registerButton = document.getElementById('open-register');
    var loginForm = document.getElementById('login-form');

    function readLegacyCookie(name) {
        var prefix = encodeURIComponent(name) + '=';
        var parts = document.cookie.split(';');
        for (var i = 0; i < parts.length; i++) {
            var item = parts[i].trim();
            if (item.indexOf(prefix) === 0) {
                return decodeURIComponent(item.substring(prefix.length));
            }
        }
        return '';
    }

    function deleteLegacyCookie(name) {
        var secure = location.protocol === 'https:' ? '; Secure' : '';
        document.cookie = encodeURIComponent(name)
                + '=; Path=/; Max-Age=0; SameSite=Lax' + secure;
    }

    function nextGeneration() {
        var current = Number(sessionStorage.getItem(SESSION_SEQUENCE_KEY) || '0');
        if (!Number.isFinite(current) || current < 0) {
            current = 0;
        }
        current += 1;
        sessionStorage.setItem(SESSION_SEQUENCE_KEY, String(current));
        return current;
    }

    function normalizedSession(value) {
        if (!value || typeof value !== 'object') {
            return null;
        }
        var token = typeof value.token === 'string' ? value.token : '';
        var login = typeof value.login === 'string' ? value.login : '';
        var generation = Number(value.generation);
        if (!token || !Number.isFinite(generation) || generation <= 0) {
            return null;
        }
        return {
            token: token,
            login: login,
            generation: generation,
            status: 'active'
        };
    }

    function storeSession(session) {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify({
            token: session.token,
            login: session.login,
            generation: session.generation
        }));
    }

    function loadStoredSession() {
        var stored = null;
        try {
            stored = normalizedSession(JSON.parse(
                    sessionStorage.getItem(SESSION_KEY) || 'null'));
        } catch (ignored) {
            sessionStorage.removeItem(SESSION_KEY);
        }

        if (!stored) {
            var legacyToken = readLegacyCookie('token');
            if (legacyToken) {
                stored = {
                    token: legacyToken,
                    login: readLegacyCookie('login'),
                    generation: nextGeneration(),
                    status: 'active'
                };
                storeSession(stored);
            }
        }

        deleteLegacyCookie('token');
        deleteLegacyCookie('login');
        return stored;
    }

    function persistSession(token, login) {
        var session = {
            token: String(token || ''),
            login: String(login || ''),
            generation: nextGeneration(),
            status: 'active'
        };
        if (!session.token) {
            throw new Error('Server returned an empty session token');
        }
        storeSession(session);
        state.session = session;
        return session;
    }

    function clearStoredSession(expected) {
        var stored = null;
        try {
            stored = normalizedSession(JSON.parse(
                    sessionStorage.getItem(SESSION_KEY) || 'null'));
        } catch (ignored) {
            sessionStorage.removeItem(SESSION_KEY);
            return;
        }
        if (!expected || !stored
                || (stored.token === expected.token
                && stored.generation === expected.generation)) {
            sessionStorage.removeItem(SESSION_KEY);
        }
    }

    function sameSession(left, right) {
        return !!left && !!right
                && left.token === right.token
                && left.generation === right.generation;
    }

    function setPendingToken(token) {
        state.pendingActionToken = token || '';
        if (state.pendingActionToken) {
            sessionStorage.setItem(PENDING_TOKEN_KEY, state.pendingActionToken);
        } else {
            sessionStorage.removeItem(PENDING_TOKEN_KEY);
        }
    }

    function showView(name) {
        Object.keys(views).forEach(function (key) {
            views[key].classList.toggle('hidden', key !== name);
        });
    }

    function message(text, type) {
        notice.textContent = text || '';
        notice.className = 'notice' + (type ? ' ' + type : '');
    }

    function describeError(data, fallback) {
        if (data && data.description) {
            return String(data.description);
        }
        return fallback;
    }

    function setFormBusy(form, busy) {
        Array.prototype.forEach.call(form.elements, function (control) {
            control.disabled = !!busy;
        });
    }

    async function fetchWithTimeout(url, options) {
        var controller = typeof AbortController === 'function'
                ? new AbortController() : null;
        var timer = null;
        if (controller) {
            options.signal = controller.signal;
            timer = setTimeout(function () {
                controller.abort();
            }, REQUEST_TIMEOUT_MS);
        }
        try {
            return await fetch(url, options);
        } catch (error) {
            if (error && error.name === 'AbortError') {
                throw new Error('KANGER request timed out');
            }
            throw error;
        } finally {
            if (timer) {
                clearTimeout(timer);
            }
        }
    }

    async function post(context, parameters) {
        if (!API_HOST) {
            throw new Error('KANGER API host is not configured');
        }
        var response = await fetchWithTimeout(API_HOST + '/', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            cache: 'no-store',
            credentials: 'omit',
            body: JSON.stringify({context: context, parameters: parameters || {}})
        });
        var text = await response.text();
        var data;
        try {
            data = text ? JSON.parse(text) : {};
        } catch (error) {
            throw new Error('Server returned an invalid JSON response');
        }
        if (!response.ok) {
            throw new Error(describeError(data, 'HTTP ' + response.status));
        }
        return data;
    }

    function applyCapabilities(data) {
        var auth = data && data.auth;
        if (!auth || typeof auth.public_registration !== 'boolean'
                || !auth.registration_policy) {
            state.capabilities = null;
            registerButton.classList.add('hidden');
            policy.textContent = 'The server did not publish a valid authentication capability snapshot. Public registration is unavailable.';
            throw new Error('Authentication capabilities are unavailable');
        }
        state.capabilities = auth;
        registerButton.classList.toggle('hidden', !auth.public_registration);
        if (auth.registration_policy === 'TRUSTED') {
            policy.textContent = 'Trusted deployment: accounts are provisioned by the server operator. Public registration is disabled.';
        } else if (auth.registration_policy === 'EMAIL_VERIFIED') {
            policy.textContent = 'E-mail verified deployment: registration creates a pending request. Confirmation activates the account; sign-in remains a separate step.';
        } else {
            registerButton.classList.add('hidden');
            policy.textContent = 'Unknown authentication policy. Public registration is unavailable.';
            throw new Error('Unknown authentication policy');
        }
    }

    async function loadCapabilities() {
        var data = await post('version', {});
        version.textContent = data.version || data.server_version || 'KANGER Server';
        applyCapabilities(data);
    }

    function assertConsoleTemplate() {
        if (!state.consoleTemplate
                || state.consoleTemplate.indexOf(CONSOLE_SESSION_MARKER) < 0) {
            throw new Error('Owner console session marker is unavailable');
        }
    }

    async function preloadConsoleTemplate() {
        if (state.consoleTemplate) {
            assertConsoleTemplate();
            return;
        }
        var response = await fetchWithTimeout('console.html', {
            cache: 'no-store',
            credentials: 'same-origin'
        });
        if (!response.ok) {
            throw new Error('Owner console payload is unavailable');
        }
        state.consoleTemplate = await response.text();
        assertConsoleTemplate();
    }

    function consoleBridgeSource(session) {
        var bootstrap = {
            token: session.token,
            login: session.login,
            generation: session.generation,
            parentOrigin: location.origin,
            channel: BRIDGE_CHANNEL
        };
        var lines = [
            '<script>',
            '(function () {',
            "    'use strict';",
            '    var session = window.KANGER_SESSION_BOOTSTRAP;',
            "    var layoutPrefix = 'kanger.console.layout.';",
            '    function send(type, payload) {',
            '        window.parent.postMessage({',
            '            channel: session.channel,',
            '            type: type,',
            '            generation: session.generation,',
            '            payload: payload || {}',
            '        }, session.parentOrigin);',
            '    }',
            '    window.getCookie = function (name) {',
            "        if (name === 'token') { return session.token; }",
            "        if (name === 'login') { return session.login; }",
            '        try { return localStorage.getItem(layoutPrefix + name) || ""; }',
            '        catch (ignored) { return ""; }',
            '    };',
            '    window.setCookie = function (name, value) {',
            "        if (name === 'token' || name === 'login') { return; }",
            '        try { localStorage.setItem(layoutPrefix + name, String(value || "")); }',
            '        catch (ignored) { }',
            '    };',
            '    window.loginCheck = function (callback) {',
            '        window.post({context: "command", parameters: {token: session.token, ping: ""}}, function (data) {',
            '            if (data && data.result === "OK") {',
            '                callback({result: "OK", token: session.token});',
            '            } else {',
            '                send("session.invalid", {description: data && data.description ? String(data.description) : "Session rejected"});',
            '            }',
            '        });',
            '    };',
            '    window.login = function () {',
            '        send("session.invalid", {description: "Direct console login is disabled"});',
            '    };',
            '    window.commandQuit = function () {',
            '        if (typeof window.setQueryStatus === "function") { window.setQueryStatus("Signing out"); }',
            '        send("session.logout");',
            '    };',
            '    window.password = function () {',
            '        send("session.credentials.change", {',
            '            currentPassword: document.getElementById("password-old-pass").value,',
            '            login: document.getElementById("login-pass").value,',
            '            password: document.getElementById("password-pass").value',
            '        });',
            '    };',
            '    window.addEventListener("message", function (event) {',
            '        if (event.source !== window.parent || event.origin !== session.parentOrigin) { return; }',
            '        var data = event.data;',
            '        if (!data || data.channel !== session.channel || data.generation !== session.generation) { return; }',
            '        if (data.type === "session.closing") {',
            '            if (typeof window.setQueryStatus === "function") { window.setQueryStatus("Signing out"); }',
            '        } else if (data.type === "session.error" || data.type === "session.credentials.error") {',
            '            if (typeof window.dropQueryStatus === "function") { window.dropQueryStatus(); }',
            '            var oldPassword = document.getElementById("password-old-pass");',
            '            var newPassword = document.getElementById("password-pass");',
            '            if (oldPassword) { oldPassword.value = ""; }',
            '            if (newPassword) { newPassword.value = ""; }',
            '            alert(data.payload && data.payload.description ? data.payload.description : "Session operation failed");',
            '        }',
            '    });',
            '    window.addEventListener("DOMContentLoaded", function () { send("console.ready"); });',
            '}());',
            '</script>'
        ];
        return {
            bootstrap: bootstrap,
            source: lines.join('\n')
        };
    }

    function buildConsoleDocument(session) {
        assertConsoleTemplate();
        var bridge = consoleBridgeSource(session);
        var replacement = 'window.apihost = ' + JSON.stringify(API_HOST) + ';\n'
                + '        window.KANGER_SESSION_BOOTSTRAP = Object.freeze('
                + JSON.stringify(bridge.bootstrap) + ');\n'
                + '        window.token = window.KANGER_SESSION_BOOTSTRAP.token;';
        var documentText = state.consoleTemplate.replace(
                CONSOLE_SESSION_MARKER, replacement);
        documentText = documentText.replace('<head>', '<head><base href="./">');
        documentText = documentText.replace('</head>', bridge.source + '\n</head>');
        return documentText;
    }

    function postToConsole(type, payload, expected) {
        if (!expected || !sameSession(state.session, expected)
                || !consoleFrame.contentWindow) {
            return;
        }
        consoleFrame.contentWindow.postMessage({
            channel: BRIDGE_CHANNEL,
            type: type,
            generation: expected.generation,
            payload: payload || {}
        }, location.origin);
    }

    function stopSessionMonitor() {
        if (state.sessionMonitor) {
            clearInterval(state.sessionMonitor);
            state.sessionMonitor = null;
        }
    }

    function destroyConsole() {
        stopSessionMonitor();
        consoleFrame.srcdoc = '';
        consoleView.classList.add('hidden');
        authShell.classList.remove('hidden');
    }

    function closeLocalSession(expected, text, type) {
        if (expected && !sameSession(state.session, expected)) {
            return;
        }
        clearStoredSession(expected);
        state.session = null;
        destroyConsole();
        showView('login');
        message(text || 'Session closed. Sign in again.', type || '');
        document.getElementById('password').value = '';
        document.getElementById('login').focus();
    }

    async function probeSession(expected, closeWhenInvalid) {
        if (!expected || !sameSession(state.session, expected)
                || expected.status !== 'active' || state.sessionProbeInFlight) {
            return PROBE_UNAVAILABLE;
        }
        state.sessionProbeInFlight = true;
        try {
            var data = await post('command', {token: expected.token, ping: ''});
            if (!sameSession(state.session, expected)) {
                return PROBE_UNAVAILABLE;
            }
            if (data.result !== 'OK') {
                if (closeWhenInvalid) {
                    closeLocalSession(expected,
                            'Session expired or was replaced. Sign in again.');
                }
                return PROBE_INVALID;
            }
            return PROBE_VALID;
        } catch (ignored) {
            return PROBE_UNAVAILABLE;
        } finally {
            state.sessionProbeInFlight = false;
        }
    }

    function startSessionMonitor(expected) {
        stopSessionMonitor();
        state.sessionMonitor = setInterval(function () {
            probeSession(expected, true);
        }, SESSION_PROBE_MS);
    }

    async function revokeSession(expected) {
        if (!expected || !sameSession(state.session, expected)
                || expected.status === 'closing') {
            return;
        }
        expected.status = 'closing';
        postToConsole('session.closing', {}, expected);
        try {
            var data = await post('command', {token: expected.token, quit: ''});
            if (!sameSession(state.session, expected)) {
                return;
            }
            if (data.result === 'OK') {
                closeLocalSession(expected, 'Session closed.');
                return;
            }
            expected.status = 'active';
            var probe = await probeSession(expected, false);
            if (probe === PROBE_INVALID) {
                closeLocalSession(expected, 'Session was already closed.');
            } else {
                postToConsole('session.error', {
                    description: probe === PROBE_UNAVAILABLE
                            ? 'Logout result could not be verified; the local session was retained.'
                            : describeError(data, 'Server refused to close the session')
                }, expected);
            }
        } catch (error) {
            if (sameSession(state.session, expected)) {
                expected.status = 'active';
                postToConsole('session.error', {
                    description: error.message
                            + '. The local session was retained because revocation was not confirmed.'
                }, expected);
            }
        }
    }

    async function changeCredentials(expected, payload) {
        if (!expected || !sameSession(state.session, expected)
                || expected.status !== 'active' || state.credentialChangeInFlight) {
            return;
        }
        state.credentialChangeInFlight = true;
        try {
            var data = await post('login', {
                token: expected.token,
                currentlogin: expected.login,
                currentpassword: String(payload.currentPassword || ''),
                login: String(payload.login || ''),
                password: String(payload.password || '')
            });
            if (!sameSession(state.session, expected)) {
                return;
            }
            if (data.result === 'OK' && data.token) {
                clearStoredSession(expected);
                var replacement = persistSession(
                        String(data.token),
                        String(data.login || payload.login || expected.login));
                await launchConsole(replacement);
                return;
            }

            postToConsole('session.credentials.error', {
                description: describeError(data, 'Credential change failed')
            }, expected);
            var probe = await probeSession(expected, false);
            if (probe === PROBE_INVALID && sameSession(state.session, expected)) {
                closeLocalSession(expected,
                        'Credential change failed and the previous session is no longer valid.',
                        'error');
            }
        } catch (error) {
            if (sameSession(state.session, expected)) {
                postToConsole('session.credentials.error', {
                    description: error.message
                            + '. The existing local session was retained pending verification.'
                }, expected);
            }
        } finally {
            state.credentialChangeInFlight = false;
        }
    }

    async function launchConsole(expected) {
        if (!expected || !sameSession(state.session, expected)) {
            throw new Error('No active session is available');
        }
        await preloadConsoleTemplate();
        var consoleDocument = buildConsoleDocument(expected);
        authShell.classList.add('hidden');
        consoleView.classList.remove('hidden');
        consoleFrame.srcdoc = consoleDocument;
        startSessionMonitor(expected);
        message('');
    }

    function updatePendingFromResponse(data) {
        if (data && data.pending_action_token) {
            setPendingToken(String(data.pending_action_token));
        }
        document.getElementById('pending-email').textContent = data && data.email_hint
                ? String(data.email_hint) : 'the registered address';
    }

    async function login(event) {
        event.preventDefault();
        if (state.loginInFlight) {
            return;
        }
        state.loginInFlight = true;
        setFormBusy(loginForm, true);
        message('Signing in…');
        var loginValue = document.getElementById('login').value.trim();
        var password = document.getElementById('password').value;
        try {
            await preloadConsoleTemplate();
            var data = await post('login', {
                login: loginValue,
                password: password
            });
            document.getElementById('password').value = '';
            if (data.result === 'OK' && data.token) {
                setPendingToken('');
                var session = persistSession(String(data.token), loginValue);
                await launchConsole(session);
                return;
            }
            if (data.code === 'EMAIL_CONFIRMATION_REQUIRED'
                    && data.pending_action_token) {
                updatePendingFromResponse(data);
                showView('pending');
                message('Confirm the e-mail address, or use one of the scoped pending-registration actions.');
                return;
            }
            message(describeError(data, 'Authentication failed'), 'error');
        } catch (error) {
            document.getElementById('password').value = '';
            message(error.message, 'error');
        } finally {
            state.loginInFlight = false;
            setFormBusy(loginForm, false);
        }
    }

    async function register(event) {
        event.preventDefault();
        if (!state.capabilities || !state.capabilities.public_registration) {
            message('Public registration is disabled by server policy.', 'error');
            return;
        }
        var password = document.getElementById('register-password').value;
        var repeated = document.getElementById('register-password-repeat').value;
        if (password !== repeated) {
            message('Password confirmation does not match.', 'error');
            return;
        }
        if (!document.getElementById('privacy').checked) {
            message('Privacy consent is required.', 'error');
            return;
        }
        message('Creating pending registration…');
        var parameters = {
            register: document.getElementById('register-login').value.trim(),
            password: password,
            email: document.getElementById('register-email').value.trim(),
            name: document.getElementById('register-name').value.trim(),
            country: document.getElementById('register-country').value.trim(),
            city: document.getElementById('register-city').value.trim(),
            privacy: true,
            token: ''
        };
        try {
            var data = await post('login', parameters);
            document.getElementById('register-password').value = '';
            document.getElementById('register-password-repeat').value = '';
            if (data.result === 'OK' && data.state === 'PENDING_CONFIRMATION') {
                document.getElementById('login').value = parameters.register;
                showView('login');
                message('Registration is pending for '
                        + (data.email_hint || 'the supplied e-mail')
                        + '. Follow the confirmation link, then sign in here.',
                        'success');
                return;
            }
            var suffix = data && data.state === 'PENDING_CONFIRMATION'
                    ? ' The pending registration remains available; sign in with the same credentials to manage it.'
                    : '';
            message(describeError(data, 'Registration failed') + suffix, 'error');
        } catch (error) {
            document.getElementById('register-password').value = '';
            document.getElementById('register-password-repeat').value = '';
            message(error.message, 'error');
        }
    }

    async function pendingAction(parameters, successText) {
        if (!state.pendingActionToken) {
            showView('login');
            message('Sign in with the pending registration credentials to obtain a scoped action token.', 'error');
            return;
        }
        parameters.pending_action_token = state.pendingActionToken;
        try {
            var data = await post('login', parameters);
            updatePendingFromResponse(data);
            if (data.result === 'OK') {
                message(successText, 'success');
            } else {
                message(describeError(data,
                        'Pending registration action failed'), 'error');
            }
        } catch (error) {
            message(error.message, 'error');
        }
    }

    async function cancelPending() {
        if (!state.pendingActionToken) {
            showView('login');
            message('No scoped pending-registration token is available.', 'error');
            return;
        }
        try {
            var data = await post('login', {
                pending_action_token: state.pendingActionToken,
                cancel_pending: ''
            });
            if (data.result === 'OK') {
                setPendingToken('');
                showView('login');
                message('Pending registration cancelled.', 'success');
            } else {
                message(describeError(data,
                        'Pending registration could not be cancelled'), 'error');
            }
        } catch (error) {
            message(error.message, 'error');
        }
    }

    function onConsoleMessage(event) {
        if (event.source !== consoleFrame.contentWindow) {
            return;
        }
        if (event.origin !== location.origin && event.origin !== 'null') {
            return;
        }
        var data = event.data;
        if (!data || data.channel !== BRIDGE_CHANNEL || !state.session
                || data.generation !== state.session.generation) {
            return;
        }
        var expected = state.session;
        if (data.type === 'session.logout') {
            revokeSession(expected);
        } else if (data.type === 'session.invalid') {
            probeSession(expected, true);
        } else if (data.type === 'session.credentials.change') {
            changeCredentials(expected, data.payload || {});
        }
    }

    loginForm.addEventListener('submit', login);
    document.getElementById('register-form').addEventListener('submit', register);
    registerButton.addEventListener('click', function () {
        showView('register');
        message('');
    });
    document.getElementById('cancel-register').addEventListener('click', function () {
        showView('login');
        message('');
    });
    document.getElementById('pending-back').addEventListener('click', function () {
        showView('login');
        message('');
    });
    document.getElementById('pending-resend').addEventListener('click', function () {
        pendingAction({resend: ''}, 'A new confirmation e-mail was queued.');
    });
    document.getElementById('pending-change').addEventListener('click', function () {
        var email = document.getElementById('pending-new-email').value.trim();
        if (!email) {
            message('Enter the new unconfirmed e-mail address.', 'error');
            return;
        }
        pendingAction({change_pending_email: '', email: email},
                'The pending e-mail address was changed and a new confirmation was queued.');
    });
    document.getElementById('pending-cancel').addEventListener('click', cancelPending);
    window.addEventListener('message', onConsoleMessage);
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden && state.session) {
            probeSession(state.session, true);
        }
    });

    (async function initialize() {
        state.session = loadStoredSession();
        try {
            await Promise.all([loadCapabilities(), preloadConsoleTemplate()]);
            var query = new URLSearchParams(location.search);
            if (query.get('confirmation') === 'success'
                    || query.get('confirmed') === 'true') {
                message('E-mail confirmed. Sign in to create a session.', 'success');
                history.replaceState(null, '', location.pathname);
            }
            if (state.session) {
                var restored = state.session;
                var probe = await probeSession(restored, false);
                if (probe === PROBE_VALID && sameSession(state.session, restored)) {
                    await launchConsole(restored);
                    return;
                }
                if (probe === PROBE_INVALID) {
                    closeLocalSession(restored,
                            'Stored session is no longer valid. Sign in again.');
                } else {
                    showView('login');
                    message('The stored session could not be validated because the server is unavailable. It was retained for a later retry.',
                            'error');
                }
            } else {
                showView('login');
            }
            document.getElementById('login').focus();
        } catch (error) {
            showView('login');
            message(error.message, 'error');
        }
    }());
}());

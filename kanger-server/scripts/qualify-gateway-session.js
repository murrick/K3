#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('html/gateway.js', 'utf8');
const consoleTemplate = [
    '<!DOCTYPE html>',
    '<html><head><script>',
    'window.apihost = "http://localhost:1964";',
    '        window.token = "";',
    '</script></head><body></body></html>'
].join('\n');

function storage(initial) {
    const values = Object.assign({}, initial || {});
    return {
        getItem(key) {
            return Object.prototype.hasOwnProperty.call(values, key)
                    ? values[key] : null;
        },
        setItem(key, value) {
            values[key] = String(value);
        },
        removeItem(key) {
            delete values[key];
        },
        snapshot() {
            return Object.assign({}, values);
        }
    };
}

function classList() {
    const names = new Set();
    return {
        add(name) { names.add(name); },
        remove(name) { names.delete(name); },
        toggle(name, force) {
            if (force === undefined) {
                if (names.has(name)) {
                    names.delete(name);
                    return false;
                }
                names.add(name);
                return true;
            }
            if (force) {
                names.add(name);
            } else {
                names.delete(name);
            }
            return !!force;
        },
        contains(name) { return names.has(name); }
    };
}

function element(id) {
    const listeners = Object.create(null);
    return {
        id,
        value: '',
        textContent: '',
        className: '',
        classList: classList(),
        disabled: false,
        elements: [],
        focusCount: 0,
        addEventListener(type, listener) {
            listeners[type] = listener;
        },
        listener(type) {
            return listeners[type];
        },
        focus() {
            this.focusCount += 1;
        }
    };
}

function jsonResponse(data, ok = true) {
    return {
        ok,
        async text() {
            return JSON.stringify(data);
        }
    };
}

function textResponse(text, ok = true) {
    return {
        ok,
        async text() {
            return text;
        }
    };
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((accept, fail) => {
        resolve = accept;
        reject = fail;
    });
    return {promise, resolve, reject};
}

async function settle(rounds = 12) {
    for (let i = 0; i < rounds; i += 1) {
        await Promise.resolve();
        await new Promise((resolve) => setImmediate(resolve));
    }
}

function createHarness(options) {
    const configuration = options || {};
    const ids = [
        'login-view', 'register-view', 'pending-view', 'auth-shell',
        'console-view', 'console-frame', 'notice', 'policy', 'version',
        'open-register', 'login-form', 'register-form', 'password', 'login',
        'register-login', 'register-email', 'register-name', 'register-country',
        'register-city', 'register-password', 'register-password-repeat',
        'privacy', 'pending-email', 'pending-new-email', 'cancel-register',
        'pending-back', 'pending-resend', 'pending-change', 'pending-cancel'
    ];
    const elements = Object.create(null);
    ids.forEach((id) => {
        elements[id] = element(id);
    });

    const frameMessages = [];
    const frameWindow = {
        postMessage(message, origin) {
            frameMessages.push({message, origin});
        }
    };
    elements['console-frame'].contentWindow = frameWindow;
    elements['console-frame'].srcdoc = '';

    elements['login-form'].elements = [
        elements.login,
        elements.password,
        elements['open-register']
    ];
    elements['register-form'].elements = [
        elements['register-login'],
        elements['register-email'],
        elements['register-name'],
        elements['register-country'],
        elements['register-city'],
        elements['register-password'],
        elements['register-password-repeat'],
        elements.privacy
    ];

    const documentListeners = Object.create(null);
    const windowListeners = Object.create(null);
    const cookieWrites = [];
    let cookieHeader = configuration.cookies || '';
    const document = {
        hidden: false,
        getElementById(id) {
            if (!elements[id]) {
                elements[id] = element(id);
            }
            return elements[id];
        },
        addEventListener(type, listener) {
            documentListeners[type] = listener;
        }
    };
    Object.defineProperty(document, 'cookie', {
        get() {
            return cookieHeader;
        },
        set(value) {
            cookieWrites.push(String(value));
            if (String(value).includes('Max-Age=0')) {
                const name = String(value).split('=')[0];
                cookieHeader = cookieHeader
                        .split(';')
                        .map((part) => part.trim())
                        .filter((part) => part && part.split('=')[0] !== name)
                        .join('; ');
            }
        }
    });

    const requests = [];
    const api = configuration.api || (() => jsonResponse({result: 'error'}));
    async function fetch(url, fetchOptions) {
        if (url === 'console.html') {
            requests.push({kind: 'console'});
            if (configuration.consoleFailure) {
                throw new Error('console unavailable');
            }
            return textResponse(configuration.consoleTemplate || consoleTemplate);
        }
        const body = JSON.parse(fetchOptions.body);
        requests.push({kind: 'api', context: body.context, parameters: body.parameters});
        return api(body.context, body.parameters, requests);
    }

    let intervalSequence = 0;
    const activeIntervals = new Set();
    function setIntervalStub() {
        intervalSequence += 1;
        activeIntervals.add(intervalSequence);
        return intervalSequence;
    }
    function clearIntervalStub(id) {
        activeIntervals.delete(id);
    }

    const sessionStore = storage(configuration.sessionStorage);
    const localStore = storage(configuration.localStorage);
    const location = {
        origin: 'https://kanger.example',
        protocol: 'https:',
        search: '',
        pathname: '/'
    };
    const window = {
        KANGER_API_HOST: 'https://api.kanger.example',
        addEventListener(type, listener) {
            windowListeners[type] = listener;
        }
    };

    const context = {
        window,
        document,
        location,
        history: {replaceState() {}},
        sessionStorage: sessionStore,
        localStorage: localStore,
        fetch,
        URLSearchParams,
        AbortController,
        Promise,
        JSON,
        Object,
        Array,
        String,
        Number,
        Error,
        Set,
        Map,
        console,
        setTimeout,
        clearTimeout,
        setInterval: setIntervalStub,
        clearInterval: clearIntervalStub
    };
    window.window = window;
    window.document = document;
    window.location = location;
    window.sessionStorage = sessionStore;
    window.localStorage = localStore;
    window.fetch = fetch;
    window.URLSearchParams = URLSearchParams;
    window.AbortController = AbortController;
    window.setTimeout = setTimeout;
    window.clearTimeout = clearTimeout;
    window.setInterval = setIntervalStub;
    window.clearInterval = clearIntervalStub;

    vm.runInNewContext(source, context, {filename: 'html/gateway.js'});

    return {
        elements,
        sessionStore,
        localStore,
        requests,
        cookieWrites,
        frameMessages,
        frameWindow,
        windowListeners,
        documentListeners,
        async submitLogin() {
            const listener = elements['login-form'].listener('submit');
            assert(listener, 'login submit listener was not installed');
            return listener({preventDefault() {}});
        },
        messageFromFrame(data, sourceWindow = frameWindow,
                         origin = location.origin) {
            const listener = windowListeners.message;
            assert(listener, 'message listener was not installed');
            listener({source: sourceWindow, origin, data});
        }
    };
}

function capabilities() {
    return jsonResponse({
        result: 'OK',
        version: '3.3',
        auth: {
            registration_policy: 'TRUSTED',
            public_registration: false
        }
    });
}

async function duplicateLoginAndBootstrap() {
    const loginResponse = deferred();
    let loginCalls = 0;
    const harness = createHarness({
        api(context) {
            if (context === 'version') {
                return capabilities();
            }
            if (context === 'login') {
                loginCalls += 1;
                return loginResponse.promise;
            }
            throw new Error('unexpected API request: ' + context);
        }
    });
    await settle();

    harness.elements.login.value = 'alice';
    harness.elements.password.value = 'secret';
    const first = harness.submitLogin();
    const duplicate = harness.submitLogin();
    await settle(3);
    assert.strictEqual(loginCalls, 1, 'duplicate login reached the server');

    loginResponse.resolve(jsonResponse({
        result: 'OK',
        token: 'token-alice'
    }));
    await Promise.all([first, duplicate]);
    await settle();

    const stored = JSON.parse(
            harness.sessionStore.getItem('kanger.applicationSession.v1'));
    assert.strictEqual(stored.token, 'token-alice');
    assert.strictEqual(stored.login, 'alice');
    assert(stored.generation > 0);
    assert(harness.elements['console-frame'].srcdoc.includes(
            'window.KANGER_SESSION_BOOTSTRAP = Object.freeze'));
    assert(harness.elements['console-frame'].srcdoc.includes('token-alice'));
    assert(harness.cookieWrites.length >= 2);
    assert(harness.cookieWrites.every((value) => value.includes('Max-Age=0')),
            'supported login wrote a live application cookie');
    console.log('GATEWAY_SESSION_AUTHORITY_PASS duplicate-login-bootstrap');
}

async function legacyCookieMigration() {
    const harness = createHarness({
        cookies: 'token=legacy-token; login=legacy-user',
        api(context, parameters) {
            if (context === 'version') {
                return capabilities();
            }
            if (context === 'command' && parameters.ping === '') {
                return jsonResponse({result: 'OK'});
            }
            throw new Error('unexpected API request: ' + context);
        }
    });
    await settle();

    const stored = JSON.parse(
            harness.sessionStore.getItem('kanger.applicationSession.v1'));
    assert.strictEqual(stored.token, 'legacy-token');
    assert.strictEqual(stored.login, 'legacy-user');
    assert(harness.elements['console-frame'].srcdoc.includes('legacy-token'));
    assert(harness.cookieWrites.some((value) => value.startsWith('token=')));
    assert(harness.cookieWrites.some((value) => value.startsWith('login=')));
    assert(harness.cookieWrites.every((value) => value.includes('Max-Age=0')));
    console.log('GATEWAY_SESSION_AUTHORITY_PASS legacy-cookie-migration');
}

async function restoredSessionInvalidation() {
    const restored = JSON.stringify({
        token: 'expired-token',
        login: 'expired-user',
        generation: 9
    });
    const harness = createHarness({
        sessionStorage: {'kanger.applicationSession.v1': restored},
        api(context, parameters) {
            if (context === 'version') {
                return capabilities();
            }
            if (context === 'command' && parameters.ping === '') {
                return jsonResponse({result: 'error', description: 'expired'});
            }
            throw new Error('unexpected API request: ' + context);
        }
    });
    await settle();

    assert.strictEqual(
            harness.sessionStore.getItem('kanger.applicationSession.v1'), null);
    assert(harness.elements.notice.textContent.includes('no longer valid'));
    console.log('GATEWAY_SESSION_AUTHORITY_PASS invalid-restored-session');
}

async function unavailableServerRetainsRecoveryState() {
    const restored = JSON.stringify({
        token: 'recoverable-token',
        login: 'recoverable-user',
        generation: 11
    });
    const harness = createHarness({
        sessionStorage: {'kanger.applicationSession.v1': restored},
        api(context, parameters) {
            if (context === 'version') {
                return capabilities();
            }
            if (context === 'command' && parameters.ping === '') {
                throw new Error('network unavailable');
            }
            throw new Error('unexpected API request: ' + context);
        }
    });
    await settle();

    assert.strictEqual(
            harness.sessionStore.getItem('kanger.applicationSession.v1'), restored);
    assert(harness.elements.notice.textContent.includes('retained for a later retry'));
    console.log('GATEWAY_SESSION_AUTHORITY_PASS unavailable-retains-session');
}

async function generationAndLogoutAtomicity() {
    let quitMode = 'network';
    let quitCalls = 0;
    const harness = createHarness({
        api(context, parameters) {
            if (context === 'version') {
                return capabilities();
            }
            if (context === 'login') {
                return jsonResponse({result: 'OK', token: 'logout-token'});
            }
            if (context === 'command' && parameters.quit === '') {
                quitCalls += 1;
                if (quitMode === 'network') {
                    throw new Error('connection reset');
                }
                return jsonResponse({result: 'OK'});
            }
            if (context === 'command' && parameters.ping === '') {
                return jsonResponse({result: 'OK'});
            }
            throw new Error('unexpected API request: ' + context);
        }
    });
    await settle();
    harness.elements.login.value = 'bob';
    harness.elements.password.value = 'secret';
    await harness.submitLogin();
    await settle();

    const stored = JSON.parse(
            harness.sessionStore.getItem('kanger.applicationSession.v1'));
    const baseMessage = {
        channel: 'kanger.session.v1',
        type: 'session.logout',
        generation: stored.generation,
        payload: {}
    };

    harness.messageFromFrame(Object.assign({}, baseMessage, {
        generation: stored.generation + 1
    }));
    harness.messageFromFrame(baseMessage, {});
    await settle();
    assert.strictEqual(quitCalls, 0, 'stale or foreign iframe controlled logout');

    harness.messageFromFrame(baseMessage);
    await settle();
    assert.strictEqual(quitCalls, 1);
    assert(harness.sessionStore.getItem('kanger.applicationSession.v1'),
            'transport failure cleared the local session');
    assert(harness.frameMessages.some((entry) =>
        entry.message.type === 'session.error'
        && entry.message.payload.description.includes('retained')));

    quitMode = 'success';
    harness.messageFromFrame(baseMessage);
    await settle();
    assert.strictEqual(quitCalls, 2);
    assert.strictEqual(
            harness.sessionStore.getItem('kanger.applicationSession.v1'), null);
    assert.strictEqual(harness.elements['console-frame'].srcdoc, '');
    console.log('GATEWAY_SESSION_AUTHORITY_PASS generation-logout-atomicity');
}

async function main() {
    await duplicateLoginAndBootstrap();
    await legacyCookieMigration();
    await restoredSessionInvalidation();
    await unavailableServerRetainsRecoveryState();
    await generationAndLogoutAtomicity();
    console.log('GATEWAY_SESSION_AUTHORITY_OK');
}

main().catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exitCode = 1;
});

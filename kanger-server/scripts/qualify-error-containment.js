#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const root = process.argv[2] || path.resolve(__dirname, '..', '..');
const containmentSource = fs.readFileSync(
    path.join(root, 'html', 'containment.js'), 'utf8');
const errorSource = fs.readFileSync(
    path.join(root, 'html', 'error.js'), 'utf8');

function storage(initial) {
    const values = new Map(Object.entries(initial || {}));
    return {
        getItem(name) { return values.has(name) ? values.get(name) : null; },
        setItem(name, value) { values.set(name, String(value)); },
        removeItem(name) { values.delete(name); },
        snapshot() { return Object.fromEntries(values.entries()); }
    };
}

function response(body, status = 200) {
    return {
        ok: status >= 200 && status < 300,
        status,
        async text() { return body; }
    };
}

function tick() {
    return new Promise(resolve => setTimeout(resolve, 0));
}

async function qualifyContainment() {
    const secret = 'secret-bearer-token';
    const sessionStorage = storage({
        'kanger.applicationSession.v1': JSON.stringify({
            token: secret,
            login: 'owner',
            generation: 7
        }),
        'kanger.applicationSession.sequence': '7'
    });
    const localStorage = storage({
        'kanger.console.layout.sx': '42',
        'kanger.console.layout.qy': '17'
    });
    const listeners = {message: [], DOMContentLoaded: []};
    const childMessages = [];
    const contentWindow = {
        postMessage(message, targetOrigin) {
            childMessages.push({message, targetOrigin});
        }
    };

    function HTMLIFrameElement() {
        this.id = '';
        this._srcdoc = '';
        this.contentWindow = contentWindow;
        this.attributes = Object.create(null);
    }
    Object.defineProperty(HTMLIFrameElement.prototype, 'srcdoc', {
        configurable: true,
        enumerable: true,
        get() { return this._srcdoc; },
        set(value) { this._srcdoc = String(value); }
    });
    HTMLIFrameElement.prototype.getAttribute = function (name) {
        return Object.prototype.hasOwnProperty.call(this.attributes, name)
            ? this.attributes[name] : null;
    };
    HTMLIFrameElement.prototype.setAttribute = function (name, value) {
        this.attributes[name] = String(value);
    };

    const iframe = new HTMLIFrameElement();
    iframe.id = 'console-frame';
    iframe.setAttribute('sandbox', 'allow-scripts');

    const document = {
        getElementById(id) { return id === 'console-frame' ? iframe : null; },
        addEventListener(type, listener) {
            (listeners[type] || (listeners[type] = [])).push(listener);
        }
    };
    const fetchCalls = [];
    let fetchImpl = async (url, options) => {
        fetchCalls.push({url, options});
        return response(JSON.stringify({result: 'OK', description: 'pong'}));
    };
    const location = {
        origin: 'https://kanger.example',
        reloads: 0,
        reload() { this.reloads += 1; }
    };
    const window = {
        KANGER_API_HOST: 'https://api.kanger.example',
        HTMLIFrameElement,
        addEventListener(type, listener, capture) {
            (listeners[type] || (listeners[type] = [])).push({listener, capture});
        }
    };
    const context = vm.createContext({
        window,
        document,
        location,
        sessionStorage,
        localStorage,
        fetch(...args) { return fetchImpl(...args); },
        AbortController: global.AbortController,
        setTimeout,
        clearTimeout,
        JSON,
        Object,
        Number,
        String,
        Array,
        RegExp,
        Error,
        isFinite
    });
    vm.runInContext(containmentSource, context, {filename: 'containment.js'});

    assert.strictEqual(window.KANGER_CONTAINMENT_BOUNDARY.version, 1);
    assert.strictEqual(listeners.message.length, 1);
    for (const listener of listeners.DOMContentLoaded) {
        listener();
    }

    iframe.srcdoc = '<!doctype html><html><head></head><body>'
        + '<script>window.KANGER_SESSION_BOOTSTRAP={token:"' + secret + '"};<\/script>'
        + '</body></html>';
    assert(!iframe.srcdoc.includes(secret), 'bearer leaked into child srcdoc');
    assert(iframe.srcdoc.includes('__KANGER_PARENT_SESSION__'));
    assert(iframe.srcdoc.includes('sandbox') === false);
    assert(iframe.srcdoc.includes("connect-src 'none'"));
    assert(iframe.srcdoc.includes('Direct child network access is disabled'));
    assert(iframe.srcdoc.includes('kanger.containment.v1'));
    assert(iframe.srcdoc.includes('"sx":"42"'));
    console.log('ERROR_CONTAINMENT_PASS opaque-srcdoc-redaction');

    function dispatch(data, source = contentWindow, origin = 'null') {
        const entry = listeners.message[0];
        entry.listener({
            source,
            origin,
            data,
            stopped: false,
            stopImmediatePropagation() { this.stopped = true; }
        });
    }

    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 7,
        payload: {
            request_id: 1,
            packet: {context: 'command', parameters: {token: 'child-token', ping: ''}}
        }
    });
    await tick();
    assert.strictEqual(fetchCalls.length, 1);
    const sent = JSON.parse(fetchCalls[0].options.body);
    assert.strictEqual(sent.parameters.token, secret);
    assert.notStrictEqual(sent.parameters.token, 'child-token');
    assert.strictEqual(childMessages.length, 1);
    assert.strictEqual(childMessages[0].targetOrigin, '*');
    assert.strictEqual(childMessages[0].message.type, 'api.response');
    assert.strictEqual(childMessages[0].message.payload.request_id, 1);
    console.log('ERROR_CONTAINMENT_PASS authoritative-token-broker');

    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 6,
        payload: {request_id: 2, packet: {context: 'command', parameters: {ping: ''}}}
    });
    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 7,
        payload: {request_id: 3, packet: {context: 'command', parameters: {ping: ''}}}
    }, {});
    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 7,
        payload: {request_id: 4, packet: {context: 'command', parameters: {ping: ''}}}
    }, contentWindow, 'https://kanger.example');
    await tick();
    assert.strictEqual(fetchCalls.length, 1);
    console.log('ERROR_CONTAINMENT_PASS source-origin-generation-filter');

    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 7,
        payload: {request_id: 5, packet: {context: 'admin', parameters: {erase: ''}}}
    });
    await tick();
    assert.strictEqual(fetchCalls.length, 1);
    assert.strictEqual(childMessages.at(-1).message.type, 'api.error');
    assert.strictEqual(childMessages.at(-1).message.payload.data.error.domain, 'containment');
    assert.strictEqual(childMessages.at(-1).message.payload.data.code,
        'containment_context_denied');
    console.log('ERROR_CONTAINMENT_PASS context-denial');

    fetchImpl = async (url, options) => {
        fetchCalls.push({url, options});
        return response('not-json');
    };
    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 7,
        payload: {request_id: 6, packet: {context: 'query', parameters: {results: ''}}}
    });
    await tick();
    assert.strictEqual(childMessages.at(-1).message.payload.data.code,
        'protocol_invalid_json');
    assert.strictEqual(childMessages.at(-1).message.payload.data.error.domain,
        'protocol');
    console.log('ERROR_CONTAINMENT_PASS protocol-taxonomy');

    fetchImpl = async () => { throw new Error('offline'); };
    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 7,
        payload: {request_id: 7, packet: {context: 'history', parameters: {get: ''}}}
    });
    await tick();
    assert.strictEqual(childMessages.at(-1).message.payload.data.code,
        'transport_unavailable');
    assert.strictEqual(childMessages.at(-1).message.payload.data.error.retryable, true);
    console.log('ERROR_CONTAINMENT_PASS transport-taxonomy');

    fetchImpl = async (url, options) => {
        fetchCalls.push({url, options});
        return response(JSON.stringify({
            result: 'OK',
            session: {schema: 1, state: 'closed'}
        }));
    };
    dispatch({
        channel: 'kanger.containment.v1',
        type: 'api.request',
        generation: 7,
        payload: {
            request_id: 8,
            packet: {context: 'dialogue', parameters: {line: 'q'}}
        }
    });
    await tick();
    const dialogueSent = JSON.parse(fetchCalls.at(-1).options.body);
    assert.strictEqual(dialogueSent.context, 'dialogue');
    assert.strictEqual(dialogueSent.parameters.line, 'q');
    assert.strictEqual(dialogueSent.parameters.token, secret);
    assert.strictEqual(childMessages.at(-1).message.type, 'api.response');
    assert.strictEqual(childMessages.at(-1).message.payload.request_id, 8);
    assert.strictEqual(sessionStorage.getItem('kanger.applicationSession.v1'), null);
    await tick();
    assert.strictEqual(location.reloads, 1);
    console.log('ERROR_CONTAINMENT_PASS dialogue-session-closure');
}

async function qualifyErrorBoundary() {
    const callbacks = [];
    const window = {
        KANGER_WORKSPACE_STATE: {version: 1},
        logResponse(data, presentation, callback) {
            callbacks.push({data, presentation});
            if (callback) callback(data);
        },
        post(packet, callback) {
            callback(packet.__response);
        }
    };
    const context = vm.createContext({
        window,
        Object,
        String,
        RegExp,
        Error,
        setTimeout,
        clearTimeout
    });
    vm.runInContext(errorSource, context, {filename: 'error.js'});
    assert.strictEqual(window.KANGER_ERROR_BOUNDARY.version, 1);

    let application;
    window.post({__response: {
        result: 'error', code: 'storage_switch_failed', description: 'bad storage'
    }}, data => { application = data; });
    assert.strictEqual(application.error.domain, 'application');
    assert.strictEqual(application.error.operation_outcome, 'confirmed');

    let session;
    window.post({__response: {
        result: 'error', description: 'Authentication error'
    }}, data => { session = data; });
    assert.strictEqual(session.error.domain, 'session');
    assert.strictEqual(session.error.session_action, 'verify');

    const busy = window.KANGER_ERROR_BOUNDARY.normalize({
        result: 'error', code: 'operation_busy', description: 'busy'
    });
    assert.strictEqual(busy.error.domain, 'operation');
    assert.strictEqual(busy.error.retryable, true);
    assert.strictEqual(busy.error.operation_outcome, 'not_applied');

    const unavailable = window.KANGER_ERROR_BOUNDARY.local(
        'transport', 'transport_unavailable', 'offline', true,
        'retain', 'unknown');
    assert.strictEqual(window.KANGER_ERROR_BOUNDARY.describe(unavailable),
        '[transport:transport_unavailable] offline');

    window.logResponse(unavailable);
    assert.strictEqual(callbacks.at(-1).presentation,
        '[transport:transport_unavailable] offline');
    console.log('ERROR_CONTAINMENT_PASS unified-error-reaction');
}

(async function main() {
    await qualifyContainment();
    await qualifyErrorBoundary();
    console.log('ERROR_CONTAINMENT_OK');
}()).catch(error => {
    console.error(error && error.stack ? error.stack : error);
    process.exit(1);
});

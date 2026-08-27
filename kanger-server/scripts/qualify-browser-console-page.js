#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = process.argv[2] || path.resolve(__dirname, '..', '..');
const page = fs.readFileSync(path.join(root, 'html', 'console.html'), 'utf8');

const staticScripts = Array.from(page.matchAll(
    /<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*><\/script>/gi))
    .map(match => match[1]);
const expectedStaticScripts = [
    'jquery-3.6.0.min.js',
    'codemirror.js',
    'javascript.js',
    'javascript-mode.js',
    'javascript-mode-vendor.js',
    'operation.js',
    'workspace.js',
    'editor-state.js',
    'error.js',
    'dialogue.js',
    'presentation.js',
    'bottom-layout.js',
    'editor-local-file.js'
];
assert.deepStrictEqual(staticScripts, expectedStaticScripts,
    'console.html must declare the complete opaque-sandbox script topology');
[
    'javascript.js',
    'javascript-mode.js',
    'workspace.js',
    'bottom-layout.js'
].forEach(file => {
    const source = fs.readFileSync(path.join(root, 'html', file), 'utf8');
    assert(!source.includes('document.write('),
        file + ' must not mutate parser-time script topology');
});

class Node {
    constructor(type, value) {
        this.type = type || 'div';
        this.style = {};
        this.attributes = {};
        this.childNodes = [];
        this.children = this.childNodes;
        this.parentNode = null;
        this.value = value || '';
        this.innerHTML = '';
        this.scrollTop = 0;
        this.scrollHeight = 0;
    }
    appendChild(child) {
        this.childNodes.push(child);
        child.parentNode = this;
        return child;
    }
    append(child) { return this.appendChild(child); }
    removeChild(child) {
        const index = this.childNodes.indexOf(child);
        if (index >= 0) this.childNodes.splice(index, 1);
        child.parentNode = null;
        return child;
    }
    insertBefore(child, before) {
        const index = this.childNodes.indexOf(before);
        if (index < 0) return this.appendChild(child);
        this.childNodes.splice(index, 0, child);
        child.parentNode = this;
        return child;
    }
    setAttribute(name, value) { this.attributes[name] = String(value); }
    getAttribute(name) {
        return Object.prototype.hasOwnProperty.call(this.attributes, name)
            ? this.attributes[name] : null;
    }
    removeAttribute(name) { delete this.attributes[name]; }
    addEventListener() {}
    focus() { this.focused = true; }
    click() {}
    get firstChild() { return this.childNodes[0] || null; }
    get textContent() {
        return this.value + this.childNodes.map(child => child.textContent).join('');
    }
    set textContent(value) {
        this.value = String(value);
        this.childNodes = [];
        this.children = this.childNodes;
    }
}

const nodes = new Map();
function element(id) {
    if (!nodes.has(id)) nodes.set(id, new Node('div'));
    return nodes.get(id);
}
[
    'super', 'register', 'password', 'login', 'login-in',
    'version-code-login', 'version-code-reg', 'query-input', 'query-history'
].forEach(element);

const document = {
    cookie: '',
    readyState: 'loading',
    body: new Node('body'),
    documentElement: new Node('html'),
    location: {reload() {}},
    write() {},
    createElement(type) { return new Node(type); },
    createTextNode(value) { return new Node('text', String(value)); },
    createDocumentFragment() { return new Node('fragment'); },
    getElementById(id) { return element(id); },
    addEventListener() {}
};

const readyCallbacks = [];
function jQuery() {
    return Object.create(jQuery.fn);
}
jQuery.fn = {
    ready(callback) {
        readyCallbacks.push(callback);
        return this;
    }
};

const requests = [];
const dialoguePackets = [];
jQuery.post = function (url, encoded, callback) {
    const packet = JSON.parse(encoded);
    requests.push(packet);
    let response = {result: 'OK', version: 'test', size: 0, list: []};
    if (packet.context === 'dialogue') {
        dialoguePackets.push(packet);
        response = packet.parameters.line === 's'
            ? {result: 'error', code: 'command_parse_error',
                reason: 'AMBIGUOUS_PREFIX', description: 'ambiguous'}
            : {result: 'OK', canonical_intent: packet.parameters.line === 'squash'
                ? 'TX_SQUASH' : 'CORE', transaction: 1, empty: false};
    }
    if (typeof callback === 'function') callback(response);
};

let timerId = 0;
function immediate(callback, delay) {
    const id = ++timerId;
    if (!delay) callback();
    return id;
}
function interval() { return ++timerId; }

const window = {
    window: null,
    document,
    location: {href: 'http://localhost:1964/console.html'},
    jQuery,
    $: jQuery,
    token: '',
    editor: {},
    event: null,
    setTimeout: immediate,
    clearTimeout() {},
    setInterval: interval,
    clearInterval() {},
    addEventListener() {},
    alert() {},
    btoa(value) { return Buffer.from(String(value), 'binary').toString('base64'); },
    atob(value) { return Buffer.from(String(value), 'base64').toString('binary'); },
    CodeMirror: {fromTextArea() { return {}; }},
    post(packet, callback) {
        requests.push(packet);
        if (typeof callback === 'function') callback({result: 'OK'});
    }
};
window.window = window;

Object.assign(window, {
    Object,
    String,
    Number,
    Boolean,
    Array,
    JSON,
    Error,
    Date,
    Math,
    Map,
    console,
    isFinite,
    parseInt,
    encodeURIComponent,
    decodeURIComponent,
    setTimeout: immediate,
    clearTimeout() {},
    setInterval: interval,
    clearInterval() {}
});

const context = vm.createContext(window);
[
    'javascript.js',
    'javascript-mode.js',
    'operation.js',
    'workspace.js',
    'error.js',
    'dialogue.js'
].forEach(file => {
    const source = fs.readFileSync(path.join(root, 'html', file), 'utf8');
    vm.runInContext(source, context, {filename: file});
});

const inlineScripts = Array.from(page.matchAll(
    /<script([^>]*)>([\s\S]*?)<\/script>/gi))
    .filter(match => !/\bsrc\s*=/.test(match[1]))
    .map(match => match[2]);
assert.strictEqual(inlineScripts.length, 1,
    'console.html inline script topology changed');
vm.runInContext(inlineScripts[0], context, {filename: 'console.html:inline'});
const legacyCommand = window.command;
const legacyQuery = window.query;

/* Keep this test focused on command ownership; editor adaptation is qualified
 * independently and is intentionally disabled before the real ready chain. */
window.compileSource = null;
assert.strictEqual(readyCallbacks.length, 1,
    'real console ready callback did not pass through ownership wrappers');
readyCallbacks[0].call(document);

assert(window.KANGER_TRUSTED_RENDERING,
    'real ready chain did not install trusted rendering');
assert(window.KANGER_OPERATION_PROTOCOL,
    'real ready chain did not install the operation protocol');
assert(window.KANGER_WORKSPACE_STATE,
    'real ready chain did not install the workspace authority');
assert(window.KANGER_ERROR_BOUNDARY,
    'real ready chain did not install the error boundary');
assert(window.KANGER_DIALOGUE_TRANSPORT,
    'real ready chain did not install canonical dialogue');
assert(window.KANGER_STARTUP_ADAPTER,
    'real startup adapter did not observe the historical command function');
assert.notStrictEqual(window.command, legacyCommand,
    'full ready chain left the legacy Browser command parser executable');
assert.notStrictEqual(window.query, legacyQuery,
    'full ready chain left the legacy Browser Core entry point executable');
assert.strictEqual(window.query, window.KANGER_DIALOGUE_TRANSPORT.dispatch,
    'full ready chain left a separate Browser Core entry point executable');

const legacyErrors = [];
window.logError = message => legacyErrors.push(String(message));
window.refreshScreen = function () {};
window.showTransactionLevel = function () {};
window.logResponse = function () {};

const directBefore = dialoguePackets.length;
let directResponse = null;
window.command('direct-probe', function (data) { directResponse = data; });
assert.strictEqual(dialoguePackets.length, directBefore + 1,
    'qualified command boundary did not delegate raw dialogue; requests='
    + JSON.stringify(requests) + '; command=' + String(window.command)
    + '; canonical=' + String(window.KANGER_DIALOGUE_TRANSPORT.dispatch)
    + '; response=' + JSON.stringify(directResponse)
    + '; operation='
    + JSON.stringify(window.KANGER_OPERATION_PROTOCOL.snapshot()));
assert.strictEqual(dialoguePackets[dialoguePackets.length - 1].parameters.line,
    'direct-probe', 'qualified command boundary rewrote operator dialogue');

function enter(line) {
    const input = element('query-input');
    input.value = line;
    window.event = {ctrlKey: false, keyCode: 13};
    const before = dialoguePackets.length;
    assert.strictEqual(window.check_enter(), false,
        'Enter was not consumed by the real page handler');
    assert.strictEqual(dialoguePackets.length, before + 1,
        'real page did not emit exactly one dialogue packet for ' + line
        + '; requests=' + JSON.stringify(requests));
    const packet = dialoguePackets[dialoguePackets.length - 1];
    assert.deepStrictEqual(Object.keys(packet).sort(), ['context', 'parameters']);
    assert.strictEqual(packet.context, 'dialogue');
    assert.strictEqual(packet.parameters.line, line,
        'real page rewrote operator dialogue');
}

enter('squash');
enter('s');
enter('?father(John,Tom);');

assert.deepStrictEqual(legacyErrors, [],
    'real page reached its local Unknown command fallback');
assert(!dialoguePackets.some(packet => packet.context !== 'dialogue'),
    'real page emitted a second command/query protocol');

console.log('BROWSER_CONSOLE_PAGE_PASS full-ready-ownership');
console.log('BROWSER_CONSOLE_PAGE_PASS real-check-enter');
console.log('BROWSER_CONSOLE_PAGE_PASS squash-and-ambiguity-raw');
console.log('BROWSER_CONSOLE_PAGE_OK');

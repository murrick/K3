'use strict';
const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

class FakeNode {
    constructor(tagName) {
        this.nodeType = 1;
        this.tagName = tagName || 'DIV';
        this.childNodes = [];
        this.parentNode = null;
        this.style = {};
        this.id = '';
    }
    get firstChild() { return this.childNodes[0] || null; }
    appendChild(node) {
        if (node.parentNode) node.parentNode.removeChild(node);
        this.childNodes.push(node);
        node.parentNode = this;
        return node;
    }
    removeChild(node) {
        const index = this.childNodes.indexOf(node);
        if (index >= 0) this.childNodes.splice(index, 1);
        node.parentNode = null;
        return node;
    }
    get textContent() {
        return this.childNodes.map((node) => node.textContent).join('');
    }
    set textContent(value) {
        this.childNodes = [];
        if (String(value) !== '') this.appendChild(new FakeText(String(value)));
    }
}

class FakeText {
    constructor(data) {
        this.nodeType = 3;
        this.tagName = '';
        this.data = data;
        this.parentNode = null;
    }
    get textContent() { return this.data; }
    set textContent(value) { this.data = String(value); }
}

const targetIds = [
    'statements', 'functions', 'query-results', 'query-solutions',
    'query-hypothesis', 'query-log'
];
const elements = {};
targetIds.concat(['container-hypothesis']).forEach((id) => {
    const node = new FakeNode('DIV');
    node.id = id;
    elements[id] = node;
});
elements['query-hypothesis'].textContent = 'stale hypothesis';
elements['container-hypothesis'].style.display = '';

const document = {
    createElement(tagName) { return new FakeNode(String(tagName).toUpperCase()); },
    createTextNode(value) { return new FakeText(String(value)); },
    getElementById(id) { return elements[id] || null; }
};

const requests = [];
function transport(packet, callback) {
    const request = {packet, callback, resolved: false};
    requests.push(request);
    return request;
}

const window = {
    window: null,
    document,
    token: 'token',
    KANGER_OPERATION_TIMEOUT_MS: 1000,
    KANGER_TRUSTED_RENDERING: Object.freeze({version: 1, installed: true}),
    post: transport,
    logRequest() {},
    logResponse() {},
    placeElements() {},
    setQueryStatus() {},
    dropQueryStatus() {},
    refreshScreen() {},
    setTimeout,
    clearTimeout,
    isFinite,
    Object,
    Array,
    Number,
    String,
    Error,
    console
};
window.window = window;
[
    'showStatements', 'showFunctions', 'showResults',
    'showSolutions', 'showHypothesis', 'showLog'
].forEach((name) => { window[name] = function () {}; });

const context = {
    window,
    document,
    setTimeout,
    clearTimeout,
    isFinite,
    Object,
    Array,
    Number,
    String,
    Error,
    console
};
vm.runInNewContext(fs.readFileSync('html/operation.js', 'utf8'), context,
        {filename: 'operation.js'});

let response = null;
window.post({
    context: 'query',
    parameters: {token: 'token', request: '?target(item);'}
}, (data) => { response = data; });

const mutation = requests.find((item) => item.packet.context === 'query'
        && Object.prototype.hasOwnProperty.call(item.packet.parameters, 'request'));
assert(mutation, 'serialized query mutation was not sent');
mutation.resolved = true;
mutation.callback({
    result: 'OK',
    response: 'unknown',
    hypothesis: 1,
    description: 'Result: WHO KNOWS? Hypothesis found'
});

assert(response && response.response === 'unknown', 'query response was not delivered');
assert.strictEqual(elements['query-hypothesis'].childNodes.length, 0,
        'previous completed hypothesis rows survived new query settlement');
assert.strictEqual(elements['container-hypothesis'].style.display, 'none',
        'stale hypothesis panel remained visible while new projection settles');
console.log('OPERATION_PROTOCOL_PASS stale-hypothesis-projection-clear');

'use strict';
const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

class FakeNode {
    constructor(type, tagName) {
        this.nodeType = type;
        this.tagName = tagName || '';
        this.childNodes = [];
        this.parentNode = null;
        this.style = {};
        this.id = '';
        this.value = '';
        this.listeners = {};
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
        if (this.nodeType === 3) return this.data;
        return this.childNodes.map((node) => node.textContent).join('');
    }
    set textContent(value) {
        this.childNodes = [];
        if (String(value) !== '') this.appendChild(new FakeText(String(value)));
    }
    addEventListener(type, listener) { this.listeners[type] = listener; }
    focus() {}
}
class FakeText extends FakeNode {
    constructor(data) { super(3, ''); this.data = data; }
    get textContent() { return this.data; }
    set textContent(value) { this.data = String(value); }
}

function deferredTransport() {
    const requests = [];
    return {
        requests,
        post(packet, callback) {
            const record = {packet, callback, resolved: false};
            requests.push(record);
            return record;
        },
        unresolved(predicate) {
            return requests.filter((item) => !item.resolved && predicate(item.packet));
        },
        resolve(record, data) {
            assert(record && !record.resolved, 'request must be unresolved');
            record.resolved = true;
            if (typeof record.callback === 'function') record.callback(data);
        }
    };
}

function settle(ms = 0) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function snapshotReads(transport) {
    return transport.unresolved((p) => p.context === 'query'
            && !Object.prototype.hasOwnProperty.call(p.parameters, 'request')
            && !Object.prototype.hasOwnProperty.call(p.parameters, 'compile')
            && !Object.prototype.hasOwnProperty.call(p.parameters, 'transaction'));
}

async function main() {
    const targetIds = [
        'statements', 'functions', 'query-results', 'query-solutions',
        'query-hypothesis', 'query-log'
    ];
    const elements = {};
    targetIds.concat(['query-input', 'query-message']).forEach((id) => {
        const element = new FakeNode(1, 'DIV');
        element.id = id;
        element.textContent = targetIds.includes(id) ? 'baseline-' + id : '';
        elements[id] = element;
    });
    const document = {
        createElement(tagName) { return new FakeNode(1, String(tagName).toUpperCase()); },
        createTextNode(value) { return new FakeText(String(value)); },
        getElementById(id) { return elements[id] || null; }
    };
    const transport = deferredTransport();
    const window = {
        window: null,
        document,
        KANGER_OPERATION_TIMEOUT_MS: 25,
        token: 'token',
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
    window.post = transport.post;
    window.setQueryStatus = function (text) { window.status = text; };
    window.dropQueryStatus = function () { window.status = ''; };
    window.placeElements = function (data) {
        window.post({
            context: 'query',
            parameters: {token: window.token, functions: ''}
        }, function (tmp) {
            window.layoutCommits = (window.layoutCommits || 0) + 1;
            window.lastLayoutData = data;
            window.layoutFunctionCount = tmp && tmp.size;
        });
    };
    window.storeHistory = function (text, callback) {
        window.post({context: 'history', parameters: {put: text}}, callback);
    };
    window.logRequest = function (text, callback) {
        window.requestLog = (window.requestLog || []).concat([text]);
        window.storeHistory('Q:' + text, callback);
    };
    window.logResponse = function (data, presentation, callback) {
        window.responseLog = (window.responseLog || []).concat([
            presentation || (data && data.description) || ''
        ]);
        window.storeHistory('R:' + (presentation || ''), callback);
    };

    const rendererKeys = [
        ['showStatements', 'statements', 'predicates'],
        ['showFunctions', 'functions', 'functions'],
        ['showResults', 'query-results', 'results'],
        ['showSolutions', 'query-solutions', 'solutions'],
        ['showHypothesis', 'query-hypothesis', 'hypothesis'],
        ['showLog', 'query-log', 'log']
    ];
    rendererKeys.forEach(([name, id, key]) => {
        window[name] = function () {
            const target = document.getElementById(id);
            while (target.firstChild) target.removeChild(target.firstChild);
            const parameters = {token: window.token};
            parameters[key] = '';
            window.post({context: 'query', parameters}, function (data) {
                target.appendChild(document.createTextNode(data.value));
            });
        };
    });
    window.refreshScreen = function () {};

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

    window.KANGER_TRUSTED_RENDERING =
            Object.freeze({version: 1, installed: true});
    assert(window.KANGER_OPERATION_PROTOCOL);

    let historyContinuation = false;
    window.logRequest('!history~~~<b>x</b>', function () {
        historyContinuation = true;
    });
    assert.strictEqual(historyContinuation, true,
            'operation execution waited for history persistence');
    assert.strictEqual(transport.unresolved((p) => p.context === 'history').length, 1);
    console.log('OPERATION_PROTOCOL_PASS history-decoupling');

    let firstResult = null;
    let busyResult = null;
    window.post({context: 'query', parameters: {request: '!a'}},
            (data) => { firstResult = data; });
    window.post({context: 'query', parameters: {request: '!b'}},
            (data) => { busyResult = data; });
    await settle(2);
    const firstMutation = transport.unresolved((p) => p.context === 'query'
            && Object.prototype.hasOwnProperty.call(p.parameters, 'request'))[0];
    assert(firstMutation);
    assert.strictEqual(transport.unresolved((p) => p.context === 'query'
            && Object.prototype.hasOwnProperty.call(p.parameters, 'request')).length, 1);
    assert.strictEqual(busyResult.code, 'operation_busy');
    assert.strictEqual(busyResult.blocking_operation_id, 1);
    transport.resolve(firstMutation, {
        result: 'OK',
        response: 'unknown',
        results: 2,
        solutions: 1,
        hypothesis: 1,
        description: 'first'
    });
    assert.strictEqual(firstResult.client_operation_id, 1);
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 1,
            'main response released BUSY before snapshot settlement');
    assert.strictEqual(window.status, 'Operation #1: request');
    await settle(2);
    const firstSnapshot = snapshotReads(transport).slice();
    assert.strictEqual(firstSnapshot.length, 6);

    let settlingBusy = null;
    window.post({
        context: 'dialogue',
        parameters: {token: 'token', line: 'erase'}
    }, (data) => { settlingBusy = data; });
    await settle(2);
    assert(settlingBusy && settlingBusy.code === 'operation_busy');
    assert.strictEqual(settlingBusy.blocking_operation_id, 1);
    assert.strictEqual(transport.unresolved((p) => p.context === 'dialogue').length, 0);

    for (let i = 0; i < 5; i++) {
        transport.resolve(firstSnapshot[i], {result: 'OK', value: 'first-' + i});
    }
    targetIds.forEach((id) => assert.strictEqual(elements[id].textContent,
            'baseline-' + id, 'partial snapshot leaked into live DOM'));
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 1);

    transport.resolve(firstSnapshot[5], {result: 'OK', value: 'first-5'});
    targetIds.forEach((id, index) => assert.strictEqual(
            elements[id].textContent, 'first-' + index));
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 1,
            'semantic snapshot released BUSY before layout settlement');
    assert.strictEqual(window.status, 'Operation #1: request');
    assert.strictEqual(window.layoutCommits || 0, 0);
    const firstLayout = snapshotReads(transport).filter(
            (record) => firstSnapshot.indexOf(record) < 0);
    assert.strictEqual(firstLayout.length, 1,
            'expected exactly one async layout read after semantic snapshot');

    let layoutBusy = null;
    window.post({
        context: 'dialogue',
        parameters: {token: 'token', line: 'storage'}
    }, (data) => { layoutBusy = data; });
    await settle(2);
    assert(layoutBusy && layoutBusy.code === 'operation_busy');
    assert.strictEqual(layoutBusy.blocking_operation_id, 1);

    transport.resolve(firstLayout[0], {result: 'OK', size: 1});
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 0);
    assert.strictEqual(window.status, '');
    assert.strictEqual(window.layoutCommits, 1);
    assert.strictEqual(window.layoutFunctionCount, 1);
    assert.strictEqual(window.lastLayoutData.results, 0);
    assert.strictEqual(window.lastLayoutData.solutions, 0);
    assert.strictEqual(window.lastLayoutData.hypothesis, 1);
    assert.strictEqual(firstResult.results, 2,
            'WHO KNOWS presentation mutated canonical response');
    assert.strictEqual(firstResult.solutions, 1,
            'WHO KNOWS presentation mutated canonical response');
    console.log('OPERATION_PROTOCOL_PASS one-mutation-in-flight');
    console.log('OPERATION_PROTOCOL_PASS busy-through-snapshot-settlement');
    console.log('OPERATION_PROTOCOL_PASS busy-through-layout-settlement');
    console.log('OPERATION_PROTOCOL_PASS unknown-panel-suppression');
    console.log('OPERATION_PROTOCOL_PASS coherent-snapshot-barrier');

    let secondResult = null;
    window.post({
        context: 'dialogue',
        parameters: {token: 'token', line: 'erase'}
    }, (data) => { secondResult = data; });
    const secondMutation = transport.unresolved((p) => p.context === 'dialogue')[0];
    assert(secondMutation);
    assert.strictEqual(secondMutation.packet.parameters.line, 'erase');
    assert.strictEqual(window.status, 'Operation #5: dialogue');
    transport.resolve(secondMutation, {result: 'OK', description: 'second'});
    assert.strictEqual(secondResult.client_operation_id, 5);
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 5);
    await settle(2);
    const secondSnapshot = snapshotReads(transport).slice();
    assert.strictEqual(secondSnapshot.length, 6);
    for (let i = 0; i < 5; i++) {
        transport.resolve(secondSnapshot[i], {result: 'OK', value: 'fresh-' + i});
    }
    targetIds.forEach((id, index) => assert.strictEqual(
            elements[id].textContent, 'first-' + index,
            'partial second snapshot leaked into live DOM'));
    transport.resolve(secondSnapshot[5], {result: 'OK', value: 'fresh-5'});
    targetIds.forEach((id, index) => assert.strictEqual(
            elements[id].textContent, 'fresh-' + index));
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 5);
    const secondLayout = snapshotReads(transport).filter(
            (record) => secondSnapshot.indexOf(record) < 0);
    assert.strictEqual(secondLayout.length, 1);
    transport.resolve(secondLayout[0], {result: 'OK', size: 0});
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 0);
    assert.strictEqual(window.layoutCommits, 2);
    console.log('OPERATION_PROTOCOL_PASS dialogue-serialization');

    let staleReadCalled = false;
    window.post({context: 'command', parameters: {token: 'token', help: ''}},
            () => { staleReadCalled = true; });
    const staleRead = transport.unresolved((p) => p.context === 'command'
            && Object.prototype.hasOwnProperty.call(p.parameters, 'help'))[0];
    window.post({context: 'command', parameters: {token: 'token', delete: 'x.k'}},
            () => {});
    const thirdMutation = transport.unresolved((p) => p.context === 'command'
            && Object.prototype.hasOwnProperty.call(p.parameters, 'delete'))[0];
    transport.resolve(thirdMutation, {result: 'OK'});
    transport.resolve(staleRead, {result: 'OK', description: 'late read'});
    assert.strictEqual(staleReadCalled, false);
    await settle(2);
    const thirdSnapshot = snapshotReads(transport).slice();
    assert.strictEqual(thirdSnapshot.length, 6);
    thirdSnapshot.forEach((record, index) => {
        transport.resolve(record, {result: 'OK', value: 'third-' + index});
    });
    assert(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId > 0);
    const thirdLayout = snapshotReads(transport).filter(
            (record) => thirdSnapshot.indexOf(record) < 0);
    assert.strictEqual(thirdLayout.length, 1);
    transport.resolve(thirdLayout[0], {result: 'OK', size: 0});
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId, 0);
    console.log('OPERATION_PROTOCOL_PASS stale-read-rejection');

    let timeoutResult = null;
    window.post({context: 'query', parameters: {compile: 'source'}},
            (data) => { timeoutResult = data; });
    const timedMutation = transport.unresolved((p) => p.context === 'query'
            && Object.prototype.hasOwnProperty.call(p.parameters, 'compile'))[0];
    await settle(35);
    assert(timeoutResult && timeoutResult.code === 'operation_timeout');
    const timeoutOperationId = timeoutResult.client_operation_id;
    const snapshotBeforeLate =
            window.KANGER_OPERATION_PROTOCOL.snapshot().currentSnapshotId;
    transport.resolve(timedMutation, {result: 'OK', description: 'too late'});
    await settle(2);
    assert.strictEqual(timeoutResult.client_operation_id, timeoutOperationId);
    assert(window.KANGER_OPERATION_PROTOCOL.snapshot().currentSnapshotId
            > snapshotBeforeLate,
            'late mutation response did not request authoritative resync');
    console.log('OPERATION_PROTOCOL_PASS late-operation-rejection');
    console.log('OPERATION_PROTOCOL_PASS late-operation-resync');

    const protocol = window.KANGER_OPERATION_PROTOCOL.snapshot();
    assert.strictEqual(protocol.activeOperationId, 0);
    assert(protocol.generation >= 4);
    console.log('OPERATION_PROTOCOL_OK');
}

main().catch((error) => {
    console.error(error.stack || error);
    process.exitCode = 1;
});

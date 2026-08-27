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

function isMutationRequest(packet) {
    return packet.context === 'query'
            && Object.prototype.hasOwnProperty.call(packet.parameters, 'request');
}

function isSnapshotRequest(packet) {
    return packet.context === 'query'
            && !Object.prototype.hasOwnProperty.call(packet.parameters, 'request')
            && !Object.prototype.hasOwnProperty.call(packet.parameters, 'compile');
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
    window.placeElements = function () { window.layoutCommits = (window.layoutCommits || 0) + 1; };
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
    const firstMutation = transport.unresolved(isMutationRequest)[0];
    assert(firstMutation);
    assert.strictEqual(transport.unresolved(isMutationRequest).length, 1);
    assert.strictEqual(busyResult.code, 'operation_busy');
    assert.strictEqual(busyResult.blocking_operation_id, 1);
    transport.resolve(firstMutation, {result: 'OK', description: 'first'});
    assert.strictEqual(firstResult.client_operation_id, 1);
    await settle(2);
    const oldSnapshot = transport.unresolved(isSnapshotRequest);
    assert.strictEqual(oldSnapshot.length, 6);
    const firstSettling = window.KANGER_OPERATION_PROTOCOL.snapshot();
    assert.strictEqual(firstSettling.activeOperationId, 0);
    assert.strictEqual(firstSettling.settlingOperationId, 1);
    assert.notStrictEqual(window.status, '',
            'query status cleared before semantic snapshot committed');
    console.log('OPERATION_PROTOCOL_PASS one-mutation-in-flight');

    let settlingBusy = null;
    window.post({
        context: 'dialogue',
        parameters: {token: 'token', line: 'erase'}
    }, (data) => { settlingBusy = data; });
    await settle(2);
    assert(settlingBusy);
    assert.strictEqual(settlingBusy.code, 'operation_busy');
    assert.strictEqual(settlingBusy.blocking_operation_id, 1);
    assert.strictEqual(transport.unresolved((p) => p.context === 'dialogue').length, 0,
            'settling mutation allowed a second authoritative request');
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().settlingOperationId, 1);
    console.log('OPERATION_PROTOCOL_PASS mutation-blocked-during-settlement');

    window.KANGER_OPERATION_PROTOCOL.requestSnapshot();
    await settle(2);
    const allSnapshots = transport.unresolved(isSnapshotRequest);
    const replacementSnapshot = allSnapshots.filter((record) =>
        oldSnapshot.indexOf(record) < 0);
    assert.strictEqual(replacementSnapshot.length, 6);

    oldSnapshot.forEach((record, index) => {
        transport.resolve(record, {result: 'OK', value: 'stale-' + index});
    });
    targetIds.forEach((id) => assert.strictEqual(elements[id].textContent,
            'baseline-' + id));

    for (let i = 0; i < 5; i++) {
        transport.resolve(replacementSnapshot[i],
                {result: 'OK', value: 'fresh-' + i});
    }
    targetIds.forEach((id) => assert.strictEqual(elements[id].textContent,
            'baseline-' + id, 'partial snapshot leaked into live DOM'));
    transport.resolve(replacementSnapshot[5], {result: 'OK', value: 'fresh-5'});
    targetIds.forEach((id, index) => assert.strictEqual(
            elements[id].textContent, 'fresh-' + index));
    assert.strictEqual(window.layoutCommits, 1);
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().settlingOperationId, 0);
    assert.strictEqual(window.status, '',
            'query status remained busy after semantic snapshot committed');
    console.log('OPERATION_PROTOCOL_PASS coherent-snapshot-barrier');
    console.log('OPERATION_PROTOCOL_PASS stale-snapshot-rejection');
    console.log('OPERATION_PROTOCOL_PASS settlement-release-after-snapshot');

    let secondResult = null;
    window.post({
        context: 'dialogue',
        parameters: {token: 'token', line: 'erase'}
    }, (data) => { secondResult = data; });
    const secondMutation = transport.unresolved((p) => p.context === 'dialogue')[0];
    assert(secondMutation);
    assert.strictEqual(secondMutation.packet.parameters.line, 'erase');
    const secondOperationId = window.KANGER_OPERATION_PROTOCOL.snapshot().activeOperationId;
    assert(secondOperationId > 1);
    assert.strictEqual(window.status,
            'Operation #' + secondOperationId + ': dialogue');
    transport.resolve(secondMutation, {result: 'OK', description: 'second'});
    assert.strictEqual(secondResult.client_operation_id, secondOperationId);
    await settle(2);
    const secondSnapshot = transport.unresolved(isSnapshotRequest);
    assert.strictEqual(secondSnapshot.length, 6);
    for (let i = 0; i < 5; i++) {
        transport.resolve(secondSnapshot[i], {result: 'OK', value: 'second-' + i});
    }
    targetIds.forEach((id, index) => assert.strictEqual(
            elements[id].textContent, 'fresh-' + index,
            'second partial snapshot leaked into live DOM'));
    transport.resolve(secondSnapshot[5], {result: 'OK', value: 'second-5'});
    targetIds.forEach((id, index) => assert.strictEqual(
            elements[id].textContent, 'second-' + index));
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().settlingOperationId, 0);
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
    assert(thirdMutation);
    transport.resolve(thirdMutation, {result: 'OK'});
    transport.resolve(staleRead, {result: 'OK', description: 'late read'});
    assert.strictEqual(staleReadCalled, false);
    await settle(2);
    const thirdSnapshot = transport.unresolved(isSnapshotRequest);
    assert.strictEqual(thirdSnapshot.length, 6);
    thirdSnapshot.forEach((record, index) => {
        transport.resolve(record, {result: 'OK', value: 'third-' + index});
    });
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().settlingOperationId, 0);
    console.log('OPERATION_PROTOCOL_PASS stale-read-rejection');

    let timeoutResult = null;
    window.post({context: 'query', parameters: {compile: 'source'}},
            (data) => { timeoutResult = data; });
    const timedMutation = transport.unresolved((p) => p.context === 'query'
            && Object.prototype.hasOwnProperty.call(p.parameters, 'compile'))[0];
    assert(timedMutation);
    await settle(35);
    assert(timeoutResult && timeoutResult.code === 'operation_timeout');
    const timeoutOperationId = timeoutResult.client_operation_id;
    const timeoutSnapshot = transport.unresolved(isSnapshotRequest);
    assert.strictEqual(timeoutSnapshot.length, 6);
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().settlingOperationId,
            timeoutOperationId);
    const snapshotBeforeLate =
            window.KANGER_OPERATION_PROTOCOL.snapshot().currentSnapshotId;

    transport.resolve(timedMutation, {result: 'OK', description: 'too late'});
    await settle(2);
    assert.strictEqual(timeoutResult.client_operation_id, timeoutOperationId);
    assert(window.KANGER_OPERATION_PROTOCOL.snapshot().currentSnapshotId
            > snapshotBeforeLate,
            'late mutation response did not request authoritative resync');
    const timeoutSnapshots = transport.unresolved(isSnapshotRequest);
    const timeoutReplacement = timeoutSnapshots.filter((record) =>
        timeoutSnapshot.indexOf(record) < 0);
    assert.strictEqual(timeoutReplacement.length, 6);
    timeoutSnapshot.forEach((record, index) => {
        transport.resolve(record, {result: 'OK', value: 'timeout-stale-' + index});
    });
    timeoutReplacement.forEach((record, index) => {
        transport.resolve(record, {result: 'OK', value: 'timeout-fresh-' + index});
    });
    assert.strictEqual(window.KANGER_OPERATION_PROTOCOL.snapshot().settlingOperationId, 0);
    assert.strictEqual(window.status, '');
    console.log('OPERATION_PROTOCOL_PASS late-operation-rejection');
    console.log('OPERATION_PROTOCOL_PASS late-operation-resync');

    const protocol = window.KANGER_OPERATION_PROTOCOL.snapshot();
    assert.strictEqual(protocol.activeOperationId, 0);
    assert.strictEqual(protocol.settlingOperationId, 0);
    assert(protocol.generation >= 4);
    console.log('OPERATION_PROTOCOL_OK');
}

main().catch((error) => {
    console.error(error.stack || error);
    process.exitCode = 1;
});

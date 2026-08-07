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
        this.title = '';
    }
    get firstChild() { return this.childNodes[0] || null; }
    appendChild(node) {
        if (node.parentNode) node.parentNode.removeChild(node);
        this.childNodes.push(node);
        node.parentNode = this;
        return node;
    }
    insertBefore(node, reference) {
        if (node.parentNode) node.parentNode.removeChild(node);
        const index = this.childNodes.indexOf(reference);
        this.childNodes.splice(index < 0 ? this.childNodes.length : index, 0, node);
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
}
class FakeText extends FakeNode {
    constructor(data) { super(3, ''); this.data = data; }
    get textContent() { return this.data; }
    set textContent(value) { this.data = String(value); }
}

function workspace(sourceState, storageName) {
    return {
        schema: 1,
        source: {
            logical_name: sourceState === 'unbound' ? null : 'alpha.k',
            has_text: sourceState !== 'unbound',
            bytes_utf8: 17,
            repository_state: sourceState,
            persisted: sourceState === 'saved' || sourceState === 'modified',
            dirty: sourceState !== 'saved'
        },
        storage: storageName ? {
            active: true,
            logical_name: storageName,
            canonical_name: storageName.replace(/\./g, '/'),
            physical_generation: {
                present: true,
                artifacts: ['index', 'store'],
                wal_segments: 1
            }
        } : {
            active: false,
            logical_name: null,
            canonical_name: null,
            physical_generation: {
                present: false,
                artifacts: [],
                wal_segments: 0
            }
        },
        transaction: {level: 0, empty: true}
    };
}

function main() {
    const elements = {};
    const header = new FakeNode(1, 'DIV');
    const database = new FakeNode(1, 'SPAN');
    database.id = 'db-name';
    header.appendChild(database);
    elements['db-name'] = database;

    const document = {
        createElement(tagName) { return new FakeNode(1, String(tagName).toUpperCase()); },
        createTextNode(value) { return new FakeText(String(value)); },
        getElementById(id) { return elements[id] || findById(header, id); }
    };

    let generation = 0;
    let response = null;
    let packetSeen = null;
    let loggedPresentation = null;
    const window = {
        window: null,
        document,
        Object,
        Array,
        Number,
        String,
        Error,
        isFinite,
        console,
        post(packet, callback) {
            packetSeen = packet;
            callback(response);
        },
        logResponse(data, presentation, callback) {
            loggedPresentation = presentation;
            if (callback) callback(data);
        }
    };
    window.window = window;

    const context = {window, document, Object, Array, Number, String, Error, isFinite, console};
    vm.runInNewContext(fs.readFileSync('html/workspace.js', 'utf8'), context,
            {filename: 'workspace.js'});
    window.KANGER_OPERATION_PROTOCOL = Object.freeze({
        version: 1,
        snapshot() { return {generation}; }
    });
    assert(window.KANGER_WORKSPACE_STATE);
    elements['source-name'] = document.getElementById('source-name');
    assert(elements['source-name'], 'source state target was not installed');

    response = {result: 'OK', client_generation: 0,
        workspace: workspace('saved', 'alpha')};
    window.post({context: 'command', parameters: {put: 'alpha'}}, function () {
        database.textContent = 'unused';
    });
    assert.strictEqual(packetSeen.parameters.put, 'alpha.k');
    assert.strictEqual(database.textContent, 'DB: alpha');
    assert.strictEqual(elements['source-name'].textContent, 'Source: alpha.k');
    console.log('WORKSPACE_STATE_PASS source-lifecycle');

    response = {result: 'OK', client_generation: 1,
        workspace: workspace('saved', 'nested.one')};
    generation = 1;
    window.post({context: 'command', parameters: {use: 'nested/one'}}, function () {
        database.textContent = 'legacy-value';
    });
    assert.strictEqual(packetSeen.parameters.use, 'nested.one');
    assert.strictEqual(database.textContent, 'DB: nested.one');
    assert(database.title.includes('canonical=nested/one'));
    console.log('WORKSPACE_STATE_PASS canonical-storage');

    response = {result: 'OK', client_generation: 2,
        workspace: workspace('saved', 'nested.one')};
    generation = 2;
    window.post({context: 'command', parameters: {drop: 'other'}}, function () {
        database.textContent = 'unused';
    });
    assert.strictEqual(database.textContent, 'DB: nested.one');
    console.log('WORKSPACE_STATE_PASS nonactive-drop');

    response = {result: 'error', code: 'storage_switch_failed',
        description: 'corrupt generation', client_generation: 3,
        workspace: workspace('modified', 'nested.one')};
    generation = 3;
    window.post({context: 'command', parameters: {use: 'corrupt'}}, function () {
        database.textContent = 'unused';
    });
    assert.strictEqual(database.textContent, 'DB: nested.one');
    assert.strictEqual(elements['source-name'].textContent, 'Source: alpha.k *');
    window.logResponse(response);
    assert.strictEqual(loggedPresentation,
            '[storage_switch_failed] corrupt generation');
    console.log('WORKSPACE_STATE_PASS failed-switch-preservation');
    console.log('WORKSPACE_STATE_PASS typed-errors');

    response = {result: 'OK', client_generation: 4,
        workspace: workspace('missing', null)};
    generation = 4;
    window.post({context: 'command', parameters: {delete: 'alpha'}}, function () {});
    assert.strictEqual(elements['source-name'].textContent,
            'Source: alpha.k (missing)');
    assert.strictEqual(database.textContent, 'DB: unused');

    response = {result: 'OK', client_generation: 3,
        workspace: workspace('saved', 'stale')};
    window.post({context: 'command', parameters: {used: ''}}, function () {});
    assert.strictEqual(database.textContent, 'DB: unused',
            'stale projection replaced current workspace state');
    assert.strictEqual(window.KANGER_WORKSPACE_STATE.snapshot().generation, 4);
    console.log('WORKSPACE_STATE_PASS browser-projection-authority');
    console.log('WORKSPACE_STATE_OK');
}

function findById(node, id) {
    if (node.id === id) return node;
    for (const child of node.childNodes) {
        const found = findById(child, id);
        if (found) return found;
    }
    return null;
}

try {
    main();
} catch (error) {
    console.error(error.stack || error);
    process.exitCode = 1;
}

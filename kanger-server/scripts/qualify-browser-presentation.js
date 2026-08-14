#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const root = process.argv[2] || path.resolve(__dirname, '..', '..');
const source = fs.readFileSync(path.join(root, 'html', 'presentation.js'), 'utf8');

class FakeStyle {
    setProperty(name, value) { this[name] = String(value); }
}

class FakeClassList {
    constructor(node) { this.node = node; }
    values() {
        return String(this.node.className || '').split(/\s+/).filter(Boolean);
    }
    add(name) {
        const values = this.values();
        if (!values.includes(name)) values.push(name);
        this.node.className = values.join(' ');
    }
    remove(name) {
        this.node.className = this.values().filter(v => v !== name).join(' ');
    }
    contains(name) { return this.values().includes(name); }
}

class FakeNode {
    constructor(tagName, id) {
        this.tagName = String(tagName || 'div').toUpperCase();
        this.id = id || '';
        this.parentNode = null;
        this.childNodes = [];
        this.style = new FakeStyle();
        this.attributes = {};
        this.listeners = {};
        this.className = '';
        this.classList = new FakeClassList(this);
        this.textContent = '';
        this.value = '';
        this.selectionStart = 0;
        this.selectionEnd = 0;
        this.focused = false;
    }
    get children() { return this.childNodes; }
    get firstChild() { return this.childNodes[0] || null; }
    get firstElementChild() { return this.childNodes[0] || null; }
    appendChild(node) {
        if (node.parentNode) {
            const old = node.parentNode.childNodes.indexOf(node);
            if (old >= 0) node.parentNode.childNodes.splice(old, 1);
        }
        this.childNodes.push(node);
        node.parentNode = this;
        return node;
    }
    setAttribute(name, value) { this.attributes[name] = String(value); }
    getAttribute(name) {
        return Object.prototype.hasOwnProperty.call(this.attributes, name)
            ? this.attributes[name] : null;
    }
    removeAttribute(name) { delete this.attributes[name]; }
    addEventListener(type, listener) {
        (this.listeners[type] || (this.listeners[type] = [])).push(listener);
    }
    focus() { this.focused = true; }
    querySelectorAll(selector) {
        const match = /^\[id\^="([^"]+)"\]$/.exec(selector);
        if (!match) return [];
        const prefix = match[1];
        const found = [];
        function visit(node) {
            node.childNodes.forEach(child => {
                if (String(child.id || '').startsWith(prefix)) found.push(child);
                visit(child);
            });
        }
        visit(this);
        return found;
    }
}

const nodes = new Map();
function node(tag, id, parent) {
    const n = new FakeNode(tag, id);
    if (id) nodes.set(id, n);
    if (parent) parent.appendChild(n);
    return n;
}

const documentElement = node('html', 'html');
const head = node('head', '', documentElement);
const body = node('body', '', documentElement);
const superNode = node('div', 'super', body);
const header = node('div', '', superNode);
const dbName = node('span', 'db-name', header);
const transaction = node('span', 'transaction', header);
const container = node('div', 'container', superNode);
const left = node('div', 'container-left', container);
left.style.width = '240px';
node('div', '', left).textContent = 'Statements';
const statements = node('div', 'statements', left);
const functionsTitle = node('div', 'title-functions', left);
const functions = node('div', 'functions', left);
node('div', 'container-left-size', container);
const center = node('div', 'container-right', container);
const consoleContainer = node('div', 'container-console', center);
const consoleHeader = node('div', '', consoleContainer);
node('span', 'console-title', consoleHeader).textContent = 'Console';
node('span', 'console-button', consoleHeader).textContent = 'Editor';
node('span', 'console-close', consoleHeader);
const editorNode = node('div', 'editor', consoleContainer);
editorNode.style.display = 'none';
const consoleNode = node('div', 'console', consoleContainer);
node('div', 'query-history', consoleNode);
const consoleInput = node('div', 'console-input', consoleContainer);
const queryInput = node('textarea', 'query-input', consoleInput);
node('span', 'query-message', consoleInput);
node('div', 'container-div-resize', center);
const bottom = node('div', 'container-div', center);
bottom.style.height = '300px';
function resultPanel(id, contentId, title) {
    const p = node('div', id, bottom);
    node('div', '', p).textContent = title;
    return node('div', contentId, p);
}
const results = resultPanel('container-results', 'query-results', 'Results');
const solutions = resultPanel('container-solutions', 'query-solutions', 'Solutions');
const hypothesis = resultPanel('container-hypothesis', 'query-hypothesis', 'Hypothesis');
resultPanel('container-logging', 'query-log', 'Logging');
const footer = node('div', '', superNode);
node('span', 'copyright', footer).textContent = 'Copyright';

function strong(parent, value) {
    const s = node('strong', '', parent);
    s.textContent = value;
    return s;
}
const predicate = node('div', 'PR7', statements);
node('span', 'SN7', predicate).textContent = '+';
strong(predicate, 'owns');
const statement = node('div', 'PS8', statements);
strong(statement, '!owns(John,Account17)');
const fn = node('div', 'FN9', functions);
strong(fn, 'score');
const solution = node('div', 'SOL10', solutions);
strong(solution, '!approved(Account17)');
const hypo = node('div', 'HYL2', hypothesis);
strong(hypo, '?missing(Account17)');
const table = node('table', '', results);
const headerRow = node('tr', '', table);
node('td', '', headerRow).textContent = '#';
node('td', '', headerRow).textContent = 'account';
const valueRow = node('tr', '', table);
node('td', '', valueRow).textContent = '1';
const valueCell = node('td', '', valueRow);
valueCell.textContent = 'Account17';

const documentListeners = {};
const document = {
    head,
    body,
    documentElement,
    readyState: 'complete',
    createElement(tag) { return new FakeNode(tag); },
    getElementById(id) { return nodes.get(id) || findById(documentElement, id); },
    addEventListener(type, listener) {
        (documentListeners[type] || (documentListeners[type] = [])).push(listener);
    }
};
function findById(rootNode, id) {
    if (rootNode.id === id) return rootNode;
    for (const child of rootNode.childNodes) {
        const found = findById(child, id);
        if (found) return found;
    }
    return null;
}

documentElement.parentNode = document;

let layoutCalls = 0;
const windowListeners = {};
const window = {
    window: null,
    document,
    Object,
    String,
    Number,
    Error,
    isFinite,
    setTimeout,
    clearTimeout,
    MutationObserver: null,
    addEventListener(type, listener) {
        (windowListeners[type] || (windowListeners[type] = [])).push(listener);
    },
    KANGER_TRUSTED_RENDERING: Object.freeze({version: 1, installed: true}),
    KANGER_OPERATION_PROTOCOL: Object.freeze({
        version: 1,
        snapshot() {
            return {
                generation: 4,
                activeOperationId: 0,
                activeOperationName: '',
                currentSnapshotId: 0,
                lastCommittedSnapshotId: 12
            };
        }
    }),
    KANGER_WORKSPACE_STATE: Object.freeze({
        version: 2,
        snapshot() {
            return {
                generation: 4,
                workspace: {
                    schema: 2,
                    storage: {
                        active: true,
                        logical_name: 'demo.db',
                        physical_generation: {
                            present: true,
                            wal_segments: 2
                        }
                    },
                    transaction: {
                        level: 0,
                        empty: true
                    }
                }
            };
        }
    }),
    KANGER_ERROR_BOUNDARY: Object.freeze({version: 1, installed: true}),
    KANGER_DIALOGUE_TRANSPORT: Object.freeze({version: 1, installed: true}),
    editor: {
        setSize(width, height) { this.size = [width, height]; }
    },
    placeElements() { layoutCalls += 1; },
    openConsole() {},
    openEditor() {}
};
window.window = window;

const context = vm.createContext({
    window,
    document,
    Object,
    String,
    Number,
    Error,
    isFinite,
    setTimeout,
    clearTimeout,
    console
});
vm.runInContext(source, context, {filename: 'presentation.js'});

function settle(ms = 20) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function click(target) {
    const listeners = documentListeners.click || [];
    assert(listeners.length > 0, 'presentation click boundary was not installed');
    let stopped = false;
    const event = {
        target,
        preventDefault() {},
        stopPropagation() { stopped = true; },
        stopImmediatePropagation() { stopped = true; }
    };
    listeners.forEach(listener => listener(event));
    return stopped;
}

async function main() {
    await settle();
    const presentation = window.KANGER_PRESENTATION;
    assert(presentation && presentation.installed);
    assert.strictEqual(presentation.version, 1);
    assert(document.getElementById('kanger-presentation-css'));
    assert(document.getElementById('technical-panel'));
    assert.strictEqual(document.getElementById('tech-source'), null);
    assert.strictEqual(
        document.getElementById('tech-storage').textContent,
        'storage: demo.db'
    );
    assert(body.classList.contains('kanger-presentation'));
    assert(container.classList.contains('kanger-grid'));
    assert(left.classList.contains('kanger-semantic'));
    assert(center.classList.contains('kanger-center'));
    assert(bottom.classList.contains('kanger-bottom'));
    console.log('BROWSER_PRESENTATION_PASS geometry-workspace-v2');

    queryInput.value = 'alpha omega';
    queryInput.selectionStart = 6;
    queryInput.selectionEnd = 11;
    assert.strictEqual(presentation.compose('beta'), true);
    assert.strictEqual(queryInput.value, 'alpha beta');
    assert.strictEqual(queryInput.selectionStart, 10);
    assert.strictEqual(queryInput.selectionEnd, 10);
    assert.strictEqual(queryInput.focused, true);
    console.log('BROWSER_PRESENTATION_PASS caret-selection-compose');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    assert.strictEqual(click(statement), true);
    assert.strictEqual(queryInput.value, '!owns(John,Account17)');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    assert.strictEqual(click(predicate), true);
    assert.strictEqual(queryInput.value, 'owns(');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    assert.strictEqual(click(fn), true);
    assert.strictEqual(queryInput.value, 'score(');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    assert.strictEqual(click(solution), true);
    assert.strictEqual(queryInput.value, '!approved(Account17)');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    assert.strictEqual(click(hypo), true);
    assert.strictEqual(queryInput.value, '!~missing(Account17)');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    assert.strictEqual(click(valueCell), true);
    assert.strictEqual(queryInput.value, 'Account17');
    console.log('BROWSER_PRESENTATION_PASS semantic-click-compose');

    const statementAction = statement.childNodes.find(n =>
        n.getAttribute && n.getAttribute('data-kanger-compose'));
    assert(statementAction);
    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    click(statementAction);
    assert.strictEqual(queryInput.value, 'base tree 8');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    click(dbName);
    assert.strictEqual(queryInput.value, 'storage use demo.db');

    queryInput.value = '';
    queryInput.selectionStart = queryInput.selectionEnd = 0;
    click(transaction);
    assert.strictEqual(queryInput.value, 'transaction');
    console.log('BROWSER_PRESENTATION_PASS command-projection-compose');

    assert.strictEqual(presentation.snapshot().technicalOpen, false);
    assert.strictEqual(presentation.toggleTechnical(), true);
    assert.strictEqual(presentation.snapshot().technicalOpen, true);
    assert(container.classList.contains('kanger-tech-open'));
    assert.strictEqual(presentation.toggleTechnical(), false);
    console.log('BROWSER_PRESENTATION_PASS technical-collapse');

    window.placeElements();
    assert.strictEqual(layoutCalls, 1);
    assert.deepStrictEqual(window.editor.size, ['100%', '100%']);
    console.log('BROWSER_PRESENTATION_PASS legacy-layout-convergence');

    assert(!source.includes('workspace.source'));
    assert(!source.includes('tech-source'));
    assert(!source.includes('source-name'));
    assert(!source.includes('window.command'));
    assert(!source.includes('window.query'));
    assert(!source.includes('window.post'));
    assert(!source.includes('window.token'));
    assert(!source.includes('innerHTML'));
    assert(!source.includes('document.cookie'));
    assert(!source.includes('eval('));
    assert(!source.includes('new Function'));
    console.log('BROWSER_PRESENTATION_PASS workspace-v2-no-source-authority');
    console.log('BROWSER_PRESENTATION_PASS no-execution-authority');
    console.log('BROWSER_PRESENTATION_OK');
}

main().catch(error => {
    console.error(error.stack || error);
    process.exitCode = 1;
});
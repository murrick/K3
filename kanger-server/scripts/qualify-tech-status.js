/*
 * Executable qualification for canonical STATUS telemetry in Browser TECH.
 */
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('html/tech-status.js', 'utf8');
const presentationSource = fs.readFileSync('html/presentation.js', 'utf8');

assert(!source.includes('setInterval('), 'TECH status must not poll');
assert(!source.includes('window.command('), 'TECH status must not execute Console commands');
assert(!source.includes('window.query('), 'TECH status must not execute Console queries');
assert(!source.includes('logRequest('), 'TECH status must not write Console request history');
assert(!source.includes('logResponse('), 'TECH status must not write Console response history');
assert(!source.includes('storeHistory('), 'TECH status must not persist Console history');
assert(!source.includes('window.token'),
    'TECH status child must not read bearer or containment sentinel');
assert(!source.includes('window.jQuery.post'),
    'TECH status must not bypass parent containment with direct XHR');
assert(!source.includes('window.apihost'),
    'TECH status must not address the API host directly');
assert(source.includes('window.post('),
    'TECH status must use the Browser transport boundary');
assert(!source.includes("context: 'dialogue'"),
    'TECH telemetry must not enter the serialized raw dialogue operation path');
assert(source.includes("context: 'command'"),
    'TECH telemetry must use the structured read transport shape');
assert(source.includes("status: ''"),
    'TECH telemetry must request the structured STATUS marker');
assert(source.includes('kanger-tech-metric-row'),
    'TECH telemetry must render structured label/value rows');
assert(source.includes('kanger-tech-canonical-boundary'),
    'canonical STATUS must remain visually separated from Browser-local TECH');
assert(!presentationSource.includes('window.token'),
    'presentation authority must remain bearer-free');
assert(presentationSource.includes("script.src = 'tech-status.js'"),
    'presentation must load the isolated TECH status companion');

class Element {
    constructor(tagName) {
        this.tagName = String(tagName || '').toUpperCase();
        this.id = '';
        this.className = '';
        this.childNodes = [];
        this.parentNode = null;
        this.listeners = Object.create(null);
        this.attributes = Object.create(null);
        this.hidden = false;
        this._textContent = '';
    }

    appendChild(child) {
        child.parentNode = this;
        this.childNodes.push(child);
        return child;
    }

    addEventListener(type, callback) {
        if (!this.listeners[type]) {
            this.listeners[type] = [];
        }
        this.listeners[type].push(callback);
    }

    setAttribute(name, value) {
        this.attributes[String(name)] = String(value);
    }

    getAttribute(name) {
        return Object.prototype.hasOwnProperty.call(this.attributes, name)
            ? this.attributes[name] : null;
    }

    dispatch(type, extra) {
        const event = Object.assign({
            currentTarget: this,
            target: this,
            key: '',
            preventDefault() {},
            stopPropagation() {}
        }, extra || {});
        (this.listeners[type] || []).forEach((callback) => callback(event));
    }

    get firstElementChild() {
        return this.childNodes.length ? this.childNodes[0] : null;
    }

    get textContent() {
        if (this.childNodes.length) {
            return this.childNodes.map((child) => child.textContent).join('');
        }
        return this._textContent;
    }

    set textContent(value) {
        this.childNodes = [];
        this._textContent = value === null || value === undefined
            ? '' : String(value);
    }
}

function findById(root, id) {
    if (!root) {
        return null;
    }
    if (root.id === id) {
        return root;
    }
    for (const child of root.childNodes || []) {
        const found = findById(child, id);
        if (found) {
            return found;
        }
    }
    return null;
}

const document = {
    head: new Element('head'),
    body: new Element('body'),
    createElement(tagName) {
        return new Element(tagName);
    },
    getElementById(id) {
        return findById(this.head, id) || findById(this.body, id);
    }
};

const panel = new Element('aside');
panel.id = 'technical-panel';
const toggle = new Element('div');
toggle.className = 'kanger-tech-toggle';
const body = new Element('div');
body.className = 'kanger-tech-body';
panel.appendChild(toggle);
panel.appendChild(body);
document.body.appendChild(panel);

let technicalOpen = false;
let generation = 7;
const requests = [];

function canonicalResponse() {
    return {
        result: 'OK',
        canonical_intent: 'STATUS',
        status: {
            schema: 1,
            core: {
                transaction: {
                    level: 1,
                    compatibility: 'VALID',
                    quiescent: false,
                    current_pending_children: 0,
                    root_pending_children: 1
                },
                levels: {current: 1, mind: 4, root_mind: 0},
                objects: {count: null}
            },
            storage: {
                current: 'natives',
                state: 'open',
                backend: 'DUMB data model',
                bases: 12,
                records: 345,
                physical_bytes: 4096,
                wal_pending_bases: 0,
                cache_used_bytes: 2048,
                cache_max_bytes: 8192,
                cache_entries: 8,
                cache_hits: 21,
                cache_misses: 3,
                cache_evictions: 1
            },
            session: {
                user: 3,
                mind: 4,
                user_dir: '/user/',
                database_dir: '/db/',
                sources_dir: '/src/'
            },
            runtime: {
                version: '3.7.0',
                source_branch: 'fix/3.7.0-semantic-use-recovery',
                build_date: '2026-09-04_17:00:00',
                java: '1.8.0_482',
                jvm: 'Eclipse OpenJ9 VM',
                uptime_ms: 65000,
                heap: {
                    used_bytes: 1024,
                    committed_bytes: 2048,
                    max_bytes: 4096
                },
                os: 'Mac OS X',
                arch: 'x86_64'
            }
        }
    };
}

const window = {
    document,
    token: '__KANGER_PARENT_SESSION__',
    apihost: '',
    KANGER_PRESENTATION: {
        installed: true,
        snapshot() {
            return {technicalOpen};
        }
    },
    KANGER_OPERATION_PROTOCOL: {
        snapshot() {
            return {generation};
        }
    },
    post(packet, callback) {
        requests.push({packet: JSON.parse(JSON.stringify(packet))});
        callback(canonicalResponse());
    },
    jQuery: {
        post() {
            throw new Error('Direct child network access is disabled');
        }
    },
    setTimeout(callback) {
        callback();
        return 1;
    }
};
window.window = window;

const context = {
    window,
    document,
    Object,
    String,
    Number,
    Boolean,
    Array,
    RegExp,
    JSON,
    Math,
    Error,
    isFinite,
    setTimeout: window.setTimeout
};

vm.runInNewContext(source, context, {filename: 'html/tech-status.js'});

assert(window.KANGER_TECH_STATUS);
assert.strictEqual(window.KANGER_TECH_STATUS.version, 1);
assert.strictEqual(window.KANGER_TECH_STATUS.installed, true);
assert(document.getElementById('kanger-tech-status-css'));
assert(document.getElementById('kanger-tech-status-css').textContent
    .includes('grid-template-columns: 86px minmax(0, 1fr)'));
assert.strictEqual(requests.length, 0,
    'closed TECH must not request canonical STATUS');
console.log('TECH_STATUS_PASS closed-no-read');

technicalOpen = true;
toggle.dispatch('click');
assert.strictEqual(requests.length, 1,
    'opening TECH must perform exactly one canonical STATUS read');
assert.deepStrictEqual(requests[0].packet, {
    context: 'command',
    parameters: {
        status: ''
    }
});
assert.strictEqual(
    Object.prototype.hasOwnProperty.call(requests[0].packet.parameters, 'token'),
    false,
    'TECH status child request must not carry bearer data');

assert.strictEqual(document.getElementById('tech-status-badge').textContent,
    'CURRENT');
assert(document.getElementById('tech-status-badge').className
    .includes('kanger-tech-badge-ok'));
assert.strictEqual(document.getElementById('tech-status-schema-value').textContent,
    '1');
assert.strictEqual(document.getElementById('tech-status-generation-value').textContent,
    '7');
assert.strictEqual(document.getElementById('tech-status-source').hidden, true);

assert.strictEqual(document.getElementById('tech-core-transaction-value').textContent,
    'U1');
assert.strictEqual(document.getElementById('tech-core-compatibility-value').textContent,
    'VALID');
assert.strictEqual(document.getElementById('tech-core-state-value').textContent,
    'ACTIVE');
assert.strictEqual(document.getElementById('tech-core-levels-value').textContent,
    'current 4 · root 0');
assert.strictEqual(document.getElementById('tech-core-pending-value').textContent,
    'current 0 · root 1');
assert.strictEqual(document.getElementById('tech-core-objects-value').textContent,
    'unavailable');

assert.strictEqual(document.getElementById('tech-storage-badge').textContent,
    'OPEN');
assert.strictEqual(document.getElementById('tech-canonical-storage-value').textContent,
    'natives');
assert.strictEqual(document.getElementById('tech-storage-volume-value').textContent,
    '12 bases · 345 records · 4.00 KiB');
assert.strictEqual(document.getElementById('tech-storage-cache-value').textContent,
    '2.00 KiB / 8.00 KiB · 8 entries');

assert.strictEqual(document.getElementById('tech-session-user-value').textContent,
    '3');
assert.strictEqual(document.getElementById('tech-session-mind-value').textContent,
    '4');
assert.strictEqual(document.getElementById('tech-session-user-dir-value').textContent,
    '/user/');

assert.strictEqual(document.getElementById('tech-runtime-badge').textContent,
    '3.7.0');
assert.strictEqual(document.getElementById('tech-runtime-build-value').textContent,
    'fix/3.7.0-semantic-use-recovery');
assert.strictEqual(document.getElementById('tech-runtime-built-value').textContent,
    '2026-09-04 17:00:00');
assert.strictEqual(document.getElementById('tech-runtime-java-value').textContent,
    '1.8.0_482 · Eclipse OpenJ9 VM');
assert.strictEqual(document.getElementById('tech-runtime-system-value').textContent,
    'Mac OS X · x86_64');
assert.strictEqual(document.getElementById('tech-runtime-uptime-value').textContent,
    '1m 5s');
assert.strictEqual(window.KANGER_TECH_STATUS.snapshot().schema, 1);
console.log('TECH_STATUS_PASS open-single-structured-read-render');
console.log('TECH_STATUS_PASS structured-visual-hierarchy');

technicalOpen = false;
toggle.dispatch('click');
assert.strictEqual(requests.length, 1,
    'closing TECH must not request canonical STATUS');
console.log('TECH_STATUS_PASS close-no-read');

generation = 8;
technicalOpen = true;
toggle.dispatch('click');
assert.strictEqual(requests.length, 2,
    'reopening TECH must refresh exactly once');
assert.strictEqual(document.getElementById('tech-status-generation-value').textContent,
    '8');
console.log('TECH_STATUS_PASS reopen-single-refresh');

assert(!source.includes('setInterval('));
console.log('TECH_STATUS_PASS no-polling-history-side-effects');
console.log('TECH_STATUS_PASS containment-transport-boundary');
console.log('TECH_STATUS_PASS structured-read-boundary');
console.log('TECH_STATUS_OK');

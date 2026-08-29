#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const consoleSource = fs.readFileSync('html/console.html', 'utf8');
const editorStateSource = fs.readFileSync('html/editor-state.js', 'utf8');
const startMarker = '        function compileSource() {';
const endMarker = '        function passwordForm() {';
const start = consoleSource.indexOf(startMarker);
const end = consoleSource.indexOf(endMarker, start);
assert(start >= 0 && end > start, 'compileSource() was not found in console.html');
const compileSourceText = consoleSource.slice(start, end);

function harness(response) {
    let posted = null;
    let refreshCount = 0;
    let consoleOpenCount = 0;
    let logCount = 0;

    const nodes = {
        editor: {style: {display: ''}},
        'console-title': {textContent: 'Editor', title: ''},
        'editor-local-status': {textContent: '', title: '', style: {display: 'none'}}
    };
    const document = {
        getElementById(id) { return nodes[id] || null; },
        addEventListener() {}
    };
    const editor = {
        text: '!edited(source);',
        getValue() { return this.text; },
        on() {},
        refresh() {},
        focus() {}
    };

    const context = {
        document,
        editor,
        token: 'test-token',
        encodeURIComponent,
        KANGER_WORKSPACE_STATE: {version: 2},
        KANGER_EDITOR_FILE_ADAPTER: {installed: true},
        KANGER_BROWSER_SOAK_CONVERGENCE: {installed: true},
        KANGER_ERROR_BOUNDARY: {
            describe(data) {
                if (!data || String(data.result).toUpperCase() === 'OK') {
                    return '';
                }
                const domain = data.error && data.error.domain
                        ? data.error.domain : 'application';
                return '[' + domain + ':' + data.code + '] ' + data.description;
            }
        },
        parent: {postMessage() {}},
        addEventListener() {},
        setTimeout(callback) {
            callback();
            return 1;
        },
        post(packet, callback) {
            posted = packet;
            callback(response);
        },
        logRequest(text, callback) {
            logCount += 1;
            assert.strictEqual(text, '// Compile');
            callback();
        },
        refreshScreen(data) {
            refreshCount += 1;
            assert.strictEqual(data, response);
        },
        openConsole() {
            consoleOpenCount += 1;
        },
        openEditor() {},
        showSourceEditor() {},
        showFunctionEditor() {}
    };
    context.window = context;

    vm.runInNewContext(compileSourceText, context,
            {filename: 'console-compileSource.js'});
    vm.runInNewContext(editorStateSource, context,
            {filename: 'editor-state.js'});

    context.compileSource();
    return {
        posted,
        refreshCount,
        consoleOpenCount,
        logCount,
        status: nodes['editor-local-status'],
        state: context.KANGER_EDITOR_STATE.snapshot()
    };
}

const rejected = harness({
    result: 'error',
    code: 'parse_error',
    description: 'Unclosed quotes',
    error: {schema: 1, domain: 'application'}
});
assert(rejected.posted, 'failed Compile was not posted');
assert(Object.prototype.hasOwnProperty.call(rejected.posted.parameters, 'compile'));
assert.strictEqual(rejected.refreshCount, 1,
        'failed Compile did not refresh canonical diagnostics');
assert.strictEqual(rejected.logCount, 1,
        'failed Compile did not log the Compile operation');
assert.strictEqual(rejected.consoleOpenCount, 0,
        'failed Compile closed the Source Editor instead of preserving repair state');
assert.strictEqual(rejected.state.dirty, true,
        'failed Compile did not preserve dirty Editor state');
assert.strictEqual(rejected.status.textContent,
        '[application:parse_error] Unclosed quotes',
        'failed Compile did not expose the canonical diagnostic in Editor status');
assert.notStrictEqual(rejected.status.style.display, 'none',
        'failed Compile diagnostic is hidden in Editor status');
console.log('COMPILE_EDITOR_PASS failure-keeps-editor-open');
console.log('COMPILE_EDITOR_PASS failure-shows-canonical-diagnostic');

const accepted = harness({result: 'OK'});
assert.strictEqual(accepted.refreshCount, 1);
assert.strictEqual(accepted.consoleOpenCount, 1,
        'successful Compile did not return to Dialogue');
assert.strictEqual(accepted.state.dirty, false,
        'successful Compile did not settle clean Editor state');
assert.strictEqual(accepted.status.textContent, '',
        'successful Compile retained a stale Editor diagnostic');
console.log('COMPILE_EDITOR_PASS success-returns-to-dialogue');
console.log('COMPILE_EDITOR_OK');

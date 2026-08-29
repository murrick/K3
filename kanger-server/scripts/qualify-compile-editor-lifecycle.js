#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('html/console.html', 'utf8');
const startMarker = '        function compileSource() {';
const endMarker = '        function passwordForm() {';
const start = source.indexOf(startMarker);
const end = source.indexOf(endMarker, start);
assert(start >= 0 && end > start, 'compileSource() was not found in console.html');
const compileSourceText = source.slice(start, end);

function harness(response) {
    let posted = null;
    let refreshCount = 0;
    let consoleOpenCount = 0;
    let logCount = 0;
    const window = {
        editor: {
            getValue() { return '!edited(source);'; }
        }
    };
    const context = {
        window,
        encodeURIComponent,
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
        }
    };
    vm.runInNewContext(compileSourceText + '\nthis.runCompile = compileSource;', context,
            {filename: 'console-compileSource.js'});
    context.runCompile();
    return {posted, refreshCount, consoleOpenCount, logCount};
}

const rejected = harness({
    result: 'error',
    code: 'source_compile_rejected',
    description: 'Collisions in Program'
});
assert(rejected.posted, 'failed Compile was not posted');
assert(Object.prototype.hasOwnProperty.call(rejected.posted.parameters, 'compile'));
assert.strictEqual(rejected.refreshCount, 1,
        'failed Compile did not refresh canonical diagnostics');
assert.strictEqual(rejected.logCount, 1,
        'failed Compile did not log the Compile operation');
assert.strictEqual(rejected.consoleOpenCount, 0,
        'failed Compile closed the Source Editor instead of preserving repair state');
console.log('COMPILE_EDITOR_PASS failure-keeps-editor-open');

const accepted = harness({result: 'OK'});
assert.strictEqual(accepted.refreshCount, 1);
assert.strictEqual(accepted.consoleOpenCount, 1,
        'successful Compile did not return to Dialogue');
console.log('COMPILE_EDITOR_PASS success-returns-to-dialogue');
console.log('COMPILE_EDITOR_OK');

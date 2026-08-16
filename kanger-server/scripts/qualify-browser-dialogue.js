#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const root = process.argv[2] || path.resolve(__dirname, '..', '..');
const source = fs.readFileSync(path.join(root, 'html', 'dialogue.js'), 'utf8');

const calls = [];
const refreshes = [];
const callbacks = [];
let readyCallback = null;
const jQuery = {
    fn: {
        ready(callback) {
            readyCallback = callback;
            return this;
        }
    }
};
const window = {
    token: '__KANGER_PARENT_SESSION__',
    editor: {},
    jQuery,
    KANGER_ERROR_BOUNDARY: Object.freeze({version: 1, installed: true}),
    post(packet, callback) {
        calls.push(JSON.parse(JSON.stringify(packet)));
        const result = {result: 'OK', description: 'accepted'};
        if (typeof callback === 'function') callback(result);
        return calls.length;
    },
    refreshScreen(data) {
        refreshes.push(data);
    },
    command() {
        throw new Error('historical Browser command parser remained active');
    },
    query() {
        throw new Error('historical Browser Core branch remained active');
    }
};

const context = vm.createContext({
    window,
    Object,
    String,
    Error
});
vm.runInContext(source, context, {filename: 'dialogue.js'});

assert(window.KANGER_DIALOGUE_TRANSPORT);
assert.strictEqual(window.KANGER_DIALOGUE_TRANSPORT.version, 2);
assert.strictEqual(typeof window.KANGER_DIALOGUE_TRANSPORT.reassert, 'function');
assert.strictEqual(window.command, window.query,
    'command and Core entry points must converge on one raw transport');
assert.strictEqual(window.command, window.KANGER_DIALOGUE_TRANSPORT.dispatch);

/*
 * Reproduce the real parser-time hazard: dialogue.js may assign the canonical
 * entry points before console.html later declares its historical globals. The
 * ready ownership wrapper must restore the canonical transport before the
 * startup adapter captures window.command.
 */
const legacyCommand = function () {
    throw new Error('legacy command parser captured by startup adapter');
};
const legacyQuery = function () {
    throw new Error('legacy Core branch captured by startup adapter');
};
window.command = legacyCommand;
window.query = legacyQuery;
assert.notStrictEqual(window.command, window.KANGER_DIALOGUE_TRANSPORT.dispatch);

let sawLegacyInsideReadyCallback = false;
window.jQuery.fn.ready(function () {
    sawLegacyInsideReadyCallback = window.command === legacyCommand;
});
assert.strictEqual(typeof readyCallback, 'function',
    'dialogue ownership wrapper did not register through jQuery.ready');
readyCallback();
assert.strictEqual(sawLegacyInsideReadyCallback, true,
    'test did not reproduce the historical global overwrite');
assert.strictEqual(window.command, window.KANGER_DIALOGUE_TRANSPORT.dispatch,
    'canonical command transport was not restored after ready callback');
assert.strictEqual(window.query, window.KANGER_DIALOGUE_TRANSPORT.dispatch,
    'canonical Core transport was not restored after ready callback');

window.command = legacyCommand;
window.KANGER_DIALOGUE_TRANSPORT.reassert();
assert.strictEqual(window.command, window.KANGER_DIALOGUE_TRANSPORT.dispatch,
    'explicit ownership reassertion did not restore canonical command transport');

function invoke(line, throughQuery) {
    const before = calls.length;
    const entry = throughQuery ? window.query : window.command;
    entry(line, data => callbacks.push(data));
    assert.strictEqual(calls.length, before + 1);
    const packet = calls[calls.length - 1];
    assert.deepStrictEqual(Object.keys(packet).sort(), ['context', 'parameters']);
    assert.strictEqual(packet.context, 'dialogue');
    assert.deepStrictEqual(Object.keys(packet.parameters).sort(), ['line', 'token']);
    assert.strictEqual(packet.parameters.token, '__KANGER_PARENT_SESSION__');
    assert.strictEqual(packet.parameters.line, line,
        'Browser rewrote raw operator dialogue');
}

invoke('r a', false);
invoke('?father(John,Tom);', true);
invoke('s', false);
invoke('  MiXeD  "a b"  ', false);
invoke('storage use close', false);

assert.strictEqual(refreshes.length, calls.length);
assert.strictEqual(callbacks.length, calls.length);
assert(!source.includes('split('), 'dialogue adapter contains local tokenization');
assert(!/switch\s*\(/.test(source), 'dialogue adapter contains local dispatch switch');
assert(!source.includes('toLowerCase('), 'dialogue adapter normalizes operator language');

console.log('BROWSER_DIALOGUE_PASS one-raw-envelope');
console.log('BROWSER_DIALOGUE_PASS core-command-convergence');
console.log('BROWSER_DIALOGUE_PASS ready-ownership-reassertion');
console.log('BROWSER_DIALOGUE_PASS ambiguity-left-to-server');
console.log('BROWSER_DIALOGUE_PASS lexical-preservation');
console.log('BROWSER_DIALOGUE_OK');

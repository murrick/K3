#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const root = process.argv[2] || path.resolve(__dirname, '..', '..');
const source = fs.readFileSync(path.join(root, 'html', 'dialogue.js'), 'utf8');
const corpus = fs.readFileSync(path.join(root,
    'kanger-command', 'test-data', 'client-vocabulary.tsv'), 'utf8')
    .split(/\r?\n/)
    .filter(line => line && !line.startsWith('#'))
    .map(line => {
        const fields = line.split('\t');
        assert.strictEqual(fields.length, 8,
            'invalid shared client vocabulary row: ' + line);
        return {
            accepted: fields[0] === 'ACCEPT',
            line: fields[1],
            result: fields[2]
        };
    });

const calls = [];
const refreshes = [];
const callbacks = [];
let readyCallback = null;
function domNode(type, value) {
    return {
        type,
        children: [],
        style: {},
        attributes: {},
        _text: value || '',
        appendChild(child) {
            this.children.push(child);
            return child;
        },
        setAttribute(name, attributeValue) {
            this.attributes[name] = String(attributeValue);
        },
        getAttribute(name) {
            return Object.prototype.hasOwnProperty.call(this.attributes, name)
                ? this.attributes[name] : null;
        },
        get textContent() {
            return this._text + this.children.map(child => child.textContent).join('');
        },
        set textContent(text) {
            this._text = String(text);
            this.children = [];
        }
    };
}
const document = {
    createDocumentFragment() {
        return domNode('fragment');
    },
    createElement(type) {
        return domNode(type);
    },
    createTextNode(value) {
        return domNode('text', String(value));
    }
};
const jQuery = {
    fn: {
        ready(callback) {
            readyCallback = callback;
            return this;
        }
    }
};
function responseFor(line) {
    if (line === 'help') {
        return {
            result: 'OK',
            canonical_intent: 'HELP',
            dialogue_help: {
                schema: 1,
                sections: [{
                    name: 'COMMANDS',
                    commands: [{
                        syntax: 'base predicates',
                        family_spellings: ['predicate', 'predicates'],
                        aliases: [],
                        summary: 'Show predicates.'
                    }, {
                        syntax: 'transaction squash',
                        family_spellings: [],
                        aliases: ['squash'],
                        summary: 'Collapse transaction history.'
                    }]
                }]
            }
        };
    }
    if (line === 'squash') {
        return {
            result: 'OK',
            canonical_intent: 'TX_SQUASH',
            transaction: 1,
            empty: false,
            description: 'Transaction history squashed'
        };
    }
    if (line === 'transaction rollback') {
        return {
            result: 'error',
            code: 'ROLLBACK_REBASE_CONFLICT',
            reason: 'STORAGE_BASELINE_COLLISION',
            canonical_intent: 'TX_ROLLBACK',
            transaction: 2,
            empty: false,
            description: 'Rollback U2 -> U1 rejected.',
            rejection: {
                schema: 1,
                kind: 'ROLLBACK_REBASE_CONFLICT',
                target_level: 1,
                storage: 'storage-b',
                collisions: [{left: '!ghost;', right: '!~ghost;'}],
                actions: [{
                    id: 'USE_COMPATIBLE_STORAGE',
                    description: 'Return to a compatible storage baseline.'
                }, {
                    id: 'TRANSACTION_SQUASH',
                    command: 'transaction squash',
                    description: 'Keep current state and discard rollback history.'
                }, {
                    id: 'TRANSACTION_COMMIT',
                    command: 'transaction commit',
                    description: 'Merge current changes into the lower level.'
                }]
            }
        };
    }
    const expected = corpus.find(one => one.line === line);
    if (expected && !expected.accepted) {
        return {
            result: 'error',
            code: 'command_parse_error',
            reason: expected.result,
            description: 'rejected'
        };
    }
    return expected
        ? {result: 'OK', canonical_intent: expected.result,
            description: 'accepted'}
        : {result: 'OK', description: 'accepted'};
}
const window = {
    token: '__KANGER_PARENT_SESSION__',
    editor: {},
    document,
    jQuery,
    KANGER_ERROR_BOUNDARY: Object.freeze({version: 1, installed: true}),
    post(packet, callback) {
        calls.push(JSON.parse(JSON.stringify(packet)));
        const result = responseFor(packet.parameters.line);
        if (typeof callback === 'function') callback(result);
        return calls.length;
    },
    refreshScreen(data, presentation) {
        refreshes.push({data, presentation});
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
    return refreshes[refreshes.length - 1];
}

invoke('ru a', false);
invoke('?father(John,Tom);', true);
invoke('s', false);
invoke('  MiXeD  "a b"  ', false);
invoke('storage use close', false);
invoke('help', false);
corpus.forEach(one => invoke(one.line, false));
const squash = invoke('squash', false).presentation;
const rollback = invoke('transaction rollback', false).presentation;

const help = refreshes.find(one => one.data.canonical_intent === 'HELP').presentation;
assert(help, 'Browser did not render structured canonical help');
assert(help.textContent.includes(
    'base predicates  (family spellings: predicate, predicates) — Show predicates.'),
    'Browser help hid predicate/predicates family spellings');
assert(help.textContent.includes(
    'transaction squash  (alias: squash) — Collapse transaction history.'),
    'Browser help hid the squash alias');

assert(squash, 'Browser did not select transaction presentation for TX_SQUASH');
assert(squash.textContent.includes('Transaction history squashed'),
    'Browser hid the successful squash result');
assert(squash.textContent.includes('Transaction level 1'),
    'Browser hid the post-squash transaction level');

assert(rollback, 'Browser did not render rejected rollback diagnostics');
assert(rollback.textContent.includes('ROLLBACK_REBASE_CONFLICT'),
    'Browser hid rollback rejection kind');
assert(rollback.textContent.includes('STORAGE_BASELINE_COLLISION'),
    'Browser hid rollback rejection reason');
assert(rollback.textContent.includes('!ghost; <> !~ghost;'),
    'Browser hid exact rollback collision witness');
assert(rollback.textContent.includes('USE_COMPATIBLE_STORAGE'),
    'Browser hid compatible-storage resolution');
assert(rollback.textContent.includes('TRANSACTION_SQUASH'),
    'Browser hid squash resolution');
assert(rollback.textContent.includes('TRANSACTION_COMMIT'),
    'Browser hid commit resolution');
function composeCommands(node, result) {
    const command = node.getAttribute && node.getAttribute('data-kanger-compose');
    if (command) result.push(command);
    (node.children || []).forEach(child => composeCommands(child, result));
    return result;
}
assert.deepStrictEqual(composeCommands(rollback, []),
    ['transaction squash', 'transaction commit'],
    'Browser rejection actions are not composable canonical commands');

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
console.log('BROWSER_DIALOGUE_PASS vocabulary-help-metadata');
console.log('BROWSER_DIALOGUE_PASS shared-client-vocabulary');
console.log('BROWSER_DIALOGUE_PASS squash-result-presentation');
console.log('BROWSER_DIALOGUE_PASS rollback-resolution-presentation');
console.log('BROWSER_DIALOGUE_OK');

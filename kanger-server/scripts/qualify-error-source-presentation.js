#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const root = process.argv[2] || path.resolve(__dirname, '..', '..');
const errorSource = fs.readFileSync(
    path.join(root, 'html', 'error.js'), 'utf8');

function tick() {
    return new Promise(resolve => setTimeout(resolve, 0));
}

function canonicalParseFailure(operationId, offset, length) {
    return {
        result: 'error',
        code: 'parse_error',
        description: 'Unexpected term',
        client_operation_id: operationId,
        error: {
            schema: 1,
            domain: 'application',
            code: 'parse_error',
            retryable: false,
            session_action: 'retain',
            operation_outcome: 'confirmed',
            source: {offset, length}
        }
    };
}

function sourcePresentationHarness(initialSource) {
    let editorText = initialSource;
    let activeOperationId = 0;
    let nextOperationId = 0;
    let cursorIndex = null;
    let selectionStart = null;
    let selectionEnd = null;
    let revealCount = 0;

    const editor = {
        getValue() {
            return editorText;
        },
        posFromIndex(index) {
            return {index};
        },
        setCursor(position) {
            cursorIndex = position.index;
        },
        setSelection(start, end) {
            selectionStart = start.index;
            selectionEnd = end.index;
        }
    };

    const window = {
        KANGER_WORKSPACE_STATE: {version: 2},
        KANGER_EDITOR_STATE: {version: 1, installed: true},
        KANGER_OPERATION_PROTOCOL: {
            snapshot() {
                return {activeOperationId};
            }
        },
        editor,
        openEditor(value) {
            if (value === null || value === undefined) {
                revealCount += 1;
            } else {
                editorText = String(value);
            }
        },
        logResponse() {},
        post(packet, callback) {
            if (typeof callback === 'function') {
                callback(packet.__response);
            }
        },
        compileSource() {
            if (activeOperationId !== 0) {
                return undefined;
            }
            activeOperationId = ++nextOperationId;
            return activeOperationId;
        },
        refreshScreen() {},
        setTimeout,
        clearTimeout
    };

    const context = vm.createContext({
        window,
        Object,
        Number,
        String,
        RegExp,
        Error,
        isFinite,
        setTimeout,
        clearTimeout
    });
    vm.runInContext(errorSource, context, {filename: 'error.js'});

    assert.strictEqual(window.KANGER_ERROR_BOUNDARY.version, 1);
    assert.strictEqual(window.KANGER_ERROR_SOURCE_PRESENTATION.version, 1);

    return {
        window,
        operationId() {
            return activeOperationId;
        },
        finish(data) {
            activeOperationId = 0;
            window.refreshScreen(data);
        },
        setText(value) {
            editorText = String(value);
        },
        cursorIndex() {
            return cursorIndex;
        },
        selection() {
            return selectionStart === null ? null
                : {start: selectionStart, end: selectionEnd};
        },
        revealCount() {
            return revealCount;
        }
    };
}

async function qualifyUtf16Point() {
    const harness = sourcePresentationHarness('a\uD83D\uDE00bc');
    harness.window.compileSource();
    const operationId = harness.operationId();
    harness.finish(canonicalParseFailure(operationId, 3, 0));
    await tick();

    assert.strictEqual(harness.cursorIndex(), 3,
        'UTF-16 point must address b after the surrogate pair');
    assert.strictEqual(harness.selection(), null);
    assert.strictEqual(harness.revealCount(), 1);
    console.log('ERROR_SOURCE_PASS utf16-point-cursor');
}

async function qualifyUtf16Range() {
    const harness = sourcePresentationHarness('a\uD83D\uDE00bc');
    harness.window.compileSource();
    const operationId = harness.operationId();
    harness.finish(canonicalParseFailure(operationId, 1, 2));
    await tick();

    assert.deepStrictEqual(harness.selection(), {start: 1, end: 3},
        'UTF-16 range must select the complete surrogate pair');
    assert.strictEqual(harness.cursorIndex(), null);
    assert.strictEqual(harness.revealCount(), 1);
    console.log('ERROR_SOURCE_PASS utf16-range-selection');
}

async function qualifyOperationBinding() {
    const harness = sourcePresentationHarness('abcdef');
    harness.window.compileSource();
    const operationId = harness.operationId();

    harness.finish(canonicalParseFailure(operationId + 1, 2, 0));
    await tick();
    assert.strictEqual(harness.cursorIndex(), null,
        'mismatched operation must not move the cursor');
    assert.strictEqual(harness.revealCount(), 0);

    harness.finish(canonicalParseFailure(operationId, 2, 0));
    await tick();
    assert.strictEqual(harness.cursorIndex(), 2,
        'matching compile operation must own the diagnostic');
    assert.strictEqual(harness.revealCount(), 1);
    console.log('ERROR_SOURCE_PASS compile-operation-binding');
}

async function qualifyDeferredStaleSourceGuard() {
    const harness = sourcePresentationHarness('a\uD83D\uDE00bc');
    harness.window.compileSource();
    const operationId = harness.operationId();
    harness.finish(canonicalParseFailure(operationId, 3, 0));

    // The production adapter deliberately defers presentation by one tick.
    // A user edit in that window must invalidate the source diagnostic.
    harness.setText('changed after submit');
    await tick();

    assert.strictEqual(harness.cursorIndex(), null,
        'stale source must not move the cursor');
    assert.strictEqual(harness.selection(), null,
        'stale source must not create a selection');
    assert.strictEqual(harness.revealCount(), 0,
        'stale source must not reopen the Editor');
    console.log('ERROR_SOURCE_PASS deferred-stale-source-guard');
}

(async function main() {
    await qualifyUtf16Point();
    await qualifyUtf16Range();
    await qualifyOperationBinding();
    await qualifyDeferredStaleSourceGuard();
    console.log('ERROR_SOURCE_OK');
}()).catch(error => {
    console.error(error && error.stack ? error.stack : error);
    process.exit(1);
});

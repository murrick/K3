'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

class Node {
    constructor(id, text) {
        this.id = id || '';
        this.textContent = text || '';
        this.title = '';
        this.style = {};
        this.parentNode = null;
        this.type = '';
        this.files = null;
    }
}

class FakeEditor {
    constructor() {
        this.value = '';
        this.handlers = {};
    }
    getValue() { return this.value; }
    setValue(value) {
        this.value = String(value);
        this.emit('change');
    }
    userSet(value) {
        this.value = String(value);
        this.emit('change');
    }
    on(name, handler) {
        if (!this.handlers[name]) this.handlers[name] = [];
        this.handlers[name].push(handler);
    }
    emit(name) {
        for (const handler of this.handlers[name] || []) handler(this);
    }
    refresh() {}
    focus() {}
}

function main() {
    const title = new Node('console-title', 'Editor');
    const editorContainer = new Node('editor');
    editorContainer.style.display = '';
    const status = new Node('editor-local-status');
    status.style.display = 'none';
    const elements = {
        'console-title': title,
        'editor': editorContainer,
        'editor-local-status': status
    };
    const document = {
        getElementById(id) { return elements[id] || null; }
    };
    const editor = new FakeEditor();
    const listeners = {};
    let postCount = 0;
    let sourceResponse = {result: 'OK', source: '!fresh;', workspace: {schema: 2}};
    let compileCalls = 0;
    let refreshCalls = 0;
    let consoleOpenCalls = 0;
    const parentMessages = [];
    const parent = {
        postMessage(message) { parentMessages.push(message); }
    };

    const window = {
        window: null,
        document,
        editor,
        parent,
        token: 'token',
        KANGER_WORKSPACE_STATE: {version: 2},
        KANGER_EDITOR_FILE_ADAPTER: {version: 2, installed: true},
        KANGER_BROWSER_SOAK_CONVERGENCE: {version: 1},
        setTimeout(callback) { callback(); return 1; },
        addEventListener(name, handler) {
            if (!listeners[name]) listeners[name] = [];
            listeners[name].push(handler);
        },
        openEditor(text) {
            editorContainer.style.display = '';
            if (text !== null && text !== undefined) editor.setValue(text);
            title.textContent = 'Editor';
        },
        openConsole() {
            consoleOpenCalls += 1;
            editorContainer.style.display = 'none';
            title.textContent = 'Console';
        },
        showSourceEditor() {
            throw new Error('legacy source navigation must be superseded');
        },
        showFunctionEditor(id) {
            window.openEditor('=function-' + id + ';');
        },
        compileSource() { compileCalls += 1; },
        refreshScreen() { refreshCalls += 1; },
        post(packet, callback) {
            postCount += 1;
            callback(sourceResponse);
        },
        logRequest(text, callback) { callback(); },
        logResponse() {}
    };
    window.window = window;

    const context = {window, document, Object, Array, Number, String, Error, isFinite, console};
    vm.runInNewContext(fs.readFileSync('html/editor-state.js', 'utf8'), context,
            {filename: 'editor-state.js'});

    assert(window.KANGER_EDITOR_STATE, 'editor state authority was not installed');
    assert.deepStrictEqual(Array.from(window.KANGER_EDITOR_STATE.modes),
            ['CONTEXT', 'EDITED', 'RECOVERY']);

    window.openEditor('!context;');
    let snap = window.KANGER_EDITOR_STATE.snapshot();
    assert.strictEqual(snap.mode, 'CONTEXT');
    assert.strictEqual(snap.dirty, false);
    assert.strictEqual(title.textContent, 'Editor');
    console.log('EDITOR_STATE_PASS clean-context');

    editor.userSet('!context;\n!edited;');
    snap = window.KANGER_EDITOR_STATE.snapshot();
    assert.strictEqual(snap.mode, 'EDITED');
    assert.strictEqual(snap.dirty, true);
    assert.strictEqual(title.textContent, 'Editor *');
    const beforeDirtyNavigation = postCount;
    window.showSourceEditor();
    assert.strictEqual(postCount, beforeDirtyNavigation,
            'dirty source navigation fetched server context');
    assert.strictEqual(editor.getValue(), '!context;\n!edited;');
    console.log('EDITOR_STATE_PASS dirty-local-reuse');
    console.log('EDITOR_STATE_PASS local-dirty-star');

    const recoveryText = '!rejected(one);\n!exact(last);';
    assert.strictEqual(window.KANGER_EDITOR_STATE.recover({
        schema: 1,
        logical_name: 'reject-me.k',
        text: recoveryText
    }), true);
    snap = window.KANGER_EDITOR_STATE.snapshot();
    assert.strictEqual(snap.mode, 'RECOVERY');
    assert.strictEqual(snap.dirty, true);
    assert.strictEqual(snap.fileName, 'reject-me.k');
    assert.strictEqual(snap.text, recoveryText);
    assert.strictEqual(editor.getValue(), recoveryText);
    assert.strictEqual(title.textContent, 'Editor *');
    console.log('EDITOR_STATE_PASS exact-recovery');

    editor.userSet(recoveryText + '\n!repair;');
    snap = window.KANGER_EDITOR_STATE.snapshot();
    assert.strictEqual(snap.mode, 'EDITED');
    assert.strictEqual(snap.dirty, true);
    console.log('EDITOR_STATE_PASS recovery-to-edited');

    window.compileSource();
    assert.strictEqual(compileCalls, 1);
    window.refreshScreen({result: 'OK'});
    assert.strictEqual(refreshCalls, 1);
    snap = window.KANGER_EDITOR_STATE.snapshot();
    assert.strictEqual(snap.mode, 'CONTEXT');
    assert.strictEqual(snap.dirty, false);
    assert.strictEqual(title.textContent, 'Editor');
    assert.strictEqual(consoleOpenCalls, 0,
            'editor state fixture unexpectedly opened Console');
    console.log('EDITOR_STATE_PASS compile-apply-clears-dirty');

    const beforeCleanNavigation = postCount;
    sourceResponse = {result: 'OK', source: '!fresh;', workspace: {schema: 2}};
    window.showSourceEditor();
    assert.strictEqual(postCount, beforeCleanNavigation + 1,
            'clean source navigation did not fetch semantic context');
    assert.strictEqual(editor.getValue(), '!fresh;');
    snap = window.KANGER_EDITOR_STATE.snapshot();
    assert.strictEqual(snap.mode, 'CONTEXT');
    assert.strictEqual(snap.dirty, false);
    console.log('EDITOR_STATE_PASS clean-context-refresh');

    const fileInput = new Node();
    fileInput.type = 'file';
    fileInput.files = [{name: 'local-stage.k'}];
    fire(listeners, 'change', {target: fileInput});
    editor.userSet('!local;');
    snap = window.KANGER_EDITOR_STATE.snapshot();
    assert.strictEqual(snap.fileName, 'local-stage.k');
    assert.strictEqual(snap.dirty, true);

    const actions = new Node('editor-local-actions');
    const save = new Node('', 'Save');
    save.parentNode = actions;
    let stopped = false;
    fire(listeners, 'click', {
        target: save,
        preventDefault() {},
        stopImmediatePropagation() { stopped = true; }
    });
    assert.strictEqual(stopped, true);
    assert.strictEqual(parentMessages.length, 1);
    assert.strictEqual(parentMessages[0].channel, 'kanger.localfile.v1');
    assert.strictEqual(parentMessages[0].type, 'save');
    assert.strictEqual(parentMessages[0].name, 'local-stage.k');
    assert.strictEqual(parentMessages[0].text, '!local;');
    assert.strictEqual(window.KANGER_EDITOR_STATE.snapshot().dirty, true,
            'local save incorrectly marked semantic context applied');
    console.log('EDITOR_STATE_PASS local-staging-save');

    console.log('EDITOR_STATE_OK');
}

function fire(listeners, name, event) {
    for (const handler of listeners[name] || []) handler(event);
}

try {
    main();
} catch (error) {
    console.error(error.stack || error);
    process.exitCode = 1;
}

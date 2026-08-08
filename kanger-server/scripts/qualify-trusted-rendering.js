/*
 * Executable qualification for the KANGER trusted rendering boundary.
 */
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('html/javascript.js', 'utf8');

class Node {
    constructor(nodeType, tagName, text) {
        this.nodeType = nodeType;
        this.tagName = tagName || '';
        this.nodeValue = text || '';
        this.childNodes = [];
        this.parentNode = null;
        this.style = {};
        this.listeners = {};
        this.attributes = Object.create(null);
        this.id = '';
        this.value = '';
        this.selectionStart = 0;
        this.selectionEnd = 0;
        this.onclick = null;
    }

    get firstChild() {
        return this.childNodes.length ? this.childNodes[0] : null;
    }

    appendChild(child) {
        if (child.nodeType === 11) {
            while (child.childNodes.length) {
                this.appendChild(child.childNodes.shift());
            }
            return child;
        }
        if (child.parentNode) {
            const index = child.parentNode.childNodes.indexOf(child);
            if (index >= 0) {
                child.parentNode.childNodes.splice(index, 1);
            }
        }
        child.parentNode = this;
        this.childNodes.push(child);
        return child;
    }

    removeChild(child) {
        const index = this.childNodes.indexOf(child);
        if (index >= 0) {
            this.childNodes.splice(index, 1);
            child.parentNode = null;
        }
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
        const key = String(name);
        return Object.prototype.hasOwnProperty.call(this.attributes, key)
            ? this.attributes[key] : null;
    }

    dispatch(type) {
        const event = {
            currentTarget: this,
            stopPropagation() {}
        };
        (this.listeners[type] || []).forEach(
            (callback) => callback.call(this, event));
        if (type === 'click' && typeof this.onclick === 'function') {
            this.onclick.call(this, event);
        }
    }

    focus() {}

    get textContent() {
        if (this.nodeType === 3) {
            return this.nodeValue;
        }
        return this.childNodes.map((child) => child.textContent).join('');
    }

    set textContent(value) {
        this.childNodes = [];
        const text = value === null || value === undefined
            ? '' : String(value);
        if (text) {
            const child = new Node(3, '', text);
            child.parentNode = this;
            this.childNodes.push(child);
        }
    }
}

class Element extends Node {
    constructor(tagName) {
        super(1, String(tagName || '').toUpperCase(), '');
    }
}

Object.defineProperty(Element.prototype, 'innerHTML', {
    configurable: true,
    get() {
        return this.textContent;
    },
    set(value) {
        // Deliberately model an unsafe native HTML sink. Protected fixed
        // targets must replace this inherited setter with a text-only setter.
        this.childNodes = [];
        const sourceText = String(value || '');
        const match = /<\s*([a-z0-9-]+)/i.exec(sourceText);
        if (match) {
            this.appendChild(new Element(match[1]));
        } else {
            this.textContent = sourceText;
        }
    }
});

class DocumentFragment extends Node {
    constructor() {
        super(11, '', '');
    }
}

function createHarness() {
    const ids = [
        'version-code-login', 'version-code-reg', 'version-code',
        'user-name', 'db-name', 'transaction-level', 'query-message',
        'console-title', 'console-button', 'query-history', 'query-input',
        'statements', 'functions', 'query-results', 'query-solutions',
        'query-hypothesis', 'query-log', 'console-input', 'console',
        'editor', 'console-close'
    ];
    const elements = Object.create(null);
    ids.forEach((id) => {
        const element = new Element(
            id === 'query-input' ? 'textarea' : 'div');
        element.id = id;
        elements[id] = element;
    });

    const writes = [];
    const document = {
        write(value) {
            writes.push(String(value));
        },
        createElement(tagName) {
            return new Element(tagName);
        },
        createTextNode(value) {
            return new Node(3, '', String(value));
        },
        createDocumentFragment() {
            return new DocumentFragment();
        },
        getElementById(id) {
            return elements[id] || null;
        }
    };

    const readyCallbacks = [];
    function jQuery() {
        return {ready: jQuery.fn.ready};
    }
    jQuery.fn = {
        ready(callback) {
            readyCallbacks.push(callback);
            return this;
        }
    };

    const histories = [];
    const requests = [];
    let postHandler = null;

    const window = {
        window: null,
        document,
        Element,
        HTMLElement: Element,
        jQuery,
        btoa(value) {
            return Buffer.from(value, 'binary').toString('base64');
        },
        atob(value) {
            return Buffer.from(value, 'base64').toString('binary');
        },
        token: 'token',
        dots: null,
        editor: {setValue() {}, setCursor() {}},
        scrollToBottom() {},
        placeElements() {},
        dropQueryStatus() {},
        showSourceEditor() {},
        showFunctionEditor() {},
        compileSource() {},
        command() {},
        post(request, callback) {
            requests.push(request);
            assert(postHandler, 'unexpected post without handler');
            postHandler(request, callback);
        },
        storeHistory(value, callback) {
            histories.push(value);
            if (callback) {
                callback({result: 'OK'});
            }
        },
        commandGet() {},
        commandPut() {},
        commandDelete() {},
        commandUse() {},
        commandDrop() {},
        commandReindex() {},
        showTransactionLevel() {},
        logRequestRaw() {},
        logRequest() {},
        logError() {},
        logResponseRaw() {},
        logResponse() {},
        decodeEntities() {},
        setQueryStatus() {},
        openConsole() {},
        openEditor() {},
        showStatements() {},
        showFunctions() {},
        showResults() {},
        showSolutions() {},
        showHypothesis() {},
        showLog() {},
        recurseTree() {},
        showTree() {}
    };
    window.window = window;

    const context = {
        window,
        document,
        Element,
        HTMLElement: Element,
        Object,
        String,
        Number,
        Boolean,
        Array,
        RegExp,
        JSON,
        Math,
        Date,
        Error,
        isFinite,
        parseInt,
        encodeURIComponent,
        decodeURIComponent,
        escape,
        unescape,
        setInterval() { return 1; },
        clearInterval() {}
    };

    vm.runInNewContext(source, context, {filename: 'html/javascript.js'});

    // Register the historical console callback through the wrapped ready API,
    // then execute the wrapper to install the boundary first.
    let historicalReadyRan = false;
    jQuery.fn.ready(function () {
        historicalReadyRan = true;
    });
    assert.strictEqual(readyCallbacks.length, 1);
    readyCallbacks[0]();
    assert(historicalReadyRan);

    return {
        window,
        document,
        elements,
        writes,
        histories,
        requests,
        setPostHandler(handler) {
            postHandler = handler;
        }
    };
}

function tags(node, result = []) {
    if (node.nodeType === 1) {
        result.push(node.tagName);
    }
    node.childNodes.forEach((child) => tags(child, result));
    return result;
}

function descendants(node, predicate, result = []) {
    if (predicate(node)) {
        result.push(node);
    }
    node.childNodes.forEach(
        (child) => descendants(child, predicate, result));
    return result;
}

function decodeHistory(record) {
    const payload = record.split('~~~')[1];
    assert(payload.startsWith('@K2@'));
    return Buffer.from(payload.substring(4), 'base64').toString('utf8');
}

(function run() {
    const harness = createHarness();
    const {window, elements} = harness;

    assert.deepStrictEqual(
        harness.writes,
        ['<script src="javascript-mode.js"></script>']
    );
    assert.strictEqual(window.KANGER_TRUSTED_RENDERING.version, 1);
    assert.strictEqual(window.KANGER_TRUSTED_RENDERING.installed, true);
    assert(Object.isFrozen(window.KANGER_TRUSTED_RENDERING));

    elements['user-name'].innerHTML =
        '<img src=x onerror="globalThis.compromised=true">';
    assert.strictEqual(
        elements['user-name'].textContent,
        '<img src=x onerror="globalThis.compromised=true">'
    );
    assert(!tags(elements['user-name']).includes('IMG'));
    console.log('TRUSTED_RENDERING_PASS fixed-text-targets');

    const hostileQuery =
        '<img src=x onerror=alert(1)>~~~ Привет & שלום';
    window.logRequest(hostileQuery);
    const requestLine = elements['query-history'].childNodes.at(-1);
    assert.strictEqual(requestLine.textContent, hostileQuery);
    assert(!tags(requestLine).includes('IMG'));
    assert.strictEqual(decodeHistory(harness.histories.at(-1)), hostileQuery);

    const restored = window.logRequestRaw(
        '<b>legacy</b><img src=x onerror=alert(1)>&lt;');
    assert.strictEqual(
        restored.textContent,
        'legacy<img src=x onerror=alert(1)><'
    );
    assert(!tags(restored).includes('IMG'));
    console.log('TRUSTED_RENDERING_PASS plain-history');

    const description =
        '<b>Allowed</b><br><b onclick=alert(1)>literal</b>'
        + '<script>attack()</script>&lt;';
    const response = window.logResponseRaw({
        result: 'OK',
        description,
        transaction: 0,
        empty: true
    });
    const responseTags = tags(response);
    assert(responseTags.includes('STRONG'));
    assert(responseTags.includes('BR'));
    assert(!responseTags.includes('SCRIPT'));
    assert(!responseTags.includes('IMG'));
    assert(response.textContent.includes('<b onclick=alert(1)>'));
    assert(response.textContent.includes('<script>attack()</script><'));
    console.log('TRUSTED_RENDERING_PASS narrow-legacy-description');

    harness.setPostHandler((request, callback) => {
        if (request.context === 'command'
                && Object.prototype.hasOwnProperty.call(
                    request.parameters, 'get')) {
            callback({
                result: 'OK',
                size: 1,
                list: ['evil\'" onclick="attack()<img>']
            });
            return;
        }
        throw new Error('unexpected request ' + JSON.stringify(request));
    });
    window.commandGet(['get']);
    const choices = elements['query-history'].childNodes.at(-1);
    const spans = descendants(
        choices, (node) => node.tagName === 'SPAN');
    assert.strictEqual(spans.length, 1);
    assert.strictEqual(
        spans[0].textContent,
        'evil\'" onclick="attack()<img>'
    );
    assert(!tags(choices).includes('IMG'));
    assert.strictEqual(spans[0].onclick, null);
    assert.strictEqual((spans[0].listeners.click || []).length, 0);
    const composed = spans[0].getAttribute('data-kanger-compose');
    assert(composed.startsWith('get "'));
    assert(composed.includes("evil'"));
    assert(composed.includes('onclick='));
    assert(composed.includes('\\"attack()<img>'));
    assert(composed.endsWith('"'));
    console.log('TRUSTED_RENDERING_PASS generated-choice-compose');

    harness.setPostHandler((request, callback) => {
        if (request.context === 'query'
                && request.parameters.results === '') {
            callback({
                result: 'OK',
                size: 1,
                list: [[{
                    name: '<img src=x onerror=attack()>',
                    value: '<script>attack()</script>'
                }]]
            });
            return;
        }
        throw new Error('unexpected request ' + JSON.stringify(request));
    });
    window.showResults();
    assert.strictEqual(
        elements['query-results'].textContent,
        '#<img src=x onerror=attack()>1<script>attack()</script>'
    );
    const resultTags = tags(elements['query-results']);
    assert(!resultTags.includes('IMG'));
    assert(!resultTags.includes('SCRIPT'));
    console.log('TRUSTED_RENDERING_PASS semantic-table-text');

    harness.setPostHandler((request, callback) => {
        if (request.context === 'query'
                && request.parameters.statements === ''
                && request.parameters.causes === true) {
            callback({
                result: 'OK',
                size: 1,
                list: [{
                    origin: '<img src=x onerror=attack()>',
                    causes: [{
                        rule: {origin: '<script>rule()</script>'},
                        donor: {
                            origin: '<img src=x onerror=attack()>',
                            generated: true
                        },
                        causes: []
                    }]
                }]
            });
            return;
        }
        throw new Error('unexpected request ' + JSON.stringify(request));
    });
    window.showTree('7');
    const tree = elements['query-history'].childNodes.at(-1);
    assert(tree.textContent.includes('<script>rule()</script>'));
    assert(tree.textContent.includes('<img src=x onerror=attack()>'));
    const treeTags = tags(tree);
    assert(!treeTags.includes('SCRIPT'));
    assert(!treeTags.includes('IMG'));
    console.log('TRUSTED_RENDERING_PASS inference-tree-text');

    assert(!source.includes('insertAdjacentHTML'));
    assert(!source.includes('outerHTML'));
    assert(!source.includes('onclick='));
    console.log('TRUSTED_RENDERING_OK');
}());

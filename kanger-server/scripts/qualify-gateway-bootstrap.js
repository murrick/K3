#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('html/gateway.js', 'utf8');
const match = source.match(
        /function stringifyForInlineScript\(value\) \{[\s\S]*?\n    \}/);
assert(match, 'script-safe JSON serializer is absent');

const anonymous = match[0].replace(
        'function stringifyForInlineScript', 'function');
const stringifyForInlineScript = vm.runInNewContext(
        '(' + anonymous + ')', {JSON});

const hostileLogin = '</script><script>globalThis.compromised=true</script>'
        + '&\u2028\u2029';
const bootstrap = {
    token: 'opaque-token',
    login: hostileLogin,
    generation: 17,
    parentOrigin: 'https://kanger.example',
    channel: 'kanger.session.v1'
};
const serialized = stringifyForInlineScript(bootstrap);

assert(!serialized.includes('<'), 'serialized bootstrap contains raw <');
assert(!serialized.includes('>'), 'serialized bootstrap contains raw >');
assert(!serialized.includes('&'), 'serialized bootstrap contains raw &');
assert(!serialized.includes('\u2028'), 'serialized bootstrap contains raw U+2028');
assert(!serialized.includes('\u2029'), 'serialized bootstrap contains raw U+2029');
assert(serialized.includes('\\u003c/script\\u003e'));
assert(serialized.includes('\\u0026'));
assert.deepStrictEqual(JSON.parse(serialized), bootstrap,
        'script-safe serialization changed the principal identity');

assert(source.includes('stringifyForInlineScript(API_HOST)'));
assert(source.includes('stringifyForInlineScript(bridge.bootstrap)'));

const expression = 'globalThis.bootstrap = Object.freeze('
        + serialized + ');';
const context = {globalThis: {compromised: false}, Object};
vm.runInNewContext(expression, context);
assert.strictEqual(context.globalThis.compromised, false,
        'hostile principal escaped the bootstrap script expression');
assert.strictEqual(context.globalThis.bootstrap.login, hostileLogin);
assert(Object.isFrozen(context.globalThis.bootstrap));

console.log('GATEWAY_BOOTSTRAP_SERIALIZATION_PASS hostile-principal');
console.log('GATEWAY_BOOTSTRAP_SERIALIZATION_OK');

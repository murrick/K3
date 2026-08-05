/*
 * KANGER parser-time console loader.
 *
 * The historical console expects the CodeMirror JavaScript mode at this
 * position. Preserve that ordering explicitly, then install the supported
 * trusted-rendering boundary before the legacy inline script registers its
 * jQuery.ready callback.
 */
(function (document) {
    'use strict';
    document.write('<script src="javascript-mode.js"><\/script>');
}(document));

/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Supported KANGER console rendering boundary.
 *
 * This adapter is loaded before the historical console registers its
 * jQuery.ready callback. It replaces string-built DOM presentation with
 * text nodes, explicit DOM builders and a deliberately narrow legacy
 * description renderer.
 */
(function (window, document) {
    'use strict';

    var HISTORY_PREFIX = '@K2@';
    var TEXT_ONLY_IDS = [
        'version-code-login',
        'version-code-reg',
        'version-code',
        'user-name',
        'db-name',
        'transaction-level',
        'query-message',
        'console-title',
        'console-button'
    ];
    var installed = false;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function clearElement(element) {
        if (!element) {
            return;
        }
        while (element.firstChild) {
            element.removeChild(element.firstChild);
        }
    }

    function appendText(parent, value) {
        parent.appendChild(document.createTextNode(stringValue(value)));
    }

    function appendStrong(parent, value) {
        var strong = document.createElement('strong');
        strong.textContent = stringValue(value);
        parent.appendChild(strong);
        return strong;
    }

    function pad4(value) {
        var text = '0000' + stringValue(value);
        return text.substring(text.length - 4);
    }

    function addHover(element) {
        element.addEventListener('mouseover', function () {
            element.style.backgroundColor = '#DDD';
        });
        element.addEventListener('mouseout', function () {
            element.style.backgroundColor = '';
        });
    }

    function decodeEntity(token) {
        var body = token.substring(1, token.length - 1);
        var named = {
            amp: '&',
            lt: '<',
            gt: '>',
            quot: '"',
            apos: "'",
            nbsp: '\u00a0'
        };
        var lower = body.toLowerCase();
        if (Object.prototype.hasOwnProperty.call(named, lower)) {
            return named[lower];
        }
        var code = null;
        if (/^#x[0-9a-f]+$/i.test(body)) {
            code = parseInt(body.substring(2), 16);
        } else if (/^#[0-9]+$/.test(body)) {
            code = parseInt(body.substring(1), 10);
        }
        if (code === null || !isFinite(code) || code < 0
                || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff)) {
            return token;
        }
        if (String.fromCodePoint) {
            return String.fromCodePoint(code);
        }
        if (code <= 0xffff) {
            return String.fromCharCode(code);
        }
        code -= 0x10000;
        return String.fromCharCode(
                0xd800 + (code >> 10),
                0xdc00 + (code & 0x3ff));
    }

    function tokenizeLegacyMarkup(value, consumer) {
        var source = stringValue(value).replace(/\r\n?/g, '\n');
        var pattern = /(<\/?b\s*>|<br\s*\/?\s*>|&(?:#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos|nbsp);)/ig;
        var offset = 0;
        var match;
        while ((match = pattern.exec(source)) !== null) {
            if (match.index > offset) {
                consumer('text', source.substring(offset, match.index));
            }
            var token = match[0];
            if (/^<b\s*>$/i.test(token)) {
                consumer('bold-start', '');
            } else if (/^<\/b\s*>$/i.test(token)) {
                consumer('bold-end', '');
            } else if (/^<br\s*\/?\s*>$/i.test(token)) {
                consumer('break', '');
            } else {
                consumer('text', decodeEntity(token));
            }
            offset = pattern.lastIndex;
        }
        if (offset < source.length) {
            consumer('text', source.substring(offset));
        }
    }

    function appendLegacyDescription(parent, value) {
        var current = parent;
        tokenizeLegacyMarkup(value, function (type, text) {
            if (type === 'bold-start') {
                var strong = document.createElement('strong');
                parent.appendChild(strong);
                current = strong;
            } else if (type === 'bold-end') {
                current = parent;
            } else if (type === 'break') {
                parent.appendChild(document.createElement('br'));
            } else {
                appendText(current, text);
            }
        });
    }

    function legacyDescriptionText(value) {
        var text = '';
        tokenizeLegacyMarkup(value, function (type, part) {
            if (type === 'break') {
                text += '\n';
            } else if (type === 'text') {
                text += part;
            }
        });
        return text;
    }

    function encodeUtf8Base64(value) {
        var utf8 = unescape(encodeURIComponent(stringValue(value)));
        return window.btoa(utf8);
    }

    function decodeUtf8Base64(value) {
        try {
            return decodeURIComponent(escape(window.atob(value)));
        } catch (ignored) {
            return null;
        }
    }

    function encodeHistoryText(value) {
        return HISTORY_PREFIX + encodeUtf8Base64(value);
    }

    function decodeHistoryText(value) {
        var source = stringValue(value);
        if (source.indexOf(HISTORY_PREFIX) === 0) {
            var decoded = decodeUtf8Base64(source.substring(HISTORY_PREFIX.length));
            return decoded === null ? '[invalid history record]' : decoded;
        }
        return legacyDescriptionText(source);
    }

    function protectTextOnlyElement(element) {
        if (!element || element.__kangerTextOnly) {
            return;
        }
        var prototype = window.Element && window.Element.prototype;
        var descriptor = prototype
                ? Object.getOwnPropertyDescriptor(prototype, 'innerHTML') : null;
        if (!descriptor && window.HTMLElement) {
            descriptor = Object.getOwnPropertyDescriptor(
                    window.HTMLElement.prototype, 'innerHTML');
        }
        if (!descriptor || !descriptor.get || !descriptor.set) {
            return;
        }
        Object.defineProperty(element, 'innerHTML', {
            configurable: true,
            enumerable: false,
            get: function () {
                return descriptor.get.call(this);
            },
            set: function (value) {
                this.textContent = stringValue(value);
            }
        });
        element.__kangerTextOnly = true;
    }

    function protectFixedTextTargets() {
        for (var i = 0; i < TEXT_ONLY_IDS.length; i++) {
            protectTextOnlyElement(document.getElementById(TEXT_ONLY_IDS[i]));
        }
    }

    function requestLine(text, fromHistory) {
        var value = fromHistory ? decodeHistoryText(text) : stringValue(text);
        var div = document.createElement('div');
        div.style.padding = '2px 2px 2px 8px';
        div.style.fontFamily = 'monospace';
        div.style.fontSize = '13px';
        div.style.whiteSpace = 'pre-wrap';
        div.textContent = value;
        div.__kangerHistoryText = value;

        if (value.indexOf('//') !== 0) {
            div.style.cursor = 'pointer';
            addHover(div);
            div.addEventListener('click', function () {
                var queryInput = document.getElementById('query-input');
                queryInput.value = value;
                queryInput.focus();
                queryInput.selectionStart = queryInput.selectionEnd =
                        queryInput.value.length;
            });
        }

        document.getElementById('query-history').appendChild(div);
        window.scrollToBottom();
        return div;
    }

    function responseLine(data, presentation, fromHistory) {
        var div = document.createElement('div');
        div.style.padding = '2px 2px 4px 32px';
        div.style.fontSize = '13px';
        div.style.fontFamily = 'monospace';
        div.style.whiteSpace = 'pre-wrap';

        var historyText;
        if (fromHistory) {
            historyText = decodeHistoryText(presentation);
            div.textContent = historyText;
        } else if (presentation && typeof presentation === 'object'
                && presentation.nodeType) {
            div.appendChild(presentation);
            historyText = stringValue(
                    presentation.__kangerHistoryText || presentation.textContent);
        } else if (presentation !== null && presentation !== undefined) {
            historyText = stringValue(presentation);
            div.textContent = historyText;
        } else if (data) {
            var description = stringValue(data.description);
            appendLegacyDescription(div, description);
            historyText = legacyDescriptionText(description);
            window.showTransactionLevel(data);
        } else {
            historyText = '';
        }

        if (data) {
            window.placeElements(data);
        }
        div.__kangerHistoryText = historyText;
        div.style.color = !data || data.result === 'OK' ? '#777' : '#A55';
        document.getElementById('query-history').appendChild(div);
        window.scrollToBottom();
        return div;
    }

    function installHistoryBoundary() {
        window.logRequestRaw = function (historyValue) {
            return requestLine(historyValue, true);
        };
        window.logRequest = function (queryText, callback) {
            var div = requestLine(queryText, false);
            window.storeHistory(
                    'Q' + div.style.color + '~~~'
                    + encodeHistoryText(div.__kangerHistoryText),
                    callback);
        };
        window.logError = function (line) {
            var div = document.createElement('div');
            div.style.padding = '2px 2px 4px 32px';
            div.style.fontSize = '13px';
            div.style.fontFamily = 'monospace';
            div.style.whiteSpace = 'pre-wrap';
            div.style.color = '#A55';
            div.textContent = stringValue(line);
            div.__kangerHistoryText = stringValue(line);
            document.getElementById('query-history').appendChild(div);
            window.scrollToBottom();
            return div;
        };
        window.logResponseRaw = function (data, presentation) {
            return responseLine(data, presentation, data === null);
        };
        window.logResponse = function (data, presentation, callback) {
            var div = responseLine(data, presentation, false);
            window.storeHistory(
                    'R' + div.style.color + '~~~'
                    + encodeHistoryText(div.__kangerHistoryText),
                    callback);
        };
        window.decodeEntities = function (value) {
            return stringValue(value);
        };
    }

    function choicePresentation(label, list, commandName) {
        var fragment = document.createDocumentFragment();
        appendText(fragment, label);
        var history = label;
        for (var i = 0; i < list.length; i++) {
            (function (item) {
                var span = document.createElement('span');
                span.style.cursor = 'pointer';
                span.style.padding = '0 3px';
                span.textContent = item;
                addHover(span);
                span.addEventListener('click', function () {
                    var query = commandName + ' ' + item;
                    window.logRequest(query, function () {
                        window.command(query);
                    });
                });
                fragment.appendChild(span);
                appendText(fragment, ' ');
                history += item + ' ';
            }(stringValue(list[i])));
        }
        fragment.__kangerHistoryText = history.replace(/\s+$/, '');
        return fragment;
    }

    function listCommand(requestParameters, label, emptyText,
                         commandName, callback) {
        requestParameters.token = window.token;
        window.post({
            context: 'command',
            parameters: requestParameters
        }, function (data) {
            if (data.result === 'OK') {
                if (data.size > 0) {
                    window.logResponse(
                            data,
                            choicePresentation(
                                    label, data.list || [], commandName));
                } else {
                    window.logResponse(data, emptyText);
                }
            } else {
                window.logResponse(data);
            }
            if (callback) {
                callback(data);
            }
        });
    }

    function installChoiceBoundaries() {
        var legacyGet = window.commandGet;
        var legacyPut = window.commandPut;
        var legacyDelete = window.commandDelete;
        var legacyUse = window.commandUse;
        var legacyDrop = window.commandDrop;
        var legacyReindex = window.commandReindex;

        window.commandGet = function (cmd, callback) {
            if (cmd.length === 2) {
                return legacyGet(cmd, callback);
            }
            listCommand({get: ''}, 'Available sources: ',
                    'No source files available', 'get', callback);
        };
        window.commandPut = function (cmd, callback) {
            if (cmd.length === 2) {
                return legacyPut(cmd, callback);
            }
            listCommand({get: ''}, 'Save current source to: ',
                    'You have to select a source file name',
                    'put', callback);
        };
        window.commandDelete = function (cmd, callback) {
            if (cmd.length === 2) {
                return legacyDelete(cmd, callback);
            }
            listCommand({get: ''}, 'Select file for delete: ',
                    'No source files available', 'delete', callback);
        };
        window.commandUse = function (cmd, callback) {
            if (!(cmd[0] === 'use' && cmd.length !== 2)) {
                return legacyUse(cmd, callback);
            }
            listCommand({use: ''}, 'Available DBs: ',
                    'No databases was created', 'use', callback);
        };
        window.commandDrop = function (cmd, callback) {
            if (!(cmd[0] === 'drop' && cmd.length !== 2)) {
                return legacyDrop(cmd, callback);
            }
            listCommand({use: ''}, 'Select DB for drop: ',
                    'No databases was created', 'drop', callback);
        };
        window.commandReindex = function (cmd, callback) {
            if (!(cmd[0] === 'reindex' && cmd.length !== 2)) {
                return legacyReindex(cmd, callback);
            }
            listCommand({use: ''}, 'Select DB for reindex: ',
                    'No databases was created', 'reindex', callback);
        };
    }

    function installStatusBoundary() {
        window.showTransactionLevel = function (data) {
            if (data && data.transaction !== null
                    && data.transaction !== undefined
                    && data.empty !== null && data.empty !== undefined) {
                var transactionLevel =
                        document.getElementById('transaction-level');
                transactionLevel.textContent = stringValue(data.transaction);
                if (data.empty) {
                    transactionLevel.style.color = '#777';
                    transactionLevel.style.fontWeight = '';
                } else {
                    transactionLevel.style.color = '#282';
                    transactionLevel.style.fontWeight = 'bold';
                }
            }
        };

        window.setQueryStatus = function (status) {
            var queryInput = document.getElementById('query-input');
            var queryMessage = document.getElementById('query-message');
            queryInput.style.display = 'none';
            queryMessage.style.display = '';
            queryMessage.textContent = status || 'Processing query';
            if (window.dots) {
                clearInterval(window.dots);
            }
            window.dots = setInterval(function () {
                var value = queryMessage.textContent || '';
                if (value.indexOf('.......') >= 0) {
                    value = value.replace('.......', '');
                }
                queryMessage.textContent = value + '.';
            }, 500);
        };

        window.openConsole = function () {
            var consoleInput = document.getElementById('console-input');
            var consoleElement = document.getElementById('console');
            var editor = document.getElementById('editor');
            var consoleTitle = document.getElementById('console-title');
            var consoleButton = document.getElementById('console-button');
            var consoleClose = document.getElementById('console-close');

            editor.style.display = 'none';
            consoleClose.style.display = 'none';
            consoleInput.style.display = '';
            consoleElement.style.display = '';
            consoleTitle.textContent = 'Console';
            consoleButton.textContent = 'Editor';
            consoleButton.onclick = window.showSourceEditor;
        };

        window.openEditor = function (text) {
            if (text === null || text === undefined) {
                return;
            }
            var consoleInput = document.getElementById('console-input');
            var consoleElement = document.getElementById('console');
            var editor = document.getElementById('editor');
            var consoleTitle = document.getElementById('console-title');
            var consoleButton = document.getElementById('console-button');
            var consoleClose = document.getElementById('console-close');

            consoleInput.style.display = 'none';
            consoleElement.style.display = 'none';
            editor.style.display = '';
            consoleClose.style.display = '';
            consoleTitle.textContent = 'Editor';
            consoleButton.textContent = 'Compile';
            consoleButton.onclick = window.compileSource;
            consoleClose.onclick = window.openConsole;
            window.editor.setValue(stringValue(text));
            window.editor.setCursor(0, 0);
        };
    }

    function installSemanticRenderers() {
        window.showStatements = function () {
            var statements = document.getElementById('statements');
            if (!statements) {
                return;
            }
            clearElement(statements);
            window.post({
                context: 'query',
                parameters: {
                    token: window.token,
                    predicates: '',
                    statements: true
                }
            }, function (data) {
                if (!data || data.result !== 'OK') {
                    return;
                }
                for (var i = 0; i < data.size; i++) {
                    var predicate = data.list[i];
                    var row = document.createElement('div');
                    row.id = 'PR' + predicate.id;
                    row.style.fontSize = '13px';
                    row.style.fontFamily = 'monospace';
                    row.style.whiteSpace = 'nowrap';
                    row.style.cursor = 'pointer';
                    if (predicate.deleted) {
                        row.style.color = '#777';
                    }

                    var sign = document.createElement('span');
                    sign.id = 'SN' + predicate.id;
                    sign.style.display = 'inline-block';
                    sign.style.width = '10px';
                    sign.textContent =
                            predicate.statements.length ? '+' : '-';
                    row.appendChild(sign);
                    appendText(row, '\u00a0' + pad4(predicate.id) + '\u00a0');
                    appendStrong(row, predicate.name);
                    appendText(row, '(' + stringValue(predicate.range)
                            + ') ' + predicate.statements.length);
                    statements.appendChild(row);

                    if (predicate.statements.length) {
                        var details = document.createElement('div');
                        details.id = 'PRL' + predicate.id;
                        details.style.fontSize = '13px';
                        details.style.fontFamily = 'monospace';
                        details.style.whiteSpace = 'nowrap';
                        details.style.paddingLeft = '16px';
                        details.style.display = 'none';
                        statements.appendChild(details);

                        (function (detailsNode, signNode) {
                            row.addEventListener('click', function () {
                                var open = detailsNode.style.display === 'none';
                                detailsNode.style.display = open ? '' : 'none';
                                signNode.textContent = open ? '-' : '+';
                            });
                        }(details, sign));

                        for (var j = 0;
                             j < predicate.statements.length; j++) {
                            var statement = predicate.statements[j];
                            var statementRow =
                                    document.createElement('div');
                            statementRow.id = 'PS' + statement.id;
                            statementRow.style.fontSize = '13px';
                            statementRow.style.fontFamily = 'monospace';
                            statementRow.style.whiteSpace = 'nowrap';
                            statementRow.style.cursor = 'pointer';
                            if (statement.deleted) {
                                statementRow.style.color = '#777';
                            } else if (statement.generated) {
                                statementRow.style.color = '#282';
                            } else {
                                statementRow.style.color = '#228';
                            }
                            appendText(statementRow,
                                    pad4(statement.id) + '\u00a0');
                            appendStrong(statementRow, statement.origin);
                            statementRow.addEventListener(
                                    'click', function (event) {
                                        event.stopPropagation();
                                        window.showTree(
                                                event.currentTarget.id
                                                        .substring(2));
                                    });
                            details.appendChild(statementRow);
                        }
                    }
                }
            });
        };

        window.showFunctions = function () {
            var functions = document.getElementById('functions');
            clearElement(functions);
            window.post({
                context: 'query',
                parameters: {token: window.token, functions: ''}
            }, function (data) {
                if (!data || data.result !== 'OK') {
                    return;
                }
                for (var i = 0; i < data.size; i++) {
                    var fn = data.list[i];
                    var row = document.createElement('div');
                    row.id = 'FN' + fn.id;
                    row.style.fontSize = '13px';
                    row.style.fontFamily = 'monospace';
                    row.style.whiteSpace = 'nowrap';
                    row.style.cursor = 'pointer';
                    if (fn.deleted) {
                        row.style.color = '#777';
                    }
                    appendText(row, pad4(fn.id) + '\u00a0');
                    appendStrong(row, fn.name);
                    appendText(row, '(' + stringValue(fn.range) + ')');
                    row.addEventListener('click', function (event) {
                        window.showFunctionEditor(
                                event.currentTarget.id.substring(2));
                    });
                    functions.appendChild(row);
                }
            });
        };

        window.showResults = function () {
            var results = document.getElementById('query-results');
            if (!results) {
                return;
            }
            clearElement(results);
            window.post({
                context: 'query',
                parameters: {token: window.token, results: ''}
            }, function (data) {
                if (!data || data.result !== 'OK' || data.size <= 0) {
                    return;
                }
                var titles = [];
                var table = document.createElement('table');
                results.appendChild(table);
                var header = document.createElement('tr');
                table.appendChild(header);

                function headerCell(value) {
                    var cell = document.createElement('td');
                    cell.style.backgroundColor = '#444';
                    cell.style.color = '#FFF';
                    cell.style.fontWeight = 'bold';
                    cell.style.fontFamily = 'monospace';
                    cell.style.fontSize = '13px';
                    cell.style.padding = '6px';
                    cell.textContent = stringValue(value);
                    header.appendChild(cell);
                }

                headerCell('#');
                for (var i = 0; i < data.list.length; i++) {
                    for (var j = 0; j < data.list[i].length; j++) {
                        var title = stringValue(data.list[i][j].name);
                        if (titles.indexOf(title) < 0) {
                            titles.push(title);
                            headerCell(title);
                        }
                    }
                }

                for (var rowIndex = 0;
                     rowIndex < data.list.length; rowIndex++) {
                    var row = document.createElement('tr');
                    table.appendChild(row);

                    function bodyCell(value, right) {
                        var cell = document.createElement('td');
                        cell.style.backgroundColor =
                                rowIndex % 2 ? '#FFF' : '#EEE';
                        cell.style.color = '#000';
                        cell.style.fontFamily = 'monospace';
                        cell.style.fontSize = '13px';
                        cell.style.padding = '2px 6px';
                        if (right) {
                            cell.style.textAlign = 'right';
                        }
                        cell.textContent = stringValue(value);
                        row.appendChild(cell);
                    }

                    bodyCell(rowIndex + 1, true);
                    for (var titleIndex = 0;
                         titleIndex < titles.length; titleIndex++) {
                        var cellValue = '';
                        for (var valueIndex = 0;
                             valueIndex < data.list[rowIndex].length;
                             valueIndex++) {
                            if (stringValue(
                                    data.list[rowIndex][valueIndex].name)
                                    === titles[titleIndex]) {
                                cellValue =
                                        data.list[rowIndex][valueIndex].value;
                            }
                        }
                        bodyCell(cellValue, false);
                    }
                }
            });
        };

        window.showSolutions = function () {
            var solutions = document.getElementById('query-solutions');
            if (!solutions) {
                return;
            }
            clearElement(solutions);
            window.post({
                context: 'query',
                parameters: {token: window.token, solutions: ''}
            }, function (data) {
                if (!data || data.result !== 'OK') {
                    return;
                }
                for (var i = 0; i < data.list.length; i++) {
                    var solution = data.list[i];
                    var row = document.createElement('div');
                    row.id = 'SOL' + solution.id;
                    row.style.fontSize = '13px';
                    row.style.fontFamily = 'monospace';
                    row.style.whiteSpace = 'nowrap';
                    row.style.cursor = 'pointer';
                    if (stringValue(solution.origin).charAt(0) === '!') {
                        row.style.color = '#272';
                    } else if (stringValue(solution.origin).charAt(0) === '?') {
                        row.style.color = '#722';
                    }
                    appendText(row, pad4(solution.id) + '\u00a0');
                    appendStrong(row, solution.origin);
                    row.addEventListener('click', function (event) {
                        window.showTree(
                                event.currentTarget.id.substring(3));
                    });
                    solutions.appendChild(row);
                }
            });
        };

        window.showHypothesis = function () {
            var hypothesis = document.getElementById('query-hypothesis');
            if (!hypothesis) {
                return;
            }
            clearElement(hypothesis);
            window.setQueryStatus('Analyzing hypothesis...');
            window.post({
                context: 'query',
                parameters: {token: window.token, hypothesis: ''}
            }, function (data) {
                window.dropQueryStatus();
                if (!data || data.result !== 'OK') {
                    return;
                }
                for (var i = 0; i < data.list.length; i++) {
                    var item = data.list[i];
                    var origin = stringValue(item.origin);
                    var row = document.createElement('div');
                    row.id = 'HYL' + i;
                    row.style.fontSize = '13px';
                    row.style.fontFamily = 'monospace';
                    row.style.whiteSpace = 'nowrap';
                    row.style.cursor = 'pointer';
                    if (origin.charAt(0) === '!') {
                        row.style.color = '#272';
                    } else if (origin.charAt(0) === '?') {
                        row.style.color = '#722';
                    }
                    appendText(row, pad4(i) + '\u00a0');
                    appendStrong(row, origin);
                    row.__kangerOrigin = origin;
                    row.addEventListener('click', function () {
                        var queryInput =
                                document.getElementById('query-input');
                        var value = this.__kangerOrigin.charAt(0) === '?'
                                ? '!~' + this.__kangerOrigin.substring(1)
                                : this.__kangerOrigin;
                        queryInput.value = value;
                        queryInput.focus();
                        queryInput.selectionStart =
                                queryInput.selectionEnd =
                                        queryInput.value.length;
                    });
                    hypothesis.appendChild(row);
                }
            });
        };

        window.showLog = function () {
            var log = document.getElementById('query-log');
            if (!log) {
                return;
            }
            clearElement(log);
            window.post({
                context: 'query',
                parameters: {token: window.token, log: ''}
            }, function (data) {
                if (!data || data.result !== 'OK') {
                    return;
                }
                for (var i = 0; i < data.list.length; i++) {
                    var entry = data.list[i];
                    var row = document.createElement('div');
                    row.style.fontSize = '13px';
                    row.style.fontFamily = 'monospace';
                    row.style.whiteSpace = 'pre-wrap';
                    if (entry.type === 'ANALYZER') {
                        row.style.color = '#000';
                    } else if (entry.type === 'VALUES'
                            || entry.type === 'SOLVES') {
                        row.style.color = '#272';
                    } else if (entry.type === 'STORAGE') {
                        row.style.color = '#227';
                    } else {
                        row.style.color = '#777';
                    }
                    row.textContent = stringValue(entry.record);
                    log.appendChild(row);
                }
            });
        };
    }

    function appendInferenceTree(parent, causes, level) {
        causes = causes || [];
        for (var i = 0; i < causes.length; i++) {
            var cause = causes[i];
            var indent = '';
            for (var p = 0; p < level; p++) {
                indent += '    ';
            }
            appendText(parent, indent);
            appendStrong(parent,
                    cause.rule ? cause.rule.origin : '');
            parent.appendChild(document.createElement('br'));

            appendText(parent, indent);
            var donor = document.createElement('span');
            donor.style.color = !cause.donor
                    || cause.donor.generated === null
                    || cause.donor.generated === undefined
                    || cause.donor.generated ? '#3A3' : '#33A';
            donor.textContent = cause.donor
                    ? stringValue(cause.donor.origin) : '';
            parent.appendChild(donor);
            parent.appendChild(document.createElement('br'));
            appendInferenceTree(parent, cause.causes, level + 1);
        }
    }

    function installInferenceRenderer() {
        window.recurseTree = function (causes, level) {
            var fragment = document.createDocumentFragment();
            appendInferenceTree(fragment, causes, level || 0);
            fragment.__kangerHistoryText = fragment.textContent;
            return fragment;
        };

        window.showTree = function (id) {
            window.post({
                context: 'query',
                parameters: {
                    token: window.token,
                    statements: '',
                    id: id,
                    causes: true
                }
            }, function (data) {
                if (!data || data.result !== 'OK' || data.size !== 1) {
                    return;
                }
                var origin = stringValue(data.list[0].origin);
                window.logRequest('// Inference tree for: ' + origin,
                        function () {
                            var fragment = document.createDocumentFragment();
                            appendInferenceTree(
                                    fragment,
                                    data.list[0].causes,
                                    0);
                            fragment.__kangerHistoryText =
                                    fragment.textContent;
                            window.logResponse(null, fragment);
                        });
            });
        };
    }

    function install() {
        if (installed) {
            return;
        }
        installed = true;
        protectFixedTextTargets();
        installHistoryBoundary();
        installChoiceBoundaries();
        installStatusBoundary();
        installSemanticRenderers();
        installInferenceRenderer();
        window.KANGER_TRUSTED_RENDERING =
                Object.freeze({version: 1, installed: true});
    }

    function wrapJQueryReady() {
        if (!window.jQuery || !window.jQuery.fn
                || typeof window.jQuery.fn.ready !== 'function') {
            throw new Error('KANGER trusted rendering requires jQuery.ready');
        }
        var originalReady = window.jQuery.fn.ready;
        window.jQuery.fn.ready = function (callback) {
            return originalReady.call(this, function () {
                install();
                return callback.apply(this, arguments);
            });
        };
    }

    wrapJQueryReady();
}(window, document));

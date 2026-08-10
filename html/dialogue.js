/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Browser boundary for the canonical KANGER command dialogue.
 *
 * The Browser deliberately does not parse, abbreviate, normalize or dispatch
 * operator language. Historical command/query entry points are reduced to one
 * raw dialogue transport. Canonical parsing remains owned by kanger-command on
 * the Server; Core prefixes travel through the same raw envelope and are split
 * from ordinary commands only by the shared Server parser.
 *
 * Canonical response presentation is selected only from the server-supplied
 * canonical_intent marker. Destructive confirmation is also server-guided: the
 * Browser can ask the operator and resend the exact same raw line with one
 * boolean confirmation bit, but never decides by itself which command is
 * destructive.
 */
(function (window) {
    'use strict';

    var installed = false;
    var document = window.document;

    function stringValue(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function appendText(parent, value) {
        parent.appendChild(document.createTextNode(stringValue(value)));
    }

    function appendLine(parent, value) {
        var row = document.createElement('div');
        row.style.whiteSpace = 'pre-wrap';
        row.textContent = stringValue(value);
        parent.appendChild(row);
        return row;
    }

    function itemIdentity(item, fallback) {
        if (!item || typeof item !== 'object') {
            return stringValue(fallback);
        }
        if (item.origin !== null && item.origin !== undefined) {
            return stringValue(item.origin);
        }
        if (item.name !== null && item.name !== undefined) {
            return stringValue(item.name);
        }
        return stringValue(fallback);
    }

    function listPresentation(data, label) {
        var fragment = document.createDocumentFragment();
        var list = data && Array.isArray(data.list) ? data.list : [];
        if (label) {
            appendLine(fragment, label);
        }
        if (!list.length) {
            appendLine(fragment, 'No items');
            fragment.__kangerHistoryText = fragment.textContent;
            return fragment;
        }
        for (var i = 0; i < list.length; i++) {
            var item = list[i];
            var prefix = item && item.id !== null && item.id !== undefined
                    ? stringValue(item.id) + ': ' : stringValue(i) + ': ';
            appendLine(fragment, prefix + itemIdentity(item, ''));
        }
        fragment.__kangerHistoryText = fragment.textContent;
        return fragment;
    }

    function basePresentation(data) {
        var fragment = document.createDocumentFragment();
        var list = data && Array.isArray(data.list) ? data.list : [];
        if (!list.length) {
            appendLine(fragment, 'No base statements');
        }
        for (var i = 0; i < list.length; i++) {
            var predicate = list[i] || {};
            var title = predicate.id !== null && predicate.id !== undefined
                    ? stringValue(predicate.id) + ': ' : '';
            title += stringValue(predicate.name || predicate.origin || 'predicate');
            appendLine(fragment, title);
            var statements = Array.isArray(predicate.statements)
                    ? predicate.statements : [];
            for (var j = 0; j < statements.length; j++) {
                var statement = statements[j] || {};
                appendLine(fragment, '    '
                        + stringValue(statement.id) + ': '
                        + stringValue(statement.origin));
            }
        }
        fragment.__kangerHistoryText = fragment.textContent;
        return fragment;
    }

    function valuesPresentation(data) {
        var fragment = document.createDocumentFragment();
        var rows = data && Array.isArray(data.list) ? data.list : [];
        if (!rows.length) {
            appendLine(fragment, 'No values found');
        }
        for (var i = 0; i < rows.length; i++) {
            var cells = Array.isArray(rows[i]) ? rows[i] : [];
            var text = String(i) + ':';
            for (var j = 0; j < cells.length; j++) {
                text += ' ' + stringValue(cells[j].name)
                        + '=' + stringValue(cells[j].value);
            }
            appendLine(fragment, text);
        }
        fragment.__kangerHistoryText = fragment.textContent;
        return fragment;
    }

    function whenPresentation(data) {
        var fragment = document.createDocumentFragment();
        var list = data && Array.isArray(data.list) ? data.list : [];
        if (!list.length) {
            appendLine(fragment, 'No hypotheses');
        }
        for (var i = 0; i < list.length; i++) {
            appendLine(fragment, String(i) + ': '
                    + itemIdentity(list[i], ''));
        }
        fragment.__kangerHistoryText = fragment.textContent;
        return fragment;
    }

    function transactionPresentation(data) {
        var fragment = document.createDocumentFragment();
        var level = data && data.transaction !== null
                && data.transaction !== undefined ? data.transaction : '-';
        appendLine(fragment, 'Transaction level ' + stringValue(level));
        if (data && data.empty === true) {
            appendLine(fragment, 'Current level is empty');
        }
        fragment.__kangerHistoryText = fragment.textContent;
        return fragment;
    }

    function composeSyntax(value) {
        var syntax = stringValue(value);
        var required = syntax.indexOf(' <');
        var optional = syntax.indexOf(' [<');
        var cut = -1;
        if (required >= 0) {
            cut = required;
        }
        if (optional >= 0 && (cut < 0 || optional < cut)) {
            cut = optional;
        }
        return cut < 0 ? syntax : syntax.substring(0, cut) + ' ';
    }

    function helpPresentation(data) {
        var help = data && data.dialogue_help;
        if (!help || help.schema !== 1 || !Array.isArray(help.sections)) {
            return null;
        }
        var fragment = document.createDocumentFragment();
        for (var i = 0; i < help.sections.length; i++) {
            var section = help.sections[i] || {};
            var heading = document.createElement('div');
            heading.style.fontWeight = 'bold';
            heading.style.marginTop = i ? '8px' : '0';
            heading.textContent = stringValue(section.name) + ':';
            fragment.appendChild(heading);
            var commands = Array.isArray(section.commands) ? section.commands : [];
            for (var j = 0; j < commands.length; j++) {
                var command = commands[j] || {};
                var syntaxText = stringValue(command.syntax);
                var composeText = command.compose === null
                        || command.compose === undefined
                        ? composeSyntax(syntaxText) : stringValue(command.compose);
                var row = document.createElement('div');
                var syntax = document.createElement('span');
                syntax.textContent = syntaxText;
                syntax.setAttribute('data-kanger-compose', composeText);
                syntax.title = 'Compose: ' + composeText;
                syntax.style.fontWeight = 'bold';
                row.appendChild(syntax);
                appendText(row, ' — ' + stringValue(command.summary));
                fragment.appendChild(row);
            }
        }
        fragment.__kangerHistoryText = fragment.textContent;
        return fragment;
    }

    function presentationFor(data) {
        if (!data || typeof data !== 'object') {
            return null;
        }
        var intent = stringValue(data.canonical_intent);
        if (intent === 'HELP') {
            return helpPresentation(data);
        }
        if (intent === 'RULE_STATUS' || intent === 'RULE_SHOW'
                || intent === 'RULE_ALL' || intent === 'RULE_PRODUCED'
                || intent === 'RULE_LEVEL' || intent === 'RULE_TREE') {
            return listPresentation(data, 'Rules');
        }
        if (intent === 'FUNCTIONS' || intent === 'FUNCTION_SHOW'
                || intent === 'FUNCTION_SOURCE') {
            return listPresentation(data, 'Functions');
        }
        if (intent === 'BASE_STATUS') {
            return basePresentation(data);
        }
        if (intent === 'BASE_PREDICATES' || intent === 'BASE_PREDICATE'
                || intent === 'BASE_TREE') {
            return listPresentation(data, 'Base');
        }
        if (intent === 'VALUES' || intent === 'VALUES_ORDER') {
            return valuesPresentation(data);
        }
        if (intent === 'SOLUTIONS' || intent === 'SOLUTION_SHOW'
                || intent === 'SOLUTION_TREE') {
            return listPresentation(data, 'Solutions');
        }
        if (intent === 'WHEN_STATUS') {
            return whenPresentation(data);
        }
        if (intent === 'TX_STATUS' || intent === 'TX_START'
                || intent === 'TX_COMMIT' || intent === 'TX_ROLLBACK') {
            return transactionPresentation(data);
        }
        return null;
    }

    function confirmationRequired(data) {
        return !!(data && data.result === 'confirmation_required'
                && data.confirmation && data.confirmation.schema === 1);
    }

    function requestConfirmation(prompt, callback) {
        var previous = document.getElementById('kanger-confirmation-overlay');
        if (previous && previous.parentNode) {
            previous.parentNode.removeChild(previous);
        }

        var overlay = document.createElement('div');
        overlay.id = 'kanger-confirmation-overlay';
        overlay.setAttribute('role', 'presentation');
        overlay.style.position = 'fixed';
        overlay.style.left = '0';
        overlay.style.top = '0';
        overlay.style.right = '0';
        overlay.style.bottom = '0';
        overlay.style.zIndex = '2147483647';
        overlay.style.display = 'flex';
        overlay.style.alignItems = 'center';
        overlay.style.justifyContent = 'center';
        overlay.style.background = 'rgba(20, 25, 30, 0.32)';

        var dialog = document.createElement('div');
        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-modal', 'true');
        dialog.setAttribute('aria-label', 'Confirm operation');
        dialog.style.boxSizing = 'border-box';
        dialog.style.minWidth = '280px';
        dialog.style.maxWidth = '520px';
        dialog.style.padding = '16px';
        dialog.style.border = '1px solid #9da6ae';
        dialog.style.background = '#fff';
        dialog.style.boxShadow = '0 8px 28px rgba(0,0,0,.28)';
        dialog.style.fontFamily = 'helvetica, sans-serif';
        dialog.style.color = '#20262d';

        var message = document.createElement('div');
        message.textContent = stringValue(prompt || 'Confirm operation?');
        message.style.marginBottom = '14px';
        message.style.whiteSpace = 'pre-wrap';
        dialog.appendChild(message);

        var actions = document.createElement('div');
        actions.style.display = 'flex';
        actions.style.justifyContent = 'flex-end';
        actions.style.gap = '8px';

        var cancel = document.createElement('button');
        cancel.type = 'button';
        cancel.textContent = 'Cancel';
        var confirm = document.createElement('button');
        confirm.type = 'button';
        confirm.textContent = 'Confirm';
        confirm.style.fontWeight = 'bold';
        actions.appendChild(cancel);
        actions.appendChild(confirm);
        dialog.appendChild(actions);
        overlay.appendChild(dialog);

        var finished = false;
        function finish(accepted) {
            if (finished) {
                return;
            }
            finished = true;
            document.removeEventListener('keydown', onKeyDown, true);
            if (overlay.parentNode) {
                overlay.parentNode.removeChild(overlay);
            }
            callback(accepted === true);
        }

        function onKeyDown(event) {
            if (event.key === 'Escape' || event.keyCode === 27) {
                event.preventDefault();
                finish(false);
            }
        }

        cancel.onclick = function () {
            finish(false);
        };
        confirm.onclick = function () {
            finish(true);
        };
        document.addEventListener('keydown', onKeyDown, true);
        document.body.appendChild(overlay);
        confirm.focus();
    }

    function dispatch(line, callback, confirmed) {
        var raw = stringValue(line);
        var parameters = {
            token: window.token,
            line: raw
        };
        if (confirmed === true) {
            parameters.confirmed = true;
        }
        return window.post({
            context: 'dialogue',
            parameters: parameters
        }, function (data) {
            if (confirmationRequired(data)) {
                var prompt = stringValue(data.confirmation.prompt
                        || data.description || 'Confirm operation?');
                requestConfirmation(prompt, function (accepted) {
                    if (accepted) {
                        window.setTimeout(function () {
                            dispatch(raw, callback, true);
                        }, 0);
                    } else {
                        var cancelled = {
                            result: 'OK',
                            description: 'Cancelled'
                        };
                        if (typeof window.logResponse === 'function') {
                            window.logResponse(cancelled, 'Cancelled');
                        }
                        if (typeof callback === 'function') {
                            callback(cancelled);
                        }
                    }
                });
                return;
            }

            var presentation = presentationFor(data);
            if (typeof window.refreshScreen === 'function') {
                window.refreshScreen(data, presentation);
            } else if (typeof window.logResponse === 'function') {
                window.logResponse(data, presentation);
            }
            if (typeof window.showTransactionLevel === 'function') {
                window.showTransactionLevel(data);
            }
            if (typeof callback === 'function') {
                callback(data);
            }
        });
    }

    function install() {
        if (installed) {
            return;
        }
        installed = true;
        if (!window.KANGER_ERROR_BOUNDARY
                || !window.KANGER_ERROR_BOUNDARY.installed) {
            throw new Error('KANGER dialogue transport requires the error boundary');
        }
        if (typeof window.post !== 'function') {
            throw new Error('KANGER dialogue transport requires Browser transport');
        }

        window.command = dispatch;
        window.query = dispatch;
        window.KANGER_DIALOGUE_TRANSPORT = Object.freeze({
            version: 2,
            installed: true,
            dispatch: dispatch
        });
    }

    function observeErrorBoundary() {
        var boundary = window.KANGER_ERROR_BOUNDARY;
        if (boundary && boundary.installed) {
            install();
            return;
        }
        var observed = boundary;
        Object.defineProperty(window, 'KANGER_ERROR_BOUNDARY', {
            configurable: true,
            enumerable: true,
            get: function () {
                return observed;
            },
            set: function (value) {
                observed = value;
                if (value && value.installed) {
                    install();
                }
            }
        });
    }

    observeErrorBoundary();
}(window));

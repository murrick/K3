from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, got {count}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "kanger/src/org/kanger/factory/RuleFactory.java",
    '''        if (existing != null) {
            if (existing.getId() != r.getId()) {
                ((Rule) r).setDeleted(true, mind);
                if (primary && isGenerated(existing)) {
                    existing = promotePrimary(existing);
                }
                if (existing.isDeleted(mind)) {
                    ((Rule) existing).setDeleted(false, mind);
                    action = true;
                }
            }
            return existing;
''',
    '''        if (existing != null) {
            if (existing.getId() != r.getId()) {
                ((Rule) r).setDeleted(true, mind);
                if (primary && isGenerated(existing)) {
                    existing = promotePrimary(existing);
                }
            }
            if (existing.isDeleted(mind)) {
                ((Rule) existing).setDeleted(false, mind);
                action = true;
            }
            return existing;
''')

Path("kanger-server/src/org/kanger/DeclarativeSourceBoundary.java").write_text('''/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

/** Enforces the declarative-only contract of Browser Editor source. */
final class DeclarativeSourceBoundary {
    private DeclarativeSourceBoundary() {
    }

    static String rejection(String source) {
        return containsQueryStatement(source)
                ? "Editor source cannot contain query statements"
                : null;
    }

    private static boolean containsQueryStatement(String source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        boolean statementStart = true;
        boolean lineComment = false;
        boolean blockComment = false;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < source.length(); ++i) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : 0;

            if (lineComment) {
                if (ch == '\\n' || ch == '\\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    blockComment = false;
                    ++i;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }

            if (ch == '/' && next == '/') {
                lineComment = true;
                ++i;
                continue;
            }
            if (ch == '/' && next == '*') {
                blockComment = true;
                ++i;
                continue;
            }
            if (ch == '\\'' || ch == '"') {
                quote = ch;
                statementStart = false;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (statementStart && ch == '?') {
                return true;
            }
            if (ch == ';') {
                statementStart = true;
            } else {
                statementStart = false;
            }
        }
        return false;
    }
}
''')

replace_once(
    "kanger-server/src/org/kanger/RootCurrentLevelSourceReplacement.java",
    '''        if (root.getTransactionLevel() != 0 || root.getNext() != null) {
            throw new IllegalArgumentException(
                    "Root source replacement requires explicit transaction level U0");
        }

        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(root)) {
''',
    '''        if (root.getTransactionLevel() != 0 || root.getNext() != null) {
            throw new IllegalArgumentException(
                    "Root source replacement requires explicit transaction level U0");
        }
        String boundaryRejection = DeclarativeSourceBoundary.rejection(exactSource);
        if (boundaryRejection != null) {
            return new Outcome(false, boundaryRejection, root);
        }

        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(root)) {
''')

replace_once(
    "kanger-server/src/org/kanger/NestedCurrentLevelSourceReplacement.java",
    '''        if (current.getTransactionLevel() <= 0 || current.getNext() == null) {
            throw new IllegalArgumentException(
                    "Nested source replacement requires transaction level above U0");
        }

        Mind parent = (Mind) current.getNext();
''',
    '''        if (current.getTransactionLevel() <= 0 || current.getNext() == null) {
            throw new IllegalArgumentException(
                    "Nested source replacement requires transaction level above U0");
        }
        String boundaryRejection = DeclarativeSourceBoundary.rejection(exactSource);
        if (boundaryRejection != null) {
            return new Outcome(false, boundaryRejection, current);
        }

        Mind parent = (Mind) current.getNext();
''')

replace_once(
    "kanger-server/test/org/kanger/Soak3708ConvergenceContractTest.java",
    '''        boolean found = false;
        for (CommandRegistry.Definition definition : CommandRegistry.definitions()) {
            if (definition.getIntent() == CommandIntent.RULE_ALL
                    && "rules".equals(definition.getSyntax())) {
                found = true;
                break;
            }
        }

        assertTrue(found, "Help metadata must expose the executable rules alias");
        assertTrue(new CommandHelpRenderer().render().contains("  rules\\n"));
''',
    '''        String help = new CommandHelpRenderer().render();
        assertTrue(help.contains("rule/rules family spellings are synonymous"),
                "Help must disclose the executable plural family spelling");
''')

replace_once(
    "kanger-server/test/org/kanger/PublicAuthUiContractTest.java",
    '        assertTrue(workspace.contains("repository_state"));\n',
    '        assertFalse(workspace.contains("repository_state"));\n')

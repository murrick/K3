/*
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
                if (ch == '\n' || ch == '\r') {
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
                } else if (ch == '\\') {
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
            if (ch == '\'' || ch == '"') {
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

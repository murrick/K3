/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeclarativeSourceBoundaryTest {

    @Test
    void rejectsQueryStatementsAtAnyStatementBoundary() {
        assertNotNull(DeclarativeSourceBoundary.rejection("?x;"));
        assertNotNull(DeclarativeSourceBoundary.rejection("!base;\n  ?x;"));
        assertNotNull(DeclarativeSourceBoundary.rejection("/* prefix */ ?x;"));
        assertNotNull(DeclarativeSourceBoundary.rejection("!base;;\n?x;"));
    }

    @Test
    void ignoresQuestionMarksInsideCommentsAndQuotedText() {
        assertNull(DeclarativeSourceBoundary.rejection(
                "// ? not a statement\n!base;"));
        assertNull(DeclarativeSourceBoundary.rejection(
                "/* ? not a statement */\n!base;"));
        assertNull(DeclarativeSourceBoundary.rejection(
                "!text(\"?\");"));
        assertNull(DeclarativeSourceBoundary.rejection(
                "!text('?');"));
    }
}

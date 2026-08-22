/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.compiler.Leaf;
import org.kanger.compiler.Parser;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.SourceSpan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbsoluteParseSourcePositionTest {

    @Test
    void lexicalFailureUsesCallerSourceOffset() {
        ParseErrorException failure = assertThrows(
                ParseErrorException.class,
                () -> Parser.nextToken("\"unterminated", null, 11));

        assertEquals("Unclosed quotes", failure.getMessage());
        assertEquals(new SourceSpan(11, 0), failure.getSourceSpan());
    }

    @Test
    void parsedLeafUsesCallerSourceOffset() throws Exception {
        Leaf root = Parser.parse("alpha", 23);

        assertEquals(23, root.getPos());
    }

    @Test
    void functionalBlockFailurePointsAtOffendingToken() {
        ParseErrorException failure = assertThrows(
                ParseErrorException.class,
                () -> Parser.parse("alpha {x}", 30));

        assertEquals("Unexpected functional block", failure.getMessage());
        assertEquals(new SourceSpan(36, 0), failure.getSourceSpan());
    }
}

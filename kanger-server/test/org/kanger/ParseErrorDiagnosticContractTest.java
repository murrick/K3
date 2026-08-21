/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.compiler.Parser;
import org.kanger.enums.ParseError;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.SourceLocatedFailure;
import org.kanger.exception.SourceSpan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseErrorDiagnosticContractTest {

    @Test
    void structuredParseFailureCarriesMachineCodeMessageAndSpan() {
        ParseErrorException failure = new ParseErrorException(
                7, 3, "Unexpected operation");

        assertTrue(failure instanceof SourceLocatedFailure);
        assertEquals("parse_error", failure.getFailureCode());
        assertEquals("Unexpected operation", failure.getMessage());
        assertEquals(new SourceSpan(7, 3), failure.getSourceSpan());
        assertEquals(10, failure.getSourceSpan().getEndOffset());
    }

    @Test
    void historicalProducerEncodingIsNormalizedAtConstruction() {
        @SuppressWarnings("deprecation")
        ParseErrorException failure =
                new ParseErrorException("12@Unclosed quotes");

        assertEquals("Unclosed quotes", failure.getMessage());
        assertEquals(new SourceSpan(12, 0), failure.getSourceSpan());
    }

    @Test
    void malformedHistoricalDuplicateVariableEncodingCannotBreakRenderer() {
        @SuppressWarnings("deprecation")
        ParseErrorException failure =
                new ParseErrorException("17Variable x duplicated");

        assertEquals("Variable x duplicated", failure.getMessage());
        assertEquals(new SourceSpan(17, 0), failure.getSourceSpan());
        assertEquals(17, failure.getExceptionPosition());
        assertEquals("Variable x duplicated", failure.getExceptionMessage());
    }

    @Test
    void existingParserFailureAlreadyExposesStructuredLocation() {
        ParseErrorException failure = assertThrows(
                ParseErrorException.class,
                () -> Parser.nextToken("\"unterminated", null));

        assertEquals("parse_error", failure.getFailureCode());
        assertEquals("Unclosed quotes", failure.getMessage());
        assertEquals(new SourceSpan(0, 0), failure.getSourceSpan());
    }

    @Test
    void explicitParserReasonRemainsMachineReadable() {
        ParseErrorException failure =
                new ParseErrorException(4, ParseError.BRACKET);

        assertEquals(ParseError.BRACKET, failure.getCode());
        assertEquals("Right brackets mismatch", failure.getMessage());
        assertEquals(new SourceSpan(4, 0), failure.getSourceSpan());
    }

    @Test
    void sourceSpanRejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new SourceSpan(-1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceSpan(0, -1));
    }
}

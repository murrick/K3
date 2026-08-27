/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.ParseErrorException;
import org.kanger.exception.SourceSpan;

/** Console presentation adapter for structured Core source failures. */
final class ConsoleParseErrorRenderer {

    private ConsoleParseErrorRenderer() {
    }

    static void show(ParseErrorException failure, String source) {
        System.out.println(render(failure, source));
    }

    static String render(ParseErrorException failure, String source) {
        if (failure == null) {
            throw new IllegalArgumentException("failure must not be null");
        }

        StringBuilder output = new StringBuilder();
        output.append("ERROR: ").append(failure.getMessage());

        SourceSpan span = failure.getSourceSpan();
        if (span == null || source == null || source.isEmpty()) {
            return output.toString();
        }

        int offset = span.getOffset();
        if (offset < 0 || offset > source.length()) {
            return output.toString();
        }

        int lineStart = offset;
        while (lineStart > 0) {
            char before = source.charAt(lineStart - 1);
            if (before == '\n' || before == '\r') {
                break;
            }
            --lineStart;
        }

        int lineEnd = offset;
        while (lineEnd < source.length()) {
            char current = source.charAt(lineEnd);
            if (current == '\n' || current == '\r') {
                break;
            }
            ++lineEnd;
        }

        output.append('\n').append(source, lineStart, lineEnd).append('\n');
        for (int index = lineStart; index < offset && index < lineEnd; ++index) {
            output.append(source.charAt(index) == '\t' ? '\t' : ' ');
        }
        output.append('^');

        int rangeEnd = Math.min(lineEnd, offset + span.getLength());
        for (int index = offset + 1; index < rangeEnd; ++index) {
            output.append('~');
        }
        return output.toString();
    }
}

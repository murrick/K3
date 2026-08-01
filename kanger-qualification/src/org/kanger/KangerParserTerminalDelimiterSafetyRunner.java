/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.compiler.Parser;
import org.kanger.compiler.Token;
import org.kanger.enums.Tools;

/** Regression gate for terminal delimiter and exact-end token boundaries. */
public final class KangerParserTerminalDelimiterSafetyRunner {

    private KangerParserTerminalDelimiterSafetyRunner() {
    }

    public static void main(String[] args) throws Exception {
        require(Parser.nextToken(" \t\r\n", null) == null,
                "Delimiter-only input must terminate without charAt past end");

        Token exact = Tools.extractLine("!value;", null);
        require(exact != null, "Expected first exact-end statement");
        require(Tools.extractLine("!value;", exact) == null,
                "Exact-end extraction must terminate cleanly");

        Token trailing = Tools.extractLine("!value;\r\n", null);
        require(trailing != null, "Expected statement before trailing delimiters");
        require(Tools.extractLine("!value;\r\n", trailing) == null,
                "Trailing delimiters must terminate cleanly");

        System.out.println("PARSER_TERMINAL_DELIMITER_OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

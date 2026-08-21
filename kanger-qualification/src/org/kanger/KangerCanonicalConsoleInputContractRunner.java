/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Characterizes the physical-line aggregation contract of CanonicalConsole.
 *
 * <p>This runner deliberately reaches the existing private accept(Scanner)
 * boundary as a black box. Console history/editing may replace the terminal
 * reader, but it must not silently change what constitutes one logical KANGER
 * input operation.</p>
 */
public final class KangerCanonicalConsoleInputContractRunner {

    private KangerCanonicalConsoleInputContractRunner() {
    }

    public static void main(String[] args) throws Exception {
        Method accept = CanonicalConsole.class.getDeclaredMethod("accept", Scanner.class);
        accept.setAccessible(true);

        assertAccept(accept, "help\n", "help", "ordinary command is one line");
        assertAccept(accept, "?\n", "?", "bare program check is one line");
        assertAccept(accept, "?$x p(x);\n", "?$x p(x);",
                "terminated query is one line");

        String nl = System.lineSeparator();
        assertAccept(accept, "?$x p(x)\n;\n", "?$x p(x)" + nl + ";",
                "query continues until semicolon");
        assertAccept(accept, "!p(a)\n;\n", "!p(a)" + nl + ";",
                "insert continues until semicolon");
        assertAccept(accept, "+p(a)\n;\n", "+p(a)" + nl + ";",
                "materialization continues until semicolon");
        assertAccept(accept, "-p(a)\n;\n", "-p(a)" + nl + ";",
                "delete continues until semicolon");
        assertAccept(accept, "/* one\ntwo */\n", "/* one" + nl + "two */",
                "block comment continues until closing delimiter");
        assertAccept(accept, "// one line\n", "// one line",
                "line comment is one line");
        assertAccept(accept, "= first\nsecond\n\n",
                "= first" + nl + "second" + nl,
                "equals form terminates on a blank physical line");

        System.out.println("PASS: Canonical Console logical-input boundary characterized");
    }

    private static void assertAccept(Method accept,
                                     String physicalInput,
                                     String expected,
                                     String label) throws Exception {
        Scanner scanner = new Scanner(new ByteArrayInputStream(
                physicalInput.getBytes(StandardCharsets.UTF_8)), "UTF-8");
        PrintStream previous = System.out;
        ByteArrayOutputStream prompts = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(prompts, true, "UTF-8"));
            String actual;
            try {
                actual = (String) accept.invoke(null, scanner);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw ex;
            }
            if (!expected.equals(actual)) {
                throw new AssertionError(label + ": expected <" + printable(expected)
                        + "> but was <" + printable(actual) + ">");
            }
        } finally {
            System.setOut(previous);
            scanner.close();
        }
    }

    private static String printable(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

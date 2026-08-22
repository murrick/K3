/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.ParseErrorException;
import org.kanger.exception.SourceSpan;

/**
 * Qualifies the logical-input and structured parse-diagnostic boundaries used
 * by the interactive Console reader.
 *
 * <p>Terminal editing/history may change independently, but Enter must keep
 * the historical KANGER aggregation contract: ordinary commands are complete,
 * Core operator forms continue to semicolon, block comments continue to their
 * closing delimiter, and the legacy '=' form terminates on a blank physical
 * line. Parse diagnostics must render against the exact unmodified Core source
 * and must not invent a source location when Core did not provide one.</p>
 */
public final class KangerCanonicalConsoleInputContractRunner {

    private KangerCanonicalConsoleInputContractRunner() {
    }

    public static void main(String[] args) {
        expectComplete("help", "ordinary command is one line");
        expectComplete("?", "bare program check is one line");
        expectComplete("?$x p(x);", "terminated query is one line");
        expectIncomplete("?$x p(x)", "query continues until semicolon");
        expectComplete("?$x p(x)\n;", "multiline query ends at semicolon");
        expectIncomplete("!p(a)", "insert continues until semicolon");
        expectComplete("!p(a)\n;", "multiline insert ends at semicolon");
        expectIncomplete("+p(a)", "materialization continues until semicolon");
        expectComplete("+p(a)\n;", "multiline materialization ends at semicolon");
        expectIncomplete("-p(a)", "delete continues until semicolon");
        expectComplete("-p(a)\n;", "multiline delete ends at semicolon");
        expectIncomplete("/* one", "block comment continues");
        expectComplete("/* one\ntwo */", "block comment closes on delimiter");
        expectComplete("// one line", "line comment is one line");
        expectIncomplete("= first", "equals form needs a blank physical line");
        expectIncomplete("= first\nsecond", "equals form continues on nonblank line");
        expectComplete("= first\nsecond\n", "equals form ends on blank physical line");
        expectComplete("", "empty command remains complete");

        expectRender(
                new ParseErrorException(2, 3, "leading"),
                "  alpha",
                "ERROR: leading\n  alpha\n  ^~~",
                "leading whitespace and source range");
        expectRender(
                new ParseErrorException((SourceSpan) null, "unknown"),
                "  alpha",
                "ERROR: unknown",
                "unknown location must not invent caret");
        expectRender(
                new ParseErrorException(5, "multiline"),
                "one\n\tbad",
                "ERROR: multiline\n\tbad\n\t^",
                "multiline source selects containing line");
        expectRender(
                new ParseErrorException(2, "utf16"),
                "\uD83D\uDE00x",
                "ERROR: utf16\n\uD83D\uDE00x\n  ^",
                "UTF-16 source offsets are preserved");

        System.out.println("PASS: Canonical Console logical-input and parse-diagnostic boundary qualified");
    }

    private static void expectComplete(String line, String label) {
        if (!ConsoleLineInput.isComplete(line)) {
            throw new AssertionError(label + ": expected complete <" + printable(line) + ">");
        }
    }

    private static void expectIncomplete(String line, String label) {
        if (ConsoleLineInput.isComplete(line)) {
            throw new AssertionError(label + ": expected incomplete <" + printable(line) + ">");
        }
    }

    private static void expectRender(ParseErrorException failure,
                                     String source,
                                     String expected,
                                     String label) {
        String actual = ConsoleParseErrorRenderer.render(failure, source);
        if (!expected.equals(actual)) {
            throw new AssertionError(label
                    + ": expected <" + printable(expected)
                    + "> but was <" + printable(actual) + ">");
        }
    }

    private static String printable(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

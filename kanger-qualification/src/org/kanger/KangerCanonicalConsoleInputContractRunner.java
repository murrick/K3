/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Qualifies the logical-input boundary used by the interactive Console reader.
 *
 * <p>Terminal editing/history may change independently, but Enter must keep
 * the historical KANGER aggregation contract: ordinary commands are complete,
 * Core operator forms continue to semicolon, block comments continue to their
 * closing delimiter, and the legacy '=' form terminates on a blank physical
 * line.</p>
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

        System.out.println("PASS: Canonical Console logical-input boundary qualified");
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

    private static String printable(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

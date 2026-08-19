/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

/** Minimal Console probe for overlay hypothesis settlement + optimize + status. */
public final class KangerCompletedHypothesisConsoleProbeRunner {

    private KangerCompletedHypothesisConsoleProbeRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = test() ? 0 : 1;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    public static boolean test() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        IUser user = UserFactory.createUser(
                "autotest-completed-console-" + suffix,
                "autotest-completed-console-" + suffix);
        new UDF().init(user);
        new DB().init(user);
        IMind mind = new Mind(user);

        String script = ""
                + "!@x consolepremise(x) -> consoletarget(x);\n"
                + "?consoletarget(item);\n"
                + "w\n"
                + "quit\n";

        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(script.getBytes("UTF-8")));
            System.setOut(new PrintStream(stdout, true, "UTF-8"));
            System.setErr(new PrintStream(stderr, true, "UTF-8"));
            CanonicalConsole.session(mind, null);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String out = stdout.toString("UTF-8");
        String err = stderr.toString("UTF-8");
        System.out.println("COMPLETED_CONSOLE_PROBE_STDOUT_BEGIN");
        System.out.print(out);
        System.out.println("COMPLETED_CONSOLE_PROBE_STDOUT_END");
        if (!err.isEmpty()) {
            System.out.println("COMPLETED_CONSOLE_PROBE_STDERR_BEGIN");
            System.out.print(err);
            System.out.println("COMPLETED_CONSOLE_PROBE_STDERR_END");
        }

        require(out.contains("Hypothesis list (1)"),
                "minimal Console did not expose one completed hypothesis");
        require(out.contains("000:\t!consolepremise(item);"),
                "minimal Console lost concrete premise hypothesis");
        System.out.println("COMPLETED_HYPOTHESIS_CONSOLE_PROBE_OK");
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

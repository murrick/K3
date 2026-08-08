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

/**
 * Scripted end-to-end qualification for the canonical Java Console boundary.
 *
 * <p>The runner drives the real {@link CanonicalConsole#session(IMind,
 * ShutdownHook)} method through {@code System.in}. It therefore covers the
 * interactive acceptance loop, shared command parsing, Core bypass,
 * Console-local forms, presentation adapters and transaction state changes in
 * one operator session.</p>
 */
public final class KangerCanonicalConsoleRunner {

    private KangerCanonicalConsoleRunner() {
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
        String userName = "autotest-canonical-console-" + suffix;
        IUser user = UserFactory.createUser(userName, userName);
        new UDF().init(user);
        new DB().init(user);
        IMind mind = new Mind(user);

        String script = ""
                + "!consoleconvergence;\n"
                + "r a\n"
                + "v\n"
                + "so\n"
                + "w\n"
                + "st\n"
                + "put \"console test.k\"\n"
                + "get\n"
                + "delete \"console test.k\"\n"
                + "y\n"
                + "get\n"
                + "xplain mode on\n"
                + "xplain mode off\n"
                + "t\n"
                + "t s\n"
                + "t r\n"
                + "s\n"
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

        require(out.contains("consoleconvergence"),
                "Core line did not reach canonical Console execution");
        require(out.contains("No values found"),
                "values prefix did not reach canonical Values binding");
        require(out.contains("No solutions found"),
                "solution-family prefix did not reach canonical Solutions binding");
        require(out.contains("No hypothesis found"),
                "when prefix did not reach canonical hypothesis binding");
        require(out.contains("Current storage: none"),
                "storage-family prefix did not reach canonical storage status");

        require(out.contains("Source file console test.k saved."),
                "quoted source put did not execute");
        require(out.contains("console test.k"),
                "bare source listing did not expose the saved source name");
        require(out.contains("Source file console test.k deleted."),
                "canonical source delete did not execute");
        require(!err.contains("MISSING_ARGUMENT"),
                "bare source-list forms leaked into canonical missing-argument rejection");

        require(out.contains("Xplain runtime mode: ON"),
                "Console-local xplain mode on did not execute");
        require(out.contains("Xplain runtime mode: OFF"),
                "Console-local xplain mode off did not execute");

        require(out.contains("Transaction level 0"),
                "canonical transaction status did not report root level");
        require(out.contains("Transaction level 1"),
                "minimum-prefix transaction start did not create level 1");
        require(out.contains("SUCCESS: Transaction rolled back"),
                "minimum-prefix transaction rollback did not execute");

        require(err.contains("AMBIGUOUS_PREFIX"),
                "ambiguous top-level prefix 's' was not rejected by shared parser");
        require(out.contains("KANGER III Session closed"),
                "canonical Console did not close the session cleanly");

        System.out.println("Canonical Console interactive conformance passed");
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

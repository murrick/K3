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
 * Console-local forms, presentation adapters, hypothesis acceptance and
 * storage/transaction state changes in one operator session.</p>
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
        String storageName = "canonical_console_" + suffix;
        IUser user = UserFactory.createUser(userName, userName);
        new UDF().init(user);
        new DB().init(user);
        IMind mind = new Mind(user);

        String script = ""
                + "!consoleconvergence;\n"
                + "!@x consolepremise(x) -> consoletarget(x);\n"
                + "?consoletarget(item);\n"
                + "w\n"
                + "w a 0\n"
                + "?consoletarget(item);\n"
                + "r a\n"
                + "v\n"
                + "so\n"
                + "st\n"
                + "st u " + storageName + "\n"
                + "t\n"
                + "t st\n"
                + "!consolecommitted;\n"
                + "t c\n"
                + "?consolecommitted;\n"
                + "st\n"
                + "st c\n"
                + "st\n"
                + "put \"console test.k\"\n"
                + "put \"console test.k\"\n"
                + "n\n"
                + "put \"console test.k\"\n"
                + "y\n"
                + "get\n"
                + "delete \"console test.k\"\n"
                + "y\n"
                + "get\n"
                + "xplain mode on\n"
                + "xplain mode off\n"
                + "t\n"
                + "t st\n"
                + "t r\n"
                + "!!eating(Cat, Mouse);\n"
                + "s\n"
                // Reindex deliberately runs last: it owns a storage-lifecycle
                // reconstruction boundary and must not invalidate the stateful
                // source/transaction assertions that precede it.
                + "st r " + storageName + "\n"
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
        require(out.contains("Hypothesis list (1)"),
                "canonical when status did not expose the optimized hypothesis rowset");
        require(out.contains("000:\t!consolepremise(item);"),
                "canonical when status did not use zero-based row addressing");
        require(out.contains("Statement: !consolepremise(item);"),
                "when accept 0 did not resolve the first optimized hypothesis row");
        require(out.contains("consoletarget(item)"),
                "accepted hypothesis did not participate in subsequent Core inference");

        require(out.contains("No values found"),
                "values prefix did not reach canonical Values binding");
        require(out.contains("Solution"),
                "solution-family prefix did not reach canonical Solutions binding");
        require(out.contains("Current storage: none"),
                "storage-family prefix did not reach canonical storage status");

        require(out.contains("Current storage: " + storageName),
                "canonical storage use did not bind the requested storage");
        require(out.contains("SUCCESS: Transaction committed"),
                "canonical transaction commit did not commit storage baseline insertion");
        require(out.contains("Database " + storageName + " closed"),
                "canonical storage close did not execute");

        require(out.contains("Source file console test.k saved."),
                "quoted source put did not execute");
        require(out.contains("Overwrite source file console test.k? [y/N]?"),
                "existing source overwrite did not request explicit confirmation");
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
        require(!out.contains("Rollback transaction"),
                "transaction rollback unexpectedly requested confirmation");

        require(err.contains("Unexpected '!' after Core statement operator"),
                "double statement prefix was not rejected by shared parser in Console");
        require(err.contains("AMBIGUOUS_PREFIX"),
                "ambiguous top-level prefix 's' was not rejected by shared parser");

        require(out.contains("Database reindexed"),
                "storage reindex did not execute immediately");
        require(!out.contains("Reindex storage " + storageName + "?"),
                "storage reindex unexpectedly requested confirmation");
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

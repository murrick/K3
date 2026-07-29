/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.test.KangerTest;
import org.kanger.udf.UDF;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Headless D1/D2/D3 diagnostic runner.
 *
 * <p>Without arguments it creates an isolated KANGER home, verifies the
 * historical set_03_01 through a real temporary database and then checks
 * persistence after explicit close, normal JVM shutdown hook and a hard halt
 * without close/flush.</p>
 */
public final class KangerDiagnosticRunner {

    private static final String LOGIN = "diagnostics";
    private static final String PASSWORD = "diagnostics";

    private KangerDiagnosticRunner() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("kanger.diagnostics", "true");
        System.setProperty("kanger.diagnostics.timeout.ms",
                System.getProperty("kanger.diagnostics.timeout.ms", "5000"));

        if (args.length >= 2) {
            System.setProperty("user.home", Paths.get(args[1]).toAbsolutePath().toString());
        }

        if (args.length == 0) {
            runParent();
            return;
        }

        String mode = args[0];
        if ("set0301".equals(mode)) {
            runSet0301();
        } else if ("write-close".equals(mode)) {
            writeMarker("close", true, true, false);
        } else if ("write-shutdown".equals(mode)) {
            writeMarker("shutdown", false, false, true);
        } else if ("write-halt".equals(mode)) {
            writeMarker("halt", false, false, false);
            Runtime.getRuntime().halt(0);
        } else if (mode.startsWith("read-")) {
            readMarker(mode.substring("read-".length()));
        } else {
            throw new IllegalArgumentException("Unknown diagnostic mode: " + mode);
        }
    }

    private static void runParent() throws Exception {
        Path home = Files.createTempDirectory("kanger-diagnostics-home-");
        System.out.println("KANGER diagnostics home: " + home.toAbsolutePath());

        boolean testOk = runChild(home, "set0301", 30L) == 0;
        System.out.println("D2 set_03_01 with storage: " + (testOk ? "PASS" : "FAIL/HANG"));

        boolean closeOk = runScenario(home, "close");
        boolean shutdownOk = runScenario(home, "shutdown");
        boolean haltOk = runScenario(home, "halt");

        System.out.println("\n========== D3 PERSISTENCE MATRIX ==========");
        System.out.println("explicit closeStorage: " + closeOk);
        System.out.println("normal JVM shutdown hook: " + shutdownOk);
        System.out.println("hard halt without close/flush: " + haltOk);
        System.out.println("===========================================");

        if (!testOk || !closeOk || !shutdownOk) {
            System.exit(1);
        }
    }

    private static boolean runScenario(Path home, String scenario) throws Exception {
        int write = runChild(home, "write-" + scenario, 30L);
        int read = write == 0 ? runChild(home, "read-" + scenario, 30L) : write;
        boolean result = write == 0 && read == 0;
        System.out.println("D3 " + scenario + ": " + result);
        return result;
    }

    private static int runChild(Path home, String mode, long timeoutSeconds) throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(
                java,
                "-Dkanger.diagnostics=true",
                "-Dkanger.diagnostics.timeout.ms=5000",
                "-cp",
                System.getProperty("java.class.path"),
                KangerDiagnosticRunner.class.getName(),
                mode,
                home.toAbsolutePath().toString());
        builder.inheritIO();
        Process process = builder.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            System.err.println("Diagnostic child timed out: " + mode);
            process.destroyForcibly();
            process.waitFor();
            return 124;
        }
        return process.exitValue();
    }

    private static void runSet0301() throws Exception {
        IUser user = openUser();
        new UDF().init(user);
        new DB().init(user);
        IMind mind = new Mind(user);
        mind = mind.useStorage("diagnostics/set0301");
        mind = mind.clearWorkspace();
        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after clear"));

        boolean compiled = mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        if (!compiled) {
            throw new IllegalStateException("set_03_01 setup compilation rejected");
        }
        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after compile"));
        Diagnostics.resetStorageCounters(mind);

        Boolean result;
        try (Diagnostics.Watchdog watchdog = Diagnostics.watch("set_03_01 direct query", mind)) {
            result = mind.query("?$x @y a(x,y);");
        }
        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after query"));
        mind.closeStorage();
        System.exit(Boolean.FALSE.equals(result) ? 0 : 1);
    }

    private static void writeMarker(String scenario,
                                    boolean explicitClose,
                                    boolean explicitFlush,
                                    boolean installShutdownHook) throws Exception {
        IUser user = openUser();
        new UDF().init(user);
        new DB().init(user);
        if (installShutdownHook) {
            Runtime.getRuntime().addShutdownHook(new ShutdownHook(user));
        }

        IMind mind = new Mind(user);
        mind = mind.useStorage("diagnostics/persistence/" + scenario);
        mind = mind.clearWorkspace();
        boolean compiled = mind.compile("!persistence_marker(\"" + scenario + "\");");
        if (!compiled) {
            throw new IllegalStateException("Marker compilation rejected: " + scenario);
        }
        System.out.println(Diagnostics.snapshot(mind, "written marker " + scenario));

        if (explicitFlush) {
            ((User) user).flush();
        }
        if (explicitClose) {
            mind.closeStorage();
        }
    }

    private static void readMarker(String scenario) throws Exception {
        IUser user = openUser();
        new UDF().init(user);
        new DB().init(user);
        IMind mind = new Mind(user);
        mind = mind.useStorage("diagnostics/persistence/" + scenario);
        Boolean result = mind.query("?persistence_marker(\"" + scenario + "\");");
        System.out.println(Diagnostics.snapshot(mind, "read marker " + scenario));
        mind.closeStorage();
        System.exit(Boolean.TRUE.equals(result) ? 0 : 1);
    }

    private static IUser openUser() throws Exception {
        try {
            return UserFactory.createUser(LOGIN, PASSWORD);
        } catch (AuthenticationErrorException exists) {
            return UserFactory.getUser(LOGIN, PASSWORD);
        }
    }
}

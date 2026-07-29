/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.test.KangerTest;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Headless entry point for the existing KangerTest regression corpus.
 *
 * <p>This class deliberately reuses the normal KANGER bootstrap path and the
 * existing reflection-based test harness. It adds only process-level isolation
 * and a reliable exit status for scripts and CI.</p>
 */
public final class KangerTestRunner {

    private KangerTestRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            String prefix = args.length == 0 ? "set_" : args[0];
            Path testHome = createTestHome();
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            System.out.println("KANGER test home: " + testHome.toAbsolutePath());
            System.out.println("KANGER test prefix: " + prefix);

            IUser user = UserFactory.createUser("autotest", "autotest");
            new UDF().init(user);
            new DB().init(user);

            IMind mind = new Mind(user);
            boolean success = KangerTest.test(mind, prefix);
            exitCode = success ? 0 : 1;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }

        System.exit(exitCode);
    }

    private static Path createTestHome() throws Exception {
        String configuredHome = System.getProperty("kanger.test.home");
        if (configuredHome == null || configuredHome.trim().isEmpty()) {
            return Files.createTempDirectory("kanger-test-home-");
        }

        Path path = Paths.get(configuredHome);
        Files.createDirectories(path);
        return path;
    }
}

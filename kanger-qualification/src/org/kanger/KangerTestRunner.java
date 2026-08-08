/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.test.KangerC1PromotionTest;
import org.kanger.test.KangerC3BindingTest;
import org.kanger.test.KangerC4IntervalTest;
import org.kanger.test.KangerStabilizationTest;
import org.kanger.test.KangerTest;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Headless entry point for the historical and stabilization test corpora.
 *
 * <p>This class deliberately reuses the normal KANGER bootstrap path. It adds
 * only process-level isolation and a reliable exit status for scripts and CI.</p>
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

            IUser legacyUser = createUser("autotest");
            IMind legacyMind = new Mind(legacyUser);
            boolean legacySuccess = KangerTest.test(legacyMind, prefix);

            IUser stabilizationUser = createUser("autotest-stabilization");
            IMind stabilizationMind = new Mind(stabilizationUser);
            boolean stabilizationSuccess = KangerStabilizationTest.test(stabilizationMind, prefix);

            IUser c1User = createUser("autotest-c1-promotion");
            IMind c1Mind = new Mind(c1User);
            boolean c1Success = KangerC1PromotionTest.test(c1Mind, prefix);

            IUser c4User = createUser("autotest-c4-interval");
            IMind c4Mind = new Mind(c4User);
            boolean c4Success = KangerC4IntervalTest.test(c4Mind, prefix);

            IUser c3User = createUser("autotest-c3-binding");
            IMind c3Mind = new Mind(c3User);
            boolean c3Success = KangerC3BindingTest.test(c3Mind, prefix);

            boolean consoleLifecycleSuccess =
                    KangerConsoleLifecycleBindingRunner.test();
            boolean canonicalConsoleSuccess =
                    KangerCanonicalConsoleRunner.test();

            exitCode = legacySuccess
                    && stabilizationSuccess
                    && c1Success
                    && c4Success
                    && c3Success
                    && consoleLifecycleSuccess
                    && canonicalConsoleSuccess ? 0 : 1;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }

        System.exit(exitCode);
    }

    private static IUser createUser(String name) throws Exception {
        IUser user = UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return user;
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

/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.test.KangerTest;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Repeats the historical four-thread set_08_02 scenario with persistent DUMB
 * storage. The test is intentionally repeated because the original defect
 * depended on a child query copying the parent RuleCandidateIndex while a
 * concurrent child commit was merging IDs into it.
 */
public final class KangerRuleCandidateConcurrencyRunner {

    private static final int DEFAULT_ITERATIONS = 12;

    private KangerRuleCandidateConcurrencyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            int iterations = Integer.getInteger(
                    "kanger.rule.candidate.concurrency.iterations",
                    DEFAULT_ITERATIONS);
            if (iterations <= 0) {
                throw new IllegalArgumentException("iterations must be positive");
            }

            Path home = Files.createTempDirectory("kanger-rule-candidate-concurrency-");
            System.setProperty("user.home", home.toAbsolutePath().toString());
            System.out.println("Rule candidate concurrency home: " + home.toAbsolutePath());

            for (int iteration = 1; iteration <= iterations; ++iteration) {
                String userName = "rule-candidate-concurrency-" + iteration;
                IUser user = UserFactory.createUser(userName, userName);
                new UDF().init(user);
                new DB().init(user);

                IMind mind = new Mind(user);
                mind = mind.useStorage("data/concurrency-source-" + iteration);
                boolean success = KangerTest.test(mind, "set_08_02");
                if (!success) {
                    throw new AssertionError("set_08_02 failed at iteration " + iteration);
                }
                System.out.println("RULE_CANDIDATE_CONCURRENCY_PASS "
                        + iteration + "/" + iterations);
            }

            System.out.println("RULE_CANDIDATE_CONCURRENCY_OK iterations=" + iterations);
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }
}

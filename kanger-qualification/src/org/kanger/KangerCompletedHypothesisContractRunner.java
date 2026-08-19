/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Production-shaped qualification for completed hypothesis optimization.
 *
 * <p>This runner freezes the two historical hypothesis-list contracts whose
 * expected rowsets legitimately change when abstractive formation is restored,
 * probes the canonical Console premise/target shape, and verifies that
 * parameterized query replay preserves and replaces external values.</p>
 */
public final class KangerCompletedHypothesisContractRunner {

    private KangerCompletedHypothesisContractRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            test();
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    public static boolean test() throws Exception {
        if (System.getProperty("user.home") == null) {
            System.setProperty("user.home",
                    Files.createTempDirectory("kanger-completed-contract-")
                            .toAbsolutePath().toString());
        }

        IUser user = UserFactory.createUser(
                "autotest-completed-hypothesis-contract",
                "autotest-completed-hypothesis-contract");
        new UDF().init(user);
        new DB().init(user);

        testHistoricalMale(user);
        testHistoricalConjunction(user);
        probeCanonicalConsoleShape(user);
        testExternalReplay(user);

        System.out.println("COMPLETED_HYPOTHESIS_CONTRACT_OK");
        return true;
    }

    private static void testHistoricalMale(IUser user) throws Exception {
        Mind mind = historicalMind(user);
        Set<String> actual = optimized(mind, "?male(Tom);", null);
        Set<String> expected = set(
                "?son(Tom,John);",
                "!mother(Tom,Sarah);",
                "!mother(Tom,John);",
                "!daughter(Tom,Sarah);",
                "!daughter(Tom,John);",
                "!female(Tom);",
                "?daughter(Tom,John);",
                "?female(Tom);",
                "!father(Tom,Sarah);",
                "!father(Tom,John);",
                "!son(Tom,Sarah);",
                "!son(Tom,John);",
                "!$y mother(Tom,y);",
                "!$y father(Tom,y);");
        requireEqual("historical male(Tom)", expected, actual);
        System.out.println("COMPLETED_CONTRACT historical-male count="
                + actual.size());
    }

    private static void testHistoricalConjunction(IUser user) throws Exception {
        Mind mind = historicalMind(user);
        Set<String> actual = optimized(
                mind, "?$x male(x) && age(x,12);", null);
        Set<String> expected = set(
                "?daughter(Tom,John);",
                "!male(Tom);",
                "?female(Tom);",
                "!father(Tom,Sarah);",
                "!father(Tom,John);",
                "!son(Tom,Sarah);",
                "!son(Tom,John);",
                "!$y father(Tom,y);");
        requireEqual("historical conjunction", expected, actual);
        System.out.println("COMPLETED_CONTRACT historical-conjunction count="
                + actual.size());
    }

    private static void probeCanonicalConsoleShape(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        require(mind.compile(
                "!@x consolepremise(x) -> consoletarget(x);"),
                "console probe program rejected");
        Set<String> actual = optimized(
                mind, "?consoletarget(item);", null);
        require(actual.contains("!consolepremise(item);"),
                "console probe lost concrete premise hypothesis: " + actual);
        System.out.println("COMPLETED_CONTRACT console count=" + actual.size()
                + " hypotheses=" + actual);
    }

    private static void testExternalReplay(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        require(mind.compile("!@x seed(x) -> target(x);"),
                "external replay program rejected");

        Set<String> first = optimized(
                mind, "?target(?);", new Object[]{"item"});
        require(first.contains("!seed(item);"),
                "external replay lost first canonical value: " + first);

        Set<String> second = optimized(
                mind, "?target(?);", new Object[]{"other"});
        require(second.contains("!seed(other);"),
                "external replay lost replacement canonical value: " + second);
        require(!second.contains("!seed(item);"),
                "external replay reused stale value: " + second);

        System.out.println("COMPLETED_CONTRACT external first=" + first
                + " second=" + second);
    }

    private static Mind historicalMind(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        require(mind.compile(
                "!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"),
                "historical hypothesis program rejected");
        return mind;
    }

    private static Set<String> optimized(Mind mind,
                                         String query,
                                         Object[] externals) throws Exception {
        Boolean result = externals == null
                ? mind.query(query)
                : mind.query(query, externals);
        require(result == null,
                "expected WHO KNOWS for " + query + ", got " + result);
        mind.optimizeHypothesis();
        Set<String> values = new LinkedHashSet<String>();
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            values.add(((Hypothesis) hypothesis).toString(mind));
        }
        return values;
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }

    private static void requireEqual(String label,
                                     Set<String> expected,
                                     Set<String> actual) {
        if (!expected.equals(actual)) {
            Set<String> missing = new LinkedHashSet<String>(expected);
            missing.removeAll(actual);
            Set<String> extra = new LinkedHashSet<String>(actual);
            extra.removeAll(expected);
            throw new IllegalStateException(label + " mismatch: missing="
                    + missing + ", extra=" + extra + ", actual=" + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

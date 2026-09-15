/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.*;

/** Qualifies existing factory signature topology against visible canonical rules.
 * Does not introduce a long-lived adjacency implementation or scheduling changes.
 */
public final class LatentSubstitutionLifecycleRunner {
    private static final Set<Long> seenPredicates = new TreeSet<>();
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-lifecycle-").toString());
        User user = (User) UserFactory.createUser("latent-life", "latent-life");
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        try {
            check(root, "empty");
            require(root.compile("!@x p(x) -> q(x); !p(1);"), "compile");
            check(root, "compiled-and-produced");
            require(Boolean.TRUE.equals(root.query("?q(1);")), "production");
            check(root, "query-finished");
            Mind child = new Mind(root);
            require(child.compile("!p(2); !@x q(x) -> r(x);"), "child compile");
            check(child, "child-produced");
            check(root, "parent-during-child");
            require(!Boolean.TRUE.equals(root.query("?p(2);")), "child leaked into parent");
            root.release(child);
            check(root, "child-rolled-back");
            child = new Mind(root);
            require(child.compile("!p(3);"), "committed child compile");
            require(root.commit(child), "child commit");
            check(root, "child-committed");
            require(Boolean.TRUE.equals(root.query("?q(3);")), "committed production lost");
            require(!Boolean.TRUE.equals(root.query("!~p(3);")), "collision accepted");
            check(root, "rejected-collision");
            root.query("-p(3);");
            check(root, "deletion");

            root = (Mind) root.useStorage("latent-a");
            user.setCurrentMind(root);
            require(root.compile("!@x stored(x) -> derived(x); !stored(7);"), "stored compile");
            check(root, "storage-compiled");
            root = (Mind) root.closeStorage();
            user.setCurrentMind(root);
            check(root, "storage-closed");
            root = (Mind) root.useStorage("latent-a");
            user.setCurrentMind(root);
            check(root, "storage-reopened");
            require(Boolean.TRUE.equals(root.query("?derived(7);")), "reopen lost production");
            root = (Mind) root.closeStorage();
            user.setCurrentMind(root);
            root = (Mind) root.useStorage("latent-b");
            user.setCurrentMind(root);
            require(root.compile("!other(9);"), "replacement compile");
            check(root, "replacement-generation");
            require(!Boolean.TRUE.equals(root.query("?stored(7);")), "old storage leaked");
            System.out.println("LATENT_LIFECYCLE_PASS");
        } finally {
            if (user.getCurrentMind() != null && user.getCurrentMind().isStorageUsed()) {
                user.setCurrentMind(user.getCurrentMind().closeStorage());
            }
        }
    }

    private static void check(Mind mind, String label) throws Exception {
        Map<String, Set<Long>> expected = new TreeMap<>();
        List<IRule> rules = new ArrayList<>();
        List<Domain> domains = new ArrayList<>();
        for (IRule r : mind.getRules()) {
            if (r.isDeleted(mind)) continue;
            rules.add(r);
            for (List<Domain> branch : ((Rule) r).getTree()) for (Domain d : branch) {
                domains.add(d);
                String key = d.getPredicateId() + ":" + d.isAntc();
                if (!expected.containsKey(key)) expected.put(key, new TreeSet<Long>());
                expected.get(key).add(r.getId());
                seenPredicates.add(d.getPredicateId());
            }
        }
        // Include signatures that disappeared after rollback/close/replacement.
        // Keep IDs only: never retain Domains from a discarded generation.
        for (long predicate : seenPredicates) for (boolean antc : new boolean[]{false, true}) {
            String key = predicate + ":" + antc;
            Set<Long> want = expected.get(key);
            if (want == null) want = Collections.emptySet();
            Set<Long> actual = new TreeSet<>();
            for (IRule r : mind.getRules().findByDomain(predicate, antc)) actual.add(r.getId());
            require(actual.equals(want), label + " topology mismatch " + key);
        }
        if (System.getProperty("kanger.experiment.latent", "off").startsWith("factory")) {
            long before = builds(mind);
            for (int repeat = 0; repeat < 2; repeat++) {
                for (Domain source : domains) for (IRule rule : rules) {
                    List<Domain> want = new ArrayList<>();
                    for (List<Domain> branch : ((Rule) rule).getTree()) for (Domain d : branch) {
                        if (d.getPredicateId() == source.getPredicateId() && d.isAntc() != source.isAntc()) want.add(d);
                    }
                    List<Domain> actual = mind.getRules().getLatentDomainCandidates(rule, source);
                    require(want.size() == actual.size(), label + " occurrence count");
                    for (int i = 0; i < want.size(); i++) require(want.get(i) == actual.get(i), label + " occurrence identity/order");
                }
            }
            require(builds(mind) == before, label + " lookup rebuilt topology");
        }
        System.out.println("LIFECYCLE " + label + " signatures=" + expected.size());
    }

    private static long builds(Mind mind) throws Exception {
        long total = 0;
        Object factory = mind.getRules();
        Field index = factory.getClass().getDeclaredField("candidateIndex");
        Field parent = factory.getClass().getDeclaredField("parentIndex");
        index.setAccessible(true);
        parent.setAccessible(true);
        while (factory != null) {
            Object candidate = index.get(factory);
            Field builds = candidate.getClass().getDeclaredField("occurrenceBuilds");
            builds.setAccessible(true);
            total += builds.getLong(candidate);
            factory = parent.get(factory);
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

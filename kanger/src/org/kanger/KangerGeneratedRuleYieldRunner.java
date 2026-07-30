/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * P5a generated-rule experiment. Each scenario starts from an independent Mind
 * and measures one durable insertion that may materialize generated rules.
 * This runner remains observational and does not change inference behaviour.
 */
public final class KangerGeneratedRuleYieldRunner {

    private KangerGeneratedRuleYieldRunner() {
    }

    public static void main(String[] args) {
        try {
            Path home = Files.createTempDirectory("kanger-generated-yield-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            System.out.println("scenario,passes,executed_operations,new_tvalues,new_causes,"
                    + "new_tsolves,total_rule_delta,generated_rule_delta,"
                    + "observed_generated_rules,effect_delta,derived_true");

            runScenario(
                    "one-hop",
                    new String[]{"!@x source(x) -> derived(x);"},
                    null,
                    "!source(1);",
                    "?derived(1);");

            runScenario(
                    "two-hop",
                    new String[]{
                            "!@x source(x) -> middle(x);",
                            "!@x middle(x) -> derived(x);"
                    },
                    null,
                    "!source(1);",
                    "?derived(1);");

            runScenario(
                    "deduplicated-target",
                    new String[]{"!@x source(x) -> derived(x);"},
                    "!derived(1);",
                    "!source(1);",
                    "?derived(1);");
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runScenario(String scenario,
                                    String[] rules,
                                    String preexisting,
                                    String operation,
                                    String verification) throws Exception {
        Mind mind = createMind(scenario);
        for (String rule : rules) {
            requireTrue(mind.query(rule, null, false),
                    "Rule fixture failed: " + scenario + " / " + rule);
        }
        if (preexisting != null) {
            requireTrue(mind.query(preexisting, null, false),
                    "Preexisting fixture failed: " + scenario);
        }

        Snapshot before = Snapshot.capture(mind);
        Boolean result;
        SemanticEffectTelemetry.Snapshot effects;
        SemanticEffectTelemetry.begin();
        try {
            result = mind.query(operation, null, false);
        } finally {
            effects = SemanticEffectTelemetry.end();
        }
        requireTrue(result, "Measured insertion failed: " + scenario);
        Snapshot after = Snapshot.capture(mind);

        LinkerStatistics statistics = mind.getLinkerStatistics();
        long totalRuleDelta = nonNegativeDelta(after.rules, before.rules);
        long generatedRuleDelta = nonNegativeDelta(
                after.generatedRules, before.generatedRules);
        long observedGeneratedRules = effects.getNewGeneratedRules();
        if (observedGeneratedRules != generatedRuleDelta) {
            throw new IllegalStateException(
                    "Generated-rule hook mismatch for " + scenario
                            + ": observed=" + observedGeneratedRules
                            + ", delta=" + generatedRuleDelta);
        }
        long effectDelta = statistics.getNewTValues()
                + effects.getNewCauses()
                + effects.getNewTSolves()
                + observedGeneratedRules;

        Boolean derived = mind.query(verification, null, false);

        System.out.printf(Locale.ROOT,
                "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s%n",
                scenario,
                statistics.getPasses(),
                statistics.getUnificationAttempts(),
                statistics.getNewTValues(),
                effects.getNewCauses(),
                effects.getNewTSolves(),
                totalRuleDelta,
                generatedRuleDelta,
                observedGeneratedRules,
                effectDelta,
                Boolean.TRUE.equals(derived));
    }

    private static Mind createMind(String scenario) throws Exception {
        String suffix = scenario + "-" + System.nanoTime();
        User user = (User) UserFactory.createUser(
                "generated-yield-" + suffix,
                "generated-yield-" + suffix);
        new UDF().init(user);
        new DB().init(user);
        return (Mind) new Mind(user).clearWorkspace();
    }

    private static void requireTrue(Boolean result, String message) {
        if (!Boolean.TRUE.equals(result)) {
            throw new IllegalStateException(message);
        }
    }

    private static long nonNegativeDelta(long after, long before) {
        return Math.max(0L, after - before);
    }

    private static final class Snapshot {
        private final long rules;
        private final long generatedRules;

        private Snapshot(long rules, long generatedRules) {
            this.rules = rules;
            this.generatedRules = generatedRules;
        }

        private static Snapshot capture(Mind mind) throws Exception {
            long rules = 0L;
            long generated = 0L;
            for (IRule rule : mind.getRules()) {
                if (!rule.isDeleted(mind)) {
                    ++rules;
                    if (rule.isGenerated()) {
                        ++generated;
                    }
                }
            }
            return new Snapshot(rules, generated);
        }
    }
}

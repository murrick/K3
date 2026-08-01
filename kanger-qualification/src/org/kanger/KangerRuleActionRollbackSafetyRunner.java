/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Domain;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focused regression gate for RuleFactory action rollback.
 *
 * <p>A released speculative Rule must disappear from canonical storage and
 * derived candidate metadata, and the Linker continuation signal must return
 * to its pre-mark value.</p>
 */
public final class KangerRuleActionRollbackSafetyRunner {

    private KangerRuleActionRollbackSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-rule-action-rollback-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            IUser user = UserFactory.createUser(
                    "rule-action-rollback", "rule-action-rollback");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = new Mind(user);

            Rule baseline = createRule(mind, "rule_action_baseline");
            IRule insertedBaseline = mind.getRules().add(baseline);
            require(insertedBaseline == baseline, "baseline Rule was not inserted");
            mind.getRules().dropAction();
            require(!mind.getRules().isAction(),
                    "baseline Rule action flag was not cleared");

            Rule speculative = createRule(mind, "rule_action_speculative");
            long speculativeId = speculative.getId();
            int baselineSize = mind.getRules().size();

            mind.getRules().mark();
            IRule insertedSpeculative = mind.getRules().add(speculative);
            require(insertedSpeculative == speculative,
                    "speculative Rule was not inserted");
            require(mind.getRules().isAction(),
                    "speculative Rule mutation did not raise action");

            mind.getRules().release();
            require(mind.getRules().size() == baselineSize,
                    "released speculative Rule remained in canonical cache");
            require(mind.getRules().get(speculativeId) == null,
                    "released speculative Rule remained findable");
            require(!mind.getRules().isAction(),
                    "release retained a stale RuleFactory Linker continuation signal");

            System.out.println("RULE_ACTION_ROLLBACK_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static Rule createRule(Mind mind, String name) throws Exception {
        ITerm predicateName = mind.getTerms().add(name);
        Predicate predicate = mind.getPredicates().add(predicateName, 0);

        Rule rule = new Rule(mind);
        mind.getRules().register(rule);
        rule.setOrigin(mind.getTerms().add(name + "_origin"));

        Domain domain = mind.getDomains().add(
                predicate, false, new ArgumentsList(), rule);
        rule.getTree().get(0).add(domain);
        return rule;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

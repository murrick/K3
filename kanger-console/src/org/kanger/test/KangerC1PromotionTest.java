/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.test;

import org.kanger.Mind;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Regression corpus for C1: an explicit primary basis must not be lost when
 * its canonical formula already exists only as a generated rule.
 */
public final class KangerC1PromotionTest {

    private IMind mind;

    private KangerC1PromotionTest(IMind mind) {
        this.mind = mind;
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        KangerC1PromotionTest suite = new KangerC1PromotionTest(mind);
        Map<String, Method> methods = new TreeMap<>();
        for (Method method : KangerC1PromotionTest.class.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix) && method.getParameterTypes().length == 0) {
                method.setAccessible(true);
                methods.put(method.getName(), method);
            }
        }

        int success = 0;
        int failures = 0;
        long start = System.currentTimeMillis();
        System.out.println("====================================================");
        System.out.println("C1 primary promotion regression tests");

        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            System.out.println("Testing: " + entry.getKey());
            try {
                entry.getValue().invoke(suite);
                ++success;
                System.out.println("OK");
            } catch (InvocationTargetException error) {
                ++failures;
                Throwable cause = error.getCause() == null ? error : error.getCause();
                cause.printStackTrace(System.err);
            }
            System.out.println("----------------------------------------------------");
        }

        System.out.println("C1 Timing: " + ((System.currentTimeMillis() - start) / 1000.0));
        System.out.println("C1 Success: " + success);
        System.out.println("C1 Fails: " + failures);
        System.out.println("====================================================");
        return failures == 0;
    }

    private void resetWorkspace() throws Exception {
        mind = mind.clearWorkspace();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private Rule findStoredRule(IMind context, String predicate, Object argument) throws Exception {
        Mind current = (Mind) context;
        for (IRule candidate : context.getRules()) {
            Rule rule = (Rule) candidate;
            if (rule.isDeleted(context) || !rule.isStored() || rule.getArguments().size() != 1) {
                continue;
            }
            if (!predicate.equals(rule.getPredicate().getName(context))) {
                continue;
            }
            ITerm value = rule.getArguments().get(0).getValue(current);
            if (value != null && Objects.equals(argument, value.getValue())) {
                return rule;
            }
        }
        throw new AssertionError("Stored rule not found: " + predicate + "(" + argument + ")");
    }

    private Rule requireGeneratedRule(IMind context, String predicate, Object argument) throws Exception {
        Rule rule = findStoredRule(context, predicate, argument);
        require(((Mind) context).getRules().isGenerated(rule),
                "Expected generated rule: " + predicate + "(" + argument + ")");
        return rule;
    }

    private Rule requirePrimaryRule(IMind context, long id) throws Exception {
        Rule rule = ((Mind) context).getRules().get(id);
        require(rule != null, "Primary rule is missing: " + id);
        require(!((Mind) context).getRules().isGenerated(rule),
                "Rule is still generated after promotion: " + id);
        require(rule.getCauses().isEmpty(), "Promoted rule retained derivation causes: " + id);
        return rule;
    }

    public void set_c1_01_direct_primary_promotion() throws Exception {
        resetWorkspace();

        require(Boolean.TRUE.equals(mind.query("!@x seed_direct(x) -> target_direct(x);")),
                "Producer rule was not accepted");
        long producerId = mind.getAcceptedRule().getId();
        require(Boolean.TRUE.equals(mind.query("!seed_direct(value);")),
                "Producer fact was not accepted");

        Rule derived = requireGeneratedRule(mind, "target_direct", "value");
        long derivedId = derived.getId();

        require(Boolean.TRUE.equals(mind.query("!target_direct(value);")),
                "Explicit primary duplicate was not accepted");
        require(mind.getAcceptedRule() != null && mind.getAcceptedRule().getId() == derivedId,
                "Promotion changed canonical rule identity");
        requirePrimaryRule(mind, derivedId);

        require(Boolean.TRUE.equals(mind.query("-rule(" + producerId + ");")),
                "Producer rule was not deleted");
        require(Boolean.TRUE.equals(mind.query("?")), "Program reinitialization failed");
        require(Boolean.TRUE.equals(mind.query("?target_direct(value);")),
                "Promoted primary rule did not survive producer deletion and reinitialization");
        requirePrimaryRule(mind, derivedId);
    }

    public void set_c1_02_plus_primary_promotion() throws Exception {
        resetWorkspace();

        require(Boolean.TRUE.equals(mind.query("!@x seed_plus(x) -> middle_plus(x);")),
                "First producer rule was not accepted");
        require(Boolean.TRUE.equals(mind.query("!@x middle_plus(x) -> target_plus(x);")),
                "Second producer rule was not accepted");
        long producerId = mind.getAcceptedRule().getId();
        require(Boolean.TRUE.equals(mind.query("!seed_plus(value);")),
                "Producer fact was not accepted");

        Rule derived = requireGeneratedRule(mind, "target_plus", "value");
        long derivedId = derived.getId();

        require(Boolean.TRUE.equals(mind.query("+@x seed_plus(x) -> target_plus(x);")),
                "Materialization did not complete");
        requirePrimaryRule(mind, derivedId);

        require(Boolean.TRUE.equals(mind.query("-rule(" + producerId + ");")),
                "Old producing rule was not deleted");
        require(Boolean.TRUE.equals(mind.query("?")), "Program reinitialization failed");
        require(Boolean.TRUE.equals(mind.query("?target_plus(value);")),
                "Materialized primary rule did not survive loss of the old derivation");
        requirePrimaryRule(mind, derivedId);
    }

    public void set_c1_03_child_commit_release_promotion() throws Exception {
        resetWorkspace();
        Mind parent = (Mind) mind;

        require(Boolean.TRUE.equals(parent.query("!@x seed_child(x) -> target_child(x);")),
                "Producer rule was not accepted");
        require(Boolean.TRUE.equals(parent.query("!seed_child(value);")),
                "Producer fact was not accepted");
        Rule derived = requireGeneratedRule(parent, "target_child", "value");
        long derivedId = derived.getId();

        Mind discarded = new Mind(parent);
        require(Boolean.TRUE.equals(discarded.query("!target_child(value);")),
                "Child promotion candidate was not accepted");
        requirePrimaryRule(discarded, derivedId);
        requireGeneratedRule(parent, "target_child", "value");
        parent.release(discarded);
        requireGeneratedRule(parent, "target_child", "value");

        Mind committed = new Mind(parent);
        require(Boolean.TRUE.equals(committed.query("!target_child(value);")),
                "Committed child promotion candidate was not accepted");
        requirePrimaryRule(committed, derivedId);
        require(parent.commit(committed), "Child promotion commit was rejected");
        requirePrimaryRule(parent, derivedId);

        mind = parent;
    }
}

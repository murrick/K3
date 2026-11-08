/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.units.Term;

/** Whole arithmetic rule instances retain signs, sharing, tuple order and source identity. */
public final class ContextArithmeticRuleInspectionRunner {
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-rule-").toString());
        verify("!@x fun(x)->next(x+1);", new double[]{1}, "!fun(1)->next(2);");
        verify("!@x fun(x)->next(x+x);", new double[]{3}, "!fun(3)->next(6);");
        verify("!@x @y fun(x,y)->next(x+1,y,x);", new double[]{1, 4},
                "!fun(1,4)->next(2,4,1);");
        verify("!@x fun(x)->next(x+1)&&keep(x);", new double[]{1},
                "!fun(1)->next(2)&&keep(1);");
        ContextArithmeticRuleInspection shape = capture("!@x fun(x)->next(x+1);", false);
        require(shape.equivalentTo(capture("!@y fun(y)->next(y+1);", true), 100000),
                "replay shape normalizes binder names and native rule allocation");
        require(!shape.equivalentTo(capture("!@x fun(x)->next(x+2);", false), 100000),
                "a changed arithmetic constant is rejected");
        require(!capture("!@x @y pair(x,y)->next(x+x);", false).equivalentTo(
                capture("!@x @y pair(x,y)->next(x+y);", false), 100000),
                "replay preserves repeated variable dependencies");
        User metaUser = new User();
        Mind meta = new Mind(metaUser);
        metaUser.setCurrentMind(meta);
        meta.compileLine("!rule(1);", false, new LinkedList<>());
        ContextProductionInventory mi = ContextProductionInventory.inspect(meta, UUID.randomUUID(), 0,
                true, Collections.emptyList(), 1000);
        boolean metaRejected = false;
        try { ContextArithmeticRuleInspection.inspect(meta, mi, mi.primary.get(0).nativeRule, 100000); }
        catch (IllegalStateException unsupported) { metaRejected = true; }
        require(metaRejected, "native rule metadata is excluded from ordinary relation models");
        System.out.println("CONTEXT_ARITHMETIC_RULE_INSPECTION_PASS checks=" + checks);
        System.exit(0);
    }

    private static void verify(String text, double[] values, String ground) throws Exception {
        Mind mind = memory(text);
        UUID context = UUID.randomUUID();
        ContextProductionInventory inventory = ContextProductionInventory.inspect(
                mind, context, 7, true, Collections.emptyList(), 1000);
        long id = inventory.primary.get(0).nativeRule;
        ContextArithmeticRuleInspection rule = ContextArithmeticRuleInspection.inspect(mind, inventory, id, 100000);
        require(rule.source.context.equals(context) && rule.source.revision == 7 && rule.source.rule == id
                && rule.origin.equals(inventory.primary.get(0).origin), "whole original source identity");
        require(rule.variables.size() == values.length, "shared variable inventory");
        Map<Integer, SemanticTermSnapshot> assignment = new LinkedHashMap<>();
        int index = 0;
        for (int variable : rule.variables) assignment.put(variable,
                SemanticTermSnapshot.capture(new Term(values[index++], null)));
        List<Clause> actual = rule.instantiate(assignment, 100000);
        Mind expectedMind = memory(ground);
        ContextProductionInventory expectedInventory = ContextProductionInventory.inspect(expectedMind,
                UUID.randomUUID(), 0, true, Collections.emptyList(), 1000);
        ContextProductionNativeBinding.Result expected = ContextProductionNativeBinding.bind(
                expectedMind, expectedInventory, 1000000, 100000);
        require(expected.bound, "ground comparison fixture binds");
        List<Clause> expectedClauses = expected.snapshot.primary.get(0).clauses;
        boolean equivalent = actual.size() == expectedClauses.size();
        for (int i = 0; equivalent && i < actual.size(); i++) equivalent = actual.get(i).equivalent(expectedClauses.get(i));
        require(equivalent, "complete instance matches independent native ground compilation");
        boolean stopped = false;
        try { rule.instantiate(assignment, 1); } catch (IllegalStateException exhausted) { stopped = true; }
        require(stopped, "incomplete instance cannot escape a work stop");
        boolean missing = false;
        try { rule.instantiate(Collections.emptyMap(), 100000); } catch (IllegalArgumentException rejected) { missing = true; }
        require(missing, "partial whole-rule assignment refused");
        require(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,
                context, 7, true, Collections.emptyList(), 1000).fingerprint), "instantiation preserves native source");
    }

    private static ContextArithmeticRuleInspection capture(String source, boolean shifted) throws Exception {
        Mind mind = memory((shifted ? "!preamble(7);" : "") + source);
        ContextProductionInventory inventory = ContextProductionInventory.inspect(
                mind, UUID.randomUUID(), 0, true, Collections.emptyList(), 1000);
        long id = inventory.primary.get(inventory.primary.size() - 1).nativeRule;
        return ContextArithmeticRuleInspection.inspect(mind, inventory, id, 100000);
    }

    private static Mind memory(String text) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile(text, null, false)) throw new AssertionError("fixture " + text);
        return mind;
    }

    private static void require(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
}

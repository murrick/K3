/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextArithmeticRoundInspection.Result;

public final class ContextArithmeticRoundInspectionRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-round-").toString());
        Mind x = memory("!fun(1);");
        Mind foreign = memory("!@x fun(x)->next(x+1); !@x next(x)->out(x+1); "
                + "!private_seed(4); !@x private_seed(x)->hidden(x+1);");
        ContextProductionInventory xi = inventory(x, true);
        ContextProductionInventory fi = inventory(foreign, false);
        List<ContextArithmeticRuleInspection> rules = new ArrayList<>();
        List<Premise> initial = new ArrayList<>();
        initial.add(Premise.primary(x, xi, xi.primary.get(0).nativeRule, 0, 100000));
        for (ContextProductionInventory.Row row : fi.primary) {
            if (row.origin.contains("@")) rules.add(ContextArithmeticRuleInspection.inspect(foreign, fi, row.nativeRule, 100000));
            else initial.add(Premise.primary(foreign, fi, row.nativeRule, 0, 100000));
        }
        List<Premise> before = new ArrayList<>(initial);
        Result first = round(xi, fi, rules, initial, 100000, 100);
        require(first.changed && has(first.frontier, "next") && !has(first.frontier, "out"),
                "first frozen round cannot chain into out");
        require(has(first.frontier, "hidden") && !has(first.common, "hidden")
                && !has(first.common, "private_seed") && has(first.common, "next"),
                "private-only ancestry stays outside common products");
        List<ContextArithmeticRuleInspection> reversed = new ArrayList<>(rules);
        Collections.reverse(reversed);
        Result reverse = round(xi, fi, reversed, initial, 100000, 100);
        require(same(first.frontier, reverse.frontier), "rule traversal order preserves full proof frontier");
        Result second = round(xi, fi, rules, first.frontier, 100000, 100);
        require(second.changed && has(second.common, "out"), "next common barrier enables second rule");
        Result third = round(xi, fi, rules, second.frontier, 100000, 100);
        require(!third.changed && same(second.frontier, third.frontier), "unchanged round preserves minimum-depth proofs");
        reject(() -> round(xi, fi, rules, initial, 1, 100), "shared work exhaustion");
        reject(() -> round(xi, fi, rules, initial, 100000, 2), "frontier capacity exhaustion");
        Mind outsider = memory("!fun(7);");
        ContextProductionInventory oi = inventory(outsider, false);
        List<Premise> alien = Collections.singletonList(Premise.primary(outsider, oi, oi.primary.get(0).nativeRule, 0, 100000));
        reject(() -> round(xi, fi, rules, alien, 100000, 100), "raw premise from another independent cut");
        require(same(initial, before), "initial frontier remains intact");
        System.out.println("CONTEXT_ARITHMETIC_ROUND_INSPECTION_PASS checks=" + checks);
        System.exit(0);
    }

    private static Result round(ContextProductionInventory x, ContextProductionInventory foreign,
                                List<ContextArithmeticRuleInspection> rules, List<Premise> input, int work, int capacity) throws Exception {
        return ContextArithmeticRoundInspection.inspect(x, Collections.singletonList(foreign), null,
                rules, input, 16, work, capacity);
    }

    private static boolean same(List<Premise> a, List<Premise> b) {
        return a.size() == b.size() && a.stream().allMatch(left -> b.stream().anyMatch(
                right -> left.fact.equivalent(right.fact) && left.proof.equals(right.proof)));
    }

    private static boolean has(List<Premise> input, String predicate) {
        for (Premise premise : input) if (premise.fact.predicate.equals(predicate)) return true;
        return false;
    }

    private static ContextProductionInventory inventory(Mind mind, boolean local) throws Exception {
        return ContextProductionInventory.inspect(mind, UUID.randomUUID(), 0, local, Collections.emptyList(), 1000);
    }

    private static Mind memory(String source) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile(source, null, false)) throw new AssertionError("fixture " + source);
        return mind;
    }

    private static void reject(Attempt attempt, String why) throws Exception {
        boolean rejected = false;
        try { attempt.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        require(rejected, why);
    }

    private static void require(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
}

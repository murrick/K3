/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextArithmeticRuleInspection.Step;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.units.Rule;

public final class ContextArithmeticStepRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-step-").toString());
        ContextArithmeticRuleInspection first = rule("!@x fun(x)->next(x+1);");
        Premise seed = premise("!fun(1);");
        Step next = first.apply(0, Collections.singletonList(seed), 2, 100000);
        require(next.conclusion.fact.equivalent(premise("!next(2);").fact), "arithmetic forward consequence");
        require(next.conclusion.proof.depth == 1 && next.conclusion.proof.sources.equals(
                new HashSet<>(Arrays.asList(first.source, seed.proof.sources.iterator().next()))), "exact rule and donor proof");
        ContextArithmeticRuleInspection second = rule("!@x next(x)->out(x+1);");
        Step out = second.apply(0, Collections.singletonList(next.conclusion), 2, 100000);
        Set<Source> expectedSources = new HashSet<>(next.conclusion.proof.sources);
        expectedSources.add(second.source);
        require(out.conclusion.fact.equivalent(premise("!out(3);").fact)
                && out.conclusion.proof.depth == 2 && out.conclusion.proof.sources.equals(expectedSources),
                "chained arithmetic retains whole source ancestry and depth");
        reject(() -> second.apply(0, Collections.singletonList(next.conclusion), 1, 100000), "cumulative depth stop");
        reject(() -> first.apply(0, Collections.emptyList(), 2, 100000), "missing premise");
        reject(() -> first.apply(0, Collections.singletonList(seed), 2, 1), "work stop");
        ContextArithmeticRuleInspection diagonal = rule("!@x pair(x,x)->out(x+1);");
        reject(() -> diagonal.apply(0, Collections.singletonList(premise("!pair(1,2);")), 2, 100000),
                "repeated variable mismatch");
        require(diagonal.apply(0, Collections.singletonList(premise("!pair(2,2);")), 2, 100000)
                .conclusion.fact.equivalent(premise("!out(3);").fact), "repeated variable agreement");
        reject(() -> first.apply(0, Collections.singletonList(premise("!~fun(1);")), 2, 100000), "opposite donor sign");
        ContextArithmeticRuleInspection join = rule("!@x a(x)&&b(x)->out(x+1);");
        Step joined = join.apply(0, Arrays.asList(premise("!a(2);"), premise("!b(2);")), 2, 100000);
        require(joined.conclusion.fact.equivalent(premise("!out(3);").fact)
                && joined.conclusion.proof.sources.size() == 3 && joined.premises.size() == 2,
                "all joined donors remain in the consequence proof");
        reject(() -> join.apply(0, Arrays.asList(premise("!a(2);"), premise("!b(3);")), 2, 100000),
                "shared variable mismatch across donors");
        ContextProductionNativeBinding.Snapshot one = snapshot("!fun(1);");
        ContextProductionNativeBinding.Snapshot two = snapshot("!fun(2);");
        ContextProductionNativeBinding.Snapshot trusted = ContextProductionNativeBinding.trusted(
                "team", Arrays.asList(one, two), 100000);
        require(one.primary.get(0).source.rule == two.primary.get(0).source.rule,
                "trusted member fixture reuses a native rule number");
        require(Premise.primary(trusted, two.primary.get(0).source, 0).fact.equivalent(premise("!fun(2);").fact),
                "full Source selects the second trusted member");
        System.out.println("CONTEXT_ARITHMETIC_STEP_PASS checks=" + checks);
        System.exit(0);
    }

    private static ContextArithmeticRuleInspection rule(String source) throws Exception {
        Mind mind = memory(source);
        ContextProductionInventory inventory = inventory(mind, true);
        Rule rule = (Rule) mind.getRules().get(inventory.primary.get(0).nativeRule);
        return ContextArithmeticRuleInspection.inspect(mind, inventory, rule.getId(), 100000);
    }

    private static Premise premise(String source) throws Exception {
        ContextProductionNativeBinding.Snapshot bound = snapshot(source);
        return Premise.primary(bound, bound.primary.get(0).source, 0);
    }

    private static ContextProductionNativeBinding.Snapshot snapshot(String source) throws Exception {
        Mind mind = memory(source);
        ContextProductionInventory inventory = inventory(mind, false);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(mind, inventory, 1000000, 100000);
        if (!bound.bound) throw new AssertionError(bound.reason);
        return bound.snapshot;
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

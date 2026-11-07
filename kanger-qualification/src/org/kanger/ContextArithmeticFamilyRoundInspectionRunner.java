/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextArithmeticFamilyRoundInspection.Cut;
import org.kanger.ContextArithmeticFamilyRoundInspection.Frame;

public final class ContextArithmeticFamilyRoundInspectionRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }

    private static final class Fixture {
        final ContextProductionInventory inventory;
        final List<ContextArithmeticRuleInspection> rules = new ArrayList<>();
        final List<Premise> seeds = new ArrayList<>();

        Fixture(boolean local, String source) throws Exception {
            User user = new User();
            Mind mind = new Mind(user);
            user.setCurrentMind(mind);
            if (!mind.compile(source, null, false)) throw new AssertionError("fixture");
            inventory = ContextProductionInventory.inspect(mind, UUID.randomUUID(), 0,
                    local, Collections.emptyList(), 1000);
            for (ContextProductionInventory.Row row : inventory.primary) {
                if (row.origin.contains("@")) rules.add(ContextArithmeticRuleInspection.inspect(mind, inventory, row.nativeRule, 100000));
                else seeds.add(Premise.primary(mind, inventory, row.nativeRule, 0, 100000));
            }
        }

        Cut cut() { return new Cut(null, Collections.singletonList(inventory), rules, seeds); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-family-round-").toString());
        Fixture x = new Fixture(true, "!seed(1); !@x out(x)->local(x+1);");
        Fixture a = new Fixture(false, "!@x seed(x)->middle(x+1); !private_seed(4); !@x private_seed(x)->hidden(x+1);");
        Fixture b = new Fixture(false, "!@x middle(x)->out(x+1); !@x hidden(x)->leak(x+1);");
        Frame start = start(x, Arrays.asList(a.cut(), b.cut()));
        Frame first = advance(start);
        require(has(first.common, "middle") && !has(first.common, "out"), "frozen first family barrier");
        require(has(first.privateFrontiers.get(0), "hidden") && !has(first.common, "hidden"),
                "private result remains in A");
        Frame second = advance(first);
        require(has(second.common, "out") && !has(second.common, "local"), "B consumes only prior common products");
        Premise out = find(second.common, "out");
        require(out.proof.depth == 2 && out.proof.sources.size() == 3,
                "foreign ancestry survives transport into independent B");
        Frame third = advance(second);
        require(has(third.common, "local") && !has(third.common, "leak"),
                "X receives B result while A private data cannot leak through B");
        Frame fourth = advance(third);
        require(!fourth.changed && same(third.common, fourth.common), "unchanged complete family frontier");
        Frame reverse = start(x, Arrays.asList(b.cut(), a.cut()));
        for (int i = 0; i < 4; i++) reverse = advance(reverse);
        require(same(fourth.common, reverse.common) && !reverse.changed, "cut traversal order preserves Source/depth alternatives");

        List<ContextArithmeticRuleInspection> trustedRules = new ArrayList<>(a.rules);
        trustedRules.addAll(b.rules);
        List<Premise> trustedSeeds = new ArrayList<>(a.seeds);
        trustedSeeds.addAll(b.seeds);
        Cut trusted = new Cut("team", Arrays.asList(a.inventory, b.inventory), trustedRules, trustedSeeds);
        Frame group = start(x, Collections.singletonList(trusted));
        for (int i = 0; i < 4; i++) group = advance(group);
        require(has(group.privateFrontiers.get(0), "leak") && !has(group.common, "leak")
                && has(group.common, "local"), "trusted members share private data without making it X-supported");
        reject(() -> ContextArithmeticFamilyRoundInspection.advance(third, 1, 1000000, 100), "imported proof depth stop");
        reject(() -> ContextArithmeticFamilyRoundInspection.advance(start, 16, 1, 100), "whole-family work stop");
        reject(() -> ContextArithmeticFamilyRoundInspection.advance(start, 16, 1000000, 1), "common frontier capacity stop");
        reject(() -> start(x, Arrays.asList(a.cut(), a.cut())), "repeated independent member");
        require(!has(start.common, "middle") && start.privateFrontiers.get(0).size() == a.seeds.size(),
                "earlier sealed frame stays unchanged");
        System.out.println("CONTEXT_ARITHMETIC_FAMILY_ROUND_INSPECTION_PASS checks=" + checks);
        System.exit(0);
    }

    private static Frame start(Fixture x, List<Cut> cuts) {
        return ContextArithmeticFamilyRoundInspection.start(x.inventory, x.rules, x.seeds, cuts, 1000000, 100);
    }

    private static Frame advance(Frame frame) throws Exception {
        return ContextArithmeticFamilyRoundInspection.advance(frame, 16, 1000000, 100);
    }

    private static boolean has(List<Premise> input, String predicate) { return find(input, predicate) != null; }

    private static Premise find(List<Premise> input, String predicate) {
        for (Premise premise : input) if (premise.fact.predicate.equals(predicate)) return premise;
        return null;
    }

    private static boolean same(List<Premise> a, List<Premise> b) {
        return a.size() == b.size() && a.stream().allMatch(left -> b.stream().anyMatch(
                right -> left.fact.equivalent(right.fact) && left.proof.equals(right.proof)));
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

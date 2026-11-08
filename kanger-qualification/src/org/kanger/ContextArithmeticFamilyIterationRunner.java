/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import static org.kanger.ContextArithmeticFamilyRoundInspection.iterate;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextArithmeticFamilyRoundInspection.*;

public final class ContextArithmeticFamilyIterationRunner {
    private static int checks;

    private static final class Fixture {
        final ContextProductionInventory inventory;
        final List<ContextArithmeticRuleInspection> rules = new ArrayList<>();
        final List<Premise> seeds = new ArrayList<>();

        Fixture(boolean local, String source) throws Exception {
            User user = new User();
            Mind mind = new Mind(user);
            user.setCurrentMind(mind);
            if (!mind.compile(source, null, false)) throw new AssertionError("fixture " + source);
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
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-family-iteration-").toString());
        Fixture x = new Fixture(true, "!seed(1); !@x out(x)->local(x+1);");
        Fixture a = new Fixture(false, "!@x seed(x)->middle(x+1);");
        Fixture b = new Fixture(false, "!@x middle(x)->out(x+1);");
        Frame start = start(x, Arrays.asList(a.cut(), b.cut()));
        Iteration closed = iterate(start, 4, 16, 1000000, 100);
        require(closed.status == Status.STABLE_PENDING && closed.completedBarriers == 4
                && closed.frame != null && !closed.frame.changed, "unchanged complete barrier closes the loop");
        incomplete(iterate(start, 3, 16, 1000000, 100), "productive last allowed barrier is not saturation");
        incomplete(iterate(start, 4, 16, 1, 100), "whole iteration work stop");
        incomplete(iterate(start, 4, 16, 1000000, 1), "frontier capacity stop");
        incomplete(iterate(start, 4, 1, 1000000, 100), "proof depth stop");
        require(start.common.size() == 1 && !start.changed, "incomplete iterations leave the input frame intact");
        Iteration reversed = iterate(start(x, Arrays.asList(b.cut(), a.cut())), 4, 16, 1000000, 100);
        require(reversed.status == Status.STABLE_PENDING && same(closed.frame.common, reversed.frame.common),
                "reversed cuts preserve the final proof frontier");
        Fixture denial = new Fixture(false, "!~middle(2);");
        Iteration collision = iterate(start(x, Arrays.asList(a.cut(), denial.cut())), 1, 16, 1000000, 100);
        require(collision.status == Status.COLLISION && collision.completedBarriers == 1
                && collision.frame != null && !collision.frame.collisions.isEmpty(), "collision at last allowed barrier remains evidence");
        Fixture positive = new Fixture(true, "!p(1);");
        Fixture negative = new Fixture(false, "!~p(1);");
        Iteration initial = iterate(start(positive, Collections.singletonList(negative.cut())), 1, 16, 1000000, 100);
        require(initial.status == Status.COLLISION && initial.completedBarriers == 0, "initial collision precedes inference");

        // Build recursion without executing ordinary native saturation during fixture setup.
        User user = new User();
        Mind recursive = new Mind(user);
        user.setCurrentMind(recursive);
        recursive.compileLine("!num(1);", false, new LinkedList<>());
        recursive.compileLine("!@x num(x)->num(x+1);", false, new LinkedList<>());
        ContextProductionInventory ri = ContextProductionInventory.inspect(recursive, UUID.randomUUID(), 0,
                true, Collections.emptyList(), 1000);
        List<ContextArithmeticRuleInspection> rules = new ArrayList<>();
        List<Premise> seeds = new ArrayList<>();
        for (ContextProductionInventory.Row row : ri.primary) {
            if (row.origin.contains("@")) rules.add(ContextArithmeticRuleInspection.inspect(recursive, ri, row.nativeRule, 100000));
            else seeds.add(Premise.primary(recursive, ri, row.nativeRule, 0, 100000));
        }
        Frame recursiveStart = ContextArithmeticFamilyRoundInspection.start(ri, rules, seeds, Collections.emptyList(), 1000000, 100);
        incomplete(iterate(recursiveStart, 5, 16, 1000000, 100), "unbounded arithmetic generation stops at schedule limit");
        require(recursiveStart.common.size() == 1, "recursive stop exposes no partial frontier through input");
        System.out.println("CONTEXT_ARITHMETIC_FAMILY_ITERATION_PASS checks=" + checks);
        System.exit(0);
    }

    private static Frame start(Fixture x, List<Cut> cuts) throws Exception {
        return ContextArithmeticFamilyRoundInspection.start(x.inventory, x.rules, x.seeds, cuts, 1000000, 100);
    }

    private static void incomplete(Iteration result, String why) {
        require(result.status == Status.INCOMPLETE && result.frame == null, why);
    }

    private static boolean same(List<Premise> a, List<Premise> b) {
        return a.size() == b.size() && a.stream().allMatch(left -> b.stream().anyMatch(
                right -> left.fact.equivalent(right.fact) && left.proof.equals(right.proof)));
    }

    private static void require(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
}

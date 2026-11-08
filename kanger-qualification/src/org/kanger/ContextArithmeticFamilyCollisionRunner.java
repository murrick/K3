/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextArithmeticFamilyRoundInspection.Cut;
import org.kanger.ContextArithmeticFamilyRoundInspection.Frame;
import org.kanger.ContextArithmeticFamilyRoundInspection.Collision;
import org.kanger.ContextProductionCapture.Source;

public final class ContextArithmeticFamilyCollisionRunner {
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
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-family-collision-").toString());
        Fixture x = new Fixture(true, "!seed(1);");
        Fixture a = new Fixture(false, "!@x seed(x)->next(x+1);");
        Fixture b = new Fixture(false, "!~next(2);");
        Frame start = start(x, Arrays.asList(a.cut(), b.cut()));
        require(start.collisions.isEmpty(), "initial compatible cuts");
        Frame refuted = advance(start);
        require(refuted.collisions.size() == 1, "merged production is checked against private receiving facts");
        Collision proof = refuted.collisions.get(0);
        require(proof.cut.equals(ContextDependentCutInspection.independent(b.inventory.context))
                && proof.left.fact.equivalent(proof.right.fact.opposite()), "collision identifies exact receiving cut and literals");
        Set<Source> sources = new HashSet<>(proof.left.proof.sources);
        sources.addAll(proof.right.proof.sources);
        require(sources.size() == 3 && proof.left.proof.depth == 1 && proof.right.proof.depth == 0,
                "collision retains complete X/A/B source evidence and depths");
        Frame reverse = advance(start(x, Arrays.asList(b.cut(), a.cut())));
        Collision reversed = reverse.collisions.get(0);
        require(reversed.cut.equals(proof.cut) && reversed.left.proof.equals(proof.left.proof)
                && reversed.right.proof.equals(proof.right.proof), "cut order preserves the same sourced collision");
        boolean terminal = false;
        try { advance(refuted); } catch (IllegalArgumentException rejected) { terminal = true; }
        require(terminal, "refuted frame cannot continue inference");
        require(start.collisions.isEmpty() && start.common.size() == 1, "prior sealed frame remains unchanged");

        Fixture anchor = new Fixture(true, "!anchor(1);");
        Fixture positive = new Fixture(false, "!p(1);");
        Fixture negative = new Fixture(false, "!~p(1);");
        Frame independent = advance(start(anchor, Arrays.asList(positive.cut(), negative.cut())));
        require(independent.collisions.isEmpty(), "opposite independent sibling facts remain compatible with X");
        List<Premise> jointSeeds = new ArrayList<>(positive.seeds);
        jointSeeds.addAll(negative.seeds);
        Cut trusted = new Cut("team", Arrays.asList(positive.inventory, negative.inventory),
                Collections.emptyList(), jointSeeds);
        Frame trustedFrame = start(anchor, Collections.singletonList(trusted));
        require(trustedFrame.collisions.size() == 1 && trustedFrame.collisions.get(0).cut.equals(
                ContextDependentCutInspection.trusted("team")), "trusted private contradiction is found at the initial barrier");

        Frame initial = start(new Fixture(true, "!p(1);"), Collections.singletonList(negative.cut()));
        require(initial.collisions.size() == 1, "X versus private fact is checked before the first round");
        boolean immutable = false;
        try { refuted.collisions.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "collision evidence is immutable");
        System.out.println("CONTEXT_ARITHMETIC_FAMILY_COLLISION_PASS checks=" + checks);
        System.exit(0);
    }

    private static Frame start(Fixture x, List<Cut> cuts) throws Exception {
        return ContextArithmeticFamilyRoundInspection.start(x.inventory, x.rules, x.seeds, cuts, 1000000, 100);
    }

    private static Frame advance(Frame frame) throws Exception {
        return ContextArithmeticFamilyRoundInspection.advance(frame, 16, 1000000, 100);
    }

    private static void require(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
}

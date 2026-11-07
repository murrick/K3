/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextProductionCapture.Source;

/** One frozen arithmetic cut round. Detached evidence, without a family consistency verdict. */
public final class ContextArithmeticRoundInspection {
    private ContextArithmeticRoundInspection() {}

    public static final class Result {
        public final List<Premise> frontier, common;
        public final boolean changed;

        private Result(List<Premise> frontier, List<Premise> common, boolean changed) {
            this.frontier = Collections.unmodifiableList(new ArrayList<>(frontier));
            this.common = Collections.unmodifiableList(new ArrayList<>(common));
            this.changed = changed;
        }
    }

    public static Result inspect(ContextProductionInventory x, List<ContextProductionInventory> foreign,
                                 String group, List<ContextArithmeticRuleInspection> rules, List<Premise> input,
                                 int maximumDepth, int maximumWork, int capacity) throws Exception {
        if (x == null || !x.localX || foreign == null || rules == null || input == null
                || maximumDepth < 1 || capacity < 1 || (group == null && foreign.size() > 1))
            throw new IllegalArgumentException("One bounded actual cut manifest required");
        if (group != null) TrustGroups.validate(group);
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        Set<UUID> contexts = new HashSet<>();
        Set<Source> universe = new HashSet<>();
        List<ContextProductionInventory> inventories = new ArrayList<>();
        inventories.add(x);
        inventories.addAll(foreign);
        for (ContextProductionInventory inventory : inventories) {
            work.run();
            if (inventory == null || !contexts.add(inventory.context) || (inventory != x && inventory.localX))
                throw new IllegalArgumentException("Distinct physical member inventories required");
            for (ContextProductionInventory.Row row : inventory.primary) {
                work.run();
                universe.add(new Source(inventory.context, inventory.revision, row.nativeRule,
                        inventory.localX, "inventory:" + inventory.fingerprint));
            }
        }
        List<ContextArithmeticRuleInspection> producers = new ArrayList<>();
        for (ContextArithmeticRuleInspection rule : rules) {
            work.run();
            if (rule == null || !universe.contains(rule.source)) throw new IllegalArgumentException("Rule outside the selected cut");
            rule.preflight(work);
            producers.add(rule);
        }
        List<Premise> frozen = new ArrayList<>();
        for (Premise premise : input) {
            work.run();
            if (premise == null) throw new IllegalArgumentException("Complete frozen frontier required");
            for (Source source : premise.proof.sources) {
                work.run();
                if (!universe.contains(source)) throw new IllegalArgumentException("Premise outside the selected cut");
            }
            merge(frozen, premise, capacity, work);
        }
        List<Premise> frontier = new ArrayList<>(frozen);
        boolean[] changed = {false};
        for (ContextArithmeticRuleInspection rule : producers) {
            rule.produce(frozen, maximumDepth, work, step -> {
                changed[0] |= merge(frontier, step.conclusion, capacity, work);
            });
        }
        List<Premise> common = new ArrayList<>();
        for (Premise premise : frontier) {
            work.run();
            boolean local = false;
            for (Source source : premise.proof.sources) {
                work.run();
                local |= source.localX;
            }
            if (local) common.add(premise);
        }
        return new Result(frontier, common, changed[0]);
    }

    private static boolean merge(List<Premise> frontier, Premise candidate, int capacity, Runnable work) {
        for (int i = 0; i < frontier.size(); i++) {
            work.run();
            Premise existing = frontier.get(i);
            if (existing.fact.equivalent(candidate.fact) && existing.proof.sources.equals(candidate.proof.sources)) {
                if (existing.proof.depth <= candidate.proof.depth) return false;
                frontier.set(i, candidate);
                return true;
            }
        }
        if (frontier.size() == capacity) throw new IllegalStateException("Arithmetic round frontier capacity");
        frontier.add(candidate);
        return true;
    }
}
